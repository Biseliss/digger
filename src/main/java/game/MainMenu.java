package game;

import core.Audio;
import core.GameWindow;
import ui.DrawCtx;
import ui.Screen;
import ui.UIObject;
import ui.widgets.Button;
import ui.widgets.Slider;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

/** Стартовое меню: Start Game / Settings / Quit. */
public class MainMenu {
    private static final String[] TITLE_LINES = {
            "DIGGING A HOLE", "(etc.)"
    };

    private final GameWindow window;
    private final int screenW;
    private final int screenH;
    private final Screen mainScreen;
    private final Screen settingsScreen;
    private final Screen guideScreen;

    /** Играет, пока мы в меню; останавливается на старте игры — у Game своя (п.3). */
    private final Audio music = new Audio();

    public MainMenu(GameWindow window, int screenW, int screenH) {
        this.window = window;
        this.screenW = screenW;
        this.screenH = screenH;
        this.settingsScreen = buildSettingsScreen();
        this.mainScreen = buildMainScreen();
        this.guideScreen = GuideScreen.build(screenW, screenH, () -> window.setScreen(mainScreen));

        music.setFile("Soundtrack");
        music.setVolume(AppSettings.musicVolume);
        music.loop();
    }

    public Screen getScreen() {
        return mainScreen;
    }

    private Screen buildMainScreen() {
        Screen screen = new Screen(screenW, screenH);
        screen.addChild(new TitleView(screenW, screenH, TITLE_LINES));

        int bw = 260;
        int bh = 44;
        int gap = 16;
        int bx = (screenW - bw) / 2;
        int by = screenH / 2 + 20;

        screen.addChild(new Button(bx, by, bw, bh, "Start Game", this::startGame));
        screen.addChild(new Button(bx, by + (bh + gap), bw, bh, "Settings",
                () -> window.setScreen(settingsScreen)));
        screen.addChild(new Button(bx, by + (bh + gap) * 2, bw, bh, "Guide",
                () -> window.setScreen(guideScreen)));
        screen.addChild(new Button(bx, by + (bh + gap) * 3, bw, bh, "Quit", () -> System.exit(0)));

        return screen;
    }

    private Screen buildSettingsScreen() {
        Screen screen = new Screen(screenW, screenH);
        screen.addChild(new TitleView(screenW, screenH, new String[]{"Settings"}));

        int sliderW = 340;
        int sx = (screenW - sliderW) / 2;
        int sy = screenH / 2;

        screen.addChild(new Slider(sx, sy, sliderW, 26, "Music", AppSettings.musicVolume,
                v -> {
            AppSettings.musicVolume = (float) v;
            music.setVolume((float) v);   // слышно сразу, а не только в следующей игре
        }));
        screen.addChild(new Slider(sx, sy + 46, sliderW, 26, "Sound", AppSettings.sfxVolume,
                v -> AppSettings.sfxVolume = (float) v));

        int bw = 160;
        int bh = 40;
        screen.addChild(new Button((screenW - bw) / 2, sy + 100, bw, bh, "Back",
                () -> window.setScreen(mainScreen)));

        return screen;
    }

    /** Интро (п.2) — перед самой первой раскопкой, а не внутри Game самой. */
    private void startGame() {
        music.stop();   // у Game своя дорожка на том же треке — не наслаиваем
        IntroScreen intro = new IntroScreen(screenW, screenH, this::launchGame);
        window.setScreen(intro.getScreen());
    }

    private void launchGame() {
        Game game = new Game(window.getInput(), screenW, screenH, this::returnToMenu);
        window.setScene(game);
    }

    /**
     * Победа (сундук в финальной комнате) через несколько секунд выкидывает
     * обратно сюда (п.4): пересобираем экран заново, следующий Start Game
     * создаст полностью новую Game — старый мир и прогресс не переживают это.
     */
    private void returnToMenu() {
        window.setScreen(mainScreen);
        music.setVolume(AppSettings.musicVolume);
        music.loop();
    }

    /** Тёмный фон и заголовок — общий вид для главного меню и настроек. */
    private static class TitleView extends UIObject {
        private static final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 30);
        private static final Color BG = new Color(18, 18, 26);
        private static final Color TITLE_COLOR = new Color(242, 213, 68);

        private final String[] lines;

        TitleView(int width, int height, String[] lines) {
            super(0, 0, width, height);
            this.lines = lines;
        }

        @Override
        protected void onDraw(DrawCtx ctx) {
            Graphics2D g = ctx.g;
            g.setColor(BG);
            g.fillRect(0, 0, width, height);

            g.setFont(TITLE_FONT);
            g.setColor(TITLE_COLOR);
            FontMetrics fm = g.getFontMetrics();
            int startY = height / 2 - 140;
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                int tx = (width - fm.stringWidth(line)) / 2;
                g.drawString(line, tx, startY + i * (fm.getHeight() + 4));
            }
        }
    }
}
