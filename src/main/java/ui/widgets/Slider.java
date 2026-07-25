package ui.widgets;

import ui.DrawCtx;
import ui.UIObject;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.util.function.DoubleConsumer;

/**
 * Ползунок для значения 0..1 — например, громкости.
 *
 * Тянется мышью: нажатие сразу ставит значение под курсором, дальше работает
 * захват из Container, поэтому ползунок не срывается, если увести мышь за
 * пределы дорожки.
 */
public class Slider extends UIObject {
    private static final Font FONT = new Font("SansSerif", Font.PLAIN, 14);
    private static final Color TRACK = new Color(60, 60, 72);
    private static final Color FILL = new Color(120, 180, 230);
    private static final Color KNOB = new Color(230, 235, 245);
    private static final Color TEXT = new Color(225, 225, 230);

    private final String label;
    private final DoubleConsumer onChange;
    private double value;

    public Slider(int x, int y, int width, int height, String label,
                  double initialValue, DoubleConsumer onChange) {
        super(x, y, width, height);
        this.label = label;
        this.value = clamp(initialValue);
        this.onChange = onChange;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double v) {
        double clamped = clamp(v);
        if (clamped == value) return;
        value = clamped;
        if (onChange != null) onChange.accept(value);
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    @Override
    protected void onDraw(DrawCtx ctx) {
        var g = ctx.g;
        int trackH = 6;
        int trackY = ctx.oy + (height - trackH) / 2;

        g.setFont(FONT);
        g.setColor(TEXT);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(label, ctx.ox, trackY - 8);

        String percent = Math.round(value * 100) + "%";
        g.drawString(percent, ctx.ox + width - fm.stringWidth(percent), trackY - 8);

        g.setColor(TRACK);
        g.fillRect(ctx.ox, trackY, width, trackH);

        int filled = (int) Math.round(width * value);
        g.setColor(FILL);
        g.fillRect(ctx.ox, trackY, filled, trackH);

        int knobX = ctx.ox + filled;
        g.setColor(KNOB);
        g.fillRect(knobX - 4, trackY - 6, 8, trackH + 12);
    }

    @Override
    public boolean handleMousePressed(int localX, int localY, int button) {
        setValue(localX / (double) width);
        return true;
    }

    @Override
    public boolean handleMouseDragged(int localX, int localY) {
        setValue(localX / (double) width);
        return true;
    }

    @Override
    public boolean handleMouseReleased(int localX, int localY, int button) {
        return true;
    }
}
