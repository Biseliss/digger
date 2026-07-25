package ui.widgets;

import ui.DrawCtx;
import ui.UIObject;

import java.awt.Color;
import java.awt.FontMetrics;

/** Simple clickable rectangle with centered text; runs onClick on release. */
public class Button extends UIObject {
    private String text;
    private final Runnable onClick;
    private boolean pressed = false;

    private Color baseColor = new Color(70, 70, 90);
    private Color pressedColor = new Color(100, 100, 140);
    private Color textColor = Color.WHITE;

    public Button(int x, int y, int width, int height, String text, Runnable onClick) {
        super(x, y, width, height);
        this.text = text;
        this.onClick = onClick;
    }

    public void setText(String text) { this.text = text; }

    @Override
    protected void onDraw(DrawCtx ctx) {
        ctx.g.setColor(pressed ? pressedColor : baseColor);
        ctx.g.fillRect(ctx.ox, ctx.oy, width, height);

        ctx.g.setColor(textColor);
        FontMetrics fm = ctx.g.getFontMetrics();
        int tx = ctx.ox + (width - fm.stringWidth(text)) / 2;
        int ty = ctx.oy + (height + fm.getAscent()) / 2 - 2;
        ctx.g.drawString(text, tx, ty);
    }

    @Override
    public boolean handleMousePressed(int localX, int localY, int button) {
        pressed = true;
        return true;
    }

    @Override
    public boolean handleMouseReleased(int localX, int localY, int button) {
        boolean wasPressed = pressed;
        pressed = false;
        if (wasPressed && onClick != null) onClick.run();
        return true;
    }
}
