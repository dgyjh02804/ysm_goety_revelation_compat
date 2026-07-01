package com.dgy.ysmgoetycompat;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Client-side configuration for the YSM-GoetyRevelation compatibility mod.
 * <p>
 * Provides independent X/Y/Z position offsets and X/Y/Z rotation angles for
 * each halo type so users can fine-tune both placement and orientation of
 * {@code ascension_halo}, {@code broken_halo}, and {@code halo_of_the_end}.
 * <p>
 * Position offsets are in pixels (1 block = 16 pixels).
 * Rotation angles are in degrees.
 */
@OnlyIn(Dist.CLIENT)
public class YsmGoetyCompatConfig {

    public static final ForgeConfigSpec CLIENT_SPEC;

    // ── ascension_halo ─────────────────────────────────────────────
    public static ForgeConfigSpec.BooleanValue ascensionHaloEnabled;
    public static ForgeConfigSpec.DoubleValue ascensionHaloXOffset;
    public static ForgeConfigSpec.DoubleValue ascensionHaloYOffset;
    public static ForgeConfigSpec.DoubleValue ascensionHaloZOffset;
    public static ForgeConfigSpec.DoubleValue ascensionHaloXRot;
    public static ForgeConfigSpec.DoubleValue ascensionHaloYRot;
    public static ForgeConfigSpec.DoubleValue ascensionHaloZRot;

    // ── broken_halo ────────────────────────────────────────────────
    public static ForgeConfigSpec.BooleanValue brokenHaloEnabled;
    public static ForgeConfigSpec.DoubleValue brokenHaloXOffset;
    public static ForgeConfigSpec.DoubleValue brokenHaloYOffset;
    public static ForgeConfigSpec.DoubleValue brokenHaloZOffset;
    public static ForgeConfigSpec.DoubleValue brokenHaloXRot;
    public static ForgeConfigSpec.DoubleValue brokenHaloYRot;
    public static ForgeConfigSpec.DoubleValue brokenHaloZRot;

    // ── halo_of_the_end ────────────────────────────────────────────
    public static ForgeConfigSpec.BooleanValue haloOfTheEndEnabled;
    public static ForgeConfigSpec.DoubleValue haloOfTheEndXOffset;
    public static ForgeConfigSpec.DoubleValue haloOfTheEndYOffset;
    public static ForgeConfigSpec.DoubleValue haloOfTheEndZOffset;
    public static ForgeConfigSpec.DoubleValue haloOfTheEndXRot;
    public static ForgeConfigSpec.DoubleValue haloOfTheEndYRot;
    public static ForgeConfigSpec.DoubleValue haloOfTheEndZRot;

    // ── unholy_hat ─────────────────────────────────────────────────
    public static ForgeConfigSpec.BooleanValue unholyHatEnabled;
    public static ForgeConfigSpec.DoubleValue unholyHatXOffset;
    public static ForgeConfigSpec.DoubleValue unholyHatYOffset;
    public static ForgeConfigSpec.DoubleValue unholyHatZOffset;
    public static ForgeConfigSpec.DoubleValue unholyHatXRot;
    public static ForgeConfigSpec.DoubleValue unholyHatYRot;
    public static ForgeConfigSpec.DoubleValue unholyHatZRot;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("YSM GoetyRevelation Compatibility - Client Settings",
                "Adjust halo positions and rotations if effects appear at the wrong location or angle.",
                "Position offsets are in pixels (1 block = 16 pixels).",
                "Rotation angles are in degrees.",
                "",
                "Coordinate system (Minecraft world space):",
                "  X = left(-) / right(+)",
                "  Y = up(+) / down(-)",
                "  Z = forward(+) / back(-)",
                "  X-rotation = pitch (positive = look down, negative = look up)",
                "  Y-rotation = yaw (positive = turn right, negative = turn left)",
                "  Z-rotation = roll (positive = tilt right, negative = tilt left)");

        buildAscensionHaloSection(builder);
        buildBrokenHaloSection(builder);
        buildHaloOfTheEndSection(builder);
        buildUnholyHatSection(builder);

