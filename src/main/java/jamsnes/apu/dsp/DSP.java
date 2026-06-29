package jamsnes.apu.dsp;

import jamsnes.exceptions.InvalidAddress;

import static jamsnes.models.Unsigned.u8;

public class DSP {
    enum EnvelopeMode {
        RELEASE,
        ATTACK,
        DECAY,
        SUSTAIN
    }

    private static final int[] RATE_MODULUS = {
            0, 2048, 1536, 1280, 1024, 768,
            640, 512, 384, 320, 256, 192,
            160, 128, 96, 80, 64, 48,
            40, 32, 24, 20, 16, 12,
            10, 8, 6, 5, 4, 3,
            2, 1
    };
    private static final int[] COUNTER_OFFSET = {
            0, 0, 1040, 536, 0, 1040,
            536, 0, 1040, 536, 0, 1040,
            536, 0, 1040, 536, 0, 1040,
            536, 0, 1040, 536, 0, 1040,
            536, 0, 1040, 536, 0, 1040,
            0, 0
    };

    private final Voice[] voices = new Voice[8];
    private final Master master = new Master();
    private final Echo echo = new Echo();
    private final Noise noise = new Noise();
    private final BRR brr = new BRR();
    private final Latch latch = new Latch();
    private final Timer timer = new Timer();
    private final short[] soundBuffer = new short[0x10000];
    private int voicePhase;
    private int bufferOffset;

    public DSP() {
        for (int i = 0; i < voices.length; i++) {
            voices[i] = new Voice();
        }
    }

    public void reset() {
        for (Voice voice : voices) {
            voice.reset();
        }
        master.reset();
        echo.reset();
        noise.reset();
        brr.reset();
        latch.reset();
        timer.reset();
        voicePhase = 0;
        bufferOffset = 0;
    }

    public int read(int address) {
        int normalized = u8(address);
        int voice = normalized >>> 4;
        int register = normalized & 0x0f;

        if (voice < voices.length) {
            switch (register) {
                case 0x0:
                    return voices[voice].volume[0];
                case 0x1:
                    return voices[voice].volume[1];
                case 0x2:
                    return voices[voice].pitchLow;
                case 0x3:
                    return voices[voice].pitchHigh;
                case 0x4:
                    return voices[voice].sourceNumber;
                case 0x5:
                    return voices[voice].adsr1;
                case 0x6:
                    return voices[voice].adsr2;
                case 0x7:
                    return voices[voice].gain;
                case 0x8:
                    return latch.envx;
                case 0x9:
                    return latch.outx;
                default:
                    break;
            }
        }

        return switch (normalized) {
            case 0x0c -> master.volume[0];
            case 0x1c -> master.volume[1];
            case 0x2c -> echo.volume[0];
            case 0x3c -> echo.volume[1];
            case 0x4c -> packedVoiceFlags(Flag.KON);
            case 0x5c -> packedVoiceFlags(Flag.KOF);
            case 0x6c -> ((master.reset ? 1 : 0) << 7)
                    | ((master.mute ? 1 : 0) << 6)
                    | ((echo.enabled ? 1 : 0) << 5)
                    | noise.clock;
            case 0x7c -> packedVoiceFlags(Flag.ENDX);
            case 0x0d -> echo.feedback;
            case 0x1d -> master.unused;
            case 0x2d -> packedVoiceFlags(Flag.PMON);
            case 0x3d -> packedVoiceFlags(Flag.NON);
            case 0x4d -> packedVoiceFlags(Flag.EON);
            case 0x5d -> brr.offset;
            case 0x6d -> echo.data;
            case 0x7d -> echo.delay;
            case 0x0f, 0x1f, 0x2f, 0x3f, 0x4f, 0x5f, 0x6f, 0x7f -> echo.fir[normalized >>> 4];
            default -> throw new InvalidAddress("DSP Registers read", normalized);
        };
    }

