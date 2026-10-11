package mchorse.bbs_mod.ui.film.utils;

import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;

import java.util.function.Consumer;

public class UITracksideOverlayPanel extends UIMessageOverlayPanel
{
    /* Remembered between openings (except the ticks), so tweaking and regenerating is quick */
    private static TracksideGenerator.Settings last = new TracksideGenerator.Settings();

    private UITrackpad start = new UITrackpad().limit(0D, Double.MAX_VALUE, true);
    private UITrackpad length = new UITrackpad().limit(1D, Double.MAX_VALUE, true);
    private UITrackpad minCameras = new UITrackpad().limit(1D, Double.MAX_VALUE, true);
    private UITrackpad maxCameras = new UITrackpad().limit(1D, Double.MAX_VALUE, true);
    private UITrackpad minDistance = new UITrackpad().limit(0D);
    private UITrackpad maxDistance = new UITrackpad().limit(0D);
    private UITrackpad minHeight = new UITrackpad().limit(0D);
    private UITrackpad maxHeight = new UITrackpad().limit(0D);
    private UISliderTrackpad uniformity = new UISliderTrackpad().limit(0D, 1D);

    public UITracksideOverlayPanel(int start, int length, Consumer<TracksideGenerator.Settings> callback)
    {
        super(UIKeys.CAMERA_TRACKSIDE_TITLE, UIKeys.CAMERA_TRACKSIDE_DESCRIPTION);

        this.start.setValue(start);
        this.length.setValue(length);
        this.minCameras.setValue(last.minCameras);
        this.maxCameras.setValue(last.maxCameras);
        this.minDistance.setValue(last.minDistance);
        this.maxDistance.setValue(last.maxDistance);
        this.minHeight.setValue(last.minHeight);
        this.maxHeight.setValue(last.maxHeight);
        this.uniformity.setValue(last.uniformity);

        UIButton confirm = new UIButton(UIKeys.GENERAL_OK, (b) ->
        {
            this.close();
            callback.accept(this.read());
        });

        UIElement column = UI.column(
            UI.label(UIKeys.CAMERA_TRACKSIDE_TICKS), UI.row(this.start, this.length),
            UI.label(UIKeys.CAMERA_TRACKSIDE_CAMERAS), UI.row(this.minCameras, this.maxCameras),
            UI.label(UIKeys.CAMERA_TRACKSIDE_DISTANCE), UI.row(this.minDistance, this.maxDistance),
            UI.label(UIKeys.CAMERA_TRACKSIDE_HEIGHT), UI.row(this.minHeight, this.maxHeight),
            UI.label(UIKeys.CAMERA_TRACKSIDE_UNIFORMITY), this.uniformity,
            confirm
        );

        column.relative(this.content).x(6).y(1F, -6).w(1F, -12).anchor(0, 1F);

        this.content.add(column);

        this.bottom = column;
    }

    private TracksideGenerator.Settings read()
    {
        TracksideGenerator.Settings settings = new TracksideGenerator.Settings();

        settings.start = (int) this.start.getValue();
        settings.length = (int) this.length.getValue();
        settings.minCameras = (int) Math.min(this.minCameras.getValue(), this.maxCameras.getValue());
        settings.maxCameras = (int) Math.max(this.minCameras.getValue(), this.maxCameras.getValue());
        settings.minDistance = Math.min(this.minDistance.getValue(), this.maxDistance.getValue());
        settings.maxDistance = Math.max(this.minDistance.getValue(), this.maxDistance.getValue());
        settings.minHeight = Math.min(this.minHeight.getValue(), this.maxHeight.getValue());
        settings.maxHeight = Math.max(this.minHeight.getValue(), this.maxHeight.getValue());
        settings.uniformity = this.uniformity.getValue();

        last = settings;

        return settings;
    }
}
