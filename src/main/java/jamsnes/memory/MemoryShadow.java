package jamsnes.memory;

import jamsnes.models.Component;

public class MemoryShadow extends AMemory {
    private final IMemory initial;
    private final int relativeOffset;

    public MemoryShadow(IMemory initial, int start, int end) {
        this(initial, start, end, 0);
    }

    public MemoryShadow(IMemory initial, int start, int end, int relativeOffset) {
        this.initial = initial;
        this.relativeOffset = relativeOffset;
        setMemoryRegion(start, end);
    }

    @Override
    public int read(int address) {
        return initial.read(relativeOffset + address);
    }

    @Override
    public void write(int address, int data) {
        initial.write(relativeOffset + address, data);
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
        return initial.getValueName(relativeOffset + address);
    }

    public IMemory getMirrored() {
        return initial;
    }
}
