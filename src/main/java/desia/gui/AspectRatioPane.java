package desia.gui;

import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.layout.Pane;

/**
 * A container that keeps its children laid out inside a fixed aspect-ratio box,
 * centered within the available space (letterboxed/pillarboxed as needed).
 *
 * Ratio is expressed as: height : width
 */
public final class AspectRatioPane extends Pane {

    private final double heightRatio;
    private final double widthRatio;

    public AspectRatioPane(double heightRatio, double widthRatio) {
        this.heightRatio = heightRatio;
        this.widthRatio = widthRatio;
        setMinSize(0, 0);
    }

    @Override
    protected void layoutChildren() {
        final double w = getWidth();
        final double h = getHeight();
        if (w <= 0 || h <= 0) return;

        // target box size inside (w,h) with fixed aspect (height:width)
        double targetW = w;
        double targetH = w * (heightRatio / widthRatio);

        if (targetH > h) {
            targetH = h;
            targetW = h * (widthRatio / heightRatio);
        }

        final double x = (w - targetW) / 2.0;
        final double y = (h - targetH) / 2.0;

        for (Node child : getChildren()) {
            layoutInArea(child, x, y, targetW, targetH, 0, HPos.CENTER, VPos.CENTER);
        }
    }

    // SplitPane sizing friendliness
    @Override
    protected double computeMinWidth(double height) { return 0; }

    @Override
    protected double computeMinHeight(double width) { return 0; }
}
