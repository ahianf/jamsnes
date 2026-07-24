package jamsnes.renderer.lwjgl;

import jamsnes.input.JoypadButton;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_UP;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_Z;

class LwjglRendererTest {
    @Test
    void defaultRendererKeepsFullFrameBufferButDisplaysVisibleSnesFrame() {
        LwjglRenderer renderer = new LwjglRenderer(1024, 1024, 60);

        assertEquals(1024, renderer.height());
        assertEquals(1024, renderer.width());
        assertEquals(448, renderer.displayHeight());
        assertEquals(512, renderer.displayWidth());
        assertEquals(256, renderer.windowWidth());
        assertEquals(224, renderer.windowHeight());
        assertEquals(3, renderer.windowScale());
    }

    @Test
    void exposesDefaultKeyBindingsForJoypadInput() {
        assertEquals(JoypadButton.B, LwjglRenderer.defaultKeyBindings().get(GLFW_KEY_Z));
        assertEquals(JoypadButton.START, LwjglRenderer.defaultKeyBindings().get(GLFW_KEY_ENTER));
        assertEquals(JoypadButton.UP, LwjglRenderer.defaultKeyBindings().get(GLFW_KEY_UP));
    }

    @Test
    void rejectsInvalidScale() {
        assertThrows(IllegalArgumentException.class, () -> new LwjglRenderer(224, 256, 60, 0,
                LwjglRenderer.defaultKeyBindings()));
    }

    @Test
    void rejectsDisplayDimensionsOutsideFrameBuffer() {
        assertThrows(IllegalArgumentException.class, () -> new LwjglRenderer(224, 256, 60, 225, 256, 1,
                LwjglRenderer.defaultKeyBindings()));
        assertThrows(IllegalArgumentException.class, () -> new LwjglRenderer(224, 256, 60, 224, 257, 1,
                LwjglRenderer.defaultKeyBindings()));
    }

    @Test
    void emptyAudioBatchOnlyUpdatesCounters() {
        LwjglRenderer renderer = new LwjglRenderer(224, 256, 60);

        renderer.playAudio(new short[0]);

        assertEquals(1, renderer.audioCalls());
        assertEquals(0, renderer.audioSamples());
    }
}
