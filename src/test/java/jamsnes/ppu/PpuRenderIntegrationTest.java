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
    void updateDrawsComposedScreenToRenderer() {
        TestRenderer renderer = new TestRenderer();
        SNES snes = init(renderer);
        writeColor(snes, 0, 0x7c00);

        snes.ppu.update(1);

        assertEquals(PPUUtils.cgramColorToRGBA(0x7c00), renderer.firstPixel);
        assertEquals((long) Background.BUFFER_SIZE * Background.BUFFER_SIZE, renderer.putPixelCalls);
        assertEquals(1, renderer.drawScreenCalls);
    }

    private static void writeColor(SNES snes, int colorIndex, int color) {
        int address = colorIndex * 2;
        snes.ppu.cgram.write(address, color);
        snes.ppu.cgram.write(address + 1, color >>> 8);
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
