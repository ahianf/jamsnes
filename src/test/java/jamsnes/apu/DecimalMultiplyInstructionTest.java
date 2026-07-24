package jamsnes.apu;

import jamsnes.SNES;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecimalMultiplyInstructionTest {
    @Test
    void decimalAdjustsAccumulatorAfterAddAndSubtract() {
        SNES snes = init();

        snes.apu.internalRegisters().c = true;
        snes.apu.internalRegisters().h = true;
        snes.apu.internalRegisters().a = 0x1a;
        assertEquals(3, snes.apu.DAA());
        assertEquals(0x80, snes.apu.internalRegisters().a);

        snes.apu.internalRegisters().c = false;
        snes.apu.internalRegisters().h = false;
        snes.apu.internalRegisters().a = 0xff;
        assertEquals(3, snes.apu.DAS());
        assertEquals(0x99, snes.apu.internalRegisters().a);
    }

    @Test
    void multiplyStoresProductInYaAndSetsFlagsFromHighProductByte() {
        SNES snes = init();

        snes.apu.internalRegisters().a = 10;
        snes.apu.internalRegisters().y = 23;

        assertEquals(9, snes.apu.MUL());
        assertEquals(230, snes.apu.internalRegisters().ya());
        assertFalse(snes.apu.internalRegisters().n);
        assertTrue(snes.apu.internalRegisters().z);

        snes.apu.internalRegisters().a = 0xff;
        snes.apu.internalRegisters().y = 0xff;
        assertEquals(9, snes.apu.MUL());
        assertEquals(0xfe01, snes.apu.internalRegisters().ya());
        assertTrue(snes.apu.internalRegisters().n);
        assertFalse(snes.apu.internalRegisters().z);
    }

    @Test
    void divideStoresQuotientAndRemainder() {
        SNES snes = init();

        snes.apu.internalRegisters().setYa(235);
        snes.apu.internalRegisters().x = 10;
        assertEquals(12, snes.apu.DIV());
        assertEquals(5, snes.apu.internalRegisters().y);
        assertEquals(23, snes.apu.internalRegisters().a);

        snes.apu.internalRegisters().setYa(12345);
        snes.apu.internalRegisters().x = 2;
        assertEquals(12, snes.apu.DIV());
        assertEquals(147, snes.apu.internalRegisters().y);
        assertEquals(211, snes.apu.internalRegisters().a);
    }

    private static SNES init() {
        return new SNES(new NoRenderer(0, 0, 0));
    }
}
