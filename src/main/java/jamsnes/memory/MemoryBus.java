package jamsnes.memory;

import jamsnes.SNES;
import jamsnes.cartridge.MappingMode;
import jamsnes.exceptions.InvalidAddress;
import jamsnes.models.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static jamsnes.models.Unsigned.u24;
import static jamsnes.models.Unsigned.u8;

public class MemoryBus implements IMemoryBus {
    private final List<IMemory> memoryAccessors = new ArrayList<>();
    private final List<MemoryShadow> shadows = new ArrayList<>();
    private final List<RepeatingMemoryShadow> repeatingShadows = new ArrayList<>();
    private final List<RectangleShadow> rectangleShadows = new ArrayList<>();
    private int openBus;
    private int externalOpenBus;

    @Override
    public IMemory getAccessor(int address) {
        int normalized = u24(address);
        for (IMemory accessor : memoryAccessors) {
            if (accessor.hasMemoryAt(normalized)) {
                return accessor;
            }
        }
        return null;
    }

    @Override
    public int read(int address) {
        int normalized = u24(address);
        IMemory handler = getAccessor(normalized);
        boolean internal = isInternalCpuBus(normalized, handler);
        int data;
        if (handler == null) {
            data = internal ? openBus : externalOpenBus;
        } else {
            try {
                data = handler.read(handler.getRelativeAddress(normalized));
            } catch (InvalidAddress exception) {
                data = internal ? openBus : externalOpenBus;
            }
        }
        openBus = u8(data);
        if (!internal) {
            externalOpenBus = openBus;
        }
        return openBus;
    }

    @Override
    public OptionalInt peek(int address) {
        int normalized = u24(address);
        IMemory handler = getAccessor(normalized);
        if (handler == null) {
            return OptionalInt.of(isInternalCpuBus(normalized, null) ? openBus : externalOpenBus);
        }
        try {
            return OptionalInt.of(handler.read(handler.getRelativeAddress(normalized)));
        } catch (RuntimeException exception) {
            return OptionalInt.empty();
        }
    }

    @Override
    public int peekValue(int address) {
        return peek(address).orElse(0);
    }

    @Override
    public void write(int address, int data) {
        int normalized = u24(address);
        int value = u8(data);
        IMemory handler = getAccessor(normalized);
        if (!isInternalCpuBus(normalized, handler)) {
            externalOpenBus = value;
        }
        if (handler == null) {
            return;
        }
        handler.write(handler.getRelativeAddress(normalized), value);
    }

    private boolean isInternalCpuBus(int address, IMemory handler) {
        if (handler != null) {
            Component component = handler.getComponent();
            return component == Component.CPU || component == Component.JOYPAD;
        }
        int bank = address >>> 16;
        int page = address & 0xffff;
        boolean lowBank = bank <= 0x3f || (bank >= 0x80 && bank <= 0xbf);
        return lowBank && page >= 0x4000 && page <= 0x437f;
    }

