package jamsnes.renderer;

import jamsnes.SNES;

import java.util.Arrays;

public class FrameBufferRenderer implements IRenderer {
    private final int height;
    private final int width;
    private final int maxFPS;
    private final int[] frameBuffer;
    private SNES snes;
    private String windowName = "";
    private int drawScreenCalls;
    private int audioCalls;
    private int audioSamples;

    public FrameBufferRenderer(int height, int width, int maxFPS) {
        if (height <= 0 || width <= 0) {
            throw new IllegalArgumentException("Frame buffer dimensions must be positive");
        }
        this.height = height;
        this.width = width;
        this.maxFPS = maxFPS;
        this.frameBuffer = new int[height * width];
    }

    @Override
    public void setWindowName(String newWindowName) {
        windowName = newWindowName == null ? "" : newWindowName;
    }

    @Override
    public void drawScreen() {
        drawScreenCalls++;
    }

    @Override
    public void putPixel(int y, int x, int rgba) {
        if (y < 0 || y >= height || x < 0 || x >= width) {
            throw new IndexOutOfBoundsException("Pixel out of frame buffer bounds: " + x + "," + y);
        }
        frameBuffer[y * width + x] = rgba;
    }

    @Override
    public void createWindow(SNES snes, int maxFPS) {
        this.snes = snes;
    }

    @Override
    public void playAudio(short[] samples) {
        audioCalls++;
        audioSamples += samples.length;
    }

    public int height() {
        return height;
    }

    public int width() {
        return width;
    }

    public int maxFPS() {
        return maxFPS;
    }

    public String windowName() {
        return windowName;
    }

    public SNES snes() {
        return snes;
    }

    public int drawScreenCalls() {
        return drawScreenCalls;
    }

    public int audioCalls() {
        return audioCalls;
    }

    public int audioSamples() {
        return audioSamples;
    }

    public int pixel(int y, int x) {
        return frameBuffer[y * width + x];
    }

    public int[] frameBuffer() {
        return frameBuffer;
    }

    public int[] frameBufferCopy() {
        return Arrays.copyOf(frameBuffer, frameBuffer.length);
    }
}
