package jamsnes.ppu;

import jamsnes.memory.AMemory;
import jamsnes.models.Component;
import jamsnes.models.Vector2;
import jamsnes.ram.Ram;
import jamsnes.renderer.IRenderer;

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
    private int vramAddress;
    private int vmain;
    private int vramIncrementAmount = 1;
    private int vramReadBuffer;

    public PPU(IRenderer renderer) {
    }

    @Override
    public int read(int address) {
        return switch (address) {
            case 0x39 -> readVramLow();
            case 0x3a -> readVramHigh();
            default -> registers[address];
        };
    }

    @Override
    public void write(int address, int data) {
        int value = u8(data);
        registers[address] = value;
        switch (address) {
            case 0x04 -> writeOamData(value);
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
                vram.write(getVramAddress(), value);
                if (!isVramIncrementAfterHighByte()) {
                    incrementVramAddress();
                }
            }
            case 0x19 -> {
                vram.write(u16(getVramAddress() + 1), value);
                if (isVramIncrementAfterHighByte()) {
                    incrementVramAddress();
                }
            }
            case 0x1b, 0x1c, 0x1d, 0x1e -> ppuRegisters.writeM7Matrix(address - 0x1b, value);
            case 0x21 -> ppuRegisters.setCgAddress(value);
            case 0x22 -> writeCgData(value);
            default -> {
            }
        }
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

    @Override
    public int getSize() {
        return 0x3f;
    }

    @Override
    public String getName() {
        return "PPU";
    }

    @Override
    public Component getComponent() {
        return Component.PPU;
    }
}
