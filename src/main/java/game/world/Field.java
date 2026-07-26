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

    // Рабочие структуры волны видимости. Переиспользуются, чтобы не мусорить
    // аллокациями каждый кадр.
    private final java.util.ArrayDeque<Long> visitQueue = new java.util.ArrayDeque<>();
    private final java.util.HashSet<Long> visited = new java.util.HashSet<>();
    private static final int[][] NEIGHBOURS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** Одна анимация на всю лаву в мире — кадр общий, тикается в Field.tick. */
    private final game.render.Animation lavaAnimation =
            new game.render.Animation("lava/lava", Constants.LAVA_FRAME_TIME);

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

    /**
     * Пересчёт видимости (п.7). Раньше тут был простой круг, и сквозь стену
     * просвечивали пещеры, в которых игрок никогда не был.
     *
     * Теперь идём волной от игрока по проходимым тайлам (воздух, лестницы) в
     * пределах радиуса: так «наша» полость раскрывается, а соседняя, отрезанная
     * породой, — нет. Стенки раскрываются как соседи достигнутого воздуха,
     * то есть блок видно ровно с той стороны, с которой к нему подошли.
     *
     * @param frame номер кадра — им помечаются блоки, видимые прямо сейчас
     *              (нужно, чтобы нельзя было копать сквозь стену по старой памяти)
     */
    public void updateVisibility(double centerX, double centerY, double radius, int frame) {
        int cx = (int) Math.floor(centerX);
        int cy = (int) Math.floor(centerY);
        double r2 = radius * radius;

        // BFS по проходимым тайлам; область маленькая (радиус света), так что дёшево
        visitQueue.clear();
        visited.clear();

        if (!inBounds(cx, cy)) return;
        visitQueue.add(pack(cx, cy));
        visited.add(pack(cx, cy));

        while (!visitQueue.isEmpty()) {
            long cur = visitQueue.poll();
            int x = unpackX(cur);
            int y = unpackY(cur);

            // сам проходимый тайл тоже виден (фон, лестница)
            markSeen(x, y, frame);

            // стенки вокруг: их видно именно с этой стороны
            for (int nx = x - 1; nx <= x + 1; nx++) {
                for (int ny = y - 1; ny <= y + 1; ny++) {
                    if (!inBounds(nx, ny)) continue;
                    if (getBlock(nx, ny).isSolid()) markSeen(nx, ny, frame);
                }
            }

            // дальше волна идёт только по пустоте и только в пределах радиуса
            for (int[] d : NEIGHBOURS) {
                int nx = x + d[0];
                int ny = y + d[1];
                if (!inBounds(nx, ny)) continue;

                double ddx = nx + 0.5 - centerX;
                double ddy = ny + 0.5 - centerY;
                if (ddx * ddx + ddy * ddy > r2) continue;
                if (getBlock(nx, ny).isSolid()) continue;

                long key = pack(nx, ny);
                if (visited.add(key)) visitQueue.add(key);
            }
        }
    }

    private void markSeen(int tx, int ty, int frame) {
        Block b = getBlock(tx, ty);
        b.markVisible(frame);
        if (!b.isRevealed()) {
            b.setRevealed(true);
            Chunk c = chunkAt(tx, ty);
            if (c != null) c.markDirty();
        }
    }

    /** Виден ли блок игроку прямо сейчас — по этому решается, можно ли копать. */
    public boolean isVisibleNow(int tx, int ty, int frame) {
        return inBounds(tx, ty) && getBlock(tx, ty).isVisibleNow(frame);
    }

    private static long pack(int x, int y) {
        return ((long) x << 32) ^ (y & 0xFFFFFFFFL);
    }

    private static int unpackX(long key) {
        return (int) (key >> 32);
    }

    private static int unpackY(long key) {
        return (int) key;
    }

    // --- разрушение ---

    /**
     * Убирает блок и запускает всё, что за этим следует: задний план,
     * обвал гравия сверху и каскад лавы.
     *
     * @return все реально разрушенные блоки — обычно один, но у лестницы
     *         осыпается вся колонна, и вызывающему нужно вернуть игроку всё.
     */
    public List<Block> breakBlock(int tx, int ty) {
        Block b = getBlock(tx, ty);
        if (!inBounds(tx, ty) || b.isAir() || !b.getType().breakable) return List.of();

        List<Block> broken = new ArrayList<>();
        if (b.getType() == BlockType.LADDER) {
            breakLadderColumn(tx, ty, broken);
        } else {
            broken.add(removeTile(tx, ty, true));
        }
        return broken;
    }

    /**
     * Лестница держится всей колонной: выбили один блок — осыпается и то, что
     * над ним, и то, что под ним (иначе в воздухе повисали бы обрывки).
     */
    private void breakLadderColumn(int tx, int ty, List<Block> out) {
        int top = ty;
        while (isLadder(tx, top - 1)) top--;
        int bottom = ty;
        while (isLadder(tx, bottom + 1)) bottom++;

        for (int y = top; y <= bottom; y++) {
            out.add(removeTile(tx, y, false));
        }
    }

    /**
     * @param leaveBackground оставить ли на месте блока тёмный фон (п.11).
     *                        Лестница — не порода, после неё фону взяться неоткуда.
     */
    private Block removeTile(int tx, int ty, boolean leaveBackground) {
        Block b = getBlock(tx, ty);
        if (leaveBackground) setBackground(tx, ty, b.getType());
        setBlock(tx, ty, new AirBlock(tx, ty));
        b.onBreak(this);
        onTileFreed(tx, ty);
        return b;
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

    /**
     * Зона вокруг точки спавна, где копать запрещено: под базой должна
     * оставаться твёрдая земля, а игрок после респавна — не проваливаться
     * в собственную яму.
     */
    public boolean isSpawnProtected(int tx, int ty) {
        double dx = tx + 0.5 - (Constants.WORLD_W / 2.0);
        double dy = ty + 0.5 - Constants.SURFACE_Y;
        return Math.sqrt(dx * dx + dy * dy) <= Constants.SPAWN_PROTECT_RADIUS;
    }

    public boolean isLadder(int tx, int ty) {
        return getBlock(tx, ty).getType() == BlockType.LADDER;
    }

    /**
     * Лестница должна на что-то опираться или к чему-то крепиться: ставим её
     * либо ПОВЕРХ плотного блока (или другой лестницы), либо ПОД ним.
     * Иначе колонны висели бы посреди пустоты.
     */
    public boolean canPlaceLadder(int tx, int ty) {
        if (!inBounds(tx, ty) || !getBlock(tx, ty).isAir()) return false;
        return hasLadderSupport(tx, ty + 1) || hasLadderSupport(tx, ty - 1);
    }

    private boolean hasLadderSupport(int tx, int ty) {
        return isSolid(tx, ty) || isLadder(tx, ty);
    }

    public void placeLadder(int tx, int ty) {
        if (!canPlaceLadder(tx, ty)) return;
        setBlock(tx, ty, new LadderBlock(tx, ty));
        reveal(tx, ty);
    }

    // --- тик мира ---

    public void tick(double dt, Player player) {
        lavaAnimation.tick(dt);

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

        drawLava(g, camX, camY, viewW, viewH);

        // падающий гравий рисуем поверх — он вне сетки чанков
        for (FallingBlock fb : falling) {
            int sx = (int) Math.round((fb.tileX * Constants.TILE - camX) * scale);
            int sy = (int) Math.round((fb.y * Constants.TILE - camY) * scale);
            g.drawImage(game.render.Textures.get(fb.type.texture),
                    sx, sy, Constants.TILE * scale, Constants.TILE * scale, null);
        }

    }

    /**
     * Лава рисуется отдельным проходом, а не из кэша чанка: она анимирована,
     * и запекать её в кэш значило бы пересобирать чанк каждый кадр.
     *
     * Проход дешёвый — идём только по тайлам, попавшим в кадр (это пара сотен
     * клеток), и берём один общий кадр анимации на всю лаву.
     */
    private void drawLava(Graphics2D g, double camX, double camY, int viewW, int viewH) {
        int scale = Constants.SCALE;
        int size = Constants.TILE * scale;

        int firstTx = (int) Math.floor(camX / Constants.TILE);
        int firstTy = (int) Math.floor(camY / Constants.TILE);
        int lastTx = (int) Math.ceil((camX + viewW / (double) scale) / Constants.TILE);
        int lastTy = (int) Math.ceil((camY + viewH / (double) scale) / Constants.TILE);

        var frame = lavaAnimation.currentFrame();
        var src = game.render.Textures.opaqueBounds(frame);

        for (int tx = firstTx; tx <= lastTx; tx++) {
            for (int ty = firstTy; ty <= lastTy; ty++) {
                if (!inBounds(tx, ty)) continue;
                Block b = getBlock(tx, ty);
                if (b.getType() != BlockType.LAVA) continue;
                if (!b.isRevealed()) continue;   // туман войны (п.7) действует и на лаву

                int sx = (int) Math.round((tx * Constants.TILE - camX) * scale);
                int sy = (int) Math.round((ty * Constants.TILE - camY) * scale);
                // непрозрачную часть кадра растягиваем на всю клетку, иначе
                // между тайлами лужи видны щели (см. Textures.opaqueBounds)
                g.drawImage(frame, sx, sy, sx + size, sy + size,
                        src.x, src.y, src.x + src.width, src.y + src.height, null);

                int shade = Chunk.depthShade(ty);
                if (shade > 0) {
                    g.setColor(Chunk.shadeColor(shade));
                    g.fillRect(sx, sy, size, size);
                }
            }
        }
    }
}
