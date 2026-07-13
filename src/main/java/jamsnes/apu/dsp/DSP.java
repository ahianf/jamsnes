package jamsnes.apu.dsp;

import jamsnes.exceptions.InvalidAddress;
import jamsnes.renderer.IRenderer;

import java.util.Arrays;
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
    private final IRenderer renderer;
    private int voicePhase;
    private int bufferOffset;

    public DSP() {
        int[] ram = new int[0x10000];
        ramReader = address -> ram[u16(address)];
        ramWriter = (address, value) -> ram[u16(address)] = u8(value);
        renderer = null;
        for (int i = 0; i < voices.length; i++) {
            voices[i] = new Voice();
        }
    }

    public DSP(IntUnaryOperator ramReader, RamWriter ramWriter) {
        this(ramReader, ramWriter, null);
    }

    public DSP(IntUnaryOperator ramReader, RamWriter ramWriter, IRenderer renderer) {
        this.ramReader = address -> u8(ramReader.applyAsInt(u16(address)));
        this.ramWriter = (address, value) -> ramWriter.write(u16(address), u8(value));
        this.renderer = renderer;
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
        int normalized = u8(address) & 0x7f;
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
        int normalized = u8(address) & 0x7f;
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
            case 0x7c -> setVoiceFlags(0, Flag.ENDX);
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
            case 0 -> {
                voice5(voices[0]);
                voice2(voices[1]);
            }
            case 1 -> {
                voice6(voices[0]);
                voice3(voices[1]);
            }
            case 2 -> {
                voice7(voices[0]);
                voice4(voices[1]);
                voice1(voices[3]);
            }
            case 3 -> {
                voice8(voices[0]);
                voice5(voices[1]);
                voice2(voices[2]);
            }
            case 4 -> {
                voice9(voices[0]);
                voice6(voices[1]);
                voice3(voices[2]);
            }
            case 5 -> {
                voice7(voices[1]);
                voice4(voices[2]);
                voice1(voices[4]);
            }
            case 6 -> {
                voice8(voices[1]);
                voice5(voices[2]);
                voice2(voices[3]);
            }
            case 7 -> {
                voice9(voices[1]);
                voice6(voices[2]);
                voice3(voices[3]);
            }
            case 8 -> {
                voice7(voices[2]);
                voice4(voices[3]);
                voice1(voices[5]);
            }
            case 9 -> {
                voice8(voices[2]);
                voice5(voices[3]);
                voice2(voices[4]);
            }
            case 10 -> {
                voice9(voices[2]);
                voice6(voices[3]);
                voice3(voices[4]);
            }
            case 11 -> {
                voice7(voices[3]);
                voice4(voices[4]);
                voice1(voices[6]);
            }
            case 12 -> {
                voice8(voices[3]);
                voice5(voices[4]);
                voice2(voices[5]);
            }
            case 13 -> {
                voice9(voices[3]);
                voice6(voices[4]);
                voice3(voices[5]);
            }
            case 14 -> {
                voice7(voices[4]);
                voice4(voices[5]);
                voice1(voices[7]);
            }
            case 15 -> {
                voice8(voices[4]);
                voice5(voices[5]);
                voice2(voices[6]);
            }
            case 16 -> {
                voice9(voices[4]);
                voice6(voices[5]);
                voice3(voices[6]);
            }
            case 17 -> {
                voice1(voices[0]);
                voice7(voices[5]);
                voice4(voices[6]);
            }
            case 18 -> {
                voice8(voices[5]);
                voice5(voices[6]);
                voice2(voices[7]);
            }
            case 19 -> {
                voice9(voices[5]);
                voice6(voices[6]);
                voice3(voices[7]);
            }
            case 20 -> {
                voice1(voices[1]);
                voice7(voices[6]);
                voice4(voices[7]);
            }
            case 21 -> {
                voice8(voices[6]);
                voice5(voices[7]);
                voice2(voices[0]);
            }
            case 22 -> {
                voice3a(voices[0]);
                voice9(voices[6]);
                voice6(voices[7]);
                echo22();
            }
            case 23 -> {
                voice7(voices[7]);
                echo23();
            }
            case 24 -> {
                voice8(voices[7]);
                echo24();
            }
            case 25 -> {
                voice3b(voices[0]);
                voice9(voices[7]);
                echo25();
            }
            case 26 -> echo26();
            case 27 -> {
                misc27();
                echo27();
            }
            case 28 -> {
                misc28();
                echo28();
            }
            case 29 -> {
                misc29();
                echo29();
            }
            case 30 -> {
                misc30();
                voice3c(voices[0]);
                echo30();
            }
            case 31 -> {
                voice4(voices[0]);
                voice1(voices[2]);
            }
            default -> {
            }
        }
        voicePhase = (voicePhase + 1) % 32;
        playBufferedAudio();
    }

    private void playBufferedAudio() {
        int samples = getSamplesCount();
        if (renderer != null && samples > 0) {
            renderer.playAudio(Arrays.copyOf(soundBuffer, samples));
        }
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

    int voiceBrrAddress(int voiceIndex) {
        return voices[voiceIndex].brrAddress;
    }

    int voiceBrrOffset(int voiceIndex) {
        return voices[voiceIndex].brrOffset;
    }

    int voiceGaussOffset(int voiceIndex) {
        return voices[voiceIndex].gaussOffset;
    }

    void setVoiceGaussOffset(int voiceIndex, int gaussOffset) {
        voices[voiceIndex].gaussOffset = u16(gaussOffset);
    }

    void setVoiceRuntimeState(
            int voiceIndex,
            int konDelay,
            boolean loop,
            boolean echoEnabled,
            boolean tempKon,
            boolean tempKof
    ) {
        Voice voice = voices[voiceIndex];
        voice.konDelay = u8(konDelay);
        voice.loop = loop;
        voice.echo = echoEnabled;
        voice.tempKon = tempKon;
        voice.tempKof = tempKof;
    }

    int interpolate(int voiceIndex) {
        return interpolate(voices[voiceIndex]);
    }

    void setBrrDirectoryState(int offsetAddress, int source, int address, int nextAddress) {
        brr.offsetAddress = u8(offsetAddress);
        brr.source = u8(source);
        brr.address = u16(address);
        brr.nextAddress = u16(nextAddress);
    }

    int brrAddress() {
        return brr.address;
    }

    int brrNextAddress() {
        return brr.nextAddress;
    }

    int brrHeader() {
        return brr.header;
    }

    int brrValue() {
        return brr.value;
    }

    int brrSource() {
        return brr.source;
    }

    void setLatchState(int pitch, int output) {
        latch.pitch = u16(pitch);
        latch.output = u16(output);
    }

    int latchPitch() {
        return latch.pitch;
    }

    int latchOutput() {
        return latch.output;
    }

    int masterOutput(int channel) {
        return master.output[channel];
    }

    void setMasterOutput(int channel, int output) {
        master.output[channel] = u16(output);
    }

    void setEchoRuntimeState(
            int address,
            int offset,
            int length,
            int historyOffset,
            int value,
            boolean toggle
    ) {
        echo.address = u16(address);
        echo.offset = u16(offset);
        echo.length = u16(length);
        echo.historyOffset = u8(historyOffset);
        echo.value = u8(value);
        echo.toggle = toggle;
    }

    int echoAddress() {
        return echo.address;
    }

    int echoOffset() {
        return echo.offset;
    }

    int echoLength() {
        return echo.length;
    }

    int echoHistoryOffset() {
        return echo.historyOffset;
    }

    int echoHistory(int channel, int index) {
        return echo.history[channel][index & 0x0f];
    }

    void setEchoHistory(int channel, int index, int value) {
        echo.history[channel][index & 0x0f] = (short) value;
    }

    int echoInput(int channel) {
        return echo.input[channel];
    }

    void setEchoInput(int channel, int value) {
        echo.input[channel] = u16(value);
    }

    int echoOutput(int channel) {
        return echo.output[channel];
    }

    void setEchoOutput(int channel, int value) {
        echo.output[channel] = u16(value);
    }

    int voiceOutx(int voiceIndex) {
        return voices[voiceIndex].outx;
    }

    boolean voiceEndx(int voiceIndex) {
        return voices[voiceIndex].endx;
    }

    void voice1(int voiceIndex) {
        voice1(voices[voiceIndex]);
    }

    void voice2(int voiceIndex) {
        voice2(voices[voiceIndex]);
    }

    void voice3(int voiceIndex) {
        voice3(voices[voiceIndex]);
    }

    void voice3a(int voiceIndex) {
        voice3a(voices[voiceIndex]);
    }

    void voice3b(int voiceIndex) {
        voice3b(voices[voiceIndex]);
    }

    void voice3c(int voiceIndex) {
        voice3c(voices[voiceIndex]);
    }

    void voice4(int voiceIndex) {
        voice4(voices[voiceIndex]);
    }

    void voice5(int voiceIndex) {
        voice5(voices[voiceIndex]);
    }

    void voice6(int voiceIndex) {
        voice6(voices[voiceIndex]);
    }

    void voice7(int voiceIndex) {
        voice7(voices[voiceIndex]);
    }

    void voice8(int voiceIndex) {
        voice8(voices[voiceIndex]);
    }

    void voice9(int voiceIndex) {
        voice9(voices[voiceIndex]);
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

    private void voiceOutput(Voice voice, int channel) {
        int out = latch.output * (byte) voice.volume[channel] >> 7;

        master.output[channel] = u16(master.output[channel] + out);
        if (!voice.echo) {
            return;
        }
        echo.volume[channel] = u8(echo.volume[channel] + out);
    }

    private void voice1(Voice voice) {
        brr.address = u16((brr.offsetAddress << 8) + (brr.source << 2));
        brr.source = voice.sourceNumber;
    }

    private void voice2(Voice voice) {
        int address = brr.address;

        if (voice.konDelay == 0) {
            address += 2;
        }
        brr.nextAddress = readRam(address++);
        brr.nextAddress = u16(brr.nextAddress + (readRam(address) << 8));
        latch.adsr1 = voice.adsr1;
        latch.pitch = voice.pitchLow;
    }

    private void voice3(Voice voice) {
        voice3a(voice);
        voice3b(voice);
        voice3c(voice);
    }

    private void voice3a(Voice voice) {
        latch.pitch = u16(latch.pitch | ((voice.pitchHigh & 0x3f) << 8));
    }

    private void voice3b(Voice voice) {
        brr.header = readRam(brr.address);
        brr.value = readRam(brr.address + voice.brrOffset);
    }

    private void voice3c(Voice voice) {
        if (voice.prevPmon) {
            latch.pitch = u16(latch.pitch + ((latch.output >> 5) * latch.pitch >> 10));
        }

        if (voice.konDelay != 0) {
            if (voice.konDelay == 5) {
                voice.brrAddress = brr.nextAddress;
                voice.brrOffset = 1;
                voice.sampleOffset = 0;
                brr.header = 0;
            }

            voice.envelope = 0;
            voice.hiddenEnvelope = 0;
            voice.gaussOffset = 0;
            voice.konDelay -= 1;
            if ((voice.konDelay & 3) != 0) {
                voice.gaussOffset = 0x4000;
            }
            latch.pitch = 0;
        }

        int interpolated = interpolate(voice);

        if (voice.tempNon) {
            interpolated = noise.lfsr << 1;
        }

        latch.output = u16((interpolated * voice.envelope >> 11) & ~1);
        voice.envx = voice.envelope >> 4;

        if (master.reset || (brr.header & 3) == 1) {
            voice.envelope = 0;
            voice.envelopeMode = EnvelopeMode.RELEASE;
        }

        if (timer.sample) {
            if (voice.tempKof) {
                voice.envelopeMode = EnvelopeMode.RELEASE;
            }
            if (voice.tempKon) {
                voice.konDelay = 5;
                voice.envelopeMode = EnvelopeMode.ATTACK;
            }
        }

        if (voice.konDelay == 0) {
            runEnvelope(voice);
        }
    }

    private void voice4(Voice voice) {
        voice.loop = false;
        if (voice.gaussOffset >= 0x4000) {
            decodeBRR(voice);
            voice.brrOffset += 2;
            if (voice.brrOffset >= 9) {
                voice.brrOffset = voice.brrAddress + 9;
                if ((brr.header & 1) != 0) {
                    voice.brrAddress = brr.nextAddress;
                    voice.loop = true;
                }
                voice.brrOffset = 1;
            }
        }

        voice.gaussOffset = (voice.gaussOffset & 0x3fff) + latch.pitch;
        if (voice.gaussOffset > 0x7fff) {
            voice.gaussOffset = 0x7fff;
        }
        voiceOutput(voice, 0);
    }

    private void voice5(Voice voice) {
        voiceOutput(voice, 1);

        voice.endx |= voice.loop;
        if (voice.konDelay == 5) {
            voice.endx = false;
        }
    }

    private void voice6(Voice voice) {
        latch.outx = latch.output >> 8;
    }

    private void voice7(Voice voice) {
        latch.envx = voice.envx;
    }

    private void voice8(Voice voice) {
        voice.outx = latch.outx;
    }

    private void voice9(Voice voice) {
        voice.envx = latch.envx;
    }

    int loadFIR(int channel, int fir) {
        int sample = echo.history[channel][(echo.historyOffset + fir + 1) & 0x0f];

        return sample * echo.fir[fir] >> 6;
    }

    void loadEcho(int channel) {
        int address = echo.address + channel * 2;
        int low = readRam(address++);
        int high = readRam(address);
        short echoSample = (short) ((high << 8) + low);

        echo.history[channel][echo.historyOffset & 0x0f] = (short) (echoSample >> 1);
    }

    void writeEcho(int channel) {
        if (!echo.toggle) {
            int address = echo.address + channel * 2;
            short sample = (short) echo.output[channel];

            writeRam(address++, sample);
            writeRam(address, sample >> 8);
        }
        echo.output[channel] = 0;
    }

    int outputEcho(int channel) {
        short masterSample = (short) (master.output[channel] * master.volume[channel] >> 7);
        short echoSample = (short) (echo.input[channel] * echo.input[channel] >> 7);

        return (short) (masterSample + echoSample);
    }

    void echo22() {
        echo.historyOffset = u8(echo.historyOffset + 1);
        echo.address = u16((echo.value << 8) + echo.offset);

        loadEcho(0);

        echo.input[0] = u16(loadFIR(0, 0));
        echo.input[1] = u16(loadFIR(1, 0));
    }

    void echo23() {
        loadEcho(1);

        echo.input[0] = u16(echo.input[0] + loadFIR(0, 1) + loadFIR(0, 2));
        echo.input[1] = u16(echo.input[1] + loadFIR(1, 1) + loadFIR(1, 2));
    }

    void echo24() {
        echo.input[0] = u16(echo.input[0] + loadFIR(0, 3) + loadFIR(0, 4) + loadFIR(0, 5));
        echo.input[1] = u16(echo.input[1] + loadFIR(1, 3) + loadFIR(1, 4) + loadFIR(1, 5));
    }

    void echo25() {
        echo.input[0] = u16(echo.input[0] + loadFIR(0, 6) + loadFIR(0, 7));
        echo.input[1] = u16(echo.input[1] + loadFIR(1, 6) + loadFIR(1, 7));
    }

    void echo26() {
        master.output[0] = u16(outputEcho(0));

        echo.output[0] = u16(echo.output[0] + (echo.input[0] * echo.feedback >> 7));
        echo.output[1] = u16(echo.output[1] + (echo.input[1] * echo.feedback >> 7));
    }

    void echo27() {
        short outputLeft = (short) master.output[0];
        short outputRight = (short) outputEcho(1);

        master.output[0] = 0;
        master.output[1] = 0;

        if (master.mute) {
            outputLeft = 0;
            outputRight = 0;
        }

        soundBuffer[bufferOffset] = outputLeft;
        soundBuffer[bufferOffset + 1] = outputRight;
        bufferOffset += 2;
        if (bufferOffset >= soundBuffer.length / 2) {
            bufferOffset = 0;
        }
    }

    void echo28() {
        echo.toggle = echo.enabled;
    }

    void echo29() {
        echo.value = echo.data;

        if (echo.offset == 0) {
            echo.length = echo.delay << 11;
        }

        echo.offset += 4;
        if (echo.offset >= echo.length) {
            echo.offset = 0;
        }

        writeEcho(0);

        echo28();
    }

    void echo30() {
        writeEcho(1);
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
        private int outx;
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
        private int konDelay;
        private boolean echo;
        private boolean loop;
        private boolean prevPmon;
        private boolean tempNon;
        private boolean tempKon;
        private boolean tempKof;

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
            outx = 0;
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
            konDelay = 0;
            echo = false;
            loop = false;
            prevPmon = false;
            tempNon = false;
            tempKon = false;
            tempKof = false;
        }
    }

    private static final class Master {
        private final int[] volume = new int[2];
        private final int[] output = new int[2];
        private boolean mute;
        private boolean reset;
        private int unused;

        private void reset() {
            volume[0] = 0;
            volume[1] = 0;
            output[0] = 0;
            output[1] = 0;
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
        private int offset;
        private int length;
        private int delay;
        private boolean enabled = true;
        private boolean toggle;
        private final short[][] history = new short[2][16];
        private int historyOffset;
        private int address;
        private int value;
        private final int[] input = new int[2];
        private final int[] output = new int[2];

        private void reset() {
            volume[0] = 0;
            volume[1] = 0;
            feedback = 0;
            for (int i = 0; i < fir.length; i++) {
                fir[i] = 0;
            }
            data = 0;
            offset = 0;
            length = 0;
            delay = 0;
            enabled = true;
            toggle = false;
            for (int channel = 0; channel < history.length; channel++) {
                for (int i = 0; i < history[channel].length; i++) {
                    history[channel][i] = 0;
                }
            }
            historyOffset = 0;
            address = 0;
            value = 0;
            input[0] = 0;
            input[1] = 0;
            output[0] = 0;
            output[1] = 0;
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
        private int address;
        private int nextAddress;
        private int header;
        private int value;
        private int source;

        private void reset() {
            offset = 0;
            offsetAddress = 0;
            address = 0;
            nextAddress = 0;
            header = 0;
            value = 0;
            source = 0;
        }
    }

    private static final class Latch {
        private int adsr1;
        private int envx;
        private int outx;
        private int pitch;
        private int output;

        private void reset() {
            adsr1 = 0;
            envx = 0;
            outx = 0;
            pitch = 0;
            output = 0;
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
