package jamsnes.apu;

import jamsnes.SNES;
import jamsnes.renderer.TestFrontend;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateAndPswInstructionTest {
    @Test
    void resetClearsPriorProgramStatus() {
        SNES snes = init();
        snes.apu.internalRegisters().setPsw(0xff);

        snes.apu.reset();

        assertEquals(0x00, snes.apu.internalRegisters().psw());
        assertEquals(0xffc0, snes.apu.internalRegisters().pc);
        assertEquals(0xef, snes.apu.internalRegisters().sp);
    }

    @Test
    void standbyInstructionsReturnCyclesAndSetState() {
        SNES snes = init();

        assertEquals(2, snes.apu.NOP());

        assertEquals(3, snes.apu.SLEEP());
        assertEquals(APU.StateMode.SLEEPING, snes.apu.getState());

        assertEquals(3, snes.apu.STOP());
        assertEquals(APU.StateMode.STOPPED, snes.apu.getState());
    }

    @Test
    void carryStatusInstructionsUpdateCarry() {
        SNES snes = init();

        assertEquals(2, snes.apu.SETC());
        assertTrue(snes.apu.internalRegisters().c);

        assertEquals(3, snes.apu.NOTC());
        assertFalse(snes.apu.internalRegisters().c);

        assertEquals(2, snes.apu.CLRC());
        assertFalse(snes.apu.internalRegisters().c);
    }

    @Test
    void otherStatusInstructionsUpdateFlags() {
        SNES snes = init();
        snes.apu.internalRegisters().v = true;
        snes.apu.internalRegisters().h = true;

        assertEquals(2, snes.apu.CLRV());
        assertFalse(snes.apu.internalRegisters().v);
        assertFalse(snes.apu.internalRegisters().h);

        assertEquals(2, snes.apu.SETP());
        assertTrue(snes.apu.internalRegisters().p);

        assertEquals(2, snes.apu.CLRP());
        assertFalse(snes.apu.internalRegisters().p);

        assertEquals(3, snes.apu.EI());
        assertTrue(snes.apu.internalRegisters().i);

        assertEquals(3, snes.apu.DI());
        assertFalse(snes.apu.internalRegisters().i);
    }

    private static SNES init() {
        return new SNES(new TestFrontend(0, 0, 0));
    }
}
