package game.npc;

import game.entity.Player;
import game.item.Tool;

/** Кузнец: апгрейд кирки на следующий тир, выбирать тоже нечего (п.9). */
public class ToolsmithNpc extends NpcPoint {
    public ToolsmithNpc(int tileX, int tileY) {
        super(tileX, tileY, "npc_smith", "Toolsmith");
    }

    @Override
    public String prompt(Player p) {
        Tool tool = p.getTool();
        if (tool.isMaxed()) return "Toolsmith: pickaxe is maxed out";
        return "E - upgrade to " + tool.getNextName() + " pickaxe ($" + tool.getUpgradePrice() + ")";
    }

    @Override
    public void interact(Player p) {
        Tool tool = p.getTool();
        if (tool.isMaxed()) return;
        int price = tool.getUpgradePrice();
        if (p.getMoney() < price) return;
        p.spendMoney(price);
        tool.upgrade();
    }
}
