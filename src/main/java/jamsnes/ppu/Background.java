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

    public record ScanlineState(
            boolean enabled,
            int scrollX,
            int scrollY,
            int mosaicSize,
            boolean[] windowMask,
            int horizontalScale,
            int horizontalPhase,
            int[] palette,
            boolean directColor) {
        public ScanlineState(
                boolean enabled,
                int scrollX,
                int scrollY,
                int mosaicSize,
                boolean[] windowMask,
                int horizontalScale,
                int[] palette,
                boolean directColor) {
            this(enabled, scrollX, scrollY, mosaicSize, windowMask, horizontalScale, 0, palette, directColor);
        }

        public ScanlineState(
                boolean enabled,
                int scrollX,
                int scrollY,
                int mosaicSize,
                boolean[] windowMask,
                int horizontalScale) {
            this(enabled, scrollX, scrollY, mosaicSize, windowMask, horizontalScale, 0, null, false);
        }
    }

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
    private final short[][] tilePixelDescriptors = new short[16][16];
    private final short[][] pixelDescriptors = new short[BUFFER_SIZE][BUFFER_SIZE];
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
        backgroundSize = new Vector2<>(
                ((tileMapMirroring.x ? 1 : 0) + 1) * characterNbPixels.x * NB_CHARACTER_WIDTH,
                ((tileMapMirroring.y ? 1 : 0) + 1) * characterNbPixels.y * NB_CHARACTER_HEIGHT);
        clearBuffers();

        int mapColumns = tileMapMirroring.x ? 2 : 1;
        int mapRows = tileMapMirroring.y ? 2 : 1;
        for (int mapY = 0; mapY < mapRows; mapY++) {
            for (int mapX = 0; mapX < mapColumns; mapX++) {
                int page = mapY * mapColumns + mapX;
                drawBasicTileMap(
                        u16(tileMapStartAddress + page * TILE_MAP_BYTE_SIZE),
                        mapX,
                        mapY);
            }
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
        mergeBackgroundBuffer(
                bufferDest, pixelDestinationLevelMap, null, 0, backgroundSrc, levelLow, levelHigh, scrollX, scrollY);
    }

    public static void mergeBackgroundBuffer(
            int[][] bufferDest,
            int[][] pixelDestinationLevelMap,
            int[][] sourceDestinationMap,
            int source,
            Background backgroundSrc,
            int levelLow,
            int levelHigh,
            int scrollX,
            int scrollY) {
        mergeBackgroundBuffer(
                bufferDest, pixelDestinationLevelMap, sourceDestinationMap, source,
                backgroundSrc, levelLow, levelHigh, scrollX, scrollY, 1);
    }

    public static void mergeBackgroundBuffer(
            int[][] bufferDest,
            int[][] pixelDestinationLevelMap,
            int[][] sourceDestinationMap,
            int source,
            Background backgroundSrc,
            int levelLow,
            int levelHigh,
            int scrollX,
            int scrollY,
            int mosaicSize) {
        mergeBackgroundBuffer(
                bufferDest, pixelDestinationLevelMap, sourceDestinationMap, source,
                backgroundSrc, levelLow, levelHigh, scrollX, scrollY, mosaicSize, null);
    }

    public static void mergeBackgroundBuffer(
            int[][] bufferDest,
            int[][] pixelDestinationLevelMap,
            int[][] sourceDestinationMap,
            int source,
            Background backgroundSrc,
            int levelLow,
            int levelHigh,
            int scrollX,
            int scrollY,
            int mosaicSize,
            boolean[] windowMask) {
        mergeBackgroundBuffer(
                bufferDest, pixelDestinationLevelMap, sourceDestinationMap, source,
                backgroundSrc, levelLow, levelHigh, scrollX, scrollY, mosaicSize, windowMask, 1);
    }

    public static void mergeBackgroundBuffer(
            int[][] bufferDest,
            int[][] pixelDestinationLevelMap,
            int[][] sourceDestinationMap,
            int source,
            Background backgroundSrc,
            int levelLow,
            int levelHigh,
            int scrollX,
            int scrollY,
            int mosaicSize,
            boolean[] windowMask,
            int horizontalScale) {
        ScanlineState commonState = new ScanlineState(
                true, scrollX, scrollY, mosaicSize, windowMask, horizontalScale);
        ScanlineState[] scanlineStates = new ScanlineState[bufferDest.length];
        Arrays.fill(scanlineStates, commonState);
        mergeBackgroundBuffer(
                bufferDest,
                pixelDestinationLevelMap,
                sourceDestinationMap,
                source,
                backgroundSrc,
                levelLow,
                levelHigh,
                scanlineStates);
    }

    public static void mergeBackgroundBuffer(
            int[][] bufferDest,
            int[][] pixelDestinationLevelMap,
            int[][] sourceDestinationMap,
            int source,
            Background backgroundSrc,
            int levelLow,
            int levelHigh,
            ScanlineState[] scanlineStates) {
        mergeBackgroundBuffer(
                bufferDest,
                pixelDestinationLevelMap,
                sourceDestinationMap,
                source,
                backgroundSrc,
                levelLow,
                levelHigh,
                scanlineStates,
                Integer.MAX_VALUE);
    }

    public static void mergeBackgroundBuffer(
            int[][] bufferDest,
            int[][] pixelDestinationLevelMap,
            int[][] sourceDestinationMap,
            int source,
            Background backgroundSrc,
            int levelLow,
            int levelHigh,
            ScanlineState[] scanlineStates,
            int maxWidth) {
        int height = Math.min(scanlineStates.length, Math.min(bufferDest.length, backgroundSrc.buffer.length));
        int sourceHeight = backgroundSrc.backgroundSize.y > 0
                ? Math.min(backgroundSrc.backgroundSize.y, backgroundSrc.buffer.length)
                : backgroundSrc.buffer.length;
        int sourceWidth = backgroundSrc.backgroundSize.x > 0
                ? Math.min(backgroundSrc.backgroundSize.x, backgroundSrc.buffer[0].length)
                : backgroundSrc.buffer[0].length;
        for (int y = 0; y < height; y++) {
            ScanlineState state = scanlineStates[y];
            if (state == null || !state.enabled()) {
                continue;
            }
            int width = Math.min(maxWidth, Math.min(bufferDest[y].length, backgroundSrc.buffer[y].length));
            int pixelSize = Math.max(1, state.mosaicSize());
            int mosaicY = (y / pixelSize) * pixelSize;
            int[] offsetSourceX = null;
            int[] offsetScrollY = null;
            if (backgroundSrc.ppu.usesOffsetPerTile(backgroundSrc.backgroundNumber)) {
                offsetSourceX = new int[width];
                offsetScrollY = new int[width];
                for (int x = 0; x < width; x++) {
                    int mosaicX = (x / pixelSize) * pixelSize;
                    offsetSourceX[x] = backgroundSrc.ppu.offsetPerTileHorizontalCoordinate(
                            backgroundSrc.backgroundNumber, mosaicX, state.scrollX());
                    offsetScrollY[x] = backgroundSrc.ppu.offsetPerTileVerticalScroll(
                            backgroundSrc.backgroundNumber, mosaicX, state.scrollX(), state.scrollY());
                }
            }
            for (int x = 0; x < width; x++) {
                if (state.windowMask() != null
                        && x < state.windowMask().length
                        && state.windowMask()[x]) {
                    continue;
                }
                int mosaicX = (x / pixelSize) * pixelSize;
                int sourceCoordinateX = mosaicX + state.scrollX();
                int sourceScrollY = state.scrollY();
                if (offsetSourceX != null) {
                    sourceCoordinateX = offsetSourceX[x];
                    sourceScrollY = offsetScrollY[x];
                }
                int sourceX = Math.floorMod(
                        sourceCoordinateX * state.horizontalScale() + state.horizontalPhase(),
                        sourceWidth);
                int sourceY = Math.floorMod(mosaicY + sourceScrollY, sourceHeight);
                int pixel = backgroundSrc.resolvePixel(sourceX, sourceY, state);
                if (Integer.compareUnsigned(pixel, 0xff) <= 0) {
                    continue;
                }
                int pixelLevel = backgroundSrc.isPriorityPixel(sourceX, sourceY) ? levelHigh : levelLow;
                if (pixelLevel >= pixelDestinationLevelMap[y][x]) {
                    bufferDest[y][x] = pixel;
                    pixelDestinationLevelMap[y][x] = pixelLevel;
                    if (sourceDestinationMap != null) {
                        sourceDestinationMap[y][x] = source;
                    }
                }
            }
        }
    }

    private int resolvePixel(int x, int y, ScanlineState state) {
        if (state.palette() == null) {
            return buffer[y][x];
        }
        int descriptor = pixelDescriptors[y][x] & 0xffff;
        int pixelReference = descriptor & 0xff;
        if (pixelReference == 0) {
            return 0;
        }
        int paletteIndex = descriptor >>> 8;
        if (bpp == 8 && state.directColor()) {
            return PPUUtils.directColorToRGBA(paletteIndex, pixelReference);
        }
        int colorIndex = bpp == 8
                ? pixelReference
                : paletteIndex * (1 << bpp) + pixelReference;
        return PPUUtils.cgramColorToRGBA(state.palette()[colorIndex & 0xff]);
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
            System.arraycopy(
                    tilePixelDescriptors[y],
                    0,
                    pixelDescriptors[pixelY + y],
                    pixelX,
                    characterNbPixels.x);
        }
    }

    private void drawTileFromMemoryToTileBuffer(TileData tileData) {
        clearTileBuffer();
        int paletteBase = ppu.getBgMode() == 0 ? (backgroundNumber - 1) * 8 : 0;
        tileRenderer.setPaletteIndex(paletteBase + tileData.palette);
        int tileOffsetY = 0;
        for (int y = 0; y < characterNbPixels.y; y += Tile.NB_PIXELS_HEIGHT) {
            int tileOffsetX = 0;
            for (int x = 0; x < characterNbPixels.x; x += Tile.NB_PIXELS_WIDTH) {
                int graphicAddress = tilesetAddress
                        + ((tileData.posY + tileOffsetY) * NB_TILE_PER_ROW * bpp * Tile.BASE_BYTE_SIZE)
                        + ((tileData.posX + tileOffsetX) * bpp * Tile.BASE_BYTE_SIZE);
                tileRenderer.render(graphicAddress, bpp == 8 && ppu.ppuRegisters().cgwselDirectColorMode());
                mergeTileRendererBuffer(x, y);
                tileOffsetX++;
            }
            tileOffsetY++;
        }
    }

    private void mergeTileRendererBuffer(int offsetX, int offsetY) {
        for (int y = 0; y < Tile.NB_PIXELS_HEIGHT; y++) {
            System.arraycopy(tileRenderer.buffer[y], 0, tileBuffer[offsetY + y], offsetX, Tile.NB_PIXELS_WIDTH);
            for (int x = 0; x < Tile.NB_PIXELS_WIDTH; x++) {
                int pixelReference = tileRenderer.pixelReferences[y][x];
                tilePixelDescriptors[offsetY + y][offsetX + x] =
                        (short) ((tileRenderer.getPaletteIndex() << 8) | pixelReference);
            }
        }
    }

    private void horizontalFlipTileBuffer(int width, int height) {
        for (int y = 0; y < height / 2; y++) {
            int[] tmp = tileBuffer[y];
            tileBuffer[y] = tileBuffer[height - 1 - y];
            tileBuffer[height - 1 - y] = tmp;
            short[] descriptorTmp = tilePixelDescriptors[y];
            tilePixelDescriptors[y] = tilePixelDescriptors[height - 1 - y];
            tilePixelDescriptors[height - 1 - y] = descriptorTmp;
        }
        if (width < tileBuffer[0].length) {
            for (int y = 0; y < height; y++) {
                Arrays.fill(tileBuffer[y], width, tileBuffer[y].length, 0);
                Arrays.fill(tilePixelDescriptors[y], width, tilePixelDescriptors[y].length, (short) 0);
            }
        }
    }

    private void verticalFlipTileBuffer(int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width / 2; x++) {
                int tmp = tileBuffer[y][x];
                tileBuffer[y][x] = tileBuffer[y][width - 1 - x];
                tileBuffer[y][width - 1 - x] = tmp;
                short descriptorTmp = tilePixelDescriptors[y][x];
                tilePixelDescriptors[y][x] = tilePixelDescriptors[y][width - 1 - x];
                tilePixelDescriptors[y][width - 1 - x] = descriptorTmp;
            }
        }
    }

    private void clearBuffers() {
        int height = backgroundSize.y > 0 ? Math.min(backgroundSize.y, buffer.length) : buffer.length;
        int width = backgroundSize.x > 0 ? Math.min(backgroundSize.x, buffer[0].length) : buffer[0].length;
        for (int y = 0; y < height; y++) {
            Arrays.fill(buffer[y], 0, width, 0);
            Arrays.fill(pixelDescriptors[y], 0, width, (short) 0);
        }
        for (boolean[] row : tilesPriority) {
            Arrays.fill(row, false);
        }
    }

    private void clearTileBuffer() {
        for (int[] row : tileBuffer) {
            Arrays.fill(row, 0);
        }
        for (short[] row : tilePixelDescriptors) {
            Arrays.fill(row, (short) 0);
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
