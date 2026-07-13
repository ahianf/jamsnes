package jamsnes.memory;

import jamsnes.models.Component;
import jamsnes.ram.Ram;

import static jamsnes.models.Unsigned.u8;

public class WramPort extends AMemory {
    private static final int WMDATA = 0x00;
    private static final int WMADDL = 0x01;
    private static final int WMADDM = 0x02;
    private static final int WMADDH = 0x03;
    private static final int WRAM_ADDRESS_MASK = 0x1ffff;

    private final Ram wram;
    private int address;

    public WramPort(Ram wram) {
        this.wram = wram;
    }

    @Override
    public int read(int register) {
        if (register != WMDATA) {
            return 0;
        }
        int value = wram.read(address);
        incrementAddress();
        return value;
    }

    @Override
    public void write(int register, int data) {
        int value = u8(data);
        switch (register) {
            case WMDATA -> {
                wram.write(address, value);
                incrementAddress();
            }
            case WMADDL -> address = (address & 0x1ff00) | value;
            case WMADDM -> address = (address & 0x100ff) | (value << 8);
            case WMADDH -> address = (address & 0x0ffff) | ((value & 0x01) << 16);
            default -> {
            }
        }
    }

    public void resetAddress() {
        address = 0;
    }

    public int address() {
        return address;
    }

    private void incrementAddress() {
        address = (address + 1) & WRAM_ADDRESS_MASK;
    }

    @Override
    public int getSize() {
        return 4;
    }

    @Override
    public String getName() {
        return "WRam Port";
    }

    @Override
    public Component getComponent() {
        return Component.WRAM;
    }

    @Override
    public String getValueName(int address) {
        return switch (address) {
            case WMDATA -> "WMDATA";
            case WMADDL -> "WMADDL";
            case WMADDM -> "WMADDM";
            case WMADDH -> "WMADDH";
            default -> "???";
        };
    }
}
