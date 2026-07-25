package game.world;

import game.Constants;

import java.util.Random;

/**
 * Процедурная генерация мира строго по стадиям из п.5:
 * слои -> пещеры -> лава и гравий -> руда.
 *
 * Работаем на плоском массиве типов, а объекты Block создаём один раз в конце —
 * так стадии не мешают друг другу и не надо пересоздавать блоки по десять раз.
 */
public class WorldGenerator {
    private final long seed;
    private final Random rnd;
    private final Noise noise;

    private final BlockType[][] tiles = new BlockType[Constants.WORLD_W][Constants.WORLD_H];

    public WorldGenerator(long seed) {
        this.seed = seed;
        this.rnd = new Random(seed);
        this.noise = new Noise(seed);
    }

    public void generateInto(Field field) {
        generateLayers();
        generateCaves();
        generateHazards();
        generateOres();
        carveCoreRoom();
        writeToField(field);
    }

    // --- 1. Слои: границы не полосами, а кривыми дугами (п.5) ---

    private void generateLayers() {
        int[] boundaries = {Constants.LAYER_1_END, Constants.LAYER_2_END,
                Constants.LAYER_3_END, Constants.LAYER_4_END};

        for (int x = 0; x < Constants.WORLD_W; x++) {
            // своя фаза шума на каждую границу, чтобы они не шли параллельно
            int[] wavy = new int[boundaries.length];
            for (int i = 0; i < boundaries.length; i++) {
                double n = noise.fbm1D(x * 0.05 + i * 37.7, 3) * 2 - 1; // -1..1
                wavy[i] = boundaries[i] + (int) Math.round(n * Constants.LAYER_BOUNDARY_WAVE);
            }

            for (int y = 0; y < Constants.WORLD_H; y++) {
                int depth = y - Constants.SURFACE_Y;
                if (depth < 0) {
                    tiles[x][y] = BlockType.AIR;                  // небо и база
                } else if (y >= Constants.WORLD_H - 2) {
                    tiles[x][y] = BlockType.BEDROCK;              // дно мира
                } else if (depth < wavy[0]) {
                    tiles[x][y] = BlockType.DIRT;
                } else if (depth < wavy[1]) {
                    tiles[x][y] = BlockType.STONE;
                } else if (depth < wavy[2]) {
                    tiles[x][y] = BlockType.DEEPSLATE;
                } else if (depth < wavy[3]) {
                    tiles[x][y] = BlockType.HOT_ROCK;
                } else {
                    tiles[x][y] = BlockType.CORE_ROCK;
                }
            }
        }
    }

    // --- 2. Пещеры: продолговатые кривые «черви», гуще к середине (п.5) ---

    private void generateCaves() {
        int minY = Constants.SURFACE_Y + Constants.CAVE_MIN_DEPTH;
        int maxY = Constants.SURFACE_Y + Constants.LAYER_4_END;

        for (int i = 0; i < Constants.CAVE_WORMS; i++) {
            // тянем стартовую глубину к вертикальному центру: (a+b)/2 даёт «горб» посередине
            double t = (rnd.nextDouble() + rnd.nextDouble()) / 2.0;
            int startY = (int) (minY + t * (maxY - minY));
            int startX = rnd.nextInt(Constants.WORLD_W);

            // ближе к центру диапазона — крупнее и длиннее
            double centerFactor = 1.0 - Math.abs(t - 0.5) * 2.0;   // 0 у краёв, 1 в центре
            if (rnd.nextDouble() > 0.25 + centerFactor * 0.75) continue;

            int length = (int) (18 + centerFactor * 70 + rnd.nextInt(20));
            double radius = 1.2 + centerFactor * 2.0 + rnd.nextDouble();

            digWorm(startX, startY, length, radius, i);
        }
    }

    /** Идём «червём» преимущественно вбок, слегка виляя по шуму — получаются дуги. */
    private void digWorm(double x, double y, int length, double radius, int wormIndex) {
        double angle = rnd.nextBoolean() ? 0 : Math.PI;   // стартуем влево или вправо

        for (int step = 0; step < length; step++) {
            double n = noise.value2D(step * 0.08, wormIndex * 13.5) * 2 - 1;
            angle += n * 0.35;
            // держим направление в основном горизонтальным, но иногда пускаем вниз
            double vertical = Math.sin(angle) * 0.55;
            x += Math.cos(angle);
            y += vertical;

            if (x < 1 || x > Constants.WORLD_W - 2) break;
            if (y < Constants.SURFACE_Y + Constants.CAVE_MIN_DEPTH) break;
            if (y > Constants.SURFACE_Y + Constants.LAYER_4_END) break;

            carveCircle(x, y, radius);
        }
    }

