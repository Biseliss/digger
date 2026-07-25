package game.world;

/**
 * Гравий в полёте (п.6). Пока летит — его нет в сетке чанка, он живёт
 * отдельной сущностью с дробной Y; приземлившись, снова становится блоком.
 */
public class FallingBlock {
    public static final double FALL_SPEED = 26; // тайлов/сек

    public final int tileX;
    public double y;                 // дробная позиция в тайлах
    public final BlockType type;
    /** Урон от обвала наносится один раз за полёт. */
    public boolean alreadyHitPlayer;

    public FallingBlock(int tileX, double y, BlockType type) {
        this.tileX = tileX;
        this.y = y;
        this.type = type;
    }

    public int tileY() {
        return (int) Math.floor(y);
    }
}
