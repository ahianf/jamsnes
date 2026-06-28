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
    void returnsTilesetAddressWithOriginalNibbleBehavior() {
        SNES snes = init();

        snes.bus.write(0x210b, 0b1010_1010);
        snes.bus.write(0x210c, 0b0000_0011);

        assertEquals(0x4000, snes.ppu.getTilesetAddress(1));
        assertEquals(0x0000, snes.ppu.getTilesetAddress(2));
        assertEquals(0x6000, snes.ppu.getTilesetAddress(3));
    }

    private static SNES init() {
        SNES snes = new SNES(new NoRenderer(0, 0, 0));
        snes.bus.mapComponents(snes);
        return snes;
    }
}
