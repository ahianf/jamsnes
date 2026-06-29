package jamsnes.apu.dsp;

import jamsnes.exceptions.InvalidAddress;

import java.util.function.IntUnaryOperator;

import static jamsnes.models.Unsigned.u16;
import static jamsnes.models.Unsigned.u8;

public class DSP {
    @FunctionalInterface
    public interface RamWriter {
        void write(int address, int value);
    }

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
    private static final int[] GAUSS = {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2,
            2, 2, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 5, 5, 5, 5,
            6, 6, 6, 6, 7, 7, 7, 8, 8, 8, 9, 9, 9, 10, 10, 10,
            11, 11, 11, 12, 12, 13, 13, 14, 14, 15, 15, 15, 16, 16, 17, 17,
            18, 19, 19, 20, 20, 21, 21, 22, 23, 23, 24, 24, 25, 26, 27, 27,
            28, 29, 29, 30, 31, 32, 32, 33, 34, 35, 36, 36, 37, 38, 39, 40,
            41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56,
            58, 59, 60, 61, 62, 64, 65, 66, 67, 69, 70, 71, 73, 74, 76, 77,
            78, 80, 81, 83, 84, 86, 87, 89, 90, 92, 94, 95, 97, 99, 100, 102,
            104, 106, 107, 109, 111, 113, 115, 117, 118, 120, 122, 124, 126, 128, 130, 132,
            134, 137, 139, 141, 143, 145, 147, 150, 152, 154, 156, 159, 161, 163, 166, 168,
            171, 173, 175, 178, 180, 183, 186, 188, 191, 193, 196, 199, 201, 204, 207, 210,
            212, 215, 218, 221, 224, 227, 230, 233, 236, 239, 242, 245, 248, 251, 254, 257,
            260, 263, 267, 270, 273, 276, 280, 283, 286, 290, 293, 297, 300, 304, 307, 311,
            314, 318, 321, 325, 328, 332, 336, 339, 343, 347, 351, 354, 358, 362, 366, 370,
            374, 378, 381, 385, 389, 393, 397, 401, 405, 410, 414, 418, 422, 426, 430, 434,
            439, 443, 447, 451, 456, 460, 464, 469, 473, 477, 482, 486, 491, 495, 499, 504,
            508, 513, 517, 522, 527, 531, 536, 540, 545, 550, 554, 559, 563, 568, 573, 577,
            582, 587, 592, 596, 601, 606, 611, 615, 620, 625, 630, 635, 640, 644, 649, 654,
            659, 664, 669, 674, 678, 683, 688, 693, 698, 703, 708, 713, 718, 723, 728, 732,
            737, 742, 747, 752, 757, 762, 767, 772, 777, 782, 787, 792, 797, 802, 806, 811,
            816, 821, 826, 831, 836, 841, 846, 851, 855, 860, 865, 870, 875, 880, 884, 889,
            894, 899, 904, 908, 913, 918, 923, 927, 932, 937, 941, 946, 951, 955, 960, 965,
            969, 974, 978, 983, 988, 992, 997, 1001, 1005, 1010, 1014, 1019, 1023, 1027, 1032, 1036,
            1040, 1045, 1049, 1053, 1057, 1061, 1066, 1070, 1074, 1078, 1082, 1086, 1090, 1094, 1098, 1102,
            1106, 1109, 1113, 1117, 1121, 1125, 1128, 1132, 1136, 1139, 1143, 1146, 1150, 1153, 1157, 1160,
            1164, 1167, 1170, 1174, 1177, 1180, 1183, 1186, 1190, 1193, 1196, 1199, 1202, 1205, 1207, 1210,
            1213, 1216, 1219, 1221, 1224, 1227, 1229, 1232, 1234, 1237, 1239, 1241, 1244, 1246, 1248, 1251,
            1253, 1255, 1257, 1259, 1261, 1263, 1265, 1267, 1269, 1270, 1272, 1274, 1275, 1277, 1279, 1280,
            1282, 1283, 1284, 1286, 1287, 1288, 1290, 1291, 1292, 1293, 1294, 1295, 1296, 1297, 1297, 1298,
            1299, 1300, 1300, 1301, 1302, 1302, 1303, 1303, 1303, 1304, 1304, 1304, 1304, 1304, 1305, 1305
    };

    private final Voice[] voices = new Voice[8];
    private final Master master = new Master();
    private final Echo echo = new Echo();
    private final Noise noise = new Noise();
    private final BRR brr = new BRR();
    private final Latch latch = new Latch();
    private final Timer timer = new Timer();
    private final short[] soundBuffer = new short[0x10000];
    private final IntUnaryOperator ramReader;
    private final RamWriter ramWriter;
    private int voicePhase;
    private int bufferOffset;

