package game.item;

/** Ресурсы, которые игрок таскает и продаёт (цены — п.14). */
public enum OreType {
    STONE("Stone", 1, "icon_stone"),
    COAL("Coal", 3, "icon_coal"),
    COPPER("Copper", 5, "icon_copper"),
    IRON("Iron", 10, "icon_iron"),
    GOLD("Gold", 20, "icon_gold"),
    DIAMOND("Diamond", 50, "icon_diamond");

    public final String displayName;
    public final int price;
    public final String icon;

    OreType(String displayName, int price, String icon) {
        this.displayName = displayName;
        this.price = price;
        this.icon = icon;
    }
}
