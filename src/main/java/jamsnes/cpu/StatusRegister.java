package jamsnes.cpu;

import static jamsnes.models.Unsigned.u8;

public class StatusRegister {
    public boolean c;
    public boolean z;
    public boolean i;
    public boolean d;
    public boolean x_b;
    public boolean m;
    public boolean v;
    public boolean n;

    public int flags() {
        return (c ? 1 : 0)
                | (z ? 1 << 1 : 0)
                | (i ? 1 << 2 : 0)
                | (d ? 1 << 3 : 0)
                | (x_b ? 1 << 4 : 0)
                | (m ? 1 << 5 : 0)
                | (v ? 1 << 6 : 0)
                | (n ? 1 << 7 : 0);
    }

    public void setFlags(int flags) {
        int value = u8(flags);
        c = (value & (1 << 0)) != 0;
        z = (value & (1 << 1)) != 0;
        i = (value & (1 << 2)) != 0;
        d = (value & (1 << 3)) != 0;
        x_b = (value & (1 << 4)) != 0;
        m = (value & (1 << 5)) != 0;
        v = (value & (1 << 6)) != 0;
        n = (value & (1 << 7)) != 0;
    }
}
