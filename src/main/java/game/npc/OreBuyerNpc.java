package game.npc;

import game.entity.Player;
import game.item.OreType;
import java.util.Map;

/** Скупщик руды: одно нажатие E продаёт всё разом, выбор товара не нужен (п.9). */
public class OreBuyerNpc extends NpcPoint {
    /** Просто крутит иконки всех руд по кругу над головой — decor, ничего не выбирает (п.4, доп.). */
    private static final double ICON_CYCLE_SECONDS = 1.2;
    private static final OreType[] ORES = OreType.values();

    private String lastMessage = "";
    private double iconCycleTimer;
    private int iconIndex;

    public OreBuyerNpc(int tileX, int tileY) {
        // krill лежит горизонтально — не портретные 1x2, а 2x1 (п.3)
        super(tileX, tileY, "npc/krill/krill", "Ore buyer", 2, 1);
        setOverheadIcon(() -> ORES[iconIndex].icon);
    }

    @Override
    public void tick(double dt, Player player) {
        super.tick(dt, player);
        iconCycleTimer += dt;
        if (iconCycleTimer >= ICON_CYCLE_SECONDS) {
            iconCycleTimer -= ICON_CYCLE_SECONDS;
            iconIndex = (iconIndex + 1) % ORES.length;
        }
    }

    @Override
    public String prompt(Player p) {
        int total = 0;
        for (Map.Entry<OreType, Integer> e : p.getCarriedOre().entrySet()) {
            total += e.getKey().price * e.getValue();
        }
        if (total == 0) return "Ore buyer: nothing to sell";
        return "E - sell all ore for $" + total;
    }

    /** Продажа — не покупка, эффекта монет тут не будет (он только на тратах, п.3). */
    @Override
    public boolean interact(Player p) {
        int earned = p.sellAllOre();
        lastMessage = earned > 0 ? "Sold for $" + earned : "Nothing to sell";
        return false;
    }

    public String getLastMessage() {
        return lastMessage;
    }
}
