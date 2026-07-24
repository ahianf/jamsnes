package jamsnes.ppu;

import jamsnes.SNES;
import jamsnes.models.Vector2;
import jamsnes.renderer.IRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PpuRenderIntegrationTest {
    @Test
    void registerWritesRefreshOwnedBackgrounds() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 1, 0x001f);
        snes.bus.write(0x2107, 0x04);
        snes.bus.write(0x210b, 0x01);
        snes.bus.write(0x212c, 0x01);
        snes.ppu.vram.write(0x0800, 0x00);
        snes.ppu.vram.write(0x0801, 0x00);
        snes.ppu.vram.write(0x2000, 0x80);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][0]);
    }

    @Test
    void composesModeZeroBackgroundsByPriorityLevel() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 1, 0x001f);
        writeColor(snes, 2, 0x03e0);
        snes.bus.write(0x2105, 0x00);
        snes.bus.write(0x2109, 0x04);
        snes.bus.write(0x210b, 0x01);
        snes.bus.write(0x210c, 0x02);
        snes.bus.write(0x212c, 0x05);
        snes.ppu.vram.write(0x0000, 0x00);
        snes.ppu.vram.write(0x0001, 0x00);
        snes.ppu.vram.write(0x0800, 0x00);
        snes.ppu.vram.write(0x0801, 0x00);
        snes.ppu.vram.write(0x2000, 0x00);
        snes.ppu.vram.write(0x2001, 0x80);
        snes.ppu.vram.write(0x4000, 0x80);
        snes.ppu.vram.write(0x4001, 0x00);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x03e0), snes.ppu.mainScreen()[0][0]);
    }

    @Test
    void modeZeroUsesBackgroundTwoPaletteRegion() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 1, 0x001f);
        writeColor(snes, 33, 0x03e0);
        snes.bus.write(0x2105, 0x00);
        snes.bus.write(0x2108, 0x04);
        snes.bus.write(0x210b, 0x10);
        snes.bus.write(0x212c, 0x02);
        snes.ppu.vram.write(0x0800, 0x00);
        snes.ppu.vram.write(0x0801, 0x00);
        snes.ppu.vram.write(0x2000, 0x80);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x03e0), snes.ppu.mainScreen()[0][0]);
    }

    @Test
    void modeZeroBackgroundTwoLowPriorityRendersAboveObjectPriorityOne() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 33, 0x001f);
        snes.bus.write(0x2105, 0x00);
        snes.bus.write(0x2108, 0x04);
        snes.bus.write(0x210b, 0x10);
        snes.bus.write(0x212c, 0x12);
        snes.ppu.vram.write(0x0800, 0x00);
        snes.ppu.vram.write(0x0801, 0x00);
        snes.ppu.vram.write(0x2000, 0x80);
        setupObjFirstPixel(snes, 0x03e0, 0, 1);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][0]);
    }

    @Test
    void modeOneBg3PriorityBitRaisesBg3Priority() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 1, 0x001f);
        writeColor(snes, 2, 0x03e0);
        snes.bus.write(0x2105, 0x09);
        snes.bus.write(0x2109, 0x04);
        snes.bus.write(0x210b, 0x01);
        snes.bus.write(0x210c, 0x02);
        snes.bus.write(0x212c, 0x05);
        snes.ppu.vram.write(0x0000, 0x00);
        snes.ppu.vram.write(0x0001, 0x00);
        snes.ppu.vram.write(0x0800, 0x00);
        snes.ppu.vram.write(0x0801, 1 << 5);
        snes.ppu.vram.write(0x2000, 0x00);
        snes.ppu.vram.write(0x2001, 0x80);
        snes.ppu.vram.write(0x4000, 0x80);
        snes.ppu.vram.write(0x4001, 0x00);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][0]);
    }

    @Test
    void modeOneBackgroundThreeHighPriorityRendersAboveObjectPriorityZero() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 1, 0x001f);
        snes.bus.write(0x2105, 0x01);
        snes.bus.write(0x2109, 0x04);
        snes.bus.write(0x210c, 0x01);
        snes.bus.write(0x212c, 0x14);
        snes.ppu.vram.write(0x0800, 0x00);
        snes.ppu.vram.write(0x0801, 0x20);
        snes.ppu.vram.write(0x2000, 0x80);
        setupObjFirstPixel(snes, 0x03e0, 0, 0);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][0]);
    }

    @Test
    void composesModeTwoBackgrounds() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 1, 0x001f);
        writeColor(snes, 2, 0x03e0);
        snes.bus.write(0x2105, 0x02);
        snes.bus.write(0x2107, 0x04);
        snes.bus.write(0x2108, 0x08);
        snes.bus.write(0x210b, 0x01);
        snes.bus.write(0x212c, 0x03);
        snes.ppu.vram.write(0x0800, 0x00);
        snes.ppu.vram.write(0x0801, 0x00);
        snes.ppu.vram.write(0x1000, 0x00);
        snes.ppu.vram.write(0x1001, 0x00);
        snes.ppu.vram.write(0x0000, 0x80);
        snes.ppu.vram.write(0x0001, 0x00);
        snes.ppu.vram.write(0x2000, 0x00);
        snes.ppu.vram.write(0x2001, 0x80);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x03e0), snes.ppu.mainScreen()[0][0]);
    }

    @Test
    void modeTwoBackgroundTwoHighPriorityRendersAboveObjectPriorityOne() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 1, 0x001f);
        snes.bus.write(0x2105, 0x02);
        snes.bus.write(0x2108, 0x04);
        snes.bus.write(0x210b, 0x10);
        snes.bus.write(0x212c, 0x12);
        snes.ppu.vram.write(0x0800, 0x00);
        snes.ppu.vram.write(0x0801, 0x20);
        snes.ppu.vram.write(0x2000, 0x80);
        setupObjFirstPixel(snes, 0x03e0, 0, 1);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][0]);
    }

    @Test
    void backgroundScrollOffsetsSelectScrolledPixels() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 1, 0x001f);
        writeColor(snes, 2, 0x03e0);
        snes.bus.write(0x210b, 0x01);
        snes.bus.write(0x212c, 0x01);
        snes.bus.write(0x210d, 0x08);
        snes.bus.write(0x210d, 0x00);
        snes.ppu.vram.write(0x0000, 0x00);
        snes.ppu.vram.write(0x0001, 0x00);
        snes.ppu.vram.write(0x0002, 0x01);
        snes.ppu.vram.write(0x0003, 0x00);
        snes.ppu.vram.write(0x2000, 0x80);
        snes.ppu.vram.write(0x2001, 0x00);
        snes.ppu.vram.write(0x2010, 0x00);
        snes.ppu.vram.write(0x2011, 0x80);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x03e0), snes.ppu.mainScreen()[0][0]);
    }

    @Test
    void backgroundMosaicRepeatsFirstPixelInMosaicBlock() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 1, 0x001f);
        writeColor(snes, 2, 0x03e0);
        snes.bus.write(0x2106, 0x11);
        snes.bus.write(0x210b, 0x01);
        snes.bus.write(0x212c, 0x01);
        snes.ppu.vram.write(0x0000, 0x00);
        snes.ppu.vram.write(0x0001, 0x00);
        snes.ppu.vram.write(0x2000, 0x80);
        snes.ppu.vram.write(0x2001, 0x40);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][0]);
        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][1]);
    }

    @Test
    void modeThreeDirectColorBypassesCgramForBackgroundOne() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 7, 0x03e0);
        snes.bus.write(0x2105, 0x03);
        snes.bus.write(0x210b, 0x01);
        snes.bus.write(0x212c, 0x01);
        snes.bus.write(0x2130, 0x01);
        snes.ppu.vram.write(0x0000, 0x00);
        snes.ppu.vram.write(0x0001, 0x00);
        snes.ppu.vram.write(0x2000, 0x80);
        snes.ppu.vram.write(0x2001, 0x80);
        snes.ppu.vram.write(0x2010, 0x80);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(0xe70000ff, snes.ppu.mainScreen()[0][0]);
    }

    @Test
    void modeTwoUsesBg3HorizontalOffsetsAfterLeftmostVisibleColumn() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 1, 0x001f);
        writeColor(snes, 2, 0x03e0);
        snes.bus.write(0x2105, 0x02);
        snes.bus.write(0x2109, 0x04);
        snes.bus.write(0x210b, 0x01);
        snes.bus.write(0x212c, 0x01);
        snes.ppu.vram.write(0x0000, 0x00);
        snes.ppu.vram.write(0x0002, 0x00);
        snes.ppu.vram.write(0x0004, 0x01);
        snes.ppu.vram.write(0x0800, 0x08);
        snes.ppu.vram.write(0x0801, 0x20);
        snes.ppu.vram.write(0x2000, 0x80);
        snes.ppu.vram.write(0x2021, 0x80);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][0]);
        assertEquals(PPUUtils.cgramColorToRGBA(0x03e0), snes.ppu.mainScreen()[0][8]);
    }

    @Test
    void modeTwoUsesBg3VerticalOffsetsFromFollowingRow() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 1, 0x001f);
        writeColor(snes, 2, 0x03e0);
        snes.bus.write(0x2105, 0x02);
        snes.bus.write(0x2109, 0x04);
        snes.bus.write(0x210b, 0x01);
        snes.bus.write(0x212c, 0x01);
        snes.ppu.vram.write(0x0000, 0x00);
        snes.ppu.vram.write(0x0042, 0x01);
        snes.ppu.vram.write(0x0840, 0x08);
        snes.ppu.vram.write(0x0841, 0x20);
        snes.ppu.vram.write(0x2000, 0x80);
        snes.ppu.vram.write(0x2021, 0x80);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][0]);
        assertEquals(PPUUtils.cgramColorToRGBA(0x03e0), snes.ppu.mainScreen()[0][8]);
    }

    @Test
    void modeFourUsesHorizontalEntriesWhenDirectionBitIsClear() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 1, 0x001f);
        writeColor(snes, 2, 0x03e0);
        snes.bus.write(0x2105, 0x04);
        snes.bus.write(0x2109, 0x04);
        snes.bus.write(0x210b, 0x01);
        snes.bus.write(0x212c, 0x01);
        snes.ppu.vram.write(0x0000, 0x00);
        snes.ppu.vram.write(0x0002, 0x00);
        snes.ppu.vram.write(0x0004, 0x02);
        snes.ppu.vram.write(0x0800, 0x08);
        snes.ppu.vram.write(0x0801, 0x20);
        snes.ppu.vram.write(0x2000, 0x80);
        snes.ppu.vram.write(0x2081, 0x80);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][0]);
        assertEquals(PPUUtils.cgramColorToRGBA(0x03e0), snes.ppu.mainScreen()[0][8]);
    }

    @Test
    void modeFourUsesVerticalEntriesWhenDirectionBitIsSet() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 1, 0x001f);
        writeColor(snes, 2, 0x03e0);
        snes.bus.write(0x2105, 0x04);
        snes.bus.write(0x2109, 0x04);
        snes.bus.write(0x210b, 0x01);
        snes.bus.write(0x212c, 0x01);
        snes.ppu.vram.write(0x0000, 0x00);
        snes.ppu.vram.write(0x0042, 0x01);
        snes.ppu.vram.write(0x0800, 0x08);
        snes.ppu.vram.write(0x0801, 0xa0);
        snes.ppu.vram.write(0x2000, 0x80);
        snes.ppu.vram.write(0x2041, 0x80);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][0]);
        assertEquals(PPUUtils.cgramColorToRGBA(0x03e0), snes.ppu.mainScreen()[0][8]);
    }

    @Test
    void modeSixUsesBg3HorizontalOffsetsAtHighResolution() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 1, 0x001f);
        writeColor(snes, 2, 0x03e0);
        snes.bus.write(0x2105, 0x06);
        snes.bus.write(0x2109, 0x04);
        snes.bus.write(0x210b, 0x01);
        snes.bus.write(0x212c, 0x01);
        snes.ppu.vram.write(0x0000, 0x00);
        snes.ppu.vram.write(0x0002, 0x00);
        snes.ppu.vram.write(0x0004, 0x02);
        snes.ppu.vram.write(0x0800, 0x08);
        snes.ppu.vram.write(0x0801, 0x20);
        snes.ppu.vram.write(0x2000, 0x80);
        snes.ppu.vram.write(0x2041, 0x80);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][0]);
        assertEquals(PPUUtils.cgramColorToRGBA(0x03e0), snes.ppu.mainScreen()[0][8]);
    }

    @Test
    void modeSixUsesBg3VerticalOffsetsAtHighResolution() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 1, 0x001f);
        writeColor(snes, 2, 0x03e0);
        snes.bus.write(0x2105, 0x06);
        snes.bus.write(0x2109, 0x04);
        snes.bus.write(0x210b, 0x01);
        snes.bus.write(0x212c, 0x01);
        snes.ppu.vram.write(0x0000, 0x00);
        snes.ppu.vram.write(0x0042, 0x02);
        snes.ppu.vram.write(0x0840, 0x08);
        snes.ppu.vram.write(0x0841, 0x20);
        snes.ppu.vram.write(0x2000, 0x80);
        snes.ppu.vram.write(0x2041, 0x80);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][0]);
        assertEquals(PPUUtils.cgramColorToRGBA(0x03e0), snes.ppu.mainScreen()[0][8]);
    }

    @Test
    void mainScreenWindowMaskSuppressesBackgroundPixelsInsideWindow() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 1, 0x001f);
        snes.bus.write(0x210b, 0x01);
        snes.bus.write(0x2123, 0x02);
        snes.bus.write(0x2126, 0x00);
        snes.bus.write(0x2127, 0x00);
        snes.bus.write(0x212c, 0x01);
        snes.bus.write(0x212e, 0x01);
        snes.ppu.vram.write(0x0000, 0x00);
        snes.ppu.vram.write(0x0001, 0x00);
        snes.ppu.vram.write(0x2000, 0xc0);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(0, snes.ppu.mainScreen()[0][0]);
        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][1]);
    }

    @Test
    void mainScreenWindowTwoMaskSuppressesBackgroundPixelsInsideWindow() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 1, 0x001f);
        snes.bus.write(0x210b, 0x01);
        snes.bus.write(0x2123, 0x08);
        snes.bus.write(0x2128, 0x00);
        snes.bus.write(0x2129, 0x00);
        snes.bus.write(0x212c, 0x01);
        snes.bus.write(0x212e, 0x01);
        snes.ppu.vram.write(0x0000, 0x00);
        snes.ppu.vram.write(0x0001, 0x00);
        snes.ppu.vram.write(0x2000, 0xc0);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(0, snes.ppu.mainScreen()[0][0]);
        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][1]);
    }

    @Test
    void invertedMainScreenWindowMasksBackgroundPixelsOutsideWindow() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 1, 0x001f);
        snes.bus.write(0x210b, 0x01);
        snes.bus.write(0x2123, 0x03);
        snes.bus.write(0x2126, 0x00);
        snes.bus.write(0x2127, 0x00);
        snes.bus.write(0x212c, 0x01);
        snes.bus.write(0x212e, 0x01);
        snes.ppu.vram.write(0x0000, 0x00);
        snes.ppu.vram.write(0x0001, 0x00);
        snes.ppu.vram.write(0x2000, 0xc0);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][0]);
        assertEquals(0, snes.ppu.mainScreen()[0][1]);
    }

    @Test
    void backgroundWindowLogicUsesItsLowRegisterPair() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 1, 0x001f);
        snes.bus.write(0x210b, 0x01);
        snes.bus.write(0x2123, 0x0a);
        snes.bus.write(0x2126, 0x00);
        snes.bus.write(0x2127, 0x01);
        snes.bus.write(0x2128, 0x01);
        snes.bus.write(0x2129, 0x02);
        snes.bus.write(0x212a, 0x02);
        snes.bus.write(0x212c, 0x01);
        snes.bus.write(0x212e, 0x01);
        snes.ppu.vram.write(0x0000, 0x00);
        snes.ppu.vram.write(0x0001, 0x00);
        snes.ppu.vram.write(0x2000, 0xe0);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(0, snes.ppu.mainScreen()[0][0]);
        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][1]);
        assertEquals(0, snes.ppu.mainScreen()[0][2]);
    }

    @Test
    void modeSixComposesOnlyBackgroundOne() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 1, 0x001f);
        snes.bus.write(0x2105, 0x06);
        snes.bus.write(0x2108, 0x08);
        snes.bus.write(0x212c, 0x02);
        snes.ppu.vram.write(0x1000, 0x00);
        snes.ppu.vram.write(0x1001, 0x00);
        snes.ppu.vram.write(0x0000, 0x80);
        snes.ppu.vram.write(0x0001, 0x00);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(0, snes.ppu.mainScreen()[0][0]);
    }

    @Test
    void modeFiveDownsamplesFullHighResolutionCharacterRow() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 1, 0x001f);
        writeColor(snes, 2, 0x03e0);
        snes.bus.write(0x2105, 0x05);
        snes.bus.write(0x210b, 0x01);
        snes.bus.write(0x212c, 0x01);
        snes.ppu.vram.write(0x0000, 0x00);
        snes.ppu.vram.write(0x0001, 0x00);
        snes.ppu.vram.write(0x2000, 0x80);
        snes.ppu.vram.write(0x2021, 0x80);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(new Vector2<>(512, 256), snes.ppu.background(0).backgroundSize);
        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][0]);
        assertEquals(PPUUtils.cgramColorToRGBA(0x03e0), snes.ppu.mainScreen()[0][4]);
    }

    @Test
    void modeSevenRendersIdentityMappedBackgroundOne() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 5, 0x001f);
        setupMode7Identity(snes);
        writeMode7Map(snes, 0, 0, 1);
        writeMode7Pixel(snes, 1, 0, 0, 5);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][0]);
    }

    @Test
    void modeSevenBackgroundOneHonorsMainScreenWindowMask() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 5, 0x001f);
        setupMode7Identity(snes);
        snes.bus.write(0x2123, 0x02);
        snes.bus.write(0x2126, 0x00);
        snes.bus.write(0x2127, 0x00);
        snes.bus.write(0x212e, 0x01);
        writeMode7Map(snes, 0, 0, 1);
        writeMode7Pixel(snes, 1, 0, 0, 5);
        writeMode7Pixel(snes, 1, 1, 0, 5);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(0, snes.ppu.mainScreen()[0][0]);
        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][1]);
    }

    @Test
    void modeSevenAppliesBackgroundOneMosaicBeforeTransformingCoordinates() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 5, 0x001f);
        writeColor(snes, 6, 0x03e0);
        setupMode7Identity(snes);
        snes.bus.write(0x2106, 0x11);
        writeMode7Map(snes, 0, 0, 1);
        writeMode7Pixel(snes, 1, 0, 0, 5);
        writeMode7Pixel(snes, 1, 1, 0, 6);
        writeMode7Pixel(snes, 1, 0, 1, 6);

        snes.ppu.renderMainAndSubScreen();

        int mosaicColor = PPUUtils.cgramColorToRGBA(0x001f);
        assertEquals(mosaicColor, snes.ppu.mainScreen()[0][0]);
        assertEquals(mosaicColor, snes.ppu.mainScreen()[0][1]);
        assertEquals(mosaicColor, snes.ppu.mainScreen()[1][0]);
    }

    @Test
    void modeSevenDirectColorBypassesCgramForBackgroundOne() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 0xe7, 0x001f);
        setupMode7Identity(snes);
        snes.bus.write(0x2130, 0x01);
        writeMode7Map(snes, 0, 0, 1);
        writeMode7Pixel(snes, 1, 0, 0, 0xe7);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(0xe784c6ff, snes.ppu.mainScreen()[0][0]);
    }

    @Test
    void modeSevenLargePlayingFieldMakesOutsidePixelsTransparent() {
        SNES snes = init(new TestRenderer());
        setupMode7Identity(snes);
        snes.bus.write(0x211a, 0x80);
        writeMode7Register(snes, 0x210d, 0x03ff);
        writeMode7Pixel(snes, 0, 0, 0, 5);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(0, snes.ppu.mainScreen()[0][1]);
    }

    @Test
    void modeSevenLargePlayingFieldCanFillOutsidePixelsWithCharacterZero() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 5, 0x001f);
        setupMode7Identity(snes);
        snes.bus.write(0x211a, 0xc0);
        writeMode7Register(snes, 0x210d, 0x03ff);
        writeMode7Pixel(snes, 0, 0, 0, 5);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][1]);
    }

    @Test
    void modeSevenHorizontalMirroringSamplesFromOppositeSide() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 5, 0x001f);
        setupMode7Identity(snes);
        snes.bus.write(0x211a, 0x01);
        writeMode7Map(snes, 31, 0, 1);
        writeMode7Pixel(snes, 1, 7, 0, 5);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][0]);
    }

    @Test
    void modeSevenVerticalMirroringSamplesFromOppositeSide() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 5, 0x001f);
        setupMode7Identity(snes);
        snes.bus.write(0x211a, 0x02);
        writeMode7Map(snes, 0, 31, 1);
        writeMode7Pixel(snes, 1, 0, 7, 5);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][0]);
    }

    @Test
    void modeSevenTransformsScrollOffsetWithTheAffineMatrix() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 5, 0x001f);
        writeColor(snes, 6, 0x03e0);
        setupMode7Identity(snes);
        writeMode7Register(snes, 0x211b, 0x0200);
        writeMode7Register(snes, 0x210d, 0x0001);
        writeMode7Map(snes, 0, 0, 1);
        writeMode7Pixel(snes, 1, 1, 0, 6);
        writeMode7Pixel(snes, 1, 2, 0, 5);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][0]);
    }

    @Test
    void modeSevenExtBgRendersBackgroundTwoWhenEnabled() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 5, 0x001f);
        setupMode7Identity(snes);
        snes.bus.write(0x2133, 0x40);
        snes.bus.write(0x212c, 0x02);
        writeMode7Map(snes, 0, 0, 1);
        writeMode7Pixel(snes, 1, 0, 0, 5);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][0]);
    }

    @Test
    void modeSevenExtBgHonorsBackgroundTwoMainScreenWindowMask() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 5, 0x001f);
        setupMode7Identity(snes);
        snes.bus.write(0x2133, 0x40);
        snes.bus.write(0x2123, 0x20);
        snes.bus.write(0x2126, 0x00);
        snes.bus.write(0x2127, 0x00);
        snes.bus.write(0x212c, 0x02);
        snes.bus.write(0x212e, 0x02);
        writeMode7Map(snes, 0, 0, 1);
        writeMode7Pixel(snes, 1, 0, 0, 5);
        writeMode7Pixel(snes, 1, 1, 0, 5);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(0, snes.ppu.mainScreen()[0][0]);
        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][1]);
    }

    @Test
    void modeSevenExtBgUsesBg1ForVerticalMosaicAndBg2ForHorizontalMosaic() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 5, 0x001f);
        writeColor(snes, 6, 0x03e0);
        setupMode7Identity(snes);
        snes.bus.write(0x2133, 0x40);
        snes.bus.write(0x212c, 0x02);
        snes.bus.write(0x2106, 0x11);
        writeMode7Map(snes, 0, 0, 1);
        writeMode7Pixel(snes, 1, 0, 0, 5);
        writeMode7Pixel(snes, 1, 1, 0, 6);
        writeMode7Pixel(snes, 1, 0, 1, 6);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[1][0]);
        assertEquals(PPUUtils.cgramColorToRGBA(0x03e0), snes.ppu.mainScreen()[0][1]);
    }

    @Test
    void modeSevenExtBgBackgroundTwoIgnoresDirectColor() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 5, 0x001f);
        setupMode7Identity(snes);
        snes.bus.write(0x2130, 0x01);
        snes.bus.write(0x2133, 0x40);
        snes.bus.write(0x212c, 0x02);
        writeMode7Map(snes, 0, 0, 1);
        writeMode7Pixel(snes, 1, 0, 0, 0x85);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][0]);
    }

    @Test
    void modeSevenExtBgUsesBitSevenAsPriority() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 5, 0x001f);
        writeColor(snes, 6, 0x03e0);
        setupMode7Identity(snes);
        snes.bus.write(0x2133, 0x40);
        snes.bus.write(0x212c, 0x03);
        writeMode7Map(snes, 0, 0, 1);
        writeMode7Pixel(snes, 1, 0, 0, 0x86);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x03e0), snes.ppu.mainScreen()[0][0]);
    }

    @Test
    void rendersObjectPixelsWhenEnabledOnMainScreen() {
        SNES snes = init(new TestRenderer());
        setupObjFirstPixel(snes, 0x001f);
        snes.bus.write(0x212c, 0x10);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][0]);
    }

    @Test
    void objectInterlaceSelectsAlternatingSourceRowsAndHalvesCoverage() {
        SNES snes = init(new TestRenderer());
        hideAllObjects(snes);
        writeColor(snes, 129, 0x001f);
        writeColor(snes, 130, 0x03e0);
        writeObject(snes, 0, 0x00, 0x00, 0x00, 0x30);
        snes.ppu.vram.write(0x0000, 0x80);
        snes.ppu.vram.write(0x0003, 0x80);
        snes.ppu.vram.write(0x000c, 0x80);
        snes.bus.write(0x212c, 0x10);
        snes.bus.write(0x2133, 0x02);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][0]);
        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[3][0]);
        assertEquals(0, snes.ppu.mainScreen()[4][0]);

        snes.ppu.advanceCountersOnly(PPU.H_COUNTER_DOTS * PPU.V_COUNTER_SCANLINES);
        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x03e0), snes.ppu.mainScreen()[0][0]);
    }

    @Test
    void verticallyFlippedObjectInterlaceReversesFieldRowSelection() {
        SNES snes = init(new TestRenderer());
        hideAllObjects(snes);
        writeColor(snes, 129, 0x001f);
        writeColor(snes, 130, 0x03e0);
        writeObject(snes, 0, 0x00, 0x00, 0x00, 0xb0);
        snes.ppu.vram.write(0x000c, 0x80);
        snes.ppu.vram.write(0x000f, 0x80);
        snes.bus.write(0x212c, 0x10);
        snes.bus.write(0x2133, 0x02);

        snes.ppu.renderMainAndSubScreen();
        assertEquals(PPUUtils.cgramColorToRGBA(0x03e0), snes.ppu.mainScreen()[0][0]);

        snes.ppu.advanceCountersOnly(PPU.H_COUNTER_DOTS * PPU.V_COUNTER_SCANLINES);
        snes.ppu.renderMainAndSubScreen();
        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][0]);
    }

    @Test
    void lowerObjectIndexWinsEqualObjectPriorityByDefault() {
        SNES snes = init(new TestRenderer());
        setupOverlappingObjectPixels(snes);
        snes.bus.write(0x212c, 0x10);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][0]);
    }

    @Test
    void oamPriorityRotationSelectsConfiguredObjectAsHighestPriority() {
        SNES snes = init(new TestRenderer());
        setupOverlappingObjectPixels(snes);
        snes.bus.write(0x2102, 0x02);
        snes.bus.write(0x2103, 0x80);
        snes.bus.write(0x212c, 0x10);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x03e0), snes.ppu.mainScreen()[0][0]);
    }

    @Test
    void objectRangeLimitDropsTheThirtyThirdObjectAndSetsStat77() {
        SNES snes = init(new TestRenderer());
        hideAllObjects(snes);
        writeColor(snes, 129, 0x001f);
        snes.ppu.vram.write(0x0000, 0x80);
        for (int objectIndex = 0; objectIndex < 32; objectIndex++) {
            writeObject(snes, objectIndex, 0x08, 0x00, 0x00, 0x30);
        }
        writeObject(snes, 32, 0x00, 0x00, 0x00, 0x30);
        snes.bus.write(0x212c, 0x10);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(0, snes.ppu.mainScreen()[0][0]);
        assertEquals(0x40, snes.ppu.read(0x3e) & 0xc0);
    }

    @Test
    void objectOverflowFlagsClearAtEndOfVblank() {
        SNES snes = init(new TestRenderer());
        hideAllObjects(snes);
        for (int objectIndex = 0; objectIndex < 33; objectIndex++) {
            writeObject(snes, objectIndex, 0x08, 0x00, 0x00, 0x30);
        }
        snes.ppu.renderMainAndSubScreen();
        assertEquals(0x40, snes.ppu.read(0x3e) & 0xc0);

        snes.ppu.advanceCountersOnly(PPU.H_COUNTER_DOTS * PPU.V_COUNTER_SCANLINES - 1);
        assertEquals(0x40, snes.ppu.read(0x3e) & 0xc0);

        snes.ppu.advanceCountersOnly(1);
        assertEquals(0x00, snes.ppu.read(0x3e) & 0xc0);
    }

    @Test
    void objectRangeLimitStartsAtThePriorityRotationObject() {
        SNES snes = init(new TestRenderer());
        hideAllObjects(snes);
        writeColor(snes, 129, 0x001f);
        snes.ppu.vram.write(0x0000, 0x80);
        for (int objectIndex = 0; objectIndex < 32; objectIndex++) {
            writeObject(snes, objectIndex, 0x08, 0x00, 0x00, 0x30);
        }
        writeObject(snes, 32, 0x00, 0x00, 0x00, 0x30);
        snes.bus.write(0x2102, 0x02);
        snes.bus.write(0x2103, 0x80);
        snes.bus.write(0x212c, 0x10);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][0]);
        assertEquals(0x40, snes.ppu.read(0x3e) & 0xc0);
    }

    @Test
    void objectsAtNegativeTwoHundredFiftySixStillCountTowardTheRangeLimit() {
        SNES snes = init(new TestRenderer());
        hideAllObjects(snes);
        for (int objectIndex = 0; objectIndex < 33; objectIndex++) {
            writeObject(snes, objectIndex, 0x00, 0x00, 0x00, 0x00);
            int highTableAddress = 0x200 + objectIndex / 4;
            int xHighBit = 1 << ((objectIndex % 4) * 2);
            snes.ppu.oamram.write(
                    highTableAddress,
                    snes.ppu.oamram.read(highTableAddress) | xHighBit);
        }

        snes.ppu.renderMainAndSubScreen();

        assertEquals(0x40, snes.ppu.read(0x3e) & 0xc0);
    }

    @Test
    void objectTimeLimitDropsTheThirtyFifthSliverAndSetsStat77() {
        SNES snes = init(new TestRenderer());
        hideAllObjects(snes);
        writeColor(snes, 129, 0x001f);
        snes.ppu.vram.write(0x0000, 0x80);
        snes.bus.write(0x2101, 0x60);
        writeObject(snes, 0, 0x00, 0x00, 0x00, 0x30);
        for (int objectIndex = 1; objectIndex < 18; objectIndex++) {
            writeObject(snes, objectIndex, 0x20, 0x00, 0x00, 0x30);
        }
        snes.bus.write(0x212c, 0x10);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(0, snes.ppu.mainScreen()[0][0]);
        assertEquals(0x80, snes.ppu.read(0x3e) & 0xc0);
    }

    @Test
    void largeObjectsWrapLowTileNibbleHorizontally() {
        SNES snes = init(new TestRenderer());
        hideAllObjects(snes);
        writeColor(snes, 129, 0x001f);
        writeColor(snes, 130, 0x03e0);
        snes.ppu.oamram.write(0x000, 0x00);
        snes.ppu.oamram.write(0x001, 0x00);
        snes.ppu.oamram.write(0x002, 0x0f);
        snes.ppu.oamram.write(0x003, 0x30);
        snes.ppu.oamram.write(0x200, 0x02);
        snes.ppu.vram.write(0x0000, 0x80);
        snes.ppu.vram.write(0x0201, 0x80);
        snes.bus.write(0x212c, 0x10);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][8]);
        assertEquals(PPUUtils.cgramColorToRGBA(0x03e0), snes.ppu.mainScreen()[8][8]);
    }

    @Test
    void objectSizeModeSixRendersSmallObjectsAsSixteenByThirtyTwo() {
        SNES snes = init(new TestRenderer());
        hideAllObjects(snes);
        writeColor(snes, 129, 0x001f);
        snes.bus.write(0x2101, 0xc0);
        writeObject(snes, 0, 0x00, 0x00, 0x00, 0x30);
        snes.ppu.vram.write(0x0600, 0x80);
        snes.bus.write(0x212c, 0x10);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[24][0]);
    }

    @Test
    void objectSizeModeSixRendersLargeObjectsAsThirtyTwoBySixtyFour() {
        SNES snes = init(new TestRenderer());
        hideAllObjects(snes);
        writeColor(snes, 129, 0x001f);
        snes.bus.write(0x2101, 0xc0);
        writeObject(snes, 0, 0x00, 0x00, 0x00, 0x30);
        snes.ppu.oamram.write(0x200, 0x02);
        snes.ppu.vram.write(0x0e00, 0x80);
        snes.bus.write(0x212c, 0x10);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[56][0]);
    }

    @Test
    void objectSizeModeSevenRendersSmallObjectsAsSixteenByThirtyTwo() {
        SNES snes = init(new TestRenderer());
        hideAllObjects(snes);
        writeColor(snes, 129, 0x001f);
        snes.bus.write(0x2101, 0xe0);
        writeObject(snes, 0, 0x00, 0x00, 0x00, 0x30);
        snes.ppu.vram.write(0x0600, 0x80);
        snes.bus.write(0x212c, 0x10);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[24][0]);
    }

    @Test
    void verticallyFlippedRectangularObjectsFlipEachSquareHalf() {
        SNES snes = init(new TestRenderer());
        hideAllObjects(snes);
        writeColor(snes, 129, 0x001f);
        writeColor(snes, 130, 0x03e0);
        snes.bus.write(0x2101, 0xc0);
        writeObject(snes, 0, 0x00, 0x00, 0x00, 0xb0);
        snes.ppu.vram.write(0x020e, 0x80);
        snes.ppu.vram.write(0x060f, 0x80);
        snes.bus.write(0x212c, 0x10);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][0]);
        assertEquals(PPUUtils.cgramColorToRGBA(0x03e0), snes.ppu.mainScreen()[16][0]);
    }

    @Test
    void objectsWrapVerticallyAcrossEightBitCoordinates() {
        SNES snes = init(new TestRenderer());
        hideAllObjects(snes);
        writeColor(snes, 129, 0x001f);
        snes.bus.write(0x2101, 0x60);
        writeObject(snes, 0, 0x00, 0xfa, 0x00, 0x30);
        snes.ppu.vram.write(0x000c, 0x80);
        snes.bus.write(0x212c, 0x10);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][0]);
    }

    @Test
    void rectangularObjectsWrapTheirLowerHalfToTheTop() {
        SNES snes = init(new TestRenderer());
        hideAllObjects(snes);
        writeColor(snes, 129, 0x001f);
        snes.bus.write(0x2101, 0xc0);
        writeObject(snes, 0, 0x00, 0xf0, 0x00, 0x30);
        snes.ppu.vram.write(0x0400, 0x80);
        snes.bus.write(0x212c, 0x10);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][0]);
    }

    @Test
    void updateDrawsComposedScreenToRenderer() {
        TestRenderer renderer = new TestRenderer();
        SNES snes = init(renderer);
        snes.bus.write(0x2100, 0x0f);
        writeColor(snes, 0, 0x7c00);

        snes.ppu.update(1);

        assertEquals(PPUUtils.cgramColorToRGBA(0x7c00), renderer.firstPixel);
        assertEquals((long) Background.BUFFER_SIZE * Background.BUFFER_SIZE, renderer.putPixelCalls);
        assertEquals(1, renderer.drawScreenCalls);
    }

    @Test
    void updateDoesNotDisplaySubscreenWithoutColorMath() {
        TestRenderer renderer = new TestRenderer();
        SNES snes = init(renderer);
        writeColor(snes, 0, 0x0010);
        writeColor(snes, 1, 0x0200);
        snes.bus.write(0x2100, 0x0f);
        snes.bus.write(0x210b, 0x01);
        snes.bus.write(0x212d, 0x01);
        snes.ppu.vram.write(0x0000, 0x00);
        snes.ppu.vram.write(0x0001, 0x00);
        snes.ppu.vram.write(0x2000, 0x80);
        snes.ppu.vram.write(0x2001, 0x00);

        snes.ppu.update(1);

        assertEquals(PPUUtils.cgramColorToRGBA(0x0010), renderer.firstPixel);
    }

    @Test
    void updateAppliesDisplayBrightness() {
        TestRenderer renderer = new TestRenderer();
        SNES snes = init(renderer);
        snes.bus.write(0x2100, 0x07);
        writeColor(snes, 0, 0x001f);

        snes.ppu.update(1);

        assertEquals(PPUUtils.cgramColorToRGBA(0x000e), renderer.firstPixel);
    }

    @Test
    void updateAppliesDisplayBrightnessInFiveBitColorSpace() {
        TestRenderer renderer = new TestRenderer();
        SNES snes = init(renderer);
        snes.bus.write(0x2100, 0x07);
        writeColor(snes, 0, 0x0004);

        snes.ppu.update(1);

        assertEquals(PPUUtils.cgramColorToRGBA(0x0001), renderer.firstPixel);
    }

    @Test
    void updateDrawsBlackDuringForcedBlank() {
        TestRenderer renderer = new TestRenderer();
        SNES snes = init(renderer);
        snes.bus.write(0x2100, 0x8f);
        writeColor(snes, 0, 0x001f);

        snes.ppu.update(1);

        assertEquals(0x000000ff, renderer.firstPixel);
    }

    @Test
    void updateAddsFixedColorMathForEnabledBackground() {
        TestRenderer renderer = new TestRenderer();
        SNES snes = init(renderer);
        setupBg1FirstPixel(snes, 0x0010);
        snes.bus.write(0x2100, 0x0f);
        snes.bus.write(0x2131, 0x01);
        snes.bus.write(0x2132, 0x50);

        snes.ppu.update(1);

        assertEquals(0x848400ff, renderer.firstPixel);
    }

    @Test
    void updateSubtractsFixedColorMathForEnabledBackground() {
        TestRenderer renderer = new TestRenderer();
        SNES snes = init(renderer);
        setupBg1FirstPixel(snes, 0x0210);
        snes.bus.write(0x2100, 0x0f);
        snes.bus.write(0x2131, 0x81);
        snes.bus.write(0x2132, 0x50);

        snes.ppu.update(1);

        assertEquals(0x840000ff, renderer.firstPixel);
    }

    @Test
    void updateUsesFixedColorForTransparentSubscreenColorMath() {
        TestRenderer renderer = new TestRenderer();
        SNES snes = init(renderer);
        setupBg1FirstPixel(snes, 0x0010);
        snes.bus.write(0x2100, 0x0f);
        snes.bus.write(0x2130, 0x02);
        snes.bus.write(0x2131, 0x01);
        snes.bus.write(0x2132, 0x50);

        snes.ppu.update(1);

        assertEquals(0x848400ff, renderer.firstPixel);
    }

    @Test
    void updateDoesNotHalfTransparentSubscreenBackdrop() {
        TestRenderer renderer = new TestRenderer();
        SNES snes = init(renderer);
        setupBg1FirstPixel(snes, 0x0010);
        snes.bus.write(0x2100, 0x0f);
        snes.bus.write(0x2130, 0x02);
        snes.bus.write(0x2131, 0x41);
        snes.bus.write(0x2132, 0x90);

        snes.ppu.update(1);

        assertEquals(PPUUtils.cgramColorToRGBA(0x4010), renderer.firstPixel);
    }

    @Test
    void updateHalvesVisibleSubscreenAgainstMainBackdrop() {
        TestRenderer renderer = new TestRenderer();
        SNES snes = init(renderer);
        writeColor(snes, 0, 0x0010);
        writeColor(snes, 1, 0x0200);
        snes.bus.write(0x2100, 0x0f);
        snes.bus.write(0x210b, 0x01);
        snes.bus.write(0x212d, 0x01);
        snes.bus.write(0x2130, 0x02);
        snes.bus.write(0x2131, 0x60);
        snes.ppu.vram.write(0x0000, 0x00);
        snes.ppu.vram.write(0x0001, 0x00);
        snes.ppu.vram.write(0x2000, 0x80);
        snes.ppu.vram.write(0x2001, 0x00);

        snes.ppu.update(1);

        assertEquals(PPUUtils.cgramColorToRGBA(0x0108), renderer.firstPixel);
    }

    @Test
    void updateHalvesColorMathInFiveBitColorSpace() {
        TestRenderer renderer = new TestRenderer();
        SNES snes = init(renderer);
        setupBg1FirstPixel(snes, 0x0004);
        snes.bus.write(0x2100, 0x0f);
        snes.bus.write(0x2131, 0x41);
        snes.bus.write(0x2132, 0x25);

        snes.ppu.update(1);

        assertEquals(PPUUtils.cgramColorToRGBA(0x0004), renderer.firstPixel);
    }

    @Test
    void updateCanGloballyPreventColorMath() {
        TestRenderer renderer = new TestRenderer();
        SNES snes = init(renderer);
        setupBg1FirstPixel(snes, 0x0010);
        snes.bus.write(0x2100, 0x0f);
        snes.bus.write(0x2130, 0x30);
        snes.bus.write(0x2131, 0x01);
        snes.bus.write(0x2132, 0x50);

        snes.ppu.update(1);

        assertEquals(0x840000ff, renderer.firstPixel);
    }

    @Test
    void updateCanGloballyClipColorToBlackBeforeMath() {
        TestRenderer renderer = new TestRenderer();
        SNES snes = init(renderer);
        setupBg1FirstPixel(snes, 0x0010);
        snes.bus.write(0x2100, 0x0f);
        snes.bus.write(0x2130, 0xc0);
        snes.bus.write(0x2131, 0x01);
        snes.bus.write(0x2132, 0x50);

        snes.ppu.update(1);

        assertEquals(0x008400ff, renderer.firstPixel);
    }

    @Test
    void updateDoesNotHalfColorMathWhenMainScreenIsClippedToBlack() {
        TestRenderer renderer = new TestRenderer();
        SNES snes = init(renderer);
        setupBg1FirstPixel(snes, 0x0010);
        snes.bus.write(0x2100, 0x0f);
        snes.bus.write(0x2130, 0xc0);
        snes.bus.write(0x2131, 0x41);
        snes.bus.write(0x2132, 0x50);

        snes.ppu.update(1);

        assertEquals(PPUUtils.cgramColorToRGBA(0x0200), renderer.firstPixel);
    }

    @Test
    void updateCanClipColorInsideColorWindowBeforeMath() {
        TestRenderer renderer = new TestRenderer();
        SNES snes = init(renderer);
        setupBg1FirstPixel(snes, 0x0010);
        snes.bus.write(0x2100, 0x0f);
        snes.bus.write(0x2125, 0x20);
        snes.bus.write(0x2126, 0x00);
        snes.bus.write(0x2127, 0x00);
        snes.bus.write(0x2130, 0x80);
        snes.bus.write(0x2131, 0x01);
        snes.bus.write(0x2132, 0x50);

        snes.ppu.update(1);

        assertEquals(0x008400ff, renderer.firstPixel);
    }

    @Test
    void updateCanPreventColorMathOutsideColorWindow() {
        TestRenderer renderer = new TestRenderer();
        SNES snes = init(renderer);
        setupBg1FirstPixel(snes, 0x0010);
        snes.bus.write(0x2100, 0x0f);
        snes.bus.write(0x2125, 0x20);
        snes.bus.write(0x2126, 0x10);
        snes.bus.write(0x2127, 0x20);
        snes.bus.write(0x2130, 0x10);
        snes.bus.write(0x2131, 0x01);
        snes.bus.write(0x2132, 0x50);

        snes.ppu.update(1);

        assertEquals(0x840000ff, renderer.firstPixel);
    }

    @Test
    void updateAddsFixedColorMathForEnabledObject() {
        TestRenderer renderer = new TestRenderer();
        SNES snes = init(renderer);
        setupObjFirstPixel(snes, 0x0010, 4);
        snes.bus.write(0x2100, 0x0f);
        snes.bus.write(0x212c, 0x10);
        snes.bus.write(0x2131, 0x10);
        snes.bus.write(0x2132, 0x50);

        snes.ppu.update(1);

        assertEquals(0x848400ff, renderer.firstPixel);
    }

    @Test
    void updateDoesNotApplyObjectColorMathForLowerPalettes() {
        TestRenderer renderer = new TestRenderer();
        SNES snes = init(renderer);
        setupObjFirstPixel(snes, 0x0010);
        snes.bus.write(0x2100, 0x0f);
        snes.bus.write(0x212c, 0x10);
        snes.bus.write(0x2131, 0x10);
        snes.bus.write(0x2132, 0x50);

        snes.ppu.update(1);

        assertEquals(0x840000ff, renderer.firstPixel);
    }

    @Test
    void mainScreenWindowMaskSuppressesObjectPixelsInsideWindow() {
        SNES snes = init(new TestRenderer());
        setupObjFirstPixel(snes, 0x001f);
        snes.bus.write(0x2125, 0x02);
        snes.bus.write(0x2126, 0x00);
        snes.bus.write(0x2127, 0x00);
        snes.bus.write(0x212c, 0x10);
        snes.bus.write(0x212e, 0x10);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(0, snes.ppu.mainScreen()[0][0]);
    }

    private static void writeColor(SNES snes, int colorIndex, int color) {
        int address = colorIndex * 2;
        snes.ppu.cgram.write(address, color);
        snes.ppu.cgram.write(address + 1, color >>> 8);
    }

    private static void setupBg1FirstPixel(SNES snes, int color) {
        writeColor(snes, 1, color);
        snes.bus.write(0x210b, 0x01);
        snes.bus.write(0x212c, 0x01);
        snes.ppu.vram.write(0x0000, 0x00);
        snes.ppu.vram.write(0x0001, 0x00);
        snes.ppu.vram.write(0x2000, 0x80);
        snes.ppu.vram.write(0x2001, 0x00);
    }

    private static void setupObjFirstPixel(SNES snes, int color) {
        setupObjFirstPixel(snes, color, 0);
    }

    private static void setupObjFirstPixel(SNES snes, int color, int palette) {
        setupObjFirstPixel(snes, color, palette, 3);
    }

    private static void setupObjFirstPixel(SNES snes, int color, int palette, int priority) {
        hideAllObjects(snes);
        writeColor(snes, 128 + palette * 16 + 1, color);
        writeObject(
                snes,
                0,
                0x00,
                0x00,
                0x00,
                ((priority & 0x03) << 4) | ((palette & 0x07) << 1));
        snes.ppu.vram.write(0x0000, 0x80);
    }

    private static void setupOverlappingObjectPixels(SNES snes) {
        hideAllObjects(snes);
        writeColor(snes, 129, 0x001f);
        writeColor(snes, 145, 0x03e0);
        writeObject(snes, 0, 0x00, 0x00, 0x00, 0x30);
        writeObject(snes, 1, 0x00, 0x00, 0x01, 0x32);
        snes.ppu.vram.write(0x0000, 0x80);
        snes.ppu.vram.write(0x0020, 0x80);
    }

    private static void hideAllObjects(SNES snes) {
        for (int objectIndex = 0; objectIndex < 128; objectIndex++) {
            writeObject(snes, objectIndex, 0x80, 0x00, 0x00, 0x00);
        }
        for (int highTableAddress = 0x200; highTableAddress < 0x220; highTableAddress++) {
            snes.ppu.oamram.write(highTableAddress, 0x55);
        }
    }

    private static void writeObject(SNES snes, int objectIndex, int x, int y, int tile, int attributes) {
        int address = objectIndex * 4;
        snes.ppu.oamram.write(address, x);
        snes.ppu.oamram.write(address + 1, y);
        snes.ppu.oamram.write(address + 2, tile);
        snes.ppu.oamram.write(address + 3, attributes);
        int highTableAddress = 0x200 + objectIndex / 4;
        int xHighBit = 1 << ((objectIndex % 4) * 2);
        snes.ppu.oamram.write(highTableAddress, snes.ppu.oamram.read(highTableAddress) & ~xHighBit);
    }

    private static void writeMode7Register(SNES snes, int address, int value) {
        snes.bus.write(address, value);
        snes.bus.write(address, value >>> 8);
    }

    private static void writeMode7Map(SNES snes, int tileX, int tileY, int tile) {
        int wordAddress = tileY * 128 + tileX;
        snes.ppu.vram.write(wordAddress * 2, tile);
    }

    private static void writeMode7Pixel(SNES snes, int tile, int pixelX, int pixelY, int color) {
        int wordAddress = tile * 64 + pixelY * 8 + pixelX;
        snes.ppu.vram.write(wordAddress * 2 + 1, color);
    }

    private static void setupMode7Identity(SNES snes) {
        snes.bus.write(0x2105, 0x07);
        snes.bus.write(0x212c, 0x01);
        writeMode7Register(snes, 0x211b, 0x0100);
        writeMode7Register(snes, 0x211c, 0x0000);
        writeMode7Register(snes, 0x211d, 0x0000);
        writeMode7Register(snes, 0x211e, 0x0100);
    }

    private static SNES init(IRenderer renderer) {
        SNES snes = new SNES(renderer);
        snes.bus.mapComponents(snes);
        return snes;
    }

    private static final class TestRenderer implements IRenderer {
        private int firstPixel;
        private long putPixelCalls;
        private int drawScreenCalls;

        @Override
        public void setWindowName(String newWindowName) {
        }

        @Override
        public void drawScreen() {
            drawScreenCalls++;
        }

        @Override
        public void putPixel(int y, int x, int rgba) {
            if (y == 0 && x == 0) {
                firstPixel = rgba;
            }
            putPixelCalls++;
        }

        @Override
        public void createWindow(SNES snes, int maxFPS) {
        }

        @Override
        public void playAudio(short[] samples) {
        }
    }
}
