package jamsnes.desktop;

import jamsnes.video.VideoFrame;
import jamsnes.video.VideoSink;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;

import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Presents completed {@link VideoFrame}s on a GLFW window through OpenGL.
 * Owns one texture sized to the maximum frame surface and reusable upload
 * storage; SNES rendering rules stay in the core.
 */
final class OpenGlVideoSink implements VideoSink {
    private long window = NULL;
    private int texture;
    private ByteBuffer pixelBuffer;
    private final int[] framebufferWidth = new int[1];
    private final int[] framebufferHeight = new int[1];

    void attach(long window) {
        this.window = window;
        GL.createCapabilities();
        texture = GL11.glGenTextures();
        pixelBuffer = BufferUtils.createByteBuffer(VideoFrame.MAX_PIXELS * Integer.BYTES);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, VideoFrame.STRIDE, VideoFrame.MAX_HEIGHT,
                0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixelBuffer);
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
        int[] pixels = frame.pixels();
        pixelBuffer.clear();
        for (int i = 0, count = VideoFrame.STRIDE * visibleHeight; i < count; i++) {
            int pixel = pixels[i];
            pixelBuffer.put((byte) ((pixel >>> 24) & 0xff));
            pixelBuffer.put((byte) ((pixel >>> 16) & 0xff));
            pixelBuffer.put((byte) ((pixel >>> 8) & 0xff));
            pixelBuffer.put((byte) (pixel & 0xff));
        }
        pixelBuffer.flip();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, VideoFrame.STRIDE, visibleHeight,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixelBuffer);
    }

    private void drawTexture(int visibleHeight) {
        glfwGetFramebufferSize(window, framebufferWidth, framebufferHeight);
        GL11.glViewport(0, 0, framebufferWidth[0], framebufferHeight[0]);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
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
}
