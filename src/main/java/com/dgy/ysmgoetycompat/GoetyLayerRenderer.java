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

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    // ── Reflective handles for ATAHelper2 (RevelationFix) ──────────
    private static MethodHandle hasOdamaneHandle;

    // ── Reflective handles for CuriosFinder (Goety) ────────────────
    private static MethodHandle hasCurioItemHandle;
    private static MethodHandle findCurioHandle;

    // ── Reflective handles for Curios API direct rendering ─────────
    private static MethodHandle getRendererHandle;
    private static MethodHandle slotContextCtorHandle;
    private static MethodHandle curioRenderHandle;

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
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();

            // GoetyRevelation: ATAHelper.hasHalo / hasBrokenHalo
            try {
                Class<?> ataHelper = Class.forName("z1gned.goetyrevelation.util.ATAHelper");
                MethodType mt = MethodType.methodType(boolean.class, LivingEntity.class);
                hasHaloHandle = lookup.findStatic(ataHelper, "hasHalo", mt);
                hasBrokenHaloHandle = lookup.findStatic(ataHelper, "hasBrokenHalo", mt);
                LOGGER.info("GoetyLayerRenderer: ATAHelper reflection initialized");
            } catch (Exception e) {
                LOGGER.warn("GoetyLayerRenderer: ATAHelper reflection failed: {}", e.toString());
            }

            // RevelationFix: ATAHelper2.hasOdamane
            try {
                Class<?> ataHelper2 = Class.forName("com.mega.revelationfix.util.entity.ATAHelper2");
                MethodType mtOdamane = MethodType.methodType(boolean.class, net.minecraft.world.entity.Entity.class);
                hasOdamaneHandle = lookup.findStatic(ataHelper2, "hasOdamane", mtOdamane);
                LOGGER.info("GoetyLayerRenderer: ATAHelper2.hasOdamane reflection initialized");
            } catch (Exception e) {
                LOGGER.warn("GoetyLayerRenderer: ATAHelper2 reflection failed: {}", e.toString());
            }

            // Goety: CuriosFinder.hasCurio / findCurio
            try {
                Class<?> curiosFinder = Class.forName("com.Polarice3.Goety.utils.CuriosFinder");
                MethodType mtCurio = MethodType.methodType(boolean.class, LivingEntity.class, Item.class);
                hasCurioItemHandle = lookup.findStatic(curiosFinder, "hasCurio", mtCurio);
                MethodType mtFind = MethodType.methodType(ItemStack.class, LivingEntity.class, Item.class);
                findCurioHandle = lookup.findStatic(curiosFinder, "findCurio", mtFind);
                LOGGER.info("GoetyLayerRenderer: CuriosFinder reflection initialized");
            } catch (Exception e) {
                LOGGER.warn("GoetyLayerRenderer: CuriosFinder reflection failed: {}", e.toString());
            }

            // Curios API: CuriosRendererRegistry.getRenderer(Item) → Optional<ICurioRenderer>
            try {
                Class<?> registry = Class.forName("top.theillusivec4.curios.api.client.CuriosRendererRegistry");
                MethodType mtGet = MethodType.methodType(Optional.class, Item.class);
                getRendererHandle = lookup.findStatic(registry, "getRenderer", mtGet);
                LOGGER.info("GoetyLayerRenderer: CuriosRendererRegistry reflection initialized");
            } catch (Exception e) {
                LOGGER.warn("GoetyLayerRenderer: CuriosRendererRegistry reflection failed: {}", e.toString());
            }

            // Curios API: SlotContext constructor + ICurioRenderer.render()
            try {
                Class<?> slotContextClass = Class.forName("top.theillusivec4.curios.api.SlotContext");
                MethodType mtCtor = MethodType.methodType(void.class, String.class, LivingEntity.class,
                        int.class, boolean.class, boolean.class);
                slotContextCtorHandle = lookup.findConstructor(slotContextClass, mtCtor);

                Class<?> iCurioRenderer = Class.forName("top.theillusivec4.curios.api.client.ICurioRenderer");
                Class<?> renderLayerParent = Class.forName("net.minecraft.client.renderer.entity.RenderLayerParent");
                Class<?> multiBufferSource = Class.forName("net.minecraft.client.renderer.MultiBufferSource");
                MethodType mtRender = MethodType.methodType(void.class,
                        ItemStack.class, slotContextClass, com.mojang.blaze3d.vertex.PoseStack.class,
                        renderLayerParent, multiBufferSource, int.class,
                        float.class, float.class, float.class, float.class, float.class, float.class);
                curioRenderHandle = lookup.findVirtual(iCurioRenderer, "render", mtRender);
                LOGGER.info("GoetyLayerRenderer: ICurioRenderer direct rendering initialized");
            } catch (Exception e) {
                LOGGER.warn("GoetyLayerRenderer: ICurioRenderer reflection failed: {}", e.toString());
            }
        } catch (Exception e) {
            LOGGER.warn("GoetyLayerRenderer: reflection initialization failed: {}", e.toString());
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

    /**
     * Checks whether the entity wears a {@code halo_of_the_end} via
     * {@code ATAHelper2.hasOdamane(Entity)} from RevelationFix.
     */
    private static boolean hasOdamane(LivingEntity entity) {
        initReflection();
        if (hasOdamaneHandle == null) return false;
        try { return (boolean) hasOdamaneHandle.invoke(entity); } catch (Throwable t) { return false; }
    }

    /**
     * Checks whether the entity wears an unholy hat in a Curios slot
     * via {@code CuriosFinder.hasCurio(LivingEntity, Item)} from Goety.
     */
    private static boolean hasUnholyHat(LivingEntity entity) {
        initReflection();
        if (hasCurioItemHandle == null) return false;
        Item unholyHat = ForgeRegistries.ITEMS.getValue(new ResourceLocation("goety", "unholy_hat"));
        if (unholyHat == null) return false;
        try { return (boolean) hasCurioItemHandle.invoke(entity, unholyHat); } catch (Throwable t) { return false; }
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
                        if (!YsmGoetyCompatConfig.brokenHaloEnabled.get()) continue;
                        offsets = new HaloOffsets(
                                YsmGoetyCompatConfig.brokenHaloXOffset.get(),
                                YsmGoetyCompatConfig.brokenHaloYOffset.get(),
                                YsmGoetyCompatConfig.brokenHaloZOffset.get(),
                                YsmGoetyCompatConfig.brokenHaloXRot.get(),
                                YsmGoetyCompatConfig.brokenHaloYRot.get(),
                                YsmGoetyCompatConfig.brokenHaloZRot.get()
                        );
                    } else if (hasHalo(player)) {
                        if (!YsmGoetyCompatConfig.ascensionHaloEnabled.get()) continue;
                        offsets = new HaloOffsets(
                                YsmGoetyCompatConfig.ascensionHaloXOffset.get(),
                                YsmGoetyCompatConfig.ascensionHaloYOffset.get(),
                                YsmGoetyCompatConfig.ascensionHaloZOffset.get(),
                                YsmGoetyCompatConfig.ascensionHaloXRot.get(),
                                YsmGoetyCompatConfig.ascensionHaloYRot.get(),
                                YsmGoetyCompatConfig.ascensionHaloZRot.get()
                        );
                    }
                    // If neither halo type, offsets stays null → skip.

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
                } else if (layerName.contains("Curios")) {
                    // Do NOT render the CuriosLayer directly — it would render
                    // ALL equipped curios (robe, rings, etc.) at the halo position.
                    // Instead, render each halo-type ICurioRenderer individually.
                    renderCuriosHalos(player, poseStack, bufferSource, packedLight,
                            limbSwing, limbSwingAmount, partialTick,
                            ageInTicks, netHeadYaw, headPitch, playerRenderer);
                }
                // Non-halo, non-curios layers are silently skipped.
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

    /**
     * Renders Curios-based halos directly via their {@code ICurioRenderer}
     * instead of going through the broad {@code CuriosLayer} which would also
     * render unrelated curios (robe, rings, etc.).
     */
    private static void renderCuriosHalos(
            AbstractClientPlayer player,
            com.mojang.blaze3d.vertex.PoseStack poseStack,
            net.minecraft.client.renderer.MultiBufferSource bufferSource,
            int packedLight,
            float limbSwing, float limbSwingAmount, float partialTick,
            float ageInTicks, float netHeadYaw, float headPitch,
            PlayerRenderer playerRenderer
    ) {
        // halo_of_the_end
        if (YsmGoetyCompatConfig.haloOfTheEndEnabled.get() && hasOdamane(player)) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("goety_revelation", "halo_of_the_end"));
            if (item != null) {
                HaloOffsets offsets = new HaloOffsets(
                        YsmGoetyCompatConfig.haloOfTheEndXOffset.get(),
                        YsmGoetyCompatConfig.haloOfTheEndYOffset.get(),
                        YsmGoetyCompatConfig.haloOfTheEndZOffset.get(),
                        YsmGoetyCompatConfig.haloOfTheEndXRot.get(),
                        YsmGoetyCompatConfig.haloOfTheEndYRot.get(),
                        YsmGoetyCompatConfig.haloOfTheEndZRot.get()
                );
                renderSingleCurioItem(player, item, offsets, poseStack, bufferSource,
                        packedLight, limbSwing, limbSwingAmount, partialTick,
                        ageInTicks, netHeadYaw, headPitch, playerRenderer);
            }
        }

        // unholy_hat
        if (YsmGoetyCompatConfig.unholyHatEnabled.get() && hasUnholyHat(player)) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("goety", "unholy_hat"));
            if (item != null) {
                HaloOffsets offsets = new HaloOffsets(
                        YsmGoetyCompatConfig.unholyHatXOffset.get(),
                        YsmGoetyCompatConfig.unholyHatYOffset.get(),
                        YsmGoetyCompatConfig.unholyHatZOffset.get(),
                        YsmGoetyCompatConfig.unholyHatXRot.get(),
                        YsmGoetyCompatConfig.unholyHatYRot.get(),
                        YsmGoetyCompatConfig.unholyHatZRot.get()
                );
                renderSingleCurioItem(player, item, offsets, poseStack, bufferSource,
                        packedLight, limbSwing, limbSwingAmount, partialTick,
                        ageInTicks, netHeadYaw, headPitch, playerRenderer);
            }
        }
    }

    /**
     * Renders a single curio item by obtaining its {@code ICurioRenderer} from the
     * Curios registry and invoking it directly, so no unrelated curios are drawn.
     */
    private static void renderSingleCurioItem(
            AbstractClientPlayer player,
            Item item,
            HaloOffsets offsets,
            com.mojang.blaze3d.vertex.PoseStack poseStack,
            net.minecraft.client.renderer.MultiBufferSource bufferSource,
            int packedLight,
            float limbSwing, float limbSwingAmount, float partialTick,
            float ageInTicks, float netHeadYaw, float headPitch,
            PlayerRenderer playerRenderer
    ) {
        if (findCurioHandle == null || getRendererHandle == null
                || slotContextCtorHandle == null || curioRenderHandle == null) return;

        try {
            // 1. Get the ItemStack from the player's curios inventory
            ItemStack stack = (ItemStack) findCurioHandle.invoke(player, item);
            if (stack == null || stack.isEmpty()) return;

            // 2. Get the registered ICurioRenderer for this item
            Optional<?> opt = (Optional<?>) getRendererHandle.invoke(item);
            if (opt == null || !opt.isPresent()) return;
            Object curioRenderer = opt.get();

            // 3. Build a minimal SlotContext (head slot, index 0)
            Object slotContext = slotContextCtorHandle.invoke("head", player, 0, false, true);

            // 4. Render with per-halo offsets
            poseStack.pushPose();
            try {
                offsets.applyTo(poseStack);
                curioRenderHandle.invoke(curioRenderer, stack, slotContext, poseStack,
                        playerRenderer, bufferSource, packedLight,
                        limbSwing, limbSwingAmount, partialTick,
                        ageInTicks, netHeadYaw, headPitch);
            } finally {
                poseStack.popPose();
            }
        } catch (Throwable t) {
            LOGGER.warn("Error rendering single curio {}: {}", item, t.toString());
        }
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
        hasOdamaneHandle = null;
        hasCurioItemHandle = null;
        findCurioHandle = null;
        getRendererHandle = null;
        slotContextCtorHandle = null;
        curioRenderHandle = null;
        LOGGER.debug("GoetyLayerRenderer cache invalidated");
    }
}
