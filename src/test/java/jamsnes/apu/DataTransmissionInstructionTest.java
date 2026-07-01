package jamsnes.apu;

import jamsnes.SNES;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataTransmissionInstructionTest {
    @Test
    void movesBetweenRegisters() {
        SNES snes = init();
        snes.apu.internalRegisters().a = 23;
        snes.apu.internalRegisters().x = 45;

        assertEquals(2, snes.apu.MOVregToReg("x", "a"));
        assertEquals(45, snes.apu.internalRegisters().a);
    }

    @Test
    void movesBetweenMemoryAddresses() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x23;
        snes.apu._internalWrite(0x23, 0x56);
        snes.apu._internalWrite(0x24, 0x33);
        snes.apu._internalWrite(0x56, 99);
        snes.apu._internalWrite(0x33, 66);

        int memFrom = snes.apu._getImmediateData();
        int memTo = snes.apu._getDirectAddr();
        assertEquals(5, snes.apu.MOVmemToMem(memTo, memFrom));
        assertEquals(99, snes.apu._internalRead(0x33));
    }

    @Test
    void movesBetweenRegisterAndMemoryWithOptionalXIncrement() {
        SNES snes = init();
        snes.apu.internalRegisters().x = 0x23;
        snes.apu.internalRegisters().a = 0x44;
        snes.apu._internalWrite(0x23, 0x56);

        assertEquals(4, snes.apu.MOVregToMem("a", snes.apu._getIndexXAddr(), 4, true));
        assertEquals(0x44, snes.apu._internalRead(0x23));
        assertEquals(0x24, snes.apu.internalRegisters().x);

        snes.apu.internalRegisters().x = 0x23;
        snes.apu.internalRegisters().a = 0x44;
        assertEquals(4, snes.apu.MOVmemToReg(snes.apu._getIndexXAddr(), "a", 4, true));
        assertEquals(0x24, snes.apu.internalRegisters().x);
        assertEquals(0x44, snes.apu.internalRegisters().a);
    }

    @Test
    void movesWordsToAndFromYa() {
        SNES snes = init();
        snes.apu._internalWrite(0x42, 0x34);
        snes.apu._internalWrite(0x43, 0x12);

        assertEquals(5, snes.apu.MOVW(0x42, true));
        assertEquals(0x1234, snes.apu.internalRegisters().ya());

        snes.apu.internalRegisters().a = 0x78;
        snes.apu.internalRegisters().y = 0x56;
        assertEquals(5, snes.apu.MOVW(0x50, false));
        assertEquals(0x78, snes.apu._internalRead(0x50));
        assertEquals(0x56, snes.apu._internalRead(0x51));
    }

    private static SNES init() {
        return new SNES(new NoRenderer(0, 0, 0));
    }
}
