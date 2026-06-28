package jamsnes.apu;

import jamsnes.memory.AMemory;
import jamsnes.models.Component;
import jamsnes.renderer.IRenderer;

import static jamsnes.models.Unsigned.u16;
import static jamsnes.models.Unsigned.u8;

public class APU extends AMemory {
    private final APURegisters internalRegisters = new APURegisters();
    private final int[] internalMemory = new int[0x10000];
    private final int[] ports = new int[4];

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
