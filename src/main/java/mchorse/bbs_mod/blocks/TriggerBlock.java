package mchorse.bbs_mod.blocks;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.blocks.entities.TriggerBlockEntity;
import mchorse.bbs_mod.network.ServerNetwork;
import mchorse.bbs_mod.settings.values.core.ValueList;
import mchorse.bbs_mod.triggers.Trigger;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class TriggerBlock extends Block implements BlockEntityProvider, Waterloggable
{
    public static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> validateTicker(BlockEntityType<A> givenType, BlockEntityType<E> expectedType, BlockEntityTicker<? super E> ticker)
    {
        return expectedType == givenType ? (BlockEntityTicker<A>) ticker : null;
    }

    public TriggerBlock(Settings settings)
    {
        super(settings);

        this.setDefaultState(getDefaultState()
            .with(Properties.WATERLOGGED, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder)
    {
        builder.add(Properties.WATERLOGGED);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx)
    {
        return this.getDefaultState()
            .with(Properties.WATERLOGGED, ctx.getWorld().getFluidState(ctx.getBlockPos()).isOf(Fluids.WATER));
    }

    @Override
    public float getAmbientOcclusionLightLevel(BlockState state, BlockView world, BlockPos pos)
    {
        return 1.0F;
    }

    @Override
    public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state)
    {
        BlockEntity entity = world.getBlockEntity(pos);

        if (entity instanceof TriggerBlockEntity triggerBlock)
        {
            ItemStack stack = new ItemStack(this);
            NbtCompound compound = new NbtCompound();

            compound.put("BlockEntityTag", triggerBlock.createNbtWithId());
            stack.setNbt(compound);

            return stack;
        }

        return super.getPickStack(world, pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state)
    {
        return BlockRenderType.INVISIBLE;
    }

    @Override
    public boolean isTransparent(BlockState state, BlockView world, BlockPos pos)
    {
        return true;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type)
    {
        return type == BBSMod.TRIGGER_BLOCK_ENTITY ? (BlockEntityTicker<T>) (BlockEntityTicker<TriggerBlockEntity>) (w, p, s, e) -> TriggerBlockEntity.tick(w, p, s, e) : null;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state)
    {
        return new TriggerBlockEntity(pos, state);
    }

    @Override
    public void onBlockBreakStart(BlockState state, World world, BlockPos pos, PlayerEntity player)
    {
        if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer && !player.isCreative())
        {
            BlockEntity be = world.getBlockEntity(pos);

            if (be instanceof TriggerBlockEntity triggerBlock)
            {
                triggerBlock.trigger(serverPlayer, false);
            }
        }

        super.onBlockBreakStart(state, world, pos, player);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit)
    {
        if (hand == Hand.MAIN_HAND)
        {
            if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer)
            {
                if (!player.isCreative() || (player.isCreative() && player.isSneaking()))
                {
                    BlockEntity be = world.getBlockEntity(pos);

                    if (be instanceof TriggerBlockEntity triggerBlock)
                    {
                        triggerBlock.trigger(serverPlayer, true);

                        return ActionResult.SUCCESS;
                    }
                }
                else
                {
                    ServerNetwork.sendClickedTriggerBlock(serverPlayer, pos);

                    return ActionResult.SUCCESS;
                }
            }

            return ActionResult.SUCCESS;
        }

        return super.onUse(state, world, pos, player, hand, hit);
    }

    @Override
    public FluidState getFluidState(BlockState state)
    {
        return state.get(Properties.WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context)
    {
        return this.getShape(world, pos);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context)
    {
        BlockEntity be = world.getBlockEntity(pos);

        if (be instanceof TriggerBlockEntity)
        {
            TriggerBlockEntity trigger = (TriggerBlockEntity) be;

            if (!trigger.collidable.get())
            {
                return VoxelShapes.empty();
            }

            return this.getShape(world, pos);
        }

        return super.getCollisionShape(state, world, pos, context);
    }

    @Override
    public void afterBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity be, ItemStack tool)
    {
        if (!world.isClient && !player.getAbilities().creativeMode)
        {
            if (be instanceof TriggerBlockEntity model)
            {
                ItemStack stack = new ItemStack(this);
                NbtCompound wrapper = new NbtCompound();

                wrapper.put("BlockEntityTag", model.createNbtWithId());
                stack.setNbt(wrapper);

                ItemScatterer.spawn(world, pos, DefaultedList.ofSize(1, stack));
            }
        }

        super.afterBreak(world, player, pos, state, be, tool);
    }

    private VoxelShape getShape(BlockView world, BlockPos pos)
    {
        BlockEntity be = world.getBlockEntity(pos);

        if (be instanceof TriggerBlockEntity)
        {
            TriggerBlockEntity trigger = (TriggerBlockEntity) be;
            Vector3f min = trigger.pos1.get();
            Vector3f max = trigger.pos2.get();

            double minX = Math.min(min.x, max.x);
            double minY = Math.min(min.y, max.y);
            double minZ = Math.min(min.z, max.z);
            double maxX = Math.max(min.x, max.x);
            double maxY = Math.max(min.y, max.y);
            double maxZ = Math.max(min.z, max.z);

            return VoxelShapes.cuboid(minX, minY, minZ, maxX, maxY, maxZ);
        }

        return VoxelShapes.fullCube();
    }
}