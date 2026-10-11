package mchorse.bbs_mod.ui.film.controller;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.api.client.editor.FilmEditorTool;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.overwrite.IdleClip;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.utils.TracksideGenerator;
import mchorse.bbs_mod.ui.film.utils.UITracksideOverlayPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.AABB;
import mchorse.bbs_mod.utils.RayTracing;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Trackside cameras preview
 *
 * Before the trackside cameras become clips, shows them in the viewport: the
 * target's path, the cameras' sequence (numbered) and a line from every camera
 * to the part of the path it films. Clicking a camera selects it and shows a
 * translate gizmo: the cube drags it horizontally, the axes along X, Y or Z.
 * The cameras are then generated, rerolled or discarded with the buttons on
 * top of the viewport.
 */
public class TracksidePreview extends FilmEditorTool
{
    private static final float CUBE_SIZE = 0.15F;
    private static final float[] PATH_COLOR = {1F, 0.8F, 0.2F};
    private static final float[] SEQUENCE_COLOR = {0.2F, 0.7F, 1F};
    private static final float[] LINK_COLOR = {0.6F, 0.6F, 0.6F};
    private static final float[][] AXIS_COLORS = {{1F, 0.25F, 0.25F}, {0.25F, 1F, 0.25F}, {0.25F, 0.5F, 1F}};
    /** Gizmo axis length and half-thickness, in cube sizes. */
    private static final float AXIS_LENGTH = 7F;
    private static final float AXIS_THICKNESS = 0.2F;

    private UIFilmController controller;
    private UIElement buttons;

    private Replay replay;
    private TracksideGenerator.Settings settings;
    private Consumer<List<IdleClip>> apply;
    private List<IdleClip> idles;
    private List<Vec3d> path;

    private IdleClip selected;
    private IdleClip dragging;
    /* -1 = on the horizontal plane (grabbed by the cube), 0..2 = along gizmo's X, Y or Z axis */
    private int axis = -1;
    private Vector3d grab = new Vector3d();
    private double axisStart;

    /* Last world render's matrices, to put the numbers on screen */
    private Matrix4f view = new Matrix4f();
    private Matrix4f projection = new Matrix4f();
    private Vec3d renderCamera = Vec3d.ZERO;

    public TracksidePreview(UIFilmController controller)
    {
        this.controller = controller;

        UIButton generate = new UIButton(UIKeys.CAMERA_TRACKSIDE_GENERATE, (b) ->
        {
            this.apply.accept(this.idles);
            this.close();
        });
        UIButton reroll = new UIButton(UIKeys.CAMERA_TRACKSIDE_REROLL, (b) -> this.reroll());
        UIButton edit = new UIButton(UIKeys.CAMERA_TRACKSIDE_SETTINGS, (b) -> this.openSettings());
        UIButton cancel = new UIButton(UIKeys.CAMERA_TRACKSIDE_DISCARD, (b) -> this.close());

        this.buttons = UI.row(generate, reroll, edit, cancel);
        this.buttons.setVisible(false);
    }

    /**
     * Start previewing trackside cameras for given replay; the apply callback
     * receives the cameras once the user generates them.
     */
    public void open(Replay replay, TracksideGenerator.Settings settings, Consumer<List<IdleClip>> apply)
    {
        this.replay = replay;
        this.settings = settings;
        this.apply = apply;
        this.reroll();

        /* Lives in the preview (drawn over the world, unlike this tool), which
         * doesn't exist yet when the controller (and this tool) is constructed */
        if (!this.buttons.hasParent())
        {
            this.controller.panel.preview.add(this.buttons);
            this.buttons.relative(this.controller.panel.preview).x(0.5F).y(8).w(320).h(20).anchorX(0.5F);
        }

        this.buttons.setVisible(true);
        this.buttons.resize();
    }

    public void close()
    {
        this.idles = null;
        this.selected = null;
        this.dragging = null;
        this.buttons.setVisible(false);
    }

    private boolean isActive()
    {
        return this.idles != null;
    }

    private void reroll()
    {
        this.selected = null;
        this.idles = TracksideGenerator.generate(MinecraftClient.getInstance().world, this.replay, this.settings, new Random());
        this.path = TracksideGenerator.samplePath(this.replay, this.settings.start, this.settings.length);
    }

    private void openSettings()
    {
        UITracksideOverlayPanel panel = new UITracksideOverlayPanel(this.settings.start, this.settings.length, (settings) ->
        {
            /* Unchanged settings keep the cameras (and their manual edits) */
            if (!settings.isSame(this.settings))
            {
                this.settings = settings;
                this.reroll();
            }
        });

        UIOverlay.addOverlay(this.getContext(), panel, 240, 300);
    }

