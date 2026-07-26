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

/**
 * Гайд по утилитам (фидбек игроков): новым игрокам никто не объяснял, что
 * такое лестницы/мостики/динамит/факел/броня и когда их ставить. Общий
 * билдер, чтобы одну и ту же страницу можно было открыть и из главного меню
 * (через смену сцены), и прямо из паузы поверх игры (без смены сцены,
 * значит без потери состояния забега) — вызывающая сторона просто передаёт
 * свой способ вернуться назад.
 */
public final class GuideScreen {
    private GuideScreen() {}

    private record Entry(String icon, String title, String description) {}

    private static final Entry[] ENTRIES = {
            new Entry("lantern", "Torch - passive, buy once",
                    "Widens your light radius for good. Deep layers are dark - get one early."),
            new Entry("icon_armor", "Armor - passive, buy once",
                    "Halves fall/lava/dynamite damage, and removes deep-layer pressure damage entirely."),
            new Entry("icon_dynamite", "Dynamite - place with G",
                    "Drop it and run. After the fuse, it clears a whole radius of blocks and ore at once."),
            new Entry("icon_ladder", "Ladder - place with F",
                    "Needs a solid block right above or below to hold on to. Climb with W/S while on it."),
            new Entry("icon_bridge", "Bridge - place with B",
                    "Needs a solid block right to its left or right to hold on to. Walk across gaps and chasms."),
    };

    public static Screen build(int screenW, int screenH, Runnable onBack) {
        Screen screen = new Screen(screenW, screenH);
        screen.addChild(new GuideView(screenW, screenH));

        int bw = 160;
        int bh = 40;
        screen.addChild(new Button((screenW - bw) / 2, screenH - 60, bw, bh, "Back", onBack));

        return screen;
    }

    private static class GuideView extends UIObject {
        private static final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 26);
        private static final Font NAME_FONT = new Font("SansSerif", Font.BOLD, 16);
        private static final Font DESC_FONT = new Font("SansSerif", Font.PLAIN, 14);

        private static final Color BG = new Color(18, 18, 26);
        private static final Color TITLE_COLOR = new Color(242, 213, 68);
        private static final Color NAME_COLOR = new Color(220, 220, 228);
        private static final Color DESC_COLOR = new Color(180, 180, 190);
        private static final Color ROW_BG = new Color(255, 255, 255, 12);

        GuideView(int w, int h) {
            super(0, 0, w, h);
        }

        @Override
        protected void onDraw(DrawCtx ctx) {
            Graphics2D g = ctx.g;
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g.setColor(BG);
            g.fillRect(0, 0, width, height);

            String title = "Guide: Utilities & Tools";
            g.setFont(TITLE_FONT);
            g.setColor(TITLE_COLOR);
            FontMetrics tfm = g.getFontMetrics();
            g.drawString(title, (width - tfm.stringWidth(title)) / 2, 55);

            int rowH = 84;
            int startY = 90;
            int iconSize = 48;
            int textX = 140;
            int rowW = Math.min(720, width - 80);
            int rowX = (width - rowW) / 2;

            for (int i = 0; i < ENTRIES.length; i++) {
                Entry e = ENTRIES[i];
                int rowY = startY + i * rowH;

                g.setColor(ROW_BG);
                g.fillRect(rowX, rowY, rowW, rowH - 10);

                var img = Textures.get(e.icon());
                double scale = Math.min(iconSize / (double) img.getWidth(), iconSize / (double) img.getHeight());
                int iw = (int) Math.round(img.getWidth() * scale);
                int ih = (int) Math.round(img.getHeight() * scale);
                g.drawImage(img, rowX + 30 + (iconSize - iw) / 2, rowY + (rowH - 10 - ih) / 2, iw, ih, null);

                g.setFont(NAME_FONT);
                g.setColor(NAME_COLOR);
                g.drawString(e.title(), rowX + textX, rowY + 32);

                g.setFont(DESC_FONT);
                g.setColor(DESC_COLOR);
                g.drawString(e.description(), rowX + textX, rowY + 56);
            }
        }
    }
}
