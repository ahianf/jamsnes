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
import jamsnes.audio.AudioSink;
import jamsnes.ram.Ram;
import jamsnes.video.VideoSink;

public class SNES {
    static final int APU_CLOCK_HZ = 1_024_000;
    static final int PPU_DOT_CLOCK_HZ = 5_369_318;
    static final int MASTER_CLOCK_HZ = PPU_DOT_CLOCK_HZ * 4;
    private static final int APU_CYCLES_PER_AUDIO_UPDATE = 32;
    private static final int NMITIMEN_AUTO_JOYPAD_ENABLE = 0x01;
    private static final int NMITIMEN_H_IRQ_ENABLE = 0x10;
    private static final int NMITIMEN_V_IRQ_ENABLE = 0x20;
    private static final int H_TIMER_MAX_DOT = 339;
    private static final int MASTER_CLOCKS_PER_PPU_DOT = 4;
    static final int HDMA_INITIALIZE_DOT = 16 / MASTER_CLOCKS_PER_PPU_DOT;
    static final int DRAM_REFRESH_START_DOT = 536 / MASTER_CLOCKS_PER_PPU_DOT;
    static final int HDMA_START_DOT = 1104 / MASTER_CLOCKS_PER_PPU_DOT;
    private static final int AUTO_JOYPAD_READ_DOTS = 4224 / MASTER_CLOCKS_PER_PPU_DOT;

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
    private long hdmaInitializationFrame = -1;
    private boolean wasInVBlank;
    private boolean wasNmiEnabled;
    private int autoJoypadReadCyclesRemaining;
    private int autoJoypadReadDotsElapsed;
    private int autoJoypadBitsRead;
    private boolean autoJoypadLatchReleased;
    private int ppuMasterClockRemainder;
    private long apuClockRemainder;
    private long dramRefreshFrame = -1;
    private int dramRefreshScanline = -1;
    private long hdmaFrame = -1;
    private int hdmaScanline = -1;
    private int eventStallMasterClocks;
    private int eventStallDots;
    private boolean eagerAdvanceEnabled;
    private int eagerMasterClocksConverted;
    private int eagerDotsProduced;
    private int eagerDotsAdvanced;

    public SNES(VideoSink videoSink, AudioSink audioSink) {
        this.bus = new MemoryBus();
        this.cartridge = new Cartridge();
        this.wram = new Ram(131_072, Component.WRAM, "WRam");
        this.wramPort = new WramPort(wram);
        this.sram = new MirroredRam(0, Component.SRAM, "SRam");
        this.cpu = new CPU(bus, cartridge.header);
        this.joypad = new Joypad(bus);
        this.ppu = new PPU(videoSink);
        this.cpu.setIoPortLatchListener(this.ppu::latchCounters);
        this.cpu.setNmiControlListener(this::updateNmiControl);
        this.ppu.setExternalCounterLatchEnabled(() -> (cpu.internalRegisters()[0x01] & 0x80) != 0);
        this.cpu.setBusAccessListener(this::onCpuBusAccess);
        this.apu = new APU(audioSink);
    }

    /**
     * Advances the PPU counters to the timestamp of a CPU bus access, so reads
     * such as the $2137 counter latch observe access-time positions instead of
     * the position at the start of the CPU slice. Advancement parks exactly at
     * pending scanline-event dots (HDMA initialization, DRAM refresh, HDMA
     * start); those events keep running in the aggregate scheduler, which also
     * advances whatever this eager pass could not. CPU internal cycles still
     * elapse through the aggregate remainder.
     */
    private void onCpuBusAccess(int masterClocks) {
        if (!eagerAdvanceEnabled) {
            return;
        }
        eagerMasterClocksConverted += masterClocks;
        int dots = ppuDotsForMasterClocks(masterClocks);
        eagerDotsProduced += dots;
        while (dots > 0) {
            int step = Math.min(dots, dotsToNextPendingEvent());
            if (step <= 0) {
                return;
            }
            ppu.advanceCountersOnly(step);
            eagerDotsAdvanced += step;
            dots -= step;
        }
    }

