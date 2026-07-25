package tools;

import game.Constants;
import game.entity.Player;
import game.item.*;
import game.world.*;

/**
 * Прогон основной механики без окна: копание, гейтинг по тиру, лимиты руды,
 * экономика, обвал гравия, каскад лавы, урон, респавн, свет.
 *
 * Полноценных юнит-тестов на джеме заводить не стали, но проверить, что
 * ничего не развалилось после правок, хочется одной командой:
 *
 *   ./mvnw compile exec:java -Dexec.mainClass=tools.SmokeTest
 */
public class SmokeTest {
    static int failed = 0;
    static int frame = 1;
    static void check(String name, boolean ok) {
        System.out.println((ok ? "  OK  " : " FAIL ") + name);
        if (!ok) failed++;
    }

    public static void main(String[] a) {
        Field f = new Field();
        new WorldGenerator(Constants.DEFAULT_SEED).generateInto(f);

        // 1. копание вниз от поверхности
        Player p = new Player(Constants.WORLD_W / 2, Constants.SURFACE_Y - 2);
        // цель — не жёсткое число, а глубина, докуда достаёт СТАРТОВАЯ кирка:
        // ниже слоя 2 нужен уже медный тир, а границы слоёв ещё и волнистые
        int digTarget = Constants.LAYER_2_END - Constants.LAYER_BOUNDARY_WAVE - 4;
        for (int i = 0; i < 400 && p.depth() < digTarget; i++) {
            int tx = (int) p.centerTileX();
            int ty = (int) (p.centerTileY() + 1);
            java.util.List<Block> broken = java.util.List.of();
            for (int k = 0; k < 200 && broken.isEmpty() && f.isSolid(tx, ty); k++) {
                frame++;
                f.updateVisibility(p.centerTileX(), p.centerTileY(), 12, frame);
                broken = p.dig(f, tx, ty, 1.0 / 60, frame);
            }
            for (Block b : broken) if (b.drop() != null) p.addOre(b.drop());
            for (int k = 0; k < 30; k++) p.tick(1.0 / 60, f, false, false, false, false);
        }
        check("докопался деревянной киркой до " + digTarget + " тайлов (глубина " + p.depth() + ")",
                p.depth() >= digTarget);
        check("собрал руду по пути (" + p.getCarriedOre() + ")", !p.getCarriedOre().isEmpty());

        // 2. лимит переноса на тип
        int limit = p.getTool().getOreCarryLimit();
        for (int i = 0; i < 100; i++) p.addOre(OreType.STONE);
        check("лимит руды на тип соблюдён (" + p.getOreCount(OreType.STONE) + "/" + limit + ")",
                p.getOreCount(OreType.STONE) == limit);

        // 3. продажа
        int before = p.getMoney();
        int earned = p.sellAllOre();
        check("продажа начислила деньги (+" + earned + ")", earned > 0 && p.getMoney() == before + earned);
        check("после продажи карман пуст", p.getCarriedOre().isEmpty());

        // 4. апгрейд инструмента
        Tool t = p.getTool();
        int lvl = t.getLevel();
        t.upgrade();
        check("апгрейд поднимает тир и лимит",
                t.getLevel() == lvl + 1 && t.getOreCarryLimit() > limit);

        // 5. гейтинг по тиру: глубинную породу деревянной киркой не взять
        Player rookie = new Player(Constants.WORLD_W / 2, Constants.SURFACE_Y);
        int deepY = Constants.SURFACE_Y + Constants.LAYER_2_END + 20;
        int deepX = Constants.WORLD_W / 2;
        while (!f.isSolid(deepX, deepY)) deepY++;
        rookie.teleportToTile(deepX, deepY - 1);
        java.util.List<Block> r = java.util.List.of();
        for (int k = 0; k < 500; k++) {
            frame++;
            f.updateVisibility(rookie.centerTileX(), rookie.centerTileY(), 12, frame);
            r = rookie.dig(f, deepX, deepY, 1.0 / 60, frame);
        }
        check("deepslate не копается деревянной киркой", f.isSolid(deepX, deepY) && r.isEmpty());

        // 6. обвал гравия: гравий, под ним опора, ниже пустота и дно
        int gx = 5, gy = Constants.SURFACE_Y + 50;
        f.setBlock(gx, gy, new GravelBlock(gx, gy));
        f.setBlock(gx, gy + 1, new SolidBlock(gx, gy + 1, BlockType.STONE));
        f.setBlock(gx, gy + 2, new AirBlock(gx, gy + 2));
        f.setBlock(gx, gy + 3, new AirBlock(gx, gy + 3));
        f.setBlock(gx, gy + 4, new SolidBlock(gx, gy + 4, BlockType.BEDROCK));
        f.breakBlock(gx, gy + 1);
        Player bystander = new Player(0, 0);
        for (int i = 0; i < 120; i++) f.tick(1.0 / 60, bystander);
        check("гравий улетел со своего места", !f.isSolid(gx, gy));
        check("гравий приземлился на дно колодца",
                f.getBlock(gx, gy + 3).getType() == BlockType.GRAVEL);

        // 7. каскад лавы вниз
        int lx = 8, ly = Constants.SURFACE_Y + 60;
        f.setBlock(lx, ly, new LavaBlock(lx, ly));
        for (int i = 1; i <= 3; i++) f.setBlock(lx, ly + i, new SolidBlock(lx, ly + i, BlockType.STONE));
        f.setBlock(lx, ly + 4, new SolidBlock(lx, ly + 4, BlockType.BEDROCK));
        for (int i = 1; i <= 3; i++) f.breakBlock(lx, ly + i);
        boolean spread = f.isLava(lx, ly + 1) && f.isLava(lx, ly + 2) && f.isLava(lx, ly + 3);
        check("лава продублировалась вниз до упора", spread);

        // 8. урон от лавы
        Player burner = new Player(lx, ly + 1);
        int hp0 = burner.getHealth();
        burner.tick(1.0 / 60, f, false, false, false, false);
        check("лава наносит урон при касании (" + hp0 + " -> " + burner.getHealth() + ")",
                burner.getHealth() < hp0);

        // 9. смерть обнуляет руду, но не апгрейды
        p.addOre(OreType.GOLD);
        int keptLevel = p.getTool().getLevel();
        int keptMoney = p.getMoney();
        p.respawn(Constants.WORLD_W / 2, Constants.SURFACE_Y - 2);
        check("после респавна руда потеряна, апгрейд и деньги целы",
                p.getCarriedOre().isEmpty() && p.getTool().getLevel() == keptLevel
                        && p.getMoney() == keptMoney && p.getHealth() == Constants.PLAYER_MAX_HP);

        // 10. свет: с факелом радиус больше
        double base = p.lightRadius();
        p.addUtility(UtilityType.TORCH);
        check("факел увеличивает радиус света (" + base + " -> " + p.lightRadius() + ")",
                p.lightRadius() == base + Constants.TORCH_BONUS_RADIUS);

        // 11. лестница-блок: стак и покупка пачкой
        Player climber = new Player(20, Constants.SURFACE_Y - 2);
        check("лестницы стакаются до " + Constants.LADDER_CARRY_LIMIT,
                climber.addUtility(UtilityType.LADDER, Constants.LADDER_CARRY_LIMIT)
                        && !climber.addUtility(UtilityType.LADDER)
                        && climber.getUtility(UtilityType.LADDER) == Constants.LADDER_CARRY_LIMIT);
        check("одна покупка даёт " + Constants.LADDER_PURCHASE_AMOUNT + " штук",
                UtilityType.LADDER.purchaseAmount == Constants.LADDER_PURCHASE_AMOUNT);

        // 12. лестница проходима, но держит игрока и позволяет лезть вверх
        int cx = 20;
        int floorY = Constants.SURFACE_Y + 40;
        for (int y = floorY - 12; y < floorY; y++) f.setBlock(cx, y, new AirBlock(cx, y));
        f.setBlock(cx, floorY, new SolidBlock(cx, floorY, BlockType.STONE));
        for (int y = floorY - 1; y >= floorY - 10; y--) f.placeLadder(cx, y);
        check("лестница не solid — сквозь неё можно ходить", !f.isSolid(cx, floorY - 5));

        climber.teleportToTile(cx, floorY - 5);
        double startY = climber.getY();
        for (int i = 0; i < 60; i++) climber.tick(1.0 / 60, f, false, false, false, false);
        check("на лестнице игрок висит и не падает (" + startY + " -> " + climber.getY() + ")",
                Math.abs(climber.getY() - startY) < 0.5 && climber.isOnLadder());

        for (int i = 0; i < 60; i++) climber.tick(1.0 / 60, f, false, false, true, false);
        check("W поднимает по лестнице (" + startY + " -> " + climber.getY() + ")",
                climber.getY() < startY - Constants.TILE);

        double topY = climber.getY();
        for (int i = 0; i < 30; i++) climber.tick(1.0 / 60, f, false, false, false, true);
        check("S опускает по лестнице (" + topY + " -> " + climber.getY() + ")",
                climber.getY() > topY);

        // 13. снятая лестница возвращается в инвентарь
        Player miner = new Player(cx + 1, floorY - 2);
        miner.addUtility(UtilityType.LADDER, 1);
        int had = miner.getUtility(UtilityType.LADDER);
        java.util.List<Block> brokenLadders = java.util.List.of();
        for (int k = 0; k < 200 && brokenLadders.isEmpty(); k++) {
            frame++;
            f.updateVisibility(miner.centerTileX(), miner.centerTileY(), 12, frame);
            brokenLadders = miner.dig(f, cx, floorY - 2, 1.0 / 60, frame);
        }
        for (Block b : brokenLadders) miner.addUtility(UtilityType.LADDER);
        check("лестница ломается и вся колонна возвращается в инвентарь ("
                        + brokenLadders.size() + " шт)",
                brokenLadders.size() > 1
                        && brokenLadders.stream().allMatch(b -> b.getType() == BlockType.LADDER)
                        && miner.getUtility(UtilityType.LADDER) == had + brokenLadders.size());

        System.out.println(failed == 0 ? "\nВСЕ ПРОВЕРКИ ПРОШЛИ" : "\nПРОВАЛЕНО: " + failed);
        System.exit(failed == 0 ? 0 : 1);
    }
}
