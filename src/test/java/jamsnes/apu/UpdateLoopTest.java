package jamsnes.apu;

import jamsnes.SNES;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpdateLoopTest {
    @Test
    void updateRunsInstructionsUntilCycleBudgetIsMet() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu._internalWrite(0x200, 0x00);
        snes.apu._internalWrite(0x201, 0x00);

        snes.apu.update(2);

        assertEquals(0x201, snes.apu.internalRegisters().pc);
        assertEquals(0, snes.apu.paddingCycles());
    }

    @Test
    void updateCarriesExcessCyclesIntoNextCall() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu._internalWrite(0x200, 0x00);
        snes.apu._internalWrite(0x201, 0x00);

        snes.apu.update(1);

        assertEquals(0x201, snes.apu.internalRegisters().pc);
        assertEquals(1, snes.apu.paddingCycles());

        snes.apu.update(1);

        assertEquals(0x201, snes.apu.internalRegisters().pc);
        assertEquals(0, snes.apu.paddingCycles());
    }

    @Test
    void updateStopsWhenApuLeavesRunningState() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu._internalWrite(0x200, 0xef);
        snes.apu._internalWrite(0x201, 0x00);

        snes.apu.update(10);

        assertEquals(APU.StateMode.SLEEPING, snes.apu.getState());
        assertEquals(0x201, snes.apu.internalRegisters().pc);
        assertEquals(0, snes.apu.paddingCycles());
    }

    @Test
    void disabledApuDoesNotExecuteInstructions() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu._internalWrite(0x200, 0x00);
        snes.apu.isDisabled = true;

        snes.apu.update(20);

        assertEquals(0x200, snes.apu.internalRegisters().pc);
    }

    private static SNES init() {
        return new SNES(new NoRenderer(0, 0, 0));
    }
}
