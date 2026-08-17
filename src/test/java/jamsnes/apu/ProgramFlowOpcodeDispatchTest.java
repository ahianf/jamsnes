package jamsnes.apu;

import jamsnes.SNES;
import jamsnes.audio.RecordingAudioSink;
import jamsnes.video.RecordingVideoSink;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgramFlowOpcodeDispatchTest {
    @Test
    void executesConditionalBranchOpcodes() {
        assertBranch(0x10, registers -> registers.n = false, true);
        assertBranch(0x10, registers -> registers.n = true, false);
        assertBranch(0x30, registers -> registers.n = true, true);
        assertBranch(0x30, registers -> registers.n = false, false);
        assertBranch(0x50, registers -> registers.v = false, true);
        assertBranch(0x50, registers -> registers.v = true, false);
        assertBranch(0x70, registers -> registers.v = true, true);
        assertBranch(0x70, registers -> registers.v = false, false);
        assertBranch(0x90, registers -> registers.c = false, true);
        assertBranch(0x90, registers -> registers.c = true, false);
        assertBranch(0xb0, registers -> registers.c = true, true);
        assertBranch(0xb0, registers -> registers.c = false, false);
        assertBranch(0xd0, registers -> registers.z = false, true);
        assertBranch(0xd0, registers -> registers.z = true, false);
        assertBranch(0xf0, registers -> registers.z = true, true);
        assertBranch(0xf0, registers -> registers.z = false, false);
    }

    @Test
    void executesCbneOpcodes() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x0200;
        snes.apu.internalRegisters().a = 0x10;
        snes.apu._internalWrite(0x44, 0x20);
        snes.apu._internalWrite(0x0200, 0x2e);
        snes.apu._internalWrite(0x0201, 0x44);
        snes.apu._internalWrite(0x0202, 0x03);

        assertEquals(7, snes.apu.executeInstruction());
        assertEquals(0x0206, snes.apu.internalRegisters().pc);

        snes = init();
        snes.apu.internalRegisters().pc = 0x0200;
        snes.apu.internalRegisters().a = 0x10;
        snes.apu.internalRegisters().x = 0x02;
        snes.apu._internalWrite(0x42, 0x20);
        snes.apu._internalWrite(0x0200, 0xde);
        snes.apu._internalWrite(0x0201, 0x40);
        snes.apu._internalWrite(0x0202, 0x03);

        assertEquals(8, snes.apu.executeInstruction());
        assertEquals(0x0206, snes.apu.internalRegisters().pc);
    }

    @Test
    void executesYDbnzOpcode() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x0200;
        snes.apu.internalRegisters().y = 0x02;
        snes.apu._internalWrite(0x0200, 0xfe);
        snes.apu._internalWrite(0x0201, 0x03);

        assertEquals(6, snes.apu.executeInstruction());
        assertEquals(0x01, snes.apu.internalRegisters().y);
        assertEquals(0x0205, snes.apu.internalRegisters().pc);

        snes = init();
        snes.apu.internalRegisters().pc = 0x0200;
        snes.apu.internalRegisters().y = 0x01;
        snes.apu._internalWrite(0x0200, 0xfe);
        snes.apu._internalWrite(0x0201, 0x03);

        assertEquals(4, snes.apu.executeInstruction());
        assertEquals(0x00, snes.apu.internalRegisters().y);
        assertEquals(0x0202, snes.apu.internalRegisters().pc);
    }

    @Test
    void executesJmpOpcodes() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x0200;
        snes.apu._internalWrite(0x0200, 0x5f);
        writeAbsoluteOperand(snes, 0x0201, 0x1234);

        assertEquals(3, snes.apu.executeInstruction());
        assertEquals(0x1234, snes.apu.internalRegisters().pc);

        snes = init();
        snes.apu.internalRegisters().pc = 0x0200;
        snes.apu.internalRegisters().x = 0x02;
        snes.apu._internalWrite(0x0200, 0x1f);
        writeAbsoluteOperand(snes, 0x0201, 0x0300);
        snes.apu._internalWrite(0x0302, 0x78);
        snes.apu._internalWrite(0x0303, 0x56);

        assertEquals(6, snes.apu.executeInstruction());
        assertEquals(0x5678, snes.apu.internalRegisters().pc);
    }

    private static void assertBranch(int opcode, Consumer<APURegisters> setup, boolean taken) {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x0200;
        setup.accept(snes.apu.internalRegisters());
        snes.apu._internalWrite(0x0200, opcode);
        snes.apu._internalWrite(0x0201, 0x05);

        assertEquals(taken ? 4 : 2, snes.apu.executeInstruction());
        assertEquals(taken ? 0x0207 : 0x0202, snes.apu.internalRegisters().pc);
    }

    private static void writeAbsoluteOperand(SNES snes, int pc, int address) {
        snes.apu._internalWrite(pc, address);
        snes.apu._internalWrite(pc + 1, address >>> 8);
    }

    private static SNES init() {
        return new SNES(new RecordingVideoSink(), new RecordingAudioSink());
    }
}
