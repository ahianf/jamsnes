package jamsnes.input;

import jamsnes.exceptions.InvalidAddress;
import jamsnes.memory.AMemory;
import jamsnes.models.Component;

import static jamsnes.models.Unsigned.u16;

public class Joypad extends AMemory {
    public static final int BUTTON_B = 1 << 0;
    public static final int BUTTON_Y = 1 << 1;
    public static final int BUTTON_SELECT = 1 << 2;
    public static final int BUTTON_START = 1 << 3;
    public static final int BUTTON_UP = 1 << 4;
    public static final int BUTTON_DOWN = 1 << 5;
    public static final int BUTTON_LEFT = 1 << 6;
    public static final int BUTTON_RIGHT = 1 << 7;
    public static final int BUTTON_A = 1 << 8;
    public static final int BUTTON_X = 1 << 9;
    public static final int BUTTON_L = 1 << 10;
    public static final int BUTTON_R = 1 << 11;

    private final int[] controllerState = new int[2];
    private final int[] shiftRegister = new int[2];
    private boolean strobe;

    @Override
    public int read(int address) {
        validatePort(address, "Joypad read");
        if (strobe) {
            latchControllers();
        }

        int value = shiftRegister[address] & 1;
        if (!strobe) {
            shiftRegister[address] = u16((shiftRegister[address] >>> 1) | 0x8000);
        }
        return value;
    }

    @Override
    public void write(int address, int data) {
        validatePort(address, "Joypad write");
        if (address != 0) {
            return;
        }

        strobe = (data & 1) != 0;
        if (strobe) {
            latchControllers();
        }
    }

    public void setControllerState(int controller, int state) {
        validateController(controller);
        controllerState[controller] = u16(state);
        if (strobe) {
            shiftRegister[controller] = controllerState[controller];
        }
    }

    public int controllerState(int controller) {
        validateController(controller);
        return controllerState[controller];
    }

    public boolean isStrobe() {
        return strobe;
    }

    @Override
    public int getSize() {
        return 2;
    }

    @Override
    public String getName() {
        return "Joypad";
    }

    @Override
    public Component getComponent() {
        return Component.JOYPAD;
    }

    @Override
    public String getValueName(int address) {
        return switch (address) {
            case 0 -> "JOYSER0";
            case 1 -> "JOYSER1";
            default -> "???";
        };
    }

    private void latchControllers() {
        shiftRegister[0] = controllerState[0];
        shiftRegister[1] = controllerState[1];
    }

    private void validatePort(int address, String where) {
        if (address < 0 || address > 1) {
            throw new InvalidAddress(where, address + start);
        }
    }

    private void validateController(int controller) {
        if (controller < 0 || controller >= controllerState.length) {
            throw new InvalidAddress("Joypad controller", controller);
        }
    }
}
