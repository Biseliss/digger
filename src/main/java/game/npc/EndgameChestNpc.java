package game.npc;

import game.entity.Player;

/**
 * Сундук с сокровищами в финальной комнате (п.6): заглушка без текстуры —
 * рисуется «недостающей» текстурой движка, пока арт не готов, но логика уже
 * рабочая. Открытие (E) завершает игру.
 */
public class EndgameChestNpc extends NpcPoint {
    private final Runnable onOpen;
    private boolean opened;

    public EndgameChestNpc(int tileX, int tileY, Runnable onOpen) {
        super(tileX, tileY, "chest", "Treasure chest", 2, 2);
        this.onOpen = onOpen;
    }

    @Override
    public String prompt(Player p) {
        return opened ? "" : "E - open the chest";
    }

    @Override
    public boolean interact(Player p) {
        if (opened) return false;
        opened = true;
        onOpen.run();
        return false;   // не покупка — эффекта монет тут не нужно (п.3)
    }
}
