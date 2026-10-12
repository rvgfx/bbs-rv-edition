package mchorse.bbs_mod.ui.film.utils;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

public class UICameraRigOverlayPanel extends UIMessageOverlayPanel
{
    /* Remembered per rig between openings (except the ticks), so tweaking and regenerating is quick */
    private static final Map<CameraRigGenerator.Rig, CameraRigGenerator.Settings> LAST = new EnumMap<>(CameraRigGenerator.Rig.class);

    private final CameraRigGenerator.Rig rig;

    private UITrackpad start = new UITrackpad().limit(0D, Double.MAX_VALUE, true);
    private UITrackpad length = new UITrackpad().limit(1D, Double.MAX_VALUE, true);
    private UITrackpad distance = new UITrackpad().limit(0D);
    private UITrackpad heightStart = new UITrackpad();
    private UITrackpad heightEnd = new UITrackpad();
    private UITrackpad degrees = new UITrackpad();

    public UICameraRigOverlayPanel(CameraRigGenerator.Rig rig, IKey title, int start, int length, Consumer<CameraRigGenerator.Settings> callback)
    {
        super(title, UIKeys.CAMERA_RIG_DESCRIPTION);

        CameraRigGenerator.Settings last = LAST.getOrDefault(rig, rig.defaults);

        this.rig = rig;
        this.start.setValue(start);
        this.length.setValue(length);
        this.distance.setValue(last.distance);
        this.heightStart.setValue(last.heightStart);
        this.heightEnd.setValue(last.heightEnd);
        this.degrees.setValue(last.degrees);

        UIButton confirm = new UIButton(UIKeys.GENERAL_OK, (b) ->
        {
            this.close();
            callback.accept(this.read());
        });

        UIElement column = UI.column(
            UI.label(UIKeys.CAMERA_TRACKSIDE_TICKS), UI.row(this.start, this.length),
            UI.label(UIKeys.CAMERA_RIG_DISTANCE), this.distance,
            UI.label(UIKeys.CAMERA_RIG_HEIGHT), UI.row(this.heightStart, this.heightEnd)
        );

        if (rig == CameraRigGenerator.Rig.ORBIT)
        {
            column.add(UI.label(UIKeys.CAMERA_RIG_ROTATION), this.degrees);
        }

        column.add(confirm);
        column.relative(this.content).x(6).y(1F, -6).w(1F, -12).anchor(0, 1F);

        this.content.add(column);

        this.bottom = column;
    }

    private CameraRigGenerator.Settings read()
    {
        CameraRigGenerator.Settings settings = new CameraRigGenerator.Settings();

        settings.rig = this.rig;
        settings.start = (int) this.start.getValue();
        settings.length = (int) this.length.getValue();
        settings.distance = this.distance.getValue();
        settings.heightStart = this.heightStart.getValue();
        settings.heightEnd = this.heightEnd.getValue();
        settings.degrees = this.degrees.getValue();

        LAST.put(this.rig, settings);

        return settings;
    }
}
