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

        snes.bus.write(0x2105, 0b1010_0011);

        assertEquals(new Vector2<>(8, 8), snes.ppu.getCharacterSize(1));
        assertEquals(new Vector2<>(16, 16), snes.ppu.getCharacterSize(2));
        assertEquals(new Vector2<>(8, 8), snes.ppu.getCharacterSize(3));
        assertEquals(new Vector2<>(16, 16), snes.ppu.getCharacterSize(4));
    }

    @Test
    void highResolutionModesUseSixteenPixelWideCharacters() {
        SNES snes = init();

        snes.bus.write(0x2105, 0x05);
        assertEquals(new Vector2<>(16, 8), snes.ppu.getCharacterSize(1));
        assertEquals(new Vector2<>(16, 8), snes.ppu.getCharacterSize(2));

        snes.bus.write(0x2105, 0x16);
        assertEquals(new Vector2<>(16, 16), snes.ppu.getCharacterSize(1));
        assertEquals(new Vector2<>(16, 8), snes.ppu.getCharacterSize(2));
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
    void tracksHorizontalAndVerticalScrollForEveryBackground() {
        SNES snes = init();

        for (int background = 0; background < 4; background++) {
            int horizontalPort = 0x210d + background * 2;
            int verticalPort = horizontalPort + 1;
            int horizontal = 0x111 + background * 0x11;
            int vertical = 0x255 + background * 0x11;

            snes.bus.write(horizontalPort, horizontal & 0xff);
            snes.bus.write(horizontalPort, horizontal >>> 8);
            snes.bus.write(verticalPort, vertical & 0xff);
            snes.bus.write(verticalPort, vertical >>> 8);

            assertEquals(new Vector2<>(horizontal, vertical), snes.ppu.getBgScroll(background + 1));
        }
    }

    @Test
    void modeTwoOffsetLookupPreservesFineScrollAndLayerSelection() {
        SNES snes = init();
        snes.bus.write(0x2105, 0x02);
        snes.bus.write(0x2109, 0x04);
        snes.ppu.vram.write(0x0800, 0x08);
        snes.ppu.vram.write(0x0801, 0x20);

        assertEquals(3, snes.ppu.offsetPerTileHorizontalCoordinate(1, 0, 3));
        assertEquals(19, snes.ppu.offsetPerTileHorizontalCoordinate(1, 8, 3));
        assertEquals(11, snes.ppu.offsetPerTileHorizontalCoordinate(2, 8, 3));

        snes.ppu.vram.write(0x0801, 0x40);

        assertEquals(11, snes.ppu.offsetPerTileHorizontalCoordinate(1, 8, 3));
        assertEquals(19, snes.ppu.offsetPerTileHorizontalCoordinate(2, 8, 3));
    }

    @Test
    void modeSixReadsOneBg3OffsetEntryPerEightFrontendPixels() {
        SNES snes = init();
        snes.bus.write(0x2105, 0x06);
        snes.bus.write(0x2109, 0x04);
        snes.ppu.vram.write(0x0800, 0x00);
        snes.ppu.vram.write(0x0801, 0x00);
        snes.ppu.vram.write(0x0802, 0x08);
        snes.ppu.vram.write(0x0803, 0x20);

        assertEquals(8, snes.ppu.offsetPerTileHorizontalCoordinate(1, 8, 0));
        assertEquals(24, snes.ppu.offsetPerTileHorizontalCoordinate(1, 16, 0));
    }

    private static SNES init() {
        SNES snes = new SNES(new NoRenderer(0, 0, 0));
        snes.bus.mapComponents(snes);
        return snes;
    }
}
