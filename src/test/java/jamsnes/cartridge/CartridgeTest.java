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

    private static void writeLoRomHeader(byte[] rom, int base, String title) {
        byte[] name = title.getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(name, 0, rom, base + 0xc0, name.length);
        rom[base + 0xd5] = 0x20;
        rom[base + 0xd6] = 0x00;
        rom[base + 0xd7] = 0x05;
        rom[base + 0xd8] = 0x00;
        rom[base + 0xfc] = 0x00;
        rom[base + 0xfd] = (byte) 0x80;
    }
}
