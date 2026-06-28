package jamsnes.apu;

import jamsnes.SNES;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StackInstructionTest {
    @Test
    void resetInitializesStackPointerAndProgramCounterLikeCpp() {
        SNES snes = init();

        assertEquals(0xef, snes.apu.internalRegisters().sp);
        assertEquals(0xffc0, snes.apu.internalRegisters().pc);
    }

    @Test
    void pushWritesToStackPageThenDecrementsStackPointer() {
        SNES snes = init();
        snes.apu.internalRegisters().a = 56;

        assertEquals(4, snes.apu.PUSH(snes.apu.internalRegisters().a));

        assertEquals(0xee, snes.apu.internalRegisters().sp);
        assertEquals(56, snes.apu._internalRead(0x01ef));
    }

    @Test
    void popIncrementsStackPointerThenReadsIntoRegister() {
        SNES snes = init();
        snes.apu._internalWrite(0x01f0, 82);

        assertEquals(4, snes.apu.POP("y"));

        assertEquals(0xf0, snes.apu.internalRegisters().sp);
        assertEquals(82, snes.apu.internalRegisters().y);
    }

    @Test
    void stackPointerWrapsAsUnsignedByte() {
        SNES snes = init();
        snes.apu.internalRegisters().sp = 0x00;

        assertEquals(4, snes.apu.PUSH(0xab));
        assertEquals(0xff, snes.apu.internalRegisters().sp);
        assertEquals(0xab, snes.apu._internalRead(0x0100));

        assertEquals(4, snes.apu.POP("x"));
        assertEquals(0x00, snes.apu.internalRegisters().sp);
        assertEquals(0xab, snes.apu.internalRegisters().x);
    }

    private static SNES init() {
        return new SNES(new NoRenderer(0, 0, 0));
    }
}
