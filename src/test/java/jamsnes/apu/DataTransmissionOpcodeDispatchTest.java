package jamsnes.apu;

import jamsnes.SNES;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataTransmissionOpcodeDispatchTest {
    @Test
    void executesImmediateAndRegisterMoveOpcodes() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu.internalRegisters().sp = 0x44;
        snes.apu._internalWrite(0x200, 0x8d);
        snes.apu._internalWrite(0x201, 0x7a);
        snes.apu._internalWrite(0x202, 0x9d);
        snes.apu._internalWrite(0x203, 0xbd);

        assertEquals(2, snes.apu.executeInstruction());
        assertEquals(0x7a, snes.apu.internalRegisters().y);
        assertFalse(snes.apu.internalRegisters().z);
        assertFalse(snes.apu.internalRegisters().n);

        assertEquals(2, snes.apu.executeInstruction());
        assertEquals(0x44, snes.apu.internalRegisters().x);

        snes.apu.internalRegisters().x = 0x33;
        snes.apu.internalRegisters().n = true;
        assertEquals(2, snes.apu.executeInstruction());
        assertEquals(0x33, snes.apu.internalRegisters().sp);
        assertTrue(snes.apu.internalRegisters().n);
    }

    @Test
    void executesImmediateToDirectMemoryMoveOpcode() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu._internalWrite(0x200, 0x8f);
        snes.apu._internalWrite(0x201, 0x55);
        snes.apu._internalWrite(0x202, 0xab);

        assertEquals(5, snes.apu.executeInstruction());

        assertEquals(0xab, snes.apu._internalRead(0x55));
    }

    @Test
    void executesRegisterToMemoryStoreOpcodes() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu.internalRegisters().a = 0x66;
        snes.apu.internalRegisters().x = 0x77;
        snes.apu.internalRegisters().y = 0x88;
        snes.apu._internalWrite(0x200, 0xc4);
        snes.apu._internalWrite(0x201, 0x40);
        snes.apu._internalWrite(0x202, 0xc9);
        snes.apu._internalWrite(0x203, 0x00);
        snes.apu._internalWrite(0x204, 0x03);
        snes.apu._internalWrite(0x205, 0xcb);
        snes.apu._internalWrite(0x206, 0x41);
        snes.apu._internalWrite(0x207, 0xeb);
        snes.apu._internalWrite(0x208, 0x42);
        snes.apu._internalWrite(0x209, 0xec);
        snes.apu._internalWrite(0x20a, 0x10);
        snes.apu._internalWrite(0x20b, 0x03);

        assertEquals(4, snes.apu.executeInstruction());
        assertEquals(0x66, snes.apu._internalRead(0x40));

        assertEquals(5, snes.apu.executeInstruction());
        assertEquals(0x77, snes.apu._internalRead(0x0300));

        assertEquals(4, snes.apu.executeInstruction());
        assertEquals(0x88, snes.apu._internalRead(0x41));

        assertEquals(3, snes.apu.executeInstruction());
        assertEquals(0x88, snes.apu._internalRead(0x42));

        assertEquals(4, snes.apu.executeInstruction());
        assertEquals(0x88, snes.apu._internalRead(0x0310));
    }

    @Test
    void executesIndexedXAutoIncrementMoveOpcodes() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu.internalRegisters().x = 0x30;
        snes.apu.internalRegisters().a = 0x5c;
        snes.apu._internalWrite(0x200, 0xaf);
        snes.apu._internalWrite(0x201, 0xbf);

        assertEquals(4, snes.apu.executeInstruction());
        assertEquals(0x5c, snes.apu._internalRead(0x30));
        assertEquals(0x31, snes.apu.internalRegisters().x);

        snes.apu.internalRegisters().a = 0;
        assertEquals(4, snes.apu.executeInstruction());
        assertEquals(0x31, snes.apu.internalRegisters().a);
        assertEquals(0x32, snes.apu.internalRegisters().x);
    }

    @Test
    void executesWordMoveOpcodes() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu._internalWrite(0x40, 0x34);
        snes.apu._internalWrite(0x41, 0x12);
        snes.apu._internalWrite(0x200, 0xba);
        snes.apu._internalWrite(0x201, 0x40);
        snes.apu._internalWrite(0x202, 0xda);
        snes.apu._internalWrite(0x203, 0x50);

        assertEquals(5, snes.apu.executeInstruction());
        assertEquals(0x1234, snes.apu.internalRegisters().ya());

        snes.apu.internalRegisters().a = 0x78;
        snes.apu.internalRegisters().y = 0x56;
        assertEquals(5, snes.apu.executeInstruction());
        assertEquals(0x78, snes.apu._internalRead(0x50));
        assertEquals(0x56, snes.apu._internalRead(0x51));
    }

    @Test
    void executesLoadLikeCppReadBeforeMoveOpcodes() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x200;
        snes.apu.internalRegisters().x = 0x02;
        snes.apu._internalWrite(0x42, 0x99);
        snes.apu._internalWrite(0x200, 0xe4);
        snes.apu._internalWrite(0x201, 0x42);
        snes.apu._internalWrite(0x202, 0xf4);
        snes.apu._internalWrite(0x203, 0x40);

        assertEquals(3, snes.apu.executeInstruction());
        assertEquals(0x99, snes.apu.internalRegisters().a);

        snes.apu._internalWrite(0x42, 0x55);
        assertEquals(4, snes.apu.executeInstruction());
        assertEquals(0x55, snes.apu.internalRegisters().a);
    }

    private static SNES init() {
        return new SNES(new NoRenderer(0, 0, 0));
    }
}
