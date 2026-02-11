package desia.gui;

import desia.Game;
import desia.audio.AudioService;
import desia.io.Io;
import desia.ui.UiText;
import java.util.concurrent.CountDownLatch;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.text.Font;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.Node;
import javafx.stage.Stage;
import java.io.PrintStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * JavaFX wrapper that runs the game in a window.
 *
 * Strategy:
 * - Redirect System.out/System.err to a TextArea.
 * - Provide Io implementation (FxIo) that renders inputs as on-screen buttons / text field.
 * - Run the game loop on a background thread to keep the UI responsive.
 */
public final class FxApp extends Application {

    private TextArea output;
    private Label promptLabel;
    private FlowPane choiceButtons;
    private TextField textField;
    private Button textOk;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        // Audio is optional: if resources are missing, it silently no-ops.
        AudioService.get().init();

        output = new TextArea();
        output.setEditable(false);
        output.setWrapText(true);
        // Make UiText.clear() actually clear the log pane (instead of newline spam).
        UiText.setClearHandler(() -> {
            if (Platform.isFxApplicationThread()) {
                output.clear();
                return;
            }
            CountDownLatch latch = new CountDownLatch(1);
            Platform.runLater(() -> {
                output.clear();
                latch.countDown();
            });
            try {
                latch.await(); // clear가 화면에 반영될 때까지 동기 대기
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });


// parchment-friendly + 완전 투명(부모 배경 노출)
        output.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-control-inner-background: transparent;" +
                        "-fx-text-fill: #111111;" +
                        "-fx-highlight-fill: rgba(0,0,0,0.18);" +
                        "-fx-highlight-text-fill: #111111;" +
                        "-fx-font-size: 15px;"
        );

// TextArea 내부 viewport(.content)까지 투명 처리 (이게 하늘색 제거의 핵심)
        Platform.runLater(() -> {
            Node content = output.lookup(".content");
            if (content != null) {
                content.setStyle("-fx-background-color: transparent;");
            }
        });


        promptLabel = new Label("");

        choiceButtons = new FlowPane(Orientation.HORIZONTAL);
        choiceButtons.setHgap(8);
        choiceButtons.setVgap(8);
        choiceButtons.setPrefWrapLength(860);

        textField = new TextField();
        textField.setPromptText("텍스트 입력");
        textOk = new Button("확인");
        HBox textRow = new HBox(8, textField, textOk);

        // default hidden; shown only for text input
        textField.setVisible(false);
        textOk.setVisible(false);

        ScrollPane choiceScroll = new ScrollPane(choiceButtons);
        choiceScroll.setFitToWidth(true);
        choiceScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        choiceScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        choiceScroll.setPrefViewportHeight(160);
        choiceScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        VBox bottom = new VBox(8, promptLabel, choiceScroll, textRow);
        bottom.setPadding(new Insets(10));
        // LEFT bottom background (fill, no margins) - same texture as the log pane.
        bottom.setStyle(
                "-fx-background-image: url('/ui/left_panel_bg.jpg');" +
                "-fx-background-repeat: no-repeat;" +
                "-fx-background-position: center center;" +
                "-fx-background-size: cover;" +
                "-fx-border-color: rgba(0,0,0,0.65);" +
                "-fx-border-width: 2 0 0 0;"
        );

        // LEFT: log pane (paperboard texture background + output)
        StackPane logPane = new StackPane(output);
        logPane.setPadding(new Insets(12));
        logPane.setMinSize(0, 0);
        logPane.setStyle(
                "-fx-background-image: url('/ui/left_panel_bg.jpg');" +
                "-fx-background-repeat: no-repeat;" +
                "-fx-background-position: center center;" +
                "-fx-background-size: cover;"
        );

        BorderPane overlay = new BorderPane();
        overlay.setCenter(logPane);
        overlay.setBottom(bottom);
        // IMPORTANT: allow SplitPane to shrink this side if needed.
        // (We will also prevent the right side from claiming a huge min width.)
        overlay.setMinSize(0, 0);

        // RIGHT: image area (fixed aspect ratio 10:7) + title overlay
        // Start screen rule: show ONLY the title (no chapter background yet).
        ImageView battleBgView = new ImageView();
        battleBgView.setImage(null);
        battleBgView.setPreserveRatio(false);

        ImageView titleView = new ImageView();
        titleView.setImage(loadFirstAvailable(
                "/ui/title.png",
                "/ui/title.jpg"
        ));
        titleView.setPreserveRatio(true);
        titleView.setSmooth(true);
        titleView.setVisible(true);

        // placeholder layer for battle UI (enemy/player images, hp bars, status icons)
        StackPane battleUiLayer = new StackPane();
        battleUiLayer.setPickOnBounds(false);
        battleUiLayer.setMouseTransparent(true);

        // Inner pane keeps a fixed 10:7 (height:width) ratio; outer pane letterboxes.
        AspectRatioPane ratioPane = new AspectRatioPane(10, 7);
        ratioPane.getChildren().addAll(battleBgView, battleUiLayer, titleView);

        StackPane battlePane = new StackPane(ratioPane);
        battlePane.setStyle("-fx-background-color: #000000;");
        // CRITICAL: SplitPane uses each item's min/pref sizes to decide divider layout.
        // StackPane computes its min width from its child ImageView (i.e., the *original image size*)
        // unless we force the min size down.
        battlePane.setMinSize(0, 0);
        battlePane.setPrefWidth(Region.USE_COMPUTED_SIZE);

