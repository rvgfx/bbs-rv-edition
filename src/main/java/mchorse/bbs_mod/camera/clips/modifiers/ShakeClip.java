package mchorse.bbs_mod.camera.clips.modifiers;

import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.joml.Matrices;
import org.joml.Vector3f;

/**
 * Shake modifier
 *
 * This modifier shakes the camera depending on the given component
 * flags.
 *
 * <p>It shakes in one of two characters. A sine, which is perfectly periodic and moves
 * every enabled component off the same two waves — read it as a sway rather than a shake.
 * Or noise, where each component wanders on a channel of its own, which is what reads as a
 * camera someone is holding.</p>
 *
 * <p>With per axis on, every component gets its own amplitude and frequency (Hz) instead.
 * Local moves the position in camera space (right, up, forward) rather than world axes.</p>
 */
public class ShakeClip extends ComponentClip
{
    public static final String[] AXES = {"X", "Y", "Z", "Yaw", "Pitch", "Roll", "Fov"};

    public final ValueFloat shake = new ValueFloat("shake", 0F);
    public final ValueFloat shakeAmount = new ValueFloat("shakeAmount", 0F);
    public final ValueBoolean noise = new ValueBoolean("noise", false);
    public final ValueBoolean perAxis = new ValueBoolean("perAxis", false);
    public final ValueBoolean local = new ValueBoolean("local", false);
    public final ValueInt seed = new ValueInt("seed", 0);
    public final ValueFloat[] amplitudes = new ValueFloat[AXES.length];
    public final ValueFloat[] frequencies = new ValueFloat[AXES.length];

    public ShakeClip()
    {
        super();

        this.add(this.shake);
        this.add(this.shakeAmount);
        this.add(this.noise);
        this.add(this.perAxis);
        this.add(this.local);
        this.add(this.seed);

        for (int i = 0; i < AXES.length; i++)
        {
            this.amplitudes[i] = new ValueFloat("amplitude" + AXES[i], 1F);
            this.frequencies[i] = new ValueFloat("frequency" + AXES[i], 1F);

            this.add(this.amplitudes[i]);
            this.add(this.frequencies[i]);
        }

        /* Yaw and pitch should be enabled by default */
        this.active.set(0b0011000);
    }

    @Override
    public void applyClip(ClipContext context, Position position)
    {
        float time = context.ticks + context.transition;
        float[] offsets = new float[7];

        if (this.perAxis.get())
        {
            this.perAxisOffsets(time / 20F, offsets);
        }
        else
        {
            this.legacyOffsets(time, offsets);
        }

        if (this.local.get())
        {
            /* Camera space: x is right, y is up, z is forward. Same basis DollyClip moves along */
            Vector3f point = Matrices.rotate(
                new Vector3f(-offsets[0], offsets[1], offsets[2]),
                MathUtils.toRad(position.angle.pitch),
                MathUtils.toRad(180F - position.angle.yaw)
            );

            position.point.x += point.x;
            position.point.y += point.y;
            position.point.z += point.z;
        }
        else
        {
            position.point.x += offsets[0];
            position.point.y += offsets[1];
            position.point.z += offsets[2];
        }

        position.angle.yaw += offsets[3];
        position.angle.pitch += offsets[4];
        position.angle.roll += offsets[5];
        position.angle.fov += offsets[6];
    }

    /**
     * Every component on its own amplitude and frequency (in Hz). Sine axes get a phase of
     * their own off the seed, so equal frequencies don't move in lock-step.
     */
    private void perAxisOffsets(float seconds, float[] offsets)
    {
        boolean noise = this.noise.get();
        int seed = this.seed.get();

        for (int i = 0; i < offsets.length; i++)
        {
            if (!this.isActive(i))
            {
                continue;
            }

            float frequency = this.frequencies[i].get();
            float wave;

            if (noise)
            {
                /* A sine turns around 2 * f times a second and noise every second cell,
                 * so 4 * f cells a second keeps both at the same speed */
                wave = noise(seconds * frequency * 4F, i + seed * 7);
            }
            else
            {
                float phase = hash(seed, i) * (float) Math.PI;

                wave = (float) Math.sin(seconds * frequency * (float) (Math.PI * 2) + phase);
            }

            offsets[i] = wave * this.amplitudes[i].get();
        }
    }

    /**
     * The original single shake/amount behavior, kept so older films look the same.
     */
    private void legacyOffsets(float time, float[] offsets)
    {
        float shake = this.shake.get();
        float amount = this.shakeAmount.get();
        float period = shake == 0 ? 1 : shake;
        float x = time / period;

        if (this.noise.get())
        {
            /* The sine below turns around every PI * shake ticks, while noise turns around
             * at roughly every second cell — so cells of PI * shake / 2 ticks put the two at
             * the same speed, and the toggle is left changing the character and nothing else.
             * Measured: 160 vs 159 direction changes per 1000 ticks at shake = 2. */
            float n = time / (period * (float) (Math.PI / 2));
            int seed = this.seed.get();

            for (int i = 0; i < offsets.length; i++)
            {
                if (this.isActive(i))
                {
                    offsets[i] = noise(n, i + seed * 7) * amount;
                }
            }

            return;
        }

        float sin = (float) Math.sin(x);
        float cos = (float) Math.cos(x);

        if ((this.active.get() & 0b1111111) == 0b0011000)
        {
            offsets[3] = (float) (sin * sin * cos * Math.cos(x / 2)) * amount;
            offsets[4] = cos * sin * sin * amount;

            return;
        }

        float[] waves = {sin, -sin, cos, sin, cos, sin, cos};

        for (int i = 0; i < offsets.length; i++)
        {
            if (this.isActive(i))
            {
                offsets[i] = waves[i] * amount;
            }
        }
    }

    /**
     * Value noise: a smooth signal in [-1, 1] that wanders instead of repeating.
     *
     * <p>It is a pure function of x and the channel — the same tick always yields the same
     * value, so scrubbing the timeline, playback and the final render all agree. That is why
     * this cannot reach for {@link Math#random()}.</p>
     *
     * <p>Each component asks for a channel of its own, which is what keeps the axes
     * independent; the sine path moves them all off the same two waves.</p>
     */
    private static float noise(float x, int channel)
    {
        int cell = (int) Math.floor(x);
        float t = x - cell;

        /* Smoothstep, so the lattice points don't show up as kinks */
        t = t * t * (3F - 2F * t);

        return Lerps.lerp(hash(cell, channel), hash(cell + 1, channel), t);
    }

    /**
     * Hash a lattice point into [-1, 1). An integer avalanche, so that neighbouring cells —
     * and neighbouring channels — land nowhere near each other.
     */
    private static float hash(int cell, int channel)
    {
        int h = cell * 374761393 + channel * 668265263;

        h = (h ^ (h >>> 13)) * 1274126177;
        h = h ^ (h >>> 16);

        return (h >>> 8) / (float) (1 << 24) * 2F - 1F;
    }

    @Override
    public Clip create()
    {
        return new ShakeClip();
    }
}