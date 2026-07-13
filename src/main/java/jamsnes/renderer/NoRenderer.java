package jamsnes.renderer;

import jamsnes.SNES;
import jamsnes.runtime.EmulatorLoop;

public class NoRenderer implements IRenderer {
    public NoRenderer(int height, int width, int maxFPS) {
    }

    @Override
    public void setWindowName(String newWindowName) {
    }

    @Override
    public void drawScreen() {
    }

    @Override
    public void putPixel(int y, int x, int rgba) {
    }

    @Override
    public void createWindow(SNES snes, int maxFPS) {
        EmulatorLoop.runForUpdates(snes, 1);
    }

    @Override
    public void playAudio(short[] samples) {
    }
}
