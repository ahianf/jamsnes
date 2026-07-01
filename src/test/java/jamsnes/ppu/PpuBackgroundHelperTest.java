package jamsnes.ppu;

import jamsnes.SNES;
import jamsnes.models.Vector2;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PpuBackgroundHelperTest {
    @Test
    void returnsBitsPerPixelFromBackgroundMode() {
        SNES snes = init();

        snes.bus.write(0x2105, 0);
        assertEquals(2, snes.ppu.getBpp(1));
        assertEquals(2, snes.ppu.getBpp(4));

        snes.bus.write(0x2105, 1);
        assertEquals(4, snes.ppu.getBpp(1));
        assertEquals(4, snes.ppu.getBpp(2));
        assertEquals(2, snes.ppu.getBpp(3));

        snes.bus.write(0x2105, 3);
        assertEquals(8, snes.ppu.getBpp(1));
        assertEquals(4, snes.ppu.getBpp(2));

        snes.bus.write(0x2105, 7);
        assertEquals(8, snes.ppu.getBpp(1));
        assertEquals(7, snes.ppu.getBpp(2));
    }

    @Test
    void returnsCharacterSizeFromBgModeBits() {
        SNES snes = init();

        snes.bus.write(0x2105, 0b1010_0101);

        assertEquals(new Vector2<>(8, 8), snes.ppu.getCharacterSize(1));
        assertEquals(new Vector2<>(16, 16), snes.ppu.getCharacterSize(2));
        assertEquals(new Vector2<>(8, 8), snes.ppu.getCharacterSize(3));
        assertEquals(new Vector2<>(16, 16), snes.ppu.getCharacterSize(4));
    }

    @Test
    void returnsTileMapStartAddressAndMirroring() {
        SNES snes = init();

        snes.bus.write(0x210a, 0b1100_0110);

        assertEquals(0x8800, snes.ppu.getTileMapStartAddress(4));
        assertEquals(new Vector2<>(false, true), snes.ppu.getBackgroundMirroring(4));
    }

    @Test
    void returnsTilesetAddressFromBackgroundBaseAddressNibbles() {
        SNES snes = init();

        snes.bus.write(0x210b, 0b1010_1010);
        snes.bus.write(0x210c, 0b1100_0011);

        assertEquals(0x4000, snes.ppu.getTilesetAddress(1));
        assertEquals(0x4000, snes.ppu.getTilesetAddress(2));
        assertEquals(0x6000, snes.ppu.getTilesetAddress(3));
        assertEquals(0x8000, snes.ppu.getTilesetAddress(4));
    }

    @Test
    void tracksBackgroundScrollWithOriginalLatchIndexing() {
        SNES snes = init();

        snes.bus.write(0x210d, 0x12);
        assertEquals(new Vector2<>(0x200, 0), snes.ppu.getBgScroll(1));

        snes.bus.write(0x210f, 0x34);
        assertEquals(new Vector2<>(0x12, 0), snes.ppu.getBgScroll(2));

        snes.bus.write(0x2110, 0x56);
        assertEquals(new Vector2<>(0x234, 0), snes.ppu.getBgScroll(2));
    }

    private static SNES init() {
        SNES snes = new SNES(new NoRenderer(0, 0, 0));
        snes.bus.mapComponents(snes);
        return snes;
    }
}
