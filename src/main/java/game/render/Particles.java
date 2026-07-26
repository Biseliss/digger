package game.render;

import game.Constants;
import game.world.BlockType;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Осколки от разрушенных блоков: разлетаются, падают и гаснут.
 *
 * Отдельных ассетов не требуют — осколок это кусочек текстуры самого блока,
 * поэтому земля сыплется землёй, а камень камнем без единого нового файла.
 */
public final class Particles {
    /** Во сколько раз осколок мельче тайла. */
    private static final int PIECE_DIVISOR = 3;

    private static final class Particle {
        double x, y;        // мировые пиксели
        double vx, vy;
        double life;
        double maxLife;
        BufferedImage sprite;
        /** Размер отрисовки в тайлах — у осколков блока мельче, у монет ближе к целому тайлу. */
        double sizeTiles;
    }

    private final List<Particle> particles = new ArrayList<>();
    private final Random rnd = new Random();

    /** Сколько монеток сыплется за одну успешную покупку. */
    private static final int CASH_PARTICLES = 7;

    /** Разлёт осколков на месте разрушенного блока. */
    public void burst(int tileX, int tileY, BlockType type) {
        BufferedImage tex = Textures.get(type.texture);
        int pieceSrc = Math.max(1, tex.getWidth() / PIECE_DIVISOR);
        double sizeTiles = 1.0 / PIECE_DIVISOR;

        for (int i = 0; i < Constants.PARTICLES_PER_BLOCK; i++) {
            Particle p = new Particle();
            p.x = tileX * Constants.TILE + rnd.nextDouble() * Constants.TILE;
            p.y = tileY * Constants.TILE + rnd.nextDouble() * Constants.TILE;
            p.vx = (rnd.nextDouble() - 0.5) * 40;
            p.vy = -rnd.nextDouble() * 45;                 // сначала подбрасывает
            p.life = Constants.PARTICLE_LIFETIME * (0.6 + rnd.nextDouble() * 0.4);
            p.maxLife = p.life;
            p.sizeTiles = sizeTiles;

            // случайный кусочек текстуры блока
            int sx = rnd.nextInt(Math.max(1, tex.getWidth() - pieceSrc + 1));
            int sy = rnd.nextInt(Math.max(1, tex.getHeight() - pieceSrc + 1));
            p.sprite = tex.getSubimage(sx, sy, pieceSrc, pieceSrc);

            particles.add(p);
        }
    }

    /**
     * Монетки после удачной покупки (п.3, доп.) — та же механика, что и у
     * осколков блока, только целыми картинками cash.png, а не нарезкой, и
     * без привязки к тайловой сетке — сыплются из точки в мировых пикселях.
     */
    public void burstCash(double worldX, double worldY) {
        BufferedImage tex = Textures.get("cash");

        for (int i = 0; i < CASH_PARTICLES; i++) {
            Particle p = new Particle();
            p.x = worldX + (rnd.nextDouble() - 0.5) * Constants.TILE * 2;
            p.y = worldY + (rnd.nextDouble() - 0.5) * Constants.TILE;
            p.vx = (rnd.nextDouble() - 0.5) * 30;
            p.vy = -rnd.nextDouble() * 55 - 15;            // подбрасывает повыше осколков
            p.life = Constants.PARTICLE_LIFETIME * (1.1 + rnd.nextDouble() * 0.6);
            p.maxLife = p.life;
            p.sizeTiles = 0.8;
            p.sprite = tex;

            particles.add(p);
        }
    }

    public void tick(double dt) {
        for (Iterator<Particle> it = particles.iterator(); it.hasNext(); ) {
            Particle p = it.next();
            p.life -= dt;
            if (p.life <= 0) {
                it.remove();
                continue;
            }
            p.vy += Constants.PARTICLE_GRAVITY * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
        }
    }

    public void draw(Graphics2D g, double camX, double camY) {
        if (particles.isEmpty()) return;

        int scale = Constants.SCALE;
        var oldComposite = g.getComposite();

        for (Particle p : particles) {
            // к концу жизни частица растворяется
            float alpha = (float) Math.max(0, Math.min(1, p.life / p.maxLife));
            g.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, alpha));

            int sx = (int) Math.round((p.x - camX) * scale);
            int sy = (int) Math.round((p.y - camY) * scale);
            int size = Math.max(1, (int) Math.round(p.sizeTiles * Constants.TILE * scale));
            g.drawImage(p.sprite, sx, sy, size, size, null);
        }
        g.setComposite(oldComposite);
    }

    public int count() {
        return particles.size();
    }

    public void clear() {
        particles.clear();
    }
}
