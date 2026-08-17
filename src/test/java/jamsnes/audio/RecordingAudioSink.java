package jamsnes.audio;

import java.util.Arrays;

/**
 * Test-only audio sink. Copies the last written batch so tests can assert on
 * exact samples after the producer has reused its buffer.
 */
public final class RecordingAudioSink implements AudioSink {
    private short[] lastBatch = new short[0];
    private int writeCalls;
    private long samplesWritten;

    @Override
    public void write(short[] interleavedStereo, int offset, int sampleCount) {
        writeCalls++;
        samplesWritten += sampleCount;
        lastBatch = Arrays.copyOfRange(interleavedStereo, offset, offset + sampleCount);
    }

    public int writeCalls() {
        return writeCalls;
    }

    public long samplesWritten() {
        return samplesWritten;
    }

    public short[] lastBatch() {
        return lastBatch;
    }
}
