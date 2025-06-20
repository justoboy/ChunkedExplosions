package com.github.justoboy.chunkedexplosions.core;

import com.github.justoboy.chunkedexplosions.ChunkedExplosions;
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
        public final ForgeConfigSpec.EnumValue<Timing> damageTiming;
        public final ForgeConfigSpec.EnumValue<Method> damageMethod;
        public final ForgeConfigSpec.EnumValue<Timing> soundTiming;
        public final ForgeConfigSpec.BooleanValue soundVolumeSplit;
        public final ForgeConfigSpec.EnumValue<Timing> particleTiming;
        public final ForgeConfigSpec.EnumValue<Timing> knockbackTiming;
        public final ForgeConfigSpec.EnumValue<Method> knockbackMethod;

        Config(ForgeConfigSpec.Builder builder) {
            builder.push("general");
            enable = builder.comment(CommandComments.getComment("enable"))
                    .define("enable", true);
            blocksPerExplosionTick = builder.comment(CommandComments.getComment("blocksPerExplosionTick"))
                    .defineInRange("blocksPerExplosionTick", 1, 0 ,Integer.MAX_VALUE);
            explosionsPerTick = builder.comment(CommandComments.getComment("explosionsPerTick"))
                    .defineInRange("explosionsPerTick", 4096, 0, Integer.MAX_VALUE);
            damageTiming = builder.comment(CommandComments.getComment("damageTiming"))
                    .defineEnum("damageTiming", Timing.SPREAD);
            damageMethod = builder.comment(CommandComments.getComment("damageMethod"))
                    .defineEnum("damageMethod", Method.SPREAD);
            soundTiming = builder.comment(CommandComments.getComment("soundTiming"))
                    .defineEnum("soundTiming", Timing.SPREAD);
            soundVolumeSplit = builder.comment(CommandComments.getComment("soundVolumeSplit"))
                    .define("soundVolumeSplit", true);
            particleTiming = builder.comment(CommandComments.getComment("particleTiming"))
                    .defineEnum("particleTiming", Timing.SPREAD);
            knockbackTiming = builder.comment(CommandComments.getComment("knockbackTiming"))
                    .defineEnum("knockbackTiming", Timing.SPREAD);
            knockbackMethod = builder.comment(CommandComments.getComment("knockbackMethod"))
                    .defineEnum("knockbackMethod", Method.ONCE);
            builder.pop();
        }
    }

    public enum Timing { START, END, START_END, SPREAD }
    public enum Method { SPREAD, ONCE }

    public static boolean getEnable() {
        return COMMON_CONFIG.enable.get();
    }
    public static void setEnable(boolean value) {
        COMMON_CONFIG.enable.set(value);
    }

    public static int getBlocksPerExplosionTick() { return COMMON_CONFIG.blocksPerExplosionTick.get(); }
    public static void setBlocksPerExplosionTick(int value) { COMMON_CONFIG.blocksPerExplosionTick.set(value); }

    public static int getExplosionsPerTick() { return COMMON_CONFIG.explosionsPerTick.get(); }
    public static void setExplosionsPerTick(int value) { COMMON_CONFIG.explosionsPerTick.set(value); }


    public static Timing getDamageTiming() { return COMMON_CONFIG.damageTiming.get(); }
    public static void setDamageTiming(Timing value) { COMMON_CONFIG.damageTiming.set(value); }

    public static Method getDamageMethod() { return COMMON_CONFIG.damageMethod.get(); }
    public static void setDamageMethod(Method value) { COMMON_CONFIG.damageMethod.set(value); }

    public static Timing getSoundTiming() { return COMMON_CONFIG.soundTiming.get(); }
    public static void setSoundTiming(Timing value) { COMMON_CONFIG.soundTiming.set(value); }

    public static boolean getSoundVolumeSplit() { return COMMON_CONFIG.soundVolumeSplit.get(); }
    public static void setSoundVolumeSplit(boolean value) { COMMON_CONFIG.soundVolumeSplit.set(value); }

    public static Timing getParticleTiming() { return COMMON_CONFIG.particleTiming.get(); }
    public static void setParticleTiming(Timing value) { COMMON_CONFIG.particleTiming.set(value); }

    public static Timing getKnockbackTiming() { return COMMON_CONFIG.knockbackTiming.get(); }
    public static void setKnockbackTiming(Timing value) { COMMON_CONFIG.knockbackTiming.set(value); }

    public static Method getKnockbackMethod() { return COMMON_CONFIG.knockbackMethod.get(); }
    public static void setKnockbackMethod(Method value) { COMMON_CONFIG.knockbackMethod.set(value); }
}
