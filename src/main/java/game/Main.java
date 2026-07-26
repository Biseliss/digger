package game;

import core.GameWindow;

/** Точка входа игры. */
public class Main {
    public static void main(String[] args) {
        configureJava2D();

        GameWindow window = new GameWindow("A game about digging a hole as a shark",
                Constants.WINDOW_W, Constants.WINDOW_H);
        MainMenu menu = new MainMenu(window, Constants.WINDOW_W, Constants.WINDOW_H);
        window.setScreen(menu.getScreen());

        window.start();
        // F11 требует Fn на части клавиатур и легко промахивается — стартуем
        // сразу в полноэкранном режиме, Tab переключает обратно в окно.
        window.setFullscreen(true);
    }

    /**
     * Настройки рендер-пайплайна Java2D. Выставлять их надо до первого
     * обращения к AWT, иначе они уже не подхватятся.
     *
     * OpenGL-пайплайн на Linux обычно заметно быстрее софтверного (особенно
     * там, где мы блитим полупрозрачные поверхности — маска света, кэши
     * чанков), но на отдельных драйверах он глючит, поэтому оставлен
     * выключаемым: java -Dhole.opengl=false -jar target/ui-demo.jar
     */
    private static void configureJava2D() {
        if (!Boolean.parseBoolean(System.getProperty("hole.opengl", "true"))) return;
        System.setProperty("sun.java2d.opengl", "true");
    }
}
