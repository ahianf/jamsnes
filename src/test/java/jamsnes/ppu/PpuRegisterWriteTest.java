package jamsnes.ppu;

import jamsnes.SNES;
import jamsnes.exceptions.InvalidAddress;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PpuRegisterWriteTest {
    @Test
    void decodesInidisp() {
        SNES snes = init();

        snes.bus.write(0x2100, 0b1111_1111);
        assertTrue(snes.ppu.ppuRegisters().inidispFblank());
        assertEquals(0x0f, snes.ppu.ppuRegisters().inidispBrightness());

        snes.bus.write(0x2100, 0b0000_0101);
        assertFalse(snes.ppu.ppuRegisters().inidispFblank());
        assertEquals(0x05, snes.ppu.ppuRegisters().inidispBrightness());
    }

    @Test
    void decodesObsel() {
        SNES snes = init();

        snes.bus.write(0x2101, 0b1111_1111);
        assertEquals(0b111, snes.ppu.ppuRegisters().obselObjectSize());
        assertEquals(0b11, snes.ppu.ppuRegisters().obselNameSelect());
        assertEquals(0b111, snes.ppu.ppuRegisters().obselNameBaseSelect());

        snes.bus.write(0x2101, 0b0001_1000);
        assertEquals(0b000, snes.ppu.ppuRegisters().obselObjectSize());
        assertEquals(0b11, snes.ppu.ppuRegisters().obselNameSelect());
        assertEquals(0b000, snes.ppu.ppuRegisters().obselNameBaseSelect());
    }

    @Test
    void decodesOamAddress() {
        SNES snes = init();

        snes.bus.write(0x2102, 0b1111_1111);
        assertEquals(0x1fe, snes.ppu.ppuRegisters().oamAddress());

        snes.bus.write(0x2103, 0b1111_1111);
        assertTrue(snes.ppu.ppuRegisters().oamObjPriorityActivationBit());
        assertEquals(0x3fe, snes.ppu.ppuRegisters().oamAddress());
    }

    @Test
    void writesOamDataAndIncrementsAddress() {
        SNES snes = init();

        snes.bus.write(0x2100, 0x80);
        snes.bus.write(0x2102, 0x05);
        snes.bus.write(0x2103, 0x80);
        snes.bus.write(0x2104, 0x42);

        assertEquals(0x42, snes.ppu.ppuRegisters().oamData());
        assertEquals(0, snes.ppu.oamram.read(0x0a));
        assertEquals(0x0b, snes.ppu.ppuRegisters().oamAddress());
        assertTrue(snes.ppu.ppuRegisters().oamObjPriorityActivationBit());

        snes.bus.write(0x2104, 0x24);
        assertEquals(0x42, snes.ppu.oamram.read(0x0a));
        assertEquals(0x24, snes.ppu.oamram.read(0x0b));
        assertEquals(0x0c, snes.ppu.ppuRegisters().oamAddress());

        snes.bus.write(0x2104, 0x66);
        assertEquals(0, snes.ppu.oamram.read(0x0c));
        assertEquals(0x0d, snes.ppu.ppuRegisters().oamAddress());
    }

    @Test
    void oamLowTableWritesCommitEvenOddPairs() {
        SNES snes = init();

        snes.bus.write(0x2100, 0x80);
        snes.bus.write(0x2102, 0x00);
        snes.bus.write(0x2103, 0x00);
        snes.bus.write(0x2104, 0x12);

        assertEquals(0, snes.ppu.oamram.read(0x00));
        assertEquals(0x01, snes.ppu.ppuRegisters().oamAddress());

        snes.bus.write(0x2104, 0x34);

        assertEquals(0x12, snes.ppu.oamram.read(0x00));
        assertEquals(0x34, snes.ppu.oamram.read(0x01));
        assertEquals(0x02, snes.ppu.ppuRegisters().oamAddress());
    }

    @Test
    void oamDataWriteMapsUpperAddressRangeToHighTable() {
        SNES snes = init();

        snes.bus.write(0x2100, 0x80);
        snes.bus.write(0x2102, 0x00);
        snes.bus.write(0x2103, 0x01);
        snes.bus.write(0x2104, 0x55);

        assertEquals(0x55, snes.ppu.oamram.read(0x200));
        assertEquals(0, snes.ppu.oamram.read(0x100));
        assertEquals(0x201, snes.ppu.ppuRegisters().oamAddress());

        snes.bus.write(0x2102, 0x3f);
        snes.bus.write(0x2103, 0x01);
        snes.bus.write(0x2104, 0x66);

        assertEquals(0x66, snes.ppu.oamram.read(0x21e));
        assertEquals(0, snes.ppu.oamram.read(0x13f));
        assertEquals(0x27f, snes.ppu.ppuRegisters().oamAddress());
    }

    @Test
    void oamDataWriteIsSkippedDuringActiveDisplayButStillIncrements() {
        SNES snes = init();

        snes.bus.write(0x2102, 0x00);
        snes.bus.write(0x2103, 0x01);
        snes.bus.write(0x2104, 0x55);

        assertEquals(0, snes.ppu.oamram.read(0x200));
        assertEquals(0x201, snes.ppu.ppuRegisters().oamAddress());
    }

    @Test
    void oamDataWriteCommitsDuringVBlankWithoutForcedBlank() {
        SNES snes = init();

        snes.ppu.advanceCountersOnly(PPU.H_COUNTER_DOTS * PPU.V_BLANK_START_SCANLINE);
        snes.bus.write(0x2102, 0x00);
        snes.bus.write(0x2103, 0x01);
        snes.bus.write(0x2104, 0x55);

        assertEquals(0x55, snes.ppu.oamram.read(0x200));
        assertEquals(0x201, snes.ppu.ppuRegisters().oamAddress());
    }

    @Test
    void vBlankEntryReloadsOamAddressWhenDisplayIsActive() {
        SNES snes = init();
        snes.bus.write(0x2100, 0x0f);
        snes.bus.write(0x2102, 0x05);
        snes.bus.write(0x2103, 0x00);
        snes.bus.read(0x2138);

        assertEquals(0x0b, snes.ppu.ppuRegisters().oamAddress());

        snes.ppu.advanceCountersOnly(PPU.H_COUNTER_DOTS * PPU.V_BLANK_START_SCANLINE);

        assertEquals(0x0a, snes.ppu.ppuRegisters().oamAddress());
    }

    @Test
    void vBlankEntryDoesNotReloadOamAddressDuringForcedBlank() {
        SNES snes = init();
        snes.bus.write(0x2100, 0x80);
        snes.bus.write(0x2102, 0x05);
        snes.bus.write(0x2103, 0x00);
        snes.bus.read(0x2138);

        assertEquals(0x0b, snes.ppu.ppuRegisters().oamAddress());

        snes.ppu.advanceCountersOnly(PPU.H_COUNTER_DOTS * PPU.V_BLANK_START_SCANLINE);

        assertEquals(0x0b, snes.ppu.ppuRegisters().oamAddress());
    }

    @Test
    void clearingForcedBlankOnFirstVBlankLineReloadsOamAddress() {
        SNES snes = init();
        snes.bus.write(0x2100, 0x80);
        snes.bus.write(0x2102, 0x05);
        snes.bus.write(0x2103, 0x00);
        snes.bus.read(0x2138);
        snes.ppu.advanceCountersOnly(PPU.H_COUNTER_DOTS * PPU.V_BLANK_START_SCANLINE);

        assertEquals(0x0b, snes.ppu.ppuRegisters().oamAddress());

        snes.bus.write(0x2100, 0x0f);

        assertEquals(0x0a, snes.ppu.ppuRegisters().oamAddress());
    }

    @Test
    void clearingForcedBlankAfterFirstVBlankLineDoesNotReloadOamAddress() {
        SNES snes = init();
        snes.bus.write(0x2100, 0x80);
        snes.bus.write(0x2102, 0x05);
        snes.bus.write(0x2103, 0x00);
        snes.bus.read(0x2138);
        snes.ppu.advanceCountersOnly(PPU.H_COUNTER_DOTS * (PPU.V_BLANK_START_SCANLINE + 1));

        snes.bus.write(0x2100, 0x0f);

        assertEquals(0x0b, snes.ppu.ppuRegisters().oamAddress());
    }

    @Test
    void overscanDelaysVBlankMemoryAccessAndOamReload() {
        SNES snes = init();
        snes.bus.write(0x2100, 0x0f);
        snes.bus.write(0x2102, 0x05);
        snes.bus.write(0x2103, 0x00);
        snes.bus.read(0x2138);
        snes.bus.write(0x2133, 0x04);

        snes.ppu.advanceCountersOnly(PPU.H_COUNTER_DOTS * PPU.V_BLANK_START_SCANLINE);
        snes.bus.write(0x2104, 0x55);

        assertFalse(snes.ppu.isInVBlank());
        assertEquals(0x0c, snes.ppu.ppuRegisters().oamAddress());
        assertEquals(0, snes.ppu.oamram.read(0x0b));

        snes.ppu.advanceCountersOnly(
                PPU.H_COUNTER_DOTS * (PPU.OVERSCAN_V_BLANK_START_SCANLINE - PPU.V_BLANK_START_SCANLINE));
        snes.bus.write(0x2104, 0x66);
        snes.bus.write(0x2104, 0x77);

        assertTrue(snes.ppu.isInVBlank());
        assertEquals(0x66, snes.ppu.oamram.read(0x0a));
        assertEquals(0x77, snes.ppu.oamram.read(0x0b));
        assertEquals(0x0c, snes.ppu.ppuRegisters().oamAddress());
    }

    @Test
    void decodesBgModeAndMosaic() {
        SNES snes = init();

        snes.bus.write(0x2105, 0b1010_0101);
        assertEquals(5, snes.ppu.ppuRegisters().bgMode());
        assertFalse(snes.ppu.ppuRegisters().bgCharacterSize(0));
        assertTrue(snes.ppu.ppuRegisters().bgCharacterSize(1));
        assertFalse(snes.ppu.ppuRegisters().bgCharacterSize(2));
        assertTrue(snes.ppu.ppuRegisters().bgCharacterSize(3));
        assertFalse(snes.ppu.ppuRegisters().bgMode1Bg3PriorityBit());

        snes.bus.write(0x2106, 0b0010_1001);
        assertTrue(snes.ppu.ppuRegisters().mosaicAffectsBackground(0));
        assertFalse(snes.ppu.ppuRegisters().mosaicAffectsBackground(1));
        assertFalse(snes.ppu.ppuRegisters().mosaicAffectsBackground(2));
        assertTrue(snes.ppu.ppuRegisters().mosaicAffectsBackground(3));
        assertEquals(0x02, snes.ppu.ppuRegisters().mosaicPixelSize());
    }

    @Test
    void decodesBgTilemapAndBaseAddressRegisters() {
        SNES snes = init();

        snes.bus.write(0x210a, 0b1100_0110);
        assertEquals(0b110001, snes.ppu.ppuRegisters().bgTilemapAddress(3));
        assertFalse(snes.ppu.ppuRegisters().bgTilemapHorizontalMirroring(3));
        assertTrue(snes.ppu.ppuRegisters().bgTilemapVerticalMirroring(3));

        snes.bus.write(0x210b, 0b1010_1010);
        assertEquals(0b1010, snes.ppu.ppuRegisters().bgBaseAddressFirst(0));
        assertEquals(0b1010, snes.ppu.ppuRegisters().bgBaseAddressSecond(0));
    }

    @Test
    void decodesVmainVmaddAndVmdata() {
        SNES snes = init();

        snes.bus.write(0x2100, 0x80);
        snes.bus.write(0x2115, 0b1111_1111);
        assertTrue(snes.ppu.ppuRegisters().vmainIncrementMode());
        assertEquals(0b11, snes.ppu.ppuRegisters().vmainAddressRemapping());
        assertEquals(0b11, snes.ppu.ppuRegisters().vmainIncrementAmount());

        snes.bus.write(0x2116, 0xff);
        snes.bus.write(0x2117, 0xff);
        assertEquals(0xffff, snes.ppu.ppuRegisters().vmadd());

        snes.bus.write(0x2118, 0xff);
        snes.bus.write(0x2119, 0x00);
        assertEquals(0x00ff, snes.ppu.ppuRegisters().vmdata());
    }

    @Test
    void vramDataWriteCommitsDuringForcedBlankAndIncrementsLowByteMode() {
        SNES snes = init();

        snes.bus.write(0x2100, 0x80);
        snes.bus.write(0x2116, 0x00);
        snes.bus.write(0x2117, 0x00);
        snes.bus.write(0x2118, 0x42);

        assertEquals(0x42, snes.ppu.vram.read(0));
        assertEquals(1, snes.ppu.getVramAddressRegister());
    }

    @Test
    void vramDataWriteCommitsDuringForcedBlankAndIncrementsHighByteMode() {
        SNES snes = init();

        snes.bus.write(0x2100, 0x80);
        snes.bus.write(0x2115, 0x80);
        snes.bus.write(0x2116, 0x00);
        snes.bus.write(0x2117, 0x00);
        snes.bus.write(0x2119, 0x42);

        assertEquals(0x42, snes.ppu.vram.read(1));
        assertEquals(1, snes.ppu.getVramAddressRegister());
    }

    @Test
    void vramDataWriteIsSkippedDuringActiveDisplayButStillIncrements() {
        SNES snes = init();

        snes.bus.write(0x2116, 0x00);
        snes.bus.write(0x2117, 0x00);
        snes.bus.write(0x2118, 0x42);

        assertEquals(0x00, snes.ppu.vram.read(0));
        assertEquals(1, snes.ppu.getVramAddressRegister());
    }

    @Test
    void vramDataWriteCommitsDuringVBlankWithoutForcedBlank() {
        SNES snes = init();

        snes.ppu.advanceCountersOnly(PPU.H_COUNTER_DOTS * PPU.V_BLANK_START_SCANLINE);
        snes.bus.write(0x2116, 0x00);
        snes.bus.write(0x2117, 0x00);
        snes.bus.write(0x2118, 0x42);

        assertEquals(0x42, snes.ppu.vram.read(0));
        assertEquals(1, snes.ppu.getVramAddressRegister());
    }

    @Test
    void writesCgAddressAndData() {
        SNES snes = init();

        snes.bus.write(0x2100, 0x80);
        snes.bus.write(0x2121, 0x10);
        assertEquals(0x10, snes.ppu.ppuRegisters().cgAddress());
        assertTrue(snes.ppu.ppuRegisters().isCgLowByte());

        snes.bus.write(0x2122, 0xff);
        assertEquals(0xff, snes.ppu.ppuRegisters().cgDataLow());
        assertFalse(snes.ppu.ppuRegisters().isCgLowByte());
        assertEquals(0x10, snes.ppu.ppuRegisters().cgAddress());

        snes.bus.write(0x2122, 0xf8);
        assertEquals(0x78, snes.ppu.ppuRegisters().cgDataHigh());
        assertTrue(snes.ppu.ppuRegisters().isCgLowByte());
        assertEquals(0x11, snes.ppu.ppuRegisters().cgAddress());
        assertEquals(0xff, snes.ppu.cgram.read(0x20));
        assertEquals(0x78, snes.ppu.cgram.read(0x21));
    }

    @Test
    void cgDataWriteIsSkippedDuringActiveDisplayButStillAdvancesPhaseAndAddress() {
        SNES snes = init();

        snes.bus.write(0x2121, 0x10);
        snes.bus.write(0x2122, 0xff);
        snes.bus.write(0x2122, 0xf8);

        assertEquals(0xff, snes.ppu.ppuRegisters().cgDataLow());
        assertEquals(0x78, snes.ppu.ppuRegisters().cgDataHigh());
        assertTrue(snes.ppu.ppuRegisters().isCgLowByte());
        assertEquals(0x11, snes.ppu.ppuRegisters().cgAddress());
        assertEquals(0x00, snes.ppu.cgram.read(0x20));
        assertEquals(0x00, snes.ppu.cgram.read(0x21));
    }

    @Test
    void cgDataWriteCommitsDuringVBlankWithoutForcedBlank() {
        SNES snes = init();

        snes.ppu.advanceCountersOnly(PPU.H_COUNTER_DOTS * PPU.V_BLANK_START_SCANLINE);
        snes.bus.write(0x2121, 0x10);
        snes.bus.write(0x2122, 0xff);
        snes.bus.write(0x2122, 0xf8);

        assertEquals(0xff, snes.ppu.cgram.read(0x20));
        assertEquals(0x78, snes.ppu.cgram.read(0x21));
        assertEquals(0x11, snes.ppu.ppuRegisters().cgAddress());
    }

    @Test
    void decodesMode7Registers() {
        SNES snes = init();

        snes.bus.write(0x211a, 0b0111_1101);
        assertFalse(snes.ppu.ppuRegisters().m7PlayingFieldSize());
        assertTrue(snes.ppu.ppuRegisters().m7EmptySpaceFill());
        assertTrue(snes.ppu.ppuRegisters().m7HorizontalMirroring());
        assertFalse(snes.ppu.ppuRegisters().m7VerticalMirroring());

        writeMode7Register(snes, 0x211b, 0b1111_1111_1011_1001);
        assertEquals(0b1011_1001, snes.ppu.ppuRegisters().m7MatrixLow(0));
        writeMode7Register(snes, 0x211d, 0b1111_1111_1011_1001);
        assertEquals(0b1111_1111_1011_1001, snes.ppu.ppuRegisters().m7Matrix(2));

        writeMode7Register(snes, 0x211f, 0b0001_1001_1010_0101);
        assertEquals(0b0001_1001_1010_0101, snes.ppu.ppuRegisters().m7CenterRaw(0));
        assertEquals(0b0001_1001_1010_0101, snes.ppu.ppuRegisters().m7CenterValue(0));

        writeMode7Register(snes, 0x2120, 0b0110_1001_0101_1010);
        assertEquals(0b0110_1001_0101_1010, snes.ppu.ppuRegisters().m7CenterRaw(1));
        assertEquals(0b0000_1001_0101_1010, snes.ppu.ppuRegisters().m7CenterValue(1));
    }

    @Test
    void modeSevenRegistersShareThePreviousByteLatch() {
        SNES snes = init();

        snes.bus.write(0x211b, 0x34);
        snes.bus.write(0x211c, 0x12);
        snes.bus.write(0x210d, 0x56);
        snes.bus.write(0x210e, 0x78);

        assertEquals(0x3400, snes.ppu.ppuRegisters().m7Matrix(0));
        assertEquals(0x1234, snes.ppu.ppuRegisters().m7Matrix(1));
        assertEquals(0x5612, snes.ppu.ppuRegisters().m7OffsetRaw(0));
        assertEquals(0x7856, snes.ppu.ppuRegisters().m7OffsetRaw(1));
        assertEquals(0x1612, snes.ppu.ppuRegisters().m7OffsetValue(0));
        assertEquals(0x1856, snes.ppu.ppuRegisters().m7OffsetValue(1));
    }

    @Test
    void decodesWindowSelectionAndLogicRegisters() {
        SNES snes = init();

        snes.bus.write(0x2123, 0b1111_1111);
        assertTrue(snes.ppu.ppuRegisters().window1InversionForBg1Bg3Obj(0));
        assertTrue(snes.ppu.ppuRegisters().windowEnableWindow1ForBg1Bg3Obj(0));
        assertTrue(snes.ppu.ppuRegisters().window2InversionForBg1Bg3Obj(0));
        assertTrue(snes.ppu.ppuRegisters().windowEnableWindow2ForBg1Bg3Obj(0));
        assertTrue(snes.ppu.ppuRegisters().window1InversionForBg2Bg4Color(0));
        assertTrue(snes.ppu.ppuRegisters().windowEnableWindow1ForBg2Bg4Color(0));
        assertTrue(snes.ppu.ppuRegisters().window2InversionForBg2Bg4Color(0));
        assertTrue(snes.ppu.ppuRegisters().windowEnableWindow2ForBg2Bg4Color(0));

        snes.bus.write(0x2125, 0b1011_0001);
        assertTrue(snes.ppu.ppuRegisters().window1InversionForBg1Bg3Obj(2));
        assertFalse(snes.ppu.ppuRegisters().windowEnableWindow1ForBg1Bg3Obj(2));
        assertFalse(snes.ppu.ppuRegisters().window2InversionForBg1Bg3Obj(2));
        assertFalse(snes.ppu.ppuRegisters().windowEnableWindow2ForBg1Bg3Obj(2));
        assertTrue(snes.ppu.ppuRegisters().window1InversionForBg2Bg4Color(2));
        assertTrue(snes.ppu.ppuRegisters().windowEnableWindow1ForBg2Bg4Color(2));
        assertFalse(snes.ppu.ppuRegisters().window2InversionForBg2Bg4Color(2));
        assertTrue(snes.ppu.ppuRegisters().windowEnableWindow2ForBg2Bg4Color(2));

        snes.bus.write(0x2126, 0x12);
        snes.bus.write(0x2127, 0x34);
        snes.bus.write(0x2128, 0x56);
        snes.bus.write(0x2129, 0x78);
        assertEquals(0x12, snes.ppu.ppuRegisters().windowPosition(0));
        assertEquals(0x34, snes.ppu.ppuRegisters().windowPosition(1));
        assertEquals(0x56, snes.ppu.ppuRegisters().windowPosition(2));
        assertEquals(0x78, snes.ppu.ppuRegisters().windowPosition(3));

        snes.bus.write(0x212a, 0b1011_0001);
        assertEquals(0b01, snes.ppu.ppuRegisters().windowMaskLogicBg1());
        assertEquals(0b00, snes.ppu.ppuRegisters().windowMaskLogicBg2());
        assertEquals(0b11, snes.ppu.ppuRegisters().windowMaskLogicBg3());
        assertEquals(0b10, snes.ppu.ppuRegisters().windowMaskLogicBg4());

        snes.bus.write(0x212b, 0b1011_0001);
        assertEquals(0b01, snes.ppu.ppuRegisters().windowMaskLogicObj());
        assertEquals(0b00, snes.ppu.ppuRegisters().windowMaskLogicColor());
    }

    @Test
    void decodesScreenAndWindowMaskDesignationRegisters() {
        SNES snes = init();

        snes.bus.write(0x212c, 0b1011_0001);
        assertTrue(snes.ppu.ppuRegisters().screenDesignationBackground(0, 0));
        assertFalse(snes.ppu.ppuRegisters().screenDesignationBackground(0, 1));
        assertFalse(snes.ppu.ppuRegisters().screenDesignationBackground(0, 2));
        assertFalse(snes.ppu.ppuRegisters().screenDesignationBackground(0, 3));
        assertTrue(snes.ppu.ppuRegisters().screenDesignationObj(0));

        snes.bus.write(0x212d, 0b1010_1110);
        assertFalse(snes.ppu.ppuRegisters().screenDesignationBackground(1, 0));
        assertTrue(snes.ppu.ppuRegisters().screenDesignationBackground(1, 1));
        assertTrue(snes.ppu.ppuRegisters().screenDesignationBackground(1, 2));
        assertTrue(snes.ppu.ppuRegisters().screenDesignationBackground(1, 3));
        assertFalse(snes.ppu.ppuRegisters().screenDesignationObj(1));

        snes.bus.write(0x212f, 0b1010_0011);
        assertTrue(snes.ppu.ppuRegisters().windowMaskDesignationBackground(1, 0));
        assertTrue(snes.ppu.ppuRegisters().windowMaskDesignationBackground(1, 1));
        assertFalse(snes.ppu.ppuRegisters().windowMaskDesignationBackground(1, 2));
        assertFalse(snes.ppu.ppuRegisters().windowMaskDesignationBackground(1, 3));
        assertFalse(snes.ppu.ppuRegisters().windowMaskDesignationObj(1));
    }

    @Test
    void decodesColorMathAndSetiniRegisters() {
        SNES snes = init();

        snes.bus.write(0x2130, 0b1011_1001);
        assertEquals(0b10, snes.ppu.ppuRegisters().cgwselClipColorToBlackBeforeMath());
        assertEquals(0b11, snes.ppu.ppuRegisters().cgwselPreventColorMath());
        assertFalse(snes.ppu.ppuRegisters().cgwselAddSubscreen());
        assertTrue(snes.ppu.ppuRegisters().cgwselDirectColorMode());

        snes.bus.write(0x2131, 0b1011_1001);
        assertTrue(snes.ppu.ppuRegisters().cgadsubAddSubtractSelect());
        assertFalse(snes.ppu.ppuRegisters().cgadsubHalfColorMath());
        assertTrue(snes.ppu.ppuRegisters().cgadsubEnableColorMathBackdrop());
        assertTrue(snes.ppu.ppuRegisters().cgadsubEnableColorMathObj());
        assertTrue(snes.ppu.ppuRegisters().cgadsubEnableColorMathBg(3));
        assertFalse(snes.ppu.ppuRegisters().cgadsubEnableColorMathBg(2));
        assertFalse(snes.ppu.ppuRegisters().cgadsubEnableColorMathBg(1));
        assertTrue(snes.ppu.ppuRegisters().cgadsubEnableColorMathBg(0));

        snes.bus.write(0x2132, 0b1011_1001);
        assertTrue(snes.ppu.ppuRegisters().coldataBlue());
        assertFalse(snes.ppu.ppuRegisters().coldataGreen());
        assertTrue(snes.ppu.ppuRegisters().coldataRed());
        assertEquals(0b1_1001, snes.ppu.ppuRegisters().coldataColorIntensity());
        assertEquals(0b1_1001, snes.ppu.ppuRegisters().fixedColorRed());
        assertEquals(0b0_0000, snes.ppu.ppuRegisters().fixedColorGreen());
        assertEquals(0b1_1001, snes.ppu.ppuRegisters().fixedColorBlue());
        assertEquals(0b1_1001_00000_1_1001, snes.ppu.ppuRegisters().fixedColor());

        snes.bus.write(0x2132, 0b0100_0011);
        assertEquals(0b1_1001, snes.ppu.ppuRegisters().fixedColorRed());
        assertEquals(0b0_0011, snes.ppu.ppuRegisters().fixedColorGreen());
        assertEquals(0b1_1001, snes.ppu.ppuRegisters().fixedColorBlue());
        assertEquals(0b1_1001_00011_1_1001, snes.ppu.ppuRegisters().fixedColor());

        snes.bus.write(0x2133, 0b1011_1001);
        assertTrue(snes.ppu.ppuRegisters().setiniExternalSync());
        assertFalse(snes.ppu.ppuRegisters().setiniMode7ExtBg());
        assertTrue(snes.ppu.ppuRegisters().setiniEnablePseudoHiresMode());
        assertFalse(snes.ppu.ppuRegisters().setiniOverscanMode());
        assertFalse(snes.ppu.ppuRegisters().setiniObjInterlace());
        assertTrue(snes.ppu.ppuRegisters().setiniScreenInterlace());
    }

    @Test
    void readOnlyPpuRegisterWritesAreNoop() {
        SNES snes = init();
        writeMode7Register(snes, 0x211b, 0x0100);
        snes.ppu.advanceCountersOnly(PPU.H_COUNTER_DOTS + 3);
        snes.bus.read(0x2137);

        snes.bus.write(0x2134, 0xff);
        snes.bus.write(0x213e, 0xff);
        snes.bus.write(0x213f, 0xff);

        assertEquals(0x00, snes.bus.read(0x2134));
        assertEquals(0, snes.ppu.registers()[0x3e]);
        assertEquals(0x03, snes.bus.read(0x213c));
        assertEquals(0x02, snes.bus.read(0x213c));
        assertEquals(0x01, snes.bus.read(0x213d));
    }

    @Test
    void unsupportedPpuWriteRegisterThrows() {
        SNES snes = init();

        assertThrows(InvalidAddress.class, () -> snes.ppu.write(0x40, 0xff));
    }

    private static void writeMode7Register(SNES snes, int address, int value) {
        snes.bus.write(address, value);
        snes.bus.write(address, value >>> 8);
    }

    private static SNES init() {
        SNES snes = new SNES(new NoRenderer(0, 0, 0));
        snes.bus.mapComponents(snes);
        return snes;
    }
}
