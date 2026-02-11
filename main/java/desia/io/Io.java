package desia.io;

import java.util.List;

/**
 * Input/Output abstraction.
 *
 * Console version uses Scanner; JavaFX version uses button/text UI.
 */
public interface Io {

    /** Read an int in range [1..userChoices]. */
    int readInt(String prompt, int userChoices);

    /** Read an int in range [0..max]. */
    int readIntAllowZero(String prompt, int max);

    /**
     * Labeled-choice menu.
     *
     * @return 1..options.size()
     */
    int choose(String prompt, List<String> options);

    /**
     * Labeled-choice menu with a cancel option.
     *
     * @return 0 if cancel, else 1..options.size()
     */
    int chooseAllowCancel(String prompt, List<String> options, String cancelLabel);

    /** Convenience confirmation. */
    default boolean confirm(String prompt, String yesLabel, String noLabel) {
        int v = choose(prompt, List.of(yesLabel, noLabel));
        return v == 1;
    }

    /** Read a non-empty string (after trimming), max length 제한. */
    String readNonEmptyString(String prompt, int maxLen);

    /** Wait until the user triggers a continue action. */
    void anythingToContinue();

    /** Ask exit confirmation. true=exit */
    boolean confirmExit();

    /**
     * Notify UI that the current chapter has changed (or should be (re)rendered).
     * Console implementation can ignore this.
     */
    default void onChapterChanged(int chapter) {
        // no-op
    }

    /**
     * Notify UI that the main menu is being shown.
     * (JavaFX can re-show the title overlay / clear HUD.)
     */
    default void onMainMenu() {
        // no-op
    }

    /**
     * Non-battle HUD update.
     *
     * @param chapter current chapter
     * @param act current act (1..12)
     * @param playerName player nickname
     * @param playerLevel player level
     * @param hp current hp
     * @param maxHp max hp
     */
    default void onHubHud(int chapter, int act,
                          String playerName, int playerLevel,
                          double hp, double maxHp) {
        // no-op
    }

    /**
     * Battle HUD update.
     */
    default void onBattleHud(int chapter, int act,
                             String playerName, int playerLevel,
                             double playerHp, double playerMaxHp,
                             String enemyName, int enemyLevel,
                             double enemyHp, double enemyMaxHp) {
        // no-op
    }
}
