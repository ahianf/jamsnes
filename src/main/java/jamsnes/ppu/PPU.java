package jamsnes.ppu;

import jamsnes.memory.AMemory;
import jamsnes.models.Component;
import jamsnes.ram.Ram;
import jamsnes.renderer.IRenderer;

import static jamsnes.models.Unsigned.u8;

public class PPU extends AMemory {
    public static final int VRAM_SIZE = 65_536;
    public static final int CGRAM_SIZE = 512;
    public static final int OAMRAM_SIZE = 544;

    public final Ram vram = new Ram(VRAM_SIZE, Component.VRAM, "VRAM");
    public final Ram oamram = new Ram(OAMRAM_SIZE, Component.OAMRAM, "OAMRAM");
    public final Ram cgram = new Ram(CGRAM_SIZE, Component.CGRAM, "CGRAM");
    private final int[] registers = new int[0x40];

    public PPU(IRenderer renderer) {
    }

    @Override
    public int read(int address) {
        return registers[address];
    }

    @Override
    public void write(int address, int data) {
        registers[address] = u8(data);
    }

    public int[] registers() {
        return registers;
    }

    @Override
    public int getSize() {
        return 0x3f;
    }

    @Override
    public String getName() {
        return "PPU";
    }

    @Override
    public Component getComponent() {
        return Component.PPU;
    }
}
