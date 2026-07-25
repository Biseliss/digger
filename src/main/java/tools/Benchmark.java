package tools;

import core.Input;
import game.Constants;
import game.Game;
import game.entity.Player;
import game.render.Lighting;
import game.world.Field;
import ui.DrawCtx;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Замер производительности рендера без окна.
 *
 * Два сценария, потому что они нагружают движок по-разному:
 *   A) игрок ходит по уже исследованному коридору — кэш чанков горячий;
 *   B) игрок идёт по НОВОЙ территории — туман войны раскрывается каждый кадр,
 *      чанки помечаются dirty и кэш пересобирается. Это и есть «фпс в жопе».
 *
 * Запуск: ./mvnw compile exec:java -Dexec.mainClass=tools.Benchmark
 */
public final class Benchmark {
    private static final int WARMUP = 60;
    private static final int FRAMES = 300;

    public static void main(String[] args) throws Exception {
        System.out.println("=== A: исследованная территория (кэш горячий) ===");
        measure(false);

        System.out.println();
        System.out.println("=== B: новая территория (туман раскрывается всё время) ===");
        measure(true);

        System.out.println();
        System.out.println("=== C: по этапам отрисовки, новая территория ===");
        stages();
    }

    private static void measure(boolean freshTerrain) {
        Game game = new Game(new Input(), Constants.WINDOW_W, Constants.WINDOW_H);
        Player p = game.getPlayer();
        BufferedImage target = new BufferedImage(Constants.WINDOW_W, Constants.WINDOW_H,
                BufferedImage.TYPE_INT_RGB);

        for (int i = 0; i < WARMUP; i++) step(game, p, target, i, freshTerrain);

        long t0 = System.nanoTime();
        for (int i = 0; i < FRAMES; i++) step(game, p, target, WARMUP + i, freshTerrain);
        double ms = (System.nanoTime() - t0) / 1_000_000.0 / FRAMES;

        System.out.printf("  %.2f мс/кадр  →  %.0f FPS%n", ms, 1000.0 / ms);
    }

    private static void step(Game game, Player p, BufferedImage target, int frame, boolean fresh) {
        place(p, frame, fresh);
        game.tick(1.0 / 60);
        Graphics2D g = target.createGraphics();
        try {
            game.draw(new DrawCtx(g, 0, 0));
        } finally {
            g.dispose();
        }
    }

    /** A — челнок по одному коридору; B — непрерывный уход в неисследованное. */
    private static void place(Player p, int frame, boolean fresh) {
        if (fresh) {
            int x = 10 + (int) (frame * 0.35) % (Constants.WORLD_W - 20);
            int y = Constants.SURFACE_Y + 20 + (int) (frame * 0.25);
            p.teleportToTile(x, Math.min(y, Constants.SURFACE_Y + Constants.LAYER_4_END - 5));
        } else {
            int span = 40;
            int offset = frame % (span * 2);
            p.teleportToTile(20 + (offset < span ? offset : span * 2 - offset),
                    Constants.SURFACE_Y + 60);
        }
    }

    /** Разбивка кадра по этапам — видно, кто именно ест время. */
    private static void stages() throws Exception {
        Game game = new Game(new Input(), Constants.WINDOW_W, Constants.WINDOW_H);
        Player p = game.getPlayer();

        java.lang.reflect.Field ff = Game.class.getDeclaredField("field");
        ff.setAccessible(true);
        Field field = (Field) ff.get(game);

        BufferedImage target = new BufferedImage(Constants.WINDOW_W, Constants.WINDOW_H,
                BufferedImage.TYPE_INT_RGB);
        int viewW = Constants.WINDOW_W;
        int viewH = Constants.WINDOW_H;

        for (int i = 0; i < WARMUP; i++) step(game, p, target, i, true);

        long revealNs = 0, worldNs = 0, lightNs = 0, hudNs = 0;
        for (int i = 0; i < FRAMES; i++) {
            place(p, WARMUP + i, true);

            long a = System.nanoTime();
            field.revealCircle(p.centerTileX(), p.centerTileY(), p.lightRadius() + 1);
            revealNs += System.nanoTime() - a;

            Graphics2D g = target.createGraphics();
            double camX = p.getX() - viewW / (double) Constants.SCALE / 2;
            double camY = p.getY() - viewH / (double) Constants.SCALE / 2;

            long b = System.nanoTime();
            field.draw(g, camX, camY, viewW, viewH);
            worldNs += System.nanoTime() - b;

            long c = System.nanoTime();
            Lighting.drawDarkness(g, viewW, viewH, viewW / 2.0, viewH / 2.0, p.lightRadius(), 1.0);
            lightNs += System.nanoTime() - c;

            long d = System.nanoTime();
            game.draw(new DrawCtx(g, 0, 0));
            hudNs += System.nanoTime() - d;

            g.dispose();
        }

        System.out.printf("  revealCircle:  %.2f мс/кадр%n", revealNs / 1_000_000.0 / FRAMES);
        System.out.printf("  field.draw:    %.2f мс/кадр%n", worldNs / 1_000_000.0 / FRAMES);
        System.out.printf("  освещение:     %.2f мс/кадр%n", lightNs / 1_000_000.0 / FRAMES);
        System.out.printf("  полный кадр:   %.2f мс/кадр%n", hudNs / 1_000_000.0 / FRAMES);
    }
}
