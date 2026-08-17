package jamsnes.cpu;

import jamsnes.SNES;
import jamsnes.cartridge.MappingMode;
import jamsnes.audio.RecordingAudioSink;
import jamsnes.video.RecordingVideoSink;
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
        assertEquals(0x3f, snes.cpu._pop());
        assertEquals(0x156, snes.cpu._pop16());
    }

    @Test
    void rtiInNativeModeRestoresSingleByteProgramBank() {
        SNES snes = init();
        snes.cpu.setEmulationMode(false);
        snes.cpu.registers().s = 0x0100;
        snes.cpu._push8(0x12);
        snes.cpu._push16(0x3456);
        snes.cpu._push8(0xa5);

        int cycles = snes.cpu.RTI(0);

        assertEquals(1, cycles);
        assertEquals(0xa5, snes.cpu.registers().p.flags());
        assertEquals(0x12, snes.cpu.registers().pbr);
        assertEquals(0x3456, snes.cpu.registers().pc);
        assertEquals(0x123456, snes.cpu.registers().pac);
        assertEquals(0x0100, snes.cpu.registers().s);
    }

    @Test
    void rtiInEmulationModeForcesAccumulatorAndIndexWidthFlags() {
        SNES snes = init();
        snes.cpu.setEmulationMode(true);
        snes.cpu.registers().s = 0x0100;
        snes.cpu._push16(0x3456);
        snes.cpu._push8(0x00);

        int cycles = snes.cpu.RTI(0);

        assertEquals(0, cycles);
        assertEquals(0x30, snes.cpu.registers().p.flags() & 0x30);
        assertEquals(0x3456, snes.cpu.registers().pc);
        assertEquals(0, snes.cpu.registers().pbr);
        assertEquals(0x0100, snes.cpu.registers().s);
    }

    @Test
    void resetLoadsEmulationResetVector() {
        SNES snes = init();
        snes.cpu.setEmulationMode(false);
        snes.cpu.registers().p.d = true;
        snes.cpu.registers().p.m = false;
        snes.cpu.registers().p.x_b = false;
        snes.cpu.registers().x = 0xabcd;
        snes.cpu.registers().y = 0xef12;
        snes.cartridge.header.emulationInterrupts.reset = 0x8123;
        snes.cpu.WAI(0);
        snes.cpu.requestNMI();
        snes.cpu.requestIRQ();
        snes.cpu.requestABORT();
        snes.cpu.internalRegisters()[0x00] = 0x81;
        snes.cpu.internalRegisters()[0x01] = 0x00;
        snes.cpu.internalRegisters()[0x0b] = 0xff;
        snes.cpu.internalRegisters()[0x0c] = 0xff;
        snes.cpu.internalRegisters()[0x0d] = 0xff;

        snes.cpu.RESB();

        assertTrue(snes.cpu.isEmulationMode());
        assertTrue(snes.cpu.registers().p.i);
        assertFalse(snes.cpu.registers().p.d);
        assertTrue(snes.cpu.registers().p.m);
        assertTrue(snes.cpu.registers().p.x_b);
        assertEquals(0x00cd, snes.cpu.registers().x);
        assertEquals(0x0012, snes.cpu.registers().y);
        assertEquals(0x8123, snes.cpu.registers().pc);
        assertFalse(snes.cpu.isStopped());
        assertFalse(snes.cpu.isWaitingForInterrupt());
        assertFalse(snes.cpu.isNMIRequested);
        assertFalse(snes.cpu.isIRQRequested);
        assertFalse(snes.cpu.isAbortRequested);
        assertEquals(0, snes.cpu.internalRegisters()[0x10] & 0x80);
        assertEquals(0, snes.cpu.internalRegisters()[0x11] & 0x80);
        assertEquals(0x00, snes.cpu.internalRegisters()[0x00]);
        assertEquals(0xff, snes.cpu.internalRegisters()[0x01]);
        assertEquals(0xff, snes.cpu.read(0x13));
        assertEquals(0x00, snes.cpu.internalRegisters()[0x0b]);
        assertEquals(0x00, snes.cpu.internalRegisters()[0x0c]);
        assertEquals(0x00, snes.cpu.internalRegisters()[0x0d]);
    }

    @Test
    void waiSetsWaitingFlag() {
        SNES snes = init();

        snes.cpu.WAI(0);

        assertTrue(snes.cpu.isWaitingForInterrupt());
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
