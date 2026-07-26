package game.hud;

import game.Constants;
import game.Game;
import game.entity.Player;
import game.item.OreType;
import game.item.Tool;
import game.item.UtilityType;
import game.render.Textures;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import ui.DrawCtx;
import ui.UIObject;


/**
 * Весь HUD (п.9) одним виджетом поверх UI-дерева: без инвентарного окна,
 * ресурсы и состояние читаются прямо с экрана.
 *
 * Это обычный кастомный UIObject — ровно тот случай, ради которого в
 * библиотеке и сделан хук onDraw.
 */
public class HudView extends UIObject {
    private static final Font FONT = new Font("SansSerif", Font.BOLD, 14);
    private static final Font SMALL = new Font("SansSerif", Font.PLAIN, 12);
    private static final Font BIG = new Font("SansSerif", Font.BOLD, 26);

    // цвета заранее: HUD рисуется каждый кадр, незачем плодить мусор для GC
    private static final Color HP_FULL = new Color(220, 60, 60);
    private static final Color HP_EMPTY = new Color(70, 40, 40);
    private static final Color DIM = new Color(210, 210, 210);
    private static final Color FAINT = new Color(200, 200, 200, 200);
    private static final Color ORE_FULL = new Color(255, 170, 90);
    private static final Color PANEL = new Color(0, 0, 0, 180);
    private static final Color OVERLAY_BG = new Color(0, 0, 0, 150);
    private static final Color WIN_TEXT = new Color(255, 220, 80);
    private static final Color LOSE_TEXT = new Color(255, 120, 120);

    private final Game game;

    public HudView(Game game, int width, int height) {
        super(0, 0, width, height);
        this.game = game;
    }

    @Override
    protected void onDraw(DrawCtx ctx) {
        Graphics2D g = ctx.g;
        Player p = game.getPlayer();

        drawStats(g, p);
        drawOres(g, p);
        drawGoal(g, p);
        drawPrompt(g);
        drawStuckHint(g);
        drawOverlayMessage(g);
        drawFps(g);
    }

    private void drawStats(Graphics2D g, Player p) {
        g.setFont(FONT);

        // HP: внутри 100, в HUD показываем /10 (п.14)
        int hp = p.getHudHealth();
        int max = Constants.PLAYER_MAX_HP / Constants.HUD_HP_DIVISOR;
        for (int i = 0; i < max; i++) {
            g.setColor(i < hp ? HP_FULL : HP_EMPTY);
            g.fillRect(12 + i * 14, 12, 10, 10);
        }

        g.setColor(Color.WHITE);
        g.drawString("$" + p.getMoney(), 12, 44);
        g.setFont(SMALL);
        g.setColor(DIM);
        g.drawImage(Textures.get(p.getTool().getIcon()), 12, 50, 16, 16, null);
        g.drawString(p.getTool().getName(), 32, 62);

        int depth = Math.max(0, p.depth());
        g.drawString("Depth " + depth + "  (" + p.currentLayer().displayName + ")", 12, 78);

        // купленные пассивки и расходники
        int x = 12;
        int y = 92;
        for (UtilityType type : UtilityType.values()) {
            int count = p.getUtility(type);
            if (count <= 0) continue;
            g.drawImage(Textures.get(type.icon), x, y, 16, 16, null);
            g.setColor(Color.WHITE);
            if (!type.passive) g.drawString("x" + count, x + 18, y + 13);
            x += type.passive ? 22 : 44;
        }

        drawPressureHint(g, p);
    }

    private static final Color WARNING = new Color(255, 180, 90);

    /**
     * Фидбек игроков: несколько раз умирали от давления в глубоких слоях и
     * не понимали, чем с ним бороться. Подсказка появляется, как только
     * куплена каменная кирка (то есть игрок уже способен закопаться туда,
     * где давление реально бьёт), и пропадает, как только броня куплена.
     */
    private void drawPressureHint(Graphics2D g, Player p) {
        if (p.getTool().getLevel() < 1 || p.hasArmor()) return;
        g.setFont(SMALL);
        g.setColor(WARNING);
        g.drawString("Tip: buy Armor at the shop - it stops the deep-layer pressure damage", 12, 118);
    }

    private static final Color UNKNOWN = new Color(140, 140, 140);

    private void drawOres(Graphics2D g, Player p) {
        g.setFont(SMALL);
        int y = 12;
        for (OreType ore : OreType.values()) {
            int right = width - 12;

            if (!p.hasDiscovered(ore)) {
                // руду ещё не находили — не спойлерим ни иконку, ни лимит (п.1)
                g.setColor(UNKNOWN);
                g.drawString("??? 0/0", right - 78, y + 13);
                y += 20;
                continue;
            }

            int count = p.getOreCount(ore);
            int limit = p.getTool().getOreCarryLimit(ore);
            boolean full = count >= limit;

            g.drawImage(Textures.get(ore.icon), right - 78, y, 16, 16, null);
            g.setColor(full ? ORE_FULL : Color.WHITE);
            g.drawString(count + "/" + limit, right - 56, y + 13);

            // переполнение показываем значком у самого слота, а не всплывающей
            // строкой посреди экрана — иначе она лезет поверх игры на каждый удар
            if (full) drawFullMark(g, right - 16, y);

            y += 20;
        }
    }

