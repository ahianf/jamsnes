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
    void emptyAudioBatchOnlyUpdatesCounters() {
        LwjglRenderer renderer = new LwjglRenderer(224, 256, 60);

        renderer.playAudio(new short[0]);

        assertEquals(1, renderer.audioCalls());
        assertEquals(0, renderer.audioSamples());
    }
}
