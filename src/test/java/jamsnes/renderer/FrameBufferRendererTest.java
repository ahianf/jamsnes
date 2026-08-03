package jamsnes.renderer;

import jamsnes.SNES;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameBufferRendererTest {
    @Test
    void storesPixelsInRowMajorFrameBuffer() {
        FrameBufferRenderer renderer = new FrameBufferRenderer(2, 3, 60);

        renderer.putPixel(0, 0, 0x11223344);
        renderer.putPixel(1, 2, 0xaabbccdd);
        renderer.drawScreen();

        assertArrayEquals(new int[]{
                0x11223344, 0, 0,
                0, 0, 0xaabbccdd
        }, renderer.frameBuffer());
        assertEquals(0x11223344, renderer.pixel(0, 0));
        assertEquals(0xaabbccdd, renderer.pixel(1, 2));
        assertEquals(1, renderer.drawScreenCalls());
    }

    @Test
    void exposesStableFrameBufferSnapshotForGoldenChecks() {
        FrameBufferRenderer renderer = new FrameBufferRenderer(1, 2, 60);
        renderer.putPixel(0, 1, 0x12345678);

        int[] snapshot = renderer.frameBufferCopy();
        renderer.putPixel(0, 1, 0x87654321);

        assertArrayEquals(new int[]{0, 0x12345678}, snapshot);
        assertNotSame(renderer.frameBuffer(), snapshot);
    }

    @Test
    void exposesFrameBufferCrc32ForGoldenChecks() {
        FrameBufferRenderer renderer = new FrameBufferRenderer(1, 2, 60);
        renderer.putPixel(0, 0, 0x11223344);
        renderer.putPixel(0, 1, 0xaabbccdd);
        long firstHash = renderer.frameBufferCrc32();

        assertEquals(firstHash, renderer.frameBufferCrc32());

        renderer.putPixel(0, 1, 0xaabbccde);
        assertNotEquals(firstHash, renderer.frameBufferCrc32());
    }

    @Test
    void reportsWhetherTheFrameContainsMoreThanOneColor() {
        FrameBufferRenderer renderer = new FrameBufferRenderer(2, 2, 60);

        assertFalse(renderer.hasNonUniformFrame());

        renderer.putPixel(1, 1, 0x12345678);

        assertTrue(renderer.hasNonUniformFrame());
    }

    @Test
    void tracksWindowAudioAndDimensionState() {
        FrameBufferRenderer renderer = new FrameBufferRenderer(4, 5, 120);
        SNES snes = new SNES(renderer);

        renderer.setWindowName("JamSNES");
        renderer.createWindow(snes, 60);
        renderer.playAudio(new short[]{1, 2, 3});

        assertEquals(4, renderer.height());
        assertEquals(5, renderer.width());
        assertEquals(120, renderer.maxFPS());
        assertEquals("JamSNES", renderer.windowName());
        assertEquals(snes, renderer.snes());
        assertEquals(1, renderer.audioCalls());
        assertEquals(3, renderer.audioSamples());
    }

    @Test
    void rejectsInvalidDimensionsAndOutOfBoundsPixels() {
        assertThrows(IllegalArgumentException.class, () -> new FrameBufferRenderer(0, 1, 60));
        FrameBufferRenderer renderer = new FrameBufferRenderer(2, 2, 60);

        assertThrows(IndexOutOfBoundsException.class, () -> renderer.putPixel(2, 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> renderer.putPixel(0, 2, 0));
    }
}
