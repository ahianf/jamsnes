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
        assertMirrors(snes.apu, snes.bus.getAccessor(0x002144));
        assertMirrors(snes.apu, snes.bus.getAccessor(0x00217f));
        assertMirrors(snes.apu, snes.bus.getAccessor(0xab2143));
        assertMirrors(snes.apu, snes.bus.getAccessor(0x052143));
        assertMirrors(snes.apu, snes.bus.getAccessor(0xab2144));
        assertMirrors(snes.apu, snes.bus.getAccessor(0x05217f));

        assertSame(snes.cpu, snes.bus.getAccessor(0x004200));
        assertSame(snes.cpu, snes.bus.getAccessor(0x00421f));
        assertMirrors(snes.cpu, snes.bus.getAccessor(0x004300));
        assertMirrors(snes.cpu, snes.bus.getAccessor(0x00437f));
        assertSame(snes.cpu, snes.bus.getAccessor(0x004212));
        assertNull(snes.bus.getAccessor(0x00420e));
        assertNull(snes.bus.getAccessor(0x00420f));
        assertNull(snes.bus.getAccessor(0x80420e));
        assertNull(snes.bus.getAccessor(0xbf420f));
        assertNull(snes.bus.getAccessor(0x004380));
        assertNull(snes.bus.getAccessor(0x004400));
        assertMirrors(snes.cpu, snes.bus.getAccessor(0x804212));
        assertMirrors(snes.cpu, snes.bus.getAccessor(0x804300));
        assertMirrors(snes.cpu, snes.bus.getAccessor(0xbf437f));

        assertSame(snes.joypad, snes.bus.getAccessor(0x004016));
        assertSame(snes.joypad, snes.bus.getAccessor(0x004017));
        assertMirrors(snes.joypad, snes.bus.getAccessor(0x804017));
        assertMirrors(snes.joypad, snes.bus.getAccessor(0x054016));

        assertSame(snes.ppu, snes.bus.getAccessor(0x00213e));
        assertSame(snes.ppu, snes.bus.getAccessor(0x00213f));
        assertMirrors(snes.ppu, snes.bus.getAccessor(0x80213f));

        assertSame(snes.wramPort, snes.bus.getAccessor(0x002180));
        assertSame(snes.wramPort, snes.bus.getAccessor(0x002183));
        assertMirrors(snes.wramPort, snes.bus.getAccessor(0x802180));
        assertMirrors(snes.wramPort, snes.bus.getAccessor(0xbf2183));

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

        snes.apu.ports()[3] = 45;
        assertEquals(123, snes.bus.read(0x002144));
        assertEquals(45, snes.bus.read(0x00217f));
        assertEquals(45, snes.bus.read(0x80217f));

        snes.cartridge.data()[5] = 123;
        assertEquals(123, snes.bus.read(0x808005));

        snes.cpu.internalRegisters()[0x01] = 123;
        assertEquals(123, snes.bus.read(0x004201));

        snes.joypad.setControllerState(0, 1);
        snes.bus.write(0x004016, 1);
        assertEquals(1, snes.bus.read(0x004016));

        snes.ppu.registers()[0x37] = 123;
        assertEquals(123, snes.bus.read(0x002137));

        snes.sram.data()[7] = 123;
        assertEquals(123, snes.bus.read(0x700007));

        snes.wram.data()[0x1010] = 123;
        assertEquals(123, snes.bus.read(0x7e1010));
        assertEquals(123, snes.bus.read(0x001010));
    }

    @Test
    void wramPortWritesAndReadsAtAddressRegisterWithAutoIncrement() {
        SNES snes = init();

        snes.bus.write(0x2181, 0xfe);
        snes.bus.write(0x2182, 0xff);
        snes.bus.write(0x2183, 0x01);
        snes.bus.write(0x2180, 0x12);
        snes.bus.write(0x802180, 0x34);
        snes.bus.write(0x2180, 0x56);

        assertEquals(0x12, snes.wram.data()[0x1fffe]);
        assertEquals(0x34, snes.wram.data()[0x1ffff]);
        assertEquals(0x56, snes.wram.data()[0x00000]);
        assertEquals(0x00001, snes.wramPort.address());

        snes.bus.write(0x2181, 0xfe);
        snes.bus.write(0x2182, 0xff);
        snes.bus.write(0x2183, 0x01);

        assertEquals(0x12, snes.bus.read(0x2180));
        assertEquals(0x34, snes.bus.read(0x802180));
        assertEquals(0x56, snes.bus.read(0x2180));
        assertEquals(0x00001, snes.wramPort.address());
    }

    @Test
    void wramPortHighAddressRegisterUsesOnlyLowBit() {
        SNES snes = init();

        snes.bus.write(0x2181, 0x00);
        snes.bus.write(0x2182, 0x00);
        snes.bus.write(0x2183, 0xff);
        snes.bus.write(0x2180, 0x9a);

        assertEquals(0x9a, snes.wram.data()[0x10000]);
        assertEquals(0, snes.wram.data()[0x00000]);
    }

    @Test
    void unmappedCpuRegisterSpaceUsesOpenBus() {
        SNES snes = init();
        snes.bus.setOpenBus(0x5a);

        assertEquals(0x5a, snes.bus.read(0x00420e));
        assertEquals(0x5a, snes.bus.read(0x80420f));
        assertEquals(0x5a, snes.bus.read(0x004380));
        assertEquals(0x5a, snes.bus.read(0x004400));

        snes.bus.write(0x00420e, 0x56);
        snes.bus.write(0x80420f, 0x78);
        snes.bus.write(0x004380, 0x12);
        snes.bus.write(0x004400, 0x34);
        assertEquals(0x5a, snes.bus.read(0x00420e));
        assertEquals(0x5a, snes.bus.read(0x80420f));
        assertEquals(0x5a, snes.bus.read(0x004380));
        assertEquals(0x5a, snes.bus.read(0x004400));
    }

    @Test
    void writesMappedMemory() {
        SNES snes = init();

        snes.bus.write(0x000000, 123);
        assertEquals(123, snes.wram.data()[0]);

        snes.bus.write(0x002143, 123);
        assertEquals(123, snes.apu.inputPorts()[3]);
        assertEquals(123, snes.apu._internalRead(0x00f7));
        snes.bus.write(0x002144, 0x34);
        snes.bus.write(0x00217f, 0x56);
        snes.bus.write(0x80217e, 0x78);
        assertEquals(0x34, snes.apu.inputPorts()[0]);
        assertEquals(0x78, snes.apu.inputPorts()[2]);
        assertEquals(0x56, snes.apu.inputPorts()[3]);

        snes.bus.write(0x002106, 123);
        assertEquals(123, snes.ppu.registers()[0x06]);

        snes.bus.write(0x00420d, 123);
        assertEquals(123, snes.cpu.internalRegisters()[0x0d]);

        snes.bus.write(0x804300, 0x02);
        assertEquals(0x02, snes.bus.read(0x804300));
        assertEquals(0x02, snes.cpu.dmaChannels()[0].getControlRegister());

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
    void loromLeavesSramUnmappedWhenSizeIsZero() {
        SNES snes = initLoromWithoutSram();
        snes.bus.setOpenBus(0x66);

        assertNull(snes.bus.getAccessor(0x700000));
        assertNull(snes.bus.getAccessor(0xf00000));
        assertEquals(0x66, snes.bus.read(0x700000));
        assertEquals(0x66, snes.bus.read(0xf00000));

        snes.bus.write(0x700000, 0x12);
        snes.bus.write(0xf00000, 0x34);
        assertEquals(0, snes.sram.getSize());
    }

    @Test
    void loromSramMirrorsPhysicalSizeAcrossMappedWindow() {
        SNES snes = init();
        snes.sram.setSize(0x800);
        snes.bus.mapComponents(snes);

        snes.bus.write(0x700000, 0x12);
        snes.bus.write(0x700800, 0x34);
        snes.bus.write(0xf00001, 0x56);
        snes.bus.write(0xf00801, 0x78);

        assertEquals(0x34, snes.bus.read(0x700000));
        assertEquals(0x34, snes.bus.read(0x700800));
        assertEquals(0x34, snes.sram.data()[0]);
        assertEquals(0x78, snes.bus.read(0xf00001));
        assertEquals(0x78, snes.bus.read(0xf00801));
        assertEquals(0x78, snes.sram.data()[1]);
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
        assertMirrors(snes.cartridge, snes.bus.getAccessor(0x400000));
        assertMirrors(snes.cartridge, snes.bus.getAccessor(0x410000));
        assertMirrors(snes.cartridge, snes.bus.getAccessor(0x7dffff));
        assertMirrors(snes.cartridge, snes.bus.getAccessor(0x808000));

        assertEquals(0x11, snes.bus.read(0xc00000));
        assertEquals(0x11, snes.bus.read(0x400000));
        assertEquals(0x22, snes.bus.read(0x008000));
        assertEquals(0x22, snes.bus.read(0x808000));
        assertEquals(0x33, snes.bus.read(0x410000));
        assertEquals(0x33, snes.bus.read(0xc10000));
    }

    @Test
    void mappedLoromReadsMirrorPhysicalRomSize() {
        SNES snes = init();
        snes.cartridge.setSize(0x8000);
        snes.bus.mapComponents(snes);
        snes.cartridge.data()[0x0000] = 0x12;
        snes.cartridge.data()[0x7fff] = 0x34;

        assertEquals(0x12, snes.bus.read(0x808000));
        assertEquals(0x34, snes.bus.read(0x80ffff));
        assertEquals(0x12, snes.bus.read(0x818000));
        assertEquals(0x34, snes.bus.read(0x81ffff));
    }

    @Test
    void mappedHiromReadsMirrorPhysicalRomSize() {
        SNES snes = initHirom();
        snes.cartridge.setSize(0x10000);
        snes.bus.mapComponents(snes);
        snes.cartridge.data()[0x0000] = 0x56;
        snes.cartridge.data()[0xffff] = 0x78;

        assertEquals(0x56, snes.bus.read(0xc00000));
        assertEquals(0x78, snes.bus.read(0xc0ffff));
        assertEquals(0x56, snes.bus.read(0x400000));
        assertEquals(0x78, snes.bus.read(0x40ffff));
        assertEquals(0x56, snes.bus.read(0xc10000));
        assertEquals(0x78, snes.bus.read(0xc1ffff));
    }

    @Test
    void hiromLeavesSramUnmappedWhenSizeIsZero() {
        SNES snes = initHirom();
        snes.bus.setOpenBus(0x77);

        assertNull(snes.bus.getAccessor(0x206000));
        assertNull(snes.bus.getAccessor(0xa06000));
        assertEquals(0x77, snes.bus.read(0x206000));
        assertEquals(0x77, snes.bus.read(0xa06000));

        snes.bus.write(0x206000, 0x12);
        snes.bus.write(0xa06000, 0x34);
        assertEquals(0, snes.sram.getSize());
    }

    @Test
    void hiromMapsSramBanksAndHighMirrors() {
        SNES snes = initHirom();
        snes.sram.setSize(0x2000 * 0x20);
        snes.bus.mapComponents(snes);

        snes.bus.write(0x206000, 0x12);
        snes.bus.write(0x3f7fff, 0x34);
        snes.bus.write(0xa16000, 0x56);

        assertMirrors(snes.sram, snes.bus.getAccessor(0x206000));
        assertMirrors(snes.sram, snes.bus.getAccessor(0x3f7fff));
        assertMirrors(snes.sram, snes.bus.getAccessor(0xa16000));
        assertMirrors(snes.sram, snes.bus.getAccessor(0xbf7fff));
        assertEquals(0x12, snes.bus.read(0xa06000));
        assertEquals(0x12, snes.bus.read(0x206000));
        assertEquals(0x56, snes.bus.read(0xa16000));
        assertEquals(0x56, snes.bus.read(0x216000));
        assertEquals(0x34, snes.bus.read(0x3f7fff));
        assertEquals(0x34, snes.bus.read(0xbf7fff));
        assertEquals(0x12, snes.sram.data()[0]);
        assertEquals(0x56, snes.sram.data()[0x2000]);
        assertEquals(0x34, snes.sram.data()[0x2000 * 0x20 - 1]);
    }

    @Test
    void hiromSramMirrorsPhysicalSizeAcrossMappedWindow() {
        SNES snes = initHirom();
        snes.sram.setSize(0x800);
        snes.bus.mapComponents(snes);

        snes.bus.write(0x206000, 0x12);
        snes.bus.write(0x206800, 0x34);
        snes.bus.write(0xa06001, 0x56);
        snes.bus.write(0xa06801, 0x78);

        assertEquals(0x34, snes.bus.read(0x206000));
        assertEquals(0x34, snes.bus.read(0x206800));
        assertEquals(0x34, snes.sram.data()[0]);
        assertEquals(0x78, snes.bus.read(0xa06001));
        assertEquals(0x78, snes.bus.read(0xa06801));
        assertEquals(0x78, snes.sram.data()[1]);
    }

    private static SNES init() {
        SNES snes = new SNES(new NoRenderer(0, 0, 0));
        snes.cartridge.setSize(100);
        snes.cartridge.header.addMappingMode(MappingMode.LOROM);
        snes.sram.setSize(100);
        snes.bus.mapComponents(snes);
        return snes;
    }

    private static SNES initLoromWithoutSram() {
        SNES snes = new SNES(new NoRenderer(0, 0, 0));
        snes.cartridge.setSize(100);
        snes.cartridge.header.addMappingMode(MappingMode.LOROM);
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
        if (actual instanceof RepeatingMemoryShadow repeatingMemoryShadow) {
            assertSame(expected, repeatingMemoryShadow.getMirrored());
            return;
        }
        RectangleShadow rectangleShadow = assertInstanceOf(RectangleShadow.class, actual);
        assertSame(expected, rectangleShadow.getMirrored());
    }
}
