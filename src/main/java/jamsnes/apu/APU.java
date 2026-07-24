package jamsnes.apu;

import jamsnes.apu.dsp.DSP;
import jamsnes.cartridge.Cartridge;
import jamsnes.exceptions.InvalidAddress;
import jamsnes.exceptions.InvalidOpcode;
import jamsnes.memory.AMemory;
import jamsnes.models.Component;
import jamsnes.renderer.IRenderer;

import static jamsnes.models.Unsigned.u16;
import static jamsnes.models.Unsigned.u8;

public class APU extends AMemory {
    public enum StateMode {
        RUNNING,
        SLEEPING,
        STOPPED
    }

    private final APURegisters internalRegisters = new APURegisters();
    private final int[] internalMemory = new int[0x10000];
    private final int[] iplRom = {
            0xcd, 0xef, 0xbd, 0xe8, 0x00, 0xc6, 0x1d, 0xd0,
            0xfc, 0x8f, 0xaa, 0xf4, 0x8f, 0xbb, 0xf5, 0x78,
            0xcc, 0xf4, 0xd0, 0xfb, 0x2f, 0x19, 0xeb, 0xf4,
            0xd0, 0xfc, 0x7e, 0xf4, 0xd0, 0x0b, 0xe4, 0xf5,
            0xcb, 0xf4, 0xd7, 0x00, 0xfc, 0xd0, 0xf3, 0xab,
            0x01, 0x10, 0xef, 0x7e, 0xf4, 0x10, 0xeb, 0xba,
            0xf6, 0xda, 0x00, 0xba, 0xf4, 0xc4, 0xf4, 0xdd,
            0x5d, 0xd0, 0xdb, 0x1f, 0x00, 0x00, 0xc0, 0xff
    };
    private final DSP dsp;
    private final int[] cpuReadPorts = new int[4];
    private final int[] apuReadPorts = new int[4];
    private final int[] timers = new int[3];
    private final int[] counters = new int[3];
    private final boolean[] timerEnabled = new boolean[3];
    private final int[] timerDividers = new int[3];
    private final int[] timerStages = new int[3];
    private StateMode state = StateMode.RUNNING;
    private int unknownRegister;
    private int controlRegister;
    private int dspRegisterAddress;
    private int registerMemory1;
    private int registerMemory2;
    private int paddingCycles;
    private boolean iplRomEnabled = true;
    public boolean isDisabled;

    public APU(IRenderer renderer) {
        dsp = new DSP(this::_internalRead, this::_internalWrite, renderer);
        reset();
    }

    @Override
    public int read(int address) {
        return switch (address) {
            case 0x00, 0x01, 0x02, 0x03 -> cpuReadPorts[address];
            default -> throw new InvalidAddress("APU Registers read", address);
        };
    }

    @Override
    public void write(int address, int data) {
        switch (address) {
            case 0x00, 0x01, 0x02, 0x03 -> apuReadPorts[address] = u8(data);
            default -> throw new InvalidAddress("APU Registers write", address);
        }
    }

    public int[] ports() {
        return cpuReadPorts;
    }

    public int[] inputPorts() {
        return apuReadPorts;
    }

    public int[] counters() {
        return counters;
    }

    public APURegisters internalRegisters() {
        return internalRegisters;
    }

    public DSP dsp() {
        return dsp;
    }

    public StateMode getState() {
        return state;
    }

    public int paddingCycles() {
        return paddingCycles;
    }

    public int _internalRead(int address) {
        validateInternalAddress(address, "APU Registers read");
        return switch (address) {
            case 0x00f0, 0x00f1, 0x00fa, 0x00fb, 0x00fc -> 0;
            case 0x00f2 -> dspRegisterAddress;
            case 0x00f3 -> dsp.read(dspRegisterAddress);
            case 0x00f4, 0x00f5, 0x00f6, 0x00f7 -> apuReadPorts[address - 0x00f4];
            case 0x00f8 -> registerMemory1;
            case 0x00f9 -> registerMemory2;
            case 0x00fd, 0x00fe, 0x00ff -> readCounter(address - 0x00fd);
            default -> {
                if (address >= 0xffc0 && iplRomEnabled) {
                    yield iplRom[address - 0xffc0];
                }
                if (address <= 0x00ef || address >= 0x0100) {
                    yield internalMemory[address];
                }
                throw new InvalidAddress("APU Registers read", address);
            }
        };
    }

    public void _internalWrite(int address, int data) {
        validateInternalAddress(address, "APU Registers write");
        int value = u8(data);
        switch (address) {
            case 0x00f0 -> unknownRegister = value;
            case 0x00f1 -> writeControlRegister(value);
            case 0x00f2 -> dspRegisterAddress = value;
            case 0x00f3 -> {
                if ((dspRegisterAddress & 0x80) == 0) {
                    dsp.write(dspRegisterAddress, value);
                }
            }
            case 0x00f4, 0x00f5, 0x00f6, 0x00f7 -> cpuReadPorts[address - 0x00f4] = value;
            case 0x00f8 -> registerMemory1 = value;
            case 0x00f9 -> registerMemory2 = value;
            case 0x00fa, 0x00fb, 0x00fc -> timers[address - 0x00fa] = value;
            case 0x00fd, 0x00fe, 0x00ff -> {
            }
            default -> {
                if (address <= 0x00ef || address >= 0x0100) {
                    internalMemory[address] = value;
                    return;
                }
                throw new InvalidAddress("APU Registers write", address);
            }
        }
    }

    private int readCounter(int index) {
        int value = counters[index] & 0x0f;
        counters[index] = 0;
        return value;
    }

    private void writeControlRegister(int value) {
        controlRegister = value;
        for (int i = 0; i < timerEnabled.length; i++) {
            boolean enabled = (value & (1 << i)) != 0;
            if (enabled && !timerEnabled[i]) {
                timerStages[i] = 0;
                counters[i] = 0;
            }
            timerEnabled[i] = enabled;
        }
        iplRomEnabled = (value & 0x80) != 0;
        if ((value & 0x10) != 0) {
            apuReadPorts[0] = 0;
            apuReadPorts[1] = 0;
        }
        if ((value & 0x20) != 0) {
            apuReadPorts[2] = 0;
            apuReadPorts[3] = 0;
        }
    }

    private void advanceTimers(int cycles) {
        if (cycles <= 0) {
            return;
        }
        for (int i = 0; i < timerEnabled.length; i++) {
            int dividerPeriod = i == 2 ? 16 : 128;
            timerDividers[i] += cycles;
            while (timerDividers[i] >= dividerPeriod) {
                timerDividers[i] -= dividerPeriod;
                if (timerEnabled[i]) {
                    tickTimer(i);
                }
            }
        }
    }

    private void tickTimer(int index) {
        int target = timers[index] == 0 ? 0x100 : timers[index];
        timerStages[index] += 1;
        if (timerStages[index] < target) {
            return;
        }
        timerStages[index] = 0;
        counters[index] = u8(counters[index] + 1) & 0x0f;
    }

    public int _getImmediateData() {
        int value = _internalRead(internalRegisters.pc);
        internalRegisters.incrementPc();
        return value;
    }

    public int _getDirectAddr() {
        return directPageAddress(_getImmediateData());
    }

    public int _getIndexXAddr() {
        return directPageAddress(internalRegisters.x);
    }

    public int _getIndexYAddr() {
        return directPageAddress(internalRegisters.y);
    }

    public int _getDirectAddrByX() {
        return directPageAddress(_getImmediateData() + internalRegisters.x);
    }

    public int _getDirectAddrByY() {
        return directPageAddress(_getImmediateData() + internalRegisters.y);
    }

    private int directPageAddress(int offset) {
        return (internalRegisters.p ? 0x100 : 0) | u8(offset);
    }

