package jamsnes.models;

public enum Component {
    CPU(1 << 0),
    PPU(1 << 1),
    APU(1 << 2),
    ROM(1 << 3),
    WRAM(1 << 4),
    VRAM(1 << 5),
    OAMRAM(1 << 6),
    CGRAM(1 << 7),
    SRAM(1 << 8),
    JOYPAD(1 << 9);

    private final int mask;

    Component(int mask) {
        this.mask = mask;
    }

    public int mask() {
        return mask;
    }
}
