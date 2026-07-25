package core;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

public class Audio {
    Clip clip;
    // store file paths instead of InputStreams so we can open a fresh stream each
    // time
    HashMap<String, String> soundFiles = new HashMap<>();

    public Audio() {
        try {
            soundFiles.put("Soundtrack", Paths.get(getClass().getResource("/sounds/soundtrack.wav").toURI()).toString());
            soundFiles.put("SFX_Dig", Paths.get(getClass().getResource("/sounds/sfx_dig.wav").toURI()).toString());
        } catch (URISyntaxException e) {
            System.out.println("Path doesn't exist or is wrong");
        }
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

            // open a fresh buffered stream for each request so the stream is at the file
            // start
            try (InputStream in = new BufferedInputStream(Files.newInputStream(Paths.get(soundFiles.get(key))));
                    AudioInputStream ais = AudioSystem.getAudioInputStream(in)) {

                clip = AudioSystem.getClip();
                clip.open(ais);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void play() {
        if (clip != null)
            clip.start();
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
