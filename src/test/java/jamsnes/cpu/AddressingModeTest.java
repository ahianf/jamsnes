package jamsnes.cpu;

import jamsnes.SNES;
import jamsnes.cartridge.Header;
import jamsnes.cartridge.MappingMode;
import jamsnes.memory.IMemory;
import jamsnes.memory.IMemoryBus;
import jamsnes.renderer.TestFrontend;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddressingModeTest {
    @Test
    void immediateForAUsesMemoryFlag() {
        SNES snes = init();
        snes.cpu.registers().setPac(0x000015);
        snes.cpu.registers().p.m = false;

        assertEquals(0x000015, snes.cpu._getImmediateAddrForA());
        assertEquals(0x000017, snes.cpu.registers().pac);
    }

    @Test
    void immediateForAWrapsWithinProgramBank() {
        SNES snes = init();
        snes.cpu.registers().setPac(0x00ffff);
        snes.cpu.registers().p.m = true;

        assertEquals(0x00ffff, snes.cpu._getImmediateAddrForA());
        assertEquals(0x000000, snes.cpu.registers().pac);
    }

    @Test
    void direct() {
        SNES snes = init();
        snes.cartridge.data()[0] = 0x15;
        snes.cpu.registers().setPac(0x808000);
        snes.cpu.registers().d = 0x1000;

        assertEquals(0x1015, snes.cpu._getDirectAddr());
        assertEquals(0x808001, snes.cpu.registers().pac);
    }

    @Test
    void directWordReadsWrapWithinBankZeroWhileAbsoluteReadsCrossBanks() {
        SparseBus bus = new SparseBus();
        CPU cpu = new CPU(bus, new Header());
        cpu.setEmulationMode(false);
        cpu.registers().p.m = false;
        cpu.registers().d = 0xffff;
        cpu.registers().setPac(0x008000);
        bus.write(0x008000, 0xa5);
        bus.write(0x008001, 0x00);
        bus.write(0x00ffff, 0x34);
        bus.write(0x000000, 0x12);
        bus.write(0x010000, 0x56);

        assertEquals(5, cpu.executeInstruction());
        assertEquals(0x1234, cpu.registers().a);

        cpu.registers().setPac(0x008010);
        bus.write(0x008010, 0xad);
        bus.write(0x008011, 0xff);
        bus.write(0x008012, 0xff);

        assertEquals(5, cpu.executeInstruction());
        assertEquals(0x5634, cpu.registers().a);
    }

    @Test
    void directIndexedAndStackRelativeWordReadsWrapWithinBankZero() {
        SparseBus bus = new SparseBus();
        CPU cpu = new CPU(bus, new Header());
        cpu.setEmulationMode(false);
        cpu.registers().p.m = false;
        cpu.registers().p.x_b = false;
        cpu.registers().d = 0xfffe;
        cpu.registers().x = 0x0001;
        cpu.registers().setPac(0x008000);
        bus.write(0x008000, 0xb5);
        bus.write(0x008001, 0x00);
        bus.write(0x00ffff, 0x34);
        bus.write(0x000000, 0x12);
        bus.write(0x010000, 0x56);

        assertEquals(6, cpu.executeInstruction());
        assertEquals(0x1234, cpu.registers().a);

        cpu.registers().s = 0xffff;
        cpu.registers().setPac(0x008010);
        bus.write(0x008010, 0xa3);
        bus.write(0x008011, 0x00);

        assertEquals(5, cpu.executeInstruction());
        assertEquals(0x1234, cpu.registers().a);
    }

    @Test
    void directWordStoresWrapWithinBankZero() {
        SparseBus bus = new SparseBus();
        CPU cpu = new CPU(bus, new Header());
        cpu.setEmulationMode(false);
        cpu.registers().p.m = false;
        cpu.registers().a = 0xabcd;
        cpu.registers().d = 0xffff;
        cpu.registers().setPac(0x008000);
        bus.write(0x008000, 0x85);
        bus.write(0x008001, 0x00);
        bus.write(0x010000, 0x7e);

        assertEquals(5, cpu.executeInstruction());
        assertEquals(0xcd, bus.read(0x00ffff));
        assertEquals(0xab, bus.read(0x000000));
        assertEquals(0x7e, bus.read(0x010000));
    }

    @Test
    void directWordReadModifyWriteWrapsWithinBankZero() {
        SparseBus bus = new SparseBus();
        CPU cpu = new CPU(bus, new Header());
        cpu.setEmulationMode(false);
        cpu.registers().p.m = false;
        cpu.registers().d = 0xffff;
        cpu.registers().setPac(0x008000);
        bus.write(0x008000, 0xe6);
        bus.write(0x008001, 0x00);
        bus.write(0x00ffff, 0xff);
        bus.write(0x000000, 0x00);
        bus.write(0x010000, 0x7e);

        assertEquals(8, cpu.executeInstruction());
        assertEquals(0x00, bus.read(0x00ffff));
        assertEquals(0x01, bus.read(0x000000));
        assertEquals(0x7e, bus.read(0x010000));
    }

    @Test
    void absolute() {
        SNES snes = init();
        snes.cartridge.data()[0] = 0x1c;
        snes.cartridge.data()[1] = 0x90;
        snes.cpu.registers().setPac(0x808000);
        snes.cpu.registers().dbr = 0x88;

        assertEquals(0x88901c, snes.cpu._getAbsoluteAddr());
        assertEquals(0x808002, snes.cpu.registers().pac);
    }

    @Test
    void absoluteLong() {
        SNES snes = init();
        snes.cartridge.data()[0] = 0x1c;
        snes.cartridge.data()[1] = 0x90;
        snes.cartridge.data()[2] = 0xff;
        snes.cpu.registers().setPac(0x808000);

        assertEquals(0xff901c, snes.cpu._getAbsoluteLongAddr());
        assertEquals(0x808003, snes.cpu.registers().pac);
    }

    @Test
    void directIndirectIndexedY() {
        SNES snes = init();
        snes.cartridge.data()[0] = 0x10;
        snes.wram.data()[0x1010] = 0x30;
        snes.wram.data()[0x1011] = 0x40;
        snes.cpu.registers().setPac(0x808000);
        snes.cpu.registers().dbr = 0x80;
        snes.cpu.registers().y = 0x0001;
        snes.cpu.registers().d = 0x1000;

        assertEquals(0x804031, snes.cpu._getDirectIndirectIndexedYAddr());
        assertEquals(0x808001, snes.cpu.registers().pac);
    }

    @Test
    void directIndirectIndexedYWrapsDirectPagePointerBytes() {
        SNES snes = init();
        snes.cartridge.setSize(0x8000);
        snes.cartridge.data()[0] = 0x00;
        snes.cartridge.data()[0x7fff] = 0xef;
        snes.wram.data()[0x0000] = 0x01;
        snes.cpu.registers().setPac(0x808000);
        snes.cpu.registers().dbr = 0x88;
        snes.cpu.registers().y = 0x0002;
        snes.cpu.registers().d = 0xffff;

        assertEquals(0x8801f1, snes.cpu._getDirectIndirectIndexedYAddr());
        assertEquals(0x808001, snes.cpu.registers().pac);
    }

    @Test
    void directIndirectIndexedYTracksPageBoundaryCrossing() {
        SNES snes = init();
        snes.cartridge.data()[0] = 0x10;
        snes.wram.data()[0x1010] = 0xf0;
        snes.wram.data()[0x1011] = 0x12;
        snes.cpu.registers().setPac(0x808000);
        snes.cpu.registers().dbr = 0x80;
        snes.cpu.registers().y = 0x0010;
        snes.cpu.registers().d = 0x1000;

        assertEquals(0x801300, snes.cpu._getDirectIndirectIndexedYAddr());
        assertTrue(snes.cpu.hasIndexCrossedPageBoundary());
    }

    @Test
    void directIndirectIndexedYLong() {
        SNES snes = init();
        snes.cpu.registers().setPac(0x808000);
        snes.cpu.registers().d = 0x1000;
        snes.cpu.registers().y = 0x0005;
        snes.cartridge.data()[0] = 0x10;
        snes.wram.data()[0x1010] = 0x30;
        snes.wram.data()[0x1011] = 0x40;
        snes.wram.data()[0x1012] = 0x23;

        assertEquals(0x234035, snes.cpu._getDirectIndirectIndexedYLongAddr());
        assertEquals(0x808001, snes.cpu.registers().pac);
    }

    @Test
    void directIndirectIndexedYLongWrapsDirectPagePointerBytes() {
        SNES snes = init();
        snes.cartridge.setSize(0x8000);
        snes.cpu.registers().setPac(0x808000);
        snes.cpu.registers().d = 0xffff;
        snes.cartridge.data()[0] = 0x00;
        snes.cartridge.data()[0x7fff] = 0xef;
        snes.wram.data()[0x0000] = 0x01;
        snes.wram.data()[0x0001] = 0x88;

        assertEquals(0x8801ef, snes.cpu._getDirectIndirectIndexedYLongAddr());
        assertEquals(0x808001, snes.cpu.registers().pac);
    }

    @Test
    void directIndirectIndexedYLongAddsIndexAcrossBankBoundary() {
        SNES snes = init();
        snes.cpu.registers().setPac(0x808000);
        snes.cpu.registers().d = 0x1000;
        snes.cpu.registers().y = 0x0020;
        snes.cartridge.data()[0] = 0x10;
        snes.wram.data()[0x1010] = 0xf0;
        snes.wram.data()[0x1011] = 0xff;
        snes.wram.data()[0x1012] = 0x23;

        assertEquals(0x240010, snes.cpu._getDirectIndirectIndexedYLongAddr());
        assertEquals(0x808001, snes.cpu.registers().pac);
    }

    @Test
    void directIndexedIndirectX() {
        SNES snes = init();
        snes.cartridge.data()[0] = 0x10;
        snes.cpu.registers().d = 0x1000;
        snes.cpu.registers().x = 0x0002;
        snes.wram.data()[0x1012] = 0x30;
        snes.wram.data()[0x1013] = 0x40;
        snes.cpu.registers().dbr = 0x80;
        snes.cpu.registers().setPac(0x808000);

        assertEquals(0x804030, snes.cpu._getDirectIndirectIndexedXAddr());
        assertEquals(0x808001, snes.cpu.registers().pac);
    }

    @Test
    void directIndexedIndirectXWrapsPointerWithinAlignedDirectPageInEmulationMode() {
        SNES snes = init();
        snes.cartridge.setSize(0x8000);
        snes.cartridge.data()[0] = 0xfe;
        snes.cpu.registers().d = 0xff00;
        snes.cpu.registers().x = 0x0001;
        snes.cartridge.data()[0x7fff] = 0xef;
        snes.cartridge.data()[0x7f00] = 0x01;
        snes.wram.data()[0x0000] = 0x56;
        snes.cpu.registers().dbr = 0x88;
        snes.cpu.registers().setPac(0x808000);

        assertEquals(0x8801ef, snes.cpu._getDirectIndirectIndexedXAddr());
        assertEquals(0x808001, snes.cpu.registers().pac);
    }

    @Test
    void directIndexedByX() {
        SNES snes = init();
        snes.cartridge.data()[0] = 0x10;
        snes.cpu.registers().d = 0x1000;
        snes.cpu.registers().x = 0x0002;
        snes.cpu.registers().setPac(0x808000);

        assertEquals(0x1012, snes.cpu._getDirectIndexedByXAddr());
        assertEquals(0x808001, snes.cpu.registers().pac);
    }

    @Test
    void directIndexedByXUsesEightBitIndexWidth() {
        SNES snes = init();
        snes.cpu.registers().p.x_b = true;
        snes.cartridge.data()[0] = 0x10;
        snes.cpu.registers().d = 0x1000;
        snes.cpu.registers().x = 0x1202;
        snes.cpu.registers().setPac(0x808000);

        assertEquals(0x1012, snes.cpu._getDirectIndexedByXAddr());
        assertEquals(0x808001, snes.cpu.registers().pac);
    }

    @Test
    void directIndexedByY() {
        SNES snes = init();
        snes.cartridge.data()[0] = 0x10;
        snes.cpu.registers().d = 0x1000;
        snes.cpu.registers().y = 0x0002;
        snes.cpu.registers().setPac(0x808000);

        assertEquals(0x1012, snes.cpu._getDirectIndexedByYAddr());
        assertEquals(0x808001, snes.cpu.registers().pac);
    }

    @Test
    void directIndexedAddressesWrapWithinAlignedPageInEmulationMode() {
        SNES snes = init();
        snes.cpu.setEmulationMode(true);
        snes.cpu.registers().d = 0x1200;
        snes.cpu.registers().x = 0x02;
        snes.cpu.registers().y = 0x03;
        snes.cartridge.data()[0] = 0xff;

        snes.cpu.registers().setPac(0x808000);
        assertEquals(0x1201, snes.cpu._getDirectIndexedByXAddr());

        snes.cpu.registers().setPac(0x808000);
        assertEquals(0x1202, snes.cpu._getDirectIndexedByYAddr());
    }

    @Test
    void directIndexedAddressesCarryAcrossPagesInNativeMode() {
        SNES snes = init();
        snes.cpu.setEmulationMode(false);
        snes.cpu.registers().d = 0x1200;
        snes.cpu.registers().x = 0x0002;
        snes.cpu.registers().y = 0x0003;
        snes.cartridge.data()[0] = 0xff;

        snes.cpu.registers().setPac(0x808000);
        assertEquals(0x1301, snes.cpu._getDirectIndexedByXAddr());

        snes.cpu.registers().setPac(0x808000);
        assertEquals(0x1302, snes.cpu._getDirectIndexedByYAddr());
    }

    @Test
    void directIndexedIndirectXUsesEightBitIndexWidth() {
        SNES snes = init();
        snes.cartridge.data()[0] = 0xfe;
        snes.cpu.registers().p.x_b = true;
        snes.cpu.registers().d = 0x0100;
        snes.cpu.registers().x = 0x1201;
        snes.cpu.registers().dbr = 0x80;
        snes.cpu.registers().setPac(0x808000);
        snes.wram.data()[0x01ff] = 0x34;
        snes.wram.data()[0x0100] = 0x12;
        snes.wram.data()[0x0200] = 0x56;
        snes.wram.data()[0x13ff] = 0xaa;
        snes.wram.data()[0x1400] = 0xbb;

        assertEquals(0x801234, snes.cpu._getDirectIndirectIndexedXAddr());
        assertEquals(0x808001, snes.cpu.registers().pac);
    }

    @Test
    void absoluteIndexedByX() {
        SNES snes = init();
        snes.cpu.registers().setPac(0x808000);
        snes.cartridge.data()[0] = 0x10;
        snes.cartridge.data()[1] = 0xac;
        snes.cpu.registers().dbr = 0xef;
        snes.cpu.registers().x = 0x0005;

        assertEquals(0xefac15, snes.cpu._getAbsoluteIndexedByXAddr());
        assertEquals(0x808002, snes.cpu.registers().pac);
    }

    @Test
    void absoluteIndexedByXDoesNotTrackPageBoundaryWhenIndexStaysOnPage() {
        SNES snes = init();
        snes.cpu.registers().setPac(0x808000);
        snes.cartridge.data()[0] = 0xf0;
        snes.cartridge.data()[1] = 0x12;
        snes.cpu.registers().dbr = 0x80;
        snes.cpu.registers().x = 0x000f;

        assertEquals(0x8012ff, snes.cpu._getAbsoluteIndexedByXAddr());
        assertFalse(snes.cpu.hasIndexCrossedPageBoundary());
    }

    @Test
    void absoluteIndexedByXTracksPageBoundaryCrossing() {
        SNES snes = init();
        snes.cpu.registers().setPac(0x808000);
        snes.cartridge.data()[0] = 0xf0;
        snes.cartridge.data()[1] = 0x12;
        snes.cpu.registers().dbr = 0x80;
        snes.cpu.registers().x = 0x0010;

        assertEquals(0x801300, snes.cpu._getAbsoluteIndexedByXAddr());
        assertTrue(snes.cpu.hasIndexCrossedPageBoundary());
    }

    @Test
    void absoluteIndexedByYUsesEightBitIndexWidthForAddressAndBoundary() {
        SNES snes = init();
        snes.cpu.registers().setPac(0x808000);
        snes.cartridge.data()[0] = 0xf0;
        snes.cartridge.data()[1] = 0x12;
        snes.cpu.registers().p.x_b = true;
        snes.cpu.registers().dbr = 0x80;
        snes.cpu.registers().y = 0x120f;

        assertEquals(0x8012ff, snes.cpu._getAbsoluteIndexedByYAddr());
        assertFalse(snes.cpu.hasIndexCrossedPageBoundary());
    }

    @Test
    void absoluteIndexedByY() {
        SNES snes = init();
        snes.cpu.registers().setPac(0x808000);
        snes.cartridge.data()[0] = 0x10;
        snes.cartridge.data()[1] = 0xac;
        snes.cpu.registers().dbr = 0xef;
        snes.cpu.registers().y = 0x0005;

        assertEquals(0xefac15, snes.cpu._getAbsoluteIndexedByYAddr());
        assertEquals(0x808002, snes.cpu.registers().pac);
    }

    @Test
    void absoluteIndexedAddressesCrossDataBankBoundary() {
        SNES snes = init();
        snes.cpu.registers().dbr = 0x7e;
        snes.cpu.registers().x = 1;
        snes.cpu.registers().y = 1;
        snes.cartridge.data()[0] = 0xff;
        snes.cartridge.data()[1] = 0xff;

        snes.cpu.registers().setPac(0x808000);
        assertEquals(0x7f0000, snes.cpu._getAbsoluteIndexedByXAddr());
        assertTrue(snes.cpu.hasIndexCrossedPageBoundary());

        snes.cpu.registers().setPac(0x808000);
        assertEquals(0x7f0000, snes.cpu._getAbsoluteIndexedByYAddr());
        assertTrue(snes.cpu.hasIndexCrossedPageBoundary());
    }

    @Test
    void absoluteIndexedByXLong() {
        SNES snes = init();
        snes.cpu.registers().setPac(0x808000);
        snes.cartridge.data()[0] = 0x10;
        snes.cartridge.data()[1] = 0xac;
        snes.cartridge.data()[2] = 0xef;
        snes.cpu.registers().x = 0x0005;

        assertEquals(0xefac15, snes.cpu._getAbsoluteIndexedByXLongAddr());
        assertEquals(0x808003, snes.cpu.registers().pac);
    }

    @Test
    void absoluteIndirect() {
        SNES snes = init();
        snes.cpu.registers().setPac(0x808000);
        snes.cartridge.data()[0] = 0xab;
        snes.cartridge.data()[1] = 0x01;
        snes.wram.data()[0x01ab] = 0xef;
        snes.wram.data()[0x01ac] = 0x01;

        assertEquals(0x01ef, snes.cpu._getAbsoluteIndirectAddr());
        assertEquals(0x808002, snes.cpu.registers().pac);
    }

    @Test
    void absoluteIndirectPointersWrapWithinBankZero() {
        SparseBus bus = new SparseBus();
        CPU cpu = new CPU(bus, new Header());
        cpu.registers().setPac(0x008000);
        bus.write(0x008000, 0xff);
        bus.write(0x008001, 0xff);
        bus.write(0x00ffff, 0x34);
        bus.write(0x000000, 0x12);
        bus.write(0x000001, 0x9a);
        bus.write(0x010000, 0x56);
        bus.write(0x010001, 0x78);

        assertEquals(0x1234, cpu._getAbsoluteIndirectAddr());

        cpu.registers().setPac(0x008000);
        assertEquals(0x9a1234, cpu._getAbsoluteIndirectLongAddr());
    }

    @Test
    void peiPointerWrapsWithinBankZero() {
        SparseBus bus = new SparseBus();
        CPU cpu = new CPU(bus, new Header());
        cpu.setEmulationMode(false);
        cpu.registers().s = 0x0200;
        bus.write(0x00ffff, 0x34);
        bus.write(0x000000, 0x12);
        bus.write(0x010000, 0x56);

        assertEquals(0, cpu.PEI(0xffff));
        assertEquals(0x01fe, cpu.registers().s);
        assertEquals(0x1234, cpu._pop16());
    }

    @Test
    void absoluteIndexedIndirect() {
        SNES snes = init();
        snes.cpu.registers().setPac(0x7f0200);
        snes.wram.data()[0x10200] = 0xab;
        snes.wram.data()[0x10201] = 0x01;
        snes.cpu.registers().x = 2;
        snes.wram.data()[0x101ad] = 0xef;
        snes.wram.data()[0x101ae] = 0x01;

        assertEquals(0x01ef, snes.cpu._getAbsoluteIndirectIndexedByXAddr());
        assertEquals(0x7f0202, snes.cpu.registers().pac);
    }

    @Test
    void absoluteIndexedIndirectWrapsPointerWithinProgramBank() {
        SNES snes = init();
        snes.cpu.registers().setPac(0x7f0200);
        snes.wram.data()[0x10200] = 0xff;
        snes.wram.data()[0x10201] = 0xff;
        snes.wram.data()[0x1ffff] = 0xef;
        snes.wram.data()[0x10000] = 0x01;

        assertEquals(0x01ef, snes.cpu._getAbsoluteIndirectIndexedByXAddr());
        assertEquals(0x7f0202, snes.cpu.registers().pac);
    }

    @Test
    void directIndirect() {
        SNES snes = init();
        snes.cpu.registers().setPac(0x808000);
        snes.cartridge.data()[0] = 0x01;
        snes.cpu.registers().d = 0x1010;
        snes.wram.data()[0x1011] = 0xef;
        snes.wram.data()[0x1012] = 0x01;
        snes.cpu.registers().dbr = 0x88;

        assertEquals(0x8801ef, snes.cpu._getDirectIndirectAddr());
        assertEquals(0x808001, snes.cpu.registers().pac);
    }

    @Test
    void directIndirectWrapsDirectPagePointerBytes() {
        SNES snes = init();
        snes.cartridge.setSize(0x8000);
        snes.cpu.registers().setPac(0x808000);
        snes.cartridge.data()[0] = 0x00;
        snes.cpu.registers().d = 0xffff;
        snes.cartridge.data()[0x7fff] = 0xef;
        snes.wram.data()[0x0000] = 0x01;
        snes.cpu.registers().dbr = 0x88;

        assertEquals(0x8801ef, snes.cpu._getDirectIndirectAddr());
        assertEquals(0x808001, snes.cpu.registers().pac);
    }

    @Test
    void directIndirectPointersWrapWithinAlignedPageInEmulationMode() {
        SparseBus bus = new SparseBus();
        CPU cpu = new CPU(bus, new Header());
        cpu.setEmulationMode(true);
        cpu.registers().setPac(0x008000);
        cpu.registers().d = 0x1200;
        cpu.registers().dbr = 0x34;
        bus.write(0x008000, 0xff);
        bus.write(0x0012ff, 0x78);
        bus.write(0x001200, 0x56);
        bus.write(0x001300, 0x9a);

        assertEquals(0x345678, cpu._getDirectIndirectAddr());
    }

    @Test
    void directIndirectLongPointersCanCrossAlignedPageInEmulationMode() {
        SparseBus bus = new SparseBus();
        CPU cpu = new CPU(bus, new Header());
        cpu.setEmulationMode(true);
        cpu.registers().setPac(0x008000);
        cpu.registers().d = 0x1200;
        bus.write(0x008000, 0xff);
        bus.write(0x0012ff, 0x78);
        bus.write(0x001300, 0x56);
        bus.write(0x001301, 0x34);
        bus.write(0x001200, 0xab);

        assertEquals(0x345678, cpu._getDirectIndirectLongAddr());
    }

    @Test
    void directIndirectLong() {
        SNES snes = init();
        snes.cpu.registers().setPac(0x808000);
        snes.cartridge.data()[0] = 0x06;
        snes.cpu.registers().d = 0x1010;
        snes.wram.data()[0x1016] = 0xef;
        snes.wram.data()[0x1017] = 0x01;
        snes.wram.data()[0x1018] = 0x88;

        assertEquals(0x8801ef, snes.cpu._getDirectIndirectLongAddr());
        assertEquals(0x808001, snes.cpu.registers().pac);
    }

    @Test
    void stackRelative() {
        SNES snes = init();
        snes.cpu.setEmulationMode(false);
        snes.cpu.registers().setPac(0x808000);
        snes.cartridge.data()[0] = 0x06;
        snes.cpu.registers().s = 0x1010;

        assertEquals(0x1016, snes.cpu._getStackRelativeAddr());
        assertEquals(0x808001, snes.cpu.registers().pac);
    }

    @Test
    void stackRelativeWrapsAtSixteenBits() {
        SNES snes = init();
        snes.cpu.setEmulationMode(false);
        snes.cpu.registers().setPac(0x808000);
        snes.cartridge.data()[0] = 0x02;
        snes.cpu.registers().s = 0xffff;

        assertEquals(0x0001, snes.cpu._getStackRelativeAddr());
        assertEquals(0x808001, snes.cpu.registers().pac);
    }

    @Test
    void stackRelativeIndirectIndexedY() {
        SNES snes = init();
        snes.cpu.setEmulationMode(false);
        snes.cpu.registers().setPac(0x808000);
        snes.cartridge.data()[0] = 0x06;
        snes.cpu.registers().s = 0x1010;
        snes.cpu.registers().y = 0x5;
        snes.cpu.registers().dbr = 0x88;
        snes.wram.data()[0x1016] = 0xef;
        snes.wram.data()[0x1017] = 0x01;

        assertEquals(0x8801f4, snes.cpu._getStackRelativeIndirectIndexedYAddr());
        assertEquals(0x808001, snes.cpu.registers().pac);
    }

    @Test
    void stackRelativeInEmulationModeUsesPageOneStackBase() {
        SNES snes = init();
        snes.cpu.setEmulationMode(true);
        snes.cpu.registers().setPac(0x808000);
        snes.cartridge.data()[0] = 0x06;
        snes.cpu.registers().s = 0x1010;

        assertEquals(0x0116, snes.cpu._getStackRelativeAddr());
        assertEquals(0x808001, snes.cpu.registers().pac);
    }

    @Test
    void stackRelativeIndirectIndexedYInEmulationModeUsesPageOneStackBase() {
        SNES snes = init();
        snes.cpu.setEmulationMode(true);
        snes.cpu.registers().setPac(0x808000);
        snes.cartridge.data()[0] = 0x06;
        snes.cpu.registers().s = 0x1010;
        snes.cpu.registers().y = 0x5;
        snes.cpu.registers().dbr = 0x88;
        snes.wram.data()[0x0116] = 0xef;
        snes.wram.data()[0x0117] = 0x01;

        assertEquals(0x8801f4, snes.cpu._getStackRelativeIndirectIndexedYAddr());
        assertEquals(0x808001, snes.cpu.registers().pac);
    }

    @Test
    void stackRelativeAddressesCarryAcrossPagesInEmulationMode() {
        SparseBus bus = new SparseBus();
        CPU cpu = new CPU(bus, new Header());
        cpu.setEmulationMode(true);
        cpu.registers().s = 0x01ff;
        cpu.registers().dbr = 0x34;
        bus.write(0x008000, 0x02);
        bus.write(0x008001, 0x00);
        bus.write(0x0001ff, 0x78);
        bus.write(0x000100, 0x56);
        bus.write(0x000200, 0x9a);

        cpu.registers().setPac(0x008000);
        assertEquals(0x0201, cpu._getStackRelativeAddr());

        cpu.registers().setPac(0x008001);
        assertEquals(0x349a78, cpu._getStackRelativeIndirectIndexedYAddr());
    }

    @Test
    void stackRelativeOpcodesCarryAcrossPagesInEmulationMode() {
        SparseBus bus = new SparseBus();
        CPU cpu = new CPU(bus, new Header());
        cpu.setEmulationMode(true);
        cpu.registers().s = 0x01ff;
        cpu.registers().dbr = 0x34;
        bus.write(0x008000, 0xa3);
        bus.write(0x008001, 0x02);
        bus.write(0x000101, 0x5a);
        bus.write(0x000201, 0xa5);
        bus.write(0x008010, 0xb3);
        bus.write(0x008011, 0x00);
        bus.write(0x0001ff, 0x78);
        bus.write(0x000100, 0x56);
        bus.write(0x000200, 0x9a);
        bus.write(0x349a78, 0xbc);

        cpu.registers().setPac(0x008000);
        assertEquals(4, cpu.executeInstruction());
        assertEquals(0xa5, cpu.registers().a);

        cpu.registers().setPac(0x008010);
        assertEquals(7, cpu.executeInstruction());
        assertEquals(0xbc, cpu.registers().a);
    }

    private static SNES init() {
        SNES snes = new SNES(new TestFrontend(0, 0, 0));
        snes.cartridge.setSize(100);
        snes.cartridge.header.addMappingMode(MappingMode.LOROM);
        snes.sram.setSize(100);
        snes.bus.mapComponents(snes);
        return snes;
    }

    private static final class SparseBus implements IMemoryBus {
        private final Map<Integer, Integer> values = new HashMap<>();
        private int openBus;

        @Override
        public int read(int address) {
            openBus = values.getOrDefault(address & 0xffffff, 0);
            return openBus;
        }

        @Override
        public OptionalInt peek(int address) {
            return OptionalInt.of(values.getOrDefault(address & 0xffffff, openBus));
        }

        @Override
        public int peekValue(int address) {
            return peek(address).orElse(0);
        }

        @Override
        public int getOpenBus() {
            return openBus;
        }

        @Override
        public void write(int address, int data) {
            values.put(address & 0xffffff, data & 0xff);
        }

        @Override
        public IMemory getAccessor(int address) {
            return null;
        }
    }
}
