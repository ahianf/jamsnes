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
    static final int APU_CLOCK_HZ = 1_024_000;
    static final int PPU_DOT_CLOCK_HZ = 5_369_318;
    static final int MASTER_CLOCK_HZ = PPU_DOT_CLOCK_HZ * 4;
    private static final int APU_CYCLES_PER_AUDIO_UPDATE = 32;
    private static final int NMITIMEN_AUTO_JOYPAD_ENABLE = 0x01;
    private static final int NMITIMEN_H_IRQ_ENABLE = 0x10;
    private static final int NMITIMEN_V_IRQ_ENABLE = 0x20;
    private static final int MASTER_CLOCKS_PER_PPU_DOT = 4;
    private static final int AUTO_JOYPAD_READ_DOTS = 4224 / MASTER_CLOCKS_PER_PPU_DOT;

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
    private int autoJoypadReadDotsElapsed;
    private int autoJoypadBitsRead;
    private boolean autoJoypadLatchReleased;
    private int ppuMasterClockRemainder;
    private long apuClockRemainder;

    public SNES(IRenderer renderer) {
        this.renderer = renderer;
        this.bus = new MemoryBus();
        this.cartridge = new Cartridge();
        this.wram = new Ram(131_072, Component.WRAM, "WRam");
        this.wramPort = new WramPort(wram);
        this.sram = new MirroredRam(0, Component.SRAM, "SRam");
        this.cpu = new CPU(bus, cartridge.header);
        this.joypad = new Joypad(bus);
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
        autoJoypadReadDotsElapsed = 0;
        autoJoypadBitsRead = 0;
        autoJoypadLatchReleased = true;
        ppuMasterClockRemainder = 0;
        apuClockRemainder = 0;
    }

    public void update() {
        if (cartridge.getType() == CartridgeType.AUDIO) {
            apu.update(APU_CYCLES_PER_AUDIO_UPDATE);
            return;
        }

        int timerStartHCounter = ppu.hCounter();
        int timerStartVCounter = ppu.vCounter();
        boolean timerStartSecondField = ppu.isSecondField();
        long startFrameCounter = ppu.frameCounter();
        int hdmaInitMasterClocks = initializeHdmaAtFrameStart();
        int hdmaInitDots = advancePpuForMasterClocks(hdmaInitMasterClocks);
        if (hdmaInitMasterClocks > 0) {
            advanceApuForMasterClocks(hdmaInitMasterClocks);
        }

        int startHCounter = ppu.hCounter();
        int startVCounter = ppu.vCounter();
        cpu.update(0x0c);
        int cpuMasterClocks = cpu.elapsedMasterClocks();
        int cpuDots = advancePpuForMasterClocks(cpuMasterClocks);
        boolean entersHBlank = entersHBlank(startHCounter, startVCounter, cpuDots);
        int hdmaMasterClocks = 0;
        int hdmaDots = 0;
        if (entersHBlank && !ppu.isInVBlank()) {
            ppu.captureScanlineState(startVCounter);
            hdmaMasterClocks = cpu.runHDMALine();
            hdmaDots = advancePpuForMasterClocks(hdmaMasterClocks);
        }
        if (ppu.frameCounter() != startFrameCounter) {
            hdmaInitializedThisFrame = false;
        }
        boolean enteredVBlank = requestFrameNmi();
        if (enteredVBlank) {
            ppu.renderFrame();
        }
        updateAutoJoypadBusy(enteredVBlank, timerStartHCounter, timerStartVCounter,
                hdmaInitDots + cpuDots + hdmaDots);
        updateVideoStatusRegisters();
        updateTimerIrq(timerStartHCounter, timerStartVCounter, timerStartSecondField,
                hdmaInitDots + cpuDots + hdmaDots);
        advanceApuForMasterClocks(cpuMasterClocks);
        if (hdmaMasterClocks > 0) {
            advanceApuForMasterClocks(hdmaMasterClocks);
        }
    }

    private int advancePpuForMasterClocks(int masterClocks) {
        if (masterClocks <= 0) {
            return 0;
        }
        int totalMasterClocks = ppuMasterClockRemainder + masterClocks;
        int dots = totalMasterClocks / MASTER_CLOCKS_PER_PPU_DOT;
        ppuMasterClockRemainder = totalMasterClocks % MASTER_CLOCKS_PER_PPU_DOT;
        ppu.advanceCountersOnly(dots);
        return dots;
    }

    private void advanceApuForMasterClocks(int masterClocks) {
        if (masterClocks <= 0) {
            return;
        }
        long scaledCycles = apuClockRemainder + (long) masterClocks * APU_CLOCK_HZ;
        int apuCycles = (int) (scaledCycles / MASTER_CLOCK_HZ);
        apuClockRemainder = scaledCycles % MASTER_CLOCK_HZ;
        if (apuCycles > 0) {
            apu.update(apuCycles);
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

        joypad.beginAutoRead();
        autoJoypadReadDotsElapsed = 0;
        autoJoypadBitsRead = 0;
        autoJoypadLatchReleased = false;
        for (int controller = 0; controller < 4; controller++) {
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
            autoJoypadReadCyclesRemaining = AUTO_JOYPAD_READ_DOTS;
            advanceAutoJoypadRead(cyclesAfterVBlankStart(startHCounter, startVCounter, cycles));
            return;
        }
        if (!autoJoypadEnabled()) {
            if (!autoJoypadLatchReleased) {
                joypad.releaseAutoReadLatch();
                autoJoypadLatchReleased = true;
            }
            autoJoypadReadCyclesRemaining = 0;
            return;
        }
        if (autoJoypadReadCyclesRemaining > 0) {
            advanceAutoJoypadRead(Math.max(0, cycles));
        }
    }

    private void advanceAutoJoypadRead(int dots) {
        int elapsed = Math.min(Math.max(0, dots), autoJoypadReadCyclesRemaining);
        autoJoypadReadCyclesRemaining -= elapsed;
        autoJoypadReadDotsElapsed += elapsed;

        if (!autoJoypadLatchReleased && autoJoypadReadDotsElapsed >= 32) {
            joypad.releaseAutoReadLatch();
            autoJoypadLatchReleased = true;
        }
        while (autoJoypadBitsRead < 16
                && autoJoypadReadDotsElapsed >= 96 + autoJoypadBitsRead * 64) {
            int[] values = joypad.clockAutoReadBit();
            for (int controller = 0; controller < values.length; controller++) {
                int lowAddress = 0x18 + controller * 2;
                int report = cpu.internalRegisters()[lowAddress]
                        | (cpu.internalRegisters()[lowAddress + 1] << 8);
                report = ((report << 1) | values[controller]) & 0xffff;
                cpu.internalRegisters()[lowAddress] = report & 0xff;
                cpu.internalRegisters()[lowAddress + 1] = report >>> 8;
            }
            autoJoypadBitsRead++;
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
        updateTimerIrq(ppu.hCounter(), ppu.vCounter(), ppu.isSecondField(), 0);
    }

    private void updateTimerIrq(int startHCounter, int startVCounter, boolean startSecondField, int cycles) {
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

        int[] matchedPosition = timerMatchPosition(
                hTimerEnabled, vTimerEnabled, startHCounter, startVCounter, startSecondField, cycles);
        if (matchedPosition == null) {
            clearLastTimerIrqPosition();
            return;
        }
        if (matchedPosition[0] == lastTimerIrqHCounter && matchedPosition[1] == lastTimerIrqVCounter) {
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

    private int[] timerMatchPosition(boolean hTimerEnabled, boolean vTimerEnabled,
                                     int startHCounter, int startVCounter, boolean startSecondField, int cycles) {
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
        if (vTimerEnabled && vTarget > PPU.V_COUNTER_SCANLINES) {
            return null;
        }
        int targetH = hTimerEnabled ? hTarget : 0;
        int hCounter = startHCounter;
        int vCounter = startVCounter;
        boolean secondField = startSecondField;
        long elapsed = 0;
        while (elapsed <= cycles) {
            int scanlineDots = ppu.scanlineDotsAt(vCounter, secondField);
            boolean verticalMatch = !vTimerEnabled || vCounter == vTarget;
            if (verticalMatch && targetH < scanlineDots) {
                long candidate = elapsed + targetH - hCounter;
                if (candidate > 0 && candidate <= cycles) {
                    return new int[]{targetH, vCounter};
                }
            }

            int toNextScanline = scanlineDots - hCounter;
            if (elapsed + toNextScanline > cycles) {
                return null;
            }
            elapsed += toNextScanline;
            hCounter = 0;
            vCounter++;
            if (vCounter >= ppu.scanlinesInField(secondField)) {
                vCounter = 0;
                secondField = !secondField;
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
