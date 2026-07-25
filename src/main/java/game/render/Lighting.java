package game.render;

import game.Constants;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

/**
 * Слой 1 из п.7 — круг света вокруг игрока.
 *
 * Раньше это был RadialGradientPaint, залитый на весь экран: градиент честно
 * считался для каждого из ~600 000 пикселей КАЖДЫЙ кадр и съедал ~85% времени
 * отрисовки. Теперь круг рисуется один раз в текстуру и дальше только блитится,
 * а всё, что вне круга, закрывается четырьмя сплошными прямоугольниками —
 * это уже почти бесплатно.
 *
 * Текстура пересоздаётся только при смене радиуса (слой/факел) или заметном
 * изменении прозрачности, поэтому в обычной игре — считанные разы за забег.
 */
public final class Lighting {
    /** Шаг квантования альфы: без него кэш сбрасывался бы на каждом кадре у поверхности. */
    private static final int ALPHA_STEP = 8;

    private static BufferedImage maskCache;
    private static int cachedRadiusPx = -1;
    private static int cachedAlpha = -1;

    private Lighting() {}

    /**
     * @param radiusTiles радиус круга света в тайлах (слой + бонус факела)
     * @param strength    0..1 — насколько глубоко игрок под землёй (плавный переход у поверхности)
     */
    public static void drawDarkness(Graphics2D g, int viewW, int viewH,
                                    double playerScreenX, double playerScreenY,
                                    double radiusTiles, double strength) {
        if (strength <= 0) return;

        int alpha = (int) Math.round(Constants.DARKNESS_ALPHA * Math.min(1, strength));
        alpha = Math.min(255, Math.round(alpha / (float) ALPHA_STEP) * ALPHA_STEP);
        int radiusPx = Math.max(1, (int) Math.round(radiusTiles * Constants.TILE * Constants.SCALE));

        BufferedImage mask = mask(radiusPx, alpha);
        int size = mask.getWidth();
        int x = (int) Math.round(playerScreenX) - size / 2;
        int y = (int) Math.round(playerScreenY) - size / 2;

        g.drawImage(mask, x, y, null);

        // всё за пределами круга — сплошная темнота, четырьмя заливками
        g.setColor(new Color(0, 0, 0, alpha));
        fill(g, 0, 0, viewW, y);                              // сверху
        fill(g, 0, y + size, viewW, viewH - (y + size));      // снизу
        fill(g, 0, y, x, size);                               // слева
        fill(g, x + size, y, viewW - (x + size), size);       // справа
    }

    private static void fill(Graphics2D g, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        g.fillRect(Math.max(0, x), Math.max(0, y), w, h);
    }

    private static BufferedImage mask(int radiusPx, int alpha) {
        if (maskCache != null && cachedRadiusPx == radiusPx && cachedAlpha == alpha) {
            return maskCache;
        }

        int size = radiusPx * 2;
        BufferedImage img = Images.createTranslucent(size, size);
        Graphics2D g = img.createGraphics();
        try {
            RadialGradientPaint paint = new RadialGradientPaint(
                    new Point2D.Double(size / 2.0, size / 2.0),
                    radiusPx,
                    new float[]{0.0f, 0.62f, 1.0f},
                    new Color[]{
                            new Color(0, 0, 0, 0),
                            new Color(0, 0, 0, alpha / 2),
                            new Color(0, 0, 0, alpha)},
                    MultipleGradientPaint.CycleMethod.NO_CYCLE);
            g.setPaint(paint);
            g.fillRect(0, 0, size, size);
        } finally {
            g.dispose();
        }

        maskCache = img;
        cachedRadiusPx = radiusPx;
        cachedAlpha = alpha;
        return img;
    }
}
