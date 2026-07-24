package jamsnes;

import jamsnes.cartridge.CartridgeType;
import jamsnes.cartridge.MappingMode;
import jamsnes.cpu.DMA;
import jamsnes.input.Joypad;
import jamsnes.ppu.Background;
import jamsnes.ppu.PPU;
import jamsnes.ppu.PPUUtils;
import jamsnes.renderer.IRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SNESTest {
    @TempDir
    Path tempDir;

    @Test
    void loadRomMapsComponentsAndResetsCpuAndApu() throws IOException {
        SNES snes = new SNES(new TestRenderer());
        snes.cpu.registers().setPc(0x1234);
        snes.apu.internalRegisters().pc = 0x1234;
        Path rom = writeGameRom();

        snes.loadRom(rom.toString());

        assertEquals(0x8000, snes.cpu.registers().pc);
        assertEquals(0xffc0, snes.apu.internalRegisters().pc);
        assertEquals(0x78, snes.bus.read(0x808000));
    }

    @Test
    void loadRomUsesZeroSramSizeWhenHeaderDeclaresNoSram() throws IOException {
        SNES snes = new SNES(new TestRenderer());

        snes.loadRom(writeGameRom().toString());

        assertEquals(0, snes.cartridge.header.sramSize);
        assertEquals(0, snes.sram.getSize());
    }

    @Test
    void loadRomClearsSramContentsForNewCartridge() throws IOException {
        SNES snes = new SNES(new TestRenderer());
        Path rom = writeGameRomWithSram();
        snes.loadRom(rom.toString());
        assertEquals(0x8000, snes.sram.getSize());
        snes.sram.write(0, 0x5a);

        snes.loadRom(rom.toString());

        assertEquals(0x8000, snes.sram.getSize());
        assertEquals(0x00, snes.sram.read(0));
    }

    @Test
    void loadRomClearsAutoJoypadBusyStatus() throws IOException {
        SNES snes = new SNES(new TestRenderer());
        snes.bus.mapComponents(snes);
        snes.cpu.isDisabled = true;
        snes.apu.isDisabled = true;
        snes.cpu.internalRegisters()[0x00] = 0x01;
        snes.ppu.advanceCountersOnly(PPU.H_COUNTER_DOTS * PPU.V_BLANK_START_SCANLINE - 0xff);
        snes.update();
        assertEquals(0x81, snes.bus.read(0x4212));

        snes.loadRom(writeGameRom().toString());
        snes.updateVideoStatusRegisters();

        assertEquals(0x00, snes.bus.read(0x4212));
    }

    @Test
    void loadRomResetsCpuControlPorts() throws IOException {
        SNES snes = new SNES(new TestRenderer());
        snes.bus.mapComponents(snes);
        snes.cpu.internalRegisters()[0x00] = 0x81;
        snes.cpu.internalRegisters()[0x01] = 0x00;
        snes.cpu.internalRegisters()[0x0b] = 0xff;
        snes.cpu.internalRegisters()[0x0c] = 0xff;
        snes.cpu.internalRegisters()[0x0d] = 0xff;

        snes.loadRom(writeGameRom().toString());

        assertEquals(0x00, snes.cpu.internalRegisters()[0x00]);
        assertEquals(0xff, snes.bus.read(0x4213));
        assertEquals(0x00, snes.cpu.internalRegisters()[0x0b]);
        assertEquals(0x00, snes.cpu.internalRegisters()[0x0c]);
        assertEquals(0x00, snes.cpu.internalRegisters()[0x0d]);
    }

    @Test
    void wrioHighToLowTransitionLatchesPpuCounters() {
        SNES snes = new SNES(new TestRenderer());
        snes.bus.mapComponents(snes);

        snes.bus.write(0x4201, 0x80);
        snes.ppu.advanceCountersOnly(PPU.H_COUNTER_DOTS + 259);
        snes.bus.write(0x4201, 0x00);

        assertEquals(0x43, snes.bus.read(0x213f));
        assertEquals(0x03, snes.bus.read(0x213c));
        assertEquals(0x03, snes.bus.read(0x213c));
        assertEquals(0x01, snes.bus.read(0x213d));
        assertEquals(0x00, snes.bus.read(0x213d));
    }

    @Test
    void rdioReflectsWrioWithoutChangingPpuLatchState() {
        SNES snes = new SNES(new TestRenderer());
        snes.bus.mapComponents(snes);

        snes.bus.write(0x4201, 0x80);
        assertEquals(0x80, snes.bus.read(0x4213));
        assertEquals(0x03, snes.bus.read(0x213f));

        snes.bus.write(0x4201, 0x7f);
        assertEquals(0x7f, snes.bus.read(0x4213));
        assertEquals(0x43, snes.bus.read(0x213f));
    }

    @Test
    void wrioDoesNotLatchPpuCountersWithoutHighToLowTransition() {
        SNES snes = new SNES(new TestRenderer());
        snes.bus.mapComponents(snes);

        snes.ppu.advanceCountersOnly(12);
        snes.bus.write(0x4201, 0x00);
        snes.bus.write(0x4201, 0x80);
        snes.ppu.advanceCountersOnly(PPU.H_COUNTER_DOTS + 34);
        snes.bus.write(0x4201, 0x80);

        assertEquals(0x03, snes.bus.read(0x213f));
        assertEquals(0x00, snes.bus.read(0x213c));
        assertEquals(0x00, snes.bus.read(0x213c));
        assertEquals(0x00, snes.bus.read(0x213d));
        assertEquals(0x00, snes.bus.read(0x213d));
    }

    @Test
    void loadRomResetsPpuCountersToFrameStart() throws IOException {
        SNES snes = new SNES(new TestRenderer());
        snes.bus.mapComponents(snes);
        snes.ppu.advanceCountersOnly(PPU.H_COUNTER_DOTS * PPU.V_BLANK_START_SCANLINE + 12);
        snes.updateVideoStatusRegisters();
        assertEquals(0x80, snes.bus.read(0x4212));

        snes.loadRom(writeGameRom().toString());
        snes.updateVideoStatusRegisters();

        assertEquals(0, snes.ppu.hCounter());
        assertEquals(0, snes.ppu.vCounter());
        assertEquals(0x00, snes.bus.read(0x4212));
    }

    @Test
    void loadRomResetsWramPortAddress() throws IOException {
        SNES snes = new SNES(new TestRenderer());
        snes.bus.mapComponents(snes);
        snes.bus.write(0x2181, 0x34);
        snes.bus.write(0x2182, 0x12);
        snes.bus.write(0x2183, 0x01);

        snes.loadRom(writeGameRom().toString());
        snes.bus.write(0x2180, 0xab);

        assertEquals(0xab, snes.wram.data()[0]);
        assertEquals(0x01, snes.wramPort.address());
        assertEquals(0x00, snes.wram.data()[0x11234]);
    }

    @Test
    void loadRomResetsPpuVideoRegisterState() throws IOException {
        SNES snes = new SNES(new TestRenderer());
        snes.bus.mapComponents(snes);
        snes.bus.write(0x2100, 0x80);
        snes.bus.write(0x2115, 0x80);
        snes.bus.write(0x2116, 0x34);
        snes.bus.write(0x2117, 0x12);

        snes.loadRom(writeGameRom().toString());
        snes.bus.write(0x2118, 0x42);

        assertEquals(0x80, snes.ppu.registers()[0x00]);
        assertEquals(0x00, snes.ppu.registers()[0x15]);
        assertEquals(0x42, snes.ppu.vram.read(0));
        assertEquals(1, snes.ppu.getVramAddressRegister());
    }

    @Test
    void loadRomResetsPpuCgramWriteLatchAndAddress() throws IOException {
        SNES snes = new SNES(new TestRenderer());
        snes.bus.mapComponents(snes);
        snes.bus.write(0x2121, 0x20);
        snes.bus.write(0x2122, 0x12);

        snes.loadRom(writeGameRom().toString());
        snes.bus.write(0x2122, 0x34);
        assertEquals(0, snes.ppu.cgram.read(0));
        assertEquals(0, snes.ppu.ppuRegisters().cgAddress());
        assertEquals(false, snes.ppu.ppuRegisters().isCgLowByte());

        snes.bus.write(0x2122, 0x56);
        assertEquals(0x34, snes.ppu.cgram.read(0));
        assertEquals(0x56, snes.ppu.cgram.read(1));
        assertEquals(1, snes.ppu.ppuRegisters().cgAddress());
        assertEquals(true, snes.ppu.ppuRegisters().isCgLowByte());
    }

    @Test
    void loadRomClearsPpuMemoryState() throws IOException {
        SNES snes = new SNES(new TestRenderer());
        snes.bus.mapComponents(snes);
        snes.ppu.vram.write(0x1234, 0x12);
        snes.ppu.oamram.write(0x0200, 0x34);
        snes.ppu.cgram.write(0x0040, 0x56);

        snes.loadRom(writeGameRom().toString());

        assertEquals(0x00, snes.ppu.vram.read(0x1234));
        assertEquals(0x00, snes.ppu.oamram.read(0x0200));
        assertEquals(0x00, snes.ppu.cgram.read(0x0040));
    }

    @Test
    void loadRomClearsLastTimerIrqPosition() throws IOException {
        SNES snes = new SNES(new TestRenderer());
        snes.bus.mapComponents(snes);
        snes.bus.write(0x4200, 0x10);
        snes.bus.write(0x4207, 0x08);
        snes.bus.write(0x4208, 0x00);

        snes.ppu.update(8);
        snes.updateTimerIrq();
        assertEquals(0x80, snes.bus.read(0x4211));

        snes.loadRom(writeGameRom().toString());
        snes.bus.write(0x4200, 0x10);
        snes.bus.write(0x4207, 0x08);
        snes.bus.write(0x4208, 0x00);
        snes.ppu.advanceCountersOnly(8);
        snes.updateTimerIrq();

        assertEquals(0x80, snes.bus.read(0x4211));
    }

    @Test
    void loadRomClearsDmaAndHdmaEnableState() throws IOException {
        SNES snes = new SNES(new TestRenderer());
        snes.bus.mapComponents(snes);
        snes.bus.write(0x420b, 0x01);
        snes.bus.write(0x420c, 0x01);

        snes.loadRom(writeGameRom().toString());

        assertEquals(0x00, snes.cpu.internalRegisters()[0x0b]);
        assertEquals(0x00, snes.cpu.internalRegisters()[0x0c]);
        assertEquals(false, snes.cpu.dmaChannels()[0].isEnabled());
        assertEquals(false, snes.cpu.dmaChannels()[0].isHdmaEnabled());
    }

    @Test
    void updateRunsCpuPpuAndApuWithoutRenderingBeforeVBlank() {
        TestRenderer renderer = new TestRenderer();
        SNES snes = new SNES(renderer);
        snes.cpu.isDisabled = true;
        snes.apu.isDisabled = true;

        snes.update();

        assertEquals(0, renderer.drawScreenCalls);
        assertEquals(0, renderer.putPixelCalls);
        assertEquals(0xff, snes.ppu.hCounter());
        assertEquals(0, snes.ppu.vCounter());
    }

    @Test
    void updateAdvancesRequestedCyclesWhileCpuWaitsForInterrupt() {
        SNES snes = new SNES(new TestRenderer());
        snes.cartridge.setSize(0x10000);
        snes.cartridge.header.addMappingMode(MappingMode.LOROM);
        snes.sram.setSize(0x10000);
        snes.bus.mapComponents(snes);
        snes.apu.isDisabled = true;
        snes.cpu.registers().setPc(0x0200);
        snes.wram.data()[0x0200] = 0xcb;

        snes.update();
        snes.update();

        assertEquals(24, snes.ppu.hCounter());
        assertEquals(0, snes.ppu.vCounter());
        assertEquals(0x0201, snes.cpu.registers().pc);
    }

    @Test
    void updateAdvancesPpuByInterruptEntryAndHandlerCycles() {
        SNES snes = new SNES(new TestRenderer());
        snes.cartridge.setSize(0x10000);
        snes.cartridge.header.addMappingMode(MappingMode.LOROM);
        snes.sram.setSize(0x10000);
        snes.bus.mapComponents(snes);
        snes.apu.isDisabled = true;
        snes.cartridge.header.emulationInterrupts.nmi = 0x0300;
        snes.wram.data()[0x0300] = 0xea;
        snes.wram.data()[0x0301] = 0xea;
        snes.wram.data()[0x0302] = 0xea;
        snes.cpu.requestNMI();

        snes.update();

        assertEquals(13, snes.ppu.hCounter());
        assertEquals(0x0303, snes.cpu.registers().pc);
    }

    @Test
    void updateDrawsFrameWhenEnteringVBlank() {
        TestRenderer renderer = new TestRenderer();
        SNES snes = new SNES(renderer);
        snes.cpu.isDisabled = true;
        snes.apu.isDisabled = true;
        snes.ppu.advanceCountersOnly(PPU.H_COUNTER_DOTS * PPU.V_BLANK_START_SCANLINE - 0xff);

        snes.update();

        assertEquals(1, renderer.drawScreenCalls);
        assertEquals((long) Background.BUFFER_SIZE * Background.BUFFER_SIZE, renderer.putPixelCalls);
    }

    @Test
    void overscanDelaysFrameAndNmiUntilScanline240() {
        TestRenderer renderer = new TestRenderer();
        SNES snes = new SNES(renderer);
        snes.bus.mapComponents(snes);
        snes.cpu.isDisabled = true;
        snes.apu.isDisabled = true;
        snes.bus.write(0x2133, 0x04);
        snes.bus.write(0x4200, 0x80);
        snes.ppu.advanceCountersOnly(PPU.H_COUNTER_DOTS * PPU.V_BLANK_START_SCANLINE - 0xff);

        snes.update();

        assertEquals(0, renderer.drawScreenCalls);
        assertEquals(0x02, snes.bus.read(0x4210));
        assertEquals(0x00, snes.bus.read(0x4212) & 0x80);

        snes.ppu.advanceCountersOnly(
                PPU.H_COUNTER_DOTS * (PPU.OVERSCAN_V_BLANK_START_SCANLINE - PPU.V_BLANK_START_SCANLINE) - 0xff);
        snes.update();

        assertEquals(1, renderer.drawScreenCalls);
        assertEquals(0x82, snes.bus.read(0x4210));
        assertEquals(0x80, snes.bus.read(0x4212) & 0x80);
    }

    @Test
    void updateRunsOnlyApuForAudioCartridges() throws IOException {
        TestRenderer renderer = new TestRenderer();
        SNES snes = new SNES(renderer);
        Path spc = writeSpcFile();

        snes.loadRom(spc.toString());
        snes.update();

        assertEquals(0, renderer.drawScreenCalls);
        assertEquals(0, renderer.putPixelCalls);
    }

    @Test
    void updateDoesNotCopyJoypadStateBeforeVBlankWhenAutoReadIsEnabled() {
        SNES snes = new SNES(new TestRenderer());
        snes.cpu.isDisabled = true;
        snes.apu.isDisabled = true;
        snes.cpu.internalRegisters()[0x00] = 0x01;
        snes.cpu.internalRegisters()[0x18] = 0x55;
        snes.joypad.setControllerState(0, Joypad.BUTTON_B | Joypad.BUTTON_START | Joypad.BUTTON_A);

        snes.update();

        assertEquals(0x55, snes.cpu.internalRegisters()[0x18]);
    }

    @Test
    void updateCopiesJoypadStateToAutoReadRegistersOnVBlankEntry() {
        SNES snes = new SNES(new TestRenderer());
        snes.cpu.isDisabled = true;
        snes.apu.isDisabled = true;
        snes.cpu.internalRegisters()[0x00] = 0x01;
        snes.joypad.setControllerState(0, Joypad.BUTTON_B | Joypad.BUTTON_START | Joypad.BUTTON_A);
        snes.joypad.setControllerState(1, Joypad.BUTTON_Y | Joypad.BUTTON_L | Joypad.BUTTON_R);
        snes.ppu.advanceCountersOnly(PPU.H_COUNTER_DOTS * PPU.V_BLANK_START_SCANLINE - 0xff);

        snes.update();

        assertEquals(0x09, snes.cpu.internalRegisters()[0x18]);
        assertEquals(0x01, snes.cpu.internalRegisters()[0x19]);
        assertEquals(0x02, snes.cpu.internalRegisters()[0x1a]);
        assertEquals(0x0c, snes.cpu.internalRegisters()[0x1b]);
        assertEquals(0x00, snes.cpu.internalRegisters()[0x1c]);
        assertEquals(0x00, snes.cpu.internalRegisters()[0x1d]);
        assertEquals(0x00, snes.cpu.internalRegisters()[0x1e]);
        assertEquals(0x00, snes.cpu.internalRegisters()[0x1f]);
    }

    @Test
    void updateSetsAutoJoypadBusyStatusWhenAutoReadStartsAtVBlankEntry() {
        SNES snes = new SNES(new TestRenderer());
        snes.bus.mapComponents(snes);
        snes.cpu.isDisabled = true;
        snes.apu.isDisabled = true;
        snes.cpu.internalRegisters()[0x00] = 0x01;
        snes.ppu.advanceCountersOnly(PPU.H_COUNTER_DOTS * PPU.V_BLANK_START_SCANLINE - 0xff);

        snes.update();

        assertEquals(0x81, snes.bus.read(0x4212));
    }

    @Test
    void updateClearsAutoJoypadBusyStatusAfterReadDuration() {
        SNES snes = new SNES(new TestRenderer());
        snes.bus.mapComponents(snes);
        snes.cpu.isDisabled = true;
        snes.apu.isDisabled = true;
        snes.cpu.internalRegisters()[0x00] = 0x01;
        snes.ppu.advanceCountersOnly(PPU.H_COUNTER_DOTS * PPU.V_BLANK_START_SCANLINE - 0xff);

        snes.update();
        for (int i = 0; i < 17; i++) {
            snes.update();
        }

        assertEquals(0x80, snes.bus.read(0x4212));
    }

    @Test
    void updateDoesNotCopyJoypadStateWhenAutoReadIsDisabled() {
        SNES snes = new SNES(new TestRenderer());
        snes.cpu.isDisabled = true;
        snes.apu.isDisabled = true;
        snes.cpu.internalRegisters()[0x18] = 0x55;
        snes.joypad.setControllerState(0, Joypad.BUTTON_B);

        snes.update();

        assertEquals(0x55, snes.cpu.internalRegisters()[0x18]);
    }

    @Test
    void updateDoesNotSetAutoJoypadBusyStatusWhenAutoReadIsDisabled() {
        SNES snes = new SNES(new TestRenderer());
        snes.bus.mapComponents(snes);
        snes.cpu.isDisabled = true;
        snes.apu.isDisabled = true;
        snes.ppu.advanceCountersOnly(PPU.H_COUNTER_DOTS * PPU.V_BLANK_START_SCANLINE - 0xff);

        snes.update();

        assertEquals(0x80, snes.bus.read(0x4212));
    }

    @Test
    void updateDoesNotRequestNmiBeforeVBlankWhenEnabled() {
        SNES snes = new SNES(new TestRenderer());
        snes.bus.mapComponents(snes);
        snes.cpu.isDisabled = true;
        snes.apu.isDisabled = true;
        snes.cpu.internalRegisters()[0x00] = 0x80;

        snes.update();

        assertEquals(0x00, snes.cpu.internalRegisters()[0x10]);
        assertEquals(0x02, snes.bus.read(0x4210));
    }

    @Test
    void updateRequestsNmiOnceWhenEnteringVBlankAndEnabled() {
        SNES snes = new SNES(new TestRenderer());
        snes.bus.mapComponents(snes);
        snes.cpu.isDisabled = true;
        snes.apu.isDisabled = true;
        snes.cpu.internalRegisters()[0x00] = 0x80;
        snes.ppu.advanceCountersOnly(PPU.H_COUNTER_DOTS * PPU.V_BLANK_START_SCANLINE - 0xff);

        snes.update();

        assertEquals(0x80, snes.cpu.internalRegisters()[0x10]);
        assertEquals(0x82, snes.bus.read(0x4210));
        assertEquals(0x02, snes.bus.read(0x4210));

        snes.update();

        assertEquals(0x02, snes.bus.read(0x4210));
    }

    @Test
    void updateRequestsNmiWhenEnabledDuringCurrentVBlank() {
        SNES snes = new SNES(new TestRenderer());
        snes.bus.mapComponents(snes);
        snes.cpu.isDisabled = true;
        snes.apu.isDisabled = true;
        snes.ppu.advanceCountersOnly(PPU.H_COUNTER_DOTS * PPU.V_BLANK_START_SCANLINE + 12);

        snes.update();
        assertEquals(0x02, snes.bus.read(0x4210));

        snes.bus.write(0x4200, 0x80);
        snes.update();

        assertEquals(0x82, snes.bus.read(0x4210));
        assertEquals(0x02, snes.bus.read(0x4210));
    }

    @Test
    void updateDoesNotRequestNmiWhenDisabled() {
        SNES snes = new SNES(new TestRenderer());
        snes.cpu.isDisabled = true;
        snes.apu.isDisabled = true;

        snes.update();

        assertEquals(0x00, snes.cpu.internalRegisters()[0x10]);
    }

    @Test
    void updateVideoStatusRegistersReflectsPpuBlanking() {
        SNES snes = new SNES(new TestRenderer());
        snes.bus.mapComponents(snes);

        snes.ppu.update(256);
        snes.updateVideoStatusRegisters();
        assertEquals(0x40, snes.bus.read(0x4212));

        snes.ppu.update(341 * 225 - 256);
        snes.updateVideoStatusRegisters();
        assertEquals(0x80, snes.bus.read(0x4212));

        snes.ppu.update(341 * (262 - 225));
        snes.updateVideoStatusRegisters();
        assertEquals(0x00, snes.bus.read(0x4212));
    }

    @Test
    void updateTimerIrqRequestsIrqWhenEnabledTimerMatchesCounters() {
        SNES snes = new SNES(new TestRenderer());
        snes.bus.mapComponents(snes);
        snes.bus.write(0x4200, 0x30);
        snes.bus.write(0x4207, 0x0c);
        snes.bus.write(0x4208, 0x00);
        snes.bus.write(0x4209, 0x02);
        snes.bus.write(0x420a, 0x00);

        snes.ppu.update(341 * 2 + 12);
        snes.updateTimerIrq();

        assertEquals(0x80, snes.bus.read(0x4211));
    }

    @Test
    void updateTimerIrqRequestsIrqWhenUpdateCrossesHTimerCounter() {
        SNES snes = new SNES(new TestRenderer());
        snes.bus.mapComponents(snes);
        snes.cpu.isDisabled = true;
        snes.apu.isDisabled = true;
        snes.bus.write(0x4200, 0x10);
        snes.bus.write(0x4207, 0x08);
        snes.bus.write(0x4208, 0x00);

        snes.update();

        assertEquals(0x80, snes.bus.read(0x4211));
    }

    @Test
    void updateTimerIrqDoesNotReassertAtSameCounterPosition() {
        SNES snes = new SNES(new TestRenderer());
        snes.bus.mapComponents(snes);
        snes.bus.write(0x4200, 0x10);
        snes.bus.write(0x4207, 0x08);
        snes.bus.write(0x4208, 0x00);

        snes.ppu.update(8);
        snes.updateTimerIrq();
        assertEquals(0x80, snes.bus.read(0x4211));

        snes.updateTimerIrq();
        assertEquals(0x00, snes.bus.read(0x4211));
    }

    @Test
    void updateTimerIrqCanReassertAfterTimersAreDisabledAndReenabled() {
        SNES snes = new SNES(new TestRenderer());
        snes.bus.mapComponents(snes);
        snes.bus.write(0x4200, 0x10);
        snes.bus.write(0x4207, 0x08);
        snes.bus.write(0x4208, 0x00);

        snes.ppu.update(8);
        snes.updateTimerIrq();
        assertEquals(0x80, snes.bus.read(0x4211));

        snes.bus.write(0x4200, 0x00);
        snes.updateTimerIrq();
        snes.bus.write(0x4200, 0x10);
        snes.updateTimerIrq();

        assertEquals(0x80, snes.bus.read(0x4211));
    }

    @Test
    void updateTimerIrqCanReassertWhenTimerEnableMaskChangesBetweenChecks() {
        SNES snes = new SNES(new TestRenderer());
        snes.bus.mapComponents(snes);
        snes.bus.write(0x4200, 0x10);
        snes.bus.write(0x4207, 0x08);
        snes.bus.write(0x4208, 0x00);

        snes.ppu.update(8);
        snes.updateTimerIrq();
        assertEquals(0x80, snes.bus.read(0x4211));

        snes.bus.write(0x4200, 0x00);
        snes.bus.write(0x4200, 0x10);
        snes.updateTimerIrq();

        assertEquals(0x80, snes.bus.read(0x4211));
    }

    @Test
    void updateInitializesAndRunsHdmaWhenEnteringHBlank() {
        SNES snes = new SNES(new TestRenderer());
        snes.bus.mapComponents(snes);
        snes.cpu.isDisabled = true;
        snes.apu.isDisabled = true;
        snes.wram.data()[0x0200] = 0x01;
        snes.wram.data()[0x0201] = 0x12;
        snes.wram.data()[0x0202] = 0x34;
        snes.wram.data()[0x0203] = 0x00;

        snes.bus.write(0x2121, 0x20);
        setupHdma(snes, DMA.TWO_TO_ONE, 0x22, 0x7e0200);
        snes.bus.write(0x420c, 0x01);

        snes.update();

        assertEquals(0x12, snes.ppu.cgram.read(0x40));
        assertEquals(0x34, snes.ppu.cgram.read(0x41));
        assertEquals(287, snes.ppu.hCounter());
        assertEquals(0, snes.ppu.vCounter());
        assertEquals(0, snes.cpu.internalRegisters()[0x0b]);
        assertEquals(0x01, snes.cpu.internalRegisters()[0x0c]);
        assertEquals(0x40, snes.bus.read(0x4212));
    }

    @Test
    void updatePreservesDisplayBrightnessFromBeforeEachScanlineHdmaTransfer() {
        TestRenderer renderer = new TestRenderer();
        SNES snes = new SNES(renderer);
        snes.bus.mapComponents(snes);
        snes.cpu.isDisabled = true;
        snes.apu.isDisabled = true;
        snes.ppu.cgram.write(0, 0x1f);
        snes.ppu.cgram.write(1, 0x00);
        snes.bus.write(0x2100, 0x0f);
        snes.wram.data()[0x0200] = 0x01;
        snes.wram.data()[0x0201] = 0x07;
        snes.wram.data()[0x0202] = 0x00;
        setupHdma(snes, DMA.ONE_TO_ONE, 0x00, 0x7e0200);
        snes.bus.write(0x420c, 0x01);

        for (int updates = 0; renderer.drawScreenCalls == 0 && updates < 400; updates++) {
            snes.update();
        }

        assertEquals(1, renderer.drawScreenCalls);
        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), renderer.firstScanlinePixel);
        assertEquals(PPUUtils.cgramColorToRGBA(0x000e), renderer.secondScanlinePixel);
    }

    @Test
    void updateSpreadsNormalDmaAcrossHblankAccessWindow() {
        SNES snes = new SNES(new TestRenderer());
        snes.bus.mapComponents(snes);
        snes.apu.isDisabled = true;
        snes.cpu.registers().setPc(0x0200);
        snes.wram.data()[0x0200] = 0xea;
        snes.wram.data()[0x0201] = 0xea;
        snes.wram.data()[0x0040] = 0x11;
        snes.wram.data()[0x0041] = 0x22;
        snes.wram.data()[0x0042] = 0x33;
        snes.wram.data()[0x0043] = 0x44;
        snes.ppu.advanceCountersOnly(250);
        snes.bus.write(0x2121, 0x00);
        setupDma(snes, DMA.TWO_TO_ONE, 0x22, 0x7e0040, 4);
        snes.bus.write(0x420b, 0x01);

        snes.update();

        assertEquals(266, snes.ppu.hCounter());
        assertEquals(0x00, snes.ppu.cgram.read(0));
        assertEquals(true, snes.cpu.dmaChannels()[0].isEnabled());

        snes.update();

        assertEquals(0x11, snes.ppu.cgram.read(0));
        assertEquals(0x22, snes.ppu.cgram.read(1));
        assertEquals(0x00, snes.ppu.cgram.read(2));
        assertEquals(true, snes.cpu.dmaChannels()[0].isEnabled());

        snes.update();

        assertEquals(0x33, snes.ppu.cgram.read(2));
        assertEquals(0x44, snes.ppu.cgram.read(3));
        assertEquals(false, snes.cpu.dmaChannels()[0].isEnabled());
        assertEquals(0x00, snes.cpu.internalRegisters()[0x0b]);
    }

    @Test
    void loadRomClearsSmcOffsetBeforeLoadingAudioCartridge() throws IOException {
        SNES snes = new SNES(new TestRenderer());

        snes.loadRom(writeHeaderedGameRomWithSram().toString());
        assertEquals(0x8000, snes.sram.getSize());
        snes.loadRom(writeSpcFile().toString());

        assertEquals(CartridgeType.AUDIO, snes.cartridge.getType());
        assertEquals(0, snes.cartridge.header.sramSize);
        assertEquals(0, snes.sram.getSize());
        assertEquals(0x1234, snes.apu.internalRegisters().pc);
    }

    @Test
    void loadRomScoresSmcHeaderResetOpcodeFromRomPayload() throws IOException {
        SNES snes = new SNES(new TestRenderer());
        byte[] rom = headeredGameRomBytes("JAMSNES SMC RESET", 0x00);
        rom[0] = 0x00;
        rom[0x200] = 0x78;
        Path romPath = tempDir.resolve("game-smc-reset.sfc");
        Files.write(romPath, rom);

        snes.loadRom(romPath.toString());

        assertEquals(0x8000, snes.cpu.registers().pc);
        assertEquals(0x78, snes.cartridge.read(0));
        assertEquals(0x8000, snes.cartridge.getSize());
    }

    @Test
    void loadRomScoresHiromResetOpcodeFromBankZeroMirror() throws IOException {
        SNES snes = new SNES(new TestRenderer());
        byte[] rom = ambiguousLoHiRomBytes();
        Path romPath = tempDir.resolve("game-hirom-reset.sfc");
        Files.write(romPath, rom);

        snes.loadRom(romPath.toString());

        assertEquals(true, snes.cartridge.header.hasMappingMode(MappingMode.HIROM));
        assertEquals(false, snes.cartridge.header.hasMappingMode(MappingMode.LOROM));
        assertEquals(0x8000, snes.cpu.registers().pc);
        assertEquals(0x5c, snes.bus.read(0x008000));
    }

    private Path writeGameRom() throws IOException {
        byte[] rom = gameRomBytes("JAMSNES TEST ROM", 0x00);
        Path romPath = tempDir.resolve("game.sfc");
        Files.write(romPath, rom);
        return romPath;
    }

    private Path writeGameRomWithSram() throws IOException {
        byte[] rom = gameRomBytes("JAMSNES SRAM ROM", 0x05);
        Path romPath = tempDir.resolve("game-sram.sfc");
        Files.write(romPath, rom);
        return romPath;
    }

    private Path writeHeaderedGameRomWithSram() throws IOException {
        byte[] rom = headeredGameRomBytes("JAMSNES SMC SRAM", 0x05);
        Path romPath = tempDir.resolve("game-smc-sram.sfc");
        Files.write(romPath, rom);
        return romPath;
    }

    private byte[] gameRomBytes(String title, int sramSizeByte) {
        byte[] rom = new byte[0x8000];
        int base = 0x7f00;
        rom[0] = 0x78;
        byte[] name = title.getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(name, 0, rom, 0x7fc0, name.length);
        rom[base + 0xd5] = 0x20;
        rom[base + 0xd6] = 0x00;
        rom[base + 0xd7] = 0x05;
        rom[base + 0xd8] = (byte) sramSizeByte;
        rom[base + 0xfc] = 0x00;
        rom[base + 0xfd] = (byte) 0x80;
        return rom;
    }

    private byte[] headeredGameRomBytes(String title, int sramSizeByte) {
        byte[] rom = new byte[0x8200];
        int base = 0x8100;
        rom[0] = 0x78;
        rom[0x200] = 0x78;
        byte[] name = title.getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(name, 0, rom, 0x81c0, name.length);
        rom[base + 0xd5] = 0x20;
        rom[base + 0xd6] = 0x00;
        rom[base + 0xd7] = 0x05;
        rom[base + 0xd8] = (byte) sramSizeByte;
        rom[base + 0xfc] = 0x00;
        rom[base + 0xfd] = (byte) 0x80;
        return rom;
    }

    private byte[] ambiguousLoHiRomBytes() {
        byte[] rom = new byte[0x10000];
        rom[0x0000] = 0x00;
        rom[0x0001] = 0x78;
        rom[0x8000] = 0x5c;
        writeRomHeader(rom, 0x7f00, 0x20, "JAMSNES LOROM TIE", 0x01, 0x80, 0, 0);
        writeRomHeader(rom, 0xff00, 0x21, "JAMSNES HIROM WIN", 0x00, 0x80, 0x1234, 0xedcb);
        return rom;
    }

    private void writeRomHeader(byte[] rom, int base, int mode, String title, int resetLow, int resetHigh,
                                int checksumComplement, int checksum) {
        byte[] name = title.getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(name, 0, rom, base + 0xc0, name.length);
        rom[base + 0xd5] = (byte) mode;
        rom[base + 0xd6] = 0x00;
        rom[base + 0xd7] = 0x06;
        rom[base + 0xd8] = 0x00;
        rom[base + 0xdc] = (byte) checksumComplement;
        rom[base + 0xdd] = (byte) (checksumComplement >>> 8);
        rom[base + 0xde] = (byte) checksum;
        rom[base + 0xdf] = (byte) (checksum >>> 8);
        rom[base + 0xfc] = (byte) resetLow;
        rom[base + 0xfd] = (byte) resetHigh;
    }

    private Path writeSpcFile() throws IOException {
        byte[] spc = new byte[0x101c0];
        byte[] magic = "SNES-SPC700 Sound File Data v0.30".getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(magic, 0, spc, 0, magic.length);
        spc[0x21] = 0x1a;
        spc[0x22] = 0x1a;
        spc[0x23] = 0x1a;
        spc[0x24] = 0x1e;
        spc[0x25] = 0x34;
        spc[0x26] = 0x12;
        Path spcPath = tempDir.resolve("audio.spc");
        Files.write(spcPath, spc);
        return spcPath;
    }

    private static void setupHdma(SNES snes, int control, int port, int tableAddress) {
        snes.bus.write(0x4300, control);
        snes.bus.write(0x4301, port);
        snes.bus.write(0x4302, tableAddress);
        snes.bus.write(0x4303, tableAddress >>> 8);
        snes.bus.write(0x4304, tableAddress >>> 16);
    }

    private static void setupDma(SNES snes, int control, int port, int address, int count) {
        setupHdma(snes, control, port, address);
        snes.bus.write(0x4305, count);
        snes.bus.write(0x4306, count >>> 8);
    }

    private static final class TestRenderer implements IRenderer {
        private long putPixelCalls;
        private int drawScreenCalls;
        private int firstScanlinePixel;
        private int secondScanlinePixel;

        @Override
        public void setWindowName(String newWindowName) {
        }

        @Override
        public void drawScreen() {
            drawScreenCalls++;
        }

        @Override
        public void putPixel(int y, int x, int rgba) {
            putPixelCalls++;
            if (x == 0 && y == 0) {
                firstScanlinePixel = rgba;
            } else if (x == 0 && y == 1) {
                secondScanlinePixel = rgba;
            }
        }

        @Override
        public void createWindow(SNES snes, int maxFPS) {
        }

        @Override
        public void playAudio(short[] samples) {
        }
    }
}
