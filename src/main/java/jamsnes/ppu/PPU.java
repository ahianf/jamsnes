package jamsnes.ppu;

import jamsnes.memory.AMemory;
import jamsnes.exceptions.InvalidAddress;
import jamsnes.models.Component;
import jamsnes.models.Vector2;
import jamsnes.ram.Ram;
import jamsnes.renderer.IRenderer;

import java.util.Arrays;
import java.util.function.BooleanSupplier;

import static jamsnes.models.Unsigned.u16;
import static jamsnes.models.Unsigned.u8;

public class PPU extends AMemory {
    public static final int VRAM_SIZE = 65_536;
    public static final int CGRAM_SIZE = 512;
    public static final int OAMRAM_SIZE = 544;
    private static final int SOURCE_NONE = -1;
    private static final int SOURCE_BACKDROP = 0;
    private static final int SOURCE_OBJ = 5;
    private static final int SOURCE_OBJ_COLOR_MATH = 6;
    private static final int OBJ_COUNT = 128;
    private static final int OBJ_SCANLINE_LIMIT = 32;
    private static final int OBJ_SLIVER_LIMIT = 34;
    private static final int OBJ_LOW_TABLE_SIZE = 0x200;
    private static final int OBJ_TILE_BPP = 4;
    private static final int OBJ_TILE_BYTE_SIZE = 32;
    private static final int OBJ_TILE_ROW_SIZE = 16;
    private static final int OBJ_PALETTE_BASE = 128;
    private static final ObjectDimensions[][] OBJ_SIZE_PRESETS = {
            {new ObjectDimensions(8, 8), new ObjectDimensions(16, 16)},
            {new ObjectDimensions(8, 8), new ObjectDimensions(32, 32)},
            {new ObjectDimensions(8, 8), new ObjectDimensions(64, 64)},
            {new ObjectDimensions(16, 16), new ObjectDimensions(32, 32)},
            {new ObjectDimensions(16, 16), new ObjectDimensions(64, 64)},
            {new ObjectDimensions(32, 32), new ObjectDimensions(64, 64)},
            {new ObjectDimensions(16, 32), new ObjectDimensions(32, 64)},
            {new ObjectDimensions(16, 32), new ObjectDimensions(32, 32)}
    };
    private static final int MODE7_SIZE = 1024;
    private static final int MODE7_TILE_MAP_WIDTH = 128;
    private static final int MODE7_TILE_SIZE = 8;
    public static final int H_COUNTER_DOTS = 341;
    public static final int H_BLANK_START_DOT = 256;
    public static final int V_COUNTER_SCANLINES = 262;
    private static final int NTSC_SHORT_SCANLINE = 240;
    public static final int V_BLANK_START_SCANLINE = 225;
    public static final int OVERSCAN_V_BLANK_START_SCANLINE = 240;
    private static final int OBJ_EVALUATED_SCANLINES = OVERSCAN_V_BLANK_START_SCANLINE;
    private static final int PPU1_VERSION = 1;
    private static final int PPU2_VERSION = 3;

    public final Ram vram = new Ram(VRAM_SIZE, Component.VRAM, "VRAM");
    public final Ram oamram = new Ram(OAMRAM_SIZE, Component.OAMRAM, "OAMRAM");
    public final Ram cgram = new Ram(CGRAM_SIZE, Component.CGRAM, "CGRAM");
    private final int[] registers = new int[0x40];
    private final PPURegisters ppuRegisters = new PPURegisters(registers);
    private final IRenderer renderer;
    private BooleanSupplier externalCounterLatchEnabled = () -> true;
    private final Background[] backgrounds;
    private final int[][] mainScreen = new int[Background.BUFFER_SIZE][Background.BUFFER_SIZE];
    private final int[][] subScreen = new int[Background.BUFFER_SIZE][Background.BUFFER_SIZE];
    private final int[][] screen = new int[Background.BUFFER_SIZE][Background.BUFFER_SIZE];
    private final int[][] mainScreenLevelMap = new int[Background.BUFFER_SIZE][Background.BUFFER_SIZE];
    private final int[][] subScreenLevelMap = new int[Background.BUFFER_SIZE][Background.BUFFER_SIZE];
    private final int[][] mainScreenSourceMap = new int[Background.BUFFER_SIZE][Background.BUFFER_SIZE];
    private final int[][] subScreenSourceMap = new int[Background.BUFFER_SIZE][Background.BUFFER_SIZE];
    private final int[][] evaluatedObjectIndices = new int[OBJ_EVALUATED_SCANLINES][OBJ_SCANLINE_LIMIT];
    private final int[][] evaluatedObjectSliverMasks = new int[OBJ_EVALUATED_SCANLINES][OBJ_SCANLINE_LIMIT];
    private final int[] evaluatedObjectCounts = new int[OBJ_EVALUATED_SCANLINES];
    private final int[] scanlineDisplayControl = new int[Background.BUFFER_SIZE];
    private final ColorMathState[] scanlineColorMathStates = new ColorMathState[Background.BUFFER_SIZE];
    private final LayerState[] scanlineLayerStates = new LayerState[Background.BUFFER_SIZE];
    private final Mode7State[] scanlineMode7States = new Mode7State[Background.BUFFER_SIZE];
    private final int[][] scanlineCgramStates = new int[Background.BUFFER_SIZE][];
    private final boolean[] scanlineDisplayControlCaptured = new boolean[Background.BUFFER_SIZE];
    private int vramAddress;
    private int vmain;
    private int vramIncrementAmount = 1;
    private int vramReadBuffer;
    private int hvSharedScrollPreviousValue;
    private int hScrollPreviousValue;
    private int hCounter;
    private int vCounter;
    private long frameCounter;
    private int latchedHCounter;
    private int latchedVCounter;
    private int oamLowTableLatch;
    private boolean hCounterHighByte;
    private boolean vCounterHighByte;
    private boolean counterLatchFlag;
    private boolean secondField;
    private boolean fieldInterlace;
    private boolean fieldOverscan;
    private boolean objectRangeOver;
    private boolean objectTimeOver;
    private int ppu1OpenBus;
    private int ppu2OpenBus;

    public PPU(IRenderer renderer) {
        this.renderer = renderer;
        this.backgrounds = new Background[]{
                new Background(this, 1),
                new Background(this, 2),
                new Background(this, 3),
                new Background(this, 4)
        };
    }

    @Override
    public int read(int address) {
        return switch (address) {
            case 0x34, 0x35, 0x36 -> mode7MultiplicationResultByte(address - 0x34);
            case 0x37 -> readSoftwareLatch();
            case 0x38 -> readOamData();
            case 0x39 -> readVramLow();
            case 0x3a -> readVramHigh();
            case 0x3b -> readCgData();
            case 0x3c -> readLatchedHCounter();
            case 0x3d -> readLatchedVCounter();
            case 0x3e -> readStat77();
            case 0x3f -> readStat78();
            default -> throw new InvalidAddress("PPU Internal Registers read ", address + start);
        };
    }

    @Override
    public void write(int address, int data) {
        int value = u8(data);
        if (isReadOnlyRegister(address)) {
            return;
        }
        if (!isWritableRegister(address)) {
            throw new InvalidAddress("PPU Internal Registers write", address + start);
        }
        boolean wasForcedBlank = ppuRegisters.inidispFblank();
        registers[address] = value;
        switch (address) {
            case 0x00 -> reloadOamAddressAfterForcedBlankDeactivation(wasForcedBlank);
            case 0x02, 0x03 -> ppuRegisters.reloadOamAddress();
            case 0x04 -> writeOamData(value);
            case 0x05 -> updateBackgroundModes();
            case 0x07, 0x08, 0x09, 0x0a -> updateBackgroundTileMap(address - 0x07);
            case 0x0b -> updateBackgroundTilesets(0, 1);
            case 0x0c -> updateBackgroundTilesets(2, 3);
            case 0x0d, 0x0f, 0x11, 0x13 -> writeBgHorizontalOffset(address, value);
            case 0x0e, 0x10, 0x12, 0x14 -> writeBgVerticalOffset(address, value);
            case 0x15 -> setVmain(value);
            case 0x16 -> vramAddress = u16((vramAddress & 0xff00) | value);
            case 0x17 -> vramAddress = u16((vramAddress & 0x00ff) | (value << 8));
            case 0x18 -> {
                if (canAccessVideoMemory()) {
                    vram.write(getVramAddress(), value);
                }
                if (!isVramIncrementAfterHighByte()) {
                    incrementVramAddress();
                }
            }
            case 0x19 -> {
                if (canAccessVideoMemory()) {
                    vram.write(u16(getVramAddress() + 1), value);
                }
                if (isVramIncrementAfterHighByte()) {
                    incrementVramAddress();
                }
            }
            case 0x1b, 0x1c, 0x1d, 0x1e -> ppuRegisters.writeM7Matrix(address - 0x1b, value);
            case 0x1f, 0x20 -> ppuRegisters.writeM7Center(address - 0x1f, value);
            case 0x21 -> ppuRegisters.setCgAddress(value);
            case 0x22 -> writeCgData(value);
            case 0x32 -> ppuRegisters.writeColdata(value);
            case 0x33 -> latchFieldModeAtInitialFieldStart();
            default -> {
            }
        }
    }

