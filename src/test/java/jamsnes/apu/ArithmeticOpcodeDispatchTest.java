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
    void absoluteAdcUsesFourCycles() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu.internalRegisters().a = 0x10;
        snes.apu._internalWrite(0x0340, 0x20);
        writeProgram(snes, 0x200, 0x85, 0x40, 0x03);

        assertEquals(4, snes.apu.executeInstruction());
        assertEquals(0x30, snes.apu.internalRegisters().a);
        assertEquals(0x0203, snes.apu.internalRegisters().pc);
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

        assertEquals(5, snes.apu.executeInstruction());
        assertEquals(9, snes.apu._internalRead(0x10));

        snes.apu.internalRegisters().c = true;
        assertEquals(5, snes.apu.executeInstruction());
        assertEquals(6, snes.apu._internalRead(0x10));
    }

    @Test
    void executesIndirectIndexedAdcAndSbcWithWrappedDirectPagePointers() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x300;
        snes.apu.internalRegisters().p = true;
        snes.apu.internalRegisters().x = 0x20;
        snes.apu.internalRegisters().y = 0x10;
        snes.apu.internalRegisters().a = 0x01;
        snes.apu._internalWrite(0x110, 0x34);
        snes.apu._internalWrite(0x111, 0x12);
        snes.apu._internalWrite(0x1234, 0x10);
        snes.apu._internalWrite(0x1ff, 0x40);
        snes.apu._internalWrite(0x100, 0x12);
        snes.apu._internalWrite(0x1250, 0x20);
        writeProgram(snes, 0x300,
                0x87, 0xf0,
                0x97, 0xff,
                0xa7, 0xf0,
                0xb7, 0xff);

        assertEquals(6, snes.apu.executeInstruction());
        assertEquals(0x11, snes.apu.internalRegisters().a);

        assertEquals(6, snes.apu.executeInstruction());
        assertEquals(0x31, snes.apu.internalRegisters().a);

        snes.apu.internalRegisters().a = 0x40;
        snes.apu.internalRegisters().c = true;
        assertEquals(6, snes.apu.executeInstruction());
        assertEquals(0x30, snes.apu.internalRegisters().a);

        assertEquals(6, snes.apu.executeInstruction());
        assertEquals(0x10, snes.apu.internalRegisters().a);
    }

    @Test
    void executesImmediateArithmeticOpcodesAsValues() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu.internalRegisters().a = 10;
        snes.apu._internalWrite(0x30, 10);
        writeProgram(snes, 0x200,
                0x88, 5,
                0x98, 7, 0x30,
                0xa8, 3,
                0xb8, 2, 0x30);

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
    void executesTwoDirectArithmeticOpcodesInSourceDestinationOrder() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu._internalWrite(0x10, 3);
        snes.apu._internalWrite(0x20, 5);
        snes.apu._internalWrite(0x11, 2);
        snes.apu._internalWrite(0x21, 9);
        writeProgram(snes, 0x200,
                0x89, 0x10, 0x20,
                0xa9, 0x11, 0x21);

        assertEquals(6, snes.apu.executeInstruction());
        assertEquals(3, snes.apu._internalRead(0x10));
        assertEquals(8, snes.apu._internalRead(0x20));

        snes.apu.internalRegisters().c = true;
        assertEquals(6, snes.apu.executeInstruction());
        assertEquals(2, snes.apu._internalRead(0x11));
        assertEquals(7, snes.apu._internalRead(0x21));
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
                0x78, 0x45, 0x22,
                0x79);

        assertEquals(3, snes.apu.executeInstruction());
        assertTrue(snes.apu.internalRegisters().c);
        assertFalse(snes.apu.internalRegisters().z);

        assertEquals(2, snes.apu.executeInstruction());
        assertTrue(snes.apu.internalRegisters().c);
        assertTrue(snes.apu.internalRegisters().z);

        assertEquals(6, snes.apu.executeInstruction());
        assertTrue(snes.apu.internalRegisters().c);

        assertEquals(5, snes.apu.executeInstruction());
        assertFalse(snes.apu.internalRegisters().c);
        assertTrue(snes.apu.internalRegisters().n);

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
        assertFalse(snes.apu.internalRegisters().n);
        assertTrue(snes.apu.internalRegisters().z);

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
