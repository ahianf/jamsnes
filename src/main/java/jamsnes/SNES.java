package jamsnes;

import jamsnes.apu.APU;
import jamsnes.cartridge.Cartridge;
import jamsnes.cartridge.CartridgeType;
import jamsnes.cpu.CPU;
import jamsnes.input.Joypad;
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
    public final Joypad joypad;
    public final PPU ppu;
    public final APU apu;

    public SNES(IRenderer renderer) {
        this.renderer = renderer;
        this.bus = new MemoryBus();
        this.cartridge = new Cartridge();
        this.wram = new Ram(131_072, Component.WRAM, "WRam");
        this.sram = new Ram(0, Component.SRAM, "SRam");
        this.cpu = new CPU(bus, cartridge.header);
        this.joypad = new Joypad();
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
        cpu.RESB();
        apu.reset();
        if (cartridge.getType() == CartridgeType.AUDIO) {
            apu.loadFromSPC(cartridge);
        }
    }

    public void update() {
        if (cartridge.getType() == CartridgeType.AUDIO) {
            apu.update(0x01);
            return;
        }

        updateAutoJoypadRegisters();
        int cycleCount = cpu.update(0x0c);
        ppu.update(cycleCount);
        apu.update(cycleCount);
    }

    private void updateAutoJoypadRegisters() {
        if ((cpu.internalRegisters()[0x00] & 1) == 0) {
            return;
        }

        for (int controller = 0; controller < 2; controller++) {
            int state = joypad.controllerState(controller);
            cpu.internalRegisters()[0x18 + controller * 2] = state & 0xff;
            cpu.internalRegisters()[0x19 + controller * 2] = (state >>> 8) & 0xff;
        }
        for (int controller = 2; controller < 4; controller++) {
            cpu.internalRegisters()[0x18 + controller * 2] = 0;
            cpu.internalRegisters()[0x19 + controller * 2] = 0;
        }
    }

    public IRenderer getRenderer() {
        return renderer;
    }
}
