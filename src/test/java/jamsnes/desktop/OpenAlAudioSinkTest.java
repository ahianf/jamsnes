package jamsnes.desktop;

import jamsnes.apu.dsp.DSP;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAlAudioSinkTest {
    @Test
    void packsPcm16SamplesAsLittleEndianStereoData() {
        ByteBuffer pcm = OpenAlAudioSink.pcm16StereoLittleEndian(new short[]{
                0x7777,
                0x1234,
                (short) 0xabcd
        }, 1, 2);

        assertEquals(4, pcm.remaining());
        assertEquals(0x34, pcm.get() & 0xff);
        assertEquals(0x12, pcm.get() & 0xff);
        assertEquals(0xcd, pcm.get() & 0xff);
        assertEquals(0xab, pcm.get() & 0xff);
    }

    @Test
    void usesTheNativeDspOutputSampleRate() {
        assertEquals(32_000, DSP.OUTPUT_SAMPLE_RATE_HZ);
        assertEquals(DSP.OUTPUT_SAMPLE_RATE_HZ, OpenAlAudioSink.SAMPLE_RATE);
    }

    @Test
    void queueLimitAllowsLowLatencyHeadroomBeforeBackpressure() {
        assertFalse(OpenAlAudioSink.queueIsFull(OpenAlAudioSink.MAX_QUEUED_BUFFERS - 1));
        assertTrue(OpenAlAudioSink.queueIsFull(OpenAlAudioSink.MAX_QUEUED_BUFFERS));
        assertTrue(OpenAlAudioSink.queueIsFull(OpenAlAudioSink.MAX_QUEUED_BUFFERS + 1));
    }

    @Test
    void emptyWriteOnlyPumpsEventsAndSkipsDeviceInitialization() {
        int[] pumps = {0};
        try (OpenAlAudioSink sink = new OpenAlAudioSink(() -> pumps[0]++)) {
            sink.write(new short[0], 0, 0);
        }

        assertEquals(1, pumps[0]);
    }
}
