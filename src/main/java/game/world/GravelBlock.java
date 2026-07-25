package game.world;

/**
 * Гравий (п.6). Сам по себе обычный блок; вся логика обвала — событийная:
 * Field при разрушении любого тайла смотрит, не оказался ли над ним гравий,
 * и если да — превращает его в падающий FallingBlock.
 */
public class GravelBlock extends SolidBlock {
    public GravelBlock(int worldX, int worldY) {
        super(worldX, worldY, BlockType.GRAVEL);
    }
}
