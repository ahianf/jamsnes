package jamsnes.cpu;

import jamsnes.cartridge.Header;
import jamsnes.exceptions.InvalidAddress;
import jamsnes.exceptions.InvalidOpcode;
import jamsnes.memory.AMemory;
import jamsnes.memory.IMemory;
import jamsnes.memory.IMemoryBus;
import jamsnes.models.Component;

import java.util.OptionalInt;

import static jamsnes.models.Unsigned.u16;
import static jamsnes.models.Unsigned.u24;
import static jamsnes.models.Unsigned.u8;

public class CPU extends AMemory {
    private static final int CPU_VERSION = 2;
    private static final int MULTIPLICATION_CYCLES = 8;
    private static final int DIVISION_CYCLES = 16;
    private static final int DMA_SHARED_OVERHEAD_CYCLES = 8;
    private static final int MASTER_CLOCKS_PER_CPU_CYCLE = 6;
    private static final int MATH_OPERATION_NONE = 0;
    private static final int MATH_OPERATION_MULTIPLY = 1;
    private static final int MATH_OPERATION_DIVIDE = 2;
    private final Registers registers = new Registers();
    private final int[] internalRegisters = new int[0x300];
    private final DMA[] dmaChannels = new DMA[8];
    private final Header cartridgeHeader;
    private IMemoryBus bus;
    private int timerEnableGeneration;
    private boolean hasIndexCrossedPageBoundary;
    private boolean operandWrapsBank;
    private boolean emulationMode = true;
    private boolean stopped;
    private boolean waitingForInterrupt;
    private boolean dmaStartupPending;
    private int mathOperation;
    private int mathCyclesRemaining;
    private int pendingQuotient;
    private int pendingProductOrRemainder;
    private int elapsedMasterClocks;
    private int busMasterClockSurcharge;
    private IMemoryBus rawBus;
    private Runnable ioPortLatchListener = () -> {
    };
    public boolean isNMIRequested;
    public boolean isIRQRequested;
    public boolean isAbortRequested;
    public boolean isDisabled;

    public CPU(IMemoryBus bus, Header cartridgeHeader) {
        rawBus = bus;
        this.bus = new CpuTimingBus(bus);
        this.cartridgeHeader = cartridgeHeader;
        for (int i = 0; i < dmaChannels.length; i++) {
            dmaChannels[i] = new DMA(bus);
        }
        registers.p.i = true;
        registers.p.m = true;
        registers.p.x_b = true;
        registers.setPbr(0);
        registers.d = 0;
        registers.s = 0x0100;
    }

    public void setBus(IMemoryBus bus) {
        rawBus = bus;
        this.bus = new CpuTimingBus(bus);
        for (DMA dmaChannel : dmaChannels) {
            dmaChannel.setBus(bus);
        }
    }

    public IMemoryBus getBus() {
        return rawBus;
    }

    public void setIoPortLatchListener(Runnable ioPortLatchListener) {
        this.ioPortLatchListener = ioPortLatchListener == null ? () -> {
        } : ioPortLatchListener;
    }

    public Registers registers() {
        return registers;
    }

    public boolean hasIndexCrossedPageBoundary() {
        return hasIndexCrossedPageBoundary;
    }

    public boolean isEmulationMode() {
        return emulationMode;
    }

    public void setEmulationMode(boolean emulationMode) {
        this.emulationMode = emulationMode;
        if (emulationMode) {
            registers.s = 0x0100 | registers.sl();
            enforceStatusWidth();
        }
    }

    public boolean isStopped() {
        return stopped;
    }

    public boolean isWaitingForInterrupt() {
        return waitingForInterrupt;
    }

    @Override
    public boolean hasMemoryAt(int address) {
        int normalized = u24(address);
        if (normalized < start || normalized > end) {
            return false;
        }
        return isInternalRegister(normalized - start);
    }

    @Override
    public int read(int address) {
        if (address == 0x10) {
            return readNmiStatus();
        }
        if (address == 0x11) {
            return readIrqStatus();
        }
        if (address == 0x12) {
            return readVideoStatus();
        }
        if (address == 0x13) {
            return internalRegisters[0x01];
        }
        if (address >= 0x100 && address < 0x180) {
            return dmaChannels[(address - 0x100) >>> 4].read(address & 0x0f);
        }
        if (!isInternalRegister(address)) {
            throw new InvalidAddress("CPU Internal Registers read", address + start);
        }
        if (isWriteOnlyInternalRegister(address)) {
            throw new InvalidAddress("CPU Internal Registers read", address + start);
        }
        return internalRegisters[address];
    }

    @Override
    public void write(int address, int data) {
        int value = u8(data);
        if (address == 0x0b) {
            internalRegisters[address] = value;
            dmaStartupPending = value != 0;
            for (int i = 0; i < dmaChannels.length; i++) {
                dmaChannels[i].setEnabled((value & (1 << i)) != 0);
            }
            return;
        }
        if (address == 0x0c) {
            internalRegisters[address] = value;
            for (int i = 0; i < dmaChannels.length; i++) {
                dmaChannels[i].setHdmaEnabled((value & (1 << i)) != 0);
            }
            return;
        }
        if (address >= 0x100 && address < 0x180) {
            dmaChannels[(address - 0x100) >>> 4].write(address & 0x0f, data);
            return;
        }
        if (isReadOnlyInternalRegister(address)) {
            return;
        }
        if (!isInternalRegister(address)) {
            throw new InvalidAddress("CPU Internal Registers write", address + start);
        }
        if (address == 0x00) {
            if (((internalRegisters[address] ^ value) & 0x30) != 0) {
                timerEnableGeneration++;
            }
            if ((value & 0x30) == 0) {
                clearIrqStatus();
            }
        }
        if (address == 0x01 && (internalRegisters[address] & 0x80) != 0 && (value & 0x80) == 0) {
            ioPortLatchListener.run();
        }
        internalRegisters[address] = value;
        if (address == 0x03) {
            startMultiplication();
        } else if (address == 0x06) {
            startDivision();
        }
    }

    private boolean isInternalRegister(int address) {
        return (address >= 0x00 && address <= 0x0d) || (address >= 0x10 && address <= 0x1f);
    }

    private boolean isReadOnlyInternalRegister(int address) {
        return address >= 0x10 && address <= 0x1f;
    }

    private boolean isWriteOnlyInternalRegister(int address) {
        return address >= 0x00 && address <= 0x0d;
    }

    private void startMultiplication() {
        pendingProductOrRemainder = internalRegisters[0x02] * internalRegisters[0x03];
        mathOperation = MATH_OPERATION_MULTIPLY;
        mathCyclesRemaining = MULTIPLICATION_CYCLES;
    }

    private void startDivision() {
        int dividend = internalRegisters[0x04] | (internalRegisters[0x05] << 8);
        int divisor = internalRegisters[0x06];
        if (divisor == 0) {
            pendingQuotient = 0xffff;
            pendingProductOrRemainder = dividend;
        } else {
            pendingQuotient = dividend / divisor;
            pendingProductOrRemainder = dividend % divisor;
        }
        mathOperation = MATH_OPERATION_DIVIDE;
        mathCyclesRemaining = DIVISION_CYCLES;
    }

    private void advanceMathUnit(int cycles) {
        if (mathOperation == MATH_OPERATION_NONE || cycles <= 0) {
            return;
        }

        mathCyclesRemaining -= cycles;
        if (mathCyclesRemaining > 0) {
            return;
        }

        if (mathOperation == MATH_OPERATION_DIVIDE) {
            internalRegisters[0x14] = u8(pendingQuotient);
            internalRegisters[0x15] = u8(pendingQuotient >>> 8);
        }
        internalRegisters[0x16] = u8(pendingProductOrRemainder);
        internalRegisters[0x17] = u8(pendingProductOrRemainder >>> 8);
        mathOperation = MATH_OPERATION_NONE;
        mathCyclesRemaining = 0;
    }

    private int readNmiStatus() {
        int value = (internalRegisters[0x10] & 0x80) | (bus.getOpenBus() & 0x70) | CPU_VERSION;
        internalRegisters[0x10] &= 0x7f;
        isNMIRequested = false;
        return value;
    }

    private int readIrqStatus() {
        int value = (internalRegisters[0x11] & 0x80) | (bus.getOpenBus() & 0x7f);
        clearIrqStatus();
        return value;
    }

    private int readVideoStatus() {
        return (internalRegisters[0x12] & 0xc1) | (bus.getOpenBus() & 0x3e);
    }

    private void clearIrqStatus() {
        internalRegisters[0x11] &= 0x7f;
        isIRQRequested = false;
    }

    public int[] internalRegisters() {
        return internalRegisters;
    }

    public DMA[] dmaChannels() {
        return dmaChannels;
    }

    public int timerEnableGeneration() {
        return timerEnableGeneration;
    }

    public int elapsedMasterClocks() {
        return elapsedMasterClocks;
    }

    public void requestNMI() {
        isNMIRequested = true;
        internalRegisters[0x10] |= 0x80;
    }

    public void requestIRQ() {
        isIRQRequested = true;
        internalRegisters[0x11] |= 0x80;
    }

    public void requestABORT() {
        isAbortRequested = true;
    }

    public int update(int maxCycles) {
        if (isDisabled) {
            elapsedMasterClocks = 0xff * 4;
            return 0xff;
        }
        int cycles = 0;
        elapsedMasterClocks = 0;

        while (cycles < maxCycles) {
            int dmaMasterClocks = runDMA(maxCycles - cycles);
            cycles += dmaMasterClocks;
            elapsedMasterClocks += dmaMasterClocks;
            if (cycles >= maxCycles) {
                continue;
            }

            if (stopped) {
                cycles++;
                elapsedMasterClocks += MASTER_CLOCKS_PER_CPU_CYCLE;
                advanceMathUnit(1);
                continue;
            }

            busMasterClockSurcharge = 0;
            int interruptCycles = checkInterrupts();
            cycles += interruptCycles;
            elapsedMasterClocks += interruptCycles * MASTER_CLOCKS_PER_CPU_CYCLE + busMasterClockSurcharge;
            advanceMathUnit(interruptCycles);
            if (cycles >= maxCycles) {
                continue;
            }

            if (!waitingForInterrupt) {
                busMasterClockSurcharge = 0;
                int instructionCycles = executeInstruction();
                cycles += instructionCycles;
                elapsedMasterClocks += instructionCycles * MASTER_CLOCKS_PER_CPU_CYCLE + busMasterClockSurcharge;
                advanceMathUnit(instructionCycles);
            } else {
                int idleCycles = maxCycles - cycles;
                elapsedMasterClocks += idleCycles * MASTER_CLOCKS_PER_CPU_CYCLE;
                advanceMathUnit(idleCycles);
                return maxCycles;
            }
        }
        return cycles;
    }

