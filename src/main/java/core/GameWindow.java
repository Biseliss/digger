package core;

import ui.DrawCtx;
import ui.Screen;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
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

    /** Tab переключает полноэкранный режим независимо от игровой логики. */
    private boolean fullscreen;

    /**
     * setFullscreen выполняется на EDT (из KeyListener), а рендер — на своём
     * "game-loop" потоке. Без этой блокировки цикл мог схватить canvas ровно
     * в момент между dispose() и новым setVisible(true) — peer в этот момент
     * временно недействителен, и strategy.getDrawGraphics() падал с
     * IllegalStateException. Оборачиваем обе стороны одним монитором, чтобы
     * пересоздание peer'а и рендер кадра никогда не пересекались по времени.
     */
    private final Object peerLock = new Object();

    /**
     * Letterbox-подгонка текущего кадра: канвас в полноэкранном режиме крупнее
     * логического разрешения width x height, и мышь репортит координаты уже в
     * реальных пикселях канваса — их нужно обратно пересчитать в логические,
     * иначе курсор для копания и клики по UI будут промахиваться мимо цели.
     */
    private volatile double renderScale = 1.0;
    private volatile int renderOffX;
    private volatile int renderOffY;

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
        // Иначе Tab у AWT по умолчанию — клавиша смены фокуса между
        // компонентами: KeyListener её попросту не увидит, и toggleFullscreen()
        // никогда не вызовется, как бы часто Tab ни жали.
        canvas.setFocusTraversalKeysEnabled(false);

        frame = new JFrame(title);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(canvas);
        frame.pack();
        frame.setResizable(false);
        frame.setLocationRelativeTo(null);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int lx = toLogicalX(e.getX());
                int ly = toLogicalY(e.getY());
                input.mouseMoved(lx, ly);
                input.mouseButton(e.getButton(), true);
                Screen s = uiScreen;
                if (s != null) s.handleMousePressed(lx, ly, e.getButton());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                int lx = toLogicalX(e.getX());
                int ly = toLogicalY(e.getY());
                input.mouseMoved(lx, ly);
                input.mouseButton(e.getButton(), false);
                Screen s = uiScreen;
                if (s != null) s.handleMouseReleased(lx, ly, e.getButton());
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                input.mouseMoved(toLogicalX(e.getX()), toLogicalY(e.getY()));
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                input.mouseMoved(toLogicalX(e.getX()), toLogicalY(e.getY()));
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
                // Tab, а не F11: F-клавиши на части клавиатур (особенно
                // ноутбучных) требуют Fn и легко промахиваются.
                if (e.getKeyCode() == KeyEvent.VK_TAB) {
                    toggleFullscreen();
                    return;
                }
                input.keyDown(e.getKeyCode());
            }

            @Override
            public void keyReleased(KeyEvent e) {
                input.keyUp(e.getKeyCode());
            }
        });
    }

    public boolean isFullscreen() {
        return fullscreen;
    }

    public void toggleFullscreen() {
        setFullscreen(!fullscreen);
    }

    /**
     * Полноэкранный режим — безрамочное окно на весь экран, а не exclusive
     * fullscreen API (device.setFullScreenWindow): с ним, если единственная
     * клавиша выхода вдруг не сработает (как уже случилось с Tab), из игры
     * не выбраться вообще ничем, вплоть до перезагрузки. Обычное окно
     * (пусть и без рамки, во весь экран) по-прежнему сворачивается
     * Alt+Tab/Cmd+Tab средствами самой ОС — это подстраховка, а не основной
     * способ выхода. Пересоздаём peer'ы фрейма (dispose/show), поэтому
     * buffer strategy в игровом цикле берём каждый раз заново.
     */
    public void setFullscreen(boolean on) {
        if (on == fullscreen) return;

        synchronized (peerLock) {
            frame.setVisible(false);
            frame.dispose();
            if (on) {
                frame.setUndecorated(true);
                var bounds = GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
                frame.setBounds(bounds);
                frame.setVisible(true);
            } else {
                frame.setUndecorated(false);
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            }
            fullscreen = on;

            canvas.createBufferStrategy(2);
            canvas.requestFocus();
        }
    }

    public Input getInput() {
        return input;
    }

    private int toLogicalX(int screenPx) {
        return (int) ((screenPx - renderOffX) / renderScale);
    }

    private int toLogicalY(int screenPx) {
        return (int) ((screenPx - renderOffY) / renderScale);
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
        synchronized (peerLock) {
            frame.setVisible(true);
            canvas.createBufferStrategy(2);
            canvas.requestFocus();
        }
        running = true;
        loopThread = new Thread(this::loop, "game-loop");
        loopThread.start();
    }

    public void stop() {
        running = false;
    }

    private void loop() {
        long lastTime = System.nanoTime();
        final long nsPerFrame = (long) (1_000_000_000.0 / targetFps);

        while (running) {
            long frameStart = System.nanoTime();
            double dt = (frameStart - lastTime) / 1_000_000_000.0;
            lastTime = frameStart;
            if (dt > 0.1) dt = 0.1; // после фриза не даём физике «прыгнуть» сквозь стены

            Scene s = scene;
            if (s != null) s.tick(dt);

            // Всё, что трогает canvas/frame, — под тем же локом, что и
            // setFullscreen(): иначе тугл посреди кадра ловит невалидный peer
            // (см. комментарий у peerLock).
            synchronized (peerLock) {
                // берём buffer strategy каждый кадр — Tab пересоздаёт peer канваса,
                // и закэшированная на входе в цикл ссылка после этого стала бы мёртвой
                BufferStrategy strategy = canvas.getBufferStrategy();
                if (strategy == null) {
                    canvas.createBufferStrategy(2);
                    strategy = canvas.getBufferStrategy();
                }

                // логика всегда рисует в фиксированном разрешении width x height;
                // в полноэкранном режиме канвас крупнее — вписываем с сохранением
                // пропорций (letterbox), а не растягиваем и не обрезаем картинку
                int cw = Math.max(1, canvas.getWidth());
                int ch = Math.max(1, canvas.getHeight());
                double fit = Math.min(cw / (double) width, ch / (double) height);
                int drawW = (int) Math.round(width * fit);
                int drawH = (int) Math.round(height * fit);
                int offX = (cw - drawW) / 2;
                int offY = (ch - drawH) / 2;
                renderScale = fit;
                renderOffX = offX;
                renderOffY = offY;

                do {
                    do {
                        Graphics2D g = (Graphics2D) strategy.getDrawGraphics();
                        try {
                            g.setColor(Color.BLACK);
                            g.fillRect(0, 0, cw, ch);
                            g.translate(offX, offY);
                            g.scale(fit, fit);
                            // Без этого клипа сцена не обрезана по границе логического
                            // разрешения: любой лишний пиксель отрисовки (край чанка,
                            // спрайт у самой кромки экрана и т.п.) утекал в
                            // letterbox-поля на широких мониторах — там, где должна
                            // быть чёрная рамка, вместо неё проглядывала карта.
                            g.clipRect(0, 0, width, height);
                            if (s != null) s.draw(new DrawCtx(g, 0, 0));
                        } finally {
                            g.dispose();
                        }
                    } while (strategy.contentsRestored());
                    strategy.show();
                } while (strategy.contentsLost());
            }

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
