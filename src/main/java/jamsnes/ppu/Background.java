package jamsnes.ppu;

import jamsnes.models.Vector2;
import jamsnes.ram.Ram;

import java.util.Arrays;

import static jamsnes.models.Unsigned.u16;

public class Background {
    private static final int NB_CHARACTER_WIDTH = 32;
    private static final int NB_CHARACTER_HEIGHT = 32;
    private static final int NB_TILE_PER_ROW = 16;
    private static final int TILE_MAP_BYTE_SIZE = 0x800;
    public static final int BUFFER_SIZE = 1024;
    public static final int PRIORITY_SIZE = 64;

    private final PPU ppu;
    private final int backgroundNumber;
    private final Ram vram;
    private final TileRenderer tileRenderer;
    private Vector2<Boolean> tileMapMirroring;
    private Vector2<Integer> characterNbPixels;
    private int bpp;
    private int tileMapStartAddress;
    private int tilesetAddress;
    private final int[][] tileBuffer = new int[16][16];
    public final int[][] buffer = new int[BUFFER_SIZE][BUFFER_SIZE];
    public final boolean[][] tilesPriority = new boolean[PRIORITY_SIZE][PRIORITY_SIZE];
    public Vector2<Integer> backgroundSize = new Vector2<>(0, 0);

    public Background(PPU ppu, int backgroundNumber) {
        this.ppu = ppu;
        this.backgroundNumber = backgroundNumber;
        this.vram = ppu.vram;
        this.tileRenderer = new TileRenderer(ppu.vram, ppu.cgram);
        this.tileMapMirroring = ppu.getBackgroundMirroring(backgroundNumber);
        this.characterNbPixels = ppu.getCharacterSize(backgroundNumber);
        this.bpp = ppu.getBpp(backgroundNumber);
        this.tileMapStartAddress = ppu.getTileMapStartAddress(backgroundNumber);
        this.tilesetAddress = ppu.getTilesetAddress(backgroundNumber);
        this.tileRenderer.setBpp(bpp);
    }

    public void renderBackground() {
        clearBuffers();
        int vramAddress = tileMapStartAddress;
        int offsetX = 0;
        int offsetY = 0;
        backgroundSize = new Vector2<>(
                ((tileMapMirroring.x ? 1 : 0) + 1) * characterNbPixels.x * NB_CHARACTER_WIDTH,
                ((tileMapMirroring.y ? 1 : 0) + 1) * characterNbPixels.y * NB_CHARACTER_HEIGHT);

        drawBasicTileMap(vramAddress, offsetX, offsetY);
        for (int i = 1; i < 4; i++) {
            vramAddress += TILE_MAP_BYTE_SIZE;
            offsetX++;
            if (i == 2) {
                offsetX = 0;
                offsetY++;
            }
            if (i > 1 && !tileMapMirroring.y) {
                break;
            }
            if ((i == 1 || i == 3) && !tileMapMirroring.x) {
                continue;
            }
            drawBasicTileMap(vramAddress, offsetX, offsetY);
        }
    }

    public void setTileMapStartAddress(int address) {
        tileMapStartAddress = u16(address);
    }

    public void setTilesetAddress(int address) {
        tilesetAddress = u16(address);
    }

    public void setCharacterSize(Vector2<Integer> size) {
        characterNbPixels = size;
    }

    public void setBpp(int bpp) {
        this.bpp = bpp == 2 || bpp == 4 || bpp == 8 || bpp == 7 ? bpp : 2;
        tileRenderer.setBpp(this.bpp);
    }

    public void setTileMapMirroring(Vector2<Boolean> tileMaps) {
        tileMapMirroring = tileMaps;
    }

    public int getBackgroundNumber() {
        return backgroundNumber;
    }

    public boolean isPriorityPixel(int x, int y) {
        return tilesPriority[y / characterNbPixels.y][x / characterNbPixels.x];
    }

    public static void mergeBackgroundBuffer(
            int[][] bufferDest,
            int[][] pixelDestinationLevelMap,
            Background backgroundSrc,
            int levelLow,
            int levelHigh) {
        mergeBackgroundBuffer(bufferDest, pixelDestinationLevelMap, backgroundSrc, levelLow, levelHigh, 0, 0);
    }

    public static void mergeBackgroundBuffer(
            int[][] bufferDest,
            int[][] pixelDestinationLevelMap,
            Background backgroundSrc,
            int levelLow,
            int levelHigh,
            int scrollX,
            int scrollY) {
        int height = Math.min(bufferDest.length, backgroundSrc.buffer.length);
        int sourceHeight = backgroundSrc.backgroundSize.y > 0
                ? Math.min(backgroundSrc.backgroundSize.y, backgroundSrc.buffer.length)
                : backgroundSrc.buffer.length;
        int sourceWidth = backgroundSrc.backgroundSize.x > 0
                ? Math.min(backgroundSrc.backgroundSize.x, backgroundSrc.buffer[0].length)
                : backgroundSrc.buffer[0].length;
        for (int y = 0; y < height; y++) {
            int width = Math.min(bufferDest[y].length, backgroundSrc.buffer[y].length);
            int sourceY = Math.floorMod(y + scrollY, sourceHeight);
            for (int x = 0; x < width; x++) {
                int sourceX = Math.floorMod(x + scrollX, sourceWidth);
                int pixel = backgroundSrc.buffer[sourceY][sourceX];
                if (Integer.compareUnsigned(pixel, 0xff) <= 0) {
                    continue;
                }
                int pixelLevel = backgroundSrc.isPriorityPixel(sourceX, sourceY) ? levelHigh : levelLow;
                if (pixelLevel >= pixelDestinationLevelMap[y][x]) {
                    bufferDest[y][x] = pixel;
                    pixelDestinationLevelMap[y][x] = pixelLevel;
                }
            }
        }
    }

