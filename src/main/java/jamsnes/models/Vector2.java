package jamsnes.models;

import java.util.Objects;

public class Vector2<T> {
    public final T x;
    public final T y;

    public Vector2(T x, T y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Vector2<?> vector2)) {
            return false;
        }
        return Objects.equals(x, vector2.x) && Objects.equals(y, vector2.y);
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return x + " " + y;
    }
}
