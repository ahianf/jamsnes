package jamsnes.cpu;

import jamsnes.cartridge.Header;
import jamsnes.exceptions.InvalidAddress;
import jamsnes.exceptions.InvalidOpcode;
import jamsnes.memory.AMemory;
import jamsnes.memory.IMemoryBus;
import jamsnes.models.Component;

import static jamsnes.models.Unsigned.u16;
import static jamsnes.models.Unsigned.u24;
import static jamsnes.models.Unsigned.u8;

public class CPU extends AMemory {
    private final Registers registers = new Registers();
    private final int[] internalRegisters = new int[0x300];
    private final DMA[] dmaChannels = new DMA[8];
    private final Header cartridgeHeader;
    private IMemoryBus bus;
    private boolean hasIndexCrossedPageBoundary;
    private boolean emulationMode = true;
    private boolean stopped;
    private boolean waitingForInterrupt;
    public boolean isNMIRequested;
    public boolean isIRQRequested;
    public boolean isAbortRequested;
    public boolean isDisabled;

    public CPU(IMemoryBus bus, Header cartridgeHeader) {
        this.bus = bus;
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
        this.bus = bus;
        for (DMA dmaChannel : dmaChannels) {
            dmaChannel.setBus(bus);
        }
    }

    public IMemoryBus getBus() {
        return bus;
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
    }

    public boolean isStopped() {
        return stopped;
    }

    public boolean isWaitingForInterrupt() {
        return waitingForInterrupt;
    }

    @Override
    public int read(int address) {
        if (address == 0x0b) {
            int value = 0;
            for (int i = 0; i < dmaChannels.length; i++) {
                if (dmaChannels[i].isEnabled()) {
                    value |= 1 << i;
                }
            }
            return value;
        }
        if (address == 0x10) {
            return readNmiStatus();
        }
        if (address == 0x11) {
            return readIrqStatus();
        }
        if (address >= 0x100 && address < 0x180) {
            return dmaChannels[(address - 0x100) >>> 4].read(address & 0x0f);
        }
        if (!isInternalRegister(address)) {
            throw new InvalidAddress("CPU Internal Registers read", address + start);
        }
        return internalRegisters[address];
    }

    @Override
    public void write(int address, int data) {
        int value = u8(data);
        if (address == 0x0b) {
            internalRegisters[address] = value;
            for (int i = 0; i < dmaChannels.length; i++) {
                dmaChannels[i].setEnabled((value & (1 << i)) != 0);
            }
            return;
        }
        if (address >= 0x100 && address < 0x180) {
            dmaChannels[(address - 0x100) >>> 4].write(address & 0x0f, data);
            return;
        }
        if (!isInternalRegister(address)) {
            throw new InvalidAddress("CPU Internal Registers write", address + start);
        }
        internalRegisters[address] = value;
        if (address == 0x03) {
            runMultiplication();
        } else if (address == 0x06) {
            runDivision();
        }
    }

    private boolean isInternalRegister(int address) {
        return (address >= 0x00 && address <= 0x0d) || (address >= 0x10 && address <= 0x1f);
    }

    private void runMultiplication() {
        int result = internalRegisters[0x02] * internalRegisters[0x03];
        internalRegisters[0x16] = u8(result);
        internalRegisters[0x17] = u8(result >>> 8);
    }

    private void runDivision() {
        int dividend = internalRegisters[0x04] | (internalRegisters[0x05] << 8);
        int divisor = internalRegisters[0x06];
        int quotient;
        int remainder;
        if (divisor == 0) {
            quotient = 0xffff;
            remainder = dividend;
        } else {
            quotient = dividend / divisor;
            remainder = dividend % divisor;
        }
        internalRegisters[0x14] = u8(quotient);
        internalRegisters[0x15] = u8(quotient >>> 8);
        internalRegisters[0x16] = u8(remainder);
        internalRegisters[0x17] = u8(remainder >>> 8);
    }

    private int readNmiStatus() {
        int value = internalRegisters[0x10];
        internalRegisters[0x10] = value & 0x7f;
        isNMIRequested = false;
        return value;
    }

    private int readIrqStatus() {
        int value = internalRegisters[0x11];
        internalRegisters[0x11] = value & 0x7f;
        isIRQRequested = false;
        return value;
    }

