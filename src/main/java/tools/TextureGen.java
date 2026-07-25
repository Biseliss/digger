package tools;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Генератор текстур в src/main/resources/textures.
 *
 * ВАЖНО: это по-прежнему сгенерированные кодом плейсхолдеры, а не рисованный
 * арт. Игра рисует готовые файлы (см. game.render.Textures) — нарисованный
 * ассет просто кладётся поверх файла с тем же именем, перезапускать генератор
 * не нужно.
 *
 * Размеры (п.11): блок 8x8, персонаж 8x16, иконка 16x16.
 * Оверлеи руд рисуются с прозрачным фоном — они ложатся поверх породы своего
 * слоя, поэтому одна текстура меди одинаково работает и в камне, и в ядре.
 *
 * Запуск: mvn compile exec:java -Dexec.mainClass=tools.TextureGen
 */
public final class TextureGen {
    private static final int TILE = 8;
    private static final int ICON = 16;

    public static void main(String[] args) throws IOException {
        File dir = new File("src/main/resources/textures");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Не удалось создать " + dir);
        }

        // --- порода: базовый цвет + шум + светлая кромка сверху ---
        rock(dir, "block_dirt", 0x7A4A24, 0x5C3418, 0x8E5C2E);
        rock(dir, "block_stone", 0x8A8A8A, 0x6A6A6A, 0x9E9E9E);
        rock(dir, "block_deepslate", 0x45454E, 0x2F2F36, 0x565660);
        rock(dir, "block_hotrock", 0x6E2A24, 0x4A1A16, 0x8A3A30);
        rock(dir, "block_core", 0x2C2333, 0x1A1420, 0x3E3247);
        rock(dir, "block_gravel", 0x968C82, 0x6E665E, 0xA89E94);
        rock(dir, "block_bedrock", 0x2A2A2A, 0x141414, 0x3A3A3A);
        lava(dir);

        // --- руды: ПРОЗРАЧНЫЕ оверлеи, только самородки ---
        oreOverlay(dir, "ore_coal", 0x1C1C1C, 0x000000);
        oreOverlay(dir, "ore_copper", 0xC87A3A, 0xE8A868);
        oreOverlay(dir, "ore_iron", 0xD8CFC4, 0xFFFFFF);
        oreOverlay(dir, "ore_gold", 0xF2C245, 0xFFE9A0);
        oreOverlay(dir, "ore_diamond", 0x5FE0DC, 0xC0FFFC);

        // --- объекты ---
        ladder(dir);
        dynamite(dir);
        door(dir);
        explosions(dir);

        // --- персонажи 8x16 ---
        character(dir, "player", 0x3E6FB0, 0xE8B98C, 0xF2C245);
        character(dir, "npc_buyer", 0x3F7A46, 0xE8B98C, 0x6B4A2A);
        character(dir, "npc_smith", 0x8A4B2A, 0xD8A87C, 0x8A8A8A);
        character(dir, "npc_utility", 0x6A4A8A, 0xE8B98C, 0xCC4444);

        // --- иконки 16x16 ---
        oreIcon(dir, "icon_stone", 0x8A8A8A, 0xA8A8A8);
        oreIcon(dir, "icon_coal", 0x2A2A2A, 0x4A4A4A);
        oreIcon(dir, "icon_copper", 0xC87A3A, 0xE8A868);
        oreIcon(dir, "icon_iron", 0xD8CFC4, 0xFFFFFF);
        oreIcon(dir, "icon_gold", 0xF2C245, 0xFFE9A0);
        oreIcon(dir, "icon_diamond", 0x5FE0DC, 0xC0FFFC);
        torchIcon(dir);
        armorIcon(dir);
        dynamiteIcon(dir);
        ladderIcon(dir);

