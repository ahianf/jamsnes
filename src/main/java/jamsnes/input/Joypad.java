package jamsnes.input;

import jamsnes.exceptions.InvalidAddress;
import jamsnes.memory.AMemory;
import jamsnes.memory.IMemoryBus;
import jamsnes.models.Component;

import java.util.Objects;

import static jamsnes.models.Unsigned.u16;

public class Joypad extends AMemory {
    public static final int BUTTON_B = JoypadButton.B.mask();
    public static final int BUTTON_Y = JoypadButton.Y.mask();
    public static final int BUTTON_SELECT = JoypadButton.SELECT.mask();
    public static final int BUTTON_START = JoypadButton.START.mask();
    public static final int BUTTON_UP = JoypadButton.UP.mask();
    public static final int BUTTON_DOWN = JoypadButton.DOWN.mask();
    public static final int BUTTON_LEFT = JoypadButton.LEFT.mask();
    public static final int BUTTON_RIGHT = JoypadButton.RIGHT.mask();
    public static final int BUTTON_A = JoypadButton.A.mask();
    public static final int BUTTON_X = JoypadButton.X.mask();
    public static final int BUTTON_L = JoypadButton.L.mask();
    public static final int BUTTON_R = JoypadButton.R.mask();

    private final int[] controllerState = new int[2];
    private final int[] shiftRegister = new int[2];
    private final IMemoryBus bus;
    private boolean cpuStrobe;
    private boolean autoStrobe;

    public Joypad() {
        this(null);
    }

    public Joypad(IMemoryBus bus) {
        this.bus = bus;
    }

    @Override
    public int read(int address) {
        validatePort(address, "Joypad read");
        int value = readSerialBit(address);
        int openBus = bus == null ? 0 : bus.getOpenBus();
        if (address == 0) {
            return (openBus & 0xfc) | value;
        }
        return (openBus & 0xe0) | 0x1c | value;
    }

    public int[] autoRead() {
        beginAutoRead();
        releaseAutoReadLatch();

        int[] reports = new int[controllerState.length];
        for (int bit = 0; bit < 16; bit++) {
            int[] values = clockAutoReadBit();
            for (int controller = 0; controller < values.length; controller++) {
                reports[controller] = (reports[controller] << 1) | values[controller];
            }
        }
        return reports;
    }

    public void beginAutoRead() {
        autoStrobe = true;
        latchControllers();
    }

    public void releaseAutoReadLatch() {
        autoStrobe = false;
    }

    public int[] clockAutoReadBit() {
        int[] values = new int[controllerState.length];
        for (int controller = 0; controller < values.length; controller++) {
            values[controller] = readSerialBit(controller);
        }
        return values;
    }

    @Override
    public void write(int address, int data) {
        validatePort(address, "Joypad write");
        if (address != 0) {
            return;
        }

        cpuStrobe = (data & 1) != 0;
        if (isStrobe()) {
            latchControllers();
        }
    }

    public void setControllerState(int controller, int state) {
        validateController(controller);
        controllerState[controller] = u16(state);
        if (isStrobe()) {
            shiftRegister[controller] = controllerState[controller];
        }
    }

    public void setButtonPressed(int controller, JoypadButton button, boolean pressed) {
        validateController(controller);
        Objects.requireNonNull(button, "button");

        int state = controllerState[controller];
        if (pressed) {
            state |= button.mask();
        } else {
            state &= ~button.mask();
        }
        setControllerState(controller, state);
    }

    public boolean isButtonPressed(int controller, JoypadButton button) {
        validateController(controller);
        Objects.requireNonNull(button, "button");
        return (controllerState[controller] & button.mask()) != 0;
    }

    public int controllerState(int controller) {
        validateController(controller);
        return controllerState[controller];
    }

    public boolean isStrobe() {
        return cpuStrobe || autoStrobe;
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

    private int readSerialBit(int controller) {
        if (isStrobe()) {
            latchControllers();
        }

        int value = shiftRegister[controller] & 1;
        if (!isStrobe()) {
            shiftRegister[controller] = u16((shiftRegister[controller] >>> 1) | 0x8000);
        }
        return value;
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
