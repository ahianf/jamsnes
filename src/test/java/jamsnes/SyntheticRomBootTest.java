package jamsnes;

import jamsnes.ppu.Background;
import jamsnes.ppu.PPUUtils;
import jamsnes.renderer.FrameBufferRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyntheticRomBootTest {
    @TempDir
    Path tempDir;

    @Test
    void loadedLoRomExecutesPpuInitializationAndPresentsAFrame() throws IOException {
        FrameBufferRenderer renderer =
                new FrameBufferRenderer(Background.BUFFER_SIZE, Background.BUFFER_SIZE, 60);
        SNES snes = new SNES(writeBootRom().toString(), renderer);

        for (int updates = 0; renderer.drawScreenCalls() == 0 && updates < 10_000; updates++) {
            snes.update();
        }

        assertTrue(renderer.drawScreenCalls() > 0, "Synthetic ROM should reach VBlank and present a frame");
        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), renderer.pixel(0, 0));
        assertEquals(0x8015, snes.cpu.registers().pc);
    }

    private Path writeBootRom() throws IOException {
        byte[] rom = new byte[0x8000];
        byte[] program = {
                (byte) 0x78,                         // SEI
                (byte) 0xa9, 0x00,                   // LDA #$00
                (byte) 0x8d, 0x21, 0x21,             // STA $2121 (CGADD)
                (byte) 0xa9, 0x1f,                   // LDA #$1f
                (byte) 0x8d, 0x22, 0x21,             // STA $2122 (CGDATA low)
                (byte) 0xa9, 0x00,                   // LDA #$00
                (byte) 0x8d, 0x22, 0x21,             // STA $2122 (CGDATA high)
                (byte) 0xa9, 0x0f,                   // LDA #$0f
                (byte) 0x8d, 0x00, 0x21,             // STA $2100 (INIDISP)
                (byte) 0x80, (byte) 0xfe             // BRA *
        };
        System.arraycopy(program, 0, rom, 0, program.length);
        byte[] title = "JAMSNES BOOT PROBE".getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(title, 0, rom, 0x7fc0, title.length);
        rom[0x7fd5] = 0x20;
        rom[0x7fd6] = 0x00;
        rom[0x7fd7] = 0x05;
        rom[0x7fd8] = 0x00;
        rom[0x7ffc] = 0x00;
        rom[0x7ffd] = (byte) 0x80;
        Path path = tempDir.resolve("synthetic-boot.sfc");
        Files.write(path, rom);
        return path;
    }
}
