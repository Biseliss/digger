package game.npc;

import game.Constants;
import game.entity.Player;
import game.render.Textures;

import java.awt.Graphics2D;

/**
 * Точка взаимодействия на базе (п.9): NPC за прилавком, «подойти + E».
 * Фон магазина рисуется отдельно самой базой, а не каждым NPC.
 */
public abstract class NpcPoint {
    public final int tileX;
    public final int tileY;
    private final String texture;
    private final String title;

    protected NpcPoint(int tileX, int tileY, String texture, String title) {
        this.tileX = tileX;
        this.tileY = tileY;
        this.texture = texture;
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public boolean isPlayerInRange(Player p) {
        return p.distanceToTile(tileX + 0.5, tileY + 1) <= Constants.INTERACT_RANGE;
    }

    /** Что показать в подсказке над игроком, пока он рядом. */
    public abstract String prompt(Player p);

    /** Нажали E. */
    public abstract void interact(Player p);

    public void draw(Graphics2D g, double camX, double camY) {
        int scale = Constants.SCALE;
        int sx = (int) Math.round((tileX * Constants.TILE - camX) * scale);
        int sy = (int) Math.round((tileY * Constants.TILE - camY) * scale);
        g.drawImage(Textures.get(texture), sx, sy,
                Constants.PLAYER_W * scale, Constants.PLAYER_H * scale, null);
    }
}
