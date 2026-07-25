package tools;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Одноразовый генератор ПЛЕЙСХОЛДЕР-текстур в src/main/resources/textures.
 *
 * Это не часть игры и не «процедурные текстуры» из ТЗ: игра всегда рисует
 * готовые файлы (см. game.render.Textures), просто художнику нужно с чего-то
 * начать. Нарисовали настоящий ассет — просто положите его поверх файла с тем
 * же именем, перезапускать генератор не нужно.
 *
 * Запуск: ./mvnw compile exec:java -Dexec.mainClass=tools.TextureGen
 */
public final class TextureGen {
    private static final int TILE = 8;
    private static final int ICON = 16;

    public static void main(String[] args) throws IOException {
        File dir = new File("src/main/resources/textures");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Не удалось создать " + dir);
        }

        // порода
        tile(dir, "block_dirt", 0x8B5A2B, 0x6E4520);
        tile(dir, "block_stone", 0x8A8A8A, 0x6E6E6E);
        tile(dir, "block_deepslate", 0x4A4A52, 0x36363C);
        tile(dir, "block_hotrock", 0x7A2B26, 0x5A1D1A);
        tile(dir, "block_core", 0x2A2130, 0x1A1420);
        tile(dir, "block_gravel", 0x9A9088, 0x746C66);
        tile(dir, "block_bedrock", 0x2E2E2E, 0x1A1A1A);
        tile(dir, "block_lava", 0xE8631A, 0xFFA53C);

        // руды: порода + вкрапления
        ore(dir, "ore_coal", 0x8A8A8A, 0x1E1E1E);
        ore(dir, "ore_copper", 0x8A8A8A, 0xC87A3A);
        ore(dir, "ore_iron", 0x4A4A52, 0xD8CFC4);
        ore(dir, "ore_gold", 0x4A4A52, 0xF2C245);
        ore(dir, "ore_diamond", 0x7A2B26, 0x6EE6E0);

        // объекты
        ladder(dir);
        dynamite(dir);
        door(dir);

        // персонажи 8x16
        character(dir, "player", 0x3E6FB0, 0xE8B98C);
        character(dir, "npc_buyer", 0x3F7A46, 0xE8B98C);
        character(dir, "npc_smith", 0x8A4B2A, 0xE8B98C);
        character(dir, "npc_utility", 0x6A4A8A, 0xE8B98C);

        // иконки 16x16
        icon(dir, "icon_stone", 0x8A8A8A);
        icon(dir, "icon_coal", 0x2A2A2A);
        icon(dir, "icon_copper", 0xC87A3A);
        icon(dir, "icon_iron", 0xD8CFC4);
        icon(dir, "icon_gold", 0xF2C245);
        icon(dir, "icon_diamond", 0x6EE6E0);
        icon(dir, "icon_torch", 0xFFB43C);
        icon(dir, "icon_armor", 0x9AA7B4);
        icon(dir, "icon_dynamite", 0xC7422E);
        icon(dir, "icon_ladder", 0xA97C40);