    private void carveCircle(double cx, double cy, double radius) {
        int r = (int) Math.ceil(radius);
        for (int x = (int) cx - r; x <= (int) cx + r; x++) {
            for (int y = (int) cy - r; y <= (int) cy + r; y++) {
                if (!Field.inBounds(x, y)) continue;
                if (y >= Constants.WORLD_H - 2) continue;      // дно не пробиваем
                double dx = x - cx;
                double dy = y - cy;
                if (dx * dx + dy * dy <= radius * radius) tiles[x][y] = BlockType.AIR;
            }
        }
    }

    // --- 3. Угрозы: лава и гравий (предпоследний шаг, п.5) ---

    private void generateHazards() {
        generateLava();
        generateGravel();
    }

    /**
     * Правило из ТЗ: ниже определённой глубины берём плотный блок; если под ним
     * воздух, а сверху тоже плотный блок — можно поставить затравку лавы.
     * Дальше лава сама стекает вниз обычной механикой (п.6).
     */
    private void generateLava() {
        int minY = Constants.SURFACE_Y + Constants.LAVA_MIN_DEPTH;

        for (int x = 1; x < Constants.WORLD_W - 1; x++) {
            for (int y = minY; y < Constants.WORLD_H - 3; y++) {
                if (!isDense(x, y)) continue;
                if (tiles[x][y + 1] != BlockType.AIR) continue;   // под кандидатом должна быть пустота
                if (!isDense(x, y - 1)) continue;                 // а сверху — потолок
                if (rnd.nextDouble() > Constants.LAVA_SEED_CHANCE) continue;

                tiles[x][y] = BlockType.LAVA;
                spillLavaDown(x, y + 1);
            }
        }
    }

    /** Тот же каскад, что и в рантайме, только на этапе генерации. */
    private void spillLavaDown(int x, int y) {
        while (Field.inBounds(x, y) && tiles[x][y] == BlockType.AIR && y < Constants.WORLD_H - 2) {
            tiles[x][y] = BlockType.LAVA;
            y++;
        }
    }

    /**
     * Гравий: небольшое пятно, которое не касается воздуха НИЖНЕЙ стороной
     * (иначе оно обязано было бы сразу падать). Сверху и сбоку — можно.
     */
    private void generateGravel() {
        int minY = Constants.SURFACE_Y + 6;

        for (int x = 2; x < Constants.WORLD_W - 2; x++) {
            for (int y = minY; y < Constants.WORLD_H - 4; y++) {
                if (!isDense(x, y)) continue;
                if (rnd.nextDouble() > Constants.GRAVEL_PATCH_CHANCE) continue;

                int size = 3 + rnd.nextInt(4); // 3-6 тайлов
                java.util.List<int[]> patch = new java.util.ArrayList<>();
                int px = x;
                int py = y;
                for (int i = 0; i < size; i++) {
                    if (!isDense(px, py)) break;
                    if (tiles[px][py + 1] == BlockType.AIR) break;   // не висим над пустотой
                    patch.add(new int[]{px, py});
                    px += rnd.nextInt(3) - 1;
                    py += rnd.nextInt(2);
                    if (!Field.inBounds(px, py + 1)) break;
                }
                if (patch.size() < 3) continue;
                for (int[] p : patch) {
                    tiles[p[0]][p[1]] = BlockType.GRAVEL;
                }
            }
        }
    }

    // --- 4. Руда (самый последний шаг, п.5) ---

    /** Руда, её родной слой и базовый шанс. Чем глубже своего слоя — тем реже. */
    private record OreSpawn(BlockType type, int nativeLayer, double baseChance) {}

    private static final OreSpawn[] ORES = {
            new OreSpawn(BlockType.COAL_ORE, 1, 0.055),
            new OreSpawn(BlockType.COPPER_ORE, 1, 0.040),
            new OreSpawn(BlockType.IRON_ORE, 2, 0.035),
            new OreSpawn(BlockType.GOLD_ORE, 2, 0.020),
            new OreSpawn(BlockType.DIAMOND_ORE, 3, 0.014),
    };

