package ui.widgets;

import ui.Container;
import ui.DrawCtx;

import java.awt.Color;

/** Container that just paints a background rectangle behind its children. */
public class Panel extends Container {
    private Color background;

    public Panel(int x, int y, int width, int height, Color background) {
        super(x, y, width, height);
        this.background = background;
    }

    public void setBackground(Color background) {
        this.background = background;
    }

    @Override
    protected void onDraw(DrawCtx ctx) {
        ctx.g.setColor(background);
        ctx.g.fillRect(ctx.ox, ctx.oy, width, height);
        super.onDraw(ctx);
    }
}
