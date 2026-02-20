package desia.audio;

/**
 * Background music tracks.
 *
 * Put your audio files in:
 * - src/main/resources/audio/bgm/
 */
public enum AudioTrack {
    IDLE("/audio/bgm/idle"),
    BATTLE("/audio/bgm/battle");

    private final String resourceBase;

    AudioTrack(String resourceBase) {
        this.resourceBase = resourceBase;
    }

    public String resourceBase() {
        return resourceBase;
    }
}
