package game.render;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Анимация — последовательность кадров одной текстуры. Кадры ищутся по
 * маске "<base>_0", "<base>_1", ... пока такой файл существует в
 * /textures, так что для добавления анимации достаточно докинуть файлы
 * player_walk_0.png, player_walk_1.png и т.д.
 */
public final class Animation {
    private final List<BufferedImage> frames;
    private final double frameDuration;
    private double elapsed;
    private int currentFrame;

    /**
     * @param base          общее имя, например "player_walk"
     * @param frameDuration сколько секунд показывать один кадр
     */
    public Animation(String base, double frameDuration) {
        this.frameDuration = frameDuration;
        frames = new ArrayList<>();
        for (int i = 0; Textures.exists(base + "_" + i); i++) {
            frames.add(Textures.get(base + "_" + i));
        }
        if (frames.isEmpty()) {
            throw new IllegalStateException("Нет кадров анимации для " + base);
        }
    }

    public void tick(double dt) {
        if (frames.size() <= 1) return;
        elapsed += dt;
        while (elapsed >= frameDuration) {
            elapsed -= frameDuration;
            currentFrame = (currentFrame + 1) % frames.size();
        }
    }

    public BufferedImage currentFrame() {
        return frames.get(currentFrame);
    }

    public int frameCount() {
        return frames.size();
    }

    /** Сбросить анимацию на первый кадр, например при смене состояния (стоит/идёт). */
    public void reset() {
        currentFrame = 0;
        elapsed = 0;
    }
}
