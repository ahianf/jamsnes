package jamsnes.cpu;

import jamsnes.SNES;
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
        assertEquals(0x01, snes.bus.read(0x420b));
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
    void hdmaEnableRegisterControlsSeparateHdmaState() {
        SNES snes = init();
        DMA dma = snes.cpu.dmaChannels()[0];

        snes.bus.write(0x420c, 0x01);

        assertEquals(0x01, snes.bus.read(0x420c));
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
        assertEquals(16, snes.cpu.runHDMALine());
        assertEquals(0x12, snes.ppu.cgram.read(0x20));
        assertEquals(0x34, snes.ppu.cgram.read(0x21));
        assertEquals(0x01, dma.getLineCounter());
        assertTrue(dma.isHdmaEnabled());

        assertEquals(8, snes.cpu.runHDMALine());
        assertEquals(0x0204, dma.getTableAddress());
        assertFalse(dma.isHdmaEnabled());
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
        assertEquals(16, snes.cpu.runHDMALine());
        assertEquals(0x81, dma.getLineCounter());
        assertEquals(16 + 8, snes.cpu.runHDMALine());

        assertEquals(0x12, snes.ppu.cgram.read(0x20));
        assertEquals(0x34, snes.ppu.cgram.read(0x21));
        assertEquals(0x56, snes.ppu.cgram.read(0x22));
        assertEquals(0x78, snes.ppu.cgram.read(0x23));
        assertEquals(0x0206, dma.getTableAddress());
        assertFalse(dma.isHdmaEnabled());
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
        assertEquals(16 + 8, snes.cpu.runHDMALine());

        assertEquals(0xab, snes.ppu.cgram.read(0x20));
        assertEquals(0xcd, snes.ppu.cgram.read(0x21));
        assertEquals(0x0402, dma.getCount());
        assertEquals(0x0204, dma.getTableAddress());
        assertFalse(dma.isHdmaEnabled());
    }

    @Test
    void vramWriteIncrementsAfterLowByteByDefault() {
        SNES snes = init();

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
    void bBusToABusDmaTransfersBytesAndIncrementsAAddress() {
        SNES snes = init();
        snes.ppu.cgram.write(0x20, 0x12);
        snes.ppu.cgram.write(0x21, 0x34);
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
        assertEquals(0x12, snes.ppu.cgram.read(0x20));
        assertEquals(0x12, snes.ppu.cgram.read(0x21));
    }

    @Test
    void decrementAddressDmaMovesBackwardWithinSourceBank() {
        SNES snes = init();
        snes.wram.data()[0x51] = 0x22;
        snes.wram.data()[0x52] = 0x33;
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
        assertEquals(0x33, snes.ppu.cgram.read(0x20));
        assertEquals(0x22, snes.ppu.cgram.read(0x21));
    }

    @Test
    void wramDataPortDmaSpecialCasesAvoidWramBusConflict() {
        SNES snes = init();
        snes.wram.data()[0x60] = 0x44;

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

        snes.bus.write(0x4301, 0x80);
        snes.bus.write(0x4304, 0x7e);
        snes.bus.write(0x4303, 0x00);
        snes.bus.write(0x4302, 0x60);
        snes.bus.write(0x4306, 0x00);
        snes.bus.write(0x4305, 0x01);
        snes.bus.write(0x4300, 0x80 | DMA.ONE_TO_ONE);
        snes.bus.write(0x420b, 0x01);

        assertEquals(8 + 4, dma.run(1_000_000));
        assertEquals(0xff, snes.wram.data()[0x60]);
    }

    private static SNES init() {
        SNES snes = new SNES(new NoRenderer(0, 0, 0));
        snes.bus.mapComponents(snes);
        return snes;
    }

    private static void setupHdma(SNES snes, int control, int port, int tableAddress) {
        snes.bus.write(0x4300, control);
        snes.bus.write(0x4301, port);
        snes.bus.write(0x4302, tableAddress);
        snes.bus.write(0x4303, tableAddress >>> 8);
        snes.bus.write(0x4304, tableAddress >>> 16);
    }
}
