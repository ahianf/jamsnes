package jamsnes.cartridge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
}
