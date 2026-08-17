package jamsnes.apu;

import jamsnes.SNES;
import jamsnes.audio.RecordingAudioSink;
import jamsnes.video.RecordingVideoSink;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class IncrementLogicalInstructionTest {
    @Test
    void incrementsAndDecrementsMemoryAndRegisters() {
        SNES snes = init();

        snes.apu._internalWrite(0x55, 0xdd);
        assertEquals(4, snes.apu.INC(0x55, 4));
        assertEquals(0xde, snes.apu._internalRead(0x55));

        snes.apu.internalRegisters().a = 0x76;
        assertEquals(2, snes.apu.INCreg("a"));
        assertEquals(0x77, snes.apu.internalRegisters().a);

        snes.apu._internalWrite(0x55, 0xdd);
        assertEquals(4, snes.apu.DEC(0x55, 4));
        assertEquals(0xdc, snes.apu._internalRead(0x55));

        snes.apu.internalRegisters().a = 0x76;
        assertEquals(2, snes.apu.DECreg("a"));
        assertEquals(0x75, snes.apu.internalRegisters().a);
    }

    @Test
    void logicalAccumulatorInstructionsUpdateAccumulator() {
        SNES snes = init();
        snes.apu.internalRegisters().x = 4;
        snes.apu._internalWrite(4, 23);

        snes.apu.internalRegisters().a = 24;
        assertEquals(3, snes.apu.ANDacc(snes.apu._getIndexXAddr(), 3));
        assertEquals(16, snes.apu.internalRegisters().a);

        snes.apu.internalRegisters().a = 24;
        assertEquals(3, snes.apu.ORacc(snes.apu._getIndexXAddr(), 3));
        assertEquals(31, snes.apu.internalRegisters().a);

        snes.apu.internalRegisters().a = 24;
        assertEquals(3, snes.apu.EORacc(snes.apu._getIndexXAddr(), 3));
        assertEquals(15, snes.apu.internalRegisters().a);
        assertFalse(snes.apu.internalRegisters().z);
        assertFalse(snes.apu.internalRegisters().n);
    }

    @Test
    void logicalMemoryInstructionsWriteFirstOperand() {
        SNES snes = init();
        snes.apu.internalRegisters().x = 4;
        snes.apu.internalRegisters().y = 7;

        snes.apu._internalWrite(4, 12);
        snes.apu._internalWrite(7, 44);
        assertEquals(5, snes.apu.AND(snes.apu._getIndexXAddr(), snes.apu._getIndexYAddr(), 5));
        assertEquals(12, snes.apu._internalRead(4));

        snes.apu._internalWrite(4, 12);
        snes.apu._internalWrite(7, 44);
        assertEquals(5, snes.apu.OR(snes.apu._getIndexXAddr(), snes.apu._getIndexYAddr(), 5));
        assertEquals(44, snes.apu._internalRead(4));

        snes.apu._internalWrite(4, 12);
        snes.apu._internalWrite(7, 44);
        assertEquals(5, snes.apu.EOR(snes.apu._getIndexXAddr(), snes.apu._getIndexYAddr(), 5));
        assertEquals(32, snes.apu._internalRead(4));
    }

    private static SNES init() {
        return new SNES(new RecordingVideoSink(), new RecordingAudioSink());
    }
}
