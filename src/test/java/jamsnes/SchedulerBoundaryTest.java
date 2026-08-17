package jamsnes;

import jamsnes.audio.RecordingAudioSink;
import jamsnes.ppu.PPU;
import jamsnes.video.RecordingVideoSink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the ordering of CPU bus accesses against scheduler boundaries. The PPU
 * counter domain advances at each timed CPU bus access (parking exactly on
 * pending scanline-event dots), so hardware-visible latches observe
 * access-time positions rather than the position at the start of a CPU slice.
 */
class SchedulerBoundaryTest {
    @TempDir
    Path tempDir;

    @Test
    void counterLatchDuringInstructionSamplesAccessTimePosition() throws IOException {
        SNES snes = new SNES(writeLatchRom().toString(),
                new RecordingVideoSink(), new RecordingAudioSink());

        int startHCounter = -1;
        int startVCounter = -1;
        for (int updates = 0; updates < 1_000; updates++) {
            startHCounter = snes.ppu.hCounter();
            startVCounter = snes.ppu.vCounter();
            snes.update();
            if (snes.cpu.registers().pc >= 0x8004) {
                break;
            }
        }

        assertEquals(0x8004, snes.cpu.registers().pc, "LDA $2137 should have executed");
        int latchedHCounter = snes.bus.read(0x213c);
        latchedHCounter |= (snes.bus.read(0x213c) & 1) << 8;
        int latchedVCounter = snes.bus.read(0x213d);
        latchedVCounter |= (snes.bus.read(0x213d) & 1) << 8;

        assertEquals(startVCounter, latchedVCounter,
                "latch happened on the scanline where the read executed");
        assertTrue(latchedHCounter > startHCounter,
                "the $2137 read must observe dots elapsed by the accesses preceding it, "
                        + "not the slice-start position (start " + startHCounter
                        + ", latched " + latchedHCounter + ")");
        boolean sliceEndedLater = snes.ppu.vCounter() != startVCounter
                || latchedHCounter <= snes.ppu.hCounter();
        assertTrue(sliceEndedLater, "the latch cannot be ahead of the position after the slice");
    }

    @Test
    void timestampedAccessesConserveTotalSliceAdvancement() throws IOException {
        SNES snes = new SNES(writeLatchRom().toString(),
                new RecordingVideoSink(), new RecordingAudioSink());

        long framesBefore = snes.ppu.frameCounter();
        int updatesPerFrameEstimate = PPU.H_COUNTER_DOTS * PPU.V_COUNTER_SCANLINES / 2;
        for (int updates = 0; updates < 3 * updatesPerFrameEstimate; updates++) {
            snes.update();
        }

        assertTrue(snes.ppu.frameCounter() - framesBefore >= 2,
                "eager per-access advancement must not lose or double-count dots across frames");
    }

    private Path writeLatchRom() throws IOException {
        byte[] rom = new byte[0x8000];
        byte[] program = {
                (byte) 0x78,                         // SEI
                (byte) 0xad, 0x37, 0x21,             // LDA $2137 (SLHV counter latch)
                (byte) 0x80, (byte) 0xfe             // BRA *
        };
        System.arraycopy(program, 0, rom, 0, program.length);
        byte[] title = "JAMSNES LATCH PROBE".getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(title, 0, rom, 0x7fc0, title.length);
        rom[0x7fd5] = 0x20;
        rom[0x7fd6] = 0x00;
        rom[0x7fd7] = 0x05;
        rom[0x7fd8] = 0x00;
        rom[0x7ffc] = 0x00;
        rom[0x7ffd] = (byte) 0x80;
        Path path = tempDir.resolve("latch-probe.sfc");
        Files.write(path, rom);
        return path;
    }
}
