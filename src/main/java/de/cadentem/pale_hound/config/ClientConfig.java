package de.cadentem.pale_hound.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ClientConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;
    public static ModConfigSpec.ConfigValue<Boolean> USE_UPDATED_TEXTURES;

    static {
        USE_UPDATED_TEXTURES = BUILDER.comment("Use updated textures by the user '...'").define("use_updated_textures", true);
        SPEC = BUILDER.build();
    }
}
