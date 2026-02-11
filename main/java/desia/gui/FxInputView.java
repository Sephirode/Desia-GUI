package desia.gui;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.FlowPane;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * UI-side input renderer.
 *
 * Game thread blocks on returned futures; UI completes them on button click.
 */
public final class FxInputView {

    private final Label promptLabel;
    private final FlowPane buttonPane;
    private final TextField textField;
    private final Button textOk;
    private final ImageView backgroundView;
    private final ImageView titleView;
    private final FxHudOverlay hud;

    private final AtomicReference<CompletableFuture<?>> pending = new AtomicReference<>(null);

    public FxInputView(Label promptLabel,
                       FlowPane buttonPane,
                       TextField textField,
                       Button textOk,
                       ImageView backgroundView,
                       ImageView titleView,
                       StackPane hudLayer) {
        this.promptLabel = Objects.requireNonNull(promptLabel);
        this.buttonPane = Objects.requireNonNull(buttonPane);
        this.textField = Objects.requireNonNull(textField);
        this.textOk = Objects.requireNonNull(textOk);
        this.backgroundView = backgroundView;
        this.titleView = titleView;
        this.hud = (hudLayer == null) ? null : new FxHudOverlay(hudLayer);
    }

    /** Swap background image (safe to call from game thread). */
    public void setBackground(Image img) {
        if (backgroundView == null) return;
        Platform.runLater(() -> backgroundView.setImage(img));
    }

    /** Show/hide title overlay (safe to call from game thread). */
    public void showTitle(boolean visible) {
        if (titleView == null) return;
        Platform.runLater(() -> titleView.setVisible(visible));
    }

    /** Clear HUD and show title-only mode (main menu). */
    public void showMainMenuHud() {
        if (hud == null) return;
        Platform.runLater(hud::clear);
    }

    /** Non-battle HUD update. */
    public void showHubHud(int chapter, int act,
                           String playerName, int playerLevel,
                           double hp, double maxHp) {
        if (hud == null) return;
        Platform.runLater(() -> hud.showHub(chapter, act, playerName, playerLevel, hp, maxHp));
    }

    /** Battle HUD update. */
    public void showBattleHud(int chapter, int act,
                              String playerName, int playerLevel,
                              double playerHp, double playerMaxHp,
                              String enemyName, int enemyLevel,
                              double enemyHp, double enemyMaxHp) {
        if (hud == null) return;
        Platform.runLater(() -> hud.showBattle(
                chapter, act,
                playerName, playerLevel,
                playerHp, playerMaxHp,
                enemyName, enemyLevel,
                enemyHp, enemyMaxHp
        ));
    }

    /**
     * Show buttons for the provided options.
     * Returns the option's explicit value.
     */
    public int requestChoice(String prompt, List<ChoiceOption> options) {
        CompletableFuture<Integer> fut = new CompletableFuture<>();
        if (!pending.compareAndSet(null, fut)) {
            throw new IllegalStateException("Another input request is already pending.");
        }

        Platform.runLater(() -> {
            promptLabel.setText(prompt == null ? "" : prompt);

            // hide text input
            textField.setVisible(false);
            textOk.setVisible(false);

            buttonPane.getChildren().clear();
            for (ChoiceOption opt : options) {
                Button b = new Button(opt.label());
                b.setOnAction(e -> {
                    if (!fut.isDone()) {
                        fut.complete(opt.value());
                    }
                    clearPending(fut);
                });
                buttonPane.getChildren().add(b);
            }
        });

        try {
            return fut.get();
        } catch (Exception e) {
            clearPending(fut);
            return 0;
        }
    }

    /**
     * Legacy helper: treat each label as an integer-returning option.
     * - If label starts with digits, that number is returned.
     * - Otherwise returns its 1-based position.
     */
    public int requestChoiceFromLabels(String prompt, List<String> labels) {
        List<ChoiceOption> opts = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            String lab = labels.get(i);
            int v = parseLeadingInt(lab);
            if (v == 0) v = i + 1;
            opts.add(new ChoiceOption(v, lab));
        }
        return requestChoice(prompt, opts);
    }

    public String requestText(String prompt) {
        CompletableFuture<String> fut = new CompletableFuture<>();
        if (!pending.compareAndSet(null, fut)) {
            throw new IllegalStateException("Another input request is already pending.");
        }

        Platform.runLater(() -> {
            promptLabel.setText(prompt == null ? "" : prompt);
            buttonPane.getChildren().clear();

            textField.clear();
            textField.setVisible(true);
            textOk.setVisible(true);

            Runnable submit = () -> {
                if (!fut.isDone()) fut.complete(textField.getText());
                clearPending(fut);
            };

            textOk.setOnAction(e -> submit.run());
            textField.setOnAction(e -> submit.run());
            textField.requestFocus();
        });

        try {
            return fut.get();
        } catch (Exception e) {
            clearPending(fut);
            return "";
        }
    }

    public void requestContinue(String prompt) {
        CompletableFuture<Void> fut = new CompletableFuture<>();
        if (!pending.compareAndSet(null, fut)) {
            throw new IllegalStateException("Another input request is already pending.");
        }

        Platform.runLater(() -> {
            promptLabel.setText(prompt == null ? "" : prompt);
            textField.setVisible(false);
            textOk.setVisible(false);
            buttonPane.getChildren().clear();
            Button b = new Button("계속");
            b.setOnAction(e -> {
                if (!fut.isDone()) fut.complete(null);
                clearPending(fut);
            });
            buttonPane.getChildren().add(b);
        });

        try {
            fut.get();
        } catch (Exception ignored) {
            clearPending(fut);
        }
    }

    public void shutdown() {
        CompletableFuture<?> fut = pending.getAndSet(null);
        if (fut != null && !fut.isDone()) {
            // unblock game thread with safe defaults
            fut.complete(null);
        }
    }

    private void clearPending(CompletableFuture<?> fut) {
        pending.compareAndSet(fut, null);
        Platform.runLater(() -> {
            // IMPORTANT:
            // When the user clicks a button, we complete the current future and schedule
            // this UI cleanup. But the game thread may immediately request the next input
            // (e.g., "게임 종료" -> "예/아니오").
            // If this cleanup runs after the next prompt has rendered, it would wipe out
            // the newly-rendered buttons.
            // So only clear the UI if there is no new pending request.
            if (pending.get() != null) return;

            // keep UI quiet between prompts
            buttonPane.getChildren().clear();
            textField.setVisible(false);
            textOk.setVisible(false);
        });
    }

    private static int parseLeadingInt(String s) {
        if (s == null) return 0;
        // Accept formats like "1", "1)", "1. ...", "1(예)"...
        String t = s.trim();
        int i = 0;
        while (i < t.length() && Character.isDigit(t.charAt(i))) i++;
        if (i == 0) return 0;
        try {
            return Integer.parseInt(t.substring(0, i));
        } catch (Exception e) {
            return 0;
        }
    }

    // No fallback helper; callers should handle 0 as cancel/error when needed.
}
