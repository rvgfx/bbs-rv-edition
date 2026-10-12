package mchorse.bbs_mod.camera.clips.screen;

import mchorse.bbs_mod.camera.clips.modifiers.EntityClip;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.ArrayList;
import java.util.List;

/**
 * Depth of field: blurs what is nearer or farther than the focus distance.
 *
 * <p>With a target replay picked, the focus follows the distance from the camera to it, and the
 * focus channel becomes an offset on top (a focus pull is then just keyframes around 0).
 */
public class DepthOfFieldClip extends EntityClip
{
    private static final double DEFAULT_FOCUS = 5D;

    /** Distance in blocks, or an offset from the target's distance when one is picked. */
    public final KeyframeChannel<Double> focus = new KeyframeChannel<>("focus", KeyframeFactories.DOUBLE);
    /** Depth in blocks on each side of the focus that stays fully sharp. */
    public final KeyframeChannel<Double> range = new KeyframeChannel<>("range", KeyframeFactories.DOUBLE);
    /** Largest blur radius, in pixels at 1080p. */
    public final KeyframeChannel<Double> strength = new KeyframeChannel<>("strength", KeyframeFactories.DOUBLE);

    public final KeyframeChannel<Double>[] channels;

    private DepthOfFieldEffect effect = new DepthOfFieldEffect();

    public static List<DepthOfFieldEffect> getEffects(ClipContext context)
    {
        return context.clipData.get("dofEffects", ArrayList::new);
    }

    public DepthOfFieldClip()
    {
        this.channels = new KeyframeChannel[] {this.focus, this.range, this.strength};

        this.add(this.focus);
        this.add(this.range);
        this.add(this.strength);
    }

    @Override
    protected void applyClip(ClipContext context, Position position)
    {
        float t = context.relativeTick + context.transition;
        float strength = this.strength.isEmpty() ? 0F : (float) (double) this.strength.interpolate(t);

        if (strength <= 0F)
        {
            return;
        }

        double focus = this.focus.isEmpty() ? 0D : this.focus.interpolate(t);
        List<IEntity> entities = this.getEntities(context);

        if (!entities.isEmpty())
        {
            IEntity entity = entities.get(0);
            Point offset = this.offset.get();
            double dx = Lerps.lerp(entity.getPrevX(), entity.getX(), context.transition) + offset.x - position.point.x;
            double dy = Lerps.lerp(entity.getPrevY(), entity.getY(), context.transition) + offset.y - position.point.y;
            double dz = Lerps.lerp(entity.getPrevZ(), entity.getZ(), context.transition) + offset.z - position.point.z;

            focus += Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        else if (this.focus.isEmpty())
        {
            focus = DEFAULT_FOCUS;
        }

        this.effect.focus = (float) Math.max(0.05D, focus);
        this.effect.range = this.range.isEmpty() ? 0F : (float) Math.max(0D, this.range.interpolate(t));
        this.effect.strength = strength * this.envelope.factorEnabled(this.duration.get(), t);

        getEffects(context).add(this.effect);
    }

    @Override
    public boolean isPositionClip()
    {
        return false;
    }

    @Override
    protected Clip create()
    {
        return new DepthOfFieldClip();
    }
}
