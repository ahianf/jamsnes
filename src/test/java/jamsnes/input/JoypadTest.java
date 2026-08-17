package jamsnes.input;

import jamsnes.SNES;
import jamsnes.audio.RecordingAudioSink;
import jamsnes.video.RecordingVideoSink;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

        assertEquals(0x1c, snes.bus.read(0x4017));
        assertEquals(0x1d, snes.bus.read(0x4017));
        assertEquals(0x1d, snes.bus.read(0x4017));
        assertEquals(0x1c, snes.bus.read(0x4017));
        assertEquals(0x1c, snes.bus.read(0x4017));
        assertEquals(0x1c, snes.bus.read(0x4017));
        assertEquals(0x1d, snes.bus.read(0x4017));
    }

    @Test
    void serialReadsPreserveOpenBusBitsAndJoyser1Pullups() {
        SNES snes = init();
        snes.joypad.setControllerState(0, Joypad.BUTTON_B);
        snes.joypad.setControllerState(1, Joypad.BUTTON_B);
        snes.bus.write(0x4016, 1);
        snes.bus.write(0x4016, 0);

        snes.bus.setOpenBus(0xa4);
        assertEquals(0xa5, snes.bus.read(0x4016));

        snes.bus.setOpenBus(0xa4);
        assertEquals(0xbd, snes.bus.read(0x4017));
    }

    @Test
    void automaticReadClocksCompleteReportsAndLeavesSerialPositionAfterSignature() {
        SNES snes = init();
        snes.joypad.setControllerState(0, Joypad.BUTTON_B | Joypad.BUTTON_START | Joypad.BUTTON_A);
        snes.joypad.setControllerState(1, Joypad.BUTTON_Y | Joypad.BUTTON_L | Joypad.BUTTON_R);

        int[] reports = snes.joypad.autoRead();

        assertEquals(0x9080, reports[0]);
        assertEquals(0x4030, reports[1]);
        assertFalse(snes.joypad.isStrobe());
        assertEquals(1, snes.bus.read(0x4016) & 1);
        assertEquals(1, snes.bus.read(0x4017) & 1);
    }

    @Test
    void cpuStrobeRemainsAssertedAcrossAutomaticLatchPulses() {
        SNES snes = init();
        snes.joypad.setControllerState(0, Joypad.BUTTON_B);
        snes.bus.write(0x4016, 1);

        int[] reports = snes.joypad.autoRead();

        assertTrue(snes.joypad.isStrobe());
        assertEquals(0xffff, reports[0]);
        assertEquals(1, snes.bus.read(0x4016) & 1);
        assertEquals(1, snes.bus.read(0x4016) & 1);
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

    @Test
    void buttonsExposeSerialReadBitOrder() {
        assertEquals(1 << 0, JoypadButton.B.mask());
        assertEquals(1 << 1, JoypadButton.Y.mask());
        assertEquals(1 << 2, JoypadButton.SELECT.mask());
        assertEquals(1 << 3, JoypadButton.START.mask());
        assertEquals(1 << 4, JoypadButton.UP.mask());
        assertEquals(1 << 5, JoypadButton.DOWN.mask());
        assertEquals(1 << 6, JoypadButton.LEFT.mask());
        assertEquals(1 << 7, JoypadButton.RIGHT.mask());
        assertEquals(1 << 8, JoypadButton.A.mask());
        assertEquals(1 << 9, JoypadButton.X.mask());
        assertEquals(1 << 10, JoypadButton.L.mask());
        assertEquals(1 << 11, JoypadButton.R.mask());
    }

    @Test
    void setButtonPressedUpdatesOnlyTheSelectedButton() {
        SNES snes = init();
        snes.joypad.setControllerState(0, Joypad.BUTTON_START);

        snes.joypad.setButtonPressed(0, JoypadButton.B, true);
        snes.joypad.setButtonPressed(0, JoypadButton.A, true);
        snes.joypad.setButtonPressed(0, JoypadButton.B, false);

        assertEquals(Joypad.BUTTON_START | Joypad.BUTTON_A, snes.joypad.controllerState(0));
        assertTrue(snes.joypad.isButtonPressed(0, JoypadButton.A));
        assertFalse(snes.joypad.isButtonPressed(0, JoypadButton.B));
    }

    @Test
    void setButtonPressedRefreshesLatchedStateWhileStrobeIsHigh() {
        SNES snes = init();
        snes.bus.write(0x4016, 1);

        snes.joypad.setButtonPressed(0, JoypadButton.B, true);

        assertEquals(1, snes.bus.read(0x4016));
    }

    private static SNES init() {
        SNES snes = new SNES(new RecordingVideoSink(), new RecordingAudioSink());
        snes.bus.mapComponents(snes);
        return snes;
    }
}
