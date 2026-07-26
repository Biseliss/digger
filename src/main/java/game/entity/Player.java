package game.entity;

import game.Constants;
import game.item.OreType;
import game.item.Tool;
import game.item.UtilityType;
import game.render.Textures;
import game.world.Block;
import game.world.BlockType;
import game.world.Field;
import game.world.Layer;

import java.awt.Graphics2D;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Игрок: физика, копание, урон, инвентарь.
 *
 * Координаты — в «мировых пикселях» (1 тайл = Constants.TILE пикселей),
 * x/y — левый верхний угол хитбокса. Хитбокс чуть меньше спрайта 8x16 (п.11).
 */
public class Player {
    private double x, y;
    private double vx, vy;
    private boolean onGround;
    private boolean onLadder;
    private boolean facingRight = true;

    private int health = Constants.PLAYER_MAX_HP;
    private int money = Constants.START_MONEY;
    private Tool tool = new Tool(0);

    private final Map<OreType, Integer> carriedOre = new EnumMap<>(OreType.class);
    private final Map<UtilityType, Integer> utilities = new EnumMap<>(UtilityType.class);

    /** Откуда начали падать — для урона по расстоянию (п.6). */
    private double fallStartY;
    private boolean wasInLava;
    private double lavaTimer;
    private double pressureTimer;

    /** Пройденное по земле расстояние с прошлого шага — звук идёт не по времени, а по факту ходьбы. */
    private double stepDistance;
    private boolean stepPending;

    /** Прогресс копания привязан к конкретному тайлу: сменили цель — прогресс сброшен. */
    private int digTargetX = Integer.MIN_VALUE;
    private int digTargetY = Integer.MIN_VALUE;

    private String lastDeathReason = "";

    public Player(double tileX, double tileY) {
        this.x = tileX * Constants.TILE;
        this.y = tileY * Constants.TILE;
        this.fallStartY = y;
    }

    // --- позиция ---

    public double getX() { return x; }
    public double getY() { return y; }

    public double centerTileX() {
        return (x + Constants.HITBOX_W / 2.0) / Constants.TILE;
    }

    public double centerTileY() {
        return (y + Constants.HITBOX_H / 2.0) / Constants.TILE;
    }

    public int depth() {
        return (int) centerTileY() - Constants.SURFACE_Y;
    }

    public Layer currentLayer() {
        return Layer.atWorldY((int) centerTileY());
    }

    public boolean isUnderground() {
        return centerTileY() > Constants.SURFACE_Y;
    }

