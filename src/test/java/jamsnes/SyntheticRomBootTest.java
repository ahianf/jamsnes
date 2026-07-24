package jamsnes;

import jamsnes.cartridge.MappingMode;
import jamsnes.input.Joypad;
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

    @Test
    void loadedHiRomUsesBankZeroResetMirrorAndPresentsAFrame() throws IOException {
        FrameBufferRenderer renderer =
                new FrameBufferRenderer(Background.BUFFER_SIZE, Background.BUFFER_SIZE, 60);
        SNES snes = new SNES(writeHiRomBootRom().toString(), renderer);

        for (int updates = 0; renderer.drawScreenCalls() == 0 && updates < 10_000; updates++) {
            snes.update();
        }

        assertTrue(snes.cartridge.header.hasMappingMode(MappingMode.HIROM));
        assertEquals(0x78, snes.bus.read(0x008000), "Bank-zero reset mirror should expose the HiROM program");
        assertTrue(renderer.drawScreenCalls() > 0, "Synthetic HiROM should reach VBlank and present a frame");
        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), renderer.pixel(0, 0));
        assertEquals(0x8015, snes.cpu.registers().pc);
    }

    @Test
    void loadedLoRomRunsNmiHandlerAndPresentsItsPaletteUpdate() throws IOException {
        FrameBufferRenderer renderer =
                new FrameBufferRenderer(Background.BUFFER_SIZE, Background.BUFFER_SIZE, 60);
        SNES snes = new SNES(writeNmiRom().toString(), renderer);

        for (int updates = 0; renderer.drawScreenCalls() < 2 && updates < 20_000; updates++) {
            snes.update();
        }

        assertEquals(2, renderer.drawScreenCalls(), "Synthetic ROM should present the frame after its first NMI");
        assertEquals(1, snes.wram.data()[0], "The NMI handler should run exactly once before the second frame");
        assertEquals(PPUUtils.cgramColorToRGBA(0x03e0), renderer.pixel(0, 0));
        assertEquals(0x801a, snes.cpu.registers().pc, "RTI should return execution to the idle loop");
    }

    @Test
    void loadedLoRomRunsDmaFromCartridgeIntoCgramBeforePresenting() throws IOException {
        FrameBufferRenderer renderer =
                new FrameBufferRenderer(Background.BUFFER_SIZE, Background.BUFFER_SIZE, 60);
        SNES snes = new SNES(writeDmaRom().toString(), renderer);

        for (int updates = 0; renderer.drawScreenCalls() == 0 && updates < 10_000; updates++) {
            snes.update();
        }

        assertTrue(renderer.drawScreenCalls() > 0, "Synthetic ROM should present its DMA-loaded palette");
        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), renderer.pixel(0, 0));
        assertEquals(0, snes.cpu.dmaChannels()[0].getCount());
        assertEquals(0x008102, snes.cpu.dmaChannels()[0].getAAddress());
        assertEquals(0x8038, snes.cpu.registers().pc);
    }

    @Test
    void loadedLoRomRunsHdmaAtHBlankAndPreservesThePriorScanline() throws IOException {
        FrameBufferRenderer renderer =
                new FrameBufferRenderer(Background.BUFFER_SIZE, Background.BUFFER_SIZE, 60);
        SNES snes = new SNES(writeHdmaRom().toString(), renderer);

        for (int updates = 0; renderer.drawScreenCalls() < 2 && updates < 20_000; updates++) {
            snes.update();
        }

        assertEquals(2, renderer.drawScreenCalls(), "Synthetic ROM should present its HDMA-updated frame");
        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), renderer.pixel(0, 0));
        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), renderer.pixel(1, 0));
        assertEquals(PPUUtils.cgramColorToRGBA(0x03e0), renderer.pixel(2, 0));
        assertEquals(0x8103, snes.cpu.dmaChannels()[0].getTableAddress());
        assertEquals(0x8114, snes.cpu.dmaChannels()[1].getTableAddress());
        assertEquals(0x8051, snes.cpu.registers().pc);
    }

    @Test
    void loadedLoRomReadsAutomaticJoypadStateInNmiAndUpdatesTheFrame() throws IOException {
        FrameBufferRenderer renderer =
                new FrameBufferRenderer(Background.BUFFER_SIZE, Background.BUFFER_SIZE, 60);
        SNES snes = new SNES(writeJoypadRom().toString(), renderer);
        snes.joypad.setControllerState(0, Joypad.BUTTON_B);

        for (int updates = 0; renderer.drawScreenCalls() < 2 && updates < 20_000; updates++) {
            snes.update();
        }

        assertEquals(2, renderer.drawScreenCalls(), "Synthetic ROM should present its input-updated frame");
        assertEquals(0x80, snes.cpu.internalRegisters()[0x19], "Auto-read should place B in JOY1H bit 7");
        assertEquals(1, snes.wram.data()[0], "The NMI handler should observe B exactly once before frame two");
        assertEquals(PPUUtils.cgramColorToRGBA(0x03e0), renderer.pixel(0, 0));
        assertEquals(0x801a, snes.cpu.registers().pc, "RTI should return execution to the idle loop");
    }

    @Test
    void loadedLoRomWaitsForTheApuIplHandshakeBeforePresenting() throws IOException {
        FrameBufferRenderer renderer =
                new FrameBufferRenderer(Background.BUFFER_SIZE, Background.BUFFER_SIZE, 60);
        SNES snes = new SNES(writeApuHandshakeRom().toString(), renderer);

        for (int updates = 0; renderer.drawScreenCalls() < 2 && updates < 20_000; updates++) {
            snes.update();
        }

        assertEquals(2, renderer.drawScreenCalls(), "Synthetic ROM should present after the APU IPL handshake");
        assertEquals(0xaa, snes.bus.read(0x002140));
        assertEquals(0xbb, snes.bus.read(0x002141));
        assertEquals(0x5a, snes.wram.data()[0], "Cartridge code should observe both IPL signature bytes");
        assertEquals(PPUUtils.cgramColorToRGBA(0x03e0), renderer.pixel(0, 0));
        assertEquals(0x802d, snes.cpu.registers().pc);
    }

    @Test
    void loadedLoRomUploadsAndLaunchesAnApuProgramBeforePresenting() throws IOException {
        FrameBufferRenderer renderer =
                new FrameBufferRenderer(Background.BUFFER_SIZE, Background.BUFFER_SIZE, 60);
        SNES snes = new SNES(writeApuUploadRom().toString(), renderer);

        for (int updates = 0; renderer.drawScreenCalls() < 2 && updates < 20_000; updates++) {
            snes.update();
        }

        assertEquals(2, renderer.drawScreenCalls(), "Synthetic ROM should present after launching uploaded APU code");
        assertEquals(0x8f, snes.apu._internalRead(0x0200));
        assertEquals(0x5a, snes.apu._internalRead(0x0201));
        assertEquals(0xf6, snes.apu._internalRead(0x0202));
        assertEquals(0xff, snes.apu._internalRead(0x0203));
        assertEquals(0x5a, snes.bus.read(0x002142), "Uploaded SPC700 code should signal through output port 2");
        assertEquals(0xa5, snes.wram.data()[0], "Cartridge code should observe the uploaded program's signal");
        assertEquals(PPUUtils.cgramColorToRGBA(0x001f), renderer.pixel(0, 0));
        assertEquals(0x80ae, snes.cpu.registers().pc);
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

    private Path writeHiRomBootRom() throws IOException {
        byte[] rom = new byte[0x10000];
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
        System.arraycopy(program, 0, rom, 0x8000, program.length);
        byte[] title = "JAMSNES HIROM BOOT".getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(title, 0, rom, 0xffc0, title.length);
        rom[0xffd5] = 0x21;
        rom[0xffd6] = 0x00;
        rom[0xffd7] = 0x06;
        rom[0xffd8] = 0x00;
        rom[0xfffc] = 0x00;
        rom[0xfffd] = (byte) 0x80;
        Path path = tempDir.resolve("synthetic-hirom-boot.sfc");
        Files.write(path, rom);
        return path;
    }

    private Path writeNmiRom() throws IOException {
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
                (byte) 0xa9, (byte) 0x80,             // LDA #$80
                (byte) 0x8d, 0x00, 0x42,             // STA $4200 (NMITIMEN)
                (byte) 0x80, (byte) 0xfe             // BRA *
        };
        byte[] nmiHandler = {
                (byte) 0xa9, 0x00,                   // LDA #$00
                (byte) 0x8d, 0x21, 0x21,             // STA $2121 (CGADD)
                (byte) 0xa9, (byte) 0xe0,             // LDA #$e0
                (byte) 0x8d, 0x22, 0x21,             // STA $2122 (CGDATA low)
                (byte) 0xa9, 0x03,                   // LDA #$03
                (byte) 0x8d, 0x22, 0x21,             // STA $2122 (CGDATA high)
                (byte) 0xee, 0x00, 0x00,             // INC $0000
                (byte) 0x40                          // RTI
        };
        System.arraycopy(program, 0, rom, 0, program.length);
        System.arraycopy(nmiHandler, 0, rom, 0x100, nmiHandler.length);
        byte[] title = "JAMSNES NMI PROBE".getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(title, 0, rom, 0x7fc0, title.length);
        rom[0x7fd5] = 0x20;
        rom[0x7fd6] = 0x00;
        rom[0x7fd7] = 0x05;
        rom[0x7fd8] = 0x00;
        rom[0x7ffa] = 0x00;
        rom[0x7ffb] = (byte) 0x81;
        rom[0x7ffc] = 0x00;
        rom[0x7ffd] = (byte) 0x80;
        Path path = tempDir.resolve("synthetic-nmi.sfc");
        Files.write(path, rom);
        return path;
    }

    private Path writeDmaRom() throws IOException {
        byte[] rom = new byte[0x8000];
        byte[] program = {
                (byte) 0x78,                         // SEI
                (byte) 0xa9, (byte) 0x80,            // LDA #$80
                (byte) 0x8d, 0x00, 0x21,             // STA $2100 (forced blank)
                (byte) 0xa9, 0x00,                   // LDA #$00
                (byte) 0x8d, 0x21, 0x21,             // STA $2121 (CGADD)
                (byte) 0xa9, 0x02,                   // LDA #$02
                (byte) 0x8d, 0x00, 0x43,             // STA $4300 (two bytes to one register)
                (byte) 0xa9, 0x22,                   // LDA #$22
                (byte) 0x8d, 0x01, 0x43,             // STA $4301 (CGDATA)
                (byte) 0xa9, 0x00,                   // LDA #$00
                (byte) 0x8d, 0x02, 0x43,             // STA $4302 (A1T low)
                (byte) 0xa9, (byte) 0x81,            // LDA #$81
                (byte) 0x8d, 0x03, 0x43,             // STA $4303 (A1T high)
                (byte) 0xa9, 0x00,                   // LDA #$00
                (byte) 0x8d, 0x04, 0x43,             // STA $4304 (A1 bank)
                (byte) 0xa9, 0x02,                   // LDA #$02
                (byte) 0x8d, 0x05, 0x43,             // STA $4305 (transfer size low)
                (byte) 0xa9, 0x00,                   // LDA #$00
                (byte) 0x8d, 0x06, 0x43,             // STA $4306 (transfer size high)
                (byte) 0xa9, 0x01,                   // LDA #$01
                (byte) 0x8d, 0x0b, 0x42,             // STA $420b (start DMA)
                (byte) 0xa9, 0x0f,                   // LDA #$0f
                (byte) 0x8d, 0x00, 0x21,             // STA $2100 (display on)
                (byte) 0x80, (byte) 0xfe             // BRA *
        };
        System.arraycopy(program, 0, rom, 0, program.length);
        rom[0x100] = 0x1f;
        rom[0x101] = 0x00;
        byte[] title = "JAMSNES DMA PROBE".getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(title, 0, rom, 0x7fc0, title.length);
        rom[0x7fd5] = 0x20;
        rom[0x7fd6] = 0x00;
        rom[0x7fd7] = 0x05;
        rom[0x7fd8] = 0x00;
        rom[0x7ffc] = 0x00;
        rom[0x7ffd] = (byte) 0x80;
        Path path = tempDir.resolve("synthetic-dma.sfc");
        Files.write(path, rom);
        return path;
    }

    private Path writeHdmaRom() throws IOException {
        byte[] rom = new byte[0x8000];
        byte[] program = {
                (byte) 0x78,                         // SEI
                (byte) 0xa9, (byte) 0x80,            // LDA #$80
                (byte) 0x8d, 0x00, 0x21,             // STA $2100 (forced blank)
                (byte) 0xa9, 0x00,                   // LDA #$00
                (byte) 0x8d, 0x21, 0x21,             // STA $2121 (CGADD)
                (byte) 0xa9, 0x1f,                   // LDA #$1f
                (byte) 0x8d, 0x22, 0x21,             // STA $2122 (CGDATA low)
                (byte) 0xa9, 0x00,                   // LDA #$00
                (byte) 0x8d, 0x22, 0x21,             // STA $2122 (CGDATA high)
                (byte) 0xa9, 0x00,                   // LDA #$00
                (byte) 0x8d, 0x00, 0x43,             // STA $4300 (one byte)
                (byte) 0xa9, 0x21,                   // LDA #$21
                (byte) 0x8d, 0x01, 0x43,             // STA $4301 (CGADD)
                (byte) 0xa9, 0x00,                   // LDA #$00
                (byte) 0x8d, 0x02, 0x43,             // STA $4302 (table low)
                (byte) 0xa9, (byte) 0x81,            // LDA #$81
                (byte) 0x8d, 0x03, 0x43,             // STA $4303 (table high)
                (byte) 0xa9, 0x00,                   // LDA #$00
                (byte) 0x8d, 0x04, 0x43,             // STA $4304 (table bank)
                (byte) 0xa9, 0x02,                   // LDA #$02
                (byte) 0x8d, 0x10, 0x43,             // STA $4310 (two bytes to one register)
                (byte) 0xa9, 0x22,                   // LDA #$22
                (byte) 0x8d, 0x11, 0x43,             // STA $4311 (CGDATA)
                (byte) 0xa9, 0x10,                   // LDA #$10
                (byte) 0x8d, 0x12, 0x43,             // STA $4312 (table low)
                (byte) 0xa9, (byte) 0x81,            // LDA #$81
                (byte) 0x8d, 0x13, 0x43,             // STA $4313 (table high)
                (byte) 0xa9, 0x00,                   // LDA #$00
                (byte) 0x8d, 0x14, 0x43,             // STA $4314 (table bank)
                (byte) 0xa9, 0x03,                   // LDA #$03
                (byte) 0x8d, 0x0c, 0x42,             // STA $420c (enable channels 0 and 1)
                (byte) 0xa9, 0x0f,                   // LDA #$0f
                (byte) 0x8d, 0x00, 0x21,             // STA $2100 (display on)
                (byte) 0x80, (byte) 0xfe             // BRA *
        };
        System.arraycopy(program, 0, rom, 0, program.length);
        rom[0x0100] = 0x01;
        rom[0x0101] = 0x00;
        rom[0x0102] = 0x00;
        rom[0x0110] = 0x01;
        rom[0x0111] = (byte) 0xe0;
        rom[0x0112] = 0x03;
        rom[0x0113] = 0x00;
        byte[] title = "JAMSNES HDMA PROBE".getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(title, 0, rom, 0x7fc0, title.length);
        rom[0x7fd5] = 0x20;
        rom[0x7fd6] = 0x00;
        rom[0x7fd7] = 0x05;
        rom[0x7fd8] = 0x00;
        rom[0x7ffc] = 0x00;
        rom[0x7ffd] = (byte) 0x80;
        Path path = tempDir.resolve("synthetic-hdma.sfc");
        Files.write(path, rom);
        return path;
    }

    private Path writeJoypadRom() throws IOException {
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
                (byte) 0xa9, (byte) 0x81,            // LDA #$81
                (byte) 0x8d, 0x00, 0x42,             // STA $4200 (NMI and auto-joypad enable)
                (byte) 0x80, (byte) 0xfe             // BRA *
        };
        byte[] nmiHandler = {
                (byte) 0xad, 0x19, 0x42,             // LDA $4219 (JOY1H)
                (byte) 0x10, 0x12,                   // BPL noButton
                (byte) 0xa9, 0x00,                   // LDA #$00
                (byte) 0x8d, 0x21, 0x21,             // STA $2121 (CGADD)
                (byte) 0xa9, (byte) 0xe0,            // LDA #$e0
                (byte) 0x8d, 0x22, 0x21,             // STA $2122 (CGDATA low)
                (byte) 0xa9, 0x03,                   // LDA #$03
                (byte) 0x8d, 0x22, 0x21,             // STA $2122 (CGDATA high)
                (byte) 0xee, 0x00, 0x00,             // INC $0000
                (byte) 0x40                          // noButton: RTI
        };
        System.arraycopy(program, 0, rom, 0, program.length);
        System.arraycopy(nmiHandler, 0, rom, 0x100, nmiHandler.length);
        byte[] title = "JAMSNES JOYPAD PROBE".getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(title, 0, rom, 0x7fc0, title.length);
        rom[0x7fd5] = 0x20;
        rom[0x7fd6] = 0x00;
        rom[0x7fd7] = 0x05;
        rom[0x7fd8] = 0x00;
        rom[0x7ffa] = 0x00;
        rom[0x7ffb] = (byte) 0x81;
        rom[0x7ffc] = 0x00;
        rom[0x7ffd] = (byte) 0x80;
        Path path = tempDir.resolve("synthetic-joypad.sfc");
        Files.write(path, rom);
        return path;
    }

    private Path writeApuHandshakeRom() throws IOException {
        byte[] rom = new byte[0x8000];
        byte[] program = {
                (byte) 0x78,                         // SEI
                (byte) 0xa9, (byte) 0x80,            // LDA #$80
                (byte) 0x8d, 0x00, 0x21,             // STA $2100 (forced blank)
                (byte) 0xad, 0x40, 0x21,             // wait: LDA $2140
                (byte) 0xc9, (byte) 0xaa,            // CMP #$aa
                (byte) 0xd0, (byte) 0xf9,            // BNE wait
                (byte) 0xad, 0x41, 0x21,             // LDA $2141
                (byte) 0xc9, (byte) 0xbb,            // CMP #$bb
                (byte) 0xd0, (byte) 0xf2,            // BNE wait
                (byte) 0xa9, 0x00,                   // LDA #$00
                (byte) 0x8d, 0x21, 0x21,             // STA $2121 (CGADD)
                (byte) 0xa9, (byte) 0xe0,            // LDA #$e0
                (byte) 0x8d, 0x22, 0x21,             // STA $2122 (CGDATA low)
                (byte) 0xa9, 0x03,                   // LDA #$03
                (byte) 0x8d, 0x22, 0x21,             // STA $2122 (CGDATA high)
                (byte) 0xa9, 0x5a,                   // LDA #$5a
                (byte) 0x8d, 0x00, 0x00,             // STA $0000 (handshake marker)
                (byte) 0xa9, 0x0f,                   // LDA #$0f
                (byte) 0x8d, 0x00, 0x21,             // STA $2100 (display on)
                (byte) 0x80, (byte) 0xfe             // BRA *
        };
        System.arraycopy(program, 0, rom, 0, program.length);
        byte[] title = "JAMSNES APU PROBE".getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(title, 0, rom, 0x7fc0, title.length);
        rom[0x7fd5] = 0x20;
        rom[0x7fd6] = 0x00;
        rom[0x7fd7] = 0x05;
        rom[0x7fd8] = 0x00;
        rom[0x7ffc] = 0x00;
        rom[0x7ffd] = (byte) 0x80;
        Path path = tempDir.resolve("synthetic-apu-handshake.sfc");
        Files.write(path, rom);
        return path;
    }

    private Path writeApuUploadRom() throws IOException {
        byte[] rom = new byte[0x8000];
        byte[] program = {
                (byte) 0x78,                         // SEI
                (byte) 0xa9, (byte) 0x80,            // LDA #$80
                (byte) 0x8d, 0x00, 0x21,             // STA $2100 (forced blank)
                (byte) 0xad, 0x40, 0x21,             // waitBoot0: LDA $2140
                (byte) 0xc9, (byte) 0xaa,            // CMP #$aa
                (byte) 0xd0, (byte) 0xf9,            // BNE waitBoot0
                (byte) 0xad, 0x41, 0x21,             // waitBoot1: LDA $2141
                (byte) 0xc9, (byte) 0xbb,            // CMP #$bb
                (byte) 0xd0, (byte) 0xf9,            // BNE waitBoot1
                (byte) 0xa9, 0x00,                   // LDA #$00
                (byte) 0x8d, 0x42, 0x21,             // STA $2142 (destination low)
                (byte) 0xa9, 0x02,                   // LDA #$02
                (byte) 0x8d, 0x43, 0x21,             // STA $2143 (destination high)
                (byte) 0xa9, 0x01,                   // LDA #$01
                (byte) 0x8d, 0x41, 0x21,             // STA $2141 (upload command)
                (byte) 0xa9, (byte) 0xcc,            // LDA #$cc
                (byte) 0x8d, 0x40, 0x21,             // STA $2140 (start upload)
                (byte) 0xad, 0x40, 0x21,             // waitCommand: LDA $2140
                (byte) 0xc9, (byte) 0xcc,            // CMP #$cc
                (byte) 0xd0, (byte) 0xf9,            // BNE waitCommand
                (byte) 0xa9, (byte) 0x8f,            // LDA #$8f (MOV dp,#imm)
                (byte) 0x8d, 0x41, 0x21,             // STA $2141
                (byte) 0xa9, 0x00,                   // LDA #$00
                (byte) 0x8d, 0x40, 0x21,             // STA $2140
                (byte) 0xad, 0x40, 0x21,             // waitByte0: LDA $2140
                (byte) 0xc9, 0x00,                   // CMP #$00
                (byte) 0xd0, (byte) 0xf9,            // BNE waitByte0
                (byte) 0xa9, 0x5a,                   // LDA #$5a
                (byte) 0x8d, 0x41, 0x21,             // STA $2141
                (byte) 0xa9, 0x01,                   // LDA #$01
                (byte) 0x8d, 0x40, 0x21,             // STA $2140
                (byte) 0xad, 0x40, 0x21,             // waitByte1: LDA $2140
                (byte) 0xc9, 0x01,                   // CMP #$01
                (byte) 0xd0, (byte) 0xf9,            // BNE waitByte1
                (byte) 0xa9, (byte) 0xf6,            // LDA #$f6
                (byte) 0x8d, 0x41, 0x21,             // STA $2141
                (byte) 0xa9, 0x02,                   // LDA #$02
                (byte) 0x8d, 0x40, 0x21,             // STA $2140
                (byte) 0xad, 0x40, 0x21,             // waitByte2: LDA $2140
                (byte) 0xc9, 0x02,                   // CMP #$02
                (byte) 0xd0, (byte) 0xf9,            // BNE waitByte2
                (byte) 0xa9, (byte) 0xff,            // LDA #$ff (STOP)
                (byte) 0x8d, 0x41, 0x21,             // STA $2141
                (byte) 0xa9, 0x03,                   // LDA #$03
                (byte) 0x8d, 0x40, 0x21,             // STA $2140
                (byte) 0xad, 0x40, 0x21,             // waitByte3: LDA $2140
                (byte) 0xc9, 0x03,                   // CMP #$03
                (byte) 0xd0, (byte) 0xf9,            // BNE waitByte3
                (byte) 0xa9, 0x00,                   // LDA #$00
                (byte) 0x8d, 0x42, 0x21,             // STA $2142 (entry low)
                (byte) 0xa9, 0x02,                   // LDA #$02
                (byte) 0x8d, 0x43, 0x21,             // STA $2143 (entry high)
                (byte) 0xa9, 0x00,                   // LDA #$00
                (byte) 0x8d, 0x41, 0x21,             // STA $2141 (execute command)
                (byte) 0xa9, 0x05,                   // LDA #$05
                (byte) 0x8d, 0x40, 0x21,             // STA $2140 (finish upload)
                (byte) 0xad, 0x40, 0x21,             // waitExecute: LDA $2140
                (byte) 0xc9, 0x05,                   // CMP #$05
                (byte) 0xd0, (byte) 0xf9,            // BNE waitExecute
                (byte) 0xad, 0x42, 0x21,             // waitSignal: LDA $2142
                (byte) 0xc9, 0x5a,                   // CMP #$5a
                (byte) 0xd0, (byte) 0xf9,            // BNE waitSignal
                (byte) 0xa9, 0x00,                   // LDA #$00
                (byte) 0x8d, 0x21, 0x21,             // STA $2121 (CGADD)
                (byte) 0xa9, 0x1f,                   // LDA #$1f
                (byte) 0x8d, 0x22, 0x21,             // STA $2122 (CGDATA low)
                (byte) 0xa9, 0x00,                   // LDA #$00
                (byte) 0x8d, 0x22, 0x21,             // STA $2122 (CGDATA high)
                (byte) 0xa9, (byte) 0xa5,            // LDA #$a5
                (byte) 0x8d, 0x00, 0x00,             // STA $0000 (upload marker)
                (byte) 0xa9, 0x0f,                   // LDA #$0f
                (byte) 0x8d, 0x00, 0x21,             // STA $2100 (display on)
                (byte) 0x80, (byte) 0xfe             // BRA *
        };
        System.arraycopy(program, 0, rom, 0, program.length);
        byte[] title = "JAMSNES APU UPLOAD".getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(title, 0, rom, 0x7fc0, title.length);
        rom[0x7fd5] = 0x20;
        rom[0x7fd6] = 0x00;
        rom[0x7fd7] = 0x05;
        rom[0x7fd8] = 0x00;
        rom[0x7ffc] = 0x00;
        rom[0x7ffd] = (byte) 0x80;
        Path path = tempDir.resolve("synthetic-apu-upload.sfc");
        Files.write(path, rom);
        return path;
    }
}
