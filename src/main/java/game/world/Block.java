package game.world;

/**
 * Один тайл мира.
 *
 * worldX/worldY нужны сразу трём системам: выбор варианта текстуры и поворота
 * по хэшу координат (п.11), проверка соседей для тумана войны (п.7) и проверка
 * «что над/под» для гравия и лавы (п.6).
 */
public abstract class Block {
    public final int worldX;
    public final int worldY;
    protected final BlockType type;

    /** Остаток прочности: уменьшается, пока игрок копает этот тайл. */
    private int durability;

    /**
     * Слой 2 тумана войны (п.7): выставляется событийно (игрок увидел/раскопал),
     * не пересчитывается каждый кадр.
     */
    private boolean revealed;

    /** 0/90/180/270 — детерминированно по координатам, для разнообразия (п.11). */
    private final int rotation;

    protected Block(int worldX, int worldY, BlockType type) {
        this.worldX = worldX;
        this.worldY = worldY;
        this.type = type;
        this.durability = type.durability;
        this.rotation = pickRotation(worldX, worldY);
    }

    /** Поворачиваем только обычную породу — не фон, не лаву, не двери. */
    private static int pickRotation(int x, int y) {
        int h = x * 73856093 ^ y * 19349663;
        return ((h >>> 8) & 3) * 90;
    }

    public BlockType getType() {
        return type;
    }

    public int getRotation() {
        return rotation;
    }

    public boolean isSolid() {
        return type.solid;
    }

    public boolean isAir() {
        return type == BlockType.AIR;
    }

    public boolean isRevealed() {
        return revealed;
    }

    public void setRevealed(boolean revealed) {
        this.revealed = revealed;
    }

    public int getDurability() {
        return durability;
    }

    public void resetDurability() {
        this.durability = type.durability;
    }

    /** Возвращает true, если блок «докопан» и его пора ломать. */
    public boolean applyDigDamage(int amount) {
        durability -= amount;
        return durability <= 0;
    }

    /** Минимальный тир кирки. Берётся от слоя, где блок лежит физически (п.5). */
    public int requiredToolTier() {
        return Layer.atWorldY(worldY).requiredTier;
    }

    public void tick(Field field, double dt) {
        // по умолчанию тайлы статичны
    }

    /**
     * Назван не break — это зарезервированное слово в Java.
     * Вызывается Field'ом уже после того, как тайл убран из сетки.
     */
    public void onBreak(Field field) {
        // хуки для подклассов
    }
}