    private int busAccessMasterClocks(int address) {
        int normalized = u24(address);
        if ((normalized & 0x408000) != 0) {
            boolean fastRom = (normalized & 0x800000) != 0 && (internalRegisters[0x0d] & 0x01) != 0;
            return fastRom ? 6 : 8;
        }
        if (((normalized + 0x6000) & 0x4000) != 0) {
            return 8;
        }
        if (((normalized - 0x4000) & 0x7e00) != 0) {
            return 6;
        }
        return 12;
    }

    public int runDMA(int maxCycles) {
        if (maxCycles <= 0) {
            return 0;
        }

        int cycles = 0;
        if (dmaStartupPending) {
            cycles += DMA_SHARED_OVERHEAD_CYCLES;
            dmaStartupPending = false;
        }
        for (int i = 0; i < dmaChannels.length; i++) {
            DMA dmaChannel = dmaChannels[i];
            if (!dmaChannel.isEnabled()) {
                continue;
            }
            if (cycles >= maxCycles) {
                break;
            }
            cycles += dmaChannel.run(maxCycles - cycles);
            if (!dmaChannel.isEnabled()) {
                internalRegisters[0x0b] &= ~(1 << i);
            } else {
                break;
            }
        }
        advanceMathUnit(cycles);
        return cycles;
    }

    public int initializeHDMA() {
        int cycles = hasEnabledHdmaChannel() ? DMA_SHARED_OVERHEAD_CYCLES : 0;
        for (int i = 0; i < dmaChannels.length; i++) {
            cycles += dmaChannels[i].initializeHDMA(!hasEnabledHdmaChannelAfter(i));
        }
        synchronizeDmaEnableRegister();
        advanceMathUnit(cycles);
        return cycles;
    }

    public int runHDMALine() {
        int cycles = hasActiveHdmaChannel() ? DMA_SHARED_OVERHEAD_CYCLES : 0;
        for (DMA dmaChannel : dmaChannels) {
            cycles += dmaChannel.transferHDMALine();
        }
        synchronizeDmaEnableRegister();
        for (int i = 0; i < dmaChannels.length; i++) {
            cycles += dmaChannels[i].completeHDMALine(!hasActiveHdmaChannelAfter(i));
        }
        advanceMathUnit(cycles);
        return cycles;
    }

    private boolean hasEnabledHdmaChannel() {
        for (DMA dmaChannel : dmaChannels) {
            if (dmaChannel.isHdmaEnabled()) {
                return true;
            }
        }
        return false;
    }

    private void synchronizeDmaEnableRegister() {
        int enabledChannels = 0;
        for (int i = 0; i < dmaChannels.length; i++) {
            if (dmaChannels[i].isEnabled()) {
                enabledChannels |= 1 << i;
            }
        }
        internalRegisters[0x0b] = enabledChannels;
        if (enabledChannels == 0) {
            dmaStartupPending = false;
        }
    }

