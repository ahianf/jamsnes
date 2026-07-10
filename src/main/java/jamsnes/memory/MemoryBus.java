package jamsnes.memory;

import jamsnes.SNES;
import jamsnes.cartridge.MappingMode;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static jamsnes.models.Unsigned.u24;
import static jamsnes.models.Unsigned.u8;

public class MemoryBus implements IMemoryBus {
    private final List<IMemory> memoryAccessors = new ArrayList<>();
    private final List<MemoryShadow> shadows = new ArrayList<>();
    private final List<RectangleShadow> rectangleShadows = new ArrayList<>();
    private int openBus;

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
        IMemory handler = getAccessor(address);
        if (handler == null) {
            return openBus;
        }
        int data = handler.read(handler.getRelativeAddress(address));
        openBus = data;
        return data;
    }

    @Override
    public OptionalInt peek(int address) {
        IMemory handler = getAccessor(address);
        if (handler == null) {
            return OptionalInt.of(openBus);
        }
        try {
            return OptionalInt.of(handler.read(handler.getRelativeAddress(address)));
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
        IMemory handler = getAccessor(address);
        if (handler == null) {
            return;
        }
        handler.write(handler.getRelativeAddress(address), data);
    }

    public void mapComponents(SNES console) {
        memoryAccessors.clear();
        shadows.clear();
        rectangleShadows.clear();

        console.wram.setMemoryRegion(0x7e, 0x7f, 0x0000, 0xffff);
        memoryAccessors.add(console.wram);

        console.ppu.setMemoryRegion(0x2100, 0x213f);
        memoryAccessors.add(console.ppu);

        console.apu.setMemoryRegion(0x2140, 0x2143);
        memoryAccessors.add(console.apu);

        console.joypad.setMemoryRegion(0x4016, 0x4017);
        memoryAccessors.add(console.joypad);

        console.cpu.setMemoryRegion(0x4200, 0x44ff);
        memoryAccessors.add(console.cpu);

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

            console.sram.setMemoryRegion(0xf0, 0xff, 0x0000, 0x7fff);
            memoryAccessors.add(console.sram);
            rectangleShadows.add(new RectangleShadow(console.sram, 0x70, 0x7d, 0x0000, 0x7fff));
        } else if (console.cartridge.header.hasMappingMode(MappingMode.HIROM)) {
            console.cartridge.setMemoryRegion(0xc0, 0xff, 0x0000, 0xffff);
            memoryAccessors.add(console.cartridge);

            rectangleShadows.add(new RectangleShadow(console.cartridge, 0x00, 0x3f, 0x8000, 0xffff)
                    .setBankOffset(1));
            rectangleShadows.add(new RectangleShadow(console.cartridge, 0x80, 0xbf, 0x8000, 0xffff)
                    .setBankOffset(1));
            rectangleShadows.add(new RectangleShadow(console.sram, 0x20, 0x3f, 0x6000, 0x7fff));
            rectangleShadows.add(new RectangleShadow(console.sram, 0xa0, 0xbf, 0x6000, 0x7fff));
        }

        memoryAccessors.addAll(shadows);
        memoryAccessors.addAll(rectangleShadows);
    }

    private void mirrorComponents(SNES console, int bank) {
        int normalizedBank = u8(bank);
        rectangleShadows.add(new RectangleShadow(console.wram, normalizedBank, normalizedBank, 0x0000, 0x1fff));
        shadows.add(new MemoryShadow(console.ppu, (normalizedBank << 16) + 0x2100, (normalizedBank << 16) + 0x213f));
        shadows.add(new MemoryShadow(console.apu, (normalizedBank << 16) + 0x2140, (normalizedBank << 16) + 0x2143));
        shadows.add(new MemoryShadow(console.joypad, (normalizedBank << 16) + 0x4016, (normalizedBank << 16) + 0x4017));
        shadows.add(new MemoryShadow(console.cpu, (normalizedBank << 16) + 0x4200, (normalizedBank << 16) + 0x421f));
    }

    public int getOpenBus() {
        return openBus;
    }

    public void setOpenBus(int openBus) {
        this.openBus = u8(openBus);
    }
}
