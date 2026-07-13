package jamsnes.input;

public enum JoypadButton {
    B(1 << 0),
    Y(1 << 1),
    SELECT(1 << 2),
    START(1 << 3),
    UP(1 << 4),
    DOWN(1 << 5),
    LEFT(1 << 6),
    RIGHT(1 << 7),
    A(1 << 8),
    X(1 << 9),
    L(1 << 10),
    R(1 << 11);

    private final int mask;

    JoypadButton(int mask) {
        this.mask = mask;
    }

    public int mask() {
        return mask;
    }
}
