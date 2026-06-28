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
    void cgramColorToRGBA() {
        assertEquals(0x000000ff, PPUUtils.cgramColorToRGBA(0x0000));
        assertEquals(0xffffffff, PPUUtils.cgramColorToRGBA(0x7fff));
        assertEquals(0xff0000ff, PPUUtils.cgramColorToRGBA(0x001f));
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

    private static void fillRam(Ram ram, String... values) {
        int index = 0;
        for (String row : values) {
            for (int offset = 0; offset < row.length(); offset += 2) {
                ram.write(index++, Integer.parseInt(row.substring(offset, offset + 2), 16));
            }
        }
    }
}
