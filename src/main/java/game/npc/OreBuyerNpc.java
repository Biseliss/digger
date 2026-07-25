package game.npc;

import game.entity.Player;
import game.item.OreType;
import java.util.Map;

/** Скупщик руды: одно нажатие E продаёт всё разом, выбор товара не нужен (п.9). */
public class OreBuyerNpc extends NpcPoint {
    private String lastMessage = "";

    public OreBuyerNpc(int tileX, int tileY) {
        super(tileX, tileY, "npc_buyer", "Ore buyer");
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

    @Override
    public void interact(Player p) {
        int earned = p.sellAllOre();
        lastMessage = earned > 0 ? "Sold for $" + earned : "Nothing to sell";
    }

    public String getLastMessage() {
        return lastMessage;
    }
}
