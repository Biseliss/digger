package core;

import java.util.HashMap;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

/**
 * Проигрывание звука.
 *
 * Раньше здесь был Clip, и с ним громкость менялась с задержкой почти в
 * секунду: Clip отдаёт в линию звук далеко вперёд (её буфер — целая секунда),
 * а регулятор усиления действует только на то, что ещё не отправлено.
 *
 * Поэтому звук проигрывается вручную: файл декодируется в PCM один раз, а
 * фоновый поток скармливает его линии небольшими кусками, домножая сэмплы на
 * текущую громкость. Буфер линии — 50 мс, кусок — ~12 мс, так что регулятор
 * слышно практически сразу.
 *
 * Публичный API прежний: setFile / play / loop / stop.
 */
public class Audio {
    /** Всё приводим к одному формату, чтобы масштабирование было единообразным. */
    private static final AudioFormat TARGET =
            new AudioFormat(44100f, 16, 2, true, false); // 16 бит, стерео, little-endian

    private static final double LINE_BUFFER_SECONDS = 0.05;
    private static final int CHUNK_FRAMES = 512;         // ~12 мс при 44.1 кГц

    /**
     * Ухо слышит примерно как амплитуда^0.6, поэтому «честная» амплитуда,
     * равная положению ползунка, звучит совсем не как половина на середине.
     * Возводя положение в 1/0.6, компенсируем это: воспринимаемая громкость
     * идёт ровно за ползунком.
     */
    private static final double PERCEPTUAL_EXPONENT = 1.0 / 0.6;

    // store resource paths instead of InputStreams so we can open a fresh stream each
    // time
    private final HashMap<String, String> soundFiles = new HashMap<>();

    /** Распакованный звук целиком — файлы маленькие, читать с диска каждый раз незачем. */
    private byte[] pcm;

    private volatile boolean playing;
    private volatile boolean looping;
    private volatile boolean stopRequested;
    private Thread playbackThread;

    private volatile float volume = 1f;
    /** Амплитуда, с которой реально сведён предыдущий кусок — для плавного перехода. */
    private float appliedAmplitude = 1f;

    public Audio() {
        soundFiles.put("Soundtrack", "/sounds/soundtrack.wav");
        soundFiles.put("SFX_Dig", "/sounds/sfx_dig.wav");
    }

    public void setFile(String key) {
        stop();
        try {
            var url = getClass().getResource(soundFiles.get(key));
            if (url == null) {
                System.err.println("Звук не найден: " + key);
                return;
            }
            try (AudioInputStream raw = AudioSystem.getAudioInputStream(url);
                 AudioInputStream converted = AudioSystem.getAudioInputStream(TARGET, raw)) {
                pcm = converted.readAllBytes();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Проиграть один раз. Пока звук ещё звучит, повторный вызов игнорируется. */
    public void play() {
        start(false);
    }

    /** Проиграть зациклённо (музыка). */
    public void loop() {
        start(true);
    }

    private void start(boolean loopForever) {
        if (pcm == null || playing) return;

        looping = loopForever;
        stopRequested = false;
        playing = true;

        playbackThread = new Thread(this::pump, "audio");
        playbackThread.setDaemon(true);   // не держим JVM при выходе из игры
        playbackThread.start();
    }

    public void stop() {
        stopRequested = true;
        Thread t = playbackThread;
        if (t != null) {
            try {
                t.join(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        playing = false;
        playbackThread = null;
    }

    /** Гоняет PCM в линию кусками, применяя текущую громкость. */
    private void pump() {
        SourceDataLine line = null;
        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, TARGET);
            line = (SourceDataLine) AudioSystem.getLine(info);
            int bufferBytes = (int) (TARGET.getFrameSize() * TARGET.getFrameRate() * LINE_BUFFER_SECONDS);
            line.open(TARGET, bufferBytes);
            line.start();

            int chunkBytes = CHUNK_FRAMES * TARGET.getFrameSize();
            byte[] chunk = new byte[chunkBytes];
            int pos = 0;

            while (!stopRequested) {
                if (pos >= pcm.length) {
                    if (!looping) break;
                    pos = 0;
                }
                int len = Math.min(chunkBytes, pcm.length - pos);
                applyVolume(pcm, pos, chunk, len);
                line.write(chunk, 0, len);
                pos += len;
            }

            if (!stopRequested) line.drain();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (line != null) {
                line.stop();
                line.close();
            }
            playing = false;
        }
    }

    /**
     * Копирует кусок PCM, домножая 16-битные сэмплы на громкость.
     *
     * Амплитуда плавно доводится до целевой внутри куска: резкий скачок
     * усиления даёт щелчок, а так переход растянут на ~12 мс и не слышен.
     */
    private void applyVolume(byte[] src, int offset, byte[] dst, int len) {
        float target = amplitude();
        int samples = len / 2;

        for (int i = 0; i < samples; i++) {
            float mix = appliedAmplitude + (target - appliedAmplitude) * (i / (float) samples);

            int lo = src[offset + i * 2] & 0xFF;
            int hi = src[offset + i * 2 + 1];
            int sample = (hi << 8) | lo;

            int scaled = Math.round(sample * mix);
            if (scaled > Short.MAX_VALUE) scaled = Short.MAX_VALUE;
            if (scaled < Short.MIN_VALUE) scaled = Short.MIN_VALUE;

            dst[i * 2] = (byte) (scaled & 0xFF);
            dst[i * 2 + 1] = (byte) ((scaled >> 8) & 0xFF);
        }
        appliedAmplitude = target;
    }

    /** Положение ползунка -> амплитуда по перцептивной кривой. */
    private float amplitude() {
        float v = volume;
        if (v <= 0f) return 0f;
        return (float) Math.pow(v, PERCEPTUAL_EXPONENT);
    }

    /** Громкость 0..1. Значение запоминается и переживает смену файла. */
    public void setVolume(float value) {
        volume = Math.max(0f, Math.min(1f, value));
    }

    public float getVolume() {
        return volume;
    }

    public boolean isPlaying() {
        return playing;
    }
}
