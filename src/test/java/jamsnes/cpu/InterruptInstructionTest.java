package jamsnes.cpu;

import jamsnes.SNES;
import jamsnes.cartridge.MappingMode;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterruptInstructionTest {
    @Test
    void brkInEmulationPushesStatusAndPc() {
        SNES snes = init();
        snes.cpu.setEmulationMode(true);
        snes.cartridge.header.emulationInterrupts.brk = 0x123;
        snes.cpu.registers().p.setFlags(0xf1);
        snes.cpu.registers().setPc(0x156);
        snes.cpu.registers().setPbr(0x15);

        snes.cpu.BRK(0);

        assertEquals(0x123, snes.cpu.registers().pc);
        assertEquals(0, snes.cpu.registers().pbr);
        assertFalse(snes.cpu.registers().p.d);
        assertTrue(snes.cpu.registers().p.i);
        assertTrue(snes.cpu.registers().p.x_b);
        assertEquals(0xf1, snes.cpu._pop());
        assertEquals(0x156, snes.cpu._pop16());
    }

    @Test
    void brkInNativeModePushesProgramBank() {
        SNES snes = init();
        snes.cpu.setEmulationMode(false);
        snes.cartridge.header.nativeInterrupts.brk = 0x123;
        snes.cpu.registers().p.setFlags(0xf1);
        snes.cpu.registers().setPc(0x156);
        snes.cpu.registers().setPbr(0x15);

        snes.cpu.BRK(0);

        assertEquals(0x123, snes.cpu.registers().pc);
        assertEquals(0, snes.cpu.registers().pbr);
        assertEquals(0xf1, snes.cpu._pop());
        assertEquals(0x156, snes.cpu._pop16());
        assertEquals(0x15, snes.cpu._pop());
    }

    @Test
    void copUsesCopVectors() {
        SNES snes = init();
        snes.cpu.setEmulationMode(true);
        snes.cartridge.header.emulationInterrupts.cop = 0x123;
        snes.cpu.registers().p.setFlags(0x0f);
        snes.cpu.registers().setPc(0x156);
        snes.cpu.registers().setPbr(0x15);

        snes.cpu.COP(0);

        assertEquals(0x123, snes.cpu.registers().pc);
        assertEquals(0, snes.cpu.registers().pbr);
        assertFalse(snes.cpu.registers().p.d);
        assertTrue(snes.cpu.registers().p.i);
        assertFalse(snes.cpu.registers().p.x_b);
        assertEquals(0x0f, snes.cpu._pop());
        assertEquals(0x156, snes.cpu._pop16());
    }

    @Test
    void resetLoadsEmulationResetVector() {
        SNES snes = init();
        snes.cpu.setEmulationMode(false);
        snes.cpu.registers().p.d = true;
        snes.cpu.registers().p.m = false;
        snes.cpu.registers().p.x_b = false;
        snes.cartridge.header.emulationInterrupts.reset = 0x8123;

        snes.cpu.RESB();

        assertTrue(snes.cpu.isEmulationMode());
        assertTrue(snes.cpu.registers().p.i);
        assertFalse(snes.cpu.registers().p.d);
        assertTrue(snes.cpu.registers().p.m);
        assertTrue(snes.cpu.registers().p.x_b);
        assertEquals(0x8123, snes.cpu.registers().pc);
        assertFalse(snes.cpu.isStopped());
    }

    @Test
    void waiSetsWaitingFlag() {
        SNES snes = init();

        snes.cpu.WAI(0);

        assertTrue(snes.cpu.isWaitingForInterrupt());
    }

    private static SNES init() {
        SNES snes = new SNES(new NoRenderer(0, 0, 0));
        snes.cartridge.setSize(100);
        snes.cartridge.header.addMappingMode(MappingMode.LOROM);
        snes.sram.setSize(100);
        snes.bus.mapComponents(snes);
        return snes;
    }
}
