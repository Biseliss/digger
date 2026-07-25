package game.world;

import game.item.OreType;

/**
 * Тип тайла. Руда — это отдельный BlockType, замещающий плотный блок при
 * генерации (п.5), а не bool-флаг «hasOre» на обычном блоке: тогда вся
 * логика (текстура, дроп, отрисовка) остаётся единой и ходит через type.
 */
public enum BlockType {
    AIR("air", false, 0, null, false),

    // Базовая порода слоёв
    DIRT("block_dirt", true, 8, null, true),
    STONE("block_stone", true, 18, OreType.STONE, true),
    DEEPSLATE("block_deepslate", true, 32, null, true),
    HOT_ROCK("block_hotrock", true, 48, null, true),
    CORE_ROCK("block_core", true, 70, null, true),

    // Руды
    COAL_ORE("ore_coal", true, 20, OreType.COAL, true),
    COPPER_ORE("ore_copper", true, 24, OreType.COPPER, true),
    IRON_ORE("ore_iron", true, 36, OreType.IRON, true),
    GOLD_ORE("ore_gold", true, 40, OreType.GOLD, true),
    DIAMOND_ORE("ore_diamond", true, 60, OreType.DIAMOND, true),

    // Особые
    GRAVEL("block_gravel", true, 10, null, true),
    LAVA("block_lava", false, 0, null, false),          // не копается (п.6)
    BEDROCK("block_bedrock", true, 0, null, false),     // граница мира
    YELLOW_DOOR("door", false, 0, null, false),         // финальная цель (п.10)
    /** Ставится игроком: сквозь неё ходят и по ней лазают, но она не держит (п.8). */
    LADDER("ladder", false, 4, null, true);

    public final String texture;
    /** Мешает ли движению игрока. */
    public final boolean solid;
    /** Сколько «работы» надо вложить, чтобы сломать. */
    public final int durability;
    /** Что падает игроку в карман при разрушении (null — ничего). */
    public final OreType drop;
    public final boolean breakable;

    BlockType(String texture, boolean solid, int durability, OreType drop, boolean breakable) {
        this.texture = texture;
        this.solid = solid;
        this.durability = durability;
        this.drop = drop;
        this.breakable = breakable;
    }

    public boolean isOre() {
        return drop != null && this != STONE;
    }

    /**
     * Крутим на случайный угол только обычную породу (п.11). У лестницы,
     * лавы и двери поворот сломал бы читаемость — верх должен быть верхом.
     */
    public boolean isRotatable() {
        return breakable && this != LAVA && this != YELLOW_DOOR && this != LADDER;
    }
}
