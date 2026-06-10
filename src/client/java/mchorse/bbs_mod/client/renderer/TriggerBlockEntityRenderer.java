package mchorse.bbs_mod.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.blocks.entities.TriggerBlockEntity;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.graphics.Draw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;

public class TriggerBlockEntityRenderer implements BlockEntityRenderer<TriggerBlockEntity> {

    public TriggerBlockEntityRenderer(BlockEntityRendererFactory.Context ctx)
    {}

    @Override
    public void render(TriggerBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        BBSRendering.capturedTriggerBlocks.add(entity);

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.getDebugHud().shouldShowDebugHud())
        {
            matrices.push();
            matrices.translate(0.5D, 0, 0.5D);
            Draw.renderBox(matrices, -0.5D, 0, -0.5D, 1, 1, 1, 0, 1F, 0.5F, 0.5F);
            matrices.pop();

            if (entity.region.get())
            {
                Box box = entity.getRegionBoxRelative();

                RenderSystem.disableDepthTest();
                Draw.renderBox(matrices, box.minX, box.minY, box.minZ, box.maxX - box.minX, box.maxY - box.minY, box.maxZ - box.minZ, 1F, 1F, 1F, 0.5F);
                RenderSystem.enableDepthTest();
            }
        }
    }
}
