package tools;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Плейсхолдеры трещин для анимации ломания блока: textures/break/break1..4.png.
 *
 * Отдельно от TextureGen, потому что тот перезаписывает ВЕСЬ пак, а живой арт
 * трогать нельзя. Этот пишет только папку break/ — нарисованные трещины просто
 * кладутся поверх файлов с теми же именами.
 *
 * Запуск: mvn compile exec:java -Dexec.mainClass=tools.CrackGen
 */
public final class CrackGen {
    private static final int SIZE = 16;      // как блоки в текущем паке
    private static final int STAGES = 4;

    public static void main(String[] args) throws IOException {
        File dir = new File("src/main/resources/textures/break");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Не удалось создать " + dir);
        }

        for (int stage = 1; stage <= STAGES; stage++) {
            BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
            Random rnd = new Random(1234);   // одно зерно: трещины растут, а не пляшут

            // с каждой стадией добавляем ещё одну ветку от центра
            int branches = stage + 1;
            for (int b = 0; b < branches; b++) {
                double angle = (Math.PI * 2 / branches) * b + rnd.nextDouble() * 0.6;
                double len = (SIZE / 2.0) * (0.35 + 0.18 * stage);
                crack(img, SIZE / 2.0, SIZE / 2.0, angle, len, rnd);
            }
            write(img, dir, "break" + stage);
        }
        System.out.println("Трещины записаны в " + dir.getAbsolutePath());
    }

    /** Ломаная линия от центра наружу, слегка виляющая. */
    private static void crack(BufferedImage img, double x, double y,
                              double angle, double len, Random rnd) {
        for (double step = 0; step < len; step += 0.7) {
            angle += (rnd.nextDouble() - 0.5) * 0.5;
            x += Math.cos(angle) * 0.7;
            y += Math.sin(angle) * 0.7;

            plot(img, (int) Math.round(x), (int) Math.round(y), 0xE0101010);
            // подсветка снизу-справа, чтобы скол читался объёмнее
            plot(img, (int) Math.round(x) + 1, (int) Math.round(y), 0x60FFFFFF);
        }
    }

    private static void plot(BufferedImage img, int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) return;
        if ((img.getRGB(x, y) >>> 24) != 0) return;   // не затираем уже нарисованное
        img.setRGB(x, y, argb);
    }

    private static void write(BufferedImage img, File dir, String name) throws IOException {
        ImageIO.write(img, "png", new File(dir, name + ".png"));
    }
}
