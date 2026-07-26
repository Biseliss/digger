package game.item;

/** Ресурсы, которые игрок таскает и продаёт (цены — п.14). */
public enum OreType {
    // лимит переноски задан по тирам инструмента (0..MAX_TOOL_TIER) отдельно для
    // каждой руды: чем реже/дороже руда, тем меньше лимит и тем мельче шаг роста —
    // прогрессия у каждой своя, а не общий множитель на всех (п.1)
    STONE("Stone", 1, "icon_stone", null, 0, new int[]{20, 28, 38, 50, 64}),
    COAL("Coal", 3, "icon_coal", "ore_coal", 4, new int[]{14, 18, 23, 29, 36}),
    COPPER("Copper", 5, "icon_copper", "ore_copper", 6, new int[]{10, 13, 17, 22, 28}),
    IRON("Iron", 10, "icon_iron", "ore_iron", 10, new int[]{7, 9, 12, 16, 21}),
    GOLD("Gold", 20, "icon_gold", "ore_gold", 12, new int[]{5, 6, 8, 11, 15}),
    DIAMOND("Diamond", 50, "icon_diamond", "ore_diamond", 20, new int[]{3, 4, 5, 7, 10});

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
    /** Лимит переноски по тиру инструмента (индекс = Tool.getLevel()). */
    private final int[] carryLimitByTier;

    OreType(String displayName, int price, String icon, String overlay, int extraDurability,
            int[] carryLimitByTier) {
        this.displayName = displayName;
        this.price = price;
        this.icon = icon;
        this.overlay = overlay;
        this.extraDurability = extraDurability;
        this.carryLimitByTier = carryLimitByTier;
    }

    public int carryLimit(int toolLevel) {
        int i = Math.max(0, Math.min(carryLimitByTier.length - 1, toolLevel));
        return carryLimitByTier[i];
    }
}