    @Override
    public boolean click(UIContext context)
    {
        Area area = this.controller.panel.preview.getViewport();

        if (context.mouseButton != 0 || !area.isInside(context) || !this.isActive())
        {
            return false;
        }

        Vector3d origin = new Vector3d();
        Vector3f direction = this.mouseRay(context, origin);

        /* The selected camera's gizmo axes come first, as they stick out of the cube */
        if (this.selected != null)
        {
            Point point = this.selected.position.get().point;
            double best = Double.POSITIVE_INFINITY;

            for (int i = 0; i < 3; i++)
            {
                double distance = RayTracing.intersect(origin, direction, this.axisBox(point, i));

                if (distance < best)
                {
                    best = distance;
                    this.axis = i;
                }
            }

            if (best < Double.POSITIVE_INFINITY)
            {
                this.grab.set(point.x, point.y, point.z);
                this.axisStart = this.alongAxis(origin, direction, this.grab, this.axis);
                this.dragging = this.selected;

                return true;
            }
        }

        IdleClip closest = null;
        double closestDistance = Double.POSITIVE_INFINITY;

        for (IdleClip idle : this.idles)
        {
            Point point = idle.position.get().point;
            double size = this.cubeSize(point) * 2D;
            double distance = RayTracing.intersect(origin, direction, new AABB(point.x - size, point.y - size, point.z - size, size * 2D, size * 2D, size * 2D));

            if (distance < closestDistance)
            {
                closest = idle;
                closestDistance = distance;
            }
        }

        if (closest == null)
        {
            return false;
        }

        Point point = closest.position.get().point;
        Vector3d hit = this.onPlane(origin, direction, point);

        this.selected = closest;

        if (hit != null)
        {
            this.dragging = closest;
            this.axis = -1;
            this.grab.set(point.x, point.y, point.z).sub(hit);
        }

        return true;
    }

    @Override
    public void updateTool(UIContext context)
    {
        if (!this.isActive())
        {
            return;
        }

        if (this.dragging != null)
        {
            Vector3d origin = new Vector3d();
            Vector3f direction = this.mouseRay(context, origin);
            Point point = this.dragging.position.get().point;

            if (this.axis >= 0)
            {
                Vector3d moved = new Vector3d(this.grab);

                moved.setComponent(this.axis, moved.get(this.axis) + this.alongAxis(origin, direction, this.grab, this.axis) - this.axisStart);
                point.set(moved.x, moved.y, moved.z);
            }
            else
            {
                Vector3d hit = this.onPlane(origin, direction, point);

                if (hit != null)
                {
                    hit.add(this.grab);
                    point.set(hit.x, hit.y, hit.z);
                }
            }
        }

        this.renderNumbers(context);
    }

    @Override
    public boolean release(UIContext context)
    {
        if (this.dragging == null)
        {
            return false;
        }

        this.dragging = null;

        return true;
    }

    @Override
    public void stopTool()
    {
        this.dragging = null;
    }

    @Override
    public void renderTool(WorldRenderContext context)
    {
        if (!this.isActive())
        {
            return;
        }

        Vec3d camera = context.camera().getPos();
        MatrixStack stack = context.matrixStack();
        Matrix4f matrix = stack.peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        this.view.set(matrix);
        this.projection.set(RenderSystem.getProjectionMatrix());
        this.renderCamera = camera;

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();

        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int i = 1; i < this.path.size(); i++)
        {
            MotionPath.ribbon(builder, matrix, this.target(i - 1, camera), this.target(i, camera), PATH_COLOR, PATH_COLOR, 0.02F);
        }

        Vector3d previous = null;

        for (IdleClip idle : this.idles)
        {
            Vector3d position = this.relative(idle.position.get().point, camera);
            int middle = idle.tick.get() + idle.duration.get() / 2 - this.settings.start;
            float size = this.cubeSize(idle.position.get().point);
            float green = idle == this.selected ? 1F : 0.4F;

            MotionPath.ribbon(builder, matrix, position, this.target(middle, camera), LINK_COLOR, LINK_COLOR, 0.01F);

            if (previous != null)
            {
                MotionPath.ribbon(builder, matrix, previous, position, SEQUENCE_COLOR, SEQUENCE_COLOR, 0.03F);
            }

            Draw.fillBox(builder, stack, (float) position.x - size, (float) position.y - size, (float) position.z - size, (float) position.x + size, (float) position.y + size, (float) position.z + size, 0.2F, green, 1F, 1F);

            previous = position;
        }

        if (this.selected != null)
        {
            Point point = this.selected.position.get().point;

            this.renderGizmo(builder, stack, this.relative(point, camera), this.cubeSize(point));
        }

        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    /**
     * Camera numbers (in timeline order) projected onto the viewport.
     */
    private void renderNumbers(UIContext context)
    {
        Area area = this.controller.panel.preview.getViewport();
        Matrix4f matrix = new Matrix4f(this.projection).mul(this.view);

        for (int i = 0; i < this.idles.size(); i++)
        {
            Vector3d position = this.relative(this.idles.get(i).position.get().point, this.renderCamera);
            Vector4f clip = matrix.transform(new Vector4f((float) position.x, (float) position.y, (float) position.z, 1F));

            if (clip.w <= 0F)
            {
                continue;
            }

            float x = area.x + (clip.x / clip.w * 0.5F + 0.5F) * area.w;
            float y = area.y + (0.5F - clip.y / clip.w * 0.5F) * area.h;
            String label = String.valueOf(i + 1);

            context.batcher.textCard(label, x - context.batcher.getFont().getWidth(label) / 2F, y - 16);
        }
    }