        // Keep the right image area at a fixed aspect ratio (height:width = 10:7)
        // while the outer battlePane fills its SplitPane cell (letterboxing/pillarboxing).
        DoubleBinding targetW = Bindings.createDoubleBinding(() -> {
            double w = battlePane.getWidth();
            double h = battlePane.getHeight();
            if (w <= 0 || h <= 0) return 0.0;
            double wFromH = h * 7.0 / 10.0;
            return Math.min(w, wFromH);
        }, battlePane.widthProperty(), battlePane.heightProperty());

        DoubleBinding targetH = Bindings.createDoubleBinding(
                () -> targetW.get() * (10.0 / 7.0),
                targetW
        );

        ratioPane.prefWidthProperty().bind(targetW);
        ratioPane.maxWidthProperty().bind(targetW);
        ratioPane.prefHeightProperty().bind(targetH);
        ratioPane.maxHeightProperty().bind(targetH);

        SplitPane split = new SplitPane();
        split.getItems().addAll(overlay, battlePane);
        // Set divider after first layout pass so min sizes are already applied.
        Platform.runLater(() -> split.setDividerPositions(0.35));
        split.setStyle("-fx-background-color: transparent;");

        BorderPane root = new BorderPane(split);

        // Load custom UI font (optional). Place your .ttf/.otf at src/main/resources/fonts/
        String fontFamily = loadAppFontFamily();

        Scene scene = new Scene(root, 900, 700);
        if (fontFamily != null && !fontFamily.isBlank()) {
            // Apply globally so newly created nodes (buttons, labels) also use it.
            root.setStyle(appendStyle(root.getStyle(), "-fx-font-family: '" + fontFamily + "'; -fx-font-size: 14px;"));
            output.setStyle(appendStyle(output.getStyle(), "-fx-font-family: '" + fontFamily + "'; -fx-font-size: 14px;"));
            promptLabel.setStyle(appendStyle(promptLabel.getStyle(), "-fx-font-family: '" + fontFamily + "';"));
            textField.setStyle(appendStyle(textField.getStyle(), "-fx-font-family: '" + fontFamily + "';"));
            textOk.setStyle(appendStyle(textOk.getStyle(), "-fx-font-family: '" + fontFamily + "';"));
        }

        battleBgView.fitWidthProperty().bind(ratioPane.widthProperty());
        battleBgView.fitHeightProperty().bind(ratioPane.heightProperty());
        // Make title as large as possible while keeping the whole image visible.
        titleView.fitWidthProperty().bind(ratioPane.widthProperty());
        titleView.fitHeightProperty().bind(ratioPane.heightProperty());
        stage.setTitle("Desia (JavaFX)");
        stage.setScene(scene);
        stage.show();

        // Default: idle BGM (chapter 1). If /audio/bgm/idle_ch1.* exists, it will be used.
        AudioService.get().playIdleForChapter(1);
        // Ensure the log TextArea does not paint its own opaque background.
        // (Modena skin paints background on internal nodes like .content/.viewport.)
        Platform.runLater(() -> {
            Node content = output.lookup(".content");
            if (content != null) {
                content.setStyle("-fx-background-color: transparent;");
            }
            Node viewport = output.lookup(".viewport");
            if (viewport != null) {
                viewport.setStyle("-fx-background-color: transparent;");
            }
            Node scrollPane = output.lookup(".scroll-pane");
            if (scrollPane != null) {
                scrollPane.setStyle("-fx-background-color: transparent;");
            }
        });


        // Wire stdout/stderr -> TextArea
        PrintStream ps = new PrintStream(new FxTextAreaOutputStream(output), true, StandardCharsets.UTF_8);
        System.setOut(ps);
        System.setErr(ps);

        // Input bridge (buttons / text)
        FxInputView inputView = new FxInputView(promptLabel, choiceButtons, textField, textOk, battleBgView, titleView, battleUiLayer);
        Io io = new FxIo(inputView);

        // Start the game loop on a background thread
        Thread gameThread = new Thread(() -> {
            try {
                new Game(io).start();
            } catch (Throwable t) {
                t.printStackTrace();
            } finally {
                // If the game loop returns (e.g., user selected "게임 종료"),
                // close the JavaFX application as well.
                Platform.runLater(() -> {
                    output.appendText("\n\n[게임 종료]\n");

                    // disable inputs
                    choiceButtons.getChildren().clear();
                    textField.setDisable(true);
                    textOk.setDisable(true);

                    // shutdown services and close window
                    inputView.shutdown();
                    AudioService.get().shutdown();

                    // Close JavaFX runtime. (If other non-daemon threads exist, this still requests exit.)
                    Platform.exit();
                });
            }
        }, "desia-game-thread");
        gameThread.setDaemon(true);
        gameThread.start();

        stage.setOnCloseRequest(e -> {
            inputView.shutdown();
            AudioService.get().shutdown();
            Platform.exit();
        });
    }


    private static String appendStyle(String base, String extra) {
        String b = (base == null) ? "" : base.trim();
        if (!b.isEmpty() && !b.endsWith(";")) b += ";";
        String e = (extra == null) ? "" : extra.trim();
        if (!e.isEmpty() && !e.endsWith(";")) e += ";";
        return b + e;
    }

    private String loadAppFontFamily() {
        // Change this path if you use a different font file name.
        try (InputStream is = getClass().getResourceAsStream("/fonts/NanumMyeongjo.otf")) {
            if (is == null) return null;
            Font f = Font.loadFont(is, 16);
            return (f == null) ? null : f.getFamily();
        } catch (Exception e) {
            return null;
        }
    }

    private Image loadFirstAvailable(String... resourcePaths) {
        if (resourcePaths == null) return null;
        for (String p : resourcePaths) {
            if (p == null || p.isBlank()) continue;
            try (InputStream is = getClass().getResourceAsStream(p)) {
                if (is == null) continue;
                return new Image(is);
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
