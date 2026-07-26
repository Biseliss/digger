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
        BufferedImage sprite;
    }

    private final List<Particle> particles = new ArrayList<>();
    private final Random rnd = new Random();

    /** Разлёт осколков на месте разрушенного блока. */
    public void burst(int tileX, int tileY, BlockType type) {
        BufferedImage tex = Textures.get(type.texture);
        int pieceSrc = Math.max(1, tex.getWidth() / PIECE_DIVISOR);

        for (int i = 0; i < Constants.PARTICLES_PER_BLOCK; i++) {
            Particle p = new Particle();
            p.x = tileX * Constants.TILE + rnd.nextDouble() * Constants.TILE;
            p.y = tileY * Constants.TILE + rnd.nextDouble() * Constants.TILE;
            p.vx = (rnd.nextDouble() - 0.5) * 40;
            p.vy = -rnd.nextDouble() * 45;                 // сначала подбрасывает
            p.life = Constants.PARTICLE_LIFETIME * (0.6 + rnd.nextDouble() * 0.4);

            // случайный кусочек текстуры блока
            int sx = rnd.nextInt(Math.max(1, tex.getWidth() - pieceSrc + 1));
            int sy = rnd.nextInt(Math.max(1, tex.getHeight() - pieceSrc + 1));
            p.sprite = tex.getSubimage(sx, sy, pieceSrc, pieceSrc);

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
        int size = Math.max(1, Constants.TILE / PIECE_DIVISOR) * scale;
        var oldComposite = g.getComposite();

        for (Particle p : particles) {
            // к концу жизни осколок растворяется
            float alpha = (float) Math.max(0, Math.min(1, p.life / Constants.PARTICLE_LIFETIME));
            g.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, alpha));

            int sx = (int) Math.round((p.x - camX) * scale);
            int sy = (int) Math.round((p.y - camY) * scale);
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
