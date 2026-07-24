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
    private int indirectBank;
    private int tableAddress;
    private int lineCounter;
    private int unusedMirror;
    private IMemoryBus bus;
    private boolean enabled;
    private boolean hdmaEnabled;
    private boolean hdmaActive;
    private boolean hdmaDoTransfer;
    private boolean hdmaRepeat;
    private int hdmaLineRemaining;
    private boolean dmaStartupPending;
    private int dmaModeIndex;

    public DMA(IMemoryBus bus) {
        this.bus = bus;
    }

    public void setBus(IMemoryBus bus) {
        this.bus = bus;
    }

    public void resetRuntimeState() {
        enabled = false;
        hdmaEnabled = false;
        hdmaActive = false;
        hdmaDoTransfer = false;
        hdmaRepeat = false;
        hdmaLineRemaining = 0;
        dmaStartupPending = false;
        dmaModeIndex = 0;
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
            case 0x7 -> indirectBank;
            case 0x8 -> tableAddress & 0xff;
            case 0x9 -> (tableAddress >>> 8) & 0xff;
            case 0xa -> lineCounter;
            case 0xb, 0xf -> unusedMirror;
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
            case 0x7 -> indirectBank = value;
            case 0x8 -> tableAddress = u16((tableAddress & 0xff00) | value);
            case 0x9 -> tableAddress = u16((tableAddress & 0x00ff) | (value << 8));
            case 0xa -> lineCounter = value;
            case 0xb, 0xf -> unusedMirror = value;
            case 0xc, 0xd, 0xe -> {
            }
            default -> throw new InvalidAddress("DMA write", address);
        }
    }

    public int run(int maxCycles) {
        if (!enabled || maxCycles <= 0) {
            return 0;
        }

        int cycles = 0;
        if (dmaStartupPending) {
            cycles += 8;
            dmaStartupPending = false;
        }

        while (enabled && cycles < maxCycles) {
            cycles += writeOneByte(aAddress, 0x2100 | u8(port + getModeOffset(dmaModeIndex)), getDirection());
            if (!isFixed()) {
                setAddressPage(getAddressPage() + (isIncrement() ? -1 : 1));
            }
            count = u16(count - 1);
            dmaModeIndex++;
            if (count == 0) {
                enabled = false;
            }
        }
        return cycles;
    }

    public int initializeHDMA() {
        if (!hdmaEnabled) {
            hdmaActive = false;
            return 0;
        }
        hdmaActive = true;
        tableAddress = getAddressPage();
        return loadNextHdmaLine();
    }

    public int runHDMALine() {
        if (!hdmaEnabled || !hdmaActive) {
            return 0;
        }
        int cycles = 0;
        if (hdmaDoTransfer) {
            cycles += transferHdmaBytes();
        }
        hdmaLineRemaining--;
        if (hdmaLineRemaining <= 0) {
            cycles += loadNextHdmaLine();
        } else {
            lineCounter = (hdmaRepeat ? 0x80 : 0) | (hdmaLineRemaining & 0x7f);
            hdmaDoTransfer = hdmaRepeat;
        }
        return cycles;
    }

    private int loadNextHdmaLine() {
        int tableBank = aAddress & 0xff0000;
        lineCounter = bus.read(tableBank | tableAddress);
        tableAddress = u16(tableAddress + 1);
        if (lineCounter == 0) {
            hdmaActive = false;
            hdmaDoTransfer = false;
            hdmaRepeat = false;
            hdmaLineRemaining = 0;
            return 8;
        }

        int cycles = 8;
        hdmaRepeat = lineCounter > 0x80;
        hdmaLineRemaining = lineCounter & 0x7f;
        if (hdmaLineRemaining == 0) {
            hdmaLineRemaining = 128;
        }
        hdmaDoTransfer = true;
        if (isHdmaIndirect()) {
            int low = bus.read(tableBank | tableAddress);
            tableAddress = u16(tableAddress + 1);
            int high = bus.read(tableBank | tableAddress);
            tableAddress = u16(tableAddress + 1);
            count = u16(low | (high << 8));
            cycles += 16;
        }
        return cycles;
    }

    private int transferHdmaBytes() {
        int cycles = 0;
        for (int i = 0; i < getHdmaTransferLength(); i++) {
            int source = hdmaSourceAddress();
            cycles += writeOneByte(source, 0x2100 | u8(port + getModeOffset(i)), 0);
            incrementHdmaSourceAddress();
        }
        return cycles;
    }

    private int hdmaSourceAddress() {
        if (isHdmaIndirect()) {
            return (indirectBank << 16) | count;
        }
        return (aAddress & 0xff0000) | tableAddress;
    }

    private void incrementHdmaSourceAddress() {
        if (isHdmaIndirect()) {
            count = u16(count + 1);
        } else {
            tableAddress = u16(tableAddress + 1);
        }
    }

    private int writeOneByte(int aAddress, int bAddress, int direction) {
        if (isInvalidABusAddress(aAddress)) {
            if (direction == 0) {
                bus.write(bAddress, bus.getOpenBus());
            } else {
                bus.read(bAddress);
            }
            return 8;
        }
        if (port == 0x80) {
            IMemory accessor = bus.getAccessor(aAddress);
            if (accessor != null && accessor.getComponent() == Component.WRAM) {
                if (direction == 0) {
                    return 8;
                }
                bus.write(aAddress, bus.getOpenBus());
                return 4;
            }
        }
        if (direction == 0) {
            bus.write(bAddress, bus.read(aAddress));
        } else {
            bus.write(aAddress, bus.read(bAddress));
        }
        return 8;
    }

    private boolean isInvalidABusAddress(int address) {
        int bank = (address >>> 16) & 0xff;
        if (bank > 0x3f && (bank < 0x80 || bank > 0xbf)) {
            return false;
        }
        int page = address & 0xffff;
        return (page >= 0x2100 && page <= 0x21ff)
                || (page >= 0x4000 && page <= 0x41ff)
                || (page >= 0x4200 && page <= 0x421f)
                || (page >= 0x4300 && page <= 0x437f);
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

    private int getHdmaTransferLength() {
        return switch (getMode()) {
            case ONE_TO_ONE -> 1;
            case TWO_TO_TWO, TWO_TO_ONE, TWO_TO_ONE_BIS -> 2;
            case FOUR_TO_TWO, FOUR_TO_FOUR, TWO_TO_TWO_BIS, FOUR_TO_TWO_BIS -> 4;
            default -> 1;
        };
    }

    private boolean isHdmaIndirect() {
        return (controlRegister & 0b0100_0000) != 0;
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

    public int getIndirectBank() {
        return indirectBank;
    }

    public int getTableAddress() {
        return tableAddress;
    }

    public int getLineCounter() {
        return lineCounter;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        if (enabled && !this.enabled) {
            dmaStartupPending = true;
            dmaModeIndex = 0;
        } else if (!enabled) {
            dmaStartupPending = false;
        }
        this.enabled = enabled;
    }

    public boolean isHdmaEnabled() {
        return hdmaEnabled;
    }

    public boolean isHdmaActive() {
        return hdmaActive;
    }

    public void setHdmaEnabled(boolean hdmaEnabled) {
        this.hdmaEnabled = hdmaEnabled;
        if (!hdmaEnabled) {
            hdmaActive = false;
            hdmaDoTransfer = false;
            hdmaRepeat = false;
            hdmaLineRemaining = 0;
        }
    }
}
