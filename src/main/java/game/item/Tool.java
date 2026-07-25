package game.item;

import game.Constants;

/**
 * Единственный инструмент игрока — кирка, апгрейдится по тирам (п.3).
 * Отдельный класс (а не голый int в Player) — чтобы добавление других
 * инструментов потом было правкой в одном месте, а не рефактором.
 */
public class Tool {
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

    public double getDigSpeed() {
        return Constants.DIG_SPEED[level];
    }

    /** Лимит переноски на каждый тип руды отдельно (п.3). */
    public int getOreCarryLimit() {
        return Constants.ORE_CARRY_BASE + Constants.ORE_CARRY_PER_TIER * level;
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

    public void upgrade() {
        if (!isMaxed()) level++;
    }
}
