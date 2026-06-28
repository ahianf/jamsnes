package jamsnes.models;

public final class Unsigned {
    private Unsigned() {
    }

    public static int u8(int value) {
        return value & 0xff;
    }

    public static int u16(int value) {
        return value & 0xffff;
    }

    public static int u24(int value) {
        return value & 0xffffff;
    }

    public static int bank(int address) {
        return u8(address >>> 16);
    }

    public static int page(int address) {
        return u16(address);
    }
}
