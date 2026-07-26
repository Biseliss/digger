package game;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import core.Audio;
import core.Input;
import core.Scene;
import game.entity.Dynamite;
import game.entity.Player;
import game.hud.HudView;
import game.hud.PauseView;
import game.item.OreType;
import game.item.UtilityType;
import game.npc.EndgameChestNpc;
import game.npc.NpcPoint;
import game.npc.OreBuyerNpc;
import game.npc.ToolsmithNpc;
import game.npc.UtilityShopNpc;
import game.render.Lighting;
import game.render.Textures;
import game.world.Block;
import game.world.BlockType;
import game.world.Field;
import game.world.WorldGenerator;
import ui.DrawCtx;
import ui.Screen;


/** Склейка всего: мир, игрок, NPC, свет и HUD под одним tick/draw. */
public class Game implements Scene {
    private final Input input;
    private final Field field = new Field();
    private final Player player;
    private final Camera camera;
    private final Screen hud;

    private final List<NpcPoint> npcs = new ArrayList<>();
    private final UtilityShopNpc utilityShop;
    private final List<Dynamite> dynamites = new ArrayList<>();

    private final int spawnTileX = Constants.WORLD_W / 2;
    private final int spawnTileY = Constants.SURFACE_Y - 2;

    private String activePrompt = "";
    private String overlayMessage = "";
    private double overlayTimer;
    private boolean won;
    /** Сколько ещё ждать после победы, прежде чем вышвырнуть в главное меню (п.4). */
    private double winReturnTimer = -1;
    /** Куда возвращаться после победы — задаёт MainMenu при создании Game. */
    private final Runnable onExitToMenu;

    // --- пасхалка у gate-main (п.6, доп.): "E to peek" -> имитация краша ---
    // Точка досягаемости — у пола, а не в геометрическом центре мурала: тот
    // висит на высоте ~10 тайлов над полом, и достать до него, стоя на земле,
    // было физически невозможно ни при каком INTERACT_RANGE.
    private final double gateMainCenterTileX = Constants.CORE_ROOM_LEFT + Constants.CORE_ROOM_H / 2.0;
    private final double gateMainCenterTileY = Constants.CORE_ROOM_BOTTOM - 2;
    private double fakeCrashTimer = -1;

    /**
     * Финальное падение (п.6): докопался до глубины последнего слоя в любой
     * точке карты — экран плавно гаснет в черноту, на середине «полёта»
     * игрока незаметно телепортирует в центр финальной комнаты, и после
     * приземления экран так же плавно проявляется обратно.
     */
    private boolean endgameTriggered;
    private boolean endgameSeqActive;
    private boolean endgameTeleported;
    private boolean endgameLanded;
    private double endgameSeqTimer;

    /** Пауза: игровой тик не выполняется, поверх кадра рисуется свой экран. */
    private boolean paused;
    private final Screen pauseScreen;

    /** Счётчик кадров для отметки «блок виден прямо сейчас» (п.7). */
    private int visibilityFrame;

    private final game.render.Particles particles = new game.render.Particles();
    /** Кулдаун, чтобы десяток блоков подряд не превращался в треск. */

    /** Тряска экрана от урона: сколько ещё трясти и насколько сильно. */
    private double shakeTimer;
    private double shakeStrength;
    /** HP на прошлом кадре — по нему замечаем, что игрока задело. */
    private int lastHealth = Constants.PLAYER_MAX_HP;

    private int fps = 0;
    private int frames = 0;
    private long lastTime = System.currentTimeMillis();


    /**
     * Голосов на звук поломки — несколько (п.5): ломаем блоки быстро подряд,
     * и раньше единственный Audio-инстанс с кулдауном просто съедал часть
     * звуков, если предыдущий ещё не доиграл. Теперь свободный голос ищется
     * round-robin'ом, и переполнение случается только если реально заняты все.
     */
    private static final int DIG_VOICES = 4;

    public Audio music;
    public Audio sfx_dig;
    public Audio sfx_step;
    public Audio sfx_cash;
    private final Audio[] sfxDigVoices = new Audio[DIG_VOICES];
    private int digVoiceIndex;

