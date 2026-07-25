package game.world;

import game.Constants;

/**
 * Слои породы (п.4). Глубина хранится в тайлах от поверхности,
 * требование по тиру кирки — гейтинг прогрессии.
 */
public enum Layer {
    DIRT("Dirt", Constants.LAYER_1_END, BlockType.DIRT, 0),
    STONE("Stone", Constants.LAYER_2_END, BlockType.STONE, 0),
    DEEPSLATE("Deepslate", Constants.LAYER_3_END, BlockType.DEEPSLATE, 2),
    HOT("Hot belt", Constants.LAYER_4_END, BlockType.HOT_ROCK, 3),
    CORE("Core", Integer.MAX_VALUE, BlockType.CORE_ROCK, 4);

    public final String displayName;
    /** Нижняя граница слоя в тайлах от поверхности. */
    public final int endDepth;
    public final BlockType baseBlock;
    /** Минимальный тир кирки, чтобы копать блоки этого слоя. */
    public final int requiredTier;

    Layer(String displayName, int endDepth, BlockType baseBlock, int requiredTier) {
        this.displayName = displayName;
        this.endDepth = endDepth;
        this.baseBlock = baseBlock;
        this.requiredTier = requiredTier;
    }

    public int index() {
        return ordinal();
    }

    /** Слой по глубине в тайлах от поверхности. */
    public static Layer atDepth(int depth) {
        for (Layer l : values()) {
            if (depth < l.endDepth) return l;
        }
        return CORE;
    }

    /** Слой по мировой Y-координате тайла. */
    public static Layer atWorldY(int worldY) {
        return atDepth(worldY - Constants.SURFACE_Y);
    }

    /** Радиус круга света на этом слое (п.7). */
    public double lightRadius() {
        return Constants.LIGHT_RADIUS_BY_LAYER[index()];
    }
}
