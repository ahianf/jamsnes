package jamsnes.apu;

import jamsnes.SNES;
import jamsnes.audio.RecordingAudioSink;
import jamsnes.video.RecordingVideoSink;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogicalIncrementOpcodeDispatchTest {
    @Test
    void executesLogicalAccumulatorOpcodes() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu.internalRegisters().a = 0x10;
        writeProgram(snes, 0x200,
                0x08, 0x03,
                0x28, 0x0f,
                0x48, 0xff);

        assertEquals(2, snes.apu.executeInstruction());
        assertEquals(0x13, snes.apu.internalRegisters().a);
        assertFalse(snes.apu.internalRegisters().z);
        assertFalse(snes.apu.internalRegisters().n);

        assertEquals(2, snes.apu.executeInstruction());
        assertEquals(0x03, snes.apu.internalRegisters().a);

        assertEquals(2, snes.apu.executeInstruction());
        assertEquals(0xfc, snes.apu.internalRegisters().a);
        assertTrue(snes.apu.internalRegisters().n);
    }

    @Test
    void executesLogicalDirectImmediateOpcodes() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu._internalWrite(0x40, 0x10);
        snes.apu._internalWrite(0x41, 0xf0);
        snes.apu._internalWrite(0x42, 0x0f);
        writeProgram(snes, 0x200,
                0x18, 0x03, 0x40,
                0x38, 0x0f, 0x41,
                0x58, 0x0f, 0x42);

        assertEquals(5, snes.apu.executeInstruction());
        assertEquals(0x13, snes.apu._internalRead(0x40));

        assertEquals(5, snes.apu.executeInstruction());
        assertEquals(0x00, snes.apu._internalRead(0x41));
        assertTrue(snes.apu.internalRegisters().z);

        assertEquals(5, snes.apu.executeInstruction());
        assertEquals(0x00, snes.apu._internalRead(0x42));
    }

    @Test
    void executesLogicalMemoryToMemoryOpcodes() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu._internalWrite(0x10, 0xf0);
        snes.apu._internalWrite(0x20, 0x0f);
        snes.apu._internalWrite(0x11, 0x0f);
        snes.apu._internalWrite(0x21, 0xf3);
        snes.apu._internalWrite(0x12, 0xaa);
        snes.apu._internalWrite(0x22, 0x0f);
        writeProgram(snes, 0x200,
                0x09, 0x10, 0x20,
                0x29, 0x11, 0x21,
                0x49, 0x12, 0x22);

        assertEquals(6, snes.apu.executeInstruction());
        assertEquals(0xf0, snes.apu._internalRead(0x10));
        assertEquals(0xff, snes.apu._internalRead(0x20));

        assertEquals(6, snes.apu.executeInstruction());
        assertEquals(0x0f, snes.apu._internalRead(0x11));
        assertEquals(0x03, snes.apu._internalRead(0x21));

        assertEquals(6, snes.apu.executeInstruction());
        assertEquals(0xaa, snes.apu._internalRead(0x12));
        assertEquals(0xa5, snes.apu._internalRead(0x22));
        assertTrue(snes.apu.internalRegisters().n);
        assertFalse(snes.apu.internalRegisters().z);
    }

    @Test
    void executesIndirectIndexedLogicalOpcodesWithWrappedDirectPagePointers() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x300;
        snes.apu.internalRegisters().p = true;
        snes.apu.internalRegisters().x = 0x20;
        snes.apu.internalRegisters().y = 0x10;
        snes.apu.internalRegisters().a = 0x10;
        snes.apu._internalWrite(0x110, 0x34);
        snes.apu._internalWrite(0x111, 0x12);
        snes.apu._internalWrite(0x1234, 0x03);
        snes.apu._internalWrite(0x1ff, 0x40);
        snes.apu._internalWrite(0x100, 0x12);
        snes.apu._internalWrite(0x1250, 0xff);
        writeProgram(snes, 0x300,
                0x07, 0xf0,
                0x17, 0xff,
                0x27, 0xf0,
                0x37, 0xff,
                0x47, 0xf0,
                0x57, 0xff);

        assertEquals(6, snes.apu.executeInstruction());
        assertEquals(0x13, snes.apu.internalRegisters().a);

        assertEquals(6, snes.apu.executeInstruction());
        assertEquals(0xff, snes.apu.internalRegisters().a);

        assertEquals(6, snes.apu.executeInstruction());
        assertEquals(0x03, snes.apu.internalRegisters().a);

        assertEquals(6, snes.apu.executeInstruction());
        assertEquals(0x03, snes.apu.internalRegisters().a);

        assertEquals(6, snes.apu.executeInstruction());
        assertEquals(0x00, snes.apu.internalRegisters().a);
        assertTrue(snes.apu.internalRegisters().z);

        assertEquals(6, snes.apu.executeInstruction());
        assertEquals(0xff, snes.apu.internalRegisters().a);
        assertTrue(snes.apu.internalRegisters().n);
    }

    @Test
    void executesByteIncrementAndDecrementOpcodes() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu.internalRegisters().x = 0x0f;
        snes.apu.internalRegisters().y = 0x20;
        snes.apu._internalWrite(0x200, 0x3d);
        snes.apu._internalWrite(0x201, 0xdc);

        assertEquals(2, snes.apu.executeInstruction());
        assertEquals(0x10, snes.apu.internalRegisters().x);

        assertEquals(2, snes.apu.executeInstruction());
        assertEquals(0x1f, snes.apu.internalRegisters().y);

        snes.apu._internalWrite(0x202, 0xab);
        snes.apu._internalWrite(0x203, 0x50);
        snes.apu._internalWrite(0x50, 0xff);
        assertEquals(4, snes.apu.executeInstruction());
        assertEquals(0x00, snes.apu._internalRead(0x50));
        assertTrue(snes.apu.internalRegisters().z);

        snes.apu._internalWrite(0x204, 0x8c);
        snes.apu._internalWrite(0x205, 0x00);
        snes.apu._internalWrite(0x206, 0x03);
        snes.apu._internalWrite(0x0300, 0x00);
        assertEquals(5, snes.apu.executeInstruction());
        assertEquals(0xff, snes.apu._internalRead(0x0300));
        assertTrue(snes.apu.internalRegisters().n);
    }

    @Test
    void executesWordIncrementAndDecrementOpcodes() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu._internalWrite(0x40, 0xff);
        snes.apu._internalWrite(0x41, 0x00);
        snes.apu._internalWrite(0x200, 0x3a);
        snes.apu._internalWrite(0x201, 0x40);
        snes.apu._internalWrite(0x202, 0x1a);
        snes.apu._internalWrite(0x203, 0x40);

        assertEquals(6, snes.apu.executeInstruction());
        assertEquals(0x00, snes.apu._internalRead(0x40));
        assertEquals(0x01, snes.apu._internalRead(0x41));

        assertEquals(6, snes.apu.executeInstruction());
        assertEquals(0xff, snes.apu._internalRead(0x40));
        assertEquals(0x00, snes.apu._internalRead(0x41));
    }

    private static SNES init() {
        return new SNES(new RecordingVideoSink(), new RecordingAudioSink());
    }

    private static void writeProgram(SNES snes, int start, int... bytes) {
        for (int i = 0; i < bytes.length; i++) {
            snes.apu._internalWrite(start + i, bytes[i]);
        }
    }
}
