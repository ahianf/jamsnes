package jamsnes.ppu;

import jamsnes.models.Component;
import jamsnes.ram.Ram;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TileRendererTest {
    @Test
    void read2BPPValue() {
        Ram vram = new Ram(100, Component.VRAM, "vramTest");
        TileRenderer renderer = renderer(vram);
        vram.write(0, 0xba);
        vram.write(1, 0x7c);

        int[] values = new int[8];
        for (int i = 0; i < values.length; i++) {
            values[i] = renderer.read2BPPValue(0, i);
        }
        assertArrayEquals(new int[]{1, 2, 3, 3, 3, 2, 1, 0}, values);
    }

    @Test
    void read2BPPValueWithCircularRam() {
        Ram vram = new Ram(10, Component.VRAM, "vramTest");
        TileRenderer renderer = renderer(vram);
        vram.write(9, 0xba);
        vram.write(0, 0x7c);

        int[] values = new int[8];
        for (int i = 0; i < values.length; i++) {
            values[i] = renderer.read2BPPValue(9, i);
        }
        assertArrayEquals(new int[]{1, 2, 3, 3, 3, 2, 1, 0}, values);
    }

    @Test
    void getPixelReferenceFromTileRow2bpp() {
        Ram vram = new Ram(10, Component.VRAM, "vramTest");
        TileRenderer renderer = renderer(vram);
        renderer.setBpp(2);
        vram.write(2, 0xd6);
        vram.write(3, 0x00);

        assertRow(renderer, 2, new int[]{1, 1, 0, 1, 0, 1, 1, 0});
    }

    @Test
    void getPixelReferenceFromTileRow4bpp() {
        Ram vram = new Ram(40, Component.VRAM, "vramTest");
        TileRenderer renderer = renderer(vram);
        renderer.setBpp(4);
        fillRam(vram,
                "7C7C82EE82FE7C7C",
                "0000D68254443838",
                "7C00AA1082007C00",
                "1000540038000000");

        int[][] expected = {
                {0, 7, 7, 7, 7, 7, 0, 0},
                {7, 2, 6, 8, 6, 2, 7, 0},
                {7, 2, 2, 2, 2, 2, 7, 0},
                {0, 7, 7, 7, 7, 7, 0, 0},
                {0, 0, 0, 4, 0, 0, 0, 0},
                {3, 5, 0, 5, 0, 5, 3, 0},
                {0, 3, 4, 5, 4, 3, 0, 0},
                {0, 0, 3, 3, 3, 0, 0, 0}
        };

        for (int row = 0; row < expected.length; row++) {
            assertRow(renderer, row * 2, expected[row]);
        }
    }

    @Test
    void getPixelReferenceFromTileRow8bpp() {
        Ram vram = new Ram(100, Component.VRAM, "vramTest");
        TileRenderer renderer = renderer(vram);
        renderer.setBpp(8);
        fill8BppTile(vram);

        int[][] expected = expected8BppTile();

        for (int row = 0; row < expected.length; row++) {
            assertRow(renderer, row * 2, expected[row]);
        }
    }

    @Test
    void getPixelReferenceFromTile2bpp() {
        Ram vram = new Ram(10, Component.VRAM, "vramTest");
        TileRenderer renderer = renderer(vram);
        renderer.setBpp(2);
        vram.write(2, 0xd6);
        vram.write(3, 0x00);

        int[] actual = new int[8];
        for (int i = 0; i < actual.length; i++) {
            actual[i] = renderer.getPixelReferenceFromTile(2, i);
        }

        assertArrayEquals(new int[]{1, 1, 0, 1, 0, 1, 1, 0}, actual);
    }

    @Test
    void getPixelReferenceFromTile4bpp() {
        Ram vram = new Ram(40, Component.VRAM, "vramTest");
        TileRenderer renderer = renderer(vram);
        renderer.setBpp(4);
        fillRam(vram,
                "7C7C82EE82FE7C7C",
                "0000D68254443838",
                "7C00AA1082007C00",
                "1000540038000000");

        int[][] expected = {
                {0, 7, 7, 7, 7, 7, 0, 0},
                {7, 2, 6, 8, 6, 2, 7, 0},
                {7, 2, 2, 2, 2, 2, 7, 0},
                {0, 7, 7, 7, 7, 7, 0, 0},
                {0, 0, 0, 4, 0, 0, 0, 0},
                {3, 5, 0, 5, 0, 5, 3, 0},
                {0, 3, 4, 5, 4, 3, 0, 0},
                {0, 0, 3, 3, 3, 0, 0, 0}
        };

        assertTile(renderer, 0, expected);
    }

    @Test
    void getPixelReferenceFromTile8bpp() {
        Ram vram = new Ram(100, Component.VRAM, "vramTest");
        TileRenderer renderer = renderer(vram);
        renderer.setBpp(8);
        fill8BppTile(vram);

        assertTile(renderer, 0, expected8BppTile());
    }

    @Test
    void render2bppUsesPaletteColors() {
        Ram vram = new Ram(100, Component.VRAM, "vramTest");
        Ram cgram = new Ram(512, Component.CGRAM, "cgramTest");
        TileRenderer renderer = new TileRenderer(vram, cgram);
        renderer.setBpp(2);
        renderer.setPaletteIndex(1);
        fillRam(vram,
                "7C00BA7C827C7C00",
                "1000D6007C003800");
        writePalette(cgram, 1, 2, 0x0000, 0x67bf, 0x4298, 0x150e);

        int[][] references = {
                {0, 1, 1, 1, 1, 1, 0, 0},
                {1, 2, 3, 3, 3, 2, 1, 0},
                {1, 2, 2, 2, 2, 2, 1, 0},
                {0, 1, 1, 1, 1, 1, 0, 0},
                {0, 0, 0, 1, 0, 0, 0, 0},
                {1, 1, 0, 1, 0, 1, 1, 0},
                {0, 1, 1, 1, 1, 1, 0, 0},
                {0, 0, 1, 1, 1, 0, 0, 0}
        };

        renderer.render(0);

        assertRenderedReferences(renderer, 1, references);
    }

    @Test
    void render4bppUsesPaletteColors() {
        Ram vram = new Ram(100, Component.VRAM, "vramTest");
        Ram cgram = new Ram(512, Component.CGRAM, "cgramTest");
        TileRenderer renderer = new TileRenderer(vram, cgram);
        renderer.setBpp(4);
        renderer.setPaletteIndex(0);
        fillRam(vram,
                "7C7C82EE82FE7C7C",
                "0000D68254443838",
                "7C00AA1082007C00",
                "1000540038000000");
        writePalette(cgram, 0, 4,
                0x0000, 0x0000, 0x7fff, 0x0e21,
                0x0ee2, 0x0fe3, 0x0c79, 0x11fd,
                0x079f, 0x4bbf, 0x01dd, 0x0ccb,
                0x0000, 0x55df, 0x0c3c, 0x108b);

        renderer.render(0);

        assertRenderedReferences(renderer, 0, new int[][]{
                {0, 7, 7, 7, 7, 7, 0, 0},
                {7, 2, 6, 8, 6, 2, 7, 0},
                {7, 2, 2, 2, 2, 2, 7, 0},
                {0, 7, 7, 7, 7, 7, 0, 0},
                {0, 0, 0, 4, 0, 0, 0, 0},
                {3, 5, 0, 5, 0, 5, 3, 0},
                {0, 3, 4, 5, 4, 3, 0, 0},
                {0, 0, 3, 3, 3, 0, 0, 0}
        });
    }

    @Test
    void render8bppUsesPaletteColors() {
        Ram vram = new Ram(100, Component.VRAM, "vramTest");
        Ram cgram = new Ram(512, Component.CGRAM, "cgramTest");
        TileRenderer renderer = new TileRenderer(vram, cgram);
        renderer.setBpp(8);
        renderer.setPaletteIndex(0);
        fill8BppTile(vram);
        for (int color = 0; color < 256; color++) {
            writeCgramColor(cgram, color, (color * 97) & 0x7fff);
        }

        renderer.render(0);

        assertRenderedReferences(renderer, 0, expected8BppTile());
    }

    @Test
    void render8bppIgnoresTilePaletteBitsForCgramLookup() {
        Ram vram = new Ram(100, Component.VRAM, "vramTest");
        Ram cgram = new Ram(512, Component.CGRAM, "cgramTest");
        TileRenderer renderer = new TileRenderer(vram, cgram);
        renderer.setBpp(8);
        renderer.setPaletteIndex(7);
        vram.write(0x00, 0x80);
        vram.write(0x01, 0x80);
        vram.write(0x10, 0x80);
        writeCgramColor(cgram, 7, 0x03e0);

        renderer.render(0);

        assertEquals(PPUUtils.cgramColorToRGBA(0x03e0), renderer.buffer[0][0]);
    }

    @Test
    void render8bppCanUseDirectColorInsteadOfCgram() {
        Ram vram = new Ram(100, Component.VRAM, "vramTest");
        Ram cgram = new Ram(512, Component.CGRAM, "cgramTest");
        TileRenderer renderer = new TileRenderer(vram, cgram);
        renderer.setBpp(8);
        vram.write(0x00, 0x80);
        vram.write(0x01, 0x80);
        vram.write(0x10, 0x80);
        writeCgramColor(cgram, 7, 0x03e0);

        renderer.render(0, true);

        assertEquals(0xe70000ff, renderer.buffer[0][0]);
    }

    @Test
    void cgramColorToRGBA() {
        assertEquals(0x000000ff, PPUUtils.cgramColorToRGBA(0x0000));
        assertEquals(0xffffffff, PPUUtils.cgramColorToRGBA(0x7fff));
        assertEquals(0xff0000ff, PPUUtils.cgramColorToRGBA(0x001f));
        assertEquals(0x000000ff, PPUUtils.cgramColorToRGBA(0x8000));
        assertEquals(0xffffffff, PPUUtils.cgramColorToRGBA(0xffff));
    }

    @Test
    void directColorToRGBA() {
        assertEquals(0xe70000ff, PPUUtils.directColorToRGBA(0, 0x07));
        assertEquals(PPUUtils.cgramColorToRGBA(0x0002), PPUUtils.directColorToRGBA(0b001, 0x00));
        assertEquals(PPUUtils.cgramColorToRGBA(0x0040), PPUUtils.directColorToRGBA(0b010, 0x00));
        assertEquals(PPUUtils.cgramColorToRGBA(0x1000), PPUUtils.directColorToRGBA(0b100, 0x00));
        assertEquals(PPUUtils.cgramColorToRGBA(0x0380), PPUUtils.directColorToRGBA(0, 0x38));
        assertEquals(PPUUtils.cgramColorToRGBA(0x6000), PPUUtils.directColorToRGBA(0, 0xc0));
        assertEquals(PPUUtils.cgramColorToRGBA(0x73de), PPUUtils.directColorToRGBA(0b111, 0xff));
    }

    private static TileRenderer renderer(Ram vram) {
        return new TileRenderer(vram, new Ram(512, Component.CGRAM, "cgramTest"));
    }

    private static void assertRow(TileRenderer renderer, int tileRowAddress, int[] expected) {
        int[] actual = new int[8];
        for (int i = 0; i < actual.length; i++) {
            actual[i] = renderer.getPixelReferenceFromTileRow(tileRowAddress, i);
        }
        assertArrayEquals(expected, actual);
    }

    private static void assertTile(TileRenderer renderer, int tileAddress, int[][] expected) {
        for (int row = 0; row < expected.length; row++) {
            int[] actual = new int[8];
            for (int column = 0; column < actual.length; column++) {
                actual[column] = renderer.getPixelReferenceFromTile(tileAddress, row * 8 + column);
            }
            assertArrayEquals(expected[row], actual);
        }
    }

    private static void fill8BppTile(Ram ram) {
        fillRam(ram,
                "0C7C5CA0C0BC3C001010964038282018",
                "0000020002007C000000820044003800",
                "007C44927C82007C1010D6D67C7C3838",
                "00002800000000000000000000000000");
    }

    private static int[][] expected8BppTile() {
        return new int[][]{
                {0x00, 0x22, 0x22, 0x22, 0x23, 0x23, 0x00, 0x00},
                {0x22, 0x11, 0x42, 0x21, 0x41, 0x11, 0x24, 0x00},
                {0x23, 0x11, 0x12, 0x12, 0x12, 0x12, 0x24, 0x00},
                {0x00, 0x24, 0x25, 0x25, 0x25, 0x25, 0x00, 0x00},
                {0x00, 0x00, 0x00, 0x33, 0x00, 0x00, 0x00, 0x00},
                {0x35, 0x32, 0x00, 0x31, 0x00, 0x31, 0x35, 0x00},
                {0x00, 0x34, 0x33, 0x31, 0x33, 0x34, 0x00, 0x00},
                {0x00, 0x00, 0x35, 0x36, 0x36, 0x00, 0x00, 0x00}
        };
    }

    private static void assertRenderedReferences(TileRenderer renderer, int paletteIndex, int[][] references) {
        int[] palette = renderer.getPalette(paletteIndex);
        for (int y = 0; y < references.length; y++) {
            for (int x = 0; x < references[y].length; x++) {
                int reference = references[y][x];
                int expected = reference == 0 ? 0 : PPUUtils.cgramColorToRGBA(palette[reference]);
                assertEquals(expected, renderer.buffer[y][x]);
            }
        }
    }

    private static void writePalette(Ram cgram, int paletteIndex, int bpp, int... colors) {
        int baseAddress = paletteIndex * bpp * bpp * 2;
        for (int i = 0; i < colors.length; i++) {
            writeCgramColor(cgram, baseAddress / 2 + i, colors[i]);
        }
    }

    private static void writeCgramColor(Ram cgram, int colorIndex, int color) {
        int address = colorIndex * 2;
        cgram.write(address, color);
        cgram.write(address + 1, color >>> 8);
    }

    private static void fillRam(Ram ram, String... values) {
        int index = 0;
        for (String row : values) {
            for (int offset = 0; offset < row.length(); offset += 2) {
                ram.write(index++, Integer.parseInt(row.substring(offset, offset + 2), 16));
            }
        }
    }
}
