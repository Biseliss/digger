package ui.widgets;

import ui.DrawCtx;
import ui.UIObject;

import java.awt.Color;
import java.awt.Font;

/** Leaf widget that draws a single line of text at its position. */
public class Label extends UIObject {
    private String text;
    private Color color = Color.WHITE;
    private Font font = new Font("SansSerif", Font.PLAIN, 16);

    public Label(int x, int y, String text) {
        super(x, y, 0, 0);
        this.text = text;
    }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public void setColor(Color color) { this.color = color; }
    public void setFont(Font font) { this.font = font; }

    @Override
    protected void onDraw(DrawCtx ctx) {
        ctx.g.setColor(color);
        ctx.g.setFont(font);
        ctx.g.drawString(text, ctx.ox, ctx.oy);
    }
}
