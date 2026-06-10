package mchorse.bbs_mod.items.playback;

import mchorse.bbs_mod.network.ServerNetwork;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class PlaybackItem extends Item
{
    public PlaybackItem(Settings settings)
    {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand)
    {
        ItemStack stack = user.getStackInHand(hand);

        if (world.isClient)
        {
            return TypedActionResult.success(stack);
        }

        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) user;
        NbtCompound nbt = stack.getNbt();

        if (user.isSneaking())
        {
            String currentFilm = (nbt != null && nbt.contains("Film")) ? nbt.getString("Film") : "";

            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(currentFilm);
            ServerPlayNetworking.send(serverPlayer, ServerNetwork.CLIENT_OPEN_PLAYBACK_PANEL, buf);
        }
        else
        {
            if (nbt == null || !nbt.contains("Film"))
            {
                return TypedActionResult.pass(stack);
            }
            String filmId = nbt.getString("Film");
            if (filmId.isEmpty())
            {
                return TypedActionResult.pass(stack);
            }

            boolean withCamera = nbt.contains("WithCamera") && nbt.getBoolean("WithCamera");

            ServerNetwork.sendPlayFilm(serverPlayer, filmId, withCamera);
        }

        return TypedActionResult.success(stack);
    }
}