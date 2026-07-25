package game.npc;

import game.entity.Player;
import game.item.UtilityType;

import java.util.ArrayList;
import java.util.List;

/**
 * Торговец утилитами (п.9). Здесь товаров несколько, поэтому пока игрок рядом,
 * выбранный товар листается на Q и колесо мыши (стрелки заняты движением),
 * покупка — E.
 */
public class UtilityShopNpc extends NpcPoint {
    /** Одна позиция в списке товаров. */
    public record UtilityOffer(UtilityType type, int price) {}

    private final List<UtilityOffer> offers = new ArrayList<>();
    private int selectedIndex;

    public UtilityShopNpc(int tileX, int tileY) {
        super(tileX, tileY, "npc_utility", "Utility shop");
        for (UtilityType type : UtilityType.values()) {
            offers.add(new UtilityOffer(type, type.price));
        }
    }

    public List<UtilityOffer> getOffers() {
        return offers;
    }

    public UtilityOffer selected() {
        return offers.get(selectedIndex);
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    /** direction = +1/-1, зацикленно. */
    public void cycle(int direction) {
        if (offers.isEmpty()) return;
        selectedIndex = Math.floorMod(selectedIndex + direction, offers.size());
    }

    @Override
    public String prompt(Player p) {
        UtilityOffer offer = selected();
        UtilityType type = offer.type();
        int have = p.getUtility(type);
        String name = type.purchaseAmount > 1
                ? type.displayName + " x" + type.purchaseAmount
                : type.displayName;
        String stock = type.passive
                ? (have > 0 ? " [owned]" : "")
                : " [" + have + "/" + type.carryLimit + "]";
        return "Q/wheel - " + name + " $" + offer.price() + stock + "  |  E - buy";
    }

    @Override
    public void interact(Player p) {
        UtilityOffer offer = selected();
        UtilityType type = offer.type();
        if (p.getMoney() < offer.price()) return;
        if (type.passive && p.getUtility(type) > 0) return;   // второй факел ни к чему
        if (p.addUtility(type, type.purchaseAmount)) {
            p.spendMoney(offer.price());
        }
    }
}
