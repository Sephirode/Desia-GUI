package desia.audio;

import javafx.application.Platform;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/*
 * Simple JavaFX audio wrapper.
 *
 * Resource lookup strategy (classpath):
 * - BGM: /audio/bgm/idle.(mp3|wav|m4a|aac|ogg)
 * - BGM: /audio/bgm/battle.(mp3|wav|m4a|aac|ogg)
 * - SFX: /audio/sfx/hit.(mp3|wav|m4a|aac|ogg)
 *
 * Only the base name is hard-coded; you can choose the extension.
 */
public final class AudioService {
    private static final AudioService INSTANCE = new AudioService();

    /** Candidate extensions to try (first match wins). */
    private static final String[] EXT = {".mp3", ".wav", ".m4a", ".aac", ".ogg"};
    private static final String[] NORMAL_BATTLE_CYCLE = {
            "/audio/bgm/battle1",
            "/audio/bgm/battle2",
            "/audio/bgm/battle3"
    };
    private final AtomicInteger normalBattleCursor = new AtomicInteger(0);

    private final Map<String, URL> resolved = new ConcurrentHashMap<>();

    private volatile boolean enabled = true;
    private volatile String currentTrackKey;
    private MediaPlayer bgmPlayer;

    private double bgmVolume = 0.35;
    private double sfxVolume = 0.70;

    private AudioService() {}

    public static AudioService get() {
        return INSTANCE;
    }

    /**
     * Call once from the JavaFX Application thread (FxApp.start).
     * Safe to call multiple times.
     */
    public void init() {
        // no-op for now; kept as an explicit lifecycle hook
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) stopBgm();
    }

    public void setBgmVolume(double volume01) {
        this.bgmVolume = clamp01(volume01);
        runFx(() -> {
            if (bgmPlayer != null) bgmPlayer.setVolume(this.bgmVolume);
        });
    }

    public void setSfxVolume(double volume01) {
        this.sfxVolume = clamp01(volume01);
    }

    public void playBgm(AudioTrack track) {
        if (track == null) return;
        playBgmBase(track.resourceBase());
    }

    /**
     * Play a BGM track by its resource base path (without extension).
     * Example: "/audio/bgm/idle" -> tries idle.mp3, idle.wav, ...
     */
    public void playBgmBase(String resourceBase) {
        if (!enabled || resourceBase == null || resourceBase.isBlank()) return;
        if (resourceBase.equals(currentTrackKey) && bgmPlayer != null) return;

        URL url = resolve(resourceBase);
        if (url == null) return;

        runFx(() -> {
            stopBgmInternal();
            try {
                Media media = new Media(url.toExternalForm());
                MediaPlayer mp = new MediaPlayer(media);
                mp.setCycleCount(MediaPlayer.INDEFINITE);
                mp.setVolume(bgmVolume);
                mp.play();
                bgmPlayer = mp;
                currentTrackKey = resourceBase;
            } catch (Throwable t) {
                // JavaFX Media can fail depending on platform codecs.
                // Keep the game running.
                currentTrackKey = null;
                bgmPlayer = null;
            }
        });
    }

    /**
     * Chapter-specific idle BGM.
     * Priority:
     * 1) /audio/bgm/idle_ch{chapter}.*
     * 2) /audio/bgm/idle.*
     */
    public void playIdleForChapter(int chapter) {
        if (!enabled) return;
        if (chapter > 0) {
            String ch = "/audio/bgm/idle_ch" + chapter;
            if (resolve(ch) != null) {
                playBgmBase(ch);
                return;
            }
        }
        playBgm(AudioTrack.IDLE);
    }

    /**
     * Battle/Boss BGM.
     * - Normal battle: /audio/bgm/battle.*
     * - Boss battle priority:
     *   1) /audio/bgm/boss_ch{chapter}.*
     *   2) /audio/bgm/boss_{enemyNameSanitized}.*
     *   3) /audio/bgm/battle.*
     */
    public void playBattleFor(int chapter, String enemyName, boolean boss) {
        if (!enabled) return;
        if (boss) {
            if (chapter > 0) {
                String ch = "/audio/bgm/boss_ch" + chapter;
                if (resolve(ch) != null) {
                    playBgmBase(ch);
                    return;
                }
            }
            String key = bossNameKey(enemyName);
            if (key != null) {
                String byName = "/audio/bgm/boss_" + key;
                if (resolve(byName) != null) {
                    playBgmBase(byName);
                    return;
                }
            }
        }
        //playBgm(AudioTrack.BATTLE);
        playNextNormalBattleBgm();
    }

    private void playNextNormalBattleBgm() {
        String base = nextNormalBattleBase();
        if (base != null) playBgmBase(base);
    }

    private String nextNormalBattleBase() {
        int len = NORMAL_BATTLE_CYCLE.length;
        int start = normalBattleCursor.getAndUpdate(i -> (i + 1) % len);

        for (int i = 0; i < len; i++) {
            String base = NORMAL_BATTLE_CYCLE[(start + i) % len];
            if (resolve(base) != null) return base;
        }
        if (resolve(AudioTrack.BATTLE.resourceBase()) != null) return AudioTrack.BATTLE.resourceBase();
        return null;
    }

    public void stopBgm() {
        runFx(this::stopBgmInternal);
    }

    public void playSfx(Sfx sfx) {
        if (!enabled || sfx == null) return;
        URL url = resolve(sfx.resourceBase());
        if (url == null) return;

        runFx(() -> {
            try {
                Media media = new Media(url.toExternalForm());
                MediaPlayer mp = new MediaPlayer(media);
                mp.setVolume(sfxVolume);
                mp.setOnEndOfMedia(() -> {
                    mp.stop();
                    mp.dispose();
                });
                mp.setOnError(mp::dispose);
                mp.play();
            } catch (Throwable t) {
                // ignore
            }
        });
    }

    public void shutdown() {
        enabled = false;
        stopBgm();
    }

    // -----------------

    private void stopBgmInternal() {
        if (bgmPlayer != null) {
            try {
                bgmPlayer.stop();
            } catch (Throwable ignored) {
            }
            try {
                bgmPlayer.dispose();
            } catch (Throwable ignored) {
            }
        }
        bgmPlayer = null;
        currentTrackKey = null;
    }

    private URL resolve(String resourceBase) {
        if (resourceBase == null || resourceBase.isBlank()) return null;
        return resolved.computeIfAbsent(resourceBase, key -> {
            ClassLoader cl = AudioService.class.getClassLoader();
            for (String ext : EXT) {
                URL u = cl.getResource(stripLeadingSlash(key) + ext);
                if (u != null) return u;
            }
            return null;
        });
    }

    private static String stripLeadingSlash(String s) {
        return (s != null && s.startsWith("/")) ? s.substring(1) : s;
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    private static void runFx(Runnable r) {
        if (r == null) return;
        if (Platform.isFxApplicationThread()) r.run();
        else Platform.runLater(r);
    }

    private static String bossNameKey(String enemyName) {
        if (enemyName == null) return null;
        String s = enemyName.strip();
        if (s.isEmpty()) return null;
        // Resource-friendly key: keep Unicode letters/numbers, replace whitespace with '_', strip path separators.
        s = s.replaceAll("\\s+", "_");
        s = s.replace("/", "_").replace("\\\\", "_");
        s = s.replaceAll("[:*?\"<>|]", "_");
        return s;
    }
}
