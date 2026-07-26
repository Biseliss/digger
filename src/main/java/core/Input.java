package core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.swing.Timer;

/**
 * Состояние ввода. Заполняется из AWT-потока событий, читается из игрового
 * цикла, поэтому всё под synchronized.
 *
 * Различаем «зажато» (isDown) и «нажали в этом кадре» (wasPressed) — второе
 * нужно для одноразовых действий (E, F, G, Q), чтобы одно нажатие не
 * срабатывало 60 раз в секунду.
 */
public class Input {
    private final Set<Integer> down = new HashSet<>();
    private final Set<Integer> pressed = new HashSet<>();

    private int mouseX, mouseY;
    private boolean leftDown, rightDown;
    private int wheelDelta;
    /** Фронты нажатия/отпускания ЛКМ — нужны UI (клик по кнопке, захват слайдера). */
    private boolean leftPressedEdge, leftReleasedEdge;

    /**
     * Без "detectable autorepeat" (типично для X11/Linux) удержание клавиши
     * шлёт не один keyPressed на всё время удержания, а пары release+press на
     * каждый тик автоповтора ОС. Из-за этого одноразовые действия (Q, E, F...)
     * срабатывали то через раз, то по два подряд — выглядело как случайность.
     * Лечим дебаунсом: настоящее отпускание применяем не сразу, а с небольшой
     * задержкой; если за это время прилетает новый press того же кода —
     * значит, клавишу всё это время держали, а не нажали заново.
     */
    private static final int RELEASE_DEBOUNCE_MS = 40;
    private final Map<Integer, Timer> pendingReleases = new HashMap<>();

    synchronized void keyDown(int code) {
        Timer pending = pendingReleases.remove(code);
        if (pending != null) {
            pending.stop();
            return;   // автоповтор ОС поверх уже зажатой клавиши, а не новое нажатие
        }
        if (down.add(code)) pressed.add(code);
    }

    synchronized void keyUp(int code) {
        Timer t = new Timer(RELEASE_DEBOUNCE_MS, e -> {
            synchronized (Input.this) {
                down.remove(code);
                pendingReleases.remove(code);
            }
        });
        t.setRepeats(false);
        pendingReleases.put(code, t);
        t.start();
    }

    synchronized void mouseMoved(int x, int y) {
        mouseX = x;
        mouseY = y;
    }

    synchronized void mouseButton(int button, boolean isDown) {
        if (button == java.awt.event.MouseEvent.BUTTON1) {
            if (isDown && !leftDown) leftPressedEdge = true;
            if (!isDown && leftDown) leftReleasedEdge = true;
            leftDown = isDown;
        }
        if (button == java.awt.event.MouseEvent.BUTTON3) rightDown = isDown;
    }

    synchronized void wheel(int notches) {
        wheelDelta += notches;
    }

    public synchronized boolean isDown(int code) {
        return down.contains(code);
    }

    /** true один раз на нажатие. */
    public synchronized boolean wasPressed(int code) {
        return pressed.contains(code);
    }

    /** Любая из клавиш зажата — для дублирования WASD стрелками (п.2). */
    public synchronized boolean isAnyDown(int... codes) {
        for (int c : codes) {
            if (down.contains(c)) return true;
        }
        return false;
    }

    public synchronized int getMouseX() { return mouseX; }
    public synchronized int getMouseY() { return mouseY; }
    public synchronized boolean isLeftDown() { return leftDown; }
    public synchronized boolean isRightDown() { return rightDown; }

    /** true один раз на нажатие ЛКМ — чтобы клик не срабатывал каждый кадр. */
    public synchronized boolean wasLeftPressed() { return leftPressedEdge; }

    public synchronized boolean wasLeftReleased() { return leftReleasedEdge; }

    /** Накопленные щелчки колеса; вызывается один раз за кадр. */
    public synchronized int consumeWheel() {
        int d = wheelDelta;
        wheelDelta = 0;
        return d;
    }

    /** Вызывается игровым циклом в конце кадра. */
    public synchronized void endFrame() {
        pressed.clear();
        leftPressedEdge = false;
        leftReleasedEdge = false;
    }
}
