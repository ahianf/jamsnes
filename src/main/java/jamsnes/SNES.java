package jamsnes;

import jamsnes.renderer.IRenderer;

public class SNES {
    private final IRenderer renderer;

    public SNES(IRenderer renderer) {
        this.renderer = renderer;
    }

    public IRenderer getRenderer() {
        return renderer;
    }
}
