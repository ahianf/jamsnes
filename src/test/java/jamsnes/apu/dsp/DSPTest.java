package jamsnes.apu.dsp;

import jamsnes.SNES;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DSPTest {
    @Test
    void timerTickReloadsAndDecrementsCounter() {
        DSP dsp = new DSP();

        dsp.timerTick();

        assertEquals(0x77ff, dsp.timerCounter());

        dsp.timerTick();
        assertEquals(0x77fe, dsp.timerCounter());
    }

    @Test
    void timerPollUsesRateTables() {
        DSP dsp = new DSP();

        assertFalse(dsp.timerPoll(0));
        assertTrue(dsp.timerPoll(1));
        assertTrue(dsp.timerPoll(31));

        dsp.timerTick();

        assertTrue(dsp.timerPoll(31));
        assertFalse(dsp.timerPoll(1));
    }

    @Test
    void updateAdvancesThirtyTwoPhaseStateAndRunsMiscTimerPhases() {
        DSP dsp = new DSP();

        for (int i = 0; i < 30; i++) {
            dsp.update();
        }

        assertEquals(30, dsp.voicePhase());
        assertFalse(dsp.timerSample());
        assertEquals(0, dsp.timerCounter());

        dsp.update();

        assertEquals(31, dsp.voicePhase());
        assertEquals(0x77ff, dsp.timerCounter());

        dsp.update();
        assertEquals(0, dsp.voicePhase());
    }

    @Test
    void updatePollsNoiseClockOnMiscThirty() {
        DSP dsp = new DSP();
        dsp.write(0x6c, 0b0010_1111);

        for (int i = 0; i < 31 + 55 * 32; i++) {
            dsp.update();
        }

        assertEquals(0x2000, dsp.noiseLfsr());
    }

    @Test
    void apuUpdateTicksDspAfterApuWork() {
        SNES snes = new SNES(new NoRenderer(0, 0, 0));

        snes.apu.update(0);

        assertEquals(1, snes.apu.dsp().voicePhase());
    }

    @Test
    void releaseEnvelopeFallsToZero() {
        DSP dsp = new DSP();
        dsp.setVoiceEnvelopeState(0, 0x04, 0, DSP.EnvelopeMode.RELEASE);

        dsp.runEnvelope(0);

        assertEquals(0, dsp.voiceEnvelope(0));
    }

    @Test
    void adsrAttackClampsAndTransitionsToDecay() {
        DSP dsp = new DSP();
        dsp.write(0x05, 0x8f);
        dsp.setVoiceEnvelopeState(0, 0x400, 0, DSP.EnvelopeMode.ATTACK);

        dsp.runEnvelope(0);

        assertEquals(0x7ff, dsp.voiceEnvelope(0));
        assertEquals(0x800, dsp.voiceHiddenEnvelope(0));
        assertEquals(DSP.EnvelopeMode.DECAY, dsp.voiceEnvelopeMode(0));
    }

    @Test
    void adsrDecayCanTransitionToSustain() {
        DSP dsp = new DSP();
        dsp.write(0x05, 0x80);
        dsp.write(0x06, 0x40);
        dsp.setVoiceEnvelopeState(0, 0x300, 0, DSP.EnvelopeMode.DECAY);

        dsp.runEnvelope(0);

        assertEquals(0x2fd, dsp.voiceEnvelope(0));
        assertEquals(0x2fd, dsp.voiceHiddenEnvelope(0));
        assertEquals(DSP.EnvelopeMode.SUSTAIN, dsp.voiceEnvelopeMode(0));
    }

    @Test
    void gainDirectModeSetsEnvelopeFromGainData() {
        DSP dsp = new DSP();
        dsp.write(0x07, 0x1f);
        dsp.setVoiceEnvelopeState(0, 0, 0, DSP.EnvelopeMode.SUSTAIN);

        dsp.runEnvelope(0);

        assertEquals(0x1f0, dsp.voiceEnvelope(0));
        assertEquals(0x1f0, dsp.voiceHiddenEnvelope(0));
    }

    @Test
    void defaultDspRamReadWriteWrapsToSixteenBits() {
        DSP dsp = new DSP();

        dsp.writeRam(0x10001, 0x12);

        assertEquals(0x12, dsp.readRam(0x0001));
        assertEquals(0x12, dsp.readRam(0x10001));
    }

    @Test
    void decodeBrrReadsNibblesIntoVoiceSamples() {
        DSP dsp = new DSP();
        dsp.setBrrState(0x00, 0x12);
        dsp.setVoiceBrrState(0, 0x2000, 1, 0);
        dsp.writeRam(0x2002, 0x34);

        dsp.decodeBRR(0);

        assertEquals(0, dsp.voiceSample(0, 0));
        assertEquals(18, dsp.voiceSample(0, 1));
        assertEquals(32, dsp.voiceSample(0, 2));
        assertEquals(32, dsp.voiceSample(0, 3));
        assertEquals(4, dsp.voiceSampleOffset(0));
    }

    @Test
    void decodeBrrAppliesPreviousSampleFilterAndWrapsOffset() {
        DSP dsp = new DSP();
        dsp.setBrrState(0x04, 0xf0);
        dsp.setVoiceBrrState(0, 0x3000, 1, 10);
        dsp.setVoiceSample(0, 9, 16);
        dsp.writeRam(0x3002, 0x00);

        dsp.decodeBRR(0);

        assertEquals(32, dsp.voiceSample(0, 10));
        assertEquals(32, dsp.voiceSample(0, 11));
        assertEquals(32, dsp.voiceSample(0, 0));
        assertEquals(32, dsp.voiceSample(0, 1));
        assertEquals(2, dsp.voiceSampleOffset(0));
    }

    @Test
    void apuBackedDspReadsApuRamForBrrDecode() {
        SNES snes = new SNES(new NoRenderer(0, 0, 0));
        snes.apu._internalWrite(0x4002, 0x34);
        snes.apu.dsp().setBrrState(0x00, 0x12);
        snes.apu.dsp().setVoiceBrrState(0, 0x4000, 1, 0);

        snes.apu.dsp().decodeBRR(0);

        assertEquals(32, snes.apu.dsp().voiceSample(0, 3));
    }

    @Test
    void interpolateUsesGaussianTableAndRoundsToEvenSample() {
        DSP dsp = new DSP();
        dsp.setVoiceBrrState(0, 0, 1, 0);
        dsp.setVoiceSample(0, 0, 8);
        dsp.setVoiceSample(0, 1, 8);
        dsp.setVoiceSample(0, 2, 8);
        dsp.setVoiceSample(0, 3, 8);
        dsp.setVoiceGaussOffset(0, 0);

        assertEquals(6, dsp.interpolate(0));
    }

    @Test
    void interpolateWrapsVoiceSampleRingFromGaussOffset() {
        DSP dsp = new DSP();
        dsp.setVoiceBrrState(0, 0, 1, 11);
        dsp.setVoiceSample(0, 0, 8);
        dsp.setVoiceSample(0, 1, 8);
        dsp.setVoiceSample(0, 2, 8);
        dsp.setVoiceSample(0, 3, 8);
        dsp.setVoiceGaussOffset(0, 0x1000);

        assertEquals(6, dsp.interpolate(0));
    }

    @Test
    void voicePhasesLoadBrrDirectoryAndHeaderState() {
        DSP dsp = new DSP();
        dsp.setBrrDirectoryState(0x12, 0x05, 0, 0);
        dsp.write(0x04, 0x07);
        dsp.write(0x05, 0x8f);
        dsp.write(0x02, 0x34);
        dsp.write(0x03, 0x12);
        dsp.writeRam(0x1216, 0xcd);
        dsp.writeRam(0x1217, 0xab);
        dsp.writeRam(0x1214, 0x60);
        dsp.writeRam(0x1215, 0xee);

        dsp.voice1(0);
        dsp.voice2(0);
        dsp.voice3a(0);
        dsp.voice3b(0);

        assertEquals(0x1214, dsp.brrAddress());
        assertEquals(0x07, dsp.brrSource());
        assertEquals(0xabcd, dsp.brrNextAddress());
        assertEquals(0x1234, dsp.latchPitch());
        assertEquals(0x60, dsp.brrHeader());
        assertEquals(0xee, dsp.brrValue());
    }

    @Test
    void voiceFourDecodesBrrAndAdvancesGaussOffset() {
        DSP dsp = new DSP();
        dsp.setBrrState(0x00, 0x12);
        dsp.setVoiceBrrState(0, 0x2000, 1, 0);
        dsp.setVoiceGaussOffset(0, 0x4000);
        dsp.setLatchState(0x0111, 0);
        dsp.writeRam(0x2002, 0x34);

        dsp.voice4(0);

        assertEquals(4, dsp.voiceSampleOffset(0));
        assertEquals(3, dsp.voiceBrrOffset(0));
        assertEquals(0x0111, dsp.voiceGaussOffset(0));
    }

    @Test
    void voiceFiveThroughNineTransferLatchedOutputAndEnvelope() {
        DSP dsp = new DSP();
        dsp.setLatchState(0, 0x1234);
        dsp.voice6(0);
        dsp.voice8(0);
        dsp.write(0x08, 0x2a);
        dsp.voice7(0);
        dsp.voice9(1);

        assertEquals(0x12, dsp.voiceOutx(0));
        assertEquals(0x2a, dsp.read(0x18));

        dsp.setVoiceRuntimeState(0, 0, true, false, false, false);
        dsp.voice5(0);
        assertTrue(dsp.voiceEndx(0));

        dsp.setVoiceRuntimeState(0, 5, true, false, false, false);
        dsp.voice5(0);
        assertFalse(dsp.voiceEndx(0));
    }
}
