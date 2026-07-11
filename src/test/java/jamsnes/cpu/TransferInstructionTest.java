package jamsnes.cpu;

import jamsnes.SNES;
import jamsnes.cartridge.MappingMode;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferInstructionTest {
    @Test
    void taxHandlesIndexWidth() {
        SNES snes = init();
        snes.cpu.registers().p.x_b = false;
        snes.cpu.registers().a = 0xfedc;
        snes.cpu.TAX(0);
        assertEquals(0xfedc, snes.cpu.registers().x);
        assertTrue(snes.cpu.registers().p.n);

        snes.cpu.registers().p.x_b = true;
        snes.cpu.registers().x = 0xfe12;
        snes.cpu.registers().a = 0xab00;
        snes.cpu.TAX(0);
        assertEquals(0xfe00, snes.cpu.registers().x);
        assertTrue(snes.cpu.registers().p.z);
        assertFalse(snes.cpu.registers().p.n);
    }

    @Test
    void tayHandlesEightBitTransfers() {
        SNES snes = init();
        snes.cpu.registers().p.x_b = true;
        snes.cpu.registers().y = 0xfe12;
        snes.cpu.registers().a = 0x00ab;
        snes.cpu.TAY(0);
        assertEquals(0xfeab, snes.cpu.registers().y);
        assertTrue(snes.cpu.registers().p.n);
    }

    @Test
    void txsDoesNotAffectStatusFlags() {
        SNES snes = init();
        snes.cpu.registers().p.x_b = true;
        snes.cpu.registers().p.n = false;
        snes.cpu.registers().p.z = true;

        snes.cpu.registers().x = 0xabcd;
        snes.cpu.TXS(0);
        assertEquals(0x00cd, snes.cpu.registers().s);
        assertFalse(snes.cpu.registers().p.n);
        assertTrue(snes.cpu.registers().p.z);

        snes.cpu.registers().p.x_b = false;
        snes.cpu.registers().p.n = true;
        snes.cpu.registers().p.z = false;
        snes.cpu.registers().x = 0x0000;
        snes.cpu.TXS(0);
        assertEquals(0x0000, snes.cpu.registers().s);
        assertTrue(snes.cpu.registers().p.n);
        assertFalse(snes.cpu.registers().p.z);
    }

    @Test
    void txsOpcodePreservesStatusFlags() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().p.x_b = true;
        snes.cpu.registers().p.n = false;
        snes.cpu.registers().p.z = true;
        snes.cpu.registers().x = 0x0080;
        snes.wram.data()[0x0200] = 0x9a;

        assertEquals(2, snes.cpu.executeInstruction());
        assertEquals(0x0080, snes.cpu.registers().s);
        assertFalse(snes.cpu.registers().p.n);
        assertTrue(snes.cpu.registers().p.z);
    }

    @Test
    void transfersBetween16BitRegistersSetFlags() {
        SNES snes = init();
        snes.cpu.registers().a = 0xabcd;
        snes.cpu.TCD(0);
        assertEquals(0xabcd, snes.cpu.registers().d);
        assertTrue(snes.cpu.registers().p.n);

        snes.cpu.registers().d = 0;
        snes.cpu.TDC(0);
        assertEquals(0, snes.cpu.registers().a);
        assertTrue(snes.cpu.registers().p.z);

        snes.cpu.registers().s = 0x8001;
        snes.cpu.TSC(0);
        assertEquals(0x8001, snes.cpu.registers().a);
        assertTrue(snes.cpu.registers().p.n);
    }

    @Test
    void tcsForcesStackPageInEmulationMode() {
        SNES snes = init();
        snes.cpu.setEmulationMode(true);
        snes.cpu.registers().a = 0xabcd;

        snes.cpu.TCS(0);

        assertEquals(0x01cd, snes.cpu.registers().s);
    }

    @Test
    void txaAndTyaRespectAccumulatorWidth() {
        SNES snes = init();
        snes.cpu.registers().p.m = true;
        snes.cpu.registers().a = 0x1200;
        snes.cpu.registers().x = 0x00cd;
        snes.cpu.TXA(0);
        assertEquals(0x12cd, snes.cpu.registers().a);

        snes.cpu.registers().p.m = false;
        snes.cpu.registers().p.x_b = true;
        snes.cpu.registers().y = 0xabef;
        snes.cpu.TYA(0);
        assertEquals(0x00ef, snes.cpu.registers().a);
    }

    @Test
    void txaAndTyaSetFlagsFromAccumulatorWidth() {
        SNES snes = init();
        snes.cpu.registers().p.m = true;
        snes.cpu.registers().a = 0x1201;
        snes.cpu.registers().x = 0x0000;

        snes.cpu.TXA(0);

        assertEquals(0x1200, snes.cpu.registers().a);
        assertTrue(snes.cpu.registers().p.z);
        assertFalse(snes.cpu.registers().p.n);

        snes.cpu.registers().a = 0x0000;
        snes.cpu.registers().y = 0x0080;

        snes.cpu.TYA(0);

        assertEquals(0x0080, snes.cpu.registers().a);
        assertFalse(snes.cpu.registers().p.z);
        assertTrue(snes.cpu.registers().p.n);
    }

    @Test
    void txaOpcodeSetsZeroFromEightBitAccumulatorResult() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().p.m = true;
        snes.cpu.registers().a = 0x3401;
        snes.cpu.registers().x = 0x0000;
        snes.wram.data()[0x0200] = 0x8a;

        assertEquals(2, snes.cpu.executeInstruction());
        assertEquals(0x3400, snes.cpu.registers().a);
        assertTrue(snes.cpu.registers().p.z);
        assertFalse(snes.cpu.registers().p.n);
    }

    @Test
    void indexTransfersPreserveHighByteInEightBitMode() {
        SNES snes = init();
        snes.cpu.registers().p.x_b = true;
        snes.cpu.registers().x = 0x1200;
        snes.cpu.registers().y = 0x3400;

        snes.cpu.TXY(0);

        assertEquals(0x3400, snes.cpu.registers().y);
        assertTrue(snes.cpu.registers().p.z);
        assertFalse(snes.cpu.registers().p.n);

        snes.cpu.registers().y = 0x3480;
        snes.cpu.TYX(0);

        assertEquals(0x1280, snes.cpu.registers().x);
        assertFalse(snes.cpu.registers().p.z);
        assertTrue(snes.cpu.registers().p.n);
    }

    @Test
    void incrementAndDecrementIndexUseIndexWidth() {
        SNES snes = init();
        snes.cpu.registers().p.x_b = true;
        snes.cpu.registers().x = 0xff;
        snes.cpu.INX(0);
        assertEquals(0, snes.cpu.registers().x);
        assertTrue(snes.cpu.registers().p.z);

        snes.cpu.registers().p.x_b = false;
        snes.cpu.registers().y = 0x0100;
        snes.cpu.DEY(0);
        assertEquals(0x00ff, snes.cpu.registers().y);
        assertFalse(snes.cpu.registers().p.z);
    }

    @Test
    void cpxAndCpySetComparisonFlags() {
        SNES snes = init();
        snes.cpu.registers().p.x_b = true;
        snes.cpu.registers().x = 0xff;
        snes.wram.data()[0] = 0xff;
        snes.cpu.CPX(0);
        assertTrue(snes.cpu.registers().p.z);
        assertTrue(snes.cpu.registers().p.c);

        snes.cpu.registers().p.x_b = false;
        snes.cpu.registers().y = 0x8888;
        snes.wram.data()[0] = 0x88;
        snes.wram.data()[1] = 0x98;
        snes.cpu.CPY(0);
        assertFalse(snes.cpu.registers().p.z);
        assertTrue(snes.cpu.registers().p.n);
        assertFalse(snes.cpu.registers().p.c);
    }

    @Test
    void mvnCopiesForwardAndUpdatesRegisters() {
        SNES snes = init();
        snes.cpu.registers().a = 0x10;
        snes.cpu.registers().x = 0x0000;
        snes.cpu.registers().y = 0x1000;
        snes.wram.data()[0x1ff0] = 0x00;
        snes.wram.data()[0x1ff1] = 0x00;
        for (int i = 0; i <= snes.cpu.registers().a; i++) {
            snes.wram.data()[i] = i;
        }

        int cycles = snes.cpu.MVN(0x1ff0);

        assertEquals(0x77, cycles);
        assertEquals(0x00, snes.cpu.registers().dbr);
        assertEquals(0xffff, snes.cpu.registers().a);
        assertEquals(0x0011, snes.cpu.registers().x);
        assertEquals(0x1011, snes.cpu.registers().y);
        for (int i = 0; i < 0x11; i++) {
            assertEquals(i, snes.wram.data()[0x1000 + i]);
        }
    }

    @Test
    void mvpCopiesBackwardAndUpdatesRegisters() {
        SNES snes = init();
        snes.cpu.registers().a = 0x10;
        snes.cpu.registers().x = 0x0010;
        snes.cpu.registers().y = 0x1010;
        snes.wram.data()[0x1ff0] = 0x00;
        snes.wram.data()[0x1ff1] = 0x00;
        for (int i = 0; i <= snes.cpu.registers().a; i++) {
            snes.wram.data()[i] = i;
        }

        int cycles = snes.cpu.MVP(0x1ff0);

        assertEquals(0x77, cycles);
        assertEquals(0x00, snes.cpu.registers().dbr);
        assertEquals(0xffff, snes.cpu.registers().a);
        assertEquals(0xffff, snes.cpu.registers().x);
        assertEquals(0x0fff, snes.cpu.registers().y);
        for (int i = 0; i < 0x11; i++) {
            assertEquals(i, snes.wram.data()[0x1000 + i]);
        }
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
