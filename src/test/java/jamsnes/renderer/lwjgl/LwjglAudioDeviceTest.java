package jamsnes.renderer.lwjgl;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void usesSnesAudioSampleRateFromOriginalRenderer() {
        assertEquals(32_040, LwjglAudioDevice.SAMPLE_RATE);
    }
}
