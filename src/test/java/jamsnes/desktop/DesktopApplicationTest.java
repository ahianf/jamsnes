package jamsnes.desktop;

import jamsnes.input.JoypadButton;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_UP;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_Z;

class DesktopApplicationTest {
    @Test
    void exposesDefaultKeyBindingsForJoypadInput() {
        assertEquals(JoypadButton.B, DesktopApplication.defaultKeyBindings().get(GLFW_KEY_Z));
        assertEquals(JoypadButton.START, DesktopApplication.defaultKeyBindings().get(GLFW_KEY_ENTER));
        assertEquals(JoypadButton.UP, DesktopApplication.defaultKeyBindings().get(GLFW_KEY_UP));
    }

    @Test
    void windowMatchesVisibleSnesFrameAtIntegerScale() {
        assertEquals(256, DesktopApplication.WINDOW_WIDTH);
        assertEquals(224, DesktopApplication.WINDOW_HEIGHT);
        assertEquals(3, DesktopApplication.WINDOW_SCALE);
    }
}
