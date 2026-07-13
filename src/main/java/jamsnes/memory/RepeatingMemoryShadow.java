package jamsnes.memory;

import jamsnes.models.Component;

public class RepeatingMemoryShadow extends AMemory {
    private final IMemory initial;
    private final int period;

    public RepeatingMemoryShadow(IMemory initial, int start, int end, int period) {
        if (period <= 0) {
            throw new IllegalArgumentException("period must be positive");
        }
        this.initial = initial;
        this.period = period;
        setMemoryRegion(start, end);
    }

    @Override
    public int read(int address) {
        return initial.read(address % period);
    }

    @Override
    public void write(int address, int data) {
        initial.write(address % period, data);
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
        return initial.getValueName(address % period);
    }

    public IMemory getMirrored() {
        return initial;
    }
}
