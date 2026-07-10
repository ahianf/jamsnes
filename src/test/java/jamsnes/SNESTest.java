package jamsnes;

import jamsnes.cartridge.CartridgeType;
import jamsnes.cpu.DMA;
import jamsnes.input.Joypad;
import jamsnes.ppu.Background;
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
    void updateRunsCpuPpuAndApuForGameCartridges() {
        TestRenderer renderer = new TestRenderer();
        SNES snes = new SNES(renderer);
        snes.cpu.isDisabled = true;
        snes.apu.isDisabled = true;

        snes.update();

        assertEquals(1, renderer.drawScreenCalls);
        assertEquals((long) Background.BUFFER_SIZE * Background.BUFFER_SIZE, renderer.putPixelCalls);
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
    void updateCopiesJoypadStateToAutoReadRegistersWhenEnabled() {
        SNES snes = new SNES(new TestRenderer());
        snes.cpu.isDisabled = true;
        snes.apu.isDisabled = true;
        snes.cpu.internalRegisters()[0x00] = 0x01;
        snes.joypad.setControllerState(0, Joypad.BUTTON_B | Joypad.BUTTON_START | Joypad.BUTTON_A);
        snes.joypad.setControllerState(1, Joypad.BUTTON_Y | Joypad.BUTTON_L | Joypad.BUTTON_R);

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
    void updateRequestsNmiForNextFrameWhenEnabled() {
        SNES snes = new SNES(new TestRenderer());
        snes.bus.mapComponents(snes);
        snes.cpu.isDisabled = true;
        snes.apu.isDisabled = true;
        snes.cpu.internalRegisters()[0x00] = 0x80;

        snes.update();

        assertEquals(0x80, snes.cpu.internalRegisters()[0x10]);
        assertEquals(0x80, snes.bus.read(0x4210));
        assertEquals(0x00, snes.bus.read(0x4210));
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

        assertEquals(0x12, snes.ppu.cgram.read(0x20));
        assertEquals(0x34, snes.ppu.cgram.read(0x21));
        assertEquals(287, snes.ppu.hCounter());
        assertEquals(0, snes.ppu.vCounter());
        assertEquals(0, snes.bus.read(0x420b));
        assertEquals(0x01, snes.bus.read(0x420c));
        assertEquals(0x40, snes.bus.read(0x4212));
    }

    @Test
    void loadRomClearsSmcOffsetBeforeLoadingAudioCartridge() throws IOException {
        SNES snes = new SNES(new TestRenderer());

        snes.loadRom(writeHeaderedGameRom().toString());
        snes.loadRom(writeSpcFile().toString());

        assertEquals(CartridgeType.AUDIO, snes.cartridge.getType());
        assertEquals(0x1234, snes.apu.internalRegisters().pc);
    }

    private Path writeGameRom() throws IOException {
        byte[] rom = new byte[0x8000];
        int base = 0x7f00;
        rom[0] = 0x78;
        byte[] name = "JAMSNES TEST ROM".getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(name, 0, rom, 0x7fc0, name.length);
        rom[base + 0xd5] = 0x20;
        rom[base + 0xd6] = 0x00;
        rom[base + 0xd7] = 0x05;
        rom[base + 0xd8] = 0x00;
        rom[base + 0xfc] = 0x00;
        rom[base + 0xfd] = (byte) 0x80;
        Path romPath = tempDir.resolve("game.sfc");
        Files.write(romPath, rom);
        return romPath;
    }

    private Path writeHeaderedGameRom() throws IOException {
        byte[] rom = new byte[0x8200];
        int base = 0x8100;
        rom[0] = 0x78;
        rom[0x200] = 0x78;
        byte[] name = "JAMSNES SMC ROM".getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(name, 0, rom, 0x81c0, name.length);
        rom[base + 0xd5] = 0x20;
        rom[base + 0xd6] = 0x00;
        rom[base + 0xd7] = 0x05;
        rom[base + 0xd8] = 0x00;
        rom[base + 0xfc] = 0x00;
        rom[base + 0xfd] = (byte) 0x80;
        Path romPath = tempDir.resolve("game-smc.sfc");
        Files.write(romPath, rom);
        return romPath;
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

    private static final class TestRenderer implements IRenderer {
        private long putPixelCalls;
        private int drawScreenCalls;

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
        }

        @Override
        public void createWindow(SNES snes, int maxFPS) {
        }

        @Override
        public void playAudio(short[] samples) {
        }
    }
}