    private boolean isWritableRegister(int address) {
        return address >= 0x00 && address <= 0x33;
    }

    private boolean isReadOnlyRegister(int address) {
        return address >= 0x34 && address <= 0x3f;
    }

    public int[] registers() {
        return registers;
    }

    public PPURegisters ppuRegisters() {
        return ppuRegisters;
    }

    public void setExternalCounterLatchEnabled(BooleanSupplier enabled) {
        externalCounterLatchEnabled = enabled == null ? () -> true : enabled;
    }

    public int getVramAddressRegister() {
        return vramAddress;
    }

    public int getVramAddress() {
        int wordAddress = switch ((vmain >>> 2) & 0b11) {
            case 0b00 -> vramAddress;
            case 0b01 -> (vramAddress & 0xff00) | ((vramAddress & 0x00e0) >>> 5) | ((vramAddress & 0x001f) << 3);
            case 0b10 -> (vramAddress & 0xfe00) | ((vramAddress & 0x01c0) >>> 6) | ((vramAddress & 0x003f) << 3);
            case 0b11 -> (vramAddress & 0xfc00) | ((vramAddress & 0x0380) >>> 7) | ((vramAddress & 0x007f) << 3);
            default -> vramAddress;
        };
        return u16(wordAddress * 2);
    }

    public void update(int cycles) {
        advanceCounters(cycles);
        renderFrame();
    }

    public void renderFrame() {
        renderMainAndSubScreen();
        ColorMathState currentColorMathState = currentColorMathState();
        LayerState currentLayerState = currentLayerState();
        boolean interlaced = fieldInterlace;

        for (int outputY = 0; outputY < screen.length; outputY++) {
            if (interlaced && (outputY & 1) != (secondField ? 1 : 0)) {
                continue;
            }
            int sourceY = outputY >>> 1;
            int displayControl = scanlineDisplayControlCaptured[sourceY]
                    ? scanlineDisplayControl[sourceY]
                    : registers[0x00];
            ColorMathState colorMathState = scanlineDisplayControlCaptured[sourceY]
                    ? scanlineColorMathStates[sourceY]
                    : currentColorMathState;
            LayerState layerState = scanlineDisplayControlCaptured[sourceY]
                    ? scanlineLayerStates[sourceY]
                    : currentLayerState;
            boolean highResolution = highResolutionEnabled(layerState);
            for (int x = 0; x < screen[outputY].length; x++) {
                int sourceX = x >>> 1;
                screen[outputY][x] = highResolution && (x & 1) == 0
                        ? composeSubscreenPixel(sourceX, sourceY, colorMathState)
                        : composePixel(sourceX, sourceY, colorMathState);
                renderer.putPixel(
                        outputY,
                        x,
                        applyDisplayControl(screen[outputY][x], displayControl));
            }
        }
        renderer.drawScreen();
        Arrays.fill(scanlineColorMathStates, null);
        Arrays.fill(scanlineLayerStates, null);
        Arrays.fill(scanlineMode7States, null);
        Arrays.fill(scanlineCgramStates, null);
        Arrays.fill(scanlineDisplayControlCaptured, false);
        clearBuffer(mainScreen);
        clearBuffer(subScreen);
        clearSourceMap(mainScreenSourceMap, SOURCE_NONE);
        clearSourceMap(subScreenSourceMap, SOURCE_NONE);
    }

    public void advanceCountersOnly(int cycles) {
        advanceCounters(cycles);
    }

    public void resetTimingState() {
        hCounter = 0;
        vCounter = 0;
        frameCounter = 0;
        latchedHCounter = 0;
        latchedVCounter = 0;
        hCounterHighByte = false;
        vCounterHighByte = false;
        counterLatchFlag = false;
        secondField = false;
        fieldInterlace = false;
        fieldOverscan = false;
    }

    public void resetRegisterState() {
        Arrays.fill(registers, 0);
        ppuRegisters.reset();
        registers[0x00] = 0x80;
        vramAddress = 0;
        vmain = 0;
        vramIncrementAmount = 1;
        vramReadBuffer = 0;
        hvSharedScrollPreviousValue = 0;
        hScrollPreviousValue = 0;
        oamLowTableLatch = 0;
        objectRangeOver = false;
        objectTimeOver = false;
        ppu1OpenBus = 0;
        ppu2OpenBus = 0;
        Arrays.fill(scanlineDisplayControl, 0);
        Arrays.fill(scanlineColorMathStates, null);
        Arrays.fill(scanlineLayerStates, null);
        Arrays.fill(scanlineMode7States, null);
        Arrays.fill(scanlineCgramStates, null);
        Arrays.fill(scanlineDisplayControlCaptured, false);
        updateBackgroundModes();
        for (int i = 0; i < backgrounds.length; i++) {
            updateBackgroundTileMap(i);
        }
        updateBackgroundTilesets(0, 1);
        updateBackgroundTilesets(2, 3);
    }

    public void resetMemoryState() {
        vram.clear();
        oamram.clear();
        cgram.clear();
    }

    public void renderMainAndSubScreen() {
        if (ppuRegisters.bgMode() != 7) {
            for (Background background : backgrounds) {
                background.renderBackground();
            }
        }

        fillSubScreenBackdrop();
        clearBuffer(mainScreen);
        clearBuffer(mainScreenLevelMap);
        clearBuffer(subScreenLevelMap);
        clearSourceMap(mainScreenSourceMap, SOURCE_NONE);
        clearSourceMap(subScreenSourceMap, SOURCE_BACKDROP);

        switch (ppuRegisters.bgMode()) {
            case 0 -> {
                addToMainSubScreen(backgrounds[3], 15, 25);
                addToMainSubScreen(backgrounds[2], 16, 26);
                addToMainSubScreen(backgrounds[1], 31, 35);
                addToMainSubScreen(backgrounds[0], 32, 36);
            }
            case 1 -> {
                addToMainSubScreen(backgrounds[2], 15, ppuRegisters.bgMode1Bg3PriorityBit() ? 40 : 25);
                addToMainSubScreen(backgrounds[1], 31, 35);
                addToMainSubScreen(backgrounds[0], 32, 36);
            }
            case 2, 3, 4, 5 -> {
                addToMainSubScreen(backgrounds[1], 15, 32);
                addToMainSubScreen(backgrounds[0], 25, 36);
            }
            case 6 -> addToMainSubScreen(backgrounds[0], 25, 36);
            case 7 -> addMode7ToMainSubScreen();
            default -> throw new IllegalStateException("Bg mode not implemented or commented (bg nb "
                    + ppuRegisters.bgMode() + ")");
        }
        addObjectsToMainSubScreen();
    }

    private void fillSubScreenBackdrop() {
        ColorMathState currentState = currentColorMathState();
        for (int y = 0; y < subScreen.length; y++) {
            ColorMathState state = scanlineDisplayControlCaptured[y]
                    ? scanlineColorMathStates[y]
                    : currentState;
            Arrays.fill(subScreen[y], PPUUtils.cgramColorToRGBA(state.fixedColor()));
        }
    }

    public void captureScanlineState(int scanline) {
        if (scanline < 0 || scanline >= vBlankStartScanline()) {
            return;
        }
        scanlineDisplayControl[scanline] = registers[0x00];
        scanlineColorMathStates[scanline] = currentColorMathState();
        scanlineLayerStates[scanline] = currentLayerState();
        scanlineMode7States[scanline] = currentMode7State();
        scanlineCgramStates[scanline] = currentCgramState();
        scanlineDisplayControlCaptured[scanline] = true;
    }

    private ColorMathState currentColorMathState() {
        return new ColorMathState(
                registers[0x25],
                registers[0x26],
                registers[0x27],
                registers[0x28],
                registers[0x29],
                registers[0x2b],
                registers[0x30],
                registers[0x31],
                ppuRegisters.fixedColor(),
                cgram.read(0) | (cgram.read(1) << 8));
    }

    private LayerState currentLayerState() {
        int[] offsets = new int[8];
        for (int i = 0; i < offsets.length; i++) {
            offsets[i] = ppuRegisters.bgOffset(i);
        }
        return new LayerState(
                registers[0x05],
                registers[0x33],
                registers[0x01],
                registers[0x06],
                registers[0x23],
                registers[0x24],
                registers[0x25],
                registers[0x26],
                registers[0x27],
                registers[0x28],
                registers[0x29],
                registers[0x2a],
                registers[0x2b],
                registers[0x2c],
                registers[0x2d],
                registers[0x2e],
                registers[0x2f],
                offsets);
    }

    private Mode7State currentMode7State() {
        int[] matrix = new int[4];
        for (int i = 0; i < matrix.length; i++) {
            matrix[i] = ppuRegisters.m7Matrix(i);
        }
        int[] offsets = new int[2];
        int[] centers = new int[2];
        for (int i = 0; i < offsets.length; i++) {
            offsets[i] = ppuRegisters.m7OffsetValue(i);
            centers[i] = ppuRegisters.m7CenterValue(i);
        }
        return new Mode7State(registers[0x1a], registers[0x33], matrix, offsets, centers);
    }

