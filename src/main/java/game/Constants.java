package game;

/**
 * Все числа баланса из п.14 ТЗ в одном месте.
 * Правим здесь, а не по коду — так плейтест-правки не требуют искать магические числа.
 */
public final class Constants {
    private Constants() {}

    // --- Размеры и рендер (п.11) ---
    public static final int TILE = 8;          // текстура блока 8x8
    public static final int SCALE = 4;         // во сколько раз увеличиваем при отрисовке
    public static final int PLAYER_W = 8;      // спрайт игрока 8x16 (1:2)
    public static final int PLAYER_H = 16;
    public static final int HITBOX_W = 6;      // хитбокс чуть меньше спрайта
    public static final int HITBOX_H = 15;
    public static final int ICON = 16;         // иконки HUD/магазина 16x16

    public static final int WINDOW_W = 960;
    public static final int WINDOW_H = 640;

    // --- Мир (п.5) ---
    public static final int CHUNK_W = 32;
    public static final int CHUNK_H = 32;
    public static final int WORLD_CHUNKS_X = 6;                       // ширина мира ограничена
    public static final int WORLD_W = CHUNK_W * WORLD_CHUNKS_X;       // 192 тайла
    public static final int SURFACE_Y = 24;                           // уровень поверхности (выше — небо и база)

    // Глубины границ слоёв, в тайлах от поверхности (п.4)
    public static final int LAYER_1_END = 40;
    public static final int LAYER_2_END = 90;
    public static final int LAYER_3_END = 150;
    public static final int LAYER_4_END = 220;
    public static final int CORE_ROOM_H = 20;                         // финальная комната с дверью
    public static final int WORLD_H = SURFACE_Y + LAYER_4_END + CORE_ROOM_H + 4;

    public static final int LAYER_BOUNDARY_WAVE = 6;                  // амплитуда «дуг» на границах слоёв

    // --- Игрок (п.14) ---
    public static final int PLAYER_MAX_HP = 100;                      // внутреннее HP
    public static final int HUD_HP_DIVISOR = 10;                      // в HUD показываем /10 → 10 сердец
    public static final int START_MONEY = 50;

    public static final double MOVE_SPEED = 52;                       // px/сек (мировые пиксели)
    public static final double GRAVITY = 260;
    public static final double JUMP_SPEED = 105;
    public static final double MAX_FALL_SPEED = 300;

    public static final double DIG_REACH = 5;                         // радиус досягаемости копания, тайлы

    // --- Урон (п.6) ---
    public static final int FALL_SAFE_TILES = 4;                      // безопасная высота падения
    public static final int FALL_DAMAGE_PER_TILE = 8;                 // от расстояния
    public static final int LAVA_BURST_DAMAGE = 40;                   // разово при касании
    public static final int LAVA_DAMAGE_PER_SEC = 20;                 // по кулдауну, пока в контакте
    public static final int PRESSURE_DAMAGE_PER_SEC = 10;             // слои 4-5 без снаряжения
    public static final int GRAVEL_DAMAGE = 25;                       // обвал — мгновенно
    public static final double DAMAGE_TICK = 0.5;                     // кулдаун периодического урона, сек

    // --- Динамит (п.3) ---
    public static final double DYNAMITE_FUSE = 1.5;                   // сек
    public static final double DYNAMITE_RADIUS = 3;                   // тайлы
    public static final int DYNAMITE_MAX_DAMAGE = 60;                 // в эпицентре, спадает к краю

    // --- Лестницы (п.8): обычный ставящийся блок, как в Minecraft ---
    public static final int LADDER_CARRY_LIMIT = 32;      // размер стака
    public static final int LADDER_PURCHASE_AMOUNT = 8;   // сколько даёт одна покупка
    public static final double CLIMB_SPEED = 34;          // px/сек вверх-вниз по лестнице

    // --- Свет (п.7) ---
    public static final double[] LIGHT_RADIUS_BY_LAYER = {8, 6, 5, 4, 3};
    public static final double TORCH_BONUS_RADIUS = 4;
    public static final int DARKNESS_ALPHA = 238;                     // насколько темно вне круга света

    // --- Инструмент (п.3) ---
    public static final int MAX_TOOL_TIER = 4;                        // 0 дерево, 1 камень, 2 медь, 3 железо, 4 алмаз
    public static final int[] TOOL_UPGRADE_PRICE = {0, 50, 150, 400, 1000};
    public static final String[] TOOL_NAMES = {"Wooden", "Stone", "Copper", "Iron", "Diamond"};
    /** Скорость копания по тиру: камень и медь — один слой, но медь быстрее (п.3). */
    public static final double[] DIG_SPEED = {1.0, 1.6, 2.4, 3.4, 5.0};

    public static final int ORE_CARRY_BASE = 10;                      // лимит на тип руды
    public static final int ORE_CARRY_PER_TIER = 5;

    // --- Генерация (п.5) ---
    public static final long DEFAULT_SEED = 1337L;
    public static final int CAVE_MIN_DEPTH = 12;                      // не роем пещеры у самой поверхности
    public static final int CAVE_WORMS = 90;
    public static final double GRAVEL_PATCH_CHANCE = 0.0025;          // на тайл-кандидат
    public static final double LAVA_SEED_CHANCE = 0.10;               // на подходящий тайл (п.5)
    public static final int LAVA_MIN_DEPTH = LAYER_3_END;             // лава от слоя 4 и ниже

    // --- Экономика (п.9) ---
    public static final double INTERACT_RANGE = 2.5;                  // тайлы, «подойти + E»
}
