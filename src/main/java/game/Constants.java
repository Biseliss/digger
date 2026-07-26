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
    public static final int HITBOX_H = 12;
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
    public static final int LAYER_1_END = 20;
    public static final int LAYER_2_END = 45;
    public static final int LAYER_3_END = 75;
    public static final int LAYER_4_END = 110;
    public static final int CORE_ROOM_H = 20;                         // финальная комната (п.6)
    public static final int WORLD_H = SURFACE_Y + LAYER_4_END + CORE_ROOM_H + 4;

    public static final int LAYER_BOUNDARY_WAVE = 6;                  // амплитуда «дуг» на границах слоёв

    // --- Финальная локация за вратами (п.6) ---
    // Вход в последний слой (CORE) больше не докапывают вручную: стоит игроку
    // дойти до этой глубины в любой точке карты — начинается скриптовое падение
    // в темноте и телепорт в центр этой же комнаты, gate-main — у левого края.
    public static final int CORE_ROOM_TOP = SURFACE_Y + LAYER_4_END + 4;
    public static final int CORE_ROOM_BOTTOM = CORE_ROOM_TOP + CORE_ROOM_H - 1;
    public static final int CORE_ROOM_LEFT = 4;
    public static final int CORE_ROOM_RIGHT = WORLD_W - 5;
    public static final int CORE_ROOM_CENTER_X = (CORE_ROOM_LEFT + CORE_ROOM_RIGHT) / 2;

    // Три фазы затемнения: гаснет (мир ещё виден) -> целиком чёрный (тут
    // прячется телепорт) -> плавно проявляется уже в финальной комнате.
    public static final double ENDGAME_FADE_OUT = 0.4;
    public static final double ENDGAME_BLACK_HOLD = 0.5;
    public static final double ENDGAME_FADE_IN = 0.6;

    // --- Игрок (п.14) ---
    public static final int PLAYER_MAX_HP = 100;                      // внутреннее HP
    public static final int HUD_HP_DIVISOR = 10;                      // в HUD показываем /10 → 10 сердец
    public static final int START_MONEY = 50;

    public static final double MOVE_SPEED = 52;                       // px/сек (мировые пиксели)
    public static final double GRAVITY = 260;
    public static final double JUMP_SPEED = 105;
    public static final double MAX_FALL_SPEED = 170;                  // терминальная скорость, px/сек

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
    public static final double DYNAMITE_FUSE = 2.5;                   // сек, успеть отбежать
    public static final double DYNAMITE_RADIUS = 3;                   // тайлы
    public static final int DYNAMITE_MAX_DAMAGE = 60;                 // в эпицентре, спадает к краю
    public static final double EXPLOSION_ANIM_TIME = 0.55;            // сколько играет вспышка

    // --- Анимации (число кадров берётся из файлов, тут только тайминги) ---
    public static final double LAVA_FRAME_TIME = 0.14;                // сек на кадр кипящей лавы

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

    // --- Генерация (п.5) ---
    public static final long DEFAULT_SEED = 1337L;
    public static final int CAVE_MIN_DEPTH = 12;                      // не роем пещеры у самой поверхности
    public static final int CAVE_WORMS = 90;
    public static final double GRAVEL_PATCH_CHANCE = 0.0025;          // на тайл-кандидат
    public static final double LAVA_SEED_CHANCE = 0.10;               // на подходящий тайл (п.5)
    public static final int LAVA_MIN_DEPTH = LAYER_3_END;             // лава от слоя 4 и ниже

    // --- Экономика (п.9) ---
    public static final double INTERACT_RANGE = 2.5;                  // тайлы, «подойти + E»

    /**
     * Вокруг спавна копать нельзя: иначе можно снести землю под базой и
     * возвращаться из шахты прямо в дыру (или застрять в ней после респавна).
     */
    public static final double SPAWN_PROTECT_RADIUS = 2;             // тайлы

    // --- Эффекты ---
    public static final double PARTICLE_LIFETIME = 0.6;               // сек, сколько живёт осколок
    public static final int PARTICLES_PER_BLOCK = 9;
    public static final double PARTICLE_GRAVITY = 220;
    public static final double BLOCK_SHAKE_AMPLITUDE = 1.2;           // px, дрожь блока под киркой
    public static final double SCREEN_SHAKE_TIME = 0.35;              // сек, тряска от урона
    public static final double SCREEN_SHAKE_AMPLITUDE = 5;            // px на полной силе
    public static final int BREAK_STAGES = 4;                         // кадров трещин в textures/break

    // --- Реверб музыки по глубине (п.5) ---
    public static final double MUSIC_REVERB_MAX_DEPTH = 150;          // глубина, на которой реверб уже максимальный
    public static final float MUSIC_REVERB_MAX_WET = 0.55f;

    // --- Звук шагов ---
    public static final double STEP_DISTANCE = 12;                    // px пройдено между шагами
    public static final float STEP_PITCH_MIN = 0.9f;                  // разброс питча — чтобы шаги не звучали монотонно
    public static final float STEP_PITCH_MAX = 1.1f;
}
