package jamsnes.runtime;

import jamsnes.SNES;

import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class EmulatorLoop {
    private EmulatorLoop() {
    }

    public static int runWhile(SNES snes, BooleanSupplier shouldContinue) {
        Objects.requireNonNull(snes, "snes");
        Objects.requireNonNull(shouldContinue, "shouldContinue");
        int updates = 0;
        while (shouldContinue.getAsBoolean()) {
            snes.update();
            updates++;
        }
        return updates;
    }

    public static int runForUpdates(SNES snes, int updates) {
        if (updates < 0) {
            throw new IllegalArgumentException("updates must be non-negative");
        }
        return runWhile(snes, new FixedUpdateCounter(updates));
    }

    private static final class FixedUpdateCounter implements BooleanSupplier {
        private final int updates;
        private int current;

        private FixedUpdateCounter(int updates) {
            this.updates = updates;
        }

        @Override
        public boolean getAsBoolean() {
            return current++ < updates;
        }
    }
}
