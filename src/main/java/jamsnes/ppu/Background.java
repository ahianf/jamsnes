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

    /** Per-scanline merge parameters. Mutable so callers can reuse instances. */
    public static final class ScanlineState {
        private boolean enabled;
        private int scrollX;
        private int scrollY;
        private int mosaicSize;
        private int mosaicSourceY;
        private boolean[] windowMask;
        private int horizontalScale;
        private int horizontalPhase;
        private int[] palette;
        private boolean directColor;
        private int levelLow;
        private int levelHigh;

        ScanlineState() {
            this(false, 0, 0, 1, null, 1);
        }

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
                boolean directColor,
                int levelLow,
                int levelHigh) {
            set(enabled, scrollX, scrollY, mosaicSize, mosaicSourceY, windowMask,
                    horizontalScale, horizontalPhase, palette, directColor, levelLow, levelHigh);
        }

        /** Refills every field; the reuse equivalent of the canonical constructor. */
        public void set(
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
            this.enabled = enabled;
            this.scrollX = scrollX;
            this.scrollY = scrollY;
            this.mosaicSize = mosaicSize;
            this.mosaicSourceY = mosaicSourceY;
            this.windowMask = windowMask;
            this.horizontalScale = horizontalScale;
            this.horizontalPhase = horizontalPhase;
            this.palette = palette;
            this.directColor = directColor;
            this.levelLow = levelLow;
            this.levelHigh = levelHigh;
        }

        public boolean enabled() {
            return enabled;
        }

        public int scrollX() {
            return scrollX;
        }

        public int scrollY() {
            return scrollY;
        }

        public int mosaicSize() {
            return mosaicSize;
        }

        public int mosaicSourceY() {
            return mosaicSourceY;
        }

        public boolean[] windowMask() {
            return windowMask;
        }

        public int horizontalScale() {
            return horizontalScale;
        }

        public int horizontalPhase() {
            return horizontalPhase;
        }

        public int[] palette() {
            return palette;
        }

        public boolean directColor() {
            return directColor;
        }

        public int levelLow() {
            return levelLow;
        }

        public int levelHigh() {
            return levelHigh;
        }

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
    private Vector2<Boolean> tileMapMirroring;
    private Vector2<Integer> characterNbPixels;
    private int bpp;
    private int tileMapStartAddress;
    private int tilesetAddress;
    public Vector2<Integer> backgroundSize = new Vector2<>(0, 0);
    private final int[] offsetPerTileSourceX = new int[BUFFER_SIZE];
    private final int[] offsetPerTileScrollY = new int[BUFFER_SIZE];

    public Background(PPU ppu, int backgroundNumber) {
        this.ppu = ppu;
        this.backgroundNumber = backgroundNumber;
        this.vram = ppu.vram;
        this.tileMapMirroring = ppu.getBackgroundMirroring(backgroundNumber);
        this.characterNbPixels = ppu.getCharacterSize(backgroundNumber);
        this.bpp = ppu.getBpp(backgroundNumber);
        this.tileMapStartAddress = ppu.getTileMapStartAddress(backgroundNumber);
        this.tilesetAddress = ppu.getTilesetAddress(backgroundNumber);
    }

    /** Refreshes the logical background dimensions from the current configuration. */
    public void updateBackgroundSize() {
        int width = ((tileMapMirroring.x ? 1 : 0) + 1) * characterNbPixels.x * NB_CHARACTER_WIDTH;
        int height = ((tileMapMirroring.y ? 1 : 0) + 1) * characterNbPixels.y * NB_CHARACTER_HEIGHT;
        if (backgroundSize.x != width || backgroundSize.y != height) {
            backgroundSize = new Vector2<>(width, height);
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
        int height = Math.min(scanlineStates.length, Math.min(bufferDest.length, BUFFER_SIZE));
        for (int y = 0; y < height; y++) {
            int width = Math.min(maxWidth, Math.min(bufferDest[y].length, BUFFER_SIZE));
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
                ? Math.min(backgroundSrc.backgroundSize.y, BUFFER_SIZE)
                : BUFFER_SIZE;
        int sourceWidth = backgroundSrc.backgroundSize.x > 0
                ? Math.min(backgroundSrc.backgroundSize.x, BUFFER_SIZE)
                : BUFFER_SIZE;
        int scanlineLevelLow = state.levelLow() == USE_MERGE_LEVELS ? levelLow : state.levelLow();
        int scanlineLevelHigh = state.levelHigh() == USE_MERGE_LEVELS ? levelHigh : state.levelHigh();
        int pixelSize = Math.max(1, state.mosaicSize());
        int mosaicY = state.mosaicSourceY() == DERIVE_MOSAIC_SOURCE_FROM_OUTPUT
                ? (y / pixelSize) * pixelSize
                : state.mosaicSourceY();
        int[] offsetSourceX = null;
        int[] offsetScrollY = null;
        if (backgroundSrc.ppu.usesOffsetPerTile(backgroundSrc.backgroundNumber)) {
            offsetSourceX = backgroundSrc.offsetPerTileSourceX;
            offsetScrollY = backgroundSrc.offsetPerTileScrollY;
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

    private static final ScanlineState SAMPLE_COLOR_STATE =
            new ScanlineState(true, 0, 0, 1, null, 1);

    /**
     * Samples one logical background pixel and resolves it to RGBA against the
     * current CGRAM contents. Transparent pixels return 0.
     */
    public int samplePixelColor(int x, int y) {
        return resolveSampleColor(samplePackedPixel(x, y), SAMPLE_COLOR_STATE);
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

}
