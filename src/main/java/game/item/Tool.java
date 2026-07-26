package game.item;

import game.Constants;

/**
 * Единственный инструмент игрока — кирка, апгрейдится по тирам (п.3).
 * Отдельный класс (а не голый int в Player) — чтобы добавление других
 * инструментов потом было правкой в одном месте, а не рефактором.
 */
public class Tool {
    /**
     * Апгрейд требует не только денег, но и материала — того же типа руды,
     * что и следующий тир (стальная логика: без камня в кармане каменную
     * кирку не купить, даже с деньгами). Числа заведомо ниже лимита переноски
     * ПРЕДЫДУЩЕГО тира (см. OreType.carryLimitByTier), иначе требование было
     * бы физически невыполнимым — софт-лок.
     */
    private static final OreType[] UPGRADE_MATERIAL = {null, OreType.STONE, OreType.COPPER, OreType.IRON, OreType.DIAMOND};
    private static final int[] UPGRADE_MATERIAL_AMOUNT = {0, 8, 8, 8, 4};

    private int level; // 0 дерево .. 4 алмаз

    public Tool(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public String getName() {
        return Constants.TOOL_NAMES[level] + " pickaxe";
    }

    /** Иконка/спрайт кирки текущего тира — файлы pickaxe-wooden..pickaxe-diamond (п.4). */
    public String getIcon() {
        return "pickaxe-" + Constants.TOOL_NAMES[level].toLowerCase();
    }

    public double getDigSpeed() {
        return Constants.DIG_SPEED[level];
    }

    /** Лимит переноски на конкретный тип руды — своя прогрессия у каждой (п.1, п.3). */
    public int getOreCarryLimit(OreType ore) {
        return ore.carryLimit(level);
    }

    public boolean isMaxed() {
        return level >= Constants.MAX_TOOL_TIER;
    }

    public int getUpgradePrice() {
        return isMaxed() ? 0 : Constants.TOOL_UPGRADE_PRICE[level + 1];
    }

    public String getNextName() {
        return isMaxed() ? "-" : Constants.TOOL_NAMES[level + 1];
    }

    /** Материал, нужный для следующего апгрейда — null, если кирка уже максимальная. */
    public OreType getUpgradeMaterial() {
        return isMaxed() ? null : UPGRADE_MATERIAL[level + 1];
    }

    /** Сколько единиц материала нужно на руках (не считая уже потраченного на продажу). */
    public int getUpgradeMaterialAmount() {
        return isMaxed() ? 0 : UPGRADE_MATERIAL_AMOUNT[level + 1];
    }

    /** Иконка кирки, которую можно купить следующей — для витрины у кузнеца (п.4, доп.). */
    public String getNextIcon() {
        return isMaxed() ? getIcon() : "pickaxe-" + Constants.TOOL_NAMES[level + 1].toLowerCase();
    }

    public void upgrade() {
        if (!isMaxed()) level++;
    }
}