    public Game(Input input, int screenW, int screenH, Runnable onExitToMenu) {
        this.input = input;
        this.onExitToMenu = onExitToMenu;

        new WorldGenerator(Constants.DEFAULT_SEED).generateInto(field);

        this.player = new Player(spawnTileX, spawnTileY);
        this.camera = new Camera(screenW, screenH);

        // база: три точки взаимодействия, «подойти + E» (п.9)
        npcs.add(new OreBuyerNpc(spawnTileX - 8, Constants.SURFACE_Y - 2));
        npcs.add(new ToolsmithNpc(spawnTileX - 3, Constants.SURFACE_Y - 2));
        utilityShop = new UtilityShopNpc(spawnTileX + 3, Constants.SURFACE_Y - 2);
        npcs.add(utilityShop);

        // финальная комната (п.6): сундук у правого края, подальше от gate-main слева
        int chestX = Constants.CORE_ROOM_RIGHT - 3;
        int chestY = Constants.CORE_ROOM_BOTTOM - 2;
        npcs.add(new EndgameChestNpc(chestX, chestY, this::onChestOpened));

        this.hud = new Screen(screenW, screenH);
        hud.addChild(new HudView(this, screenW, screenH));

        music = new Audio();

        for (int i = 0; i < DIG_VOICES; i++) {
            sfxDigVoices[i] = new Audio();
            sfxDigVoices[i].setFile("SFX_Dig");
        }
        sfx_dig = sfxDigVoices[0];   // используется только слайдером громкости

        sfx_step = new Audio();
        sfx_step.setFile("SFX_Step");

        sfx_cash = new Audio();
        sfx_cash.setFile("SFX_Cash");

        music.setFile("Soundtrack"); // подгружает музыку из регистра, однако я потом сделаю поиск по названию файла а не индексу в списке, это временно.
        music.loop(); // проигрывает и лупит её, есть функция play(), она играет без лупа один раз, подходит для отдельных звуков

        // громкость выставляется на экране настроек до старта игры (AppSettings)
        music.setVolume(AppSettings.musicVolume);
        for (Audio voice : sfxDigVoices) voice.setVolume(AppSettings.sfxVolume);
        sfx_step.setVolume(AppSettings.sfxVolume);
        sfx_cash.setVolume(AppSettings.sfxVolume);

        // строго после аудио: ползунки читают стартовую громкость из Audio
        this.pauseScreen = buildPauseScreen(screenW, screenH);
    }

    /**
     * Экран паузы: затемнение с памяткой (PauseView) плюс два ползунка
     * громкости. Собирается один раз — на паузе только меняются значения.
     */
    private Screen buildPauseScreen(int screenW, int screenH) {
        Screen screen = new Screen(screenW, screenH);

        PauseView view = new PauseView(screenW, screenH);
        screen.addChild(view);

        int sliderX = view.panelX() + 28;
        int sliderW = view.panelWidth() - 56;
        int sliderY = view.panelY() + 90;

        screen.addChild(new ui.widgets.Slider(sliderX, sliderY, sliderW, 26,
                "Music", music.getVolume(), v -> {
            music.setVolume((float) v);
            AppSettings.musicVolume = (float) v;
        }));
        screen.addChild(new ui.widgets.Slider(sliderX, sliderY + 46, sliderW, 26,
                "Sound", sfx_dig.getVolume(), v -> {
            for (Audio voice : sfxDigVoices) voice.setVolume((float) v);
            sfx_step.setVolume((float) v);
            sfx_cash.setVolume((float) v);
            AppSettings.sfxVolume = (float) v;
        }));

        int buttonW = sliderW;
        int buttonH = 36;
        int buttonY = view.buttonsY();
        screen.addChild(new ui.widgets.Button(sliderX, buttonY, buttonW, buttonH, "Main Menu", () -> {
            paused = false;
            if (onExitToMenu != null) onExitToMenu.run();
        }));
        screen.addChild(new ui.widgets.Button(sliderX, buttonY + buttonH + 8, buttonW, buttonH, "Quit Game",
                () -> System.exit(0)));

        return screen;
    }

    public Player getPlayer() { return player; }
    public String getActivePrompt() { return activePrompt; }
    public String getOverlayMessage() { return overlayMessage; }
    public boolean isWon() { return won; }
    public boolean isPaused() { return paused; }

    // --- тик ---

