package desia.gui;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import java.io.InputStream;

/**
 * Right-panel HUD overlay (chapter/act + HP bars).
 *
 * - Hub: chapter/act (top-left), player HP (top-right)
 * - Battle: + enemy HP (top-left under chapter/act)
 */
public final class FxHudOverlay {

    private final StackPane layer;

    private final Label chapterAct;
    private final ImageBar playerHp;
    private final ImageBar enemyHp;
    private final VBox leftTop;

    public FxHudOverlay(StackPane layer) {
        this.layer = layer;

        // Labels
        chapterAct = new Label(" ");
        chapterAct.setStyle(
                "-fx-text-fill: white;" +
                "-fx-font-size: 18px;" +
                "-fx-font-weight: bold;" +
                "-fx-padding: 6 10 6 10;" +
                "-fx-background-color: rgba(0,0,0,0.45);" +
                "-fx-background-radius: 10;"
        );

        // HUD never intercepts clicks.
        chapterAct.setMouseTransparent(true);
        // When hidden, don't reserve layout space.
        chapterAct.managedProperty().bind(chapterAct.visibleProperty());

        Image hpFrame = loadImage("/ui/hud/hp_frame.png");
        Image hpFill = loadImage("/ui/hud/hp_fill.png");

        playerHp = new ImageBar(hpFrame, hpFill);
        enemyHp = new ImageBar(hpFrame, hpFill);

        // Size scales with the right panel.
        playerHp.prefWidthProperty().bind(layer.widthProperty().multiply(0.48));
        playerHp.prefHeightProperty().bind(layer.heightProperty().multiply(0.06));
        enemyHp.prefWidthProperty().bind(layer.widthProperty().multiply(0.48));
        enemyHp.prefHeightProperty().bind(layer.heightProperty().multiply(0.06));


        // Prevent StackPane from stretching bars to fill the entire right panel.
        playerHp.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        playerHp.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        enemyHp.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        enemyHp.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        // When hidden, don't reserve layout space.
        playerHp.managedProperty().bind(playerHp.visibleProperty());
        enemyHp.managedProperty().bind(enemyHp.visibleProperty());

        // HUD never intercepts clicks.
        playerHp.setMouseTransparent(true);
        enemyHp.setMouseTransparent(true);


        leftTop = new VBox(8, chapterAct, enemyHp);
        leftTop.setAlignment(Pos.TOP_LEFT);

        leftTop.setPickOnBounds(false);
        leftTop.setMouseTransparent(true);
        leftTop.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        leftTop.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        layer.getChildren().addAll(leftTop, playerHp);

        StackPane.setAlignment(leftTop, Pos.TOP_LEFT);
        StackPane.setMargin(leftTop, new Insets(10, 0, 0, 10));

        StackPane.setAlignment(playerHp, Pos.TOP_RIGHT);
        StackPane.setMargin(playerHp, new Insets(10, 10, 0, 0));

        clear();
    }

    public void clear() {
        chapterAct.setVisible(false);
        playerHp.setVisible(false);
        enemyHp.setVisible(false);
    }

    public void showHub(int chapter, int act,
                        String playerName, int playerLevel,
                        double hp, double maxHp) {
        chapterAct.setText("CHAPTER " + chapter + "\nACT " + act + "/12");
        chapterAct.setVisible(true);

        playerHp.setVisible(true);
        enemyHp.setVisible(false);

        String name = (playerName == null || playerName.isBlank()) ? "PLAYER" : playerName;
        playerHp.setValue(name + "  Lv." + playerLevel, hp, maxHp);
    }

    public void showBattle(int chapter, int act,
                           String playerName, int playerLevel,
                           double playerHpVal, double playerMaxHp,
                           String enemyName, int enemyLevel,
                           double enemyHpVal, double enemyMaxHp) {
        chapterAct.setText("CHAPTER " + chapter + "\nACT " + act + "/12");
        chapterAct.setVisible(true);

        playerHp.setVisible(true);
        enemyHp.setVisible(true);

        String pName = (playerName == null || playerName.isBlank()) ? "PLAYER" : playerName;
        String eName = (enemyName == null || enemyName.isBlank()) ? "ENEMY" : enemyName;

        playerHp.setValue(pName + "  Lv." + playerLevel, playerHpVal, playerMaxHp);
        enemyHp.setValue(eName + "  Lv." + enemyLevel, enemyHpVal, enemyMaxHp);
    }

    private static Image loadImage(String resourcePath) {
        if (resourcePath == null) return null;
        try (InputStream is = FxHudOverlay.class.getResourceAsStream(resourcePath)) {
            if (is == null) return null;
            return new Image(is);
        } catch (Exception e) {
            return null;
        }
    }

    /** Image-based bar that clips its fill by percentage. */
    static final class ImageBar extends Pane {

        private final ImageView frame;
        private final ImageView fill;
        private final Rectangle clip;
        private final Label text;
        private final DoubleProperty progress = new SimpleDoubleProperty(1.0);

        ImageBar(Image frameImg, Image fillImg) {
            frame = new ImageView(frameImg);
            frame.setPreserveRatio(false);
            frame.setSmooth(true);

            fill = new ImageView(fillImg);
            fill.setPreserveRatio(false);
            fill.setSmooth(true);

            clip = new Rectangle();
            fill.setClip(clip);

            text = new Label();
            text.setAlignment(Pos.CENTER);
            text.setStyle(
                    "-fx-text-fill: white;" +
                    "-fx-font-size: 13px;" +
                    "-fx-font-weight: bold;" +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,1), 6, 0.33, 1, 1);"
            );

            getChildren().addAll(fill, frame, text);

            widthProperty().addListener((obs, a, b) -> requestLayout());
            heightProperty().addListener((obs, a, b) -> requestLayout());
            progress.addListener((obs, a, b) -> updateClip());

            setPickOnBounds(false);
        }

        void setValue(String title, double cur, double max) {
            double p = 0.0;
            if (max > 0) p = cur / max;
            if (Double.isNaN(p) || Double.isInfinite(p)) p = 0.0;
            p = Math.max(0.0, Math.min(1.0, p));
            progress.set(p);

            long c = Math.round(cur);
            long m = Math.round(max);
            String t = (title == null ? "" : title);
            text.setText(t + "  " + c + "/" + m);
        }

        @Override
        protected void layoutChildren() {
            double w = getWidth();
            double h = getHeight();
            if (w <= 0 || h <= 0) return;

            // Frame uses full area.
            frame.setFitWidth(w);
            frame.setFitHeight(h);
            frame.relocate(0, 0);

            // Fill is thinner and inset.
            double insetX = w * 0.035;
            double fillW = Math.max(0, w - insetX * 2);
            double fillH = h * 0.50;
            double insetY = (h - fillH) * 0.5;

            fill.setFitWidth(fillW);
            fill.setFitHeight(fillH);
            fill.relocate(insetX, insetY);

            clip.setX(0);
            clip.setY(0);
            clip.setHeight(fillH);
            updateClip();

            text.resizeRelocate(0, 0, w, h);
        }

        private void updateClip() {
            double fillW = fill.getFitWidth();
            clip.setWidth(Math.max(0, fillW * progress.get()));
        }
    }
}
