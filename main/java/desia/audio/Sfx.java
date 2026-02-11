package desia.audio;

/**
 * One-shot sound effects.
 *
 * Put your audio files in:
 * - src/main/resources/audio/sfx/
 */
public enum Sfx {
    HIT("/audio/sfx/hit");

    private final String resourceBase;

    Sfx(String resourceBase) {
        this.resourceBase = resourceBase;
    }

    public String resourceBase() {
        return resourceBase;
    }
}