    private int[] currentCgramState() {
        int[] palette = new int[CGRAM_SIZE / 2];
        for (int i = 0; i < palette.length; i++) {
            int address = i * 2;
            palette[i] = cgram.read(address) | (cgram.read(address + 1) << 8);
        }
        return palette;
    }

    public int getBpp(int backgroundNumber) {
        return switch (ppuRegisters.bgMode()) {
            case 0 -> 2;
            case 1 -> backgroundNumber < 3 ? 4 : 2;
            case 2 -> 4;
            case 3 -> backgroundNumber == 1 ? 8 : 4;
            case 4 -> backgroundNumber == 1 ? 8 : 2;
            case 5 -> backgroundNumber == 1 ? 4 : 2;
            case 6 -> 4;
            case 7 -> backgroundNumber == 1 ? 8 : 7;
            default -> throw new IllegalStateException("Invalid background mode");
        };
    }

    public Vector2<Integer> getCharacterSize(int backgroundNumber) {
        boolean largeCharacters = (registers[0x05] & (1 << (3 + backgroundNumber))) != 0;
        if (ppuRegisters.bgMode() == 5 || ppuRegisters.bgMode() == 6) {
            return new Vector2<>(16, largeCharacters ? 16 : 8);
        }
        if (largeCharacters) {
            return new Vector2<>(16, 16);
        }
        return new Vector2<>(8, 8);
    }

    public int getTileMapStartAddress(int backgroundNumber) {
        return u16(ppuRegisters.bgTilemapAddress(backgroundNumber - 1) << 11);
    }

    public int getTilesetAddress(int backgroundNumber) {
        int baseAddress = registers[0x0b + (backgroundNumber > 2 ? 1 : 0)];
        baseAddress = backgroundNumber % 2 != 0 ? baseAddress & 0x0f : (baseAddress >>> 4) & 0x0f;
        return u16(baseAddress << 13);
    }

    public Vector2<Boolean> getBackgroundMirroring(int backgroundNumber) {
        return new Vector2<>(
                ppuRegisters.bgTilemapHorizontalMirroring(backgroundNumber - 1),
                ppuRegisters.bgTilemapVerticalMirroring(backgroundNumber - 1));
    }

    public Vector2<Integer> getBgScroll(int backgroundNumber) {
        int index = (backgroundNumber - 1) * 2;
        return new Vector2<>(ppuRegisters.bgOffset(index), ppuRegisters.bgOffset(index + 1));
    }

    boolean usesOffsetPerTile(int backgroundNumber) {
        int mode = ppuRegisters.bgMode();
        return ((mode == 2 || mode == 4) && backgroundNumber >= 1 && backgroundNumber <= 2)
                || (mode == 6 && backgroundNumber == 1);
    }

    int offsetPerTileHorizontalCoordinate(int backgroundNumber, int screenX, int horizontalScroll) {
        int normalCoordinate = screenX + horizontalScroll;
        if (isFirstVisibleOffsetColumn(screenX, horizontalScroll)) {
            return normalCoordinate;
        }

        int value = readOffsetPerTileEntry(backgroundNumber, screenX, horizontalScroll, false);
        if (!offsetPerTileEnabledForBackground(value, backgroundNumber)
                || (ppuRegisters.bgMode() == 4 && (value & 0x8000) != 0)) {
            return normalCoordinate;
        }
        return (normalCoordinate & 0x07) | ((screenX & ~0x07) + (value & 0x03f8));
    }

    int offsetPerTileVerticalScroll(
            int backgroundNumber,
            int screenX,
            int horizontalScroll,
            int verticalScroll) {
        if (isFirstVisibleOffsetColumn(screenX, horizontalScroll)) {
            return verticalScroll;
        }

        int value = readOffsetPerTileEntry(backgroundNumber, screenX, horizontalScroll, true);
        boolean verticalEntry = ppuRegisters.bgMode() != 4 || (value & 0x8000) != 0;
        return verticalEntry && offsetPerTileEnabledForBackground(value, backgroundNumber)
                ? value & 0x03ff
                : verticalScroll;
    }

    private boolean isFirstVisibleOffsetColumn(int screenX, int horizontalScroll) {
        return screenX < 8 - (horizontalScroll & 0x07);
    }

    private int readOffsetPerTileEntry(
            int backgroundNumber,
            int screenX,
            int horizontalScroll,
            boolean vertical) {
        Vector2<Integer> bg3Scroll = getBgScroll(3);
        int normalCoordinate = screenX + horizontalScroll;
        int mapX = (normalCoordinate & 0x07) | (((screenX - 8) & ~0x07) + (bg3Scroll.x & ~0x07));
        if (ppuRegisters.bgMode() == 6) {
            mapX *= 2;
        }
        int mapY = (bg3Scroll.y & ~0x07)
                + (vertical && ppuRegisters.bgMode() != 4 ? 8 : 0);
        return readBackgroundTileMapEntry(3, mapX, mapY);
    }

    private boolean offsetPerTileEnabledForBackground(int value, int backgroundNumber) {
        return (value & (backgroundNumber == 1 ? 0x2000 : 0x4000)) != 0;
    }

    private int readBackgroundTileMapEntry(int backgroundNumber, int pixelX, int pixelY) {
        Vector2<Integer> characterSize = getCharacterSize(backgroundNumber);
        Vector2<Boolean> mirroring = getBackgroundMirroring(backgroundNumber);
        int mapColumns = mirroring.x ? 2 : 1;
        int mapRows = mirroring.y ? 2 : 1;
        int tileX = Math.floorMod(Math.floorDiv(pixelX, characterSize.x), mapColumns * 32);
        int tileY = Math.floorMod(Math.floorDiv(pixelY, characterSize.y), mapRows * 32);
        int page = (tileY / 32) * mapColumns + tileX / 32;
        int address = getTileMapStartAddress(backgroundNumber)
                + page * 0x800
                + (((tileY & 0x1f) * 32 + (tileX & 0x1f)) * 2);
        return vram.read(u16(address)) | (vram.read(u16(address + 1)) << 8);
    }

    public int getBgMode() {
        return ppuRegisters.bgMode();
    }

    public int hCounter() {
        return hCounter;
    }

    public int vCounter() {
        return vCounter;
    }

    public long frameCounter() {
        return frameCounter;
    }

    public boolean isSecondField() {
        return secondField;
    }

    public int scanlineDotsAt(int scanline, boolean field) {
        if (field && scanline == NTSC_SHORT_SCANLINE && !fieldInterlace) {
            return H_COUNTER_DOTS - 1;
        }
        return H_COUNTER_DOTS;
    }

    public int scanlinesInField(boolean field) {
        if (!field && fieldInterlace) {
            return V_COUNTER_SCANLINES + 1;
        }
        return V_COUNTER_SCANLINES;
    }

    public boolean isInHBlank() {
        return hCounter >= H_BLANK_START_DOT;
    }

    public boolean isInVBlank() {
        return vCounter >= vBlankStartScanline();
    }

    public int vBlankStartScanline() {
        return fieldOverscan
                ? OVERSCAN_V_BLANK_START_SCANLINE
                : V_BLANK_START_SCANLINE;
    }

    public int cgramRead(int address) {
        return cgram.read(u16(address));
    }

    Background background(int index) {
        return backgrounds[index];
    }

    int[][] mainScreen() {
        return mainScreen;
    }

    int[][] subScreen() {
        return subScreen;
    }

    int[][] screen() {
        return screen;
    }

    private void setVmain(int value) {
        vmain = value;
        vramIncrementAmount = switch (value & 0b11) {
            case 0b00 -> 1;
            case 0b01 -> 32;
            default -> 128;
        };
    }

    private boolean isVramIncrementAfterHighByte() {
        return (vmain & 0b1000_0000) != 0;
    }

    private boolean canAccessVideoMemory() {
        return ppuRegisters.inidispFblank() || isInVBlank();
    }

    private void incrementVramAddress() {
        vramAddress = u16(vramAddress + vramIncrementAmount);
    }

    private int readVramLow() {
        int value = vramReadBuffer & 0xff;
        if (!isVramIncrementAfterHighByte()) {
            updateVramReadBufferIfAccessible();
            incrementVramAddress();
        }
        return readPpu1(value);
    }

    private int readVramHigh() {
        int value = (vramReadBuffer >>> 8) & 0xff;
        if (isVramIncrementAfterHighByte()) {
            updateVramReadBufferIfAccessible();
            incrementVramAddress();
        }
        return readPpu1(value);
    }

    private void updateVramReadBufferIfAccessible() {
        if (!canAccessVideoMemory()) {
            return;
        }
        vramReadBuffer = vram.read(getVramAddress()) | (vram.read(u16(getVramAddress() + 1)) << 8);
    }

    private int readCgData() {
        int address = ppuRegisters.cgByteAddress();
        int value = cgram.read(address);
        if (!ppuRegisters.isCgLowByte()) {
            value = (value & 0x7f) | (ppu2OpenBus & 0x80);
            ppuRegisters.incrementCgAddress();
        }
        ppuRegisters.toggleCgLowByte();
        return readPpu2(value);
    }

