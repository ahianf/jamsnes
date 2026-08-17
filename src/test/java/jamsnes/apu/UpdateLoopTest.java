package jamsnes.apu;

import jamsnes.SNES;
import jamsnes.audio.RecordingAudioSink;
import jamsnes.video.RecordingVideoSink;
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
        assertEquals(1, snes.apu.dsp().voicePhase());

        snes.apu.update(1);

        assertEquals(0x201, snes.apu.internalRegisters().pc);
        assertEquals(0, snes.apu.paddingCycles());
        assertEquals(2, snes.apu.dsp().voicePhase());
    }

    @Test
    void updateAdvancesOneDspPhaseForEveryElapsedApuCycle() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu._internalWrite(0x200, 0x00);

        snes.apu.update(31);
        assertEquals(31, snes.apu.dsp().voicePhase());

        snes.apu.update(1);
        assertEquals(0, snes.apu.dsp().voicePhase());
    }

    @Test
    void updateAdvancesDspWhilePayingDownInstructionPadding() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu._internalWrite(0x200, 0x3f);
        snes.apu._internalWrite(0x201, 0x00);
        snes.apu._internalWrite(0x202, 0x03);

        snes.apu.update(1);
        assertEquals(7, snes.apu.paddingCycles());
        assertEquals(1, snes.apu.dsp().voicePhase());

        snes.apu.update(1);
        assertEquals(6, snes.apu.paddingCycles());
        assertEquals(2, snes.apu.dsp().voicePhase());
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
    void updateCarriesRemainingSleepInstructionCyclesIntoStandby() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu._internalWrite(0x200, 0xef);

        snes.apu.update(1);

        assertEquals(APU.StateMode.SLEEPING, snes.apu.getState());
        assertEquals(0x201, snes.apu.internalRegisters().pc);
        assertEquals(2, snes.apu.paddingCycles());
        assertEquals(1, snes.apu.dsp().voicePhase());

        snes.apu.update(1);

        assertEquals(1, snes.apu.paddingCycles());
        assertEquals(2, snes.apu.dsp().voicePhase());

        snes.apu.update(1);

        assertEquals(0, snes.apu.paddingCycles());
        assertEquals(3, snes.apu.dsp().voicePhase());
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

    @Test
    void updateAdvancesTimersZeroAndOneEveryOneHundredTwentyEightCycles() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu._internalWrite(0x00fa, 0x02);
        snes.apu._internalWrite(0x00fb, 0x02);
        snes.apu._internalWrite(0x00f1, 0x03);

        snes.apu.update(255);
        assertEquals(0, snes.apu._internalRead(0x00fd));
        assertEquals(0, snes.apu._internalRead(0x00fe));

        snes.apu.update(1);
        assertEquals(1, snes.apu._internalRead(0x00fd));
        assertEquals(1, snes.apu._internalRead(0x00fe));
    }

    @Test
    void updateAdvancesTimerTwoEverySixteenCycles() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu._internalWrite(0x00fc, 0x01);
        snes.apu._internalWrite(0x00f1, 0x04);

        snes.apu.update(15);
        assertEquals(0, snes.apu._internalRead(0x00ff));

        snes.apu.update(1);
        assertEquals(1, snes.apu._internalRead(0x00ff));
    }

    @Test
    void timerTargetZeroActsAsTwoHundredFiftySix() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu._internalWrite(0x00fa, 0x00);
        snes.apu._internalWrite(0x00f1, 0x01);

        snes.apu.update(128 * 255);
        assertEquals(0, snes.apu._internalRead(0x00fd));

        snes.apu.update(128);
        assertEquals(1, snes.apu._internalRead(0x00fd));
    }

    @Test
    void disabledTimerKeepsFreeRunningDividerPhase() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu._internalWrite(0x00fa, 0x01);

        snes.apu.update(64);
        snes.apu._internalWrite(0x00f1, 0x01);
        snes.apu.update(63);
        assertEquals(0, snes.apu._internalRead(0x00fd));

        snes.apu.update(1);
        assertEquals(1, snes.apu._internalRead(0x00fd));
    }

    @Test
    void testRegisterGatesTimersWhileTheirDividersKeepRunning() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu._internalWrite(0x00fa, 0x01);
        snes.apu._internalWrite(0x00f1, 0x01);

        snes.apu._internalWrite(0x00f0, 0x0b);
        snes.apu.update(128);
        assertEquals(0, snes.apu._internalRead(0x00fd));

        snes.apu._internalWrite(0x00f0, 0x02);
        snes.apu.update(128);
        assertEquals(0, snes.apu._internalRead(0x00fd));

        snes.apu._internalWrite(0x00f0, 0x0a);
        snes.apu.update(127);
        assertEquals(0, snes.apu._internalRead(0x00fd));

        snes.apu.update(1);
        assertEquals(1, snes.apu._internalRead(0x00fd));
    }

    @Test
    void enablingTimerResetsTargetStageAndCounterButPreservesDividerPhase() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu._internalWrite(0x00fa, 0x02);
        snes.apu._internalWrite(0x00f1, 0x01);
        snes.apu.update(128);
        snes.apu.counters()[0] = 0x07;

        snes.apu._internalWrite(0x00f1, 0x00);
        snes.apu.update(64);
        snes.apu._internalWrite(0x00f1, 0x01);
        snes.apu.update(63);
        assertEquals(0, snes.apu._internalRead(0x00fd));

        snes.apu.update(1);
        assertEquals(0, snes.apu._internalRead(0x00fd));
        snes.apu.update(128);
        assertEquals(1, snes.apu._internalRead(0x00fd));
    }

    @Test
    void timerDisableInstructionTakesEffectWithinUpdateSlice() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x0300;
        snes.apu._internalWrite(0x00fa, 0x01);
        snes.apu._internalWrite(0x00f1, 0x01);
        snes.apu.update(120);

        snes.apu.internalRegisters().pc = 0x0200;
        snes.apu._internalWrite(0x0200, 0x8f);
        snes.apu._internalWrite(0x0201, 0x00);
        snes.apu._internalWrite(0x0202, 0xf1);

        snes.apu.update(130);

        assertEquals(0, snes.apu._internalRead(0x00fd));
    }

    @Test
    void timerEnableInstructionTakesEffectWithinUpdateSlice() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x0300;
        snes.apu._internalWrite(0x00fa, 0x01);
        snes.apu.update(120);

        snes.apu.internalRegisters().pc = 0x0200;
        snes.apu._internalWrite(0x0200, 0x8f);
        snes.apu._internalWrite(0x0201, 0x01);
        snes.apu._internalWrite(0x0202, 0xf1);

        snes.apu.update(130);

        assertEquals(1, snes.apu._internalRead(0x00fd));
    }

    private static SNES init() {
        return new SNES(new RecordingVideoSink(), new RecordingAudioSink());
    }
}
