package game.entity;

import game.Constants;
import game.render.Textures;
import game.world.Field;

import java.awt.Graphics2D;

/**
 * Динамит (п.3): ставится, тикает фитиль, потом сносит блоки в радиусе.
 * Урон игроку — от расстояния до эпицентра (п.6), а не фиксированный.
 */
public class Dynamite {
    private final double tileX;
    private final double tileY;
    private double fuse = Constants.DYNAMITE_FUSE;
    private boolean exploded;

    public Dynamite(double tileX, double tileY) {
        this.tileX = tileX;
        this.tileY = tileY;
    }

    public boolean isExploded() {
        return exploded;
    }

    public void tick(double dt, Field field, Player player) {
        if (exploded) return;
        fuse -= dt;
        if (fuse > 0) return;

        exploded = true;
        explode(field, player);
    }

    private void explode(Field field, Player player) {
        int r = (int) Math.ceil(Constants.DYNAMITE_RADIUS);
        int cx = (int) Math.floor(tileX);
        int cy = (int) Math.floor(tileY);

        for (int x = cx - r; x <= cx + r; x++) {
            for (int y = cy - r; y <= cy + r; y++) {
                double dx = x - tileX;
                double dy = y - tileY;
                if (Math.sqrt(dx * dx + dy * dy) <= Constants.DYNAMITE_RADIUS) {
                    field.breakBlock(x, y);
                }
            }
        }

        // урон по расстоянию: максимум в эпицентре, 0 на границе радиуса
        double dist = player.distanceToTile(tileX, tileY);
        if (dist <= Constants.DYNAMITE_RADIUS) {
            double falloff = 1.0 - dist / Constants.DYNAMITE_RADIUS;
            int damage = (int) Math.round(Constants.DYNAMITE_MAX_DAMAGE * falloff);
            if (damage > 0) player.damage(player.reduceByArmor(damage), "blown up");
        }
    }

    public void draw(Graphics2D g, double camX, double camY) {
        if (exploded) return;
        int scale = Constants.SCALE;
        int size = Constants.TILE * scale;
        int sx = (int) Math.round((tileX * Constants.TILE - camX) * scale);
        int sy = (int) Math.round((tileY * Constants.TILE - camY) * scale);
        g.drawImage(Textures.get("dynamite"), sx, sy, size, size, null);
    }
}