    @Override
    public void tick(double dt) {
        // "E to peek" у gate-main: полный "зависон" — ни ввод, ни мир, вообще
        // ничего не обрабатывается, даже пауза и Esc, пока не сработает
        // имитация краша (п.3, доп.). Проверяем раньше вообще всего остального.
        if (fakeCrashTimer >= 0) {
            fakeCrashTimer -= dt;
            if (fakeCrashTimer <= 0) crashGame();
            return;
        }

        // Esc обрабатываем до всего остального — иначе с паузы не выйти
        if (input.wasPressed(KeyEvent.VK_ESCAPE)) paused = !paused;

        // Временный тугл godmode (F1): без урона, бесплатные/бесконечные
        // покупки, любая кирка ломает блоки мгновенно.
        if (input.wasPressed(KeyEvent.VK_F1)) {
            player.setGodMode(!player.isGodMode());
            showMessage(player.isGodMode() ? "God mode: ON" : "God mode: OFF", 1.5);
        }

        if (paused) {
            tickPauseScreen();
            return;   // сам игровой мир на паузе не тикает вообще
        }

        if (overlayTimer > 0) {
            overlayTimer -= dt;
            if (overlayTimer <= 0) overlayMessage = "";
        }

        if (won) {
            camera.follow(player);
            if (winReturnTimer >= 0) {
                winReturnTimer -= dt;
                if (winReturnTimer <= 0) {
                    winReturnTimer = Double.NEGATIVE_INFINITY;   // не сработает второй раз
                    if (onExitToMenu != null) onExitToMenu.run();
                }
            }
            return;
        }

        if (endgameSeqActive) {
            boolean stillFrozen = tickEndgameSequence(dt);
            if (stillFrozen) return;   // управление и мир заморожены, пока экран не почернел целиком
        }

        boolean left = input.isAnyDown(KeyEvent.VK_A, KeyEvent.VK_LEFT);
        boolean right = input.isAnyDown(KeyEvent.VK_D, KeyEvent.VK_RIGHT);
        // W — прыжок на земле и подъём на лестнице, S — спуск по лестнице (п.8)
        boolean up = input.isAnyDown(KeyEvent.VK_W, KeyEvent.VK_UP, KeyEvent.VK_SPACE);
        boolean down = input.isAnyDown(KeyEvent.VK_S, KeyEvent.VK_DOWN);

        player.tick(dt, field, left, right, up, down);
        if (player.consumeStep()) playStepSound();
        field.tick(dt, player);
        for (NpcPoint npc : npcs) npc.tick(dt, player);
        updateMusicReverb();
        tickDynamites(dt);
        tickEffects(dt);

        if (!endgameTriggered && player.depth() >= Constants.LAYER_4_END) {
            beginEndgameFall();
            camera.follow(player);
            return;
        }

        // Видимость считаем ДО копания: копать можно только то, что видно
        // сейчас, а волна должна учитывать уже случившиеся за кадр изменения.
        visibilityFrame++;
        field.updateVisibility(player.centerTileX(), player.centerTileY(),
                player.lightRadius() + 1, visibilityFrame);

        handleDigging(dt);
        handleInteractions();

        if (player.isDead()) {
            respawn("You died: " + player.getLastDeathReason());
        }

        camera.follow(player);
    }

    /**
     * Ввод для экрана паузы. Мышь в UI-дерево шлём отсюда, а не из GameWindow:
     * окно крутит Game как Scene и про этот Screen ничего не знает.
     */
    private void tickPauseScreen() {
        int mx = input.getMouseX();
        int my = input.getMouseY();

        if (input.wasLeftPressed()) {
            pauseScreen.handleMousePressed(mx, my, java.awt.event.MouseEvent.BUTTON1);
        } else if (input.wasLeftReleased()) {
            pauseScreen.handleMouseReleased(mx, my, java.awt.event.MouseEvent.BUTTON1);
        } else if (input.isLeftDown()) {
            pauseScreen.handleMouseDragged(mx, my);   // тянем ползунок громкости
        }
    }

    /** Осколки и тряска экрана — всё, что живёт «поверх» логики. */
    private void tickEffects(double dt) {
        particles.tick(dt);

        // урон замечаем по падению HP — не надо тянуть колбэки через весь Player
        int hp = player.getHealth();
        if (hp < lastHealth) {
            double severity = Math.min(1.0, (lastHealth - hp) / 40.0);
            shake(Math.max(0.35, severity));
        }
        lastHealth = hp;

        if (shakeTimer > 0) shakeTimer -= dt;
    }

    /** @param strength 0..1 — насколько сильно тряхнуть экран. */
    private void shake(double strength) {
        shakeTimer = Constants.SCREEN_SHAKE_TIME;
        shakeStrength = Math.max(shakeStrength, strength);
    }

    private void tickDynamites(double dt) {
        for (Iterator<Dynamite> it = dynamites.iterator(); it.hasNext(); ) {
            Dynamite d = it.next();
            d.tick(dt, field, player);
            if (d.isFinished()) it.remove();   // ждём, пока доиграет вспышка
        }
    }