    public int[] internalRegisters() {
        return internalRegisters;
    }

    public DMA[] dmaChannels() {
        return dmaChannels;
    }

    public void requestNMI() {
        isNMIRequested = true;
        internalRegisters[0x10] |= 0x80;
    }

    public void requestIRQ() {
        isIRQRequested = true;
        internalRegisters[0x11] |= 0x80;
    }

    public int update(int maxCycles) {
        if (isDisabled) {
            return 0xff;
        }
        int cycles = runDMA(maxCycles);

        while (cycles < maxCycles) {
            if (stopped) {
                cycles++;
                continue;
            }

            checkInterrupts();

            if (!waitingForInterrupt) {
                cycles += executeInstruction();
            } else {
                return 0xff;
            }
        }
        return cycles;
    }

    public int runDMA(int maxCycles) {
        int cycles = 0;
        for (DMA dmaChannel : dmaChannels) {
            if (!dmaChannel.isEnabled()) {
                continue;
            }
            cycles += dmaChannel.run(maxCycles - cycles);
        }
        return cycles;
    }

    public int executeInstruction() {
        int opcode = readPC();
        hasIndexCrossedPageBoundary = false;
        return switch (opcode) {
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
            case 0x0d -> 3 + ORA(_getAbsoluteAddr());
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
            case 0x1e -> 7 + ASL(_getAbsoluteIndexedByXAddr(), AddressingMode.ABSOLUTE_INDEXED_BY_X) + indexBoundaryExtraCycle();
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
            case 0x3e -> 7 + ROL(_getAbsoluteIndexedByXAddr(), AddressingMode.ABSOLUTE_INDEXED_BY_X) + indexBoundaryExtraCycle();
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
            case 0x53 -> 4 + EOR(_getStackRelativeIndirectIndexedYAddr());
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
            case 0x5e -> 7 + LSR(_getAbsoluteIndexedByXAddr(), AddressingMode.ABSOLUTE_INDEXED_BY_X) + indexBoundaryExtraCycle();
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
            case 0x7e -> 7 + ROR(_getAbsoluteIndexedByXAddr(), AddressingMode.ABSOLUTE_INDEXED_BY_X) + indexBoundaryExtraCycle();
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
            case 0xbc -> 4 + LDY(_getAbsoluteIndexedByXAddr());
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
            case 0xcf -> 6 + CMP(_getAbsoluteLongAddr());
            case 0xd0 -> 2 + BNE(_getImmediateAddr8Bits());
            case 0xd1 -> 5 + CMP(_getDirectIndirectIndexedYAddr()) + directPageIndexedYExtraCycle();
            case 0xd2 -> 5 + CMP(_getDirectIndirectAddr()) + directPageExtraCycle();
            case 0xd3 -> 7 + CMP(_getStackRelativeIndirectIndexedYAddr());
            case 0xd4 -> 6 + PEI(_getDirectAddr());
            case 0xd5 -> 4 + CMP(_getDirectIndexedByXAddr()) + directPageExtraCycle();
            case 0xd6 -> 6 + DEC(_getDirectIndexedByXAddr()) + directPageExtraCycle();
            case 0xd7 -> 6 + CMP(_getDirectIndirectIndexedYLongAddr()) + directPageExtraCycle();
            case 0xd8 -> 2 + CLD(0);
            case 0xd9 -> 4 + CMP(_getAbsoluteIndexedByYAddr()) + indexBoundaryExtraCycle();
            case 0xda -> 3 + PHX(0);
            case 0xdb -> 3 + STP(0);
            case 0xdc -> 7 + JML(_getAbsoluteIndirectLongAddr());
            case 0xdd -> 4 + CMP(_getAbsoluteIndexedByXAddr()) + indexBoundaryExtraCycle();
            case 0xde -> 7 + DEC(_getAbsoluteIndexedByXAddr()) + indexBoundaryExtraCycle();
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
            case 0xfc -> 8 + JSR(_getAbsoluteIndirectIndexedByXAddr());
            case 0xfd -> 4 + SBC(_getAbsoluteIndexedByXAddr()) + indexBoundaryExtraCycle();
            case 0xfe -> 7 + INC(_getAbsoluteIndexedByXAddr()) + indexBoundaryExtraCycle();
            case 0xff -> 5 + SBC(_getAbsoluteIndexedByXLongAddr());
            default -> throw new InvalidOpcode("CPU opcode 0x%02x is not implemented".formatted(opcode));
        };
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
        registers.incrementPc(registers.p.m ? 1 : 2);
        return effective;
    }

