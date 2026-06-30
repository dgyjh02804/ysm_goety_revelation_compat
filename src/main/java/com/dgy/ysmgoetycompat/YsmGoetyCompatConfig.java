package com.dgy.ysmgoetycompat;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Client-side configuration for the YSM-GoetyRevelation compatibility mod.
 * The only configurable value is the vertical head offset (in pixels)
 * used to position halo effects at the correct height above the player model.
 */
@OnlyIn(Dist.CLIENT)
public class YsmGoetyCompatConfig {

    public static final ForgeConfigSpec CLIENT_SPEC;

    /**
     * Vertical offset in pixels (1 block = 16 pixels) from entity feet
     * to the head center where halos should render.
     * <p>
     * Vanilla PlayerModel places the head at y=24 pixels = 1.5 blocks.
     * YSM's custom models may have a different head height, so this
     * value can be adjusted via the in-game mod config screen.
     * <p>
     * Default: 40 pixels = 2.5 blocks (1 extra block above vanilla head
     * height to compensate for YSM model differences).
     */
    public static ForgeConfigSpec.DoubleValue headYOffset;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("YSM GoetyRevelation Compatibility - Client Settings",
                "Adjust the halo vertical offset if effects appear at the wrong height.");
        builder.push("rendering");

        headYOffset = builder
                .comment("Vertical offset in pixels from entity feet where halo effects render.",
                        "Vanilla Minecraft places the player model head at y=24 (1.5 blocks).",
                        "Increase this value to move halos higher; decrease to move lower.",
                        "Range: 0 ~ 128 pixels (0 ~ 8 blocks)")
                .defineInRange("headYOffset", 40.0D, 0.0D, 128.0D);

        builder.pop();

        CLIENT_SPEC = builder.build();
    }

    /**
     * Register the client config with Forge's mod config system.
     * Must be called during mod construction on the client side.
     */
    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC);
    }
}