    public void write(int address, int data) {
        int normalized = u8(address);
        int value = u8(data);
        int voice = normalized >>> 4;
        int register = normalized & 0x0f;

        if (voice < voices.length) {
            switch (register) {
                case 0x0 -> {
                    voices[voice].volume[0] = value;
                    return;
                }
                case 0x1 -> {
                    voices[voice].volume[1] = value;
                    return;
                }
                case 0x2 -> {
                    voices[voice].pitchLow = value;
                    return;
                }
                case 0x3 -> {
                    voices[voice].pitchHigh = value;
                    return;
                }
                case 0x4 -> {
                    voices[voice].sourceNumber = value;
                    return;
                }
                case 0x5 -> {
                    voices[voice].adsr1 = value;
                    return;
                }
                case 0x6 -> {
                    voices[voice].adsr2 = value;
                    return;
                }
                case 0x7 -> {
                    voices[voice].gain = value;
                    return;
                }
                case 0x8 -> {
                    voices[voice].envx = value;
                    return;
                }
                case 0x9 -> {
                    latch.outx = value;
                    return;
                }
                default -> {
                }
            }
        }

        switch (normalized) {
            case 0x0c -> master.volume[0] = value;
            case 0x1c -> master.volume[1] = value;
            case 0x2c -> echo.volume[0] = value;
            case 0x3c -> echo.volume[1] = value;
            case 0x4c -> setVoiceFlags(value, Flag.KON);
            case 0x5c -> setVoiceFlags(value, Flag.KOF);
            case 0x6c -> {
                master.reset = (value & 0x80) != 0;
                master.mute = (value & 0x40) != 0;
                echo.enabled = (value & 0x20) != 0;
                noise.clock = value & 0x0f;
            }
            case 0x7c -> setVoiceFlags(value, Flag.ENDX);
            case 0x0d -> echo.feedback = value;
            case 0x1d -> master.unused = value;
            case 0x2d -> setVoiceFlags(value, Flag.PMON);
            case 0x3d -> setVoiceFlags(value, Flag.NON);
            case 0x4d -> setVoiceFlags(value, Flag.EON);
            case 0x5d -> brr.offset = value;
            case 0x6d -> echo.data = value;
            case 0x7d -> echo.delay = value;
            case 0x0f, 0x1f, 0x2f, 0x3f, 0x4f, 0x5f, 0x6f, 0x7f -> echo.fir[normalized >>> 4] = value;
            default -> throw new InvalidAddress("DSP Registers write", normalized);
        }
    }

    public void update() {
        switch (voicePhase) {
            case 27 -> misc27();
            case 28 -> misc28();
            case 29 -> misc29();
            case 30 -> misc30();
            default -> {
            }
        }
        voicePhase = (voicePhase + 1) % 32;
    }

    public void timerTick() {
        if (timer.counter == 0) {
            timer.counter = 0x7800;
        }
        timer.counter -= 1;
    }

    public boolean timerPoll(int rate) {
        if (rate == 0) {
            return false;
        }
        return (timer.counter + COUNTER_OFFSET[rate]) % RATE_MODULUS[rate] == 0;
    }

    public int getSize() {
        return 0x7f;
    }

    public int getSamplesCount() {
        return bufferOffset;
    }

    public int voicePhase() {
        return voicePhase;
    }

    public int timerCounter() {
        return timer.counter;
    }

    public boolean timerSample() {
        return timer.sample;
    }

    public int noiseLfsr() {
        return noise.lfsr;
    }

    public short[] soundBuffer() {
        return soundBuffer;
    }

    void runEnvelope(int voiceIndex) {
        runEnvelope(voices[voiceIndex]);
    }

    void setVoiceEnvelopeState(int voiceIndex, int envelope, int hiddenEnvelope, EnvelopeMode mode) {
        Voice voice = voices[voiceIndex];
        voice.envelope = envelope;
        voice.hiddenEnvelope = hiddenEnvelope;
        voice.envelopeMode = mode;
        latch.adsr1 = voice.adsr1;
    }

    int voiceEnvelope(int voiceIndex) {
        return voices[voiceIndex].envelope;
    }

    int voiceHiddenEnvelope(int voiceIndex) {
        return voices[voiceIndex].hiddenEnvelope;
    }

    EnvelopeMode voiceEnvelopeMode(int voiceIndex) {
        return voices[voiceIndex].envelopeMode;
    }

    private void misc27() {
        for (Voice voice : voices) {
            voice.prevPmon = voice.pmon;
        }
    }

    private void misc28() {
        for (Voice voice : voices) {
            voice.tempNon = voice.non;
        }
        brr.offsetAddress = brr.offset;
    }

    private void misc29() {
        timer.sample = !timer.sample;
        if (timer.sample) {
            for (Voice voice : voices) {
                voice.kon = false;
            }
        }
    }

    private void misc30() {
        if (timer.sample) {
            for (Voice voice : voices) {
                voice.kof = false;
            }
        }

        timerTick();

        if (!timerPoll(noise.clock)) {
            return;
        }
        int feedback = (noise.lfsr << 13) ^ (noise.lfsr << 14);
        noise.lfsr = (feedback & 0x4000) ^ (noise.lfsr >>> 1);
    }

