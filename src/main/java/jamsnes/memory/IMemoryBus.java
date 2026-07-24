package jamsnes.memory;

import java.util.OptionalInt;

public interface IMemoryBus {
    int read(int address);

    OptionalInt peek(int address);

    int peekValue(int address);

    int getOpenBus();

    default int getExternalOpenBus() {
        return getOpenBus();
    }

    void write(int address, int data);

    IMemory getAccessor(int address);
}
