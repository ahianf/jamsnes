package jamsnes.memory;

import jamsnes.SNES;
import jamsnes.cartridge.MappingMode;
import jamsnes.exceptions.InvalidAction;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemoryBusTest {
    @Test
    void getsPrimaryAndMirrorAccessors() {
        SNES snes = init();

        assertSame(snes.wram, snes.bus.getAccessor(0x7e0000));
        assertSame(snes.wram, snes.bus.getAccessor(0x7fffff));
        assertMirrors(snes.wram, snes.bus.getAccessor(0x2f11ff));
        assertMirrors(snes.wram, snes.bus.getAccessor(0x100000));
        assertMirrors(snes.wram, snes.bus.getAccessor(0x001010));
        assertNull(snes.bus.getAccessor(0x897654));

        assertMirrors(snes.sram, snes.bus.getAccessor(0x700000));
        assertMirrors(snes.sram, snes.bus.getAccessor(0x7d7fff));
        assertSame(snes.sram, snes.bus.getAccessor(0xf00123));
        assertSame(snes.sram, snes.bus.getAccessor(0xfe0000));
        assertSame(snes.sram, snes.bus.getAccessor(0xff7fff));

        assertSame(snes.apu, snes.bus.getAccessor(0x002140));
        assertSame(snes.apu, snes.bus.getAccessor(0x002143));
        assertMirrors(snes.apu, snes.bus.getAccessor(0xab2143));
        assertMirrors(snes.apu, snes.bus.getAccessor(0x052143));

        assertSame(snes.cpu, snes.bus.getAccessor(0x004200));
        assertSame(snes.cpu, snes.bus.getAccessor(0x00421f));
        assertSame(snes.cpu, snes.bus.getAccessor(0x004212));
        assertMirrors(snes.cpu, snes.bus.getAccessor(0x804212));

        assertSame(snes.ppu, snes.bus.getAccessor(0x00213e));
        assertSame(snes.ppu, snes.bus.getAccessor(0x00213f));
        assertMirrors(snes.ppu, snes.bus.getAccessor(0x80213f));

        assertSame(snes.cartridge, snes.bus.getAccessor(0x808000));
        assertSame(snes.cartridge, snes.bus.getAccessor(0xffffff));
        assertMirrors(snes.cartridge, snes.bus.getAccessor(0x694200));
        assertMirrors(snes.cartridge, snes.bus.getAccessor(0x01fedc));
        assertMirrors(snes.cartridge, snes.bus.getAccessor(0xde1248));
        assertMirrors(snes.wram, snes.bus.getAccessor(0x000000));
    }

    @Test
    void readsMappedMemoryAndOpenBus() {
        SNES snes = init();

        snes.wram.data()[0] = 123;
        assertEquals(123, snes.bus.read(0x000000));

        snes.bus.setOpenBus(123);
        assertEquals(123, snes.bus.read(0x002000));
        assertEquals(123, snes.bus.read(0xbf2fff));
        assertEquals(123, snes.bus.read(0x127654));

        snes.apu.ports()[0] = 123;
        assertEquals(123, snes.bus.read(0x002140));

        snes.cartridge.data()[5] = 123;
        assertEquals(123, snes.bus.read(0x808005));

        snes.cpu.internalRegisters()[0x01] = 123;
        assertEquals(123, snes.bus.read(0x004201));

        snes.ppu.registers()[0x37] = 123;
        assertEquals(123, snes.bus.read(0x002137));

        snes.sram.data()[7] = 123;
        assertEquals(123, snes.bus.read(0x700007));

        snes.wram.data()[0x1010] = 123;
        assertEquals(123, snes.bus.read(0x7e1010));
        assertEquals(123, snes.bus.read(0x001010));
    }

    @Test
    void writesMappedMemory() {
        SNES snes = init();

        snes.bus.write(0x000000, 123);
        assertEquals(123, snes.wram.data()[0]);

        snes.bus.write(0x002143, 123);
        assertEquals(123, snes.apu.ports()[3]);

        snes.bus.write(0x002106, 123);
        assertEquals(123, snes.ppu.registers()[0x06]);

        snes.bus.write(0x00420d, 123);
        assertEquals(123, snes.cpu.internalRegisters()[0x0d]);

        assertThrows(InvalidAction.class, () -> snes.bus.write(0x808005, 123));

        snes.bus.write(0x7e0002, 123);
        assertEquals(123, snes.wram.data()[2]);

        snes.bus.write(0x700009, 123);
        assertEquals(123, snes.sram.data()[9]);
    }

    @Test
    void loromSramMapsUpperBanksFeAndFf() {
        SNES snes = init();
        snes.sram.setSize(0x8000 * 0x10);
        snes.bus.mapComponents(snes);

        snes.bus.write(0xfe0000, 0x5a);
        snes.bus.write(0xff7fff, 0xa5);

        assertEquals(0x5a, snes.bus.read(0xfe0000));
        assertEquals(0xa5, snes.bus.read(0xff7fff));
        assertEquals(0x5a, snes.sram.data()[0x8000 * 0x0e]);
        assertEquals(0xa5, snes.sram.data()[0x8000 * 0x10 - 1]);
    }

    @Test
    void hiromMapsRomBanksAndUpperHalfMirrors() {
        SNES snes = initHirom();
        snes.cartridge.data()[0x00000] = 0x11;
        snes.cartridge.data()[0x08000] = 0x22;
        snes.cartridge.data()[0x10000] = 0x33;

        assertSame(snes.cartridge, snes.bus.getAccessor(0xc00000));
        assertSame(snes.cartridge, snes.bus.getAccessor(0xc10000));
        assertMirrors(snes.cartridge, snes.bus.getAccessor(0x008000));
        assertMirrors(snes.cartridge, snes.bus.getAccessor(0x808000));

        assertEquals(0x11, snes.bus.read(0xc00000));
        assertEquals(0x22, snes.bus.read(0x008000));
        assertEquals(0x22, snes.bus.read(0x808000));
        assertEquals(0x33, snes.bus.read(0xc10000));
    }

    private static SNES init() {
        SNES snes = new SNES(new NoRenderer(0, 0, 0));
        snes.cartridge.setSize(100);
        snes.cartridge.header.addMappingMode(MappingMode.LOROM);
        snes.sram.setSize(100);
        snes.bus.mapComponents(snes);
        return snes;
    }

    private static SNES initHirom() {
        SNES snes = new SNES(new NoRenderer(0, 0, 0));
        snes.cartridge.setSize(0x20000);
        snes.cartridge.header.addMappingMode(MappingMode.HIROM);
        snes.bus.mapComponents(snes);
        return snes;
    }

    private static void assertMirrors(IMemory expected, IMemory actual) {
        if (actual instanceof MemoryShadow memoryShadow) {
            assertSame(expected, memoryShadow.getMirrored());
            return;
        }
        RectangleShadow rectangleShadow = assertInstanceOf(RectangleShadow.class, actual);
        assertSame(expected, rectangleShadow.getMirrored());
    }
}
