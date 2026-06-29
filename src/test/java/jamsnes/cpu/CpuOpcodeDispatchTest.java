package jamsnes.cpu;

import jamsnes.SNES;
import jamsnes.cartridge.MappingMode;
import jamsnes.exceptions.InvalidOpcode;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CpuOpcodeDispatchTest {
    @Test
    void executesNopAndRejectsUnsupportedOpcode() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.wram.data()[0x0200] = 0xea;
        snes.wram.data()[0x0201] = 0x01;

        assertEquals(2, snes.cpu.executeInstruction());
        assertEquals(0x0201, snes.cpu.registers().pc);
        assertThrows(InvalidOpcode.class, () -> snes.cpu.executeInstruction());
    }

    @Test
    void executesRelativeBranchesWithFetchedSignedOffset() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.wram.data()[0x0200] = 0xd0;
        snes.wram.data()[0x0201] = 0xfe;

        assertEquals(4, snes.cpu.executeInstruction());
        assertEquals(0x0200, snes.cpu.registers().pc);

        snes.cpu.registers().p.z = true;
        assertEquals(3, snes.cpu.executeInstruction());
        assertEquals(0x0202, snes.cpu.registers().pc);
    }

    @Test
    void executesAbsoluteJumpAndSubroutineOpcodes() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().s = 0x01ff;
        snes.wram.data()[0x0200] = 0x20;
        snes.wram.data()[0x0201] = 0x34;
        snes.wram.data()[0x0202] = 0x12;

        assertEquals(6, snes.cpu.executeInstruction());

        assertEquals(0x1234, snes.cpu.registers().pc);
        assertEquals(0x01fd, snes.cpu.registers().s);
        assertEquals(0x0202, snes.cpu._pop16());

        snes.cpu.registers().setPc(0x0210);
        snes.wram.data()[0x0210] = 0x4c;
        snes.wram.data()[0x0211] = 0x78;
        snes.wram.data()[0x0212] = 0x56;

        assertEquals(3, snes.cpu.executeInstruction());
        assertEquals(0x5678, snes.cpu.registers().pc);
    }

    @Test
    void executesStatusImmediateOpcodes() {
        SNES snes = init();
        snes.cpu.setEmulationMode(false);
        snes.cpu.registers().setPc(0x0200);
        snes.wram.data()[0x0200] = 0xe2;
        snes.wram.data()[0x0201] = 0x81;
        snes.wram.data()[0x0202] = 0xc2;
        snes.wram.data()[0x0203] = 0x01;

        assertEquals(3, snes.cpu.executeInstruction());
        assertTrue(snes.cpu.registers().p.c);
        assertTrue(snes.cpu.registers().p.n);

        assertEquals(3, snes.cpu.executeInstruction());
        assertEquals(0xb4, snes.cpu.registers().p.flags());
    }

    @Test
    void executesStopAndWaitOpcodes() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.wram.data()[0x0200] = 0xcb;
        snes.wram.data()[0x0201] = 0xdb;

        assertEquals(3, snes.cpu.executeInstruction());
        assertTrue(snes.cpu.isWaitingForInterrupt());

        assertEquals(3, snes.cpu.executeInstruction());
        assertTrue(snes.cpu.isStopped());
    }

    @Test
    void executesPushOpcodes() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().s = 0x01ff;
        snes.cpu.registers().a = 0x44;
        snes.wram.data()[0x0200] = 0x48;
        snes.wram.data()[0x0201] = 0x08;

        assertEquals(3, snes.cpu.executeInstruction());
        assertEquals(0x44, snes.cpu._pop());

        snes.cpu.registers().p.c = true;
        assertEquals(3, snes.cpu.executeInstruction());
        assertEquals(snes.cpu.registers().p.flags(), snes.cpu._pop());
    }

    private static SNES init() {
        SNES snes = new SNES(new NoRenderer(0, 0, 0));
        snes.cartridge.setSize(0x10000);
        snes.cartridge.header.addMappingMode(MappingMode.LOROM);
        snes.sram.setSize(0x10000);
        snes.bus.mapComponents(snes);
        return snes;
    }
}
