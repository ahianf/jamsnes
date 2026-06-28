package jamsnes.apu;

import static jamsnes.models.Unsigned.u16;
import static jamsnes.models.Unsigned.u8;

public class APURegisters {
    public int x;
    public int a;
    public int y;
    public int sp;
    public int pc;
    public boolean c;
    public boolean z;
    public boolean i;
    public boolean h;
    public boolean b;
    public boolean p;
    public boolean v;
    public boolean n;

    public int ya() {
        return u16(a | (y << 8));
    }

    public void setYa(int value) {
        a = u8(value);
        y = u8(value >>> 8);
    }

    public int pcLow() {
        return pc & 0xff;
    }

    public int pcHigh() {
        return (pc >>> 8) & 0xff;
    }

    public void setPcLow(int value) {
        pc = u16((pc & 0xff00) | u8(value));
    }

    public void setPcHigh(int value) {
        pc = u16((u8(value) << 8) | (pc & 0x00ff));
    }

    public int psw() {
        return (c ? 0x01 : 0)
                | (z ? 0x02 : 0)
                | (i ? 0x04 : 0)
                | (h ? 0x08 : 0)
                | (b ? 0x10 : 0)
                | (p ? 0x20 : 0)
                | (v ? 0x40 : 0)
                | (n ? 0x80 : 0);
    }

    public void setPsw(int value) {
        int normalized = u8(value);
        c = (normalized & 0x01) != 0;
        z = (normalized & 0x02) != 0;
        i = (normalized & 0x04) != 0;
        h = (normalized & 0x08) != 0;
        b = (normalized & 0x10) != 0;
        p = (normalized & 0x20) != 0;
        v = (normalized & 0x40) != 0;
        n = (normalized & 0x80) != 0;
    }

    public void incrementPc() {
        pc = u16(pc + 1);
    }
}
