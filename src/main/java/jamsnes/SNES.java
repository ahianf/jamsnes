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
    private static final int NMITIMEN_H_IRQ_ENABLE = 0x10;
    private static final int NMITIMEN_V_IRQ_ENABLE = 0x20;

    private final IRenderer renderer;
    public final MemoryBus bus;
    public final Cartridge cartridge;
    public final Ram wram;
    public final Ram sram;
    public final CPU cpu;
    public final Joypad joypad;
    public final PPU ppu;
    public final APU apu;
    private int lastTimerIrqHCounter = -1;
    private int lastTimerIrqVCounter = -1;
    private boolean hdmaInitializedThisFrame;
    private boolean wasInVBlank;

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

        int hdmaInitCycles = initializeHdmaAtFrameStart();
        if (hdmaInitCycles > 0) {
            ppu.advanceCountersOnly(hdmaInitCycles);
            apu.update(hdmaInitCycles);
        }

        int startHCounter = ppu.hCounter();
        int startVCounter = ppu.vCounter();
        int cycleCount = cpu.update(0x0c);
        boolean entersHBlank = entersHBlank(startHCounter, startVCounter, cycleCount);
        boolean startsNewFrame = startsNewFrame(startHCounter, startVCounter, cycleCount);
        ppu.update(cycleCount);
        if (startsNewFrame) {
            hdmaInitializedThisFrame = false;
        }
        int hdmaCycles = 0;
        if (entersHBlank && !ppu.isInVBlank()) {
            hdmaCycles = cpu.runHDMALine();
            if (hdmaCycles > 0) {
                ppu.advanceCountersOnly(hdmaCycles);
            }
        }
        updateVideoStatusRegisters();
        updateTimerIrq();
        requestFrameNmi();
        apu.update(cycleCount);
        if (hdmaCycles > 0) {
            apu.update(hdmaCycles);
        }
    }

    private int initializeHdmaAtFrameStart() {
        if (hdmaInitializedThisFrame || ppu.vCounter() != 0) {
            return 0;
        }
        hdmaInitializedThisFrame = true;
        return cpu.initializeHDMA();
    }

    private boolean entersHBlank(int hCounter, int vCounter, int cycles) {
        if (cycles <= 0 || vCounter >= PPU.V_BLANK_START_SCANLINE || hCounter >= PPU.H_BLANK_START_DOT) {
            return false;
        }
        return hCounter + cycles >= PPU.H_BLANK_START_DOT;
    }

    private boolean startsNewFrame(int hCounter, int vCounter, int cycles) {
        if (cycles <= 0) {
            return false;
        }
        int dots = vCounter * PPU.H_COUNTER_DOTS + hCounter + cycles;
        return dots >= PPU.V_COUNTER_SCANLINES * PPU.H_COUNTER_DOTS;
    }

    private void requestFrameNmi() {
        boolean inVBlank = ppu.isInVBlank();
        if (inVBlank && !wasInVBlank) {
            updateAutoJoypadRegisters();
        }
        if (inVBlank && !wasInVBlank && (cpu.internalRegisters()[0x00] & 0x80) != 0) {
            cpu.requestNMI();
        }
        wasInVBlank = inVBlank;
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

    void updateVideoStatusRegisters() {
        int value = 0;
        if (ppu.isInVBlank()) {
            value |= 0x80;
        }
        if (ppu.isInHBlank()) {
            value |= 0x40;
        }
        cpu.internalRegisters()[0x12] = value;
    }

    void updateTimerIrq() {
        int nmitimen = cpu.internalRegisters()[0x00];
        boolean hTimerEnabled = (nmitimen & NMITIMEN_H_IRQ_ENABLE) != 0;
        boolean vTimerEnabled = (nmitimen & NMITIMEN_V_IRQ_ENABLE) != 0;
        if (!hTimerEnabled && !vTimerEnabled) {
            return;
        }

        int hCounter = ppu.hCounter();
        int vCounter = ppu.vCounter();
        boolean matched = timerMatches(hTimerEnabled, vTimerEnabled, hCounter, vCounter);
        if (!matched || (hCounter == lastTimerIrqHCounter && vCounter == lastTimerIrqVCounter)) {
            return;
        }

        lastTimerIrqHCounter = hCounter;
        lastTimerIrqVCounter = vCounter;
        cpu.requestIRQ();
    }

    private boolean timerMatches(boolean hTimerEnabled, boolean vTimerEnabled, int hCounter, int vCounter) {
        int hTarget = cpu.internalRegisters()[0x07] | ((cpu.internalRegisters()[0x08] & 1) << 8);
        int vTarget = cpu.internalRegisters()[0x09] | ((cpu.internalRegisters()[0x0a] & 1) << 8);

        if (hTimerEnabled && vTimerEnabled) {
            return hCounter == hTarget && vCounter == vTarget;
        }
        if (hTimerEnabled) {
            return hCounter == hTarget;
        }
        return hCounter == 0 && vCounter == vTarget;
    }

    public IRenderer getRenderer() {
        return renderer;
    }
}
