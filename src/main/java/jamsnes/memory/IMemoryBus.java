package jamsnes.memory;

import java.util.OptionalInt;

public interface IMemoryBus {
    int read(int address);

    OptionalInt peek(int address);

    int peekValue(int address);

    int getOpenBus();

    void write(int address, int data);

    IMemory getAccessor(int address);
}
