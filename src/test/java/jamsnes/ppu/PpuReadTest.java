package jamsnes.ppu;

import jamsnes.SNES;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private static SNES init() {
        SNES snes = new SNES(new NoRenderer(0, 0, 0));
        snes.bus.mapComponents(snes);
        return snes;
    }
}
