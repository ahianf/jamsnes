package jamsnes.apu;

import jamsnes.SNES;
import jamsnes.audio.RecordingAudioSink;
import jamsnes.video.RecordingVideoSink;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StackOpcodeDispatchTest {
    @Test
    void executesXAndYPushPopOpcodes() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu.internalRegisters().x = 0x12;
        snes.apu.internalRegisters().y = 0x34;
        snes.apu._internalWrite(0x200, 0x4d);
        snes.apu._internalWrite(0x201, 0x6d);
        snes.apu._internalWrite(0x202, 0xce);
        snes.apu._internalWrite(0x203, 0xee);

        assertEquals(4, snes.apu.executeInstruction());
        assertEquals(0xee, snes.apu.internalRegisters().sp);
        assertEquals(0x12, snes.apu._internalRead(0x01ef));

        assertEquals(4, snes.apu.executeInstruction());
        assertEquals(0xed, snes.apu.internalRegisters().sp);
        assertEquals(0x34, snes.apu._internalRead(0x01ee));

        snes.apu.internalRegisters().x = 0x00;
        snes.apu.internalRegisters().y = 0x00;
        assertEquals(4, snes.apu.executeInstruction());
        assertEquals(0xee, snes.apu.internalRegisters().sp);
        assertEquals(0x34, snes.apu.internalRegisters().x);

        assertEquals(4, snes.apu.executeInstruction());
        assertEquals(0xef, snes.apu.internalRegisters().sp);
        assertEquals(0x12, snes.apu.internalRegisters().y);
    }

    private static SNES init() {
        return new SNES(new RecordingVideoSink(), new RecordingAudioSink());
    }
}