    private void runEnvelope(Voice voice) {
        int envelope = voice.envelope;

        if (voice.envelopeMode == EnvelopeMode.RELEASE) {
            envelope -= 0x08;
            if (envelope < 0) {
                envelope = 0;
            }
            voice.envelope = envelope;
            return;
        }

        int rate;
        int data = voice.adsr2;
        if ((latch.adsr1 & 0x80) != 0) {
            if (voice.envelopeMode.ordinal() >= EnvelopeMode.DECAY.ordinal()) {
                envelope -= 1;
                envelope -= envelope >> 8;
                rate = data & 0b11111;
                if (voice.envelopeMode == EnvelopeMode.DECAY) {
                    rate = ((latch.adsr1 >>> 3) & 0x0e) + 0x10;
                }
            } else {
                rate = ((latch.adsr1 & 0x0f) << 1) + 1;
                if (rate < 0b11111) {
                    envelope += 0x20;
                } else {
                    envelope += 0x400;
                }
            }
        } else {
            data = voice.gain;
            int mode = data >>> 5;
            if (mode < 4) {
                envelope = data << 4;
                rate = 0b11111;
            } else {
                rate = data & 0b11111;
                if (mode == 4) {
                    envelope -= 0x20;
                } else if (mode < 6) {
                    envelope -= 1;
                    envelope -= envelope >> 8;
                } else {
                    envelope += 0x20;
                    if (mode > 6 && voice.hiddenEnvelope >= 0x600) {
                        envelope += 0x08 - 0x20;
                    }
                }
            }
        }

        if ((envelope >> 8) == (data >> 5) && voice.envelopeMode == EnvelopeMode.DECAY) {
            voice.envelopeMode = EnvelopeMode.SUSTAIN;
        }
        voice.hiddenEnvelope = envelope;
        if (Integer.compareUnsigned(envelope, 0x7ff) > 0) {
            if (envelope < 0) {
                envelope = 0;
            } else {
                envelope = 0x7ff;
            }
            if (voice.envelopeMode == EnvelopeMode.ATTACK) {
                voice.envelopeMode = EnvelopeMode.DECAY;
            }
        }

        if (timerPoll(rate)) {
            voice.envelope = envelope;
        }
    }

    private int packedVoiceFlags(Flag flag) {
        int packed = 0;
        for (int i = 0; i < voices.length; i++) {
            packed |= (getVoiceFlag(voices[i], flag) ? 1 : 0) << i;
        }
        return packed;
    }

    private void setVoiceFlags(int value, Flag flag) {
        for (int i = 0; i < voices.length; i++) {
            setVoiceFlag(voices[i], flag, (value & (1 << i)) != 0);
        }
    }

    private boolean getVoiceFlag(Voice voice, Flag flag) {
        return switch (flag) {
            case KON -> voice.kon;
            case KOF -> voice.kof;
            case ENDX -> voice.endx;
            case PMON -> voice.pmon;
            case NON -> voice.non;
            case EON -> voice.eon;
        };
    }

    private void setVoiceFlag(Voice voice, Flag flag, boolean value) {
        switch (flag) {
            case KON -> voice.kon = value;
            case KOF -> voice.kof = value;
            case ENDX -> voice.endx = value;
            case PMON -> voice.pmon = value;
            case NON -> voice.non = value;
            case EON -> voice.eon = value;
        }
    }

    private enum Flag {
        KON,
        KOF,
        ENDX,
        PMON,
        NON,
        EON
    }

    private static final class Voice {
        private final int[] volume = new int[2];
        private int pitchLow;
        private int pitchHigh;
        private int sourceNumber;
        private int adsr1;
        private int adsr2;
        private int gain;
        private int envx;
        private int envelope;
        private int hiddenEnvelope;
        private EnvelopeMode envelopeMode = EnvelopeMode.RELEASE;
        private boolean kon;
        private boolean kof;
        private boolean pmon;
        private boolean non;
        private boolean eon;
        private boolean endx;
        private boolean prevPmon;
        private boolean tempNon;

        private void reset() {
            volume[0] = 0;
            volume[1] = 0;
            pitchLow = 0;
            pitchHigh = 0;
            sourceNumber = 0;
            adsr1 = 0;
            adsr2 = 0;
            gain = 0;
            envx = 0;
            envelope = 0;
            hiddenEnvelope = 0;
            envelopeMode = EnvelopeMode.RELEASE;
            kon = false;
            kof = false;
            pmon = false;
            non = false;
            eon = false;
            endx = false;
            prevPmon = false;
            tempNon = false;
        }
    }

    private static final class Master {
        private final int[] volume = new int[2];
        private boolean mute;
        private boolean reset;
        private int unused;

        private void reset() {
            volume[0] = 0;
            volume[1] = 0;
            mute = false;
            reset = false;
            unused = 0;
        }
    }

    private static final class Echo {
        private final int[] volume = new int[2];
        private int feedback;
        private final int[] fir = new int[8];
        private int data;
        private int delay;
        private boolean enabled = true;

        private void reset() {
            volume[0] = 0;
            volume[1] = 0;
            feedback = 0;
            for (int i = 0; i < fir.length; i++) {
                fir[i] = 0;
            }
            data = 0;
            delay = 0;
            enabled = true;
        }
    }

    private static final class Noise {
        private int clock;
        private int lfsr = 0x4000;

        private void reset() {
            clock = 0;
            lfsr = 0x4000;
        }
    }

    private static final class BRR {
        private int offset;
        private int offsetAddress;

        private void reset() {
            offset = 0;
            offsetAddress = 0;
        }
    }

    private static final class Latch {
        private int adsr1;
        private int envx;
        private int outx;

        private void reset() {
            adsr1 = 0;
            envx = 0;
            outx = 0;
        }
    }

    private static final class Timer {
        private int counter;
        private boolean sample = true;

        private void reset() {
            counter = 0;
            sample = true;
        }
    }
}
