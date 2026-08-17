package jamsnes.cpu;

import jamsnes.SNES;
import jamsnes.cartridge.MappingMode;
import jamsnes.audio.RecordingAudioSink;
import jamsnes.video.RecordingVideoSink;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryInstructionTest {
    @Test
    void storesAccumulatorInConfiguredWidth() {
        SNES snes = init();
        snes.cpu.registers().p.m = true;
        snes.cpu.registers().a = 0x11ab;
        snes.cpu.STA(0);
        assertEquals(0xab, snes.wram.data()[0]);

        snes.cpu.registers().p.m = false;
        snes.cpu.STA(2);
        assertEquals(0xab, snes.wram.data()[2]);
        assertEquals(0x11, snes.wram.data()[3]);
    }

    @Test
    void storesIndexRegistersInConfiguredWidth() {
        SNES snes = init();
        snes.cpu.registers().p.x_b = true;
        snes.cpu.registers().x = 0x11ab;
        snes.cpu.STX(0);
        assertEquals(0xab, snes.wram.data()[0]);

        snes.cpu.registers().p.x_b = false;
        snes.cpu.registers().y = 0x22cd;
        snes.cpu.STY(2);
        assertEquals(0xcd, snes.wram.data()[2]);
        assertEquals(0x22, snes.wram.data()[3]);
    }

    @Test
    void storesZeroInAccumulatorWidth() {
        SNES snes = init();
        snes.cpu.registers().p.m = false;
        snes.wram.data()[0] = 0x11;
        snes.wram.data()[1] = 0x22;

        snes.cpu.STZ(0);

        assertEquals(0, snes.wram.data()[0]);
        assertEquals(0, snes.wram.data()[1]);
    }

    @Test
    void loadsAccumulatorAndFlags() {
        SNES snes = init();
        snes.cpu.registers().p.m = true;
        snes.cpu.registers().a = 0xab00;
        snes.wram.data()[0] = 0x11;
        snes.cpu.LDA(0);
        assertEquals(0xab11, snes.cpu.registers().a);
        assertFalse(snes.cpu.registers().p.n);
        assertFalse(snes.cpu.registers().p.z);

        snes.wram.data()[0] = 0x80;
        snes.cpu.LDA(0);
        assertEquals(0xab80, snes.cpu.registers().a);
        assertTrue(snes.cpu.registers().p.n);
        assertFalse(snes.cpu.registers().p.z);

        snes.cpu.registers().a = 0xab00;
        snes.wram.data()[0] = 0x00;
        snes.cpu.LDA(0);
        assertEquals(0xab00, snes.cpu.registers().a);
        assertTrue(snes.cpu.registers().p.z);
        assertFalse(snes.cpu.registers().p.n);

        snes.cpu.registers().p.m = false;
        snes.wram.data()[0] = 0x00;
        snes.wram.data()[1] = 0x00;
        snes.cpu.LDA(0);
        assertEquals(0x0000, snes.cpu.registers().a);
        assertTrue(snes.cpu.registers().p.z);
        assertFalse(snes.cpu.registers().p.n);
    }

    @Test
    void loadsXInConfiguredWidth() {
        SNES snes = init();
        snes.cpu.registers().p.x_b = true;
        snes.wram.data()[0] = 0x11;
        snes.cpu.LDX(0);
        assertEquals(0x11, snes.cpu.registers().x);
        assertFalse(snes.cpu.registers().p.n);

        snes.wram.data()[0] = 0x80;
        snes.cpu.LDX(0);
        assertEquals(0x80, snes.cpu.registers().x);
        assertTrue(snes.cpu.registers().p.n);

        snes.cpu.registers().p.x_b = false;
        snes.wram.data()[0] = 0xab;
        snes.wram.data()[1] = 0x01;
        snes.cpu.LDX(0);
        assertEquals(0x01ab, snes.cpu.registers().x);
        assertFalse(snes.cpu.registers().p.n);
    }

    @Test
    void loadsYInConfiguredWidth() {
        SNES snes = init();
        snes.cpu.registers().p.x_b = false;
        snes.wram.data()[0] = 0xab;
        snes.wram.data()[1] = 0x11;
        snes.cpu.LDY(0);
        assertEquals(0x11ab, snes.cpu.registers().y);
        assertFalse(snes.cpu.registers().p.n);
        assertFalse(snes.cpu.registers().p.z);

        snes.wram.data()[0] = 0x00;
        snes.wram.data()[1] = 0x80;
        snes.cpu.LDY(0);
        assertEquals(0x8000, snes.cpu.registers().y);
        assertTrue(snes.cpu.registers().p.n);
        assertFalse(snes.cpu.registers().p.z);
    }

    private static SNES init() {
        SNES snes = new SNES(new RecordingVideoSink(), new RecordingAudioSink());
        snes.cartridge.setSize(100);
        snes.cartridge.header.addMappingMode(MappingMode.LOROM);
        snes.sram.setSize(100);
        snes.bus.mapComponents(snes);
        return snes;
    }
}
