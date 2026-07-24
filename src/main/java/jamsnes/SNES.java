package jamsnes;

import jamsnes.apu.APU;
import jamsnes.cartridge.Cartridge;
import jamsnes.cartridge.CartridgeType;
import jamsnes.cpu.CPU;
import jamsnes.input.Joypad;
import jamsnes.memory.MemoryBus;
import jamsnes.memory.WramPort;
import jamsnes.models.Component;
import jamsnes.ram.MirroredRam;
import jamsnes.ppu.PPU;
import jamsnes.ram.Ram;
import jamsnes.renderer.IRenderer;

public class SNES {
    private static final int NMITIMEN_AUTO_JOYPAD_ENABLE = 0x01;
    private static final int NMITIMEN_H_IRQ_ENABLE = 0x10;
    private static final int NMITIMEN_V_IRQ_ENABLE = 0x20;
    private static final int AUTO_JOYPAD_READ_CYCLES = 4224;

    private final IRenderer renderer;
    public final MemoryBus bus;
    public final Cartridge cartridge;
    public final Ram wram;
    public final WramPort wramPort;
    public final Ram sram;
    public final CPU cpu;
    public final Joypad joypad;
    public final PPU ppu;
    public final APU apu;
    private int lastTimerIrqHCounter = -1;
    private int lastTimerIrqVCounter = -1;
    private int lastTimerEnableGeneration;
    private boolean hdmaInitializedThisFrame;
    private boolean wasInVBlank;
    private boolean wasNmiEnabled;
    private int autoJoypadReadCyclesRemaining;

    public SNES(IRenderer renderer) {
        this.renderer = renderer;
        this.bus = new MemoryBus();
        this.cartridge = new Cartridge();
        this.wram = new Ram(131_072, Component.WRAM, "WRam");
        this.wramPort = new WramPort(wram);
        this.sram = new MirroredRam(0, Component.SRAM, "SRam");
        this.cpu = new CPU(bus, cartridge.header);
        this.joypad = new Joypad();
        this.ppu = new PPU(renderer);
        this.cpu.setIoPortLatchListener(this.ppu::latchCounters);
        this.ppu.setExternalCounterLatchEnabled(() -> (cpu.internalRegisters()[0x01] & 0x80) != 0);
        this.apu = new APU(renderer);
    }

    public SNES(String romPath, IRenderer renderer) {
        this(renderer);
        loadRom(romPath);
    }

    public void loadRom(String path) {
        cartridge.loadRom(path);
        sram.setSize(cartridge.header.sramSize);
        sram.clear();
        wramPort.resetAddress();
        bus.mapComponents(this);
        cpu.RESB();
        apu.reset();
        ppu.resetMemoryState();
        ppu.resetRegisterState();
        ppu.resetTimingState();
        resetRuntimeTimingState();
        if (cartridge.getType() == CartridgeType.AUDIO) {
            apu.loadFromSPC(cartridge);
        }
    }

    private void resetRuntimeTimingState() {
        lastTimerIrqHCounter = -1;
        lastTimerIrqVCounter = -1;
        lastTimerEnableGeneration = cpu.timerEnableGeneration();
        hdmaInitializedThisFrame = false;
        wasInVBlank = ppu.isInVBlank();
        wasNmiEnabled = nmiEnabled();
        autoJoypadReadCyclesRemaining = 0;
    }

