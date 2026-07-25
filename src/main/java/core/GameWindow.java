package core;

import ui.DrawCtx;
import ui.Screen;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferStrategy;
import javax.swing.JFrame;

/**
 * Thin wrapper around a JFrame + Canvas: owns the game loop (fixed target
 * FPS, active rendering via BufferStrategy) and collects keyboard/mouse
 * input into Input. Swap scenes at any time with setScene().
 */
public class GameWindow {
    private final JFrame frame;
    private final Canvas canvas;
    private final int width;
    private final int height;
    private final double targetFps;
    private final Input input = new Input();

    private volatile Scene scene;
    /** Отдельно от scene: кому слать клики мышью, если сцена — UI-дерево. */
    private volatile Screen uiScreen;
    private volatile boolean running;
    private Thread loopThread;

    public GameWindow(String title, int width, int height) {
        this(title, width, height, 60.0);
    }

    public GameWindow(String title, int width, int height, double targetFps) {
        this.width = width;
        this.height = height;
        this.targetFps = targetFps;

        canvas = new Canvas();
        canvas.setPreferredSize(new Dimension(width, height));
        canvas.setFocusable(true);

        frame = new JFrame(title);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(canvas);
        frame.pack();
        frame.setResizable(false);
        frame.setLocationRelativeTo(null);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                input.mouseMoved(e.getX(), e.getY());
                input.mouseButton(e.getButton(), true);
                Screen s = uiScreen;
                if (s != null) s.handleMousePressed(e.getX(), e.getY(), e.getButton());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                input.mouseMoved(e.getX(), e.getY());
                input.mouseButton(e.getButton(), false);
                Screen s = uiScreen;
                if (s != null) s.handleMouseReleased(e.getX(), e.getY(), e.getButton());
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                input.mouseMoved(e.getX(), e.getY());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                input.mouseMoved(e.getX(), e.getY());
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                input.wheel(e.getWheelRotation());
            }
        };
        canvas.addMouseListener(mouse);
        canvas.addMouseMotionListener(mouse);
        canvas.addMouseWheelListener(mouse);

        canvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                input.keyDown(e.getKeyCode());
            }

            @Override
            public void keyReleased(KeyEvent e) {
                input.keyUp(e.getKeyCode());
            }
        });
    }

    public Input getInput() {
        return input;
    }

    public void setScene(Scene scene) {
        this.scene = scene;
        this.uiScreen = null;
    }

    /**
     * Оставлено для чистых UI-демок. Заворачиваем Screen в Scene адаптером,
     * чтобы библиотека ui/ не зависела от core/ (зависимость только в эту сторону).
     */
    public void setScreen(Screen screen) {
        this.scene = new Scene() {
            @Override
            public void tick(double dt) {
                screen.tick(dt);
            }

            @Override
            public void draw(DrawCtx ctx) {
                screen.draw(ctx);
            }
        };
        this.uiScreen = screen;
    }

    public void start() {
        frame.setVisible(true);
        canvas.createBufferStrategy(2);
        canvas.requestFocus();
        running = true;
        loopThread = new Thread(this::loop, "game-loop");
        loopThread.start();
    }

    public void stop() {
        running = false;
    }

    private void loop() {
        BufferStrategy strategy = canvas.getBufferStrategy();
        long lastTime = System.nanoTime();
        final long nsPerFrame = (long) (1_000_000_000.0 / targetFps);

        while (running) {
            long frameStart = System.nanoTime();
            double dt = (frameStart - lastTime) / 1_000_000_000.0;
            lastTime = frameStart;
            if (dt > 0.1) dt = 0.1; // после фриза не даём физике «прыгнуть» сквозь стены

            Scene s = scene;
            if (s != null) s.tick(dt);

            do {
                do {
                    Graphics2D g = (Graphics2D) strategy.getDrawGraphics();
                    try {
                        g.setColor(Color.BLACK);
                        g.fillRect(0, 0, width, height);
                        if (s != null) s.draw(new DrawCtx(g, 0, 0));
                    } finally {
                        g.dispose();
                    }
                } while (strategy.contentsRestored());
                strategy.show();
            } while (strategy.contentsLost());

            input.endFrame();

            long elapsed = System.nanoTime() - frameStart;
            long sleepNs = nsPerFrame - elapsed;
            if (sleepNs > 0) {
                try {
                    Thread.sleep(sleepNs / 1_000_000, (int) (sleepNs % 1_000_000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
