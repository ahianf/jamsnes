package jamsnes.cpu;

import jamsnes.SNES;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private static SNES init() {
        SNES snes = new SNES(new NoRenderer(0, 0, 0));
        snes.bus.mapComponents(snes);
        return snes;
    }
}
