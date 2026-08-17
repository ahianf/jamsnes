package jamsnes.runtime;

import jamsnes.SNES;
import jamsnes.renderer.TestFrontend;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmulatorLoopTest {
    @Test
    void runsFixedNumberOfUpdates() {
        CountingSNES snes = new CountingSNES();

        int updates = EmulatorLoop.runForUpdates(snes, 3);

        assertEquals(3, updates);
        assertEquals(3, snes.updateCalls);
    }

    @Test
    void runsWhileConditionAllowsUpdates() {
        CountingSNES snes = new CountingSNES();
        AtomicInteger remaining = new AtomicInteger(2);

        int updates = EmulatorLoop.runWhile(snes, () -> remaining.getAndDecrement() > 0);

        assertEquals(2, updates);
        assertEquals(2, snes.updateCalls);
    }

    @Test
    void zeroUpdatesDoesNotAdvanceConsole() {
        CountingSNES snes = new CountingSNES();

        int updates = EmulatorLoop.runForUpdates(snes, 0);

        assertEquals(0, updates);
        assertEquals(0, snes.updateCalls);
    }

    @Test
    void rejectsInvalidLoopArguments() {
        CountingSNES snes = new CountingSNES();

        assertThrows(NullPointerException.class, () -> EmulatorLoop.runWhile(null, () -> false));
        assertThrows(NullPointerException.class, () -> EmulatorLoop.runWhile(snes, null));
        assertThrows(IllegalArgumentException.class, () -> EmulatorLoop.runForUpdates(snes, -1));
    }

    private static final class CountingSNES extends SNES {
        private int updateCalls;

        private CountingSNES() {
            super(new TestFrontend(0, 0, 0));
        }

        @Override
        public void update() {
            updateCalls++;
        }
    }
}