    private int readSoftwareLatch() {
        if (externalCounterLatchEnabled.getAsBoolean()) {
            latchCounters();
        }
        throw new InvalidAddress("PPU Internal Registers read ", 0x2137);
    }

    private int readLatchedHCounter() {
        int value;
        if (hCounterHighByte) {
            hCounterHighByte = false;
            value = (ppu2OpenBus & 0xfe) | ((latchedHCounter >>> 8) & 1);
        } else {
            hCounterHighByte = true;
            value = latchedHCounter & 0xff;
        }
        return readPpu2(value);
    }

    private int readLatchedVCounter() {
        int value;
        if (vCounterHighByte) {
            vCounterHighByte = false;
            value = (ppu2OpenBus & 0xfe) | ((latchedVCounter >>> 8) & 1);
        } else {
            vCounterHighByte = true;
            value = latchedVCounter & 0xff;
        }
        return readPpu2(value);
    }

    private int readStat77() {
        int value = (ppu1OpenBus & 0x10) | PPU1_VERSION;
        if (objectRangeOver) {
            value |= 0x40;
        }
        if (objectTimeOver) {
            value |= 0x80;
        }
        return readPpu1(value);
    }

    private int readStat78() {
        boolean externalLatchEnabled = externalCounterLatchEnabled.getAsBoolean();
        int value = (ppu2OpenBus & 0x20) | PPU2_VERSION
                | (!externalLatchEnabled || counterLatchFlag ? 0x40 : 0)
                | (secondField ? 0x80 : 0);
        if (externalLatchEnabled) {
            counterLatchFlag = false;
        }
        hCounterHighByte = false;
        vCounterHighByte = false;
        return readPpu2(value);
    }

    private int readPpu2(int value) {
        ppu2OpenBus = u8(value);
        return ppu2OpenBus;
    }

    private int readPpu1(int value) {
        ppu1OpenBus = u8(value);
        return ppu1OpenBus;
    }

    private void advanceCounters(int cycles) {
        if (cycles <= 0) {
            return;
        }
        hCounter += cycles;
        int scanlineDots = scanlineDotsAt(vCounter, secondField);
        while (hCounter >= scanlineDots) {
            hCounter -= scanlineDots;
            vCounter++;
            if (vCounter >= scanlinesInField(secondField)) {
                vCounter = 0;
                frameCounter++;
                secondField = !secondField;
                latchFieldMode();
                objectRangeOver = false;
                objectTimeOver = false;
            }
            if (vCounter == vBlankStartScanline()) {
                reloadOamAddressAtVBlankEntry();
            }
            scanlineDots = scanlineDotsAt(vCounter, secondField);
        }
    }

    private void latchFieldModeAtInitialFieldStart() {
        if (frameCounter == 0 && vCounter == 0 && hCounter == 0) {
            latchFieldMode();
        }
    }

    private void latchFieldMode() {
        fieldInterlace = ppuRegisters.setiniScreenInterlace();
        fieldOverscan = ppuRegisters.setiniOverscanMode();
    }

    private void reloadOamAddressAtVBlankEntry() {
        if (!ppuRegisters.inidispFblank()) {
            ppuRegisters.reloadOamAddress();
        }
    }

    private void reloadOamAddressAfterForcedBlankDeactivation(boolean wasForcedBlank) {
        if (wasForcedBlank && !ppuRegisters.inidispFblank() && vCounter == vBlankStartScanline()) {
            ppuRegisters.reloadOamAddress();
        }
    }

    public void latchCounters() {
        latchedHCounter = hCounter;
        latchedVCounter = vCounter;
        counterLatchFlag = true;
    }

    private int readOamData() {
        int value = oamram.read(getOamDataAddress());
        ppuRegisters.incrementOamAddress();
        return readPpu1(value);
    }

    private void writeCgData(int value) {
        if (ppuRegisters.isCgLowByte()) {
            ppuRegisters.setCgDataLow(value);
        } else {
            ppuRegisters.setCgDataHigh(value);
            int byteAddress = u16(ppuRegisters.cgAddress() * 2);
            if (canAccessCgramMemory()) {
                cgram.write(byteAddress, ppuRegisters.cgDataLow());
                cgram.write(u16(byteAddress + 1), ppuRegisters.cgDataHigh());
            }
            ppuRegisters.incrementCgAddress();
        }
        ppuRegisters.toggleCgLowByte();
    }

    private void writeOamData(int value) {
        int address = ppuRegisters.oamAddress();
        if (canAccessVideoMemory()) {
            if (address < OBJ_LOW_TABLE_SIZE) {
                writeOamLowTableData(address, value);
            } else {
                oamram.write(getOamDataAddress(), value);
            }
        }
        ppuRegisters.incrementOamAddress();
    }

    private boolean canAccessCgramMemory() {
        return canAccessVideoMemory() || isInHBlank();
    }

    private void writeOamLowTableData(int address, int value) {
        if ((address & 1) == 0) {
            oamLowTableLatch = value;
            return;
        }
        oamram.write(address - 1, oamLowTableLatch);
        oamram.write(address, value);
    }

    private int getOamDataAddress() {
        int address = ppuRegisters.oamAddress();
        if (address >= OBJ_LOW_TABLE_SIZE) {
            return OBJ_LOW_TABLE_SIZE + (address & 0x1f);
        }
        return address;
    }

    private void writeBgHorizontalOffset(int address, int value) {
        int offset = ((value << 8) | (hvSharedScrollPreviousValue & ~7) | (hScrollPreviousValue & 7)) & 0x3ff;
        ppuRegisters.setBgOffset(address - 0x0d, offset);
        if (address == 0x0d) {
            ppuRegisters.writeM7Offset(0, value);
        }
        hScrollPreviousValue = value;
        hvSharedScrollPreviousValue = value;
    }

    private void writeBgVerticalOffset(int address, int value) {
        int offset = ((value << 8) | hvSharedScrollPreviousValue) & 0x3ff;
        ppuRegisters.setBgOffset(address - 0x0e, offset);
        if (address == 0x0e) {
            ppuRegisters.writeM7Offset(1, value);
        }
        hvSharedScrollPreviousValue = value;
    }

    private void updateBackgroundModes() {
        for (int i = 0; i < backgrounds.length; i++) {
            backgrounds[i].setBpp(getBpp(i + 1));
            backgrounds[i].setCharacterSize(getCharacterSize(i + 1));
        }
    }

    private void updateBackgroundTileMap(int index) {
        backgrounds[index].setTileMapStartAddress(getTileMapStartAddress(index + 1));
        backgrounds[index].setTileMapMirroring(getBackgroundMirroring(index + 1));
    }

    private void updateBackgroundTilesets(int firstIndex, int secondIndex) {
        backgrounds[firstIndex].setTilesetAddress(getTilesetAddress(firstIndex + 1));
        backgrounds[secondIndex].setTilesetAddress(getTilesetAddress(secondIndex + 1));
    }

    private void addToMainSubScreen(Background background, int levelLow, int levelHigh) {
        int backgroundIndex = background.getBackgroundNumber() - 1;
        Background.mergeBackgroundBuffer(
                mainScreen,
                mainScreenLevelMap,
                mainScreenSourceMap,
                background.getBackgroundNumber(),
                background,
                levelLow,
                levelHigh,
                backgroundScanlineStates(backgroundIndex, 0));
        Background.mergeBackgroundBuffer(
                subScreen,
                subScreenLevelMap,
                subScreenSourceMap,
                background.getBackgroundNumber(),
                background,
                levelLow,
                levelHigh,
                backgroundScanlineStates(backgroundIndex, 1));
    }

    private Background.ScanlineState[] backgroundScanlineStates(int backgroundIndex, int screenIndex) {
        Background.ScanlineState[] result = new Background.ScanlineState[Background.BUFFER_SIZE];
        LayerState currentState = currentLayerState();
        ColorMathState currentColorMathState = currentColorMathState();
        int[] currentPalette = currentCgramState();
        boolean[] currentWindowMask = backgroundWindowMask(currentState, backgroundIndex, screenIndex);
        int offsetIndex = backgroundIndex * 2;
        int backgroundBit = 1 << backgroundIndex;

        for (int y = 0; y < result.length; y++) {
            LayerState state = scanlineDisplayControlCaptured[y]
                    ? scanlineLayerStates[y]
                    : currentState;
            ColorMathState colorMathState = scanlineDisplayControlCaptured[y]
                    ? scanlineColorMathStates[y]
                    : currentColorMathState;
            int[] palette = scanlineDisplayControlCaptured[y]
                    ? scanlineCgramStates[y]
                    : currentPalette;
            int designation = screenIndex == 0
                    ? state.mainScreenDesignation()
                    : state.subScreenDesignation();
            int mode = state.backgroundMode() & 0x07;
            int mosaicSize = (state.mosaic() & backgroundBit) != 0
                    ? ((state.mosaic() >>> 4) & 0x0f) + 1
                    : 1;
            boolean[] windowMask = state == currentState
                    ? currentWindowMask
                    : backgroundWindowMask(state, backgroundIndex, screenIndex);
            result[y] = new Background.ScanlineState(
                    (designation & backgroundBit) != 0,
                    state.backgroundOffsets()[offsetIndex],
                    state.backgroundOffsets()[offsetIndex + 1],
                    mosaicSize,
                    windowMask,
                    mode == 5 || mode == 6 ? 2 : 1,
                    mode == 5 || mode == 6 ? 1 - screenIndex : 0,
                    palette,
                    (colorMathState.selection() & 0x01) != 0);
        }
        return result;
    }

