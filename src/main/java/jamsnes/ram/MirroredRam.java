package jamsnes.ram;

import jamsnes.models.Component;

public class MirroredRam extends Ram {
    public MirroredRam(int size, Component ramType, String ramName) {
        super(size, ramType, ramName);
    }

    @Override
    public int read(int address) {
        return super.read(mirroredAddress(address));
    }

    @Override
    public void write(int address, int value) {
        super.write(mirroredAddress(address), value);
    }

    private int mirroredAddress(int address) {
        int size = getSize();
        if (size == 0) {
            return address;
        }
        return Math.floorMod(address, size);
    }
}
