package jamsnes.cpu;

import jamsnes.SNES;
import jamsnes.cartridge.MappingMode;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BitShiftInstructionTest {
    @Test
    void tsbAndTrbUpdateMemoryAndZeroFlag() {
        SNES snes = init();
        snes.wram.data()[0] = 0b00110011;
        snes.cpu.registers().a = 0b00110111;
        snes.cpu.registers().p.m = true;
        snes.cpu.TSB(0);
        assertEquals(0b00110111, snes.wram.data()[0]);
        assertFalse(snes.cpu.registers().p.z);

        snes.wram.data()[0] = 0b0000_0010;
        snes.cpu.registers().a = 0xab01;
        snes.cpu.TSB(0);
        assertEquals(0b0000_0011, snes.wram.data()[0]);
        assertTrue(snes.cpu.registers().p.z);

        snes.wram.data()[0] = 0xff;
        snes.cpu.TRB(0);
        assertEquals(0b1111_1110, snes.wram.data()[0]);
        assertFalse(snes.cpu.registers().p.z);
    }

    @Test
    void bitImmediateOnlyUpdatesZeroFlag() {
        SNES snes = init();
        snes.wram.data()[0] = 0x00;
        snes.wram.data()[1] = 0xff;
        snes.cpu.registers().p.m = false;
        snes.cpu.registers().a = 0x0008;
        snes.cpu.registers().p.v = true;
        snes.cpu.registers().p.n = true;

        snes.cpu.BIT(0, AddressingMode.IMMEDIATE_FOR_A);

        assertTrue(snes.cpu.registers().p.z);
        assertTrue(snes.cpu.registers().p.v);
        assertTrue(snes.cpu.registers().p.n);
    }

    @Test
    void bitMemoryUpdatesNegativeAndOverflow() {
        SNES snes = init();
        snes.wram.data()[0] = 0x00;
        snes.wram.data()[1] = 0xff;
        snes.cpu.registers().p.m = false;
        snes.cpu.registers().a = 0x8008;

        snes.cpu.BIT(0, AddressingMode.IMPLIED);

        assertFalse(snes.cpu.registers().p.z);
        assertTrue(snes.cpu.registers().p.v);
        assertTrue(snes.cpu.registers().p.n);
    }

    @Test
    void aslShiftsMemoryAndAccumulator() {
        SNES snes = init();
        snes.cpu.registers().p.m = true;
        snes.wram.data()[0] = 0b10110011;
        snes.cpu.ASL(0, AddressingMode.ABSOLUTE);
        assertEquals(0b01100110, snes.wram.data()[0]);
        assertTrue(snes.cpu.registers().p.c);
        assertFalse(snes.cpu.registers().p.n);

        snes.cpu.registers().a = 0xab00 | 0b10110011;
        snes.cpu.ASL(0, AddressingMode.IMPLIED);
        assertEquals(0xab00 | 0b01100110, snes.cpu.registers().a);
        assertTrue(snes.cpu.registers().p.c);
    }

    @Test
    void lsrShiftsSixteenBitMemory() {
        SNES snes = init();
        snes.cpu.registers().p.m = false;
        snes.wram.data()[0] = 0b10110011;
        snes.wram.data()[1] = 0b10000011;

        snes.cpu.LSR(0, AddressingMode.ABSOLUTE);

        assertEquals(0b11011001, snes.wram.data()[0]);
        assertEquals(0b01000001, snes.wram.data()[1]);
        assertTrue(snes.cpu.registers().p.c);
        assertFalse(snes.cpu.registers().p.n);
    }

    @Test
    void rolUsesCarry() {
        SNES snes = init();
        snes.cpu.registers().p.m = true;
        snes.cpu.registers().p.c = true;
        snes.wram.data()[0] = 0b10110011;

        snes.cpu.ROL(0, AddressingMode.ABSOLUTE);

        assertEquals(0b01100111, snes.wram.data()[0]);
        assertTrue(snes.cpu.registers().p.c);
    }

    @Test
    void rorUsesCarry() {
        SNES snes = init();
        snes.cpu.registers().p.m = true;
        snes.cpu.registers().p.c = true;
        snes.wram.data()[0] = 0b01100110;

        snes.cpu.ROR(0, AddressingMode.ABSOLUTE);

        assertEquals(0b10110011, snes.wram.data()[0]);
        assertFalse(snes.cpu.registers().p.c);
        assertFalse(snes.cpu.registers().p.n);

        snes.cpu.registers().p.c = true;
        snes.cpu.registers().a = 0xab01;
        snes.cpu.ROR(0, AddressingMode.IMPLIED);

        assertEquals(0xab80, snes.cpu.registers().a);
        assertTrue(snes.cpu.registers().p.c);
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
