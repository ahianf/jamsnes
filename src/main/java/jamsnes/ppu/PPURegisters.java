package jamsnes.ppu;

import java.util.Arrays;

import static jamsnes.models.Unsigned.u16;
import static jamsnes.models.Unsigned.u8;

public class PPURegisters {
    private final int[] raw;
    private final int[] m7 = new int[4];
    private final int[] m7Center = new int[2];
    private final int[] bgOffsets = new int[8];
    private int fixedColorRed;
    private int fixedColorGreen;
    private int fixedColorBlue;
    private int cgAddress;
    private int cgData;
    private int oamAddress;
    private boolean cgLowByte = true;

    public PPURegisters(int[] raw) {
        this.raw = raw;
    }

    void reset() {
        Arrays.fill(m7, 0);
        Arrays.fill(m7Center, 0);
        Arrays.fill(bgOffsets, 0);
        fixedColorRed = 0;
        fixedColorGreen = 0;
        fixedColorBlue = 0;
        cgAddress = 0;
        cgData = 0;
        oamAddress = 0;
        cgLowByte = true;
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
        return oamAddress;
    }

    public boolean oamObjPriorityActivationBit() {
        return bit(raw[0x03], 7);
    }

    public int oamPriorityObjectNumber() {
        return (raw[0x02] >>> 1) & 0x7f;
    }

