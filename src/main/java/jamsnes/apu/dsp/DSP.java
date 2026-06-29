package jamsnes.apu.dsp;

import jamsnes.exceptions.InvalidAddress;

import static jamsnes.models.Unsigned.u8;

public class DSP {
    private final Voice[] voices = new Voice[8];
    private final Master master = new Master();
    private final Echo echo = new Echo();
    private final Noise noise = new Noise();
    private final BRR brr = new BRR();
    private final Latch latch = new Latch();

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
        private boolean kon;
        private boolean kof;
        private boolean pmon;
        private boolean non;
        private boolean eon;
        private boolean endx;

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
            kon = false;
            kof = false;
            pmon = false;
            non = false;
            eon = false;
            endx = false;
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

        private void reset() {
            clock = 0;
        }
    }

    private static final class BRR {
        private int offset;

        private void reset() {
            offset = 0;
        }
    }

    private static final class Latch {
        private int envx;
        private int outx;

        private void reset() {
            envx = 0;
            outx = 0;
        }
    }
}
