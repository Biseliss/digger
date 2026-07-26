package game.world;

import game.Constants;
import game.item.OreType;

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
    /** Маска руды поверх породы — параллельно tiles, null значит чистый камень. */
    private final OreType[][] ores = new OreType[Constants.WORLD_W][Constants.WORLD_H];
    /**
     * Порода слоя ДО пещер/лавы/гравия — фон подземелья берём отсюда, а не с
     * типа блока на момент раскопки. Иначе гравий, упавший и разбитый не там,
     * где был изначально, оставлял бы после себя пустой (чёрный) фон вместо
     * породы, которая там залегала с начала игры.
     */
    private final BlockType[][] origRock = new BlockType[Constants.WORLD_W][Constants.WORLD_H];

    public WorldGenerator(long seed) {
        this.seed = seed;
        this.rnd = new Random(seed);
        this.noise = new Noise(seed);
    }

    public void generateInto(Field field) {
        generateLayers();
        for (int x = 0; x < Constants.WORLD_W; x++) {
            origRock[x] = tiles[x].clone();   // снимок «как заложено слоями», до пещер/гравия
        }
        generateCaves();
        generateHazards();
        generateOres();
        protectSpawnArea();
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

            // Длина/радиус были подобраны под старый мир (слой 4 на глубине 110);
            // в укороченном демо-мире (п.1, ~48) те же числа выедали почти весь
            // мимо-проходной слой камня одним-двумя червями — масштабируем вниз.
            int length = (int) (10 + centerFactor * 24 + rnd.nextInt(8));
            double radius = 1.0 + centerFactor * 1.0 + rnd.nextDouble() * 0.6;

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
    private record OreSpawn(OreType type, int nativeLayer, double baseChance) {}

    private static final OreSpawn[] ORES = {
            new OreSpawn(OreType.COAL, 1, 0.055),
            new OreSpawn(OreType.COPPER, 1, 0.040),
            new OreSpawn(OreType.IRON, 2, 0.035),
            new OreSpawn(OreType.GOLD, 2, 0.020),
            new OreSpawn(OreType.DIAMOND, 3, 0.014),
    };

    private void generateOres() {
        for (int x = 0; x < Constants.WORLD_W; x++) {
            for (int y = Constants.SURFACE_Y; y < Constants.WORLD_H - 2; y++) {
                if (!isDense(x, y)) continue;                  // только плотная порода, не лава/гравий/воздух
                if (tiles[x][y] == BlockType.GRAVEL) continue;
                if (ores[x][y] != null) continue;              // уже занято другой рудой

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
    private void placeOreBlob(int x, int y, OreType ore) {
        int size = 2 + rnd.nextInt(4);
        int px = x;
        int py = y;
        for (int i = 0; i < size; i++) {
            if (!isDense(px, py) || tiles[px][py] == BlockType.GRAVEL) break;
            ores[px][py] = ore;   // порода остаётся своей, руда ложится маской
            px += rnd.nextInt(3) - 1;
            py += rnd.nextInt(3) - 1;
            if (!Field.inBounds(px, py)) break;
        }
    }

    // --- 4.5. Защита базы (фидбек игроков) ---

    /**
     * Раньше базу защищал радиус-2 вокруг точки спавна (Player.dig), а NPC
     * стоят на ±3..±8 тайлов от центра — игроки успевали раскопать землю
     * прямо под ними и потом не могли дотянуться. Вместо проверки радиуса —
     * настоящий бедрок в широкой прямоугольной зоне: и не выкопать в
     * принципе, и видно с первого взгляда, что здесь копать нельзя (другая
     * текстура, а не обычная порода).
     */
    private void protectSpawnArea() {
        int cx = Constants.WORLD_W / 2;
        int left = Math.max(0, cx - Constants.BASE_PROTECT_HALF_WIDTH);
        int right = Math.min(Constants.WORLD_W - 1, cx + Constants.BASE_PROTECT_HALF_WIDTH);
        int top = Constants.SURFACE_Y;
        int bottom = Math.min(Constants.WORLD_H - 1, Constants.SURFACE_Y + Constants.BASE_PROTECT_DEPTH - 1);

        for (int x = left; x <= right; x++) {
            for (int y = top; y <= bottom; y++) {
                tiles[x][y] = BlockType.BEDROCK;
                ores[x][y] = null;   // под бедроком руда всё равно не выкопается — не дразним оверлеем
            }
        }
    }

    // --- 5. Финальная комната за вратами (п.6) ---

    /**
     * Комната самодостаточна: пол и боковые стены — несокрушимый бедрок, чтобы
     * из неё нельзя было ни выкопаться наружу, ни провалиться сквозь пол.
     * Попадают сюда не ходьбой, а скриптовым падением-телепортом (Game),
     * когда игрок докапывается до этой глубины в любой точке карты.
     */
    private void carveCoreRoom() {
        int top = Constants.CORE_ROOM_TOP;
        int bottom = Constants.CORE_ROOM_BOTTOM;
        int left = Constants.CORE_ROOM_LEFT;
        int right = Constants.CORE_ROOM_RIGHT;

        for (int x = left; x <= right; x++) {
            for (int y = top; y <= bottom; y++) {
                if (Field.inBounds(x, y)) tiles[x][y] = BlockType.AIR;
            }
        }
        for (int x = left; x <= right; x++) {
            tiles[x][bottom] = BlockType.BEDROCK;
        }
        for (int y = top; y <= bottom; y++) {
            tiles[left][y] = BlockType.BEDROCK;
            tiles[right][y] = BlockType.BEDROCK;
        }
    }

    // --- перенос в Field ---

    private void writeToField(Field field) {
        for (int x = 0; x < Constants.WORLD_W; x++) {
            for (int y = 0; y < Constants.WORLD_H; y++) {
                Block block = create(x, y, tiles[x][y]);
                if (ores[x][y] != null) block.setOre(ores[x][y]);
                field.setBlock(x, y, block);

                // Фон фиксируем один раз при генерации, по исходной породе слоя —
                // а не по типу блока на момент раскопки (п.11). Так у природных
                // пещер за фон видна порода, а не дыра в никуда, а у гравия,
                // упавшего и разбитого в другом месте, фон остаётся той же
                // породой, что стояла там с начала игры, а не чёрной пустотой.
                //
                // Финальную комнату (п.6) исключаем: там вместо породы за фоном
                // должна проступать нарисованная сцена gate-*, которую поверх
                // чёрной заливки рисует Game — глухой фон породы её бы перекрыл.
                boolean inCoreRoom = x >= Constants.CORE_ROOM_LEFT && x <= Constants.CORE_ROOM_RIGHT
                        && y >= Constants.CORE_ROOM_TOP && y <= Constants.CORE_ROOM_BOTTOM;
                if (!inCoreRoom && y > Constants.SURFACE_Y && origRock[x][y] != BlockType.AIR) {
                    field.setBackground(x, y, origRock[x][y]);
                }
            }
        }
        // всё, что на поверхности и выше, игрок видит сразу — база не должна быть в тумане
        for (int x = 0; x < Constants.WORLD_W; x++) {
            for (int y = 0; y <= Constants.SURFACE_Y + 1; y++) {
                field.reveal(x, y);
            }
        }

        // финальную комнату раскрываем целиком: попадаем туда телепортом при
        // падении, нащупывать её в темноте по тайлу не приходится
        for (int x = Constants.CORE_ROOM_LEFT; x <= Constants.CORE_ROOM_RIGHT; x++) {
            for (int y = Constants.CORE_ROOM_TOP; y <= Constants.CORE_ROOM_BOTTOM; y++) {
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
        return t != BlockType.AIR && t != BlockType.LAVA && t != BlockType.BEDROCK;
    }

    public long getSeed() {
        return seed;
    }
}
