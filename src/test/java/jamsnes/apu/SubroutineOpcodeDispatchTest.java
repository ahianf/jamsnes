package jamsnes.apu;

import jamsnes.SNES;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubroutineOpcodeDispatchTest {
    @Test
    void executesPcallOpcodeWithFetchedImmediateTarget() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x0200;
        snes.apu._internalWrite(0x0200, 0x4f);
        snes.apu._internalWrite(0x0201, 0x7b);

        assertEquals(6, snes.apu.executeInstruction());

        assertEquals(0xff7b, snes.apu.internalRegisters().pc);
        assertEquals(0xed, snes.apu.internalRegisters().sp);
        assertEquals(0x02, snes.apu._internalRead(0x01ef));
        assertEquals(0x02, snes.apu._internalRead(0x01ee));
    }

    @Test
    void executesTcallOpcodeFromVectorTable() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x0200;
        snes.apu._internalWrite(0x00f1, 0x00);
        snes.apu._internalWrite(0xffd0, 0x34);
        snes.apu._internalWrite(0xffd1, 0x12);
        snes.apu._internalWrite(0x0200, 0x71);

        assertEquals(8, snes.apu.executeInstruction());

        assertEquals(0x1234, snes.apu.internalRegisters().pc);
        assertEquals(0xed, snes.apu.internalRegisters().sp);
        assertEquals(0x02, snes.apu._internalRead(0x01ef));
        assertEquals(0x01, snes.apu._internalRead(0x01ee));
    }

    @Test
    void executesBrkOpcodeAndLoadsVector() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x0200;
        snes.apu.internalRegisters().setPsw(0xc5);
        snes.apu._internalWrite(0x00f1, 0x00);
        snes.apu._internalWrite(0xffdf, 0xaa);
        snes.apu._internalWrite(0xffde, 0xbb);
        snes.apu._internalWrite(0x0200, 0x0f);

        assertEquals(8, snes.apu.executeInstruction());

        assertEquals(0xaabb, snes.apu.internalRegisters().pc);
        assertFalse(snes.apu.internalRegisters().i);
        assertTrue(snes.apu.internalRegisters().b);
        assertEquals(0xec, snes.apu.internalRegisters().sp);
        assertEquals(0x02, snes.apu._internalRead(0x01ef));
        assertEquals(0x01, snes.apu._internalRead(0x01ee));
        assertEquals(0xd5, snes.apu._internalRead(0x01ed));
    }

    @Test
    void executesRetAndRetiOpcodesFromStack() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x0200;
        snes.apu._internalWrite(0x0200, 0x6f);
        snes.apu._internalWrite(0x01f0, 0x12);
        snes.apu._internalWrite(0x01f1, 0x34);

        assertEquals(5, snes.apu.executeInstruction());
        assertEquals(0x1234, snes.apu.internalRegisters().pc);
        assertEquals(0xf1, snes.apu.internalRegisters().sp);

        snes.apu.internalRegisters().pc = 0x0300;
        snes.apu.internalRegisters().sp = 0xef;
        snes.apu._internalWrite(0x0300, 0x7f);
        snes.apu._internalWrite(0x01f0, 0xdd);
        snes.apu._internalWrite(0x01f1, 0x56);
        snes.apu._internalWrite(0x01f2, 0x78);

        assertEquals(6, snes.apu.executeInstruction());
        assertEquals(0xdd, snes.apu.internalRegisters().psw());
        assertEquals(0x5678, snes.apu.internalRegisters().pc);
        assertEquals(0xf2, snes.apu.internalRegisters().sp);
    }

    private static SNES init() {
        return new SNES(new NoRenderer(0, 0, 0));
    }
}
