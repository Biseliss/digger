package game.world;

import game.Constants;
import game.render.Images;
import game.render.Textures;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * Кусок мира 32x32 тайла с кэшем отрисовки (п.11).
 *
 * Кэш пересчитывается только когда чанк помечен dirty — то есть при
 * разрушении блока или обновлении тумана войны, а не каждый кадр.
 */
public class Chunk {
    private static final int MAX_SHADE = 90;
    private static final Color BACKGROUND_SHADE = new Color(0, 0, 0, 150);
    /** Предрассчитанные оттенки затемнения по глубине — см. shadeColor(). */
    private static final Color[] SHADES = buildShades();

    private static Color[] buildShades() {
        Color[] out = new Color[MAX_SHADE + 1];
        for (int i = 0; i <= MAX_SHADE; i++) out[i] = new Color(0, 0, 0, i);
        return out;
    }

    public final int cx;
    public final int cy;

    private final Block[][] blocks = new Block[Constants.CHUNK_W][Constants.CHUNK_H];
    /**
     * Задний план: тип породы, которая тут была до раскопки. null — не копали.
     * Отдельным лёгким массивом типов, а не блоками: у фона нет ни прочности,
     * ни логики, он только рисуется темнее и проходим насквозь.
     */
    private final BlockType[][] background = new BlockType[Constants.CHUNK_W][Constants.CHUNK_H];

    private boolean dirty = true;
    private BufferedImage cache;

    public Chunk(int cx, int cy) {
        this.cx = cx;
        this.cy = cy;
    }

    public Block get(int lx, int ly) {
        return blocks[lx][ly];
    }

    public void set(int lx, int ly, Block b) {
        blocks[lx][ly] = b;
        dirty = true;
    }

    public BlockType getBackground(int lx, int ly) {
        return background[lx][ly];
    }

    public void setBackground(int lx, int ly, BlockType t) {
        background[lx][ly] = t;
        dirty = true;
    }

    public void markDirty() {
        dirty = true;
    }

    /**
     * Рисует чанк на экран из кэша, пересобирая кэш только при необходимости.
     * clipW/clipH — размер экрана: блитим только ту часть чанка, что реально
     * видна, иначе на каждый кадр приходится масштабировать 1024x1024 пикселя
     * ради полоски в несколько тайлов.
     */
    public void draw(Graphics2D g, int screenX, int screenY, int scale, int clipW, int clipH) {
        if (dirty || cache == null) {
            rebuildCache();
            dirty = false;
        }

        int px = Constants.CHUNK_W * Constants.TILE;
        int size = px * scale;

        // видимая часть чанка в экранных координатах
        int dx1 = Math.max(screenX, 0);
        int dy1 = Math.max(screenY, 0);
        int dx2 = Math.min(screenX + size, clipW);
        int dy2 = Math.min(screenY + size, clipH);
        if (dx1 >= dx2 || dy1 >= dy2) return;

        // соответствующий кусок кэша
        int sx1 = (dx1 - screenX) / scale;
        int sy1 = (dy1 - screenY) / scale;
        int sx2 = Math.min(px, (int) Math.ceil((dx2 - screenX) / (double) scale));
        int sy2 = Math.min(px, (int) Math.ceil((dy2 - screenY) / (double) scale));
        if (sx1 >= sx2 || sy1 >= sy2) return;

        // выравниваем приёмник по источнику, чтобы не поехал масштаб
        g.drawImage(cache,
                screenX + sx1 * scale, screenY + sy1 * scale,
                screenX + sx2 * scale, screenY + sy2 * scale,
                sx1, sy1, sx2, sy2, null);
    }

    private void rebuildCache() {
        int px = Constants.CHUNK_W * Constants.TILE;
        if (cache == null) {
            cache = Images.createTranslucent(px, px);
        }
        Graphics2D g = cache.createGraphics();
        try {
            // полностью очищаем — нераскрытые тайлы должны остаться прозрачными (чёрными)
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, px, px);
            g.setComposite(AlphaComposite.SrcOver);

            for (int lx = 0; lx < Constants.CHUNK_W; lx++) {
                for (int ly = 0; ly < Constants.CHUNK_H; ly++) {
                    Block b = blocks[lx][ly];
                    if (b == null || !b.isRevealed()) continue; // слой 2 тумана войны (п.7)

                    int dx = lx * Constants.TILE;
                    int dy = ly * Constants.TILE;

                    BlockType bg = background[lx][ly];
                    if (bg != null) {
                        g.drawImage(Textures.get(bg.texture), dx, dy, Constants.TILE, Constants.TILE, null);
                        // задний план — та же текстура, но заметно темнее (п.11)
                        g.setColor(BACKGROUND_SHADE);
                        g.fillRect(dx, dy, Constants.TILE, Constants.TILE);
                    }

                    if (!b.isAir()) {
                        int rot = b.getType().isRotatable() ? b.getRotation() : 0;
                        g.drawImage(Textures.get(b.getType().texture, rot),
                                dx, dy, Constants.TILE, Constants.TILE, null);
                    }

                    int shade = depthShade(b.worldY);
                    if (shade > 0) {
                        g.setColor(shadeColor(shade));
                        g.fillRect(dx, dy, Constants.TILE, Constants.TILE);
                    }
                }
            }
        } finally {
            g.dispose();
        }
    }

    /** Лёгкое затемнение с глубиной — простая математика по Y, без доп. ассетов (п.11). */
    private static int depthShade(int worldY) {
        int depth = worldY - Constants.SURFACE_Y;
        if (depth <= 0) return 0;
        return Math.min(MAX_SHADE, depth / 3);
    }

    /** Цвета затемнения заранее — иначе это тысячи new Color на пересборку чанка. */
    private static Color shadeColor(int shade) {
        return SHADES[shade];
    }
}