    public int oamData() {
        return raw[0x04];
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

    public int bgOffset(int index) {
        return bgOffsets[index];
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

    public int cgAddress() {
        return cgAddress;
    }

    public int cgData() {
        return cgData;
    }

    public int cgDataLow() {
        return cgData & 0xff;
    }

    public int cgDataHigh() {
        return (cgData >>> 8) & 0xff;
    }

    public boolean m7HorizontalMirroring() {
        return bit(raw[0x1a], 0);
    }

    public boolean m7VerticalMirroring() {
        return bit(raw[0x1a], 1);
    }

    public boolean m7EmptySpaceFill() {
        return bit(raw[0x1a], 6);
    }

    public boolean m7PlayingFieldSize() {
        return bit(raw[0x1a], 7);
    }

    public int m7Matrix(int index) {
        return m7[index];
    }

    public int m7MatrixLow(int index) {
        return m7[index] & 0xff;
    }

    public int m7CenterRaw(int index) {
        return m7Center[index];
    }

    public int m7CenterValue(int index) {
        return (m7Center[index] >>> 3) & 0x1fff;
    }

    public boolean windowEnableWindow2ForBg2Bg4Color(int index) {
        return bit(raw[0x23 + index], 7);
    }

    public boolean window2InversionForBg2Bg4Color(int index) {
        return bit(raw[0x23 + index], 6);
    }

    public boolean windowEnableWindow1ForBg2Bg4Color(int index) {
        return bit(raw[0x23 + index], 5);
    }

    public boolean window1InversionForBg2Bg4Color(int index) {
        return bit(raw[0x23 + index], 4);
    }

    public boolean windowEnableWindow2ForBg1Bg3Obj(int index) {
        return bit(raw[0x23 + index], 3);
    }

    public boolean window2InversionForBg1Bg3Obj(int index) {
        return bit(raw[0x23 + index], 2);
    }

    public boolean windowEnableWindow1ForBg1Bg3Obj(int index) {
        return bit(raw[0x23 + index], 1);
    }

    public boolean window1InversionForBg1Bg3Obj(int index) {
        return bit(raw[0x23 + index], 0);
    }

    public int windowPosition(int index) {
        return raw[0x26 + index];
    }

    public int windowMaskLogicBg1() {
        return raw[0x2a] & 0b11;
    }

    public int windowMaskLogicBg2() {
        return (raw[0x2a] >>> 2) & 0b11;
    }

    public int windowMaskLogicBg3() {
        return (raw[0x2a] >>> 4) & 0b11;
    }

    public int windowMaskLogicBg4() {
        return (raw[0x2a] >>> 6) & 0b11;
    }

    public int windowMaskLogicObj() {
        return raw[0x2b] & 0b11;
    }

    public int windowMaskLogicColor() {
        return (raw[0x2b] >>> 2) & 0b11;
    }

    public boolean screenDesignationBackground(int screenIndex, int backgroundIndex) {
        return bit(raw[0x2c + screenIndex], backgroundIndex);
    }

    public boolean screenDesignationObj(int screenIndex) {
        return bit(raw[0x2c + screenIndex], 4);
    }

    public boolean windowMaskDesignationBackground(int screenIndex, int backgroundIndex) {
        return bit(raw[0x2e + screenIndex], backgroundIndex);
    }

    public boolean windowMaskDesignationObj(int screenIndex) {
        return bit(raw[0x2e + screenIndex], 4);
    }

    public boolean cgwselDirectColorMode() {
        return bit(raw[0x30], 0);
    }

    public boolean cgwselAddSubscreen() {
        return bit(raw[0x30], 1);
    }

    public int cgwselPreventColorMath() {
        return (raw[0x30] >>> 4) & 0b11;
    }

    public int cgwselClipColorToBlackBeforeMath() {
        return (raw[0x30] >>> 6) & 0b11;
    }

    public boolean cgadsubEnableColorMathBg(int backgroundIndex) {
        return bit(raw[0x31], backgroundIndex);
    }

    public boolean cgadsubEnableColorMathObj() {
        return bit(raw[0x31], 4);
    }

    public boolean cgadsubEnableColorMathBackdrop() {
        return bit(raw[0x31], 5);
    }

    public boolean cgadsubHalfColorMath() {
        return bit(raw[0x31], 6);
    }

    public boolean cgadsubAddSubtractSelect() {
        return bit(raw[0x31], 7);
    }

    public int coldataColorIntensity() {
        return raw[0x32] & 0b1_1111;
    }

    public boolean coldataRed() {
        return bit(raw[0x32], 5);
    }

    public boolean coldataGreen() {
        return bit(raw[0x32], 6);
    }

    public boolean coldataBlue() {
        return bit(raw[0x32], 7);
    }

    public int fixedColorRed() {
        return fixedColorRed;
    }

    public int fixedColorGreen() {
        return fixedColorGreen;
    }

    public int fixedColorBlue() {
        return fixedColorBlue;
    }

    public int fixedColor() {
        return fixedColorRed | (fixedColorGreen << 5) | (fixedColorBlue << 10);
    }

    public boolean setiniScreenInterlace() {
        return bit(raw[0x33], 0);
    }

    public boolean setiniObjInterlace() {
        return bit(raw[0x33], 1);
    }

    public boolean setiniOverscanMode() {
        return bit(raw[0x33], 2);
    }

    public boolean setiniEnablePseudoHiresMode() {
        return bit(raw[0x33], 3);
    }

    public boolean setiniMode7ExtBg() {
        return bit(raw[0x33], 6);
    }

    public boolean setiniExternalSync() {
        return bit(raw[0x33], 7);
    }

    public boolean isCgLowByte() {
        return cgLowByte;
    }

    int cgByteAddress() {
        return u16(cgAddress * 2 + (cgLowByte ? 0 : 1));
    }

    void setCgAddress(int value) {
        cgAddress = u8(value);
        cgLowByte = true;
    }

    void setCgDataLow(int value) {
        cgData = u16((cgData & 0xff00) | u8(value));
    }

    void setCgDataHigh(int value) {
        cgData = u16((cgData & 0x00ff) | ((u8(value) & 0x7f) << 8));
    }

    void incrementCgAddress() {
        cgAddress = u8(cgAddress + 1);
    }

    void toggleCgLowByte() {
        cgLowByte = !cgLowByte;
    }

    void writeM7Matrix(int index, int value) {
        m7[index] = u16((m7[index] << 8) | u8(value));
    }

    void writeM7Center(int index, int value) {
        m7Center[index] = u16((m7Center[index] << 8) | u8(value));
    }

    void writeColdata(int value) {
        int intensity = u8(value) & 0x1f;
        if ((value & 0x20) != 0) {
            fixedColorRed = intensity;
        }
        if ((value & 0x40) != 0) {
            fixedColorGreen = intensity;
        }
        if ((value & 0x80) != 0) {
            fixedColorBlue = intensity;
        }
    }

    void incrementOamAddress() {
        oamAddress = (oamAddress + 1) & 0x3ff;
    }

    void reloadOamAddress() {
        int reload = ((raw[0x03] & 1) << 8) | raw[0x02];
        oamAddress = (reload << 1) & 0x3fe;
    }

    void setBgOffset(int index, int value) {
        bgOffsets[index] = value & 0x3ff;
    }

    private boolean bit(int value, int bit) {
        return ((value >>> bit) & 1) != 0;
    }
}
