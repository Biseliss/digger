package game.render;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Transparency;
import java.awt.image.BufferedImage;

/**
 * Создание картинок в формате, совместимом с экраном.
 *
 * Это не косметика: Java2D умеет держать такие изображения в видеопамяти и
 * блитить их аппаратно. Обычный `new BufferedImage(...)` с «неродным» форматом
 * заставляет каждый кадр конвертировать пиксели на CPU — в окне это стоит
 * гораздо дороже, чем в офлайн-рендере, поэтому бенчмарк такую разницу
 * почти не показывает, а живая игра проседает.
 */
public final class Images {
    private static final GraphicsConfiguration GC = findConfig();

    private Images() {}

    private static GraphicsConfiguration findConfig() {
        try {
            if (GraphicsEnvironment.isHeadless()) return null;
            return GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration();
        } catch (Throwable t) {
            return null; // headless-сборка или нет дисплея — не беда, откатимся
        }
    }

    public static BufferedImage createTranslucent(int w, int h) {
        if (GC != null) {
            try {
                return GC.createCompatibleImage(w, h, Transparency.TRANSLUCENT);
            } catch (Throwable ignored) {
                // упадём в общий путь ниже
            }
        }
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    /** Перегоняет загруженный PNG в экранный формат, чтобы блит был ускоренным. */
    public static BufferedImage toCompatible(BufferedImage src) {
        if (GC == null || src == null) return src;
        try {
            BufferedImage out = GC.createCompatibleImage(
                    src.getWidth(), src.getHeight(), Transparency.TRANSLUCENT);
            var g = out.createGraphics();
            try {
                g.drawImage(src, 0, 0, null);
            } finally {
                g.dispose();
            }
            return out;
        } catch (Throwable t) {
            return src;
        }
    }
}
