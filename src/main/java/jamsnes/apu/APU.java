package jamsnes.apu;

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

    private boolean getAbsoluteBitValue(AbsoluteBit operand) {
        return (_internalRead(operand.address()) & (1 << operand.bit())) != 0;
    }

    private void setNzFlags(int data) {
        int value = u8(data);
        internalRegisters.z = value == 0;
        internalRegisters.n = (value & 0x80) != 0;
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
}
