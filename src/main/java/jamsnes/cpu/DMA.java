package jamsnes.cpu;

import jamsnes.exceptions.InvalidAddress;
import jamsnes.memory.IMemory;
import jamsnes.memory.IMemoryBus;
import jamsnes.models.Component;

import static jamsnes.models.Unsigned.u16;
import static jamsnes.models.Unsigned.u24;
import static jamsnes.models.Unsigned.u8;

public class DMA {
    public static final int ONE_TO_ONE = 0;
    public static final int TWO_TO_TWO = 1;
    public static final int TWO_TO_ONE = 2;
    public static final int FOUR_TO_TWO = 3;
    public static final int FOUR_TO_FOUR = 4;
    public static final int TWO_TO_TWO_BIS = 5;
    public static final int TWO_TO_ONE_BIS = 6;
    public static final int FOUR_TO_TWO_BIS = 7;

    private int controlRegister;
    private int port;
    private int aAddress;
    private int count;
    private IMemoryBus bus;
    private boolean enabled;

    public DMA(IMemoryBus bus) {
        this.bus = bus;
    }

    public void setBus(IMemoryBus bus) {
        this.bus = bus;
    }

    public IMemoryBus getBus() {
        return bus;
    }

    public int read(int address) {
        return switch (address) {
            case 0x0 -> controlRegister;
            case 0x1 -> port;
            case 0x2 -> aAddress & 0xff;
            case 0x3 -> (aAddress >>> 8) & 0xff;
            case 0x4 -> (aAddress >>> 16) & 0xff;
            case 0x5 -> count & 0xff;
            case 0x6 -> (count >>> 8) & 0xff;
            default -> throw new InvalidAddress("DMA read", address);
        };
    }

    public void write(int address, int data) {
        int value = u8(data);
        switch (address) {
            case 0x0 -> controlRegister = value;
            case 0x1 -> port = value;
            case 0x2 -> aAddress = u24((aAddress & 0xffff00) | value);
            case 0x3 -> aAddress = u24((aAddress & 0xff00ff) | (value << 8));
            case 0x4 -> aAddress = u24((aAddress & 0x00ffff) | (value << 16));
            case 0x5 -> count = u16((count & 0xff00) | value);
            case 0x6 -> count = u16((count & 0x00ff) | (value << 8));
            default -> throw new InvalidAddress("DMA write", address);
        }
    }

    public int run(int maxCycles) {
        int cycles = 8;
        int i = 0;

        do {
            cycles += writeOneByte(aAddress, 0x2100 | u8(port + getModeOffset(i)));
            if (!isFixed()) {
                setAddressPage(getAddressPage() + (isIncrement() ? -1 : 1));
            }
            count = u16(count - 1);
            i++;
        } while (count > 0 && enabled);
        enabled = false;
        return cycles;
    }

    private int writeOneByte(int aAddress, int bAddress) {
        if (port == 0x80) {
            IMemory accessor = bus.getAccessor(aAddress);
            if (accessor != null && accessor.getComponent() == Component.WRAM) {
                if (getDirection() == 0) {
                    return 8;
                }
                bus.write(aAddress, 0xff);
                return 4;
            }
        }
        if (getDirection() == 0) {
            bus.write(bAddress, bus.read(aAddress));
        } else {
            bus.write(aAddress, bus.read(bAddress));
        }
        return 8;
    }

    private int getModeOffset(int index) {
        return switch (getMode()) {
            case ONE_TO_ONE, TWO_TO_ONE, TWO_TO_ONE_BIS -> 0;
            case TWO_TO_TWO, TWO_TO_TWO_BIS -> index % 2;
            case FOUR_TO_TWO, FOUR_TO_TWO_BIS -> (index & 0b11) > 1 ? 1 : 0;
            case FOUR_TO_FOUR -> index & 0b11;
            default -> 0;
        };
    }

    private int getAddressPage() {
        return aAddress & 0xffff;
    }

    private void setAddressPage(int page) {
        aAddress = u24((aAddress & 0xff0000) | u16(page));
    }

    public int getControlRegister() {
        return controlRegister;
    }

    public int getMode() {
        return controlRegister & 0b111;
    }

    public boolean isFixed() {
        return (controlRegister & 0b1000) != 0;
    }

    public boolean isIncrement() {
        return (controlRegister & 0b1_0000) != 0;
    }

    public int getDirection() {
        return (controlRegister >>> 7) & 1;
    }

    public int getPort() {
        return port;
    }

    public int getAAddress() {
        return aAddress;
    }

    public int getCount() {
        return count;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
