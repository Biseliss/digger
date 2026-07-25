package game.world;

import java.util.Objects;

/** Ключ чанка в Field.chunks. Соседний чанк — просто (cx±1, cy) / (cx, cy±1). */
public record ChunkCoord(int cx, int cy) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChunkCoord other)) return false;
        return cx == other.cx && cy == other.cy;
    }

    @Override
    public int hashCode() {
        return Objects.hash(cx, cy);
    }
}
