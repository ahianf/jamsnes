package jamsnes.cpu;

import static jamsnes.models.Unsigned.bank;
import static jamsnes.models.Unsigned.u16;
import static jamsnes.models.Unsigned.u24;
import static jamsnes.models.Unsigned.u8;

public class Registers {
    public int a;
    public int dbr;
    public int d;
    public int pc;
    public int pbr;
    public int pac;
    public int s;
    public int x;
    public int y;
    public final StatusRegister p = new StatusRegister();

    public int al() {
        return u8(a);
    }

    public int ah() {
        return u8(a >>> 8);
    }

    public void setAl(int value) {
        a = u16((a & 0xff00) | u8(value));
    }

    public void setAh(int value) {
        a = u16((u8(value) << 8) | (a & 0xff));
    }

    public int dl() {
        return u8(d);
    }

    public int dh() {
        return u8(d >>> 8);
    }

    public int xl() {
        return u8(x);
    }

    public int xh() {
        return u8(x >>> 8);
    }

    public int yl() {
        return u8(y);
    }

    public int yh() {
        return u8(y >>> 8);
    }

    public void setXh(int value) {
        x = u16((u8(value) << 8) | (x & 0xff));
    }

    public void setYh(int value) {
        y = u16((u8(value) << 8) | (y & 0xff));
    }

    public int sl() {
        return u8(s);
    }

    public int sh() {
        return u8(s >>> 8);
    }

    public void setPac(int value) {
        pac = u24(value);
        pc = u16(pac);
        pbr = bank(pac);
    }

    public void setPc(int value) {
        pc = u16(value);
        syncPac();
    }

    public void setPbr(int value) {
        pbr = u8(value);
        syncPac();
    }

    public void incrementPc(int amount) {
        pc = u16(pc + amount);
        syncPac();
    }

    private void syncPac() {
        pac = u24((pbr << 16) | pc);
    }
}
