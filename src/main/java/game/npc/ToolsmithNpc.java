package game.npc;

import game.entity.Player;
import game.item.Tool;
import game.render.Animation;

/** Кузнец: апгрейд кирки на следующий тир, выбирать тоже нечего (п.9). */
public class ToolsmithNpc extends NpcPoint {
    /** Иконка следующей по тиру кирки — обновляется вживую по игроку (п.4, доп.). */
    private String nextIcon;

    public ToolsmithNpc(int tileX, int tileY) {
        super(tileX, tileY, "npc/raccoon/Raccoon1", "Toolsmith");
        setIdleAnimation(new Animation("npc/raccoon/Raccoon", 0.15));
        setOverheadIcon(() -> nextIcon);
    }

    @Override
    public void tick(double dt, Player player) {
        super.tick(dt, player);
        nextIcon = player.getTool().getNextIcon();
    }

    @Override
    public String prompt(Player p) {
        Tool tool = p.getTool();
        if (tool.isMaxed()) return "Toolsmith: pickaxe is maxed out";
        return "E - upgrade to " + tool.getNextName() + " pickaxe ($" + tool.getUpgradePrice() + ")";
    }

    @Override
    public boolean interact(Player p) {
        Tool tool = p.getTool();
        if (tool.isMaxed()) return false;
        if (!p.isGodMode()) {
            int price = tool.getUpgradePrice();
            if (p.getMoney() < price) return false;
            p.spendMoney(price);
        }
        tool.upgrade();
        return true;
    }
}
