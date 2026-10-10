package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.modifiers.ShakeClip;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.widgets.UIBitToggle;
import mchorse.bbs_mod.ui.film.clips.widgets.UIShakeGraph;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;

public class UIShakeClip extends UIClip<ShakeClip>
{
    public UITrackpad shake;
    public UITrackpad shakeAmount;
    public UIToggle noise;
    public UIToggle perAxis;
    public UIToggle local;
    public UITrackpad seed;
    public UITrackpad octaves;
    public UITrackpad roughness;
    public UITrackpad decay;
    public UIButton presets;
    public UIShakeGraph graph;
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

        this.octaves = this.trackpad(this.clip.octaves);
        this.octaves.limit(this.clip.octaves).tooltip(UIKeys.CAMERA_PANELS_SHAKE_OCTAVES, Direction.BOTTOM);

        this.roughness = this.trackpad(this.clip.roughness);
        this.roughness.limit(this.clip.roughness).tooltip(UIKeys.CAMERA_PANELS_SHAKE_ROUGHNESS, Direction.BOTTOM);

        this.decay = this.trackpad(this.clip.decay);
        this.decay.limit(this.clip.decay).tooltip(UIKeys.CAMERA_PANELS_SHAKE_DECAY, Direction.BOTTOM);

        this.graph = new UIShakeGraph(this.clip);

        this.presets = new UIButton(UIKeys.CAMERA_PANELS_SHAKE_PRESETS, (b) ->
        {
            this.getContext().replaceContextMenu((menu) ->
            {
                menu.action(Icons.CAMERA, UIKeys.CAMERA_PANELS_SHAKE_PRESET_HANDHELD, () -> this.preset(0b0111000, 3, 0F,
                    new float[] {0F, 0F, 0F, 0.6F, 0.5F, 0.4F, 0F},
                    new float[] {1F, 1F, 1F, 0.6F, 0.7F, 0.5F, 1F}));
                menu.action(Icons.PARTICLE, UIKeys.CAMERA_PANELS_SHAKE_PRESET_EXPLOSION, () -> this.preset(0b0111111, 2, 3F,
                    new float[] {0.15F, 0.15F, 0.15F, 2F, 3F, 2F, 0F},
                    new float[] {8F, 8F, 8F, 10F, 10F, 8F, 1F}));
                menu.action(Icons.BRICKS, UIKeys.CAMERA_PANELS_SHAKE_PRESET_EARTHQUAKE, () -> this.preset(0b0110111, 2, 0F,
                    new float[] {0.08F, 0.05F, 0.08F, 0F, 0.8F, 0.6F, 0F},
                    new float[] {5F, 6F, 5F, 1F, 5F, 4F, 1F}));
                menu.action(Icons.HELICOPTER, UIKeys.CAMERA_PANELS_SHAKE_PRESET_VEHICLE, () -> this.preset(0b0110010, 2, 0F,
                    new float[] {0F, 0.04F, 0F, 0F, 0.4F, 0.6F, 0F},
                    new float[] {1F, 3F, 1F, 1F, 3F, 1.5F, 1F}));
            });
        });

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

        this.panels.add(this.section(UIKeys.C_CLIP.get("bbs:shake"), this.graph, this.presets, this.perAxis, this.modes, this.noise,
            UI.row(UIConstants.MARGIN, 0, 20, this.octaves, this.roughness), this.decay, this.local, this.seed));
        this.panels.add(this.active);
    }

    /**
     * Fill the clip with a per axis noise preset. Edited on a copy and copied back in one go,
     * so the whole preset is a single undo step rather than one per field.
     */
    private void preset(int active, int octaves, float decay, float[] amplitudes, float[] frequencies)
    {
        ShakeClip copy = (ShakeClip) this.clip.copy();

        copy.perAxis.set(true);
        copy.noise.set(true);
        copy.local.set(true);
        copy.active.set(active);
        copy.octaves.set(octaves);
        copy.roughness.set(0.5F);
        copy.decay.set(decay);

        for (int i = 0; i < ShakeClip.AXES.length; i++)
        {
            copy.amplitudes[i].set(amplitudes[i]);
            copy.frequencies[i].set(frequencies[i]);
        }

        this.clip.copy(copy);
    }
}