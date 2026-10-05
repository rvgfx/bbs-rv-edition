package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

/**
 * Panel of the screen effect clips (color grade, cinematic effects, grain): every one of them is
 * a list of keyframe channels, edited in one embedded keyframe editor with a row per channel.
 */
public class UIScreenEffectClip <T extends CameraClip> extends UIClip<T>
{
    public UIButton edit;
    public UIKeyframeEditor keyframes;

    private final KeyframeChannel[] channels;
    private final String embedId;

    public UIScreenEffectClip(T clip, IUIClipsDelegate editor, KeyframeChannel[] channels, String embedId)
    {
        super(clip, editor);

        this.channels = channels;
        this.embedId = embedId;

        /* Not in registerUI(): UIClip's constructor calls it before these fields are assigned */
        this.keyframes.setUndoId(embedId + "_keyframes");
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.keyframes = new UIKeyframeEditor((consumer) -> new UIFilmKeyframes(this.editor, consumer));
        this.keyframes.view.duration(() -> this.clip.duration.get());

        this.edit = new UIButton(UIKeys.CAMERA_PANELS_EDIT_KEYFRAMES, (b) ->
        {
            this.editor.embedView(this.keyframes);
            this.keyframes.view.resetView();
            this.keyframes.view.getGraph().clearSelection();
        });
        this.edit.keys().register(Keys.FORMS_EDIT, () -> this.edit.clickItself());
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.CAMERA_PANELS_KEYFRAMES, this.edit));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.keyframes.view.removeAllSheets();

        for (int i = 0; i < this.channels.length; i++)
        {
            KeyframeChannel channel = this.channels[i];
            int color = UIKeyframeEditor.COLORS[i % UIKeyframeEditor.COLORS.length];

            this.keyframes.view.addSheet(new UIKeyframeSheet(channel.getId(), UIKeys.C_SCREEN_CHANNEL.get(channel.getId()), color, channel, null));
        }

        this.keyframes.view.getGraph().clearSelection();
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        if (this.embedId.equals(data.getString("embed")))
        {
            this.editor.embedView(this.keyframes);
            this.keyframes.view.resetView();
        }
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);

        if (this.keyframes.hasParent())
        {
            data.putString("embed", this.embedId);
        }
    }
}
