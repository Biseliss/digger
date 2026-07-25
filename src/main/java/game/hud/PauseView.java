package game.hud;

import ui.DrawCtx;
import ui.UIObject;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

/**
 * Задник экрана паузы: затемнение всего кадра, заголовок и памятка по
 * управлению. Слайдеры громкости лежат отдельными виджетами поверх — им
 * нужен свой ввод, а этот элемент просто рисует.
 */
public class PauseView extends UIObject {
    private static final Font TITLE = new Font("SansSerif", Font.BOLD, 34);
    private static final Font HINT_KEY = new Font("SansSerif", Font.BOLD, 14);
    private static final Font HINT_TEXT = new Font("SansSerif", Font.PLAIN, 14);

    private static final Color DIM = new Color(0, 0, 0, 190);
    private static final Color PANEL = new Color(28, 28, 36, 235);
    private static final Color BORDER = new Color(90, 90, 110);
    private static final Color TITLE_COLOR = new Color(242, 213, 68);
    private static final Color KEY_COLOR = new Color(150, 200, 245);
    private static final Color TEXT_COLOR = new Color(220, 220, 228);
    private static final Color FOOTER_COLOR = new Color(160, 160, 170);

    /** Памятка по управлению (п.2 ТЗ). */
    private static final String[][] CONTROLS = {
            {"WASD / стрелки", "движение, W — прыжок"},
            {"W / S", "лезть вверх-вниз по лестнице"},
            {"ЛКМ", "копать блок под курсором"},
            {"F", "поставить лестницу"},
            {"G", "поставить динамит"},
            {"E", "торговцы на базе"},
            {"Q / колесо", "листать товар у торговца"},
            {"X", "застрял — вернуться на базу"},
            {"Esc", "пауза"},
    };

    public PauseView(int width, int height) {
        super(0, 0, width, height);
    }

    /** Где начинается панель — по этому Game раскладывает слайдеры. */
    public int panelX() {
        return (width - panelWidth()) / 2;
    }

    public int panelY() {
        return (height - panelHeight()) / 2;
    }

    public int panelWidth() {
        return 460;
    }

    public int panelHeight() {
        return 420;
    }

    @Override
    protected void onDraw(DrawCtx ctx) {
        Graphics2D g = ctx.g;

        // затемняем игру под собой, чтобы панель читалась
        g.setColor(DIM);
        g.fillRect(0, 0, width, height);

        int px = panelX();
        int py = panelY();
        int pw = panelWidth();
        int ph = panelHeight();

        g.setColor(PANEL);
        g.fillRect(px, py, pw, ph);
        g.setColor(BORDER);
        g.drawRect(px, py, pw - 1, ph - 1);

        g.setFont(TITLE);
        g.setColor(TITLE_COLOR);
        FontMetrics fm = g.getFontMetrics();
        String title = "ПАУЗА";
        g.drawString(title, px + (pw - fm.stringWidth(title)) / 2, py + 52);

        // отступ такой, чтобы список не липнул к нижнему ползунку громкости
        drawControls(g, px + 28, py + 196, pw - 56);

        g.setFont(HINT_TEXT);
        g.setColor(FOOTER_COLOR);
        FontMetrics ffm = g.getFontMetrics();
        String footer = "Esc — продолжить";
        g.drawString(footer, px + (pw - ffm.stringWidth(footer)) / 2, py + ph - 20);
    }

    private void drawControls(Graphics2D g, int x, int y, int w) {
        g.setFont(HINT_TEXT);
        g.setColor(TEXT_COLOR);
        g.drawString("Управление", x, y);

        int rowY = y + 22;
        for (String[] row : CONTROLS) {
            g.setFont(HINT_KEY);
            g.setColor(KEY_COLOR);
            g.drawString(row[0], x, rowY);

            g.setFont(HINT_TEXT);
            g.setColor(TEXT_COLOR);
            g.drawString(row[1], x + 150, rowY);

            rowY += 19;
        }
    }
}
