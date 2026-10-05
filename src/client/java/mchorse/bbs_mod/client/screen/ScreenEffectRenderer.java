package mchorse.bbs_mod.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import mchorse.bbs_mod.camera.clips.screen.ColorClip;
import mchorse.bbs_mod.camera.clips.screen.ColorEffect;
import mchorse.bbs_mod.camera.clips.screen.GrainClip;
import mchorse.bbs_mod.camera.clips.screen.GrainEffect;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.clips.ClipContext;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Draws what the color grade, cinematic and grain clips pushed this frame. Registered as the
 * first {@link mchorse.bbs_mod.ui.film.FrameOverlays} family, so images and subtitles drawn
 * after it stay ungraded.
 */
public class ScreenEffectRenderer
{
    public static void render(MatrixStack stack, Batcher2D batcher, ClipContext context)
    {
        List<ColorEffect> effects = ColorClip.getEffects(context);
        List<GrainEffect> grains = GrainClip.getEffects(context);

        if (effects.isEmpty() && grains.isEmpty())
        {
            return;
        }

        List<ColorEffect> pending = new ArrayList<>();

        RenderSystem.disableDepthTest();

        for (ColorEffect effect : effects)
        {
            if (effect.hasOverlay)
            {
                /* The tint goes on top of whatever was graded before it */
                applyShader(batcher, pending, Collections.emptyList());
                renderOverlay(batcher, effect.overlayColor);
            }

            if (effect.hasGrade || effect.hasCinematic)
            {
                pending.add(effect);
            }
        }

        applyShader(batcher, pending, grains);

        RenderSystem.enableDepthTest();
    }

    private static void applyShader(Batcher2D batcher, List<ColorEffect> effects, List<GrainEffect> grains)
    {
        if (effects.isEmpty() && grains.isEmpty())
        {
            return;
        }

        batcher.flush();
        ColorGradeRenderer.apply(effects, grains);
        ColorGradeRenderer.resyncMinecraftState(batcher);
        effects.clear();
    }

    /**
     * Fill the whole frame, whatever projection the caller left: a unit ortho makes the box's
     * 0..1 cover the viewport exactly.
     */
    private static void renderOverlay(Batcher2D batcher, int color)
    {
        MatrixStack matrices = batcher.getContext().getMatrices();
        Matrix4f cache = new Matrix4f(RenderSystem.getProjectionMatrix());

        RenderSystem.setProjectionMatrix(new Matrix4f().ortho(0F, 1F, 1F, 0F, -100F, 100F), VertexSorter.BY_Z);
        matrices.push();
        matrices.peek().getPositionMatrix().identity();

        batcher.box(0F, 0F, 1F, 1F, color);
        batcher.flush();

        matrices.pop();
        RenderSystem.setProjectionMatrix(cache, VertexSorter.BY_Z);
    }
}
