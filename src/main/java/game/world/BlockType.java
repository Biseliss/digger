package game.world;

import game.item.OreType;

/**
 * Тип тайла. Руда — это отдельный BlockType, замещающий плотный блок при
 * генерации (п.5), а не bool-флаг «hasOre» на обычном блоке: тогда вся
 * логика (текстура, дроп, отрисовка) остаётся единой и ходит через type.
 */
public enum BlockType {
    AIR("air", false, 0, null, false),

    // Базовая порода слоёв. Руда — не отдельный тип блока, а «маска» поверх
    // породы (Block.ore): иначе медь, найденная в ядре, выглядела бы куском
    // камня — текстура-то от родного слоя. Так порода всегда своя, а руда
    // просто дорисовывается сверху.
    // Прочность снижена под демо-темп (короче забег) — см. game.Constants.
    DIRT("block_dirt", true, 6, null, true),
    STONE("block_stone", true, 13, OreType.STONE, true),
    DEEPSLATE("block_deepslate", true, 22, null, true),
    HOT_ROCK("block_hotrock", true, 34, null, true),
    CORE_ROCK("block_core", true, 48, null, true),

    // Особые
    GRAVEL("block_gravel", true, 10, null, true),
    LAVA("block_lava", false, 0, null, false),          // не копается (п.6)
    BEDROCK("block_bedrock", true, 0, null, false),     // граница мира, и стены/пол финальной комнаты (п.6)
    /** Ставится игроком: сквозь неё ходят и по ней лазают, но она не держит (п.8). */
    LADDER("ladder", false, 4, null, true),
    /**
     * Мостик — то же самое, что лестница, только горизонтально (доп.): опора
     * проверяется по бокам, а не сверху/снизу, и это просто твёрдый блок, а
     * не отдельная логика перемещения внутри него.
     */
    BRIDGE("bridge", true, 4, null, true);

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
        return breakable && this != LAVA && this != LADDER && this != BRIDGE;
    }
}
