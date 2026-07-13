package jamsnes.input;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyboardJoypadMapperTest {
    @Test
    void mappedKeyPressAndReleaseTogglesControllerButton() {
        Joypad joypad = new Joypad();
        KeyboardJoypadMapper mapper = new KeyboardJoypadMapper(joypad, 0, Map.of(
                90, JoypadButton.B,
                88, JoypadButton.A
        ));

        assertTrue(mapper.handleKey(90, true));
        assertEquals(Joypad.BUTTON_B, joypad.controllerState(0));

        assertTrue(mapper.handleKey(88, true));
        assertEquals(Joypad.BUTTON_B | Joypad.BUTTON_A, joypad.controllerState(0));

        assertTrue(mapper.handleKey(90, false));
        assertEquals(Joypad.BUTTON_A, joypad.controllerState(0));
    }

    @Test
    void unmappedKeyDoesNotChangeControllerState() {
        Joypad joypad = new Joypad();
        joypad.setControllerState(0, Joypad.BUTTON_START);
        KeyboardJoypadMapper mapper = new KeyboardJoypadMapper(joypad, 0, Map.of(90, JoypadButton.B));

        assertFalse(mapper.handleKey(65, true));

        assertEquals(Joypad.BUTTON_START, joypad.controllerState(0));
    }

    @Test
    void bindingsAreDefensivelyCopied() {
        Joypad joypad = new Joypad();
        Map<Integer, JoypadButton> bindings = new HashMap<>();
        bindings.put(90, JoypadButton.B);
        KeyboardJoypadMapper mapper = new KeyboardJoypadMapper(joypad, 0, bindings);

        bindings.put(90, JoypadButton.A);

        assertTrue(mapper.handleKey(90, true));
        assertEquals(Joypad.BUTTON_B, joypad.controllerState(0));
        assertThrows(UnsupportedOperationException.class, () -> mapper.bindings().put(88, JoypadButton.A));
    }
}