    private boolean[] backgroundWindowMask(LayerState state, int backgroundIndex, int screenIndex) {
        int designation = screenIndex == 0
                ? state.mainScreenWindowDesignation()
                : state.subScreenWindowDesignation();
        if ((designation & (1 << backgroundIndex)) == 0) {
            return null;
        }

        int selection = backgroundIndex < 2
                ? state.windowSelection12()
                : state.windowSelection34();
        boolean highNibble = (backgroundIndex & 1) != 0;
        int shift = highNibble ? 4 : 0;
        boolean[] mask = new boolean[Background.BUFFER_SIZE];
        for (int x = 0; x < mask.length; x++) {
            mask[x] = isInsideWindowMask(
                    (selection & (0x02 << shift)) != 0,
                    (selection & (0x01 << shift)) != 0,
                    (selection & (0x08 << shift)) != 0,
                    (selection & (0x04 << shift)) != 0,
                    (state.windowLogic() >>> (backgroundIndex * 2)) & 0x03,
                    x,
                    state.window1Left(),
                    state.window1Right(),
                    state.window2Left(),
                    state.window2Right());
        }
        return mask;
    }

    private void addMode7ToMainSubScreen() {
        renderMode7ToBuffer(mainScreen, mainScreenLevelMap, mainScreenSourceMap, 1, 20, 20, false, 0);
        renderMode7ToBuffer(subScreen, subScreenLevelMap, subScreenSourceMap, 1, 20, 20, false, 1);
        renderMode7ToBuffer(mainScreen, mainScreenLevelMap, mainScreenSourceMap, 2, 10, 30, true, 0);
        renderMode7ToBuffer(subScreen, subScreenLevelMap, subScreenSourceMap, 2, 10, 30, true, 1);
    }

    private void addObjectsToMainSubScreen() {
        evaluateObjectScanlines();
        renderObjectsToBuffer(mainScreen, mainScreenLevelMap, mainScreenSourceMap, 0);
        renderObjectsToBuffer(subScreen, subScreenLevelMap, subScreenSourceMap, 1);
    }

    private void evaluateObjectScanlines() {
        objectRangeOver = false;
        objectTimeOver = false;
        int firstObject = ppuRegisters.oamObjPriorityActivationBit() ? ppuRegisters.oamPriorityObjectNumber() : 0;
        int scanlineCount = vBlankStartScanline();
        LayerState currentState = currentLayerState();

        for (int screenY = 0; screenY < scanlineCount; screenY++) {
            LayerState state = scanlineDisplayControlCaptured[screenY]
                    ? scanlineLayerStates[screenY]
                    : currentState;
            int objectCount = 0;
            for (int offset = 0; offset < OBJ_COUNT; offset++) {
                int objectIndex = (firstObject + offset) % OBJ_COUNT;
                ObjectDimensions dimensions = objectDimensionsForObject(objectIndex, state.objectSelection());
                if (!objectIntersectsScanline(
                        objectIndex,
                        dimensions,
                        screenY,
                        objectInterlaceEnabled(state))
                        || !objectIntersectsHorizontalScreen(objectIndex, dimensions)) {
                    continue;
                }
                if (objectCount == OBJ_SCANLINE_LIMIT) {
                    objectRangeOver = true;
                    break;
                }
                evaluatedObjectIndices[screenY][objectCount] = objectIndex;
                evaluatedObjectSliverMasks[screenY][objectCount] = 0;
                objectCount++;
            }
            evaluatedObjectCounts[screenY] = objectCount;
            evaluateObjectSlivers(screenY, objectCount, state.objectSelection());
        }
    }

    private void evaluateObjectSlivers(int screenY, int objectCount, int objectSelection) {
        int sliverCount = 0;
        for (int objectSlot = objectCount - 1; objectSlot >= 0; objectSlot--) {
            int objectIndex = evaluatedObjectIndices[screenY][objectSlot];
            ObjectDimensions dimensions = objectDimensionsForObject(objectIndex, objectSelection);
            int x = objectX(objectIndex);
            int sliverMask = 0;
            for (int sliver = 0; sliver < dimensions.width() / Tile.NB_PIXELS_WIDTH; sliver++) {
                if (!objectSliverCountsTowardLimit(x, sliver)) {
                    continue;
                }
                if (sliverCount == OBJ_SLIVER_LIMIT) {
                    objectTimeOver = true;
                    continue;
                }
                sliverMask |= 1 << sliver;
                sliverCount++;
            }
            evaluatedObjectSliverMasks[screenY][objectSlot] = sliverMask;
        }
    }

    private boolean objectIntersectsScanline(
            int objectIndex,
            ObjectDimensions dimensions,
            int screenY,
            boolean objectInterlace) {
        int objectY = oamram.read(objectIndex * 4 + 1);
        int height = dimensions.height() >>> (objectInterlace ? 1 : 0);
        return u8(screenY - objectY) < height;
    }

    private boolean objectIntersectsHorizontalScreen(int objectIndex, ObjectDimensions dimensions) {
        int x = objectX(objectIndex);
        return x == -256 || (x < H_BLANK_START_DOT && x + dimensions.width() > 0);
    }

    private boolean objectSliverCountsTowardLimit(int objectX, int sliver) {
        if (objectX == -256) {
            return true;
        }
        int sliverX = objectX + sliver * Tile.NB_PIXELS_WIDTH;
        return sliverX < H_BLANK_START_DOT && sliverX + Tile.NB_PIXELS_WIDTH > 0;
    }

    private void renderObjectsToBuffer(int[][] destination, int[][] levelMap, int[][] sourceMap, int screenIndex) {
        LayerState currentState = currentLayerState();
        int[] currentPalette = currentCgramState();
        boolean[] currentWindowMask = objectWindowMask(currentState, screenIndex);
        for (int screenY = 0; screenY < vBlankStartScanline(); screenY++) {
            boolean captured = scanlineDisplayControlCaptured[screenY];
            LayerState state = captured
                    ? scanlineLayerStates[screenY]
                    : currentState;
            int[] palette = captured ? scanlineCgramStates[screenY] : currentPalette;
            int designation = screenIndex == 0
                    ? state.mainScreenDesignation()
                    : state.subScreenDesignation();
            if ((designation & 0x10) == 0) {
                continue;
            }
            boolean[] windowMask = state == currentState
                    ? currentWindowMask
                    : objectWindowMask(state, screenIndex);
            for (int objectSlot = evaluatedObjectCounts[screenY] - 1; objectSlot >= 0; objectSlot--) {
                renderObjectScanlineToBuffer(
                        evaluatedObjectIndices[screenY][objectSlot],
                        evaluatedObjectSliverMasks[screenY][objectSlot],
                        screenY,
                        destination,
                        levelMap,
                        sourceMap,
                        windowMask,
                        palette,
                        state.objectSelection(),
                        objectInterlaceEnabled(state));
            }
        }
    }

    private void renderObjectScanlineToBuffer(
            int objectIndex,
            int sliverMask,
            int screenY,
            int[][] destination,
            int[][] levelMap,
            int[][] sourceMap,
            boolean[] windowMask,
            int[] paletteColors,
            int objectSelection,
            boolean objectInterlace) {
        int objectAddress = objectIndex * 4;
        int y = oamram.read(objectAddress + 1);
        int tile = oamram.read(objectAddress + 2);
        int attributes = oamram.read(objectAddress + 3);
        int x = objectX(objectIndex);
        ObjectDimensions dimensions = objectDimensionsForObject(objectIndex, objectSelection);
        int level = objectPriorityLevel((attributes >>> 4) & 0x03);
        int palette = (attributes >>> 1) & 0x07;
        boolean horizontalFlip = (attributes & 0x40) != 0;
        boolean verticalFlip = (attributes & 0x80) != 0;
        int baseAddress = objectTileBaseAddress(attributes, objectSelection);
        int pixelY = u8(screenY - y);
        if (objectInterlace) {
            pixelY = u8(pixelY << 1);
        }
        int sourceY = verticalFlip ? verticallyFlippedObjectY(pixelY, dimensions) : pixelY;
        if (objectInterlace && secondField) {
            sourceY = u8(sourceY + (verticalFlip ? -1 : 1));
        }

        for (int pixelX = 0; pixelX < dimensions.width(); pixelX++) {
            if ((sliverMask & (1 << (pixelX / Tile.NB_PIXELS_WIDTH))) == 0) {
                continue;
            }
            int screenX = x + pixelX;
            if (screenX < 0 || screenX >= destination[screenY].length) {
                continue;
            }
            if (windowMask != null && screenX < windowMask.length && windowMask[screenX]) {
                continue;
            }
            int sourceX = horizontalFlip ? dimensions.width() - 1 - pixelX : pixelX;
            int color = readObjectPixel(baseAddress, tile, palette, sourceX, sourceY, paletteColors);
            if (Integer.compareUnsigned(color, 0xff) <= 0 || level < levelMap[screenY][screenX]) {
                continue;
            }
            destination[screenY][screenX] = color;
            levelMap[screenY][screenX] = level;
            sourceMap[screenY][screenX] = palette >= 4 ? SOURCE_OBJ_COLOR_MATH : SOURCE_OBJ;
        }
    }

