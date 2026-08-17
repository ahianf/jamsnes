package jamsnes.audio;

/**
 * Core audio output port. Samples are interleaved stereo (left, right) 16-bit
 * PCM. Calls are synchronous: the producer owns and reuses the supplied array
 * after this method returns, so a sink that needs the data later must copy it
 * into storage it owns.
 */
public interface AudioSink {
    void write(short[] interleavedStereo, int offset, int sampleCount);
}
