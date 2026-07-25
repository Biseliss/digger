package game.render;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Анимация — последовательность кадров одной текстуры.
 *
 * Кадры ищутся по номеру рядом с базовым именем, причём поддерживаются обе
 * встречающиеся в проекте раскладки:
 *   "lava/lava1", "lava/lava2", ...   (нумерация с единицы, как в текущем паке)
 *   "player_walk_0", "player_walk_1", ...
 * Так что достаточно докинуть файлы в /textures — код править не нужно.
 *
 * Анимация умеет крутиться сама по времени (tick) либо управляться извне
 * прогрессом 0..1 (setProgress) — второе удобно, когда длительность задаёт
 * игровая логика: фитиль динамита должен догореть ровно за DYNAMITE_FUSE.
 */
public final class Animation {
    private final List<BufferedImage> frames;
    private final double frameDuration;
    private final boolean looping;

    private double elapsed;
    private int currentFrame;

    /** Зацикленная анимация с фиксированной длительностью кадра. */
    public Animation(String base, double frameDuration) {
        this(base, frameDuration, true);
    }

    /**
     * @param base          общее имя, например "lava/lava" или "player_walk"
     * @param frameDuration сколько секунд показывать один кадр
     * @param looping       false — доиграть до последнего кадра и замереть
     */
    public Animation(String base, double frameDuration, boolean looping) {
        this.frameDuration = frameDuration;
        this.looping = looping;
        this.frames = loadFrames(base);
        if (frames.isEmpty()) {
            throw new IllegalStateException("Нет кадров анимации для " + base);
        }
    }

    /**
     * Кадры, растянутые на заданное время: длительность кадра считается
     * автоматически. Для взрыва и фитиля, где важна общая длительность.
     */
    public static Animation overDuration(String base, double totalSeconds, boolean looping) {
        int count = countFrames(base);
        if (count == 0) throw new IllegalStateException("Нет кадров анимации для " + base);
        return new Animation(base, totalSeconds / count, looping);
    }

    private static List<BufferedImage> loadFrames(String base) {
        List<BufferedImage> out = new ArrayList<>();
        for (String name : frameNames(base)) {
            out.add(Textures.get(name));
        }
        return out;
    }

    public static int countFrames(String base) {
        return frameNames(base).size();
    }

    /**
     * Имена кадров по порядку. Пробуем все варианты суффикса — с подчёркиванием
     * и без, с нуля и с единицы, — и берём тот, по которому нашёлся первый кадр.
     */
    private static List<String> frameNames(String base) {
        String[] separators = {"", "_"};
        int[] starts = {0, 1};

        for (String sep : separators) {
            for (int start : starts) {
                if (!Textures.exists(base + sep + start)) continue;
                List<String> names = new ArrayList<>();
                for (int i = start; Textures.exists(base + sep + i); i++) {
                    names.add(base + sep + i);
                }
                return names;
            }
        }
        // одиночная картинка без нумерации — тоже валидная «анимация» из одного кадра
        return Textures.exists(base) ? List.of(base) : List.of();
    }

    public void tick(double dt) {
        if (frames.size() <= 1) return;
        elapsed += dt;
        while (elapsed >= frameDuration) {
            elapsed -= frameDuration;
            if (currentFrame + 1 >= frames.size()) {
                if (!looping) {
                    currentFrame = frames.size() - 1;
                    elapsed = 0;
                    return;
                }
                currentFrame = 0;
            } else {
                currentFrame++;
            }
        }
    }

    /** Выбрать кадр по прогрессу 0..1 — когда длительностью управляет игровая логика. */
    public void setProgress(double progress) {
        double p = Math.max(0, Math.min(1, progress));
        currentFrame = Math.min(frames.size() - 1, (int) (p * frames.size()));
    }

    /** Для незацикленных: доиграла ли до конца. */
    public boolean isFinished() {
        return !looping && currentFrame >= frames.size() - 1;
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
