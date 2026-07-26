package game.npc;

import game.entity.Player;
import game.item.OreType;
import java.util.Map;

/**
 * Скупщик руды (п.9). Раньше продавал только всё разом молча — без звука и
 * эффекта монет игроков сбивало с толку (не понятно, сработало или нет), а
 * выбора не было вовсе. Теперь Q/колесо листает режим — "Sell ALL" и по
 * одному режиму на каждый тип руды, — а успешная продажа тоже считается
 * "покупкой" для эффекта монет и звука (Game.playPurchaseEffect), раз уж
 * деньги в кармане реально прибавились.
 */
public class OreBuyerNpc extends NpcPoint {
    private static final OreType[] ORES = OreType.values();
    /** -1 = "продать всё", иначе индекс в ORES — конкретная руда. */
    private static final int MODE_ALL = -1;

    private String lastMessage = "";
    private int mode = MODE_ALL;

    public OreBuyerNpc(int tileX, int tileY) {
        // krill лежит горизонтально — не портретные 1x2, а 2x1 (п.3)
        super(tileX, tileY, "npc/krill/krill", "Ore buyer", 2, 1);
        setOverheadIcon(() -> mode == MODE_ALL ? "cash" : ORES[mode].icon);
    }

    /** Q/колесо — листаем режим продажи: ALL, потом по одной руде (доп.). */
    @Override
    public void cycle(int direction) {
        int total = ORES.length + 1;
        mode = Math.floorMod(mode + 1 + direction, total) - 1;
    }

    private int valueOf(Player p, OreType ore) {
        return p.getOreCount(ore) * ore.price;
    }

    private int totalValue(Player p) {
        int total = 0;
        for (Map.Entry<OreType, Integer> e : p.getCarriedOre().entrySet()) {
            total += e.getKey().price * e.getValue();
        }
        return total;
    }

    @Override
    public String prompt(Player p) {
        if (mode == MODE_ALL) {
            int total = totalValue(p);
            return "Q/wheel - Sell ALL ($" + total + ")  |  E - sell";
        }
        OreType ore = ORES[mode];
        int count = p.getOreCount(ore);
        return "Q/wheel - Sell all " + ore.displayName + " (" + count + ", $" + valueOf(p, ore) + ")  |  E - sell";
    }

    /** @return true, только если реально что-то продали — тогда играют монетки и звук. */
    @Override
    public boolean interact(Player p) {
        int earned = mode == MODE_ALL ? p.sellAllOre() : p.sellOre(ORES[mode]);
        if (earned <= 0) {
            lastMessage = "Nothing to sell";
            setError("Nothing to sell");
            return false;
        }
        lastMessage = "Sold for $" + earned;
        return true;
    }

    public String getLastMessage() {
        return lastMessage;
    }
}
