package jamsnes.renderer.lwjgl;

import jamsnes.apu.dsp.DSP;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LwjglAudioDeviceTest {
    @Test
    void packsPcm16SamplesAsLittleEndianStereoData() {
        ByteBuffer pcm = LwjglAudioDevice.pcm16StereoLittleEndian(new short[]{
                0x1234,
                (short) 0xabcd
        });

        assertEquals(4, pcm.remaining());
        assertEquals(0x34, pcm.get() & 0xff);
        assertEquals(0x12, pcm.get() & 0xff);
        assertEquals(0xcd, pcm.get() & 0xff);
        assertEquals(0xab, pcm.get() & 0xff);
    }

    @Test
    void usesTheNativeDspOutputSampleRate() {
        assertEquals(32_000, DSP.OUTPUT_SAMPLE_RATE_HZ);
        assertEquals(DSP.OUTPUT_SAMPLE_RATE_HZ, LwjglAudioDevice.SAMPLE_RATE);
    }

    @Test
    void queueLimitAllowsLowLatencyHeadroomBeforeBackpressure() {
        assertFalse(LwjglAudioDevice.queueIsFull(LwjglAudioDevice.MAX_QUEUED_BUFFERS - 1));
        assertTrue(LwjglAudioDevice.queueIsFull(LwjglAudioDevice.MAX_QUEUED_BUFFERS));
        assertTrue(LwjglAudioDevice.queueIsFull(LwjglAudioDevice.MAX_QUEUED_BUFFERS + 1));
    }
}
