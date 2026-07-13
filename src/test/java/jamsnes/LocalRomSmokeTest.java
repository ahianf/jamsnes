package jamsnes;

import jamsnes.renderer.IRenderer;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LocalRomSmokeTest {
    private static final String ROM_PROPERTY = "jamsnes.smoke.rom";
    private static final String UPDATES_PROPERTY = "jamsnes.smoke.updates";
    private static final int DEFAULT_UPDATES = 600;

    @Test
    void localRomRunsForConfiguredUpdates() {
        String romProperty = System.getProperty(ROM_PROPERTY);
        assumeTrue(romProperty != null && !romProperty.isBlank(),
                () -> "Set -D" + ROM_PROPERTY + "=/path/to/local.rom to run this local-only smoke test.");
        Path rom = Path.of(romProperty);
        assumeTrue(Files.isRegularFile(rom), () -> "Local smoke ROM does not exist: " + rom);

        SmokeRenderer renderer = new SmokeRenderer();
        SNES snes = assertDoesNotThrow(() -> new SNES(rom.toString(), renderer));
        int updates = smokeUpdates();

        assertDoesNotThrow(() -> {
            for (int i = 0; i < updates; i++) {
                snes.update();
            }
        });

        if (snes.cartridge.getType() == jamsnes.cartridge.CartridgeType.AUDIO) {
            assertTrue(renderer.audioCalls > 0 || renderer.audioSamples > 0,
                    "SPC smoke run should produce or attempt audio samples");
        } else {
            assertTrue(renderer.drawScreenCalls > 0, "Game smoke run should draw at least one frame");
        }
    }

    private static int smokeUpdates() {
        String value = System.getProperty(UPDATES_PROPERTY);
        if (value == null || value.isBlank()) {
            return DEFAULT_UPDATES;
        }
        int updates = Integer.parseInt(value);
        if (updates <= 0) {
            throw new IllegalArgumentException(UPDATES_PROPERTY + " must be positive");
        }
        return updates;
    }

    private static final class SmokeRenderer implements IRenderer {
        private int drawScreenCalls;
        private int audioCalls;
        private int audioSamples;

        @Override
        public void setWindowName(String newWindowName) {
        }

        @Override
        public void drawScreen() {
            drawScreenCalls++;
        }

        @Override
        public void putPixel(int y, int x, int rgba) {
        }

        @Override
        public void createWindow(SNES snes, int maxFPS) {
        }

        @Override
        public void playAudio(short[] samples) {
            audioCalls++;
            audioSamples += samples.length;
        }
    }
}
