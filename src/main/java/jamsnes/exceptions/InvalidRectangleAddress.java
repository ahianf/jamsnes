package jamsnes.exceptions;

public class InvalidRectangleAddress extends RuntimeException {
    public InvalidRectangleAddress(String where, int address, int subAddress, int start, int end) {
        super(message(where, address, subAddress, start, end));
    }

    private static String message(String where, int address, int subAddress, int start, int end) {
        String relation = subAddress < start
                ? " (" + Integer.toHexString(subAddress) + " < " + Integer.toHexString(start) + ")"
                : " (" + Integer.toHexString(subAddress) + " > " + Integer.toHexString(end) + ")";
        return "Could not read/write data at address: 0x" + Integer.toHexString(address) + " from " + where + relation;
    }
}