    public int _getAbsoluteAddr() {
        int low = _getImmediateData();
        int high = _getImmediateData();
        return u16((high << 8) | low);
    }

    public int _getAbsoluteByXAddr() {
        int low = _getImmediateData();
        int high = _getImmediateData();
        int full = u16((high << 8) | low);

        int pointer = u16(full + internalRegisters.x);
        low = _internalRead(pointer);
        high = _internalRead(u16(pointer + 1));
        return u16((high << 8) | low);
    }

    public int _getAbsoluteAddrByX() {
        return u16(_getAbsoluteAddr() + internalRegisters.x);
    }

    public int _getAbsoluteAddrByY() {
        return u16(_getAbsoluteAddr() + internalRegisters.y);
    }

    public AbsoluteBit _getAbsoluteBit() {
        int low = _getImmediateData();
        int high = _getImmediateData();
        int operand = u16((high << 8) | low);
        return new AbsoluteBit(operand & 0x1fff, operand >>> 13);
    }

    public int _getAbsoluteDirectByXAddr() {
        int directIndexX = _getImmediateData() + internalRegisters.x;
        int low = _internalRead(directPageAddress(directIndexX));
        int high = _internalRead(directPageAddress(directIndexX + 1));
        return u16((high << 8) | low);
    }

    public int _getAbsoluteDirectAddrByY() {
        int directIndex = _getImmediateData();
        int low = _internalRead(directPageAddress(directIndex));
        int high = _internalRead(directPageAddress(directIndex + 1));
        return u16(((high << 8) | low) + internalRegisters.y);
    }

