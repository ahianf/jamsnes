package jamsnes.cpu;

import jamsnes.SNES;
import jamsnes.cartridge.MappingMode;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CpuOpcodeDispatchTest {
    @Test
    void executesNopOpcode() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.wram.data()[0x0200] = 0xea;

        assertEquals(2, snes.cpu.executeInstruction());
        assertEquals(0x0201, snes.cpu.registers().pc);
    }

    @Test
    void executesRelativeBranchesWithFetchedSignedOffset() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.wram.data()[0x0200] = 0xd0;
        snes.wram.data()[0x0201] = 0xfe;

        assertEquals(4, snes.cpu.executeInstruction());
        assertEquals(0x0200, snes.cpu.registers().pc);

        snes.cpu.registers().p.z = true;
        assertEquals(2, snes.cpu.executeInstruction());
        assertEquals(0x0202, snes.cpu.registers().pc);
    }

    @Test
    void executesBplOpcodeWithNormalRelativeBranchCycles() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        writeProgram(snes, 0x0200, 0x10, 0x02, 0x10, 0x02);

        assertEquals(4, snes.cpu.executeInstruction());
        assertEquals(0x0204, snes.cpu.registers().pc);

        snes.cpu.registers().p.n = true;
        snes.cpu.registers().setPc(0x0202);
        assertEquals(2, snes.cpu.executeInstruction());
        assertEquals(0x0204, snes.cpu.registers().pc);
    }

    @Test
    void executesClvOpcodeWithImpliedCycles() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().p.v = true;
        writeProgram(snes, 0x0200, 0xb8);

        assertEquals(2, snes.cpu.executeInstruction());
        assertFalse(snes.cpu.registers().p.v);
        assertEquals(0x0201, snes.cpu.registers().pc);
    }

    @Test
    void executesAbsoluteJumpAndSubroutineOpcodes() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().s = 0x01ff;
        snes.wram.data()[0x0200] = 0x20;
        snes.wram.data()[0x0201] = 0x34;
        snes.wram.data()[0x0202] = 0x12;

        assertEquals(6, snes.cpu.executeInstruction());

        assertEquals(0x1234, snes.cpu.registers().pc);
        assertEquals(0x01fd, snes.cpu.registers().s);
        assertEquals(0x0202, snes.cpu._pop16());

        snes.cpu.registers().setPc(0x0210);
        snes.wram.data()[0x0210] = 0x4c;
        snes.wram.data()[0x0211] = 0x78;
        snes.wram.data()[0x0212] = 0x56;

        assertEquals(3, snes.cpu.executeInstruction());
        assertEquals(0x5678, snes.cpu.registers().pc);
    }

    @Test
    void executesLongBranchAndJumpOpcodes() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        writeProgram(snes, 0x0200, 0x42, 0x99, 0x82, 0xfe, 0xff);

        assertEquals(2, snes.cpu.executeInstruction());
        assertEquals(0x0202, snes.cpu.registers().pc);

        assertEquals(4, snes.cpu.executeInstruction());
        assertEquals(0x0203, snes.cpu.registers().pc);

        snes.cpu.registers().setPc(0x0210);
        writeProgram(snes, 0x0210, 0x5c, 0x56, 0x34, 0x12);
        assertEquals(4, snes.cpu.executeInstruction());
        assertEquals(0x123456, snes.cpu.registers().pac);

        snes.cpu.registers().setPc(0x0220);
        writeProgram(snes, 0x0220, 0xdc, 0x00, 0x04);
        snes.wram.data()[0x0400] = 0x78;
        snes.wram.data()[0x0401] = 0x56;
        snes.wram.data()[0x0402] = 0x34;
        assertEquals(7, snes.cpu.executeInstruction());
        assertEquals(0x345678, snes.cpu.registers().pac);
    }

    @Test
    void executesBlockMoveOpcodesWithFetchedBankOperands() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().a = 0x0002;
        snes.cpu.registers().x = 0x0300;
        snes.cpu.registers().y = 0x0400;
        writeProgram(snes, 0x0200, 0x54, 0x00, 0x00);
        snes.wram.data()[0x0300] = 0x11;
        snes.wram.data()[0x0301] = 0x22;
        snes.wram.data()[0x0302] = 0x33;

        assertEquals(21, snes.cpu.executeInstruction());
        assertEquals(0x0203, snes.cpu.registers().pc);
        assertEquals(0xffff, snes.cpu.registers().a);
        assertEquals(0x0303, snes.cpu.registers().x);
        assertEquals(0x0403, snes.cpu.registers().y);
        assertEquals(0x11, snes.wram.data()[0x0400]);
        assertEquals(0x22, snes.wram.data()[0x0401]);
        assertEquals(0x33, snes.wram.data()[0x0402]);

        snes.cpu.registers().setPc(0x0210);
        snes.cpu.registers().a = 0x0002;
        snes.cpu.registers().x = 0x0312;
        snes.cpu.registers().y = 0x0412;
        writeProgram(snes, 0x0210, 0x44, 0x00, 0x00);
        snes.wram.data()[0x0310] = 0xaa;
        snes.wram.data()[0x0311] = 0xbb;
        snes.wram.data()[0x0312] = 0xcc;

        assertEquals(21, snes.cpu.executeInstruction());
        assertEquals(0x0213, snes.cpu.registers().pc);
        assertEquals(0xffff, snes.cpu.registers().a);
        assertEquals(0x030f, snes.cpu.registers().x);
        assertEquals(0x040f, snes.cpu.registers().y);
        assertEquals(0xaa, snes.wram.data()[0x0410]);
        assertEquals(0xbb, snes.wram.data()[0x0411]);
        assertEquals(0xcc, snes.wram.data()[0x0412]);
    }

    @Test
    void executesInterruptOpcodes() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().s = 0x01ff;
        snes.cpu.registers().p.setFlags(0xf1);
        snes.cartridge.header.emulationInterrupts.brk = 0x1234;
        writeProgram(snes, 0x0200, 0x00, 0xaa);

        assertEquals(7, snes.cpu.executeInstruction());
        assertEquals(0x1234, snes.cpu.registers().pc);
        assertEquals(0xf1, snes.cpu._pop());
        assertEquals(0x0202, snes.cpu._pop16());

        snes.cpu.registers().setPc(0x0210);
        snes.cpu.registers().p.setFlags(0x0f);
        snes.cartridge.header.emulationInterrupts.cop = 0x5678;
        writeProgram(snes, 0x0210, 0x02, 0xbb);

        assertEquals(7, snes.cpu.executeInstruction());
        assertEquals(0x5678, snes.cpu.registers().pc);
        assertEquals(0x0f, snes.cpu._pop());
        assertEquals(0x0212, snes.cpu._pop16());
    }

    @Test
    void executesReturnFromInterruptOpcode() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().s = 0x01ff;
        snes.cpu._push16(0x3456);
        snes.cpu._push8(0xa5);
        snes.wram.data()[0x0200] = 0x40;

        assertEquals(6, snes.cpu.executeInstruction());
        assertEquals(0x3456, snes.cpu.registers().pc);
        assertEquals(0xb5, snes.cpu.registers().p.flags());
    }

    @Test
    void executesStatusImmediateOpcodes() {
        SNES snes = init();
        snes.cpu.setEmulationMode(false);
        snes.cpu.registers().setPc(0x0200);
        snes.wram.data()[0x0200] = 0xe2;
        snes.wram.data()[0x0201] = 0x81;
        snes.wram.data()[0x0202] = 0xc2;
        snes.wram.data()[0x0203] = 0x01;

        assertEquals(3, snes.cpu.executeInstruction());
        assertTrue(snes.cpu.registers().p.c);
        assertTrue(snes.cpu.registers().p.n);

        assertEquals(3, snes.cpu.executeInstruction());
        assertEquals(0xb4, snes.cpu.registers().p.flags());
    }

    @Test
    void executesStopAndWaitOpcodes() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.wram.data()[0x0200] = 0xcb;
        snes.wram.data()[0x0201] = 0xdb;

        assertEquals(3, snes.cpu.executeInstruction());
        assertTrue(snes.cpu.isWaitingForInterrupt());

        assertEquals(3, snes.cpu.executeInstruction());
        assertTrue(snes.cpu.isStopped());
    }

    @Test
    void executesPushOpcodes() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().s = 0x01ff;
        snes.cpu.registers().a = 0x44;
        snes.wram.data()[0x0200] = 0x48;
        snes.wram.data()[0x0201] = 0x08;

        assertEquals(3, snes.cpu.executeInstruction());
        assertEquals(0x44, snes.cpu._pop());

        snes.cpu.registers().p.c = true;
        assertEquals(3, snes.cpu.executeInstruction());
        assertEquals(snes.cpu.registers().p.flags(), snes.cpu._pop());
    }

    @Test
    void executesPullAccumulatorAndDirectRegisterOpcodes() {
        SNES snes = init();
        snes.cpu.setEmulationMode(false);
        snes.cpu.registers().p.m = false;
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().s = 0x01ff;
        writeProgram(snes, 0x0200, 0x68, 0x2b);

        snes.cpu._push16(0x8123);
        assertEquals(5, snes.cpu.executeInstruction());
        assertEquals(0x8123, snes.cpu.registers().a);
        assertTrue(snes.cpu.registers().p.n);

        snes.cpu._push16(0);
        assertEquals(5, snes.cpu.executeInstruction());
        assertEquals(0, snes.cpu.registers().d);
        assertTrue(snes.cpu.registers().p.z);
    }

    @Test
    void executesPullDataBankAndIndexOpcodes() {
        SNES snes = init();
        snes.cpu.setEmulationMode(false);
        snes.cpu.registers().p.x_b = false;
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().s = 0x01ff;
        writeProgram(snes, 0x0200, 0xab, 0xfa, 0x7a);

        snes.cpu._push16(0x8001);
        snes.cpu._push16(0x1234);
        snes.cpu._push8(0x80);

        assertEquals(4, snes.cpu.executeInstruction());
        assertEquals(0x80, snes.cpu.registers().dbr);
        assertTrue(snes.cpu.registers().p.n);

        assertEquals(5, snes.cpu.executeInstruction());
        assertEquals(0x1234, snes.cpu.registers().x);

        assertEquals(5, snes.cpu.executeInstruction());
        assertEquals(0x8001, snes.cpu.registers().y);
        assertTrue(snes.cpu.registers().p.n);
    }

    @Test
    void plpForcesWidthFlagsInEmulationMode() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().s = 0x01ff;
        snes.wram.data()[0x0200] = 0x28;
        snes.cpu._push8(0);

        assertEquals(4, snes.cpu.executeInstruction());
        assertFalse(snes.cpu.registers().p.c);
        assertTrue(snes.cpu.registers().p.m);
        assertTrue(snes.cpu.registers().p.x_b);
    }

    @Test
    void executesEffectiveOperandPushOpcodes() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().s = 0x01ff;
        snes.cpu.registers().d = 0x1000;
        snes.wram.data()[0x1010] = 0x78;
        snes.wram.data()[0x1011] = 0x56;
        writeProgram(snes, 0x0200, 0x62, 0xff, 0xff, 0xd4, 0x10, 0xf4, 0x34, 0x12);

        assertEquals(6, snes.cpu.executeInstruction());
        assertEquals(0x0202, snes.cpu._pop16());

        assertEquals(6, snes.cpu.executeInstruction());
        assertEquals(0x5678, snes.cpu._pop16());

        assertEquals(5, snes.cpu.executeInstruction());
        assertEquals(0x0206, snes.cpu._pop16());
        assertEquals(0x0208, snes.cpu.registers().pc);
    }

    @Test
    void executesImmediateAndAbsoluteLoadOpcodes() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        writeProgram(snes, 0x0200, 0xa9, 0x11, 0xa2, 0x22, 0xa0, 0x33, 0xad, 0x00, 0x04);
        snes.wram.data()[0x0400] = 0x44;

        assertEquals(2, snes.cpu.executeInstruction());
        assertEquals(0x11, snes.cpu.registers().a);

        assertEquals(2, snes.cpu.executeInstruction());
        assertEquals(0x22, snes.cpu.registers().x);

        assertEquals(2, snes.cpu.executeInstruction());
        assertEquals(0x33, snes.cpu.registers().y);

        assertEquals(4, snes.cpu.executeInstruction());
        assertEquals(0x44, snes.cpu.registers().a);
        assertEquals(0x0209, snes.cpu.registers().pc);
    }

    @Test
    void executesDirectAndAbsoluteStoreOpcodes() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().a = 0x12ab;
        snes.cpu.registers().x = 0x34cd;
        snes.cpu.registers().y = 0x56ef;
        snes.wram.data()[0x13] = 0xff;
        writeProgram(snes, 0x0200, 0x85, 0x10, 0x86, 0x11, 0x84, 0x12, 0x64, 0x13, 0x8d, 0x00, 0x04, 0x9c, 0x02, 0x04);

        assertEquals(3, snes.cpu.executeInstruction());
        assertEquals(0xab, snes.wram.data()[0x10]);

        assertEquals(3, snes.cpu.executeInstruction());
        assertEquals(0xcd, snes.wram.data()[0x11]);

        assertEquals(3, snes.cpu.executeInstruction());
        assertEquals(0xef, snes.wram.data()[0x12]);

        assertEquals(3, snes.cpu.executeInstruction());
        assertEquals(0, snes.wram.data()[0x13]);

        assertEquals(4, snes.cpu.executeInstruction());
        assertEquals(0xab, snes.wram.data()[0x0400]);

        assertEquals(4, snes.cpu.executeInstruction());
        assertEquals(0, snes.wram.data()[0x0402]);
        assertEquals(0x020e, snes.cpu.registers().pc);
    }

    @Test
    void executesIndexedLoadStoreOpcodesWithCycleExtras() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().d = 0x0101;
        snes.cpu.registers().x = 0x02;
        snes.cpu.registers().y = 0x03;
        snes.wram.data()[0x0113] = 0x55;
        snes.wram.data()[0x0302] = 0x66;
        writeProgram(snes, 0x0200, 0xb5, 0x10, 0x95, 0x11, 0xb9, 0xff, 0x02);

        assertEquals(5, snes.cpu.executeInstruction());
        assertEquals(0x55, snes.cpu.registers().a);

        assertEquals(5, snes.cpu.executeInstruction());
        assertEquals(0x55, snes.wram.data()[0x0114]);

        assertEquals(5, snes.cpu.executeInstruction());
        assertEquals(0x66, snes.cpu.registers().a);
        assertEquals(0x0207, snes.cpu.registers().pc);
    }

    @Test
    void executesImmediateLogicalAndCompareOpcodes() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().a = 0x80;
        writeProgram(snes, 0x0200, 0x09, 0x0f, 0x29, 0x0f, 0x49, 0x0f, 0xc9, 0x00);

        assertEquals(2, snes.cpu.executeInstruction());
        assertEquals(0x8f, snes.cpu.registers().a);

        assertEquals(2, snes.cpu.executeInstruction());
        assertEquals(0x0f, snes.cpu.registers().a);

        assertEquals(2, snes.cpu.executeInstruction());
        assertEquals(0, snes.cpu.registers().a);
        assertTrue(snes.cpu.registers().p.z);

        assertEquals(2, snes.cpu.executeInstruction());
        assertTrue(snes.cpu.registers().p.z);
        assertTrue(snes.cpu.registers().p.c);
        assertEquals(0x0208, snes.cpu.registers().pc);
    }

    @Test
    void executesDirectAndAbsoluteMathOpcodes() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().d = 0x0101;
        snes.cpu.registers().a = 0x01;
        snes.cpu.registers().x = 0x22;
        snes.cpu.registers().y = 0x33;
        snes.wram.data()[0x0111] = 0x01;
        snes.wram.data()[0x0112] = 0x22;
        snes.wram.data()[0x0113] = 0x7f;
        snes.wram.data()[0x0400] = 0x01;
        snes.wram.data()[0x0401] = 0x40;
        snes.wram.data()[0x0402] = 0x81;
        writeProgram(snes, 0x0200, 0x65, 0x10, 0xed, 0x00, 0x04, 0xe4, 0x11, 0xcc, 0x01, 0x04, 0xe6, 0x12, 0xce, 0x02, 0x04);

        assertEquals(4, snes.cpu.executeInstruction());
        assertEquals(0x02, snes.cpu.registers().a);

        assertEquals(4, snes.cpu.executeInstruction());
        assertEquals(0, snes.cpu.registers().a);

        assertEquals(4, snes.cpu.executeInstruction());
        assertTrue(snes.cpu.registers().p.z);

        assertEquals(4, snes.cpu.executeInstruction());
        assertFalse(snes.cpu.registers().p.c);

        assertEquals(6, snes.cpu.executeInstruction());
        assertEquals(0x80, snes.wram.data()[0x0113]);

        assertEquals(6, snes.cpu.executeInstruction());
        assertEquals(0x80, snes.wram.data()[0x0402]);
        assertEquals(0x020f, snes.cpu.registers().pc);
    }

    @Test
    void executesBitTestAndMemoryBitOpcodes() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().a = 0x0f;
        snes.wram.data()[0x10] = 0xf0;
        snes.wram.data()[0x11] = 0x01;
        snes.wram.data()[0x12] = 0xff;
        snes.wram.data()[0x0400] = 0x40;
        writeProgram(snes, 0x0200, 0x89, 0x0f, 0x24, 0x10, 0x2c, 0x00, 0x04, 0x04, 0x11, 0x14, 0x12);

        assertEquals(2, snes.cpu.executeInstruction());
        assertFalse(snes.cpu.registers().p.z);

        assertEquals(3, snes.cpu.executeInstruction());
        assertTrue(snes.cpu.registers().p.z);
        assertTrue(snes.cpu.registers().p.n);

        assertEquals(4, snes.cpu.executeInstruction());
        assertTrue(snes.cpu.registers().p.v);

        assertEquals(5, snes.cpu.executeInstruction());
        assertEquals(0x0f, snes.wram.data()[0x11]);

        assertEquals(5, snes.cpu.executeInstruction());
        assertEquals(0xf0, snes.wram.data()[0x12]);
        assertEquals(0x020b, snes.cpu.registers().pc);
    }

    @Test
    void executesMemoryShiftRotateOpcodesWithCycleExtras() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().d = 0x0101;
        snes.cpu.registers().x = 1;
        snes.wram.data()[0x0111] = 0x80;
        snes.wram.data()[0x0112] = 0x40;
        snes.wram.data()[0x0400] = 0x03;
        snes.wram.data()[0x0500] = 0x02;
        writeProgram(snes, 0x0200, 0x06, 0x10, 0x26, 0x11, 0x4e, 0x00, 0x04, 0x7e, 0xff, 0x04);

        assertEquals(6, snes.cpu.executeInstruction());
        assertEquals(0, snes.wram.data()[0x0111]);
        assertTrue(snes.cpu.registers().p.c);

        assertEquals(6, snes.cpu.executeInstruction());
        assertEquals(0x81, snes.wram.data()[0x0112]);
        assertFalse(snes.cpu.registers().p.c);

        assertEquals(6, snes.cpu.executeInstruction());
        assertEquals(0x01, snes.wram.data()[0x0400]);
        assertTrue(snes.cpu.registers().p.c);

        assertEquals(8, snes.cpu.executeInstruction());
        assertEquals(0x81, snes.wram.data()[0x0500]);
        assertEquals(0x020a, snes.cpu.registers().pc);
    }

    @Test
    void executesIndirectLoadAndArithmeticOpcodes() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().d = 0x0100;
        snes.cpu.registers().s = 0x0300;
        snes.cpu.registers().x = 0x02;
        snes.cpu.registers().y = 0x03;
        snes.wram.data()[0x0112] = 0x00;
        snes.wram.data()[0x0113] = 0x04;
        snes.wram.data()[0x0120] = 0xfd;
        snes.wram.data()[0x0121] = 0x04;
        snes.wram.data()[0x0130] = 0x01;
        snes.wram.data()[0x0131] = 0x04;
        snes.wram.data()[0x0400] = 0x10;
        snes.wram.data()[0x0500] = 0x01;
        snes.wram.data()[0x0401] = 0x0f;
        snes.wram.data()[0x0310] = 0x00;
        snes.wram.data()[0x0311] = 0x04;
        snes.wram.data()[0x0403] = 0x02;
        writeProgram(snes, 0x0200, 0xa1, 0x10, 0x11, 0x20, 0x32, 0x30, 0x73, 0x10);

        assertEquals(6, snes.cpu.executeInstruction());
        assertEquals(0x10, snes.cpu.registers().a);

        assertEquals(6, snes.cpu.executeInstruction());
        assertEquals(0x11, snes.cpu.registers().a);

        assertEquals(5, snes.cpu.executeInstruction());
        assertEquals(0x01, snes.cpu.registers().a);

        assertEquals(7, snes.cpu.executeInstruction());
        assertEquals(0x03, snes.cpu.registers().a);
        assertEquals(0x0208, snes.cpu.registers().pc);
    }

    @Test
    void executesIndirectStoreOpcodes() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().d = 0x0100;
        snes.cpu.registers().s = 0x0300;
        snes.cpu.registers().x = 0x02;
        snes.cpu.registers().y = 0x03;
        snes.cpu.registers().a = 0x77;
        snes.wram.data()[0x0112] = 0x00;
        snes.wram.data()[0x0113] = 0x04;
        snes.wram.data()[0x0120] = 0x10;
        snes.wram.data()[0x0121] = 0x04;
        snes.wram.data()[0x0130] = 0x20;
        snes.wram.data()[0x0131] = 0x04;
        snes.wram.data()[0x0140] = 0x20;
        snes.wram.data()[0x0141] = 0x05;
        snes.wram.data()[0x0306] = 0x00;
        snes.wram.data()[0x0307] = 0x05;
        writeProgram(snes, 0x0200, 0x81, 0x10, 0x83, 0x05, 0x91, 0x20, 0x92, 0x30, 0x93, 0x06, 0x97, 0x40);

        assertEquals(6, snes.cpu.executeInstruction());
        assertEquals(0x77, snes.wram.data()[0x0400]);

        assertEquals(4, snes.cpu.executeInstruction());
        assertEquals(0x77, snes.wram.data()[0x0305]);

        assertEquals(6, snes.cpu.executeInstruction());
        assertEquals(0x77, snes.wram.data()[0x0413]);

        assertEquals(5, snes.cpu.executeInstruction());
        assertEquals(0x77, snes.wram.data()[0x0420]);

        assertEquals(7, snes.cpu.executeInstruction());
        assertEquals(0x77, snes.wram.data()[0x0503]);

        assertEquals(6, snes.cpu.executeInstruction());
        assertEquals(0x77, snes.wram.data()[0x0523]);
        assertEquals(0x020c, snes.cpu.registers().pc);
    }

    @Test
    void executesIndirectCompareAndSubtractOpcodes() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().d = 0x0100;
        snes.cpu.registers().s = 0x0300;
        snes.cpu.registers().x = 0x02;
        snes.cpu.registers().y = 0x03;
        snes.cpu.registers().a = 0x05;
        snes.wram.data()[0x0112] = 0x00;
        snes.wram.data()[0x0113] = 0x04;
        snes.wram.data()[0x0120] = 0xfd;
        snes.wram.data()[0x0121] = 0x04;
        snes.wram.data()[0x0400] = 0x05;
        snes.wram.data()[0x0500] = 0x06;
        snes.wram.data()[0x0305] = 0x01;
        writeProgram(snes, 0x0200, 0xc1, 0x10, 0xd1, 0x20, 0xe3, 0x05);

        assertEquals(6, snes.cpu.executeInstruction());
        assertTrue(snes.cpu.registers().p.z);
        assertTrue(snes.cpu.registers().p.c);

        assertEquals(6, snes.cpu.executeInstruction());
        assertFalse(snes.cpu.registers().p.c);

        snes.cpu.registers().p.c = true;
        assertEquals(4, snes.cpu.executeInstruction());
        assertEquals(0x04, snes.cpu.registers().a);
        assertEquals(0x0206, snes.cpu.registers().pc);
    }

    @Test
    void executesStackRelativeOpcodeWithWrappedAddress() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().s = 0xffff;
        snes.wram.data()[0x0001] = 0x42;
        writeProgram(snes, 0x0200, 0xa3, 0x02);

        assertEquals(4, snes.cpu.executeInstruction());
        assertEquals(0x42, snes.cpu.registers().a);
        assertEquals(0x0202, snes.cpu.registers().pc);
    }

    @Test
    void executesBlockMoveOpcodes() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().a = 1;
        snes.cpu.registers().x = 0x0400;
        snes.cpu.registers().y = 0x0500;
        snes.wram.data()[0x0400] = 0x12;
        snes.wram.data()[0x0401] = 0x34;
        writeProgram(snes, 0x0200, 0x54, 0x00, 0x00);

        assertEquals(14, snes.cpu.executeInstruction());
        assertEquals(0x12, snes.wram.data()[0x0500]);
        assertEquals(0x34, snes.wram.data()[0x0501]);
        assertEquals(0xffff, snes.cpu.registers().a);
        assertEquals(0x0402, snes.cpu.registers().x);
        assertEquals(0x0502, snes.cpu.registers().y);

        snes.cpu.registers().setPc(0x0210);
        snes.cpu.registers().a = 1;
        snes.cpu.registers().x = 0x0401;
        snes.cpu.registers().y = 0x0501;
        snes.wram.data()[0x0400] = 0x56;
        snes.wram.data()[0x0401] = 0x78;
        writeProgram(snes, 0x0210, 0x44, 0x00, 0x00);

        assertEquals(14, snes.cpu.executeInstruction());
        assertEquals(0x56, snes.wram.data()[0x0500]);
        assertEquals(0x78, snes.wram.data()[0x0501]);
        assertEquals(0xffff, snes.cpu.registers().a);
        assertEquals(0x03ff, snes.cpu.registers().x);
        assertEquals(0x04ff, snes.cpu.registers().y);
    }

    @Test
    void executesRegisterTransferOpcodes() {
        SNES snes = init();
        snes.cpu.setEmulationMode(false);
        snes.cpu.registers().p.m = false;
        snes.cpu.registers().p.x_b = false;
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().a = 0x8123;
        writeProgram(snes, 0x0200, 0xaa, 0x9b, 0xbb, 0x8a, 0xa8, 0x98, 0x1b, 0x3b, 0x5b, 0x7b, 0xba);

        assertEquals(2, snes.cpu.executeInstruction());
        assertEquals(0x8123, snes.cpu.registers().x);

        assertEquals(2, snes.cpu.executeInstruction());
        assertEquals(0x8123, snes.cpu.registers().y);

        assertEquals(2, snes.cpu.executeInstruction());
        assertEquals(0x8123, snes.cpu.registers().x);

        assertEquals(2, snes.cpu.executeInstruction());
        assertEquals(0x8123, snes.cpu.registers().a);

        assertEquals(2, snes.cpu.executeInstruction());
        assertEquals(0x8123, snes.cpu.registers().y);

        assertEquals(2, snes.cpu.executeInstruction());
        assertEquals(0x8123, snes.cpu.registers().a);

        assertEquals(2, snes.cpu.executeInstruction());
        assertEquals(0x8123, snes.cpu.registers().s);

        assertEquals(2, snes.cpu.executeInstruction());
        assertEquals(0x8123, snes.cpu.registers().a);

        assertEquals(2, snes.cpu.executeInstruction());
        assertEquals(0x8123, snes.cpu.registers().d);

        assertEquals(2, snes.cpu.executeInstruction());
        assertEquals(0x8123, snes.cpu.registers().a);

        assertEquals(2, snes.cpu.executeInstruction());
        assertEquals(0x8123, snes.cpu.registers().x);
        assertEquals(0x020b, snes.cpu.registers().pc);
    }

    @Test
    void executesIndexUpdateAndXbaOpcodes() {
        SNES snes = init();
        snes.cpu.registers().p.x_b = true;
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().x = 0x00ff;
        snes.cpu.registers().y = 0x0001;
        snes.cpu.registers().a = 0x8012;
        writeProgram(snes, 0x0200, 0xe8, 0xc8, 0xca, 0x88, 0xeb);

        assertEquals(2, snes.cpu.executeInstruction());
        assertEquals(0, snes.cpu.registers().x);
        assertTrue(snes.cpu.registers().p.z);

        assertEquals(2, snes.cpu.executeInstruction());
        assertEquals(2, snes.cpu.registers().y);

        assertEquals(2, snes.cpu.executeInstruction());
        assertEquals(0xff, snes.cpu.registers().x);
        assertTrue(snes.cpu.registers().p.n);

        assertEquals(2, snes.cpu.executeInstruction());
        assertEquals(1, snes.cpu.registers().y);

        assertEquals(3, snes.cpu.executeInstruction());
        assertEquals(0x1280, snes.cpu.registers().a);
        assertTrue(snes.cpu.registers().p.n);
        assertEquals(0x0205, snes.cpu.registers().pc);
    }

    @Test
    void executesAccumulatorShiftRotateOpcodes() {
        SNES snes = init();
        snes.cpu.registers().p.m = true;
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().a = 0x81;
        writeProgram(snes, 0x0200, 0x0a, 0x2a, 0x4a, 0x6a);

        assertEquals(2, snes.cpu.executeInstruction());
        assertEquals(0x02, snes.cpu.registers().al());
        assertTrue(snes.cpu.registers().p.c);

        assertEquals(2, snes.cpu.executeInstruction());
        assertEquals(0x05, snes.cpu.registers().al());

        assertEquals(2, snes.cpu.executeInstruction());
        assertEquals(0x02, snes.cpu.registers().al());
        assertTrue(snes.cpu.registers().p.c);

        assertEquals(2, snes.cpu.executeInstruction());
        assertEquals(0x81, snes.cpu.registers().al());
        assertTrue(snes.cpu.registers().p.n);
        assertEquals(0x0204, snes.cpu.registers().pc);
    }

    @Test
    void executesAccumulatorIncrementDecrementOpcodes() {
        SNES snes = init();
        snes.cpu.registers().p.m = false;
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().a = 0xffff;
        writeProgram(snes, 0x0200, 0x1a, 0x3a);

        assertEquals(2, snes.cpu.executeInstruction());
        assertEquals(0, snes.cpu.registers().a);
        assertTrue(snes.cpu.registers().p.z);

        assertEquals(2, snes.cpu.executeInstruction());
        assertEquals(0xffff, snes.cpu.registers().a);
        assertTrue(snes.cpu.registers().p.n);
        assertEquals(0x0202, snes.cpu.registers().pc);
    }

    @Test
    void executesPeiOpcodeFromFetchedDirectAddress() {
        SNES snes = init();
        snes.cpu.registers().setPc(0x0200);
        snes.cpu.registers().d = 0x0100;
        snes.cpu.registers().s = 0x1fff;
        snes.wram.data()[0x0120] = 0xcd;
        snes.wram.data()[0x0121] = 0xab;
        writeProgram(snes, 0x0200, 0xd4, 0x20);

        assertEquals(6, snes.cpu.executeInstruction());

        assertEquals(0x0202, snes.cpu.registers().pc);
        assertEquals(0x1ffd, snes.cpu.registers().s);
        assertEquals(0xabcd, snes.cpu._pop16());
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
