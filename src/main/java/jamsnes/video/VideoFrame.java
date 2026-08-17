package jamsnes.video;

/**
 * Reusable video output surface produced by the PPU.
 *
 * <p>The surface is a flat RGBA8888 raster with a fixed {@link #STRIDE} of 512
 * samples per row. Low-resolution scanlines duplicate each pixel into two
 * adjacent samples; high-resolution scanlines use all 512 samples. Only the
 * first {@link #visibleHeight()} rows carry the current frame.
 *
 * <p>The producer owns and reuses this frame. A {@link VideoSink} may read
 * {@link #pixels()} only during {@link VideoSink#present(VideoFrame)} and must
 * copy anything it needs afterwards.
 */
public final class VideoFrame {
    public static final int STRIDE = 512;
    public static final int MAX_HEIGHT = 478;
    public static final int MAX_PIXELS = STRIDE * MAX_HEIGHT;

    private final int[] rgba8888 = new int[MAX_PIXELS];
    private int visibleHeight = 224;
    private boolean interlaced;
    private boolean overscan;
    private long frameNumber;

    /** Documented zero-copy view. A sink may read it only during present(). */
    public int[] pixels() {
        return rgba8888;
    }

    public int stride() {
        return STRIDE;
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

    public void setGeometry(int visibleHeight, boolean interlaced, boolean overscan) {
        if (visibleHeight <= 0 || visibleHeight > MAX_HEIGHT) {
            throw new IllegalArgumentException("visibleHeight out of range: " + visibleHeight);
        }
        this.visibleHeight = visibleHeight;
        this.interlaced = interlaced;
        this.overscan = overscan;
    }

    public void setFrameNumber(long frameNumber) {
        this.frameNumber = frameNumber;
    }
}
