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
import game.item.OreType;
import game.item.UtilityType;
import game.npc.NpcPoint;
import game.npc.OreBuyerNpc;
import game.npc.ToolsmithNpc;
import game.npc.UtilityShopNpc;
import game.render.Lighting;
import game.render.Textures;
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

    private int fps = 0;
    private int frames = 0;
    private long lastTime = System.currentTimeMillis();


    public Audio music; // для звуков будет public Audio sounds; это по сути звуковые дорожки отдельные

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

        Audio music = new Audio();
        music.setFile(0); // подгружает музыку из регистра, однако я потом сделаю поиск по названию файла а не индексу в списке, это временно.
        music.loop(); // проигрывает и лупит её, есть функция play(), она играет без лупа один раз, подходит для отдельных звуков 
    }

    public Player getPlayer() { return player; }
    public String getActivePrompt() { return activePrompt; }
    public String getOverlayMessage() { return overlayMessage; }
    public boolean isWon() { return won; }

    // --- тик ---

    @Override
    public void tick(double dt) {
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
        field.tick(dt, player);
        tickDynamites(dt);

        handleDigging(dt);
        handleInteractions();
        checkWin();

        // туман войны: раскрываем то, что игрок реально видит в круге света (п.7)
        field.revealCircle(player.centerTileX(), player.centerTileY(), player.lightRadius() + 1);

        if (player.isDead()) {
            respawn("You died: " + player.getLastDeathReason());
        }

        camera.follow(player);
    }

    private void tickDynamites(double dt) {
        for (Iterator<Dynamite> it = dynamites.iterator(); it.hasNext(); ) {
            Dynamite d = it.next();
            d.tick(dt, field, player);
            if (d.isExploded()) it.remove();
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

        BlockType broken = player.dig(field, tx, ty, dt);
        if (broken == null) return;

        if (broken == BlockType.LADDER) {
            // снятая лестница возвращается в инвентарь, как в Minecraft
            if (!player.addUtility(UtilityType.LADDER)) showMessage("Ladder stack is full", 1.5);
        } else if (broken.drop != null) {
            OreType ore = broken.drop;
            if (!player.addOre(ore)) showMessage("Can't carry more " + ore.displayName, 1.5);
        }
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
        drawBase(g);
        field.draw(g, camera.getX(), camera.getY(), viewW, viewH);

        for (Dynamite d : dynamites) d.draw(g, camera.getX(), camera.getY());
        for (NpcPoint npc : npcs) npc.draw(g, camera.getX(), camera.getY());
        player.draw(g, camera.getX(), camera.getY());

        drawDigTarget(g);
        drawDarkness(g, viewW, viewH);

        hud.draw(new DrawCtx(g, 0, 0));


        frames++;

        long now = System.currentTimeMillis();
        if (now - lastTime >= 1000) {
            fps = frames;
            frames = 0;
            lastTime = now;
        }
    }

    private static final Color SKY = new Color(96, 150, 200);
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
