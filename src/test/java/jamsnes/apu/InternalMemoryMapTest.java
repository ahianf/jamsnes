package jamsnes.apu;

import jamsnes.SNES;
import jamsnes.cartridge.Cartridge;
import jamsnes.exceptions.InvalidAddress;
import jamsnes.memory.IMemory;
import jamsnes.memory.MemoryShadow;
import jamsnes.memory.RepeatingMemoryShadow;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InternalMemoryMapTest {
    @TempDir
    Path tempDir;

    @Test
    void internalReadUsesApuMemoryRegionsAndRegisters() {
        SNES snes = init();
        snes.apu._internalWrite(0x0010, 123);
        snes.apu._internalWrite(0x0142, 45);
        snes.apu._internalWrite(0xfedc, 67);
        snes.apu._internalWrite(0xffbf, 89);
        snes.apu.write(0x00, 0xaa);
        snes.apu._internalWrite(0x00f8, 0xbb);
        snes.apu.counters()[0] = 0xcc;

        assertEquals(123, snes.apu._internalRead(0x0010));
        assertEquals(45, snes.apu._internalRead(0x0142));
        assertEquals(67, snes.apu._internalRead(0xfedc));
        assertEquals(89, snes.apu._internalRead(0xffbf));
        assertEquals(0xaa, snes.apu._internalRead(0x00f4));
        assertEquals(0xbb, snes.apu._internalRead(0x00f8));
        assertEquals(0x0c, snes.apu._internalRead(0x00fd));
    }

    @Test
    void internalReadMapsIplRomAtBootVector() {
        SNES snes = init();

        assertEquals(0xcd, snes.apu._internalRead(0xffc0));
        assertEquals(0xef, snes.apu._internalRead(0xffc1));
        assertEquals(0xc0, snes.apu._internalRead(0xfffe));
        assertEquals(0xff, snes.apu._internalRead(0xffff));
    }

    @Test
    void internalWriteStoresRamBehindIplRomOverlay() {
        SNES snes = init();

        snes.apu._internalWrite(0xffc0, 0x42);
        snes.apu._internalWrite(0xffff, 0x24);

        assertEquals(0xcd, snes.apu._internalRead(0xffc0));
        assertEquals(0xff, snes.apu._internalRead(0xffff));

        snes.apu._internalWrite(0x00f1, 0x00);
        assertEquals(0x42, snes.apu._internalRead(0xffc0));
        assertEquals(0x24, snes.apu._internalRead(0xffff));

        snes.apu._internalWrite(0x00f1, 0x80);
        assertEquals(0xcd, snes.apu._internalRead(0xffc0));
        assertEquals(0xff, snes.apu._internalRead(0xffff));
    }

    @Test
    void controlRegisterTogglesIplRomOverlay() {
        SNES snes = init();

        assertEquals(0xcd, snes.apu._internalRead(0xffc0));

        snes.apu._internalWrite(0x00f1, 0x00);
        snes.apu._internalWrite(0xffc0, 0x42);
        assertEquals(0x42, snes.apu._internalRead(0xffc0));

        snes.apu._internalWrite(0x00f1, 0x80);
        assertEquals(0xcd, snes.apu._internalRead(0xffc0));
    }

    @Test
    void controlRegisterClearsCommunicationPorts() {
        SNES snes = init();
        snes.apu.write(0x00, 0x11);
        snes.apu.write(0x01, 0x22);
        snes.apu.write(0x02, 0x33);
        snes.apu.write(0x03, 0x44);

        snes.apu._internalWrite(0x00f1, 0x10);
        assertEquals(0x00, snes.apu._internalRead(0x00f4));
        assertEquals(0x00, snes.apu._internalRead(0x00f5));
        assertEquals(0x33, snes.apu._internalRead(0x00f6));
        assertEquals(0x44, snes.apu._internalRead(0x00f7));

        snes.apu._internalWrite(0x00f1, 0x20);
        assertEquals(0x00, snes.apu._internalRead(0x00f6));
        assertEquals(0x00, snes.apu._internalRead(0x00f7));
    }

    @Test
    void cpuAndApuCommunicationPortsUseSeparateDirections() {
        SNES snes = init();

        snes.bus.mapComponents(snes);
        snes.bus.write(0x2140, 0x12);
        snes.bus.write(0x2141, 0x34);

        assertEquals(0x12, snes.apu._internalRead(0x00f4));
        assertEquals(0x34, snes.apu._internalRead(0x00f5));
        assertEquals(0x00, snes.bus.read(0x2140));

        snes.apu._internalWrite(0x00f4, 0xab);
        snes.apu._internalWrite(0x00f5, 0xcd);

        assertEquals(0xab, snes.bus.read(0x2140));
        assertEquals(0xcd, snes.bus.read(0x2141));
        assertEquals(0x12, snes.apu._internalRead(0x00f4));
        assertEquals(0x34, snes.apu._internalRead(0x00f5));
    }

    @Test
    void mirroredCpuApuPortsRepeatAcross2140To217f() {
        SNES snes = init();
        snes.bus.mapComponents(snes);

        snes.bus.write(0x2144, 0x12);
        snes.bus.write(0x217f, 0x34);
        snes.bus.write(0x802146, 0x56);

        assertEquals(0x12, snes.apu._internalRead(0x00f4));
        assertEquals(0x56, snes.apu._internalRead(0x00f6));
        assertEquals(0x34, snes.apu._internalRead(0x00f7));

        snes.apu._internalWrite(0x00f4, 0xab);
        snes.apu._internalWrite(0x00f6, 0xcd);
        snes.apu._internalWrite(0x00f7, 0xef);

        assertEquals(0xab, snes.bus.read(0x2144));
        assertEquals(0xcd, snes.bus.read(0x802146));
        assertEquals(0xef, snes.bus.read(0x217f));
    }

    @Test
    void counterReadsExposeLowNibbleAndClearCounters() {
        SNES snes = init();
        snes.apu.counters()[0] = 0x12;
        snes.apu.counters()[1] = 0x34;
        snes.apu.counters()[2] = 0x56;

        assertEquals(0x02, snes.apu._internalRead(0x00fd));
        assertEquals(0x00, snes.apu._internalRead(0x00fd));
        assertEquals(0x04, snes.apu._internalRead(0x00fe));
        assertEquals(0x00, snes.apu._internalRead(0x00fe));
        assertEquals(0x06, snes.apu._internalRead(0x00ff));
        assertEquals(0x00, snes.apu._internalRead(0x00ff));
    }

    @Test
    void writeOnlyIoRegistersReadAsZero() {
        SNES snes = init();
        snes.apu._internalWrite(0x00f0, 0xaa);
        snes.apu._internalWrite(0x00f1, 0x80);
        snes.apu._internalWrite(0x00fa, 0x11);
        snes.apu._internalWrite(0x00fb, 0x22);
        snes.apu._internalWrite(0x00fc, 0x33);

        assertEquals(0, snes.apu._internalRead(0x00f0));
        assertEquals(0, snes.apu._internalRead(0x00f1));
        assertEquals(0, snes.apu._internalRead(0x00fa));
        assertEquals(0, snes.apu._internalRead(0x00fb));
        assertEquals(0, snes.apu._internalRead(0x00fc));
    }

    @Test
    void timerOutputRegistersIgnoreWrites() {
        SNES snes = init();
        snes.apu.counters()[0] = 0x01;
        snes.apu.counters()[1] = 0x02;
        snes.apu.counters()[2] = 0x03;

        snes.apu._internalWrite(0x00fd, 0xaa);
        snes.apu._internalWrite(0x00fe, 0xbb);
        snes.apu._internalWrite(0x00ff, 0xcc);

        assertEquals(0x01, snes.apu._internalRead(0x00fd));
        assertEquals(0x02, snes.apu._internalRead(0x00fe));
        assertEquals(0x03, snes.apu._internalRead(0x00ff));
    }

    @Test
    void internalWriteUsesApuMemoryRegionsAndRegisters() {
        SNES snes = init();

        snes.apu._internalWrite(0x0001, 12);
        snes.apu._internalWrite(0x01ff, 23);
        snes.apu._internalWrite(0x0789, 34);
        snes.apu._internalWrite(0xffbf, 45);
        snes.apu._internalWrite(0x00f5, 56);
        snes.apu._internalWrite(0x00f9, 67);

        assertEquals(12, snes.apu._internalRead(0x0001));
        assertEquals(23, snes.apu._internalRead(0x01ff));
        assertEquals(34, snes.apu._internalRead(0x0789));
        assertEquals(45, snes.apu._internalRead(0xffbf));
        assertEquals(56, snes.apu.ports()[1]);
        assertEquals(67, snes.apu._internalRead(0x00f9));
    }

    @Test
    void dspRegisterDataUsesSelectedDspRegister() {
        SNES snes = init();

        snes.apu._internalWrite(0x00f2, 0x00);
        snes.apu._internalWrite(0x00f3, 0x12);
        snes.apu._internalWrite(0x00f2, 0x10);
        snes.apu._internalWrite(0x00f3, 0x34);

        snes.apu._internalWrite(0x00f2, 0x00);
        assertEquals(0x12, snes.apu._internalRead(0x00f3));
        snes.apu._internalWrite(0x00f2, 0x10);
        assertEquals(0x34, snes.apu._internalRead(0x00f3));
    }

    @Test
    void dspRegisterAddressHighBitMirrorsReadsAndBlocksWrites() {
        SNES snes = init();
        snes.apu._internalWrite(0x00f2, 0x0c);
        snes.apu._internalWrite(0x00f3, 0x34);

        snes.apu._internalWrite(0x00f2, 0x8c);
        assertEquals(0x0c, snes.apu._internalRead(0x00f2));
        assertEquals(0x34, snes.apu._internalRead(0x00f3));
        snes.apu._internalWrite(0x00f3, 0x56);

        snes.apu._internalWrite(0x00f2, 0x0c);
        assertEquals(0x34, snes.apu._internalRead(0x00f3));

        snes.apu._internalWrite(0x00f2, 0x9c);
        assertEquals(0x00, snes.apu._internalRead(0x00f3));
    }

    @Test
    void dspRegisterDataDecodesGlobalFlagsAndFirRegisters() {
        SNES snes = init();

        snes.apu._internalWrite(0x00f2, 0x6c);
        snes.apu._internalWrite(0x00f3, 0b1110_0101);
        assertEquals(0b1110_0101, snes.apu._internalRead(0x00f3));

        snes.apu._internalWrite(0x00f2, 0x4c);
        snes.apu._internalWrite(0x00f3, 0b1010_0101);
        assertEquals(0b1010_0101, snes.apu._internalRead(0x00f3));

        snes.apu._internalWrite(0x00f2, 0x3f);
        snes.apu._internalWrite(0x00f3, 0x5a);
        assertEquals(0x5a, snes.apu._internalRead(0x00f3));
    }

    @Test
    void unusedDspRegisterAccessRoundTripsThroughApuDataRegister() {
        SNES snes = init();

        snes.apu._internalWrite(0x00f2, 0x0a);
        snes.apu._internalWrite(0x00f3, 0x12);

        assertEquals(0x12, snes.apu._internalRead(0x00f3));
    }

    @Test
    void loadFromSpcCopiesCpuMemoryRegistersAndDspState() throws IOException {
        SNES snes = init();
        Cartridge cartridge = new Cartridge(writeSpcFile().toString());

        snes.apu.loadFromSPC(cartridge);

        assertEquals(0x1234, snes.apu.internalRegisters().pc);
        assertEquals(0x56, snes.apu.internalRegisters().a);
        assertEquals(0x78, snes.apu.internalRegisters().x);
        assertEquals(0x9a, snes.apu.internalRegisters().y);
        assertEquals(0xa5, snes.apu.internalRegisters().psw());
        assertEquals(0xef, snes.apu.internalRegisters().sp);
        assertEquals(0x11, snes.apu._internalRead(0x0000));
        assertEquals(0x22, snes.apu._internalRead(0x0100));
        assertEquals(0x33, snes.apu._internalRead(0x0200));
        assertEquals(0x44, snes.apu._internalRead(0xffbf));
        assertEquals(0x99, snes.apu._internalRead(0xffc0));
        assertEquals(0xaa, snes.apu._internalRead(0x00f4));
        assertEquals(0xbb, snes.apu._internalRead(0x00f8));
        assertEquals(0x0c, snes.apu._internalRead(0x00fd));

        snes.apu._internalWrite(0x00f2, 0x00);
        assertEquals(0x66, snes.apu._internalRead(0x00f3));
        snes.apu._internalWrite(0x00f2, 0x10);
        assertEquals(0x77, snes.apu._internalRead(0x00f3));
        snes.apu._internalWrite(0x00f2, 0x6c);
        assertEquals(0xe5, snes.apu._internalRead(0x00f3));
        snes.apu._internalWrite(0x00f2, 0x0a);
        assertEquals(0x12, snes.apu._internalRead(0x00f3));
        snes.apu._internalWrite(0x00f2, 0x3b);
        assertEquals(0x34, snes.apu._internalRead(0x00f3));
        snes.apu._internalWrite(0x00f2, 0x7e);
        assertEquals(0x56, snes.apu._internalRead(0x00f3));
        snes.apu._internalWrite(0x00f2, 0x7c);
        assertEquals(0xa5, snes.apu._internalRead(0x00f3));
        snes.apu._internalWrite(0x00f3, 0xff);
        assertEquals(0x00, snes.apu._internalRead(0x00f3));

        snes.apu._internalWrite(0x00f2, 0x08);
        assertEquals(0x42, snes.apu._internalRead(0x00f3));
        snes.apu.update(31);
        assertEquals(0x42, snes.apu._internalRead(0x00f3));
    }

    @Test
    void loadFromSpcResetsTimerRuntimeStateBeforeApplyingSnapshot() throws IOException {
        SNES snes = init();
        snes.apu._internalWrite(0x00fa, 0x02);
        snes.apu._internalWrite(0x00f1, 0x01);
        snes.apu.update(128);

        Cartridge cartridge = new Cartridge(writeTimerSpcFile().toString());
        snes.apu.loadFromSPC(cartridge);

        snes.apu.update(128);
        assertEquals(0x00, snes.apu._internalRead(0x00fd));

        snes.apu.update(128);
        assertEquals(0x01, snes.apu._internalRead(0x00fd));
    }

    @Test
    void loadFromSpcRejectsShortCartridge() throws IOException {
        SNES snes = init();
        byte[] spc = spcHeader(0x25);
        Path path = tempDir.resolve("short.spc");
        Files.write(path, spc);
        Cartridge cartridge = new Cartridge(path.toString());

        assertThrows(InvalidAddress.class, () -> snes.apu.loadFromSPC(cartridge));
    }

    @Test
    void invalidInternalAddressesThrow() {
        SNES snes = init();

        assertThrows(InvalidAddress.class, () -> snes.apu._internalRead(0x10000));
        assertThrows(InvalidAddress.class, () -> snes.apu._internalWrite(0x10000, 123));
    }

    @Test
    void externalReadWriteOnlyExposeFourPorts() {
        SNES snes = init();

        snes.apu.write(0x03, 123);
        assertEquals(123, snes.apu._internalRead(0x00f7));
        assertEquals(0, snes.apu.read(0x03));

        snes.apu._internalWrite(0x00f7, 45);
        assertEquals(45, snes.apu.read(0x03));
        assertThrows(InvalidAddress.class, () -> snes.apu.read(0x04));
        assertThrows(InvalidAddress.class, () -> snes.apu.write(0x04, 123));
    }

    @Test
    void returnsApuRegisterValueNames() {
        SNES snes = init();

        assertEquals("APUIO0", snes.apu.getValueName(0x00));
        assertEquals("APUIO1", snes.apu.getValueName(0x01));
        assertEquals("APUIO2", snes.apu.getValueName(0x02));
        assertEquals("APUIO3", snes.apu.getValueName(0x03));
        assertEquals("???", snes.apu.getValueName(0x04));
    }

    @Test
    void apuMirrorForwardsRegisterValueNames() {
        SNES snes = init();
        snes.bus.mapComponents(snes);

        IMemory accessor = snes.bus.getAccessor(0x802143);
        MemoryShadow shadow = assertInstanceOf(MemoryShadow.class, accessor);

        assertEquals("APUIO3", shadow.getValueName(0x03));

        IMemory repeatedAccessor = snes.bus.getAccessor(0x80217f);
        RepeatingMemoryShadow repeatedShadow = assertInstanceOf(RepeatingMemoryShadow.class, repeatedAccessor);

        assertEquals("APUIO3", repeatedShadow.getValueName(0x3b));
    }

    private static SNES init() {
        return new SNES(new NoRenderer(0, 0, 0));
    }

    private Path writeSpcFile() throws IOException {
        byte[] spc = spcHeader(0x101c0);
        spc[0x25] = 0x34;
        spc[0x26] = 0x12;
        spc[0x27] = 0x56;
        spc[0x28] = 0x78;
        spc[0x29] = (byte) 0x9a;
        spc[0x2a] = (byte) 0xa5;
        spc[0x2b] = (byte) 0xef;

        spc[0x100] = 0x11;
        spc[0x200] = 0x22;
        spc[0x300] = 0x33;
        spc[0x100bf] = 0x44;
        spc[0x100c0] = (byte) 0x99;
        spc[0x1f2] = 0x00;
        spc[0x1f3] = 0x55;
        spc[0x1f4] = (byte) 0xaa;
        spc[0x1f8] = (byte) 0xbb;
        spc[0x1fd] = (byte) 0xcc;
        spc[0x10100] = 0x66;
        spc[0x10110] = 0x77;
        spc[0x10105] = (byte) 0x80;
        spc[0x10108] = 0x42;
        spc[0x1016c] = (byte) 0xe5;
        spc[0x1010a] = 0x12;
        spc[0x1013b] = 0x34;
        spc[0x1017e] = 0x56;
        spc[0x1017c] = (byte) 0xa5;

        Path path = tempDir.resolve("state.spc");
        Files.write(path, spc);
        return path;
    }

    private Path writeTimerSpcFile() throws IOException {
        byte[] spc = spcHeader(0x101c0);
        spc[0x1f1] = 0x01;
        spc[0x1fa] = 0x02;
        Path path = tempDir.resolve("timer-state.spc");
        Files.write(path, spc);
        return path;
    }

    private static byte[] spcHeader(int size) {
        byte[] spc = new byte[size];
        byte[] magic = "SNES-SPC700 Sound File Data v0.30".getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(magic, 0, spc, 0, magic.length);
        spc[0x21] = 0x1a;
        spc[0x22] = 0x1a;
        spc[0x23] = 0x1a;
        spc[0x24] = 0x1e;
        return spc;
    }
}
