package jamsnes.exceptions;

import static jamsnes.models.Unsigned.u24;

public class InvalidAddress extends DebuggableError {
    public InvalidAddress(String where, int address) {
        super("Could not read/write data at address: " + u24(address) + " from " + where);
    }
}
