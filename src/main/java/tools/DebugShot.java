package tools;

import core.Input;
import game.Constants;
import game.Game;
import ui.DrawCtx;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Дев-утилита: рендерит кадр игры с заданной глубины в PNG, не запуская окно.
 * Удобно смотреть, что выдал процген и как ложится свет, не проходя каждый раз
 * шахту руками.
 *
 * Запуск: ./mvnw compile exec:java -Dexec.mainClass=tools.DebugShot -Dexec.args="out 20 60 120 200"
 */
public final class DebugShot {
    public static void main(String[] args) throws Exception {
        String outDir = args.length > 0 ? args[0] : "target/debug-shots";
        File dir = new File(outDir);
        dir.mkdirs();

        int[] depths = new int[Math.max(0, args.length - 1)];
        for (int i = 1; i < args.length; i++) depths[i - 1] = Integer.parseInt(args[i]);
        if (depths.length == 0) depths = new int[]{0, 45, 100, 160, 225};

        Game game = new Game(new Input(), Constants.WINDOW_W, Constants.WINDOW_H, null);

        for (int depth : depths) {
            game.getPlayer().teleportToTile(Constants.WORLD_W / 2, Constants.SURFACE_Y + depth);
            // несколько тиков, чтобы раскрылся туман войны и камера доехала
            for (int i = 0; i < 3; i++) game.tick(1.0 / 60);

            BufferedImage img = new BufferedImage(Constants.WINDOW_W, Constants.WINDOW_H,
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            try {
                game.draw(new DrawCtx(g, 0, 0));
            } finally {
                g.dispose();
            }
            File out = new File(dir, "depth_" + depth + ".png");
            ImageIO.write(img, "png", out);
            System.out.println("записано " + out);
        }
    }
}