    private void generateOres() {
        for (int x = 0; x < Constants.WORLD_W; x++) {
            for (int y = Constants.SURFACE_Y; y < Constants.WORLD_H - 2; y++) {
                if (!isDense(x, y)) continue;                  // только плотная порода, не лава/гравий/воздух
                if (tiles[x][y] == BlockType.GRAVEL) continue;

                int layerIndex = Layer.atWorldY(y).index();
                for (OreSpawn ore : ORES) {
                    if (layerIndex < ore.nativeLayer()) continue;      // выше родного слоя не спавнится
                    // затухание с глубиной: чем дальше от родного слоя, тем реже
                    double falloff = Math.pow(0.55, layerIndex - ore.nativeLayer());
                    if (rnd.nextDouble() < ore.baseChance() * falloff) {
                        placeOreBlob(x, y, ore.type());
                        break;
                    }
                }
            }
        }
    }

    /** Руда идёт кучками, а не одиночными пикселями — так её приятнее копать. */
    private void placeOreBlob(int x, int y, BlockType ore) {
        int size = 2 + rnd.nextInt(4);
        int px = x;
        int py = y;
        for (int i = 0; i < size; i++) {
            if (!isDense(px, py) || tiles[px][py] == BlockType.GRAVEL) break;
            tiles[px][py] = ore;
            px += rnd.nextInt(3) - 1;
            py += rnd.nextInt(3) - 1;
            if (!Field.inBounds(px, py)) break;
        }
    }

    // --- 5. Финальная комната с жёлтой дверью (п.10) ---

    private void carveCoreRoom() {
        int roomTop = Constants.SURFACE_Y + Constants.LAYER_4_END + 4;
        int roomBottom = Math.min(Constants.WORLD_H - 3, roomTop + 10);
        int cx = Constants.WORLD_W / 2;

        for (int x = cx - 9; x <= cx + 9; x++) {
            for (int y = roomTop; y <= roomBottom; y++) {
                if (Field.inBounds(x, y)) tiles[x][y] = BlockType.AIR;
            }
        }
        // дверь стоит на полу комнаты
        tiles[cx][roomBottom] = BlockType.YELLOW_DOOR;
        tiles[cx][roomBottom - 1] = BlockType.YELLOW_DOOR;
    }

    // --- перенос в Field ---

    private void writeToField(Field field) {
        for (int x = 0; x < Constants.WORLD_W; x++) {
            for (int y = 0; y < Constants.WORLD_H; y++) {
                field.setBlock(x, y, create(x, y, tiles[x][y]));

                // Природным пустотам тоже даём задний план породы своего слоя.
                // ТЗ описывает фон только для раскопанных игроком тайлов, но без
                // этого пещеры выглядят дырами в никуда, а не полостями в породе.
                // Убрать — просто снять этот if.
                if (tiles[x][y] == BlockType.AIR && y > Constants.SURFACE_Y) {
                    field.setBackground(x, y, Layer.atWorldY(y).baseBlock);
                }
            }
        }
        // всё, что на поверхности и выше, игрок видит сразу — база не должна быть в тумане
        for (int x = 0; x < Constants.WORLD_W; x++) {
            for (int y = 0; y <= Constants.SURFACE_Y + 1; y++) {
                field.reveal(x, y);
            }
        }

        // финальную комнату раскрываем целиком: панчлайн с дверью должен быть
        // виден сразу при входе, а не нащупываться в темноте по одному тайлу
        int roomTop = Constants.SURFACE_Y + Constants.LAYER_4_END + 3;
        int cx = Constants.WORLD_W / 2;
        for (int x = cx - 10; x <= cx + 10; x++) {
            for (int y = roomTop; y < Constants.WORLD_H; y++) {
                field.reveal(x, y);
            }
        }
    }

    private static Block create(int x, int y, BlockType type) {
        return switch (type) {
            case AIR -> new AirBlock(x, y);
            case GRAVEL -> new GravelBlock(x, y);
            case LAVA -> new LavaBlock(x, y);
            default -> new SolidBlock(x, y, type);
        };
    }

    private boolean isDense(int x, int y) {
        if (!Field.inBounds(x, y)) return false;
        BlockType t = tiles[x][y];
        return t != BlockType.AIR && t != BlockType.LAVA && t != BlockType.BEDROCK
                && t != BlockType.YELLOW_DOOR;
    }

    public long getSeed() {
        return seed;
    }
}
