package mchorse.bbs_mod.client.renderer.item;

import mchorse.bbs_mod.BBSMod;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class PlaybackItemRenderer  implements BuiltinItemRendererRegistry.DynamicItemRenderer
{
    private Map<ItemStack, PlaybackItemRenderer.Item> map = new HashMap<>();

    public void update()
    {}

    @Override
    public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay)
    {
        PlaybackItemRenderer.Item item = this.get(stack);

        if (item != null)
        {}
    }

    public PlaybackItemRenderer.Item get(ItemStack stack)
    {
        if (stack == null || stack.getItem() != BBSMod.PLAYBACK_ITEM)
        {
            return null;
        }

        if (this.map.containsKey(stack))
        {
            return this.map.get(stack);
        }

        PlaybackItemRenderer.Item item = new PlaybackItemRenderer.Item();
        this.map.put(stack, item);
        return item;
    }

    public static class Item
    {
        public Item()
        {
        }
    }
}
