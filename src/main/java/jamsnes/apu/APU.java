package jamsnes.apu;

import jamsnes.memory.AMemory;
import jamsnes.models.Component;
import jamsnes.renderer.IRenderer;

import static jamsnes.models.Unsigned.u8;

public class APU extends AMemory {
    private final int[] ports = new int[4];

    public APU(IRenderer renderer) {
    }

    @Override
    public int read(int address) {
        return ports[address];
    }

    @Override
    public void write(int address, int data) {
        ports[address] = u8(data);
    }

    public int[] ports() {
        return ports;
    }

    @Override
    public int getSize() {
        return 0x3;
    }

    @Override
    public String getName() {
        return "APU";
    }

    @Override
    public Component getComponent() {
        return Component.APU;
    }
}