    public DSP() {
        int[] ram = new int[0x10000];
        ramReader = address -> ram[u16(address)];
        ramWriter = (address, value) -> ram[u16(address)] = u8(value);
        for (int i = 0; i < voices.length; i++) {
            voices[i] = new Voice();
        }
    }

    public DSP(IntUnaryOperator ramReader, RamWriter ramWriter) {
        this.ramReader = address -> u8(ramReader.applyAsInt(u16(address)));
        this.ramWriter = (address, value) -> ramWriter.write(u16(address), u8(value));
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

    int readRam(int address) {
        return u8(ramReader.applyAsInt(u16(address)));
    }

    void writeRam(int address, int value) {
        ramWriter.write(u16(address), u8(value));
    }

    void decodeBRR(int voiceIndex) {
        decodeBRR(voices[voiceIndex]);
    }

    void setBrrState(int header, int value) {
        brr.header = u8(header);
        brr.value = u8(value);
    }

    void setVoiceBrrState(int voiceIndex, int brrAddress, int brrOffset, int sampleOffset) {
        Voice voice = voices[voiceIndex];
        voice.brrAddress = u16(brrAddress);
        voice.brrOffset = u8(brrOffset);
        voice.sampleOffset = sampleOffset;
    }

    void setVoiceSample(int voiceIndex, int index, int value) {
        voices[voiceIndex].samples[index] = value;
    }

    int voiceSample(int voiceIndex, int index) {
        return voices[voiceIndex].samples[index];
    }

    int voiceSampleOffset(int voiceIndex) {
        return voices[voiceIndex].sampleOffset;
    }

    void setVoiceGaussOffset(int voiceIndex, int gaussOffset) {
        voices[voiceIndex].gaussOffset = u16(gaussOffset);
    }

    int interpolate(int voiceIndex) {
        return interpolate(voices[voiceIndex]);
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

    private void decodeBRR(Voice voice) {
        int value = (brr.value << 8) | readRam(voice.brrAddress + voice.brrOffset + 1);
        int filter = (brr.header >>> 2) & 0b11;
        int range = (brr.header >>> 4) & 0b1111;

        for (int i = 0; i < 4; i++) {
            int sample = value >> 12;
            value <<= 4;

            if (range <= 12) {
                sample <<= range;
                sample >>= 1;
            } else {
                sample &= ~0x7ff;
            }

            int offset = voice.sampleOffset;
            if (--offset < 0) {
                offset = 11;
            }
            int lastSample = voice.samples[offset];
            if (--offset < 0) {
                offset = 11;
            }
            int afterLastSample = voice.samples[offset];

            switch (filter) {
                case 1 -> {
                    sample += lastSample;
                    sample += lastSample >> 4;
                }
                case 2 -> {
                    sample += lastSample << 1;
                    sample += -((lastSample << 1) + lastSample) >> 5;
                    sample -= afterLastSample;
                    sample += afterLastSample >> 4;
                }
                case 3 -> {
                    sample += lastSample << 1;
                    sample += -(lastSample + (lastSample << 2) + (lastSample << 3)) >> 6;
                    sample -= afterLastSample;
                    sample += ((afterLastSample << 1) + afterLastSample) >> 4;
                }
                default -> {
                }
            }
            sample = Math.max(0, Math.min(16, sample));
            sample <<= 1;
            voice.samples[voice.sampleOffset] = sample;
            if (++voice.sampleOffset >= voice.samples.length) {
                voice.sampleOffset = 0;
            }
        }
    }

    private int interpolate(Voice voice) {
        int offset = u8(voice.gaussOffset >>> 4);
        int forward = 255 - offset;
        int reverse = offset;

        offset = (voice.sampleOffset + (voice.gaussOffset >>> 12)) % 12;
        int interpolated = GAUSS[forward] * voice.samples[offset++] >> 11;
        offset %= 12;
        interpolated += GAUSS[forward + 256] * voice.samples[offset++] >> 11;
        offset %= 12;
        interpolated += GAUSS[reverse + 256] * voice.samples[offset++] >> 11;
        offset %= 12;
        interpolated = (short) interpolated;
        interpolated += GAUSS[reverse] * voice.samples[offset] >> 11;

        return Math.max(0, Math.min(16, interpolated)) & ~1;
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
        private int brrAddress;
        private int brrOffset = 1;
        private final int[] samples = new int[12];
        private int sampleOffset;
        private int gaussOffset;
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
            brrAddress = 0;
            brrOffset = 1;
            for (int i = 0; i < samples.length; i++) {
                samples[i] = 0;
            }
            sampleOffset = 0;
            gaussOffset = 0;
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
        private int header;
        private int value;

        private void reset() {
            offset = 0;
            offsetAddress = 0;
            header = 0;
            value = 0;
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