    public int _getImmediateAddrForX() {
        int effective = registers.pac;
        registers.incrementPc(registers.p.x_b ? 1 : 2);
        return effective;
    }

    public int _getDirectAddr() {
        int address = readPC();
        return u16(registers.d + address);
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
        int dp = u16(readPC() + registers.d);
        int base = bus.read(dp);
        base += bus.read(u16(dp + 1)) << 8;
        base += registers.dbr << 16;
        markIndexBoundary(base, registers.y);
        return u24(base + registers.y);
    }

    public int _getDirectIndirectIndexedYLongAddr() {
        int dp = u16(readPC() + registers.d);
        int base = bus.read(dp);
        base += bus.read(u16(dp + 1)) << 8;
        base += bus.read(u16(dp + 2)) << 16;
        return u24(base);
    }

    public int _getDirectIndirectIndexedXAddr() {
        int dp = u16(readPC() + registers.d);
        dp = u16(dp + registers.x);
        int base = bus.read(dp);
        base += bus.read(u16(dp + 1)) << 8;
        base += registers.dbr << 16;
        return u24(base);
    }

    public int _getDirectIndexedByXAddr() {
        int dp = u16(readPC() + registers.d);
        return u16(dp + registers.x);
    }

    public int _getDirectIndexedByYAddr() {
        int dp = u16(readPC() + registers.d);
        return u16(dp + registers.y);
    }

    public int _getAbsoluteIndexedByXAddr() {
        int abs = readPC();
        abs = u16(abs + (readPC() << 8));
        int effective = abs + (registers.dbr << 16);
        markIndexBoundary(effective, registers.x);
        return u24(effective + registers.x);
    }

    public int _getAbsoluteIndexedByYAddr() {
        int abs = readPC();
        abs = u16(abs + (readPC() << 8));
        int effective = abs + (registers.dbr << 16);
        markIndexBoundary(effective, registers.y);
        return u24(effective + registers.y);
    }

    public int _getAbsoluteIndexedByXLongAddr() {
        int value = readPC();
        value += readPC() << 8;
        value += readPC() << 16;
        return u24(value + registers.x);
    }

    public int _getAbsoluteIndirectAddr() {
        int abs = readPC();
        abs = u16(abs + (readPC() << 8));
        int effective = bus.read(abs);
        effective += bus.read(abs + 1) << 8;
        return u24(effective);
    }

    public int _getAbsoluteIndirectLongAddr() {
        int abs = readPC();
        abs = u16(abs + (readPC() << 8));
        int effective = bus.read(abs);
        effective += bus.read(abs + 1) << 8;
        effective += bus.read(abs + 2) << 16;
        return u24(effective);
    }

    public int _getAbsoluteIndirectIndexedByXAddr() {
        int abs = readPC();
        abs = u16(abs + (readPC() << 8));
        abs = u24(abs + registers.x);
        int effective = bus.read(abs);
        effective += bus.read(abs + 1) << 8;
        return u24(effective);
    }

    public int _getDirectIndirectAddr() {
        int dp = u16(readPC() + registers.d);
        int effective = bus.read(dp);
        effective += bus.read(u16(dp + 1)) << 8;
        effective += registers.dbr << 16;
        return u24(effective);
    }

    public int _getDirectIndirectLongAddr() {
        int dp = u16(readPC() + registers.d);
        int effective = bus.read(dp);
        dp = u16(dp + 1);
        effective += bus.read(dp) << 8;
        dp = u16(dp + 1);
        effective += bus.read(dp) << 16;
        return u24(effective);
    }

    public int _getStackRelativeAddr() {
        return u24(readPC() + registers.s);
    }

    public int _getStackRelativeIndirectIndexedYAddr() {
        int pointer = u16(readPC() + registers.s);
        int base = bus.read(pointer) | (bus.read(u16(pointer + 1)) << 8);
        return u24((registers.dbr << 16) + base + registers.y);
    }