    private boolean hasActiveHdmaChannel() {
        for (DMA dmaChannel : dmaChannels) {
            if (dmaChannel.isHdmaActive()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasEnabledHdmaChannelAfter(int channelIndex) {
        for (int i = channelIndex + 1; i < dmaChannels.length; i++) {
            if (dmaChannels[i].isHdmaEnabled()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasActiveHdmaChannelAfter(int channelIndex) {
        for (int i = channelIndex + 1; i < dmaChannels.length; i++) {
            if (dmaChannels[i].isHdmaActive()) {
                return true;
            }
        }
        return false;
    }

    public int executeInstruction() {
        operandWrapsBank = false;
        int opcode = readPC();
        hasIndexCrossedPageBoundary = false;
        int cycles = switch (opcode) {
            case 0x00 -> 7 + BRK(_getImmediateAddr8Bits());
            case 0x01 -> 6 + ORA(_getDirectIndirectIndexedXAddr()) + directPageExtraCycle();
            case 0x02 -> 7 + COP(_getImmediateAddr8Bits());
            case 0x03 -> 4 + ORA(_getStackRelativeAddr());
            case 0x04 -> 5 + TSB(_getDirectAddr()) + directPageExtraCycle();
            case 0x05 -> 3 + ORA(_getDirectAddr()) + directPageExtraCycle();
            case 0x06 -> 5 + ASL(_getDirectAddr(), AddressingMode.DIRECT_PAGE) + directPageExtraCycle();
            case 0x07 -> 6 + ORA(_getDirectIndirectLongAddr()) + directPageExtraCycle();
            case 0x08 -> 3 + PHP(0);
            case 0x09 -> 2 + ORA(_getImmediateAddrForA());
            case 0x0a -> 2 + ASL(0, AddressingMode.IMPLIED);
            case 0x0b -> 4 + PHD(0);
            case 0x0c -> 6 + TSB(_getAbsoluteAddr());
            case 0x0d -> 4 + ORA(_getAbsoluteAddr());
            case 0x0e -> 6 + ASL(_getAbsoluteAddr(), AddressingMode.ABSOLUTE);
            case 0x0f -> 5 + ORA(_getAbsoluteLongAddr());
            case 0x10 -> 2 + BPL(_getImmediateAddr8Bits());
            case 0x11 -> 5 + ORA(_getDirectIndirectIndexedYAddr()) + directPageIndexedYExtraCycle();
            case 0x12 -> 5 + ORA(_getDirectIndirectAddr()) + directPageExtraCycle();
            case 0x13 -> 7 + ORA(_getStackRelativeIndirectIndexedYAddr());
            case 0x14 -> 5 + TRB(_getDirectAddr()) + directPageExtraCycle();
            case 0x15 -> 4 + ORA(_getDirectIndexedByXAddr()) + directPageExtraCycle();
            case 0x16 -> 6 + ASL(_getDirectIndexedByXAddr(), AddressingMode.DIRECT_PAGE_INDEXED_BY_X) + directPageExtraCycle();
            case 0x17 -> 6 + ORA(_getDirectIndirectIndexedYLongAddr()) + directPageExtraCycle();
            case 0x18 -> 2 + CLC(0);
            case 0x19 -> 4 + ORA(_getAbsoluteIndexedByYAddr()) + indexBoundaryExtraCycle();
            case 0x1a -> 2 + INA(0);
            case 0x1b -> 2 + TCS(0);
            case 0x1c -> 6 + TRB(_getAbsoluteAddr());
            case 0x1d -> 4 + ORA(_getAbsoluteIndexedByXAddr()) + indexBoundaryExtraCycle();
            case 0x1e -> 7 + ASL(_getAbsoluteIndexedByXAddr(), AddressingMode.ABSOLUTE_INDEXED_BY_X);
            case 0x1f -> 5 + ORA(_getAbsoluteIndexedByXLongAddr());
            case 0x20 -> 6 + JSR(_getAbsoluteAddr());
            case 0x21 -> 6 + AND(_getDirectIndirectIndexedXAddr()) + directPageExtraCycle();
            case 0x22 -> 8 + JSL(_getAbsoluteLongAddr());
            case 0x23 -> 4 + AND(_getStackRelativeAddr());
            case 0x24 -> 3 + BIT(_getDirectAddr(), AddressingMode.DIRECT_PAGE) + directPageExtraCycle();
            case 0x25 -> 3 + AND(_getDirectAddr()) + directPageExtraCycle();
            case 0x26 -> 5 + ROL(_getDirectAddr(), AddressingMode.DIRECT_PAGE) + directPageExtraCycle();
            case 0x27 -> 6 + AND(_getDirectIndirectLongAddr()) + directPageExtraCycle();
            case 0x28 -> 4 + PLP(0);
            case 0x29 -> 2 + AND(_getImmediateAddrForA());
            case 0x2a -> 2 + ROL(0, AddressingMode.IMPLIED);
            case 0x2b -> 5 + PLD(0);
            case 0x2c -> 4 + BIT(_getAbsoluteAddr(), AddressingMode.ABSOLUTE);
            case 0x2d -> 4 + AND(_getAbsoluteAddr());
            case 0x2e -> 6 + ROL(_getAbsoluteAddr(), AddressingMode.ABSOLUTE);
            case 0x2f -> 5 + AND(_getAbsoluteLongAddr());
            case 0x30 -> 2 + BMI(_getImmediateAddr8Bits());
            case 0x31 -> 5 + AND(_getDirectIndirectIndexedYAddr()) + directPageIndexedYExtraCycle();
            case 0x32 -> 5 + AND(_getDirectIndirectAddr()) + directPageExtraCycle();
            case 0x33 -> 7 + AND(_getStackRelativeIndirectIndexedYAddr());
            case 0x34 -> 4 + BIT(_getDirectIndexedByXAddr(), AddressingMode.DIRECT_PAGE_INDEXED_BY_X) + directPageExtraCycle();
            case 0x35 -> 4 + AND(_getDirectIndexedByXAddr()) + directPageExtraCycle();
            case 0x36 -> 6 + ROL(_getDirectIndexedByXAddr(), AddressingMode.DIRECT_PAGE_INDEXED_BY_X) + directPageExtraCycle();
            case 0x37 -> 6 + AND(_getDirectIndirectIndexedYLongAddr()) + directPageExtraCycle();
            case 0x38 -> 2 + SEC(0);
            case 0x39 -> 4 + AND(_getAbsoluteIndexedByYAddr()) + indexBoundaryExtraCycle();
            case 0x3a -> 2 + DEA(0);
            case 0x3b -> 2 + TSC(0);
            case 0x3c -> 4 + BIT(_getAbsoluteIndexedByXAddr(), AddressingMode.ABSOLUTE_INDEXED_BY_X) + indexBoundaryExtraCycle();
            case 0x3d -> 4 + AND(_getAbsoluteIndexedByXAddr()) + indexBoundaryExtraCycle();
            case 0x3e -> 7 + ROL(_getAbsoluteIndexedByXAddr(), AddressingMode.ABSOLUTE_INDEXED_BY_X);
            case 0x3f -> 5 + AND(_getAbsoluteIndexedByXLongAddr());
            case 0x40 -> 6 + RTI(0);
            case 0x41 -> 6 + EOR(_getDirectIndirectIndexedXAddr()) + directPageExtraCycle();
            case 0x42 -> 2 + WDM(_getImmediateAddr8Bits());
            case 0x43 -> 4 + EOR(_getStackRelativeAddr());
            case 0x44 -> MVP(_getImmediateAddr16Bits());
            case 0x45 -> 3 + EOR(_getDirectAddr()) + directPageExtraCycle();
            case 0x46 -> 5 + LSR(_getDirectAddr(), AddressingMode.DIRECT_PAGE) + directPageExtraCycle();
            case 0x47 -> 6 + EOR(_getDirectIndirectLongAddr()) + directPageExtraCycle();
            case 0x48 -> 3 + PHA(0);
            case 0x49 -> 2 + EOR(_getImmediateAddrForA());
            case 0x4a -> 2 + LSR(0, AddressingMode.IMPLIED);
            case 0x4b -> 3 + PHK(0);
            case 0x4c -> 3 + JMP(_getAbsoluteAddr());
            case 0x4d -> 4 + EOR(_getAbsoluteAddr());
            case 0x4e -> 6 + LSR(_getAbsoluteAddr(), AddressingMode.ABSOLUTE);
            case 0x4f -> 5 + EOR(_getAbsoluteLongAddr());
            case 0x50 -> 2 + BVC(_getImmediateAddr8Bits());
            case 0x51 -> 5 + EOR(_getDirectIndirectIndexedYAddr()) + directPageIndexedYExtraCycle();
            case 0x52 -> 5 + EOR(_getDirectIndirectAddr()) + directPageExtraCycle();
            case 0x53 -> 7 + EOR(_getStackRelativeIndirectIndexedYAddr());
            case 0x54 -> MVN(_getImmediateAddr16Bits());
            case 0x55 -> 4 + EOR(_getDirectIndexedByXAddr()) + directPageExtraCycle();
            case 0x56 -> 6 + LSR(_getDirectIndexedByXAddr(), AddressingMode.DIRECT_PAGE_INDEXED_BY_X) + directPageExtraCycle();
            case 0x57 -> 6 + EOR(_getDirectIndirectIndexedYLongAddr()) + directPageExtraCycle();
            case 0x58 -> 2 + CLI(0);
            case 0x59 -> 4 + EOR(_getAbsoluteIndexedByYAddr()) + indexBoundaryExtraCycle();
            case 0x5a -> 3 + PHY(0);
            case 0x5b -> 2 + TCD(0);
            case 0x5c -> 4 + JML(_getAbsoluteLongAddr());
            case 0x5d -> 4 + EOR(_getAbsoluteIndexedByXAddr()) + indexBoundaryExtraCycle();
            case 0x5e -> 7 + LSR(_getAbsoluteIndexedByXAddr(), AddressingMode.ABSOLUTE_INDEXED_BY_X);
            case 0x5f -> 5 + EOR(_getAbsoluteIndexedByXLongAddr());
            case 0x60 -> 6 + RTS(0);
            case 0x61 -> 6 + ADC(_getDirectIndirectIndexedXAddr()) + directPageExtraCycle();
            case 0x62 -> 6 + PER(_getImmediateAddr16Bits());
            case 0x63 -> 4 + ADC(_getStackRelativeAddr());
            case 0x64 -> 3 + STZ(_getDirectAddr()) + directPageExtraCycle();
            case 0x65 -> 3 + ADC(_getDirectAddr()) + directPageExtraCycle();
            case 0x66 -> 5 + ROR(_getDirectAddr(), AddressingMode.DIRECT_PAGE) + directPageExtraCycle();
            case 0x67 -> 6 + ADC(_getDirectIndirectLongAddr()) + directPageExtraCycle();
            case 0x68 -> 4 + PLA(0);
            case 0x69 -> 2 + ADC(_getImmediateAddrForA());
            case 0x6a -> 2 + ROR(0, AddressingMode.IMPLIED);
            case 0x6b -> 6 + RTL(0);
            case 0x6c -> 5 + JMP(_getAbsoluteIndirectAddr());
            case 0x6d -> 4 + ADC(_getAbsoluteAddr());
            case 0x6e -> 6 + ROR(_getAbsoluteAddr(), AddressingMode.ABSOLUTE);
            case 0x6f -> 5 + ADC(_getAbsoluteLongAddr());
            case 0x70 -> 2 + BVS(_getImmediateAddr8Bits());
            case 0x71 -> 5 + ADC(_getDirectIndirectIndexedYAddr()) + directPageIndexedYExtraCycle();
            case 0x72 -> 5 + ADC(_getDirectIndirectAddr()) + directPageExtraCycle();
            case 0x73 -> 7 + ADC(_getStackRelativeIndirectIndexedYAddr());
            case 0x74 -> 4 + STZ(_getDirectIndexedByXAddr()) + directPageExtraCycle();
            case 0x75 -> 4 + ADC(_getDirectIndexedByXAddr()) + directPageExtraCycle();
            case 0x76 -> 6 + ROR(_getDirectIndexedByXAddr(), AddressingMode.DIRECT_PAGE_INDEXED_BY_X) + directPageExtraCycle();
            case 0x77 -> 6 + ADC(_getDirectIndirectIndexedYLongAddr()) + directPageExtraCycle();
            case 0x78 -> 2 + SEI(0);
            case 0x79 -> 4 + ADC(_getAbsoluteIndexedByYAddr()) + indexBoundaryExtraCycle();
            case 0x7a -> 4 + PLY(0);
            case 0x7b -> 2 + TDC(0);
            case 0x7c -> 6 + JMP(_getAbsoluteIndirectIndexedByXAddr());
            case 0x7d -> 4 + ADC(_getAbsoluteIndexedByXAddr()) + indexBoundaryExtraCycle();
            case 0x7e -> 7 + ROR(_getAbsoluteIndexedByXAddr(), AddressingMode.ABSOLUTE_INDEXED_BY_X);
            case 0x7f -> 5 + ADC(_getAbsoluteIndexedByXLongAddr());
            case 0x80 -> 3 + BRA(_getImmediateAddr8Bits());
            case 0x81 -> 6 + STA(_getDirectIndirectIndexedXAddr()) + directPageExtraCycle();
            case 0x82 -> 4 + BRL(_getImmediateAddr16Bits());
            case 0x83 -> 4 + STA(_getStackRelativeAddr());
            case 0x84 -> 3 + STY(_getDirectAddr()) + directPageExtraCycle();
            case 0x85 -> 3 + STA(_getDirectAddr()) + directPageExtraCycle();
            case 0x86 -> 3 + STX(_getDirectAddr()) + directPageExtraCycle();
            case 0x87 -> 6 + STA(_getDirectIndirectLongAddr()) + directPageExtraCycle();
            case 0x88 -> 2 + DEY(0);
            case 0x89 -> 2 + BIT(_getImmediateAddrForA(), AddressingMode.IMMEDIATE_FOR_A);
            case 0x8a -> 2 + TXA(0);
            case 0x8b -> 3 + PHB(0);
            case 0x8c -> 4 + STY(_getAbsoluteAddr());
            case 0x8d -> 4 + STA(_getAbsoluteAddr());
            case 0x8e -> 4 + STX(_getAbsoluteAddr());
            case 0x8f -> 5 + STA(_getAbsoluteLongAddr());
            case 0x90 -> 2 + BCC(_getImmediateAddr8Bits());
            case 0x91 -> 6 + STA(_getDirectIndirectIndexedYAddr()) + directPageExtraCycle();
            case 0x92 -> 5 + STA(_getDirectIndirectAddr()) + directPageExtraCycle();
            case 0x93 -> 7 + STA(_getStackRelativeIndirectIndexedYAddr());
            case 0x94 -> 4 + STY(_getDirectIndexedByXAddr()) + directPageExtraCycle();
            case 0x95 -> 4 + STA(_getDirectIndexedByXAddr()) + directPageExtraCycle();
            case 0x96 -> 4 + STX(_getDirectIndexedByYAddr()) + directPageExtraCycle();
            case 0x97 -> 6 + STA(_getDirectIndirectIndexedYLongAddr()) + directPageExtraCycle();
            case 0x98 -> 2 + TYA(0);
            case 0x99 -> 5 + STA(_getAbsoluteIndexedByYAddr());
            case 0x9a -> 2 + TXS(0);
            case 0x9b -> 2 + TXY(0);
            case 0x9c -> 4 + STZ(_getAbsoluteAddr());
            case 0x9d -> 5 + STA(_getAbsoluteIndexedByXAddr());
            case 0x9e -> 5 + STZ(_getAbsoluteIndexedByXAddr());
            case 0x9f -> 5 + STA(_getAbsoluteIndexedByXLongAddr());
            case 0xa0 -> 2 + LDY(_getImmediateAddrForX());
            case 0xa1 -> 6 + LDA(_getDirectIndirectIndexedXAddr()) + directPageExtraCycle();
            case 0xa2 -> 2 + LDX(_getImmediateAddrForX());
            case 0xa3 -> 4 + LDA(_getStackRelativeAddr());
            case 0xa4 -> 3 + LDY(_getDirectAddr()) + directPageExtraCycle();
            case 0xa5 -> 3 + LDA(_getDirectAddr()) + directPageExtraCycle();
            case 0xa6 -> 3 + LDX(_getDirectAddr()) + directPageExtraCycle();
            case 0xa7 -> 6 + LDA(_getDirectIndirectLongAddr()) + directPageExtraCycle();
            case 0xa8 -> 2 + TAY(0);
            case 0xa9 -> 2 + LDA(_getImmediateAddrForA());
            case 0xaa -> 2 + TAX(0);
            case 0xab -> 4 + PLB(0);
            case 0xac -> 4 + LDY(_getAbsoluteAddr());
            case 0xad -> 4 + LDA(_getAbsoluteAddr());
            case 0xae -> 4 + LDX(_getAbsoluteAddr());
            case 0xaf -> 5 + LDA(_getAbsoluteLongAddr());
            case 0xb0 -> 2 + BCS(_getImmediateAddr8Bits());
            case 0xb1 -> 5 + LDA(_getDirectIndirectIndexedYAddr()) + directPageIndexedYExtraCycle();
            case 0xb2 -> 5 + LDA(_getDirectIndirectAddr()) + directPageExtraCycle();
            case 0xb3 -> 7 + LDA(_getStackRelativeIndirectIndexedYAddr());
            case 0xb4 -> 4 + LDY(_getDirectIndexedByXAddr()) + directPageExtraCycle();
            case 0xb5 -> 4 + LDA(_getDirectIndexedByXAddr()) + directPageExtraCycle();
            case 0xb6 -> 4 + LDX(_getDirectIndexedByYAddr()) + directPageExtraCycle();
            case 0xb7 -> 6 + LDA(_getDirectIndirectIndexedYLongAddr()) + directPageExtraCycle();
            case 0xb8 -> 2 + CLV(0);
            case 0xb9 -> 4 + LDA(_getAbsoluteIndexedByYAddr()) + indexBoundaryExtraCycle();
            case 0xba -> 2 + TSX(0);
            case 0xbb -> 2 + TYX(0);
            case 0xbc -> 4 + LDY(_getAbsoluteIndexedByXAddr()) + indexBoundaryExtraCycle();
            case 0xbd -> 4 + LDA(_getAbsoluteIndexedByXAddr()) + indexBoundaryExtraCycle();
            case 0xbe -> 4 + LDX(_getAbsoluteIndexedByYAddr()) + indexBoundaryExtraCycle();
            case 0xbf -> 5 + LDA(_getAbsoluteIndexedByXLongAddr());
            case 0xc0 -> 2 + CPY(_getImmediateAddrForX());
            case 0xc1 -> 6 + CMP(_getDirectIndirectIndexedXAddr()) + directPageExtraCycle();
            case 0xc2 -> 3 + REP(_getImmediateAddr8Bits());
            case 0xc3 -> 4 + CMP(_getStackRelativeAddr());
            case 0xc4 -> 3 + CPY(_getDirectAddr()) + directPageExtraCycle();
            case 0xc5 -> 3 + CMP(_getDirectAddr()) + directPageExtraCycle();
            case 0xc6 -> 5 + DEC(_getDirectAddr()) + directPageExtraCycle();
            case 0xc7 -> 6 + CMP(_getDirectIndirectLongAddr()) + directPageExtraCycle();
            case 0xc8 -> 2 + INY(0);
            case 0xc9 -> 2 + CMP(_getImmediateAddrForA());
            case 0xca -> 2 + DEX(0);
            case 0xcb -> 3 + WAI(0);
            case 0xcc -> 4 + CPY(_getAbsoluteAddr());
            case 0xcd -> 4 + CMP(_getAbsoluteAddr());
            case 0xce -> 6 + DEC(_getAbsoluteAddr());
            case 0xcf -> 5 + CMP(_getAbsoluteLongAddr());
            case 0xd0 -> 2 + BNE(_getImmediateAddr8Bits());
            case 0xd1 -> 5 + CMP(_getDirectIndirectIndexedYAddr()) + directPageIndexedYExtraCycle();
            case 0xd2 -> 5 + CMP(_getDirectIndirectAddr()) + directPageExtraCycle();
            case 0xd3 -> 7 + CMP(_getStackRelativeIndirectIndexedYAddr());
            case 0xd4 -> 6 + PEI(_getDirectAddr()) + directPageExtraCycle();
            case 0xd5 -> 4 + CMP(_getDirectIndexedByXAddr()) + directPageExtraCycle();
            case 0xd6 -> 6 + DEC(_getDirectIndexedByXAddr()) + directPageExtraCycle();
            case 0xd7 -> 6 + CMP(_getDirectIndirectIndexedYLongAddr()) + directPageExtraCycle();
            case 0xd8 -> 2 + CLD(0);
            case 0xd9 -> 4 + CMP(_getAbsoluteIndexedByYAddr()) + indexBoundaryExtraCycle();
            case 0xda -> 3 + PHX(0);
            case 0xdb -> 3 + STP(0);
            case 0xdc -> 6 + JML(_getAbsoluteIndirectLongAddr());
            case 0xdd -> 4 + CMP(_getAbsoluteIndexedByXAddr()) + indexBoundaryExtraCycle();
            case 0xde -> 7 + DEC(_getAbsoluteIndexedByXAddr());
            case 0xdf -> 5 + CMP(_getAbsoluteIndexedByXLongAddr());
            case 0xe0 -> 2 + CPX(_getImmediateAddrForX());
            case 0xe1 -> 6 + SBC(_getDirectIndirectIndexedXAddr()) + directPageExtraCycle();
            case 0xe2 -> 3 + SEP(_getImmediateAddr8Bits());
            case 0xe3 -> 4 + SBC(_getStackRelativeAddr());
            case 0xe4 -> 3 + CPX(_getDirectAddr()) + directPageExtraCycle();
            case 0xe5 -> 3 + SBC(_getDirectAddr()) + directPageExtraCycle();
            case 0xe6 -> 5 + INC(_getDirectAddr()) + directPageExtraCycle();
            case 0xe7 -> 6 + SBC(_getDirectIndirectLongAddr()) + directPageExtraCycle();
            case 0xe8 -> 2 + INX(0);
            case 0xe9 -> 2 + SBC(_getImmediateAddrForA());
            case 0xea -> 2 + NOP(0);
            case 0xeb -> 3 + XBA(0);
            case 0xec -> 4 + CPX(_getAbsoluteAddr());
            case 0xed -> 4 + SBC(_getAbsoluteAddr());
            case 0xee -> 6 + INC(_getAbsoluteAddr());
            case 0xef -> 5 + SBC(_getAbsoluteLongAddr());
            case 0xf0 -> 2 + BEQ(_getImmediateAddr8Bits());
            case 0xf1 -> 5 + SBC(_getDirectIndirectIndexedYAddr()) + directPageIndexedYExtraCycle();
            case 0xf2 -> 5 + SBC(_getDirectIndirectAddr()) + directPageExtraCycle();
            case 0xf3 -> 7 + SBC(_getStackRelativeIndirectIndexedYAddr());
            case 0xf4 -> 5 + PEA(_getImmediateAddr16Bits());
            case 0xf5 -> 4 + SBC(_getDirectIndexedByXAddr()) + directPageExtraCycle();
            case 0xf6 -> 6 + INC(_getDirectIndexedByXAddr()) + directPageExtraCycle();
            case 0xf7 -> 6 + SBC(_getDirectIndirectIndexedYLongAddr()) + directPageExtraCycle();
            case 0xf8 -> 2 + SED(0);
            case 0xf9 -> 4 + SBC(_getAbsoluteIndexedByYAddr()) + indexBoundaryExtraCycle();
            case 0xfa -> 4 + PLX(0);
            case 0xfb -> 2 + XCE(0);
            case 0xfc -> 8 + JSRIndexedIndirect(_getAbsoluteIndirectIndexedByXAddr());
            case 0xfd -> 4 + SBC(_getAbsoluteIndexedByXAddr()) + indexBoundaryExtraCycle();
            case 0xfe -> 7 + INC(_getAbsoluteIndexedByXAddr());
            case 0xff -> 5 + SBC(_getAbsoluteIndexedByXLongAddr());
            default -> throw new InvalidOpcode("CPU opcode 0x%02x is not implemented".formatted(opcode));
        };
        operandWrapsBank = false;
        return cycles;
    }

    @Override
    public int getSize() {
        return 0x300;
    }

    public int _getImmediateAddr8Bits() {
        int result = registers.pac;
        registers.incrementPc(1);
        return result;
    }

    public int _getImmediateAddr16Bits() {
        int result = registers.pac;
        registers.incrementPc(2);
        return result;
    }

    public int _getImmediateAddrForA() {
        int effective = registers.pac;
        operandWrapsBank = true;
        registers.incrementPc(registers.p.m ? 1 : 2);
        return effective;
    }

    public int _getImmediateAddrForX() {
        int effective = registers.pac;
        operandWrapsBank = true;
        registers.incrementPc(registers.p.x_b ? 1 : 2);
        return effective;
    }

    public int _getDirectAddr() {
        operandWrapsBank = true;
        return directPageAddress(readPC());
    }

    public int _getAbsoluteAddr() {
        int address = registers.dbr << 16;
        address += readPC();
        address += readPC() << 8;
        return u24(address);
    }

    public int _getAbsoluteLongAddr() {
        int address = readPC();
        address += readPC() << 8;
        address += readPC() << 16;
        return u24(address);
    }

    public int _getDirectIndirectIndexedYAddr() {
        int dp = directPageAddress(readPC());
        int base = bus.read(dp);
        base += bus.read(nextDirectPageAddress(dp)) << 8;
        base += registers.dbr << 16;
        int index = indexYValue();
        markIndexBoundary(base, index);
        return u24(base + index);
    }

    public int _getDirectIndirectIndexedYLongAddr() {
        int dp = directPageAddress(readPC());
        int base = bus.read(dp);
        base += bus.read(u16(dp + 1)) << 8;
        base += bus.read(u16(dp + 2)) << 16;
        return u24(base + indexYValue());
    }

    public int _getDirectIndirectIndexedXAddr() {
        int dp = directPageAddress(readPC() + indexXValue());
        int base = bus.read(dp);
        base += bus.read(nextDirectPageAddress(dp)) << 8;
        base += registers.dbr << 16;
        return u24(base);
    }

    public int _getDirectIndexedByXAddr() {
        operandWrapsBank = true;
        return directPageAddress(readPC() + indexXValue());
    }

    public int _getDirectIndexedByYAddr() {
        operandWrapsBank = true;
        return directPageAddress(readPC() + indexYValue());
    }

    public int _getAbsoluteIndexedByXAddr() {
        int abs = readPC();
        abs = u16(abs + (readPC() << 8));
        int effective = abs + (registers.dbr << 16);
        int index = indexXValue();
        markIndexBoundary(effective, index);
        return u24(effective + index);
    }

    public int _getAbsoluteIndexedByYAddr() {
        int abs = readPC();
        abs = u16(abs + (readPC() << 8));
        int effective = abs + (registers.dbr << 16);
        int index = indexYValue();
        markIndexBoundary(effective, index);
        return u24(effective + index);
    }

    public int _getAbsoluteIndexedByXLongAddr() {
        int value = readPC();
        value += readPC() << 8;
        value += readPC() << 16;
        return u24(value + indexXValue());
    }

    public int _getAbsoluteIndirectAddr() {
        int abs = readPC();
        abs = u16(abs + (readPC() << 8));
        int effective = bus.read(abs);
        effective += bus.read(u16(abs + 1)) << 8;
        return u24(effective);
    }

    public int _getAbsoluteIndirectLongAddr() {
        int abs = readPC();
        abs = u16(abs + (readPC() << 8));
        int effective = bus.read(abs);
        effective += bus.read(u16(abs + 1)) << 8;
        effective += bus.read(u16(abs + 2)) << 16;
        return u24(effective);
    }

    public int _getAbsoluteIndirectIndexedByXAddr() {
        int abs = readPC();
        abs = u16(abs + (readPC() << 8));
        abs = u16(abs + indexXValue());
        int programBank = registers.pbr << 16;
        int effective = bus.read(programBank | abs);
        effective += bus.read(programBank | u16(abs + 1)) << 8;
        return u24(effective);
    }

    public int _getDirectIndirectAddr() {
        int dp = directPageAddress(readPC());
        int effective = bus.read(dp);
        effective += bus.read(nextDirectPageAddress(dp)) << 8;
        effective += registers.dbr << 16;
        return u24(effective);
    }

    public int _getDirectIndirectLongAddr() {
        int dp = directPageAddress(readPC());
        int effective = bus.read(dp);
        dp = u16(dp + 1);
        effective += bus.read(dp) << 8;
        dp = u16(dp + 1);
        effective += bus.read(dp) << 16;
        return u24(effective);
    }

    public int _getStackRelativeAddr() {
        operandWrapsBank = true;
        return stackRelativeAddress(readPC());
    }

    public int _getStackRelativeIndirectIndexedYAddr() {
        int pointer = stackRelativeAddress(readPC());
        int base = bus.read(pointer) | (bus.read(nextStackRelativeAddress(pointer)) << 8);
        return u24((registers.dbr << 16) + base + indexYValue());
    }

    public void _push8(int data) {
        bus.write(stackAddress(), data);
        decrementStackPointer();
    }

    public void _push16(int data) {
        bus.write(stackAddress(), data >>> 8);
        decrementStackPointer();
        bus.write(stackAddress(), data);
        decrementStackPointer();
    }

    public int _pop() {
        incrementStackPointer();
        return bus.read(stackAddress());
    }

    public int _pop16() {
        incrementStackPointer();
        int value = bus.read(stackAddress());
        incrementStackPointer();
        value += bus.read(stackAddress()) << 8;
        return u16(value);
    }

    private int stackAddress() {
        return emulationMode ? (0x0100 | registers.sl()) : registers.s;
    }

    private int stackRelativeAddress(int offset) {
        int stackPointer = emulationMode ? (0x0100 | registers.sl()) : registers.s;
        return u16(stackPointer + offset);
    }

    private int nextStackRelativeAddress(int address) {
        return u16(address + 1);
    }

    private void pushWordAcrossEmulationStackBoundary(int value) {
        int address = stackAddress();
        bus.write(address, value >>> 8);
        address = u16(address - 1);
        bus.write(address, value);
        adjustStackPointer(-2);
    }

    private void pushLongAcrossEmulationStackBoundary(int value) {
        int address = stackAddress();
        bus.write(address, value >>> 16);
        address = u16(address - 1);
        bus.write(address, value >>> 8);
        address = u16(address - 1);
        bus.write(address, value);
        adjustStackPointer(-3);
    }

    private int popWordAcrossEmulationStackBoundary() {
        int address = u16(stackAddress() + 1);
        int value = bus.read(address);
        address = u16(address + 1);
        value |= bus.read(address) << 8;
        adjustStackPointer(2);
        return u16(value);
    }

    private int popLongAcrossEmulationStackBoundary() {
        int address = u16(stackAddress() + 1);
        int value = bus.read(address);
        address = u16(address + 1);
        value |= bus.read(address) << 8;
        address = u16(address + 1);
        value |= bus.read(address) << 16;
        adjustStackPointer(3);
        return u24(value);
    }

    private void adjustStackPointer(int amount) {
        int adjusted = u16(registers.s + amount);
        registers.s = emulationMode ? (0x0100 | u8(adjusted)) : adjusted;
    }

    private void decrementStackPointer() {
        registers.s = emulationMode ? (0x0100 | u8(registers.s - 1)) : u16(registers.s - 1);
    }

    private void incrementStackPointer() {
        registers.s = emulationMode ? (0x0100 | u8(registers.s + 1)) : u16(registers.s + 1);
    }

    public int SEC(int valueAddr) {
        registers.p.c = true;
        return 0;
    }

    public int SED(int valueAddr) {
        registers.p.d = true;
        return 0;
    }

    public int SEI(int valueAddr) {
        registers.p.i = true;
        return 0;
    }

    public int CLC(int valueAddr) {
        registers.p.c = false;
        return 0;
    }

    public int CLI(int valueAddr) {
        registers.p.i = false;
        return 0;
    }

    public int CLD(int valueAddr) {
        registers.p.d = false;
        return 0;
    }

    public int CLV(int valueAddr) {
        registers.p.v = false;
        return 0;
    }

    public int SEP(int valueAddr) {
        registers.p.setFlags(registers.p.flags() | bus.read(valueAddr));
        enforceStatusWidth();
        return 0;
    }

    public int REP(int valueAddr) {
        registers.p.setFlags(registers.p.flags() & ~bus.read(valueAddr));
        enforceStatusWidth();
        return 0;
    }

    public int JSR(int valueAddr) {
        registers.setPc(registers.pc - 1);
        _push16(registers.pc);
        registers.setPc(valueAddr);
        return 0;
    }

    public int JSRIndexedIndirect(int valueAddr) {
        registers.setPc(registers.pc - 1);
        pushWordAcrossEmulationStackBoundary(registers.pc);
        registers.setPc(valueAddr);
        return 0;
    }

    public int JSL(int valueAddr) {
        registers.setPc(registers.pc - 1);
        pushLongAcrossEmulationStackBoundary(registers.pac);
        registers.setPac(valueAddr);
        return 0;
    }

    public int PHA(int valueAddr) {
        if (registers.p.m) {
            _push8(registers.al());
        } else {
            _push16(registers.a);
        }
        return registers.p.m ? 0 : 1;
    }

    public int PHB(int valueAddr) {
        _push8(registers.dbr);
        return 0;
    }

    public int PHD(int valueAddr) {
        pushWordAcrossEmulationStackBoundary(registers.d);
        return 0;
    }

    public int PHK(int valueAddr) {
        _push8(registers.pbr);
        return 0;
    }

    public int PHP(int valueAddr) {
        _push8(registers.p.flags());
        return 0;
    }

    public int PHX(int valueAddr) {
        if (registers.p.x_b) {
            _push8(registers.xl());
        } else {
            _push16(registers.x);
        }
        return registers.p.x_b ? 0 : 1;
    }

    public int PHY(int valueAddr) {
        if (registers.p.x_b) {
            _push8(registers.yl());
        } else {
            _push16(registers.y);
        }
        return registers.p.x_b ? 0 : 1;
    }

    public int PLA(int valueAddr) {
        if (registers.p.m) {
            registers.setAl(_pop());
        } else {
            registers.a = _pop16();
        }
        setZNAccumulator(registers.a);
        return registers.p.m ? 0 : 1;
    }

    public int PLB(int valueAddr) {
        registers.dbr = _pop();
        registers.p.z = registers.dbr == 0;
        registers.p.n = (registers.dbr & 0x80) != 0;
        return 0;
    }

    public int PLD(int valueAddr) {
        registers.d = popWordAcrossEmulationStackBoundary();
        setZN16(registers.d);
        return 0;
    }

    public int PLP(int valueAddr) {
        registers.p.setFlags(_pop());
        enforceStatusWidth();
        return 0;
    }

    public int PLX(int valueAddr) {
        if (registers.p.x_b) {
            registers.x = _pop();
        } else {
            registers.x = _pop16();
        }
        setZNIndex(registers.x);
        return registers.p.x_b ? 0 : 1;
    }

    public int PLY(int valueAddr) {
        if (registers.p.x_b) {
            registers.y = _pop();
        } else {
            registers.y = _pop16();
        }
        setZNIndex(registers.y);
        return registers.p.x_b ? 0 : 1;
    }

    public int PER(int valueAddr) {
        int value = readProgramWord(valueAddr);
        value = u16(value + registers.pc);
        pushWordAcrossEmulationStackBoundary(value);
        return 0;
    }

    public int PEA(int valueAddr) {
        pushWordAcrossEmulationStackBoundary(readProgramWord(valueAddr));
        return 0;
    }

    public int PEI(int value) {
        int effective = bus.read(value) | (bus.read(u16(value + 1)) << 8);
        pushWordAcrossEmulationStackBoundary(effective);
        return 0;
    }

    public int XCE(int valueAddr) {
        boolean oldCarry = registers.p.c;
        registers.p.c = emulationMode;
        emulationMode = oldCarry;

        if (emulationMode) {
            registers.p.m = true;
            registers.p.x_b = true;
            registers.s = 0x0100 | registers.sl();
        }
        enforceStatusWidth();
        return 0;
    }

    public int BCC(int valueAddr) {
        return branch(valueAddr, !registers.p.c);
    }

    public int BCS(int valueAddr) {
        return branch(valueAddr, registers.p.c);
    }

    public int BEQ(int valueAddr) {
        return branch(valueAddr, registers.p.z);
    }

    public int BNE(int valueAddr) {
        return branch(valueAddr, !registers.p.z);
    }

    public int BMI(int valueAddr) {
        return branch(valueAddr, registers.p.n);
    }

    public int BPL(int valueAddr) {
        return branch(valueAddr, !registers.p.n);
    }

    public int BRA(int valueAddr) {
        int oldPc = registers.pc;
        int target = u16(oldPc + (byte) bus.read(valueAddr));
        registers.setPc(target);
        return relativePageCrossExtraCycle(oldPc, target);
    }

    public int BRL(int valueAddr) {
        int value = readProgramWord(valueAddr);
        registers.setPc(registers.pc + (short) value);
        return 0;
    }

    public int BVC(int valueAddr) {
        return branch(valueAddr, !registers.p.v);
    }

    public int BVS(int valueAddr) {
        return branch(valueAddr, registers.p.v);
    }

    public int JMP(int value) {
        registers.setPc(value);
        return 0;
    }

    public int JML(int value) {
        registers.setPac(value);
        return 0;
    }

    public int NOP(int valueAddr) {
        return 0;
    }

    public int RTS(int valueAddr) {
        registers.setPc(_pop16() + 1);
        return 0;
    }

    public int RTL(int valueAddr) {
        int returnAddress = popLongAcrossEmulationStackBoundary();
        registers.setPc(returnAddress + 1);
        registers.setPbr(returnAddress >>> 16);
        return 0;
    }

    public int STP(int valueAddr) {
        stopped = true;
        return 0;
    }

    public int WDM(int valueAddr) {
        return 0;
    }

    public int RESB() {
        registers.p.i = true;
        registers.p.d = false;
        emulationMode = true;
        registers.p.m = true;
        registers.p.x_b = true;
        enforceStatusWidth();
        registers.dbr = 0;
        registers.setPbr(0);
        registers.d = 0;
        registers.s = 0x0100 | registers.sl();
        registers.setPc(cartridgeHeader.emulationInterrupts.reset);
        stopped = false;
        waitingForInterrupt = false;
        isNMIRequested = false;
        isIRQRequested = false;
        isAbortRequested = false;
        mathOperation = MATH_OPERATION_NONE;
        mathCyclesRemaining = 0;
        elapsedMasterClocks = 0;
        busMasterClockSurcharge = 0;
        dmaStartupPending = false;
        internalRegisters[0x10] &= 0x7f;
        internalRegisters[0x11] &= 0x7f;
        internalRegisters[0x00] = 0;
        internalRegisters[0x01] = 0xff;
        internalRegisters[0x0b] = 0;
        internalRegisters[0x0c] = 0;
        internalRegisters[0x0d] = 0;
        for (DMA dmaChannel : dmaChannels) {
            dmaChannel.resetRuntimeState();
        }
        return 0;
    }

    public int BRK(int valueAddr) {
        runInterrupt(cartridgeHeader.nativeInterrupts.brk, cartridgeHeader.emulationInterrupts.brk, true);
        return emulationMode ? 0 : 1;
    }

    public int COP(int valueAddr) {
        runInterrupt(cartridgeHeader.nativeInterrupts.cop, cartridgeHeader.emulationInterrupts.cop, true);
        return emulationMode ? 0 : 1;
    }

    public int RTI(int valueAddr) {
        registers.p.setFlags(_pop());
        enforceStatusWidth();
        registers.setPc(_pop16());
        if (!emulationMode) {
            registers.setPbr(_pop());
        }
        return emulationMode ? 0 : 1;
    }

    public int WAI(int valueAddr) {
        waitingForInterrupt = true;
        return 0;
    }

    public int TAX(int valueAddr) {
        if (registers.p.x_b) {
            registers.x = registers.al();
            registers.p.z = registers.xl() == 0;
            registers.p.n = (registers.x & 0x80) != 0;
        } else {
            registers.x = u16(registers.a);
            registers.p.z = registers.x == 0;
            registers.p.n = (registers.x & 0x8000) != 0;
        }
        return 0;
    }

    public int TAY(int valueAddr) {
        if (registers.p.x_b) {
            registers.y = registers.al();
            registers.p.z = registers.yl() == 0;
            registers.p.n = (registers.y & 0x80) != 0;
        } else {
            registers.y = u16(registers.a);
            registers.p.z = registers.y == 0;
            registers.p.n = (registers.y & 0x8000) != 0;
        }
        return 0;
    }

    public int TXS(int valueAddr) {
        if (registers.p.x_b) {
            registers.s = registers.xl();
        } else {
            registers.s = u16(registers.x);
        }
        if (emulationMode) {
            registers.s = 0x0100 | registers.sl();
        }
        return 0;
    }

    public int TCD(int valueAddr) {
        registers.d = u16(registers.a);
        setZN16(registers.d);
        return 0;
    }

    public int TCS(int valueAddr) {
        registers.s = u16(registers.a);
        if (emulationMode) {
            registers.s = 0x0100 | registers.sl();
        }
        return 0;
    }

    public int TDC(int valueAddr) {
        registers.a = u16(registers.d);
        setZN16(registers.a);
        return 0;
    }

    public int TSC(int valueAddr) {
        registers.a = u16(registers.s);
        setZN16(registers.a);
        return 0;
    }

    public int TSX(int valueAddr) {
        registers.x = u16(registers.s);
        if (registers.p.x_b) {
            registers.x &= 0xff;
        }
        setZNIndex(registers.x);
        return 0;
    }

    public int TXA(int valueAddr) {
        if (registers.p.m) {
            registers.setAl(registers.xl());
        } else {
            registers.a = u16(registers.x);
            if (registers.p.x_b) {
                registers.setAh(0);
            }
        }
        setZNAccumulator(registers.a);
        return 0;
    }

    public int TYA(int valueAddr) {
        if (registers.p.m) {
            registers.setAl(registers.yl());
        } else {
            registers.a = u16(registers.y);
            if (registers.p.x_b) {
                registers.setAh(0);
            }
        }
        setZNAccumulator(registers.a);
        return 0;
    }

    public int TXY(int valueAddr) {
        if (registers.p.x_b) {
            registers.y = registers.xl();
        } else {
            registers.y = u16(registers.x);
        }
        setZNIndex(registers.y);
        return 0;
    }

    public int TYX(int valueAddr) {
        if (registers.p.x_b) {
            registers.x = registers.yl();
        } else {
            registers.x = u16(registers.y);
        }
        setZNIndex(registers.x);
        return 0;
    }

    public int MVN(int valueAddr) {
        return blockMove(valueAddr, 1);
    }

    public int MVP(int valueAddr) {
        return blockMove(valueAddr, -1);
    }

    private int blockMove(int valueAddr, int indexDelta) {
        int destBank = bus.read(valueAddr);
        int srcBank = bus.read(nextProgramAddress(valueAddr));
        registers.dbr = destBank;
        int data = bus.read(u24((srcBank << 16) | blockMoveIndexValue(registers.x)));
        bus.write(u24((destBank << 16) | blockMoveIndexValue(registers.y)), data);
        registers.x = advanceBlockMoveIndex(registers.x, indexDelta);
        registers.y = advanceBlockMoveIndex(registers.y, indexDelta);
        registers.a = u16(registers.a - 1);
        if (registers.a != 0xffff) {
            registers.incrementPc(-3);
        }
        return 7;
    }

    private int blockMoveIndexValue(int value) {
        return blockMoveUsesEightBitIndex() ? u8(value) : u16(value);
    }

    private int advanceBlockMoveIndex(int value, int delta) {
        return blockMoveUsesEightBitIndex() ? u8(value + delta) : u16(value + delta);
    }

    private boolean blockMoveUsesEightBitIndex() {
        return emulationMode;
    }

    public int INX(int valueAddr) {
        registers.x++;
        if (registers.p.x_b) {
            registers.x &= 0xff;
        } else {
            registers.x = u16(registers.x);
        }
        setZNIndex(registers.x);
        return 0;
    }

    public int INY(int valueAddr) {
        registers.y++;
        if (registers.p.x_b) {
            registers.y &= 0xff;
        } else {
            registers.y = u16(registers.y);
        }
        setZNIndex(registers.y);
        return 0;
    }

    public int DEX(int valueAddr) {
        registers.x = u16(registers.x - 1);
        if (registers.p.x_b) {
            registers.x &= 0xff;
        }
        setZNIndex(registers.x);
        return 0;
    }

    public int DEY(int valueAddr) {
        registers.y = u16(registers.y - 1);
        if (registers.p.x_b) {
            registers.y &= 0xff;
        }
        setZNIndex(registers.y);
        return 0;
    }

    public int CPX(int valueAddr) {
        return compareIndex(registers.x, valueAddr);
    }

    public int CPY(int valueAddr) {
        return compareIndex(registers.y, valueAddr);
    }

    public int STA(int address) {
        bus.write(address, registers.al());
        if (!registers.p.m) {
            bus.write(nextOperandAddress(address), registers.ah());
        }
        return registers.p.m ? 0 : 1;
    }

    public int STX(int address) {
        bus.write(address, registers.xl());
        if (!registers.p.x_b) {
            bus.write(nextOperandAddress(address), registers.xh());
        }
        return registers.p.x_b ? 0 : 1;
    }

    public int STY(int address) {
        bus.write(address, registers.yl());
        if (!registers.p.x_b) {
            bus.write(nextOperandAddress(address), registers.yh());
        }
        return registers.p.x_b ? 0 : 1;
    }

    public int STZ(int address) {
        bus.write(address, 0);
        if (!registers.p.m) {
            bus.write(nextOperandAddress(address), 0);
        }
        return registers.p.m ? 0 : 1;
    }

    public int LDA(int address) {
        if (registers.p.m) {
            registers.setAl(bus.read(address));
        } else {
            registers.a = readOperandWord(address);
        }
        setZNAccumulator(registers.a);
        return registers.p.m ? 0 : 1;
    }

    public int LDX(int address) {
        if (registers.p.x_b) {
            registers.x = bus.read(address);
        } else {
            registers.x = readOperandWord(address);
        }
        setZNIndex(registers.x);
        return registers.p.x_b ? 0 : 1;
    }

    public int LDY(int address) {
        if (registers.p.x_b) {
            registers.y = bus.read(address);
        } else {
            registers.y = readOperandWord(address);
        }
        setZNIndex(registers.y);
        return registers.p.x_b ? 0 : 1;
    }

    public int ORA(int valueAddr) {
        setAccumulatorValue(accumulatorValue() | readAccumulatorWidth(valueAddr));
        setZNAccumulator(registers.a);
        return registers.p.m ? 0 : 1;
    }

    public int AND(int valueAddr) {
        setAccumulatorValue(accumulatorValue() & readAccumulatorWidth(valueAddr));
        setZNAccumulator(registers.a);
        return registers.p.m ? 0 : 1;
    }

    public int EOR(int valueAddr) {
        setAccumulatorValue(accumulatorValue() ^ readAccumulatorWidth(valueAddr));
        setZNAccumulator(registers.a);
        return registers.p.m ? 0 : 1;
    }

    public int CMP(int valueAddr) {
        int value = readAccumulatorWidth(valueAddr);
        int left = accumulatorValue();
        long result = (Integer.toUnsignedLong(left) - Integer.toUnsignedLong(value)) & 0xffff_ffffL;
        if (registers.p.m) {
            result &= 0xff;
        }
        int negativeMask = registers.p.m ? 0x80 : 0x8000;
        registers.p.n = (result & negativeMask) != 0;
        registers.p.z = result == 0;
        registers.p.c = left >= value;
        return registers.p.m ? 0 : 1;
    }

    public int ADC(int valueAddr) {
        int value = readAccumulatorWidth(valueAddr);
        if (registers.p.d) {
            return decimalAdd(value);
        }
        int carryIn = registers.p.c ? 1 : 0;
        int negativeMask = registers.p.m ? 0x80 : 0x8000;
        int maxValue = registers.p.m ? 0xff : 0xffff;
        int oldA = accumulatorValue();
        int result = oldA + value + carryIn;

        registers.p.c = result > maxValue;
        registers.p.v = (~(oldA ^ value) & (oldA ^ result) & negativeMask) != 0;
        setAccumulatorValue(result);
        setZNAccumulator(registers.a);
        return registers.p.m ? 0 : 1;
    }

    public int SBC(int valueAddr) {
        int value = readAccumulatorWidth(valueAddr);
        if (registers.p.d) {
            return decimalSubtract(value);
        }
        int negativeMask = registers.p.m ? 0x80 : 0x8000;
        int borrow = registers.p.c ? 0 : 1;
        int oldA = accumulatorValue();
        int rawResult = oldA - value - borrow;
        int result = normalizeAccumulator(rawResult);

        registers.p.c = rawResult >= 0;
        registers.p.v = ((oldA ^ result) & (oldA ^ value) & negativeMask) != 0;
        setAccumulatorValue(result);
        setZNAccumulator(registers.a);
        return registers.p.m ? 0 : 1;
    }

    public int INC(int valueAddr) {
        int result;
        if (registers.p.m) {
            result = (bus.read(valueAddr) + 1) & 0xff;
            bus.write(valueAddr, result);
        } else {
            int highAddress = nextOperandAddress(valueAddr);
            result = (bus.read(valueAddr) | (bus.read(highAddress) << 8)) + 1;
            result &= 0xffff;
            bus.write(valueAddr, result);
            bus.write(highAddress, result >>> 8);
        }
        setZNAccumulator(result);
        return registers.p.m ? 0 : 2;
    }

    public int DEC(int valueAddr) {
        int result;
        if (registers.p.m) {
            result = (bus.read(valueAddr) - 1) & 0xff;
            bus.write(valueAddr, result);
        } else {
            int highAddress = nextOperandAddress(valueAddr);
            result = (bus.read(valueAddr) | (bus.read(highAddress) << 8)) - 1;
            result &= 0xffff;
            bus.write(valueAddr, result);
            bus.write(highAddress, result >>> 8);
        }
        setZNAccumulator(result);
        return registers.p.m ? 0 : 2;
    }

    public int INA(int valueAddr) {
        setAccumulatorValue(accumulatorValue() + 1);
        setZNAccumulator(registers.a);
        return 0;
    }

    public int DEA(int valueAddr) {
        setAccumulatorValue(accumulatorValue() - 1);
        setZNAccumulator(registers.a);
        return 0;
    }

    public int XBA(int valueAddr) {
        int low = registers.al();
        registers.setAl(registers.ah());
        registers.setAh(low);
        registers.p.n = (registers.al() & 0x80) != 0;
        registers.p.z = registers.al() == 0;
        return 0;
    }

    public int TSB(int valueAddr) {
        int value = readAccumulatorWidth(valueAddr);
        int accumulator = accumulatorValue();
        int newValue = normalizeAccumulator(value | accumulator);
        bus.write(valueAddr, newValue);
        if (!registers.p.m) {
            bus.write(nextOperandAddress(valueAddr), newValue >>> 8);
        }
        registers.p.z = (value & accumulator) == 0;
        return registers.p.m ? 0 : 2;
    }

    public int TRB(int valueAddr) {
        int value = readAccumulatorWidth(valueAddr);
        int accumulator = accumulatorValue();
        int newValue = normalizeAccumulator(value & ~accumulator);
        bus.write(valueAddr, newValue);
        if (!registers.p.m) {
            bus.write(nextOperandAddress(valueAddr), newValue >>> 8);
        }
        registers.p.z = (value & accumulator) == 0;
        return registers.p.m ? 0 : 2;
    }

    public int BIT(int valueAddr, AddressingMode mode) {
        int negativeMask = registers.p.m ? 0x80 : 0x8000;
        int value = readAccumulatorWidth(valueAddr);

        if (mode != AddressingMode.IMMEDIATE_FOR_A) {
            registers.p.n = (value & negativeMask) != 0;
            registers.p.v = (value & (negativeMask >>> 1)) != 0;
        }
        registers.p.z = (value & accumulatorValue()) == 0;
        return registers.p.m ? 0 : 1;
    }

    public int ASL(int valueAddr, AddressingMode mode) {
        int highBit = registers.p.m ? 0x80 : 0x8000;
        if (mode == AddressingMode.IMPLIED) {
            int value = accumulatorValue();
            registers.p.c = (value & highBit) != 0;
            setAccumulatorValue(value << 1);
            setZNAccumulator(registers.a);
            return 0;
        }

        int value = readAccumulatorWidth(valueAddr);
        registers.p.c = (value & highBit) != 0;
        value = normalizeAccumulator(value << 1);
        registers.p.n = (value & highBit) != 0;
        registers.p.z = value == 0;
        writeAccumulatorWidth(valueAddr, value);
        return registers.p.m ? 0 : 2;
    }

    public int LSR(int valueAddr, AddressingMode mode) {
        registers.p.n = false;
        if (mode == AddressingMode.IMPLIED) {
            int value = accumulatorValue();
            registers.p.c = (value & 1) != 0;
            setAccumulatorValue(value >>> 1);
            registers.p.z = accumulatorValue() == 0;
            return 0;
        }

        int value = readAccumulatorWidth(valueAddr);
        registers.p.c = (value & 1) != 0;
        value = normalizeAccumulator(value >>> 1);
        registers.p.z = value == 0;
        writeAccumulatorWidth(valueAddr, value);
        return registers.p.m ? 0 : 2;
    }

    public int ROL(int valueAddr, AddressingMode mode) {
        int highBit = registers.p.m ? 0x80 : 0x8000;
        boolean oldCarry = registers.p.c;
        if (mode == AddressingMode.IMPLIED) {
            int value = accumulatorValue();
            registers.p.c = (value & highBit) != 0;
            setAccumulatorValue((value << 1) | (oldCarry ? 1 : 0));
            setZNAccumulator(registers.a);
            return 0;
        }

        int value = readAccumulatorWidth(valueAddr);
        registers.p.c = (value & highBit) != 0;
        value = normalizeAccumulator((value << 1) | (oldCarry ? 1 : 0));
        registers.p.n = (value & highBit) != 0;
        registers.p.z = value == 0;
        writeAccumulatorWidth(valueAddr, value);
        return registers.p.m ? 0 : 2;
    }

    public int ROR(int valueAddr, AddressingMode mode) {
        boolean oldCarry = registers.p.c;
        int highBitIndex = registers.p.m ? 7 : 15;
        if (mode == AddressingMode.IMPLIED) {
            int value = accumulatorValue();
            registers.p.c = (value & 1) != 0;
            setAccumulatorValue((value >>> 1) | ((oldCarry ? 1 : 0) << highBitIndex));
            setZNAccumulator(registers.a);
            return 0;
        }

        int value = readAccumulatorWidth(valueAddr);
        registers.p.c = (value & 1) != 0;
        value = normalizeAccumulator((value >>> 1) | ((oldCarry ? 1 : 0) << highBitIndex));
        setZNAccumulator(value);
        writeAccumulatorWidth(valueAddr, value);
        return registers.p.m ? 0 : 2;
    }

    private int readPC() {
        int result = bus.read(registers.pac);
        registers.incrementPc(1);
        return result;
    }

    private int readProgramWord(int address) {
        return bus.read(address) | (bus.read(nextProgramAddress(address)) << 8);
    }

    private int nextProgramAddress(int address) {
        return (address & 0xff0000) | u16(address + 1);
    }

    private int branch(int valueAddr, boolean condition) {
        if (!condition) {
            return 0;
        }
        int oldPc = registers.pc;
        int target = u16(oldPc + (byte) bus.read(valueAddr));
        registers.setPc(target);
        return 1 + relativePageCrossExtraCycle(oldPc, target);
    }

    private int relativePageCrossExtraCycle(int oldPc, int target) {
        return emulationMode && (oldPc & 0xff00) != (target & 0xff00) ? 1 : 0;
    }

    private int checkInterrupts() {
        if (!isNMIRequested && !isIRQRequested && !isAbortRequested) {
            return 0;
        }
        boolean wasWaitingForInterrupt = waitingForInterrupt;
        waitingForInterrupt = false;

        if (isAbortRequested) {
            isAbortRequested = false;
            if (wasWaitingForInterrupt) {
                registers.incrementPc(-1);
            }
            runInterrupt(cartridgeHeader.nativeInterrupts.abort, cartridgeHeader.emulationInterrupts.abort, false);
            return interruptEntryCycles();
        }
        if (isNMIRequested) {
            isNMIRequested = false;
            runInterrupt(cartridgeHeader.nativeInterrupts.nmi, cartridgeHeader.emulationInterrupts.nmi, false);
            return interruptEntryCycles();
        }
        if (isIRQRequested && !registers.p.i) {
            runInterrupt(cartridgeHeader.nativeInterrupts.irq, cartridgeHeader.emulationInterrupts.irq, false);
            return interruptEntryCycles();
        }
        return 0;
    }

    private int interruptEntryCycles() {
        return emulationMode ? 7 : 8;
    }

    private void runInterrupt(int nativeHandler, int emulationHandler, boolean softwareInterrupt) {
        int status = registers.p.flags();
        if (emulationMode) {
            status = (status | 0x20) & ~0x10;
            if (softwareInterrupt) {
                status |= 0x10;
            }
            _push16(registers.pc);
            _push8(status);
            registers.p.i = true;
            registers.p.d = false;
            registers.setPbr(0);
            registers.setPc(emulationHandler);
        } else {
            _push8(registers.pbr);
            _push16(registers.pc);
            _push8(status);
            registers.p.i = true;
            registers.p.d = false;
            registers.setPbr(0);
            registers.setPc(nativeHandler);
        }
    }

    private void enforceStatusWidth() {
        if (emulationMode) {
            registers.p.m = true;
            registers.p.x_b = true;
        }
        if (registers.p.x_b) {
            registers.x &= 0xff;
            registers.y &= 0xff;
        }
    }

    private int directPageExtraCycle() {
        return registers.dl() != 0 ? 1 : 0;
    }

    private int directPageAddress(int offset) {
        if (emulationMode && registers.dl() == 0) {
            return (registers.d & 0xff00) | u8(offset);
        }
        return u16(registers.d + offset);
    }

    private int nextDirectPageAddress(int address) {
        if (emulationMode && registers.dl() == 0) {
            return (address & 0xff00) | u8(address + 1);
        }
        return u16(address + 1);
    }

    private int indexBoundaryExtraCycle() {
        return !registers.p.x_b || hasIndexCrossedPageBoundary ? 1 : 0;
    }

    private int directPageIndexedYExtraCycle() {
        return directPageExtraCycle() + indexBoundaryExtraCycle();
    }

    private void setZN16(int value) {
        int normalized = u16(value);
        registers.p.z = normalized == 0;
        registers.p.n = (normalized & 0x8000) != 0;
    }

    private void setZNIndex(int value) {
        int normalized = registers.p.x_b ? u8(value) : u16(value);
        int negativeFlag = registers.p.x_b ? 0x80 : 0x8000;
        registers.p.z = normalized == 0;
        registers.p.n = (normalized & negativeFlag) != 0;
    }

    private void setZNAccumulator(int value) {
        int normalized = normalizeAccumulator(value);
        int negativeFlag = registers.p.m ? 0x80 : 0x8000;
        registers.p.z = normalized == 0;
        registers.p.n = (normalized & negativeFlag) != 0;
    }

    private int accumulatorValue() {
        return registers.p.m ? registers.al() : registers.a;
    }

    private int indexXValue() {
        return registers.p.x_b ? registers.xl() : registers.x;
    }

    private int indexYValue() {
        return registers.p.x_b ? registers.yl() : registers.y;
    }

    private int decimalAdd(int value) {
        int left = accumulatorValue();
        int carryIn = registers.p.c ? 1 : 0;
        int result = 0;
        int overflowResult = 0;
        int carry = carryIn;
        int digits = registers.p.m ? 2 : 4;

        for (int digit = 0; digit < digits; digit++) {
            int shift = digit * 4;
            int sum = ((left >>> shift) & 0x0f) + ((value >>> shift) & 0x0f) + carry;
            if (digit == digits - 1) {
                overflowResult = (sum & 0x0f) << shift;
            }
            if (sum > 9) {
                sum += 6;
                carry = 1;
            } else {
                carry = 0;
            }
            result |= (sum & 0x0f) << shift;
        }

        registers.p.c = carry != 0;
        int negativeMask = registers.p.m ? 0x80 : 0x8000;
        registers.p.v = (~(left ^ value) & (left ^ overflowResult) & negativeMask) != 0;
        setAccumulatorValue(result);
        setZNAccumulator(registers.a);
        return registers.p.m ? 0 : 1;
    }

    private int decimalSubtract(int value) {
        int left = accumulatorValue();
        int borrowIn = registers.p.c ? 0 : 1;
        int binaryResult = left - value - borrowIn;
        int result = 0;
        int borrow = borrowIn;
        int digits = registers.p.m ? 2 : 4;

        for (int digit = 0; digit < digits; digit++) {
            int shift = digit * 4;
            int difference = ((left >>> shift) & 0x0f) - ((value >>> shift) & 0x0f) - borrow;
            if (difference < 0) {
                difference -= 6;
                borrow = 1;
            } else {
                borrow = 0;
            }
            result |= (difference & 0x0f) << shift;
        }

        registers.p.c = borrow == 0;
        int negativeMask = registers.p.m ? 0x80 : 0x8000;
        registers.p.v = ((left ^ value) & (left ^ binaryResult) & negativeMask) != 0;
        setAccumulatorValue(result);
        setZNAccumulator(registers.a);
        return registers.p.m ? 0 : 1;
    }

    private void setAccumulatorValue(int value) {
        if (registers.p.m) {
            registers.setAl(value);
        } else {
            registers.a = u16(value);
        }
    }

    private int readAccumulatorWidth(int valueAddr) {
        return registers.p.m ? bus.read(valueAddr) : readOperandWord(valueAddr);
    }

    private void writeAccumulatorWidth(int valueAddr, int value) {
        bus.write(valueAddr, value);
        if (!registers.p.m) {
            bus.write(nextOperandAddress(valueAddr), value >>> 8);
        }
    }

    private int normalizeAccumulator(int value) {
        return registers.p.m ? (value & 0xff) : u16(value);
    }

    private int compareIndex(int registerValue, int valueAddr) {
        int value = bus.read(valueAddr);
        int result;
        if (registers.p.x_b) {
            int left = registerValue & 0xff;
            result = (left - value) & 0xff;
            registers.p.z = result == 0;
            registers.p.n = (result & 0x80) != 0;
            registers.p.c = left >= value;
            return 0;
        }

        value = readOperandWord(valueAddr);
        int left = registerValue & 0xffff;
        result = (left - value) & 0xffff;
        registers.p.z = result == 0;
        registers.p.n = (result & 0x8000) != 0;
        registers.p.c = left >= value;
        return 1;
    }

    private int readOperandWord(int address) {
        return bus.read(address) | (bus.read(nextOperandAddress(address)) << 8);
    }

    private int nextOperandAddress(int address) {
        return operandWrapsBank ? nextProgramAddress(address) : u24(address + 1);
    }

    private void markIndexBoundary(int base, int index) {
        if ((base & 0xff00) != ((base + index) & 0xff00)) {
            hasIndexCrossedPageBoundary = true;
        }
    }

    @Override
    public String getName() {
        return "CPU";
    }

    private final class CpuTimingBus implements IMemoryBus {
        private final IMemoryBus delegate;

        private CpuTimingBus(IMemoryBus delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read(int address) {
            busMasterClockSurcharge += busAccessMasterClocks(address) - MASTER_CLOCKS_PER_CPU_CYCLE;
            return delegate.read(address);
        }

        @Override
        public OptionalInt peek(int address) {
            return delegate.peek(address);
        }

        @Override
        public int peekValue(int address) {
            return delegate.peekValue(address);
        }

        @Override
        public int getOpenBus() {
            return delegate.getOpenBus();
        }

        @Override
        public int getExternalOpenBus() {
            return delegate.getExternalOpenBus();
        }

        @Override
        public void write(int address, int data) {
            busMasterClockSurcharge += busAccessMasterClocks(address) - MASTER_CLOCKS_PER_CPU_CYCLE;
            delegate.write(address, data);
        }

        @Override
        public IMemory getAccessor(int address) {
            return delegate.getAccessor(address);
        }
    }

    @Override
    public String getValueName(int address) {
        if (address >= 0x100 && address < 0x180) {
            int channel = (address - 0x100) >>> 4;
            return switch (address & 0x0f) {
                case 0x0 -> "DMAP" + channel;
                case 0x1 -> "BBAD" + channel;
                case 0x2 -> "A1T" + channel + "L";
                case 0x3 -> "A1T" + channel + "H";
                case 0x4 -> "A1B" + channel;
                case 0x5 -> "DAS" + channel + "L";
                case 0x6 -> "DAS" + channel + "H";
                case 0x7 -> "DASB" + channel;
                case 0x8 -> "A2A" + channel + "L";
                case 0x9 -> "A2A" + channel + "H";
                case 0xa -> "NTRL" + channel;
                case 0xb -> "UNUSED" + channel;
                case 0xf -> "MIRR" + channel;
                default -> "???";
            };
        }
        return switch (address) {
            case 0x00 -> "NMITIMEN";
            case 0x01 -> "WRIO";
            case 0x02 -> "WRMPYA";
            case 0x03 -> "WRMPYB";
            case 0x04 -> "WRDIVL";
            case 0x05 -> "WRDIVH";
            case 0x06 -> "WRDIVB";
            case 0x07 -> "HTIMEL";
            case 0x08 -> "HTIMEH";
            case 0x09 -> "VTIMEL";
            case 0x0a -> "VTIMEH";
            case 0x0b -> "MDMAEN";
            case 0x0c -> "HDMAEN";
            case 0x0d -> "MEMSEL";
            case 0x10 -> "RDNMI";
            case 0x11 -> "TIMEUP";
            case 0x12 -> "HVBJOY";
            case 0x13 -> "RDIO";
            case 0x14 -> "RDDIVL";
            case 0x15 -> "RDDIVH";
            case 0x16 -> "RDMPYL";
            case 0x17 -> "RDMPYH";
            case 0x18 -> "JOY1L";
            case 0x19 -> "JOY1H";
            case 0x1a -> "JOY2L";
            case 0x1b -> "JOY2H";
            case 0x1c -> "JOY3L";
            case 0x1d -> "JOY3H";
            case 0x1e -> "JOY4L";
            case 0x1f -> "JOY4H";
            default -> "???";
        };
    }

    @Override
    public Component getComponent() {
        return Component.CPU;
    }
}
