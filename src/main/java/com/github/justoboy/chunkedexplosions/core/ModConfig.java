package com.github.justoboy.chunkedexplosions.core;

import com.github.justoboy.chunkedexplosions.common.command.CommandComments;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;
import org.apache.commons.lang3.tuple.Pair;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Forge's config APIs
@Mod.EventBusSubscriber(modid = ChunkedExplosions.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModConfig {
    public static final ForgeConfigSpec CONFIG_SPEC;
    private static final Config COMMON_CONFIG;

    static {
        Pair<Config, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(Config::new);
        COMMON_CONFIG = specPair.getLeft();
        CONFIG_SPEC = specPair.getRight();
    }

    public static class Config {
        public final ForgeConfigSpec.BooleanValue enable;
        public final ForgeConfigSpec.ConfigValue<Integer> blocksPerExplosionTick;
        public final ForgeConfigSpec.ConfigValue<Integer> explosionsPerTick;

        Config(ForgeConfigSpec.Builder builder) {
            builder.push("general");
            enable = builder.comment(CommandComments.getComment("enable"))
                    .define("enable", true);
            blocksPerExplosionTick = builder.comment(CommandComments.getComment("blocksPerExplosionTick"))
                    .define("blocksPerExplosionTick", 1);
            explosionsPerTick = builder.comment(CommandComments.getComment("explosionsPerTick"))
                    .define("explosionsPerTick", 6400);
            builder.pop();
        }
    }

    public static boolean isEnable() {
        return COMMON_CONFIG.enable.get();
    }
    public static void setEnable(boolean value) {
        COMMON_CONFIG.enable.set(value);
    }

    public static int getBlocksPerExplosionTick() { return COMMON_CONFIG.blocksPerExplosionTick.get(); }
    public static void setBlocksPerExplosionTick(int value) { COMMON_CONFIG.blocksPerExplosionTick.set(value); }

    public static int getExplosionsPerTick() { return COMMON_CONFIG.explosionsPerTick.get(); }
    public static void setExplosionsPerTick(int value) { COMMON_CONFIG.explosionsPerTick.set(value); }
}