    /** Копание курсором, как в Terraria: ЛКМ по тайлу в радиусе досягаемости (п.2). */
    private void handleDigging(double dt) {
        if (!input.isLeftDown()) {
            player.resetDigTarget(field);
            player.setDigging(false);
            return;
        }
        int tx = camera.screenToTileX(input.getMouseX());
        int ty = camera.screenToTileY(input.getMouseY());

        List<Block> broken = player.dig(field, tx, ty, dt, visibilityFrame);
        player.setDigging(player.hasDigTarget() || !broken.isEmpty());

        // у лестницы за раз осыпается вся колонна, поэтому список
        for (Block b : broken) {
            particles.burst(b.worldX, b.worldY, b.getType());

            if (b.getType() == BlockType.LADDER) {
                // снятая лестница возвращается в инвентарь, как в Minecraft
                player.addUtility(UtilityType.LADDER);
                continue;
            }
            OreType ore = b.drop();
            if (ore != null) player.addOre(ore);   // не влезло — просто пропадает, о переполнении говорит HUD

            playDigSound();
        }
    }

    /**
     * Звук поломки с кулдауном: динамит сносит десятки блоков разом, и без
     * ограничения они сливались бы в треск. Заодно звук перестал бы попадать
     * в такт, потому что каждый следующий обрывал предыдущий.
     */
    private void playDigSound() {
        // ищем свободный голос вместо жёсткого кулдауна на одном: несколько
        // блоков подряд не должны терять звук только потому, что предыдущий
        // экземпляр ещё не доиграл (п.5)
        for (int i = 0; i < sfxDigVoices.length; i++) {
            int idx = (digVoiceIndex + i) % sfxDigVoices.length;
            Audio voice = sfxDigVoices[idx];
            if (!voice.isPlaying()) {
                digVoiceIndex = (idx + 1) % sfxDigVoices.length;
                voice.play();
                return;
            }
        }
        // все голоса заняты одновременно — редкий случай, жертвуем следующим по кругу
        sfxDigVoices[digVoiceIndex].play();
        digVoiceIndex = (digVoiceIndex + 1) % sfxDigVoices.length;
    }

    /** Чем глубже игрок, тем гулче звучит музыка — простая реверберация по глубине (п.5). */
    private void updateMusicReverb() {
        double depthT = Math.max(0, Math.min(1, player.depth() / Constants.MUSIC_REVERB_MAX_DEPTH));
        music.setReverbWet((float) (depthT * Constants.MUSIC_REVERB_MAX_WET));
    }

    /** Питч слегка гуляет от шага к шагу — иначе ходьба звучит как метроном. */
    private void playStepSound() {
        float pitch = (float) (Constants.STEP_PITCH_MIN
                + Math.random() * (Constants.STEP_PITCH_MAX - Constants.STEP_PITCH_MIN));
        sfx_step.play(pitch);
    }

    /** Монетки и звон после удачной покупки у любого торговца (п.3, доп.). */
    private void playPurchaseEffect() {
        particles.burstCash(player.getX() + Constants.HITBOX_W / 2.0, player.getY());
        sfx_cash.play();
    }

    private void handleInteractions() {
        activePrompt = "";

        // товар у торговца листается на Q и колесо мыши (стрелки заняты движением, п.9)
        int wheel = input.consumeWheel();
        boolean nearShop = utilityShop.isPlayerInRange(player);
        if (nearShop) {
            if (wheel != 0) utilityShop.cycle(Integer.signum(wheel));
            if (input.wasPressed(KeyEvent.VK_Q)) utilityShop.cycle(1);
        }

        NpcPoint activeNpc = null;
        for (NpcPoint npc : npcs) {
            if (npc.isPlayerInRange(player)) {
                activeNpc = npc;
                activePrompt = npc.prompt(player);
                break;
            }
        }

        if (activeNpc == null && player.isOnLadder()) {
            activePrompt = "W / S - climb";
        }

        // "E to peek" у gate-main — пасхалка, не обычный NpcPoint (п.6, доп.)
        boolean nearGateMain = activeNpc == null && fakeCrashTimer < 0
                && player.distanceToTile(gateMainCenterTileX, gateMainCenterTileY) <= Constants.INTERACT_RANGE;
        if (nearGateMain) {
            activePrompt = "E to peek";
            if (input.wasPressed(KeyEvent.VK_E)) {
                // экран чернеет тем же кадром (draw() проверяет fakeCrashTimer
                // раньше всего остального) — отдельное сообщение тут уже не увидят
                fakeCrashTimer = 1.6;
            }
        }

        if (input.wasPressed(KeyEvent.VK_E) && activeNpc != null) {
            if (activeNpc.interact(player)) playPurchaseEffect();
        }

        if (input.wasPressed(KeyEvent.VK_F)) placeLadder();
        if (input.wasPressed(KeyEvent.VK_G)) placeDynamite();
        if (input.wasPressed(KeyEvent.VK_X)) respawn("Respawned - ore lost");
    }

