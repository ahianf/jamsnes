package jamsnes.ppu;

import jamsnes.ram.Ram;

public class TileRenderer {
    private static final int TILE_BYTE_SIZE_ROW = 16;

    private final Ram ram;
    private final Ram cgram;
    private int bpp = 2;
    private int paletteIndex;
    public final int[][] buffer = new int[8][8];
    final int[][] pixelReferences = new int[8][8];

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
        render(tileAddress, false);
    }

    public void render(int tileAddress, boolean directColor) {
        int[] vramData = ram.data();
        int[] cgramData = cgram.data();
        int vramSize = vramData.length;
        int planePairs = bpp == 2 || bpp == 4 || bpp == 8 ? bpp >> 1 : 0;
        int paletteAddress = (bpp == 8 ? 0 : paletteIndex) * bpp * bpp * 2;
        for (int y = 0; y < Tile.NB_PIXELS_HEIGHT; y++) {
            int rowAddress = tileAddress + 2 * y;
            int plane0Low = 0;
            int plane0High = 0;
            int plane1Low = 0;
            int plane1High = 0;
            int plane2Low = 0;
            int plane2High = 0;
            int plane3Low = 0;
            int plane3High = 0;
            if (planePairs >= 1) {
                plane0Low = vramData[rowAddress % vramSize];
                plane0High = vramData[(rowAddress + 1) % vramSize];
            }
            if (planePairs >= 2) {
                plane1Low = vramData[(rowAddress + TILE_BYTE_SIZE_ROW) % vramSize];
                plane1High = vramData[(rowAddress + TILE_BYTE_SIZE_ROW + 1) % vramSize];
            }
            if (planePairs == 4) {
                plane2Low = vramData[(rowAddress + TILE_BYTE_SIZE_ROW * 2) % vramSize];
                plane2High = vramData[(rowAddress + TILE_BYTE_SIZE_ROW * 2 + 1) % vramSize];
                plane3Low = vramData[(rowAddress + TILE_BYTE_SIZE_ROW * 3) % vramSize];
                plane3High = vramData[(rowAddress + TILE_BYTE_SIZE_ROW * 3 + 1) % vramSize];
            }
            for (int x = 0; x < Tile.NB_PIXELS_WIDTH; x++) {
                int shift = 7 - x;
                int pixelReference = (((plane0Low >>> shift) & 1))
                        | (((plane0High >>> shift) & 1) << 1)
                        | (((plane1Low >>> shift) & 1) << 2)
                        | (((plane1High >>> shift) & 1) << 3)
                        | (((plane2Low >>> shift) & 1) << 4)
                        | (((plane2High >>> shift) & 1) << 5)
                        | (((plane3Low >>> shift) & 1) << 6)
                        | (((plane3High >>> shift) & 1) << 7);
                pixelReferences[y][x] = pixelReference;
                if (pixelReference == 0) {
                    buffer[y][x] = 0;
                } else if (directColor) {
                    buffer[y][x] = PPUUtils.directColorToRGBA(paletteIndex, pixelReference);
                } else {
                    int colorAddress = paletteAddress + pixelReference * 2;
                    int color = cgramData[colorAddress] | (cgramData[colorAddress + 1] << 8);
                    buffer[y][x] = PPUUtils.cgramColorToRGBA(color);
                }
            }
        }
    }
}
