package game.npc;

import game.Constants;
import game.entity.Player;
import game.render.Animation;
import game.render.Textures;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Точка взаимодействия на базе (п.9): NPC за прилавком, «подойти + E».
 * Фон магазина рисуется отдельно самой базой, а не каждым NPC.
 */
public abstract class NpcPoint {
    public final int tileX;
    public final int tileY;
    private final String texture;
    private final String title;
    /** Не у всех NPC портретная 1x2: например, krill лежит горизонтально (п.3). */
    private final int tilesW;
    private final int tilesH;
    /** Идл-анимация вместо статичной текстуры — null, если её нет. */
    private Animation idleAnim;
    /** Иконка над головой (какая руда/кирка/утилита сейчас актуальна) — null, если её нет (п.4, доп.). */
    private java.util.function.Supplier<String> overheadIcon;
    /** Почему не удалась последняя покупка — Game показывает это текстом (п.3, доп.). */
    private String pendingError;

    protected NpcPoint(int tileX, int tileY, String texture, String title) {
        this(tileX, tileY, texture, title, 1, 2);
    }

    protected NpcPoint(int tileX, int tileY, String texture, String title, int tilesW, int tilesH) {
        this.tileX = tileX;
        this.tileY = tileY;
        this.texture = texture;
        this.title = title;
        this.tilesW = tilesW;
        this.tilesH = tilesH;
    }

    /** Задать зацикленную idle-анимацию (кадры "base0"/"base1"...) вместо статичной текстуры. */
    protected void setIdleAnimation(Animation anim) {
        this.idleAnim = anim;
    }

    /** Что показать иконкой над головой NPC — читается заново на каждом кадре отрисовки. */
    protected void setOverheadIcon(java.util.function.Supplier<String> overheadIcon) {
        this.overheadIcon = overheadIcon;
    }

    public void tick(double dt, Player player) {
        if (idleAnim != null) idleAnim.tick(dt);
    }

    /**
     * Q/колесо мыши, пока игрок рядом — листает товар/режим (п.9, доп.).
     * No-op по умолчанию: у большинства NPC листать нечего (кузнец, сундук).
     */
    public void cycle(int direction) {
        // нет режимов по умолчанию
    }

    public String getTitle() {
        return title;
    }

    public boolean isPlayerInRange(Player p) {
        return p.distanceToTile(tileX + 0.5, tileY + 1) <= Constants.INTERACT_RANGE;
    }

    /** Что показать в подсказке над игроком, пока он рядом. */
    public abstract String prompt(Player p);

    /** Нажали E. @return true, если это была успешная покупка (для эффекта монет, п.3). */
    public abstract boolean interact(Player p);

    /** Вызывать из interact() при отказе — почему именно не получилось (п.3, доп.). */
    protected void setError(String message) {
        this.pendingError = message;
    }

    /** Game читает и сразу сбрасывает — сообщение показывается один раз. */
    public String consumeError() {
        String e = pendingError;
        pendingError = null;
        return e;
    }

    public void draw(Graphics2D g, double camX, double camY) {
        int scale = Constants.SCALE;
        int sx = (int) Math.round((tileX * Constants.TILE - camX) * scale);
        // если NPC ниже стандартных 2 тайлов, ставим его на пол, а не по верхнему краю клетки
        int sy = (int) Math.round(((tileY + 2 - tilesH) * Constants.TILE - camY) * scale);
        BufferedImage img = idleAnim != null ? idleAnim.currentFrame() : Textures.get(texture);
        g.drawImage(img, sx, sy, tilesW * Constants.TILE * scale, tilesH * Constants.TILE * scale, null);

        if (overheadIcon != null) {
            String tex = overheadIcon.get();
            if (tex != null) {
                int iconSize = Constants.TILE * scale;
                int iconX = sx + (tilesW * Constants.TILE * scale - iconSize) / 2;
                int iconY = sy - iconSize - scale;
                g.drawImage(Textures.get(tex), iconX, iconY, iconSize, iconSize, null);
            }
        }
    }
}
