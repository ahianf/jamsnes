package jamsnes.apu;

import jamsnes.SNES;
import jamsnes.cartridge.MappingMode;
import jamsnes.renderer.TestFrontend;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OperandTest {
    @Test
    void immediate() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x32;
        snes.apu._internalWrite(0x32, 0x40);

        assertEquals(0x40, snes.apu._getImmediateData());
    }

    @Test
    void direct() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x32;
        snes.apu.internalRegisters().p = true;
        snes.apu._internalWrite(0x32, 0x40);

        assertEquals(0x140, snes.apu._getDirectAddr());
    }

    @Test
    void indexXAndY() {
        SNES snes = init();
        snes.apu.internalRegisters().x = 0x32;
        snes.apu.internalRegisters().y = 0x32;
        snes.apu.internalRegisters().p = true;

        assertEquals(0x132, snes.apu._getIndexXAddr());
        assertEquals(0x132, snes.apu._getIndexYAddr());
    }

    @Test
    void directByXAndY() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x32;
        snes.apu.internalRegisters().x = 0x03;
        snes.apu.internalRegisters().y = 0x05;
        snes.apu._internalWrite(0x32, 0x40);
        snes.apu._internalWrite(0x33, 0x40);

        assertEquals(0x43, snes.apu._getDirectAddrByX());
        assertEquals(0x45, snes.apu._getDirectAddrByY());
    }

    @Test
    void directIndexedWrapsWithinSelectedDirectPage() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x32;
        snes.apu.internalRegisters().x = 0x20;
        snes.apu.internalRegisters().y = 0x30;
        snes.apu._internalWrite(0x32, 0xf0);
        snes.apu._internalWrite(0x33, 0xe8);

        assertEquals(0x10, snes.apu._getDirectAddrByX());
        assertEquals(0x18, snes.apu._getDirectAddrByY());

        snes.apu.internalRegisters().pc = 0x40;
        snes.apu.internalRegisters().p = true;
        snes.apu._internalWrite(0x40, 0xf0);
        snes.apu._internalWrite(0x41, 0xe8);

        assertEquals(0x110, snes.apu._getDirectAddrByX());
        assertEquals(0x118, snes.apu._getDirectAddrByY());
    }

    @Test
    void absolute() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x32;
        snes.apu._internalWrite(0x32, 0b00001111);
        snes.apu._internalWrite(0x33, 0b11110000);

        assertEquals(61455, snes.apu._getAbsoluteAddr());
    }

    @Test
    void absoluteByX() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x32;
        snes.apu.internalRegisters().x = 10;
        snes.apu._internalWrite(0x32, 0b00001111);
        snes.apu._internalWrite(0x33, 0b11110000);
        snes.apu._internalWrite(0b1111000000001111 + 10, 255);

        assertEquals(255, snes.apu._getAbsoluteByXAddr());
    }

    @Test
    void absoluteByXWrapsPointerAtEndOfAddressSpace() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x32;
        snes.apu.internalRegisters().x = 0x10;
        snes.apu._internalWrite(0xf1, 0x00);
        snes.apu._internalWrite(0x32, 0xef);
        snes.apu._internalWrite(0x33, 0xff);
        snes.apu._internalWrite(0xffff, 0x34);
        snes.apu._internalWrite(0x0000, 0x12);

        assertEquals(0x1234, snes.apu._getAbsoluteByXAddr());
    }

    @Test
    void absoluteAddrByXAndY() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x32;
        snes.apu.internalRegisters().x = 10;
        snes.apu.internalRegisters().y = 10;
        snes.apu._internalWrite(0x32, 0b00001111);
        snes.apu._internalWrite(0x33, 0b11110000);
        snes.apu._internalWrite(0x34, 0b00001111);
        snes.apu._internalWrite(0x35, 0b11110000);

        assertEquals(61465, snes.apu._getAbsoluteAddrByX());
        assertEquals(61465, snes.apu._getAbsoluteAddrByY());
    }

    @Test
    void absoluteBit() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x32;
        snes.apu._internalWrite(0x32, 0b00001111);
        snes.apu._internalWrite(0x33, 0b11110000);

        AbsoluteBit result = snes.apu._getAbsoluteBit();

        assertEquals(4111, result.address());
        assertEquals(7, result.bit());
    }

    @Test
    void absoluteDirectByX() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x32;
        snes.apu.internalRegisters().p = true;
        snes.apu.internalRegisters().x = 0x10;
        snes.apu._internalWrite(0x32, 0x42);
        snes.apu._internalWrite(0x152, 0b00001101);
        snes.apu._internalWrite(0x153, 0b01101011);

        assertEquals(0b0110101100001101, snes.apu._getAbsoluteDirectByXAddr());
    }

    @Test
    void absoluteDirectByXWrapsPointerWithinDirectPage() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x32;
        snes.apu.internalRegisters().x = 0x20;
        snes.apu._internalWrite(0x32, 0xf0);
        snes.apu._internalWrite(0x10, 0x34);
        snes.apu._internalWrite(0x11, 0x12);

        assertEquals(0x1234, snes.apu._getAbsoluteDirectByXAddr());

        snes.apu.internalRegisters().pc = 0x40;
        snes.apu.internalRegisters().p = true;
        snes.apu.internalRegisters().x = 0x01;
        snes.apu._internalWrite(0x40, 0xff);
        snes.apu._internalWrite(0x100, 0xcd);
        snes.apu._internalWrite(0x101, 0xab);

        assertEquals(0xabcd, snes.apu._getAbsoluteDirectByXAddr());
    }

    @Test
    void absoluteDirectByY() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x32;
        snes.apu.internalRegisters().p = true;
        snes.apu.internalRegisters().y = 0x10;
        snes.apu._internalWrite(0x32, 0x42);
        snes.apu._internalWrite(0x142, 0b00001101);
        snes.apu._internalWrite(0x143, 0b01101011);

        assertEquals(0b0110101100011101, snes.apu._getAbsoluteDirectAddrByY());
    }

    @Test
    void absoluteDirectByYWrapsPointerWithinDirectPage() {
        SNES snes = init();
        snes.apu.internalRegisters().pc = 0x32;
        snes.apu.internalRegisters().y = 0x02;
        snes.apu._internalWrite(0x32, 0xff);
        snes.apu.counters()[2] = 0x34;
        snes.apu._internalWrite(0x00, 0x12);

        assertEquals(0x1206, snes.apu._getAbsoluteDirectAddrByY());

        snes.apu.internalRegisters().pc = 0x40;
        snes.apu.internalRegisters().p = true;
        snes.apu.internalRegisters().y = 0x10;
        snes.apu._internalWrite(0x40, 0xff);
        snes.apu._internalWrite(0x1ff, 0xcd);
        snes.apu._internalWrite(0x100, 0xab);

        assertEquals(0xabdd, snes.apu._getAbsoluteDirectAddrByY());
    }

    private static SNES init() {
        SNES snes = new SNES(new TestFrontend(0, 0, 0));
        snes.cartridge.setSize(100);
        snes.cartridge.header.addMappingMode(MappingMode.LOROM);
        snes.sram.setSize(100);
        snes.bus.mapComponents(snes);
        return snes;
    }
}
