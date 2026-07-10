package jamsnes;

import jamsnes.renderer.IRenderer;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {
    @TempDir
    Path tempDir;

    @Test
    void mainRuns() {
        assertDoesNotThrow(() -> Main.main(new String[0]));
    }

    @Test
    void runPrintsUsageWhenNoRomPathIsProvided() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = Main.run(new String[0], printStream(), new PrintStream(err));

        assertEquals(1, exitCode);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("Usage: jamsnes rom_path"));
    }

    @Test
    void runPrintsHelp() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int exitCode = Main.run(new String[]{"--help"}, new PrintStream(out), printStream());

        assertEquals(0, exitCode);
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Usage: jamsnes rom_path"));
    }

    @Test
    void runLoadsRomAndAdvancesEmulator() throws IOException {
        Path rom = writeGameRom();

        int exitCode = Main.run(new String[]{rom.toString()}, printStream(), printStream());

        assertEquals(0, exitCode);
    }

    @Test
    void runStartsRendererWindowForLoadedRom() throws IOException {
        Path rom = writeGameRom();
        TestRenderer renderer = new TestRenderer();

        int exitCode = Main.run(new String[]{rom.toString()}, printStream(), printStream(), renderer);

        assertEquals(0, exitCode);
        assertEquals(1, renderer.createWindowCalls);
        assertEquals(60, renderer.maxFPS);
        assertEquals(0x8000, renderer.snes.cpu.registers().pc);
    }

    @Test
    void noRendererCreateWindowAdvancesEmulatorOnce() {
        SNES snes = new SNES(new NoRenderer(0, 0, 0));
        snes.cpu.isDisabled = true;
        snes.apu.isDisabled = true;

        new NoRenderer(0, 0, 0).createWindow(snes, 60);

        assertEquals(0xff, snes.ppu.hCounter());
    }

    @Test
    void runReportsInvalidRomPath() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exitCode = Main.run(new String[]{tempDir.resolve("missing.sfc").toString()}, printStream(), new PrintStream(err));

        assertEquals(1, exitCode);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("Could not open the rom file"));
    }

    private Path writeGameRom() throws IOException {
        byte[] rom = new byte[0x8000];
        int base = 0x7f00;
        rom[0] = 0x78;
        byte[] name = "JAMSNES MAIN ROM".getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(name, 0, rom, 0x7fc0, name.length);
        rom[base + 0xd5] = 0x20;
        rom[base + 0xd6] = 0x00;
        rom[base + 0xd7] = 0x05;
        rom[base + 0xd8] = 0x00;
        rom[base + 0xfc] = 0x00;
        rom[base + 0xfd] = (byte) 0x80;
        Path romPath = tempDir.resolve("main.sfc");
        Files.write(romPath, rom);
        return romPath;
    }

    private static PrintStream printStream() {
        return new PrintStream(new ByteArrayOutputStream());
    }

    private static final class TestRenderer implements IRenderer {
        private SNES snes;
        private int maxFPS;
        private int createWindowCalls;

        @Override
        public void setWindowName(String newWindowName) {
        }

        @Override
        public void drawScreen() {
        }

        @Override
        public void putPixel(int y, int x, int rgba) {
        }

        @Override
        public void createWindow(SNES snes, int maxFPS) {
            this.snes = snes;
            this.maxFPS = maxFPS;
            createWindowCalls++;
        }

        @Override
        public void playAudio(short[] samples) {
        }
    }
}
