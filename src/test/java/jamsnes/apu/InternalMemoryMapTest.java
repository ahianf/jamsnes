package jamsnes.apu;

import jamsnes.SNES;
import jamsnes.exceptions.InvalidAddress;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InternalMemoryMapTest {
    @Test
    void internalReadUsesApuMemoryRegionsAndRegisters() {
        SNES snes = init();
        snes.apu._internalWrite(0x0010, 123);
        snes.apu._internalWrite(0x0142, 45);
        snes.apu._internalWrite(0xfedc, 67);
        snes.apu._internalWrite(0xffdf, 89);
        snes.apu._internalWrite(0x00f4, 0xaa);
        snes.apu._internalWrite(0x00f8, 0xbb);
        snes.apu.counters()[0] = 0xcc;

        assertEquals(123, snes.apu._internalRead(0x0010));
        assertEquals(45, snes.apu._internalRead(0x0142));
        assertEquals(67, snes.apu._internalRead(0xfedc));
        assertEquals(89, snes.apu._internalRead(0xffdf));
        assertEquals(0xaa, snes.apu._internalRead(0x00f4));
        assertEquals(0xbb, snes.apu._internalRead(0x00f8));
        assertEquals(0xcc, snes.apu._internalRead(0x00fd));
    }

    @Test
    void internalWriteUsesApuMemoryRegionsAndRegisters() {
        SNES snes = init();

        snes.apu._internalWrite(0x0001, 12);
        snes.apu._internalWrite(0x01ff, 23);
        snes.apu._internalWrite(0x0789, 34);
        snes.apu._internalWrite(0xfff0, 45);
        snes.apu._internalWrite(0x00f5, 56);
        snes.apu._internalWrite(0x00f9, 67);

        assertEquals(12, snes.apu._internalRead(0x0001));
        assertEquals(23, snes.apu._internalRead(0x01ff));
        assertEquals(34, snes.apu._internalRead(0x0789));
        assertEquals(45, snes.apu._internalRead(0xfff0));
        assertEquals(56, snes.apu.ports()[1]);
        assertEquals(67, snes.apu._internalRead(0x00f9));
    }

    @Test
    void invalidInternalReadsAndWritesThrow() {
        SNES snes = init();

        assertThrows(InvalidAddress.class, () -> snes.apu._internalRead(0x00f1));
        assertThrows(InvalidAddress.class, () -> snes.apu._internalRead(0x10000));
        assertThrows(InvalidAddress.class, () -> snes.apu._internalWrite(0x00fd, 123));
        assertThrows(InvalidAddress.class, () -> snes.apu._internalWrite(0x10000, 123));
    }

    @Test
    void externalReadWriteOnlyExposeFourPorts() {
        SNES snes = init();

        snes.apu.write(0x03, 123);

        assertEquals(123, snes.apu.read(0x03));
        assertThrows(InvalidAddress.class, () -> snes.apu.read(0x04));
        assertThrows(InvalidAddress.class, () -> snes.apu.write(0x04, 123));
    }

    private static SNES init() {
        return new SNES(new NoRenderer(0, 0, 0));
    }
}
