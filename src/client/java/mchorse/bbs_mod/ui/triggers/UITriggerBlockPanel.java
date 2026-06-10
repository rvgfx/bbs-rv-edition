package mchorse.bbs_mod.ui.triggers;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.blocks.entities.TriggerBlockEntity;
import mchorse.bbs_mod.camera.CameraUtils;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.renderer.TriggerBlockEntityRenderer;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.settings.values.core.ValueList;
import mchorse.bbs_mod.triggers.Trigger;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.IFlightSupported;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.utils.AABB;
import mchorse.bbs_mod.utils.PlayerUtils;
import mchorse.bbs_mod.utils.RayTracing;
import mchorse.bbs_mod.utils.colors.Colors;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.irisshaders.iris.helpers.Tri;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UITriggerBlockPanel extends UIDashboardPanel implements IFlightSupported {

    private Set<TriggerBlockEntity> toSave = new HashSet<>();

    private TriggerBlockEntity triggerBlock;
    public UITriggerBlockEntityList list;

    public UIScrollView scrollView;
    public UIPropTransform transform;
    public UIToggle region;
    public UIButton left_click;
    public UIButton right_click;
    public UIButton enter;
    public UIButton exit;
    public UIButton whileIn;
    public UITrackpad regionDelay;
    public UIToggle collidable;

    public UIButton entityType;

    public UIElement hitboxLabel;
    public UIElement pos1Label;
    public UIElement pos2Label;
    public UIElement shapeLabel;
    public UIElement delayLabel;
    public UIElement offsetLabel;
    public UIElement sizeLabel;
    public UIElement typeLabel;
    public UITrackpad x1, y1, z1;
    public UITrackpad x2, y2, z2;
    public UITrackpad ox, oy, oz;
    public UITrackpad sx, sy, sz;

    /* Content groups handed out to the panel's two editor cards. */
    public final UIElement actionsContent;
    public final UIElement geometryContent;

    private TriggerBlockEntity hovered;

    public UITriggerBlockPanel(UIDashboard dashboard) {
        super(dashboard);
        this.list = new UITriggerBlockEntityList((l) -> this.fill(l.get(0), false))
        {
            @Override
            public void render(UIContext context)
            {
                context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xFF141418);
                super.render(context);
                context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xFF3C3C3C);
            }
        };
        this.list.context((menu) ->
        {
            if (this.triggerBlock != null) menu.action(UIKeys.MODEL_BLOCKS_KEYS_TELEPORT, this::teleport);
        });


        this.list.background();
        this.list.h(UIStringList.DEFAULT_HEIGHT * 9);

        this.collidable = new UIToggle(UIKeys.COLLIDABLE, false, (b) ->
        {
            if (this.triggerBlock != null)
            {
                this.triggerBlock.collidable.set(b.getValue());
                this.save();
            }
        });

        this.region = new UIToggle(UIKeys.REGION, false, (b) ->
        {
            if (this.triggerBlock != null)
            {
                this.triggerBlock.region.set(b.getValue());
                this.updateButtons();
                this.save();
            }
        });

        this.left_click = new UIButton(UIKeys.TRIGGER_LEFT_CLICK, (b) -> { if (this.triggerBlock != null) this.createOverlay(this.triggerBlock.left_click); });
        this.right_click = new UIButton(UIKeys.TRIGGER_RIGHT_CLICK, (b) -> { if (this.triggerBlock != null) this.createOverlay(this.triggerBlock.right_click); });
        this.enter = new UIButton(UIKeys.ON_ENTER, (b) -> { if (this.triggerBlock != null) this.createOverlay(this.triggerBlock.enter); });
        this.exit = new UIButton(UIKeys.ON_EXIT, (b) -> { if (this.triggerBlock != null) this.createOverlay(this.triggerBlock.exit); });
        this.whileIn = new UIButton(UIKeys.WHILE_IN, (b) -> { if (this.triggerBlock != null) this.createOverlay(this.triggerBlock.whileIn); });
        this.entityType = new UIButton(UIKeys.ENTITY_TYPE_0, (b) -> { if (this.triggerBlock != null) this.setEntityType(this.triggerBlock); });
        this.regionDelay = new UITrackpad((v) -> { if (this.triggerBlock != null) { this.triggerBlock.regionDelay.set(v.intValue()); this.save(); } }).limit(0, 1000).integer();
        this.regionDelay.tooltip(UIKeys.REGION_DELAY);
        this.typeLabel = UI.label(UIKeys.ENTITY_TITLE);

        this.transform = new UIPropTransform();
        this.transform.enableHotkeys();



        this.x1 = new UITrackpad((v) -> { if (this.triggerBlock != null) { this.triggerBlock.pos1.set(v.floatValue(), this.triggerBlock.pos1.get().y, this.triggerBlock.pos1.get().z); this.save(); } }).limit(0, 1).increment(0.1);
        this.y1 = new UITrackpad((v) -> { if (this.triggerBlock != null) { this.triggerBlock.pos1.set(this.triggerBlock.pos1.get().x, v.floatValue(), this.triggerBlock.pos1.get().z); this.save(); } }).limit(0, 1).increment(0.1);
        this.z1 = new UITrackpad((v) -> { if (this.triggerBlock != null) { this.triggerBlock.pos1.set(this.triggerBlock.pos1.get().x, this.triggerBlock.pos1.get().y, v.floatValue()); this.save(); } }).limit(0, 1).increment(0.1);

        this.x2 = new UITrackpad((v) -> { if (this.triggerBlock != null) { this.triggerBlock.pos2.set(v.floatValue(), this.triggerBlock.pos2.get().y, this.triggerBlock.pos2.get().z); this.save(); } }).limit(0, 1).increment(0.1);
        this.y2 = new UITrackpad((v) -> { if (this.triggerBlock != null) { this.triggerBlock.pos2.set(this.triggerBlock.pos2.get().x, v.floatValue(), this.triggerBlock.pos2.get().z); this.save(); } }).limit(0, 1).increment(0.1);
        this.z2 = new UITrackpad((v) -> { if (this.triggerBlock != null) { this.triggerBlock.pos2.set(this.triggerBlock.pos2.get().x, this.triggerBlock.pos2.get().y, v.floatValue()); this.save(); } }).limit(0, 1).increment(0.1);

        this.ox = new UITrackpad((v) -> { if (this.triggerBlock != null) { this.triggerBlock.regionOffset.set(v.floatValue(), this.triggerBlock.regionOffset.get().y, this.triggerBlock.regionOffset.get().z); this.save(); } }).increment(0.1);
        this.oy = new UITrackpad((v) -> { if (this.triggerBlock != null) { this.triggerBlock.regionOffset.set(this.triggerBlock.regionOffset.get().x, v.floatValue(), this.triggerBlock.regionOffset.get().z); this.save(); } }).increment(0.1);
        this.oz = new UITrackpad((v) -> { if (this.triggerBlock != null) { this.triggerBlock.regionOffset.set(this.triggerBlock.regionOffset.get().x, this.triggerBlock.regionOffset.get().y, v.floatValue()); this.save(); } }).increment(0.1);

        this.sx = new UITrackpad((v) -> { if (this.triggerBlock != null) { this.triggerBlock.regionSize.set(v.floatValue(), this.triggerBlock.regionSize.get().y, this.triggerBlock.regionSize.get().z); this.save(); } }).increment(0.1);
        this.sy = new UITrackpad((v) -> { if (this.triggerBlock != null) { this.triggerBlock.regionSize.set(this.triggerBlock.regionSize.get().x, v.floatValue(), this.triggerBlock.regionSize.get().z); this.save(); } }).increment(0.1);
        this.sz = new UITrackpad((v) -> { if (this.triggerBlock != null) { this.triggerBlock.regionSize.set(this.triggerBlock.regionSize.get().x, this.triggerBlock.regionSize.get().y, v.floatValue()); this.save(); } }).increment(0.1);

        this.x1.textbox.setColor(Colors.RED);
        this.y1.textbox.setColor(Colors.GREEN);
        this.z1.textbox.setColor(Colors.BLUE);
        this.x2.textbox.setColor(Colors.RED);
        this.y2.textbox.setColor(Colors.GREEN);
        this.z2.textbox.setColor(Colors.BLUE);
        this.ox.textbox.setColor(Colors.RED);
        this.oy.textbox.setColor(Colors.GREEN);
        this.oz.textbox.setColor(Colors.BLUE);
        this.sx.textbox.setColor(Colors.RED);
        this.sy.textbox.setColor(Colors.GREEN);
        this.sz.textbox.setColor(Colors.BLUE);

        this.hitboxLabel = sectionHeader(UIKeys.HITBOX);
        this.pos1Label = UI.label(UIKeys.POS1);
        this.pos2Label = UI.label(UIKeys.POS2);
        this.shapeLabel = sectionHeader(UIKeys.SHAPE);
        this.delayLabel = UI.label(UIKeys.REGION_DELAY);
        this.offsetLabel = UI.label(UIKeys.OFFSET);
        this.sizeLabel = UI.label(UIKeys.SIZE);

        /* Actions card: event handlers + region toggles + delay. */
        this.actionsContent = UI.column(5,
                UI.row(4, this.left_click, this.right_click),
                UI.row(4, this.enter, this.exit),
                this.whileIn,
                UI.row(4, this.collidable, this.region),
                this.typeLabel,
                this.entityType,
                this.delayLabel,
                this.regionDelay);

        /* Geometry card: hitbox + region shape positioning. */
        this.geometryContent = UI.column(5,
                this.hitboxLabel,
                this.pos1Label,
                UI.row(4, this.x1, this.y1, this.z1),
                this.pos2Label,
                UI.row(4, this.x2, this.y2, this.z2),
                this.shapeLabel,
                this.offsetLabel,
                UI.row(4, this.ox, this.oy, this.oz),
                this.sizeLabel,
                UI.row(4, this.sx, this.sy, this.sz));

        this.scrollView = UI.scrollView(UIConstants.MARGIN, UIConstants.SCROLL_PADDING, this.list, this.actionsContent, this.geometryContent);
        this.scrollView.scroll.opposite().cancelScrolling();
        this.scrollView.relative(this).w(200).h(1F);

        this.keys().register(Keys.MODEL_BLOCKS_TELEPORT, this::teleport);

        this.updateButtons();
        this.fill(null, false);
        this.add(this.scrollView);
    }

    private static UIElement sectionHeader(IKey label)
    {
        UILabel header = UI.label(label)
                .labelAnchor(0, 1)
                .color(0xFF000000 | BBSSettings.primaryColor.get())
                .background(() -> 0xFF1A1A22);

        header.h(20).marginTop(8);

        return header;
    }

    @Override
    public void open()
    {
        super.open();

        this.updateList();

        if (this.triggerBlock != null && this.triggerBlock.isRemoved())
        {
            this.fill(null, true);
        }
    }

    @Override
    public void close()
    {
        super.close();

        if (this.triggerBlock != null) this.toSave.add(this.triggerBlock);

        for (TriggerBlockEntity entity : this.toSave) this.save(entity);

        this.toSave.clear();
    }

    @Override
    public boolean needsBackground()
    {
        return false;
    }

    private void updateList()
    {
        this.list.clear();
        this.list.add(BBSRendering.capturedTriggerBlocks);

        if (this.triggerBlock != null && !this.triggerBlock.isRemoved())
        {
            if (!this.list.getList().contains(this.triggerBlock)) this.list.add(this.triggerBlock);
            this.list.setCurrentScroll(this.triggerBlock);
        }
        else
        {
            this.fill(null, false);
        }
    }

    private void teleport()
    {
        if (this.triggerBlock != null)
        {
            BlockPos pos = this.triggerBlock.getPos();

            PlayerUtils.teleport(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
            UIUtils.playClick();
        }
    }

    public void fill(TriggerBlockEntity triggerBlock, boolean select)
    {
        if (triggerBlock != null)
        {
            this.toSave.add(triggerBlock);
        }

        this.triggerBlock = triggerBlock;
        this.setEntity(triggerBlock);
        if (select) this.list.setCurrentScroll(triggerBlock);
        this.resize();
    }

    private void renderBox(MatrixStack stack, TriggerBlockEntity entity, float r, float g, float b)
    {
        BlockPos bp = entity.getPos();
        Vector3f p1 = entity.pos1.get();
        Vector3f p2 = entity.pos2.get();

        double minX = Math.min(p1.x, p2.x);
        double minY = Math.min(p1.y, p2.y);
        double minZ = Math.min(p1.z, p2.z);
        double maxX = Math.max(p1.x, p2.x);
        double maxY = Math.max(p1.y, p2.y);
        double maxZ = Math.max(p1.z, p2.z);

        double x = bp.getX() + minX;
        double y = bp.getY() + minY;
        double z = bp.getZ() + minZ;
        double w = maxX - minX;
        double h = maxY - minY;
        double d = maxZ - minZ;

        if (r == -1) Draw.renderBox(stack, x, y, z, w, h, d);
        else Draw.renderBox(stack, x, y, z, w, h, d, r, g, b);
    }

    private void renderRegionBox(MatrixStack stack, TriggerBlockEntity entity, float r, float g, float b)
    {
        Box box = entity.getRegionBox();
        Draw.renderBox(stack, box.minX, box.minY, box.minZ, box.maxX - box.minX, box.maxY - box.minY, box.maxZ - box.minZ, r, g, b);
    }

    protected double getDistance(TriggerBlockEntity object, Vector3d pos, Vector3f dir)
    {
        return RayTracing.intersect(pos, dir, this.getHitbox(object));
    }


    private TriggerBlockEntity getClosestObject(Vector3d finalPosition, Vector3f mouseDirection)
    {
        TriggerBlockEntity closest = null;

        for (TriggerBlockEntity object : this.list.getList())
        {
            AABB aabb = this.getHitbox(object);

            if (aabb.intersectsRay(finalPosition, mouseDirection))
            {
                if (closest == null)
                {
                    closest = object;
                }
                else
                {
                    AABB aabb2 = this.getHitbox(closest);

                    if (finalPosition.distanceSquared(aabb.x, aabb.y, aabb.z) < finalPosition.distanceSquared(aabb2.x, aabb2.y, aabb2.z))
                    {
                        closest = object;
                    }
                }
            }
        }
        return closest;
    }

    private AABB getHitbox(TriggerBlockEntity closest)
    {
        BlockPos pos = closest.getPos();

        return new AABB(pos.getX(), pos.getY(), pos.getZ(), 1D, 1D, 1D);
    }

    public boolean isEditing(TriggerBlockEntity entity)
    {
        if (this.triggerBlock == entity)
        {
            List<UIFormPalette> children = this.getChildren(UIFormPalette.class);

            if (!children.isEmpty())
            {
                return children.get(0).editor.isEditing();
            }
        }

        return false;
    }

    public void setEntityType(TriggerBlockEntity entity) {
        if (entity.entityType.get() < 2) {
            entity.entityType.set(entity.entityType.get() + 1);
        } else
            entity.entityType.set(0);
        this.entityType.label = getEntityKey(entity);
    }

    @Override
    public void renderInWorld(WorldRenderContext context)
    {
        super.renderInWorld(context);

        this.hovered = null;

        MinecraftClient mc = MinecraftClient.getInstance();
        Camera camera = mc.gameRenderer.getCamera();
        Vec3d pos = camera.getPos();

        Vector3f mouseDirection = CameraUtils.getMouseDirection(
                RenderSystem.getProjectionMatrix(),
                context.matrixStack().peek().getPositionMatrix(),
                (int) mc.mouse.getX(), (int) mc.mouse.getY(), 0, 0, mc.getWindow().getWidth(), mc.getWindow().getHeight()
        );

        this.hovered = this.getClosestObject(new Vector3d(pos.x, pos.y, pos.z), mouseDirection);

        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        context.matrixStack().push();
        context.matrixStack().translate(-pos.x, -pos.y, -pos.z);

        if (this.triggerBlock != null)
        {
            this.renderBox(context.matrixStack(), this.triggerBlock, 0F, 1F, 0F);

            if (this.triggerBlock.region.get())
            {
                RenderSystem.disableDepthTest();
                this.renderRegionBox(context.matrixStack(), this.triggerBlock, 1F, 1F, 1F);
                RenderSystem.enableDepthTest();
            }
        }

        for (TriggerBlockEntity entity : BBSRendering.capturedTriggerBlocks)
        {
            if (this.triggerBlock == entity) continue;

            if (this.hovered == entity) this.renderBox(context.matrixStack(), entity, 0F, 1F, 0F);
            else this.renderBox(context.matrixStack(), entity, -1F, -1F, -1F);
        }

        context.matrixStack().pop();

        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();
    }

    private void updateButtons()
    {

        boolean hasEntity = this.triggerBlock != null;
        boolean region = hasEntity && this.region.getValue();

        this.left_click.setEnabled(hasEntity);
        this.right_click.setEnabled(hasEntity);

        this.entityType.setEnabled(region);
        this.enter.setEnabled(region);
        this.exit.setEnabled(region);
        this.whileIn.setEnabled(region);
        this.delayLabel.setEnabled(region);
        this.regionDelay.setEnabled(region);
        this.shapeLabel.setEnabled(region);
        this.ox.setEnabled(region);
        this.oy.setEnabled(region);
        this.oz.setEnabled(region);
        this.sx.setEnabled(region);
        this.sy.setEnabled(region);
        this.sz.setEnabled(region);
        this.offsetLabel.setEnabled(region);
        this.sizeLabel.setEnabled(region);
    }

    public IKey getEntityKey(TriggerBlockEntity entity) {
        if (entity.entityType.get() == 0) {
            return UIKeys.ENTITY_TYPE_0;
        } else if (entity.entityType.get() == 1) {
            return UIKeys.ENTITY_TYPE_1;
        } else
            return UIKeys.ENTITY_TYPE_2;
    }

    public void setEntity(TriggerBlockEntity entity)
    {
        this.triggerBlock = entity;
        this.updateButtons();

        if (entity != null)
        {
            this.collidable.setValue(entity.collidable.get());
            this.region.setValue(entity.region.get());

            this.x1.setValue(entity.pos1.get().x);
            this.y1.setValue(entity.pos1.get().y);
            this.z1.setValue(entity.pos1.get().z);

            this.x2.setValue(entity.pos2.get().x);
            this.y2.setValue(entity.pos2.get().y);
            this.z2.setValue(entity.pos2.get().z);

            this.ox.setValue(entity.regionOffset.get().x);
            this.oy.setValue(entity.regionOffset.get().y);
            this.oz.setValue(entity.regionOffset.get().z);

            this.sx.setValue(entity.regionSize.get().x);
            this.sy.setValue(entity.regionSize.get().y);
            this.sz.setValue(entity.regionSize.get().z);
            this.regionDelay.setValue(entity.regionDelay.get());
            this.entityType.label = getEntityKey(entity);

            this.updateButtons();
        }
    }

    private void save()
    {
        if (this.triggerBlock != null)
        {
            ClientNetwork.sendTriggerBlockUpdate(this.triggerBlock.getPos(), this.triggerBlock);
        }
    }

    private void save(TriggerBlockEntity entity)
    {
        if (entity != null) ClientNetwork.sendTriggerBlockUpdate(entity.getPos(), entity);
    }

    private void createOverlay(ValueList<Trigger> list)
    {
        UIOverlay.addOverlay(this.actionsContent.getContext(),new UITriggerOverlay(list, this::save), 300, 250);
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (super.subMouseClicked(context)) return true;

        if (this.hovered != null && context.mouseButton == 0)
        {
            this.fill(this.hovered, true);
            return true;
        }

        return false;
    }
}