    private Vector3d target(int index, Vec3d camera)
    {
        Vec3d point = this.path.get(Math.min(Math.max(index, 0), this.path.size() - 1));

        return new Vector3d(point.x - camera.x, point.y + TracksideGenerator.TARGET_HEIGHT - camera.y, point.z - camera.z);
    }

    private Vector3d relative(Point point, Vec3d camera)
    {
        return new Vector3d(point.x - camera.x, point.y - camera.y, point.z - camera.z);
    }

    /** Constant on-screen size, like the motion path's dots. */
    private float cubeSize(Point point)
    {
        Camera camera = this.controller.panel.getCamera();
        float distance = (float) new Vector3d(point.x, point.y, point.z).distance(camera.position);

        return CUBE_SIZE * BBSSettings.getScreenSizeScale(distance);
    }

    private Vector3f mouseRay(UIContext context, Vector3d origin)
    {
        Area area = this.controller.panel.preview.getViewport();
        Camera camera = this.controller.panel.getCamera();
        Vector3f offset = new Vector3f();
        Vector3f direction = camera.getMouseRay(context.mouseX, context.mouseY, area.x, area.y, area.w, area.h, offset);

        origin.set(camera.position).add(offset.x, offset.y, offset.z);

        return direction;
    }

    /**
     * Translate gizmo: one bar with a tip per axis, the dragged one highlighted.
     */
    private void renderGizmo(BufferBuilder builder, MatrixStack stack, Vector3d position, float size)
    {
        float length = size * AXIS_LENGTH;
        float thickness = size * AXIS_THICKNESS;
        Vector3f center = new Vector3f((float) position.x, (float) position.y, (float) position.z);

        for (int i = 0; i < 3; i++)
        {
            float[] color = this.dragging == this.selected && this.axis == i ? new float[] {1F, 1F, 0.3F} : AXIS_COLORS[i];
            Vector3f min = new Vector3f(center).sub(thickness, thickness, thickness);
            Vector3f max = new Vector3f(center).add(thickness, thickness, thickness);

            max.setComponent(i, max.get(i) + length);
            Draw.fillBox(builder, stack, min.x, min.y, min.z, max.x, max.y, max.z, color[0], color[1], color[2], 1F);

            /* Tip: a thicker block at the end of the bar */
            Vector3f tipMin = new Vector3f(center).sub(thickness * 3F, thickness * 3F, thickness * 3F);
            Vector3f tipMax = new Vector3f(center).add(thickness * 3F, thickness * 3F, thickness * 3F);

            tipMin.setComponent(i, center.get(i) + length - thickness * 6F);
            tipMax.setComponent(i, center.get(i) + length);
            Draw.fillBox(builder, stack, tipMin.x, tipMin.y, tipMin.z, tipMax.x, tipMax.y, tipMax.z, color[0], color[1], color[2], 1F);
        }
    }

    /** Pick box of a gizmo axis (outside the cube), thicker than drawn so it is easy to grab. */
    private AABB axisBox(Point point, int axis)
    {
        double size = this.cubeSize(point);
        double thickness = size * AXIS_THICKNESS * 3D;
        Vector3d min = new Vector3d(point.x - thickness, point.y - thickness, point.z - thickness);
        Vector3d extent = new Vector3d(thickness * 2D);

        min.setComponent(axis, min.get(axis) + thickness + size * 2D);
        extent.setComponent(axis, size * (AXIS_LENGTH - 2D));

        return new AABB(min.x, min.y, min.z, extent.x, extent.y, extent.z);
    }

    /**
     * Coordinate, along given axis, of the spot on the axis line (through
     * given point) closest to the mouse ray.
     */
    private double alongAxis(Vector3d origin, Vector3f direction, Vector3d point, int axis)
    {
        Vector3d a = new Vector3d().setComponent(axis, 1D);
        Vector3d d = new Vector3d(direction.x, direction.y, direction.z);
        Vector3d w = new Vector3d(point).sub(origin);
        double b = a.dot(d);
        double c = d.dot(d);
        double denominator = c - b * b;

        /* Ray parallel to the axis: stay put */
        if (Math.abs(denominator) < 1.0E-6D)
        {
            return point.get(axis);
        }

        return point.get(axis) + (b * d.dot(w) - c * a.dot(w)) / denominator;
    }

    /**
     * Where the mouse ray meets the horizontal plane through the point, or null.
     */
    private Vector3d onPlane(Vector3d origin, Vector3f direction, Point point)
    {
        if (Math.abs(direction.y) < 1.0E-6D)
        {
            return null;
        }

        double t = (point.y - origin.y) / direction.y;

        return t < 0D ? null : new Vector3d(origin.x + direction.x * t, point.y, origin.z + direction.z * t);
    }
}
