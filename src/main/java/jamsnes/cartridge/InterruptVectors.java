package jamsnes.cartridge;

import static jamsnes.models.Unsigned.u16;
import static jamsnes.models.Unsigned.u8;

public class InterruptVectors {
    public int cop;
    public int brk;
    public int abort;
    public int nmi;
    public int reset;
    public int irq;

    public void reset() {
        cop = 0;
        brk = 0;
        abort = 0;
        nmi = 0;
        reset = 0;
        irq = 0;
    }

    public void setCopBytes(int low, int high) {
        cop = word(low, high);
    }

    public void setBrkBytes(int low, int high) {
        brk = word(low, high);
    }

    public void setAbortBytes(int low, int high) {
        abort = word(low, high);
    }

    public void setNmiBytes(int low, int high) {
        nmi = word(low, high);
    }

    public void setResetBytes(int low, int high) {
        reset = word(low, high);
    }

    public void setIrqBytes(int low, int high) {
        irq = word(low, high);
    }

    private static int word(int low, int high) {
        return u16(u8(low) | (u8(high) << 8));
    }
}
