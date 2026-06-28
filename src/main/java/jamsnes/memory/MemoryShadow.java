package jamsnes.memory;

import jamsnes.models.Component;

public class MemoryShadow extends AMemory {
    private final IMemory initial;

    public MemoryShadow(IMemory initial, int start, int end) {
        this.initial = initial;
        setMemoryRegion(start, end);
    }

    @Override
    public int read(int address) {
        return initial.read(address);
    }

    @Override
    public void write(int address, int data) {
        initial.write(address, data);
    }

    @Override
    public int getSize() {
        return initial.getSize();
    }

    @Override
    public String getName() {
        return initial.getName();
    }

    @Override
    public Component getComponent() {
        return initial.getComponent();
    }

    @Override
    public String getValueName(int address) {
        return initial.getValueName(address);
    }

    public IMemory getMirrored() {
        return initial;
    }
}
