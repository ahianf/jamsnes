package jamsnes.cpu;

import jamsnes.SNES;
import jamsnes.cartridge.MappingMode;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalInstructionTest {
    @Test
    void sepAndRepUpdateStatusFlags() {
        SNES snes = init();
        snes.wram.data()[0] = 0xff;
        snes.cpu.SEP(0);
        assertEquals(0xff, snes.cpu.registers().p.flags());

        snes.cpu.setEmulationMode(false);
        snes.cpu.REP(0);
        assertEquals(0x00, snes.cpu.registers().p.flags());
    }

    @Test
    void repKeepsWidthFlagsInEmulationMode() {
        SNES snes = init();
        snes.cpu.setEmulationMode(true);
        snes.cpu.registers().p.setFlags(0b0100_0101);
        snes.wram.data()[0] = 0b0100_0001;

        snes.cpu.REP(0);

        assertEquals(0b0011_0100, snes.cpu.registers().p.flags());
    }

    @Test
    void flagSetAndClearInstructions() {
        SNES snes = init();
        snes.cpu.registers().p.setFlags(0xff);
        snes.cpu.CLC(0);
        snes.cpu.CLI(0);
        snes.cpu.CLD(0);
        snes.cpu.CLV(0);
        assertFalse(snes.cpu.registers().p.c);
        assertFalse(snes.cpu.registers().p.i);
        assertFalse(snes.cpu.registers().p.d);
        assertFalse(snes.cpu.registers().p.v);

        snes.cpu.SEC(0);
        snes.cpu.SEI(0);
        snes.cpu.SED(0);
        assertTrue(snes.cpu.registers().p.c);
        assertTrue(snes.cpu.registers().p.i);
        assertTrue(snes.cpu.registers().p.d);
    }

    @Test
    void jsrPushesReturnAddressAndJumps() {
        SNES snes = init();
        snes.cpu.registers().setPc(0xabcd);
        snes.cpu.registers().s = 0x0123;

        snes.cpu.JSR(0xabff);

        assertEquals(0xabff, snes.cpu.registers().pc);
        assertEquals(0x0121, snes.cpu.registers().s);
        assertEquals(0xabcc, snes.cpu._pop16());
    }

    @Test
    void jslPushesBankedReturnAddressAndJumps() {
        SNES snes = init();
        snes.cpu.registers().setPbr(0xff);
        snes.cpu.registers().setPc(0xabcd);
        snes.cpu.registers().s = 0x0123;

        snes.cpu.JSL(0xcdabff);

        assertEquals(0xcdabff, snes.cpu.registers().pac);
        assertEquals(0x0120, snes.cpu.registers().s);
        int pushed = snes.cpu._pop16() | (snes.cpu._pop() << 16);
        assertEquals(0xffabcc, pushed);
    }

    @Test
    void pushesAccumulatorAndRegisters() {
        SNES snes = init();
        snes.cpu.registers().s = 0x0010;
        snes.cpu.registers().a = 0xabcd;
        snes.cpu.registers().p.m = false;
        snes.cpu.PHA(0);
        assertEquals(0xabcd, snes.cpu._pop16());

        snes.cpu.registers().s = 0x0010;
        snes.cpu.registers().dbr = 0xff;
        snes.cpu.PHB(0);
        assertEquals(0xff, snes.cpu._pop());

        snes.cpu.registers().s = 0x0010;
        snes.cpu.registers().d = 0xabcd;
        snes.cpu.PHD(0);
        assertEquals(0xabcd, snes.cpu._pop16());
    }

    @Test
    void xceExchangesCarryAndEmulation() {
        SNES snes = init();
        snes.cpu.setEmulationMode(true);
        snes.cpu.registers().p.c = false;
        snes.cpu.registers().x = 0xff11;
        snes.cpu.registers().y = 0xee22;

        snes.cpu.XCE(0);

        assertFalse(snes.cpu.isEmulationMode());
        assertTrue(snes.cpu.registers().p.c);
        assertTrue(snes.cpu.registers().p.m);
        assertTrue(snes.cpu.registers().p.x_b);
        assertEquals(0x11, snes.cpu.registers().x);
        assertEquals(0x22, snes.cpu.registers().y);
    }

    @Test
    void branchesUseSignedOffsets() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x80);
        snes.wram.data()[0] = 0x50;
        snes.cpu.BCC(0);
        assertEquals(0xd0, snes.cpu.registers().pc);

        snes.cpu.registers().setPc(0x80);
        snes.wram.data()[0] = 0xf0;
        snes.cpu.BRA(0);
        assertEquals(0x70, snes.cpu.registers().pc);
    }

    @Test
    void longBranchAndJumps() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x8080);
        snes.wram.data()[0] = 0x00;
        snes.wram.data()[1] = 0xf0;
        snes.cpu.BRL(0);
        assertEquals(0x7080, snes.cpu.registers().pc);

        snes.cpu.JMP(0x1000);
        assertEquals(0x1000, snes.cpu.registers().pc);

        snes.cpu.JML(0x10ab00);
        assertEquals(0x10ab00, snes.cpu.registers().pac);
    }

    @Test
    void pushesEffectiveOperands() {
        SNES snes = init();
        snes.cpu.registers().setPac(0x008005);
        snes.cpu.registers().s = 0x1fff;
        snes.wram.data()[0] = 0xff;
        snes.wram.data()[1] = 0xff;
        snes.cpu.PER(0);
        assertEquals(0x1ffd, snes.cpu.registers().s);
        assertEquals(0x8004, snes.cpu._pop16());

        snes.cpu.registers().s = 0x1fff;
        snes.cpu.PEI(0xffff);
        assertEquals(0xffff, snes.cpu._pop16());

        snes.cpu.registers().s = 0x1fff;
        snes.cpu.PEA(0x1234);
        assertEquals(0x1234, snes.cpu._pop16());
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
