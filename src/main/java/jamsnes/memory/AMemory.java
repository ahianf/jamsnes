package jamsnes.memory;

import jamsnes.exceptions.InvalidAddress;

import static jamsnes.models.Unsigned.u24;

public abstract class AMemory implements IMemory {
    protected int start;
    protected int end;

    @Override
    public int getRelativeAddress(int address) {
        int normalized = u24(address);
        if (!hasMemoryAt(normalized)) {
            throw new InvalidAddress("Continuous memory", normalized);
        }
        return normalized - start;
    }

    public void setMemoryRegion(int start, int end) {
        this.start = u24(start);
        this.end = u24(end);
    }

    @Override
    public boolean hasMemoryAt(int address) {
        int normalized = u24(address);
        return start <= normalized && normalized <= end;
    }
}
