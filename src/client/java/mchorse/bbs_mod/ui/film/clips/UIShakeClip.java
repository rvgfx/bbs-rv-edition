package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.modifiers.ShakeClip;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.widgets.UIBitToggle;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.Direction;

public class UIShakeClip extends UIClip<ShakeClip>
{
    public UITrackpad shake;
    public UITrackpad shakeAmount;
    public UIToggle noise;
    public UIToggle perAxis;
    public UIToggle local;
    public UITrackpad seed;
    public UIBitToggle active;
    public UIElement legacy;
    public UIElement axes;
    public UIElement modes;

    public UIShakeClip(ShakeClip modifier, IUIClipsDelegate editor)
    {
        super(modifier, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.shake = this.trackpad(this.clip.shake);
        this.shake.tooltip(UIKeys.CAMERA_PANELS_SHAKE, Direction.BOTTOM);

        this.shakeAmount = this.trackpad(this.clip.shakeAmount);
        this.shakeAmount.tooltip(UIKeys.CAMERA_PANELS_SHAKE_AMOUNT, Direction.BOTTOM);

        this.noise = this.toggle(UIKeys.CAMERA_PANELS_SHAKE_NOISE, this.clip.noise);
        this.noise.tooltip(UIKeys.CAMERA_PANELS_SHAKE_NOISE_TOOLTIP, Direction.BOTTOM);

        this.perAxis = this.toggle(UIKeys.CAMERA_PANELS_SHAKE_PER_AXIS, this.clip.perAxis);
        this.perAxis.tooltip(UIKeys.CAMERA_PANELS_SHAKE_PER_AXIS_TOOLTIP, Direction.BOTTOM);

        this.local = this.toggle(UIKeys.CAMERA_PANELS_SHAKE_LOCAL, this.clip.local);
        this.local.tooltip(UIKeys.CAMERA_PANELS_SHAKE_LOCAL_TOOLTIP, Direction.BOTTOM);

        this.seed = this.trackpad(this.clip.seed);
        this.seed.tooltip(UIKeys.CAMERA_PANELS_SHAKE_SEED, Direction.BOTTOM);

        this.legacy = UI.row(UIConstants.MARGIN, 0, 20, this.shake, this.shakeAmount);
        this.axes = UI.column(UI.row(UIConstants.MARGIN, 0, 20,
            UI.label(IKey.EMPTY, 20),
            UI.label(UIKeys.CAMERA_PANELS_SHAKE_AMPLITUDE, 20),
            UI.label(UIKeys.CAMERA_PANELS_SHAKE_FREQUENCY, 20)
        ));

        for (int i = 0; i < ShakeClip.AXES.length; i++)
        {
            UITrackpad amplitude = this.trackpad(this.clip.amplitudes[i]);
            UITrackpad frequency = this.trackpad(this.clip.frequencies[i]);

            amplitude.tooltip(UIKeys.CAMERA_PANELS_SHAKE_AMPLITUDE, Direction.BOTTOM);
            frequency.tooltip(UIKeys.CAMERA_PANELS_SHAKE_FREQUENCY, Direction.BOTTOM);
            this.axes.add(UI.row(UIConstants.MARGIN, 0, 20, UI.label(IKey.constant(ShakeClip.AXES[i]), 20), amplitude, frequency));
        }

        /* Only the controls of the current mode are shown. Bound on the wrapper, since
         * a hidden element isn't rendered and so wouldn't run its own binding */
        this.modes = this.bind(UI.column(this.legacy, this.axes), () ->
        {
            this.axes.setVisible(this.clip.perAxis.get());
            this.legacy.setVisible(!this.clip.perAxis.get());
        });

        this.active = this.bind(new UIBitToggle((value) -> this.clip.active.set(value)).all(), () -> this.active.setValue(this.clip.active.get()));
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.C_CLIP.get("bbs:shake"), this.perAxis, this.modes, this.noise, this.local, this.seed));
        this.panels.add(this.active);
    }
}