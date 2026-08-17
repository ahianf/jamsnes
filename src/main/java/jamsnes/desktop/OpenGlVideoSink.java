package jamsnes.desktop;

import jamsnes.video.VideoFrame;
import jamsnes.video.VideoSink;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Presents completed {@link VideoFrame}s on a GLFW window through OpenGL.
 * Owns one texture sized to the maximum frame surface and reusable native
 * upload storage; SNES rendering rules stay in the core. The emulated frame
 * is drawn into a centered 4:3 content viewport, letterboxed or pillarboxed
 * inside the actual framebuffer.
 */
final class OpenGlVideoSink implements VideoSink {
    private long window = NULL;
    private int texture;
    private IntBuffer pixelBuffer;
    private final int[] framebufferWidth = new int[1];
    private final int[] framebufferHeight = new int[1];
    private final int[] viewport = new int[4];

    void attach(long window) {
        this.window = window;
        GL.createCapabilities();
        texture = GL11.glGenTextures();
        pixelBuffer = BufferUtils.createIntBuffer(VideoFrame.MAX_PIXELS);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, VideoFrame.STRIDE, VideoFrame.MAX_HEIGHT,
                0, GL11.GL_RGBA, GL12.GL_UNSIGNED_INT_8_8_8_8, pixelBuffer);
    }

    @Override
    public void present(VideoFrame frame) {
        if (window == NULL) {
            return;
        }
        uploadFrame(frame);
        drawTexture(frame.visibleHeight());
        glfwSwapBuffers(window);
        glfwPollEvents();
    }

    private void uploadFrame(VideoFrame frame) {
        int visibleHeight = frame.visibleHeight();
        pixelBuffer.clear();
        pixelBuffer.put(frame.pixels(), 0, VideoFrame.STRIDE * visibleHeight);
        pixelBuffer.flip();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, VideoFrame.STRIDE, visibleHeight,
                GL11.GL_RGBA, GL12.GL_UNSIGNED_INT_8_8_8_8, pixelBuffer);
    }

    private void drawTexture(int visibleHeight) {
        glfwGetFramebufferSize(window, framebufferWidth, framebufferHeight);
        GL11.glViewport(0, 0, framebufferWidth[0], framebufferHeight[0]);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        contentViewport(framebufferWidth[0], framebufferHeight[0], viewport);
        GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        float maxU = 1.0f;
        float maxV = (float) visibleHeight / VideoFrame.MAX_HEIGHT;
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

    /**
     * Computes the largest centered 4:3 viewport that fits the framebuffer,
     * writing {x, y, width, height} into {@code out}.
     */
    static void contentViewport(int framebufferWidth, int framebufferHeight, int[] out) {
        int width = framebufferWidth;
        int height = framebufferHeight;
        if (width * 3 >= height * 4) {
            width = height * 4 / 3;
        } else {
            height = width * 3 / 4;
        }
        out[0] = (framebufferWidth - width) / 2;
        out[1] = (framebufferHeight - height) / 2;
        out[2] = width;
        out[3] = height;
    }
}
