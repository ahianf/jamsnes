package jamsnes.ppu;

import jamsnes.SNES;
import jamsnes.exceptions.InvalidAddress;
import jamsnes.memory.IMemory;
import jamsnes.memory.MemoryShadow;
import jamsnes.audio.RecordingAudioSink;
import jamsnes.video.RecordingVideoSink;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PpuReadTest {
    @Test
    void vramDataReadReturnsBufferedLowAndHighBytes() {
        SNES snes = init();
        snes.ppu.vram.write(0, 0b1111_1111);
        snes.ppu.vram.write(1, 0b1111_1111);

        snes.bus.write(0x2100, 0x80);
        snes.bus.write(0x2115, 0b1000_0000);
        snes.bus.write(0x2116, 0);
        snes.bus.write(0x2117, 0);

        assertEquals(0b1111_1111, snes.bus.read(0x213a));
        assertEquals(1, snes.ppu.getVramAddressRegister());
        assertEquals(0b1111_1111, snes.bus.read(0x2139));
        assertEquals(0b1111_1111, snes.bus.read(0x213a));
        assertEquals(2, snes.ppu.getVramAddressRegister());
    }

    @Test
    void vramDataReadRefreshesBufferBeforeHighByteIncrement() {
        SNES snes = init();
        snes.ppu.vram.write(0, 0x12);
        snes.ppu.vram.write(1, 0x34);
        snes.ppu.vram.write(2, 0x56);
        snes.ppu.vram.write(3, 0x78);

        snes.bus.write(0x2100, 0x80);
        snes.bus.write(0x2115, 0b1000_0000);
        snes.bus.write(0x2116, 0);
        snes.bus.write(0x2117, 0);

        assertEquals(0x34, snes.bus.read(0x213a));
        assertEquals(1, snes.ppu.getVramAddressRegister());
        assertEquals(0x12, snes.bus.read(0x2139));
        assertEquals(0x34, snes.bus.read(0x213a));
        assertEquals(2, snes.ppu.getVramAddressRegister());
        assertEquals(0x56, snes.bus.read(0x2139));
        assertEquals(0x78, snes.bus.read(0x213a));
        assertEquals(3, snes.ppu.getVramAddressRegister());
    }

    @Test
    void writingVramAddressPrefetchesTheSelectedWord() {
        SNES snes = init();
        snes.ppu.vram.write(0, 0x12);
        snes.ppu.vram.write(1, 0x34);
        snes.ppu.vram.write(2, 0x56);
        snes.ppu.vram.write(3, 0x78);

        snes.bus.write(0x2100, 0x80);
        snes.bus.write(0x2115, 0b1000_0000);
        snes.bus.write(0x2116, 0x00);
        snes.bus.write(0x2117, 0x00);
        assertEquals(0x34, snes.bus.read(0x213a));

        snes.bus.write(0x2116, 0x01);
        snes.bus.write(0x2117, 0x00);

        assertEquals(0x56, snes.bus.read(0x2139));
        assertEquals(0x78, snes.bus.read(0x213a));
    }

    @Test
    void vramDataReadReturnsPrefetchedWordBeforeDefaultLowPortIncrement() {
        SNES snes = init();
        snes.ppu.vram.write(0, 0b0110_1001);
        snes.ppu.vram.write(1, 0b1111_1111);

        snes.bus.write(0x2100, 0x80);
        snes.bus.write(0x2116, 0);
        snes.bus.write(0x2117, 0);

        assertEquals(0b0110_1001, snes.bus.read(0x2139));
        assertEquals(0b1111_1111, snes.bus.read(0x213a));
        assertEquals(1, snes.ppu.getVramAddressRegister());
        assertEquals(0b0110_1001, snes.bus.read(0x2139));
        assertEquals(2, snes.ppu.getVramAddressRegister());
    }

    @Test
    void activeVmaddPrefetchClearsVramReadBuffer() {
        SNES snes = init();
        snes.ppu.vram.write(0, 0x12);
        snes.ppu.vram.write(1, 0x34);
        snes.ppu.vram.write(2, 0xab);
        snes.ppu.vram.write(3, 0xcd);

        snes.bus.write(0x2100, 0x80);
        snes.bus.write(0x2116, 0x00);
        snes.bus.write(0x2117, 0x00);
        assertEquals(0x12, snes.bus.read(0x2139));
        snes.bus.write(0x2100, 0x00);
        snes.bus.write(0x2116, 0x01);
        snes.bus.write(0x2117, 0x00);

        assertEquals(0x00, snes.bus.read(0x2139));
        assertEquals(2, snes.ppu.getVramAddressRegister());
        assertEquals(0x00, snes.bus.read(0x213a));
    }

    @Test
    void activeLowPortIncrementReturnsOldByteThenClearsVramReadBuffer() {
        SNES snes = init();
        snes.ppu.vram.write(0, 0x12);
        snes.ppu.vram.write(1, 0x34);

        snes.bus.write(0x2100, 0x80);
        snes.bus.write(0x2116, 0x00);
        snes.bus.write(0x2117, 0x00);
        snes.bus.write(0x2100, 0x00);

        assertEquals(0x12, snes.bus.read(0x2139));
        assertEquals(1, snes.ppu.getVramAddressRegister());
        assertEquals(0x00, snes.bus.read(0x213a));
        assertEquals(0x00, snes.bus.read(0x2139));
        assertEquals(2, snes.ppu.getVramAddressRegister());
    }

    @Test
    void activeHighPortIncrementReturnsOldWordThenClearsVramReadBuffer() {
        SNES snes = init();
        snes.ppu.vram.write(0, 0x12);
        snes.ppu.vram.write(1, 0x34);

        snes.bus.write(0x2100, 0x80);
        snes.bus.write(0x2115, 0x80);
        snes.bus.write(0x2116, 0x00);
        snes.bus.write(0x2117, 0x00);
        snes.bus.write(0x2100, 0x00);

        assertEquals(0x12, snes.bus.read(0x2139));
        assertEquals(0x34, snes.bus.read(0x213a));
        assertEquals(1, snes.ppu.getVramAddressRegister());
        assertEquals(0x00, snes.bus.read(0x2139));
        assertEquals(0x00, snes.bus.read(0x213a));
        assertEquals(2, snes.ppu.getVramAddressRegister());
    }

    @Test
    void cgramDataReadReturnsCurrentAddressAndIncrements() {
        SNES snes = init();
        snes.ppu.cgram.write(0x40, 0x12);
        snes.ppu.cgram.write(0x41, 0x34);

        snes.bus.write(0x2121, 0x20);

        assertEquals(0x12, snes.bus.read(0x213b));
        assertEquals(0x20, snes.ppu.ppuRegisters().cgAddress());
        assertEquals(0x34, snes.bus.read(0x213b));
        assertEquals(0x21, snes.ppu.ppuRegisters().cgAddress());
    }

    @Test
    void cgramDataReadUsesPpu2OpenBusForHighColorBit() {
        SNES snes = init();
        snes.ppu.cgram.write(0x40, 0xff);
        snes.ppu.cgram.write(0x41, 0xff);
        snes.ppu.cgram.write(0x42, 0x00);
        snes.ppu.cgram.write(0x43, 0xff);

        snes.bus.write(0x2121, 0x20);

        assertEquals(0xff, snes.bus.read(0x213b));
        assertEquals(0xff, snes.bus.read(0x213b));
        assertEquals(0x00, snes.bus.read(0x213b));
        assertEquals(0x7f, snes.bus.read(0x213b));
        assertEquals(0x22, snes.ppu.ppuRegisters().cgAddress());
    }

    @Test
    void cgramDataReadRedirectsToTheBackgroundPaletteFetchDuringActiveRendering() {
        SNES snes = init();
        snes.ppu.cgram.write(9 * 2, 0x12);
        snes.ppu.cgram.write(9 * 2 + 1, 0x34);
        snes.ppu.cgram.write(0x40, 0x56);
        snes.ppu.cgram.write(0x41, 0x78);
        prepareBg1PaletteFetchAtFirstVisiblePixel(snes);
        snes.bus.write(0x2121, 0x20);

        assertEquals(0x12, snes.bus.read(0x213b));
        assertEquals(0x34, snes.bus.read(0x213b));
        assertEquals(0x21, snes.ppu.ppuRegisters().cgAddress());
    }

    @Test
    void oamDataReadReturnsCurrentAddressAndIncrements() {
        SNES snes = init();
        snes.bus.write(0x2100, 0x80);
        snes.bus.write(0x2102, 0x05);
        snes.bus.write(0x2103, 0x80);
        snes.ppu.oamram.write(0x0a, 0x42);
        snes.ppu.oamram.write(0x0b, 0x24);

        assertEquals(0x42, snes.bus.read(0x2138));
        assertEquals(0x0b, snes.ppu.ppuRegisters().oamAddress());
        assertEquals(0x24, snes.bus.read(0x2138));
        assertEquals(0x0c, snes.ppu.ppuRegisters().oamAddress());
    }

    @Test
    void oamDataReadMapsUpperAddressRangeToHighTable() {
        SNES snes = init();
        snes.bus.write(0x2100, 0x80);
        snes.bus.write(0x2102, 0x00);
        snes.bus.write(0x2103, 0x01);
        snes.ppu.oamram.write(0x200, 0x77);
        snes.ppu.oamram.write(0x100, 0x55);

        assertEquals(0x77, snes.bus.read(0x2138));
        assertEquals(0x201, snes.ppu.ppuRegisters().oamAddress());
    }

    @Test
    void oamDataReadRedirectsLowTablePhaseToActiveEvaluatorObject() {
        SNES snes = init();
        snes.bus.write(0x2102, 0x20);
        snes.bus.write(0x2103, 0x00);
        snes.ppu.oamram.write(0x0c, 0x42);
        snes.ppu.oamram.write(0x0d, 0xff);
        snes.ppu.oamram.write(0x40, 0x11);
        snes.ppu.oamram.write(0x41, 0x22);
        snes.ppu.advanceCountersOnly(7);

        assertEquals(0x42, snes.bus.read(0x2138));
        assertEquals(0xff, snes.bus.read(0x2138));
        assertEquals(0x42, snes.ppu.ppuRegisters().oamAddress());
    }

    @Test
    void oamDataReadRedirectsHighTableToActiveEvaluatorGroup() {
        SNES snes = init();
        snes.bus.write(0x2102, 0x00);
        snes.bus.write(0x2103, 0x01);
        snes.ppu.oamram.write(0x202, 0xa5);
        snes.ppu.advanceCountersOnly(17);

        assertEquals(0xa5, snes.bus.read(0x2138));
        assertEquals(0x201, snes.ppu.ppuRegisters().oamAddress());
    }

    @Test
    void oamDataReadFollowsObjectFetchAddressDuringHBlank() {
        SNES snes = init();
        snes.ppu.oamram.write(30 * 4, 0x30);
        snes.ppu.oamram.write(31 * 4, 0x31);
        snes.ppu.advanceCountersOnly(271);
        snes.bus.write(0x2102, 0x00);
        snes.bus.write(0x2103, 0x00);

        assertEquals(0x31, snes.bus.read(0x2138));

        snes.ppu.advanceCountersOnly(2);
        snes.bus.write(0x2102, 0x00);
        snes.bus.write(0x2103, 0x00);

        assertEquals(0x30, snes.bus.read(0x2138));
    }

    @Test
    void priorityRotationBecomesTheNextScanlinesFirstEvaluatorObject() {
        SNES snes = init();
        snes.ppu.oamram.write(0x14, 0x5a);
        snes.ppu.oamram.write(0x15, 0x01);
        snes.bus.write(0x2102, 0x0a);
        snes.bus.write(0x2103, 0x80);
        snes.ppu.advanceCountersOnly(PPU.H_COUNTER_DOTS + 1);

        assertEquals(0x5a, snes.bus.read(0x2138));
        assertEquals(0x01, snes.bus.read(0x2138));
    }

    @Test
    void mode7MultiplicationResultReadsLowMiddleAndHighBytes() {
        SNES snes = init();
        writeMode7Register(snes, 0x211b, 0x0100);
        writeMode7Register(snes, 0x211c, 0x0200);

        assertEquals(0x00, snes.bus.read(0x2134));
        assertEquals(0x02, snes.bus.read(0x2135));
        assertEquals(0x00, snes.bus.read(0x2136));
    }

    @Test
    void mode7MultiplicationResultReadsSignedProduct() {
        SNES snes = init();
        writeMode7Register(snes, 0x211b, 0xff00);
        writeMode7Register(snes, 0x211c, 0x0200);

        assertEquals(0x00, snes.bus.read(0x2134));
        assertEquals(0xfe, snes.bus.read(0x2135));
        assertEquals(0xff, snes.bus.read(0x2136));
    }

    @Test
    void stat77IncludesPpu1OpenBusBit4AndVersion() {
        SNES snes = init();
        writeMode7Register(snes, 0x211b, 0x0008);
        writeMode7Register(snes, 0x211c, 0x0200);

        assertEquals(0x10, snes.bus.read(0x2134));
        assertEquals(0x11, snes.bus.read(0x213e));
        assertEquals(0x11, snes.bus.read(0x213e));
    }

    @Test
    void stat77ClearsOpenBusBit4WhenPriorPpu1ReadHasItClear() {
        SNES snes = init();
        writeMode7Register(snes, 0x211b, 0x0008);
        writeMode7Register(snes, 0x211c, 0x0200);

        assertEquals(0x10, snes.bus.read(0x2134));
        assertEquals(0x00, snes.bus.read(0x2135));
        assertEquals(0x01, snes.bus.read(0x213e));
    }

    @Test
    void stat77UsesPpu1OpenBusFromVramAndOamReads() {
        SNES snes = init();
        snes.ppu.vram.write(0, 0x10);
        snes.ppu.oamram.write(0, 0x00);
        snes.bus.write(0x2100, 0x80);
        snes.bus.write(0x2116, 0x00);
        snes.bus.write(0x2117, 0x00);

        assertEquals(0x10, snes.bus.read(0x2139));
        assertEquals(0x10, snes.bus.read(0x2139));
        assertEquals(0x11, snes.bus.read(0x213e));

        snes.bus.write(0x2102, 0x00);
        snes.bus.write(0x2103, 0x00);
        assertEquals(0x00, snes.bus.read(0x2138));
        assertEquals(0x01, snes.bus.read(0x213e));
    }

    @Test
    void stat78UsesPpu2OpenBusFromCgramReads() {
        SNES snes = init();
        snes.ppu.cgram.write(0, 0x20);
        snes.ppu.cgram.write(1, 0x00);
        snes.bus.write(0x2121, 0x00);

        assertEquals(0x20, snes.bus.read(0x213b));
        assertEquals(0x23, snes.bus.read(0x213f));

        assertEquals(0x00, snes.bus.read(0x213b));
        assertEquals(0x03, snes.bus.read(0x213f));
    }

    @Test
    void counterAndStatusRegistersLatchAndReturnPpuVersionBits() {
        SNES snes = init();

        snes.ppu.update(600);
        snes.bus.setOpenBus(0x5a);
        snes.ppu.registers()[0x37] = 0xab;

        assertEquals(0x5a, snes.bus.read(0x2137));
        assertEquals(0x5a, snes.bus.getOpenBus());
        assertEquals(0x03, snes.bus.read(0x213c));
        assertEquals(0x03, snes.bus.read(0x213c));
        assertEquals(0x01, snes.bus.read(0x213d));
        assertEquals(0x00, snes.bus.read(0x213d));
        assertEquals(0x01, snes.bus.read(0x213e) & 0x0f);
        assertEquals(0x43, snes.bus.read(0x213f));
        assertEquals(0x03, snes.bus.read(0x213f));
    }

    @Test
    void latchedCounterHighByteIncludesPpu2OpenBusBits() {
        SNES snes = init();

        snes.ppu.advanceCountersOnly(0x122);
        snes.bus.read(0x2137);

        assertEquals(0x22, snes.bus.read(0x213c));
        assertEquals(0x23, snes.bus.read(0x213c));
    }

    @Test
    void stat78IncludesPpu2OpenBusBit5() {
        SNES snes = init();

        snes.ppu.advanceCountersOnly(0x20);
        snes.bus.read(0x2137);
        assertEquals(0x20, snes.bus.read(0x213c));

        assertEquals(0x63, snes.bus.read(0x213f));
        assertEquals(0x23, snes.bus.read(0x213f));
    }

    @Test
    void stat78ReportsAlternatingVideoFields() {
        SNES snes = init();
        int frameDots = PPU.H_COUNTER_DOTS * PPU.V_COUNTER_SCANLINES;

        assertEquals(0x00, snes.bus.read(0x213f) & 0x80);

        snes.ppu.advanceCountersOnly(frameDots);
        assertEquals(0x80, snes.bus.read(0x213f) & 0x80);

        snes.ppu.advanceCountersOnly(frameDots - 1);
        assertEquals(0x00, snes.bus.read(0x213f) & 0x80);
        assertEquals(0, snes.ppu.hCounter());
        assertEquals(0, snes.ppu.vCounter());
    }

    @Test
    void nonInterlaceSecondFieldShortensScanline240ByOneDot() {
        SNES snes = init();
        int frameDots = PPU.H_COUNTER_DOTS * PPU.V_COUNTER_SCANLINES;

        snes.ppu.advanceCountersOnly(frameDots + PPU.H_COUNTER_DOTS * 240 + 339);
        assertEquals(240, snes.ppu.vCounter());
        assertEquals(339, snes.ppu.hCounter());

        snes.ppu.advanceCountersOnly(1);
        assertEquals(241, snes.ppu.vCounter());
        assertEquals(0, snes.ppu.hCounter());
    }

    @Test
    void interlaceSecondFieldKeepsFullScanline240() {
        SNES snes = init();
        int firstFieldDots = PPU.H_COUNTER_DOTS * (PPU.V_COUNTER_SCANLINES + 1);
        snes.bus.write(0x2133, 0x01);

        snes.ppu.advanceCountersOnly(firstFieldDots + PPU.H_COUNTER_DOTS * 240 + 340);
        assertEquals(240, snes.ppu.vCounter());
        assertEquals(340, snes.ppu.hCounter());

        snes.ppu.advanceCountersOnly(1);
        assertEquals(241, snes.ppu.vCounter());
        assertEquals(0, snes.ppu.hCounter());
    }

    @Test
    void interlaceFirstFieldIncludesExtraScanline262() {
        SNES snes = init();
        snes.bus.write(0x2133, 0x01);

        snes.ppu.advanceCountersOnly(PPU.H_COUNTER_DOTS * PPU.V_COUNTER_SCANLINES);
        assertEquals(262, snes.ppu.vCounter());
        assertEquals(0, snes.ppu.hCounter());
        assertEquals(0x00, snes.bus.read(0x213f) & 0x80);

        snes.ppu.advanceCountersOnly(PPU.H_COUNTER_DOTS);
        assertEquals(0, snes.ppu.vCounter());
        assertEquals(0, snes.ppu.hCounter());
        assertEquals(0x80, snes.bus.read(0x213f) & 0x80);
    }

    @Test
    void enablingInterlaceAndOverscanMidFieldDefersThemUntilTheNextField() {
        SNES snes = init();
        snes.ppu.advanceCountersOnly(1);

        snes.bus.write(0x2133, 0x05);

        assertEquals(PPU.V_BLANK_START_SCANLINE, snes.ppu.vBlankStartScanline());

        snes.ppu.advanceCountersOnly(PPU.H_COUNTER_DOTS * PPU.V_COUNTER_SCANLINES - 1);

        assertEquals(0, snes.ppu.vCounter());
        assertEquals(0, snes.ppu.hCounter());
        assertEquals(0x80, snes.bus.read(0x213f) & 0x80);
        assertEquals(PPU.OVERSCAN_V_BLANK_START_SCANLINE, snes.ppu.vBlankStartScanline());
    }

    @Test
    void disablingInterlaceMidFieldDoesNotRemoveTheLatchedExtraScanline() {
        SNES snes = init();
        snes.bus.write(0x2133, 0x01);
        snes.ppu.advanceCountersOnly(1);

        snes.bus.write(0x2133, 0x00);
        snes.ppu.advanceCountersOnly(PPU.H_COUNTER_DOTS * PPU.V_COUNTER_SCANLINES - 1);

        assertEquals(262, snes.ppu.vCounter());
        assertEquals(0, snes.ppu.hCounter());
        assertEquals(0x00, snes.bus.read(0x213f) & 0x80);

        snes.ppu.advanceCountersOnly(PPU.H_COUNTER_DOTS);

        assertEquals(0, snes.ppu.vCounter());
        assertEquals(0, snes.ppu.hCounter());
        assertEquals(0x80, snes.bus.read(0x213f) & 0x80);
    }

    @Test
    void stat78ForcesAndRetainsExternalLatchFlagWhileWrioIsLow() {
        SNES snes = init();

        snes.bus.write(0x4201, 0x00);
        assertEquals(0x40, snes.bus.read(0x213f) & 0x40);
        assertEquals(0x40, snes.bus.read(0x213f) & 0x40);

        snes.bus.write(0x4201, 0x80);
        assertEquals(0x40, snes.bus.read(0x213f) & 0x40);
        assertEquals(0x00, snes.bus.read(0x213f) & 0x40);
    }

    @Test
    void softwareLatchIsIgnoredWhileWrioIsLow() {
        SNES snes = init();

        snes.ppu.advanceCountersOnly(0x03);
        snes.bus.write(0x4201, 0x00);
        snes.ppu.advanceCountersOnly(0x20);

        snes.bus.read(0x2137);

        assertEquals(0x03, snes.bus.read(0x213c));
    }

    @Test
    void softwareLatchDoesNotResetCounterReadFlipFlops() {
        SNES snes = init();

        snes.ppu.advanceCountersOnly(0x03);
        snes.bus.read(0x2137);
        assertEquals(0x03, snes.bus.read(0x213c));

        snes.ppu.advanceCountersOnly(0x100);
        snes.bus.read(0x2137);

        assertEquals(0x03, snes.bus.read(0x213c));
        assertEquals(0x03, snes.bus.read(0x213c));
    }

    @Test
    void wrioLatchDoesNotResetCounterReadFlipFlops() {
        SNES snes = init();

        snes.bus.write(0x4201, 0x80);
        snes.ppu.advanceCountersOnly(0x03);
        snes.bus.write(0x4201, 0x00);
        assertEquals(0x03, snes.bus.read(0x213c));

        snes.bus.write(0x4201, 0x80);
        snes.ppu.advanceCountersOnly(0x100);
        snes.bus.write(0x4201, 0x00);

        assertEquals(0x03, snes.bus.read(0x213c));
        assertEquals(0x03, snes.bus.read(0x213c));
    }

    @Test
    void stat78ResetsCounterReadFlipFlopsAfterLatch() {
        SNES snes = init();

        snes.ppu.advanceCountersOnly(0x103);
        snes.bus.read(0x2137);
        assertEquals(0x03, snes.bus.read(0x213c));
        assertEquals(0x43, snes.bus.read(0x213f));

        assertEquals(0x03, snes.bus.read(0x213c));
        assertEquals(0x03, snes.bus.read(0x213c));
    }

    @Test
    void ppuWriteOnlyRegisterBusReadsUseOpenBus() {
        SNES snes = init();
        snes.bus.setOpenBus(0x5a);

        assertEquals(0x5a, snes.bus.read(0x2100));
        assertEquals(0x5a, snes.bus.read(0x2133));
        assertEquals(0x5a, snes.bus.read(0x802100));
        assertEquals(0x5a, snes.bus.getOpenBus());
    }

    @Test
    void selectedWriteOnlyPortsUsePpu1OpenBus() {
        SNES snes = init();
        writeMode7Register(snes, 0x211b, 0x0008);
        writeMode7Register(snes, 0x211c, 0x0200);
        assertEquals(0x10, snes.bus.read(0x2134));

        snes.bus.setOpenBus(0x5a);

        int[] ppu1OpenBusPorts = {
                0x2104, 0x2105, 0x2106, 0x2108, 0x2109, 0x210a,
                0x2114, 0x2115, 0x2116, 0x2118, 0x2119, 0x211a,
                0x2124, 0x2125, 0x2126, 0x2128, 0x2129, 0x212a
        };
        for (int address : ppu1OpenBusPorts) {
            assertEquals(0x10, snes.bus.read(address));
        }
        assertEquals(0x10, snes.bus.read(0x802104));

        snes.bus.setOpenBus(0x5a);
        assertEquals(0x5a, snes.bus.read(0x2107));
        assertEquals(0x5a, snes.bus.read(0x2117));
        assertEquals(0x5a, snes.bus.read(0x2127));
    }

    @Test
    void unsupportedDirectPpuReadRegisterThrows() {
        SNES snes = init();

        assertThrows(InvalidAddress.class, () -> snes.ppu.read(0x00));
        assertThrows(InvalidAddress.class, () -> snes.ppu.read(0x40));
    }

    @Test
    void returnsPpuRegisterValueNames() {
        SNES snes = init();

        assertEquals("INIDISP", snes.ppu.getValueName(0x00));
        assertEquals("OAMDDH", snes.ppu.getValueName(0x03));
        assertEquals("M7A", snes.ppu.getValueName(0x1b));
        assertEquals("CGDATAREAD", snes.ppu.getValueName(0x3b));
        assertEquals("STAT78", snes.ppu.getValueName(0x3f));
        assertEquals("???", snes.ppu.getValueName(0x40));
    }

    @Test
    void ppuMirrorForwardsRegisterValueNames() {
        SNES snes = init();

        IMemory accessor = snes.bus.getAccessor(0x80213f);
        MemoryShadow shadow = assertInstanceOf(MemoryShadow.class, accessor);

        assertEquals("STAT78", shadow.getValueName(0x3f));
    }

    private static void writeMode7Register(SNES snes, int address, int value) {
        snes.bus.write(address, value);
        snes.bus.write(address, value >>> 8);
    }

    private static void prepareBg1PaletteFetchAtFirstVisiblePixel(SNES snes) {
        snes.bus.write(0x2105, 0x00);
        snes.bus.write(0x2107, 0x04);
        snes.bus.write(0x212c, 0x01);
        snes.ppu.vram.write(0x0002, 0x80);
        snes.ppu.vram.write(0x0003, 0x00);
        snes.ppu.vram.write(0x0800, 0x00);
        snes.ppu.vram.write(0x0801, 0x08);
        snes.ppu.renderMainAndSubScreen();
        snes.bus.write(0x2100, 0x00);
        snes.ppu.advanceCountersOnly(PPU.H_COUNTER_DOTS + 22);
    }

    private static SNES init() {
        SNES snes = new SNES(new RecordingVideoSink(), new RecordingAudioSink());
        snes.bus.mapComponents(snes);
        snes.cpu.internalRegisters()[0x01] = 0x80;
        return snes;
    }
}
