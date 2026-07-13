package jamsnes.cpu;

import jamsnes.SNES;
import jamsnes.cartridge.MappingMode;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CpuUpdateLoopTest {
    @Test
    void updateExecutesInstructionsUntilCycleBudget() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        writeProgram(snes, 0x0200, 0xea, 0xea, 0xea);

        assertEquals(4, snes.cpu.update(4));
        assertEquals(0x0202, snes.cpu.registers().pc);
    }

    @Test
    void stoppedCpuConsumesCyclesWithoutFetchingMoreInstructions() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        writeProgram(snes, 0x0200, 0xdb, 0xea);

        assertEquals(4, snes.cpu.update(4));
        assertTrue(snes.cpu.isStopped());
        assertEquals(0x0201, snes.cpu.registers().pc);
    }

    @Test
    void waitingCpuReturnsSentinelWhenNoInterruptIsPending() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.wram.data()[0x0200] = 0xcb;

        assertEquals(0xff, snes.cpu.update(10));
        assertTrue(snes.cpu.isWaitingForInterrupt());
        assertEquals(0x0201, snes.cpu.registers().pc);
    }

    @Test
    void updateRunsNmiBeforeNextInstruction() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().s = 0x01ff;
        snes.cartridge.header.emulationInterrupts.nmi = 0x0300;
        snes.cpu.isNMIRequested = true;
        snes.wram.data()[0x0300] = 0xea;

        assertEquals(2, snes.cpu.update(2));
        assertEquals(0x0301, snes.cpu.registers().pc);
        assertEquals(0, snes.cpu.registers().pbr);
        assertEquals(snes.cpu.registers().p.flags(), snes.cpu._pop());
        assertEquals(0x0200, snes.cpu._pop16());
        assertFalse(snes.cpu.isNMIRequested);

        snes.cpu.registers().setPc(0x0400);
        snes.wram.data()[0x0400] = 0xea;

        assertEquals(2, snes.cpu.update(2));
        assertEquals(0x0401, snes.cpu.registers().pc);
    }

    @Test
    void updateRunsAbortBeforeNextInstruction() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().s = 0x01ff;
        snes.cartridge.header.emulationInterrupts.abort = 0x0300;
        snes.wram.data()[0x0300] = 0xea;
        int pushedStatus = snes.cpu.registers().p.flags();

        snes.cpu.requestABORT();

        assertEquals(2, snes.cpu.update(2));
        assertEquals(0x0301, snes.cpu.registers().pc);
        assertEquals(0, snes.cpu.registers().pbr);
        assertEquals(pushedStatus, snes.cpu._pop());
        assertEquals(0x0200, snes.cpu._pop16());
        assertFalse(snes.cpu.isAbortRequested);
    }

    @Test
    void updateLeavesMaskedIrqPendingAndRunsNextInstruction() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().p.i = true;
        snes.cpu.WAI(0);
        snes.cpu.requestIRQ();
        writeProgram(snes, 0x0200, 0xea);

        assertEquals(2, snes.cpu.update(2));
        assertEquals(0x0201, snes.cpu.registers().pc);
        assertFalse(snes.cpu.isWaitingForInterrupt());
        assertTrue(snes.cpu.isIRQRequested);
    }

    @Test
    void updateRunsIrqBeforeNextInstructionWhenInterruptsAreEnabled() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().s = 0x01ff;
        snes.cpu.registers().p.i = false;
        snes.cartridge.header.emulationInterrupts.irq = 0x0300;
        snes.cpu.requestIRQ();
        snes.wram.data()[0x0300] = 0xea;
        int pushedStatus = snes.cpu.registers().p.flags();

        assertEquals(2, snes.cpu.update(2));
        assertEquals(0x0301, snes.cpu.registers().pc);
        assertEquals(0, snes.cpu.registers().pbr);
        assertEquals(pushedStatus, snes.cpu._pop());
        assertEquals(0x0200, snes.cpu._pop16());
        assertFalse(snes.cpu.isIRQRequested);
    }

    @Test
    void updateRunsNativeAbortWithProgramBankOnStack() {
        SNES snes = init();
        snes.cpu.setEmulationMode(false);
        snes.cpu.registers().setPac(0x120200);
        snes.cpu.registers().s = 0x01ff;
        snes.cartridge.header.nativeInterrupts.abort = 0x0300;
        snes.wram.data()[0x0300] = 0xea;
        int pushedStatus = snes.cpu.registers().p.flags();

        snes.cpu.requestABORT();

        assertEquals(2, snes.cpu.update(2));
        assertEquals(0x0301, snes.cpu.registers().pc);
        assertEquals(0, snes.cpu.registers().pbr);
        assertEquals(pushedStatus, snes.cpu._pop());
        assertEquals(0x0200, snes.cpu._pop16());
        assertEquals(0x12, snes.cpu._pop());
        assertFalse(snes.cpu.isAbortRequested);
    }

    @Test
    void updateRunsDmaBeforeInstructions() {
        SNES snes = init();
        snes.wram.data()[0] = 0x34;
        snes.bus.write(0x2100, 0x80);
        snes.bus.write(0x2115, 0b1000_0000);
        snes.bus.write(0x4301, 0x18);
        snes.bus.write(0x4304, 0x7e);
        snes.bus.write(0x4303, 0x00);
        snes.bus.write(0x4302, 0x00);
        snes.bus.write(0x4306, 0x00);
        snes.bus.write(0x4305, 0x01);
        snes.bus.write(0x4300, DMA.ONE_TO_ONE);
        snes.bus.write(0x420b, 0x01);
        snes.cpu.registers().setPc(0x0200);
        snes.wram.data()[0x0200] = 0xea;

        assertEquals(16, snes.cpu.update(1));
        assertFalse(snes.cpu.dmaChannels()[0].isEnabled());
        assertEquals(0x0200, snes.cpu.registers().pc);
        assertEquals(0x34, snes.ppu.vram.data()[0]);
    }

    @Test
    void disabledCpuReturnsSentinel() {
        SNES snes = init();
        snes.cpu.isDisabled = true;

        assertEquals(0xff, snes.cpu.update(10));
    }

    private static void writeProgram(SNES snes, int start, int... opcodes) {
        for (int i = 0; i < opcodes.length; i++) {
            snes.wram.data()[start + i] = opcodes[i];
        }
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
