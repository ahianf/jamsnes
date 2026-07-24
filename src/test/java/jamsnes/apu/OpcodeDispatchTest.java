package jamsnes.apu;

import jamsnes.SNES;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpcodeDispatchTest {
    @Test
    void executesNopAndAdvancesProgramCounter() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu._internalWrite(0x200, 0x00);

        assertEquals(2, snes.apu.executeInstruction());
        assertEquals(0x201, snes.apu.internalRegisters().pc);
    }

    @Test
    void executesStackPushAndPopOpcodes() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu.internalRegisters().a = 0x5a;
        snes.apu._internalWrite(0x200, 0x2d);
        snes.apu._internalWrite(0x201, 0xae);
        snes.apu._internalWrite(0x202, 0x0d);
        snes.apu._internalWrite(0x203, 0x8e);

        assertEquals(4, snes.apu.executeInstruction());
        assertEquals(0xee, snes.apu.internalRegisters().sp);
        assertEquals(0x5a, snes.apu._internalRead(0x01ef));

        snes.apu.internalRegisters().a = 0;
        assertEquals(4, snes.apu.executeInstruction());
        assertEquals(0xef, snes.apu.internalRegisters().sp);
        assertEquals(0x5a, snes.apu.internalRegisters().a);

        snes.apu.internalRegisters().setPsw(0xa9);
        assertEquals(4, snes.apu.executeInstruction());
        assertEquals(0xee, snes.apu.internalRegisters().sp);
        assertEquals(0xa9, snes.apu._internalRead(0x01ef));

        snes.apu.internalRegisters().setPsw(0x00);
        assertEquals(4, snes.apu.executeInstruction());
        assertEquals(0xef, snes.apu.internalRegisters().sp);
        assertEquals(0xa9, snes.apu.internalRegisters().psw());
    }

    @Test
    void executesCallOpcodeWithFetchedAbsoluteAddress() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu._internalWrite(0x200, 0x3f);
        snes.apu._internalWrite(0x201, 0x34);
        snes.apu._internalWrite(0x202, 0x12);

        assertEquals(8, snes.apu.executeInstruction());

        assertEquals(0x1234, snes.apu.internalRegisters().pc);
        assertEquals(0xed, snes.apu.internalRegisters().sp);
        assertEquals(0x02, snes.apu._internalRead(0x01ef));
        assertEquals(0x03, snes.apu._internalRead(0x01ee));
    }

    @Test
    void executesRelativeBranchOpcodesFromFetchedOffset() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu._internalWrite(0x200, 0xd0);
        snes.apu._internalWrite(0x201, 0xfe);

        assertEquals(4, snes.apu.executeInstruction());
        assertEquals(0x200, snes.apu.internalRegisters().pc);

        snes.apu.internalRegisters().z = true;
        assertEquals(2, snes.apu.executeInstruction());
        assertEquals(0x202, snes.apu.internalRegisters().pc);
    }

    @Test
    void executesBitBranchOpcodeWithFetchedDirectAddressAndOffset() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu._internalWrite(0x200, 0x43);
        snes.apu._internalWrite(0x201, 0x44);
        snes.apu._internalWrite(0x202, 0x05);
        snes.apu._internalWrite(0x44, 0x04);

        assertEquals(7, snes.apu.executeInstruction());
        assertEquals(0x208, snes.apu.internalRegisters().pc);
    }

    @Test
    void executesDirectDbnzOpcodeUsingDirectThenOffsetOperandOrder() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu._internalWrite(0x200, 0x6e);
        snes.apu._internalWrite(0x201, 0x42);
        snes.apu._internalWrite(0x202, 0x03);
        snes.apu._internalWrite(0x42, 2);

        assertEquals(7, snes.apu.executeInstruction());

        assertEquals(1, snes.apu._internalRead(0x42));
        assertEquals(0x206, snes.apu.internalRegisters().pc);
    }

    @Test
    void executesStatusAndStandbyOpcodes() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu._internalWrite(0x200, 0x80);
        snes.apu._internalWrite(0x201, 0xed);
        snes.apu._internalWrite(0x202, 0xef);

        assertEquals(2, snes.apu.executeInstruction());
        assertTrue(snes.apu.internalRegisters().c);

        assertEquals(3, snes.apu.executeInstruction());
        assertFalse(snes.apu.internalRegisters().c);

        assertEquals(3, snes.apu.executeInstruction());
        assertEquals(APU.StateMode.SLEEPING, snes.apu.getState());
    }

    private static SNES init() {
        return new SNES(new NoRenderer(0, 0, 0));
    }
}