    public void _push8(int data) {
        bus.write(registers.s, data);
        registers.s = u16(registers.s - 1);
    }

    public void _push16(int data) {
        bus.write(registers.s, data >>> 8);
        registers.s = u16(registers.s - 1);
        bus.write(registers.s, data);
        registers.s = u16(registers.s - 1);
    }

    public int _pop() {
        registers.s = u16(registers.s + 1);
        return bus.read(registers.s);
    }

    public int _pop16() {
        registers.s = u16(registers.s + 1);
        int value = bus.read(registers.s);
        registers.s = u16(registers.s + 1);
        value += bus.read(registers.s) << 8;
        return u16(value);
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
        return 0;
    }

    public int REP(int valueAddr) {
        registers.p.setFlags(registers.p.flags() & ~bus.read(valueAddr));
        if (emulationMode) {
            registers.p.x_b = true;
            registers.p.m = true;
        }
        return 0;
    }

    public int JSR(int valueAddr) {
        registers.setPc(registers.pc - 1);
        _push16(registers.pc);
        registers.setPc(valueAddr);
        return 0;
    }

    public int JSL(int valueAddr) {
        registers.setPac(registers.pac - 1);
        _push8(registers.pbr);
        _push16(registers.pc);
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
        _push16(registers.d);
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
        registers.d = _pop16();
        setZN16(registers.d);
        return 0;
    }

    public int PLP(int valueAddr) {
        registers.p.setFlags(_pop());
        if (emulationMode) {
            registers.p.m = true;
            registers.p.x_b = true;
        }
        return 0;
    }

    public int PLX(int valueAddr) {
        if (registers.p.x_b) {
            registers.x = u16((registers.x & 0xff00) | _pop());
        } else {
            registers.x = _pop16();
        }
        setZNIndex(registers.x);
        return registers.p.x_b ? 0 : 1;
    }

    public int PLY(int valueAddr) {
        if (registers.p.x_b) {
            registers.y = u16((registers.y & 0xff00) | _pop());
        } else {
            registers.y = _pop16();
        }
        setZNIndex(registers.y);
        return registers.p.x_b ? 0 : 1;
    }

    public int PER(int valueAddr) {
        int value = bus.read(valueAddr) | (bus.read(valueAddr + 1) << 8);
        value = u16(value + registers.pc);
        _push16(value);
        return 0;
    }

    public int PEA(int value) {
        _push16(value);
        return 0;
    }

