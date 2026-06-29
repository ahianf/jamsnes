package jamsnes;

import jamsnes.ppu.Background;
import jamsnes.renderer.IRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SNESTest {
    @TempDir
    Path tempDir;

    @Test
    void loadRomMapsComponentsAndResetsCpuAndApu() throws IOException {
        SNES snes = new SNES(new TestRenderer());
        snes.cpu.registers().setPc(0x1234);
        snes.apu.internalRegisters().pc = 0x1234;
        Path rom = writeGameRom();

        snes.loadRom(rom.toString());

        assertEquals(0x8000, snes.cpu.registers().pc);
        assertEquals(0xffc0, snes.apu.internalRegisters().pc);
        assertEquals(0x78, snes.bus.read(0x808000));
    }

    @Test
    void updateRunsCpuPpuAndApuForGameCartridges() {
        TestRenderer renderer = new TestRenderer();
        SNES snes = new SNES(renderer);
        snes.cpu.isDisabled = true;
        snes.apu.isDisabled = true;

        snes.update();

        assertEquals(1, renderer.drawScreenCalls);
        assertEquals((long) Background.BUFFER_SIZE * Background.BUFFER_SIZE, renderer.putPixelCalls);
    }

    @Test
    void updateRunsOnlyApuForAudioCartridges() throws IOException {
        TestRenderer renderer = new TestRenderer();
        SNES snes = new SNES(renderer);
        Path spc = writeSpcFile();

        snes.loadRom(spc.toString());
        snes.update();

        assertEquals(0, renderer.drawScreenCalls);
        assertEquals(0, renderer.putPixelCalls);
    }

    private Path writeGameRom() throws IOException {
        byte[] rom = new byte[0x8000];
        int base = 0x7f00;
        rom[0] = 0x78;
        byte[] name = "JAMSNES TEST ROM".getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(name, 0, rom, 0x7fc0, name.length);
        rom[base + 0xd5] = 0x20;
        rom[base + 0xd6] = 0x00;
        rom[base + 0xd7] = 0x05;
        rom[base + 0xd8] = 0x00;
        rom[base + 0xfc] = 0x00;
        rom[base + 0xfd] = (byte) 0x80;
        Path romPath = tempDir.resolve("game.sfc");
        Files.write(romPath, rom);
        return romPath;
    }

    private Path writeSpcFile() throws IOException {
        byte[] spc = new byte[0x25];
        byte[] magic = "SNES-SPC700 Sound File Data v0.30".getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(magic, 0, spc, 0, magic.length);
        spc[0x21] = 0x1a;
        spc[0x22] = 0x1a;
        spc[0x23] = 0x1a;
        spc[0x24] = 0x1e;
        Path spcPath = tempDir.resolve("audio.spc");
        Files.write(spcPath, spc);
        return spcPath;
    }

    private static final class TestRenderer implements IRenderer {
        private long putPixelCalls;
        private int drawScreenCalls;

        @Override
        public void setWindowName(String newWindowName) {
        }

        @Override
        public void drawScreen() {
            drawScreenCalls++;
        }

        @Override
        public void putPixel(int y, int x, int rgba) {
            putPixelCalls++;
        }

        @Override
        public void createWindow(SNES snes, int maxFPS) {
        }

        @Override
        public void playAudio(short[] samples) {
        }
    }
}
