package game.npc;

import game.entity.Player;
import game.item.OreType;
import game.item.Tool;
import game.render.Animation;

/**
 * Кузнец: апгрейд кирки на следующий тир (п.9). Апгрейд стоит не только
 * денег, но и материала того же тира (п.3, доп.) — кирку куют из руды,
 * которую в неё вкладывают, а не покупают за одни деньги.
 */
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
        OreType material = tool.getUpgradeMaterial();
        int needed = tool.getUpgradeMaterialAmount();
        return "E - upgrade to " + tool.getNextName() + " pickaxe ($" + tool.getUpgradePrice()
                + " + " + needed + " " + material.displayName + ")";
    }

    @Override
    public boolean interact(Player p) {
        Tool tool = p.getTool();
        if (tool.isMaxed()) return false;
        if (p.isGodMode()) {
            tool.upgrade();
            return true;
        }

        int price = tool.getUpgradePrice();
        OreType material = tool.getUpgradeMaterial();
        int needed = tool.getUpgradeMaterialAmount();
        boolean hasMoney = p.getMoney() >= price;
        boolean hasMaterial = p.getOreCount(material) >= needed;

        if (!hasMoney && !hasMaterial) {
            setError("Need $" + price + " and " + needed + " " + material.displayName);
            return false;
        }
        if (!hasMoney) {
            setError("Not enough money (need $" + price + ")");
            return false;
        }
        if (!hasMaterial) {
            setError("Not enough " + material.displayName + " (need " + needed + ")");
            return false;
        }

        p.spendMoney(price);
        p.consumeOre(material, needed);
        tool.upgrade();
        return true;
    }
}
