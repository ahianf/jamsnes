package jamsnes.cartridge;

import static jamsnes.models.Unsigned.u16;
import static jamsnes.models.Unsigned.u8;

public class Header {
    public String gameName = "";
    public int mappingMode;
    public int romType;
    public int romSize;
    public int sramSize;
    public int creatorId;
    public int version;
    public int checksumComplement;
    public int checksum;
    public final InterruptVectors nativeInterrupts = new InterruptVectors();
    public final InterruptVectors emulationInterrupts = new InterruptVectors();

    public void reset() {
        gameName = "";
        mappingMode = 0;
        romType = 0;
        romSize = 0;
        sramSize = 0;
        creatorId = 0;
        version = 0;
        checksumComplement = 0;
        checksum = 0;
        nativeInterrupts.reset();
        emulationInterrupts.reset();
    }

    public boolean hasMappingMode(MappingMode mode) {
        return (mappingMode & mode.mask()) != 0;
    }

    public void addMappingMode(MappingMode mode) {
        mappingMode |= mode.mask();
    }

    public void setCreatorBytes(int low, int high) {
        creatorId = word(low, high);
    }

    public void setChecksumComplementBytes(int low, int high) {
        checksumComplement = word(low, high);
    }

    public void setChecksumBytes(int low, int high) {
        checksum = word(low, high);
    }

    private static int word(int low, int high) {
        return u16(u8(low) | (u8(high) << 8));
    }
}
