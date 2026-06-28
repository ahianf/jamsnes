package jamsnes.memory;

import jamsnes.models.Component;

public interface IMemory {
    int read(int address);

    void write(int address, int data);

    boolean hasMemoryAt(int address);

    int getRelativeAddress(int address);

    int getSize();

    String getName();

    Component getComponent();

    default String getValueName(int address) {
        return "???";
    }
}
