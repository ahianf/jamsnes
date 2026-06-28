package jamsnes.ppu;

public final class PPUUtils {
    private PPUUtils() {
    }

    public static int to8Bit(int color) {
        return ((color << 3) + (color >> 2)) & 0xff;
    }

    public static int cgramColorToRGBA(int cgramColor) {
        int b = to8Bit(cgramColor >> 10);
        int g = to8Bit((cgramColor >> 5) & 0x1f);
        int r = to8Bit(cgramColor & 0x1f);
        return 0x000000ff | (r << 24) | (g << 16) | (b << 8);
    }
}