    private int objectX(int objectIndex) {
        int x = oamram.read(objectIndex * 4);
        int highTable = oamram.read(OBJ_LOW_TABLE_SIZE + objectIndex / 4);
        int highShift = (objectIndex % 4) * 2;
        if (((highTable >>> highShift) & 0x01) != 0) {
            x |= 0x100;
        }
        return x >= 256 ? x - 512 : x;
    }

    private boolean objectInterlaceEnabled(LayerState state) {
        return (state.setini() & 0x02) != 0;
    }

    private ObjectDimensions objectDimensionsForObject(int objectIndex, int objectSelection) {
        int highTable = oamram.read(OBJ_LOW_TABLE_SIZE + objectIndex / 4);
        int highShift = (objectIndex % 4) * 2;
        return objectDimensions((highTable >>> (highShift + 1)) & 0x01, objectSelection);
    }

    private ObjectDimensions objectDimensions(int sizeBit, int objectSelection) {
        return OBJ_SIZE_PRESETS[(objectSelection >>> 5) & 0x07][sizeBit];
    }

    private int verticallyFlippedObjectY(int pixelY, ObjectDimensions dimensions) {
        if (dimensions.height() == dimensions.width()) {
            return dimensions.height() - 1 - pixelY;
        }
        int squareStart = pixelY / dimensions.width() * dimensions.width();
        return squareStart + dimensions.width() - 1 - pixelY % dimensions.width();
    }

    private int objectPriorityLevel(int priority) {
        return switch (priority) {
            case 0 -> 18;
            case 1 -> 28;
            case 2 -> 34;
            case 3 -> 38;
            default -> 18;
        };
    }

    private int objectTileBaseAddress(int attributes, int objectSelection) {
        int base = (objectSelection & 0x07) << 13;
        if ((attributes & 0x01) != 0) {
            base += (((objectSelection >>> 3) & 0x03) + 1) << 12;
        }
        return u16(base);
    }

    private int readObjectPixel(
            int baseAddress,
            int tile,
            int palette,
            int sourceX,
            int sourceY,
            int[] paletteColors) {
        int tileX = sourceX / Tile.NB_PIXELS_WIDTH;
        int tileY = sourceY / Tile.NB_PIXELS_HEIGHT;
        int pixelX = sourceX % Tile.NB_PIXELS_WIDTH;
        int pixelY = sourceY % Tile.NB_PIXELS_HEIGHT;
        int tileNumber = objectTileNumber(tile, tileX, tileY);
        int rowAddress = u16(baseAddress + tileNumber * OBJ_TILE_BYTE_SIZE + pixelY * 2);
        int colorIndex = readObjectPixelReference(rowAddress, pixelX);
        if (colorIndex == 0) {
            return 0;
        }
        int cgramIndex = OBJ_PALETTE_BASE + palette * 16 + colorIndex;
        return PPUUtils.cgramColorToRGBA(paletteColors[cgramIndex]);
    }

    private int objectTileNumber(int tile, int tileX, int tileY) {
        return (tile & 0xf0) + ((tile + tileX) & 0x0f) + tileY * 16;
    }

    private int readObjectPixelReference(int rowAddress, int pixelX) {
        int shift = 7 - pixelX;
        int result = 0;
        for (int plane = 0; plane < OBJ_TILE_BPP; plane++) {
            int planeAddress = rowAddress + (plane / 2) * OBJ_TILE_ROW_SIZE + (plane % 2);
            result |= ((vram.read(u16(planeAddress)) >>> shift) & 1) << plane;
        }
        return result;
    }

    private boolean[] layerWindowMask(int backgroundIndex, int screenIndex) {
        if (!ppuRegisters.windowMaskDesignationBackground(screenIndex, backgroundIndex)) {
            return null;
        }
        boolean[] mask = new boolean[Background.BUFFER_SIZE];
        for (int x = 0; x < mask.length; x++) {
            mask[x] = isInsideLayerWindow(backgroundIndex, x);
        }
        return mask;
    }

    private boolean[] objectWindowMask(LayerState state, int screenIndex) {
        int designation = screenIndex == 0
                ? state.mainScreenWindowDesignation()
                : state.subScreenWindowDesignation();
        if ((designation & 0x10) == 0) {
            return null;
        }

        int selection = state.objectWindowSelection();
        boolean[] mask = new boolean[Background.BUFFER_SIZE];
        for (int x = 0; x < mask.length; x++) {
            mask[x] = isInsideWindowMask(
                    (selection & 0x02) != 0,
                    (selection & 0x01) != 0,
                    (selection & 0x08) != 0,
                    (selection & 0x04) != 0,
                    state.objectWindowLogic() & 0x03,
                    x,
                    state.window1Left(),
                    state.window1Right(),
                    state.window2Left(),
                    state.window2Right());
        }
        return mask;
    }

    private boolean isInsideLayerWindow(int backgroundIndex, int x) {
        int groupIndex = backgroundIndex / 2;
        boolean bg1Bg3Window = (backgroundIndex & 1) == 0;
        boolean window1Enabled = bg1Bg3Window
                ? ppuRegisters.windowEnableWindow1ForBg1Bg3Obj(groupIndex)
                : ppuRegisters.windowEnableWindow1ForBg2Bg4Color(groupIndex);
        boolean window2Enabled = bg1Bg3Window
                ? ppuRegisters.windowEnableWindow2ForBg1Bg3Obj(groupIndex)
                : ppuRegisters.windowEnableWindow2ForBg2Bg4Color(groupIndex);
        boolean window1Inverted = bg1Bg3Window
                ? ppuRegisters.window1InversionForBg1Bg3Obj(groupIndex)
                : ppuRegisters.window1InversionForBg2Bg4Color(groupIndex);
        boolean window2Inverted = bg1Bg3Window
                ? ppuRegisters.window2InversionForBg1Bg3Obj(groupIndex)
                : ppuRegisters.window2InversionForBg2Bg4Color(groupIndex);
        return isInsideWindowMask(window1Enabled, window1Inverted, window2Enabled, window2Inverted,
                windowMaskLogic(backgroundIndex), x);
    }

    private int windowMaskLogic(int backgroundIndex) {
        return switch (backgroundIndex) {
            case 0 -> ppuRegisters.windowMaskLogicBg1();
            case 1 -> ppuRegisters.windowMaskLogicBg2();
            case 2 -> ppuRegisters.windowMaskLogicBg3();
            case 3 -> ppuRegisters.windowMaskLogicBg4();
            default -> 0;
        };
    }

