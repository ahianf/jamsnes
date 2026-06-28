package jamsnes;

import jamsnes.apu.APU;
import jamsnes.cartridge.Cartridge;
import jamsnes.cpu.CPU;
import jamsnes.memory.MemoryBus;
import jamsnes.models.Component;
import jamsnes.ppu.PPU;
import jamsnes.ram.Ram;
import jamsnes.renderer.IRenderer;

public class SNES {
    private final IRenderer renderer;
    public final MemoryBus bus;
    public final Cartridge cartridge;
    public final Ram wram;
    public final Ram sram;
    public final CPU cpu;
    public final PPU ppu;
    public final APU apu;

    public SNES(IRenderer renderer) {
        this.renderer = renderer;
        this.bus = new MemoryBus();
        this.cartridge = new Cartridge();
        this.wram = new Ram(131_072, Component.WRAM, "WRam");
        this.sram = new Ram(0, Component.SRAM, "SRam");
        this.cpu = new CPU(bus, cartridge.header);
        this.ppu = new PPU(renderer);
        this.apu = new APU(renderer);
    }

    public SNES(String romPath, IRenderer renderer) {
        this(renderer);
        loadRom(romPath);
    }

    public void loadRom(String path) {
        cartridge.loadRom(path);
        sram.setSize(cartridge.header.sramSize);
        bus.mapComponents(this);
    }

    public IRenderer getRenderer() {
        return renderer;
    }
}
