package jamsnes.ppu;

import jamsnes.SNES;
import jamsnes.cartridge.MappingMode;
import jamsnes.models.Vector2;
import jamsnes.renderer.TestFrontend;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackgroundRenderTest {
    @Test
    void rendersTileMapTileIntoBackgroundBuffer() {
        SNES snes = init();
        Background background = new Background(snes.ppu, 1);
        background.setTileMapStartAddress(0);
        background.setTilesetAddress(0x1000);
        background.setBpp(2);
        background.setCharacterSize(new Vector2<>(8, 8));
        background.setTileMapMirroring(new Vector2<>(false, false));
        writeColor(snes, 1, 0x001f);
        snes.ppu.vram.write(0x1000, 0x80);
        snes.ppu.vram.write(0x1001, 0x00);

        background.renderBackground();

        assertEquals(new Vector2<>(256, 256), background.backgroundSize);
        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), background.buffer[0][0]);
        assertEquals(0, background.buffer[0][1]);
        assertFalse(background.isPriorityPixel(0, 0));
    }

    @Test
    void rendersPriorityTilesAndMergesByLevel() {
        SNES snes = init();
        Background low = new Background(snes.ppu, 1);
        low.setTileMapStartAddress(0);
        low.setTilesetAddress(0x1000);
        low.setBpp(2);
        low.setTileMapMirroring(new Vector2<>(false, false));
        Background high = new Background(snes.ppu, 1);
        high.setTileMapStartAddress(0x0800);
        high.setTilesetAddress(0x1000);
        high.setBpp(2);
        high.setTileMapMirroring(new Vector2<>(false, false));
        writeColor(snes, 1, 0x001f);
        writeColor(snes, 2, 0x03e0);
        snes.ppu.vram.write(0x0000, 0x00);
        snes.ppu.vram.write(0x0001, 0x00);
        snes.ppu.vram.write(0x0800, 0x01);
        snes.ppu.vram.write(0x0801, 1 << 5);
        snes.ppu.vram.write(0x1000, 0x80);
        snes.ppu.vram.write(0x1001, 0x00);
        snes.ppu.vram.write(0x1010, 0x00);
        snes.ppu.vram.write(0x1011, 0x80);

        low.renderBackground();
        high.renderBackground();

        int[][] merged = new int[16][16];
        int[][] levels = new int[16][16];
        Background.mergeBackgroundBuffer(merged, levels, low, 10, 20);
        Background.mergeBackgroundBuffer(merged, levels, high, 5, 30);

        assertTrue(high.isPriorityPixel(0, 0));
        assertEquals(PPUUtils.cgramColorToRGBA(0x03e0), merged[0][0]);
        assertEquals(30, levels[0][0]);
    }

    @Test
    void rendersHorizontalMirroredTileMap() {
        SNES snes = init();
        Background background = new Background(snes.ppu, 1);
        background.setTileMapStartAddress(0);
        background.setTilesetAddress(0x1000);
        background.setBpp(2);
        background.setTileMapMirroring(new Vector2<>(true, false));
        writeColor(snes, 1, 0x001f);
        writeColor(snes, 2, 0x03e0);
        snes.ppu.vram.write(0x0000, 0x00);
        snes.ppu.vram.write(0x0001, 0x00);
        snes.ppu.vram.write(0x0800, 0x01);
        snes.ppu.vram.write(0x0801, 0x00);
        snes.ppu.vram.write(0x1000, 0x80);
        snes.ppu.vram.write(0x1001, 0x00);
        snes.ppu.vram.write(0x1010, 0x00);
        snes.ppu.vram.write(0x1011, 0x80);

        background.renderBackground();

        assertEquals(new Vector2<>(512, 256), background.backgroundSize);
        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), background.buffer[0][0]);
        assertEquals(PPUUtils.cgramColorToRGBA(0x03e0), background.buffer[0][256]);
    }

    @Test
    void rendersVerticalTileMapFromSecondPage() {
        SNES snes = init();
        Background background = new Background(snes.ppu, 1);
        background.setTileMapStartAddress(0);
        background.setTilesetAddress(0x2000);
        background.setBpp(2);
        background.setTileMapMirroring(new Vector2<>(false, true));
        writeColor(snes, 1, 0x001f);
        writeColor(snes, 2, 0x03e0);
        snes.ppu.vram.write(0x0000, 0x00);
        snes.ppu.vram.write(0x0800, 0x01);
        snes.ppu.vram.write(0x2000, 0x80);
        snes.ppu.vram.write(0x2011, 0x80);

        background.renderBackground();

        assertEquals(new Vector2<>(256, 512), background.backgroundSize);
        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), background.buffer[0][0]);
        assertEquals(PPUUtils.cgramColorToRGBA(0x03e0), background.buffer[256][0]);
    }

    @Test
    void rendersFourTileMapPagesInRowMajorOrder() {
        SNES snes = init();
        Background background = new Background(snes.ppu, 1);
        background.setTileMapStartAddress(0);
        background.setTilesetAddress(0x2000);
        background.setBpp(2);
        background.setTileMapMirroring(new Vector2<>(true, true));
        writeColor(snes, 1, 0x001f);
        writeColor(snes, 2, 0x03e0);
        writeColor(snes, 3, 0x7c00);
        snes.ppu.vram.write(0x0000, 0x00);
        snes.ppu.vram.write(0x0800, 0x01);
        snes.ppu.vram.write(0x1000, 0x02);
        snes.ppu.vram.write(0x1800, 0x03);
        snes.ppu.vram.write(0x2000, 0x80);
        snes.ppu.vram.write(0x2011, 0x80);
        snes.ppu.vram.write(0x2020, 0x80);
        snes.ppu.vram.write(0x2021, 0x80);
        snes.ppu.vram.write(0x2030, 0x80);

        background.renderBackground();

        assertEquals(new Vector2<>(512, 512), background.backgroundSize);
        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), background.buffer[0][0]);
        assertEquals(PPUUtils.cgramColorToRGBA(0x03e0), background.buffer[0][256]);
        assertEquals(PPUUtils.cgramColorToRGBA(0x7c00), background.buffer[256][0]);
        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), background.buffer[256][256]);
    }

    @Test
    void mergesOpaqueBlackPixelsInsteadOfTreatingThemAsTransparent() {
        SNES snes = init();
        Background background = new Background(snes.ppu, 1);
        background.setTileMapStartAddress(0);
        background.setTilesetAddress(0x1000);
        background.setBpp(2);
        background.setCharacterSize(new Vector2<>(8, 8));
        background.setTileMapMirroring(new Vector2<>(false, false));
        writeColor(snes, 1, 0x0000);
        snes.ppu.vram.write(0x1000, 0x80);
        snes.ppu.vram.write(0x1001, 0x00);

        background.renderBackground();

        assertEquals(0x000000ff, background.buffer[0][0]);

        int[][] merged = new int[16][16];
        int[][] levels = new int[16][16];
        merged[0][0] = PPUUtils.cgramColorToRGBA(0x7c00);
        Background.mergeBackgroundBuffer(merged, levels, background, 10, 20);

        assertEquals(0x000000ff, merged[0][0]);
        assertEquals(10, levels[0][0]);
        assertEquals(0, levels[0][1]);
    }

    private static void writeColor(SNES snes, int colorIndex, int color) {
        int address = colorIndex * 2;
        snes.ppu.cgram.write(address, color);
        snes.ppu.cgram.write(address + 1, color >>> 8);
    }

    private static SNES init() {
        SNES snes = new SNES(new TestFrontend(0, 0, 0));
        snes.cartridge.setSize(0x10000);
        snes.cartridge.header.addMappingMode(MappingMode.LOROM);
        snes.sram.setSize(0x10000);
        snes.bus.mapComponents(snes);
        return snes;
    }
}
