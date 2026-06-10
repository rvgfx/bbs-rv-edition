package mchorse.bbs_mod.ui.playback_button;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.list.UIDataPathList;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UIDataUtils;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Hand;

public class UIPlaybackPanel extends UIDashboardPanel
{
    private static final IKey TITLE    = L10n.lang("bbs.ui.playback_button.title");
    private static final IKey DONE     = L10n.lang("bbs.ui.general.done");
    private static final IKey WITH_CAM = L10n.lang("bbs.ui.playback_button.with_camera");
    private static final IKey NO_FILM  = L10n.lang("bbs.ui.playback_button.no_film_selected");

    private static final int PANEL_W = 240;

    public Film film;

    public UILabel titleLabel;
    public UILabel selectedFilmLabel;
    public UIToggle withCameraToggle;
    public UIButton doneButton;

    public final UIDataPathList filmList;
    public final UISearchList<DataPath> filmSearch;

    private final UIElement container;

    private String selectedFilm = "";

    public UIPlaybackPanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.filmList = new UIDataPathList((list) ->
        {
            DataPath selected = list.get(0);

            if (!selected.folder)
            {
                this.selectedFilm = selected.toString();
                this.refreshLabel();
            }
        });

        this.filmSearch = new UISearchList<>(this.filmList);
        this.filmSearch.label(UIKeys.GENERAL_SEARCH);

        this.titleLabel = new UILabel(TITLE);
        this.titleLabel.labelAnchor(0.5F, 0.5F);
        this.titleLabel.color(Colors.WHITE, true);

        this.selectedFilmLabel = new UILabel(() ->
                this.selectedFilm.isEmpty() ? NO_FILM.get() : "\u25b6 " + this.selectedFilm);
        this.selectedFilmLabel.labelAnchor(0F, 0.5F);

        this.withCameraToggle = new UIToggle(WITH_CAM, false, (b) -> {});
        this.doneButton = new UIButton(DONE, (b) -> this.saveAndClose());


        this.container = new UIElement();
        this.container.relative(this)
                .x(0.5F).y(10)
                .w(PANEL_W).h(1F, -20)
                .anchor(0.5F, 0F);

        this.titleLabel.relative(this.container)
                .xy(0, 0).w(1F).h(20);

        this.filmSearch.relative(this.container)
                .xy(0, 24).w(1F).h(1F, -108);

        this.selectedFilmLabel.relative(this.container)
                .x(0).y(1F, -84).w(1F).h(20);

        this.withCameraToggle.relative(this.container)
                .x(0).y(1F, -60).w(1F).h(20);

        this.doneButton.relative(this.container)
                .x(0).y(1F, -36).w(1F).h(20);

        this.container.add(this.titleLabel, this.filmSearch,
                this.selectedFilmLabel, this.withCameraToggle, this.doneButton);
        this.add(this.container);
    }

    @Override
    public void appear()
    {
        super.appear();

        UIDataUtils.requestNames(ContentType.FILMS, (names) ->
        {
            this.filmList.fill(names);
            this.preselectCurrentFilm();
        });
    }

    private void preselectCurrentFilm()
    {
        ItemStack stack = MinecraftClient.getInstance().player.getStackInHand(Hand.MAIN_HAND);
        NbtCompound nbt = stack.getNbt();

        if (nbt != null && nbt.contains("Film"))
        {
            String filmId = nbt.getString("Film");

            if (!filmId.isEmpty())
            {
                this.selectedFilm = filmId;
                this.filmSearch.filter(filmId, true);
            }
        }

        this.withCameraToggle.setValue(nbt != null && nbt.contains("WithCamera") && nbt.getBoolean("WithCamera"));
        this.refreshLabel();
    }

    public void fillCurrentFilm(String filmId)
    {
        this.selectedFilm = (filmId == null) ? "" : filmId;

        if (!this.selectedFilm.isEmpty())
        {
            this.filmSearch.filter(this.selectedFilm, true);
        }

        this.refreshLabel();
    }

    private void refreshLabel()
    {
        this.selectedFilmLabel.color(
                this.selectedFilm.isEmpty() ? Colors.GRAY : Colors.GREEN,
                true);
    }

    private void saveAndClose()
    {
        if (!this.selectedFilm.isEmpty())
        {
            ClientNetwork.sendPlaybackButton(this.selectedFilm, this.withCameraToggle.getValue());
        }

        this.dashboard.closeThisMenu();
    }

    @Override
    public boolean needsBackground()
    {
        return false;
    }
}