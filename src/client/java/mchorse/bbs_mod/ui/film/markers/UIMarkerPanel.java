package mchorse.bbs_mod.ui.film.markers;

import mchorse.bbs_mod.film.markers.FilmMarker;
import mchorse.bbs_mod.film.markers.FilmMarkers;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Colors;

public class UIMarkerPanel extends UIOverlayPanel
{
    private static final int ROW_H = 20;
    private static final int GAP = 4;

    private FilmMarkers markers;
    private FilmMarker current;

    private UITextbox labelField;
    private UITrackpad tickField;
    private UITrackpad durationField;
    private UIColor colorPicker;
    private UIButton deleteButton;

    public UIMarkerPanel()
    {
        super(UIKeys.TIMELINE_MARKER_TITLE);

        this.labelField = new UITextbox(256, (s) ->
        {
            if (this.current != null)
            {
                BaseValue.edit(this.current.label, (v) -> v.set(s));
            }
        });

        this.tickField = new UITrackpad((v) ->
        {
            if (this.current != null)
            {
                BaseValue.edit(this.current.tick, (t) -> t.set(v.intValue()));
            }
        });

        this.tickField.integer();

        this.durationField = new UITrackpad((v) ->
        {
            if (this.current != null)
            {
                BaseValue.edit(this.current.duration, (d) -> d.set(Math.max(0, v.intValue())));
            }
        });

        this.durationField.integer().limit(0);

        this.colorPicker = new UIColor((color) ->
        {
            if (this.current != null)
            {
                /* Store only RGB — alpha is applied at render time */
                BaseValue.edit(this.current.color, (c) -> c.set(color & 0xFFFFFF));

            }
        });

        this.deleteButton = new UIButton(UIKeys.GENERAL_REMOVE, (b) ->
        {
            if (this.current != null && this.markers != null)
            {
                BaseValue.edit(this.markers, (m) -> m.remove(this.current));
                this.close();
            }
        });

        this.deleteButton.color(Colors.NEGATIVE);

        UILabel labelKey    = UI.label(UIKeys.TIMELINE_MARKER_LABEL);
        UILabel tickKey     = UI.label(UIKeys.TIMELINE_MARKER_TICK);
        UILabel durationKey = UI.label(UIKeys.TIMELINE_MARKER_DURATION);
        UILabel colorKey    = UI.label(UIKeys.TIMELINE_MARKER_COLOR);

        int x = 6;
        int w = -12;
        int y = 6;

        labelKey.relative(this.content).xy(x, y).w(1F, w).h(ROW_H);
        y += 3 * GAP;
        this.labelField.relative(this.content).xy(x, y).w(1F, w).h(ROW_H);
        y += ROW_H + 2 * GAP;

        tickKey.relative(this.content).xy(x, y).w(1F, w).h(ROW_H);
        y += 3 * GAP;
        this.tickField.relative(this.content).xy(x, y).w(1F, w).h(ROW_H);
        y += ROW_H + 2 * GAP;

        durationKey.relative(this.content).xy(x, y).w(1F, w).h(ROW_H);
        y += 3 * GAP;
        this.durationField.relative(this.content).xy(x, y).w(1F, w).h(ROW_H);
        y += ROW_H + 2 * GAP;

        colorKey.relative(this.content).xy(x, y).w(1F, w).h(ROW_H);
        y += 3 * GAP;
        this.colorPicker.relative(this.content).xy(x, y).w(1F, w);
        y += ROW_H + 4* GAP;

        this.deleteButton.relative(this.content).xy(x, y).w(1F, w).h(ROW_H);

        this.content.add(
                labelKey, this.labelField,
                tickKey, this.tickField,
                durationKey, this.durationField,
                colorKey, this.colorPicker,
                this.deleteButton
        );

    }

    public void fill(FilmMarkers markers, FilmMarker marker)
    {
        this.markers = markers;
        this.current = marker;

        this.labelField.setText(marker.label.get());
        this.tickField.setValue(marker.tick.get());
        this.durationField.setValue(marker.duration.get());
        this.colorPicker.setColor(marker.color.get() | 0xFF000000);
    }
}