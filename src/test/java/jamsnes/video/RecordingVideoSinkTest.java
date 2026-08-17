package jamsnes.video;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordingVideoSinkTest {
    @Test
    void copiesPresentedPixelsAndGeometry() {
        VideoFrame frame = new VideoFrame();
        frame.pixels()[0] = 0x11223344;
        frame.pixels()[2 * VideoFrame.STRIDE + 5] = 0xaabbccdd;
        frame.setGeometry(448, true, false);
        frame.setFrameNumber(7);
        RecordingVideoSink sink = new RecordingVideoSink();

        sink.present(frame);
        frame.pixels()[0] = 0;

        assertEquals(1, sink.presentCalls());
        assertEquals(448, sink.visibleHeight());
        assertTrue(sink.interlaced());
        assertFalse(sink.overscan());
        assertEquals(7, sink.frameNumber());
        assertEquals(0x11223344, sink.pixel(0, 0));
        assertEquals(0xaabbccdd, sink.pixel(2, 5));
        assertTrue(sink.hasNonUniformFrame());
        assertThrows(IndexOutOfBoundsException.class, () -> sink.pixel(0, VideoFrame.STRIDE));
    }

    @Test
    void videoFrameRejectsInvalidGeometry() {
        VideoFrame frame = new VideoFrame();

        assertThrows(IllegalArgumentException.class, () -> frame.setGeometry(0, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> frame.setGeometry(VideoFrame.MAX_HEIGHT + 1, false, false));
        assertEquals(512, frame.stride());
        assertEquals(512 * 478, VideoFrame.MAX_PIXELS);
    }
}
