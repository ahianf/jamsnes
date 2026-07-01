package jamsnes.ppu;

import jamsnes.SNES;
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
    void modeSevenRendersIdentityMappedBackgroundOne() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 5, 0x001f);
        setupMode7Identity(snes);
        snes.ppu.vram.write(0x0000, 0x01);
        snes.ppu.vram.write(0x4040, 0x05);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][0]);
    }

    @Test
    void modeSevenLargePlayingFieldMakesOutsidePixelsTransparent() {
        SNES snes = init(new TestRenderer());
        setupMode7Identity(snes);
        snes.bus.write(0x211a, 0x80);
        snes.bus.write(0x210d, 0xff);
        snes.bus.write(0x210d, 0xff);
        snes.ppu.vram.write(0x4000, 0x05);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(0, snes.ppu.mainScreen()[0][1]);
    }

    @Test
    void modeSevenLargePlayingFieldCanFillOutsidePixelsWithCharacterZero() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 5, 0x001f);
        setupMode7Identity(snes);
        snes.bus.write(0x211a, 0xc0);
        snes.bus.write(0x210d, 0xff);
        snes.bus.write(0x210d, 0xff);
        snes.ppu.vram.write(0x4000, 0x05);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][1]);
    }

    @Test
    void modeSevenHorizontalMirroringSamplesFromOppositeSide() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 5, 0x001f);
        setupMode7Identity(snes);
        snes.bus.write(0x211a, 0x01);
        snes.ppu.vram.write(0x007f, 0x01);
        snes.ppu.vram.write(0x4047, 0x05);

        snes.ppu.renderMainAndSubScreen();

        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), snes.ppu.mainScreen()[0][0]);
    }

    @Test
    void modeSevenVerticalMirroringSamplesFromOppositeSide() {
        SNES snes = init(new TestRenderer());
        writeColor(snes, 5, 0x001f);
        setupMode7Identity(snes);
        snes.bus.write(0x211a, 0x02);
        snes.ppu.vram.write(0x3f80, 0x01);
        snes.ppu.vram.write(0x4078, 0x05);

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
        snes.ppu.vram.write(0x0000, 0x01);
        snes.ppu.vram.write(0x4040, 0x05);

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
        snes.ppu.vram.write(0x0000, 0x01);
        snes.ppu.vram.write(0x4040, 0x86);

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
    void updateAppliesDisplayBrightness() {
        TestRenderer renderer = new TestRenderer();
        SNES snes = init(renderer);
        snes.bus.write(0x2100, 0x07);
        writeColor(snes, 0, 0x001f);

        snes.ppu.update(1);

        assertEquals(0x770000ff, renderer.firstPixel);
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
    void updateAddsSubscreenColorMathForEnabledBackground() {
        TestRenderer renderer = new TestRenderer();
        SNES snes = init(renderer);
        setupBg1FirstPixel(snes, 0x0010);
        writeColor(snes, 0, 0x0200);
        snes.bus.write(0x2100, 0x0f);
        snes.bus.write(0x2130, 0x02);
        snes.bus.write(0x2131, 0x01);

        snes.ppu.update(1);

        assertEquals(0x848400ff, renderer.firstPixel);
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
    void updateCanClipColorInsideColorWindowBeforeMath() {
        TestRenderer renderer = new TestRenderer();
        SNES snes = init(renderer);
        setupBg1FirstPixel(snes, 0x0010);
        snes.bus.write(0x2100, 0x0f);
        snes.bus.write(0x2125, 0x04);
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
        snes.bus.write(0x2125, 0x04);
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
        setupObjFirstPixel(snes, 0x0010);
        snes.bus.write(0x2100, 0x0f);
        snes.bus.write(0x212c, 0x10);
        snes.bus.write(0x2131, 0x10);
        snes.bus.write(0x2132, 0x50);

        snes.ppu.update(1);

        assertEquals(0x848400ff, renderer.firstPixel);
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
        writeColor(snes, 129, color);
        snes.ppu.oamram.write(0x000, 0x00);
        snes.ppu.oamram.write(0x001, 0x00);
        snes.ppu.oamram.write(0x002, 0x00);
        snes.ppu.oamram.write(0x003, 0x30);
        snes.ppu.vram.write(0x0000, 0x80);
    }

    private static void writeMode7Register(SNES snes, int address, int value) {
        snes.bus.write(address, value >>> 8);
        snes.bus.write(address, value);
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