    public double distanceToTile(double tileX, double tileY) {
        double dx = centerTileX() - tileX;
        double dy = centerTileY() - tileY;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public boolean overlapsTile(int tx, int ty) {
        double left = x;
        double right = x + Constants.HITBOX_W;
        double top = y;
        double bottom = y + Constants.HITBOX_H;
        double tileLeft = tx * Constants.TILE;
        double tileTop = ty * Constants.TILE;
        return right > tileLeft && left < tileLeft + Constants.TILE
                && bottom > tileTop && top < tileTop + Constants.TILE;
    }

    public void teleportToTile(int tx, int ty) {
        this.x = tx * Constants.TILE;
        this.y = ty * Constants.TILE;
        this.vx = 0;
        this.vy = 0;
        this.fallStartY = y;
    }

    // --- физика ---

    public void tick(double dt, Field field, boolean moveLeft, boolean moveRight,
                     boolean up, boolean down) {
        vx = 0;
        if (moveLeft) { vx = -Constants.MOVE_SPEED; facingRight = false; }
        if (moveRight) { vx = Constants.MOVE_SPEED; facingRight = true; }

        onLadder = isTouchingLadder(field);

        if (onLadder) {
            // на лестнице гравитации нет: не жмём W/S — просто висим (п.8)
            if (up) vy = -Constants.CLIMB_SPEED;
            else if (down) vy = Constants.CLIMB_SPEED;
            else vy = 0;
            fallStartY = y;   // слезли с лестницы — падать считаем заново
        } else {
            if (up && onGround) {
                vy = -Constants.JUMP_SPEED;
                onGround = false;
                fallStartY = y;
            }
            vy += Constants.GRAVITY * dt;
            if (vy > Constants.MAX_FALL_SPEED) vy = Constants.MAX_FALL_SPEED;
        }

        moveAxis(field, vx * dt, 0);
        boolean wasOnGround = onGround;
        moveAxis(field, 0, vy * dt);

        if (!onGround && wasOnGround) fallStartY = y;   // только что оторвались от земли
        if (!onGround && vy > 0 && y < fallStartY) fallStartY = y;

        updateSteps(dt);
        applyEnvironmentDamage(dt, field);
    }

    /**
     * Шаг — это не тик, а факт «прошли ещё STEP_DISTANCE пикселей по земле».
     * vx тут уже после moveAxis: если движение упёрлось в стену, оно обнулено,
     * и мы не считаем шаг, хотя кнопка всё ещё зажата.
     */
    private void updateSteps(double dt) {
        if (onGround && !onLadder && vx != 0) {
            stepDistance += Math.abs(vx) * dt;
            if (stepDistance >= Constants.STEP_DISTANCE) {
                stepDistance -= Constants.STEP_DISTANCE;
                stepPending = true;
            }
        } else {
            stepDistance = 0;
        }
    }

    /** @return true один раз на каждый случившийся шаг — вызывающий сам решает, что с этим делать (звук). */
    public boolean consumeStep() {
        if (!stepPending) return false;
        stepPending = false;
        return true;
    }

    /**
     * Лестница «ловит» игрока только НИЖНЕЙ его половиной (игрок высотой в два
     * тайла). Если проверять весь хитбокс, работает эксплойт «лестница — дыра —
     * лестница»: ноги висят в пустоте, а зацепка идёт головой за верхний блок.
     */
    private boolean isTouchingLadder(Field field) {
        int minTx = (int) Math.floor(x / Constants.TILE);
        int maxTx = (int) Math.floor((x + Constants.HITBOX_W - 0.01) / Constants.TILE);
        int feetTy = (int) Math.floor((y + Constants.HITBOX_H - 0.01) / Constants.TILE);
        for (int tx = minTx; tx <= maxTx; tx++) {
            if (field.isLadder(tx, feetTy)) return true;
        }
        return false;
    }

    public boolean isOnLadder() {
        return onLadder;
    }

    /**
     * Двигаемся по одной оси мелкими шагами — так игрок не проскакивает сквозь
     * тайл на большом dt (после фриза), и не нужна честная развёртка коллизий.
     */
    private void moveAxis(Field field, double dx, double dy) {
        // Двигаемся по одной оси, поэтому работаем с одной величиной:
        // так последний шаг легко обрезать по остатку. Раньше шаг всегда был
        // целые 2 px, и на дробном остатке игрок проскакивал дальше, чем
        // просили — падение выходило заметно быстрее терминальной скорости.
        double total = dx != 0 ? dx : dy;
        if (total == 0) return;

        double sign = Math.signum(total);
        double remaining = Math.abs(total);

        while (remaining > 0) {
            double step = Math.min(MAX_STEP, remaining) * sign;
            double nx = dx != 0 ? x + step : x;
            double ny = dy != 0 ? y + step : y;

            if (collides(field, nx, ny)) {
                if (dy > 0) {
                    onLanded();
                } else if (dy < 0) {
                    vy = 0;
                }
                if (dx != 0) vx = 0;
                return;
            }

            x = nx;
            y = ny;
            remaining -= Math.abs(step);
        }

        if (dy > 0) onGround = false;   // летим вниз и ни во что не упёрлись
    }

    /** Шаг разбиения движения: меньше тайла, иначе можно проскочить сквозь стену. */
    private static final double MAX_STEP = 2;

    private void onLanded() {
        if (!onGround) {
            int tilesFallen = (int) ((y - fallStartY) / Constants.TILE);
            if (tilesFallen > Constants.FALL_SAFE_TILES) {
                int raw = (tilesFallen - Constants.FALL_SAFE_TILES) * Constants.FALL_DAMAGE_PER_TILE;
                damage(reduceByArmor(raw), "fell too far");
            }
        }
        onGround = true;
        vy = 0;
        fallStartY = y;
    }

    private boolean collides(Field field, double nx, double ny) {
        int minTx = (int) Math.floor(nx / Constants.TILE);
        int maxTx = (int) Math.floor((nx + Constants.HITBOX_W - 0.01) / Constants.TILE);
        int minTy = (int) Math.floor(ny / Constants.TILE);
        int maxTy = (int) Math.floor((ny + Constants.HITBOX_H - 0.01) / Constants.TILE);

        for (int tx = minTx; tx <= maxTx; tx++) {
            for (int ty = minTy; ty <= maxTy; ty++) {
                if (field.isSolid(tx, ty)) return true;
            }
        }
        return false;
    }

    // --- урон от среды (п.6) ---

    private void applyEnvironmentDamage(double dt, Field field) {
        boolean inLava = touchingLava(field);
        if (inLava) {
            if (!wasInLava) {
                damage(reduceByArmor(Constants.LAVA_BURST_DAMAGE), "burned in lava");
                lavaTimer = 0;
            }
            lavaTimer += dt;
            while (lavaTimer >= Constants.DAMAGE_TICK) {
                lavaTimer -= Constants.DAMAGE_TICK;
                int perTick = (int) Math.round(Constants.LAVA_DAMAGE_PER_SEC * Constants.DAMAGE_TICK);
                damage(reduceByArmor(perTick), "burned in lava");
            }
        } else {
            lavaTimer = 0;
        }
        wasInLava = inLava;

        // давление: слои 4-5 и только без снаряжения — оно убирает его полностью (п.3)
        boolean underPressure = currentLayer().index() >= Layer.HOT.index() && !hasArmor();
        if (underPressure) {
            pressureTimer += dt;
            while (pressureTimer >= Constants.DAMAGE_TICK) {
                pressureTimer -= Constants.DAMAGE_TICK;
                damage((int) Math.round(Constants.PRESSURE_DAMAGE_PER_SEC * Constants.DAMAGE_TICK),
                        "crushed by pressure");
            }
        } else {
            pressureTimer = 0;
        }
    }

    private boolean touchingLava(Field field) {
        int minTx = (int) Math.floor(x / Constants.TILE);
        int maxTx = (int) Math.floor((x + Constants.HITBOX_W - 0.01) / Constants.TILE);
        int minTy = (int) Math.floor(y / Constants.TILE);
        int maxTy = (int) Math.floor((y + Constants.HITBOX_H - 0.01) / Constants.TILE);
        for (int tx = minTx; tx <= maxTx; tx++) {
            for (int ty = minTy; ty <= maxTy; ty++) {
                if (field.isLava(tx, ty)) return true;
            }
        }
        return false;
    }

    /** Снаряжение режет вдвое урон от лавы, падения и динамита (п.3). */
    public int reduceByArmor(int amount) {
        return hasArmor() ? amount / 2 : amount;
    }

    public void damage(int amount, String reason) {
        if (amount <= 0) return;
        health -= amount;
        if (health <= 0) {
            health = 0;
            lastDeathReason = reason;
        }
    }

    public boolean isDead() {
        return health <= 0;
    }

    public String getLastDeathReason() {
        return lastDeathReason;
    }

    // --- копание (п.2: курсор + ЛКМ в радиусе досягаемости) ---

    /**
     * @param frame текущий кадр — нужен, чтобы копать можно было только то,
     *              что видно прямо сейчас (не сквозь стену по старой памяти)
     * @return разрушенные блоки (у лестницы — вся колонна), пусто — если блок
     *         ещё не докопан или копать его нельзя
     */
    public List<Block> dig(Field field, int tx, int ty, double dt, int frame) {
        if (distanceToTile(tx + 0.5, ty + 0.5) > Constants.DIG_REACH) {
            resetDigTarget(field);
            return List.of();
        }
        // правило из п.7: не видишь блок — не копаешь
        if (!field.isVisibleNow(tx, ty, frame)) {
            resetDigTarget(field);
            return List.of();
        }

        // база неприкосновенна: под ней нельзя рыть, иначе она обрушится
        // и после респавна игрок оказывался бы в собственной яме
        if (field.isSpawnProtected(tx, ty)) {
            resetDigTarget(field);
            return List.of();
        }

        Block block = field.getBlock(tx, ty);
        if (block.isAir() || !block.getType().breakable) {
            resetDigTarget(field);
            return List.of();
        }
        if (tool.getLevel() < block.requiredToolTier()) {
            resetDigTarget(field);
            return List.of();
        }

        if (tx != digTargetX || ty != digTargetY) {
            resetDigTarget(field);
            digTargetX = tx;
            digTargetY = ty;
        }

        int power = (int) Math.max(1, Math.round(tool.getDigSpeed() * 60 * dt));
        if (block.applyDigDamage(power)) {
            List<Block> broken = field.breakBlock(tx, ty);
            digTargetX = Integer.MIN_VALUE;
            digTargetY = Integer.MIN_VALUE;
            return broken;
        }
        return List.of();
    }

    public int getDigTargetX() { return digTargetX; }
    public int getDigTargetY() { return digTargetY; }

    public boolean hasDigTarget() {
        return digTargetX != Integer.MIN_VALUE;
    }

    /** Отпустили кнопку или увели курсор — недокопанный блок «залечивается». */
    public void resetDigTarget(Field field) {
        if (digTargetX != Integer.MIN_VALUE) {
            Block old = field.getBlock(digTargetX, digTargetY);
            if (old != null) old.resetDurability();
        }
        digTargetX = Integer.MIN_VALUE;
        digTargetY = Integer.MIN_VALUE;
    }

    // --- инвентарь ---

    /** @return true, если руда влезла (лимит отдельный на каждый тип, п.3). */
    public boolean addOre(OreType ore) {
        if (ore == null) return false;
        int have = carriedOre.getOrDefault(ore, 0);
        if (have >= tool.getOreCarryLimit()) return false;
        carriedOre.put(ore, have + 1);
        return true;
    }

    public Map<OreType, Integer> getCarriedOre() {
        return carriedOre;
    }

    public int getOreCount(OreType ore) {
        return carriedOre.getOrDefault(ore, 0);
    }

    /** Продажа всей руды разом (п.9). */
    public int sellAllOre() {
        int total = 0;
        for (Map.Entry<OreType, Integer> e : carriedOre.entrySet()) {
            total += e.getKey().price * e.getValue();
        }
        carriedOre.clear();
        money += total;
        return total;
    }

    public int getUtility(UtilityType type) {
        return utilities.getOrDefault(type, 0);
    }

    public boolean addUtility(UtilityType type) {
        return addUtility(type, 1);
    }

    /** @return false, если пачка не влезает в стак целиком. */
    public boolean addUtility(UtilityType type, int amount) {
        int have = getUtility(type);
        if (have + amount > type.carryLimit) return false;
        utilities.put(type, have + amount);
        return true;
    }

    public boolean consumeUtility(UtilityType type) {
        int have = getUtility(type);
        if (have <= 0) return false;
        utilities.put(type, have - 1);
        return true;
    }

    public boolean hasTorch() {
        return getUtility(UtilityType.TORCH) > 0;
    }

    public boolean hasArmor() {
        return getUtility(UtilityType.ARMOR) > 0;
    }

    public int getMoney() { return money; }
    public void spendMoney(int amount) { money -= amount; }
    public int getHealth() { return health; }
    public int getHudHealth() { return (int) Math.ceil(health / (double) Constants.HUD_HP_DIVISOR); }
    public Tool getTool() { return tool; }

    /** Радиус круга света: база от слоя + бонус факела (п.7). */
    public double lightRadius() {
        double base = currentLayer().lightRadius();
        return hasTorch() ? base + Constants.TORCH_BONUS_RADIUS : base;
    }

    /**
     * Возврат на базу после смерти/самоумирания (п.8): вся непроданная руда
     * пропадает безвозвратно, апгрейды и купленные утилиты остаются.
     */
    public void respawn(double tileX, double tileY) {
        carriedOre.clear();
        health = Constants.PLAYER_MAX_HP;
        teleportToTile((int) tileX, (int) tileY);
        wasInLava = false;
        lavaTimer = 0;
        pressureTimer = 0;
    }

    // --- отрисовка ---

    public void draw(Graphics2D g, double camX, double camY) {
        int scale = Constants.SCALE;
        // спрайт шире хитбокса — центруем по горизонтали
        double drawX = x - (Constants.PLAYER_W - Constants.HITBOX_W) / 2.0;
        // ...а по вертикали ставим НА НОГИ: спрайт выше хитбокса, и если равнять
        // по верху, лишняя высота уходит под пол и игрок в нём тонет
        double drawY = y + Constants.HITBOX_H - Constants.PLAYER_H;
        int sx = (int) Math.round((drawX - camX) * scale);
        int sy = (int) Math.round((drawY - camY) * scale);
        int w = Constants.PLAYER_W * scale;
        int h = Constants.PLAYER_H * scale;

        if (facingRight) {
            g.drawImage(Textures.get("player"), sx, sy, w, h, null);
        } else {
            g.drawImage(Textures.get("player"), sx + w, sy, -w, h, null);
        }
    }
}
