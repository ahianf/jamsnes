package jamsnes.cpu;

import jamsnes.SNES;
import jamsnes.cartridge.MappingMode;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;

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
    void directIndexedIndirectXWrapsDirectPagePointerBytes() {
        SNES snes = init();
        snes.cartridge.setSize(0x8000);
        snes.cartridge.data()[0] = 0xfe;
        snes.cpu.registers().d = 0xff00;
        snes.cpu.registers().x = 0x0001;
        snes.cartridge.data()[0x7fff] = 0xef;
        snes.wram.data()[0x0000] = 0x01;
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
    void absoluteIndexedIndirect() {
        SNES snes = init();
        snes.cpu.registers().setPac(0x808000);
        snes.cartridge.data()[0] = 0xab;
        snes.cartridge.data()[1] = 0x01;
        snes.cpu.registers().x = 2;
        snes.wram.data()[0x01ad] = 0xef;
        snes.wram.data()[0x01ae] = 0x01;

        assertEquals(0x01ef, snes.cpu._getAbsoluteIndirectIndexedByXAddr());
        assertEquals(0x808002, snes.cpu.registers().pac);
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
        snes.cpu.registers().setPac(0x808000);
        snes.cartridge.data()[0] = 0x06;
        snes.cpu.registers().s = 0x1010;

        assertEquals(0x1016, snes.cpu._getStackRelativeAddr());
        assertEquals(0x808001, snes.cpu.registers().pac);
    }

    @Test
    void stackRelativeWrapsAtSixteenBits() {
        SNES snes = init();
        snes.cpu.registers().setPac(0x808000);
        snes.cartridge.data()[0] = 0x02;
        snes.cpu.registers().s = 0xffff;

        assertEquals(0x0001, snes.cpu._getStackRelativeAddr());
        assertEquals(0x808001, snes.cpu.registers().pac);
    }

    @Test
    void stackRelativeIndirectIndexedY() {
        SNES snes = init();
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

    private static SNES init() {
        SNES snes = new SNES(new NoRenderer(0, 0, 0));
        snes.cartridge.setSize(100);
        snes.cartridge.header.addMappingMode(MappingMode.LOROM);
        snes.sram.setSize(100);
        snes.bus.mapComponents(snes);
        return snes;
    }
}
