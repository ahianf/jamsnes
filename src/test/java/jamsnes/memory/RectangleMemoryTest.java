package jamsnes.memory;

import jamsnes.models.Component;
import jamsnes.ram.Ram;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RectangleMemoryTest {
    @Test
    void horizontalRamRead() {
        Ram ram = new Ram(0xff, Component.ROM, "Rom");
        ram.setMemoryRegion(0x00, 0xff, 0x0000, 0x0000);
        for (int i = 0x00; i < 0xff; i++) {
            ram.data()[i] = i;
        }

        for (int i = 0x000000; i < 0xff0000; i += 0x010000) {
            int value = ram.read(ram.getRelativeAddress(i));
            assertEquals(i >>> 16, value);
        }
    }

    @Test
    void horizontalRamWrite() {
        Ram ram = new Ram(0xff, Component.ROM, "Rom");
        ram.setMemoryRegion(0x00, 0xff, 0x0000, 0x0000);

        for (int i = 0x000000; i < 0xff0000; i += 0x010000) {
            ram.write(ram.getRelativeAddress(i), i >>> 16);
        }
        for (int i = 0x00; i < 0xff; i++) {
            assertEquals(i, ram.data()[i]);
        }
    }

    @Test
    void dualLineRamRead() {
        Ram ram = new Ram(0xff * 2, Component.ROM, "Rom");
        ram.setMemoryRegion(0x00, 0xff, 0x0000, 0x0001);
        for (int i = 0x00; i < 0xff * 2; i++) {
            ram.data()[i] = i & 0xff;
        }

        for (int i = 0x000000, v = 0; v < 0xff * 2; i += 0x010000, v += 2) {
            assertEquals(v & 0xff, ram.read(ram.getRelativeAddress(i)));
            assertEquals((v + 1) & 0xff, ram.read(ram.getRelativeAddress(i + 1)));
        }
    }

    @Test
    void horizontalRamShadowRead() {
        Ram ram = new Ram(0xff, Component.ROM, "Rom");
        ram.setMemoryRegion(0x00, 0xff, 0x0000, 0x0000);
        RectangleShadow shadow = new RectangleShadow(ram, 0x00, 0xff, 0x8000, 0x8000);
        for (int i = 0x00; i < 0xff; i++) {
            ram.data()[i] = i;
        }

        for (int i = 0x008000; i < 0xff8000; i += 0x010000) {
            assertEquals(i >>> 16, shadow.read(shadow.getRelativeAddress(i)));
        }
    }

    @Test
    void horizontalRamShadowReadWithBankOffset() {
        Ram ram = new Ram(0xff, Component.ROM, "Rom");
        ram.setMemoryRegion(0x00, 0xff, 0x0000, 0x0000);
        RectangleShadow shadow = new RectangleShadow(ram, 0x80, 0xff, 0x8000, 0x8000);
        for (int i = 0x00; i < 0xff; i++) {
            ram.data()[i] = i;
        }
        shadow.setBankOffset(0x80);

        for (int i = 0x808000; i < 0xff8000; i += 0x010000) {
            assertEquals(i >>> 16, shadow.read(shadow.getRelativeAddress(i)));
        }
    }

    @Test
    void shadowOffsetCartridge() {
        Ram ram = new Ram(0x3fff80, Component.ROM, "Rom");
        ram.setMemoryRegion(0x80, 0xff, 0x8000, 0xffff);
        RectangleShadow shadow = new RectangleShadow(ram, 0xc0, 0xef, 0x0000, 0x7fff);
        for (int i = 0x00; i < 0x3fff80; i++) {
            ram.data()[i] = i & 0xff;
        }
        shadow.setBankOffset(0x40);

        for (int i = 0xc00000; i <= 0xef7fff; i += 0x1) {
            if ((i & 0xffff) > 0x7fff) {
                i += 0x010000 - 0x8000;
            }
            int value = shadow.read(shadow.getRelativeAddress(i));
            int reference = ram.read(ram.getRelativeAddress(i + 0x8000));
            assertEquals(reference, value);
        }
    }
}
