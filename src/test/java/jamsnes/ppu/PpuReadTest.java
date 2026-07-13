package jamsnes.ppu;

import jamsnes.SNES;
import jamsnes.exceptions.InvalidAddress;
import jamsnes.memory.IMemory;
import jamsnes.memory.MemoryShadow;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PpuReadTest {
    @Test
    void vramDataReadReturnsBufferedLowAndHighBytes() {
        SNES snes = init();
        snes.ppu.vram.write(0, 0b1111_1111);
        snes.ppu.vram.write(1, 0b1111_1111);

        snes.bus.write(0x2115, 0b1000_0000);
        snes.bus.write(0x2116, 0);
        snes.bus.write(0x2117, 0);

        assertEquals(0b1111_1111, snes.bus.read(0x2139));
        assertEquals(0b1111_1111, snes.bus.read(0x213a));
        assertEquals(1, snes.ppu.getVramAddressRegister());
    }

    @Test
    void vramDataReadRefreshesBufferAfterHighByteIncrement() {
        SNES snes = init();
        snes.ppu.vram.write(0, 0x12);
        snes.ppu.vram.write(1, 0x34);
        snes.ppu.vram.write(2, 0x56);
        snes.ppu.vram.write(3, 0x78);

        snes.bus.write(0x2115, 0b1000_0000);
        snes.bus.write(0x2116, 0);
        snes.bus.write(0x2117, 0);

        assertEquals(0x12, snes.bus.read(0x2139));
        assertEquals(0x34, snes.bus.read(0x213a));
        assertEquals(1, snes.ppu.getVramAddressRegister());
        assertEquals(0x56, snes.bus.read(0x2139));
    }

    @Test
    void vramDataReadWorksWithDefaultIncrementMode() {
        SNES snes = init();
        snes.ppu.vram.write(0, 0b0110_1001);
        snes.ppu.vram.write(1, 0b1111_1111);

        snes.bus.write(0x2116, 0);
        snes.bus.write(0x2117, 0);

        assertEquals(0b0110_1001, snes.bus.read(0x2139));
        assertEquals(0b1111_1111, snes.bus.read(0x213a));
        assertEquals(1, snes.ppu.getVramAddressRegister());
    }

    @Test
    void cgramDataReadReturnsCurrentAddressAndIncrements() {
        SNES snes = init();
        snes.ppu.cgram.write(0x40, 0x12);
        snes.ppu.cgram.write(0x41, 0x34);

        snes.bus.write(0x2121, 0x20);

        assertEquals(0x12, snes.bus.read(0x213b));
        assertEquals(0x20, snes.ppu.ppuRegisters().cgAddress());
        assertEquals(0x34, snes.bus.read(0x213b));
        assertEquals(0x21, snes.ppu.ppuRegisters().cgAddress());
    }

    @Test
    void cgramDataReadMasksHighColorBit() {
        SNES snes = init();
        snes.ppu.cgram.write(0x40, 0xff);
        snes.ppu.cgram.write(0x41, 0xff);

        snes.bus.write(0x2121, 0x20);

        assertEquals(0xff, snes.bus.read(0x213b));
        assertEquals(0x7f, snes.bus.read(0x213b));
        assertEquals(0x21, snes.ppu.ppuRegisters().cgAddress());
    }

    @Test
    void oamDataReadReturnsCurrentAddressAndIncrements() {
        SNES snes = init();
        snes.bus.write(0x2102, 0x0b);
        snes.bus.write(0x2103, 0x80);
        snes.ppu.oamram.write(0x0b, 0x42);
        snes.ppu.oamram.write(0x0c, 0x24);

        assertEquals(0x42, snes.bus.read(0x2138));
        assertEquals(0x0c, snes.ppu.ppuRegisters().oamAddress());
        assertEquals(0x24, snes.bus.read(0x2138));
        assertEquals(0x0d, snes.ppu.ppuRegisters().oamAddress());
    }

    @Test
    void oamDataReadMapsUpperAddressRangeToHighTable() {
        SNES snes = init();
        snes.bus.write(0x2102, 0x21);
        snes.bus.write(0x2103, 0x01);
        snes.ppu.oamram.write(0x201, 0x77);
        snes.ppu.oamram.write(0x121, 0x55);

        assertEquals(0x77, snes.bus.read(0x2138));
        assertEquals(0x122, snes.ppu.ppuRegisters().oamAddress());
    }

    @Test
    void mode7MultiplicationResultReadsLowMiddleAndHighBytes() {
        SNES snes = init();
        writeMode7Register(snes, 0x211b, 0x0100);
        writeMode7Register(snes, 0x211c, 0x0200);

        assertEquals(0x00, snes.bus.read(0x2134));
        assertEquals(0x02, snes.bus.read(0x2135));
        assertEquals(0x00, snes.bus.read(0x2136));
    }

    @Test
    void mode7MultiplicationResultReadsSignedProduct() {
        SNES snes = init();
        writeMode7Register(snes, 0x211b, 0xff00);
        writeMode7Register(snes, 0x211c, 0x0200);

        assertEquals(0x00, snes.bus.read(0x2134));
        assertEquals(0xfe, snes.bus.read(0x2135));
        assertEquals(0xff, snes.bus.read(0x2136));
    }

    @Test
    void counterAndStatusRegistersLatchAndReturnPpuVersionBits() {
        SNES snes = init();

        snes.ppu.update(600);
        snes.ppu.registers()[0x37] = 0xab;

        assertEquals(0xab, snes.bus.read(0x2137));
        assertEquals(0x03, snes.bus.read(0x213c));
        assertEquals(0x01, snes.bus.read(0x213c));
        assertEquals(0x01, snes.bus.read(0x213d));
        assertEquals(0x00, snes.bus.read(0x213d));
        assertEquals(0x01, snes.bus.read(0x213e));
        assertEquals(0x43, snes.bus.read(0x213f));
        assertEquals(0x03, snes.bus.read(0x213f));
    }

    @Test
    void ppuWriteOnlyRegisterBusReadsUseOpenBus() {
        SNES snes = init();
        snes.bus.setOpenBus(0x5a);

        assertEquals(0x5a, snes.bus.read(0x2100));
        assertEquals(0x5a, snes.bus.read(0x2133));
        assertEquals(0x5a, snes.bus.read(0x802100));
        assertEquals(0x5a, snes.bus.getOpenBus());
    }

    @Test
    void unsupportedDirectPpuReadRegisterThrows() {
        SNES snes = init();

        assertThrows(InvalidAddress.class, () -> snes.ppu.read(0x00));
        assertThrows(InvalidAddress.class, () -> snes.ppu.read(0x40));
    }

    @Test
    void returnsPpuRegisterValueNames() {
        SNES snes = init();

        assertEquals("INIDISP", snes.ppu.getValueName(0x00));
        assertEquals("OAMDDH", snes.ppu.getValueName(0x03));
        assertEquals("M7A", snes.ppu.getValueName(0x1b));
        assertEquals("CGDATAREAD", snes.ppu.getValueName(0x3b));
        assertEquals("STAT78", snes.ppu.getValueName(0x3f));
        assertEquals("???", snes.ppu.getValueName(0x40));
    }

    @Test
    void ppuMirrorForwardsRegisterValueNames() {
        SNES snes = init();

        IMemory accessor = snes.bus.getAccessor(0x80213f);
        MemoryShadow shadow = assertInstanceOf(MemoryShadow.class, accessor);

        assertEquals("STAT78", shadow.getValueName(0x3f));
    }

    private static void writeMode7Register(SNES snes, int address, int value) {
        snes.bus.write(address, value >>> 8);
        snes.bus.write(address, value);
    }

    private static SNES init() {
        SNES snes = new SNES(new NoRenderer(0, 0, 0));
        snes.bus.mapComponents(snes);
        return snes;
    }
}