    public int executeInstruction() {
        int opcode = _getImmediateData();

        switch (opcode) {
            case 0x00:
                return NOP();
            case 0x01:
                return TCALL(0);
            case 0x02:
                return SET1(_getDirectAddr(), 0);
            case 0x03:
                return BBS(_getDirectAddr(), _getImmediateData(), 0);
            case 0x04:
                return ORacc(_getDirectAddr(), 3);
            case 0x05:
                return ORacc(_getAbsoluteAddr(), 4);
            case 0x06:
                return ORacc(_getIndexXAddr(), 3);
            case 0x07:
                return ORacc(_getAbsoluteDirectByXAddr(), 6);
            case 0x08:
                return ORaccValue(_getImmediateData(), 2);
            case 0x09: {
                int source = _getDirectAddr();
                int destination = _getDirectAddr();
                return OR(destination, source, 6);
            }
            case 0x0a:
                return OR1(_getAbsoluteBit());
            case 0x0b:
                return ASL(_getDirectAddr(), 4);
            case 0x0c:
                return ASL(_getAbsoluteAddr(), 5);
            case 0x0d:
                return PUSH(internalRegisters.psw());
            case 0x0e:
                return TSET1(_getAbsoluteAddr());
            case 0x0f:
                return BRK();
            case 0x10:
                return BPL(_getImmediateData());
            case 0x11:
                return TCALL(1);
            case 0x12:
                return CLR1(_getDirectAddr(), 0);
            case 0x13:
                return BBC(_getDirectAddr(), _getImmediateData(), 0);
            case 0x14:
                return ORacc(_getDirectAddrByX(), 4);
            case 0x15:
                return ORacc(_getAbsoluteAddrByX(), 5);
            case 0x16:
                return ORacc(_getAbsoluteAddrByY(), 5);
            case 0x17:
                return ORacc(_getAbsoluteDirectAddrByY(), 6);
            case 0x18: {
                int source = _getImmediateData();
                int destination = _getDirectAddr();
                return ORmemValue(destination, source, 5);
            }
            case 0x19:
                return OR(_getIndexXAddr(), _getIndexYAddr(), 5);
            case 0x1a:
                return DECW(_getDirectAddr());
            case 0x1b:
                return ASL(_getDirectAddrByX(), 5);
            case 0x1c:
                return ASL(internalRegisters.a, 2, true);
            case 0x1d:
                return DECreg("x");
            case 0x1e:
                return CMPreg("x", _getAbsoluteAddr(), 4);
            case 0x1f:
                return JMP(_getAbsoluteByXAddr(), true);
            case 0x20:
                return CLRP();
            case 0x21:
                return TCALL(2);
            case 0x22:
                return SET1(_getDirectAddr(), 1);
            case 0x23:
                return BBS(_getDirectAddr(), _getImmediateData(), 1);
            case 0x24:
                return ANDacc(_getDirectAddr(), 3);
            case 0x25:
                return ANDacc(_getAbsoluteAddr(), 4);
            case 0x26:
                return ANDacc(_getIndexXAddr(), 3);
            case 0x27:
                return ANDacc(_getAbsoluteDirectByXAddr(), 6);
            case 0x28:
                return ANDaccValue(_getImmediateData(), 2);
            case 0x29: {
                int source = _getDirectAddr();
                int destination = _getDirectAddr();
                return AND(destination, source, 6);
            }
            case 0x2a:
                return OR1(_getAbsoluteBit(), true);
            case 0x2b:
                return ROL(_getDirectAddr(), 4);
            case 0x2c:
                return ROL(_getAbsoluteAddr(), 5);
            case 0x2d:
                return PUSH(internalRegisters.a);
            case 0x2e:
                return CBNE(_getDirectAddr(), _getImmediateData());
            case 0x2f:
                return BRA(_getImmediateData());
            case 0x30:
                return BMI(_getImmediateData());
            case 0x31:
                return TCALL(3);
            case 0x32:
                return CLR1(_getDirectAddr(), 1);
            case 0x33:
                return BBC(_getDirectAddr(), _getImmediateData(), 1);
            case 0x34:
                return ANDacc(_getDirectAddrByX(), 4);
            case 0x35:
                return ANDacc(_getAbsoluteAddrByX(), 5);
            case 0x36:
                return ANDacc(_getAbsoluteAddrByY(), 5);
            case 0x37:
                return ANDacc(_getAbsoluteDirectAddrByY(), 6);
            case 0x38: {
                int source = _getImmediateData();
                int destination = _getDirectAddr();
                return ANDmemValue(destination, source, 5);
            }
            case 0x39:
                return AND(_getIndexXAddr(), _getIndexYAddr(), 5);
            case 0x3a:
                return INCW(_getDirectAddr());
            case 0x3b:
                return ROL(_getAbsoluteAddrByX(), 5);
            case 0x3c:
                return ROL(internalRegisters.a, 2, true);
            case 0x3d:
                return INCreg("x");
            case 0x3e:
                return CMPreg("x", _getDirectAddr(), 3);
            case 0x3f:
                return CALL(_getAbsoluteAddr());
            case 0x40:
                return SETP();
            case 0x41:
                return TCALL(4);
            case 0x42:
                return SET1(_getDirectAddr(), 2);
            case 0x43:
                return BBS(_getDirectAddr(), _getImmediateData(), 2);
            case 0x44:
                return EORacc(_getDirectAddr(), 3);
            case 0x45:
                return EORacc(_getAbsoluteAddr(), 4);
            case 0x46:
                return EORacc(_getIndexXAddr(), 3);
            case 0x47:
                return EORacc(_getAbsoluteDirectByXAddr(), 6);
            case 0x48:
                return EORaccValue(_getImmediateData(), 2);
            case 0x49: {
                int source = _getDirectAddr();
                int destination = _getDirectAddr();
                return EOR(destination, source, 6);
            }
            case 0x4a:
                return AND1(_getAbsoluteBit());
            case 0x4b:
                return LSR(_getDirectAddr(), 4);
            case 0x4c:
                return LSR(_getAbsoluteAddr(), 5);
            case 0x4d:
                return PUSH(internalRegisters.x);
            case 0x4e:
                return TCLR1(_getAbsoluteAddr());
            case 0x4f:
                return PCALL();
            case 0x50:
                return BVC(_getImmediateData());
            case 0x51:
                return TCALL(5);
            case 0x52:
                return CLR1(_getDirectAddr(), 2);
            case 0x53:
                return BBC(_getDirectAddr(), _getImmediateData(), 2);
            case 0x54:
                return EORacc(_getDirectAddrByX(), 4);
            case 0x55:
                return EORacc(_getAbsoluteAddrByX(), 5);
            case 0x56:
                return EORacc(_getAbsoluteAddrByY(), 5);
            case 0x57:
                return EORacc(_getAbsoluteDirectAddrByY(), 6);
            case 0x58: {
                int source = _getImmediateData();
                int destination = _getDirectAddr();
                return EORmemValue(destination, source, 5);
            }
            case 0x59:
                return EOR(_getIndexXAddr(), _getIndexYAddr(), 5);
            case 0x5a:
                return CMPW(_getDirectAddr());
            case 0x5b:
                return LSR(_getDirectAddrByX(), 5);
            case 0x5c:
                return LSR(internalRegisters.a, 2, true);
            case 0x5d:
                return MOVregToReg("a", "x");
            case 0x5e:
                return CMPreg("y", _getAbsoluteAddr(), 4);
            case 0x5f:
                return JMP(_getAbsoluteAddr());
            case 0x60:
                return CLRC();
            case 0x61:
                return TCALL(6);
            case 0x62:
                return SET1(_getDirectAddr(), 3);
            case 0x63:
                return BBS(_getDirectAddr(), _getImmediateData(), 3);
            case 0x64:
                return CMPreg("a", _getDirectAddr(), 3);
            case 0x65:
                return CMPreg("a", _getAbsoluteAddr(), 4);
            case 0x66:
                return CMPreg("a", _getIndexXAddr(), 3);
            case 0x67:
                return CMPreg("a", _getAbsoluteDirectByXAddr(), 6);
            case 0x68:
                return CMPregValue("a", _getImmediateData(), 2);
            case 0x69: {
                int source = _getDirectAddr();
                int destination = _getDirectAddr();
                return CMPmemToMem(destination, source, 6);
            }
            case 0x6a:
                return AND1(_getAbsoluteBit(), true);
            case 0x6b:
                return ROR(_getDirectAddr(), 4);
            case 0x6c:
                return ROR(_getAbsoluteAddr(), 5);
            case 0x6d:
                return PUSH(internalRegisters.y);
            case 0x6e:
                return DBNZ(_getImmediateData(), true);
            case 0x6f:
                return RET();
            case 0x70:
                return BVS(_getImmediateData());
            case 0x71:
                return TCALL(7);
            case 0x72:
                return CLR1(_getDirectAddr(), 3);
            case 0x73:
                return BBC(_getDirectAddr(), _getImmediateData(), 3);
            case 0x74:
                return CMPreg("a", _getDirectAddrByX(), 4);
            case 0x75:
                return CMPreg("a", _getAbsoluteAddrByX(), 5);
            case 0x76:
                return CMPreg("a", _getAbsoluteAddrByY(), 5);
            case 0x77:
                return CMPreg("a", _getAbsoluteDirectAddrByY(), 6);
            case 0x78: {
                int source = _getImmediateData();
                int destination = _getDirectAddr();
                return CMP(destination, source, 5);
            }
            case 0x79:
                return CMPmemToMem(_getIndexXAddr(), _getIndexYAddr(), 5);
            case 0x7a:
                return ADDW(_getDirectAddr());
            case 0x7b:
                return ROR(_getDirectAddrByX(), 5);
            case 0x7c:
                return ROR(internalRegisters.a, 2, true);
            case 0x7d:
                return MOVregToReg("x", "a");
            case 0x7e:
                return CMPreg("y", _getDirectAddr(), 3);
            case 0x7f:
                return RETI();
            case 0x80:
                return SETC();
            case 0x81:
                return TCALL(8);
            case 0x82:
                return SET1(_getDirectAddr(), 4);
            case 0x83:
                return BBS(_getDirectAddr(), _getImmediateData(), 4);
            case 0x84:
                return ADCacc(_getDirectAddr(), 3);
            case 0x85:
                return ADCacc(_getAbsoluteAddr(), 4);
            case 0x86:
                return ADCacc(_getIndexXAddr(), 3);
            case 0x87:
                return ADCacc(_getAbsoluteDirectByXAddr(), 6);
            case 0x88:
                return ADCaccValue(_getImmediateData(), 2);
            case 0x89: {
                int source = _getDirectAddr();
                int destination = _getDirectAddr();
                return ADC(destination, source, 6);
            }
            case 0x8a:
                return EOR1(_getAbsoluteBit());
            case 0x8b:
                return DEC(_getDirectAddr(), 4);
            case 0x8c:
                return DEC(_getAbsoluteAddr(), 5);
            case 0x8d:
                return MOVvalueToReg(_getImmediateData(), "y", 2);
            case 0x8e:
                internalRegisters.setPsw(popStack());
                return 4;
            case 0x8f: {
                int source = _getImmediateData();
                int destination = _getDirectAddr();
                return MOVvalueToMem(destination, source, 5);
            }
            case 0x90:
                return BCC(_getImmediateData());
            case 0x91:
                return TCALL(9);
            case 0x92:
                return CLR1(_getDirectAddr(), 4);
            case 0x93:
                return BBC(_getDirectAddr(), _getImmediateData(), 4);
            case 0x94:
                return ADCacc(_getDirectAddrByX(), 4);
            case 0x95:
                return ADCacc(_getAbsoluteAddrByX(), 5);
            case 0x96:
                return ADCacc(_getAbsoluteAddrByY(), 5);
            case 0x97:
                return ADCacc(_getAbsoluteDirectAddrByY(), 6);
            case 0x98: {
                int source = _getImmediateData();
                int destination = _getDirectAddr();
                return ADCmemValue(destination, source, 5);
            }
            case 0x99:
                return ADC(_getIndexXAddr(), _getIndexYAddr(), 5);
            case 0x9a:
                return SUBW(_getDirectAddr());
            case 0x9b:
                return DEC(_getDirectAddrByX(), 5);
            case 0x9c:
                return DECreg("a");
            case 0x9d:
                return MOVregToReg("sp", "x");
            case 0x9e:
                return DIV();
            case 0x9f:
                return XCN();
            case 0xa0:
                return EI();
            case 0xa1:
                return TCALL(10);
            case 0xa2:
                return SET1(_getDirectAddr(), 5);
            case 0xa3:
                return BBS(_getDirectAddr(), _getImmediateData(), 5);
            case 0xa4:
                return SBCacc(_getDirectAddr(), 3);
            case 0xa5:
                return SBCacc(_getAbsoluteAddr(), 4);
            case 0xa6:
                return SBCacc(_getIndexXAddr(), 3);
            case 0xa7:
                return SBCacc(_getAbsoluteDirectByXAddr(), 6);
            case 0xa8:
                return SBCaccValue(_getImmediateData(), 2);
            case 0xa9: {
                int source = _getDirectAddr();
                int destination = _getDirectAddr();
                return SBC(destination, source, 6);
            }
            case 0xaa:
                return MOV1(_getAbsoluteBit(), true);
            case 0xab:
                return INC(_getDirectAddr(), 4);
            case 0xac:
                return INC(_getAbsoluteAddr(), 5);
            case 0xad:
                return CMPregValue("y", _getImmediateData(), 2);
            case 0xae:
                return POP("a");
            case 0xaf:
                return MOVregToMem("a", _getIndexXAddr(), 4, true);
            case 0xb0:
                return BCS(_getImmediateData());
            case 0xb1:
                return TCALL(11);
            case 0xb2:
                return CLR1(_getDirectAddr(), 5);
            case 0xb3:
                return BBC(_getDirectAddr(), _getImmediateData(), 5);
            case 0xb4:
                return SBCacc(_getDirectAddrByX(), 4);
            case 0xb5:
                return SBCacc(_getAbsoluteAddrByX(), 5);
            case 0xb6:
                return SBCacc(_getAbsoluteAddrByY(), 5);
            case 0xb7:
                return SBCacc(_getAbsoluteDirectAddrByY(), 6);
            case 0xb8: {
                int source = _getImmediateData();
                int destination = _getDirectAddr();
                return SBCmemValue(destination, source, 5);
            }
            case 0xb9:
                return SBC(_getIndexXAddr(), _getIndexYAddr(), 5);
            case 0xba:
                return MOVW(_getDirectAddr(), true);
            case 0xbb:
                return INC(_getDirectAddrByX(), 5);
            case 0xbc:
                return INCreg("a");
            case 0xbd:
                return MOVregToReg("x", "sp", false);
            case 0xbe:
                return DAS();
            case 0xbf:
                return MOVmemToReg(_getIndexXAddr(), "a", 4, true);
            case 0xc0:
                return DI();
            case 0xc1:
                return TCALL(12);
            case 0xc2:
                return SET1(_getDirectAddr(), 6);
            case 0xc3:
                return BBS(_getDirectAddr(), _getImmediateData(), 6);
            case 0xc4:
                return MOVregToMem("a", _getDirectAddr(), 4);
            case 0xc5:
                return MOVregToMem("a", _getAbsoluteAddr(), 5);
            case 0xc6:
                return MOVregToMem("a", _getIndexXAddr(), 4);
            case 0xc7:
                return MOVregToMem("a", _getAbsoluteDirectByXAddr(), 7);
            case 0xc8:
                return CMPregValue("x", _getImmediateData(), 2);
            case 0xc9:
                return MOVregToMem("x", _getAbsoluteAddr(), 5);
            case 0xca:
                return MOV1(_getAbsoluteBit());
            case 0xcb:
                return MOVregToMem("y", _getDirectAddr(), 4);
            case 0xcc:
                return MOVregToMem("y", _getAbsoluteAddr(), 5);
            case 0xcd:
                return MOVvalueToReg(_getImmediateData(), "x", 2);
            case 0xce:
                return POP("x");
            case 0xcf:
                return MUL();
            case 0xd0:
                return BNE(_getImmediateData());
            case 0xd1:
                return TCALL(13);
            case 0xd2:
                return CLR1(_getDirectAddr(), 6);
            case 0xd3:
                return BBC(_getDirectAddr(), _getImmediateData(), 6);
            case 0xd4:
                return MOVregToMem("a", _getDirectAddrByX(), 5);
            case 0xd5:
                return MOVregToMem("a", _getAbsoluteAddrByX(), 6);
            case 0xd6:
                return MOVregToMem("a", _getAbsoluteAddrByY(), 6);
            case 0xd7:
                return MOVregToMem("a", _getAbsoluteDirectAddrByY(), 7);
            case 0xd8:
                return MOVregToMem("x", _getDirectAddr(), 4);
            case 0xd9:
                return MOVregToMem("x", _getDirectAddrByY(), 5);
            case 0xda:
                return MOVW(_getDirectAddr(), false);
            case 0xdb:
                return MOVregToMem("y", _getDirectAddrByX(), 5);
            case 0xdc:
                return DECreg("y");
            case 0xdd:
                return MOVregToReg("y", "a");
            case 0xde:
                return CBNE(_getDirectAddrByX(), _getImmediateData(), true);
            case 0xdf:
                return DAA();
            case 0xe0:
                return CLRV();
            case 0xe1:
                return TCALL(14);
            case 0xe2:
                return SET1(_getDirectAddr(), 7);
            case 0xe3:
                return BBS(_getDirectAddr(), _getImmediateData(), 7);
            case 0xea:
                return NOT1(_getAbsoluteBit());
            case 0xe4:
                return MOVmemToReg(_getDirectAddr(), "a", 3);
            case 0xe5:
                return MOVmemToReg(_getAbsoluteAddr(), "a", 4);
            case 0xe6:
                return MOVmemToReg(_getIndexXAddr(), "a", 3);
            case 0xe7:
                return MOVmemToReg(_getAbsoluteDirectByXAddr(), "a", 6);
            case 0xe8:
                return MOVvalueToReg(_getImmediateData(), "a", 2);
            case 0xe9:
                return MOVmemToReg(_getAbsoluteAddr(), "x", 4);
            case 0xeb:
                return MOVmemToReg(_getDirectAddr(), "y", 3);
            case 0xec:
                return MOVmemToReg(_getAbsoluteAddr(), "y", 4);
            case 0xed:
                return NOTC();
            case 0xee:
                return POP("y");
            case 0xef:
                return SLEEP();
            case 0xf0:
                return BEQ(_getImmediateData());
            case 0xf1:
                return TCALL(15);
            case 0xf2:
                return CLR1(_getDirectAddr(), 7);
            case 0xf3:
                return BBC(_getDirectAddr(), _getImmediateData(), 7);
            case 0xf4:
                return MOVmemToReg(_getDirectAddrByX(), "a", 4);
            case 0xf5:
                return MOVmemToReg(_getAbsoluteAddrByX(), "a", 5);
            case 0xf6:
                return MOVmemToReg(_getAbsoluteAddrByY(), "a", 5);
            case 0xf7:
                return MOVmemToReg(_getAbsoluteDirectAddrByY(), "a", 6);
            case 0xf8:
                return MOVmemToReg(_getDirectAddr(), "x", 3);
            case 0xf9:
                return MOVmemToReg(_getDirectAddrByY(), "x", 4);
            case 0xfa: {
                int source = _getDirectAddr();
                int destination = _getDirectAddr();
                return MOVmemToMem(destination, source);
            }
            case 0xfb:
                return MOVmemToReg(_getDirectAddrByX(), "y", 4);
            case 0xfc:
                return INCreg("y");
            case 0xfd:
                return MOVregToReg("a", "y");
            case 0xfe:
                return DBNZ(_getImmediateData());
            case 0xff:
                return STOP();
            default:
                throw new InvalidOpcode("APU opcode 0x%02x is not implemented".formatted(opcode));
        }
    }

