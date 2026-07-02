package jamsnes.input;

import jamsnes.SNES;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoypadTest {
    @Test
    void strobeLatchesControllerStateAndSerialReadsShiftButtons() {
        SNES snes = init();
        snes.joypad.setControllerState(0, Joypad.BUTTON_B | Joypad.BUTTON_START | Joypad.BUTTON_A | Joypad.BUTTON_R);

        snes.bus.write(0x4016, 1);
        assertTrue(snes.joypad.isStrobe());
        assertEquals(1, snes.bus.read(0x4016));
        assertEquals(1, snes.bus.read(0x4016));

        snes.bus.write(0x4016, 0);

        int[] expected = {
                1, 0, 0, 1,
                0, 0, 0, 0,
                1, 0, 0, 1,
                0, 0, 0, 0,
                1
        };
        for (int bit : expected) {
            assertEquals(bit, snes.bus.read(0x4016));
        }
    }

    @Test
    void readsSecondControllerFromJoyser1() {
        SNES snes = init();
        snes.joypad.setControllerState(1, Joypad.BUTTON_Y | Joypad.BUTTON_SELECT | Joypad.BUTTON_LEFT);

        snes.bus.write(0x4016, 1);
        snes.bus.write(0x4016, 0);

        assertEquals(0, snes.bus.read(0x4017));
        assertEquals(1, snes.bus.read(0x4017));
        assertEquals(1, snes.bus.read(0x4017));
        assertEquals(0, snes.bus.read(0x4017));
        assertEquals(0, snes.bus.read(0x4017));
        assertEquals(0, snes.bus.read(0x4017));
        assertEquals(1, snes.bus.read(0x4017));
    }

    @Test
    void strobeHighRefreshesLatchedState() {
        SNES snes = init();

        snes.bus.write(0x4016, 1);
        assertEquals(0, snes.bus.read(0x4016));

        snes.joypad.setControllerState(0, Joypad.BUTTON_B);
        assertEquals(1, snes.bus.read(0x4016));
        assertEquals(1, snes.bus.read(0x4016));
    }

    private static SNES init() {
        SNES snes = new SNES(new NoRenderer(0, 0, 0));
        snes.bus.mapComponents(snes);
        return snes;
    }
}
