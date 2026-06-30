package com.dgy.ysmgoetycompat;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(YsmGoetyCompat.MODID)
public class YsmGoetyCompat {
    public static final String MODID = "ysm_goety_revelation_compat";
    private static final Logger LOGGER = LogUtils.getLogger();

    public YsmGoetyCompat() {
        LOGGER.info("YSM GoetyRevelation Compat initializing...");

        // Register client config EARLY (in constructor, not in FMLClientSetupEvent)
        // so Forge's in-game Mods screen can detect and enable the Config button.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            ModLoadingContext.get().registerConfig(
                    ModConfig.Type.CLIENT,
                    YsmGoetyCompatConfig.CLIENT_SPEC
            );
        });

        // Only register client setup on the client side
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);

        LOGGER.info("YSM GoetyRevelation Compat initialized successfully");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        // Run client init on the physical client side
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> YsmGoetyCompatClient::init);
    }
}
