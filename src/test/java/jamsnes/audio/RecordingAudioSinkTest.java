package jamsnes.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RecordingAudioSinkTest {
    @Test
    void copiesWrittenRangeSoProducerCanReuseItsBuffer() {
        RecordingAudioSink sink = new RecordingAudioSink();
        short[] buffer = {1, 2, 3, 4, 5};

        sink.write(buffer, 1, 3);
        buffer[2] = 99;

        assertEquals(1, sink.writeCalls());
        assertEquals(3, sink.samplesWritten());
        assertArrayEquals(new short[]{2, 3, 4}, sink.lastBatch());
    }
}
