package game.world;

/**
 * Воздух — тоже полноценный Block, а не null в сетке чанка: иначе проверки
 * соседей (туман войны, гравий, лава, коллизии) пришлось бы везде обвешивать
 * null-чеками.
 */
public class AirBlock extends Block {
    public AirBlock(int worldX, int worldY) {
        super(worldX, worldY, BlockType.AIR);
    }
}
