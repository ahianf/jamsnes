package jamsnes.input;

import java.util.Map;
import java.util.Objects;

public final class KeyboardJoypadMapper {
    private final Joypad joypad;
    private final int controller;
    private final Map<Integer, JoypadButton> bindings;

    public KeyboardJoypadMapper(Joypad joypad, int controller, Map<Integer, JoypadButton> bindings) {
        this.joypad = Objects.requireNonNull(joypad, "joypad");
        joypad.controllerState(controller);
        this.controller = controller;
        this.bindings = Map.copyOf(Objects.requireNonNull(bindings, "bindings"));
    }

    public boolean handleKey(int keyCode, boolean pressed) {
        JoypadButton button = bindings.get(keyCode);
        if (button == null) {
            return false;
        }

        joypad.setButtonPressed(controller, button, pressed);
        return true;
    }

    public Map<Integer, JoypadButton> bindings() {
        return bindings;
    }
}
