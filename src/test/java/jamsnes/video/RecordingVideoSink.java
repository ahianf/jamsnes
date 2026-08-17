package jamsnes.video;

import java.util.zip.CRC32;

/**
 * Test-only video sink. Copies the last presented frame and its geometry so
 * tests can assert on exact pixels after the producer has reused the frame.
 */
public final class RecordingVideoSink implements VideoSink {
    private final int[] pixels = new int[VideoFrame.MAX_PIXELS];
    private int presentCalls;
    private int visibleHeight;
    private boolean interlaced;
    private boolean overscan;
    private long frameNumber;

    @Override
    public void present(VideoFrame frame) {
        presentCalls++;
        visibleHeight = frame.visibleHeight();
        interlaced = frame.interlaced();
        overscan = frame.overscan();
        frameNumber = frame.frameNumber();
        System.arraycopy(frame.pixels(), 0, pixels, 0, VideoFrame.STRIDE * visibleHeight);
    }

    public int presentCalls() {
        return presentCalls;
    }

    public int visibleHeight() {
        return visibleHeight;
    }

    public boolean interlaced() {
        return interlaced;
    }

    public boolean overscan() {
        return overscan;
    }

    public long frameNumber() {
        return frameNumber;
    }

    public int pixel(int y, int x) {
        if (y < 0 || y >= VideoFrame.MAX_HEIGHT || x < 0 || x >= VideoFrame.STRIDE) {
            throw new IndexOutOfBoundsException("Pixel out of frame bounds: " + x + "," + y);
        }
        return pixels[y * VideoFrame.STRIDE + x];
    }

    public boolean hasNonUniformFrame() {
        int count = VideoFrame.STRIDE * visibleHeight;
        if (count == 0) {
            return false;
        }
        int firstPixel = pixels[0];
        for (int i = 1; i < count; i++) {
            if (pixels[i] != firstPixel) {
                return true;
            }
        }
        return false;
    }

    /**
     * CRC32 over the visible region only: {@code 512 * visibleHeight} RGBA8888
     * pixels in row-major order, each emitted as four bytes R, G, B, A.
     */
    public long frameCrc32() {
        CRC32 crc = new CRC32();
        for (int i = 0, count = VideoFrame.STRIDE * visibleHeight; i < count; i++) {
            int pixel = pixels[i];
            crc.update((pixel >>> 24) & 0xff);
            crc.update((pixel >>> 16) & 0xff);
            crc.update((pixel >>> 8) & 0xff);
            crc.update(pixel & 0xff);
        }
        return crc.getValue();
    }
}
