package game.item;

/**
 * Расходники и снаряжение у торговца утилитами (п.3, п.9).
 * TORCH и ARMOR — пассивные (важен сам факт наличия), DYNAMITE и LADDER —
 * расходуются по штуке.
 */
public enum UtilityType {
    // lantern/icon_ladder — уже готовые нарисованные иконки (п.4). Armor пока
    // оставлен плейсхолдером — под него будет отдельный костюм позже.
    TORCH("Torch", 30, "lantern", true, 1, 1),
    ARMOR("Armor", 250, "icon_armor", true, 1, 1),
    DYNAMITE("Dynamite", 40, "icon_dynamite", false, 5, 1),
    /** Лестницы продаются пачкой и стакаются — это расходный стройматериал (п.8). */
    LADDER("Ladder", 60, "icon_ladder", false,
            game.Constants.LADDER_CARRY_LIMIT, game.Constants.LADDER_PURCHASE_AMOUNT);

    public final String displayName;
    public final int price;
    public final String icon;
    /** Пассивные покупаются один раз и не тратятся. */
    public final boolean passive;
    public final int carryLimit;
    /** Сколько штук даёт одна покупка. */
    public final int purchaseAmount;

    UtilityType(String displayName, int price, String icon, boolean passive,
                int carryLimit, int purchaseAmount) {
        this.displayName = displayName;
        this.price = price;
        this.icon = icon;
        this.passive = passive;
        this.carryLimit = carryLimit;
        this.purchaseAmount = purchaseAmount;
    }
}
