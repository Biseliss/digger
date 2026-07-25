package game.render;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Загрузка и кэш текстур (п.11). Всё грузится один раз при старте из
 * ресурсов classpath (/textures/<имя>.png) и дальше только рисуется.
 *
 * Если файла нет — подставляется заметная «пропущенная текстура», чтобы
 * игра не падала на полпути из-за одного недокинутого ассета.
 */
public final class Textures {
    private static final Map<String, BufferedImage> CACHE = new HashMap<>();
    private static final Map<String, BufferedImage> ROTATED = new HashMap<>();
    private static final Map<BufferedImage, java.awt.Rectangle> BOUNDS = new java.util.IdentityHashMap<>();

    private Textures() {}

    public static BufferedImage get(String name) {
        return CACHE.computeIfAbsent(name, Textures::load);
    }

    /**
     * Прямоугольник непрозрачной части картинки (x, y, w, h).
     *
     * Нужен для тайлов, которые обязаны заполнять клетку целиком — например,
     * лава: в текущем паке её кадры нарисованы с прозрачными боковыми
     * столбцами, и при отрисовке «как есть» между тайлами лужи видны щели.
     * Рисуя именно эту область на весь тайл, получаем сплошную жидкость, а
     * если кадры перерисуют во всю ширину — область совпадёт с картинкой и
     * поведение не изменится.
     */
    public static java.awt.Rectangle opaqueBounds(BufferedImage img) {
        return BOUNDS.computeIfAbsent(img, Textures::computeOpaqueBounds);
    }

    private static java.awt.Rectangle computeOpaqueBounds(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int minX = w, minY = h, maxX = -1, maxY = -1;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((img.getRGB(x, y) >>> 24) < 128) continue;
                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (x > maxX) maxX = x;
                if (y > maxY) maxY = y;
            }
        }
        if (maxX < 0) return new java.awt.Rectangle(0, 0, w, h);   // кадр пуст — рисуем как есть
        return new java.awt.Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    /**
     * Есть ли такой файл в /textures, без загрузки в кэш и без вывода
     * ошибки в stderr. Нужен, например, чтобы Animation могла перебирать
     * кадры "base_0", "base_1", ... пока они существуют.
     */
    public static boolean exists(String name) {
        return Textures.class.getResource("/textures/" + name + ".png") != null;
    }

    /**
     * Повёрнутый вариант текстуры (0/90/180/270).
     *
     * Повороты предрассчитываются один раз и кладутся в кэш: крутить каждый
     * тайл через AffineTransform прямо при сборке чанка — это тысячи
     * трансформаций на один пересчёт кэша.
     */
    public static BufferedImage get(String name, int rotation) {
        int rot = Math.floorMod(rotation, 360);
        if (rot == 0) return get(name);
        return ROTATED.computeIfAbsent(name + '#' + rot, k -> rotate(get(name), rot));
    }

    private static BufferedImage rotate(BufferedImage src, int degrees) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = Images.createTranslucent(w, h);
        java.awt.Graphics2D g = out.createGraphics();
        try {
            g.rotate(Math.toRadians(degrees), w / 2.0, h / 2.0);
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static BufferedImage load(String name) {
        String path = "/textures/" + name + ".png";
        try (InputStream in = Textures.class.getResourceAsStream(path)) {
            if (in != null) {
                BufferedImage img = ImageIO.read(in);
                // сразу в экранный формат: иначе каждый блит конвертирует пиксели
                if (img != null) return Images.toCompatible(img);
            }
        } catch (IOException e) {
            System.err.println("Не удалось прочитать текстуру " + path + ": " + e.getMessage());
        }
        System.err.println("Текстура не найдена: " + path);
        return missing();
    }

    /** Ядовито-розовый шахматный квадрат — сразу видно, чего не хватает. */
    private static BufferedImage missing() {
        int size = game.Constants.TILE;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean odd = ((x / 2) + (y / 2)) % 2 == 0;
                img.setRGB(x, y, odd ? 0xFFFF00FF : 0xFF000000);
            }
        }
        return img;
    }
}