    public void update(int cycles) {
        if (isDisabled) {
            return;
        }

        advanceTimers(cycles);
        int remainingCycles = cycles;
        int total = 0;

        if (paddingCycles > remainingCycles) {
            paddingCycles -= remainingCycles;
            advanceDsp(cycles);
            return;
        }

        remainingCycles -= paddingCycles;
        paddingCycles = 0;
        while (total < remainingCycles && state == StateMode.RUNNING) {
            total += executeInstruction();
        }
        if (state == StateMode.RUNNING) {
            paddingCycles = total - remainingCycles;
        }
        advanceDsp(cycles);
    }

    private void advanceDsp(int cycles) {
        for (int i = 0; i < cycles; i++) {
            dsp.update();
        }
    }

    public void loadFromSPC(Cartridge cartridge) {
        int size = cartridge.getSize();
        if (size < 0x101c0) {
            throw new InvalidAddress("Cartridge is not the right size", size);
        }

        reset();

        internalRegisters.setPcLow(cartridge.read(0x25));
        internalRegisters.setPcHigh(cartridge.read(0x26));
        internalRegisters.a = cartridge.read(0x27);
        internalRegisters.x = cartridge.read(0x28);
        internalRegisters.y = cartridge.read(0x29);
        internalRegisters.setPsw(cartridge.read(0x2a));
        internalRegisters.sp = cartridge.read(0x2b);

        for (int i = 0; i < 0x00f0; i++) {
            internalMemory[i] = cartridge.read(0x100 + i);
        }
        for (int i = 0; i < 0x0100; i++) {
            internalMemory[0x0100 + i] = cartridge.read(0x200 + i);
        }
        for (int i = 0; i < 0xfdc0; i++) {
            internalMemory[0x0200 + i] = cartridge.read(0x300 + i);
        }
        for (int i = 0; i < 0x0040; i++) {
            internalMemory[0xffc0 + i] = cartridge.read(0x100 + 0xffc0 + i);
        }

        unknownRegister = cartridge.read(0x100 + 0x00f0);
        writeControlRegister(cartridge.read(0x100 + 0x00f1));
        dspRegisterAddress = cartridge.read(0x100 + 0x00f2);
        dsp.write(dspRegisterAddress, cartridge.read(0x100 + 0x00f3));
        apuReadPorts[0] = cartridge.read(0x100 + 0x00f4);
        apuReadPorts[1] = cartridge.read(0x100 + 0x00f5);
        apuReadPorts[2] = cartridge.read(0x100 + 0x00f6);
        apuReadPorts[3] = cartridge.read(0x100 + 0x00f7);
        registerMemory1 = cartridge.read(0x100 + 0x00f8);
        registerMemory2 = cartridge.read(0x100 + 0x00f9);
        timers[0] = cartridge.read(0x100 + 0x00fa);
        timers[1] = cartridge.read(0x100 + 0x00fb);
        timers[2] = cartridge.read(0x100 + 0x00fc);
        counters[0] = cartridge.read(0x100 + 0x00fd);
        counters[1] = cartridge.read(0x100 + 0x00fe);
        counters[2] = cartridge.read(0x100 + 0x00ff);

        for (int register = 0x00; register < 0x80; register += 0x10) {
            dsp.write(register, cartridge.read(0x10100 + register));
        }
        for (int register = 0x01; register < 0x80; register += 0x10) {
            dsp.write(register, cartridge.read(0x10100 + register));
        }
        for (int register = 0x02; register < 0x80; register += 0x10) {
            dsp.write(register, cartridge.read(0x10100 + register));
        }
        for (int register = 0x03; register < 0x80; register += 0x10) {
            dsp.write(register, cartridge.read(0x10100 + register));
        }
        for (int register = 0x04; register < 0x80; register += 0x10) {
            dsp.write(register, cartridge.read(0x10100 + register));
        }
        for (int register = 0x05; register < 0x80; register += 0x10) {
            dsp.write(register, cartridge.read(0x10100 + register));
        }
        for (int register = 0x06; register < 0x80; register += 0x10) {
            dsp.write(register, cartridge.read(0x10100 + register));
        }
        for (int register = 0x07; register < 0x80; register += 0x10) {
            dsp.write(register, cartridge.read(0x10100 + register));
        }
        for (int register = 0x08; register < 0x80; register += 0x10) {
            dsp.write(register, cartridge.read(0x10100 + register));
        }
        for (int register = 0x09; register < 0x80; register += 0x10) {
            dsp.write(register, cartridge.read(0x10100 + register));
        }
        for (int register = 0x0c; register < 0x80; register += 0x10) {
            dsp.write(register, cartridge.read(0x10100 + register));
        }
        for (int register = 0x0d; register < 0x80; register += 0x10) {
            dsp.write(register, cartridge.read(0x10100 + register));
        }
        for (int register = 0x0f; register < 0x80; register += 0x10) {
            dsp.write(register, cartridge.read(0x10100 + register));
        }
    }

