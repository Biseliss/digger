package game.entity;

import game.Constants;
import game.item.OreType;
import game.render.Textures;
import game.world.Block;
import game.world.Field;

import java.awt.Graphics2D;

/**
 * Динамит (п.3): ставится, тикает фитиль, потом сносит блоки в радиусе.
 * Урон игроку — от расстояния до эпицентра (п.6), а не фиксированный.
 *
 * Всё, что выбито взрывом, идёт игроку в карман — иначе динамит был бы
 * чистым убытком: и заряд потратил, и руду потерял.
 */
public class Dynamite {
    private final double tileX;
    private final double tileY;
    private double fuse = Constants.DYNAMITE_FUSE;
    private boolean exploded;

    /** Сколько ещё показывать вспышку после срабатывания. */
    private double explosionTimer;

    public Dynamite(double tileX, double tileY) {
        this.tileX = tileX;
        this.tileY = tileY;
    }

    /** true, когда и взрыв отгремел, и анимация доиграла — можно убирать. */
    public boolean isFinished() {
        return exploded && explosionTimer <= 0;
    }

    public void tick(double dt, Field field, Player player) {
        if (exploded) {
            explosionTimer -= dt;
            return;
        }

        fuse -= dt;
        if (fuse > 0) return;

        exploded = true;
        explosionTimer = Constants.EXPLOSION_ANIM_TIME;
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
                if (Math.sqrt(dx * dx + dy * dy) > Constants.DYNAMITE_RADIUS) continue;

                for (Block broken : field.breakBlock(x, y)) {
                    collect(player, broken);
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

    /** Руду — в карман, свои лестницы — обратно в стак. */
    private static void collect(Player player, Block broken) {
        if (broken.getType() == game.world.BlockType.LADDER) {
            player.addUtility(game.item.UtilityType.LADDER);
            return;
        }
        OreType ore = broken.drop();
        if (ore != null) player.addOre(ore);
    }

    public void draw(Graphics2D g, double camX, double camY) {
        int scale = Constants.SCALE;
        int size = Constants.TILE * scale;
        int sx = (int) Math.round((tileX * Constants.TILE - camX) * scale);
        int sy = (int) Math.round((tileY * Constants.TILE - camY) * scale);

        if (!exploded) {
            g.drawImage(Textures.get("dynamite"), sx, sy, size, size, null);
            return;
        }
        if (explosionTimer <= 0) return;

        // вспышка на месте заряда: кадр выбирается по остатку таймера
        int frames = Constants.EXPLOSION_FRAMES;
        double progress = 1.0 - explosionTimer / Constants.EXPLOSION_ANIM_TIME;
        int frame = Math.min(frames - 1, (int) (progress * frames));

        int burst = (int) (Constants.DYNAMITE_RADIUS * 2 + 1) * size;
        g.drawImage(Textures.get("explosion_" + frame),
                sx + size / 2 - burst / 2, sy + size / 2 - burst / 2, burst, burst, null);
    }
}
