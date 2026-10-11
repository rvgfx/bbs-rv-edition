package mchorse.bbs_mod.ui.film.utils;

import mchorse.bbs_mod.camera.clips.overwrite.IdleClip;
import mchorse.bbs_mod.camera.data.Angle;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.utils.RayTracing;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Trackside camera generator
 *
 * Splits the target replay's path into segments of (about) equal duration and
 * places one idle camera beside each segment, like racing game replays.
 * Every camera is picked among candidates: the ones stuck in blocks are
 * rejected, and the one that sees the target the most (and repeats the
 * previous camera the least) is kept.
 *
 * Uniformity (0..1) blends between evenly sized segments with cameras
 * alternating sides at the middle of the ranges (1), and random segment
 * sizes, sides, distances and heights (0).
 */
public class TracksideGenerator
{
    /** Height above the replay's feet the cameras aim at (also used as LookClip offset). */
    public static final double TARGET_HEIGHT = 1D;

    private static final int CANDIDATES = 24;
    private static final int VISIBILITY_SAMPLES = 8;
    private static final double CAMERA_RADIUS = 0.3D;
    private static final double REPEAT_ANGLE = 45D;
    private static final double REPEAT_PENALTY = 0.35D;

    public static List<IdleClip> generate(World world, Replay replay, Settings settings, Random random)
    {
        int count = Math.min(settings.minCameras + random.nextInt(settings.maxCameras - settings.minCameras + 1), settings.length);
        List<Vec3d> path = samplePath(replay, settings.start, settings.length);
        List<IdleClip> clips = new ArrayList<>();
        int[] cuts = split(settings.length, count, settings.uniformity, random);
        Generator generator = new Generator(world, path, settings, random);

        for (int i = 0; i < count; i++)
        {
            int a = cuts[i];
            int b = cuts[i + 1];

            if (b <= a)
            {
                continue;
            }

            IdleClip clip = new IdleClip();

            clip.tick.set(settings.start + a);
            clip.duration.set(b - a);
            clip.position.set(generator.pick(a, b));
            clips.add(clip);
        }

        return clips;
    }

    /**
     * The replay's position at every tick of the given range.
     */
    public static List<Vec3d> samplePath(Replay replay, int start, int length)
    {
        List<Vec3d> path = new ArrayList<>();

        for (int tick = start; tick <= start + length; tick++)
        {
            path.add(new Vec3d(
                replay.keyframes.x.interpolate(tick),
                replay.keyframes.y.interpolate(tick),
                replay.keyframes.z.interpolate(tick)
            ));
        }

        return path;
    }

    /**
     * Ticks (relative to the start) where each segment begins, plus the end.
     * Equal durations, with every cut jittered by up to almost half a segment
     * as uniformity drops.
     */
    private static int[] split(int length, int count, double uniformity, Random random)
    {
        int[] cuts = new int[count + 1];

        cuts[count] = length;

        for (int i = 1; i < count; i++)
        {
            double jitter = (random.nextDouble() - 0.5D) * 0.9D * (1D - uniformity);

            int cut = (int) Math.round(length * (i + jitter) / count);

            /* Every segment keeps at least one tick */
            cuts[i] = Math.min(Math.max(cuts[i - 1] + 1, cut), length - (count - i));
        }

        return cuts;
    }

    /**
     * Generation settings, also remembered by the overlay panel between uses.
     */
    public static class Settings
    {
        public int start;
        public int length;
        public int minCameras = 4;
        public int maxCameras = 8;
        public double minDistance = 6D;
        public double maxDistance = 14D;
        public double minHeight = 1D;
        public double maxHeight = 4D;
        public double uniformity = 0.5D;

        public boolean isSame(Settings other)
        {
            return this.start == other.start && this.length == other.length
                && this.minCameras == other.minCameras && this.maxCameras == other.maxCameras
                && this.minDistance == other.minDistance && this.maxDistance == other.maxDistance
                && this.minHeight == other.minHeight && this.maxHeight == other.maxHeight
                && this.uniformity == other.uniformity;
        }
    }

    /**
     * Picks camera positions one segment after another, remembering the
     * previous camera to avoid repeating its shot.
     */
    private static class Generator
    {
        private World world;
        private List<Vec3d> path;
        private Settings settings;
        private Random random;

        private Vec3d previous;
        private float previousYaw;
        private int side = 1;

        public Generator(World world, List<Vec3d> path, Settings settings, Random random)
        {
            this.world = world;
            this.path = path;
            this.settings = settings;
            this.random = random;
        }