    public int NOP() {
        return 2;
    }

    public int SLEEP() {
        state = StateMode.SLEEPING;
        return 3;
    }

    public int STOP() {
        state = StateMode.STOPPED;
        return 3;
    }

    public int CLRC() {
        internalRegisters.c = false;
        return 2;
    }

    public int SETC() {
        internalRegisters.c = true;
        return 2;
    }

    public int NOTC() {
        internalRegisters.c = !internalRegisters.c;
        return 3;
    }

    public int CLRV() {
        internalRegisters.v = false;
        internalRegisters.h = false;
        return 2;
    }

    public int CLRP() {
        internalRegisters.p = false;
        return 2;
    }

    public int SETP() {
        internalRegisters.p = true;
        return 2;
    }

    public int EI() {
        internalRegisters.i = true;
        return 3;
    }

    public int DI() {
        internalRegisters.i = false;
        return 3;
    }

    public int SET1(int directPageAddress, int bit) {
        int data = _internalRead(directPageAddress);
        _internalWrite(directPageAddress, data | (1 << bit));
        return 4;
    }

    public int CLR1(int directPageAddress, int bit) {
        int data = _internalRead(directPageAddress);
        _internalWrite(directPageAddress, data & ~(1 << bit));
        return 4;
    }

    public int TSET1(int absoluteAddress) {
        int data = _internalRead(absoluteAddress);
        _internalWrite(absoluteAddress, data | internalRegisters.a);
        setNzFlags(internalRegisters.a - data);
        return 6;
    }

    public int TCLR1(int absoluteAddress) {
        int data = _internalRead(absoluteAddress);
        _internalWrite(absoluteAddress, data & ~internalRegisters.a);
        setNzFlags(internalRegisters.a - data);
        return 6;
    }

    public int AND1(AbsoluteBit operand) {
        return AND1(operand, false);
    }

    public int AND1(AbsoluteBit operand, boolean invert) {
        boolean bit = getAbsoluteBitValue(operand);
        internalRegisters.c = internalRegisters.c & (invert ? !bit : bit);
        return 4;
    }

    public int OR1(AbsoluteBit operand) {
        return OR1(operand, false);
    }

    public int OR1(AbsoluteBit operand, boolean invert) {
        boolean bit = getAbsoluteBitValue(operand);
        internalRegisters.c = internalRegisters.c | (invert ? !bit : bit);
        return 5;
    }

    public int EOR1(AbsoluteBit operand) {
        internalRegisters.c = internalRegisters.c ^ getAbsoluteBitValue(operand);
        return 5;
    }

    public int NOT1(AbsoluteBit operand) {
        _internalWrite(operand.address(), _internalRead(operand.address()) ^ (1 << operand.bit()));
        return 5;
    }

    public int MOV1(AbsoluteBit operand) {
        return MOV1(operand, false);
    }

    public int MOV1(AbsoluteBit operand, boolean toCarry) {
        int mask = 1 << operand.bit();
        if (toCarry) {
            internalRegisters.c = (_internalRead(operand.address()) & mask) != 0;
            return 4;
        }
        int data = _internalRead(operand.address());
        _internalWrite(operand.address(), internalRegisters.c ? data | mask : data & ~mask);
        return 6;
    }

    public int INC(int address, int cycles) {
        int value = u8(_internalRead(address) + 1);
        _internalWrite(address, value);
        setNzFlags(value);
        return cycles;
    }

    public int INCreg(String register) {
        setRegister(register, u8(getRegister(register) + 1));
        setNzFlags(getRegister(register));
        return 2;
    }

    public int DEC(int address, int cycles) {
        int value = u8(_internalRead(address) - 1);
        _internalWrite(address, value);
        setNzFlags(value);
        return cycles;
    }

    public int DECreg(String register) {
        setRegister(register, u8(getRegister(register) - 1));
        setNzFlags(getRegister(register));
        return 2;
    }

    public int AND(int operand1, int operand2, int cycles) {
        int data = _internalRead(operand1) & _internalRead(operand2);
        _internalWrite(operand1, data);
        setNzFlags(data);
        return cycles;
    }

    public int ANDacc(int address, int cycles) {
        internalRegisters.a = u8(internalRegisters.a & _internalRead(address));
        setNzFlags(internalRegisters.a);
        return cycles;
    }

    public int ANDmemValue(int address, int value, int cycles) {
        int data = _internalRead(address) & u8(value);
        _internalWrite(address, data);
        setNzFlags(data);
        return cycles;
    }

