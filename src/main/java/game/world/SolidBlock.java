package game.world;

/** Обычная порода и руда: просто стоит на месте, пока её не выкопают. */
public class SolidBlock extends Block {
    public SolidBlock(int worldX, int worldY, BlockType type) {
        super(worldX, worldY, type);
    }
}
