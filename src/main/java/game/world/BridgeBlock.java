package game.world;

/**
 * Мостик (доп.) — как лестница, только горизонтальная опора вместо
 * вертикальной. Обычный твёрдый блок: логика опоры и обрушения живёт в Field,
 * а тут только "снимается любой киркой" — то же правило, что и у лестницы.
 */
public class BridgeBlock extends SolidBlock {
    public BridgeBlock(int worldX, int worldY) {
        super(worldX, worldY, BlockType.BRIDGE);
    }

    @Override
    public int requiredToolTier() {
        return 0;
    }
}
