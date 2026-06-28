package jamsnes.memory;

import jamsnes.exceptions.InvalidAddress;

import static jamsnes.models.Unsigned.bank;
import static jamsnes.models.Unsigned.page;
import static jamsnes.models.Unsigned.u16;
import static jamsnes.models.Unsigned.u24;
import static jamsnes.models.Unsigned.u8;

public abstract class ARectangleMemory implements IMemory {
    protected int startBank;
    protected int endBank;
    protected int startPage;
    protected int endPage;

    @Override
    public int getRelativeAddress(int address) {
        int normalized = u24(address);
        int addrBank = bank(normalized);
        int addrPage = page(normalized);
        int bankCount = addrBank - startBank;
        int pageCount = endPage + 1 - startPage;

        if (addrBank < startBank || addrBank > endBank) {
            throw new InvalidAddress("Rectangle memory: Invalid Bank", normalized);
        }
        if (addrPage < startPage || addrPage > endPage) {
            throw new InvalidAddress("Rectangle memory: Invalid Page", normalized);
        }
        return pageCount * bankCount + (addrPage - startPage);
    }

    @Override
    public boolean hasMemoryAt(int address) {
        int normalized = u24(address);
        int addrBank = bank(normalized);
        int addrPage = page(normalized);
        return startBank <= addrBank && addrBank <= endBank
                && startPage <= addrPage && addrPage <= endPage;
    }

    public void setMemoryRegion(int startBank, int endBank, int startPage, int endPage) {
        this.startBank = u8(startBank);
        this.endBank = u8(endBank);
        this.startPage = u16(startPage);
        this.endPage = u16(endPage);
    }
}
