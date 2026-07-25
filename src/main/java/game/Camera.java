package game;

import game.entity.Player;

/**
 * Камера следует за игроком (рисуем территорию вокруг него, а не фиксированный
 * кадр). Координаты — в мировых пикселях, x/y — левый верхний угол вида.
 */
public class Camera {
    private double x;
    private double y;
    private final int viewWidthPx;
    private final int viewHeightPx;

    public Camera(int screenW, int screenH) {
        this.viewWidthPx = screenW / Constants.SCALE;
        this.viewHeightPx = screenH / Constants.SCALE;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public int getViewWidthPx() { return viewWidthPx; }
    public int getViewHeightPx() { return viewHeightPx; }

    public void follow(Player p) {
        x = p.getX() + Constants.HITBOX_W / 2.0 - viewWidthPx / 2.0;
        y = p.getY() + Constants.HITBOX_H / 2.0 - viewHeightPx / 2.0;
        clamp();
    }

    /** Не показываем пустоту за краями мира по горизонтали. */
    private void clamp() {
        double worldPxW = Constants.WORLD_W * Constants.TILE;
        double worldPxH = Constants.WORLD_H * Constants.TILE;
        if (x < 0) x = 0;
        if (x > worldPxW - viewWidthPx) x = worldPxW - viewWidthPx;
        if (y < 0) y = 0;
        if (y > worldPxH - viewHeightPx) y = worldPxH - viewHeightPx;
    }

    public double worldToScreenX(double worldPx) {
        return (worldPx - x) * Constants.SCALE;
    }

    public double worldToScreenY(double worldPx) {
        return (worldPx - y) * Constants.SCALE;
    }

    /** Экранные координаты (например, курсора) — обратно в тайл мира. */
    public int screenToTileX(int screenX) {
        return (int) Math.floor((screenX / (double) Constants.SCALE + x) / Constants.TILE);
    }

    public int screenToTileY(int screenY) {
        return (int) Math.floor((screenY / (double) Constants.SCALE + y) / Constants.TILE);
    }
}
