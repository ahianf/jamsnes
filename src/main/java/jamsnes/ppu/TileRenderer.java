package jamsnes.ppu;

import jamsnes.ram.Ram;

public class TileRenderer {
    private static final int TILE_BYTE_SIZE_ROW = 16;

    private final Ram ram;
    private final Ram cgram;
    private int bpp = 2;
    private int paletteIndex;
    public final int[][] buffer = new int[8][8];

    public TileRenderer(Ram vram, Ram cgram) {
        this.ram = vram;
        this.cgram = cgram;
    }

    public void setPaletteIndex(int paletteIndex) {
        this.paletteIndex = paletteIndex;
    }

    public void setBpp(int bpp) {
        this.bpp = bpp;
    }

    public int getBpp() {
        return bpp;
    }

    public int getPaletteIndex() {
        return paletteIndex;
    }

    public int getPixelReferenceFromTile(int tileAddress, int pixelIndex) {
        int row = pixelIndex / Tile.NB_PIXELS_WIDTH;
        int column = pixelIndex % Tile.NB_PIXELS_HEIGHT;

        if (row >= Tile.NB_PIXELS_HEIGHT) {
            tileAddress += 0x80 * bpp;
            row -= Tile.NB_PIXELS_HEIGHT;
        }
        if (column >= Tile.NB_PIXELS_WIDTH) {
            tileAddress += 0x8 * bpp;
            column -= Tile.NB_PIXELS_WIDTH;
        }
        tileAddress += 2 * row;
        return getPixelReferenceFromTileRow(tileAddress, column);
    }

    public int getPixelReferenceFromTileRow(int tileRowAddress, int pixelIndex) {
        int result = 0;
        switch (bpp) {
            case 8 -> {
                result += read2BPPValue(tileRowAddress + TILE_BYTE_SIZE_ROW * 3, pixelIndex) << 6;
                result += read2BPPValue(tileRowAddress + TILE_BYTE_SIZE_ROW * 2, pixelIndex) << 4;
                result += read2BPPValue(tileRowAddress + TILE_BYTE_SIZE_ROW, pixelIndex) << 2;
                result += read2BPPValue(tileRowAddress, pixelIndex);
            }
            case 4 -> {
                result += read2BPPValue(tileRowAddress + TILE_BYTE_SIZE_ROW, pixelIndex) << 2;
                result += read2BPPValue(tileRowAddress, pixelIndex);
            }
            case 2 -> result += read2BPPValue(tileRowAddress, pixelIndex);
            default -> {
            }
        }
        return result;
    }

    public int[] getPalette(int nbPalette) {
        int nbColors = 1 << bpp;
        int address = nbPalette * bpp * bpp * 2;
        int[] palette = new int[nbColors];

        for (int i = 0; i < nbColors; i++) {
            palette[i] = cgram.read(address) | (cgram.read(address + 1) << 8);
            address += 2;
        }
        return palette;
    }

    public int read2BPPValue(int tileRowAddress, int pixelIndex) {
        int size = ram.getSize();
        int highByte = ram.read(tileRowAddress % size);
        int lowByte = ram.read((tileRowAddress + 1) % size);
        int shift = 8 - 1 - pixelIndex;
        return ((highByte & (1 << shift)) | ((lowByte & (1 << shift)) << 1)) >> shift;
    }

    public void render(int tileAddress) {
        int[] palette = getPalette(paletteIndex);
        int pixelIndex = 0;
        for (int y = 0; y < buffer.length; y++) {
            for (int x = 0; x < buffer[y].length; x++) {
                int pixelReference = getPixelReferenceFromTile(tileAddress, pixelIndex++);
                buffer[y][x] = pixelReference != 0 ? PPUUtils.cgramColorToRGBA(palette[pixelReference]) : 0;
            }
        }
    }
}
