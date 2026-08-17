package jamsnes.ppu;

import jamsnes.models.Vector2;
import jamsnes.ram.Ram;

import java.util.Arrays;

import static jamsnes.models.Unsigned.u16;

public class Background {
    private static final int DERIVE_MOSAIC_SOURCE_FROM_OUTPUT = Integer.MIN_VALUE;
    public static final int USE_MERGE_LEVELS = -1;
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
            int mosaicSourceY,
            boolean[] windowMask,
            int horizontalScale,
            int horizontalPhase,
            int[] palette,
            boolean directColor,
            int levelLow,
            int levelHigh) {
        public ScanlineState(
                boolean enabled,
                int scrollX,
                int scrollY,
                int mosaicSize,
                int mosaicSourceY,
                boolean[] windowMask,
                int horizontalScale,
                int horizontalPhase,
                int[] palette,
                boolean directColor) {
            this(
                    enabled,
                    scrollX,
                    scrollY,
                    mosaicSize,
                    mosaicSourceY,
                    windowMask,
                    horizontalScale,
                    horizontalPhase,
                    palette,
                    directColor,
                    USE_MERGE_LEVELS,
                    USE_MERGE_LEVELS);
        }

        public ScanlineState(
                boolean enabled,
                int scrollX,
                int scrollY,
                int mosaicSize,
                boolean[] windowMask,
                int horizontalScale,
                int horizontalPhase,
                int[] palette,
                boolean directColor) {
            this(
                    enabled,
                    scrollX,
                    scrollY,
                    mosaicSize,
                    DERIVE_MOSAIC_SOURCE_FROM_OUTPUT,
                    windowMask,
                    horizontalScale,
                    horizontalPhase,
                    palette,
                    directColor);
        }

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
    private long renderedVramModificationCount = -1;
    private long renderedCgramModificationCount = -1;
    private long renderedConfiguration = -1;

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

    /** Refreshes the logical background dimensions from the current configuration. */
    public void updateBackgroundSize() {
        backgroundSize = new Vector2<>(
                ((tileMapMirroring.x ? 1 : 0) + 1) * characterNbPixels.x * NB_CHARACTER_WIDTH,
                ((tileMapMirroring.y ? 1 : 0) + 1) * characterNbPixels.y * NB_CHARACTER_HEIGHT);
    }

    public void renderBackground() {
        long configuration = renderConfiguration();
        if (renderedVramModificationCount == vram.modificationCount()
                && renderedCgramModificationCount == ppu.cgram.modificationCount()
                && renderedConfiguration == configuration) {
            return;
        }
        renderedVramModificationCount = vram.modificationCount();
        renderedCgramModificationCount = ppu.cgram.modificationCount();
        renderedConfiguration = configuration;

        updateBackgroundSize();
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

    private long renderConfiguration() {
        return tileMapStartAddress
                | ((long) tilesetAddress << 16)
                | ((long) bpp << 32)
                | ((long) characterNbPixels.x << 37)
                | ((long) characterNbPixels.y << 43)
                | ((tileMapMirroring.x ? 1L : 0L) << 49)
                | ((tileMapMirroring.y ? 1L : 0L) << 50)
                | ((long) (ppu.getBgMode() == 0 ? backgroundNumber : 0) << 51)
                | ((ppu.ppuRegisters().cgwselDirectColorMode() ? 1L : 0L) << 54);
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
        return (tileMapEntry(x / characterNbPixels.x, y / characterNbPixels.y) & (1 << 13)) != 0;
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
        mergeBackgroundBuffer(
                bufferDest,
                pixelDestinationLevelMap,
                sourceDestinationMap,
                source,
                backgroundSrc,
                levelLow,
                levelHigh,
                scanlineStates,
                maxWidth,
                null);
    }

    static void mergeBackgroundBuffer(
            int[][] bufferDest,
            int[][] pixelDestinationLevelMap,
            int[][] sourceDestinationMap,
            int source,
            Background backgroundSrc,
            int levelLow,
            int levelHigh,
            ScanlineState[] scanlineStates,
            int maxWidth,
            short[][] cgramDestinationMap) {
        int height = Math.min(scanlineStates.length, Math.min(bufferDest.length, backgroundSrc.buffer.length));
        for (int y = 0; y < height; y++) {
            int width = Math.min(maxWidth, Math.min(bufferDest[y].length, backgroundSrc.buffer[y].length));
            short[] cgramRow = cgramDestinationMap != null && y < cgramDestinationMap.length
                    ? cgramDestinationMap[y]
                    : null;
            mergeBackgroundScanline(
                    bufferDest[y], 0,
                    pixelDestinationLevelMap[y], 0,
                    sourceDestinationMap == null ? null : sourceDestinationMap[y], 0,
                    cgramRow, 0,
                    source,
                    backgroundSrc,
                    levelLow,
                    levelHigh,
                    scanlineStates[y],
                    y,
                    width);
        }
    }

    /**
     * Merges one background scanline into row storage. The destination rows are
     * addressed as {@code array[base + x]}, so callers may pass either per-row
     * arrays (base 0) or flat frame rasters (base {@code y * stride}).
     */
    static void mergeBackgroundScanline(
            int[] colorRow, int colorBase,
            int[] levelRow, int levelBase,
            int[] sourceRow, int sourceBase,
            short[] cgramRow, int cgramBase,
            int source,
            Background backgroundSrc,
            int levelLow,
            int levelHigh,
            ScanlineState state,
            int y,
            int width) {
        if (state == null || !state.enabled()) {
            return;
        }
        int sourceHeight = backgroundSrc.backgroundSize.y > 0
                ? Math.min(backgroundSrc.backgroundSize.y, backgroundSrc.buffer.length)
                : backgroundSrc.buffer.length;
        int sourceWidth = backgroundSrc.backgroundSize.x > 0
                ? Math.min(backgroundSrc.backgroundSize.x, backgroundSrc.buffer[0].length)
                : backgroundSrc.buffer[0].length;
        int scanlineLevelLow = state.levelLow() == USE_MERGE_LEVELS ? levelLow : state.levelLow();
        int scanlineLevelHigh = state.levelHigh() == USE_MERGE_LEVELS ? levelHigh : state.levelHigh();
        int pixelSize = Math.max(1, state.mosaicSize());
        int mosaicY = state.mosaicSourceY() == DERIVE_MOSAIC_SOURCE_FROM_OUTPUT
                ? (y / pixelSize) * pixelSize
                : state.mosaicSourceY();
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
            int packed = backgroundSrc.samplePackedPixel(sourceX, sourceY);
            int pixel = backgroundSrc.resolveSampleColor(packed, state);
            if ((pixel & 0xff) == 0) {
                continue;
            }
            int pixelLevel = (packed & SAMPLE_PRIORITY) != 0
                    ? scanlineLevelHigh
                    : scanlineLevelLow;
            if (pixelLevel >= levelRow[levelBase + x]) {
                colorRow[colorBase + x] = pixel;
                levelRow[levelBase + x] = pixelLevel;
                if (cgramRow != null && x < cgramRow.length - cgramBase) {
                    int cgramIndex = backgroundSrc.resolveCgramIndex(packed, state);
                    if (cgramIndex >= 0) {
                        cgramRow[cgramBase + x] = (short) cgramIndex;
                    }
                }
                if (sourceRow != null) {
                    sourceRow[sourceBase + x] = source;
                }
            }
        }
    }

    /** Set on a packed sample when the tilemap entry requests high priority. */
    static final int SAMPLE_PRIORITY = 1 << 16;

    /**
     * Samples one logical background pixel directly from VRAM. The caller has
     * already applied scroll, mosaic, offset-per-tile, and wrapping, so
     * {@code x}/{@code y} are coordinates inside the logical background.
     * Returns {@code (priority ? SAMPLE_PRIORITY : 0) | paletteIndex << 8 |
     * pixelReference}; a pixel reference of zero is transparent.
     */
    int samplePackedPixel(int x, int y) {
        int charWidth = characterNbPixels.x;
        int charHeight = characterNbPixels.y;
        int entry = tileMapEntry(x / charWidth, y / charHeight);
        int inTileX = x % charWidth;
        int inTileY = y % charHeight;
        if ((entry & (1 << 15)) != 0) {
            inTileY = charHeight - 1 - inTileY;
        }
        if ((entry & (1 << 14)) != 0) {
            inTileX = charWidth - 1 - inTileX;
        }
        int characterColumn = entry & 0x0f;
        int characterRow = (entry >>> 4) & 0x3f;
        int graphicAddress = tilesetAddress
                + ((characterRow + inTileY / Tile.NB_PIXELS_HEIGHT) * NB_TILE_PER_ROW * bpp * Tile.BASE_BYTE_SIZE)
                + ((characterColumn + inTileX / Tile.NB_PIXELS_WIDTH) * bpp * Tile.BASE_BYTE_SIZE);
        int pixelReference = decodePixelReference(
                graphicAddress + 2 * (inTileY % Tile.NB_PIXELS_HEIGHT),
                inTileX % Tile.NB_PIXELS_WIDTH);
        int paletteBase = ppu.getBgMode() == 0 ? (backgroundNumber - 1) * 8 : 0;
        int paletteIndex = paletteBase + ((entry >>> 10) & 0x07);
        return ((entry & (1 << 13)) != 0 ? SAMPLE_PRIORITY : 0) | (paletteIndex << 8) | pixelReference;
    }

    private int tileMapEntry(int tileX, int tileY) {
        int mapColumns = tileMapMirroring.x ? 2 : 1;
        int page = (tileY / NB_CHARACTER_HEIGHT) * mapColumns + (tileX / NB_CHARACTER_WIDTH);
        int entryIndex = (tileY % NB_CHARACTER_HEIGHT) * NB_CHARACTER_WIDTH + (tileX % NB_CHARACTER_WIDTH);
        int address = u16(tileMapStartAddress + page * TILE_MAP_BYTE_SIZE + entryIndex * 2);
        return vram.read(address) | (vram.read(u16(address + 1)) << 8);
    }

    private int decodePixelReference(int rowAddress, int x) {
        int[] vramData = vram.data();
        int size = vramData.length;
        int shift = 7 - x;
        int reference = ((vramData[rowAddress % size] >>> shift) & 1)
                | (((vramData[(rowAddress + 1) % size] >>> shift) & 1) << 1);
        if (bpp == 2) {
            return reference;
        }
        if (bpp == 4 || bpp == 8) {
            reference |= (((vramData[(rowAddress + 16) % size] >>> shift) & 1) << 2)
                    | (((vramData[(rowAddress + 17) % size] >>> shift) & 1) << 3);
        } else {
            return 0;
        }
        if (bpp == 8) {
            reference |= (((vramData[(rowAddress + 32) % size] >>> shift) & 1) << 4)
                    | (((vramData[(rowAddress + 33) % size] >>> shift) & 1) << 5)
                    | (((vramData[(rowAddress + 48) % size] >>> shift) & 1) << 6)
                    | (((vramData[(rowAddress + 49) % size] >>> shift) & 1) << 7);
        }
        return reference;
    }

    private int resolveSampleColor(int packed, ScanlineState state) {
        int pixelReference = packed & 0xff;
        if (pixelReference == 0) {
            return 0;
        }
        int paletteIndex = (packed >>> 8) & 0xff;
        if (state.palette() == null) {
            if (bpp == 8 && ppu.ppuRegisters().cgwselDirectColorMode()) {
                return PPUUtils.directColorToRGBA(paletteIndex, pixelReference);
            }
            int colorAddress = (bpp == 8 ? 0 : paletteIndex) * bpp * bpp * 2 + pixelReference * 2;
            return PPUUtils.cgramColorToRGBA(
                    ppu.cgram.read(colorAddress) | (ppu.cgram.read(colorAddress + 1) << 8));
        }
        if (bpp == 8 && state.directColor()) {
            return PPUUtils.directColorToRGBA(paletteIndex, pixelReference);
        }
        int colorIndex = bpp == 8
                ? pixelReference
                : paletteIndex * (1 << bpp) + pixelReference;
        return PPUUtils.cgramColorToRGBA(state.palette()[colorIndex & 0xff]);
    }

    private int resolveCgramIndex(int packed, ScanlineState state) {
        int pixelReference = packed & 0xff;
        int paletteIndex = (packed >>> 8) & 0xff;
        if (bpp == 8 && state.directColor()) {
            return -1;
        }
        return (bpp == 8
                ? pixelReference
                : paletteIndex * (1 << bpp) + pixelReference) & 0xff;
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
        tilesPriority[indexY][indexX] = (data & (1 << 13)) != 0;
        drawTileFromMemoryToTileBuffer(data & 0x0f, (data >>> 4) & 0x3f, (data >>> 10) & 0x07);

        if ((data & (1 << 15)) != 0) {
            horizontalFlipTileBuffer(characterNbPixels.x, characterNbPixels.y);
        }
        if ((data & (1 << 14)) != 0) {
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

    private void drawTileFromMemoryToTileBuffer(int posX, int posY, int palette) {
        int paletteBase = ppu.getBgMode() == 0 ? (backgroundNumber - 1) * 8 : 0;
        tileRenderer.setPaletteIndex(paletteBase + palette);
        int tileOffsetY = 0;
        for (int y = 0; y < characterNbPixels.y; y += Tile.NB_PIXELS_HEIGHT) {
            int tileOffsetX = 0;
            for (int x = 0; x < characterNbPixels.x; x += Tile.NB_PIXELS_WIDTH) {
                int graphicAddress = tilesetAddress
                        + ((posY + tileOffsetY) * NB_TILE_PER_ROW * bpp * Tile.BASE_BYTE_SIZE)
                        + ((posX + tileOffsetX) * bpp * Tile.BASE_BYTE_SIZE);
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

}
