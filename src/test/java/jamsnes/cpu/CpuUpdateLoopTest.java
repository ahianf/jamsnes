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
        assertEquals(24, snes.cpu.elapsedMasterClocks());
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
    void waitingCpuConsumesRequestedBudgetWhenNoInterruptIsPending() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.wram.data()[0x0200] = 0xcb;

        assertEquals(10, snes.cpu.update(10));
        assertEquals(60, snes.cpu.elapsedMasterClocks());
        assertTrue(snes.cpu.isWaitingForInterrupt());
        assertEquals(0x0201, snes.cpu.registers().pc);

        assertEquals(6, snes.cpu.update(6));
        assertEquals(36, snes.cpu.elapsedMasterClocks());
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
        int pushedStatus = snes.cpu.registers().p.flags() & ~0x10;

        assertEquals(7, snes.cpu.update(7));
        assertEquals(0x0300, snes.cpu.registers().pc);
        assertEquals(0, snes.cpu.registers().pbr);
        assertEquals(pushedStatus, snes.cpu._pop());
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
        int pushedStatus = snes.cpu.registers().p.flags() & ~0x10;

        snes.cpu.requestABORT();

        assertEquals(7, snes.cpu.update(7));
        assertEquals(0x0300, snes.cpu.registers().pc);
        assertEquals(0, snes.cpu.registers().pbr);
        assertEquals(pushedStatus, snes.cpu._pop());
        assertEquals(0x0200, snes.cpu._pop16());
        assertFalse(snes.cpu.isAbortRequested);
    }

    @Test
    void abortDuringWaitReturnsToWaiInstruction() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().s = 0x01ff;
        snes.cartridge.header.emulationInterrupts.abort = 0x0300;
        writeProgram(snes, 0x0200, 0xcb, 0xea);
        writeProgram(snes, 0x0300, 0x40);

        assertEquals(3, snes.cpu.update(3));
        assertTrue(snes.cpu.isWaitingForInterrupt());
        assertEquals(0x0201, snes.cpu.registers().pc);

        snes.cpu.requestABORT();

        assertEquals(13, snes.cpu.update(13));
        assertFalse(snes.cpu.isWaitingForInterrupt());
        assertEquals(0x0200, snes.cpu.registers().pc);
        assertFalse(snes.cpu.isAbortRequested);

        assertEquals(3, snes.cpu.update(3));
        assertTrue(snes.cpu.isWaitingForInterrupt());
        assertEquals(0x0201, snes.cpu.registers().pc);
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
        int pushedStatus = snes.cpu.registers().p.flags() & ~0x10;

        assertEquals(7, snes.cpu.update(7));
        assertEquals(0x0300, snes.cpu.registers().pc);
        assertEquals(0, snes.cpu.registers().pbr);
        assertEquals(pushedStatus, snes.cpu._pop());
        assertEquals(0x0200, snes.cpu._pop16());
        assertTrue(snes.cpu.isIRQRequested);
    }

    @Test
    void acceptedIrqRetriggersUntilItsSourceIsAcknowledged() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().s = 0x01ff;
        snes.cpu.registers().p.i = false;
        snes.cartridge.header.emulationInterrupts.irq = 0x0300;
        writeProgram(snes, 0x0200, 0xea);
        writeProgram(snes, 0x0300, 0x40);
        snes.cpu.requestIRQ();

        assertEquals(13, snes.cpu.update(13));
        assertEquals(0x0200, snes.cpu.registers().pc);
        assertTrue(snes.cpu.isIRQRequested);

        assertEquals(7, snes.cpu.update(7));
        assertEquals(0x0300, snes.cpu.registers().pc);
    }

    @Test
    void timeupReadInIrqHandlerPreventsRetriggeringAfterRti() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().s = 0x01ff;
        snes.cpu.registers().p.i = false;
        snes.cartridge.header.emulationInterrupts.irq = 0x0300;
        writeProgram(snes, 0x0200, 0xea);
        writeProgram(snes, 0x0300, 0xad, 0x11, 0x42, 0x40);
        snes.cpu.requestIRQ();

        assertEquals(17, snes.cpu.update(17));
        assertEquals(0x0200, snes.cpu.registers().pc);
        assertFalse(snes.cpu.isIRQRequested);

        assertEquals(2, snes.cpu.update(2));
        assertEquals(0x0201, snes.cpu.registers().pc);
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

        assertEquals(8, snes.cpu.update(8));
        assertEquals(0x0300, snes.cpu.registers().pc);
        assertEquals(0, snes.cpu.registers().pbr);
        assertEquals(pushedStatus, snes.cpu._pop());
        assertEquals(0x0200, snes.cpu._pop16());
        assertEquals(0x12, snes.cpu._pop());
        assertFalse(snes.cpu.isAbortRequested);
    }

    @Test
    void updateExecutesHandlerInstructionWhenBudgetRemainsAfterInterruptEntry() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.cartridge.header.emulationInterrupts.nmi = 0x0300;
        snes.cpu.requestNMI();
        writeProgram(snes, 0x0300, 0xea);

        assertEquals(9, snes.cpu.update(9));
        assertEquals(0x0301, snes.cpu.registers().pc);
    }

    @Test
    void updateYieldsBetweenBlockMoveByteIterations() {
        SNES snes = init();
        snes.cpu.setEmulationMode(false);
        snes.cpu.registers().p.x_b = false;
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().a = 3;
        snes.cpu.registers().x = 0x0300;
        snes.cpu.registers().y = 0x0400;
        writeProgram(snes, 0x0200, 0x54, 0x00, 0x00);
        writeProgram(snes, 0x0300, 0x11, 0x22, 0x33, 0x44);

        assertEquals(14, snes.cpu.update(12));
        assertEquals(1, snes.cpu.registers().a);
        assertEquals(0x0200, snes.cpu.registers().pc);
        assertEquals(0x11, snes.wram.data()[0x0400]);
        assertEquals(0x22, snes.wram.data()[0x0401]);
        assertEquals(0x00, snes.wram.data()[0x0402]);

        assertEquals(14, snes.cpu.update(12));
        assertEquals(0xffff, snes.cpu.registers().a);
        assertEquals(0x0203, snes.cpu.registers().pc);
        assertEquals(0x33, snes.wram.data()[0x0402]);
        assertEquals(0x44, snes.wram.data()[0x0403]);
    }

    @Test
    void updateRunsDmaBeforeInstructions() {
        SNES snes = init();
        snes.wram.data()[0] = 0x34;
        snes.wram.data()[1] = 0x56;
        snes.wram.data()[2] = 0x78;
        snes.bus.write(0x2100, 0x80);
        snes.bus.write(0x2115, 0b1000_0000);
        snes.bus.write(0x4301, 0x18);
        snes.bus.write(0x4304, 0x7e);
        snes.bus.write(0x4303, 0x00);
        snes.bus.write(0x4302, 0x00);
        snes.bus.write(0x4306, 0x00);
        snes.bus.write(0x4305, 0x03);
        snes.bus.write(0x4300, DMA.TWO_TO_TWO);
        snes.bus.write(0x420b, 0x01);
        snes.cpu.registers().setPc(0x0200);
        snes.wram.data()[0x0200] = 0xea;
        snes.wram.data()[0x0201] = 0xea;

        assertEquals(8, snes.cpu.update(1));
        assertEquals(8, snes.cpu.elapsedMasterClocks());
        assertTrue(snes.cpu.dmaChannels()[0].isEnabled());
        assertEquals(0x01, snes.cpu.internalRegisters()[0x0b]);
        assertEquals(0x0200, snes.cpu.registers().pc);
        assertEquals(0x00, snes.ppu.vram.data()[0]);

        assertEquals(8, snes.cpu.update(1));
        assertTrue(snes.cpu.dmaChannels()[0].isEnabled());
        assertEquals(0x0200, snes.cpu.registers().pc);
        assertEquals(0x00, snes.ppu.vram.data()[0]);

        assertEquals(16, snes.cpu.update(12));
        assertTrue(snes.cpu.dmaChannels()[0].isEnabled());
        assertEquals(0x0200, snes.cpu.registers().pc);
        assertEquals(0x34, snes.ppu.vram.data()[0]);
        assertEquals(0x56, snes.ppu.vram.data()[1]);

        assertEquals(12, snes.cpu.update(12));
        assertFalse(snes.cpu.dmaChannels()[0].isEnabled());
        assertEquals(0x00, snes.cpu.internalRegisters()[0x0b]);
        assertEquals(0x0202, snes.cpu.registers().pc);
        assertEquals(0x78, snes.ppu.vram.data()[2]);
    }

    @Test
    void updateStartsNewDmaBeforeFollowingInstruction() {
        SNES snes = init();
        snes.wram.data()[0x0040] = 0x5a;
        snes.bus.write(0x4300, DMA.ONE_TO_ONE);
        snes.bus.write(0x4301, 0x26);
        snes.bus.write(0x4302, 0x40);
        snes.bus.write(0x4303, 0x00);
        snes.bus.write(0x4304, 0x7e);
        snes.bus.write(0x4305, 0x01);
        snes.bus.write(0x4306, 0x00);
        snes.cpu.registers().setPc(0x0200);
        writeProgram(snes, 0x0200,
                0xa9, 0x01,
                0x8d, 0x0b, 0x42,
                0xea);

        assertEquals(14, snes.cpu.update(8));
        assertEquals(0x0205, snes.cpu.registers().pc);
        assertTrue(snes.cpu.dmaChannels()[0].isEnabled());
        assertEquals(0x00, snes.ppu.registers()[0x26]);

        assertEquals(8, snes.cpu.update(8));
        assertEquals(0x0205, snes.cpu.registers().pc);
        assertTrue(snes.cpu.dmaChannels()[0].isEnabled());

        assertEquals(8, snes.cpu.update(8));
        assertEquals(0x0205, snes.cpu.registers().pc);
        assertFalse(snes.cpu.dmaChannels()[0].isEnabled());
        assertEquals(0x5a, snes.ppu.registers()[0x26]);

        assertEquals(2, snes.cpu.update(2));
        assertEquals(0x0206, snes.cpu.registers().pc);
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
