package jamsnes.apu;

import jamsnes.SNES;
import jamsnes.renderer.TestFrontend;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShiftRotationInstructionTest {
    @Test
    void aslShiftsAccumulatorAndMemoryLeft() {
        SNES snes = init();
        snes.apu.internalRegisters().a = 0x66;

        assertEquals(2, snes.apu.ASL(snes.apu.internalRegisters().a, 2, true));
        assertEquals(0xcc, snes.apu.internalRegisters().a);
        assertFalse(snes.apu.internalRegisters().c);
        assertTrue(snes.apu.internalRegisters().n);
        assertFalse(snes.apu.internalRegisters().z);

        snes.apu._internalWrite(0x55, 0xdd);
        assertEquals(5, snes.apu.ASL(0x55, 5));
        assertEquals(0xba, snes.apu._internalRead(0x55));
        assertTrue(snes.apu.internalRegisters().c);
        assertTrue(snes.apu.internalRegisters().n);
        assertFalse(snes.apu.internalRegisters().z);
    }

    @Test
    void lsrShiftsAccumulatorAndMemoryRight() {
        SNES snes = init();
        snes.apu.internalRegisters().a = 0x66;

        assertEquals(2, snes.apu.LSR(snes.apu.internalRegisters().a, 2, true));
        assertEquals(0x33, snes.apu.internalRegisters().a);
        assertFalse(snes.apu.internalRegisters().c);
        assertFalse(snes.apu.internalRegisters().n);
        assertFalse(snes.apu.internalRegisters().z);

        snes.apu._internalWrite(0x55, 0xdd);
        assertEquals(5, snes.apu.LSR(0x55, 5));
        assertEquals(0x6e, snes.apu._internalRead(0x55));
        assertTrue(snes.apu.internalRegisters().c);
        assertFalse(snes.apu.internalRegisters().n);
        assertFalse(snes.apu.internalRegisters().z);
    }

    @Test
    void rolRotatesAccumulatorAndMemoryThroughCarry() {
        SNES snes = init();
        snes.apu.internalRegisters().a = 0x66;

        assertEquals(2, snes.apu.ROL(snes.apu.internalRegisters().a, 2, true));
        assertEquals(0xcc, snes.apu.internalRegisters().a);
        assertFalse(snes.apu.internalRegisters().c);
        assertTrue(snes.apu.internalRegisters().n);
        assertFalse(snes.apu.internalRegisters().z);

        snes.apu.internalRegisters().c = true;
        snes.apu._internalWrite(0x55, 0x7f);
        assertEquals(5, snes.apu.ROL(0x55, 5));
        assertEquals(0xff, snes.apu._internalRead(0x55));
        assertFalse(snes.apu.internalRegisters().c);
        assertTrue(snes.apu.internalRegisters().n);
    }

    @Test
    void rorRotatesAccumulatorAndMemoryThroughCarry() {
        SNES snes = init();
        snes.apu.internalRegisters().a = 0x66;

        assertEquals(2, snes.apu.ROR(snes.apu.internalRegisters().a, 2, true));
        assertEquals(0x33, snes.apu.internalRegisters().a);
        assertFalse(snes.apu.internalRegisters().c);
        assertFalse(snes.apu.internalRegisters().n);
        assertFalse(snes.apu.internalRegisters().z);

        snes.apu.internalRegisters().c = true;
        snes.apu._internalWrite(0x55, 0xdc);
        assertEquals(5, snes.apu.ROR(0x55, 5));
        assertEquals(0xee, snes.apu._internalRead(0x55));
        assertFalse(snes.apu.internalRegisters().c);
        assertTrue(snes.apu.internalRegisters().n);
    }

    @Test
    void xcnExchangesAccumulatorNibbles() {
        SNES snes = init();
        snes.apu.internalRegisters().a = 0xa5;

        assertEquals(5, snes.apu.XCN());
        assertEquals(0x5a, snes.apu.internalRegisters().a);
        assertFalse(snes.apu.internalRegisters().n);
        assertFalse(snes.apu.internalRegisters().z);
    }

    private static SNES init() {
        return new SNES(new TestFrontend(0, 0, 0));
    }
}
