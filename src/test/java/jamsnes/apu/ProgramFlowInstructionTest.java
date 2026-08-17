package jamsnes.apu;

import jamsnes.SNES;
import jamsnes.audio.RecordingAudioSink;
import jamsnes.video.RecordingVideoSink;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgramFlowInstructionTest {
    @Test
    void braAppliesSignedByteOffset() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x0100;

        assertEquals(4, snes.apu.BRA(0xfe));

        assertEquals(0x00fe, snes.apu.internalRegisters().pc);
    }

    @Test
    void conditionalBranchesOnlyMoveProgramCounterWhenConditionMatches() {
        SNES snes = init();

        assertEquals(2, snes.apu.BEQ(5));
        assertEquals(0xffc0, snes.apu.internalRegisters().pc);
        snes.apu.internalRegisters().z = true;
        assertEquals(4, snes.apu.BEQ(5));
        assertEquals(0xffc5, snes.apu.internalRegisters().pc);

        snes.apu.internalRegisters().pc = 0x1000;
        assertEquals(2, snes.apu.BNE(5));
        snes.apu.internalRegisters().z = false;
        assertEquals(4, snes.apu.BNE(5));
        assertEquals(0x1005, snes.apu.internalRegisters().pc);

        snes.apu.internalRegisters().pc = 0x2000;
        assertEquals(2, snes.apu.BCS(5));
        snes.apu.internalRegisters().c = true;
        assertEquals(4, snes.apu.BCS(5));
        assertEquals(0x2005, snes.apu.internalRegisters().pc);

        assertEquals(2, snes.apu.BCC(5));
        snes.apu.internalRegisters().c = false;
        assertEquals(4, snes.apu.BCC(5));
        assertEquals(0x200a, snes.apu.internalRegisters().pc);

        assertEquals(2, snes.apu.BVS(5));
        snes.apu.internalRegisters().v = true;
        assertEquals(4, snes.apu.BVS(5));
        assertEquals(0x200f, snes.apu.internalRegisters().pc);

        assertEquals(2, snes.apu.BVC(5));
        snes.apu.internalRegisters().v = false;
        assertEquals(4, snes.apu.BVC(5));
        assertEquals(0x2014, snes.apu.internalRegisters().pc);

        assertEquals(2, snes.apu.BMI(5));
        snes.apu.internalRegisters().n = true;
        assertEquals(4, snes.apu.BMI(5));
        assertEquals(0x2019, snes.apu.internalRegisters().pc);

        assertEquals(2, snes.apu.BPL(5));
        snes.apu.internalRegisters().n = false;
        assertEquals(4, snes.apu.BPL(5));
        assertEquals(0x201e, snes.apu.internalRegisters().pc);
    }

    @Test
    void bitBranchesInspectMemoryBit() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x20;
        snes.apu._internalWrite(0x44, 0x00);

        assertEquals(5, snes.apu.BBS(0x44, 7, 2));
        assertEquals(0x20, snes.apu.internalRegisters().pc);

        snes.apu._internalWrite(0x44, 0x04);
        assertEquals(7, snes.apu.BBS(0x44, 7, 2));
        assertEquals(0x27, snes.apu.internalRegisters().pc);

        assertEquals(5, snes.apu.BBC(0x44, 7, 2));
        snes.apu._internalWrite(0x44, 0x00);
        assertEquals(7, snes.apu.BBC(0x44, 7, 2));
        assertEquals(0x2e, snes.apu.internalRegisters().pc);
    }

    @Test
    void cbneBranchesWhenAccumulatorDiffersFromMemory() {
        SNES snes = init();
        snes.apu.internalRegisters().a = 0x42;
        snes.apu.internalRegisters().pc = 0x100;
        snes.apu._internalWrite(0x55, 0x42);

        assertEquals(5, snes.apu.CBNE(0x55, 0x10));
        assertEquals(0x100, snes.apu.internalRegisters().pc);

        snes.apu._internalWrite(0x55, 0x24);
        assertEquals(8, snes.apu.CBNE(0x55, 0x10, true));
        assertEquals(0x110, snes.apu.internalRegisters().pc);
    }

    @Test
    void dbnzDecrementsThenBranchesWhenResultIsNotZero() {
        SNES snes = init();
        snes.apu.internalRegisters().y = 1;
        snes.apu.internalRegisters().pc = 0x100;

        assertEquals(4, snes.apu.DBNZ(0x7f));
        assertEquals(0, snes.apu.internalRegisters().y);
        assertEquals(0x100, snes.apu.internalRegisters().pc);

        snes.apu.internalRegisters().y = 2;
        assertEquals(6, snes.apu.DBNZ(0xff));
        assertEquals(1, snes.apu.internalRegisters().y);
        assertEquals(0x00ff, snes.apu.internalRegisters().pc);

        snes.apu.internalRegisters().pc = 0x20;
        snes.apu._internalWrite(0x20, 0x42);
        snes.apu._internalWrite(0x42, 2);
        assertEquals(7, snes.apu.DBNZ(3, true));
        assertEquals(1, snes.apu._internalRead(0x42));
        assertEquals(0x24, snes.apu.internalRegisters().pc);
    }

    @Test
    void jmpSetsProgramCounterAndReturnsAddressingCycles() {
        SNES snes = init();

        assertEquals(3, snes.apu.JMP(0x4321));
        assertEquals(0x4321, snes.apu.internalRegisters().pc);

        assertEquals(6, snes.apu.JMP(0x1234, true));
        assertEquals(0x1234, snes.apu.internalRegisters().pc);
    }

    private static SNES init() {
        return new SNES(new RecordingVideoSink(), new RecordingAudioSink());
    }
}
