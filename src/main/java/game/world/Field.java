package game.world;

import game.Constants;
import game.entity.Player;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Мир целиком: чанки, разрушение блоков, каскад лавы, обвалы гравия,
 * туман войны.
 *
 * Чанки лежат в словаре по координате чанка, а не цепочкой ссылок на соседей:
 * найти чанк под игроком — это O(1) по (cx, cy), а не проход по цепочке.
 */
public class Field {
    private final Map<ChunkCoord, Chunk> chunks = new HashMap<>();
    private final List<FallingBlock> falling = new ArrayList<>();

    /** Отдаётся за границами мира, чтобы вызывающим не приходилось знать про null. */
    private final Block outOfBounds = new SolidBlock(-1, -1, BlockType.BEDROCK);

    public Field() {
        int chunksY = (Constants.WORLD_H + Constants.CHUNK_H - 1) / Constants.CHUNK_H;
        for (int cx = 0; cx < Constants.WORLD_CHUNKS_X; cx++) {
            for (int cy = 0; cy < chunksY; cy++) {
                Chunk chunk = new Chunk(cx, cy);
                for (int lx = 0; lx < Constants.CHUNK_W; lx++) {
                    for (int ly = 0; ly < Constants.CHUNK_H; ly++) {
                        int wx = cx * Constants.CHUNK_W + lx;
                        int wy = cy * Constants.CHUNK_H + ly;
                        chunk.set(lx, ly, new AirBlock(wx, wy));
                    }
                }
                chunks.put(new ChunkCoord(cx, cy), chunk);
            }
        }
    }

    // --- доступ к тайлам ---

    public static boolean inBounds(int tx, int ty) {
        return tx >= 0 && tx < Constants.WORLD_W && ty >= 0 && ty < Constants.WORLD_H;
    }

    public Chunk chunkAt(int tx, int ty) {
        return chunks.get(new ChunkCoord(Math.floorDiv(tx, Constants.CHUNK_W),
                Math.floorDiv(ty, Constants.CHUNK_H)));
    }

    public Block getBlock(int tx, int ty) {
        if (ty < 0) return new AirBlock(tx, ty);      // над миром — открытое небо
        if (!inBounds(tx, ty)) return outOfBounds;    // по бокам и снизу — несокрушимая граница
        Chunk c = chunkAt(tx, ty);
        return c == null ? outOfBounds
                : c.get(Math.floorMod(tx, Constants.CHUNK_W), Math.floorMod(ty, Constants.CHUNK_H));
    }

    public void setBlock(int tx, int ty, Block b) {
        if (!inBounds(tx, ty)) return;
        Chunk c = chunkAt(tx, ty);
        if (c != null) c.set(Math.floorMod(tx, Constants.CHUNK_W), Math.floorMod(ty, Constants.CHUNK_H), b);
    }

    public BlockType getBackground(int tx, int ty) {
        if (!inBounds(tx, ty)) return null;
        Chunk c = chunkAt(tx, ty);
        return c == null ? null
                : c.getBackground(Math.floorMod(tx, Constants.CHUNK_W), Math.floorMod(ty, Constants.CHUNK_H));
    }

    public void setBackground(int tx, int ty, BlockType t) {
        if (!inBounds(tx, ty)) return;
        Chunk c = chunkAt(tx, ty);
        if (c != null) c.setBackground(Math.floorMod(tx, Constants.CHUNK_W), Math.floorMod(ty, Constants.CHUNK_H), t);
    }

    public boolean isSolid(int tx, int ty) {
        return getBlock(tx, ty).isSolid();
    }

    public boolean isLava(int tx, int ty) {
        return getBlock(tx, ty).getType() == BlockType.LAVA;
    }

    // --- туман войны (п.7, слой 2) ---

    public void reveal(int tx, int ty) {
        if (!inBounds(tx, ty)) return;
        Block b = getBlock(tx, ty);
        if (!b.isRevealed()) {
            b.setRevealed(true);
            Chunk c = chunkAt(tx, ty);
            if (c != null) c.markDirty();
        }
    }

    /** Раскрыть всё в радиусе — то, что игрок реально видит в круге света. */
    public void revealCircle(double centerX, double centerY, double radius) {
        int r = (int) Math.ceil(radius);
        int cx = (int) Math.floor(centerX);
        int cy = (int) Math.floor(centerY);
        for (int x = cx - r; x <= cx + r; x++) {
            for (int y = cy - r; y <= cy + r; y++) {
                double dx = x + 0.5 - centerX;
                double dy = y + 0.5 - centerY;
                if (dx * dx + dy * dy <= radius * radius) reveal(x, y);
            }
        }
    }

    /** Соседи по радиусу 1 вокруг раскопанного тайла (правило из п.7). */
    public void revealNeighbours(int tx, int ty) {
        for (int x = tx - 1; x <= tx + 1; x++) {
            for (int y = ty - 1; y <= ty + 1; y++) {
                reveal(x, y);
            }
        }
    }

    // --- разрушение ---

