package jamsnes.desktop;

import jamsnes.SNES;
import jamsnes.input.JoypadButton;
import jamsnes.input.KeyboardJoypadMapper;
import jamsnes.runtime.EmulatorLoop;
import org.lwjgl.glfw.GLFWErrorCallback;

import java.util.Map;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR;
import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_D;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_E;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_J;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_K;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_Q;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_UP;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_W;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_X;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_Z;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;
import static org.lwjgl.glfw.GLFW.GLFW_RESIZABLE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwDefaultWindowHints;
import static org.lwjgl.glfw.GLFW.glfwDestroyWindow;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSetErrorCallback;
import static org.lwjgl.glfw.GLFW.glfwSetKeyCallback;
import static org.lwjgl.glfw.GLFW.glfwShowWindow;
import static org.lwjgl.glfw.GLFW.glfwSwapInterval;
import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.glfw.GLFW.glfwWindowShouldClose;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * The LWJGL desktop frontend. Owns the GLFW window, input callbacks, the
 * emulation run loop, and cleanup; the core only sees the narrow
 * {@code VideoSink} and {@code AudioSink} ports.
 */
public final class DesktopApplication {
    static final int WINDOW_WIDTH = 256;
    static final int WINDOW_HEIGHT = 224;
    static final int WINDOW_SCALE = 3;
    private static final String WINDOW_TITLE = "JamSNES";

    private long window = NULL;

    public static void run(String romPath) {
        new DesktopApplication().start(romPath);
    }

    private void start(String romPath) {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new IllegalStateException("Could not initialize GLFW");
        }

        try (OpenAlAudioSink audioSink = new OpenAlAudioSink(this::pumpEvents)) {
            createWindow();
            OpenGlVideoSink videoSink = new OpenGlVideoSink();
            videoSink.attach(window);
            SNES snes = new SNES(romPath, videoSink, audioSink);
            bindKeyboard(snes);
            EmulatorLoop.runWhile(snes, () -> !glfwWindowShouldClose(window));
        } finally {
            destroyWindow();
        }
    }

    public static Map<Integer, JoypadButton> defaultKeyBindings() {
        return Map.ofEntries(
                Map.entry(GLFW_KEY_Z, JoypadButton.B),
                Map.entry(GLFW_KEY_X, JoypadButton.A),
                Map.entry(GLFW_KEY_A, JoypadButton.Y),
                Map.entry(GLFW_KEY_S, JoypadButton.X),
                Map.entry(GLFW_KEY_RIGHT_SHIFT, JoypadButton.SELECT),
                Map.entry(GLFW_KEY_ENTER, JoypadButton.START),
                Map.entry(GLFW_KEY_Q, JoypadButton.L),
                Map.entry(GLFW_KEY_E, JoypadButton.R),
                Map.entry(GLFW_KEY_UP, JoypadButton.UP),
                Map.entry(GLFW_KEY_W, JoypadButton.UP),
                Map.entry(GLFW_KEY_DOWN, JoypadButton.DOWN),
                Map.entry(GLFW_KEY_LEFT, JoypadButton.LEFT),
                Map.entry(GLFW_KEY_D, JoypadButton.RIGHT),
                Map.entry(GLFW_KEY_RIGHT, JoypadButton.RIGHT),
                Map.entry(GLFW_KEY_SPACE, JoypadButton.START),
                Map.entry(GLFW_KEY_J, JoypadButton.B),
                Map.entry(GLFW_KEY_K, JoypadButton.A)
        );
    }

    private void createWindow() {
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 2);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
        window = glfwCreateWindow(WINDOW_WIDTH * WINDOW_SCALE, WINDOW_HEIGHT * WINDOW_SCALE,
                WINDOW_TITLE, NULL, NULL);
        if (window == NULL) {
            throw new IllegalStateException("Could not create GLFW window");
        }
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
    }

    private void bindKeyboard(SNES snes) {
        KeyboardJoypadMapper mapper = new KeyboardJoypadMapper(snes.joypad, 0, defaultKeyBindings());
        glfwSetKeyCallback(window, (handle, key, scancode, action, mods) -> {
            if (action == GLFW_PRESS || action == GLFW_RELEASE) {
                mapper.handleKey(key, action == GLFW_PRESS);
            }
        });
    }

    private void pumpEvents() {
        if (window != NULL) {
            glfwPollEvents();
        }
    }

    private void destroyWindow() {
        if (window != NULL) {
            glfwFreeCallbacks(window);
            glfwDestroyWindow(window);
            window = NULL;
        }
        glfwTerminate();
        GLFWErrorCallback callback = glfwSetErrorCallback(null);
        if (callback != null) {
            callback.free();
        }
    }
}
