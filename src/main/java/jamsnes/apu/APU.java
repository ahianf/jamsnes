package jamsnes.apu;

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
    private final int[] ports = new int[4];
    private StateMode state = StateMode.RUNNING;

    public APU(IRenderer renderer) {
        reset();
    }

    @Override
    public int read(int address) {
        return ports[address];
    }

    @Override
    public void write(int address, int data) {
        ports[address] = u8(data);
    }

    public int[] ports() {
        return ports;
    }

    public APURegisters internalRegisters() {
        return internalRegisters;
    }

    public StateMode getState() {
        return state;
    }

    public int _internalRead(int address) {
        return internalMemory[u16(address)];
    }

    public void _internalWrite(int address, int data) {
        internalMemory[u16(address)] = u8(data);
    }

    public int _getImmediateData() {
        int value = _internalRead(internalRegisters.pc);
        internalRegisters.incrementPc();
        return value;
    }

    public int _getDirectAddr() {
        int address = _getImmediateData();
        if (internalRegisters.p) {
            address += 0x100;
        }
        return address;
    }

    public int _getIndexXAddr() {
        int address = internalRegisters.x;
        if (internalRegisters.p) {
            address += 0x100;
        }
        return address;
    }

    public int _getIndexYAddr() {
        int address = internalRegisters.y;
        if (internalRegisters.p) {
            address += 0x100;
        }
        return address;
    }

    public int _getDirectAddrByX() {
        return _getDirectAddr() + internalRegisters.x;
    }

    public int _getDirectAddrByY() {
        return _getDirectAddr() + internalRegisters.y;
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

        low = _internalRead(full + internalRegisters.x);
        high = _internalRead(full + internalRegisters.x + 1);
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
        int directIndexX = _getDirectAddr() + internalRegisters.x;
        int low = _internalRead(directIndexX);
        directIndexX += 1;
        if (internalRegisters.p) {
            directIndexX += 0x100;
        }
        int high = _internalRead(directIndexX);
        return u16((high << 8) | low);
    }

    public int _getAbsoluteDirectAddrByY() {
        int directIndex = _getDirectAddr();
        int low = _internalRead(directIndex);
        directIndex += 1;
        if (internalRegisters.p) {
            directIndex += 0x100;
        }
        int high = _internalRead(directIndex);
        return u16(((high << 8) | low) + internalRegisters.y);
    }

    public int executeInstruction() {
        int opcode = _getImmediateData();

        switch (opcode) {
            case 0x00:
                return NOP();
            case 0x01:
                return TCALL(0);
            case 0x03:
                return BBS(_getDirectAddr(), _getImmediateData(), 0);
            case 0x0d:
                return PUSH(internalRegisters.psw());
            case 0x0f:
                return BRK();
            case 0x10:
                return BPL(_getImmediateData());
            case 0x11:
                return TCALL(1);
            case 0x13:
                return BBC(_getDirectAddr(), _getImmediateData(), 0);
            case 0x1f:
                return JMP(_getAbsoluteByXAddr(), true);
            case 0x20:
                return CLRP();
            case 0x21:
                return TCALL(2);
            case 0x23:
                return BBS(_getDirectAddr(), _getImmediateData(), 1);
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
            case 0x33:
                return BBC(_getDirectAddr(), _getImmediateData(), 1);
            case 0x3f:
                return CALL(_getAbsoluteAddr());
            case 0x40:
                return SETP();
            case 0x41:
                return TCALL(4);
            case 0x43:
                return BBS(_getDirectAddr(), _getImmediateData(), 2);
            case 0x4d:
                return PUSH(internalRegisters.x);
            case 0x4f:
                return PCALL();
            case 0x50:
                return BVC(_getImmediateData());
            case 0x51:
                return TCALL(5);
            case 0x53:
                return BBC(_getDirectAddr(), _getImmediateData(), 2);
            case 0x5f:
                return JMP(_getAbsoluteAddr());
            case 0x60:
                return CLRC();
            case 0x61:
                return TCALL(6);
            case 0x63:
                return BBS(_getDirectAddr(), _getImmediateData(), 3);
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
            case 0x73:
                return BBC(_getDirectAddr(), _getImmediateData(), 3);
            case 0x7f:
                return RETI();
            case 0x80:
                return SETC();
            case 0x81:
                return TCALL(8);
            case 0x83:
                return BBS(_getDirectAddr(), _getImmediateData(), 4);
            case 0x8e:
                internalRegisters.setPsw(popStack());
                return 4;
            case 0x90:
                return BCC(_getImmediateData());
            case 0x91:
                return TCALL(9);
            case 0x93:
                return BBC(_getDirectAddr(), _getImmediateData(), 4);
            case 0xa0:
                return EI();
            case 0xa1:
                return TCALL(10);
            case 0xa3:
                return BBS(_getDirectAddr(), _getImmediateData(), 5);
            case 0xae:
                return POP("a");
            case 0xb0:
                return BCS(_getImmediateData());
            case 0xb1:
                return TCALL(11);
            case 0xb3:
                return BBC(_getDirectAddr(), _getImmediateData(), 5);
            case 0xc0:
                return DI();
            case 0xc1:
                return TCALL(12);
            case 0xc3:
                return BBS(_getDirectAddr(), _getImmediateData(), 6);
            case 0xce:
                return POP("x");
            case 0xd0:
                return BNE(_getImmediateData());
            case 0xd1:
                return TCALL(13);
            case 0xd3:
                return BBC(_getDirectAddr(), _getImmediateData(), 6);
            case 0xde:
                return CBNE(_getDirectAddrByX(), _getImmediateData(), true);
            case 0xe0:
                return CLRV();
            case 0xe1:
                return TCALL(14);
            case 0xe3:
                return BBS(_getDirectAddr(), _getImmediateData(), 7);
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
            case 0xf3:
                return BBC(_getDirectAddr(), _getImmediateData(), 7);
            case 0xfe:
                return DBNZ(_getImmediateData());
            case 0xff:
                return STOP();
            default:
                throw new InvalidOpcode("APU opcode 0x%02x is not implemented".formatted(opcode));
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
        setNzFlags(data);
        return 6;
    }

    public int TCLR1(int absoluteAddress) {
        int data = _internalRead(absoluteAddress);
        _internalWrite(absoluteAddress, data & ~internalRegisters.a);
        setNzFlags(data);
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
        internalRegisters.n = (value & 0x01) != 0;
        internalRegisters.z = value == 0;
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
        int result = u8((value >>> 1) + (internalRegisters.c ? 1 : 0));
        internalRegisters.c = (value & 0x01) != 0;
        if (accumulator) {
            internalRegisters.a = result;
        } else {
            _internalWrite(operand, result);
        }
        internalRegisters.n = (result & 0x01) != 0;
        internalRegisters.z = result == 0;
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
        internalRegisters.n = (internalRegisters.a & 0x80) != 0;
        internalRegisters.z = internalRegisters.y == 0;
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

    public int SBC(int operand1, int operand2, int cycles) {
        int data1 = _internalRead(operand1);
        int data2 = _internalRead(operand2);
        int carry = internalRegisters.c ? 1 : 0;
        int result = data1 - data2 - (carry ^ 1);

        internalRegisters.v = (((data1 ^ data2) & (data1 ^ result)) & 0x80) != 0;
        internalRegisters.h = ((result & 0x0f) - (data1 & 0x0f) + carry) > 0x0f;
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
        internalRegisters.h = ((result & 0x0f) - (internalRegisters.a & 0x0f) + carry) > 0x0f;
        internalRegisters.c = result >= 0 && result <= 0xff;
        setNzFlags(result);
        internalRegisters.a = u8(result);
        return cycles;
    }

    public int CMP(int operand1, int operand2, int cycles) {
        int data1 = _internalRead(operand1);
        internalRegisters.c = data1 >= operand2;
        setNzFlags(data1 - operand2);
        return cycles;
    }

    public int CMPreg(String register, int address, int cycles) {
        int data = _internalRead(address);
        int value = getRegister(register);
        internalRegisters.c = value >= data;
        setNzFlags(value - data);
        return cycles;
    }

    public int MOVregToReg(String from, String to) {
        int value = getRegister(from);
        setRegister(to, value);
        setNzFlags(value);
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
        setRegister(to, address);
        if (incrementX) {
            internalRegisters.x = u8(internalRegisters.x + 1);
        }
        setNzFlags(getRegister(to));
        return cycles;
    }

    public int MOVmemToMem(int memTo, int memFrom) {
        _internalWrite(memTo, memFrom);
        return 5;
    }

    public int MOVW(int address, boolean toYa) {
        int address2 = address + 1 + (internalRegisters.p ? 0x0100 : 0);
        if (toYa) {
            int value = (_internalRead(address2) << 8) | _internalRead(address);
            internalRegisters.setYa(value);
            setNzFlags(value);
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
        int address2 = address + 1 + (internalRegisters.p ? 0x0100 : 0);
        return u16((_internalRead(address2) << 8) | _internalRead(address));
    }

    private void writeWordWithDirectPage(int address, int value) {
        int address2 = address + 1 + (internalRegisters.p ? 0x0100 : 0);
        _internalWrite(address, value);
        _internalWrite(address2, value >>> 8);
    }

    private int getRegister(String register) {
        return switch (register) {
            case "a" -> internalRegisters.a;
            case "x" -> internalRegisters.x;
            case "y" -> internalRegisters.y;
            default -> throw new IllegalArgumentException("Unknown APU register: " + register);
        };
    }

    private void setRegister(String register, int value) {
        int normalized = u8(value);
        switch (register) {
            case "a" -> internalRegisters.a = normalized;
            case "x" -> internalRegisters.x = normalized;
            case "y" -> internalRegisters.y = normalized;
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
        CALL(_internalRead(0xffde - bit * 2));
        return 8;
    }

    public int BRK() {
        internalRegisters.b = true;
        PUSH(internalRegisters.pcHigh());
        PUSH(internalRegisters.pcLow());
        PUSH(internalRegisters.psw());
        internalRegisters.i = false;
        internalRegisters.setPcHigh(_internalRead(0xffdf));
        internalRegisters.setPcLow(_internalRead(0xffde));
        return 8;
    }

    public int RET() {
        internalRegisters.setPcHigh(popStack());
        internalRegisters.setPcLow(popStack());
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
    public Component getComponent() {
        return Component.APU;
    }

    private int popStack() {
        internalRegisters.sp = u8(internalRegisters.sp + 1);
        return _internalRead(0x0100 + internalRegisters.sp);
    }

    private void reset() {
        internalRegisters.a = 0;
        internalRegisters.y = 0;
        internalRegisters.x = 0;
        internalRegisters.sp = 0xef;
        internalRegisters.pc = 0xffc0;
    }
}
