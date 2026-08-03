package jamsnes.ram;

import jamsnes.exceptions.InvalidAddress;
import jamsnes.memory.ARectangleMemory;
import jamsnes.models.Component;

import java.util.Arrays;

import static jamsnes.models.Unsigned.u24;
import static jamsnes.models.Unsigned.u8;

public class Ram extends ARectangleMemory {
    private int[] data;
    private final Component ramType;
    private final String ramName;
    private long modificationCount;

    public Ram(int size, Component ramType, String ramName) {
        this.data = new int[size];
        this.ramType = ramType;
        this.ramName = ramName;
    }

    @Override
    public int read(int address) {
        int normalized = u24(address);
        if (normalized >= data.length) {
            throw new InvalidAddress(getName() + " read", normalized);
        }
        return data[normalized];
    }

    @Override
    public void write(int address, int value) {
        int normalized = u24(address);
        if (normalized >= data.length) {
            throw new InvalidAddress(getName() + " write", normalized);
        }
        data[normalized] = u8(value);
        modificationCount++;
    }

    public long modificationCount() {
        return modificationCount;
    }

    public int get(int address) {
        return read(address);
    }

    public void set(int address, int value) {
        write(address, value);
    }

    public int[] data() {
        return data;
    }

    public void clear() {
        Arrays.fill(data, 0);
        modificationCount++;
    }

    @Override
    public String getName() {
        return ramName;
    }

    @Override
    public Component getComponent() {
        return ramType;
    }

    @Override
    public int getSize() {
        return data.length;
    }

    public void setSize(int size) {
        data = Arrays.copyOf(data, size);
        modificationCount++;
    }
}
