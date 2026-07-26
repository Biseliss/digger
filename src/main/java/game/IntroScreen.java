package game;

import game.render.Textures;
import ui.DrawCtx;
import ui.Screen;
import ui.UIObject;
import ui.widgets.Button;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Обучающий интро-экран (п.2): показывается один раз перед стартом Game,
 * коротко объясняет базовые механики картинками из уже существующих текстур
 * (без отдельного арта) и озвучивает общую цель забега.
 */
public class IntroScreen {
    private record Slide(String title, String subtitle, String[] icons) {}

    private static final Slide[] SLIDES = {
            new Slide("1. Dig a Hole",
                    "Left-click breaks the block under the cursor. Start tunneling down.",
                    new String[]{"player", "pickaxe-wooden", "block_stone"}),
            new Slide("2. Sell Ores, Buy Equipment",
                    "Carry ore to the buyer, then spend it at the toolsmith and the shop.",
                    new String[]{"icon_iron", "npc/krill/krill", "cash", "pickaxe-copper"}),
            new Slide("3. Dig a Bigger Hole",
                    "Better pickaxes reach deeper layers - and rarer loot.",
                    new String[]{"pickaxe-diamond", "icon_diamond", "block_core"}),
    };

    private static final String GOAL_LINE = "Goal: uncover the secrets of the Underground City";

    private final Screen screen;
    private final IntroView view;
    private Button nextButton;

    public IntroScreen(int screenW, int screenH, Runnable onFinish) {
        screen = new Screen(screenW, screenH);
        view = new IntroView(screenW, screenH);
        screen.addChild(view);

        int bw = 160;
        int bh = 44;
        int by = 460;   // с запасом ниже цели забега (см. IntroView.onDraw) — раньше текст заезжал на кнопки
        int gap = 40;

        Button back = new Button(screenW / 2 - bw - gap / 2, by, bw, bh, "Back", () -> {
            view.prev();
            refreshButtons();
        });
        nextButton = new Button(screenW / 2 + gap / 2, by, bw, bh, "Next", () -> {
            if (view.isLast()) {
                onFinish.run();
            } else {
                view.next();
                refreshButtons();
            }
        });

        screen.addChild(back);
        screen.addChild(nextButton);
        screen.addChild(new Button(screenW - 172, 20, 152, 32, "Skip Intro", onFinish));
    }

    private void refreshButtons() {
        nextButton.setText(view.isLast() ? "Start Digging" : "Next");
    }

    public Screen getScreen() {
        return screen;
    }

    /** Рисует текущий слайд: заголовок, ряд иконок со стрелками, подпись и точки-индикатор. */
    private static class IntroView extends UIObject {
        private static final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 28);
        private static final Font SUB_FONT = new Font("SansSerif", Font.PLAIN, 16);
        private static final Font GOAL_FONT = new Font("SansSerif", Font.BOLD, 16);
        private static final Font ARROW_FONT = new Font("SansSerif", Font.BOLD, 28);

        private static final Color BG = new Color(18, 18, 26);
        private static final Color TITLE_COLOR = new Color(242, 213, 68);
        private static final Color SUB_COLOR = new Color(220, 220, 228);
        private static final Color GOAL_COLOR = new Color(150, 200, 245);
        private static final Color DOT_ON = new Color(242, 213, 68);
        private static final Color DOT_OFF = new Color(90, 90, 110);

        private int index;

        IntroView(int w, int h) {
            super(0, 0, w, h);
        }

        boolean isLast() {
            return index >= SLIDES.length - 1;
        }

        void next() {
            if (index < SLIDES.length - 1) index++;
        }

        void prev() {
            if (index > 0) index--;
        }

        @Override
        protected void onDraw(DrawCtx ctx) {
            Graphics2D g = ctx.g;
            // тот же режим, что и в игре: пиксель-арт масштабируется без размытия
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g.setColor(BG);
            g.fillRect(0, 0, width, height);

            Slide s = SLIDES[index];

            g.setFont(TITLE_FONT);
            g.setColor(TITLE_COLOR);
            FontMetrics tfm = g.getFontMetrics();
            g.drawString(s.title(), (width - tfm.stringWidth(s.title())) / 2, 90);

            int boxSize = 72;
            int gap = 40;
            int n = s.icons().length;
            int totalW = n * boxSize + (n - 1) * gap;
            int startX = (width - totalW) / 2;
            int iconY = 150;
            for (int i = 0; i < n; i++) {
                int bx = startX + i * (boxSize + gap);
                drawIconFit(g, s.icons()[i], bx, iconY, boxSize);
                if (i < n - 1) {
                    g.setFont(ARROW_FONT);
                    g.setColor(SUB_COLOR);
                    int ax = bx + boxSize + gap / 2 - 8;
                    g.drawString("→", ax, iconY + boxSize / 2 + 10);
                }
            }

            g.setFont(SUB_FONT);
            g.setColor(SUB_COLOR);
            FontMetrics sfm = g.getFontMetrics();
            g.drawString(s.subtitle(), (width - sfm.stringWidth(s.subtitle())) / 2, iconY + boxSize + 40);

            int dotSize = 10;
            int dotGap = 20;
            int dotsW = SLIDES.length * dotSize + (SLIDES.length - 1) * dotGap;
            int dotX = (width - dotsW) / 2;
            int dotY = 320;
            for (int i = 0; i < SLIDES.length; i++) {
                g.setColor(i == index ? DOT_ON : DOT_OFF);
                g.fillOval(dotX + i * (dotSize + dotGap), dotY, dotSize, dotSize);
            }

            // отдельная строка с целью — с явным запасом над кнопками Back/Next
            // ниже (см. IntroScreen конструктор): раньше она садилась ровно на них
            g.setFont(GOAL_FONT);
            g.setColor(GOAL_COLOR);
            FontMetrics gfm = g.getFontMetrics();
            g.drawString(GOAL_LINE, (width - gfm.stringWidth(GOAL_LINE)) / 2, 375);
        }

        /** Вписывает текстуру произвольных пропорций в квадратный бокс, сохраняя аспект. */
        private void drawIconFit(Graphics2D g, String tex, int bx, int by, int boxSize) {
            BufferedImage img = Textures.get(tex);
            double scale = Math.min(boxSize / (double) img.getWidth(), boxSize / (double) img.getHeight());
            int w = (int) Math.round(img.getWidth() * scale);
            int h = (int) Math.round(img.getHeight() * scale);
            int x = bx + (boxSize - w) / 2;
            int y = by + (boxSize - h) / 2;
            g.drawImage(img, x, y, w, h, null);
        }
    }
}
