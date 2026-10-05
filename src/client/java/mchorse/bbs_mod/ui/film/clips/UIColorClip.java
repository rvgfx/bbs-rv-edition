package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.screen.ColorClip;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;

public class UIColorClip extends UIScreenEffectClip<ColorClip>
{
    public UIColor overlayColor;

    public UIColorClip(ColorClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor, clip.channels, "color");
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.overlayColor = new UIColor((c) -> this.editor.editMultiple(this.clip.overlayColor, c));
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.SCREEN_PANELS_OVERLAY_COLOR, this.overlayColor));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.overlayColor.setColor(this.clip.overlayColor.get());
    }
}
