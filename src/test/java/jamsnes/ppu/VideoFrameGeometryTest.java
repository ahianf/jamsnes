package jamsnes.ppu;

import jamsnes.SNES;
import jamsnes.audio.RecordingAudioSink;
import jamsnes.video.RecordingVideoSink;
import jamsnes.video.VideoFrame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the native frame geometry: 512 canonical samples per line, active rows
 * matching the emulated display mode, no vertical doubling outside interlace,
 * and horizontal duplication only for low-resolution lines.
 */
class VideoFrameGeometryTest {
    private static final int BLUE = 0x001f;
    private static final int GREEN = 0x03e0;

    @Test
    void normalFrameUses224NativeRows() {
        RecordingVideoSink video = new RecordingVideoSink();
        SNES snes = init(video);
        writeColor(snes, 0, BLUE);
        snes.bus.write(0x2100, 0x0f);

        snes.ppu.renderFrame();

        assertEquals(224, video.visibleHeight());
        assertFalse(video.interlaced());
        assertFalse(video.overscan());
        assertEquals(PPUUtils.cgramColorToRGBA(BLUE), video.pixel(0, 0));
        assertEquals(PPUUtils.cgramColorToRGBA(BLUE), video.pixel(223, 0));
    }

    @Test
    void normalFrameWritesNothingOutsideActiveRows() {
        RecordingVideoSink video = new RecordingVideoSink();
        SNES snes = init(video);
        writeColor(snes, 0, BLUE);
        snes.bus.write(0x2100, 0x0f);

        snes.ppu.renderFrame();

        for (int y = 224; y < VideoFrame.MAX_HEIGHT; y++) {
            for (int x = 0; x < VideoFrame.STRIDE; x++) {
                assertEquals(0, video.pixel(y, x), "unexpected write at " + x + "," + y);
            }
        }
    }

    @Test
    void overscanFrameRepresentsAll239Lines() {
        RecordingVideoSink video = new RecordingVideoSink();
        SNES snes = init(video);
        writeColor(snes, 0, BLUE);
        snes.bus.write(0x2133, 0x04);
        snes.bus.write(0x2100, 0x0f);

        snes.ppu.renderFrame();

        assertEquals(239, video.visibleHeight());
        assertFalse(video.interlaced());
        assertTrue(video.overscan());
        assertEquals(PPUUtils.cgramColorToRGBA(BLUE), video.pixel(0, 0));
        assertEquals(PPUUtils.cgramColorToRGBA(BLUE), video.pixel(238, 0));
    }

    @Test
    void overscanRowsMapScanlinesOneToOne() {
        RecordingVideoSink video = new RecordingVideoSink();
        SNES snes = init(video);
        writeColor(snes, 0, BLUE);
        snes.bus.write(0x2133, 0x04);
        snes.ppu.captureScanlineState(0);
        snes.ppu.captureScanlineState(8);
        snes.bus.write(0x2100, 0x0f);
        snes.ppu.captureScanlineState(9);
        snes.ppu.captureScanlineState(232);
        snes.bus.write(0x2100, 0x8f);
        snes.ppu.captureScanlineState(233);

        snes.ppu.renderFrame();

        int black = 0x000000ff;
        assertEquals(black, video.pixel(0, 0), "scanline 1 was forced blank");
        assertEquals(black, video.pixel(7, 0), "scanline 8 was forced blank");
        assertEquals(PPUUtils.cgramColorToRGBA(BLUE), video.pixel(8, 0), "scanline 9 was visible");
        assertEquals(PPUUtils.cgramColorToRGBA(BLUE), video.pixel(231, 0), "scanline 232 was visible");
        assertEquals(black, video.pixel(232, 0), "scanline 233 was forced blank");
    }

    @Test
    void lowResolutionLinesDuplicateEachPixelIntoAdjacentSamples() {
        RecordingVideoSink video = new RecordingVideoSink();
        SNES snes = init(video);
        setupBg1Line(snes);

        snes.ppu.renderFrame();

        for (int x = 0; x < 2 * PPU.VISIBLE_WIDTH; x += 2) {
            assertEquals(video.pixel(0, x), video.pixel(0, x + 1),
                    "low-resolution samples must duplicate pairwise at x=" + x);
        }
        assertNotEquals(video.pixel(0, 0), video.pixel(0, 2));
    }

    @Test
    void interlacedFrameWeaves448RowsAcrossFields() {
        RecordingVideoSink video = new RecordingVideoSink();
        SNES snes = init(video);
        setupBg1Line(snes);
        snes.bus.write(0x2133, 0x01);

        snes.ppu.renderFrame();

        assertEquals(448, video.visibleHeight());
        assertTrue(video.interlaced());
        assertNotEquals(0, video.pixel(0, 0), "first field fills even rows");
        assertEquals(0, video.pixel(1, 0), "odd rows wait for the second field");

        snes.ppu.advanceCountersOnly(PPU.H_COUNTER_DOTS * (PPU.V_COUNTER_SCANLINES + 1));
        snes.ppu.renderFrame();

        assertNotEquals(0, video.pixel(1, 0), "second field fills odd rows");
    }

    @Test
    void interlacedOverscanFrameUsesAll478Rows() {
        RecordingVideoSink video = new RecordingVideoSink();
        SNES snes = init(video);
        writeColor(snes, 0, BLUE);
        snes.bus.write(0x2133, 0x05);
        snes.bus.write(0x2100, 0x0f);

        snes.ppu.renderFrame();

        assertEquals(478, video.visibleHeight());
        assertTrue(video.interlaced());
        assertTrue(video.overscan());
        assertEquals(PPUUtils.cgramColorToRGBA(BLUE), video.pixel(476, 0));
    }

    private static void setupBg1Line(SNES snes) {
        writeColor(snes, 0, GREEN);
        writeColor(snes, 1, BLUE);
        snes.bus.write(0x2100, 0x0f);
        snes.bus.write(0x210b, 0x01);
        snes.bus.write(0x212c, 0x01);
        snes.ppu.vram.write(0x0000, 0x00);
        snes.ppu.vram.write(0x0001, 0x00);
        snes.ppu.vram.write(0x2002, 0x80);
    }

    private static void writeColor(SNES snes, int colorIndex, int color) {
        int address = colorIndex * 2;
        snes.ppu.cgram.write(address, color);
        snes.ppu.cgram.write(address + 1, color >>> 8);
    }

    private static SNES init(RecordingVideoSink video) {
        SNES snes = new SNES(video, new RecordingAudioSink());
        snes.bus.mapComponents(snes);
        return snes;
    }
}
