package game.world;

/**
 * Простой value-noise на хэше координат. Нужен, чтобы границы слоёв и
 * пещеры шли кривыми дугами, а не ровными полосами (п.5).
 */
public class Noise {
    private final long seed;

    public Noise(long seed) {
        this.seed = seed;
    }

    private double hash(long x, long y) {
        long h = seed;
        h ^= x * 0x9E3779B97F4A7C15L;
        h ^= y * 0xC2B2AE3D27D4EB4FL;
        h ^= (h >>> 29);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 32);
        return (h >>> 11) / (double) (1L << 53); // 0..1
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    /** Одномерный шум — для волнистых границ слоёв по X. */
    public double value1D(double x) {
        int x0 = (int) Math.floor(x);
        double f = smooth(x - x0);
        return hash(x0, 0) * (1 - f) + hash(x0 + 1L, 0) * f;
    }

    public double value2D(double x, double y) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        double fx = smooth(x - x0);
        double fy = smooth(y - y0);
        double v00 = hash(x0, y0);
        double v10 = hash(x0 + 1L, y0);
        double v01 = hash(x0, y0 + 1L);
        double v11 = hash(x0 + 1L, y0 + 1L);
        double top = v00 * (1 - fx) + v10 * fx;
        double bottom = v01 * (1 - fx) + v11 * fx;
        return top * (1 - fy) + bottom * fy;
    }

    /** Несколько октав — рельеф получается менее «гладко-волнистым». */
    public double fbm1D(double x, int octaves) {
        double sum = 0;
        double amp = 1;
        double total = 0;
        for (int i = 0; i < octaves; i++) {
            sum += value1D(x) * amp;
            total += amp;
            x *= 2;
            amp *= 0.5;
        }
        return sum / total;
    }
}