        shop(dir);
        System.out.println("Плейсхолдер-текстуры записаны в " + dir.getAbsolutePath());
    }

    private static void tile(File dir, String name, int base, int dark) throws IOException {
        BufferedImage img = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
        Random rnd = new Random(name.hashCode());
        for (int y = 0; y < TILE; y++) {
            for (int x = 0; x < TILE; x++) {
                int c = rnd.nextInt(100) < 28 ? dark : base;
                img.setRGB(x, y, 0xFF000000 | jitter(c, rnd));
            }
        }
        write(img, dir, name);
    }

    private static void ore(File dir, String name, int rock, int gem) throws IOException {
        BufferedImage img = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
        Random rnd = new Random(name.hashCode());
        for (int y = 0; y < TILE; y++) {
            for (int x = 0; x < TILE; x++) {
                img.setRGB(x, y, 0xFF000000 | jitter(rock, rnd));
            }
        }
        // несколько «самородков» кучкой в центре
        int[][] spots = {{2, 2}, {3, 2}, {2, 3}, {5, 4}, {5, 5}, {4, 5}};
        for (int[] s : spots) {
            img.setRGB(s[0], s[1], 0xFF000000 | gem);
        }
        write(img, dir, name);
    }

    private static void ladder(File dir) throws IOException {
        BufferedImage img = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
        int wood = 0xFFA97C40;
        for (int y = 0; y < TILE; y++) {
            img.setRGB(1, y, wood);
            img.setRGB(6, y, wood);
        }
        for (int x = 1; x <= 6; x++) {
            img.setRGB(x, 2, wood);
            img.setRGB(x, 6, wood);
        }
        write(img, dir, "ladder");
    }

    private static void dynamite(File dir) throws IOException {
        BufferedImage img = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 2; y < 7; y++) {
            for (int x = 2; x < 6; x++) {
                img.setRGB(x, y, 0xFFC7422E);
            }
        }
        img.setRGB(4, 1, 0xFF3A3A3A);
        img.setRGB(5, 0, 0xFFFFC83C);
        write(img, dir, "dynamite");
    }

    private static void door(File dir) throws IOException {
        BufferedImage img = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < TILE; y++) {
            for (int x = 0; x < TILE; x++) {
                boolean frame = x == 0 || x == TILE - 1 || y == 0 || y == TILE - 1;
                img.setRGB(x, y, frame ? 0xFFB89A22 : 0xFFF2D544);
            }
        }
        img.setRGB(5, 4, 0xFF6A5A10); // ручка
        write(img, dir, "door");
    }

    private static void character(File dir, String name, int shirt, int skin) throws IOException {
        BufferedImage img = new BufferedImage(TILE, TILE * 2, BufferedImage.TYPE_INT_ARGB);
        // голова
        for (int y = 1; y < 6; y++) {
            for (int x = 2; x < 7; x++) {
                img.setRGB(x, y, 0xFF000000 | skin);
            }
        }
        img.setRGB(3, 3, 0xFF202020);
        img.setRGB(5, 3, 0xFF202020);
        // каска
        for (int x = 1; x < 7; x++) {
            img.setRGB(x, 0, 0xFFF2C245);
            img.setRGB(x, 1, 0xFFF2C245);
        }
        // корпус
        for (int y = 6; y < 12; y++) {
            for (int x = 1; x < 7; x++) {
                img.setRGB(x, y, 0xFF000000 | shirt);
            }
        }
        // ноги
        for (int y = 12; y < 16; y++) {
            for (int x = 2; x < 4; x++) img.setRGB(x, y, 0xFF33383F);
            for (int x = 4; x < 6; x++) img.setRGB(x, y, 0xFF262A30);
        }
        write(img, dir, name);
    }

    private static void icon(File dir, String name, int color) throws IOException {
        BufferedImage img = new BufferedImage(ICON, ICON, BufferedImage.TYPE_INT_ARGB);
        for (int y = 3; y < ICON - 3; y++) {
            for (int x = 3; x < ICON - 3; x++) {
                boolean edge = x == 3 || y == 3 || x == ICON - 4 || y == ICON - 4;
                img.setRGB(x, y, edge ? 0xFF101010 : (0xFF000000 | color));
            }
        }
        write(img, dir, name);
    }

    /** Фон базы: прилавок с навесом, 26x6 тайлов. */
    private static void shop(File dir) throws IOException {
        int w = 26 * TILE;
        int h = 6 * TILE;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c;
                if (y < 6) {
                    c = ((x / 8) % 2 == 0) ? 0xFFCC4444 : 0xFFEEEEEE; // полосатый навес
                } else if (y > h - 10) {
                    c = 0xFF6B4A2A;                                    // прилавок
                } else {
                    c = 0xFF4A3A55;                                    // стена
                }
                img.setRGB(x, y, c);
            }
        }
        write(img, dir, "shop");
    }

    private static int jitter(int color, Random rnd) {
        int d = rnd.nextInt(17) - 8;
        int r = clamp(((color >> 16) & 0xFF) + d);
        int g = clamp(((color >> 8) & 0xFF) + d);
        int b = clamp((color & 0xFF) + d);
        return (r << 16) | (g << 8) | b;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static void write(BufferedImage img, File dir, String name) throws IOException {
        ImageIO.write(img, "png", new File(dir, name + ".png"));
    }
}
