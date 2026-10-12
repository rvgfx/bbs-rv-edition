package mchorse.bbs_mod.ui.film.utils;

import mchorse.bbs_mod.camera.clips.overwrite.KeyframeClip;
import mchorse.bbs_mod.camera.data.Angle;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Camera rig generator
 *
 * Bakes a classic camera move around a replay into a single keyframe clip:
 * a drone orbiting it, a crane rising from a fixed base, or an over the
 * shoulder follow cam. The camera is sampled every {@link #STEP} ticks and
 * always aims at the target, so no extra look clip is needed and every
 * keyframe can be tweaked afterwards.
 */
public class CameraRigGenerator
{
    private static final int STEP = 4;
    /** Height above the replay's feet the orbit and crane aim at. */
    private static final double TARGET_HEIGHT = 1D;
    /** How far right of the target the over the shoulder camera sits. */
    private static final double SHOULDER_SIDE = 0.6D;
    /** How far ahead of the target the over the shoulder camera looks. */
    private static final double LOOK_AHEAD = 8D;
    /** Ticks on each side averaged into the target's yaw, so mouse jitter doesn't shake the follow cam. */
    private static final int YAW_SMOOTHING = 10;

    public static KeyframeClip generate(Replay replay, Settings settings)
    {
        KeyframeClip clip = new KeyframeClip();
        ReplayKeyframes keyframes = replay.keyframes;
        Vec3d base = position(keyframes, settings.start);
        double baseYaw = keyframes.yaw.interpolate(settings.start);
        double lastYaw = 0D;

        for (int i = 0, steps = (settings.length + STEP - 1) / STEP; i <= steps; i++)
        {
            int t = Math.min(i * STEP, settings.length);
            double factor = t / (double) settings.length;
            double height = settings.heightStart + (settings.heightEnd - settings.heightStart) * factor;
            Vec3d feet = position(keyframes, settings.start + t);
            Vec3d camera;
            Vec3d look = feet.add(0D, TARGET_HEIGHT, 0D);

            if (settings.rig == Rig.ORBIT)
            {
                camera = feet.add(behind(baseYaw + settings.degrees * factor).multiply(settings.distance)).add(0D, height, 0D);
            }
            else if (settings.rig == Rig.CRANE)
            {
                camera = base.add(behind(baseYaw).multiply(settings.distance)).add(0D, height, 0D);
            }
            else
            {
                double yaw = smoothYaw(keyframes, settings.start + t);
                Vec3d forward = behind(yaw).multiply(-1D);
                Vec3d right = new Vec3d(-forward.z, 0D, forward.x);

                camera = feet.add(forward.multiply(-settings.distance)).add(right.multiply(SHOULDER_SIDE)).add(0D, height, 0D);
                look = feet.add(forward.multiply(LOOK_AHEAD)).add(0D, height, 0D);
            }

            Angle angle = Angle.angle(look.x - camera.x, look.y - camera.y, look.z - camera.z);
            /* Unwrap so the yaw keyframes never spin the long way round between samples */
            double yaw = i == 0 ? angle.yaw : lastYaw + MathHelper.wrapDegrees(angle.yaw - lastYaw);

            lastYaw = yaw;

            clip.x.insert(t, camera.x);
            clip.y.insert(t, camera.y);
            clip.z.insert(t, camera.z);
            clip.yaw.insert(t, yaw);
            clip.pitch.insert(t, (double) angle.pitch);
        }

        clip.fov.insert(0, 70D);
        clip.tick.set(settings.start);
        clip.duration.set(settings.length);

        return clip;
    }

    private static Vec3d position(ReplayKeyframes keyframes, int tick)
    {
        return new Vec3d(keyframes.x.interpolate(tick), keyframes.y.interpolate(tick), keyframes.z.interpolate(tick));
    }

    /**
     * Horizontal unit vector pointing behind an entity with the given (Minecraft) yaw.
     */
    private static Vec3d behind(double yaw)
    {
        double radians = Math.toRadians(yaw);

        return new Vec3d(Math.sin(radians), 0D, -Math.cos(radians));
    }

    private static double smoothYaw(ReplayKeyframes keyframes, int tick)
    {
        double first = keyframes.yaw.interpolate(tick);
        double sum = 0D;
        int count = 0;

        for (int t = tick - YAW_SMOOTHING; t <= tick + YAW_SMOOTHING; t++)
        {
            sum += first + MathHelper.wrapDegrees(keyframes.yaw.interpolate(Math.max(t, 0)) - first);
            count += 1;
        }

        return sum / count;
    }

    public enum Rig
    {
        ORBIT(6D, 2D, 2D, 360D), CRANE(6D, 0.5D, 8D, 0D), FOLLOW(3D, 1.7D, 1.7D, 0D);

        public final Settings defaults = new Settings();

        Rig(double distance, double heightStart, double heightEnd, double degrees)
        {
            this.defaults.rig = this;
            this.defaults.distance = distance;
            this.defaults.heightStart = heightStart;
            this.defaults.heightEnd = heightEnd;
            this.defaults.degrees = degrees;
        }
    }

    /**
     * Generation settings, also remembered per rig by the overlay panel.
     */
    public static class Settings
    {
        public Rig rig;
        public int start;
        public int length;
        public double distance;
        public double heightStart;
        public double heightEnd;
        public double degrees;
    }
}