    private void renderMode7ToBuffer(
            int[][] destination,
            int[][] levelMap,
            int[][] sourceMap,
            int source,
            int levelLow,
            int levelHigh,
            boolean extBg,
            int screenIndex) {
        int backgroundIndex = source - 1;
        int backgroundBit = 1 << backgroundIndex;
        LayerState currentLayerState = currentLayerState();
        Mode7State currentMode7State = currentMode7State();
        ColorMathState currentColorMathState = currentColorMathState();
        int[] currentPalette = currentCgramState();
        boolean[] currentWindowMask = backgroundWindowMask(currentLayerState, backgroundIndex, screenIndex);

        for (int y = 0; y < destination.length; y++) {
            boolean captured = scanlineDisplayControlCaptured[y];
            LayerState layerState = captured ? scanlineLayerStates[y] : currentLayerState;
            Mode7State mode7State = captured ? scanlineMode7States[y] : currentMode7State;
            ColorMathState colorMathState = captured ? scanlineColorMathStates[y] : currentColorMathState;
            int[] palette = captured ? scanlineCgramStates[y] : currentPalette;
            int designation = screenIndex == 0
                    ? layerState.mainScreenDesignation()
                    : layerState.subScreenDesignation();
            if ((layerState.backgroundMode() & 0x07) != 7
                    || (designation & backgroundBit) == 0
                    || (extBg && (mode7State.setini() & 0x40) == 0)) {
                continue;
            }

            int a = signed16(mode7State.matrix()[0]);
            int b = signed16(mode7State.matrix()[1]);
            int c = signed16(mode7State.matrix()[2]);
            int d = signed16(mode7State.matrix()[3]);
            int centerX = signed13(mode7State.centers()[0]);
            int centerY = signed13(mode7State.centers()[1]);
            int scrollX = signed13(mode7State.offsets()[0]);
            int scrollY = signed13(mode7State.offsets()[1]);
            int clippedScrollX = clipMode7Offset(scrollX - centerX);
            int clippedScrollY = clipMode7Offset(scrollY - centerY);
            int mosaicSize = ((layerState.mosaic() >>> 4) & 0x0f) + 1;
            boolean horizontalMosaic = (layerState.mosaic() & backgroundBit) != 0;
            boolean verticalMosaic = (layerState.mosaic() & 0x01) != 0;
            boolean[] windowMask = captured
                    ? backgroundWindowMask(layerState, backgroundIndex, screenIndex)
                    : currentWindowMask;
            int screenY = verticalMosaic ? (y / mosaicSize) * mosaicSize : y;
            if ((mode7State.settings() & 0x02) != 0) {
                screenY = 255 - screenY;
            }
            int originX = (a * clippedScrollX & ~63)
                    + (b * clippedScrollY & ~63)
                    + (b * screenY & ~63)
                    + (centerX << 8);
            int originY = (c * clippedScrollX & ~63)
                    + (d * clippedScrollY & ~63)
                    + (d * screenY & ~63)
                    + (centerY << 8);
            for (int x = 0; x < destination[y].length; x++) {
                if (windowMask != null && windowMask[x]) {
                    continue;
                }
                int screenX = horizontalMosaic ? (x / mosaicSize) * mosaicSize : x;
                if ((mode7State.settings() & 0x01) != 0) {
                    screenX = 255 - screenX;
                }
                int sourceX = (originX + a * screenX) >> 8;
                int sourceY = (originY + c * screenX) >> 8;
                Mode7Pixel pixel = readMode7Pixel(
                        sourceX,
                        sourceY,
                        extBg,
                        mode7State.settings(),
                        (colorMathState.selection() & 0x01) != 0,
                        palette);
                int level = pixel.priority ? levelHigh : levelLow;
                if (Integer.compareUnsigned(pixel.color, 0xff) <= 0 || level < levelMap[y][x]) {
                    continue;
                }
                destination[y][x] = pixel.color;
                levelMap[y][x] = level;
                sourceMap[y][x] = source;
            }
        }
    }

    private Mode7Pixel readMode7Pixel(
            int sourceX,
            int sourceY,
            boolean extBg,
            int settings,
            boolean directColor,
            int[] palette) {
        boolean outsidePlayingField = sourceX < 0 || sourceX >= MODE7_SIZE || sourceY < 0 || sourceY >= MODE7_SIZE;
        boolean largePlayingField = (settings & 0x80) != 0;
        if (outsidePlayingField && largePlayingField && (settings & 0x40) == 0) {
            return Mode7Pixel.TRANSPARENT;
        }

        int wrappedX = sourceX & (MODE7_SIZE - 1);
        int wrappedY = sourceY & (MODE7_SIZE - 1);
        int pixelX = wrappedX % MODE7_TILE_SIZE;
        int pixelY = wrappedY % MODE7_TILE_SIZE;
        int tile;
        if (outsidePlayingField && largePlayingField) {
            tile = 0;
        } else {
            int tileX = wrappedX / MODE7_TILE_SIZE;
            int tileY = wrappedY / MODE7_TILE_SIZE;
            int tileWordAddress = tileY * MODE7_TILE_MAP_WIDTH + tileX;
            tile = vram.read(u16(tileWordAddress * 2));
        }
        int pixelWordAddress = tile * 64 + pixelY * MODE7_TILE_SIZE + pixelX;
        int colorIndex = vram.read(u16(pixelWordAddress * 2 + 1));
        boolean priority = false;
        if (extBg) {
            priority = (colorIndex & 0x80) != 0;
            colorIndex &= 0x7f;
        }
        if (colorIndex == 0) {
            return Mode7Pixel.TRANSPARENT;
        }
        if (!extBg && directColor) {
            return new Mode7Pixel(PPUUtils.directColorToRGBA(0, colorIndex), priority);
        }
        return new Mode7Pixel(PPUUtils.cgramColorToRGBA(palette[colorIndex]), priority);
    }

    private int signed16(int value) {
        return (short) u16(value);
    }

    private int mode7MultiplicationResultByte(int index) {
        int operandA = signed16(ppuRegisters.m7Matrix(0));
        int operandB = signed8(ppuRegisters.m7Matrix(1) >>> 8);
        int result = operandA * operandB;
        return readPpu1((result >>> (index * 8)) & 0xff);
    }

    private int signed8(int value) {
        return (byte) u8(value);
    }

    private int signed13(int value) {
        int normalized = value & 0x1fff;
        return (normalized & 0x1000) != 0 ? normalized - 0x2000 : normalized;
    }

    private int clipMode7Offset(int value) {
        return (value & 0x2000) != 0 ? value | ~0x3ff : value & 0x3ff;
    }

    private record Mode7Pixel(int color, boolean priority) {
        private static final Mode7Pixel TRANSPARENT = new Mode7Pixel(0, false);
    }

    private record ColorMathState(
            int windowSelection,
            int window1Left,
            int window1Right,
            int window2Left,
            int window2Right,
            int windowLogic,
            int selection,
            int designation,
            int fixedColor,
            int backdropColor) {
    }

    private record LayerState(
            int backgroundMode,
            int setini,
            int objectSelection,
            int mosaic,
            int windowSelection12,
            int windowSelection34,
            int objectWindowSelection,
            int window1Left,
            int window1Right,
            int window2Left,
            int window2Right,
            int windowLogic,
            int objectWindowLogic,
            int mainScreenDesignation,
            int subScreenDesignation,
            int mainScreenWindowDesignation,
            int subScreenWindowDesignation,
            int[] backgroundOffsets) {
    }

    private record Mode7State(
            int settings,
            int setini,
            int[] matrix,
            int[] offsets,
            int[] centers) {
    }

    private record ObjectDimensions(int width, int height) {
    }

    private void addBuffer(int[][] destination, int[][] source) {
        for (int y = 0; y < source.length; y++) {
            for (int x = 0; x < source[y].length; x++) {
                if (Integer.compareUnsigned(source[y][x], 0xff) > 0) {
                    destination[y][x] = source[y][x];
                }
            }
        }
    }

    private int composePixel(int x, int y, ColorMathState colorMathState) {
        int pixel = mainScreen[y][x];
        int source = mainScreenSourceMap[y][x];
        if (source == SOURCE_NONE) {
            pixel = PPUUtils.cgramColorToRGBA(colorMathState.backdropColor());
            source = SOURCE_BACKDROP;
        }
        boolean clippedToBlack = isColorClippedToBlack(x, colorMathState);
        if (clippedToBlack) {
            pixel = 0x000000ff;
        }
        return applyColorMath(pixel, source, x, y, clippedToBlack, colorMathState);
    }

    private int composeSubscreenPixel(int x, int y, ColorMathState colorMathState) {
        int pixel = subScreenSourceMap[y][x] == SOURCE_BACKDROP
                ? PPUUtils.cgramColorToRGBA(colorMathState.backdropColor())
                : subScreen[y][x];
        int mainSource = mainScreenSourceMap[y][x];
        if (mainSource == SOURCE_NONE) {
            mainSource = SOURCE_BACKDROP;
        }
        boolean mainClippedToBlack = isColorClippedToBlack(x, colorMathState);
        if (mainClippedToBlack) {
            return 0x000000ff;
        }
        if (isColorMathPrevented(x, colorMathState)
                || !isColorMathEnabledForSource(mainSource, colorMathState)) {
            return pixel;
        }
        boolean addMainScreen = (colorMathState.selection() & 0x02) != 0;
        int other = addMainScreen
                ? unblendedMainPixel(x, y, colorMathState)
                : PPUUtils.cgramColorToRGBA(colorMathState.fixedColor());
        boolean half = (colorMathState.designation() & 0x40) != 0;
        return (colorMathState.designation() & 0x80) != 0
                ? subtractColor(pixel, other, half)
                : addColor(pixel, other, half);
    }

    private int unblendedMainPixel(
            int x,
            int y,
            ColorMathState colorMathState) {
        if (mainScreenSourceMap[y][x] == SOURCE_NONE) {
            return PPUUtils.cgramColorToRGBA(colorMathState.backdropColor());
        }
        return mainScreen[y][x];
    }

    private boolean highResolutionEnabled(LayerState state) {
        int mode = state.backgroundMode() & 0x07;
        return (state.setini() & 0x08) != 0 || mode == 5 || mode == 6;
    }

    private int applyColorMath(
            int pixel,
            int source,
            int x,
            int y,
            boolean clippedToBlack,
            ColorMathState colorMathState) {
        if (isColorMathPrevented(x, colorMathState)
                || !isColorMathEnabledForSource(source, colorMathState)) {
            return pixel;
        }
        boolean addSubscreen = (colorMathState.selection() & 0x02) != 0;
        int other = addSubscreen
                ? subScreen[y][x]
                : PPUUtils.cgramColorToRGBA(colorMathState.fixedColor());
        boolean half = (colorMathState.designation() & 0x40) != 0
                && !clippedToBlack
                && (!addSubscreen || subScreenSourceMap[y][x] != SOURCE_BACKDROP);
        return (colorMathState.designation() & 0x80) != 0
                ? subtractColor(pixel, other, half)
                : addColor(pixel, other, half);
    }

