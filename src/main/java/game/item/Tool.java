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

    /** Иконка кирки, которую можно купить следующей — для витрины у кузнеца (п.4, доп.). */
    public String getNextIcon() {
        return isMaxed() ? getIcon() : "pickaxe-" + Constants.TOOL_NAMES[level + 1].toLowerCase();
    }

    public void upgrade() {
        if (!isMaxed()) level++;
    }
}
