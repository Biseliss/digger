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
    /**
     * Ключ — упакованные (cx, cy) в long, а не record ChunkCoord: этот словарь
     * бьют коллизии игрока и лестниц (несколько раз за тик), волна видимости
     * (десятки-сотни раз за тик) и отрисовка лавы (сотни раз за кадр) — там,
     * где раньше на каждый вызов аллоцировался ChunkCoord, теперь примитив.
     */
    private final Map<Long, Chunk> chunks = new HashMap<>();
    private final List<FallingBlock> falling = new ArrayList<>();
    /** Активные тайлы лавы (упакованный tx,ty) — отрисовка идёт по ним, а не по всему экрану. */
    private final java.util.Set<Long> lavaTiles = new java.util.HashSet<>();

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
                chunks.put(pack(cx, cy), chunk);
            }
        }
    }

    // --- доступ к тайлам ---

    public static boolean inBounds(int tx, int ty) {
        return tx >= 0 && tx < Constants.WORLD_W && ty >= 0 && ty < Constants.WORLD_H;
    }

    public Chunk chunkAt(int tx, int ty) {
        return chunks.get(pack(Math.floorDiv(tx, Constants.CHUNK_W), Math.floorDiv(ty, Constants.CHUNK_H)));
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
        if (c == null) return;

        int lx = Math.floorMod(tx, Constants.CHUNK_W);
        int ly = Math.floorMod(ty, Constants.CHUNK_H);

        // Реестр лавы (для drawLava) держим в актуальном состоянии здесь —
        // это единственная точка, через которую блоки реально попадают в мир.
        Block old = c.get(lx, ly);
        if (old != null && old.getType() == BlockType.LAVA) lavaTiles.remove(pack(tx, ty));
        if (b.getType() == BlockType.LAVA) lavaTiles.add(pack(tx, ty));

        c.set(lx, ly, b);
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
     * обвал гравия сверху, каскад лавы, и обрушение лестниц/мостиков,
     * лишившихся опоры.
     *
     * @return разрушенный блок — ровно один. Лестница/мостик больше не тянут
     *         за собой всю конструкцию: то, что реально повисло без опоры,
     *         обрушивается само по себе через onTileFreed (доп.), а не
     *         возвращается игроку заодно с тем, что он выкопал специально.
     */
    public List<Block> breakBlock(int tx, int ty) {
        Block b = getBlock(tx, ty);
        if (!inBounds(tx, ty) || b.isAir() || !b.getType().breakable) return List.of();
        return List.of(removeTile(tx, ty));
    }

    /**
     * Фон под сломанным тайлом уже выставлен генерацией мира (WorldGenerator,
     * п.11) — по исходной породе слоя, а не по типу блока на момент раскопки.
     * Здесь его трогать не нужно: иначе гравий, разбитый не там, где лежал
     * изначально, стирал бы правильный фон своим временным типом.
     */
    private Block removeTile(int tx, int ty) {
        Block b = getBlock(tx, ty);
        setBlock(tx, ty, new AirBlock(tx, ty));
        b.onBreak(this);
        onTileFreed(tx, ty);
        return b;
    }

    /**
     * Тайл освободился — гравий падает, лава течёт, а соседние лестницы и
     * мостики проверяются на обрушение (доп.): раньше, если сломать якорный
     * плотный блок, под/над который ставили лестницу, она просто повисала в
     * воздухе — ничего не пересчитывало её опору вообще.
     */
    public void onTileFreed(int tx, int ty) {
        Block above = getBlock(tx, ty - 1);
        if (above.getType() == BlockType.GRAVEL) {
            startFalling(tx, ty - 1);
        } else if (above.getType() == BlockType.LAVA) {
            cascadeLava(tx, ty);
        }
        collapseUnsupported(tx, ty);
    }

    /**
     * Обрушение без опоры (доп.). Лестница держится вертикальной цепочкой,
     * мостик — горизонтальной, но в итоге оба должны упираться хотя бы одним
     * концом в твёрдый блок. Освободившийся тайл проверяем в обе стороны его
     * "своей" оси: если соседняя лестница/мостик потеряли последнюю опору —
     * рушится только реально повисший кусок, а не вся конструкция целиком.
     */
    private void collapseUnsupported(int tx, int ty) {
        collapseLadderRun(tx, ty - 1);
        collapseLadderRun(tx, ty + 1);
        collapseBridgeRun(tx - 1, ty);
        collapseBridgeRun(tx + 1, ty);
    }

    private void collapseLadderRun(int tx, int ty) {
        if (!isLadder(tx, ty)) return;
        int top = ty;
        while (isLadder(tx, top - 1)) top--;
        int bottom = ty;
        while (isLadder(tx, bottom + 1)) bottom++;
        if (isSolid(tx, top - 1) || isSolid(tx, bottom + 1)) return;   // ещё держится

        for (int y = top; y <= bottom; y++) {
            Block b = getBlock(tx, y);
            setBlock(tx, y, new AirBlock(tx, y));
            b.onBreak(this);
            onTileFreed(tx, y);   // цепная реакция — вдруг тут держалось что-то ещё
        }
    }

    private void collapseBridgeRun(int tx, int ty) {
        if (!isBridge(tx, ty)) return;
        int left = tx;
        while (isBridge(left - 1, ty)) left--;
        int right = tx;
        while (isBridge(right + 1, ty)) right++;
        if (isSolid(left - 1, ty) || isSolid(right + 1, ty)) return;   // ещё держится

        for (int x = left; x <= right; x++) {
            Block b = getBlock(x, ty);
            setBlock(x, ty, new AirBlock(x, ty));
            b.onBreak(this);
            onTileFreed(x, ty);
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

    // --- мостики (доп.): то же самое, но опора по бокам, а не сверху/снизу ---

    public boolean isBridge(int tx, int ty) {
        return getBlock(tx, ty).getType() == BlockType.BRIDGE;
    }

    /**
     * Мостик — твёрдый блок (BRIDGE.solid), поэтому цепочку "мостик на мостике"
     * уже покрывает обычная проверка isSolid — отдельно проверять isBridge не
     * нужно, в отличие от нетвёрдой лестницы.
     */
    public boolean canPlaceBridge(int tx, int ty) {
        if (!inBounds(tx, ty) || !getBlock(tx, ty).isAir()) return false;
        return isSolid(tx - 1, ty) || isSolid(tx + 1, ty);
    }

    public void placeBridge(int tx, int ty) {
        if (!canPlaceBridge(tx, ty)) return;
        setBlock(tx, ty, new BridgeBlock(tx, ty));
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
                Chunk c = chunks.get(pack(cx, cy));
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
        if (lavaTiles.isEmpty()) return;

        int scale = Constants.SCALE;
        int size = Constants.TILE * scale;

        int firstTx = (int) Math.floor(camX / Constants.TILE);
        int firstTy = (int) Math.floor(camY / Constants.TILE);
        int lastTx = (int) Math.ceil((camX + viewW / (double) scale) / Constants.TILE);
        int lastTy = (int) Math.ceil((camY + viewH / (double) scale) / Constants.TILE);

        var frame = lavaAnimation.currentFrame();
        var src = game.render.Textures.opaqueBounds(frame);

        // Лавы на весь уровень обычно горстка тайлов — идём по её реестру, а не
        // по каждой клетке экрана каждый кадр (раньше это была сотня+ getBlock()
        // с проверкой типа блока даже там, где лавы вообще нет во вьюпорте).
        for (long key : lavaTiles) {
            int tx = unpackX(key);
            int ty = unpackY(key);
            if (tx < firstTx || tx > lastTx || ty < firstTy || ty > lastTy) continue;

            Block b = getBlock(tx, ty);
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
