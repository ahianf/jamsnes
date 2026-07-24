package jamsnes.cpu;

import jamsnes.SNES;
import jamsnes.cartridge.MappingMode;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MathLogicInstructionTest {
    @Test
    void cmpSetsCarryZeroAndNegative() {
        SNES snes = init();
        snes.cpu.registers().p.m = true;
        snes.cpu.registers().a = 0xab00;
        snes.wram.data()[0] = 1;
        snes.cpu.CMP(0);
        assertFalse(snes.cpu.registers().p.c);
        assertTrue(snes.cpu.registers().p.n);
        assertFalse(snes.cpu.registers().p.z);

        snes.cpu.registers().a = 5;
        snes.wram.data()[0] = 5;
        snes.cpu.CMP(0);
        assertTrue(snes.cpu.registers().p.c);
        assertFalse(snes.cpu.registers().p.n);
        assertTrue(snes.cpu.registers().p.z);
    }

    @Test
    void cmpHandlesSixteenBitMode() {
        SNES snes = init();
        snes.cpu.registers().p.m = false;
        snes.cpu.registers().a = 0xb000;
        snes.wram.data()[0] = 0x00;
        snes.wram.data()[1] = 0x10;

        snes.cpu.CMP(0);

        assertTrue(snes.cpu.registers().p.c);
        assertTrue(snes.cpu.registers().p.n);
        assertFalse(snes.cpu.registers().p.z);
    }

    @Test
    void adcHandlesCarryOverflowAndWidth() {
        SNES snes = init();
        snes.cpu.registers().p.m = true;
        snes.cpu.registers().a = 0xabff;
        snes.wram.data()[0] = 0x01;
        snes.cpu.ADC(0);
        assertEquals(0xab00, snes.cpu.registers().a);
        assertTrue(snes.cpu.registers().p.c);
        assertTrue(snes.cpu.registers().p.z);

        snes.cpu.registers().p.m = false;
        snes.cpu.registers().p.c = false;
        snes.cpu.registers().a = 0x7fff;
        snes.wram.data()[0] = 0x01;
        snes.wram.data()[1] = 0x00;
        snes.cpu.ADC(0);
        assertEquals(0x8000, snes.cpu.registers().a);
        assertTrue(snes.cpu.registers().p.v);
        assertTrue(snes.cpu.registers().p.n);
        assertFalse(snes.cpu.registers().p.c);
    }

    @Test
    void adcIncludesCarryInputWithoutChangingOperandSignForOverflow() {
        SNES snes = init();
        snes.cpu.registers().p.m = true;
        snes.cpu.registers().p.c = true;
        snes.cpu.registers().a = 0xab00;
        snes.wram.data()[0] = 0x7f;

        snes.cpu.ADC(0);

        assertEquals(0xab80, snes.cpu.registers().a);
        assertTrue(snes.cpu.registers().p.v);

        snes.cpu.registers().p.c = true;
        snes.cpu.registers().a = 0xab80;
        snes.wram.data()[0] = 0x7f;

        snes.cpu.ADC(0);

        assertEquals(0xab00, snes.cpu.registers().a);
        assertFalse(snes.cpu.registers().p.v);

        snes.cpu.registers().p.m = false;
        snes.cpu.registers().p.c = true;
        snes.cpu.registers().a = 0x0000;
        snes.wram.data()[0] = 0xff;
        snes.wram.data()[1] = 0x7f;

        snes.cpu.ADC(0);

        assertEquals(0x8000, snes.cpu.registers().a);
        assertTrue(snes.cpu.registers().p.v);
    }

    @Test
    void adcUsesBcdArithmeticWhenDecimalFlagIsSet() {
        SNES snes = init();
        snes.cpu.registers().p.d = true;
        snes.cpu.registers().p.m = true;
        snes.cpu.registers().a = 0xab45;
        snes.wram.data()[0] = 0x55;

        snes.cpu.ADC(0);

        assertEquals(0xab00, snes.cpu.registers().a);
        assertTrue(snes.cpu.registers().p.c);
        assertTrue(snes.cpu.registers().p.z);

        snes.cpu.registers().p.m = false;
        snes.cpu.registers().p.c = false;
        snes.cpu.registers().a = 0x1234;
        snes.wram.data()[0] = 0x66;
        snes.wram.data()[1] = 0x87;

        snes.cpu.ADC(0);

        assertEquals(0x0000, snes.cpu.registers().a);
        assertTrue(snes.cpu.registers().p.c);
        assertTrue(snes.cpu.registers().p.z);
    }

    @Test
    void sbcHandlesBorrowAndWidth() {
        SNES snes = init();
        snes.cpu.registers().p.m = true;
        snes.cpu.registers().p.c = true;
        snes.cpu.registers().a = 0xab01;
        snes.wram.data()[0] = 0x01;
        snes.cpu.SBC(0);
        assertEquals(0xab00, snes.cpu.registers().a);
        assertTrue(snes.cpu.registers().p.c);
        assertTrue(snes.cpu.registers().p.z);

        snes.cpu.registers().p.m = false;
        snes.cpu.registers().p.c = true;
        snes.cpu.registers().a = 0x0001;
        snes.wram.data()[0] = 0x03;
        snes.wram.data()[1] = 0x20;
        snes.cpu.SBC(0);
        assertEquals(0xdffe, snes.cpu.registers().a);
        assertFalse(snes.cpu.registers().p.c);
        assertTrue(snes.cpu.registers().p.n);
    }

    @Test
    void sbcCarryReflectsBorrowInput() {
        SNES snes = init();
        snes.cpu.registers().p.m = true;
        snes.cpu.registers().p.c = false;
        snes.cpu.registers().a = 0xab00;
        snes.wram.data()[0] = 0x00;

        snes.cpu.SBC(0);

        assertEquals(0xabff, snes.cpu.registers().a);
        assertFalse(snes.cpu.registers().p.c);
        assertTrue(snes.cpu.registers().p.n);

        snes.cpu.registers().p.m = false;
        snes.cpu.registers().p.c = false;
        snes.cpu.registers().a = 0x0000;
        snes.wram.data()[0] = 0x00;
        snes.wram.data()[1] = 0x00;

        snes.cpu.SBC(0);

        assertEquals(0xffff, snes.cpu.registers().a);
        assertFalse(snes.cpu.registers().p.c);
        assertTrue(snes.cpu.registers().p.n);
    }

    @Test
    void sbcSetsSignedOverflowForBinarySubtraction() {
        SNES snes = init();
        snes.cpu.registers().p.m = true;
        snes.cpu.registers().p.c = true;
        snes.cpu.registers().a = 0xab80;
        snes.wram.data()[0] = 0x01;

        snes.cpu.SBC(0);

        assertEquals(0xab7f, snes.cpu.registers().a);
        assertTrue(snes.cpu.registers().p.v);
        assertFalse(snes.cpu.registers().p.n);

        snes.cpu.registers().p.m = false;
        snes.cpu.registers().p.c = true;
        snes.cpu.registers().a = 0x7fff;
        snes.wram.data()[0] = 0xff;
        snes.wram.data()[1] = 0xff;

        snes.cpu.SBC(0);

        assertEquals(0x8000, snes.cpu.registers().a);
        assertTrue(snes.cpu.registers().p.v);
        assertTrue(snes.cpu.registers().p.n);
    }

    @Test
    void sbcUsesBcdArithmeticWhenDecimalFlagIsSet() {
        SNES snes = init();
        snes.cpu.registers().p.d = true;
        snes.cpu.registers().p.m = true;
        snes.cpu.registers().p.c = true;
        snes.cpu.registers().a = 0xab00;
        snes.wram.data()[0] = 0x01;

        snes.cpu.SBC(0);

        assertEquals(0xab99, snes.cpu.registers().a);
        assertFalse(snes.cpu.registers().p.c);
        assertTrue(snes.cpu.registers().p.n);

        snes.cpu.registers().p.m = false;
        snes.cpu.registers().p.c = true;
        snes.cpu.registers().a = 0x1000;
        snes.wram.data()[0] = 0x01;
        snes.wram.data()[1] = 0x00;

        snes.cpu.SBC(0);

        assertEquals(0x0999, snes.cpu.registers().a);
        assertTrue(snes.cpu.registers().p.c);
        assertFalse(snes.cpu.registers().p.z);
    }

    @Test
    void oraAndAndAndEorUseAccumulatorWidth() {
        SNES snes = init();
        snes.cpu.registers().p.m = true;
        snes.cpu.registers().a = 0xab80;
        snes.wram.data()[0] = 0x0f;
        snes.cpu.ORA(0);
        assertEquals(0xab8f, snes.cpu.registers().a);
        assertTrue(snes.cpu.registers().p.n);

        snes.wram.data()[0] = 0x0f;
        snes.cpu.AND(0);
        assertEquals(0xab0f, snes.cpu.registers().a);
        assertFalse(snes.cpu.registers().p.n);

        snes.wram.data()[0] = 0x0f;
        snes.cpu.EOR(0);
        assertEquals(0xab00, snes.cpu.registers().a);
        assertTrue(snes.cpu.registers().p.z);
    }

    @Test
    void incAndDecMemoryUseAccumulatorWidth() {
        SNES snes = init();
        snes.cpu.registers().p.m = true;
        snes.wram.data()[0] = 0x7f;
        snes.cpu.INC(0);
        assertEquals(0x80, snes.wram.data()[0]);
        assertTrue(snes.cpu.registers().p.n);

        snes.wram.data()[0] = 0x81;
        snes.cpu.DEC(0);
        assertEquals(0x80, snes.wram.data()[0]);
        assertTrue(snes.cpu.registers().p.n);
    }

    @Test
    void incAndDecAccumulatorUseAccumulatorWidth() {
        SNES snes = init();
        snes.cpu.registers().p.m = false;
        snes.cpu.registers().a = 0x8fff;
        snes.cpu.INA(0);
        assertEquals(0x9000, snes.cpu.registers().a);
        assertTrue(snes.cpu.registers().p.n);

        snes.cpu.registers().p.m = true;
        snes.cpu.registers().a = 0xab00;
        snes.cpu.DEA(0);
        assertEquals(0xabff, snes.cpu.registers().a);
        assertTrue(snes.cpu.registers().p.n);
    }

    @Test
    void xbaSwapsAccumulatorBytesAndSetsFlagsFromLowByte() {
        SNES snes = init();
        snes.cpu.registers().a = 0x0012;

        snes.cpu.XBA(0);

        assertEquals(0x1200, snes.cpu.registers().a);
        assertTrue(snes.cpu.registers().p.z);
        assertFalse(snes.cpu.registers().p.n);
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
