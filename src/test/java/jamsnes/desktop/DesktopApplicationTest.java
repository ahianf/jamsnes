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
    void windowUsesFourThirdsTelevisionAspect() {
        assertEquals(3, DesktopApplication.WINDOW_SCALE);
        assertEquals(672, DesktopApplication.WINDOW_HEIGHT);
        assertEquals(896, DesktopApplication.WINDOW_WIDTH);
        assertEquals(4.0 / 3.0, (double) DesktopApplication.WINDOW_WIDTH / DesktopApplication.WINDOW_HEIGHT, 0.01);
    }
}
