package jamsnes.apu;

import jamsnes.SNES;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubroutineInstructionTest {
    @Test
    void callPushesCurrentProgramCounterAndJumps() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x1234;

        assertEquals(8, snes.apu.CALL(0x5678));

        assertEquals(0x5678, snes.apu.internalRegisters().pc);
        assertEquals(0xed, snes.apu.internalRegisters().sp);
        assertEquals(0x12, snes.apu._internalRead(0x01ef));
        assertEquals(0x34, snes.apu._internalRead(0x01ee));
    }

    @Test
    void pcallJumpsWithinFfPageUsingImmediateByte() {
        SNES snes = init();
        snes.apu._internalWrite(0xffc0, 0x7b);

        assertEquals(6, snes.apu.PCALL());

        assertEquals(0xff7b, snes.apu.internalRegisters().pc);
        assertEquals(0xff, snes.apu._internalRead(0x01ef));
        assertEquals(0xc1, snes.apu._internalRead(0x01ee));
    }

    @Test
    void tcallJumpsToByteReadFromVectorTable() {
        SNES snes = init();
        snes.apu._internalWrite(0xffd0, 45);

        assertEquals(8, snes.apu.TCALL(7));

        assertEquals(45, snes.apu.internalRegisters().pc);
    }

    @Test
    void brkPushesProgramCounterAndPswThenLoadsVector() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0xffee;
        snes.apu.internalRegisters().setPsw(0xdd);
        snes.apu._internalWrite(0xffdf, 0xaa);
        snes.apu._internalWrite(0xffde, 0xbb);

        assertEquals(8, snes.apu.BRK());

        assertEquals(0xaabb, snes.apu.internalRegisters().pc);
        assertFalse(snes.apu.internalRegisters().i);
        assertTrue(snes.apu.internalRegisters().b);
        assertEquals(0xec, snes.apu.internalRegisters().sp);
        assertEquals(0xff, snes.apu._internalRead(0x01ef));
        assertEquals(0xee, snes.apu._internalRead(0x01ee));
        assertEquals(0xdd, snes.apu._internalRead(0x01ed));
    }

    @Test
    void retRestoresProgramCounterFromStack() {
        SNES snes = init();
        snes.apu._internalWrite(0x01f0, 0x12);
        snes.apu._internalWrite(0x01f1, 0x34);

        assertEquals(5, snes.apu.RET());

        assertEquals(0x1234, snes.apu.internalRegisters().pc);
        assertEquals(0xf1, snes.apu.internalRegisters().sp);
    }

    @Test
    void retiRestoresPswAndProgramCounterFromStack() {
        SNES snes = init();
        snes.apu._internalWrite(0x01f0, 0xdd);
        snes.apu._internalWrite(0x01f1, 0x34);
        snes.apu._internalWrite(0x01f2, 0x56);

        assertEquals(6, snes.apu.RETI());

        assertEquals(0xdd, snes.apu.internalRegisters().psw());
        assertEquals(0x3456, snes.apu.internalRegisters().pc);
        assertEquals(0xf2, snes.apu.internalRegisters().sp);
    }

    private static SNES init() {
        return new SNES(new NoRenderer(0, 0, 0));
    }
}