    /**
     * Dots the counters may advance before reaching the next pending scanline
     * event or the end of the current line; zero when parked on an event dot.
     */
    private int dotsToNextPendingEvent() {
        int currentHCounter = ppu.hCounter();
        int limit = ppu.scanlineDotsAt(ppu.vCounter(), ppu.isSecondField()) - currentHCounter;
        if (isHdmaInitializationPending()) {
            if (currentHCounter == HDMA_INITIALIZE_DOT) {
                return 0;
            }
            if (currentHCounter < HDMA_INITIALIZE_DOT) {
                limit = Math.min(limit, HDMA_INITIALIZE_DOT - currentHCounter);
            }
        }
        if (isDramRefreshPending()) {
            if (currentHCounter == DRAM_REFRESH_START_DOT) {
                return 0;
            }
            if (currentHCounter < DRAM_REFRESH_START_DOT) {
                limit = Math.min(limit, DRAM_REFRESH_START_DOT - currentHCounter);
            }
        }
        if (isHdmaPending()) {
            if (currentHCounter == HDMA_START_DOT) {
                return 0;
            }
            if (currentHCounter < HDMA_START_DOT) {
                limit = Math.min(limit, HDMA_START_DOT - currentHCounter);
            }
        }
        return limit;
    }

    public SNES(String romPath, VideoSink videoSink, AudioSink audioSink) {
        this(videoSink, audioSink);
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
        hdmaInitializationFrame = -1;
        wasInVBlank = ppu.isInVBlank();
        wasNmiEnabled = nmiEnabled();
        autoJoypadReadCyclesRemaining = 0;
        autoJoypadReadDotsElapsed = 0;
        autoJoypadBitsRead = 0;
        autoJoypadLatchReleased = true;
        ppuMasterClockRemainder = 0;
        apuClockRemainder = 0;
        dramRefreshFrame = -1;
        dramRefreshScanline = -1;
        hdmaFrame = -1;
        hdmaScanline = -1;
    }

    public void update() {
        if (cartridge.getType() == CartridgeType.AUDIO) {
            apu.update(APU_CYCLES_PER_AUDIO_UPDATE);
            return;
        }

        int timerStartHCounter = ppu.hCounter();
        int timerStartVCounter = ppu.vCounter();
        boolean timerStartSecondField = ppu.isSecondField();
        eagerMasterClocksConverted = 0;
        eagerDotsProduced = 0;
        eagerDotsAdvanced = 0;
        eagerAdvanceEnabled = true;
        cpu.update(0x0c);
        eagerAdvanceEnabled = false;
        int cpuMasterClocks = cpu.elapsedMasterClocks();
        int cpuDots = eagerDotsProduced
                + ppuDotsForMasterClocks(cpuMasterClocks - eagerMasterClocksConverted);
        advanceCpuDotsThroughScanlineEvents(cpuDots - eagerDotsAdvanced);
        boolean enteredVBlank = requestFrameNmi();
        if (enteredVBlank) {
            ppu.renderFrame();
        }
        updateAutoJoypadBusy(enteredVBlank, timerStartHCounter, timerStartVCounter,
                cpuDots + eventStallDots);
        updateVideoStatusRegisters();
        updateTimerIrq(timerStartHCounter, timerStartVCounter, timerStartSecondField,
                cpuDots + eventStallDots);
        advanceApuForMasterClocks(cpuMasterClocks);
        if (eventStallMasterClocks > 0) {
            advanceApuForMasterClocks(eventStallMasterClocks);
        }
    }

