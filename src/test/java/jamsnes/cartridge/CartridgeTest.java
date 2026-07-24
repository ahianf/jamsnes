package jamsnes.cartridge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CartridgeTest {
    @TempDir
    Path tempDir;

    @Test
    void loadRomWithoutCompleteHeaderCandidateLeavesHeaderEmpty() throws IOException {
        byte[] rom = new byte[0x7ff3];
        rom[0x7fc0] = 'C';
        rom[0x7ff2] = 0x78;
        Path path = tempDir.resolve("partial-header.sfc");
        Files.write(path, rom);

        Cartridge cartridge = new Cartridge(path.toString());

        assertEquals(CartridgeType.GAME, cartridge.getType());
        assertEquals(rom.length, cartridge.getSize());
        assertEquals("", cartridge.header.gameName);
        assertEquals(0, cartridge.header.emulationInterrupts.reset);
        assertFalse(cartridge.header.hasMappingMode(MappingMode.LOROM));
        assertFalse(cartridge.header.hasMappingMode(MappingMode.HIROM));
    }

    @Test
    void loadRomOnlyTreatsFiveHundredTwelveByteRemainderAsCopierHeader() throws IOException {
        byte[] rom = new byte[0x8001];
        rom[0] = 0x78;
        writeLoRomHeader(rom, 0x7f00, "JAMSNES PADDED ROM");
        Path path = tempDir.resolve("padded-rom.sfc");
        Files.write(path, rom);

        Cartridge cartridge = new Cartridge(path.toString());

        assertEquals(CartridgeType.GAME, cartridge.getType());
        assertEquals(0x8001, cartridge.getSize());
        assertTrue(cartridge.header.hasMappingMode(MappingMode.LOROM));
        assertFalse(cartridge.header.hasMappingMode(MappingMode.HIROM));
        assertEquals(0x8000, cartridge.header.emulationInterrupts.reset);
        assertEquals(0x78, cartridge.read(0));
    }

    @Test
    void loadRomPrefersHeaderWhoseLocationMatchesItsDeclaredMapping() throws IOException {
        byte[] rom = new byte[0x10000];
        rom[0x8000] = 0x78;
        writeHeader(rom, 0x7f00, "FALSE HEADER", 0x21);
        writeHeader(rom, 0xff00, "REAL HIROM HEADER", 0x21);
        Path path = tempDir.resolve("ambiguous-hirom.sfc");
        Files.write(path, rom);

        Cartridge cartridge = new Cartridge(path.toString());

        assertEquals("REAL HIROM HEADER\u0000\u0000\u0000\u0000", cartridge.header.gameName);
        assertFalse(cartridge.header.hasMappingMode(MappingMode.LOROM));
        assertTrue(cartridge.header.hasMappingMode(MappingMode.HIROM));
    }

    @Test
    void threeMegabyteRomMirrorsItsTrailingMegabyteIntoTheFourth() {
        Cartridge cartridge = new Cartridge();
        cartridge.setSize(0x300000);
        cartridge.data()[0x000000] = 0x11;
        cartridge.data()[0x200000] = 0x22;
        cartridge.data()[0x280000] = 0x33;

        assertEquals(0x22, cartridge.read(0x300000));
        assertEquals(0x33, cartridge.read(0x380000));
        assertEquals(0x11, cartridge.read(0x400000));
    }

    @Test
    void twoAndAHalfMegabyteRomRepeatsItsTrailingHalfMegabyte() {
        Cartridge cartridge = new Cartridge();
        cartridge.setSize(0x280000);
        cartridge.data()[0x000000] = 0x11;
        cartridge.data()[0x200000] = 0x22;
        cartridge.data()[0x27ffff] = 0x33;

        assertEquals(0x22, cartridge.read(0x280000));
        assertEquals(0x33, cartridge.read(0x2fffff));
        assertEquals(0x22, cartridge.read(0x300000));
        assertEquals(0x33, cartridge.read(0x37ffff));
    }

    private static void writeLoRomHeader(byte[] rom, int base, String title) {
        writeHeader(rom, base, title, 0x20);
    }

    private static void writeHeader(byte[] rom, int base, String title, int mappingMode) {
        byte[] name = title.getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(name, 0, rom, base + 0xc0, name.length);
        rom[base + 0xd5] = (byte) mappingMode;
        rom[base + 0xd6] = 0x00;
        rom[base + 0xd7] = 0x05;
        rom[base + 0xd8] = 0x00;
        rom[base + 0xfc] = 0x00;
        rom[base + 0xfd] = (byte) 0x80;
    }
}
