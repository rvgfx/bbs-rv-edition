package mchorse.bbs_mod.ui.film.clips.widgets;

import mchorse.bbs_mod.camera.clips.modifiers.ShakeClip;
import mchorse.bbs_mod.graphics.line.LineBuilder;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.graphics.line.SolidColorLineRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.Arrays;
import java.util.Objects;

/**
 * Preview of a shake clip over its whole duration, one line per active component in the
 * same colors as the component toggle. Every line is scaled to its own peak, so a position
 * shaking by 0.05 and a yaw shaking by 5 degrees are both readable — it shows the shape of
 * the shake (speed, roughness, decay), not how strong components are against each other.
 *
 * <p>The lines are only sampled again when the clip or the widget's area changes.</p>
 */
public class UIShakeGraph extends UIElement
{
    private static final int[] COLORS = {Colors.RED, Colors.GREEN, Colors.BLUE, Colors.YELLOW, Colors.CYAN, Colors.MAGENTA, Colors.WHITE};

    private ShakeClip clip;
    private LineBuilder[] lines;
    private int key;

    public UIShakeGraph(ShakeClip clip)
    {
        super();

        this.clip = clip;

        this.h(60);
    }

    @Override
    public void render(UIContext context)
    {
        int x = this.area.x;
        int y = this.area.y;
        int w = this.area.w;
        int h = this.area.h;
        int key = this.getKey();

        if (this.lines == null || key != this.key)
        {
            this.key = key;
            this.lines = this.buildLines(x, y, w, h);
        }

        context.batcher.box(x, y, x + w, y + h, Colors.A50);
        context.batcher.box(x, y + h / 2, x + w, y + h / 2 + 1, Colors.A25);

        Color color = new Color();

        for (int a = 0; a < COLORS.length; a++)
        {
            if (this.lines[a] != null)
            {
                color.set(COLORS[a], false);
                this.lines[a].render(context.batcher, SolidColorLineRenderer.get(color.r, color.g, color.b, 1F));
            }
        }

        super.render(context);
    }

    /**
     * Everything the lines depend on: the clip's values (generically, so a field added to
     * the clip later is picked up too) and where the widget sits.
     */
    private int getKey()
    {
        int key = Objects.hash(this.area.x, this.area.y, this.area.w, this.area.h);

        for (BaseValue value : this.clip.getAll())
        {
            if (value instanceof BaseValueBasic basic)
            {
                key = key * 31 + Objects.hashCode(basic.get());
            }
        }

        return key;
    }

    private LineBuilder[] buildLines(int x, int y, int w, int h)
    {
        int samples = Math.max(w, 2);
        float duration = Math.max(this.clip.duration.get(), 1);
        float start = this.clip.tick.get();
        float[][] values = new float[COLORS.length][samples];
        float[] peaks = new float[COLORS.length];
        float[] offsets = new float[COLORS.length];
        LineBuilder[] lines = new LineBuilder[COLORS.length];
        float half = h / 2F - 2F;

        for (int i = 0; i < samples; i++)
        {
            float relative = duration * i / (samples - 1);

            Arrays.fill(offsets, 0F);
            this.clip.getOffsets(start + relative, relative, offsets);

            for (int a = 0; a < offsets.length; a++)
            {
                values[a][i] = offsets[a];
                peaks[a] = Math.max(peaks[a], Math.abs(offsets[a]));
            }
        }

        for (int a = 0; a < COLORS.length; a++)
        {
            if (peaks[a] <= 0F)
            {
                continue;
            }

            lines[a] = new LineBuilder(0.75F);

            for (int i = 0; i < samples; i++)
            {
                lines[a].add(x + (float) w * i / (samples - 1), y + h / 2F - values[a][i] / peaks[a] * half);
            }
        }

        return lines;
    }
}