    public int PEI(int value) {
        int effective = bus.read(value) | (bus.read(value + 1) << 8);
        _push16(effective);
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
            registers.x &= 0xff;
            registers.y &= 0xff;
        }
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
        registers.setPc(registers.pc + (byte) bus.read(valueAddr));
        return emulationMode ? 1 : 0;
    }

    public int BRL(int valueAddr) {
        int value = bus.read(valueAddr) | (bus.read(valueAddr + 1) << 8);
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
        registers.setPc(_pop16() + 1);
        registers.setPbr(_pop());
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
        registers.dbr = 0;
        registers.setPbr(0);
        registers.d = 0;
        registers.s = 0x0100 | registers.sl();
        registers.setPc(cartridgeHeader.emulationInterrupts.reset);
        stopped = false;
        return 0;
    }

    public int BRK(int valueAddr) {
        runInterrupt(cartridgeHeader.nativeInterrupts.brk, cartridgeHeader.emulationInterrupts.brk);
        return emulationMode ? 0 : 1;
    }

    public int COP(int valueAddr) {
        runInterrupt(cartridgeHeader.nativeInterrupts.cop, cartridgeHeader.emulationInterrupts.cop);
        return emulationMode ? 0 : 1;
    }

    public int RTI(int valueAddr) {
        registers.p.setFlags(_pop());
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
            registers.x = u16((registers.x & 0xff00) | registers.al());
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
            registers.y = u16((registers.y & 0xff00) | registers.al());
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
            registers.p.z = registers.s == 0;
            registers.p.n = (registers.s & 0x80) != 0;
        } else {
            registers.s = u16(registers.x);
            registers.p.z = registers.s == 0;
            registers.p.n = (registers.s & 0x8000) != 0;
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
        int negativeFlag = registers.p.m ? 0x80 : 0x8000;
        if (registers.p.m) {
            registers.setAl(registers.xl());
        } else {
            registers.a = u16(registers.x);
            if (registers.p.x_b) {
                registers.setAh(0);
            }
        }
        registers.p.n = (registers.a & negativeFlag) != 0;
        registers.p.z = registers.a == 0;
        return 0;
    }

    public int TYA(int valueAddr) {
        int negativeFlag = registers.p.m ? 0x80 : 0x8000;
        if (registers.p.m) {
            registers.setAl(registers.yl());
        } else {
            registers.a = u16(registers.y);
            if (registers.p.x_b) {
                registers.setAh(0);
            }
        }
        registers.p.n = (registers.a & negativeFlag) != 0;
        registers.p.z = registers.a == 0;
        return 0;
    }

    public int TXY(int valueAddr) {
        if (registers.p.x_b) {
            registers.y = u16((registers.y & 0xff00) | registers.xl());
        } else {
            registers.y = u16(registers.x);
        }
        setZNIndex(registers.y);
        return 0;
    }

    public int TYX(int valueAddr) {
        if (registers.p.x_b) {
            registers.x = u16((registers.x & 0xff00) | registers.yl());
        } else {
            registers.x = u16(registers.y);
        }
        setZNIndex(registers.x);
        return 0;
    }

    public int MVN(int valueAddr) {
        int srcBank = bus.read(valueAddr);
        int destBank = bus.read(valueAddr + 1);
        int length = registers.a + 1;

        registers.dbr = destBank;
        while (registers.a != 0xffff) {
            int data = bus.read(u24((srcBank << 16) | registers.x));
            bus.write(u24((destBank << 16) | registers.y), data);
            registers.x = u16(registers.x + 1);
            registers.y = u16(registers.y + 1);
            registers.a = u16(registers.a - 1);
        }
        return 7 * length;
    }

    public int MVP(int valueAddr) {
        int srcBank = bus.read(valueAddr);
        int destBank = bus.read(valueAddr + 1);
        int length = registers.a + 1;

        registers.dbr = destBank;
        while (registers.a != 0xffff) {
            int data = bus.read(u24((srcBank << 16) | registers.x));
            bus.write(u24((destBank << 16) | registers.y), data);
            registers.x = u16(registers.x - 1);
            registers.y = u16(registers.y - 1);
            registers.a = u16(registers.a - 1);
        }
        return 7 * length;
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
            bus.write(address + 1, registers.ah());
        }
        return registers.p.m ? 0 : 1;
    }

    public int STX(int address) {
        bus.write(address, registers.xl());
        if (!registers.p.x_b) {
            bus.write(address + 1, registers.xh());
        }
        return registers.p.x_b ? 0 : 1;
    }

    public int STY(int address) {
        bus.write(address, registers.yl());
        if (!registers.p.x_b) {
            bus.write(address + 1, registers.yh());
        }
        return registers.p.x_b ? 0 : 1;
    }

    public int STZ(int address) {
        bus.write(address, 0);
        if (!registers.p.m) {
            bus.write(address + 1, 0);
        }
        return registers.p.m ? 0 : 1;
    }

    public int LDA(int address) {
        if (registers.p.m) {
            registers.setAl(bus.read(address));
        } else {
            registers.setAl(bus.read(address));
            registers.setAh(bus.read(address + 1));
        }
        setZNAccumulator(registers.a);
        return registers.p.m ? 0 : 1;
    }

    public int LDX(int address) {
        if (registers.p.x_b) {
            registers.x = bus.read(address);
        } else {
            registers.x = bus.read(address) | (bus.read(address + 1) << 8);
        }
        setZNIndex(registers.x);
        return registers.p.x_b ? 0 : 1;
    }

    public int LDY(int address) {
        if (registers.p.x_b) {
            registers.y = bus.read(address);
        } else {
            registers.y = bus.read(address) | (bus.read(address + 1) << 8);
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
        value += registers.p.c ? 1 : 0;
        int negativeMask = registers.p.m ? 0x80 : 0x8000;
        int maxValue = registers.p.m ? 0xff : 0xffff;
        int oldA = accumulatorValue();
        int result = oldA + value;

        registers.p.c = result > maxValue;
        if ((oldA & negativeMask) == (value & negativeMask)) {
            registers.p.v = (oldA & negativeMask) != (result & negativeMask);
        } else {
            registers.p.v = false;
        }
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
        boolean oldCarry = registers.p.c;
        int oldA = accumulatorValue();

        registers.p.c = oldA >= value;
        if ((oldA & negativeMask) == (value & negativeMask)) {
            registers.p.v = (oldA & negativeMask) != ((oldA + value) & negativeMask);
        } else {
            registers.p.v = false;
        }
        setAccumulatorValue(oldA + ~value + (oldCarry ? 1 : 0));
        setZNAccumulator(registers.a);
        return registers.p.m ? 0 : 1;
    }

    public int INC(int valueAddr) {
        int result;
        if (registers.p.m) {
            result = (bus.read(valueAddr) + 1) & 0xff;
            bus.write(valueAddr, result);
        } else {
            result = (bus.read(valueAddr) | (bus.read(valueAddr + 1) << 8)) + 1;
            result &= 0xffff;
            bus.write(valueAddr, result);
            bus.write(valueAddr + 1, result >>> 8);
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
            result = (bus.read(valueAddr) | (bus.read(valueAddr + 1) << 8)) - 1;
            result &= 0xffff;
            bus.write(valueAddr, result);
            bus.write(valueAddr + 1, result >>> 8);
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
            bus.write(valueAddr + 1, newValue >>> 8);
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
            bus.write(valueAddr + 1, newValue >>> 8);
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
        registers.p.z = (value & registers.a) == 0;
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

    private int branch(int valueAddr, boolean condition) {
        if (condition) {
            registers.setPc(registers.pc + (byte) bus.read(valueAddr));
        }
        return condition ? 1 + (emulationMode ? 1 : 0) : 0;
    }

    private void checkInterrupts() {
        if (!isNMIRequested && !isIRQRequested && !isAbortRequested) {
            return;
        }
        waitingForInterrupt = false;

        if (isNMIRequested) {
            isNMIRequested = false;
            runInterrupt(cartridgeHeader.nativeInterrupts.nmi, cartridgeHeader.emulationInterrupts.nmi);
            return;
        }
        if (isIRQRequested && !registers.p.i) {
            isIRQRequested = false;
            runInterrupt(cartridgeHeader.nativeInterrupts.irq, cartridgeHeader.emulationInterrupts.irq);
        }
    }

    private void runInterrupt(int nativeHandler, int emulationHandler) {
        if (emulationMode) {
            _push16(registers.pc);
            _push8(registers.p.flags());
            registers.p.i = true;
            registers.p.d = false;
            registers.setPbr(0);
            registers.setPc(emulationHandler);
        } else {
            _push8(registers.pbr);
            _push16(registers.pc);
            _push8(registers.p.flags());
            registers.p.i = true;
            registers.p.d = false;
            registers.setPbr(0);
            registers.setPc(nativeHandler);
        }
    }

    private int directPageExtraCycle() {
        return registers.dl() != 0 ? 1 : 0;
    }

    private int indexBoundaryExtraCycle() {
        return hasIndexCrossedPageBoundary ? 1 : 0;
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

    private int decimalAdd(int value) {
        int left = accumulatorValue();
        int carryIn = registers.p.c ? 1 : 0;
        int binaryResult = left + value + carryIn;
        int result = 0;
        int carry = carryIn;
        int digits = registers.p.m ? 2 : 4;

        for (int digit = 0; digit < digits; digit++) {
            int shift = digit * 4;
            int sum = ((left >>> shift) & 0x0f) + ((value >>> shift) & 0x0f) + carry;
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
        registers.p.v = (~(left ^ value) & (left ^ binaryResult) & negativeMask) != 0;
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
        int value = bus.read(valueAddr);
        if (!registers.p.m) {
            value |= bus.read(valueAddr + 1) << 8;
        }
        return value;
    }

    private void writeAccumulatorWidth(int valueAddr, int value) {
        bus.write(valueAddr, value);
        if (!registers.p.m) {
            bus.write(valueAddr + 1, value >>> 8);
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

        value |= bus.read(valueAddr + 1) << 8;
        int left = registerValue & 0xffff;
        result = (left - value) & 0xffff;
        registers.p.z = result == 0;
        registers.p.n = (result & 0x8000) != 0;
        registers.p.c = left >= value;
        return 1;
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
