package jamsnes.cpu;

import jamsnes.SNES;
import jamsnes.exceptions.InvalidAddress;
import jamsnes.memory.IMemory;
import jamsnes.memory.MemoryShadow;
import jamsnes.renderer.NoRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CpuRegisterTest {
    @Test
    void multiplyRegistersUpdateAfterEightCycles() {
        SNES snes = init();
        snes.cpu.internalRegisters()[0x16] = 0x5a;
        snes.cpu.internalRegisters()[0x17] = 0xa5;

        snes.bus.write(0x4202, 0x12);
        snes.bus.write(0x4203, 0x34);

        assertEquals(0x5a, snes.bus.read(0x4216));
        assertEquals(0xa5, snes.bus.read(0x4217));
        snes.cpu.STP(0);
        assertEquals(7, snes.cpu.update(7));
        assertEquals(0x5a, snes.bus.read(0x4216));
        assertEquals(0xa5, snes.bus.read(0x4217));
        assertEquals(1, snes.cpu.update(1));
        assertEquals(0xa8, snes.bus.read(0x4216));
        assertEquals(0x03, snes.bus.read(0x4217));
    }

    @Test
    void divideRegistersUpdateQuotientAndRemainderAfterSixteenCycles() {
        SNES snes = init();
        snes.cpu.internalRegisters()[0x14] = 0x5a;
        snes.cpu.internalRegisters()[0x15] = 0xa5;
        snes.cpu.internalRegisters()[0x16] = 0x3c;
        snes.cpu.internalRegisters()[0x17] = 0xc3;

        snes.bus.write(0x4204, 0x34);
        snes.bus.write(0x4205, 0x12);
        snes.bus.write(0x4206, 0x11);

        snes.cpu.STP(0);
        assertEquals(15, snes.cpu.update(15));
        assertEquals(0x5a, snes.bus.read(0x4214));
        assertEquals(0xa5, snes.bus.read(0x4215));
        assertEquals(0x3c, snes.bus.read(0x4216));
        assertEquals(0xc3, snes.bus.read(0x4217));
        assertEquals(1, snes.cpu.update(1));
        assertEquals(0x12, snes.bus.read(0x4214));
        assertEquals(0x01, snes.bus.read(0x4215));
        assertEquals(0x02, snes.bus.read(0x4216));
        assertEquals(0x00, snes.bus.read(0x4217));
        assertEquals(0x12, snes.bus.read(0x804214));
        assertEquals(0x01, snes.bus.read(0x804215));
    }

    @Test
    void divideByZeroProducesAllOnesQuotientAndDividendRemainder() {
        SNES snes = init();

        snes.bus.write(0x4204, 0xcd);
        snes.bus.write(0x4205, 0xab);
        snes.bus.write(0x4206, 0x00);
        snes.cpu.STP(0);
        snes.cpu.update(16);

        assertEquals(0xff, snes.bus.read(0x4214));
        assertEquals(0xff, snes.bus.read(0x4215));
        assertEquals(0xcd, snes.bus.read(0x4216));
        assertEquals(0xab, snes.bus.read(0x4217));
    }

    @Test
    void mathUnitAdvancesWhileDmaOwnsTheBus() {
        SNES snes = init();
        snes.wram.data()[0] = 0x80;
        snes.bus.write(0x4301, 0x00);
        snes.bus.write(0x4304, 0x7e);
        snes.bus.write(0x4303, 0x00);
        snes.bus.write(0x4302, 0x00);
        snes.bus.write(0x4306, 0x00);
        snes.bus.write(0x4305, 0x01);
        snes.bus.write(0x4300, DMA.ONE_TO_ONE);
        snes.bus.write(0x4202, 0x12);
        snes.bus.write(0x4203, 0x34);
        snes.bus.write(0x420b, 0x01);

        assertEquals(16, snes.cpu.runDMA(16));

        assertEquals(0xa8, snes.bus.read(0x4216));
        assertEquals(0x03, snes.bus.read(0x4217));
    }

    @Test
    void mathUnitAdvancesDuringHdmaInitialization() {
        SNES snes = init();
        snes.wram.data()[0x0200] = 0x00;
        snes.bus.write(0x4304, 0x7e);
        snes.bus.write(0x4303, 0x02);
        snes.bus.write(0x4302, 0x00);
        snes.bus.write(0x420c, 0x01);
        snes.bus.write(0x4202, 0x12);
        snes.bus.write(0x4203, 0x34);

        assertEquals(16, snes.cpu.initializeHDMA());

        assertEquals(0xa8, snes.bus.read(0x4216));
        assertEquals(0x03, snes.bus.read(0x4217));
    }

    @Test
    void resetCancelsPendingMathOperation() {
        SNES snes = init();
        snes.cpu.internalRegisters()[0x16] = 0x5a;
        snes.cpu.internalRegisters()[0x17] = 0xa5;
        snes.bus.write(0x4202, 0x12);
        snes.bus.write(0x4203, 0x34);

        snes.cpu.RESB();
        snes.cpu.STP(0);
        snes.cpu.update(8);

        assertEquals(0x5a, snes.bus.read(0x4216));
        assertEquals(0xa5, snes.bus.read(0x4217));
    }

    @Test
    void rdnmiReadClearsNmiStatusAndPendingRequest() {
        SNES snes = init();

        snes.cpu.requestNMI();

        assertEquals(0x82, snes.bus.read(0x4210));
        assertEquals(0x02, snes.bus.read(0x4210));
        assertFalse(snes.cpu.isNMIRequested);
    }

    @Test
    void rdnmiReadCarriesOpenBusBitsAndCpuVersion() {
        SNES snes = init();
        snes.bus.setOpenBus(0x7a);
        snes.cpu.requestNMI();

        assertEquals(0xf2, snes.bus.read(0x4210));
        assertEquals(0x72, snes.bus.read(0x4210));
    }

    @Test
    void timeupReadClearsIrqStatusAndPendingRequest() {
        SNES snes = init();

        snes.cpu.requestIRQ();

        assertEquals(0x80, snes.bus.read(0x4211));
        assertEquals(0x00, snes.bus.read(0x4211));
        assertFalse(snes.cpu.isIRQRequested);
    }

    @Test
    void timeupReadCarriesOpenBusLowerBits() {
        SNES snes = init();
        snes.bus.setOpenBus(0x5a);
        snes.cpu.requestIRQ();

        assertEquals(0xda, snes.bus.read(0x4211));
        assertEquals(0x5a, snes.bus.read(0x4211));
    }

    @Test
    void hvbjoyReadCarriesOpenBusUnusedBits() {
        SNES snes = init();
        snes.cpu.internalRegisters()[0x12] = 0xc1;
        snes.bus.setOpenBus(0x3e);

        assertEquals(0xff, snes.bus.read(0x4212));

        snes.cpu.internalRegisters()[0x12] = 0x00;

        assertEquals(0x3e, snes.bus.read(0x4212));
    }

    @Test
    void hvbjoyMirroredReadCarriesOpenBusUnusedBits() {
        SNES snes = init();
        snes.cpu.internalRegisters()[0x12] = 0x81;
        snes.bus.setOpenBus(0x2a);

        assertEquals(0xab, snes.bus.read(0x804212));
    }

    @Test
    void disablingTimerIrqModeAcknowledgesTimeup() {
        SNES snes = init();

        snes.bus.write(0x4200, 0x10);
        snes.cpu.requestIRQ();

        snes.bus.write(0x4200, 0x00);

        assertEquals(0x00, snes.cpu.internalRegisters()[0x11] & 0x80);
        assertFalse(snes.cpu.isIRQRequested);
        assertEquals(0x00, snes.bus.read(0x4211));
    }

    @Test
    void disablingMirroredTimerIrqModeAcknowledgesTimeup() {
        SNES snes = init();

        snes.bus.write(0x4200, 0x20);
        snes.cpu.requestIRQ();

        snes.bus.write(0x804200, 0x00);

        assertEquals(0x00, snes.cpu.internalRegisters()[0x11] & 0x80);
        assertFalse(snes.cpu.isIRQRequested);
        assertEquals(0x00, snes.bus.read(0x804211));
    }

    @Test
    void changingBetweenTimerIrqModesDoesNotAcknowledgeTimeup() {
        SNES snes = init();

        snes.bus.write(0x4200, 0x10);
        snes.cpu.requestIRQ();

        snes.bus.write(0x4200, 0x20);

        assertEquals(0x80, snes.bus.read(0x4211));
    }

    @Test
    void readOnlyCpuRegisterWritesAreNoop() {
        SNES snes = init();
        snes.cpu.requestNMI();
        snes.cpu.requestIRQ();
        snes.bus.write(0x4202, 0x12);
        snes.bus.write(0x4203, 0x34);
        snes.cpu.STP(0);
        snes.cpu.update(8);
        snes.cpu.internalRegisters()[0x18] = 0x56;

        snes.bus.write(0x4210, 0x00);
        snes.bus.write(0x4211, 0x00);
        snes.bus.write(0x4216, 0xff);
        snes.bus.write(0x4218, 0xab);

        assertEquals(0x82, snes.bus.read(0x4210));
        assertEquals(0x82, snes.bus.read(0x4211));
        assertEquals(0xa8, snes.bus.read(0x4216));
        assertEquals(0x56, snes.bus.read(0x4218));
    }

    @Test
    void writeOnlyCpuRegisterBusReadsUseOpenBus() {
        SNES snes = init();
        snes.bus.setOpenBus(0x5a);

        snes.bus.write(0x4200, 0x81);
        snes.bus.write(0x4201, 0x7f);
        snes.bus.write(0x420b, 0x01);
        snes.bus.write(0x420c, 0x01);
        snes.bus.write(0x420d, 0x01);

        assertEquals(0x81, snes.cpu.internalRegisters()[0x00]);
        assertEquals(0x7f, snes.cpu.internalRegisters()[0x01]);
        assertEquals(0x01, snes.cpu.internalRegisters()[0x0b]);
        assertEquals(0x01, snes.cpu.internalRegisters()[0x0c]);
        assertEquals(0x01, snes.cpu.internalRegisters()[0x0d]);
        assertEquals(0x5a, snes.bus.read(0x4200));
        assertEquals(0x5a, snes.bus.read(0x4201));
        assertEquals(0x5a, snes.bus.read(0x420b));
        assertEquals(0x5a, snes.bus.read(0x420c));
        assertEquals(0x5a, snes.bus.read(0x420d));
        assertEquals(0x5a, snes.bus.read(0x80420c));
    }

    @Test
    void rdioReadsCurrentWrioPortLevel() {
        SNES snes = init();
        snes.bus.setOpenBus(0x5a);

        snes.bus.write(0x4201, 0xa5);

        assertEquals(0x5a, snes.bus.read(0x4201));
        assertEquals(0xa5, snes.bus.read(0x4213));

        snes.bus.write(0x4201, 0x3c);

        assertEquals(0x3c, snes.bus.read(0x804213));
        assertEquals(0x3c, snes.cpu.read(0x13));
    }

    @Test
    void returnsCpuRegisterValueNames() {
        SNES snes = init();

        assertEquals("NMITIMEN", snes.cpu.getValueName(0x00));
        assertEquals("WRMPYA", snes.cpu.getValueName(0x02));
        assertEquals("MDMAEN", snes.cpu.getValueName(0x0b));
        assertEquals("RDDIVL", snes.cpu.getValueName(0x14));
        assertEquals("JOY4H", snes.cpu.getValueName(0x1f));
        assertEquals("???", snes.cpu.getValueName(0x20));
    }

    @Test
    void returnsDmaRegisterValueNames() {
        SNES snes = init();

        assertEquals("DMAP0", snes.cpu.getValueName(0x100));
        assertEquals("BBAD0", snes.cpu.getValueName(0x101));
        assertEquals("A1T3H", snes.cpu.getValueName(0x133));
        assertEquals("DAS7L", snes.cpu.getValueName(0x175));
        assertEquals("NTRL7", snes.cpu.getValueName(0x17a));
        assertEquals("UNUSED7", snes.cpu.getValueName(0x17b));
        assertEquals("MIRR7", snes.cpu.getValueName(0x17f));
        assertEquals("???", snes.cpu.getValueName(0x17e));
    }

    @Test
    void directUnimplementedCpuInternalRegistersThrow() {
        SNES snes = init();

        assertThrows(InvalidAddress.class, () -> snes.cpu.read(0x00));
        assertThrows(InvalidAddress.class, () -> snes.cpu.read(0x0d));
        assertThrows(InvalidAddress.class, () -> snes.cpu.read(0x0e));
        assertThrows(InvalidAddress.class, () -> snes.cpu.write(0x0f, 0x56));
    }

    @Test
    void cpuMirrorForwardsRegisterValueNames() {
        SNES snes = init();

        IMemory accessor = snes.bus.getAccessor(0x804214);
        MemoryShadow shadow = assertInstanceOf(MemoryShadow.class, accessor);

        assertEquals("RDDIVL", shadow.getValueName(0x04));
    }

    private static SNES init() {
        SNES snes = new SNES(new NoRenderer(0, 0, 0));
        snes.bus.mapComponents(snes);
        return snes;
    }
}
