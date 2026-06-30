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
    private int vramAddress;
    private int vmain;
    private int vramIncrementAmount = 1;
    private int vramReadBuffer;
    private int hvSharedScrollPreviousValue;
    private int hScrollPreviousValue;

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
            case 0x34, 0x35, 0x36, 0x37 -> registers[address];
            case 0x38 -> readOamData();
            case 0x39 -> readVramLow();
            case 0x3a -> readVramHigh();
            case 0x3b -> readCgData();
            case 0x3c, 0x3d, 0x3e, 0x3f -> 0;
            default -> throw new InvalidAddress("PPU Internal Registers read ", address + start);
        };
    }

    @Override
    public void write(int address, int data) {
        int value = u8(data);
        if (address == 0x3e) {
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
        renderMainAndSubScreen();
        clearBuffer(screen);
        addBuffer(screen, subScreen);
        addBuffer(screen, mainScreen);

        for (int y = 0; y < screen.length; y++) {
            for (int x = 0; x < screen[y].length; x++) {
                renderer.putPixel(y, x, screen[y][x]);
            }
        }
        renderer.drawScreen();
        clearBuffer(mainScreen);
        clearBuffer(subScreen);
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
            case 7 -> throw new IllegalStateException("not implemented");
            default -> throw new IllegalStateException("Bg mode not implemented or commented (bg nb "
                    + ppuRegisters.bgMode() + ")");
        }
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
        baseAddress = backgroundNumber % 2 != 0 ? baseAddress & 0x0f : (baseAddress & 0x0f) >>> 4;
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
            updateVramReadBuffer();
            incrementVramAddress();
        }
        return value;
    }

    private void updateVramReadBuffer() {
        vramReadBuffer = vram.read(getVramAddress()) | (vram.read(u16(getVramAddress() + 1)) << 8);
    }

    private int readCgData() {
        int value = cgram.read(ppuRegisters.cgAddress());
        ppuRegisters.incrementCgAddress();
        return value;
    }

    private int readOamData() {
        int value = oamram.read(ppuRegisters.oamAddress());
        ppuRegisters.incrementOamAddress();
        return value;
    }

    private void writeCgData(int value) {
        if (ppuRegisters.isCgLowByte()) {
            ppuRegisters.setCgDataLow(value);
        } else {
            ppuRegisters.setCgDataHigh(value);
            cgram.write(ppuRegisters.cgAddress(), ppuRegisters.cgDataLow());
            ppuRegisters.incrementCgAddress();
            cgram.write(ppuRegisters.cgAddress(), ppuRegisters.cgDataHigh());
            ppuRegisters.incrementCgAddress();
        }
        ppuRegisters.toggleCgLowByte();
    }

    private void writeOamData(int value) {
        oamram.write(ppuRegisters.oamAddress(), value);
        ppuRegisters.incrementOamAddress();
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
        if ((registers[0x2c] & backgroundBit) != 0) {
            Background.mergeBackgroundBuffer(mainScreen, mainScreenLevelMap, background, levelLow, levelHigh);
        }
        if ((registers[0x2d] & backgroundBit) != 0) {
            Background.mergeBackgroundBuffer(subScreen, subScreenLevelMap, background, levelLow, levelHigh);
        }
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

    private void clearBuffer(int[][] buffer) {
        fillBuffer(buffer, 0);
    }

    private void fillBuffer(int[][] buffer, int value) {
        for (int[] row : buffer) {
            Arrays.fill(row, value);
        }
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
