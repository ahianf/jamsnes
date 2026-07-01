package jamsnes.apu;

import jamsnes.SNES;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArithmeticOpcodeDispatchTest {
    @Test
    void executesAdcAndSbcAccumulatorOpcodes() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu.internalRegisters().a = 10;
        snes.apu.internalRegisters().c = true;
        snes.apu._internalWrite(0x40, 20);
        snes.apu._internalWrite(0x200, 0x84);
        snes.apu._internalWrite(0x201, 0x40);
        snes.apu._internalWrite(0x202, 0xa4);
        snes.apu._internalWrite(0x203, 0x40);

        assertEquals(3, snes.apu.executeInstruction());
        assertEquals(31, snes.apu.internalRegisters().a);
        assertFalse(snes.apu.internalRegisters().c);

        snes.apu.internalRegisters().c = true;
        assertEquals(3, snes.apu.executeInstruction());
        assertEquals(11, snes.apu.internalRegisters().a);
        assertTrue(snes.apu.internalRegisters().c);
    }

    @Test
    void executesAdcAndSbcMemoryToMemoryOpcodes() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu.internalRegisters().x = 0x10;
        snes.apu.internalRegisters().y = 0x20;
        snes.apu.internalRegisters().c = true;
        snes.apu._internalWrite(0x10, 5);
        snes.apu._internalWrite(0x20, 3);
        snes.apu._internalWrite(0x200, 0x99);
        snes.apu._internalWrite(0x201, 0xb9);

        assertEquals(3, snes.apu.executeInstruction());
        assertEquals(9, snes.apu._internalRead(0x10));

        snes.apu.internalRegisters().c = true;
        assertEquals(5, snes.apu.executeInstruction());
        assertEquals(6, snes.apu._internalRead(0x10));
    }

    @Test
    void executesImmediateArithmeticOpcodesAsValues() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu.internalRegisters().a = 10;
        snes.apu._internalWrite(0x30, 10);
        writeProgram(snes, 0x200,
                0x88, 5,
                0x98, 0x30, 7,
                0xa8, 3,
                0xb8, 0x30, 2);

        assertEquals(2, snes.apu.executeInstruction());
        assertEquals(15, snes.apu.internalRegisters().a);

        assertEquals(5, snes.apu.executeInstruction());
        assertEquals(17, snes.apu._internalRead(0x30));

        snes.apu.internalRegisters().c = true;
        assertEquals(2, snes.apu.executeInstruction());
        assertEquals(12, snes.apu.internalRegisters().a);

        snes.apu.internalRegisters().c = true;
        assertEquals(5, snes.apu.executeInstruction());
        assertEquals(15, snes.apu._internalRead(0x30));
    }

    @Test
    void executesCompareOpcodes() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu.internalRegisters().a = 0x44;
        snes.apu.internalRegisters().x = 0x20;
        snes.apu._internalWrite(0x20, 0x22);
        snes.apu._internalWrite(0x21, 0x33);
        snes.apu._internalWrite(0x22, 0x44);
        snes.apu._internalWrite(0x30, 0x70);
        snes.apu._internalWrite(0x31, 0x60);
        writeProgram(snes, 0x200,
                0x64, 0x20,
                0xc8, 0x20,
                0x69, 0x21, 0x22,
                0x79);

        assertEquals(3, snes.apu.executeInstruction());
        assertTrue(snes.apu.internalRegisters().c);
        assertFalse(snes.apu.internalRegisters().z);

        assertEquals(2, snes.apu.executeInstruction());
        assertTrue(snes.apu.internalRegisters().c);
        assertTrue(snes.apu.internalRegisters().z);

        assertEquals(6, snes.apu.executeInstruction());
        assertFalse(snes.apu.internalRegisters().c);

        snes.apu.internalRegisters().x = 0x30;
        snes.apu.internalRegisters().y = 0x31;
        assertEquals(5, snes.apu.executeInstruction());
        assertTrue(snes.apu.internalRegisters().c);
    }

    @Test
    void executesWordArithmeticOpcodes() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu.internalRegisters().setYa(0x0100);
        snes.apu._internalWrite(0x40, 0x34);
        snes.apu._internalWrite(0x41, 0x12);
        snes.apu._internalWrite(0x200, 0x7a);
        snes.apu._internalWrite(0x201, 0x40);
        snes.apu._internalWrite(0x202, 0x9a);
        snes.apu._internalWrite(0x203, 0x40);
        snes.apu._internalWrite(0x204, 0x5a);
        snes.apu._internalWrite(0x205, 0x40);

        assertEquals(5, snes.apu.executeInstruction());
        assertEquals(0x1334, snes.apu.internalRegisters().ya());

        assertEquals(5, snes.apu.executeInstruction());
        assertEquals(0x0100, snes.apu.internalRegisters().ya());

        assertEquals(4, snes.apu.executeInstruction());
        assertFalse(snes.apu.internalRegisters().c);
    }

    @Test
    void executesDecimalMultiplyAndDivideOpcodes() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu.internalRegisters().a = 0x9a;
        snes.apu.internalRegisters().c = true;
        snes.apu._internalWrite(0x200, 0xdf);

        assertEquals(3, snes.apu.executeInstruction());
        assertEquals(0x00, snes.apu.internalRegisters().a);
        assertTrue(snes.apu.internalRegisters().z);

        snes.apu.internalRegisters().pc = 0x210;
        snes.apu.internalRegisters().a = 23;
        snes.apu.internalRegisters().y = 10;
        snes.apu._internalWrite(0x210, 0xcf);
        assertEquals(9, snes.apu.executeInstruction());
        assertEquals(230, snes.apu.internalRegisters().ya());

        snes.apu.internalRegisters().pc = 0x220;
        snes.apu.internalRegisters().setYa(235);
        snes.apu.internalRegisters().x = 10;
        snes.apu._internalWrite(0x220, 0x9e);
        assertEquals(12, snes.apu.executeInstruction());
        assertEquals(23, snes.apu.internalRegisters().a);
        assertEquals(5, snes.apu.internalRegisters().y);
    }

    private static SNES init() {
        return new SNES(new NoRenderer(0, 0, 0));
    }

    private static void writeProgram(SNES snes, int start, int... bytes) {
        for (int i = 0; i < bytes.length; i++) {
            snes.apu._internalWrite(start + i, bytes[i]);
        }
    }
}
