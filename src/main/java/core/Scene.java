package core;

import ui.DrawCtx;

/**
 * То, что умеет крутить GameWindow: тик логики + отрисовка.
 * ui.Screen подходит под этот интерфейс как есть, так что чистый UI-экран
 * тоже можно отдать окну напрямую.
 */
public interface Scene {
    void tick(double dt);

    void draw(DrawCtx ctx);
}
