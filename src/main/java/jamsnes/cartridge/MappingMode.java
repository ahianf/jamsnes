package jamsnes.cartridge;

public enum MappingMode {
    LOROM(1 << 0),
    HIROM(1 << 1),
    SLOWROM(1 << 2),
    FASTROM(1 << 3),
    EXROM(1 << 4);

    private final int mask;

    MappingMode(int mask) {
        this.mask = mask;
    }

    public int mask() {
        return mask;
    }
}
