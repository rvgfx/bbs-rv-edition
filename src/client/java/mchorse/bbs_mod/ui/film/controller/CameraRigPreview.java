package mchorse.bbs_mod.ui.film.controller;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.api.client.editor.FilmEditorTool;
import mchorse.bbs_mod.camera.clips.overwrite.KeyframeClip;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.utils.CameraRigGenerator;
import mchorse.bbs_mod.ui.film.utils.TracksideGenerator;
import mchorse.bbs_mod.ui.film.utils.UICameraRigOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.UI;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.List;
import java.util.function.Consumer;

/**
 * Camera rig preview
 *
 * Before a rig becomes a clip, shows it in the viewport like the trackside
 * preview does: the target's path, the camera's path and, every few ticks, a
 * line from the camera to the target it aims at. Rigs are parametric, so they
 * are tweaked through the settings rather than dragged around.
 */
public class CameraRigPreview extends FilmEditorTool
{
    private static final float[] PATH_COLOR = {1F, 0.8F, 0.2F};
    private static final float[] CAMERA_COLOR = {0.2F, 0.7F, 1F};
    private static final float[] LINK_COLOR = {0.6F, 0.6F, 0.6F};
    private static final int LINK_STEP = 10;

    private UIFilmController controller;
    private UIElement buttons;

    private Replay replay;
    private IKey title;
    private CameraRigGenerator.Settings settings;
    private Consumer<KeyframeClip> apply;
    private KeyframeClip clip;
    private List<Vec3d> path;

    public CameraRigPreview(UIFilmController controller)
    {
        this.controller = controller;

        UIButton generate = new UIButton(UIKeys.CAMERA_TRACKSIDE_GENERATE, (b) ->
        {
            this.apply.accept(this.clip);
            this.close();
        });
        UIButton edit = new UIButton(UIKeys.CAMERA_TRACKSIDE_SETTINGS, (b) -> this.openSettings());
        UIButton cancel = new UIButton(UIKeys.CAMERA_TRACKSIDE_DISCARD, (b) -> this.close());

        this.buttons = UI.row(generate, edit, cancel);
        this.buttons.setVisible(false);
    }

    /**
     * Start previewing the rig for given replay; the apply callback receives
     * the keyframe clip once the user generates it.
     */
    public void open(Replay replay, IKey title, CameraRigGenerator.Settings settings, Consumer<KeyframeClip> apply)
    {
        this.replay = replay;
        this.title = title;
        this.apply = apply;
        this.update(settings);

        /* Lives in the preview, which doesn't exist yet when the controller is constructed */
        if (!this.buttons.hasParent())
        {
            this.controller.panel.preview.add(this.buttons);
            this.buttons.relative(this.controller.panel.preview).x(0.5F).y(8).w(240).h(20).anchorX(0.5F);
        }

        this.buttons.setVisible(true);
        this.buttons.resize();
    }

    public void close()
    {
        this.clip = null;
        this.buttons.setVisible(false);
    }

    private void update(CameraRigGenerator.Settings settings)
    {
        this.settings = settings;
        this.clip = CameraRigGenerator.generate(this.replay, settings);
        this.path = TracksideGenerator.samplePath(this.replay, settings.start, settings.length);
    }

    private void openSettings()
    {
        UICameraRigOverlayPanel panel = new UICameraRigOverlayPanel(this.settings.rig, this.title, this.settings.start, this.settings.length, this::update);

        UIOverlay.addOverlay(this.getContext(), panel, 240, 260);
    }

    @Override
    public void renderTool(WorldRenderContext context)
    {
        if (this.clip == null)
        {
            return;
        }

        Vec3d camera = context.camera().getPos();
        Matrix4f matrix = context.matrixStack().peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();

        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int t = 1; t < this.path.size(); t++)
        {
            MotionPath.ribbon(builder, matrix, this.target(t - 1, camera), this.target(t, camera), PATH_COLOR, PATH_COLOR, 0.02F);
            MotionPath.ribbon(builder, matrix, this.camera(t - 1, camera), this.camera(t, camera), CAMERA_COLOR, CAMERA_COLOR, 0.03F);
        }

        for (int t = 0; t < this.path.size(); t += LINK_STEP)
        {
            MotionPath.ribbon(builder, matrix, this.camera(t, camera), this.target(t, camera), LINK_COLOR, LINK_COLOR, 0.01F);
        }

        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    private Vector3d target(int tick, Vec3d camera)
    {
        Vec3d point = this.path.get(tick);

        return new Vector3d(point.x - camera.x, point.y + TracksideGenerator.TARGET_HEIGHT - camera.y, point.z - camera.z);
    }

    private Vector3d camera(int tick, Vec3d camera)
    {
        return new Vector3d(
            this.clip.x.interpolate(tick) - camera.x,
            this.clip.y.interpolate(tick) - camera.y,
            this.clip.z.interpolate(tick) - camera.z
        );
    }
}
