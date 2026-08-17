package jamsnes.desktop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class OpenGlVideoSinkTest {
    @Test
    void wideFramebufferGetsPillarboxedFourThirdsViewport() {
        int[] viewport = new int[4];

        OpenGlVideoSink.contentViewport(1920, 1080, viewport);

        assertArrayEquals(new int[]{240, 0, 1440, 1080}, viewport);
    }

    @Test
    void tallFramebufferGetsLetterboxedFourThirdsViewport() {
        int[] viewport = new int[4];

        OpenGlVideoSink.contentViewport(800, 1000, viewport);

        assertArrayEquals(new int[]{0, 200, 800, 600}, viewport);
    }

    @Test
    void exactFourThirdsFramebufferFillsCompletely() {
        int[] viewport = new int[4];

        OpenGlVideoSink.contentViewport(896, 672, viewport);

        assertArrayEquals(new int[]{0, 0, 896, 672}, viewport);
    }
}
