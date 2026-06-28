package jamsnes.ppu;

import jamsnes.SNES;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PpuRegisterWriteTest {
    @Test
    void decodesInidisp() {
        SNES snes = init();

        snes.bus.write(0x2100, 0b1111_1111);
        assertTrue(snes.ppu.ppuRegisters().inidispFblank());
        assertEquals(0x0f, snes.ppu.ppuRegisters().inidispBrightness());

        snes.bus.write(0x2100, 0b0000_0101);
        assertFalse(snes.ppu.ppuRegisters().inidispFblank());
        assertEquals(0x05, snes.ppu.ppuRegisters().inidispBrightness());
    }

    @Test
    void decodesObsel() {
        SNES snes = init();

        snes.bus.write(0x2101, 0b1111_1111);
        assertEquals(0b111, snes.ppu.ppuRegisters().obselObjectSize());
        assertEquals(0b11, snes.ppu.ppuRegisters().obselNameSelect());
        assertEquals(0b111, snes.ppu.ppuRegisters().obselNameBaseSelect());

        snes.bus.write(0x2101, 0b0001_1000);
        assertEquals(0b000, snes.ppu.ppuRegisters().obselObjectSize());
        assertEquals(0b11, snes.ppu.ppuRegisters().obselNameSelect());
        assertEquals(0b000, snes.ppu.ppuRegisters().obselNameBaseSelect());
    }

    @Test
    void decodesOamAddress() {
        SNES snes = init();

        snes.bus.write(0x2102, 0b1111_1111);
        assertEquals(0b0_1111_1111, snes.ppu.ppuRegisters().oamAddress());

        snes.bus.write(0x2103, 0b1111_1111);
        assertTrue(snes.ppu.ppuRegisters().oamObjPriorityActivationBit());
        assertEquals(0b1_1111_1111, snes.ppu.ppuRegisters().oamAddress());
    }

    @Test
    void decodesBgModeAndMosaic() {
        SNES snes = init();

        snes.bus.write(0x2105, 0b1010_0101);
        assertEquals(5, snes.ppu.ppuRegisters().bgMode());
        assertFalse(snes.ppu.ppuRegisters().bgCharacterSize(0));
        assertTrue(snes.ppu.ppuRegisters().bgCharacterSize(1));
        assertFalse(snes.ppu.ppuRegisters().bgCharacterSize(2));
        assertTrue(snes.ppu.ppuRegisters().bgCharacterSize(3));
        assertFalse(snes.ppu.ppuRegisters().bgMode1Bg3PriorityBit());

        snes.bus.write(0x2106, 0b0010_1001);
        assertTrue(snes.ppu.ppuRegisters().mosaicAffectsBackground(0));
        assertFalse(snes.ppu.ppuRegisters().mosaicAffectsBackground(1));
        assertFalse(snes.ppu.ppuRegisters().mosaicAffectsBackground(2));
        assertTrue(snes.ppu.ppuRegisters().mosaicAffectsBackground(3));
        assertEquals(0x02, snes.ppu.ppuRegisters().mosaicPixelSize());
    }

    @Test
    void decodesBgTilemapAndBaseAddressRegisters() {
        SNES snes = init();

        snes.bus.write(0x210a, 0b1100_0110);
        assertEquals(0b110001, snes.ppu.ppuRegisters().bgTilemapAddress(3));
        assertFalse(snes.ppu.ppuRegisters().bgTilemapHorizontalMirroring(3));
        assertTrue(snes.ppu.ppuRegisters().bgTilemapVerticalMirroring(3));

        snes.bus.write(0x210b, 0b1010_1010);
        assertEquals(0b1010, snes.ppu.ppuRegisters().bgBaseAddressFirst(0));
        assertEquals(0b1010, snes.ppu.ppuRegisters().bgBaseAddressSecond(0));
    }

    @Test
    void decodesVmainVmaddAndVmdata() {
        SNES snes = init();

        snes.bus.write(0x2115, 0b1111_1111);
        assertTrue(snes.ppu.ppuRegisters().vmainIncrementMode());
        assertEquals(0b11, snes.ppu.ppuRegisters().vmainAddressRemapping());
        assertEquals(0b11, snes.ppu.ppuRegisters().vmainIncrementAmount());

        snes.bus.write(0x2116, 0xff);
        snes.bus.write(0x2117, 0xff);
        assertEquals(0xffff, snes.ppu.ppuRegisters().vmadd());

        snes.bus.write(0x2118, 0xff);
        snes.bus.write(0x2119, 0x00);
        assertEquals(0x00ff, snes.ppu.ppuRegisters().vmdata());
    }

    @Test
    void writesCgAddressAndData() {
        SNES snes = init();

        snes.bus.write(0x2121, 0x10);
        assertEquals(0x10, snes.ppu.ppuRegisters().cgAddress());
        assertTrue(snes.ppu.ppuRegisters().isCgLowByte());

        snes.bus.write(0x2122, 0xff);
        assertEquals(0xff, snes.ppu.ppuRegisters().cgDataLow());
        assertFalse(snes.ppu.ppuRegisters().isCgLowByte());
        assertEquals(0x10, snes.ppu.ppuRegisters().cgAddress());

        snes.bus.write(0x2122, 0xf8);
        assertEquals(0xf8, snes.ppu.ppuRegisters().cgDataHigh());
        assertTrue(snes.ppu.ppuRegisters().isCgLowByte());
        assertEquals(0x12, snes.ppu.ppuRegisters().cgAddress());
        assertEquals(0xff, snes.ppu.cgram.read(0x10));
        assertEquals(0xf8, snes.ppu.cgram.read(0x11));
    }

    private static SNES init() {
        SNES snes = new SNES(new NoRenderer(0, 0, 0));
        snes.bus.mapComponents(snes);
        return snes;
    }
}
