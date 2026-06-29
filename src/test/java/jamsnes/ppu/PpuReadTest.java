package jamsnes.ppu;

import jamsnes.SNES;
import jamsnes.exceptions.InvalidAddress;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PpuReadTest {
    @Test
    void vramDataReadReturnsBufferedLowAndHighBytes() {
        SNES snes = init();
        snes.ppu.vram.write(0, 0b1111_1111);
        snes.ppu.vram.write(1, 0b1111_1111);

        snes.bus.write(0x2115, 0b1000_0000);
        snes.bus.write(0x2116, 0);
        snes.bus.write(0x2117, 0);

        assertEquals(0b1111_1111, snes.bus.read(0x2139));
        assertEquals(0b1111_1111, snes.bus.read(0x213a));
        assertEquals(1, snes.ppu.getVramAddressRegister());
    }

    @Test
    void vramDataReadWorksWithDefaultIncrementMode() {
        SNES snes = init();
        snes.ppu.vram.write(0, 0b0110_1001);
        snes.ppu.vram.write(1, 0b1111_1111);

        snes.bus.write(0x2116, 0);
        snes.bus.write(0x2117, 0);

        assertEquals(0b0110_1001, snes.bus.read(0x2139));
        assertEquals(0b1111_1111, snes.bus.read(0x213a));
        assertEquals(1, snes.ppu.getVramAddressRegister());
    }

    @Test
    void cgramDataReadReturnsCurrentAddressAndIncrements() {
        SNES snes = init();
        snes.ppu.cgram.write(0x20, 0x12);
        snes.ppu.cgram.write(0x21, 0x34);

        snes.bus.write(0x2121, 0x20);

        assertEquals(0x12, snes.bus.read(0x213b));
        assertEquals(0x21, snes.ppu.ppuRegisters().cgAddress());
        assertEquals(0x34, snes.bus.read(0x213b));
        assertEquals(0x22, snes.ppu.ppuRegisters().cgAddress());
    }

    @Test
    void placeholderPpuReadRegistersReturnZero() {
        SNES snes = init();
        snes.ppu.registers()[0x38] = 0xff;
        snes.ppu.registers()[0x3c] = 0xff;
        snes.ppu.registers()[0x3d] = 0xff;
        snes.ppu.registers()[0x3e] = 0xff;
        snes.ppu.registers()[0x3f] = 0xff;

        assertEquals(0, snes.bus.read(0x2138));
        assertEquals(0, snes.bus.read(0x213c));
        assertEquals(0, snes.bus.read(0x213d));
        assertEquals(0, snes.bus.read(0x213e));
        assertEquals(0, snes.bus.read(0x213f));
    }

    @Test
    void unsupportedPpuReadRegisterThrows() {
        SNES snes = init();

        assertThrows(InvalidAddress.class, () -> snes.bus.read(0x2100));
    }

    private static SNES init() {
        SNES snes = new SNES(new NoRenderer(0, 0, 0));
        snes.bus.mapComponents(snes);
        return snes;
    }
}