        shop(dir);
        System.out.println("Текстуры записаны в " + dir.getAbsolutePath());
    }

    // ---------- порода ----------

    /** Камень: шумная заливка + подсветка верхней кромки и тень у нижней. */
    private static void rock(File dir, String name, int base, int dark, int light) throws IOException {
        BufferedImage img = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
        Random rnd = new Random(name.hashCode());

        for (int y = 0; y < TILE; y++) {
            for (int x = 0; x < TILE; x++) {
                int c = base;
                int roll = rnd.nextInt(100);
                if (roll < 22) c = dark;
                else if (roll < 34) c = light;
                img.setRGB(x, y, 0xFF000000 | jitter(c, rnd, 10));
            }
        }
        // верхняя кромка светлее, нижняя темнее — тайлы перестают сливаться в кашу
        for (int x = 0; x < TILE; x++) {
            img.setRGB(x, 0, 0xFF000000 | jitter(light, rnd, 6));
            img.setRGB(x, TILE - 1, 0xFF000000 | jitter(dark, rnd, 6));
        }
        write(img, dir, name);
    }

    private static void lava(File dir) throws IOException {
        BufferedImage img = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
        Random rnd = new Random(42);
        for (int y = 0; y < TILE; y++) {
            for (int x = 0; x < TILE; x++) {
                // сверху ярче — будто корка светится
                int c = y < 2 ? 0xFFC246 : (rnd.nextInt(100) < 30 ? 0xFFA53C : 0xE8631A);
                img.setRGB(x, y, 0xFF000000 | jitter(c, rnd, 12));
            }
        }
        write(img, dir, "block_lava");
    }

    // ---------- руды ----------

    /**
     * Оверлей руды: прозрачный фон + несколько «самородков» с бликом.
     * Раскладка фиксированная, чтобы кристаллы не наползали на края тайла.
     */
    private static void oreOverlay(File dir, String name, int gem, int shine) throws IOException {
        BufferedImage img = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
        int[][] blobs = {{1, 2}, {5, 1}, {3, 4}, {6, 5}, {2, 6}};

        for (int[] b : blobs) {
            int bx = b[0];
            int by = b[1];
            plot(img, bx, by, gem);
            plot(img, bx + 1, by, gem);
            plot(img, bx, by + 1, gem);
            plot(img, bx + 1, by + 1, gem);
            plot(img, bx, by, shine);          // блик в левом верхнем пикселе
        }
        write(img, dir, name);
    }

    private static void plot(BufferedImage img, int x, int y, int rgb) {
        if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) return;
        img.setRGB(x, y, 0xFF000000 | rgb);
    }

    // ---------- объекты ----------

    private static void ladder(File dir) throws IOException {
        BufferedImage img = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
        int wood = 0xA97C40;
        int woodDark = 0x7A5628;
        for (int y = 0; y < TILE; y++) {
            plot(img, 1, y, wood);
            plot(img, 2, y, woodDark);
            plot(img, 5, y, woodDark);
            plot(img, 6, y, wood);
        }
        for (int x = 2; x <= 5; x++) {   // перекладины
            plot(img, x, 2, wood);
            plot(img, x, 6, wood);
        }
        write(img, dir, "ladder");
    }

    private static void dynamite(File dir) throws IOException {
        BufferedImage img = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 3; y < 8; y++) {
            for (int x = 2; x < 6; x++) {
                plot(img, x, y, x == 2 ? 0x8E2A1C : 0xC7422E);
            }
        }
        for (int x = 2; x < 6; x++) plot(img, x, 5, 0xF2E4C0);  // светлая обвязка
        plot(img, 4, 2, 0x3A3A3A);
        plot(img, 5, 1, 0x8A6A2A);
        plot(img, 5, 0, 0xFFC83C);                              // искра
        write(img, dir, "dynamite");
    }

    private static void door(File dir) throws IOException {
        BufferedImage img = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < TILE; y++) {
            for (int x = 0; x < TILE; x++) {
                boolean frame = x == 0 || x == TILE - 1 || y == 0 || y == TILE - 1;
                plot(img, x, y, frame ? 0xB89A22 : 0xF2D544);
            }
        }
        plot(img, 5, 4, 0x6A5A10);
        plot(img, 2, 2, 0xFFF0A0);
        write(img, dir, "door");
    }

    /** Четыре кадра вспышки: разрастается и гаснет. */
    private static void explosions(File dir) throws IOException {
        int size = 32;
        int[][] palette = {
                {0xFFF3C0, 0xFFC83C, 0xFF8A2A},
                {0xFFD86A, 0xFF8A2A, 0xC7422E},
                {0xE8843C, 0xC7422E, 0x7A2A1C},
                {0x8A5A3C, 0x5A3A2A, 0x2A1A14},
        };
        double[] radii = {0.45, 0.75, 0.95, 1.0};
        int[] alphas = {255, 235, 190, 110};

        for (int f = 0; f < palette.length; f++) {
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Random rnd = new Random(1000 + f);
            double cx = size / 2.0;
            double cy = size / 2.0;
            double rMax = size / 2.0 * radii[f];

            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    double d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
                    double wobble = 1 + (rnd.nextDouble() - 0.5) * 0.25;
                    if (d > rMax * wobble) continue;

                    double t = d / rMax;
                    int c = t < 0.4 ? palette[f][0] : (t < 0.75 ? palette[f][1] : palette[f][2]);
                    img.setRGB(x, y, (alphas[f] << 24) | jitter(c, rnd, 8));
                }
            }
            write(img, dir, "explosion_" + f);
        }
    }

    // ---------- персонажи ----------

    private static void character(File dir, String name, int shirt, int skin, int hat) throws IOException {
        BufferedImage img = new BufferedImage(TILE, TILE * 2, BufferedImage.TYPE_INT_ARGB);

        // каска
        for (int x = 1; x < 7; x++) {
            plot(img, x, 0, hat);
            plot(img, x, 1, hat);
        }
        plot(img, 0, 1, hat);
        plot(img, 7, 1, hat);

        // лицо
        for (int y = 2; y < 6; y++) {
            for (int x = 2; x < 6; x++) plot(img, x, y, skin);
        }
        plot(img, 2, 3, 0x1A1A1A);
        plot(img, 5, 3, 0x1A1A1A);
        plot(img, 3, 5, darken(skin, 40));   // намёк на рот/подбородок

        // корпус с более тёмным боком — объём
        for (int y = 6; y < 12; y++) {
            for (int x = 1; x < 7; x++) {
                plot(img, x, y, x <= 2 ? darken(shirt, 35) : shirt);
            }
        }
        // руки
        for (int y = 7; y < 11; y++) {
            plot(img, 0, y, darken(shirt, 55));
            plot(img, 7, y, darken(shirt, 20));
        }
        plot(img, 0, 11, skin);
        plot(img, 7, 11, skin);

        // ноги
        for (int y = 12; y < 16; y++) {
            for (int x = 1; x < 4; x++) plot(img, x, y, 0x33383F);
            for (int x = 4; x < 7; x++) plot(img, x, y, 0x262A30);
        }
        for (int x = 1; x < 7; x++) plot(img, x, 15, 0x15181C);   // подошва

        write(img, dir, name);
    }

    // ---------- иконки ----------

    private static void oreIcon(File dir, String name, int color, int shine) throws IOException {
        BufferedImage img = new BufferedImage(ICON, ICON, BufferedImage.TYPE_INT_ARGB);
        // огранённый камешек, а не просто квадрат
        int[][] rows = {
                {5, 10, 3}, {4, 11, 4}, {3, 12, 5}, {3, 12, 6},
                {3, 12, 7}, {4, 11, 8}, {5, 10, 9}, {6, 9, 10},
        };
        for (int[] r : rows) {
            for (int x = r[0]; x <= r[1]; x++) {
                plot(img, x, r[2], color);
            }
        }
        for (int x = 5; x <= 7; x++) plot(img, x, 4, shine);
        plot(img, 4, 5, shine);
        outline(img, 0x101010);
        write(img, dir, name);
    }

    /** Тонкая тёмная обводка вокруг непрозрачных пикселей — иконки читаются на любом фоне. */
    private static void outline(BufferedImage img, int color) {
        int w = img.getWidth();
        int h = img.getHeight();
        boolean[][] solid = new boolean[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) solid[x][y] = (img.getRGB(x, y) >>> 24) != 0;
        }
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (solid[x][y]) continue;
                boolean near = (x > 0 && solid[x - 1][y]) || (x < w - 1 && solid[x + 1][y])
                        || (y > 0 && solid[x][y - 1]) || (y < h - 1 && solid[x][y + 1]);
                if (near) plot(img, x, y, color);
            }
        }
    }

    private static void torchIcon(File dir) throws IOException {
        BufferedImage img = new BufferedImage(ICON, ICON, BufferedImage.TYPE_INT_ARGB);
        for (int y = 7; y < 15; y++) {
            plot(img, 7, y, 0x8A6A3A);
            plot(img, 8, y, 0x6A4A28);
        }
        int[][] flame = {{7, 2}, {8, 2}, {6, 3}, {7, 3}, {8, 3}, {9, 3},
                {6, 4}, {7, 4}, {8, 4}, {9, 4}, {7, 5}, {8, 5}, {7, 6}, {8, 6}};
        for (int[] f : flame) plot(img, f[0], f[1], 0xFFB43C);
        plot(img, 7, 3, 0xFFF0A0);
        plot(img, 8, 4, 0xFFF0A0);
        outline(img, 0x101010);
        write(img, dir, "icon_torch");
    }

    private static void armorIcon(File dir) throws IOException {
        BufferedImage img = new BufferedImage(ICON, ICON, BufferedImage.TYPE_INT_ARGB);
        for (int y = 3; y < 13; y++) {
            int inset = y > 9 ? y - 9 : 0;
            for (int x = 3 + inset; x <= 12 - inset; x++) {
                plot(img, x, y, x < 8 ? 0x9AA7B4 : 0x7A8794);
            }
        }
        for (int x = 5; x <= 7; x++) plot(img, x, 4, 0xD8E4F0);
        outline(img, 0x101010);
        write(img, dir, "icon_armor");
    }

    private static void dynamiteIcon(File dir) throws IOException {
        BufferedImage img = new BufferedImage(ICON, ICON, BufferedImage.TYPE_INT_ARGB);
        for (int y = 5; y < 14; y++) {
            for (int x = 5; x < 11; x++) {
                plot(img, x, y, x < 7 ? 0x8E2A1C : 0xC7422E);
            }
        }
        for (int x = 5; x < 11; x++) plot(img, x, 9, 0xF2E4C0);
        plot(img, 8, 4, 0x3A3A3A);
        plot(img, 9, 3, 0x8A6A2A);
        plot(img, 10, 2, 0xFFC83C);
        outline(img, 0x101010);
        write(img, dir, "icon_dynamite");
    }

    private static void ladderIcon(File dir) throws IOException {
        BufferedImage img = new BufferedImage(ICON, ICON, BufferedImage.TYPE_INT_ARGB);
        for (int y = 2; y < 14; y++) {
            plot(img, 4, y, 0xA97C40);
            plot(img, 5, y, 0x7A5628);
            plot(img, 10, y, 0x7A5628);
            plot(img, 11, y, 0xA97C40);
        }
        for (int y : new int[]{4, 8, 12}) {
            for (int x = 5; x <= 10; x++) plot(img, x, y, 0xC79A5A);
        }
        outline(img, 0x101010);
        write(img, dir, "icon_ladder");
    }

    /** Фон базы: навес, стена и прилавок, 26x6 тайлов. */
    private static void shop(File dir) throws IOException {
        int w = 26 * TILE;
        int h = 6 * TILE;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Random rnd = new Random(7);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c;
                if (y < 7) {
                    c = ((x / 8) % 2 == 0) ? 0xCC4444 : 0xEEEEEE;      // полосатый навес
                } else if (y < 9) {
                    c = 0x8A2A2A;                                       // тень под навесом
                } else if (y > h - 11) {
                    c = (y > h - 4) ? 0x4A321C : 0x6B4A2A;              // прилавок и его тень
                } else {
                    // дощатая стена
                    boolean seam = (y % 6 == 0) || (x % 24 == 0);
                    c = seam ? 0x3A2C45 : jitter(0x4A3A55, rnd, 6);
                }
                img.setRGB(x, y, 0xFF000000 | c);
            }
        }
        write(img, dir, "shop");
    }

    // ---------- утилиты ----------

    private static int jitter(int color, Random rnd, int amount) {
        int d = rnd.nextInt(amount * 2 + 1) - amount;
        return (clamp(((color >> 16) & 0xFF) + d) << 16)
                | (clamp(((color >> 8) & 0xFF) + d) << 8)
                | clamp((color & 0xFF) + d);
    }

    private static int darken(int color, int amount) {
        return (clamp(((color >> 16) & 0xFF) - amount) << 16)
                | (clamp(((color >> 8) & 0xFF) - amount) << 8)
                | clamp((color & 0xFF) - amount);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static void write(BufferedImage img, File dir, String name) throws IOException {
        ImageIO.write(img, "png", new File(dir, name + ".png"));
    }
}
