package jamsnes.apu;

import jamsnes.SNES;
import jamsnes.audio.RecordingAudioSink;
import jamsnes.video.RecordingVideoSink;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WordArithmeticInstructionTest {
    @Test
    void incrementsAndDecrementsDirectWords() {
        SNES snes = init();
        snes.apu._internalWrite(0x55, 0xff);
        snes.apu._internalWrite(0x56, 0x22);

        assertEquals(6, snes.apu.INCW(0x55));
        assertEquals(0x00, snes.apu._internalRead(0x55));
        assertEquals(0x23, snes.apu._internalRead(0x56));

        snes.apu._internalWrite(0x55, 0x00);
        snes.apu._internalWrite(0x56, 0x23);

        assertEquals(6, snes.apu.DECW(0x55));
        assertEquals(0xff, snes.apu._internalRead(0x55));
        assertEquals(0x22, snes.apu._internalRead(0x56));
    }

    @Test
    void incrementsDirectWordsWithWrappedHighByte() {
        SNES snes = init();
        snes.apu.internalRegisters().p = true;
        snes.apu._internalWrite(0x1ff, 0x00);
        snes.apu._internalWrite(0x100, 0x01);

        assertEquals(6, snes.apu.DECW(0x1ff));
        assertEquals(0xff, snes.apu._internalRead(0x1ff));
        assertEquals(0x00, snes.apu._internalRead(0x100));
    }

    @Test
    void addsAndSubtractsDirectWordsToYa() {
        SNES snes = init();
        snes.apu.internalRegisters().setYa(0x4321);
        snes.apu._internalWrite(0x55, 0x11);
        snes.apu._internalWrite(0x56, 0x22);

        assertEquals(5, snes.apu.ADDW(0x55));
        assertEquals(0x6532, snes.apu.internalRegisters().ya());
        assertFalse(snes.apu.internalRegisters().v);
        assertFalse(snes.apu.internalRegisters().h);
        assertFalse(snes.apu.internalRegisters().c);

        snes.apu.internalRegisters().setYa(0x4321);
        assertEquals(5, snes.apu.SUBW(0x55));
        assertEquals(0x2110, snes.apu.internalRegisters().ya());
        assertFalse(snes.apu.internalRegisters().v);
        assertTrue(snes.apu.internalRegisters().h);
        assertTrue(snes.apu.internalRegisters().c);
    }

    @Test
    void subtractWordHalfCarryTracksTwelveBitBorrow() {
        SNES snes = init();
        snes.apu._internalWrite(0x55, 0x01);
        snes.apu._internalWrite(0x56, 0x00);

        snes.apu.internalRegisters().setYa(0x1000);
        assertEquals(5, snes.apu.SUBW(0x55));
        assertEquals(0x0fff, snes.apu.internalRegisters().ya());
        assertFalse(snes.apu.internalRegisters().h);

        snes.apu.internalRegisters().setYa(0x1001);
        assertEquals(5, snes.apu.SUBW(0x55));
        assertEquals(0x1000, snes.apu.internalRegisters().ya());
        assertTrue(snes.apu.internalRegisters().h);
    }

    @Test
    void addsAndComparesDirectWordsWithWrappedHighByte() {
        SNES snes = init();
        snes.apu.internalRegisters().p = true;
        snes.apu.internalRegisters().setYa(0x1201);
        snes.apu._internalWrite(0x1ff, 0x01);
        snes.apu._internalWrite(0x100, 0x12);

        assertEquals(4, snes.apu.CMPW(0x1ff));
        assertTrue(snes.apu.internalRegisters().c);
        assertTrue(snes.apu.internalRegisters().z);
    }

    @Test
    void comparesDirectWordWithYa() {
        SNES snes = init();
        snes.apu.internalRegisters().setYa(0x2211);
        snes.apu._internalWrite(0x55, 0x11);
        snes.apu._internalWrite(0x56, 0x22);

        assertEquals(4, snes.apu.CMPW(0x55));
        assertTrue(snes.apu.internalRegisters().c);
        assertTrue(snes.apu.internalRegisters().z);
    }

    private static SNES init() {
        return new SNES(new RecordingVideoSink(), new RecordingAudioSink());
    }
}