    private void drawBasicTileMap(int baseAddress, int offsetX, int offsetY) {
        int posX = 0;
        int posY = 0;
        int vramAddress = u16(baseAddress);

        while (u16(vramAddress - baseAddress) < TILE_MAP_BYTE_SIZE) {
            int tileMapValue = vram.read(vramAddress) | (vram.read(u16(vramAddress + 1)) << 8);
            drawTile(tileMapValue, offsetX * NB_CHARACTER_WIDTH + posX, offsetY * NB_CHARACTER_HEIGHT + posY);
            vramAddress = u16(vramAddress + 2);
            if (posX % 31 == 0 && posX != 0) {
                posY++;
                posX = 0;
            } else {
                posX++;
            }
        }
    }

    private void drawTile(int data, int indexX, int indexY) {
        TileData tileData = new TileData(data);

        tilesPriority[indexY][indexX] = tileData.tilePriority;
        drawTileFromMemoryToTileBuffer(tileData);

        if (tileData.verticalFlip) {
            horizontalFlipTileBuffer(characterNbPixels.x, characterNbPixels.y);
        }
        if (tileData.horizontalFlip) {
            verticalFlipTileBuffer(characterNbPixels.x, characterNbPixels.y);
        }

        int pixelX = indexX * characterNbPixels.x;
        int pixelY = indexY * characterNbPixels.y;
        for (int y = 0; y < characterNbPixels.y; y++) {
            System.arraycopy(tileBuffer[y], 0, buffer[pixelY + y], pixelX, characterNbPixels.x);
        }
    }

    private void drawTileFromMemoryToTileBuffer(TileData tileData) {
        clearTileBuffer();
        tileRenderer.setPaletteIndex(tileData.palette);
        int tileOffsetY = 0;
        for (int y = 0; y < characterNbPixels.y; y += Tile.NB_PIXELS_HEIGHT) {
            int tileOffsetX = 0;
            for (int x = 0; x < characterNbPixels.x; x += Tile.NB_PIXELS_WIDTH) {
                int graphicAddress = tilesetAddress
                        + ((tileData.posY + tileOffsetY) * NB_TILE_PER_ROW * bpp * Tile.BASE_BYTE_SIZE)
                        + ((tileData.posX + tileOffsetX) * bpp * Tile.BASE_BYTE_SIZE);
                tileRenderer.render(graphicAddress);
                mergeTileRendererBuffer(x, y);
                tileOffsetX++;
            }
            tileOffsetY++;
        }
    }

    private void mergeTileRendererBuffer(int offsetX, int offsetY) {
        for (int y = 0; y < Tile.NB_PIXELS_HEIGHT; y++) {
            System.arraycopy(tileRenderer.buffer[y], 0, tileBuffer[offsetY + y], offsetX, Tile.NB_PIXELS_WIDTH);
        }
    }

    private void horizontalFlipTileBuffer(int width, int height) {
        for (int y = 0; y < height / 2; y++) {
            int[] tmp = tileBuffer[y];
            tileBuffer[y] = tileBuffer[height - 1 - y];
            tileBuffer[height - 1 - y] = tmp;
        }
        if (width < tileBuffer[0].length) {
            for (int y = 0; y < height; y++) {
                Arrays.fill(tileBuffer[y], width, tileBuffer[y].length, 0);
            }
        }
    }

    private void verticalFlipTileBuffer(int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width / 2; x++) {
                int tmp = tileBuffer[y][x];
                tileBuffer[y][x] = tileBuffer[y][width - 1 - x];
                tileBuffer[y][width - 1 - x] = tmp;
            }
        }
    }

    private void clearBuffers() {
        for (int[] row : buffer) {
            Arrays.fill(row, 0);
        }
        for (boolean[] row : tilesPriority) {
            Arrays.fill(row, false);
        }
    }

    private void clearTileBuffer() {
        for (int[] row : tileBuffer) {
            Arrays.fill(row, 0);
        }
    }

    private static final class TileData {
        private final int posX;
        private final int posY;
        private final int palette;
        private final boolean tilePriority;
        private final boolean horizontalFlip;
        private final boolean verticalFlip;

        private TileData(int raw) {
            posX = raw & 0x0f;
            posY = (raw >>> 4) & 0x3f;
            palette = (raw >>> 10) & 0x07;
            tilePriority = (raw & (1 << 13)) != 0;
            horizontalFlip = (raw & (1 << 14)) != 0;
            verticalFlip = (raw & (1 << 15)) != 0;
        }
    }
}