    public int ANDaccValue(int value, int cycles) {
        internalRegisters.a = u8(internalRegisters.a & value);
        setNzFlags(internalRegisters.a);
        return cycles;
    }

    public int OR(int operand1, int operand2, int cycles) {
        int data = _internalRead(operand1) | _internalRead(operand2);
        _internalWrite(operand1, data);
        setNzFlags(data);
        return cycles;
    }

    public int ORacc(int address, int cycles) {
        internalRegisters.a = u8(internalRegisters.a | _internalRead(address));
        setNzFlags(internalRegisters.a);
        return cycles;
    }

    public int ORmemValue(int address, int value, int cycles) {
        int data = _internalRead(address) | u8(value);
        _internalWrite(address, data);
        setNzFlags(data);
        return cycles;
    }

    public int ORaccValue(int value, int cycles) {
        internalRegisters.a = u8(internalRegisters.a | value);
        setNzFlags(internalRegisters.a);
        return cycles;
    }

    public int EOR(int operand1, int operand2, int cycles) {
        int data = _internalRead(operand1) ^ _internalRead(operand2);
        _internalWrite(operand1, data);
        setNzFlags(data);
        return cycles;
    }

    public int EORacc(int address, int cycles) {
        internalRegisters.a = u8(internalRegisters.a ^ _internalRead(address));
        setNzFlags(internalRegisters.a);
        return cycles;
    }

    public int EORmemValue(int address, int value, int cycles) {
        int data = _internalRead(address) ^ u8(value);
        _internalWrite(address, data);
        setNzFlags(data);
        return cycles;
    }

    public int EORaccValue(int value, int cycles) {
        internalRegisters.a = u8(internalRegisters.a ^ value);
        setNzFlags(internalRegisters.a);
        return cycles;
    }

    public int ASL(int operand, int cycles) {
        return ASL(operand, cycles, false);
    }

    public int ASL(int operand, int cycles, boolean accumulator) {
        int value = accumulator ? operand : _internalRead(operand);
        internalRegisters.c = (value & 0x80) != 0;
        value = u8(value << 1);
        if (accumulator) {
            internalRegisters.a = value;
        } else {
            _internalWrite(operand, value);
        }
        setNzFlags(value);
        return cycles;
    }

    public int LSR(int operand, int cycles) {
        return LSR(operand, cycles, false);
    }

    public int LSR(int operand, int cycles, boolean accumulator) {
        int value = accumulator ? operand : _internalRead(operand);
        internalRegisters.c = (value & 0x01) != 0;
        value = u8(value >>> 1);
        if (accumulator) {
            internalRegisters.a = value;
        } else {
            _internalWrite(operand, value);
        }
        setNzFlags(value);
        return cycles;
    }

    public int ROL(int operand, int cycles) {
        return ROL(operand, cycles, false);
    }

    public int ROL(int operand, int cycles, boolean accumulator) {
        int value = accumulator ? operand : _internalRead(operand);
        int result = u8((value << 1) + (internalRegisters.c ? 1 : 0));
        internalRegisters.c = (value & 0x80) != 0;
        if (accumulator) {
            internalRegisters.a = result;
        } else {
            _internalWrite(operand, result);
        }
        setNzFlags(result);
        return cycles;
    }

    public int ROR(int operand, int cycles) {
        return ROR(operand, cycles, false);
    }

    public int ROR(int operand, int cycles, boolean accumulator) {
        int value = accumulator ? operand : _internalRead(operand);
        int result = u8((value >>> 1) | (internalRegisters.c ? 0x80 : 0));
        internalRegisters.c = (value & 0x01) != 0;
        if (accumulator) {
            internalRegisters.a = result;
        } else {
            _internalWrite(operand, result);
        }
        setNzFlags(result);
        return cycles;
    }

    public int XCN() {
        internalRegisters.a = u8((internalRegisters.a >>> 4) | (internalRegisters.a << 4));
        setNzFlags(internalRegisters.a);
        return 5;
    }

    public int DAA() {
        if (internalRegisters.c || internalRegisters.a > 0x99) {
            internalRegisters.c = true;
            internalRegisters.a = u8(internalRegisters.a + 0x60);
        }
        if (internalRegisters.h || (internalRegisters.a & 0x0f) > 0x09) {
            internalRegisters.a = u8(internalRegisters.a + 0x06);
        }
        setNzFlags(internalRegisters.a);
        return 3;
    }

    public int DAS() {
        if (!internalRegisters.c || internalRegisters.a > 0x99) {
            internalRegisters.c = false;
            internalRegisters.a = u8(internalRegisters.a - 0x60);
        }
        if (!internalRegisters.h || (internalRegisters.a & 0x0f) > 0x09) {
            internalRegisters.a = u8(internalRegisters.a - 0x06);
        }
        setNzFlags(internalRegisters.a);
        return 3;
    }

    public int MUL() {
        internalRegisters.setYa(internalRegisters.y * internalRegisters.a);
        setNzFlags(internalRegisters.y);
        return 9;
    }

    public int DIV() {
        int ya = internalRegisters.ya();
        internalRegisters.v = internalRegisters.y >= internalRegisters.x;
        internalRegisters.h = (internalRegisters.y & 0x0f) >= (internalRegisters.x & 0x0f);
        if (internalRegisters.y < (internalRegisters.x << 1)) {
            internalRegisters.a = u8(ya / internalRegisters.x);
            internalRegisters.y = u8(ya % internalRegisters.x);
        } else {
            internalRegisters.a = u8(0xff - (ya - (internalRegisters.x << 9)) / (0x100 - internalRegisters.x));
            internalRegisters.y = u8(internalRegisters.x + (ya - (internalRegisters.x << 9)) % (0x100 - internalRegisters.x));
        }
        setNzFlags(internalRegisters.a);
        return 12;
    }

    public int ADC(int operand1, int operand2, int cycles) {
        int data1 = _internalRead(operand1);
        int data2 = _internalRead(operand2);
        int carry = internalRegisters.c ? 1 : 0;
        int result = data1 + data2 + carry;

        internalRegisters.v = ((~(data1 ^ data2) & (data1 ^ result)) & 0x80) != 0;
        internalRegisters.h = ((data1 & 0x0f) + (data2 & 0x0f) + carry) > 0x0f;
        internalRegisters.c = result > 0xff;
        setNzFlags(result);
        _internalWrite(operand1, result);
        return cycles;
    }

    public int ADCacc(int address, int cycles) {
        int data = _internalRead(address);
        int carry = internalRegisters.c ? 1 : 0;
        int result = internalRegisters.a + data + carry;

        internalRegisters.v = ((~(internalRegisters.a ^ data) & (internalRegisters.a ^ result)) & 0x80) != 0;
        internalRegisters.h = ((internalRegisters.a & 0x0f) + (data & 0x0f) + carry) > 0x0f;
        internalRegisters.c = result > 0xff;
        setNzFlags(result);
        internalRegisters.a = u8(result);
        return cycles;
    }

    public int ADCmemValue(int address, int value, int cycles) {
        int data1 = _internalRead(address);
        int data2 = u8(value);
        int carry = internalRegisters.c ? 1 : 0;
        int result = data1 + data2 + carry;

        internalRegisters.v = ((~(data1 ^ data2) & (data1 ^ result)) & 0x80) != 0;
        internalRegisters.h = ((data1 & 0x0f) + (data2 & 0x0f) + carry) > 0x0f;
        internalRegisters.c = result > 0xff;
        setNzFlags(result);
        _internalWrite(address, result);
        return cycles;
    }

