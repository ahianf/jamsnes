package jamsnes.memory;

import jamsnes.models.Component;

public class RectangleShadow extends ARectangleMemory {
    private final IMemory initial;
    private int bankOffset;
    private int sourceBankStride;

    public RectangleShadow(IMemory initial, int startBank, int endBank, int startPage, int endPage) {
        this.initial = initial;
        setMemoryRegion(startBank, endBank, startPage, endPage);
    }

    @Override
    public int getRelativeAddress(int address) {
        int base = super.getRelativeAddress(address);
        int pageCount = 1 + endPage - startPage;
        int bankCount = base / pageCount;
        int pageOffset = base % pageCount;
        int stride = sourceBankStride == 0 ? pageCount : sourceBankStride;
        return bankOffset * pageCount + bankCount * stride + pageOffset;
    }

    @Override
    public int read(int address) {
        return initial.read(address);
    }

    @Override
    public void write(int address, int data) {
        initial.write(address, data);
    }

    @Override
    public int getSize() {
        return initial.getSize();
    }

    @Override
    public String getName() {
        return initial.getName();
    }

    @Override
    public Component getComponent() {
        return initial.getComponent();
    }

    public IMemory getMirrored() {
        return initial;
    }

    public RectangleShadow setBankOffset(int bankOffset) {
        this.bankOffset = bankOffset;
        return this;
    }

    public RectangleShadow setSourceBankStride(int sourceBankStride) {
        this.sourceBankStride = sourceBankStride;
        return this;
    }
}
