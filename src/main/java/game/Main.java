package game;

import core.GameWindow;

/** Точка входа игры. */
public class Main {
    public static void main(String[] args) {
        configureJava2D();

        GameWindow window = new GameWindow("Hole", Constants.WINDOW_W, Constants.WINDOW_H);
        Game game = new Game(window.getInput(), Constants.WINDOW_W, Constants.WINDOW_H);
        window.setScene(game);
        window.start();
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
