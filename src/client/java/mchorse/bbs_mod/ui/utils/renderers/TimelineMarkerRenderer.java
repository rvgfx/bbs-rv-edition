package mchorse.bbs_mod.ui.utils.renderers;

import mchorse.bbs_mod.film.markers.FilmMarker;
import mchorse.bbs_mod.film.markers.FilmMarkers;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.IntUnaryOperator;

public class TimelineMarkerRenderer
{
    /** Half-width of the clickable arrow zone in pixels. */
    public static final int ARROW_HALF_W = 4;
    /** Height of the arrow icon in pixels. */
    public static final int ARROW_H = 16;

    /**
     * Renders all markers from the given FilmMarkers onto the timeline.
     */
    public static void render(
            UIContext context,
            Area area,
            int rulerY,
            int rulerBottom,
            FilmMarkers markers,
            IntUnaryOperator toGraphX,
            int mouseX,
            int mouseY
    )
    {
        for (FilmMarker marker : markers.getList())
        {
            int x = toGraphX.applyAsInt(marker.tick.get());
            int rgb = marker.color.get() & 0xFFFFFF;

            /* Duration band — rendered independently of marker head visibility,
             * because the band may be visible even when the marker head is not. */
            if (marker.duration.get() > 0)
            {
                int x2 = toGraphX.applyAsInt(marker.tick.get() + marker.duration.get());
                int clampedX = Math.max(x, area.x);
                int clampedX2 = Math.min(x2, area.ex());

                if (clampedX < clampedX2)
                {
                    int fillColor = (rgb & 0xFFFFFF) | 0x33000000;
                    int lineColor = rgb | Colors.A75;

                    context.batcher.box(clampedX, rulerY, clampedX2, rulerBottom, fillColor);

                    /* Right edge line when visible */
                    if (x2 >= area.x && x2 <= area.ex())
                    {
                        context.batcher.box(x2, rulerY, x2 + 1, area.ey(), lineColor);
                    }
                }
            }

            /* Skip marker head and vertical line if outside visible area */
            if (x < area.x || x > area.ex())
            {
                continue;
            }

            int lineColor = rgb | Colors.A75;
            int arrowColor = rgb | Colors.A100;

            /* Vertical line from ruler down to bottom of area */
            context.batcher.box(x, rulerY, x + 1, area.ey(), lineColor);

            /* Marker icon centered on x, anchored at top-center */
            context.batcher.icon(Icons.TIMELINE_MARKER, arrowColor, x, rulerY, 0.5F, 0F);

            /* Tooltip */
            String markerLabel = marker.label.get();

            if (!markerLabel.isEmpty()
                    && mouseX >= x - ARROW_HALF_W && mouseX <= x + ARROW_HALF_W
                    && mouseY >= rulerY && mouseY <= rulerY + ARROW_H)
            {
                context.batcher.textCard(
                        markerLabel,
                        mouseX + 6,
                        mouseY - 6,
                        Colors.WHITE,
                        Colors.setA(0x222222, 0.85F),
                        2
                );
            }
        }
    }

    /**
     * Returns the marker under the cursor, or null if none.
     */
    public static FilmMarker pick(
            FilmMarkers markers,
            IntUnaryOperator toGraphX,
            int mouseX,
            int mouseY,
            int rulerY
    )
    {
        for (FilmMarker marker : markers.getList())
        {
            int x = toGraphX.applyAsInt(marker.tick.get());

            if (Math.abs(mouseX - x) <= ARROW_HALF_W
                    && mouseY >= rulerY && mouseY <= rulerY + ARROW_H)
            {
                return marker;
            }
        }

        return null;
    }
}