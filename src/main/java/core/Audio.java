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
     * Насколько конец зацикленного трека наезжает на его начало.
     *
     * Без наложения на стыке слышна пауза: у трека подрезаны края, и петля
     * «дышит» тишиной. Здесь хвост плавно гаснет, а начало под ним нарастает.
     */
    private static final double LOOP_CROSSFADE_SECONDS = 0.35;

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
    private byte[] basePcm;

    /**
     * Буфер, который реально гоняется в линию. Обычно совпадает с basePcm;
     * при воспроизведении с изменённым питчем — его resample-копия (см.
     * {@link #play(float)}), пересобираемая под каждый вызов.
     */
    private byte[] active;

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
        soundFiles.put("SFX_Step", "/sounds/step.wav");
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
                basePcm = converted.readAllBytes();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Проиграть один раз. Пока звук ещё звучит, повторный вызов игнорируется. */
    public void play() {
        start(false, 1f);
    }

    /**
     * Проиграть один раз с изменённым питчем (1 — как есть, {@literal <}1 —
     * ниже и медленнее, {@literal >}1 — выше и быстрее).
     *
     * Питч меняется через resample «на лету» (шаг чтения сэмплов не 1, а
     * pitch): честного pitch-shift без изменения длительности тут нет, но
     * для короткого одноразового звука вроде шага разница неслышна, а
     * реализация не тянет за собой отдельную DSP-библиотеку.
     */
    public void play(float pitch) {
        start(false, pitch);
    }

    /** Проиграть зациклённо (музыка). */
    public void loop() {
        start(true, 1f);
    }

    private void start(boolean loopForever, float pitch) {
        if (basePcm == null || playing) return;

        active = (pitch == 1f) ? basePcm : resample(basePcm, pitch);

        looping = loopForever;
        stopRequested = false;
        playing = true;

        playbackThread = new Thread(this::pump, "audio");
        playbackThread.setDaemon(true);   // не держим JVM при выходе из игры
        playbackThread.start();
    }

    /** Нехитрый resample по ближайшему сэмплу — меняет и питч, и длительность разом. */
    private byte[] resample(byte[] src, float pitch) {
        int frame = TARGET.getFrameSize();
        int srcFrames = src.length / frame;
        int dstFrames = Math.max(1, (int) (srcFrames / pitch));
        byte[] dst = new byte[dstFrames * frame];

        for (int i = 0; i < dstFrames; i++) {
            int srcFrame = Math.min(srcFrames - 1, (int) (i * pitch));
            System.arraycopy(src, srcFrame * frame, dst, i * frame, frame);
        }
        return dst;
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
            int crossfade = crossfadeBytes();
            int pos = 0;

            while (!stopRequested) {
                if (pos >= active.length) {
                    if (!looping) break;
                    // начало трека уже прозвучало внутри кроссфейда — не повторяем его
                    pos = crossfade;
                }
                int len = Math.min(chunkBytes, active.length - pos);
                fillChunk(chunk, len, pos, crossfade);
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

    /** Длина зоны наложения в байтах, выровненная по фрейму. */
    private int crossfadeBytes() {
        if (active == null) return 0;
        int frame = TARGET.getFrameSize();
        int bytes = (int) (frame * TARGET.getFrameRate() * LOOP_CROSSFADE_SECONDS);
        bytes -= bytes % frame;
        // на очень коротких звуках наложение бессмысленно
        return Math.min(bytes, active.length / 4);
    }

    /**
     * Готовит кусок для линии: громкость плюс, если это зацикленный трек,
     * наложение хвоста на начало.
     *
     * Амплитуда плавно доводится до целевой внутри куска: резкий скачок
     * усиления даёт щелчок, а так переход растянут на ~12 мс и не слышен.
     */
    private void fillChunk(byte[] dst, int len, int pos, int crossfade) {
        float target = amplitude();
        int samples = len / 2;
        int tailStart = active.length - crossfade;

        for (int i = 0; i < samples; i++) {
            float mix = appliedAmplitude + (target - appliedAmplitude) * (i / (float) samples);

            int index = pos + i * 2;
            int sample = readSample(index);

            // хвост гаснет, начало под ним нарастает — стык петли без тишины
            if (looping && crossfade > 0 && index >= tailStart) {
                double t = (index - tailStart) / (double) crossfade;
                int head = readSample(index - tailStart);
                sample = (int) (sample * (1 - t) + head * t);
            }

            int scaled = Math.round(sample * mix);
            if (scaled > Short.MAX_VALUE) scaled = Short.MAX_VALUE;
            if (scaled < Short.MIN_VALUE) scaled = Short.MIN_VALUE;

            dst[i * 2] = (byte) (scaled & 0xFF);
            dst[i * 2 + 1] = (byte) ((scaled >> 8) & 0xFF);
        }
        appliedAmplitude = target;
    }

    /** 16-битный сэмпл little-endian по байтовому смещению. */
    private int readSample(int index) {
        if (index < 0 || index + 1 >= active.length) return 0;
        return (active[index + 1] << 8) | (active[index] & 0xFF);
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