    /**
     * Лестница — обычный блок: ставится по одному в клетку под курсором,
     * в пределах того же радиуса досягаемости, что и копание (п.8).
     */
    private void placeLadder() {
        if (!player.isGodMode() && player.getUtility(UtilityType.LADDER) <= 0) {
            showMessage("No ladders left", 1.5);
            return;
        }

        int tx = camera.screenToTileX(input.getMouseX());
        int ty = camera.screenToTileY(input.getMouseY());

        if (player.distanceToTile(tx + 0.5, ty + 0.5) > Constants.DIG_REACH) {
            showMessage("Too far to place a ladder", 1.5);
            return;
        }
        if (!field.canPlaceLadder(tx, ty)) {
            showMessage("No room for a ladder", 1.5);
            return;
        }

        player.consumeUtility(UtilityType.LADDER);
        field.placeLadder(tx, ty);
    }

    private void placeDynamite() {
        if (!player.consumeUtility(UtilityType.DYNAMITE)) {
            showMessage("No dynamite left", 1.5);
            return;
        }
        dynamites.add(new Dynamite(player.centerTileX(), player.centerTileY()));
    }

    /**
     * Игрок докопался до последнего слоя — где бы то ни было по X. Дальше
     * управление на время отдаётся скриптовой сцене (tickEndgameSequence), а
     * не физике (п.6).
     */
    private void beginEndgameFall() {
        endgameTriggered = true;
        endgameSeqActive = true;
        endgameTeleported = false;
        endgameLanded = false;
        endgameSeqTimer = 0;
        music.stop();   // резко, без затухания — вниз падаем в тишину (доп.)
    }

    /**
     * Три плавные фазы одного затемнения (п.6, правки): экран гаснет в
     * черноту (мир ещё виден и тикает как обычно), полностью чёрная пауза —
     * во время неё, ровно посередине, игрока незаметно переставляет в
     * финальную комнату и замораживает физику (иначе за секунду с лишним
     * темноты его отнесло бы течением или он влетел бы в лаву), и обратное
     * плавное проявление уже в комнате — управление к игроку возвращается
     * сразу по приземлении, ещё до того как экран проявится целиком.
     *
     * @return true, пока управление всё ещё заморожено (fade-out + чёрная пауза)
     */
    private boolean tickEndgameSequence(double dt) {
        endgameSeqTimer += dt;
        double blackEnd = Constants.ENDGAME_FADE_OUT + Constants.ENDGAME_BLACK_HOLD;

        if (!endgameTeleported && endgameSeqTimer >= Constants.ENDGAME_FADE_OUT + Constants.ENDGAME_BLACK_HOLD / 2) {
            endgameTeleported = true;
            player.teleportToTile(Constants.CORE_ROOM_CENTER_X, Constants.CORE_ROOM_TOP + 1);
        }

        if (endgameSeqTimer < blackEnd) {
            camera.follow(player);
            return true;   // ещё гаснет либо полностью черно — управление заморожено
        }

        if (!endgameLanded) {
            endgameLanded = true;
            // На пол сверху, с запасом высоты хитбокса — иначе игрок
            // приземляется чуть утопленным в бедрок пола и не может сдвинуться
            // с места вообще (коллизия блокирует движение по любой оси).
            player.teleportToTile(Constants.CORE_ROOM_CENTER_X, Constants.CORE_ROOM_BOTTOM - 2);
            visibilityFrame++;
            field.updateVisibility(player.centerTileX(), player.centerTileY(),
                    player.lightRadius() + 1, visibilityFrame);
            camera.follow(player);
        }

        if (endgameSeqTimer >= blackEnd + Constants.ENDGAME_FADE_IN) {
            endgameSeqActive = false;
        }
        return false;   // приземлились — управление уже работает, экран ещё дояснивается
    }

    /** 0 — экран чист, 1 — полностью чёрный. Используется только отрисовкой. */
    private double endgameOverlayAlpha() {
        if (!endgameSeqActive) return 0;
        double t = endgameSeqTimer;
        if (t < Constants.ENDGAME_FADE_OUT) {
            return t / Constants.ENDGAME_FADE_OUT;
        }
        double blackEnd = Constants.ENDGAME_FADE_OUT + Constants.ENDGAME_BLACK_HOLD;
        if (t < blackEnd) return 1.0;
        double total = blackEnd + Constants.ENDGAME_FADE_IN;
        if (t >= total) return 0;
        return 1.0 - (t - blackEnd) / Constants.ENDGAME_FADE_IN;
    }

