package jamsnes.renderer.lwjgl;

import jamsnes.SNES;
import jamsnes.input.JoypadButton;
import jamsnes.input.KeyboardJoypadMapper;
import jamsnes.renderer.FrameBufferRenderer;
import jamsnes.runtime.EmulatorLoop;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
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
import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSetErrorCallback;
import static org.lwjgl.glfw.GLFW.glfwSetKeyCallback;
import static org.lwjgl.glfw.GLFW.glfwSetWindowTitle;
import static org.lwjgl.glfw.GLFW.glfwShowWindow;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;
import static org.lwjgl.glfw.GLFW.glfwSwapInterval;
import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.glfw.GLFW.glfwWindowShouldClose;
import static org.lwjgl.system.MemoryUtil.NULL;

public class LwjglRenderer extends FrameBufferRenderer {
    public static final int DEFAULT_DISPLAY_WIDTH = 256;
    public static final int DEFAULT_DISPLAY_HEIGHT = 224;
    public static final int DEFAULT_WINDOW_SCALE = 3;

    private final int displayHeight;
    private final int displayWidth;
    private final int windowScale;
    private final Map<Integer, JoypadButton> keyBindings;
    private final LwjglAudioDevice audioDevice = new LwjglAudioDevice();
    private long window;
    private int texture;
    private ByteBuffer pixelBuffer;

    public LwjglRenderer(int height, int width, int maxFPS) {
        this(height, width, maxFPS, DEFAULT_DISPLAY_HEIGHT, DEFAULT_DISPLAY_WIDTH, DEFAULT_WINDOW_SCALE,
                defaultKeyBindings());
    }

    public LwjglRenderer(int height, int width, int maxFPS, int windowScale, Map<Integer, JoypadButton> keyBindings) {
        this(height, width, maxFPS, height, width, windowScale, keyBindings);
    }

    public LwjglRenderer(
            int height,
            int width,
            int maxFPS,
            int displayHeight,
            int displayWidth,
            int windowScale,
            Map<Integer, JoypadButton> keyBindings) {
        super(height, width, maxFPS);
        if (displayHeight <= 0 || displayHeight > height || displayWidth <= 0 || displayWidth > width) {
            throw new IllegalArgumentException("Display dimensions must be positive and within the frame buffer");
        }
        if (windowScale <= 0) {
            throw new IllegalArgumentException("windowScale must be positive");
        }
        this.displayHeight = displayHeight;
        this.displayWidth = displayWidth;
        this.windowScale = windowScale;
        this.keyBindings = Map.copyOf(keyBindings);
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

    public int displayHeight() {
        return displayHeight;
    }

    public int displayWidth() {
        return displayWidth;
    }

    public int windowScale() {
        return windowScale;
    }

    @Override
    public void setWindowName(String newWindowName) {
        super.setWindowName(newWindowName);
        if (window != NULL) {
            glfwSetWindowTitle(window, windowTitle());
        }
    }

    @Override
    public void createWindow(SNES snes, int maxFPS) {
        super.createWindow(snes, maxFPS);
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new IllegalStateException("Could not initialize GLFW");
        }

        try {
            createGlfwWindow(snes);
            initOpenGl();
            EmulatorLoop.runWhile(snes, () -> !glfwWindowShouldClose(window));
        } finally {
            destroyWindow();
        }
    }

    @Override
    public void drawScreen() {
        super.drawScreen();
        if (window == NULL || texture == 0) {
            return;
        }
        uploadFrameBuffer();
        drawTexture();
        glfwSwapBuffers(window);
        glfwPollEvents();
    }

    @Override
    public void playAudio(short[] samples) {
        super.playAudio(samples);
        audioDevice.queueSamples(samples);
    }

    private void createGlfwWindow(SNES snes) {
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 2);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
        window = glfwCreateWindow(displayWidth * windowScale, displayHeight * windowScale, windowTitle(), NULL, NULL);
        if (window == NULL) {
            throw new IllegalStateException("Could not create GLFW window");
        }
        KeyboardJoypadMapper mapper = new KeyboardJoypadMapper(snes.joypad, 0, keyBindings);
        glfwSetKeyCallback(window, (handle, key, scancode, action, mods) -> {
            if (action == GLFW_PRESS || action == GLFW_RELEASE) {
                mapper.handleKey(key, action == GLFW_PRESS);
            }
        });
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
    }

    private void initOpenGl() {
        GL.createCapabilities();
        texture = GL11.glGenTextures();
        pixelBuffer = BufferUtils.createByteBuffer(width() * height() * Integer.BYTES);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width(), height(), 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixelBuffer);
    }

    private void uploadFrameBuffer() {
        pixelBuffer.clear();
        for (int pixel : frameBuffer()) {
            pixelBuffer.put((byte) ((pixel >>> 24) & 0xff));
            pixelBuffer.put((byte) ((pixel >>> 16) & 0xff));
            pixelBuffer.put((byte) ((pixel >>> 8) & 0xff));
            pixelBuffer.put((byte) (pixel & 0xff));
        }
        pixelBuffer.flip();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width(), height(),
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixelBuffer);
    }

    private void drawTexture() {
        int[] frameWidth = new int[1];
        int[] frameHeight = new int[1];
        glfwGetFramebufferSize(window, frameWidth, frameHeight);
        GL11.glViewport(0, 0, frameWidth[0], frameHeight[0]);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        float maxU = (float) displayWidth / width();
        float maxV = (float) displayHeight / height();
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0f, 0.0f);
        GL11.glVertex2f(-1.0f, 1.0f);
        GL11.glTexCoord2f(maxU, 0.0f);
        GL11.glVertex2f(1.0f, 1.0f);
        GL11.glTexCoord2f(maxU, maxV);
        GL11.glVertex2f(1.0f, -1.0f);
        GL11.glTexCoord2f(0.0f, maxV);
        GL11.glVertex2f(-1.0f, -1.0f);
        GL11.glEnd();
    }

    private void destroyWindow() {
        if (window != NULL) {
            glfwFreeCallbacks(window);
            glfwDestroyWindow(window);
            window = NULL;
        }
        audioDevice.close();
        glfwTerminate();
        GLFWErrorCallback callback = glfwSetErrorCallback(null);
        if (callback != null) {
            callback.free();
        }
    }

    private String windowTitle() {
        return windowName().isBlank() ? "JamSNES" : windowName() + " - JamSNES";
    }
}
