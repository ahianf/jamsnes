package jamsnes.apu;

import jamsnes.SNES;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArithmeticInstructionTest {
    @Test
    void adcAddsMemoryOperandsAndAccumulator() {
        SNES snes = init();
        snes.apu.internalRegisters().x = 4;
        snes.apu.internalRegisters().y = 7;
        snes.apu.internalRegisters().c = true;
        snes.apu._internalWrite(4, 53);
        snes.apu._internalWrite(7, 76);

        assertEquals(5, snes.apu.ADC(snes.apu._getIndexXAddr(), snes.apu._getIndexYAddr(), 5));
        assertEquals(130, snes.apu._internalRead(4));
        assertFalse(snes.apu.internalRegisters().c);
        assertTrue(snes.apu.internalRegisters().h);
        assertTrue(snes.apu.internalRegisters().v);

        snes.apu.internalRegisters().a = 53;
        snes.apu.internalRegisters().c = true;
        assertEquals(3, snes.apu.ADCacc(snes.apu._getIndexYAddr(), 3));
        assertEquals(130, snes.apu.internalRegisters().a);
        assertFalse(snes.apu.internalRegisters().c);
        assertTrue(snes.apu.internalRegisters().h);
        assertTrue(snes.apu.internalRegisters().v);
    }

    @Test
    void sbcSubtractsMemoryOperandsAndAccumulator() {
        SNES snes = init();
        snes.apu.internalRegisters().x = 4;
        snes.apu.internalRegisters().y = 7;
        snes.apu.internalRegisters().c = true;
        snes.apu._internalWrite(4, 67);
        snes.apu._internalWrite(7, 45);

        assertEquals(5, snes.apu.SBC(snes.apu._getIndexXAddr(), snes.apu._getIndexYAddr(), 5));
        assertEquals(22, snes.apu._internalRead(4));
        assertTrue(snes.apu.internalRegisters().c);
        assertFalse(snes.apu.internalRegisters().h);
        assertFalse(snes.apu.internalRegisters().v);

        snes.apu.internalRegisters().a = 67;
        snes.apu.internalRegisters().c = true;
        assertEquals(3, snes.apu.SBCacc(snes.apu._getIndexYAddr(), 3));
        assertEquals(22, snes.apu.internalRegisters().a);
        assertTrue(snes.apu.internalRegisters().c);
        assertFalse(snes.apu.internalRegisters().h);
        assertFalse(snes.apu.internalRegisters().v);
    }

    @Test
    void cmpUpdatesCarryAndNzFlags() {
        SNES snes = init();
        snes.apu.internalRegisters().x = 4;
        snes.apu.internalRegisters().y = 7;
        snes.apu._internalWrite(4, 67);
        snes.apu._internalWrite(7, 45);

        assertEquals(5, snes.apu.CMP(snes.apu._getIndexXAddr(), snes.apu._getIndexYAddr(), 5));
        assertTrue(snes.apu.internalRegisters().c);

        snes.apu.internalRegisters().a = 67;
        assertEquals(3, snes.apu.CMPreg("a", snes.apu._getIndexYAddr(), 3));
        assertTrue(snes.apu.internalRegisters().c);
    }

    private static SNES init() {
        return new SNES(new NoRenderer(0, 0, 0));
    }
}
