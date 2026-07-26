package game;

/**
 * Настройки, пережившие бы смену сцены Main menu -> Game: громкость задаётся
 * на экране настроек ДО того, как заведён Audio у Game, поэтому хранится тут,
 * а не внутри самого Game.
 */
public final class AppSettings {
    private AppSettings() {}

    public static volatile float musicVolume = 1f;
    public static volatile float sfxVolume = 1f;
}
