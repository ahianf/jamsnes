package jamsnes.cpu;

import jamsnes.memory.AMemory;
import jamsnes.memory.IMemoryBus;
import jamsnes.models.Component;

import static jamsnes.models.Unsigned.u16;
import static jamsnes.models.Unsigned.u24;
import static jamsnes.models.Unsigned.u8;

public class CPU extends AMemory {
    private final Registers registers = new Registers();
    private final int[] internalRegisters = new int[0x300];
    private IMemoryBus bus;
    private boolean hasIndexCrossedPageBoundary;

    public CPU(IMemoryBus bus) {
        this.bus = bus;
    }

    public void setBus(IMemoryBus bus) {
        this.bus = bus;
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

    @Override
    public int read(int address) {
        return internalRegisters[address];
    }

    @Override
    public void write(int address, int data) {
        internalRegisters[address] = u8(data);
    }

    public int[] internalRegisters() {
        return internalRegisters;
    }

    @Override
    public int getSize() {
        return 0x180;
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

    private int readPC() {
        int result = bus.read(registers.pac);
        registers.incrementPc(1);
        return result;
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
