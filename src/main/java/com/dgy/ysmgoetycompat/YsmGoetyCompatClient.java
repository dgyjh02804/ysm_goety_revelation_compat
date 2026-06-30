package com.dgy.ysmgoetycompat;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

/**
 * Client-side initialization and event registration.
 * <p>
 * Registers a listener for {@link RenderLivingEvent.Post} to replay
 * ALL render layers from the vanilla {@link PlayerRenderer} on top of
 * players rendered by non-vanilla renderers (e.g. YSM).
 * This includes GoetyRevelation layers (PlayerHaloLayer, PlayerInvulLayer),
 * CuriosLayer (which drives OdamaneHaloLayer for halo_of_the_end), and
 * any other mod-added player render layers.
 */
@OnlyIn(Dist.CLIENT)
public class YsmGoetyCompatClient {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;

        boolean goetyLoaded = ModList.get().isLoaded("goety_revelation");
        boolean ysmLoaded = ModList.get().isLoaded("yes_steve_model");

        LOGGER.info("GoetyRevelation: {}, YSM: {}", goetyLoaded, ysmLoaded);

        // Client config was already registered in the @Mod constructor
        // so the in-game Mods screen can detect it.

        // Register on the Forge EVENT_BUS (not mod bus) for render events
        MinecraftForge.EVENT_BUS.addListener(YsmGoetyCompatClient::onRenderLivingPost);

        // Register resource reload listener to invalidate cached layers
        MinecraftForge.EVENT_BUS.addListener(YsmGoetyCompatClient::onAddReloadListener);

        LOGGER.info("YSM GoetyRevelation Compat initialized");
    }

    /**
     * Fired after every living entity is rendered.
     * If a non-vanilla renderer (e.g. YSM) handled the player, we replay
     * ALL layers from the vanilla PlayerRenderer to restore mod-added effects.
     */
    private static void onRenderLivingPost(RenderLivingEvent.Post event) {
        // Only handle players
        if (!(event.getEntity() instanceof AbstractClientPlayer player)) return;

        // Skip if the vanilla PlayerRenderer handled this player (layers already ran)
        if (event.getRenderer() instanceof PlayerRenderer) return;

        // Replay all vanilla PlayerRenderer layers on top of the custom-rendered model
        GoetyLayerRenderer.renderEffects(
                player,
                event.getPoseStack(),
                event.getMultiBufferSource(),
                event.getPackedLight(),
                event.getPartialTick()
        );
    }

    /**
     * Clear cached layers on resource reload so they're recreated with fresh models.
     */
    private static void onAddReloadListener(AddReloadListenerEvent event) {
        GoetyLayerRenderer.invalidateCache();
        LOGGER.debug("GoetyLayerRenderer cache invalidated due to resource reload");
    }
}