        public Position pick(int a, int b)
        {
            Vec3d direction = this.path.get(b).subtract(this.path.get(a)).multiply(1D, 0D, 1D);

            if (direction.lengthSquared() < 1.0E-4D)
            {
                direction = new Vec3d(1D, 0D, 0D);
            }

            Vec3d normal = new Vec3d(-direction.z, 0D, direction.x).normalize();
            Vec3d best = null;
            Vec3d bestAnchor = null;
            double bestScore = Double.NEGATIVE_INFINITY;

            /* Uniform generation alternates sides, random one flips a coin */
            this.side = this.random.nextDouble() < this.settings.uniformity ? -this.side : (this.random.nextBoolean() ? 1 : -1);

            for (int i = 0; i < CANDIDATES * 2 && bestScore < 1D; i++)
            {
                /* Later candidates get gradually more random, so a blocked uniform spot can still be escaped,
                 * and the second half widens the ranges for cramped places */
                double spread = Math.max(1D - this.settings.uniformity, i / (double) CANDIDATES);
                double widen = i < CANDIDATES ? 1D : 1.5D;
                int sign = i == 0 || this.random.nextDouble() > spread ? this.side : -this.side;

                Vec3d anchor = this.path.get(a + (int) ((b - a) * (0.7D + this.jitter(spread) * 0.4D)));
                double distance = this.inRange(this.settings.minDistance / widen, this.settings.maxDistance * widen, spread);
                Vec3d candidate = anchor.add(normal.multiply(sign * distance));
                double ground = this.findGround(candidate.x, anchor.y + this.settings.maxHeight * widen + 2D, candidate.z, anchor.y);

                candidate = new Vec3d(candidate.x, ground + this.inRange(this.settings.minHeight, this.settings.maxHeight * widen, spread), candidate.z);

                if (!this.isFree(candidate))
                {
                    continue;
                }

                double score = this.visibility(candidate, a, b) - this.repetition(candidate, anchor);

                if (score > bestScore)
                {
                    best = candidate;
                    bestAnchor = anchor;
                    bestScore = score;
                }
            }

            if (best == null)
            {
                bestAnchor = this.path.get((a + b) / 2);
                best = this.climb(bestAnchor.add(0D, this.settings.maxHeight, 0D));
            }

            Angle angle = Angle.angle(bestAnchor.x - best.x, bestAnchor.y + TARGET_HEIGHT - best.y, bestAnchor.z - best.z);

            this.previous = best;
            this.previousYaw = angle.yaw;

            return new Position((float) best.x, (float) best.y, (float) best.z, angle.yaw, angle.pitch);
        }

        /** Random value in -0.5..0.5 scaled by spread. */
        private double jitter(double spread)
        {
            return (this.random.nextDouble() - 0.5D) * spread;
        }

        /** Middle of the range, pushed randomly towards its edges by spread. */
        private double inRange(double min, double max, double spread)
        {
            return (min + max) / 2D + this.jitter(spread) * (max - min);
        }

        private boolean isFree(Vec3d position)
        {
            return this.world.isSpaceEmpty(new Box(position, position).expand(CAMERA_RADIUS));
        }

        /** Moves up until the position is out of blocks, as a last resort. */
        private Vec3d climb(Vec3d position)
        {
            for (int i = 0; i < 32 && !this.isFree(position); i++)
            {
                position = position.add(0D, 1D, 0D);
            }

            return position;
        }

        private double findGround(double x, double y, double z, double fallback)
        {
            BlockHitResult hit = RayTracing.rayTrace(this.world, new Vec3d(x, y, z), new Vec3d(0D, -1D, 0D), 32D);

            return hit.getType() == HitResult.Type.MISS ? fallback : hit.getPos().y;
        }

        /**
         * Penalty for shooting from about the same direction as the previous camera.
         */
        private double repetition(Vec3d candidate, Vec3d anchor)
        {
            if (this.previous == null)
            {
                return 0D;
            }

            Angle angle = Angle.angle(anchor.x - candidate.x, 0D, anchor.z - candidate.z);
            double difference = Math.abs(((angle.yaw - this.previousYaw) % 360D + 540D) % 360D - 180D);

            return difference < REPEAT_ANGLE ? REPEAT_PENALTY * (1D - difference / REPEAT_ANGLE) : 0D;
        }

        /**
         * Fraction of the segment's samples where nothing blocks the line of sight.
         */
        private double visibility(Vec3d camera, int a, int b)
        {
            int visible = 0;

            for (int i = 0; i < VISIBILITY_SAMPLES; i++)
            {
                Vec3d target = this.path.get(a + (b - a) * i / (VISIBILITY_SAMPLES - 1)).add(0D, TARGET_HEIGHT, 0D);
                Vec3d ray = target.subtract(camera);
                double length = ray.length();

                if (length < 0.5D)
                {
                    visible += 1;

                    continue;
                }

                BlockHitResult hit = RayTracing.rayTrace(this.world, camera, ray, length);

                if (hit.getType() == HitResult.Type.MISS || hit.getPos().distanceTo(camera) >= length - 0.5D)
                {
                    visible += 1;
                }
            }

            return visible / (double) VISIBILITY_SAMPLES;
        }
    }
}
