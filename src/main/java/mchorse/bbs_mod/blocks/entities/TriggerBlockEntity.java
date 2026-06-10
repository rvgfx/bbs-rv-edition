package mchorse.bbs_mod.blocks.entities;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.events.TriggerBlockEntityUpdateCallback;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.network.ServerNetwork;
import mchorse.bbs_mod.settings.values.core.ValueList;
import mchorse.bbs_mod.settings.values.misc.ValueVector3f;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.triggers.Trigger;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.*;

public class TriggerBlockEntity extends BlockEntity
{
    private IEntity entity = new StubEntity();

    public String getName()
    {
        BlockPos pos = this.getPos();
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }


    public final ValueList<Trigger> left_click = new ValueList<Trigger>("left_click") {
        @Override
        protected Trigger create(String id) {
            return new Trigger(id);
        }
    };

    public final ValueList<Trigger> right_click = new ValueList<Trigger>("right_click") {
        @Override
        protected Trigger create(String id) {
            return new Trigger(id);
        }
    };

    public final ValueList<Trigger> enter = new ValueList<Trigger>("enter")
    {
        @Override
        protected Trigger create(String id)
        {
            return new Trigger(id);
        }
    };

    public final ValueList<Trigger> exit = new ValueList<Trigger>("exit")
    {
        @Override
        protected Trigger create(String id)
        {
            return new Trigger(id);
        }
    };

    public final ValueList<Trigger> whileIn = new ValueList<Trigger>("whileIn")
    {
        @Override
        protected Trigger create(String id)
        {
            return new Trigger(id);
        }
    };

    public final ValueBoolean collidable = new ValueBoolean("collidable", false);
    public final ValueBoolean region = new ValueBoolean("region", false);
    public final ValueInt regionDelay = new ValueInt("regionDelay", 15);
    public final ValueVector3f pos1 = new ValueVector3f("pos1", new Vector3f(0, 0, 0));
    public final ValueVector3f pos2 = new ValueVector3f("pos2", new Vector3f(1, 1, 1));
    public final ValueVector3f regionOffset = new ValueVector3f("regionOffset", new Vector3f(0, 0, 0));
    public final ValueVector3f regionSize = new ValueVector3f("regionSize", new Vector3f(1, 1, 1));
    public final ValueInt entityType = new ValueInt("entityType",0);

    private Set<UUID> entitiesInRegion = new HashSet<>();
    private Map<UUID, Long> regionNextTriggerTick = new HashMap<>();

    public TriggerBlockEntity(BlockPos pos, BlockState state)
    {
        super(BBSMod.TRIGGER_BLOCK_ENTITY, pos, state);
    }

    public void trigger(ServerPlayerEntity player, boolean rightClick)
    {
        this.trigger(player, rightClick ? this.right_click.getList() : this.left_click.getList());
    }

    public void trigger(ServerPlayerEntity player, List<Trigger> triggers)
    {
        this.triggerEntity(player, triggers);
    }

