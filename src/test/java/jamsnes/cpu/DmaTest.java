package jamsnes.cpu;

import jamsnes.SNES;
import jamsnes.cartridge.MappingMode;
import jamsnes.ppu.PPU;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DmaTest {
    @Test
    void dmaRegistersRoundTripAndEnableChannel() {
        SNES snes = init();
        DMA dma = snes.cpu.dmaChannels()[0];

        snes.bus.write(0x4301, 0x18);
        assertEquals(0x18, snes.bus.read(0x4301));
        assertEquals(0x18, dma.getPort());

        snes.bus.write(0x4304, 0x13);
        snes.bus.write(0x4303, 0xbe);
        snes.bus.write(0x4302, 0x00);
        assertEquals(0x13be00, dma.getAAddress());

        snes.bus.write(0x4306, 0x08);
        snes.bus.write(0x4305, 0x00);
        assertEquals(0x0800, dma.getCount());

        snes.bus.write(0x4300, DMA.TWO_TO_TWO);
        assertEquals(0, dma.getDirection());
        assertFalse(dma.isIncrement());
        assertFalse(dma.isFixed());
        assertEquals(DMA.TWO_TO_TWO, dma.getMode());
        assertFalse(dma.isEnabled());

        snes.bus.write(0x420b, 0x01);
        assertTrue(dma.isEnabled());
        assertEquals(0x01, snes.cpu.internalRegisters()[0x0b]);
    }

    @Test
    void reverseDmaToMappedRomIsIgnoredWithoutAbortingTheTransfer() {
        SNES snes = init();
        snes.cartridge.setSize(0x8000);
        snes.cartridge.header.addMappingMode(MappingMode.LOROM);
        snes.bus.mapComponents(snes);
        DMA dma = snes.cpu.dmaChannels()[0];
        snes.cartridge.data()[5] = 0x34;
        snes.apu._internalWrite(0x00f4, 0x5a);
        snes.bus.write(0x4300, 0x80 | DMA.ONE_TO_ONE);
        snes.bus.write(0x4301, 0x40);
        snes.bus.write(0x4302, 0x05);
        snes.bus.write(0x4303, 0x80);
        snes.bus.write(0x4304, 0x80);
        snes.bus.write(0x4305, 0x01);
        snes.bus.write(0x4306, 0x00);
        snes.bus.write(0x420b, 0x01);

        assertEquals(16, dma.run(16));

        assertFalse(dma.isEnabled());
        assertEquals(0x34, snes.cartridge.data()[5]);
        assertEquals(0x808006, dma.getAAddress());
        assertEquals(0, dma.getCount());
    }

    @Test
    void hdmaBookkeepingRegistersRoundTrip() {
        SNES snes = init();
        DMA dma = snes.cpu.dmaChannels()[0];

        snes.bus.write(0x4307, 0x7e);
        snes.bus.write(0x4308, 0x34);
        snes.bus.write(0x4309, 0x12);
        snes.bus.write(0x430a, 0x80);

        assertEquals(0x7e, snes.bus.read(0x4307));
        assertEquals(0x34, snes.bus.read(0x4308));
        assertEquals(0x12, snes.bus.read(0x4309));
        assertEquals(0x80, snes.bus.read(0x430a));
        assertEquals(0x7e, dma.getIndirectBank());
        assertEquals(0x1234, dma.getTableAddress());
        assertEquals(0x80, dma.getLineCounter());
    }

    @Test
    void dmaUnusedRegisterMirrorsAt43xf() {
        SNES snes = init();

        snes.bus.write(0x430b, 0x34);

        assertEquals(0x34, snes.bus.read(0x430b));
        assertEquals(0x34, snes.bus.read(0x430f));

        snes.bus.write(0x430f, 0xab);

        assertEquals(0xab, snes.bus.read(0x430b));
        assertEquals(0xab, snes.bus.read(0x430f));
    }

    @Test
    void dmaUnusedMirrorIsPerChannelAndMirroredByBank() {
        SNES snes = init();

        snes.bus.write(0x431b, 0x12);
        snes.bus.write(0x80431f, 0x56);

        assertEquals(0x00, snes.bus.read(0x430b));
        assertEquals(0x56, snes.bus.read(0x431b));
        assertEquals(0x56, snes.bus.read(0x80431f));
    }

    @Test
    void dmaUnusedRegisterHolesReadOpenBusAndIgnoreWrites() {
        SNES snes = init();
        snes.bus.setOpenBus(0x5a);

        snes.bus.write(0x430c, 0x11);
        snes.bus.write(0x430d, 0x22);
        snes.bus.write(0x430e, 0x33);

        assertEquals(0x5a, snes.bus.read(0x430c));
        assertEquals(0x5a, snes.bus.read(0x430d));
        assertEquals(0x5a, snes.bus.read(0x430e));
    }

    @Test
    void hdmaEnableRegisterControlsSeparateHdmaState() {
        SNES snes = init();
        DMA dma = snes.cpu.dmaChannels()[0];

        snes.bus.write(0x420c, 0x01);

        assertEquals(0x01, snes.cpu.internalRegisters()[0x0c]);
        assertTrue(dma.isHdmaEnabled());
        assertFalse(dma.isEnabled());

        snes.bus.write(0x420c, 0x00);

        assertFalse(dma.isHdmaEnabled());
    }

    @Test
    void directHdmaTransfersFirstLineThenSkipsUntilNextDescriptor() {
        SNES snes = init();
        DMA dma = snes.cpu.dmaChannels()[0];

        snes.wram.data()[0x0200] = 0x02;
        snes.wram.data()[0x0201] = 0x12;
        snes.wram.data()[0x0202] = 0x34;
        snes.wram.data()[0x0203] = 0x00;

        snes.bus.write(0x2121, 0x20);
        setupHdma(snes, DMA.TWO_TO_ONE, 0x22, 0x7e0200);
        snes.bus.write(0x420c, 0x01);

        assertEquals(8, snes.cpu.initializeHDMA());
        enterHBlank(snes);
        assertEquals(16, snes.cpu.runHDMALine());
        assertEquals(0x12, snes.ppu.cgram.read(0x40));
        assertEquals(0x34, snes.ppu.cgram.read(0x41));
        assertEquals(0x01, dma.getLineCounter());
        assertTrue(dma.isHdmaEnabled());

        assertEquals(8, snes.cpu.runHDMALine());
        assertEquals(0x0204, dma.getTableAddress());
        assertTrue(dma.isHdmaEnabled());
        assertFalse(dma.isHdmaActive());
    }

    @Test
    void lineCounterRegisterWriteChangesActiveHdmaCountdown() {
        SNES snes = init();
        DMA dma = snes.cpu.dmaChannels()[0];

        snes.wram.data()[0x0200] = 0x03;
        snes.wram.data()[0x0201] = 0x5a;
        snes.wram.data()[0x0202] = 0x00;

        setupHdma(snes, DMA.ONE_TO_ONE, 0x26, 0x7e0200);
        snes.bus.write(0x420c, 0x01);

        assertEquals(8, snes.cpu.initializeHDMA());
        assertEquals(8, snes.cpu.runHDMALine());
        assertEquals(0x02, dma.getLineCounter());
        assertTrue(dma.isHdmaActive());

        snes.bus.write(0x430a, 0x01);

        assertEquals(8, snes.cpu.runHDMALine());
        assertEquals(0x0203, dma.getTableAddress());
        assertFalse(dma.isHdmaActive());
    }

    @Test
    void repeatedDirectHdmaTransfersEachLine() {
        SNES snes = init();
        DMA dma = snes.cpu.dmaChannels()[0];

        snes.wram.data()[0x0200] = 0x82;
        snes.wram.data()[0x0201] = 0x12;
        snes.wram.data()[0x0202] = 0x34;
        snes.wram.data()[0x0203] = 0x56;
        snes.wram.data()[0x0204] = 0x78;
        snes.wram.data()[0x0205] = 0x00;

        snes.bus.write(0x2121, 0x20);
        setupHdma(snes, DMA.TWO_TO_ONE, 0x22, 0x7e0200);
        snes.bus.write(0x420c, 0x01);

        assertEquals(8, snes.cpu.initializeHDMA());
        enterHBlank(snes);
        assertEquals(16, snes.cpu.runHDMALine());
        assertEquals(0x81, dma.getLineCounter());
        assertEquals(16 + 8, snes.cpu.runHDMALine());

        assertEquals(0x12, snes.ppu.cgram.read(0x40));
        assertEquals(0x34, snes.ppu.cgram.read(0x41));
        assertEquals(0x56, snes.ppu.cgram.read(0x42));
        assertEquals(0x78, snes.ppu.cgram.read(0x43));
        assertEquals(0x0206, dma.getTableAddress());
        assertTrue(dma.isHdmaEnabled());
        assertFalse(dma.isHdmaActive());
    }

    @Test
    void lineCount80TransfersOnceAcrossOneHundredTwentyEightLines() {
        SNES snes = init();
        DMA dma = snes.cpu.dmaChannels()[0];

        snes.wram.data()[0x0200] = 0x80;
        snes.wram.data()[0x0201] = 0x5a;
        snes.wram.data()[0x0202] = 0x00;

        setupHdma(snes, DMA.ONE_TO_ONE, 0x26, 0x7e0200);
        snes.bus.write(0x420c, 0x01);

        assertEquals(8, snes.cpu.initializeHDMA());
        assertEquals(8, snes.cpu.runHDMALine());
        assertEquals(0x5a, snes.ppu.registers()[0x26]);
        assertEquals(0x7f, dma.getLineCounter());
        assertEquals(0x0202, dma.getTableAddress());

        snes.bus.write(0x2126, 0x33);
        for (int i = 0; i < 126; i++) {
            assertEquals(0, snes.cpu.runHDMALine());
            assertEquals(0x33, snes.ppu.registers()[0x26]);
            assertTrue(dma.isHdmaEnabled());
            assertTrue(dma.isHdmaActive());
        }

        assertEquals(0x01, dma.getLineCounter());
        assertEquals(8, snes.cpu.runHDMALine());

        assertEquals(0x33, snes.ppu.registers()[0x26]);
        assertEquals(0x0203, dma.getTableAddress());
        assertTrue(dma.isHdmaEnabled());
        assertFalse(dma.isHdmaActive());
    }

    @Test
    void indirectHdmaReadsDataFromIndirectBankAndAdvancesPointer() {
        SNES snes = init();
        DMA dma = snes.cpu.dmaChannels()[0];

        snes.wram.data()[0x0200] = 0x01;
        snes.wram.data()[0x0201] = 0x00;
        snes.wram.data()[0x0202] = 0x04;
        snes.wram.data()[0x0203] = 0x00;
        snes.wram.data()[0x0400] = 0xab;
        snes.wram.data()[0x0401] = 0xcd;

        snes.bus.write(0x2121, 0x20);
        setupHdma(snes, 0x40 | DMA.TWO_TO_ONE, 0x22, 0x7e0200);
        snes.bus.write(0x4307, 0x7e);
        snes.bus.write(0x420c, 0x01);

        assertEquals(24, snes.cpu.initializeHDMA());
        enterHBlank(snes);
        assertEquals(16 + 8, snes.cpu.runHDMALine());

        assertEquals(0xab, snes.ppu.cgram.read(0x40));
        assertEquals(0x4d, snes.ppu.cgram.read(0x41));
        assertEquals(0x0402, dma.getCount());
        assertEquals(0x0204, dma.getTableAddress());
        assertTrue(dma.isHdmaEnabled());
        assertFalse(dma.isHdmaActive());
    }

    @Test
    void multipleHdmaChannelsInitializeAndTransferInChannelOrder() {
        SNES snes = init();
        DMA first = snes.cpu.dmaChannels()[0];
        DMA second = snes.cpu.dmaChannels()[1];

        snes.wram.data()[0x0200] = 0x01;
        snes.wram.data()[0x0201] = 0x12;
        snes.wram.data()[0x0202] = 0x34;
        snes.wram.data()[0x0203] = 0x00;
        snes.wram.data()[0x0300] = 0x01;
        snes.wram.data()[0x0301] = 0x8f;
        snes.wram.data()[0x0302] = 0x00;

        snes.bus.write(0x2121, 0x20);
        setupHdmaChannel(snes, 0, DMA.TWO_TO_ONE, 0x22, 0x7e0200);
        setupHdmaChannel(snes, 1, DMA.ONE_TO_ONE, 0x00, 0x7e0300);
        snes.bus.write(0x420c, 0x03);

        assertEquals(16, snes.cpu.initializeHDMA());
        enterHBlank(snes);
        assertEquals(40, snes.cpu.runHDMALine());

        assertEquals(0x12, snes.ppu.cgram.read(0x40));
        assertEquals(0x34, snes.ppu.cgram.read(0x41));
        assertEquals(0x8f, snes.ppu.registers()[0x00]);
        assertEquals(0x0204, first.getTableAddress());
        assertEquals(0x0303, second.getTableAddress());
        assertTrue(first.isHdmaEnabled());
        assertTrue(second.isHdmaEnabled());
        assertFalse(first.isHdmaActive());
        assertFalse(second.isHdmaActive());
    }

    @Test
    void allHdmaChannelsTransferBeforeAnyChannelReloadsItsDescriptor() {
        SNES snes = init();

        snes.wram.data()[0x0200] = 0x01;
        snes.wram.data()[0x0201] = 0x5a;
        snes.wram.data()[0x0202] = 0x00;
        snes.wram.data()[0x0300] = 0x01;
        snes.wram.data()[0x0301] = 0x00;
        snes.wram.data()[0x0302] = 0x21;
        snes.wram.data()[0x0303] = 0x00;

        setupHdmaChannel(snes, 0, DMA.ONE_TO_ONE, 0x25, 0x7e0200);
        setupHdmaChannel(snes, 1, 0x40 | DMA.ONE_TO_ONE, 0x26, 0x7e0300);
        snes.bus.write(0x4317, 0x00);
        snes.bus.write(0x420c, 0x03);

        assertEquals(32, snes.cpu.initializeHDMA());
        assertEquals(32, snes.cpu.runHDMALine());

        assertEquals(0x5a, snes.ppu.registers()[0x25]);
        assertEquals(0x5a, snes.ppu.registers()[0x26],
                "Later channels should see the transfer bus value before descriptor reloads");
    }

    @Test
    void hdmaModeFiveTransfersFourBytesToAlternatingRegisters() {
        SNES snes = init();
        DMA dma = snes.cpu.dmaChannels()[0];

        snes.wram.data()[0x0200] = 0x01;
        snes.wram.data()[0x0201] = 0x11;
        snes.wram.data()[0x0202] = 0x22;
        snes.wram.data()[0x0203] = 0x33;
        snes.wram.data()[0x0204] = 0x44;
        snes.wram.data()[0x0205] = 0x00;

        setupHdma(snes, DMA.TWO_TO_TWO_BIS, 0x26, 0x7e0200);
        snes.bus.write(0x420c, 0x01);

        assertEquals(8, snes.cpu.initializeHDMA());
        enterHBlank(snes);
        assertEquals(40, snes.cpu.runHDMALine());

        assertEquals(0x33, snes.ppu.registers()[0x26]);
        assertEquals(0x44, snes.ppu.registers()[0x27]);
        assertEquals(0x0206, dma.getTableAddress());
        assertTrue(dma.isHdmaEnabled());
        assertFalse(dma.isHdmaActive());
    }

    @Test
    void hdmaDirectionBitCopiesFromBBusToATable() {
        SNES snes = init();
        DMA dma = snes.cpu.dmaChannels()[0];

        snes.wram.data()[0x0200] = 0x01;
        snes.wram.data()[0x0201] = 0x5a;
        snes.wram.data()[0x0202] = 0x00;
        snes.bus.write(0x211b, 0x08);
        snes.bus.write(0x211b, 0x00);
        snes.bus.write(0x211c, 0x00);
        snes.bus.write(0x211c, 0x02);

        setupHdma(snes, 0x80 | DMA.ONE_TO_ONE, 0x34, 0x7e0200);
        snes.bus.write(0x420c, 0x01);

        assertEquals(8, snes.cpu.initializeHDMA());
        enterHBlank(snes);
        assertEquals(16, snes.cpu.runHDMALine());

        assertEquals(0x10, snes.wram.data()[0x0201]);
        assertEquals(0x0203, dma.getTableAddress());
        assertTrue(dma.isHdmaEnabled());
        assertFalse(dma.isHdmaActive());
    }

    @Test
    void terminatedHdmaChannelRestartsFromConfiguredTableOnNextFrame() {
        SNES snes = init();
        DMA dma = snes.cpu.dmaChannels()[0];

        snes.wram.data()[0x0200] = 0x01;
        snes.wram.data()[0x0201] = 0x5a;
        snes.wram.data()[0x0202] = 0x00;

        setupHdma(snes, DMA.ONE_TO_ONE, 0x26, 0x7e0200);
        snes.bus.write(0x420c, 0x01);

        assertEquals(8, snes.cpu.initializeHDMA());
        enterHBlank(snes);
        assertEquals(16, snes.cpu.runHDMALine());
        assertTrue(dma.isHdmaEnabled());
        assertFalse(dma.isHdmaActive());
        assertEquals(0, snes.cpu.runHDMALine());

        snes.bus.write(0x2126, 0x00);

        assertEquals(8, snes.cpu.initializeHDMA());
        assertTrue(dma.isHdmaActive());
        assertEquals(16, snes.cpu.runHDMALine());

        assertEquals(0x5a, snes.ppu.registers()[0x26]);
        assertEquals(0x0203, dma.getTableAddress());
        assertTrue(dma.isHdmaEnabled());
        assertFalse(dma.isHdmaActive());
    }

    @Test
    void vramWriteIncrementsAfterLowByteByDefault() {
        SNES snes = init();

        snes.bus.write(0x2100, 0x80);
        snes.bus.write(0x2117, 0x20);
        snes.bus.write(0x2116, 0x00);
        for (int i = 0; i < 8; i++) {
            snes.bus.write(0x2119, i >>> 8);
            snes.bus.write(0x2118, i);
            assertEquals(0x2001 + i, snes.ppu.getVramAddressRegister());
        }
        for (int i = 0; i < 8; i++) {
            int value = snes.ppu.vram.data()[0x2000 * 2 + i * 2] | (snes.ppu.vram.data()[0x2000 * 2 + i * 2 + 1] << 8);
            assertEquals(i, value);
        }
    }

    @Test
    void vramWriteCanIncrementAfterHighByte() {
        SNES snes = init();

        snes.bus.write(0x2100, 0x80);
        snes.bus.write(0x2115, 0b1000_0000);
        snes.bus.write(0x2117, 0x20);
        snes.bus.write(0x2116, 0x00);
        for (int i = 0; i < 8; i++) {
            snes.bus.write(0x2118, i);
            snes.bus.write(0x2119, i >>> 8);
            assertEquals(0x2001 + i, snes.ppu.getVramAddressRegister());
        }
        for (int i = 0; i < 8; i++) {
            int value = snes.ppu.vram.data()[0x2000 * 2 + i * 2] | (snes.ppu.vram.data()[0x2000 * 2 + i * 2 + 1] << 8);
            assertEquals(i, value);
        }
    }

    @Test
    void wramToVramDmaTransfersBytes() {
        SNES snes = init();
        int[] source = {0x34, 0x12, 0x78, 0x56};
        for (int i = 0; i < source.length; i++) {
            snes.wram.data()[i] = source[i];
        }

        snes.bus.write(0x2100, 0x80);
        snes.bus.write(0x2115, 0b1000_0000);
        snes.bus.write(0x2117, 0x00);
        snes.bus.write(0x2116, 0x00);

        snes.bus.write(0x4301, 0x18);
        snes.bus.write(0x4304, 0x7e);
        snes.bus.write(0x4303, 0x00);
        snes.bus.write(0x4302, 0x00);
        snes.bus.write(0x4306, 0x00);
        snes.bus.write(0x4305, source.length);
        snes.bus.write(0x4300, DMA.TWO_TO_TWO);
        snes.bus.write(0x420b, 0x01);

        DMA dma = snes.cpu.dmaChannels()[0];
        int cycles = dma.run(1_000_000);

        assertEquals(8 + 8 * source.length, cycles);
        assertEquals(0, dma.getCount());
        assertEquals(0x7e0004, dma.getAAddress());
        assertEquals(0x02, snes.ppu.getVramAddressRegister());
        assertEquals(0x34, snes.ppu.vram.data()[0]);
        assertEquals(0x12, snes.ppu.vram.data()[1]);
        assertEquals(0x78, snes.ppu.vram.data()[2]);
        assertEquals(0x56, snes.ppu.vram.data()[3]);
        assertFalse(dma.isEnabled());
    }

    @Test
    void dmaResumesWithinCycleBudgetWithoutRestartingModePhase() {
        SNES snes = init();
        snes.wram.data()[0x40] = 0x11;
        snes.wram.data()[0x41] = 0x22;
        snes.wram.data()[0x42] = 0x33;
        snes.wram.data()[0x43] = 0x44;
        setupDma(snes, DMA.TWO_TO_TWO, 0x26, 0x7e0040, 0x0004);
        snes.bus.write(0x420b, 0x01);

        DMA dma = snes.cpu.dmaChannels()[0];
        assertEquals(8, dma.run(8));
        assertEquals(0x0004, dma.getCount());
        assertEquals(0x00, snes.ppu.registers()[0x26]);
        assertEquals(0x00, snes.ppu.registers()[0x27]);
        assertTrue(dma.isEnabled());

        assertEquals(8, dma.run(8));
        assertEquals(0x11, snes.ppu.registers()[0x26]);
        assertEquals(0x00, snes.ppu.registers()[0x27]);

        assertEquals(8, dma.run(8));
        assertEquals(0x11, snes.ppu.registers()[0x26]);
        assertEquals(0x22, snes.ppu.registers()[0x27]);

        assertEquals(16, dma.run(16));
        assertEquals(0x33, snes.ppu.registers()[0x26]);
        assertEquals(0x44, snes.ppu.registers()[0x27]);
        assertEquals(0, dma.getCount());
        assertEquals(0x7e0044, dma.getAAddress());
        assertFalse(dma.isEnabled());
    }

    @Test
    void wramToOamDmaCommitsDuringForcedBlank() {
        SNES snes = init();
        snes.wram.data()[0x40] = 0x12;
        snes.wram.data()[0x41] = 0x34;

        snes.bus.write(0x2100, 0x80);
        snes.bus.write(0x2102, 0x00);
        snes.bus.write(0x2103, 0x00);
        setupDma(snes, DMA.ONE_TO_ONE, 0x04, 0x7e0040, 0x0002);
        snes.bus.write(0x420b, 0x01);

        DMA dma = snes.cpu.dmaChannels()[0];
        int cycles = dma.run(1_000_000);

        assertEquals(8 + 8 * 2, cycles);
        assertEquals(0x12, snes.ppu.oamram.read(0x00));
        assertEquals(0x34, snes.ppu.oamram.read(0x01));
        assertEquals(0x02, snes.ppu.ppuRegisters().oamAddress());
        assertEquals(0x7e0042, dma.getAAddress());
        assertFalse(dma.isEnabled());
    }

    @Test
    void wramToOamDmaIsSkippedDuringActiveDisplayButStillIncrementsOamAddress() {
        SNES snes = init();
        snes.wram.data()[0x40] = 0x12;
        snes.wram.data()[0x41] = 0x34;

        snes.bus.write(0x2102, 0x00);
        snes.bus.write(0x2103, 0x00);
        setupDma(snes, DMA.ONE_TO_ONE, 0x04, 0x7e0040, 0x0002);
        snes.bus.write(0x420b, 0x01);

        DMA dma = snes.cpu.dmaChannels()[0];
        int cycles = dma.run(1_000_000);

        assertEquals(8 + 8 * 2, cycles);
        assertEquals(0x00, snes.ppu.oamram.read(0x00));
        assertEquals(0x00, snes.ppu.oamram.read(0x01));
        assertEquals(0x02, snes.ppu.ppuRegisters().oamAddress());
        assertEquals(0x7e0042, dma.getAAddress());
        assertFalse(dma.isEnabled());
    }

    @Test
    void bBusToABusDmaTransfersBytesAndIncrementsAAddress() {
        SNES snes = init();
        snes.ppu.cgram.write(0x40, 0x12);
        snes.ppu.cgram.write(0x41, 0x34);
        snes.bus.write(0x2121, 0x20);

        snes.bus.write(0x4301, 0x3b);
        snes.bus.write(0x4304, 0x7e);
        snes.bus.write(0x4303, 0x00);
        snes.bus.write(0x4302, 0x20);
        snes.bus.write(0x4306, 0x00);
        snes.bus.write(0x4305, 0x02);
        snes.bus.write(0x4300, 0x80 | DMA.ONE_TO_ONE);
        snes.bus.write(0x420b, 0x01);

        DMA dma = snes.cpu.dmaChannels()[0];
        int cycles = dma.run(1_000_000);

        assertEquals(8 + 8 * 2, cycles);
        assertEquals(0x12, snes.wram.data()[0x20]);
        assertEquals(0x34, snes.wram.data()[0x21]);
        assertEquals(0x7e0022, dma.getAAddress());
        assertEquals(0, dma.getCount());
        assertFalse(dma.isEnabled());
    }

    @Test
    void fixedAddressDmaDoesNotChangeAAddress() {
        SNES snes = init();
        snes.wram.data()[0x40] = 0x12;
        snes.bus.write(0x2100, 0x80);
        snes.bus.write(0x2121, 0x20);

        snes.bus.write(0x4301, 0x22);
        snes.bus.write(0x4304, 0x7e);
        snes.bus.write(0x4303, 0x00);
        snes.bus.write(0x4302, 0x40);
        snes.bus.write(0x4306, 0x00);
        snes.bus.write(0x4305, 0x03);
        snes.bus.write(0x4300, 0x08 | DMA.ONE_TO_ONE);
        snes.bus.write(0x420b, 0x01);

        DMA dma = snes.cpu.dmaChannels()[0];
        int cycles = dma.run(1_000_000);

        assertEquals(8 + 8 * 3, cycles);
        assertEquals(0x7e0040, dma.getAAddress());
        assertEquals(0x12, snes.ppu.cgram.read(0x40));
        assertEquals(0x12, snes.ppu.cgram.read(0x41));
    }

    @Test
    void decrementAddressDmaMovesBackwardWithinSourceBank() {
        SNES snes = init();
        snes.wram.data()[0x51] = 0x22;
        snes.wram.data()[0x52] = 0x33;
        snes.bus.write(0x2100, 0x80);
        snes.bus.write(0x2121, 0x20);

        snes.bus.write(0x4301, 0x22);
        snes.bus.write(0x4304, 0x7e);
        snes.bus.write(0x4303, 0x00);
        snes.bus.write(0x4302, 0x52);
        snes.bus.write(0x4306, 0x00);
        snes.bus.write(0x4305, 0x02);
        snes.bus.write(0x4300, 0x10 | DMA.ONE_TO_ONE);
        snes.bus.write(0x420b, 0x01);

        DMA dma = snes.cpu.dmaChannels()[0];
        int cycles = dma.run(1_000_000);

        assertEquals(8 + 8 * 2, cycles);
        assertEquals(0x7e0050, dma.getAAddress());
        assertEquals(0x33, snes.ppu.cgram.read(0x40));
        assertEquals(0x22, snes.ppu.cgram.read(0x41));
    }

    @Test
    void wramDataPortDmaSpecialCasesAvoidWramBusConflict() {
        SNES snes = init();
        snes.wram.data()[0x60] = 0x44;
        snes.bus.write(0x2181, 0x20);

        snes.bus.write(0x4301, 0x80);
        snes.bus.write(0x4304, 0x7e);
        snes.bus.write(0x4303, 0x00);
        snes.bus.write(0x4302, 0x60);
        snes.bus.write(0x4306, 0x00);
        snes.bus.write(0x4305, 0x01);
        snes.bus.write(0x4300, DMA.ONE_TO_ONE);
        snes.bus.write(0x420b, 0x01);

        DMA dma = snes.cpu.dmaChannels()[0];
        assertEquals(8 + 8, dma.run(1_000_000));
        assertEquals(0x44, snes.wram.data()[0x60]);
        assertEquals(0x20, snes.wramPort.address());

        snes.bus.write(0x4301, 0x80);
        snes.bus.write(0x4304, 0x7e);
        snes.bus.write(0x4303, 0x00);
        snes.bus.write(0x4302, 0x60);
        snes.bus.write(0x4306, 0x00);
        snes.bus.write(0x4305, 0x01);
        snes.bus.write(0x4300, 0x80 | DMA.ONE_TO_ONE);
        snes.bus.write(0x420b, 0x01);
        snes.bus.setOpenBus(0x5a);

        assertEquals(8 + 4, dma.run(1_000_000));
        assertEquals(0x5a, snes.wram.data()[0x60]);
        assertEquals(0x20, snes.wramPort.address());
    }

    @Test
    void dmaReadsExternalOpenBusInsteadOfPpuRegisterThroughABus() {
        SNES snes = init();
        snes.bus.write(0x2100, 0x80);
        snes.ppu.cgram.write(0x40, 0x34);
        snes.bus.write(0x2121, 0x20);
        setupDma(snes, DMA.ONE_TO_ONE, 0x26, 0x00213b, 0x0001);
        snes.bus.write(0x420b, 0x01);
        snes.bus.setOpenBus(0x5a);
        assertEquals(0x52, snes.bus.read(0x4210));

        DMA dma = snes.cpu.dmaChannels()[0];
        assertEquals(16, dma.run(1_000_000));

        assertEquals(0x5a, snes.ppu.registers()[0x26]);
        assertEquals(0x20, snes.ppu.ppuRegisters().cgAddress());
        assertEquals(0x00213c, dma.getAAddress());
        assertEquals(0, dma.getCount());
    }

    @Test
    void dmaIgnoresABusWritesToPpuRegisters() {
        SNES snes = init();
        snes.bus.write(0x2100, 0x80);
        snes.ppu.cgram.write(0x40, 0x34);
        snes.bus.write(0x2121, 0x20);
        setupDma(snes, 0x80 | DMA.ONE_TO_ONE, 0x3b, 0x002100, 0x0001);
        snes.bus.write(0x420b, 0x01);

        DMA dma = snes.cpu.dmaChannels()[0];
        assertEquals(16, dma.run(1_000_000));

        assertEquals(0x80, snes.ppu.registers()[0x00]);
        assertEquals(0x002101, dma.getAAddress());
        assertEquals(0, dma.getCount());
    }

    private static SNES init() {
        SNES snes = new SNES(new NoRenderer(0, 0, 0));
        snes.bus.mapComponents(snes);
        return snes;
    }

    private static void enterHBlank(SNES snes) {
        snes.ppu.advanceCountersOnly(PPU.H_BLANK_START_DOT);
    }

    private static void setupHdma(SNES snes, int control, int port, int tableAddress) {
        setupHdmaChannel(snes, 0, control, port, tableAddress);
    }

    private static void setupDma(SNES snes, int control, int port, int aAddress, int count) {
        snes.bus.write(0x4301, port);
        snes.bus.write(0x4304, aAddress >>> 16);
        snes.bus.write(0x4303, aAddress >>> 8);
        snes.bus.write(0x4302, aAddress);
        snes.bus.write(0x4306, count >>> 8);
        snes.bus.write(0x4305, count);
        snes.bus.write(0x4300, control);
    }

    private static void setupHdmaChannel(SNES snes, int channel, int control, int port, int tableAddress) {
        int base = 0x4300 + channel * 0x10;
        snes.bus.write(base, control);
        snes.bus.write(base + 0x01, port);
        snes.bus.write(base + 0x02, tableAddress);
        snes.bus.write(base + 0x03, tableAddress >>> 8);
        snes.bus.write(base + 0x04, tableAddress >>> 16);
    }
}
