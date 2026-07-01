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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;

/**
 * Core rendering bridge that re-plays halo render layers from the vanilla
 * {@link PlayerRenderer} on top of YSM-customized player models.
 * <p>
 * Each halo type has its own configurable position offset (X/Y/Z pixels) and
 * rotation (X/Y/Z degrees) via {@link YsmGoetyCompatConfig}.
 */
@OnlyIn(Dist.CLIENT)
public class GoetyLayerRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static PlayerRenderer cachedPlayerRenderer;
    private static List<RenderLayer<AbstractClientPlayer, ?>> cachedLayers;

    // ── Reflective handles for ATAHelper ───────────────────────────
    private static MethodHandle hasHaloHandle;
    private static MethodHandle hasBrokenHaloHandle;
    private static boolean reflectionInitAttempted;

    public static boolean hasAnyGoetyEffect(AbstractClientPlayer player) {
        return true;
    }

    /**
     * Holds the X/Y/Z pixel offsets and X/Y/Z rotation angles for a halo type.
     */
    private static class HaloOffsets {
        final double xPixels, yPixels, zPixels;
        final double xRotDeg, yRotDeg, zRotDeg;

        HaloOffsets(double xPixels, double yPixels, double zPixels,
                    double xRotDeg, double yRotDeg, double zRotDeg) {
            this.xPixels = xPixels;
            this.yPixels = yPixels;
            this.zPixels = zPixels;
            this.xRotDeg = xRotDeg;
            this.yRotDeg = yRotDeg;
            this.zRotDeg = zRotDeg;
        }

        void applyTo(com.mojang.blaze3d.vertex.PoseStack poseStack) {
            poseStack.translate(xPixels / 16.0D, yPixels / 16.0D, zPixels / 16.0D);
            if (xRotDeg != 0.0D) poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees((float) xRotDeg));
            if (yRotDeg != 0.0D) poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees((float) yRotDeg));
            if (zRotDeg != 0.0D) poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) zRotDeg));
        }
    }

    private static void initReflection() {
        if (reflectionInitAttempted) return;
        reflectionInitAttempted = true;
        try {
            Class<?> ataHelper = Class.forName("z1gned.goetyrevelation.util.ATAHelper");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodType mt = MethodType.methodType(boolean.class, LivingEntity.class);
            hasHaloHandle = lookup.findStatic(ataHelper, "hasHalo", mt);
            hasBrokenHaloHandle = lookup.findStatic(ataHelper, "hasBrokenHalo", mt);
            LOGGER.info("GoetyLayerRenderer: ATAHelper reflection initialized");
        } catch (Exception e) {
            LOGGER.warn("GoetyLayerRenderer: ATAHelper reflection failed: {}", e.toString());
        }
    }

    private static boolean hasHalo(LivingEntity entity) {
        initReflection();
        if (hasHaloHandle == null) return false;
        try { return (boolean) hasHaloHandle.invoke(entity); } catch (Throwable t) { return false; }
    }

    private static boolean hasBrokenHalo(LivingEntity entity) {
        initReflection();
        if (hasBrokenHaloHandle == null) return false;
        try { return (boolean) hasBrokenHaloHandle.invoke(entity); } catch (Throwable t) { return false; }
    }

    public static void renderEffects(
            AbstractClientPlayer player,
            com.mojang.blaze3d.vertex.PoseStack poseStack,
            net.minecraft.client.renderer.MultiBufferSource bufferSource,
            int packedLight,
            float partialTick
    ) {
        List<RenderLayer<AbstractClientPlayer, ?>> layers = getCachedLayers();
        if (layers == null || layers.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        if (dispatcher == null) return;

        EntityRenderer<?> renderer = dispatcher.getRenderer(mc.player);
        if (!(renderer instanceof PlayerRenderer playerRenderer)) return;

        @SuppressWarnings("unchecked")
        AccessorLivingEntityRenderer<AbstractClientPlayer, EntityModel<AbstractClientPlayer>> accessor =
                (AccessorLivingEntityRenderer<AbstractClientPlayer, EntityModel<AbstractClientPlayer>>) (Object) playerRenderer;

        @SuppressWarnings("unchecked")
        PlayerModel<AbstractClientPlayer> playerModel =
                (PlayerModel<AbstractClientPlayer>) accessor.getModel();

        if (playerModel == null) return;

        float limbSwing = player.walkAnimation.position(partialTick);
        float limbSwingAmount = player.walkAnimation.speed(partialTick);
        if (player.isBaby()) limbSwingAmount *= 3.0F;
        if (limbSwingAmount > 1.0F) limbSwingAmount = 1.0F;

        float ageInTicks = player.tickCount + partialTick;

        float headYaw = Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot);
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        float netHeadYaw = headYaw - bodyYaw;

        float headPitch = Mth.lerp(partialTick, player.xRotO, player.getXRot());

        playerModel.setupAnim(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        if (player.isCrouching()) {
            playerModel.riding = player.isPassenger();
        }

        poseStack.pushPose();

        try {
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - bodyYaw));

            if (player.isCrouching() && !player.isPassenger()) {
                poseStack.translate(0.0D, 0.125D, 0.0D);
            }

            if (player.isBaby()) {
                poseStack.scale(0.5F, 0.5F, 0.5F);
                poseStack.translate(0.0D, 1.0D, 0.0D);
            }

            for (RenderLayer<AbstractClientPlayer, ?> layer : layers) {
                String layerName = layer.getClass().getSimpleName();
                HaloOffsets offsets = null;

                if (layerName.contains("Halo")) {
                    if (hasBrokenHalo(player)) {
                        offsets = new HaloOffsets(
                                YsmGoetyCompatConfig.brokenHaloXOffset.get(),
                                YsmGoetyCompatConfig.brokenHaloYOffset.get(),
                                YsmGoetyCompatConfig.brokenHaloZOffset.get(),
                                YsmGoetyCompatConfig.brokenHaloXRot.get(),
                                YsmGoetyCompatConfig.brokenHaloYRot.get(),
                                YsmGoetyCompatConfig.brokenHaloZRot.get()
                        );
                    } else if (hasHalo(player)) {
                        offsets = new HaloOffsets(
                                YsmGoetyCompatConfig.ascensionHaloXOffset.get(),
                                YsmGoetyCompatConfig.ascensionHaloYOffset.get(),
                                YsmGoetyCompatConfig.ascensionHaloZOffset.get(),
                                YsmGoetyCompatConfig.ascensionHaloXRot.get(),
                                YsmGoetyCompatConfig.ascensionHaloYRot.get(),
                                YsmGoetyCompatConfig.ascensionHaloZRot.get()
                        );
                    }
                } else if (layerName.contains("Curios")) {
                    offsets = new HaloOffsets(
                            YsmGoetyCompatConfig.haloOfTheEndXOffset.get(),
                            YsmGoetyCompatConfig.haloOfTheEndYOffset.get(),
                            YsmGoetyCompatConfig.haloOfTheEndZOffset.get(),
                            YsmGoetyCompatConfig.haloOfTheEndXRot.get(),
                            YsmGoetyCompatConfig.haloOfTheEndYRot.get(),
                            YsmGoetyCompatConfig.haloOfTheEndZRot.get()
                    );
                }

                if (offsets == null) continue;

                poseStack.pushPose();
                try {
                    offsets.applyTo(poseStack);
                    renderLayer(layer, poseStack, bufferSource, packedLight,
                            player, limbSwing, limbSwingAmount, partialTick,
                            ageInTicks, netHeadYaw, headPitch);
                } catch (Exception e) {
                    LOGGER.warn("Error rendering layer {}: {}", layerName, e.toString());
                } finally {
                    poseStack.popPose();
                }
            }
        } finally {
            poseStack.popPose();
        }

        // Flush the main buffer source — OdamaneHaloLayer renders to
        // Minecraft.getInstance().renderBuffers().bufferSource() internally
        // but doesn't always call endBatch(), so geometry may never be
        // submitted unless something else triggers a flush.
        mc.renderBuffers().bufferSource().endBatch();
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
            return null;
        }

        if (cachedPlayerRenderer != playerRenderer) {
            cachedPlayerRenderer = playerRenderer;

            AccessorLivingEntityRenderer<AbstractClientPlayer, EntityModel<AbstractClientPlayer>> accessor =
                    (AccessorLivingEntityRenderer<AbstractClientPlayer, EntityModel<AbstractClientPlayer>>) (Object) playerRenderer;
            List<RenderLayer<AbstractClientPlayer, EntityModel<AbstractClientPlayer>>> rawLayers = accessor.getLayers();

            cachedLayers = new ArrayList(rawLayers);

            LOGGER.info("Cached {} render layers from PlayerRenderer: {}",
                    cachedLayers.size(),
                    cachedLayers.stream().map(l -> l.getClass().getSimpleName()).toList());
        }

        return cachedLayers;
    }

    public static void invalidateCache() {
        cachedPlayerRenderer = null;
        cachedLayers = null;
        reflectionInitAttempted = false;
        hasHaloHandle = null;
        hasBrokenHaloHandle = null;
        LOGGER.debug("GoetyLayerRenderer cache invalidated");
    }
}