    public void mapComponents(SNES console) {
        memoryAccessors.clear();
        shadows.clear();
        repeatingShadows.clear();
        rectangleShadows.clear();

        console.wram.setMemoryRegion(0x7e, 0x7f, 0x0000, 0xffff);
        memoryAccessors.add(console.wram);

        console.ppu.setMemoryRegion(0x2100, 0x213f);
        memoryAccessors.add(console.ppu);

        console.wramPort.setMemoryRegion(0x2180, 0x2183);
        memoryAccessors.add(console.wramPort);

        console.apu.setMemoryRegion(0x2140, 0x2143);
        memoryAccessors.add(console.apu);
        repeatingShadows.add(new RepeatingMemoryShadow(console.apu, 0x2144, 0x217f, 4));

        console.joypad.setMemoryRegion(0x4016, 0x4017);
        memoryAccessors.add(console.joypad);

        console.cpu.setMemoryRegion(0x4200, 0x421f);
        memoryAccessors.add(console.cpu);
        shadows.add(new MemoryShadow(console.cpu, 0x4300, 0x437f, 0x100));

        for (int i = 0x00; i < 0x40; i += 0x01) {
            mirrorComponents(console, i);
        }
        for (int i = 0x80; i < 0xc0; i += 0x01) {
            mirrorComponents(console, i);
        }

        if (console.cartridge.header.hasMappingMode(MappingMode.LOROM)) {
            console.cartridge.setMemoryRegion(0x80, 0xff, 0x8000, 0xffff);
            memoryAccessors.add(console.cartridge);

            rectangleShadows.add(new RectangleShadow(console.cartridge, 0x00, 0x7d, 0x8000, 0xffff));
            rectangleShadows.add(new RectangleShadow(console.cartridge, 0x40, 0x6f, 0x0000, 0x7fff).setBankOffset(0x40));
            rectangleShadows.add(new RectangleShadow(console.cartridge, 0xc0, 0xef, 0x0000, 0x7fff).setBankOffset(0x40));

            if (console.sram.getSize() > 0) {
                console.sram.setMemoryRegion(0xf0, 0xff, 0x0000, 0x7fff);
                memoryAccessors.add(console.sram);
                rectangleShadows.add(new RectangleShadow(console.sram, 0x70, 0x7d, 0x0000, 0x7fff));
            }
        } else if (console.cartridge.header.hasMappingMode(MappingMode.HIROM)) {
            console.cartridge.setMemoryRegion(0xc0, 0xff, 0x0000, 0xffff);
            memoryAccessors.add(console.cartridge);

            rectangleShadows.add(new RectangleShadow(console.cartridge, 0x00, 0x3f, 0x8000, 0xffff)
                    .setBankOffset(1)
                    .setSourceBankStride(0x10000));
            rectangleShadows.add(new RectangleShadow(console.cartridge, 0x40, 0x7d, 0x0000, 0xffff));
            rectangleShadows.add(new RectangleShadow(console.cartridge, 0x80, 0xbf, 0x8000, 0xffff)
                    .setBankOffset(1)
                    .setSourceBankStride(0x10000));
            if (console.sram.getSize() > 0) {
                rectangleShadows.add(new RectangleShadow(console.sram, 0x20, 0x3f, 0x6000, 0x7fff));
                rectangleShadows.add(new RectangleShadow(console.sram, 0xa0, 0xbf, 0x6000, 0x7fff));
            }
        }

        memoryAccessors.addAll(shadows);
        memoryAccessors.addAll(repeatingShadows);
        memoryAccessors.addAll(rectangleShadows);
    }

    private void mirrorComponents(SNES console, int bank) {
        int normalizedBank = u8(bank);
        rectangleShadows.add(new RectangleShadow(console.wram, normalizedBank, normalizedBank, 0x0000, 0x1fff));
        shadows.add(new MemoryShadow(console.ppu, (normalizedBank << 16) + 0x2100, (normalizedBank << 16) + 0x213f));
        shadows.add(new MemoryShadow(console.wramPort, (normalizedBank << 16) + 0x2180, (normalizedBank << 16) + 0x2183));
        shadows.add(new MemoryShadow(console.apu, (normalizedBank << 16) + 0x2140, (normalizedBank << 16) + 0x2143));
        repeatingShadows.add(new RepeatingMemoryShadow(console.apu,
                (normalizedBank << 16) + 0x2144,
                (normalizedBank << 16) + 0x217f,
                4));
        shadows.add(new MemoryShadow(console.joypad, (normalizedBank << 16) + 0x4016, (normalizedBank << 16) + 0x4017));
        shadows.add(new MemoryShadow(console.cpu, (normalizedBank << 16) + 0x4200, (normalizedBank << 16) + 0x420d));
        shadows.add(new MemoryShadow(console.cpu,
                (normalizedBank << 16) + 0x4210,
                (normalizedBank << 16) + 0x421f,
                0x10));
        shadows.add(new MemoryShadow(console.cpu, (normalizedBank << 16) + 0x4300, (normalizedBank << 16) + 0x437f, 0x100));
    }

    @Override
    public int getOpenBus() {
        return openBus;
    }

    @Override
    public int getExternalOpenBus() {
        return externalOpenBus;
    }

    public void setOpenBus(int openBus) {
        this.openBus = u8(openBus);
        externalOpenBus = this.openBus;
    }
}
