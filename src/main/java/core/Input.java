package core;

import java.util.HashSet;
import java.util.Set;

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

    synchronized void keyDown(int code) {
        if (down.add(code)) pressed.add(code);
    }

    synchronized void keyUp(int code) {
        down.remove(code);
    }

    synchronized void mouseMoved(int x, int y) {
        mouseX = x;
        mouseY = y;
    }

    synchronized void mouseButton(int button, boolean isDown) {
        if (button == java.awt.event.MouseEvent.BUTTON1) leftDown = isDown;
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

    /** Накопленные щелчки колеса; вызывается один раз за кадр. */
    public synchronized int consumeWheel() {
        int d = wheelDelta;
        wheelDelta = 0;
        return d;
    }

    /** Вызывается игровым циклом в конце кадра. */
    public synchronized void endFrame() {
        pressed.clear();
    }
}
