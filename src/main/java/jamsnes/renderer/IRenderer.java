package jamsnes.renderer;

import jamsnes.SNES;

public interface IRenderer {
    void setWindowName(String newWindowName);

    void drawScreen();

    void putPixel(int y, int x, int rgba);

    void createWindow(SNES snes, int maxFPS);

    void playAudio(short[] samples);
}