    public void triggerEntity(Entity entity, List<Trigger> triggers)
    {
        if (entity instanceof ServerPlayerEntity player)
        {

            for (Trigger trigger : triggers)
            {
                String type = trigger.type.get();

                if (type.equals("command"))
                {
                    String cmd = trigger.command.get();

                    if (!cmd.isEmpty())
                    {
                        try
                        {
                            player.getServer().getCommandManager().executeWithPrefix(player.getCommandSource().withLevel(2), cmd);
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                        }
                    }
                }
                else if (type.equals("form"))
                {
                    Form form = trigger.form.get();

                    ServerNetwork.sendMorphToTracked(player, form);
                    Morph.getMorph(player).setForm(FormUtils.copy(form));
                }
                else if (type.equals("block"))
                {
                    int x = trigger.x.get();
                    int y = trigger.y.get();
                    int z = trigger.z.get();
                    Form form = trigger.blockForm.get();

                    BlockPos pos = new BlockPos(x, y, z);

                    if (this.world.isChunkLoaded(pos))
                    {
                        BlockEntity be = this.world.getBlockEntity(pos);

                        if (be instanceof ModelBlockEntity modelBlock)
                        {
                            modelBlock.getProperties().setForm(FormUtils.copy(form));
                            modelBlock.markDirty();
                            this.world.updateListeners(pos, this.world.getBlockState(pos), this.world.getBlockState(pos), 3);
                        }
                    }
                }
                else if (type.equals("film"))
                {
                    String filmName = trigger.film.get();
                    boolean playCamera = trigger.playCamera.get();

                    if (!filmName.isEmpty())
                    {
                        ServerNetwork.sendPlayFilm(player, filmName, playCamera);
                    }
                }
            }
        }
        else
        {
            for (Trigger trigger : triggers)
            {
                String type = trigger.type.get();

                if (type.equals("command"))
                {
                    String cmd = trigger.command.get();

                    if (!cmd.isEmpty())
                    {
                        // Substitute entity placeholders
                        cmd = cmd
                                .replace("%player%", entity.getNameForScoreboard())
                                .replace("%uuid%", entity.getUuidAsString())
                                .replace("%x%", String.valueOf(entity.getBlockPos().getX()))
                                .replace("%y%", String.valueOf(entity.getBlockPos().getY()))
                                .replace("%z%", String.valueOf(entity.getBlockPos().getZ()));

                        try
                        {
                            entity.getServer().getCommandManager().executeWithPrefix(
                                    entity.getServer().getCommandSource(), cmd
                            );
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    public static void tick(World world, BlockPos pos, BlockState state, TriggerBlockEntity blockEntity)
    {
        if (!world.isClient && blockEntity.region.get())
        {
            blockEntity.tickRegion();
        }

        TriggerBlockEntityUpdateCallback.EVENT.invoker().update(blockEntity);
    }

    public Box getRegionBox()
    {
        return this.getRegionBox(this.pos.getX(), this.pos.getY(), this.pos.getZ());
    }

    public Box getRegionBoxRelative()
    {
        return this.getRegionBox(0, 0, 0);
    }

    public Box getRegionBox(double x, double y, double z)
    {
        Vector3f offset = this.regionOffset.get();
        Vector3f size = this.regionSize.get();

        double expansion = 1.0;
        double minX = offset.x + 0.5 - size.x / 2.0 - expansion;
        double minY = offset.y + 0.5 - size.y / 2.0 - expansion;
        double minZ = offset.z + 0.5 - size.z / 2.0 - expansion;
        double maxX = offset.x + 0.5 + size.x / 2.0 + expansion;
        double maxY = offset.y + 0.5 + size.y / 2.0 + expansion;
        double maxZ = offset.z + 0.5 + size.z / 2.0 + expansion;

        return new Box(
                x + minX, y + minY, z + minZ,
                x + maxX, y + maxY, z + maxZ
        );
    }

    private void tickRegion()
    {
        Box box = this.getRegionBox();
        int type = this.entityType.get();
        long time = this.world.getTime();

        List<Entity> candidates = new ArrayList<>();

        if (type == 0 || type == 1)
        {
            candidates.addAll(this.world.getEntitiesByClass(ServerPlayerEntity.class, box, (p) -> true));
        }

        if (type == 0 || type == 2)
        {
            this.world.getEntitiesByClass(Entity.class, box, (e) -> !(e instanceof ServerPlayerEntity))
                    .forEach(candidates::add);
        }

        Set<UUID> currentEntities = new HashSet<>();

        for (Entity entity : candidates)
        {
            UUID uuid = entity.getUuid();
            currentEntities.add(uuid);

            boolean isNew = !this.entitiesInRegion.contains(uuid);
            long nextTick = this.regionNextTriggerTick.getOrDefault(uuid, 0L);

            if (isNew)
            {
                this.triggerEntity(entity, this.enter.getList());
                this.regionNextTriggerTick.put(uuid, time + this.regionDelay.get());
            }
            else if (time >= nextTick)
            {
                this.triggerEntity(entity, this.whileIn.getList());
                this.regionNextTriggerTick.put(uuid, time + this.regionDelay.get());
            }
        }

        for (UUID uuid : this.entitiesInRegion)
        {
            if (!currentEntities.contains(uuid))
            {
                List<Entity> found = this.world.getEntitiesByClass(Entity.class,
                        box.expand(64), (e) -> e.getUuid().equals(uuid));

                if (!found.isEmpty())
                {
                    this.triggerEntity(found.get(0), this.exit.getList());
                }
                else if (type == 0 || type == 1)
                {
                    ServerPlayerEntity player = (ServerPlayerEntity) this.world.getPlayerByUuid(uuid);

                    if (player != null)
                    {
                        this.triggerEntity(player, this.exit.getList());
                    }
                }

                this.regionNextTriggerTick.remove(uuid);
            }
        }

        this.entitiesInRegion = currentEntities;
    }

    @Override
    public void readNbt(NbtCompound nbt)
    {
        super.readNbt(nbt);

        if (nbt.contains("Left")) this.left_click.fromData(DataStorageUtils.fromNbt(nbt.get("Left")));
        if (nbt.contains("Right")) this.right_click.fromData(DataStorageUtils.fromNbt(nbt.get("Right")));
        if (nbt.contains("Enter")) this.enter.fromData(DataStorageUtils.fromNbt(nbt.get("Enter")));
        if (nbt.contains("Exit")) this.exit.fromData(DataStorageUtils.fromNbt(nbt.get("Exit")));
        if (nbt.contains("WhileIn")) this.whileIn.fromData(DataStorageUtils.fromNbt(nbt.get("WhileIn")));
        if (nbt.contains("RegionDelay")) this.regionDelay.set(nbt.getInt("RegionDelay"));
        if (nbt.contains("Collidable")) this.collidable.set(nbt.getBoolean("Collidable"));
        if (nbt.contains("Region")) this.region.set(nbt.getBoolean("Region"));
        if (nbt.contains("Pos1")) this.pos1.fromData(DataStorageUtils.fromNbt(nbt.get("Pos1")));
        if (nbt.contains("Pos2")) this.pos2.fromData(DataStorageUtils.fromNbt(nbt.get("Pos2")));
        if (nbt.contains("RegionOffset")) this.regionOffset.fromData(DataStorageUtils.fromNbt(nbt.get("RegionOffset")));
        if (nbt.contains("RegionSize")) this.regionSize.fromData(DataStorageUtils.fromNbt(nbt.get("RegionSize")));
        if (nbt.contains("EntityType")) this.entityType.fromData(DataStorageUtils.fromNbt(nbt.get("EntityType")));
    }

    @Override
    public void writeNbt(NbtCompound nbt)
    {
        super.writeNbt(nbt);

        nbt.put("Left", DataStorageUtils.toNbt(this.left_click.toData()));
        nbt.put("Right", DataStorageUtils.toNbt(this.right_click.toData()));
        nbt.put("Enter", DataStorageUtils.toNbt(this.enter.toData()));
        nbt.put("Exit", DataStorageUtils.toNbt(this.exit.toData()));
        nbt.put("WhileIn", DataStorageUtils.toNbt(this.whileIn.toData()));
        nbt.putInt("RegionDelay", this.regionDelay.get());
        nbt.putBoolean("Collidable", this.collidable.get());
        nbt.putBoolean("Region", this.region.get());
        nbt.put("Pos1", DataStorageUtils.toNbt(this.pos1.toData()));
        nbt.put("Pos2", DataStorageUtils.toNbt(this.pos2.toData()));
        nbt.put("RegionOffset", DataStorageUtils.toNbt(this.regionOffset.toData()));
        nbt.put("RegionSize", DataStorageUtils.toNbt(this.regionSize.toData()));
        nbt.put("EntityType", DataStorageUtils.toNbt(this.entityType.toData()));
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket()
    {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt()
    {
        return this.createNbt();
    }
}