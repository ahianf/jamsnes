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
        assertEquals(0b0_1111_1111, snes.ppu.ppuRegisters().oamAddress());

        snes.bus.write(0x2103, 0b1111_1111);
        assertTrue(snes.ppu.ppuRegisters().oamObjPriorityActivationBit());
        assertEquals(0b1_1111_1111, snes.ppu.ppuRegisters().oamAddress());
    }

    @Test
    void writesOamDataAndIncrementsAddress() {
        SNES snes = init();

        snes.bus.write(0x2102, 0x0b);
        snes.bus.write(0x2103, 0x80);
        snes.bus.write(0x2104, 0x42);

        assertEquals(0x42, snes.ppu.ppuRegisters().oamData());
        assertEquals(0x42, snes.ppu.oamram.read(0x0b));
        assertEquals(0x0c, snes.ppu.ppuRegisters().oamAddress());
        assertTrue(snes.ppu.ppuRegisters().oamObjPriorityActivationBit());

        snes.bus.write(0x2104, 0x24);
        assertEquals(0x24, snes.ppu.oamram.read(0x0c));
        assertEquals(0x0d, snes.ppu.ppuRegisters().oamAddress());
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
    void vramDataWriteIsSkippedDuringForcedBlankButStillIncrementsLowByteMode() {
        SNES snes = init();

        snes.bus.write(0x2100, 0x80);
        snes.bus.write(0x2116, 0x00);
        snes.bus.write(0x2117, 0x00);
        snes.bus.write(0x2118, 0x42);

        assertEquals(0, snes.ppu.vram.read(0));
        assertEquals(1, snes.ppu.getVramAddressRegister());
    }

    @Test
    void vramDataWriteIsSkippedDuringForcedBlankButStillIncrementsHighByteMode() {
        SNES snes = init();

        snes.bus.write(0x2100, 0x80);
        snes.bus.write(0x2115, 0x80);
        snes.bus.write(0x2116, 0x00);
        snes.bus.write(0x2117, 0x00);
        snes.bus.write(0x2119, 0x42);

        assertEquals(0, snes.ppu.vram.read(1));
        assertEquals(1, snes.ppu.getVramAddressRegister());
    }

    @Test
    void writesCgAddressAndData() {
        SNES snes = init();

        snes.bus.write(0x2121, 0x10);
        assertEquals(0x10, snes.ppu.ppuRegisters().cgAddress());
        assertTrue(snes.ppu.ppuRegisters().isCgLowByte());

        snes.bus.write(0x2122, 0xff);
        assertEquals(0xff, snes.ppu.ppuRegisters().cgDataLow());
        assertFalse(snes.ppu.ppuRegisters().isCgLowByte());
        assertEquals(0x10, snes.ppu.ppuRegisters().cgAddress());

        snes.bus.write(0x2122, 0xf8);
        assertEquals(0xf8, snes.ppu.ppuRegisters().cgDataHigh());
        assertTrue(snes.ppu.ppuRegisters().isCgLowByte());
        assertEquals(0x12, snes.ppu.ppuRegisters().cgAddress());
        assertEquals(0xff, snes.ppu.cgram.read(0x10));
        assertEquals(0xf8, snes.ppu.cgram.read(0x11));
    }

    @Test
    void decodesMode7Registers() {
        SNES snes = init();

        snes.bus.write(0x211a, 0b0111_1101);
        assertFalse(snes.ppu.ppuRegisters().m7PlayingFieldSize());
        assertTrue(snes.ppu.ppuRegisters().m7EmptySpaceFill());
        assertTrue(snes.ppu.ppuRegisters().m7HorizontalMirroring());
        assertFalse(snes.ppu.ppuRegisters().m7VerticalMirroring());

        snes.bus.write(0x211b, 0b1011_1001);
        assertEquals(0b1011_1001, snes.ppu.ppuRegisters().m7MatrixLow(0));
        snes.bus.write(0x211d, 0b1011_1001);
        snes.bus.write(0x211d, 0b1111_1111);
        assertEquals(0b1011_1001_1111_1111, snes.ppu.ppuRegisters().m7Matrix(2));

        snes.bus.write(0x211f, 0b0001_1001);
        snes.bus.write(0x211f, 0b1010_0101);
        assertEquals(0b0001_1001_1010_0101, snes.ppu.ppuRegisters().m7CenterRaw(0));
        assertEquals(0b0000_0011_0011_0100, snes.ppu.ppuRegisters().m7CenterValue(0));

        snes.bus.write(0x2120, 0b0110_1001);
        snes.bus.write(0x2120, 0b0101_1010);
        assertEquals(0b0110_1001_0101_1010, snes.ppu.ppuRegisters().m7CenterRaw(1));
        assertEquals(0b0000_1101_0010_1011, snes.ppu.ppuRegisters().m7CenterValue(1));
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
        assertTrue(snes.ppu.ppuRegisters().window2InversionForBg1Bg3Obj(2));
        assertTrue(snes.ppu.ppuRegisters().windowEnableWindow2ForBg1Bg3Obj(2));
        assertFalse(snes.ppu.ppuRegisters().window1InversionForBg2Bg4Color(2));
        assertFalse(snes.ppu.ppuRegisters().windowEnableWindow1ForBg2Bg4Color(2));
        assertFalse(snes.ppu.ppuRegisters().window2InversionForBg2Bg4Color(2));
        assertTrue(snes.ppu.ppuRegisters().windowEnableWindow2ForBg2Bg4Color(2));

        snes.bus.write(0x212a, 0b1011_0001);
        assertEquals(0b10, snes.ppu.ppuRegisters().windowMaskLogicBg1());
        assertEquals(0b11, snes.ppu.ppuRegisters().windowMaskLogicBg2());
        assertEquals(0b00, snes.ppu.ppuRegisters().windowMaskLogicBg3());
        assertEquals(0b01, snes.ppu.ppuRegisters().windowMaskLogicBg4());

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

        snes.bus.write(0x2133, 0b1011_1001);
        assertTrue(snes.ppu.ppuRegisters().setiniExternalSync());
        assertFalse(snes.ppu.ppuRegisters().setiniMode7ExtBg());
        assertTrue(snes.ppu.ppuRegisters().setiniEnablePseudoHiresMode());
        assertFalse(snes.ppu.ppuRegisters().setiniOverscanMode());
        assertFalse(snes.ppu.ppuRegisters().setiniObjInterlace());
        assertTrue(snes.ppu.ppuRegisters().setiniScreenInterlace());
    }

    @Test
    void stat77WriteIsNoop() {
        SNES snes = init();

        snes.bus.write(0x213e, 0xff);

        assertEquals(0, snes.ppu.registers()[0x3e]);
    }

    @Test
    void unsupportedPpuWriteRegisterThrows() {
        SNES snes = init();

        assertThrows(InvalidAddress.class, () -> snes.bus.write(0x2134, 0xff));
        assertThrows(InvalidAddress.class, () -> snes.bus.write(0x213f, 0xff));
    }

    private static SNES init() {
        SNES snes = new SNES(new NoRenderer(0, 0, 0));
        snes.bus.mapComponents(snes);
        return snes;
    }
}
