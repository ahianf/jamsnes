package jamsnes.ppu;

import jamsnes.memory.AMemory;
import jamsnes.exceptions.InvalidAddress;
import jamsnes.models.Component;
import jamsnes.models.Vector2;
import jamsnes.ram.Ram;
import jamsnes.renderer.IRenderer;

import java.util.Arrays;

import static jamsnes.models.Unsigned.u16;
import static jamsnes.models.Unsigned.u8;

public class PPU extends AMemory {
    public static final int VRAM_SIZE = 65_536;
    public static final int CGRAM_SIZE = 512;
    public static final int OAMRAM_SIZE = 544;
    private static final int SOURCE_NONE = -1;
    private static final int SOURCE_BACKDROP = 0;
    private static final int SOURCE_OBJ = 5;
    private static final int OBJ_COUNT = 128;
    private static final int OBJ_LOW_TABLE_SIZE = 0x200;
    private static final int OBJ_TILE_BPP = 4;
    private static final int OBJ_TILE_BYTE_SIZE = 32;
    private static final int OBJ_TILE_ROW_SIZE = 16;
    private static final int OBJ_PALETTE_BASE = 128;
    private static final int[][] OBJ_SIZE_PRESETS = {
            {8, 16},
            {8, 32},
            {8, 64},
            {16, 32},
            {16, 64},
            {32, 64},
            {16, 32},
            {16, 32}
    };
    private static final int MODE7_SIZE = 1024;
    private static final int MODE7_TILE_MAP_WIDTH = 128;
    private static final int MODE7_TILE_SIZE = 8;
    private static final int MODE7_TILE_DATA_ADDRESS = 0x4000;
    public static final int H_COUNTER_DOTS = 341;
    public static final int H_BLANK_START_DOT = 256;
    public static final int V_COUNTER_SCANLINES = 262;
    public static final int V_BLANK_START_SCANLINE = 225;
    private static final int PPU1_VERSION = 1;
    private static final int PPU2_VERSION = 3;

