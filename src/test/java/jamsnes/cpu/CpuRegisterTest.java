package jamsnes.cpu;

import jamsnes.SNES;
import jamsnes.exceptions.InvalidAddress;
import jamsnes.memory.IMemory;
import jamsnes.memory.MemoryShadow;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CpuRegisterTest {
    @Test
    void multiplyRegistersUpdateResultWhenMultiplierBIsWritten() {
        SNES snes = init();

        snes.bus.write(0x4202, 0x12);
        snes.bus.write(0x4203, 0x34);

        assertEquals(0xa8, snes.bus.read(0x4216));
        assertEquals(0x03, snes.bus.read(0x4217));
    }

    @Test
    void divideRegistersUpdateQuotientAndRemainderWhenDivisorIsWritten() {
        SNES snes = init();

        snes.bus.write(0x4204, 0x34);
        snes.bus.write(0x4205, 0x12);
        snes.bus.write(0x4206, 0x11);

        assertEquals(0x12, snes.bus.read(0x4214));
        assertEquals(0x01, snes.bus.read(0x4215));
        assertEquals(0x02, snes.bus.read(0x4216));
        assertEquals(0x00, snes.bus.read(0x4217));
    }

    @Test
    void divideByZeroProducesAllOnesQuotientAndDividendRemainder() {
        SNES snes = init();

        snes.bus.write(0x4204, 0xcd);
        snes.bus.write(0x4205, 0xab);
        snes.bus.write(0x4206, 0x00);

        assertEquals(0xff, snes.bus.read(0x4214));
        assertEquals(0xff, snes.bus.read(0x4215));
        assertEquals(0xcd, snes.bus.read(0x4216));
        assertEquals(0xab, snes.bus.read(0x4217));
    }

    @Test
    void rdnmiReadClearsNmiStatusAndPendingRequest() {
        SNES snes = init();

        snes.cpu.requestNMI();

        assertEquals(0x80, snes.bus.read(0x4210));
        assertEquals(0x00, snes.bus.read(0x4210));
        assertFalse(snes.cpu.isNMIRequested);
    }

    @Test
    void timeupReadClearsIrqStatusAndPendingRequest() {
        SNES snes = init();

        snes.cpu.requestIRQ();

        assertEquals(0x80, snes.bus.read(0x4211));
        assertEquals(0x00, snes.bus.read(0x4211));
        assertFalse(snes.cpu.isIRQRequested);
    }

    @Test
    void returnsCpuRegisterValueNames() {
        SNES snes = init();

        assertEquals("NMITIMEN", snes.cpu.getValueName(0x00));
        assertEquals("WRMPYA", snes.cpu.getValueName(0x02));
        assertEquals("MDMAEN", snes.cpu.getValueName(0x0b));
        assertEquals("RDDIVL", snes.cpu.getValueName(0x14));
        assertEquals("JOY4H", snes.cpu.getValueName(0x1f));
        assertEquals("???", snes.cpu.getValueName(0x20));
    }

    @Test
    void returnsDmaRegisterValueNames() {
        SNES snes = init();

        assertEquals("DMAP0", snes.cpu.getValueName(0x100));
        assertEquals("BBAD0", snes.cpu.getValueName(0x101));
        assertEquals("A1T3H", snes.cpu.getValueName(0x133));
        assertEquals("DAS7L", snes.cpu.getValueName(0x175));
        assertEquals("NTRL7", snes.cpu.getValueName(0x17a));
        assertEquals("???", snes.cpu.getValueName(0x17f));
    }

    @Test
    void unmappedCpuInternalRegisterReadsAndWritesThrow() {
        SNES snes = init();

        assertThrows(InvalidAddress.class, () -> snes.bus.read(0x420e));
        assertThrows(InvalidAddress.class, () -> snes.bus.write(0x420f, 0x12));
        assertThrows(InvalidAddress.class, () -> snes.bus.read(0x4220));
        assertThrows(InvalidAddress.class, () -> snes.bus.write(0x4400, 0x34));
    }

    @Test
    void mirroredUnmappedCpuInternalRegistersAlsoThrow() {
        SNES snes = init();

        assertThrows(InvalidAddress.class, () -> snes.bus.read(0x80420e));
        assertThrows(InvalidAddress.class, () -> snes.bus.write(0x80420f, 0x56));
    }

    @Test
    void cpuMirrorForwardsRegisterValueNames() {
        SNES snes = init();

        IMemory accessor = snes.bus.getAccessor(0x804214);
        MemoryShadow shadow = assertInstanceOf(MemoryShadow.class, accessor);

        assertEquals("RDDIVL", shadow.getValueName(0x14));
    }

    private static SNES init() {
        SNES snes = new SNES(new NoRenderer(0, 0, 0));
        snes.bus.mapComponents(snes);
        return snes;
    }
}
