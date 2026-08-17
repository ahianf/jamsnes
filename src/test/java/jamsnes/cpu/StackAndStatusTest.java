package jamsnes.cpu;

import jamsnes.SNES;
import jamsnes.cartridge.MappingMode;
import jamsnes.renderer.TestFrontend;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StackAndStatusTest {
    @Test
    void statusFlagsRoundTripToByte() {
        StatusRegister status = new StatusRegister();
        status.setFlags(0b1010_0101);

        assertTrue(status.c);
        assertFalse(status.z);
        assertTrue(status.i);
        assertFalse(status.d);
        assertFalse(status.x_b);
        assertTrue(status.m);
        assertFalse(status.v);
        assertTrue(status.n);
        assertEquals(0b1010_0101, status.flags());
    }

    @Test
    void pushAndPopEightBits() {
        SNES snes = init();
        snes.cpu.setEmulationMode(false);
        snes.cpu.registers().s = 0x0010;

        snes.cpu._push8(0xab);

        assertEquals(0x000f, snes.cpu.registers().s);
        assertEquals(0xab, snes.wram.data()[0x10]);
        assertEquals(0xab, snes.cpu._pop());
        assertEquals(0x0010, snes.cpu.registers().s);
    }

    @Test
    void pushAndPopSixteenBitsHighByteFirst() {
        SNES snes = init();
        snes.cpu.setEmulationMode(false);
        snes.cpu.registers().s = 0x0010;

        snes.cpu._push16(0xabcd);

        assertEquals(0x000e, snes.cpu.registers().s);
        assertEquals(0xab, snes.wram.data()[0x10]);
        assertEquals(0xcd, snes.wram.data()[0x0f]);
        assertEquals(0xabcd, snes.cpu._pop16());
        assertEquals(0x0010, snes.cpu.registers().s);
    }

    @Test
    void stackPointerWrapsAtSixteenBits() {
        SNES snes = init();
        snes.cpu.setEmulationMode(false);
        snes.cpu.registers().s = 0x0000;

        snes.cpu._push8(0x42);

        assertEquals(0xffff, snes.cpu.registers().s);
        assertEquals(0x42, snes.cpu._pop());
        assertEquals(0x0000, snes.cpu.registers().s);
    }

    @Test
    void emulationStackWrapsWithinPageOne() {
        SNES snes = init();
        snes.cpu.setEmulationMode(true);
        snes.cpu.registers().s = 0x0100;

        snes.cpu._push16(0xabcd);

        assertEquals(0x01fe, snes.cpu.registers().s);
        assertEquals(0xab, snes.wram.data()[0x0100]);
        assertEquals(0xcd, snes.wram.data()[0x01ff]);
        assertEquals(0xabcd, snes.cpu._pop16());
        assertEquals(0x0100, snes.cpu.registers().s);
    }

    @Test
    void enteringEmulationModeForcesStackPageOne() {
        SNES snes = init();
        snes.cpu.setEmulationMode(false);
        snes.cpu.registers().p.m = false;
        snes.cpu.registers().p.x_b = false;
        snes.cpu.registers().s = 0xabcd;
        snes.cpu.registers().x = 0x1234;
        snes.cpu.registers().y = 0x5678;

        snes.cpu.setEmulationMode(true);

        assertEquals(0x01cd, snes.cpu.registers().s);
        assertTrue(snes.cpu.registers().p.m);
        assertTrue(snes.cpu.registers().p.x_b);
        assertEquals(0x0034, snes.cpu.registers().x);
        assertEquals(0x0078, snes.cpu.registers().y);
    }

    private static SNES init() {
        SNES snes = new SNES(new TestFrontend(0, 0, 0));
        snes.cartridge.setSize(100);
        snes.cartridge.header.addMappingMode(MappingMode.LOROM);
        snes.sram.setSize(100);
        snes.bus.mapComponents(snes);
        return snes;
    }
}
