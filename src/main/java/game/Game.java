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

    /** Пауза: игровой тик не выполняется, поверх кадра рисуется свой экран. */
    private boolean paused;
    private final Screen pauseScreen;

    /** Счётчик кадров для отметки «блок виден прямо сейчас» (п.7). */
    private int visibilityFrame;

    private final game.render.Particles particles = new game.render.Particles();
    /** Кулдаун, чтобы десяток блоков подряд не превращался в треск. */
    private double digSoundCooldown;

    /** Тряска экрана от урона: сколько ещё трясти и насколько сильно. */
    private double shakeTimer;
    private double shakeStrength;
    /** HP на прошлом кадре — по нему замечаем, что игрока задело. */
    private int lastHealth = Constants.PLAYER_MAX_HP;

    private int fps = 0;
    private int frames = 0;
    private long lastTime = System.currentTimeMillis();


    public Audio music;
    public Audio sfx_dig;
    public Audio sfx_step;

    public Game(Input input, int screenW, int screenH) {
        this.input = input;

        new WorldGenerator(Constants.DEFAULT_SEED).generateInto(field);

        this.player = new Player(spawnTileX, spawnTileY);
        this.camera = new Camera(screenW, screenH);

        // база: три точки взаимодействия, «подойти + E» (п.9)
        npcs.add(new OreBuyerNpc(spawnTileX - 8, Constants.SURFACE_Y - 2));
        npcs.add(new ToolsmithNpc(spawnTileX - 3, Constants.SURFACE_Y - 2));
        utilityShop = new UtilityShopNpc(spawnTileX + 3, Constants.SURFACE_Y - 2);
        npcs.add(utilityShop);

        this.hud = new Screen(screenW, screenH);
        hud.addChild(new HudView(this, screenW, screenH));

        music = new Audio();

        sfx_dig = new Audio();
        sfx_dig.setFile("SFX_Dig");

        sfx_step = new Audio();
        sfx_step.setFile("SFX_Step");

        music.setFile("Soundtrack"); // подгружает музыку из регистра, однако я потом сделаю поиск по названию файла а не индексу в списке, это временно.
        music.loop(); // проигрывает и лупит её, есть функция play(), она играет без лупа один раз, подходит для отдельных звуков

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
                "Музыка", music.getVolume(), v -> music.setVolume((float) v)));
        screen.addChild(new ui.widgets.Slider(sliderX, sliderY + 46, sliderW, 26,
                "Звуки", sfx_dig.getVolume(), v -> {
            sfx_dig.setVolume((float) v);
            sfx_step.setVolume((float) v);
        }));

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
        // Esc обрабатываем до всего остального — иначе с паузы не выйти
        if (input.wasPressed(KeyEvent.VK_ESCAPE)) paused = !paused;

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
            return;
        }

        boolean left = input.isAnyDown(KeyEvent.VK_A, KeyEvent.VK_LEFT);
        boolean right = input.isAnyDown(KeyEvent.VK_D, KeyEvent.VK_RIGHT);
        // W — прыжок на земле и подъём на лестнице, S — спуск по лестнице (п.8)
        boolean up = input.isAnyDown(KeyEvent.VK_W, KeyEvent.VK_UP, KeyEvent.VK_SPACE);
        boolean down = input.isAnyDown(KeyEvent.VK_S, KeyEvent.VK_DOWN);

        player.tick(dt, field, left, right, up, down);
        if (player.consumeStep()) playStepSound();
        field.tick(dt, player);
        tickDynamites(dt);
        tickEffects(dt);

        // Видимость считаем ДО копания: копать можно только то, что видно
        // сейчас, а волна должна учитывать уже случившиеся за кадр изменения.
        visibilityFrame++;
        field.updateVisibility(player.centerTileX(), player.centerTileY(),
                player.lightRadius() + 1, visibilityFrame);

        handleDigging(dt);
        handleInteractions();
        checkWin();

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

    /** Осколки, кулдаун звука и тряска экрана — всё, что живёт «поверх» логики. */
    private void tickEffects(double dt) {
        particles.tick(dt);

        if (digSoundCooldown > 0) digSoundCooldown -= dt;

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
            return;
        }
        int tx = camera.screenToTileX(input.getMouseX());
        int ty = camera.screenToTileY(input.getMouseY());

        // у лестницы за раз осыпается вся колонна, поэтому список
        for (Block broken : player.dig(field, tx, ty, dt, visibilityFrame)) {
            particles.burst(broken.worldX, broken.worldY, broken.getType());

            if (broken.getType() == BlockType.LADDER) {
                // снятая лестница возвращается в инвентарь, как в Minecraft
                player.addUtility(UtilityType.LADDER);
                continue;
            }
            OreType ore = broken.drop();
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
        if (digSoundCooldown > 0) return;
        digSoundCooldown = Constants.DIG_SOUND_COOLDOWN;
        sfx_dig.play();
    }

    /** Питч слегка гуляет от шага к шагу — иначе ходьба звучит как метроном. */
    private void playStepSound() {
        float pitch = (float) (Constants.STEP_PITCH_MIN
                + Math.random() * (Constants.STEP_PITCH_MAX - Constants.STEP_PITCH_MIN));
        sfx_step.play(pitch);
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

        if (input.wasPressed(KeyEvent.VK_E) && activeNpc != null) {
            activeNpc.interact(player);
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
        if (player.getUtility(UtilityType.LADDER) <= 0) {
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

    private void checkWin() {
        int tx = (int) player.centerTileX();
        int ty = (int) player.centerTileY();
        if (field.getBlock(tx, ty).getType() == BlockType.YELLOW_DOOR
                || field.getBlock(tx, ty + 1).getType() == BlockType.YELLOW_DOOR) {
            won = true;
            overlayMessage = "You found the door. It was unlocked all along.";
            overlayTimer = Double.MAX_VALUE;
        }
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

        drawSky(g, viewW, viewH);

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

    /** Фон магазина на базе — рисуется один раз за кадр, отдельно от NPC. */
    private void drawBase(Graphics2D g) {
        int scale = Constants.SCALE;
        int w = 26 * Constants.TILE;
        int h = 6 * Constants.TILE;
        int x = (int) camera.worldToScreenX((spawnTileX - 11) * Constants.TILE);
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