    private boolean isColorMathEnabledForSource(int source, ColorMathState colorMathState) {
        int designation = colorMathState.designation();
        if (source == SOURCE_BACKDROP) {
            return (designation & 0x20) != 0;
        }
        if (source >= 1 && source <= 4) {
            return (designation & (1 << (source - 1))) != 0;
        }
        if (source == SOURCE_OBJ_COLOR_MATH) {
            return (designation & 0x10) != 0;
        }
        return false;
    }

    private boolean isColorClippedToBlack(int x, ColorMathState colorMathState) {
        return isColorWindowModeActive((colorMathState.selection() >>> 6) & 0x03, x, colorMathState);
    }

    private boolean isColorMathPrevented(int x, ColorMathState colorMathState) {
        return isColorWindowModeActive((colorMathState.selection() >>> 4) & 0x03, x, colorMathState);
    }

    private boolean isColorWindowModeActive(int mode, int x, ColorMathState colorMathState) {
        return switch (mode) {
            case 0b00 -> false;
            case 0b01 -> !isInsideColorWindow(x, colorMathState);
            case 0b10 -> isInsideColorWindow(x, colorMathState);
            case 0b11 -> true;
            default -> false;
        };
    }

    private boolean isInsideColorWindow(int x, ColorMathState colorMathState) {
        int selection = colorMathState.windowSelection();
        return isInsideWindowMask(
                (selection & 0x20) != 0,
                (selection & 0x10) != 0,
                (selection & 0x80) != 0,
                (selection & 0x40) != 0,
                (colorMathState.windowLogic() >>> 2) & 0x03,
                x,
                colorMathState.window1Left(),
                colorMathState.window1Right(),
                colorMathState.window2Left(),
                colorMathState.window2Right());
    }

    private boolean isInsideWindowMask(
            boolean window1Enabled,
            boolean window1Inverted,
            boolean window2Enabled,
            boolean window2Inverted,
            int maskLogic,
            int x) {
        return isInsideWindowMask(
                window1Enabled,
                window1Inverted,
                window2Enabled,
                window2Inverted,
                maskLogic,
                x,
                ppuRegisters.windowPosition(0),
                ppuRegisters.windowPosition(1),
                ppuRegisters.windowPosition(2),
                ppuRegisters.windowPosition(3));
    }

    private boolean isInsideWindowMask(
            boolean window1Enabled,
            boolean window1Inverted,
            boolean window2Enabled,
            boolean window2Inverted,
            int maskLogic,
            int x,
            int window1Left,
            int window1Right,
            int window2Left,
            int window2Right) {
        boolean window1 = window1Enabled && isInsideWindow(x, window1Left, window1Right);
        boolean window2 = window2Enabled && isInsideWindow(x, window2Left, window2Right);

        if (window1Enabled && window1Inverted) {
            window1 = !window1;
        }
        if (window2Enabled && window2Inverted) {
            window2 = !window2;
        }
        if (!window1Enabled) {
            return window2Enabled && window2;
        }
        if (!window2Enabled) {
            return window1;
        }
        return switch (maskLogic) {
            case 0b00 -> window1 || window2;
            case 0b01 -> window1 && window2;
            case 0b10 -> window1 ^ window2;
            case 0b11 -> window1 == window2;
            default -> false;
        };
    }

    private boolean isInsideWindow(int x, int positionIndex) {
        return isInsideWindow(
                x,
                ppuRegisters.windowPosition(positionIndex),
                ppuRegisters.windowPosition(positionIndex + 1));
    }

    private boolean isInsideWindow(int x, int left, int right) {
        return left <= right && x >= left && x <= right;
    }

    private int addColor(int left, int right, boolean half) {
        int red = channel5(left, 24) + channel5(right, 24);
        int green = channel5(left, 16) + channel5(right, 16);
        int blue = channel5(left, 8) + channel5(right, 8);
        if (half) {
            red >>>= 1;
            green >>>= 1;
            blue >>>= 1;
        }
        return packColor5(clamp5(red), clamp5(green), clamp5(blue), left & 0xff);
    }

    private int subtractColor(int left, int right, boolean half) {
        int red = channel5(left, 24) - channel5(right, 24);
        int green = channel5(left, 16) - channel5(right, 16);
        int blue = channel5(left, 8) - channel5(right, 8);
        if (half) {
            red >>= 1;
            green >>= 1;
            blue >>= 1;
        }
        return packColor5(clamp5(red), clamp5(green), clamp5(blue), left & 0xff);
    }

    private int channel(int color, int shift) {
        return (color >>> shift) & 0xff;
    }

    private int channel5(int color, int shift) {
        return channel(color, shift) >>> 3;
    }

    private int clamp5(int value) {
        return Math.max(0, Math.min(0x1f, value));
    }

    private int clamp8(int value) {
        return Math.max(0, Math.min(0xff, value));
    }

    private int packColor5(int red, int green, int blue, int alpha) {
        return packColor(PPUUtils.to8Bit(red), PPUUtils.to8Bit(green), PPUUtils.to8Bit(blue), alpha);
    }

    private int packColor(int red, int green, int blue, int alpha) {
        return (red << 24) | (green << 16) | (blue << 8) | alpha;
    }

    private void clearBuffer(int[][] buffer) {
        fillBuffer(buffer, 0);
    }

    private void fillBuffer(int[][] buffer, int value) {
        for (int[] row : buffer) {
            Arrays.fill(row, value);
        }
    }

    private void clearSourceMap(int[][] buffer, int value) {
        for (int[] row : buffer) {
            Arrays.fill(row, value);
        }
    }

    private int applyDisplayControl(int rgba, int displayControl) {
        if ((displayControl & 0x80) != 0) {
            return 0x000000ff;
        }
        int brightness = displayControl & 0x0f;
        int red = (channel5(rgba, 24) * brightness) / 15;
        int green = (channel5(rgba, 16) * brightness) / 15;
        int blue = (channel5(rgba, 8) * brightness) / 15;
        return packColor5(red, green, blue, rgba & 0xff);
    }

    @Override
    public int getSize() {
        return 0x3f;
    }

    @Override
    public String getName() {
        return "PPU";
    }

    @Override
    public String getValueName(int address) {
        return switch (address) {
            case 0x00 -> "INIDISP";
            case 0x01 -> "OBSEL";
            case 0x02 -> "OAMADDL";
            case 0x03 -> "OAMDDH";
            case 0x04 -> "OAMDATA";
            case 0x05 -> "BGMODE";
            case 0x06 -> "MOSAIC";
            case 0x07 -> "BG1SC";
            case 0x08 -> "BG2SC";
            case 0x09 -> "BG3SC";
            case 0x0a -> "BG4SC";
            case 0x0b -> "BG12NBA";
            case 0x0c -> "BG34NBA";
            case 0x0d -> "BG1HOFS";
            case 0x0e -> "BG1VOFS";
            case 0x0f -> "BG2HOFS";
            case 0x10 -> "BG2VOFS";
            case 0x11 -> "BG3HOFS";
            case 0x12 -> "BG3VOFS";
            case 0x13 -> "BG4HOFS";
            case 0x14 -> "BG4VOFS";
            case 0x15 -> "VMAIN";
            case 0x16 -> "VMADDL";
            case 0x17 -> "VMADDH";
            case 0x18 -> "VMDATAL";
            case 0x19 -> "VMDATAH";
            case 0x1a -> "M7SEL";
            case 0x1b -> "M7A";
            case 0x1c -> "M7B";
            case 0x1d -> "M7C";
            case 0x1e -> "M7D";
            case 0x1f -> "M7X";
            case 0x20 -> "M7Y";
            case 0x21 -> "CGADD";
            case 0x22 -> "CGDATA";
            case 0x23 -> "W12SEL";
            case 0x24 -> "W34SEL";
            case 0x25 -> "WOBJSEL";
            case 0x26 -> "WH0";
            case 0x27 -> "WH1";
            case 0x28 -> "WH2";
            case 0x29 -> "WH3";
            case 0x2a -> "WBJLOG";
            case 0x2b -> "WOBJLOG";
            case 0x2c -> "TM";
            case 0x2d -> "TS";
            case 0x2e -> "TMW";
            case 0x2f -> "TSW";
            case 0x30 -> "CGWSEL";
            case 0x31 -> "CGADDSUB";
            case 0x32 -> "COLDATA";
            case 0x33 -> "SETINI";
            case 0x34 -> "MPYL";
            case 0x35 -> "MPYM";
            case 0x36 -> "MPYH";
            case 0x37 -> "SLHV";
            case 0x38 -> "OAMDATAREAD";
            case 0x39 -> "VMDATALREAD";
            case 0x3a -> "VMDATAHREAD";
            case 0x3b -> "CGDATAREAD";
            case 0x3c -> "OPHCT";
            case 0x3d -> "OPVCT";
            case 0x3e -> "STAT77";
            case 0x3f -> "STAT78";
            default -> "???";
        };
    }

    @Override
    public Component getComponent() {
        return Component.PPU;
    }
}