        CLIENT_SPEC = builder.build();
    }

    private static void buildAscensionHaloSection(ForgeConfigSpec.Builder builder) {
        builder.push("ascension_halo");
        builder.comment("Position & rotation for the Ascension Halo (goety_revelation:ascension_halo).",
                "The standard golden halo granted by the Ascension ability.");

        ascensionHaloEnabled = builder.comment("Whether to render this halo.").define("enabled", true);

        builder.push("position");
        ascensionHaloXOffset = builder.comment("Horizontal offset (pixels).").defineInRange("xOffset", 0.0D, -128.0D, 128.0D);
        ascensionHaloYOffset = builder.comment("Vertical offset (pixels).").defineInRange("yOffset", 30.0D, 0.0D, 128.0D);
        ascensionHaloZOffset = builder.comment("Forward/back offset (pixels).").defineInRange("zOffset", -8.0D, -128.0D, 128.0D);
        builder.pop();

        builder.push("rotation");
        ascensionHaloXRot = builder.comment("X-axis rotation (degrees). Positive = tilt downward, negative = tilt upward.").defineInRange("xRot", -90.0D, -180.0D, 180.0D);
        ascensionHaloYRot = builder.comment("Y-axis rotation (degrees).").defineInRange("yRot", 0.0D, -180.0D, 180.0D);
        ascensionHaloZRot = builder.comment("Z-axis rotation (degrees).").defineInRange("zRot", 0.0D, -180.0D, 180.0D);
        builder.pop();

        builder.pop();
    }

    private static void buildBrokenHaloSection(ForgeConfigSpec.Builder builder) {
        builder.push("broken_halo");
        builder.comment("Position & rotation for the Broken Ascension Halo (goety_revelation:broken_halo).");

        brokenHaloEnabled = builder.comment("Whether to render this halo.").define("enabled", true);

        builder.push("position");
        brokenHaloXOffset = builder.comment("Horizontal offset (pixels).").defineInRange("xOffset", 0.0D, -128.0D, 128.0D);
        brokenHaloYOffset = builder.comment("Vertical offset (pixels).").defineInRange("yOffset", 30.0D, 0.0D, 128.0D);
        brokenHaloZOffset = builder.comment("Forward/back offset (pixels).").defineInRange("zOffset", -8.0D, -128.0D, 128.0D);
        builder.pop();

        builder.push("rotation");
        brokenHaloXRot = builder.comment("X-axis rotation (degrees).").defineInRange("xRot", -90.0D, -180.0D, 180.0D);
        brokenHaloYRot = builder.comment("Y-axis rotation (degrees).").defineInRange("yRot", 0.0D, -180.0D, 180.0D);
        brokenHaloZRot = builder.comment("Z-axis rotation (degrees).").defineInRange("zRot", 0.0D, -180.0D, 180.0D);
        builder.pop();

        builder.pop();
    }

    private static void buildHaloOfTheEndSection(ForgeConfigSpec.Builder builder) {
        builder.push("halo_of_the_end");
        builder.comment("Position & rotation for the Halo of the End (goety_revelation:halo_of_the_end).");

        haloOfTheEndEnabled = builder.comment("Whether to render this halo.").define("enabled", true);

        builder.push("position");
        haloOfTheEndXOffset = builder.comment("Horizontal offset (pixels).").defineInRange("xOffset", 0.0D, -128.0D, 128.0D);
        haloOfTheEndYOffset = builder.comment("Vertical offset (pixels).").defineInRange("yOffset", 20.0D, 0.0D, 128.0D);
        haloOfTheEndZOffset = builder.comment("Forward/back offset (pixels).").defineInRange("zOffset", -5.0D, -128.0D, 128.0D);
        builder.pop();

        builder.push("rotation");
        haloOfTheEndXRot = builder.comment("X-axis rotation (degrees).").defineInRange("xRot", 0.0D, -180.0D, 180.0D);
        haloOfTheEndYRot = builder.comment("Y-axis rotation (degrees).").defineInRange("yRot", 0.0D, -180.0D, 180.0D);
        haloOfTheEndZRot = builder.comment("Z-axis rotation (degrees).").defineInRange("zRot", 180.0D, -180.0D, 180.0D);
        builder.pop();

        builder.pop();
    }

    private static void buildUnholyHatSection(ForgeConfigSpec.Builder builder) {
        builder.push("unholy_hat");
        builder.comment("Position & rotation for the Unholy Hat halo (goety:unholy_hat).",
                "The cursed halo from the base Goety mod.");

        unholyHatEnabled = builder.comment("Whether to render this halo.").define("enabled", true);

        builder.push("position");
        unholyHatXOffset = builder.comment("Horizontal offset (pixels).").defineInRange("xOffset", 0.0D, -128.0D, 128.0D);
        unholyHatYOffset = builder.comment("Vertical offset (pixels).").defineInRange("yOffset", 22.0D, 0.0D, 128.0D);
        unholyHatZOffset = builder.comment("Forward/back offset (pixels).").defineInRange("zOffset", 0.0D, -128.0D, 128.0D);
        builder.pop();

        builder.push("rotation");
        unholyHatXRot = builder.comment("X-axis rotation (degrees).").defineInRange("xRot", 0.0D, -180.0D, 180.0D);
        unholyHatYRot = builder.comment("Y-axis rotation (degrees).").defineInRange("yRot", 0.0D, -180.0D, 180.0D);
        unholyHatZRot = builder.comment("Z-axis rotation (degrees).").defineInRange("zRot", 180.0D, -180.0D, 180.0D);
        builder.pop();

        builder.pop();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC);
    }
}
