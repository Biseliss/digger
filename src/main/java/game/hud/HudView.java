package game.hud;

import game.Constants;
import game.Game;
import game.entity.Player;
import game.item.OreType;
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
        g.drawString(p.getTool().getName(), 12, 62);

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
    }

    private void drawOres(Graphics2D g, Player p) {
        g.setFont(SMALL);
        int limit = p.getTool().getOreCarryLimit();
        int y = 12;
        for (OreType ore : OreType.values()) {
            int count = p.getOreCount(ore);
            int right = width - 12;
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
