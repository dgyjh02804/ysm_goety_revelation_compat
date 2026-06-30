package com.dgy.ysmgoetycompat.mixin;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Accessor mixin to expose the private {@code layers} field in {@link LivingEntityRenderer}.
 * This gives us the full list of render layers (including CuriosLayer, PlayerHaloLayer, etc.)
 * so we can manually invoke them after YSM finishes rendering a player.
 */
@Mixin(LivingEntityRenderer.class)
public interface AccessorLivingEntityRenderer<T extends LivingEntity, M extends EntityModel<T>> {

    @Accessor("layers")
    List<RenderLayer<T, M>> getLayers();

    @Accessor("model")
    M getModel();
}
