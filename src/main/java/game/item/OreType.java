package game.item;

/** Ресурсы, которые игрок таскает и продаёт (цены — п.14). */
public enum OreType {
    STONE("Stone", 1, "icon_stone", null, 0),
    COAL("Coal", 3, "icon_coal", "ore_coal", 4),
    COPPER("Copper", 5, "icon_copper", "ore_copper", 6),
    IRON("Iron", 10, "icon_iron", "ore_iron", 10),
    GOLD("Gold", 20, "icon_gold", "ore_gold", 12),
    DIAMOND("Diamond", 50, "icon_diamond", "ore_diamond", 20);

    public final String displayName;
    public final int price;
    public final String icon;
    /**
     * Текстура-оверлей с прозрачным фоном: рисуется поверх породы того слоя,
     * где руда лежит. У камня оверлея нет — он и есть порода.
     */
    public final String overlay;
    /** Насколько блок с этой рудой прочнее чистой породы. */
    public final int extraDurability;

    OreType(String displayName, int price, String icon, String overlay, int extraDurability) {
        this.displayName = displayName;
        this.price = price;
        this.icon = icon;
        this.overlay = overlay;
        this.extraDurability = extraDurability;
    }
}