    public final Ram vram = new Ram(VRAM_SIZE, Component.VRAM, "VRAM");
    public final Ram oamram = new Ram(OAMRAM_SIZE, Component.OAMRAM, "OAMRAM");
    public final Ram cgram = new Ram(CGRAM_SIZE, Component.CGRAM, "CGRAM");
    private final int[] registers = new int[0x40];
    private final PPURegisters ppuRegisters = new PPURegisters(registers);
    private final IRenderer renderer;
    private final Background[] backgrounds;
    private final int[][] mainScreen = new int[Background.BUFFER_SIZE][Background.BUFFER_SIZE];
    private final int[][] subScreen = new int[Background.BUFFER_SIZE][Background.BUFFER_SIZE];
    private final int[][] screen = new int[Background.BUFFER_SIZE][Background.BUFFER_SIZE];
    private final int[][] mainScreenLevelMap = new int[Background.BUFFER_SIZE][Background.BUFFER_SIZE];
    private final int[][] subScreenLevelMap = new int[Background.BUFFER_SIZE][Background.BUFFER_SIZE];
    private final int[][] mainScreenSourceMap = new int[Background.BUFFER_SIZE][Background.BUFFER_SIZE];
    private final int[][] subScreenSourceMap = new int[Background.BUFFER_SIZE][Background.BUFFER_SIZE];
    private int vramAddress;
    private int vmain;
    private int vramIncrementAmount = 1;
    private int vramReadBuffer;
    private int hvSharedScrollPreviousValue;
    private int hScrollPreviousValue;
    private int hCounter;
    private int vCounter;
    private int latchedHCounter;
    private int latchedVCounter;
    private int oamLowTableLatch;
    private boolean hCounterHighByte;
    private boolean vCounterHighByte;
    private boolean counterLatchFlag;

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
        registers[address] = value;
        switch (address) {
            case 0x04 -> writeOamData(value);
            case 0x05 -> updateBackgroundModes();
            case 0x07, 0x08, 0x09, 0x0a -> updateBackgroundTileMap(address - 0x07);
            case 0x0b -> updateBackgroundTilesets(0, 1);
            case 0x0c -> updateBackgroundTilesets(2, 3);
            case 0x0d, 0x0f, 0x11, 0x13 -> writeBgHorizontalOffset(address, value);
            case 0x0e, 0x10, 0x12, 0x14 -> writeBgVerticalOffset(address, value);
            case 0x15 -> setVmain(value);
            case 0x16 -> {
                vramAddress = u16((vramAddress & 0xff00) | value);
                updateVramReadBuffer();
            }
            case 0x17 -> {
                vramAddress = u16((vramAddress & 0x00ff) | (value << 8));
                updateVramReadBuffer();
            }
            case 0x18 -> {
                if (!ppuRegisters.inidispFblank()) {
                    vram.write(getVramAddress(), value);
                }
                if (!isVramIncrementAfterHighByte()) {
                    incrementVramAddress();
                }
            }
            case 0x19 -> {
                if (!ppuRegisters.inidispFblank()) {
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

    public int getVramAddressRegister() {
        return vramAddress;
    }

    public int getVramAddress() {
        int vanillaAddress = u16(vramAddress * 2);
        return switch ((vmain >>> 2) & 0b11) {
            case 0b00 -> vanillaAddress;
            case 0b01 -> (vanillaAddress & 0xff00) | ((vanillaAddress & 0x00e0) >>> 5) | ((vanillaAddress & 0x001f) << 3);
            case 0b10 -> (vanillaAddress & 0xfe00) | ((vanillaAddress & 0x01c0) >>> 6) | ((vanillaAddress & 0x003f) << 3);
            case 0b11 -> (vanillaAddress & 0xfc00) | ((vanillaAddress & 0x0380) >>> 7) | ((vanillaAddress & 0x007f) << 3);
            default -> vanillaAddress;
        };
    }

    public void update(int cycles) {
        advanceCounters(cycles);
        renderFrame();
    }

    public void renderFrame() {
        renderMainAndSubScreen();

        for (int y = 0; y < screen.length; y++) {
            for (int x = 0; x < screen[y].length; x++) {
                screen[y][x] = composePixel(x, y);
                renderer.putPixel(y, x, applyDisplayControl(screen[y][x]));
            }
        }
        renderer.drawScreen();
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
        latchedHCounter = 0;
        latchedVCounter = 0;
        hCounterHighByte = false;
        vCounterHighByte = false;
        counterLatchFlag = false;
    }

    public void resetRegisterState() {
        Arrays.fill(registers, 0);
        ppuRegisters.reset();
        vramAddress = 0;
        vmain = 0;
        vramIncrementAmount = 1;
        vramReadBuffer = 0;
        hvSharedScrollPreviousValue = 0;
        hScrollPreviousValue = 0;
        oamLowTableLatch = 0;
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
        for (Background background : backgrounds) {
            background.renderBackground();
        }

        int colorPalette = cgram.read(0) | (cgram.read(1) << 8);
        int color = PPUUtils.cgramColorToRGBA(colorPalette);
        fillBuffer(subScreen, color);
        clearBuffer(mainScreen);
        clearBuffer(mainScreenLevelMap);
        clearBuffer(subScreenLevelMap);
        clearSourceMap(mainScreenSourceMap, SOURCE_NONE);
        clearSourceMap(subScreenSourceMap, SOURCE_BACKDROP);

        switch (ppuRegisters.bgMode()) {
            case 0 -> {
                addToMainSubScreen(backgrounds[3], 0, 15);
                addToMainSubScreen(backgrounds[2], 10, 16);
                addToMainSubScreen(backgrounds[1], 20, 35);
                addToMainSubScreen(backgrounds[0], 30, 36);
            }
            case 1 -> {
                addToMainSubScreen(backgrounds[2], 0, ppuRegisters.bgMode1Bg3PriorityBit() ? 30 : 5);
                addToMainSubScreen(backgrounds[1], 10, 25);
                addToMainSubScreen(backgrounds[0], 20, 26);
            }
            case 2, 3, 4, 5 -> {
                addToMainSubScreen(backgrounds[1], 10, 25);
                addToMainSubScreen(backgrounds[0], 20, 26);
            }
            case 6 -> addToMainSubScreen(backgrounds[0], 20, 26);
            case 7 -> addMode7ToMainSubScreen();
            default -> throw new IllegalStateException("Bg mode not implemented or commented (bg nb "
                    + ppuRegisters.bgMode() + ")");
        }
        addObjectsToMainSubScreen();
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
        if ((registers[0x05] & (1 << (3 + backgroundNumber))) != 0) {
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

    public int getBgMode() {
        return ppuRegisters.bgMode();
    }

    public int hCounter() {
        return hCounter;
    }

    public int vCounter() {
        return vCounter;
    }

    public boolean isInHBlank() {
        return hCounter >= H_BLANK_START_DOT;
    }

    public boolean isInVBlank() {
        return vCounter >= V_BLANK_START_SCANLINE;
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

    private void incrementVramAddress() {
        vramAddress = u16(vramAddress + vramIncrementAmount);
    }

    private int readVramLow() {
        int value = vramReadBuffer & 0xff;
        if (!isVramIncrementAfterHighByte()) {
            updateVramReadBuffer();
            incrementVramAddress();
        }
        return value;
    }

    private int readVramHigh() {
        int value = (vramReadBuffer >>> 8) & 0xff;
        if (isVramIncrementAfterHighByte()) {
            incrementVramAddress();
            updateVramReadBuffer();
        }
        return value;
    }

    private void updateVramReadBuffer() {
        vramReadBuffer = vram.read(getVramAddress()) | (vram.read(u16(getVramAddress() + 1)) << 8);
    }

    private int readCgData() {
        int address = ppuRegisters.cgByteAddress();
        int value = cgram.read(address);
        if (!ppuRegisters.isCgLowByte()) {
            value &= 0x7f;
            ppuRegisters.incrementCgAddress();
        }
        ppuRegisters.toggleCgLowByte();
        return value;
    }

    private int readSoftwareLatch() {
        latchCounters();
        return registers[0x37];
    }

    private int readLatchedHCounter() {
        if (hCounterHighByte) {
            hCounterHighByte = false;
            return (latchedHCounter >>> 8) & 1;
        }
        hCounterHighByte = true;
        return latchedHCounter & 0xff;
    }

    private int readLatchedVCounter() {
        if (vCounterHighByte) {
            vCounterHighByte = false;
            return (latchedVCounter >>> 8) & 1;
        }
        vCounterHighByte = true;
        return latchedVCounter & 0xff;
    }

    private int readStat77() {
        return PPU1_VERSION;
    }

    private int readStat78() {
        int value = PPU2_VERSION | (counterLatchFlag ? 0x40 : 0);
        counterLatchFlag = false;
        hCounterHighByte = false;
        vCounterHighByte = false;
        return value;
    }

    private void advanceCounters(int cycles) {
        if (cycles <= 0) {
            return;
        }
        hCounter += cycles;
        while (hCounter >= H_COUNTER_DOTS) {
            hCounter -= H_COUNTER_DOTS;
            vCounter++;
            if (vCounter >= V_COUNTER_SCANLINES) {
                vCounter = 0;
            }
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
        return value;
    }

    private void writeCgData(int value) {
        if (ppuRegisters.isCgLowByte()) {
            ppuRegisters.setCgDataLow(value);
        } else {
            ppuRegisters.setCgDataHigh(value);
            int byteAddress = u16(ppuRegisters.cgAddress() * 2);
            cgram.write(byteAddress, ppuRegisters.cgDataLow());
            cgram.write(u16(byteAddress + 1), ppuRegisters.cgDataHigh());
            ppuRegisters.incrementCgAddress();
        }
        ppuRegisters.toggleCgLowByte();
    }

    private void writeOamData(int value) {
        int address = ppuRegisters.oamAddress();
        if (address < OBJ_LOW_TABLE_SIZE / 2) {
            writeOamLowTableData(address, value);
        } else {
            oamram.write(getOamDataAddress(), value);
        }
        ppuRegisters.incrementOamAddress();
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
        if (address >= OBJ_LOW_TABLE_SIZE / 2) {
            return OBJ_LOW_TABLE_SIZE + (address & 0x1f);
        }
        return address;
    }

    private void writeBgHorizontalOffset(int address, int value) {
        int offset = ((value << 8) | (hvSharedScrollPreviousValue & ~7) | (hScrollPreviousValue & 7)) & 0x3ff;
        ppuRegisters.setBgOffset(address - 0x0d, offset);
        hScrollPreviousValue = value;
        hvSharedScrollPreviousValue = value;
    }

    private void writeBgVerticalOffset(int address, int value) {
        int offset = ((value << 8) | hvSharedScrollPreviousValue) & 0x3ff;
        ppuRegisters.setBgOffset(address - 0x0e, offset);
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
        int backgroundBit = 1 << (background.getBackgroundNumber() - 1);
        int backgroundIndex = background.getBackgroundNumber() - 1;
        Vector2<Integer> scroll = getBgScroll(background.getBackgroundNumber());
        int mosaicSize = ppuRegisters.mosaicAffectsBackground(backgroundIndex)
                ? ppuRegisters.mosaicPixelSize() + 1
                : 1;
        if ((registers[0x2c] & backgroundBit) != 0) {
            Background.mergeBackgroundBuffer(
                    mainScreen, mainScreenLevelMap, mainScreenSourceMap, background.getBackgroundNumber(),
                    background, levelLow, levelHigh, scroll.x, scroll.y, mosaicSize,
                    layerWindowMask(backgroundIndex, 0));
        }
        if ((registers[0x2d] & backgroundBit) != 0) {
            Background.mergeBackgroundBuffer(
                    subScreen, subScreenLevelMap, subScreenSourceMap, background.getBackgroundNumber(),
                    background, levelLow, levelHigh, scroll.x, scroll.y, mosaicSize,
                    layerWindowMask(backgroundIndex, 1));
        }
    }

    private void addMode7ToMainSubScreen() {
        if ((registers[0x2c] & 0x01) != 0) {
            renderMode7ToBuffer(mainScreen, mainScreenLevelMap, mainScreenSourceMap, 1, 20, 20, false);
        }
        if ((registers[0x2d] & 0x01) != 0) {
            renderMode7ToBuffer(subScreen, subScreenLevelMap, subScreenSourceMap, 1, 20, 20, false);
        }
        if (ppuRegisters.setiniMode7ExtBg()) {
            if ((registers[0x2c] & 0x02) != 0) {
                renderMode7ToBuffer(mainScreen, mainScreenLevelMap, mainScreenSourceMap, 2, 10, 30, true);
            }
            if ((registers[0x2d] & 0x02) != 0) {
                renderMode7ToBuffer(subScreen, subScreenLevelMap, subScreenSourceMap, 2, 10, 30, true);
            }
        }
    }

    private void addObjectsToMainSubScreen() {
        if (ppuRegisters.screenDesignationObj(0)) {
            renderObjectsToBuffer(mainScreen, mainScreenLevelMap, mainScreenSourceMap, objectWindowMask(0));
        }
        if (ppuRegisters.screenDesignationObj(1)) {
            renderObjectsToBuffer(subScreen, subScreenLevelMap, subScreenSourceMap, objectWindowMask(1));
        }
    }

    private void renderObjectsToBuffer(int[][] destination, int[][] levelMap, int[][] sourceMap, boolean[] windowMask) {
        for (int objectIndex = OBJ_COUNT - 1; objectIndex >= 0; objectIndex--) {
            renderObjectToBuffer(objectIndex, destination, levelMap, sourceMap, windowMask);
        }
    }

    private void renderObjectToBuffer(
            int objectIndex,
            int[][] destination,
            int[][] levelMap,
            int[][] sourceMap,
            boolean[] windowMask) {
        int objectAddress = objectIndex * 4;
        int x = oamram.read(objectAddress);
        int y = oamram.read(objectAddress + 1);
        int tile = oamram.read(objectAddress + 2);
        int attributes = oamram.read(objectAddress + 3);
        int highTable = oamram.read(OBJ_LOW_TABLE_SIZE + objectIndex / 4);
        int highShift = (objectIndex % 4) * 2;
        if (((highTable >>> highShift) & 0x01) != 0) {
            x |= 0x100;
        }
        if (x >= 256) {
            x -= 512;
        }

        int objectSize = objectSize((highTable >>> (highShift + 1)) & 0x01);
        int level = objectPriorityLevel((attributes >>> 4) & 0x03);
        int palette = (attributes >>> 1) & 0x07;
        boolean horizontalFlip = (attributes & 0x40) != 0;
        boolean verticalFlip = (attributes & 0x80) != 0;
        int baseAddress = objectTileBaseAddress(attributes);

        for (int pixelY = 0; pixelY < objectSize; pixelY++) {
            int screenY = y + pixelY;
            if (screenY < 0 || screenY >= destination.length) {
                continue;
            }
            int sourceY = verticalFlip ? objectSize - 1 - pixelY : pixelY;
            for (int pixelX = 0; pixelX < objectSize; pixelX++) {
                int screenX = x + pixelX;
                if (screenX < 0 || screenX >= destination[screenY].length) {
                    continue;
                }
                if (windowMask != null && screenX < windowMask.length && windowMask[screenX]) {
                    continue;
                }
                int sourceX = horizontalFlip ? objectSize - 1 - pixelX : pixelX;
                int color = readObjectPixel(baseAddress, tile, palette, sourceX, sourceY);
                if (Integer.compareUnsigned(color, 0xff) <= 0 || level < levelMap[screenY][screenX]) {
                    continue;
                }
                destination[screenY][screenX] = color;
                levelMap[screenY][screenX] = level;
                sourceMap[screenY][screenX] = SOURCE_OBJ;
            }
        }
    }

    private int objectSize(int sizeBit) {
        return OBJ_SIZE_PRESETS[ppuRegisters.obselObjectSize()][sizeBit];
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

    private int objectTileBaseAddress(int attributes) {
        int base = ppuRegisters.obselNameBaseSelect() << 13;
        if ((attributes & 0x01) != 0) {
            base += (ppuRegisters.obselNameSelect() + 1) << 12;
        }
        return u16(base);
    }

    private int readObjectPixel(int baseAddress, int tile, int palette, int sourceX, int sourceY) {
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
        int cgramAddress = (OBJ_PALETTE_BASE + palette * 16 + colorIndex) * 2;
        int color = cgram.read(cgramAddress) | (cgram.read(cgramAddress + 1) << 8);
        return PPUUtils.cgramColorToRGBA(color);
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

    private boolean[] objectWindowMask(int screenIndex) {
        if (!ppuRegisters.windowMaskDesignationObj(screenIndex)) {
            return null;
        }
        boolean[] mask = new boolean[Background.BUFFER_SIZE];
        for (int x = 0; x < mask.length; x++) {
            mask[x] = isInsideObjectWindow(x);
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

    private boolean isInsideObjectWindow(int x) {
        return isInsideWindowMask(
                ppuRegisters.windowEnableWindow1ForBg1Bg3Obj(2),
                ppuRegisters.window1InversionForBg1Bg3Obj(2),
                ppuRegisters.windowEnableWindow2ForBg1Bg3Obj(2),
                ppuRegisters.window2InversionForBg1Bg3Obj(2),
                ppuRegisters.windowMaskLogicObj(),
                x);
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
            boolean extBg) {
        int a = signed16(ppuRegisters.m7Matrix(0));
        int b = signed16(ppuRegisters.m7Matrix(1));
        int c = signed16(ppuRegisters.m7Matrix(2));
        int d = signed16(ppuRegisters.m7Matrix(3));
        int centerX = signed13(ppuRegisters.m7CenterValue(0));
        int centerY = signed13(ppuRegisters.m7CenterValue(1));
        Vector2<Integer> scroll = getBgScroll(1);

        for (int y = 0; y < destination.length; y++) {
            for (int x = 0; x < destination[y].length; x++) {
                int sourceX = (((a * (x - centerX)) + (b * (y - centerY))) >> 8) + centerX + scroll.x;
                int sourceY = (((c * (x - centerX)) + (d * (y - centerY))) >> 8) + centerY + scroll.y;
                if (ppuRegisters.m7HorizontalMirroring()) {
                    sourceX = MODE7_SIZE - 1 - sourceX;
                }
                if (ppuRegisters.m7VerticalMirroring()) {
                    sourceY = MODE7_SIZE - 1 - sourceY;
                }
                Mode7Pixel pixel = readMode7Pixel(sourceX, sourceY, extBg);
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

    private Mode7Pixel readMode7Pixel(int sourceX, int sourceY, boolean extBg) {
        boolean outsidePlayingField = sourceX < 0 || sourceX >= MODE7_SIZE || sourceY < 0 || sourceY >= MODE7_SIZE;
        if (outsidePlayingField && ppuRegisters.m7PlayingFieldSize() && !ppuRegisters.m7EmptySpaceFill()) {
            return Mode7Pixel.TRANSPARENT;
        }

        int wrappedX = sourceX & (MODE7_SIZE - 1);
        int wrappedY = sourceY & (MODE7_SIZE - 1);
        int pixelX = wrappedX % MODE7_TILE_SIZE;
        int pixelY = wrappedY % MODE7_TILE_SIZE;
        int tile;
        if (outsidePlayingField && ppuRegisters.m7PlayingFieldSize()) {
            tile = 0;
        } else {
            int tileX = wrappedX / MODE7_TILE_SIZE;
            int tileY = wrappedY / MODE7_TILE_SIZE;
            tile = vram.read(u16(tileY * MODE7_TILE_MAP_WIDTH + tileX));
        }
        int colorIndex = vram.read(u16(MODE7_TILE_DATA_ADDRESS + tile * 64 + pixelY * MODE7_TILE_SIZE + pixelX));
        boolean priority = false;
        if (extBg) {
            priority = (colorIndex & 0x80) != 0;
            colorIndex &= 0x7f;
        }
        if (colorIndex == 0) {
            return Mode7Pixel.TRANSPARENT;
        }
        int colorAddress = colorIndex * 2;
        int color = cgram.read(colorAddress) | (cgram.read(colorAddress + 1) << 8);
        return new Mode7Pixel(PPUUtils.cgramColorToRGBA(color), priority);
    }

    private int signed16(int value) {
        return (short) u16(value);
    }

    private int mode7MultiplicationResultByte(int index) {
        int operandA = signed16(ppuRegisters.m7Matrix(0));
        int operandB = signed8(ppuRegisters.m7Matrix(1) >>> 8);
        int result = operandA * operandB;
        return (result >>> (index * 8)) & 0xff;
    }

    private int signed8(int value) {
        return (byte) u8(value);
    }

    private int signed13(int value) {
        int normalized = value & 0x1fff;
        return (normalized & 0x1000) != 0 ? normalized - 0x2000 : normalized;
    }

    private record Mode7Pixel(int color, boolean priority) {
        private static final Mode7Pixel TRANSPARENT = new Mode7Pixel(0, false);
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

    private int composePixel(int x, int y) {
        int mainPixel = mainScreen[y][x];
        int source = mainScreenSourceMap[y][x];
        int pixel = mainPixel;
        boolean mainPixelVisible = Integer.compareUnsigned(mainPixel, 0xff) > 0;
        if (!mainPixelVisible) {
            pixel = subScreen[y][x];
            source = subScreenSourceMap[y][x];
        }
        if (isColorClippedToBlack(x)) {
            pixel = 0x000000ff;
        }
        return applyColorMath(pixel, source, x, y, mainPixelVisible);
    }

    private int applyColorMath(int pixel, int source, int x, int y, boolean mainPixelVisible) {
        if (isColorMathPrevented(x) || !isColorMathEnabledForSource(source)) {
            return pixel;
        }
        int other = ppuRegisters.cgwselAddSubscreen()
                ? (mainPixelVisible ? subScreen[y][x] : 0)
                : PPUUtils.cgramColorToRGBA(ppuRegisters.fixedColor());
        return ppuRegisters.cgadsubAddSubtractSelect()
                ? subtractColor(pixel, other, ppuRegisters.cgadsubHalfColorMath())
                : addColor(pixel, other, ppuRegisters.cgadsubHalfColorMath());
    }

    private boolean isColorMathEnabledForSource(int source) {
        if (source == SOURCE_BACKDROP) {
            return ppuRegisters.cgadsubEnableColorMathBackdrop();
        }
        if (source >= 1 && source <= 4) {
            return ppuRegisters.cgadsubEnableColorMathBg(source - 1);
        }
        if (source == SOURCE_OBJ) {
            return ppuRegisters.cgadsubEnableColorMathObj();
        }
        return false;
    }

    private boolean isColorClippedToBlack(int x) {
        return isColorWindowModeActive(ppuRegisters.cgwselClipColorToBlackBeforeMath(), x);
    }

    private boolean isColorMathPrevented(int x) {
        return isColorWindowModeActive(ppuRegisters.cgwselPreventColorMath(), x);
    }

    private boolean isColorWindowModeActive(int mode, int x) {
        return switch (mode) {
            case 0b00 -> false;
            case 0b01 -> !isInsideColorWindow(x);
            case 0b10 -> isInsideColorWindow(x);
            case 0b11 -> true;
            default -> false;
        };
    }

    private boolean isInsideColorWindow(int x) {
        return isInsideWindowMask(
                ppuRegisters.windowEnableWindow1ForBg2Bg4Color(2),
                ppuRegisters.window1InversionForBg2Bg4Color(2),
                ppuRegisters.windowEnableWindow2ForBg2Bg4Color(2),
                ppuRegisters.window2InversionForBg2Bg4Color(2),
                ppuRegisters.windowMaskLogicColor(),
                x);
    }

    private boolean isInsideWindowMask(
            boolean window1Enabled,
            boolean window1Inverted,
            boolean window2Enabled,
            boolean window2Inverted,
            int maskLogic,
            int x) {
        boolean window1 = window1Enabled && isInsideWindow(x, 0);
        boolean window2 = window2Enabled && isInsideWindow(x, 2);

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
        int left = ppuRegisters.windowPosition(positionIndex);
        int right = ppuRegisters.windowPosition(positionIndex + 1);
        return left <= right && x >= left && x <= right;
    }

    private int addColor(int left, int right, boolean half) {
        int red = channel(left, 24) + channel(right, 24);
        int green = channel(left, 16) + channel(right, 16);
        int blue = channel(left, 8) + channel(right, 8);
        if (half) {
            red >>>= 1;
            green >>>= 1;
            blue >>>= 1;
        }
        return packColor(clamp8(red), clamp8(green), clamp8(blue), left & 0xff);
    }

    private int subtractColor(int left, int right, boolean half) {
        int red = channel(left, 24) - channel(right, 24);
        int green = channel(left, 16) - channel(right, 16);
        int blue = channel(left, 8) - channel(right, 8);
        if (half) {
            red >>= 1;
            green >>= 1;
            blue >>= 1;
        }
        return packColor(clamp8(red), clamp8(green), clamp8(blue), left & 0xff);
    }

    private int channel(int color, int shift) {
        return (color >>> shift) & 0xff;
    }

    private int clamp8(int value) {
        return Math.max(0, Math.min(0xff, value));
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

    private int applyDisplayControl(int rgba) {
        if (ppuRegisters.inidispFblank()) {
            return 0x000000ff;
        }
        int brightness = ppuRegisters.inidispBrightness();
        int red = (((rgba >>> 24) & 0xff) * brightness) / 15;
        int green = (((rgba >>> 16) & 0xff) * brightness) / 15;
        int blue = (((rgba >>> 8) & 0xff) * brightness) / 15;
        return (red << 24) | (green << 16) | (blue << 8) | (rgba & 0xff);
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
