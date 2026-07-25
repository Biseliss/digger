package core;

import java.util.HashMap;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;

public class Audio {
    private Clip clip;
    private boolean playing;
    /** 0..1; храним отдельно, потому что setFile пересоздаёт clip. */
    private float volume = 1f;
    // store resource paths instead of InputStreams so we can open a fresh stream each
    // time
    private final HashMap<String, String> soundFiles = new HashMap<>();

    public Audio() {
        soundFiles.put("Soundtrack", "/sounds/soundtrack.wav");
        soundFiles.put("SFX_Dig", "/sounds/sfx_dig.wav");
    }

    public void setFile(String key) {
        try {
            // close previous clip if open
            if (clip != null) {
                try {
                    if (clip.isRunning())
                        clip.stop();
                } catch (Exception ignore) {
                }
                try {
                    clip.close();
                } catch (Exception ignore) {
                }
            }

            // reopen the resource each time so the stream is at the file start;
            // getResource works both when running from files and from a packaged jar
            try (AudioInputStream ais = AudioSystem.getAudioInputStream(getClass().getResource(soundFiles.get(key)))) {
                clip = AudioSystem.getClip();
                clip.open(ais);
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        playing = false;
                    }
                });
                applyVolume();   // новый клип открывается с текущей громкостью
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void play() {
        if (clip == null) return;
        if (playing) return;

        try {
            clip.stop();
            clip.setFramePosition(0);
            clip.start();
            playing = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loop() {
        if (clip != null) {
            clip.loop(Clip.LOOP_CONTINUOUSLY);
            playing = true;
        }
    }

    public void stop() {
        if (clip != null) {
            clip.stop();
            playing = false;
        }
    }

    /** Громкость 0..1. Значение запоминается и переживает смену файла. */
    public void setVolume(float value) {
        volume = Math.max(0f, Math.min(1f, value));
        applyVolume();
    }

    public float getVolume() {
        return volume;
    }

    /**
     * MASTER_GAIN задаётся в децибелах, а не в долях, поэтому переводим:
     * линейная громкость 0..1 -> дБ. На нуле уводим ползунок в минимум линии,
     * иначе log10(0) даст -Infinity.
     */
    private void applyVolume() {
        if (clip == null) return;
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) return;

        FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float dB = volume <= 0.0001f
                ? gain.getMinimum()
                : (float) (20.0 * Math.log10(volume));
        gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), dB)));
    }
}
