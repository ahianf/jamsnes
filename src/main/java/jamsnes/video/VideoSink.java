package jamsnes.video;

/**
 * Core video output port. Calls are synchronous: the producer owns and reuses
 * the supplied frame after this method returns, so a sink that needs the data
 * later must copy it into storage it owns.
 */
public interface VideoSink {
    void present(VideoFrame frame);
}
