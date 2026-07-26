package game.world;

import game.item.OreType;

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

    /**
     * Руда в этом блоке (null — чистая порода). Именно «маска», а не свой
     * BlockType: сама порода остаётся породой своего слоя, а текстура руды
     * дорисовывается поверх — поэтому медь одинаково узнаваема и в камне,
     * и в ядре.
     */
    private OreType ore;

    /** Кадр, на котором блок последний раз был реально виден игроку (п.7). */
    private int visibleStamp = -1;

    protected Block(int worldX, int worldY, BlockType type) {
        this.worldX = worldX;
        this.worldY = worldY;
        this.type = type;
        this.durability = type.durability;
        this.rotation = rotationFor(worldX, worldY);
    }

    /**
     * Поворачиваем только обычную породу — не фон, не лаву, не двери.
     * Публичный и статический: фон (Chunk.background) — это голый BlockType без
     * своего Block/rotation, но должен крутиться тем же способом, что и передний
     * план того же тайла, иначе несовпадение поворотов бросается в глаза (п.11).
     */
    public static int rotationFor(int x, int y) {
        int h = x * 73856093 ^ y * 19349663;
        return ((h >>> 8) & 3) * 90;
    }

    public BlockType getType() {
        return type;
    }

    public OreType getOre() {
        return ore;
    }

    public void setOre(OreType ore) {
        this.ore = ore;
        resetDurability();   // руда делает блок прочнее чистой породы
    }

    /** Что упадёт игроку: руда-маска важнее «родного» дропа породы. */
    public OreType drop() {
        return ore != null ? ore : type.drop;
    }

    /**
     * Виден ли блок игроку прямо сейчас (а не «когда-то был раскрыт»).
     * Копать разрешено только такие — иначе можно ковырять породу сквозь стену.
     */
    public boolean isVisibleNow(int frame) {
        return visibleStamp == frame;
    }

    public void markVisible(int frame) {
        visibleStamp = frame;
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
        this.durability = maxDurability();
    }

    public int maxDurability() {
        return type.durability + (ore != null ? ore.extraDurability : 0);
    }

    /** 0 — целый, 1 — вот-вот развалится. По нему рисуются трещины и дрожь. */
    public double digProgress() {
        int max = maxDurability();
        if (max <= 0) return 0;
        return Math.max(0, Math.min(1, 1.0 - durability / (double) max));
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
