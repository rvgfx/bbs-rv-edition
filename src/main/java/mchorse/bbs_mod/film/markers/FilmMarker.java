package mchorse.bbs_mod.film.markers;

import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;

public class FilmMarker extends ValueGroup
{
    /** Tick where the marker sits on the timeline. */
    public final ValueInt tick = new ValueInt("tick", 0);
    /** Duration in ticks (0 = no duration band). */
    public final ValueInt duration = new ValueInt("duration", 0);
    /** Label shown in the tooltip. */
    public final ValueString label = new ValueString("label", "");
    /**
     * Packed RGB color (no alpha — renderer applies fixed alpha).
     * Default: yellow #FFFF55.
     */
    public final ValueInt color = new ValueInt("color", 0xFFFF55);

    public FilmMarker(String id)
    {
        super(id);

        this.add(this.tick);
        this.add(this.duration);
        this.add(this.label);
        this.add(this.color);
    }
}