    private void advanceCpuDotsThroughScanlineEvents(int cpuDots) {
        int remainingCpuDots = cpuDots;
        int stallMasterClocks = 0;
        int stallDots = 0;
        markEventsPassedOutsideScheduler();

        while (remainingCpuDots > 0 || hasDueScanlineEvent()) {
            if (isHdmaInitializationPending() && ppu.hCounter() == HDMA_INITIALIZE_DOT) {
                markHdmaInitializationHandled();
                int hdmaInitializationMasterClocks = cpu.initializeHDMA();
                stallMasterClocks += hdmaInitializationMasterClocks;
                stallDots += advancePpuForMasterClocks(hdmaInitializationMasterClocks);
                continue;
            }
            if (isDramRefreshPending() && ppu.hCounter() == DRAM_REFRESH_START_DOT) {
                markDramRefreshHandled();
                int refreshMasterClocks = cpu.runDramRefresh();
                stallMasterClocks += refreshMasterClocks;
                stallDots += advancePpuForMasterClocks(refreshMasterClocks);
                continue;
            }
            if (isHdmaPending() && ppu.hCounter() == HDMA_START_DOT) {
                markHdmaHandled();
                ppu.captureScanlineState(ppu.vCounter());
                int hdmaMasterClocks = cpu.runHDMALine();
                stallMasterClocks += hdmaMasterClocks;
                stallDots += advancePpuForMasterClocks(hdmaMasterClocks);
                continue;
            }

            int currentHCounter = ppu.hCounter();
            int dotsToLineEnd = ppu.scanlineDotsAt(ppu.vCounter(), ppu.isSecondField()) - currentHCounter;
            int dotsToEvent = dotsToLineEnd;
            if (isHdmaInitializationPending() && currentHCounter < HDMA_INITIALIZE_DOT) {
                dotsToEvent = Math.min(dotsToEvent, HDMA_INITIALIZE_DOT - currentHCounter);
            }
            if (isDramRefreshPending() && currentHCounter < DRAM_REFRESH_START_DOT) {
                dotsToEvent = Math.min(dotsToEvent, DRAM_REFRESH_START_DOT - currentHCounter);
            }
            if (isHdmaPending() && currentHCounter < HDMA_START_DOT) {
                dotsToEvent = Math.min(dotsToEvent, HDMA_START_DOT - currentHCounter);
            }

            int dots = Math.min(remainingCpuDots, dotsToEvent);
            ppu.advanceCountersOnly(dots);
            remainingCpuDots -= dots;
        }
        eventStallMasterClocks = stallMasterClocks;
        eventStallDots = stallDots;
    }

    private boolean hasDueScanlineEvent() {
        return (isHdmaInitializationPending() && ppu.hCounter() == HDMA_INITIALIZE_DOT)
                || (isDramRefreshPending() && ppu.hCounter() == DRAM_REFRESH_START_DOT)
                || (isHdmaPending() && ppu.hCounter() == HDMA_START_DOT);
    }

    private void markEventsPassedOutsideScheduler() {
        if (isHdmaInitializationPending() && ppu.hCounter() > HDMA_INITIALIZE_DOT) {
            markHdmaInitializationHandled();
        }
        if (isDramRefreshPending() && ppu.hCounter() > DRAM_REFRESH_START_DOT) {
            markDramRefreshHandled();
        }
        if (isHdmaPending() && ppu.hCounter() > HDMA_START_DOT) {
            markHdmaHandled();
        }
    }

    private boolean isHdmaInitializationPending() {
        return ppu.vCounter() == 0 && hdmaInitializationFrame != ppu.frameCounter();
    }

    private void markHdmaInitializationHandled() {
        hdmaInitializationFrame = ppu.frameCounter();
    }

    private boolean isDramRefreshPending() {
        return dramRefreshFrame != ppu.frameCounter() || dramRefreshScanline != ppu.vCounter();
    }

    private void markDramRefreshHandled() {
        dramRefreshFrame = ppu.frameCounter();
        dramRefreshScanline = ppu.vCounter();
    }

    private boolean isHdmaPending() {
        return ppu.vCounter() < ppu.vBlankStartScanline()
                && (hdmaFrame != ppu.frameCounter() || hdmaScanline != ppu.vCounter());
    }

    private void markHdmaHandled() {
        hdmaFrame = ppu.frameCounter();
        hdmaScanline = ppu.vCounter();
    }

    private int advancePpuForMasterClocks(int masterClocks) {
        int dots = ppuDotsForMasterClocks(masterClocks);
        ppu.advanceCountersOnly(dots);
        return dots;
    }

