package jamsnes.ppu;

public final class PPUUtils {
    private PPUUtils() {
    }

    public static int to8Bit(int color) {
        return ((color << 3) + (color >> 2)) & 0xff;
    }

    public static int cgramColorToRGBA(int cgramColor) {
        cgramColor &= 0x7fff;
        int b = to8Bit(cgramColor >> 10);
        int g = to8Bit((cgramColor >> 5) & 0x1f);
        int r = to8Bit(cgramColor & 0x1f);
        return 0x000000ff | (r << 24) | (g << 16) | (b << 8);
    }

    public static int directColorToRGBA(int palette, int colorIndex) {
        int cgramColor = ((colorIndex & 0xc0) << 7)
                | ((palette & 0x04) << 10)
                | ((colorIndex & 0x38) << 4)
                | ((palette & 0x02) << 5)
                | ((colorIndex & 0x07) << 2)
                | ((palette & 0x01) << 1);
        return cgramColorToRGBA(cgramColor);
    }
}