    public int ADCaccValue(int value, int cycles) {
        int data = u8(value);
        int carry = internalRegisters.c ? 1 : 0;
        int result = internalRegisters.a + data + carry;

        internalRegisters.v = ((~(internalRegisters.a ^ data) & (internalRegisters.a ^ result)) & 0x80) != 0;
        internalRegisters.h = ((internalRegisters.a & 0x0f) + (data & 0x0f) + carry) > 0x0f;
        internalRegisters.c = result > 0xff;
        setNzFlags(result);
        internalRegisters.a = u8(result);
        return cycles;
    }

    public int SBC(int operand1, int operand2, int cycles) {
        int data1 = _internalRead(operand1);
        int data2 = _internalRead(operand2);
        int carry = internalRegisters.c ? 1 : 0;
        int result = data1 - data2 - (carry ^ 1);

        internalRegisters.v = (((data1 ^ data2) & (data1 ^ result)) & 0x80) != 0;
        internalRegisters.h = subtractionHasNoHalfBorrow(data1, data2, carry);
        internalRegisters.c = result >= 0 && result <= 0xff;
        setNzFlags(result);
        _internalWrite(operand1, result);
        return cycles;
    }

    public int SBCacc(int address, int cycles) {
        int data = _internalRead(address);
        int carry = internalRegisters.c ? 1 : 0;
        int result = internalRegisters.a - data - (carry ^ 1);

        internalRegisters.v = (((internalRegisters.a ^ data) & (internalRegisters.a ^ result)) & 0x80) != 0;
        internalRegisters.h = subtractionHasNoHalfBorrow(internalRegisters.a, data, carry);
        internalRegisters.c = result >= 0 && result <= 0xff;
        setNzFlags(result);
        internalRegisters.a = u8(result);
        return cycles;
    }

    public int SBCmemValue(int address, int value, int cycles) {
        int data1 = _internalRead(address);
        int data2 = u8(value);
        int carry = internalRegisters.c ? 1 : 0;
        int result = data1 - data2 - (carry ^ 1);

        internalRegisters.v = (((data1 ^ data2) & (data1 ^ result)) & 0x80) != 0;
        internalRegisters.h = subtractionHasNoHalfBorrow(data1, data2, carry);
        internalRegisters.c = result >= 0 && result <= 0xff;
        setNzFlags(result);
        _internalWrite(address, result);
        return cycles;
    }

    public int SBCaccValue(int value, int cycles) {
        int data = u8(value);
        int carry = internalRegisters.c ? 1 : 0;
        int result = internalRegisters.a - data - (carry ^ 1);

        internalRegisters.v = (((internalRegisters.a ^ data) & (internalRegisters.a ^ result)) & 0x80) != 0;
        internalRegisters.h = subtractionHasNoHalfBorrow(internalRegisters.a, data, carry);
        internalRegisters.c = result >= 0 && result <= 0xff;
        setNzFlags(result);
        internalRegisters.a = u8(result);
        return cycles;
    }

    private boolean subtractionHasNoHalfBorrow(int minuend, int subtrahend, int carry) {
        int lowNibbleDifference = (minuend & 0x0f) - (subtrahend & 0x0f) - (carry ^ 1);
        return lowNibbleDifference >= 0;
    }

    public int CMP(int operand1, int operand2, int cycles) {
        int data1 = _internalRead(operand1);
        internalRegisters.c = data1 >= operand2;
        setNzFlags(data1 - operand2);
        return cycles;
    }

    public int CMPmemToMem(int operand1, int operand2, int cycles) {
        int data1 = _internalRead(operand1);
        int data2 = _internalRead(operand2);
        internalRegisters.c = data1 >= data2;
        setNzFlags(data1 - data2);
        return cycles;
    }

    public int CMPreg(String register, int address, int cycles) {
        int data = _internalRead(address);
        int value = getRegister(register);
        internalRegisters.c = value >= data;
        setNzFlags(value - data);
        return cycles;
    }

    public int CMPregValue(String register, int value, int cycles) {
        int data = u8(value);
        int registerValue = getRegister(register);
        internalRegisters.c = registerValue >= data;
        setNzFlags(registerValue - data);
        return cycles;
    }

    public int MOVregToReg(String from, String to) {
        return MOVregToReg(from, to, true);
    }

    public int MOVregToReg(String from, String to, boolean setFlags) {
        int value = getRegister(from);
        setRegister(to, value);
        if (setFlags) {
            setNzFlags(value);
        }
        return 2;
    }

    public int MOVregToMem(String from, int address, int cycles) {
        return MOVregToMem(from, address, cycles, false);
    }

    public int MOVregToMem(String from, int address, int cycles, boolean incrementX) {
        _internalWrite(address, getRegister(from));
        if (incrementX) {
            internalRegisters.x = u8(internalRegisters.x + 1);
        }
        return cycles;
    }

    public int MOVmemToReg(int address, String to, int cycles) {
        return MOVmemToReg(address, to, cycles, false);
    }

    public int MOVmemToReg(int address, String to, int cycles, boolean incrementX) {
        int value = _internalRead(address);
        setRegister(to, value);
        if (incrementX) {
            internalRegisters.x = u8(internalRegisters.x + 1);
        }
        setNzFlags(getRegister(to));
        return cycles;
    }

    public int MOVvalueToReg(int value, String to, int cycles) {
        setRegister(to, value);
        setNzFlags(getRegister(to));
        return cycles;
    }

    public int MOVvalueToMem(int address, int value, int cycles) {
        _internalWrite(address, value);
        return cycles;
    }

    public int MOVmemToMem(int memTo, int memFrom) {
        _internalWrite(memTo, _internalRead(memFrom));
        return 5;
    }

    public int MOVW(int address, boolean toYa) {
        int address2 = nextDirectPageAddress(address);
        if (toYa) {
            int value = (_internalRead(address2) << 8) | _internalRead(address);
            internalRegisters.setYa(value);
            setNzWordFlags(value);
        } else {
            _internalWrite(address, internalRegisters.a);
            _internalWrite(address2, internalRegisters.y);
        }
        return 5;
    }

    public int INCW(int address) {
        int value = u16(readWordWithDirectPage(address) + 1);
        writeWordWithDirectPage(address, value);
        setNzWordFlags(value);
        return 6;
    }

    public int DECW(int address) {
        int value = u16(readWordWithDirectPage(address) - 1);
        writeWordWithDirectPage(address, value);
        setNzWordFlags(value);
        return 6;
    }

    public int ADDW(int address) {
        int value = readWordWithDirectPage(address);
        int ya = internalRegisters.ya();
        int result = ya + value;

        internalRegisters.v = ((~(ya ^ value) & (ya ^ result)) & 0x8000) != 0;
        internalRegisters.c = result > 0xffff;
        internalRegisters.h = ((ya & 0x0fff) + (value & 0x0fff)) > 0x0fff;
        internalRegisters.setYa(result);
        setNzWordFlags(result);
        return 5;
    }

    public int SUBW(int address) {
        int value = readWordWithDirectPage(address);
        int ya = internalRegisters.ya();
        int result = ya - value;
        int halfCarryProbe = (((ya & 0x0f00) - (value & 0x0f00)) >>> 8) & 0xffff;
        if ((ya & 0x00ff) < (value & 0x00ff)) {
            halfCarryProbe = u16(halfCarryProbe - 1);
        }

        internalRegisters.v = (((ya ^ value) & (ya ^ result)) & 0x8000) != 0;
        internalRegisters.c = result >= 0 && result <= 0xffff;
        internalRegisters.h = halfCarryProbe <= 0x000f;
        internalRegisters.setYa(result);
        setNzWordFlags(result);
        return 5;
    }

    public int CMPW(int address) {
        int value = readWordWithDirectPage(address);
        int result = internalRegisters.ya() - value;

        setNzWordFlags(result);
        internalRegisters.c = internalRegisters.ya() >= value;
        return 4;
    }

    private boolean getAbsoluteBitValue(AbsoluteBit operand) {
        return (_internalRead(operand.address()) & (1 << operand.bit())) != 0;
    }