    /** Открыли сундук в финальной комнате — это и есть конец игры (п.6). */
    private void onChestOpened() {
        won = true;
        overlayMessage = "You found the treasure. The mine finally pays off.";
        overlayTimer = Double.MAX_VALUE;
        winReturnTimer = 5.0;   // пара секунд полюбоваться победой, а потом обратно в меню (п.4)
    }

    /**
     * Пасхалка у gate-main: "E to peek" пугает игрока имитацией краша — экран
     * бьёт в чёрное и игра по-настоящему зависает (весь ввод и tick
     * заблокированы), а затем честно вылетает с ошибкой в консоль и
     * ненулевым кодом выхода — раз попросили настоящий вылет, а не заглушку.
     *
     * Текст ошибки — не обычная техническая тарабарщина: то, что за дверью,
     * в принципе не описывается словами (привет Королю в Жёлтом), поэтому и
     * "ошибка" — не про segfault, а про то, что это никак не удаётся
     * интерпретировать.
     */
    private void crashGame() {
        RuntimeException fake = new IllegalStateException(
                "what lies beyond the door cannot be rendered - the knowledge cannot be interpreted");
        fake.printStackTrace();
        System.exit(1);
    }

    /** Смерть и самоумирание работают одинаково: вся руда пропадает (п.8). */
    private void respawn(String reason) {
        player.respawn(spawnTileX, spawnTileY);
        showMessage(reason, 2.5);
    }

    private void showMessage(String text, double seconds) {
        overlayMessage = text;
        overlayTimer = seconds;
    }

    // --- отрисовка ---

    @Override
    public void draw(DrawCtx ctx) {
        Graphics2D g = ctx.g;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        // сглаживание для пиксель-арта только мылит картинку, а стоит времени;
        // текст при этом сглаживаем — он читается заметно лучше
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int viewW = camera.getViewWidthPx() * Constants.SCALE;
        int viewH = camera.getViewHeightPx() * Constants.SCALE;

        if (fakeCrashTimer >= 0) {
            // резкий чёрный экран поверх вообще всего интерфейса, без затухания —
            // это "зависание", а не кат-сцена (п.3, доп.)
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, viewW, viewH);
            tickFrameCounter();
            return;
        }

