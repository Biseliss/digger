package core;

import java.util.HashMap;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

public class Audio {
    Clip clip;
    // store resource paths instead of InputStreams so we can open a fresh stream each
    // time
    HashMap<String, String> soundFiles = new HashMap<>();

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
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void play() {
        if (clip == null) return;

        try {
            if (clip.isRunning()) {
                clip.stop();
            }
            clip.setFramePosition(0);
            clip.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loop() {
        if (clip != null)
            clip.loop(Clip.LOOP_CONTINUOUSLY);
    }

    public void stop() {
        if (clip != null)
            clip.stop();
    }

}