    private void setNzFlags(int data) {
        int value = u8(data);
        internalRegisters.z = value == 0;
        internalRegisters.n = (value & 0x80) != 0;
    }

    private void setNzWordFlags(int data) {
        int value = u16(data);
        internalRegisters.z = value == 0;
        internalRegisters.n = (value & 0x8000) != 0;
    }

    private int readWordWithDirectPage(int address) {
        int address2 = nextDirectPageAddress(address);
        return u16((_internalRead(address2) << 8) | _internalRead(address));
    }

    private void writeWordWithDirectPage(int address, int value) {
        int address2 = nextDirectPageAddress(address);
        _internalWrite(address, value);
        _internalWrite(address2, value >>> 8);
    }

    private int nextDirectPageAddress(int address) {
        return (address & 0x0100) | u8(address + 1);
    }

    private int getRegister(String register) {
        return switch (register) {
            case "a" -> internalRegisters.a;
            case "x" -> internalRegisters.x;
            case "y" -> internalRegisters.y;
            case "sp" -> internalRegisters.sp;
            default -> throw new IllegalArgumentException("Unknown APU register: " + register);
        };
    }

    private void setRegister(String register, int value) {
        int normalized = u8(value);
        switch (register) {
            case "a" -> internalRegisters.a = normalized;
            case "x" -> internalRegisters.x = normalized;
            case "y" -> internalRegisters.y = normalized;
            case "sp" -> internalRegisters.sp = normalized;
            case "psw" -> internalRegisters.setPsw(normalized);
            default -> throw new IllegalArgumentException("Unknown APU register: " + register);
        }
    }

    public int PUSH(int value) {
        _internalWrite(0x0100 + internalRegisters.sp, value);
        internalRegisters.sp = u8(internalRegisters.sp - 1);
        return 4;
    }

    public int POP(String destination) {
        setRegister(destination, popStack());
        return 4;
    }

    public int CALL(int absoluteAddress) {
        PUSH(internalRegisters.pcHigh());
        PUSH(internalRegisters.pcLow());
        internalRegisters.pc = u16(absoluteAddress);
        return 8;
    }

    public int PCALL() {
        CALL(0xff00 + _getImmediateData());
        return 6;
    }

    public int TCALL(int bit) {
        int vector = 0xffde - bit * 2;
        int low = _internalRead(vector);
        int high = _internalRead(vector + 1);
        CALL((high << 8) | low);
        return 8;
    }

    public int BRK() {
        PUSH(internalRegisters.pcHigh());
        PUSH(internalRegisters.pcLow());
        PUSH(internalRegisters.psw());
        internalRegisters.b = true;
        internalRegisters.i = false;
        internalRegisters.setPcHigh(_internalRead(0xffdf));
        internalRegisters.setPcLow(_internalRead(0xffde));
        return 8;
    }

    public int RET() {
        internalRegisters.setPcLow(popStack());
        internalRegisters.setPcHigh(popStack());
        return 5;
    }

    public int RETI() {
        internalRegisters.setPsw(popStack());
        RET();
        return 6;
    }

    public int BRA(int offset) {
        internalRegisters.pc = u16(internalRegisters.pc + (byte) offset);
        return 4;
    }

    public int BEQ(int offset) {
        if (!internalRegisters.z) {
            return 2;
        }
        BRA(offset);
        return 4;
    }

    public int BNE(int offset) {
        if (internalRegisters.z) {
            return 2;
        }
        BRA(offset);
        return 4;
    }

    public int BCS(int offset) {
        if (!internalRegisters.c) {
            return 2;
        }
        BRA(offset);
        return 4;
    }

    public int BCC(int offset) {
        if (internalRegisters.c) {
            return 2;
        }
        BRA(offset);
        return 4;
    }

    public int BVS(int offset) {
        if (!internalRegisters.v) {
            return 2;
        }
        BRA(offset);
        return 4;
    }

    public int BVC(int offset) {
        if (internalRegisters.v) {
            return 2;
        }
        BRA(offset);
        return 4;
    }

    public int BMI(int offset) {
        if (!internalRegisters.n) {
            return 2;
        }
        BRA(offset);
        return 4;
    }

    public int BPL(int offset) {
        if (internalRegisters.n) {
            return 2;
        }
        BRA(offset);
        return 4;
    }

    public int BBS(int address, int offset, int bit) {
        int data = _internalRead(address);
        if ((data & (1 << bit)) == 0) {
            return 5;
        }
        BRA(offset);
        return 7;
    }

    public int BBC(int address, int offset, int bit) {
        int data = _internalRead(address);
        if ((data & (1 << bit)) != 0) {
            return 5;
        }
        BRA(offset);
        return 7;
    }

    public int CBNE(int address, int offset) {
        return CBNE(address, offset, false);
    }

    public int CBNE(int address, int offset, boolean byX) {
        int data = _internalRead(address);
        if (internalRegisters.a == data) {
            return 5 + (byX ? 1 : 0);
        }
        BRA(offset);
        return 7 + (byX ? 1 : 0);
    }

    public int DBNZ(int offset) {
        return DBNZ(offset, false);
    }

    public int DBNZ(int offset, boolean directAddress) {
        int data;
        if (directAddress) {
            int address = _getDirectAddr();
            data = u8(_internalRead(address) - 1);
            _internalWrite(address, data);
        } else {
            data = u8(internalRegisters.y - 1);
            internalRegisters.y = data;
        }
        if (data == 0) {
            return 4 + (directAddress ? 1 : 0);
        }
        BRA(offset);
        return 6 + (directAddress ? 1 : 0);
    }

    public int JMP(int address) {
        return JMP(address, false);
    }

    public int JMP(int address, boolean byX) {
        internalRegisters.pc = u16(address);
        return byX ? 6 : 3;
    }

    @Override
    public int getSize() {
        return 0x3;
    }

    @Override
    public String getName() {
        return "APU";
    }

    @Override
    public String getValueName(int address) {
        return switch (address) {
            case 0x00 -> "APUIO0";
            case 0x01 -> "APUIO1";
            case 0x02 -> "APUIO2";
            case 0x03 -> "APUIO3";
            default -> "???";
        };
    }

    @Override
    public Component getComponent() {
        return Component.APU;
    }

    private int popStack() {
        internalRegisters.sp = u8(internalRegisters.sp + 1);
        return _internalRead(0x0100 + internalRegisters.sp);
    }

    private void validateInternalAddress(int address, String where) {
        if (address < 0 || address > 0xffff) {
            throw new InvalidAddress(where, address);
        }
    }

    public void reset() {
        cpuReadPorts[0] = 0;
        cpuReadPorts[1] = 0;
        cpuReadPorts[2] = 0;
        cpuReadPorts[3] = 0;
        apuReadPorts[0] = 0;
        apuReadPorts[1] = 0;
        apuReadPorts[2] = 0;
        apuReadPorts[3] = 0;
        timers[0] = 0;
        timers[1] = 0;
        timers[2] = 0;
        counters[0] = 0;
        counters[1] = 0;
        counters[2] = 0;
        for (int i = 0; i < timerEnabled.length; i++) {
            timerEnabled[i] = false;
            timerDividers[i] = 0;
            timerStages[i] = 0;
        }
        dsp.reset();
        unknownRegister = 0;
        controlRegister = 0;
        iplRomEnabled = true;
        dspRegisterAddress = 0;
        registerMemory1 = 0;
        registerMemory2 = 0;
        internalRegisters.a = 0;
        internalRegisters.y = 0;
        internalRegisters.x = 0;
        internalRegisters.setPsw(0);
        internalRegisters.sp = 0xef;
        internalRegisters.pc = 0xffc0;
        paddingCycles = 0;
        state = StateMode.RUNNING;
    }
}
