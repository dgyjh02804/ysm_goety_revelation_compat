package com.dgy.ysmgoetycompat;

import com.dgy.ysmgoetycompat.mixin.AccessorLivingEntityRenderer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Core rendering bridge that re-plays ALL render layers from the vanilla
 * {@link PlayerRenderer} on top of YSM-customized player models.
 * <p>
 * When YSM renders a player, it bypasses {@code PlayerRenderer.render()}
 * entirely via its {@code EntityRenderDispatcherMixin}. As a result, EVERY
 * {@link RenderLayer} attached to the PlayerRenderer — including
 * {@code PlayerHaloLayer}, {@code PlayerInvulLayer}, {@code CuriosLayer}
 * (which drives {@code OdamaneHaloLayer} for the {@code halo_of_the_end} item),
 * and any other mod-added layers — is never called.
 * <p>
 * This class retrieves the vanilla PlayerRenderer (still registered in
 * {@link EntityRenderDispatcher}), uses an {@link AccessorLivingEntityRenderer}
 * mixin to obtain the full layer list and model, animates the vanilla
 * {@link PlayerModel} so that layers which copy properties from the parent
 * model (e.g. {@code PlayerInvulLayer}) get correct head rotations, and then
 * calls {@code render()} on every layer.
 */
@OnlyIn(Dist.CLIENT)
public class GoetyLayerRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Cached layer list and model — rebuilt on resource reload or PlayerRenderer change
    private static PlayerRenderer cachedPlayerRenderer;
    private static List<RenderLayer<AbstractClientPlayer, ?>> cachedLayers;
    private static PlayerModel<AbstractClientPlayer> cachedModel;

    /**
     * Quick pre-check. Each layer does its own internal "should I render?"
     * check anyway, so we just return true for YSM players.
     */
    public static boolean hasAnyGoetyEffect(AbstractClientPlayer player) {
        return true;
    }

    /**
     * Render ALL layers from the vanilla PlayerRenderer on a player
     * that was rendered by a non-vanilla renderer (e.g. YSM).
     * <p>
     * Before calling layers, we animate the vanilla {@link PlayerRenderer}'s
     * {@link PlayerModel} with {@code setupAnim()} so that layers which call
     * {@code getParentModel().copyPropertiesTo(localModel)} — such as
     * {@code PlayerInvulLayer} — pick up the correct head rotation.
     */
    public static void renderEffects(
            AbstractClientPlayer player,
            com.mojang.blaze3d.vertex.PoseStack poseStack,
            net.minecraft.client.renderer.MultiBufferSource bufferSource,
            int packedLight,
            float partialTick
    ) {
        List<RenderLayer<AbstractClientPlayer, ?>> layers = getCachedLayers();
        if (layers == null || layers.isEmpty()) return;

        // Get the vanilla PlayerRenderer and animate its model so layers
        // that call getParentModel().copyPropertiesTo() get correct head rotation
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        if (dispatcher == null) return;

        EntityRenderer<?> renderer = dispatcher.getRenderer(mc.player);
        if (!(renderer instanceof PlayerRenderer playerRenderer)) return;

        // Use accessor mixin to get the model
        @SuppressWarnings("unchecked")
        AccessorLivingEntityRenderer<AbstractClientPlayer, EntityModel<AbstractClientPlayer>> accessor =
                (AccessorLivingEntityRenderer<AbstractClientPlayer, EntityModel<AbstractClientPlayer>>) (Object) playerRenderer;

        @SuppressWarnings("unchecked")
        PlayerModel<AbstractClientPlayer> playerModel =
                (PlayerModel<AbstractClientPlayer>) accessor.getModel();

        if (playerModel == null) return;

        // Calculate standard animation parameters matching vanilla
        // LivingEntityRenderer.render()
        float limbSwing = player.walkAnimation.position(partialTick);
        float limbSwingAmount = player.walkAnimation.speed(partialTick);
        if (player.isBaby()) limbSwingAmount *= 3.0F;
        if (limbSwingAmount > 1.0F) limbSwingAmount = 1.0F;

        float ageInTicks = player.tickCount + partialTick;

        float headYaw = Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot);
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        float netHeadYaw = headYaw - bodyYaw;

        float headPitch = Mth.lerp(partialTick, player.xRotO, player.getXRot());

        // --- Animate the vanilla PlayerRenderer's model ---
        // This is critical: layers like PlayerInvulLayer call
        // getParentModel().copyPropertiesTo(localModel) to copy head rotation,
        // and layers like PlayerHaloLayer (whose model is a child of the head
        // part in the PlayerModel hierarchy) rely on the head part's rotation
        // being set via setupAnim so that translateAndRotate traversing up
        // through the parent chain applies the correct rotation.
        playerModel.setupAnim(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        // Apply crouching pose to the model (matches vanilla LivingEntityRenderer)
        if (player.isCrouching()) {
            playerModel.riding = player.isPassenger();
        }

        // --- Set up the PoseStack like vanilla LivingEntityRenderer ---
        poseStack.pushPose();

        try {
            // Apply body yaw rotation (matches vanilla LivingEntityRenderer)
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - bodyYaw));

            // Apply sneaking translation (matches vanilla)
            if (player.isCrouching() && !player.isPassenger()) {
                poseStack.translate(0.0D, 0.125D, 0.0D);
            }

            // Apply baby scaling (matches vanilla LivingEntityRenderer)
            if (player.isBaby()) {
                poseStack.scale(0.5F, 0.5F, 0.5F);
                poseStack.translate(0.0D, 1.0D, 0.0D);
            }

            // Only render halo-related layers.
            //
            // YSM already handles the player model rendering. We should NOT
            // replay ALL render layers — that would double-render hand items,
            // armor, capes, and other layers already drawn by YSM.
            //
            // The only layers we need to re-render are the halo layers that
            // YSM bypasses (because it skips PlayerRenderer.render entirely):
            //
            // - PlayerHaloLayer: GoetyRevelation's Ascension Halo
            // - CuriosLayer: delegates to OdamaneHaloLayer (halo_of_the_end)
            //
            // In Minecraft 1.20.1, ModelPart.translateAndRotate() does not
            // traverse the parent chain, so the head-level Y offset baked
            // into the PlayerModel hierarchy is lost. We compensate by
            // translating up to head height using the configurable offset.
            for (RenderLayer<AbstractClientPlayer, ?> layer : layers) {
                String layerName = layer.getClass().getSimpleName();

                // Skip non-halo layers (hand items, armor, cape, invul, etc.)
                if (!layerName.contains("Halo") && !layerName.contains("Curios")) {
                    continue;
                }

                poseStack.pushPose();
                try {
                    double offsetBlocks = YsmGoetyCompatConfig.headYOffset.get() / 16.0D;
                    poseStack.translate(0.0D, offsetBlocks, 0.0D);

                    renderLayer(layer, poseStack, bufferSource, packedLight,
                            player, limbSwing, limbSwingAmount, partialTick,
                            ageInTicks, netHeadYaw, headPitch);
                } catch (Exception e) {
                    LOGGER.debug("Error rendering layer {}: {}",
                            layerName, e.getMessage());
                } finally {
                    poseStack.popPose();
                }
            }
        } finally {
            poseStack.popPose();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends LivingEntity> void renderLayer(
            RenderLayer<T, ?> layer,
            com.mojang.blaze3d.vertex.PoseStack poseStack,
            net.minecraft.client.renderer.MultiBufferSource bufferSource,
            int packedLight,
            T entity,
            float limbSwing, float limbSwingAmount, float partialTick,
            float ageInTicks, float netHeadYaw, float headPitch
    ) {
        layer.render(poseStack, bufferSource, packedLight, entity,
                limbSwing, limbSwingAmount, partialTick,
                ageInTicks, netHeadYaw, headPitch);
    }

    /**
     * Gets the vanilla PlayerRenderer's full layer list and model, cached until
     * resource reload.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<RenderLayer<AbstractClientPlayer, ?>> getCachedLayers() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return null;

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        if (dispatcher == null) return null;

        EntityRenderer<?> renderer = dispatcher.getRenderer(mc.player);
        if (!(renderer instanceof PlayerRenderer playerRenderer)) {
            cachedPlayerRenderer = null;
            cachedLayers = null;
            cachedModel = null;
            return null;
        }

        // Rebuild cache if the PlayerRenderer instance changed (e.g. resource reload)
        if (cachedPlayerRenderer != playerRenderer) {
            cachedPlayerRenderer = playerRenderer;

            // Use our accessor mixin to get the private layers list
            AccessorLivingEntityRenderer<AbstractClientPlayer, EntityModel<AbstractClientPlayer>> accessor =
                    (AccessorLivingEntityRenderer<AbstractClientPlayer, EntityModel<AbstractClientPlayer>>) (Object) playerRenderer;
            List<RenderLayer<AbstractClientPlayer, EntityModel<AbstractClientPlayer>>> rawLayers = accessor.getLayers();

            // Copy to avoid concurrent modification
            cachedLayers = new ArrayList(rawLayers);

            // Also cache the model
            cachedModel = (PlayerModel<AbstractClientPlayer>) accessor.getModel();

            LOGGER.info("Cached {} render layers from PlayerRenderer: {}",
                    cachedLayers.size(),
                    cachedLayers.stream().map(l -> l.getClass().getSimpleName()).toList());
        }

        return cachedLayers;
    }

    /**
     * Invalidates cached layer list. Call on resource reload (F3+T).
     */
    public static void invalidateCache() {
        cachedPlayerRenderer = null;
        cachedLayers = null;
        cachedModel = null;
        LOGGER.debug("GoetyLayerRenderer cache invalidated");
    }
}