    public void update() {
        if (cartridge.getType() == CartridgeType.AUDIO) {
            apu.update(0x01);
            return;
        }

        int timerStartHCounter = ppu.hCounter();
        int timerStartVCounter = ppu.vCounter();
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
        ppu.advanceCountersOnly(cycleCount);
        if (startsNewFrame) {
            hdmaInitializedThisFrame = false;
        }
        int hdmaCycles = 0;
        if (entersHBlank && !ppu.isInVBlank()) {
            ppu.captureScanlineState(startVCounter);
            hdmaCycles = cpu.runHDMALine();
            if (hdmaCycles > 0) {
                ppu.advanceCountersOnly(hdmaCycles);
            }
        }
        boolean enteredVBlank = requestFrameNmi();
        if (enteredVBlank) {
            ppu.renderFrame();
        }
        updateAutoJoypadBusy(enteredVBlank, timerStartHCounter, timerStartVCounter,
                hdmaInitCycles + cycleCount + hdmaCycles);
        updateVideoStatusRegisters();
        updateTimerIrq(timerStartHCounter, timerStartVCounter, hdmaInitCycles + cycleCount + hdmaCycles);
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
        if (cycles <= 0 || vCounter >= ppu.vBlankStartScanline() || hCounter >= PPU.H_BLANK_START_DOT) {
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

    private boolean requestFrameNmi() {
        boolean inVBlank = ppu.isInVBlank();
        boolean nmiEnabled = nmiEnabled();
        boolean enteredVBlank = inVBlank && !wasInVBlank;
        boolean enabledDuringVBlank = inVBlank && nmiEnabled && !wasNmiEnabled;
        if (enteredVBlank) {
            updateAutoJoypadRegisters();
        }
        if ((enteredVBlank || enabledDuringVBlank) && nmiEnabled) {
            cpu.requestNMI();
        }
        wasInVBlank = inVBlank;
        wasNmiEnabled = nmiEnabled;
        return enteredVBlank;
    }

    private void updateAutoJoypadRegisters() {
        if (!autoJoypadEnabled()) {
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
        if (autoJoypadReadCyclesRemaining > 0) {
            value |= 0x01;
        }
        if (ppu.isInVBlank()) {
            value |= 0x80;
        }
        if (ppu.isInHBlank()) {
            value |= 0x40;
        }
        cpu.internalRegisters()[0x12] = value;
    }

    private void updateAutoJoypadBusy(boolean enteredVBlank, int startHCounter, int startVCounter, int cycles) {
        if (enteredVBlank && autoJoypadEnabled()) {
            autoJoypadReadCyclesRemaining = AUTO_JOYPAD_READ_CYCLES;
            autoJoypadReadCyclesRemaining = Math.max(0,
                    autoJoypadReadCyclesRemaining - cyclesAfterVBlankStart(startHCounter, startVCounter, cycles));
            return;
        }
        if (autoJoypadReadCyclesRemaining > 0) {
            autoJoypadReadCyclesRemaining = Math.max(0, autoJoypadReadCyclesRemaining - Math.max(0, cycles));
        }
    }

    private int cyclesAfterVBlankStart(int startHCounter, int startVCounter, int cycles) {
        if (cycles <= 0) {
            return 0;
        }

        int frameDots = PPU.H_COUNTER_DOTS * PPU.V_COUNTER_SCANLINES;
        int vBlankStartDot = PPU.H_COUNTER_DOTS * ppu.vBlankStartScanline();
        int startDot = startVCounter * PPU.H_COUNTER_DOTS + startHCounter;
        int endDot = startDot + cycles;
        if (startDot < vBlankStartDot && endDot >= vBlankStartDot) {
            return endDot - vBlankStartDot;
        }
        if (startDot >= vBlankStartDot && startDot < frameDots) {
            return cycles;
        }
        return 0;
    }

    private boolean autoJoypadEnabled() {
        return (cpu.internalRegisters()[0x00] & NMITIMEN_AUTO_JOYPAD_ENABLE) != 0;
    }

    private boolean nmiEnabled() {
        return (cpu.internalRegisters()[0x00] & 0x80) != 0;
    }

    void updateTimerIrq() {
        updateTimerIrq(ppu.hCounter(), ppu.vCounter(), 0);
    }

    private void updateTimerIrq(int startHCounter, int startVCounter, int cycles) {
        int nmitimen = cpu.internalRegisters()[0x00];
        if (lastTimerEnableGeneration != cpu.timerEnableGeneration()) {
            clearLastTimerIrqPosition();
            lastTimerEnableGeneration = cpu.timerEnableGeneration();
        }
        boolean hTimerEnabled = (nmitimen & NMITIMEN_H_IRQ_ENABLE) != 0;
        boolean vTimerEnabled = (nmitimen & NMITIMEN_V_IRQ_ENABLE) != 0;
        if (!hTimerEnabled && !vTimerEnabled) {
            clearLastTimerIrqPosition();
            return;
        }

        int[] matchedPosition = timerMatchPosition(hTimerEnabled, vTimerEnabled, startHCounter, startVCounter, cycles);
        if (matchedPosition == null
                || (matchedPosition[0] == lastTimerIrqHCounter && matchedPosition[1] == lastTimerIrqVCounter)) {
            return;
        }

        lastTimerIrqHCounter = matchedPosition[0];
        lastTimerIrqVCounter = matchedPosition[1];
        cpu.requestIRQ();
    }

    private void clearLastTimerIrqPosition() {
        lastTimerIrqHCounter = -1;
        lastTimerIrqVCounter = -1;
    }

    private int[] timerMatchPosition(boolean hTimerEnabled, boolean vTimerEnabled, int startHCounter, int startVCounter, int cycles) {
        if (cycles <= 0) {
            int hCounter = ppu.hCounter();
            int vCounter = ppu.vCounter();
            return timerMatches(hTimerEnabled, vTimerEnabled, hCounter, vCounter)
                    ? new int[]{hCounter, vCounter}
                    : null;
        }

        int hTarget = cpu.internalRegisters()[0x07] | ((cpu.internalRegisters()[0x08] & 1) << 8);
        int vTarget = cpu.internalRegisters()[0x09] | ((cpu.internalRegisters()[0x0a] & 1) << 8);
        if (hTimerEnabled && hTarget >= PPU.H_COUNTER_DOTS) {
            return null;
        }
        if (vTimerEnabled && vTarget >= PPU.V_COUNTER_SCANLINES) {
            return null;
        }
        long start = (long) startVCounter * PPU.H_COUNTER_DOTS + startHCounter;
        long end = start + cycles;
        if (hTimerEnabled && !vTimerEnabled) {
            long firstLine = start / PPU.H_COUNTER_DOTS;
            long lastLine = end / PPU.H_COUNTER_DOTS;
            for (long line = firstLine; line <= lastLine; line++) {
                long candidate = line * PPU.H_COUNTER_DOTS + hTarget;
                if (start < candidate && candidate <= end) {
                    return new int[]{hTarget, (int) (line % PPU.V_COUNTER_SCANLINES)};
                }
            }
            return null;
        }

        int targetH = hTimerEnabled ? hTarget : 0;
        int targetV = vTarget;
        long target = (long) targetV * PPU.H_COUNTER_DOTS + targetH;
        long frameDots = (long) PPU.V_COUNTER_SCANLINES * PPU.H_COUNTER_DOTS;
        for (long candidate = target; candidate <= end; candidate += frameDots) {
            if (start < candidate) {
                return new int[]{targetH, targetV};
            }
        }
        return null;
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