    /**
     * Убирает блок и запускает всё, что за этим следует: задний план,
     * туман войны, обвал гравия сверху и каскад лавы.
     */
    public void breakBlock(int tx, int ty) {
        Block b = getBlock(tx, ty);
        if (!inBounds(tx, ty) || b.isAir() || !b.getType().breakable) return;

        setBackground(tx, ty, b.getType());   // на месте блока остаётся тёмный фон (п.11)
        setBlock(tx, ty, new AirBlock(tx, ty));
        b.onBreak(this);

        revealNeighbours(tx, ty);
        onTileFreed(tx, ty);
    }

    /** Тайл освободился — проверяем, что там сверху: гравий падает, лава течёт. */
    public void onTileFreed(int tx, int ty) {
        Block above = getBlock(tx, ty - 1);
        if (above.getType() == BlockType.GRAVEL) {
            startFalling(tx, ty - 1);
        } else if (above.getType() == BlockType.LAVA) {
            cascadeLava(tx, ty);
        }
    }

    private void startFalling(int tx, int ty) {
        Block b = getBlock(tx, ty);
        if (b.getType() != BlockType.GRAVEL) return;
        setBlock(tx, ty, new AirBlock(tx, ty));
        falling.add(new FallingBlock(tx, ty, BlockType.GRAVEL));
        // над гравием мог лежать ещё гравий — он тоже поедет вниз
        onTileFreed(tx, ty);
    }

    /**
     * Дублирование лавы вниз (п.6). Идём столбом, пока под нами пусто —
     * каскад получается сам собой, без flood-fill.
     */
    private void cascadeLava(int tx, int ty) {
        int y = ty;
        while (inBounds(tx, y)) {
            Block above = getBlock(tx, y - 1);
            if (above.getType() != BlockType.LAVA) break;
            Block here = getBlock(tx, y);
            if (!here.isAir()) break;
            setBlock(tx, y, new LavaBlock(tx, y));
            reveal(tx, y);
            y++;
        }
    }

    // --- лестницы (п.8): теперь обычный блок, отдельных сущностей нет ---

    public boolean isLadder(int tx, int ty) {
        return getBlock(tx, ty).getType() == BlockType.LADDER;
    }

    /** Лестницу можно поставить в любую свободную клетку — она ни на что не опирается. */
    public boolean canPlaceLadder(int tx, int ty) {
        return inBounds(tx, ty) && getBlock(tx, ty).isAir();
    }

    public void placeLadder(int tx, int ty) {
        if (!canPlaceLadder(tx, ty)) return;
        setBlock(tx, ty, new LadderBlock(tx, ty));
        reveal(tx, ty);
    }

    // --- тик мира ---

    public void tick(double dt, Player player) {
        for (Iterator<FallingBlock> it = falling.iterator(); it.hasNext(); ) {
            FallingBlock fb = it.next();
            int cur = fb.tileY();

            if (!fb.alreadyHitPlayer && player.overlapsTile(fb.tileX, cur)) {
                player.damage(Constants.GRAVEL_DAMAGE, "crushed by gravel");
                fb.alreadyHitPlayer = true;
            }

            double next = fb.y + FallingBlock.FALL_SPEED * dt;
            int nextTile = (int) Math.floor(next);
            boolean blocked = nextTile != cur && (isSolid(fb.tileX, nextTile) || !inBounds(fb.tileX, nextTile));

            if (blocked) {
                fb.y = cur;
                setBlock(fb.tileX, cur, new GravelBlock(fb.tileX, cur));
                reveal(fb.tileX, cur);
                it.remove();
            } else {
                fb.y = next;
            }
        }
    }

    // --- отрисовка ---

    public void draw(Graphics2D g, double camX, double camY, int viewW, int viewH) {
        int scale = Constants.SCALE;
        int chunkPx = Constants.CHUNK_W * Constants.TILE;

        int firstCx = Math.floorDiv((int) camX / Constants.TILE, Constants.CHUNK_W);
        int firstCy = Math.floorDiv((int) camY / Constants.TILE, Constants.CHUNK_H);
        int lastCx = Math.floorDiv((int) (camX + viewW / (double) scale) / Constants.TILE, Constants.CHUNK_W);
        int lastCy = Math.floorDiv((int) (camY + viewH / (double) scale) / Constants.TILE, Constants.CHUNK_H);

        for (int cx = firstCx; cx <= lastCx; cx++) {
            for (int cy = firstCy; cy <= lastCy; cy++) {
                Chunk c = chunks.get(new ChunkCoord(cx, cy));
                if (c == null) continue;
                int sx = (int) Math.round((cx * chunkPx - camX) * scale);
                int sy = (int) Math.round((cy * chunkPx - camY) * scale);
                c.draw(g, sx, sy, scale, viewW, viewH);
            }
        }

        // падающий гравий рисуем поверх — он вне сетки чанков
        for (FallingBlock fb : falling) {
            int sx = (int) Math.round((fb.tileX * Constants.TILE - camX) * scale);
            int sy = (int) Math.round((fb.y * Constants.TILE - camY) * scale);
            g.drawImage(game.render.Textures.get(fb.type.texture),
                    sx, sy, Constants.TILE * scale, Constants.TILE * scale, null);
        }

    }
}
