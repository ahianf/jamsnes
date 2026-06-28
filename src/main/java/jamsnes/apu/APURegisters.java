package jamsnes.apu;

import static jamsnes.models.Unsigned.u16;
import static jamsnes.models.Unsigned.u8;

public class APURegisters {
    public int x;
    public int a;
    public int y;
    public int sp;
    public int pc;
    public boolean p;

    public int ya() {
        return u16(a | (y << 8));
    }

    public void setYa(int value) {
        a = u8(value);
        y = u8(value >>> 8);
    }

    public void incrementPc() {
        pc = u16(pc + 1);
    }
}
