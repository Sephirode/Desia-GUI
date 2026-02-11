package desia.gui;

import desia.audio.AudioService;
import desia.io.Io;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.io.InputStream;

/** JavaFX implementation: all inputs are driven by on-screen controls (buttons / text box). */
public final class FxIo implements Io {

    private final FxInputView view;
    private final Map<String, String> chapterBackgrounds;

    public FxIo(FxInputView view) {
        this.view = view;
        this.chapterBackgrounds = loadChapterBackgrounds();
    }

    @Override
    public void onChapterChanged(int chapter) {
        if (chapter <= 0) return;

        // Chapter-specific idle BGM (if present)
        AudioService.get().playIdleForChapter(chapter);

        if (chapterBackgrounds == null || chapterBackgrounds.isEmpty()) return;
        String rel = chapterBackgrounds.get(String.valueOf(chapter));
        if (rel == null || rel.isBlank()) return;
        // Mapping stores path relative to /ui/
        try (InputStream is = FxIo.class.getResourceAsStream("/ui/" + rel)) {
            if (is == null) return;
            view.setBackground(new javafx.scene.image.Image(is));
            // Once the campaign starts (chapter known), hide the title overlay.
            view.showTitle(false);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onMainMenu() {
        // restore title overlay and clear HUD when returning to main menu
        view.showTitle(true);
        view.setBackground(null);
        view.showMainMenuHud();
    }

    @Override
    public void onHubHud(int chapter, int act, String playerName, int playerLevel, double hp, double maxHp) {
        view.showHubHud(chapter, act, playerName, playerLevel, hp, maxHp);
    }

    @Override
    public void onBattleHud(int chapter, int act, String playerName, int playerLevel, double playerHp, double playerMaxHp,
                            String enemyName, int enemyLevel, double enemyHp, double enemyMaxHp) {
        view.showBattleHud(chapter, act, playerName, playerLevel, playerHp, playerMaxHp, enemyName, enemyLevel, enemyHp, enemyMaxHp);
    }

    @Override
    public int readInt(String prompt, int userChoices) {
        // numeric buttons 1..userChoices
        int v = view.requestChoiceFromLabels(prompt, buildNumericLabels(1, userChoices));
        if (v < 1 || v > userChoices) return 1; // safe fallback
        return v;
    }

    @Override
    public int readIntAllowZero(String prompt, int max) {
        int v = view.requestChoiceFromLabels(prompt, buildNumericLabels(0, max));
        if (v < 0 || v > max) return 0;
        return v;
    }

    @Override
    public int choose(String prompt, List<String> options) {
        if (options == null || options.isEmpty()) return 1;
        java.util.ArrayList<ChoiceOption> opts = new java.util.ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            String label = options.get(i);
            opts.add(new ChoiceOption(i + 1, label));
        }
        int v = view.requestChoice(prompt, opts);
        if (v < 1 || v > options.size()) return 1;
        return v;
    }

    @Override
    public int chooseAllowCancel(String prompt, List<String> options, String cancelLabel) {
        java.util.ArrayList<ChoiceOption> opts = new java.util.ArrayList<>();
        if (options != null) {
            for (int i = 0; i < options.size(); i++) {
                opts.add(new ChoiceOption(i + 1, options.get(i)));
            }
        }
        opts.add(new ChoiceOption(0, cancelLabel == null ? "뒤로" : cancelLabel));
        int v = view.requestChoice(prompt, opts);
        if (v == 0) return 0;
        if (options == null) return 0;
        if (v < 1 || v > options.size()) return 0;
        return v;
    }

    @Override
    public String readNonEmptyString(String prompt, int maxLen) {
        while (true) {
            String s = view.requestText(prompt);
            if (s == null) {
                System.out.println("문자열을 입력해주세요.");
                continue;
            }
            s = s.strip();
            if (s.isEmpty()) {
                System.out.println("공백만 입력할 수 없습니다.");
                continue;
            }
            if (s.length() > maxLen) {
                System.out.println(maxLen + "자 이하로만 입력 가능합니다.");
                continue;
            }
            return s;
        }
    }

    @Override
    public void anythingToContinue() {
        view.requestContinue("계속하려면 [계속] 버튼을 누르세요.");
    }

    @Override
    public boolean confirmExit() {
        return confirm("게임을 종료하시겠습니까?", "예", "아니오");
    }

    private static List<String> buildNumericLabels(int from, int to) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (int i = from; i <= to; i++) out.add(String.valueOf(i));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> loadChapterBackgrounds() {
        try (InputStream is = FxIo.class.getResourceAsStream("/ui/chapter_backgrounds.json")) {
            if (is == null) return Map.of();
            ObjectMapper om = new ObjectMapper();
            return om.readValue(is, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
