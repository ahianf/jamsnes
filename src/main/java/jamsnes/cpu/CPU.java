package jamsnes.cpu;

import jamsnes.memory.AMemory;
import jamsnes.memory.IMemoryBus;
import jamsnes.models.Component;

import static jamsnes.models.Unsigned.u8;

public class CPU extends AMemory {
    private final int[] internalRegisters = new int[0x300];
    private IMemoryBus bus;

    public CPU(IMemoryBus bus) {
        this.bus = bus;
    }

    public void setBus(IMemoryBus bus) {
        this.bus = bus;
    }

    public IMemoryBus getBus() {
        return bus;
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

    @Override
    public String getName() {
        return "CPU";
    }

    @Override
    public Component getComponent() {
        return Component.CPU;
    }
}
