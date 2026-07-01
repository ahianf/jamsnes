package jamsnes.apu;

import jamsnes.SNES;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BitShiftOpcodeDispatchTest {
    @Test
    void executesSetAndClearBitOpcodes() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu._internalWrite(0x200, 0x02);
        snes.apu._internalWrite(0x201, 0x44);
        snes.apu._internalWrite(0x202, 0x12);
        snes.apu._internalWrite(0x203, 0x44);

        assertEquals(4, snes.apu.executeInstruction());
        assertEquals(0x01, snes.apu._internalRead(0x44));

        assertEquals(4, snes.apu.executeInstruction());
        assertEquals(0x00, snes.apu._internalRead(0x44));
    }

    @Test
    void executesAbsoluteBitCarryTransferAndToggleOpcodes() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu.internalRegisters().c = true;
        writeAbsoluteBitInstruction(snes, 0x200, 0xca, 0x0123, 2);
        writeAbsoluteBitInstruction(snes, 0x203, 0xaa, 0x0123, 2);
        writeAbsoluteBitInstruction(snes, 0x206, 0xea, 0x0123, 2);

        assertEquals(6, snes.apu.executeInstruction());
        assertEquals(0x04, snes.apu._internalRead(0x0123));

        snes.apu.internalRegisters().c = false;
        assertEquals(4, snes.apu.executeInstruction());
        assertTrue(snes.apu.internalRegisters().c);

        assertEquals(5, snes.apu.executeInstruction());
        assertEquals(0x00, snes.apu._internalRead(0x0123));
    }

    @Test
    void executesTestSetAndClearOpcodes() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu.internalRegisters().a = 0x0f;
        snes.apu._internalWrite(0x0300, 0xf0);
        snes.apu._internalWrite(0x200, 0x0e);
        snes.apu._internalWrite(0x201, 0x00);
        snes.apu._internalWrite(0x202, 0x03);
        snes.apu._internalWrite(0x203, 0x4e);
        snes.apu._internalWrite(0x204, 0x00);
        snes.apu._internalWrite(0x205, 0x03);

        assertEquals(6, snes.apu.executeInstruction());
        assertEquals(0xff, snes.apu._internalRead(0x0300));
        assertTrue(snes.apu.internalRegisters().n);
        assertFalse(snes.apu.internalRegisters().z);

        assertEquals(6, snes.apu.executeInstruction());
        assertEquals(0xf0, snes.apu._internalRead(0x0300));
    }

    @Test
    void executesShiftAndNibbleExchangeOpcodes() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu._internalWrite(0x50, 0x80);
        snes.apu._internalWrite(0x200, 0x0b);
        snes.apu._internalWrite(0x201, 0x50);
        snes.apu._internalWrite(0x202, 0x9f);

        assertEquals(4, snes.apu.executeInstruction());
        assertEquals(0x00, snes.apu._internalRead(0x50));
        assertTrue(snes.apu.internalRegisters().c);
        assertTrue(snes.apu.internalRegisters().z);

        snes.apu.internalRegisters().a = 0xa5;
        assertEquals(5, snes.apu.executeInstruction());
        assertEquals(0x5a, snes.apu.internalRegisters().a);
    }

    @Test
    void executesAccumulatorRightShiftOpcodes() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu.internalRegisters().a = 0x03;
        snes.apu._internalWrite(0x200, 0x5c);
        snes.apu._internalWrite(0x201, 0x7c);

        assertEquals(2, snes.apu.executeInstruction());
        assertEquals(0x01, snes.apu.internalRegisters().a);
        assertTrue(snes.apu.internalRegisters().c);
        assertTrue(snes.apu.internalRegisters().n);

        assertEquals(2, snes.apu.executeInstruction());
        assertEquals(0x01, snes.apu.internalRegisters().a);
        assertTrue(snes.apu.internalRegisters().c);
        assertTrue(snes.apu.internalRegisters().n);
    }

    private static void writeAbsoluteBitInstruction(SNES snes, int pc, int opcode, int address, int bit) {
        int operand = (bit << 13) | address;
        snes.apu._internalWrite(pc, opcode);
        snes.apu._internalWrite(pc + 1, operand);
        snes.apu._internalWrite(pc + 2, operand >>> 8);
    }

    private static SNES init() {
        return new SNES(new NoRenderer(0, 0, 0));
    }
}
