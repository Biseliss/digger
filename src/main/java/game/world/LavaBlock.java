package game.world;

/**
 * Лава (п.6). Не копается и горизонтально не течёт — вместо физики жидкости
 * простое событийное дублирование вниз, которым занимается Field: при
 * освобождении тайла под лавой туда кладётся новая лава, и так каскадом,
 * пока не упрётся в сплошной блок. Никакого flood-fill.
 */
public class LavaBlock extends Block {
    public LavaBlock(int worldX, int worldY) {
        super(worldX, worldY, BlockType.LAVA);
    }
}
