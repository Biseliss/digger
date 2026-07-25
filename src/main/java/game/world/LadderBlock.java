package game.world;

/**
 * Лестница (п.8) — обычный ставящийся блок, как в Minecraft.
 *
 * Не solid: сквозь неё проходят и в ней стоят. Всё поведение (не падать,
 * лезть на W/S) живёт в Player, потому что зависит от состояния игрока,
 * а не самого тайла.
 */
public class LadderBlock extends Block {
    public LadderBlock(int worldX, int worldY) {
        super(worldX, worldY, BlockType.LADDER);
    }

    /** Свою же лестницу должно быть можно снять любой киркой, на любой глубине. */
    @Override
    public int requiredToolTier() {
        return 0;
    }
}
