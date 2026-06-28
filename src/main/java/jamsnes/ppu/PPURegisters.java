package jamsnes.ppu;

public class PPURegisters {
    private final int[] raw;

    public PPURegisters(int[] raw) {
        this.raw = raw;
    }

    public boolean inidispFblank() {
        return bit(raw[0x00], 7);
    }

    public int inidispBrightness() {
        return raw[0x00] & 0x0f;
    }

    public int obselNameBaseSelect() {
        return raw[0x01] & 0b111;
    }

    public int obselNameSelect() {
        return (raw[0x01] >>> 3) & 0b11;
    }

    public int obselObjectSize() {
        return (raw[0x01] >>> 5) & 0b111;
    }

    public int oamAddress() {
        return (raw[0x02] | (raw[0x03] << 8)) & 0x1ff;
    }

    public boolean oamObjPriorityActivationBit() {
        return bit(raw[0x03], 7);
    }

    public int bgMode() {
        return raw[0x05] & 0b111;
    }

    public boolean bgMode1Bg3PriorityBit() {
        return bit(raw[0x05], 3);
    }

    public boolean bgCharacterSize(int backgroundIndex) {
        return bit(raw[0x05], 4 + backgroundIndex);
    }

    public boolean mosaicAffectsBackground(int backgroundIndex) {
        return bit(raw[0x06], backgroundIndex);
    }

    public int mosaicPixelSize() {
        return (raw[0x06] >>> 4) & 0x0f;
    }

    public boolean bgTilemapHorizontalMirroring(int backgroundIndex) {
        return bit(raw[0x07 + backgroundIndex], 0);
    }

    public boolean bgTilemapVerticalMirroring(int backgroundIndex) {
        return bit(raw[0x07 + backgroundIndex], 1);
    }

    public int bgTilemapAddress(int backgroundIndex) {
        return (raw[0x07 + backgroundIndex] >>> 2) & 0b11_1111;
    }

    public int bgBaseAddressFirst(int index) {
        return raw[0x0b + index] & 0x0f;
    }

    public int bgBaseAddressSecond(int index) {
        return (raw[0x0b + index] >>> 4) & 0x0f;
    }

    public int vmainIncrementAmount() {
        return raw[0x15] & 0b11;
    }

    public int vmainAddressRemapping() {
        return (raw[0x15] >>> 2) & 0b11;
    }

    public boolean vmainIncrementMode() {
        return bit(raw[0x15], 7);
    }

    public int vmadd() {
        return raw[0x16] | (raw[0x17] << 8);
    }

    public int vmdata() {
        return raw[0x18] | (raw[0x19] << 8);
    }

    private boolean bit(int value, int bit) {
        return ((value >>> bit) & 1) != 0;
    }
}
