package jamsnes.apu.dsp;

import jamsnes.SNES;
import jamsnes.renderer.IRenderer;
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
    void resetRestoresPowerOnDspFlags() {
        DSP dsp = new DSP();
        dsp.write(0x6c, 0x00);

        dsp.reset();

        assertEquals(0xe0, dsp.read(0x6c));
    }

    @Test
    void envxAndOutxRegistersRemainIndependentPerVoice() {
        DSP dsp = new DSP();

        dsp.write(0x08, 0x12);
        dsp.write(0x18, 0x34);
        dsp.write(0x09, 0x56);
        dsp.write(0x19, 0x78);

        assertEquals(0x12, dsp.read(0x08));
        assertEquals(0x34, dsp.read(0x18));
        assertEquals(0x56, dsp.read(0x09));
        assertEquals(0x78, dsp.read(0x19));
    }

    @Test
    void flagsRegisterPreservesTheFiveBitNoiseClock() {
        DSP dsp = new DSP();

        dsp.write(0x6c, 0x1f);

        assertEquals(0x1f, dsp.read(0x6c));
    }

    @Test
    void registerFileIncludesAddressSevenF() {
        DSP dsp = new DSP();

        assertEquals(0x80, dsp.getSize());
    }

    @Test
    void unusedRegisterSlotsRoundTripStoredValues() {
        DSP dsp = new DSP();

        dsp.write(0x0a, 0x12);
        dsp.write(0x3b, 0x34);
        dsp.write(0x7e, 0x56);

        assertEquals(0x12, dsp.read(0x0a));
        assertEquals(0x34, dsp.read(0x3b));
        assertEquals(0x56, dsp.read(0x7e));
    }

    @Test
    void keyOnRegisterIsLatchedOnTheEveryOtherSamplePoll() {
        DSP dsp = new DSP();
        dsp.write(0x4c, 0x01);

        for (int i = 0; i < 63; i++) {
            dsp.update();
        }

        assertEquals(5, dsp.voiceKonDelay(0));
        assertEquals(DSP.EnvelopeMode.ATTACK, dsp.voiceEnvelopeMode(0));

        for (int i = 0; i < 32; i++) {
            dsp.update();
        }
        assertEquals(4, dsp.voiceKonDelay(0));

        for (int i = 0; i < 32; i++) {
            dsp.update();
        }
        assertEquals(3, dsp.voiceKonDelay(0));
        assertEquals(0, dsp.read(0x4c));
    }

    @Test
    void keyOffRegisterRemainsAssertedUntilSoftwareClearsIt() {
        DSP dsp = new DSP();
        dsp.setVoiceEnvelopeState(0, 0x400, 0x400, DSP.EnvelopeMode.SUSTAIN);
        dsp.write(0x5c, 0x01);

        for (int i = 0; i < 63; i++) {
            dsp.update();
        }

        assertEquals(DSP.EnvelopeMode.RELEASE, dsp.voiceEnvelopeMode(0));
        assertEquals(0x01, dsp.read(0x5c));

        dsp.write(0x5c, 0x00);
        for (int i = 0; i < 64; i++) {
            dsp.update();
        }

        assertEquals(0x00, dsp.read(0x5c));
        assertFalse(dsp.voiceKeyOffLatched(0));
    }

    @Test
    void noiseEchoAndPitchModulationFlagsLatchAtTheirPipelinePhases() {
        DSP dsp = new DSP();
        dsp.write(0x2d, 0x03);
        dsp.write(0x3d, 0x01);
        dsp.write(0x4d, 0x01);

        for (int i = 0; i < 29; i++) {
            dsp.update();
        }

        assertFalse(dsp.voicePitchModulationLatched(0));
        assertTrue(dsp.voicePitchModulationLatched(1));
        assertTrue(dsp.voiceNoiseLatched(0));
        assertTrue(dsp.voiceEchoLatched(0));
    }

    @Test
    void zeroCycleApuUpdateDoesNotTickDsp() {
        SNES snes = new SNES(new NoRenderer(0, 0, 0));

        snes.apu.update(0);

        assertEquals(0, snes.apu.dsp().voicePhase());
    }

    @Test
    void apuBackedDspPlaysBufferedAudioThroughRenderer() {
        TestRenderer renderer = new TestRenderer();
        SNES snes = new SNES(renderer);
        snes.apu.dsp().write(0x6c, 0x00);
        snes.apu.dsp().setMasterOutput(0, 0x1234);
        snes.apu.dsp().setEchoOutput(1, 0x0055);
        snes.apu.dsp().write(0x0c, 0x7f);
        snes.apu.dsp().write(0x1c, 0x7f);

        for (int i = 0; i < 28; i++) {
            snes.apu.dsp().update();
        }

        assertEquals(1, renderer.playAudioCalls);
        assertEquals(2, renderer.lastAudioSamples.length);
        assertEquals(4623, renderer.lastAudioSamples[0]);
        assertEquals(0, renderer.lastAudioSamples[1]);
    }

    @Test
    void apuBackedDspDoesNotReplayBufferedAudioWithoutNewSamples() {
        TestRenderer renderer = new TestRenderer();
        SNES snes = new SNES(renderer);
        snes.apu.dsp().write(0x6c, 0x00);
        snes.apu.dsp().setMasterOutput(0, 0x1234);
        snes.apu.dsp().write(0x0c, 0x7f);

        for (int i = 0; i < 28; i++) {
            snes.apu.dsp().update();
        }
        for (int i = 0; i < 4; i++) {
            snes.apu.dsp().update();
        }

        assertEquals(1, renderer.playAudioCalls);
        assertEquals(0, snes.apu.dsp().getSamplesCount());
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
        assertEquals(2, dsp.voiceSample(0, 1));
        assertEquals(2, dsp.voiceSample(0, 2));
        assertEquals(4, dsp.voiceSample(0, 3));
        assertEquals(4, dsp.voiceSampleOffset(0));
    }

    @Test
    void decodeBrrSignExtendsNibblesAndAppliesFilterOne() {
        DSP dsp = new DSP();
        dsp.setBrrState(0x04, 0xf0);
        dsp.setVoiceBrrState(0, 0x3000, 1, 10);
        dsp.setVoiceSample(0, 9, 16);
        dsp.writeRam(0x3002, 0x00);

        dsp.decodeBRR(0);

        assertEquals(12, dsp.voiceSample(0, 10));
        assertEquals(10, dsp.voiceSample(0, 11));
        assertEquals(8, dsp.voiceSample(0, 0));
        assertEquals(6, dsp.voiceSample(0, 1));
        assertEquals(2, dsp.voiceSampleOffset(0));
    }

    @Test
    void decodeBrrInvalidNegativeRangeProducesSignedSample() {
        DSP dsp = new DSP();
        dsp.setBrrState(0xe0, 0xf0);
        dsp.setVoiceBrrState(0, 0x3000, 1, 0);
        dsp.writeRam(0x3002, 0x00);

        dsp.decodeBRR(0);

        assertEquals(-4096, dsp.voiceSample(0, 0));
        assertEquals(0, dsp.voiceSample(0, 1));
    }

    @Test
    void apuBackedDspReadsApuRamForBrrDecode() {
        SNES snes = new SNES(new NoRenderer(0, 0, 0));
        snes.apu._internalWrite(0x4002, 0x34);
        snes.apu.dsp().setBrrState(0x00, 0x12);
        snes.apu.dsp().setVoiceBrrState(0, 0x4000, 1, 0);

        snes.apu.dsp().decodeBRR(0);

        assertEquals(4, snes.apu.dsp().voiceSample(0, 3));
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
    void interpolatePreservesSignedSamplesAndHardwareIntermediateOverflow() {
        DSP dsp = new DSP();
        dsp.setVoiceBrrState(0, 0, 1, 0);
        for (int i = 0; i < 4; i++) {
            dsp.setVoiceSample(0, i, -8192);
        }

        assertTrue(dsp.interpolate(0) < 0);

        for (int i = 0; i < 4; i++) {
            dsp.setVoiceSample(0, i, 32767);
        }
        assertEquals(-32756, dsp.interpolate(0));
    }

    @Test
    void voicePhasesLoadBrrDirectoryPointersAndCurrentBlockState() {
        DSP dsp = new DSP();
        dsp.setBrrDirectoryState(0x12, 0x05, 0, 0);
        dsp.setVoiceBrrState(0, 0x2000, 3, 0);
        dsp.write(0x04, 0x07);
        dsp.write(0x05, 0x8f);
        dsp.write(0x02, 0x34);
        dsp.write(0x03, 0x12);
        dsp.writeRam(0x1216, 0xcd);
        dsp.writeRam(0x1217, 0xab);
        dsp.writeRam(0x2000, 0x60);
        dsp.writeRam(0x2003, 0xee);

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
    void voiceFourAdvancesToTheNextBrrBlock() {
        DSP dsp = new DSP();
        dsp.setBrrState(0x00, 0x00);
        dsp.setVoiceBrrState(0, 0x2000, 7, 0);
        dsp.setVoiceGaussOffset(0, 0x4000);

        dsp.voice4(0);

        assertEquals(0x2009, dsp.voiceBrrAddress(0));
        assertEquals(1, dsp.voiceBrrOffset(0));
    }

    @Test
    void voiceMixUsesSignedVolumeAndSaturatesMainAndEchoTotals() {
        DSP dsp = new DSP();
        dsp.write(0x00, 0x80);
        dsp.setLatchState(0, 0x4000);
        dsp.setVoiceRuntimeState(0, 0, false, true, false, false);

        dsp.voice4(0);

        assertEquals(-16384, dsp.masterOutput(0));
        assertEquals(-16384, dsp.echoOutput(0));

        dsp.write(0x00, 0x7f);
        dsp.setLatchState(0, 0x7ffe);
        dsp.voice4(0);
        dsp.voice4(0);
        dsp.voice4(0);

        assertEquals(32767, dsp.masterOutput(0));
        assertEquals(32767, dsp.echoOutput(0));
    }

    @Test
    void noiseSourceIsTreatedAsSignedBeforeApplyingTheEnvelope() {
        DSP dsp = new DSP();
        dsp.write(0x3d, 0x01);
        dsp.setVoiceEnvelopeState(0, 0x7ff, 0x7ff, DSP.EnvelopeMode.SUSTAIN);
        for (int i = 0; i < 29; i++) {
            dsp.update();
        }

        dsp.voice3c(0);

        assertEquals(-32752, dsp.latchOutput());
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

    @Test
    void endxWriteClearsSampleEndFlags() {
        DSP dsp = new DSP();
        dsp.setVoiceRuntimeState(0, 0, true, false, false, false);
        dsp.setVoiceRuntimeState(1, 0, true, false, false, false);
        dsp.voice5(0);
        dsp.voice5(1);
        assertEquals(0x03, dsp.read(0x7c));

        dsp.write(0x7c, 0xff);

        assertEquals(0x00, dsp.read(0x7c));
        assertFalse(dsp.voiceEndx(0));
        assertFalse(dsp.voiceEndx(1));
    }

    @Test
    void loadEchoReadsSignedSampleFromRam() {
        DSP dsp = new DSP();
        dsp.setEchoRuntimeState(0x3000, 0, 0, 0, 0, false);
        dsp.writeRam(0x3000, 0x00);
        dsp.writeRam(0x3001, 0xf0);

        dsp.loadEcho(0);

        assertEquals(-2048, dsp.echoHistory(0, 0));
    }

    @Test
    void echoHistoryWrapsAfterEightStereoSamples() {
        DSP dsp = new DSP();
        dsp.setEchoRuntimeState(0, 0, 0, 7, 0x30, false);
        dsp.writeRam(0x3000, 0x34);
        dsp.writeRam(0x3001, 0x12);

        dsp.echo22();

        assertEquals(0, dsp.echoHistoryOffset());
        assertEquals(0x091a, dsp.echoHistory(0, 0));
    }

    @Test
    void echoDelayUsesOnlyTheLowFourRegisterBits() {
        DSP dsp = new DSP();
        dsp.write(0x7d, 0xf2);
        dsp.setEchoRuntimeState(0, 0, 0, 0, 0, false);

        dsp.echo29();

        assertEquals(0xf2, dsp.read(0x7d));
        assertEquals(0x1000, dsp.echoLength());
        assertEquals(4, dsp.echoOffset());
    }

    @Test
    void writeEchoWritesSignedSampleAndClearsOutput() {
        DSP dsp = new DSP();
        dsp.setEchoRuntimeState(0x3000, 0, 0, 0, 0, false);
        dsp.setEchoOutput(1, 0xf234);

        dsp.writeEcho(1);

        assertEquals(0x34, dsp.readRam(0x3002));
        assertEquals(0xf2, dsp.readRam(0x3003));
        assertEquals(0, dsp.echoOutput(1));
    }

    @Test
    void writeEchoDoesNotWriteRamWhenToggled() {
        DSP dsp = new DSP();
        dsp.setEchoRuntimeState(0x3000, 0, 0, 0, 0, true);
        dsp.writeRam(0x3000, 0xaa);
        dsp.writeRam(0x3001, 0xbb);
        dsp.setEchoOutput(0, 0x1234);

        dsp.writeEcho(0);

        assertEquals(0xaa, dsp.readRam(0x3000));
        assertEquals(0xbb, dsp.readRam(0x3001));
        assertEquals(0, dsp.echoOutput(0));
    }

    @Test
    void echoOutputUsesSignedMasterAndEchoVolumeRegisters() {
        DSP dsp = new DSP();
        dsp.write(0x1c, 0x40);
        dsp.write(0x3c, 0xc0);
        dsp.setMasterOutput(1, 0x0100);
        dsp.setEchoInput(1, 0x0800);

        assertEquals(-896, dsp.outputEcho(1));
    }

    @Test
    void echoFeedbackUsesSignedVolumeAndSaturates() {
        DSP dsp = new DSP();
        dsp.write(0x0d, 0x7f);
        dsp.setEchoInput(0, 0x4000);
        dsp.setEchoOutput(0, 0x7000);

        dsp.echo26();

        assertEquals(32766, dsp.echoOutput(0));
    }

    @Test
    void firCoefficientsAreSigned() {
        DSP dsp = new DSP();
        dsp.write(0x0f, 0x80);
        dsp.setEchoHistory(0, 1, 0x1000);

        assertEquals(-8192, dsp.loadFIR(0, 0));
    }

    @Test
    void echoTwentySevenWritesStereoSamplesAndClearsMasterOutput() {
        DSP dsp = new DSP();
        dsp.write(0x1c, 0x40);
        dsp.write(0x3c, 0x40);
        dsp.setMasterOutput(0, 0x1234);
        dsp.setMasterOutput(1, 0x0100);
        dsp.setEchoInput(1, 0x20);

        dsp.echo27();

        assertEquals(0x1234, dsp.soundBuffer()[0]);
        assertEquals(144, dsp.soundBuffer()[1]);
        assertEquals(2, dsp.getSamplesCount());
        assertEquals(0, dsp.masterOutput(0));
        assertEquals(0, dsp.masterOutput(1));
    }

    @Test
    void updateRunsScheduledVoiceOnePhase() {
        DSP dsp = new DSP();
        dsp.setBrrDirectoryState(0x12, 0, 0, 0);
        dsp.write(0x74, 0x05);
        dsp.write(0x04, 0x07);

        for (int i = 0; i < 18; i++) {
            dsp.update();
        }

        assertEquals(0x1214, dsp.brrAddress());
        assertEquals(0x07, dsp.brrSource());
    }

    @Test
    void updateRunsScheduledEchoOutputPhase() {
        DSP dsp = new DSP();
        dsp.write(0x0c, 0x7f);
        dsp.setMasterOutput(0, 0x2222);

        for (int i = 0; i < 28; i++) {
            dsp.update();
        }

        assertEquals(8669, dsp.soundBuffer()[0]);
        assertEquals(2, dsp.getSamplesCount());
        assertEquals(0, dsp.masterOutput(0));
    }

    private static final class TestRenderer implements IRenderer {
        private int playAudioCalls;
        private short[] lastAudioSamples = new short[0];

        @Override
        public void setWindowName(String newWindowName) {
        }

        @Override
        public void drawScreen() {
        }

        @Override
        public void putPixel(int y, int x, int rgba) {
        }

        @Override
        public void createWindow(SNES snes, int maxFPS) {
        }

        @Override
        public void playAudio(short[] samples) {
            playAudioCalls++;
            lastAudioSamples = samples;
        }
    }
}