    /** Восклицательный знак в кружке: «сюда больше не влезет». */
    private void drawFullMark(Graphics2D g, int x, int y) {
        int size = 14;
        int cx = x + size / 2;

        g.setColor(ORE_FULL);
        g.fillOval(x, y + 1, size, size);
        g.setColor(new Color(30, 20, 10));
        g.fillRect(cx - 1, y + 4, 2, 6);      // палочка
        g.fillRect(cx - 1, y + 12, 2, 2);     // точка
    }

    private static final Color GOAL_TITLE = new Color(242, 213, 68);
    private static final Color GOAL_DONE = new Color(120, 220, 120);
    private static final Color GOAL_PENDING = new Color(210, 210, 210);

    /**
     * "Next Goal" под списком руд (п.4): что покупаем следующим и чего для
     * этого не хватает. После падения в финальную комнату (п.6) переключается
     * на единственную оставшуюся цель — сама кирка тут уже ни при чём.
     */
    private void drawGoal(Graphics2D g, Player p) {
        String title;
        String[] items;
        boolean[] done;

        if (game.isEndgameTriggered()) {
            title = "Goal: Dig.";
            items = new String[]{"Explore the underground in search of something unusual"};
            done = new boolean[]{false};
        } else {
            Tool tool = p.getTool();
            if (tool.isMaxed()) {
                title = "Goal: Dig deeper.";
                items = new String[]{"Find the bottom of the mine"};
                done = new boolean[]{false};
            } else {
                OreType material = tool.getUpgradeMaterial();
                int neededMoney = tool.getUpgradePrice();
                int neededOre = tool.getUpgradeMaterialAmount();
                title = "Goal: Buy " + tool.getNextName() + " Pickaxe.";
                items = new String[]{
                        "Earn $" + neededMoney,
                        "Collect " + neededOre + " " + material.displayName
                };
                done = new boolean[]{
                        p.getMoney() >= neededMoney,
                        p.getOreCount(material) >= neededOre
                };
            }
        }

        int lineH = 18;
        int padding = 8;
        int boxW = 260;
        int boxH = padding * 2 + lineH * (1 + items.length);
        int x = width - 12 - boxW;
        int y = 12 + OreType.values().length * 20 + 10;

        g.setColor(PANEL);
        g.fillRect(x, y, boxW, boxH);

        g.setFont(FONT);
        g.setColor(GOAL_TITLE);
        g.drawString(title, x + padding, y + padding + 12);

        g.setFont(SMALL);
        for (int i = 0; i < items.length; i++) {
            String box = done[i] ? "[x] " : "[ ] ";
            g.setColor(done[i] ? GOAL_DONE : GOAL_PENDING);
            g.drawString(box + items[i], x + padding, y + padding + (i + 2) * lineH - 4);
        }
    }

    /** Подсказка рядом с игроком: NPC, лестница, товар торговца. */
    private void drawPrompt(Graphics2D g) {
        String prompt = game.getActivePrompt();
        if (prompt == null || prompt.isEmpty()) return;

        g.setFont(FONT);
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(prompt) + 20;
        int x = (width - w) / 2;
        int y = height - 96;

        g.setColor(PANEL);
        g.fillRect(x, y, w, 26);
        g.setColor(Color.WHITE);
        g.drawString(prompt, x + 10, y + 18);
    }

    /** Требование п.8: способ выбраться из софт-лока всегда на виду. */
    private void drawStuckHint(Graphics2D g) {
        g.setFont(SMALL);
        g.setColor(FAINT);
        String hint = "Got stuck? Press X to respawn (you will lose your ores)";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(hint, (width - fm.stringWidth(hint)) / 2, height - 14);
    }

    private void drawOverlayMessage(Graphics2D g) {
        String msg = game.getOverlayMessage();
        if (msg == null || msg.isEmpty()) return;

        g.setColor(OVERLAY_BG);
        g.fillRect(0, height / 2 - 40, width, 80);

        g.setFont(BIG);
        FontMetrics fm = g.getFontMetrics();
        g.setColor(game.isWon() ? WIN_TEXT : LOSE_TEXT);
        g.drawString(msg, (width - fm.stringWidth(msg)) / 2, height / 2 + 8);
    }

    private void drawFps(Graphics2D g) {
        g.setFont(SMALL);
        g.setColor(FAINT);
        String fps = "FPS: " + game.getFps();
        g.drawString(fps, width - 12 - g.getFontMetrics().stringWidth(fps), height - 14);
    }
}
