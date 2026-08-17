package jamsnes;

import jamsnes.ppu.Background;
import jamsnes.renderer.TestFrontend;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LocalRomSmokeTest {
    private static final String ROM_PROPERTY = "jamsnes.smoke.rom";
    private static final String UPDATES_PROPERTY = "jamsnes.smoke.updates";
    private static final String FRAME_CRC32_PROPERTY = "jamsnes.smoke.frameCrc32";
    private static final int DEFAULT_UPDATES = 500_000;

    @Test
    void localRomRunsForConfiguredUpdates() {
        String romProperty = System.getProperty(ROM_PROPERTY);
        assumeTrue(romProperty != null && !romProperty.isBlank(),
                () -> "Set -D" + ROM_PROPERTY + "=/path/to/local.rom to run this local-only smoke test.");
        Path rom = Path.of(romProperty);
        assumeTrue(Files.isRegularFile(rom), () -> "Local smoke ROM does not exist: " + rom);

        TestFrontend renderer = new TestFrontend(Background.BUFFER_SIZE, Background.BUFFER_SIZE, 60);
        SNES snes = assertDoesNotThrow(() -> new SNES(rom.toString(), renderer));
        int updates = smokeUpdates();

        assertDoesNotThrow(() -> {
            for (int i = 0; i < updates; i++) {
                snes.update();
            }
        });

        if (snes.cartridge.getType() == jamsnes.cartridge.CartridgeType.AUDIO) {
            assertTrue(renderer.audioCalls() > 0 || renderer.audioSamples() > 0,
                    "SPC smoke run should produce or attempt audio samples");
        } else {
            assertTrue(renderer.drawScreenCalls() > 0, "Game smoke run should draw at least one frame");
            assertEquals(0, snes.ppu.registers()[0] & 0x80,
                    "Game smoke run should leave PPU forced blank");
            assertTrue(renderer.hasNonUniformFrame(),
                    "Game smoke run should produce a frame containing more than one color");
            String expectedFrameCrc32 = System.getProperty(FRAME_CRC32_PROPERTY);
            if (expectedFrameCrc32 != null && !expectedFrameCrc32.isBlank()) {
                assertEquals(parseCrc32(expectedFrameCrc32), renderer.frameBufferCrc32(),
                        "Local smoke frame CRC32 mismatch");
            }
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

    private static long parseCrc32(String value) {
        String normalized = value.trim();
        if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
            normalized = normalized.substring(2);
        }
        return Long.parseUnsignedLong(normalized, 16) & 0xffffffffL;
    }
}
