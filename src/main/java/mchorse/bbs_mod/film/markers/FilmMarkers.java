package mchorse.bbs_mod.film.markers;

import mchorse.bbs_mod.settings.values.core.ValueList;

import java.util.List;

public class FilmMarkers extends ValueList<FilmMarker>
{
    public FilmMarkers(String id)
    {
        super(id);
    }

    public FilmMarker addMarker(int tick)
    {
        FilmMarker marker = new FilmMarker(String.valueOf(this.list.size()));

        marker.tick.set(tick);

        this.preNotify();
        this.add(marker);
        this.postNotify();

        return marker;
    }

    public void remove(FilmMarker marker)
    {
        int index = this.list.indexOf(marker);

        if (index >= 0)
        {
            this.preNotify();
            this.list.remove(index);
            this.sync();
            this.postNotify();
        }
    }

    public List<FilmMarker> getList()
    {
        return this.list;
    }

    @Override
    protected FilmMarker create(String id)
    {
        return new FilmMarker(id);
    }
}