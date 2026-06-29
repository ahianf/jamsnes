package jamsnes.cpu;

import jamsnes.cartridge.Header;
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
        if (address >= 0x100 && address < 0x180) {
            return dmaChannels[(address - 0x100) >>> 4].read(address & 0x0f);
        }
        return internalRegisters[address];
    }

    @Override
    public void write(int address, int data) {
        if (address == 0x0b) {
            int value = u8(data);
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
        internalRegisters[address] = u8(data);
    }

    public int[] internalRegisters() {
        return internalRegisters;
    }

    public DMA[] dmaChannels() {
        return dmaChannels;
    }

    public int executeInstruction() {
        int opcode = readPC();
        hasIndexCrossedPageBoundary = false;
        return switch (opcode) {
            case 0x08 -> 3 + PHP(0);
            case 0x0b -> 4 + PHD(0);
            case 0x10 -> 7 + BPL(_getImmediateAddr8Bits());
            case 0x18 -> 2 + CLC(0);
            case 0x20 -> 6 + JSR(_getAbsoluteAddr());
            case 0x22 -> 8 + JSL(_getAbsoluteLongAddr());
            case 0x30 -> 2 + BMI(_getImmediateAddr8Bits());
            case 0x38 -> 2 + SEC(0);
            case 0x48 -> 3 + PHA(0);
            case 0x4b -> 3 + PHK(0);
            case 0x4c -> 3 + JMP(_getAbsoluteAddr());
            case 0x50 -> 2 + BVC(_getImmediateAddr8Bits());
            case 0x58 -> 2 + CLI(0);
            case 0x5a -> 3 + PHY(0);
            case 0x60 -> 6 + RTS(0);
            case 0x6b -> 6 + RTL(0);
            case 0x6c -> 5 + JMP(_getAbsoluteIndirectAddr());
            case 0x70 -> 2 + BVS(_getImmediateAddr8Bits());
            case 0x78 -> 2 + SEI(0);
            case 0x7c -> 6 + JMP(_getAbsoluteIndirectIndexedByXAddr());
            case 0x80 -> 3 + BRA(_getImmediateAddr8Bits());
            case 0x8b -> 3 + PHB(0);
            case 0x90 -> 2 + BCC(_getImmediateAddr8Bits());
            case 0xb0 -> 2 + BCS(_getImmediateAddr8Bits());
            case 0xb8 -> 7 + CLV(0);
            case 0xc2 -> 3 + REP(_getImmediateAddr8Bits());
            case 0xcb -> 3 + WAI(0);
            case 0xd0 -> 2 + BNE(_getImmediateAddr8Bits());
            case 0xd8 -> 2 + CLD(0);
            case 0xda -> 3 + PHX(0);
            case 0xdb -> 3 + STP(0);
            case 0xe2 -> 3 + SEP(_getImmediateAddr8Bits());
            case 0xea -> 2 + NOP(0);
            case 0xf0 -> 2 + BEQ(_getImmediateAddr8Bits());
            case 0xf8 -> 2 + SED(0);
            case 0xfb -> 2 + XCE(0);
            case 0xfc -> 8 + JSR(_getAbsoluteIndirectIndexedByXAddr());
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
        base += bus.read(dp + 1) << 8;
        base += registers.dbr << 16;
        markIndexBoundary(base, registers.y);
        return u24(base + registers.y);
    }

    public int _getDirectIndirectIndexedYLongAddr() {
        int dp = u16(readPC() + registers.d);
        int base = bus.read(dp);
        base += bus.read(dp + 1) << 8;
        base += bus.read(dp + 2) << 16;
        return u24(base);
    }

    public int _getDirectIndirectIndexedXAddr() {
        int dp = u16(readPC() + registers.d);
        dp = u16(dp + registers.x);
        int base = bus.read(dp);
        base += bus.read(dp + 1) << 8;
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
        effective += bus.read(dp + 1) << 8;
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
        int base = readPC() + registers.s;
        base += registers.dbr << 16;
        return u24(base + registers.y);
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
        _push16(value);
        return 0;
    }

    public int XCE(int valueAddr) {
        boolean oldCarry = registers.p.c;
        registers.p.c = emulationMode;
        emulationMode = oldCarry;

        if (!emulationMode) {
            registers.p.m = true;
            registers.p.x_b = true;
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
        registers.dbr = _pop();
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
            registers.setPbr(_pop16());
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
        int negativeFlag = registers.p.x_b ? 0x80 : 0x8000;
        if (registers.p.x_b) {
            registers.y = u16((registers.y & 0xff00) | registers.xl());
        } else {
            registers.y = u16(registers.x);
        }
        registers.p.n = (registers.y & negativeFlag) != 0;
        registers.p.z = registers.y == 0;
        return 0;
    }

    public int TYX(int valueAddr) {
        int negativeFlag = registers.p.x_b ? 0x80 : 0x8000;
        if (registers.p.x_b) {
            registers.x = u16((registers.x & 0xff00) | registers.yl());
        } else {
            registers.x = u16(registers.y);
        }
        registers.p.n = (registers.y & negativeFlag) != 0;
        registers.p.z = registers.y == 0;
        return 0;
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
            registers.a = bus.read(address);
            registers.p.n = (registers.al() & 0xf0) != 0;
        } else {
            registers.setAl(bus.read(address));
            registers.setAh(bus.read(address + 1));
            registers.p.n = (registers.a & 0xf000) != 0;
        }
        registers.p.z = registers.a == 0;
        return registers.p.m ? 0 : 1;
    }

    public int LDX(int address) {
        if (registers.p.x_b) {
            registers.x = bus.read(address);
            registers.p.n = (registers.xl() & 0xf0) != 0;
        } else {
            registers.x = bus.read(address) | (bus.read(address + 1) << 8);
            registers.p.n = (registers.x & 0xf000) != 0;
        }
        registers.p.z = registers.x == 0;
        return registers.p.x_b ? 0 : 1;
    }

    public int LDY(int address) {
        if (registers.p.x_b) {
            registers.y = bus.read(address);
            registers.p.n = (registers.yl() & 0xf0) != 0;
        } else {
            registers.y = bus.read(address) | (bus.read(address + 1) << 8);
            registers.p.n = (registers.y & 0xf000) != 0;
        }
        registers.p.z = registers.y == 0;
        return registers.p.x_b ? 0 : 1;
    }

    public int ORA(int valueAddr) {
        registers.a = normalizeAccumulator(registers.a | readAccumulatorWidth(valueAddr));
        setZNAccumulator(registers.a);
        return registers.p.m ? 0 : 1;
    }

    public int AND(int valueAddr) {
        registers.a = normalizeAccumulator(registers.a & readAccumulatorWidth(valueAddr));
        setZNAccumulator(registers.a);
        return registers.p.m ? 0 : 1;
    }

    public int EOR(int valueAddr) {
        registers.a = normalizeAccumulator(registers.a ^ readAccumulatorWidth(valueAddr));
        setZNAccumulator(registers.a);
        return registers.p.m ? 0 : 1;
    }

    public int CMP(int valueAddr) {
        int value = readAccumulatorWidth(valueAddr);
        long result = (Integer.toUnsignedLong(registers.a) - Integer.toUnsignedLong(value)) & 0xffff_ffffL;
        if (registers.p.m) {
            result &= 0xff;
        }
        int negativeMask = registers.p.m ? 0x80 : 0x8000;
        registers.p.n = (result & negativeMask) != 0;
        registers.p.z = result == 0;
        registers.p.c = Integer.toUnsignedLong(registers.a) >= result;
        return registers.p.m ? 0 : 1;
    }

    public int ADC(int valueAddr) {
        int value = bus.read(valueAddr) + (registers.p.c ? 1 : 0);
        if (!registers.p.m) {
            value += bus.read(valueAddr + 1) << 8;
        }
        int negativeMask = registers.p.m ? 0x80 : 0x8000;
        int maxValue = registers.p.m ? 0xff : 0xffff;
        int oldA = registers.a;
        int result = oldA + value;

        registers.p.c = result > maxValue;
        if ((oldA & negativeMask) == (value & negativeMask)) {
            registers.p.v = (oldA & negativeMask) != (result & negativeMask);
        } else {
            registers.p.v = false;
        }
        registers.a = normalizeAccumulator(result);
        setZNAccumulator(registers.a);
        return registers.p.m ? 0 : 1;
    }

    public int SBC(int valueAddr) {
        int negativeMask = registers.p.m ? 0x80 : 0x8000;
        int value = readAccumulatorWidth(valueAddr);
        boolean oldCarry = registers.p.c;
        int oldA = registers.a;

        registers.p.c = oldA >= value;
        if ((oldA & negativeMask) == (value & negativeMask)) {
            registers.p.v = (oldA & negativeMask) != ((oldA + value) & negativeMask);
        } else {
            registers.p.v = false;
        }
        registers.a = normalizeAccumulator(oldA + ~value + (oldCarry ? 1 : 0));
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
        registers.a = normalizeAccumulator(registers.a + 1);
        setZNAccumulator(registers.a);
        return 0;
    }

    public int DEA(int valueAddr) {
        registers.a = normalizeAccumulator(registers.a - 1);
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
        value = normalizeAccumulator(value | registers.a);
        bus.write(valueAddr, value);
        if (!registers.p.m) {
            bus.write(valueAddr + 1, value >>> 8);
        }
        registers.p.z = value == 0;
        return registers.p.m ? 0 : 2;
    }

    public int TRB(int valueAddr) {
        int value = readAccumulatorWidth(valueAddr);
        int newValue = normalizeAccumulator(value & ~registers.a);
        bus.write(valueAddr, newValue);
        if (!registers.p.m) {
            bus.write(valueAddr + 1, newValue >>> 8);
        }
        registers.p.z = (value & registers.a) == 0;
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
            registers.p.c = (registers.a & highBit) != 0;
            registers.a = u16(registers.a << 1);
            registers.p.n = (registers.a & highBit) != 0;
            registers.p.z = registers.a == 0;
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
            registers.p.c = (registers.a & 1) != 0;
            registers.a = u16(registers.a >>> 1);
            registers.p.z = registers.a == 0;
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
            registers.p.c = (registers.a & highBit) != 0;
            registers.a = u16((registers.a << 1) | (oldCarry ? 1 : 0));
            registers.p.n = (registers.a & highBit) != 0;
            registers.p.z = registers.a == 0;
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
        registers.p.n = false;
        boolean oldCarry = registers.p.c;
        int highBitIndex = registers.p.m ? 7 : 15;
        if (mode == AddressingMode.IMPLIED) {
            registers.p.c = (registers.a & 1) != 0;
            registers.a = u16((registers.a >>> 1) | ((oldCarry ? 1 : 0) << highBitIndex));
            registers.p.z = registers.a == 0;
            return 0;
        }

        int value = readAccumulatorWidth(valueAddr);
        registers.p.c = (value & 1) != 0;
        value = normalizeAccumulator((value >>> 1) | ((oldCarry ? 1 : 0) << highBitIndex));
        registers.p.z = value == 0;
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
        return (condition ? 1 : 0) + (emulationMode ? 1 : 0);
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

    private void setZN16(int value) {
        int normalized = u16(value);
        registers.p.z = normalized == 0;
        registers.p.n = (normalized & 0x8000) != 0;
    }

    private void setZNIndex(int value) {
        int negativeFlag = registers.p.x_b ? 0x80 : 0x8000;
        registers.p.z = value == 0;
        registers.p.n = (value & negativeFlag) != 0;
    }

    private void setZNAccumulator(int value) {
        int normalized = normalizeAccumulator(value);
        int negativeFlag = registers.p.m ? 0x80 : 0x8000;
        registers.p.z = normalized == 0;
        registers.p.n = (normalized & negativeFlag) != 0;
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
        if ((base & 0x80000000) == ((base + index) & 0x80000000)) {
            hasIndexCrossedPageBoundary = true;
        }
    }

    @Override
    public String getName() {
        return "CPU";
    }

    @Override
    public Component getComponent() {
        return Component.CPU;
    }
}
