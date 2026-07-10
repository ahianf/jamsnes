package jamsnes.cartridge;

import jamsnes.exceptions.InvalidAction;
import jamsnes.exceptions.InvalidRom;
import jamsnes.models.Component;
import jamsnes.ram.Ram;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static jamsnes.models.Unsigned.u8;

public class Cartridge extends Ram {
    private static final int HEADER_SIZE = 0x40;
    private static final String MAGIC_SPC = "SNES-SPC700 Sound File Data v0.30";

    public final Header header = new Header();
    private Path romPath;
    private int romStart;
    private CartridgeType type = CartridgeType.GAME;

    public Cartridge() {
        super(0, Component.ROM, "Cartridge");
    }

    public Cartridge(String romPath) {
        this();
        loadRom(romPath);
    }

    public CartridgeType getType() {
        return type;
    }

    @Override
    public int read(int address) {
        int romSize = getSize();
        int mirroredAddress = romSize == 0 ? 0 : Math.floorMod(address, romSize);
        return super.read(romStart + mirroredAddress);
    }

    @Override
    public void write(int address, int data) {
        throw new InvalidAction("Witting to the ROM is not allowed.");
    }

    public Path getRomPath() {
        return romPath;
    }

    @Override
    public int getSize() {
        return super.getSize() - romStart;
    }

    public void loadRom(String path) {
        try {
            romStart = 0;
            romPath = Path.of(path);
            byte[] bytes = Files.readAllBytes(romPath);
            setSize(bytes.length);
            for (int i = 0; i < bytes.length; i++) {
                data()[i] = bytes[i] & 0xff;
            }
            loadHeader();
        } catch (IOException exception) {
            throw new InvalidRom("Could not open the rom file at " + path + ". " + exception.getMessage(), exception);
        }
    }

    private boolean loadHeader() {
        if (isSPCFile()) {
            type = CartridgeType.AUDIO;
            return false;
        }
        type = CartridgeType.GAME;

        int headerAddress = getHeaderAddress();
        if (headerAddress + HEADER_SIZE > getSize()) {
            return false;
        }

        Header mapped = mapHeader(headerAddress);
        copyHeader(mapped, header);
        header.gameName = readAscii(headerAddress, 21);
        if (((headerAddress + 0x40) & 0x200) != 0) {
            romStart = 0x200;
            return true;
        }
        romStart = 0;
        return false;
    }

    private Header mapHeader(int headerAddress) {
        Header result = new Header();
        int base = headerAddress - 0xc0;
        int mode = data()[base + 0xd5];

        result.addMappingMode((mode & 0x10) != 0 ? MappingMode.FASTROM : MappingMode.SLOWROM);
        result.addMappingMode((mode & 0x01) != 0 ? MappingMode.HIROM : MappingMode.LOROM);
        if ((mode & 0x02) != 0 || (mode & 0x04) != 0) {
            result.addMappingMode(MappingMode.EXROM);
        }
        result.romType = data()[base + 0xd6];
        result.romSize = 0x400 << data()[base + 0xd7];
        result.sramSize = 0x400 << data()[base + 0xd8];
        result.setCreatorBytes(data()[base + 0xd9], data()[base + 0xda]);
        result.version = data()[base + 0xdb];
        result.setChecksumComplementBytes(data()[base + 0xdc], data()[base + 0xdd]);
        result.setChecksumBytes(data()[base + 0xde], data()[base + 0xdf]);

        result.nativeInterrupts.setCopBytes(data()[base + 0xe4], data()[base + 0xe5]);
        result.nativeInterrupts.setBrkBytes(data()[base + 0xe6], data()[base + 0xe7]);
        result.nativeInterrupts.setAbortBytes(data()[base + 0xe8], data()[base + 0xe9]);
        result.nativeInterrupts.setNmiBytes(data()[base + 0xea], data()[base + 0xeb]);
        result.nativeInterrupts.setResetBytes(data()[base + 0xec], data()[base + 0xed]);
        result.nativeInterrupts.setIrqBytes(data()[base + 0xee], data()[base + 0xef]);

        result.emulationInterrupts.setCopBytes(data()[base + 0xf4], data()[base + 0xf5]);
        result.emulationInterrupts.setAbortBytes(data()[base + 0xf8], data()[base + 0xf9]);
        result.emulationInterrupts.setNmiBytes(data()[base + 0xfa], data()[base + 0xfb]);
        result.emulationInterrupts.setResetBytes(data()[base + 0xfc], data()[base + 0xfd]);
        result.emulationInterrupts.setBrkBytes(data()[base + 0xfe], data()[base + 0xff]);
        result.emulationInterrupts.setIrqBytes(data()[base + 0xfe], data()[base + 0xff]);
        return result;
    }

    private int getHeaderAddress() {
        int[] addresses = {0x7fc0, 0xffc0};
        int smc = getSize() % 1024;
        int bestScore = -1;
        int bestAddress = 0;

        for (int rawAddress : addresses) {
            int address = rawAddress + smc;
            int score = 0;
            if (address + 0x32 >= getSize()) {
                continue;
            }

            Header info = mapHeader(address);
            if (info.romType <= 0x8) {
                score++;
            }
            if (info.romSize < (0x400 << 0x10)) {
                score++;
            }
            if (info.sramSize < (0x400 << 0x08)) {
                score++;
            }
            if (info.checksum + info.checksumComplement == 0xffff
                    && info.checksum != 0
                    && info.checksumComplement != 0) {
                score += 8;
            }

            if (info.emulationInterrupts.reset < 0x8000) {
                continue;
            }
            int resetOpcode = data()[info.emulationInterrupts.reset - 0x8000];
            score += switch (resetOpcode) {
                case 0x18, 0x78, 0x4c, 0x5c, 0x20, 0x22, 0x9c -> 8;
                case 0xc2, 0xe2, 0xa9, 0xa2, 0xa0 -> 4;
                case 0x00, 0xff, 0xcc -> -8;
                default -> 0;
            };

            if (score > bestScore) {
                bestScore = score;
                bestAddress = address;
            }
        }
        return bestAddress;
    }

    private boolean isSPCFile() {
        if (getSize() < 0x25) {
            return false;
        }
        String magic = new String(toBytes(0, 0x21), StandardCharsets.ISO_8859_1);
        return MAGIC_SPC.equals(magic)
                && data()[0x21] == 0x1a
                && data()[0x22] == 0x1a
                && (data()[0x23] == 0x1a || data()[0x23] == 0x1b)
                && data()[0x24] == 0x1e;
    }

    private String readAscii(int start, int length) {
        return new String(toBytes(start, length), StandardCharsets.ISO_8859_1);
    }

    private byte[] toBytes(int start, int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) u8(data()[start + i]);
        }
        return bytes;
    }

    private static void copyHeader(Header from, Header to) {
        to.gameName = from.gameName;
        to.mappingMode = from.mappingMode;
        to.romType = from.romType;
        to.romSize = from.romSize;
        to.sramSize = from.sramSize;
        to.creatorId = from.creatorId;
        to.version = from.version;
        to.checksumComplement = from.checksumComplement;
        to.checksum = from.checksum;
        copyVectors(from.nativeInterrupts, to.nativeInterrupts);
        copyVectors(from.emulationInterrupts, to.emulationInterrupts);
    }

    private static void copyVectors(InterruptVectors from, InterruptVectors to) {
        to.cop = from.cop;
        to.brk = from.brk;
        to.abort = from.abort;
        to.nmi = from.nmi;
        to.reset = from.reset;
        to.irq = from.irq;
    }
}