    private int ppuDotsForMasterClocks(int masterClocks) {
        if (masterClocks <= 0) {
            return 0;
        }
        int totalMasterClocks = ppuMasterClockRemainder + masterClocks;
        int dots = totalMasterClocks / MASTER_CLOCKS_PER_PPU_DOT;
        ppuMasterClockRemainder = totalMasterClocks % MASTER_CLOCKS_PER_PPU_DOT;
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

    private boolean requestFrameNmi() {
        boolean inVBlank = ppu.isInVBlank();
        boolean nmiEnabled = nmiEnabled();
        boolean enteredVBlank = inVBlank && !wasInVBlank;
        boolean leftVBlank = !inVBlank && wasInVBlank;
        boolean enabledDuringVBlank = inVBlank && nmiEnabled && !wasNmiEnabled;
        if (enteredVBlank) {
            cpu.latchNmiStatus();
            updateAutoJoypadRegisters();
        } else if (leftVBlank) {
            cpu.clearNmiStatus();
        }
        if ((enteredVBlank || enabledDuringVBlank) && nmiEnabled && cpu.isNmiStatusLatched()) {
            cpu.requestNMI();
        }
        wasInVBlank = inVBlank;
        wasNmiEnabled = nmiEnabled;
        return enteredVBlank;
    }

    private void updateNmiControl(int value) {
        boolean enabled = (value & 0x80) != 0;
        if (enabled && !wasNmiEnabled && cpu.isNmiStatusLatched()) {
            cpu.requestNMI();
        }
        wasNmiEnabled = enabled;
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
            int values = joypad.clockAutoReadBits();
            for (int controller = 0; controller < joypad.controllerCount(); controller++) {
                int lowAddress = 0x18 + controller * 2;
                int report = cpu.internalRegisters()[lowAddress]
                        | (cpu.internalRegisters()[lowAddress + 1] << 8);
                report = ((report << 1) | ((values >>> controller) & 1)) & 0xffff;
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

        long matchedPosition = timerMatchPosition(
                hTimerEnabled, vTimerEnabled, startHCounter, startVCounter, startSecondField, cycles);
        if (matchedPosition == TIMER_NO_MATCH) {
            clearLastTimerIrqPosition();
            return;
        }
        int matchedHCounter = (int) (matchedPosition & 0xffff);
        int matchedVCounter = (int) (matchedPosition >>> 16);
        if (matchedHCounter == lastTimerIrqHCounter && matchedVCounter == lastTimerIrqVCounter) {
            return;
        }

        lastTimerIrqHCounter = matchedHCounter;
        lastTimerIrqVCounter = matchedVCounter;
        cpu.requestIRQ();
    }

    private void clearLastTimerIrqPosition() {
        lastTimerIrqHCounter = -1;
        lastTimerIrqVCounter = -1;
    }

    private static final long TIMER_NO_MATCH = -1;

    private static long timerPosition(int hCounter, int vCounter) {
        return ((long) vCounter << 16) | hCounter;
    }

    private long timerMatchPosition(boolean hTimerEnabled, boolean vTimerEnabled,
                                    int startHCounter, int startVCounter, boolean startSecondField, int cycles) {
        int hTarget = cpu.internalRegisters()[0x07] | ((cpu.internalRegisters()[0x08] & 1) << 8);
        int vTarget = cpu.internalRegisters()[0x09] | ((cpu.internalRegisters()[0x0a] & 1) << 8);
        if (hTimerEnabled && hTarget > H_TIMER_MAX_DOT) {
            return TIMER_NO_MATCH;
        }
        if (vTimerEnabled && vTarget > PPU.V_COUNTER_SCANLINES) {
            return TIMER_NO_MATCH;
        }
        if (cycles <= 0) {
            int hCounter = ppu.hCounter();
            int vCounter = ppu.vCounter();
            return timerMatches(hTimerEnabled, vTimerEnabled, hCounter, vCounter)
                    ? timerPosition(hCounter, vCounter)
                    : TIMER_NO_MATCH;
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
                    return timerPosition(targetH, vCounter);
                }
            }

            int toNextScanline = scanlineDots - hCounter;
            if (elapsed + toNextScanline > cycles) {
                return TIMER_NO_MATCH;
            }
            elapsed += toNextScanline;
            hCounter = 0;
            vCounter++;
            if (vCounter >= ppu.scanlinesInField(secondField)) {
                vCounter = 0;
                secondField = !secondField;
            }
        }
        return TIMER_NO_MATCH;
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

}