        double endgameAlpha = endgameOverlayAlpha();
        if (endgameAlpha >= 0.999) {
            // полностью чёрная фаза — мир всё равно не виден, не тратим кадр
            // на его отрисовку (тут же прячется незаметный телепорт, п.6)
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, viewW, viewH);
            tickFrameCounter();
            return;
        }

        drawSky(g, viewW, viewH);
        drawEndgameScene(g, viewW, viewH);

        // тряска сдвигает только мир: HUD дёргаться вместе с ним не должен
        double shakeX = 0;
        double shakeY = 0;
        if (shakeTimer > 0) {
            double fade = shakeTimer / Constants.SCREEN_SHAKE_TIME;
            double amp = Constants.SCREEN_SHAKE_AMPLITUDE * shakeStrength * fade;
            shakeX = (Math.random() - 0.5) * 2 * amp;
            shakeY = (Math.random() - 0.5) * 2 * amp;
        } else {
            shakeStrength = 0;
        }
        g.translate(shakeX, shakeY);

        drawBase(g);
        field.draw(g, camera.getX(), camera.getY(), viewW, viewH);
        drawBreakingBlock(g);

        for (Dynamite d : dynamites) d.draw(g, camera.getX(), camera.getY());
        for (NpcPoint npc : npcs) npc.draw(g, camera.getX(), camera.getY());
        player.draw(g, camera.getX(), camera.getY());
        particles.draw(g, camera.getX(), camera.getY());

        drawDigTarget(g);
        drawDarkness(g, viewW, viewH);

        g.translate(-shakeX, -shakeY);

        hud.draw(new DrawCtx(g, 0, 0));

        // экран паузы — последним слоем, поверх мира и HUD
        if (paused) pauseScreen.draw(new DrawCtx(g, 0, 0));

        // затемнение финального падения — поверх вообще всего, включая HUD (п.6)
        if (endgameAlpha > 0) {
            g.setColor(new Color(0, 0, 0, (int) Math.round(endgameAlpha * 255)));
            g.fillRect(0, 0, viewW, viewH);
        }

        tickFrameCounter();
    }

    private void tickFrameCounter() {
        frames++;
        long now = System.currentTimeMillis();
        if (now - lastTime >= 1000) {
            fps = frames;
            frames = 0;
            lastTime = now;
        }
    }

    private static final Color SKY = new Color(96, 150, 200);
    /** Затемнение подложки под дрожащим блоком — она «уже почти дыра». */
    private static final Color BREAK_BACKDROP = new Color(0, 0, 0, 165);
    private static final Color DIG_OUTLINE = new Color(255, 255, 255, 110);

    private void drawSky(Graphics2D g, int viewW, int viewH) {
        g.setColor(SKY);
        int horizon = (int) camera.worldToScreenY(Constants.SURFACE_Y * Constants.TILE);
        g.fillRect(0, 0, viewW, Math.max(0, Math.min(viewH, horizon)));
    }

    /**
     * Финальная комната (п.6): чёрный залитый фон, а перед ним — три слоя, от
     * заднего к переднему — gate-main (стоит на месте, у левого края комнаты,
     * как нарисовано), gate-gray и gate-black (гуляют параллаксом чуть быстрее
     * камеры — они «ближе» к игроку). Рисуется только пока камера реально
     * смотрит на эту комнату, а не на каждом кадре по всей игре.
     */
    private static final double GATE_BLACK_PARALLAX = 1.15;
    private static final double GATE_GRAY_PARALLAX = 1.05;

    private void drawEndgameScene(Graphics2D g, int viewW, int viewH) {
        int scale = Constants.SCALE;
        double roomTopPx = Constants.CORE_ROOM_TOP * Constants.TILE;
        double roomBottomPx = (Constants.CORE_ROOM_BOTTOM + 1) * Constants.TILE;
        double camY = camera.getY();
        double camX = camera.getX();
        if (camY + viewH / (double) scale < roomTopPx || camY > roomBottomPx) return;

        g.setColor(Color.BLACK);
        g.fillRect(0, 0, viewW, viewH);

        int roomHpx = Constants.CORE_ROOM_H * Constants.TILE * scale;
        int roomTopScreenY = (int) Math.round((roomTopPx - camY) * scale);

        drawParallaxLayer(g, "lastLayer/gate-gray", camX, roomTopScreenY, roomHpx, viewW, GATE_GRAY_PARALLAX, 0.5f);
        drawParallaxLayer(g, "lastLayer/gate-black", camX, roomTopScreenY, roomHpx, viewW, GATE_BLACK_PARALLAX, 0.65f);

        // gate-main стоит у левого края комнаты фиксированно — это часть сцены, не параллакс
        int mainX = (int) Math.round((Constants.CORE_ROOM_LEFT * Constants.TILE - camX) * scale);
        g.drawImage(Textures.get("lastLayer/gate-main"), mainX, roomTopScreenY, roomHpx, roomHpx, null);

        drawEndgameSign(g, camX, camY, scale);
    }

    /** "Не ходите налево" — на стене там, куда игрок приземляется после падения (п.6). */
    private static final double SIGN_TILE_SIZE = 6;

    private void drawEndgameSign(Graphics2D g, double camX, double camY, int scale) {
        double signWorldX = (Constants.CORE_ROOM_CENTER_X - SIGN_TILE_SIZE / 2) * Constants.TILE;
        double signWorldY = (Constants.CORE_ROOM_BOTTOM - SIGN_TILE_SIZE - 3) * Constants.TILE;
        int size = (int) Math.round(SIGN_TILE_SIZE * Constants.TILE * scale);
        int sx = (int) Math.round((signWorldX - camX) * scale);
        int sy = (int) Math.round((signWorldY - camY) * scale);
        g.drawImage(Textures.get("lastLayer/sign"), sx, sy, size, size, null);
    }

    /**
     * Один параллакс-слой: исходник квадратный, растягиваем на высоту комнаты
     * и тайлим по ширине экрана со сдвигом от камеры на parallaxFactor —
     * бесшовно и без накопления ошибки на любом camX.
     */
    private void drawParallaxLayer(Graphics2D g, String texture, double camX, int screenY, int tileSize,
                                    int viewW, double parallaxFactor, float alpha) {
        var img = Textures.get(texture);
        double scroll = camX * parallaxFactor;
        double localOffset = ((scroll % tileSize) + tileSize) % tileSize;
        int firstX = (int) Math.round(-localOffset);

        var oldComposite = g.getComposite();
        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha));
        for (int sx = firstX; sx < viewW; sx += tileSize) {
            g.drawImage(img, sx, screenY, tileSize, tileSize, null);
        }
        g.setComposite(oldComposite);
    }

    /** Фон магазина на базе — рисуется один раз за кадр, отдельно от NPC. */
    private void drawBase(Graphics2D g) {
        int scale = Constants.SCALE;
        int w = 26 * Constants.TILE;
        int h = 6 * Constants.TILE;
        int x = (int) camera.worldToScreenX((spawnTileX - 15) * Constants.TILE);
        int y = (int) camera.worldToScreenY((Constants.SURFACE_Y - 6) * Constants.TILE);
        g.drawImage(Textures.get("shop"), x, y, w * scale, h * scale, null);
    }

    /**
     * Блок, который сейчас копают: дрожит и обрастает трещинами.
     *
     * Рисуется поверх кэша чанка, а исходное место сначала закрывается тем,
     * что окажется под блоком после поломки — иначе смещённая копия двоилась
     * бы с оригиналом, вмороженным в кэш.
     */
    private void drawBreakingBlock(Graphics2D g) {
        if (!player.hasDigTarget()) return;

        int tx = player.getDigTargetX();
        int ty = player.getDigTargetY();
        Block block = field.getBlock(tx, ty);
        double progress = block.digProgress();
        if (progress <= 0 || block.isAir()) return;

        int scale = Constants.SCALE;
        int size = Constants.TILE * scale;
        int sx = (int) camera.worldToScreenX(tx * Constants.TILE);
        int sy = (int) camera.worldToScreenY(ty * Constants.TILE);

        // подложка: фон, если тут уже копали, иначе затемнённая та же порода
        BlockType bg = field.getBackground(tx, ty);
        g.drawImage(Textures.get(bg != null ? bg.texture : block.getType().texture),
                sx, sy, size, size, null);
        g.setColor(BREAK_BACKDROP);
        g.fillRect(sx, sy, size, size);

        // чем ближе к развалу, тем сильнее дрожь
        double amp = Constants.BLOCK_SHAKE_AMPLITUDE * progress * scale;
        int ox = (int) Math.round((Math.random() - 0.5) * 2 * amp);
        int oy = (int) Math.round((Math.random() - 0.5) * 2 * amp);

        int rot = block.getType().isRotatable() ? block.getRotation() : 0;
        g.drawImage(Textures.get(block.getType().texture, rot), sx + ox, sy + oy, size, size, null);
        if (block.getOre() != null) {
            g.drawImage(Textures.get(block.getOre().overlay, rot), sx + ox, sy + oy, size, size, null);
        }

        // трещины: стадия по прогрессу
        int stages = Constants.BREAK_STAGES;
        int stage = Math.min(stages, Math.max(1, (int) Math.ceil(progress * stages)));
        g.drawImage(Textures.get("break/break" + stage), sx + ox, sy + oy, size, size, null);
    }

    private void drawDigTarget(Graphics2D g) {
        int tx = camera.screenToTileX(input.getMouseX());
        int ty = camera.screenToTileY(input.getMouseY());
        if (player.distanceToTile(tx + 0.5, ty + 0.5) > Constants.DIG_REACH) return;
        if (field.getBlock(tx, ty).isAir()) return;

        int size = Constants.TILE * Constants.SCALE;
        int sx = (int) camera.worldToScreenX(tx * Constants.TILE);
        int sy = (int) camera.worldToScreenY(ty * Constants.TILE);
        g.setColor(DIG_OUTLINE);
        g.drawRect(sx, sy, size - 1, size - 1);
    }

    private void drawDarkness(Graphics2D g, int viewW, int viewH) {
        // ядро по ТЗ «чёрное/светящееся» — финальную комнату не затемняем,
        // иначе панчлайн с дверью игрок просто не разглядит
        if (player.depth() >= Constants.LAYER_4_END) return;

        // у поверхности темнота включается плавно, на базе её нет вовсе (п.7)
        double depth = player.centerTileY() - Constants.SURFACE_Y;
        double strength = Math.max(0, Math.min(1, depth / 6.0));
        if (strength <= 0) return;

        double px = camera.worldToScreenX(player.getX() + Constants.HITBOX_W / 2.0);
        double py = camera.worldToScreenY(player.getY() + Constants.HITBOX_H / 2.0);
        Lighting.drawDarkness(g, viewW, viewH, px, py, player.lightRadius(), strength);
    }

    public int getFps() {
        return fps;
    }
}