package ui;

import java.awt.Graphics2D;

/**
 * Immutable draw context passed down the UI tree.
 * Carries the accumulated (ox, oy) offset and the shared Graphics2D.
 */
public final class DrawCtx {
    public final Graphics2D g;
    public final int ox;
    public final int oy;

    public DrawCtx(Graphics2D g, int ox, int oy) {
        this.g = g;
        this.ox = ox;
        this.oy = oy;
    }

    public DrawCtx translated(int dx, int dy) {
        return new DrawCtx(g, ox + dx, oy + dy);
    }
}
