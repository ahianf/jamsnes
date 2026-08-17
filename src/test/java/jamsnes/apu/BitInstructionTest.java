package jamsnes.apu;

import jamsnes.SNES;
import jamsnes.audio.RecordingAudioSink;
import jamsnes.video.RecordingVideoSink;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BitInstructionTest {
    @Test
    void setAndClearDirectBits() {
        SNES snes = init();

        snes.apu._internalWrite(0x32, 0);
        assertEquals(4, snes.apu.SET1(0x32, 0));
        assertEquals(1, snes.apu._internalRead(0x32));

        snes.apu._internalWrite(0x32, 0xff);
        assertEquals(4, snes.apu.CLR1(0x32, 0));
        assertEquals(0xfe, snes.apu._internalRead(0x32));
    }

    @Test
    void testAndClearSetMemoryAgainstAccumulator() {
        SNES snes = init();

        snes.apu.internalRegisters().a = 42;
        snes.apu._internalWrite(0xf00f, 123);
        assertEquals(6, snes.apu.TSET1(0xf00f));
        assertEquals(0x7b, snes.apu._internalRead(0xf00f));
        assertFalse(snes.apu.internalRegisters().z);
        assertTrue(snes.apu.internalRegisters().n);

        snes.apu.internalRegisters().a = 0x80;
        snes.apu._internalWrite(0xf00f, 0x80);
        assertEquals(6, snes.apu.TCLR1(0xf00f));
        assertEquals(0, snes.apu._internalRead(0xf00f));
        assertTrue(snes.apu.internalRegisters().z);
        assertFalse(snes.apu.internalRegisters().n);
    }

    @Test
    void logicalBitInstructionsUpdateCarry() {
        SNES snes = init();
        AbsoluteBit operand = new AbsoluteBit(0x1000, 3);
        snes.apu._internalWrite(0x1000, 0b0000_1000);

        snes.apu.internalRegisters().c = true;
        assertEquals(4, snes.apu.AND1(operand));
        assertTrue(snes.apu.internalRegisters().c);

        assertEquals(5, snes.apu.EOR1(operand));
        assertFalse(snes.apu.internalRegisters().c);

        assertEquals(5, snes.apu.OR1(operand));
        assertTrue(snes.apu.internalRegisters().c);

        assertEquals(4, snes.apu.AND1(operand, true));
        assertFalse(snes.apu.internalRegisters().c);
    }

    @Test
    void notAndMoveBitInstructionsReadAndWriteMemory() {
        SNES snes = init();
        AbsoluteBit operand = new AbsoluteBit(0x1000, 3);
        snes.apu._internalWrite(0x1000, 0b0000_1000);

        assertEquals(5, snes.apu.NOT1(operand));
        assertEquals(0, snes.apu._internalRead(0x1000));

        snes.apu.internalRegisters().c = true;
        assertEquals(6, snes.apu.MOV1(operand));
        assertEquals(0b0000_1000, snes.apu._internalRead(0x1000));

        snes.apu.internalRegisters().c = false;
        assertEquals(4, snes.apu.MOV1(operand, true));
        assertTrue(snes.apu.internalRegisters().c);
    }

    private static SNES init() {
        return new SNES(new RecordingVideoSink(), new RecordingAudioSink());
    }
}
