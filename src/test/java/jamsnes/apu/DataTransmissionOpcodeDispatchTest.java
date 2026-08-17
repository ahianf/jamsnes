package jamsnes.apu;

import jamsnes.SNES;
import jamsnes.renderer.TestFrontend;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataTransmissionOpcodeDispatchTest {
    @Test
    void executesImmediateAndRegisterMoveOpcodes() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu.internalRegisters().sp = 0x44;
        snes.apu._internalWrite(0x200, 0x8d);
        snes.apu._internalWrite(0x201, 0x7a);
        snes.apu._internalWrite(0x202, 0x9d);
        snes.apu._internalWrite(0x203, 0xbd);
        snes.apu._internalWrite(0x204, 0xdd);
        snes.apu._internalWrite(0x205, 0xfd);

        assertEquals(2, snes.apu.executeInstruction());
        assertEquals(0x7a, snes.apu.internalRegisters().y);
        assertFalse(snes.apu.internalRegisters().z);
        assertFalse(snes.apu.internalRegisters().n);

        assertEquals(2, snes.apu.executeInstruction());
        assertEquals(0x44, snes.apu.internalRegisters().x);

        snes.apu.internalRegisters().x = 0x33;
        snes.apu.internalRegisters().n = true;
        assertEquals(2, snes.apu.executeInstruction());
        assertEquals(0x33, snes.apu.internalRegisters().sp);
        assertTrue(snes.apu.internalRegisters().n);

        assertEquals(2, snes.apu.executeInstruction());
        assertEquals(0x7a, snes.apu.internalRegisters().a);

        snes.apu.internalRegisters().a = 0x21;
        assertEquals(2, snes.apu.executeInstruction());
        assertEquals(0x21, snes.apu.internalRegisters().y);
    }

    @Test
    void executesImmediateToDirectMemoryMoveOpcode() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu._internalWrite(0x40, 0x7a);
        writeProgram(snes, 0x200,
                0x8f, 0xab, 0x55,
                0xfa, 0x40, 0x50);

        assertEquals(5, snes.apu.executeInstruction());
        assertEquals(0xab, snes.apu._internalRead(0x55));

        assertEquals(5, snes.apu.executeInstruction());
        assertEquals(0x7a, snes.apu._internalRead(0x40));
        assertEquals(0x7a, snes.apu._internalRead(0x50));
    }

    @Test
    void executesRegisterToMemoryStoreOpcodes() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu.internalRegisters().a = 0x66;
        snes.apu.internalRegisters().x = 0x77;
        snes.apu.internalRegisters().y = 0x88;
        snes.apu._internalWrite(0x42, 0x7a);
        snes.apu._internalWrite(0x0310, 0xa5);
        snes.apu._internalWrite(0x200, 0xc4);
        snes.apu._internalWrite(0x201, 0x40);
        snes.apu._internalWrite(0x202, 0xc9);
        snes.apu._internalWrite(0x203, 0x00);
        snes.apu._internalWrite(0x204, 0x03);
        snes.apu._internalWrite(0x205, 0xcb);
        snes.apu._internalWrite(0x206, 0x41);
        snes.apu._internalWrite(0x207, 0xeb);
        snes.apu._internalWrite(0x208, 0x42);
        snes.apu._internalWrite(0x209, 0xec);
        snes.apu._internalWrite(0x20a, 0x10);
        snes.apu._internalWrite(0x20b, 0x03);

        assertEquals(4, snes.apu.executeInstruction());
        assertEquals(0x66, snes.apu._internalRead(0x40));

        assertEquals(5, snes.apu.executeInstruction());
        assertEquals(0x77, snes.apu._internalRead(0x0300));

        assertEquals(4, snes.apu.executeInstruction());
        assertEquals(0x88, snes.apu._internalRead(0x41));

        assertEquals(3, snes.apu.executeInstruction());
        assertEquals(0x7a, snes.apu.internalRegisters().y);

        assertEquals(4, snes.apu.executeInstruction());
        assertEquals(0xa5, snes.apu.internalRegisters().y);
    }

    @Test
    void executesIndexedXAutoIncrementMoveOpcodes() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu.internalRegisters().x = 0x30;
        snes.apu.internalRegisters().a = 0x5c;
        snes.apu._internalWrite(0x200, 0xaf);
        snes.apu._internalWrite(0x201, 0xbf);

        assertEquals(4, snes.apu.executeInstruction());
        assertEquals(0x5c, snes.apu._internalRead(0x30));
        assertEquals(0x31, snes.apu.internalRegisters().x);

        snes.apu.internalRegisters().a = 0;
        snes.apu._internalWrite(0x31, 0xa7);
        assertEquals(4, snes.apu.executeInstruction());
        assertEquals(0xa7, snes.apu.internalRegisters().a);
        assertEquals(0x32, snes.apu.internalRegisters().x);
    }

    @Test
    void executesWordMoveOpcodes() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu._internalWrite(0x40, 0x34);
        snes.apu._internalWrite(0x41, 0x12);
        snes.apu._internalWrite(0x200, 0xba);
        snes.apu._internalWrite(0x201, 0x40);
        snes.apu._internalWrite(0x202, 0xda);
        snes.apu._internalWrite(0x203, 0x50);

        assertEquals(5, snes.apu.executeInstruction());
        assertEquals(0x1234, snes.apu.internalRegisters().ya());

        snes.apu.internalRegisters().a = 0x78;
        snes.apu.internalRegisters().y = 0x56;
        assertEquals(5, snes.apu.executeInstruction());
        assertEquals(0x78, snes.apu._internalRead(0x50));
        assertEquals(0x56, snes.apu._internalRead(0x51));
    }

    @Test
    void executesWordMoveOpcodesWithWrappedDirectPageHighByte() {
        SNES snes = init();
        snes.apu.internalRegisters().p = true;
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu._internalWrite(0x1ff, 0x34);
        snes.apu._internalWrite(0x100, 0x12);
        snes.apu._internalWrite(0x200, 0xba);
        snes.apu._internalWrite(0x201, 0xff);
        snes.apu._internalWrite(0x202, 0xda);
        snes.apu._internalWrite(0x203, 0xff);

        assertEquals(5, snes.apu.executeInstruction());
        assertEquals(0x1234, snes.apu.internalRegisters().ya());

        snes.apu.internalRegisters().a = 0x78;
        snes.apu.internalRegisters().y = 0x56;
        assertEquals(5, snes.apu.executeInstruction());
        assertEquals(0x78, snes.apu._internalRead(0x1ff));
        assertEquals(0x56, snes.apu._internalRead(0x100));
    }

    @Test
    void executesDirectAndIndexedLoadOpcodes() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu.internalRegisters().x = 0x02;
        snes.apu._internalWrite(0x42, 0x99);
        snes.apu._internalWrite(0x200, 0xe4);
        snes.apu._internalWrite(0x201, 0x42);
        snes.apu._internalWrite(0x202, 0xf4);
        snes.apu._internalWrite(0x203, 0x40);

        assertEquals(3, snes.apu.executeInstruction());
        assertEquals(0x99, snes.apu.internalRegisters().a);

        snes.apu._internalWrite(0x42, 0x55);
        assertEquals(4, snes.apu.executeInstruction());
        assertEquals(0x55, snes.apu.internalRegisters().a);
    }

    @Test
    void executesAbsoluteImmediateAndIndirectLoadOpcodes() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu.internalRegisters().x = 0x03;
        snes.apu.internalRegisters().y = 0x04;
        snes.apu._internalWrite(0x03, 0xc5);
        snes.apu._internalWrite(0x0340, 0xee);
        snes.apu._internalWrite(0x0343, 0x11);
        snes.apu._internalWrite(0x13, 0x9a);
        snes.apu._internalWrite(0x14, 0x02);
        snes.apu._internalWrite(0x029a, 0xdd);
        snes.apu._internalWrite(0x0421, 0x6c);
        writeProgram(snes, 0x200,
                0xe5, 0x40, 0x03,
                0xe6,
                0xe7, 0x10,
                0xe8, 0x80,
                0xe9, 0x21, 0x04);

        assertEquals(4, snes.apu.executeInstruction());
        assertEquals(0xee, snes.apu.internalRegisters().a);

        assertEquals(3, snes.apu.executeInstruction());
        assertEquals(0xc5, snes.apu.internalRegisters().a);

        assertEquals(6, snes.apu.executeInstruction());
        assertEquals(0xdd, snes.apu.internalRegisters().a);

        assertEquals(2, snes.apu.executeInstruction());
        assertEquals(0x80, snes.apu.internalRegisters().a);
        assertTrue(snes.apu.internalRegisters().n);

        assertEquals(4, snes.apu.executeInstruction());
        assertEquals(0x6c, snes.apu.internalRegisters().x);
    }

    @Test
    void executesIndexedLoadOpcodes() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x300;
        snes.apu.internalRegisters().x = 0x03;
        snes.apu.internalRegisters().y = 0x04;
        snes.apu._internalWrite(0x0344, 0xee);
        snes.apu._internalWrite(0x20, 0x70);
        snes.apu._internalWrite(0x21, 0x04);
        snes.apu._internalWrite(0x0474, 0xdd);
        snes.apu._internalWrite(0x06, 0x26);
        snes.apu._internalWrite(0x14, 0x14);
        snes.apu._internalWrite(0x34, 0xbb);
        writeProgram(snes, 0x300,
                0xf6, 0x40, 0x03,
                0xf7, 0x20,
                0xf8, 0x06,
                0xf9, 0x10,
                0xfb, 0x20);

        assertEquals(5, snes.apu.executeInstruction());
        assertEquals(0xee, snes.apu.internalRegisters().a);

        assertEquals(6, snes.apu.executeInstruction());
        assertEquals(0xdd, snes.apu.internalRegisters().a);

        assertEquals(3, snes.apu.executeInstruction());
        assertEquals(0x26, snes.apu.internalRegisters().x);

        assertEquals(4, snes.apu.executeInstruction());
        assertEquals(0x14, snes.apu.internalRegisters().x);

        assertEquals(4, snes.apu.executeInstruction());
        assertEquals(0xbb, snes.apu.internalRegisters().y);
    }

    @Test
    void executesIndirectIndexedLoadsWithWrappedDirectPagePointers() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x300;
        snes.apu.internalRegisters().p = true;
        snes.apu.internalRegisters().x = 0x20;
        snes.apu.internalRegisters().y = 0x10;
        snes.apu._internalWrite(0x110, 0x34);
        snes.apu._internalWrite(0x111, 0x12);
        snes.apu._internalWrite(0x1234, 0x9a);
        snes.apu._internalWrite(0x1ff, 0x40);
        snes.apu._internalWrite(0x100, 0x12);
        snes.apu._internalWrite(0x1250, 0xbc);
        writeProgram(snes, 0x300,
                0xe7, 0xf0,
                0xf7, 0xff);

        assertEquals(6, snes.apu.executeInstruction());
        assertEquals(0x9a, snes.apu.internalRegisters().a);

        assertEquals(6, snes.apu.executeInstruction());
        assertEquals(0xbc, snes.apu.internalRegisters().a);
    }

    @Test
    void executesIndirectIndexedStoresWithWrappedDirectPagePointers() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x300;
        snes.apu.internalRegisters().p = true;
        snes.apu.internalRegisters().x = 0x20;
        snes.apu.internalRegisters().y = 0x10;
        snes.apu.internalRegisters().a = 0x5a;
        snes.apu._internalWrite(0x110, 0x34);
        snes.apu._internalWrite(0x111, 0x12);
        snes.apu._internalWrite(0x1ff, 0x40);
        snes.apu._internalWrite(0x100, 0x12);
        writeProgram(snes, 0x300,
                0xc7, 0xf0,
                0xd7, 0xff);

        assertEquals(7, snes.apu.executeInstruction());
        assertEquals(0x5a, snes.apu._internalRead(0x1234));

        snes.apu.internalRegisters().a = 0xa5;
        assertEquals(7, snes.apu.executeInstruction());
        assertEquals(0xa5, snes.apu._internalRead(0x1250));
    }

    private static void writeProgram(SNES snes, int start, int... bytes) {
        for (int i = 0; i < bytes.length; i++) {
            snes.apu._internalWrite(start + i, bytes[i]);
        }
    }

    private static SNES init() {
        return new SNES(new TestFrontend(0, 0, 0));
    }
}
