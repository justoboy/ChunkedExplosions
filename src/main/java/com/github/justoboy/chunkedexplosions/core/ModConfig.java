package com.github.justoboy.chunkedexplosions.core;

import com.github.justoboy.chunkedexplosions.ChunkedExplosions;
import com.github.justoboy.chunkedexplosions.common.command.CommandComments;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Configuration management for the ChunkedExplosions mod.
 * 
 * <p>This class handles all configuration settings for the mod, including:</p>
 * <ul>
 *   <li>Performance settings (blocks per tick, explosions per tick, queue size)</li>
 *   <li>Timing settings (when damage, sound, particles, knockback are applied)</li>
 *   <li>Feature toggles (enable/disable, cascade suppression)</li>
 * </ul>
 * 
 * <h2>Configuration Lifecycle</h2>
 * <ol>
 *   <li>Static initializer creates the ForgeConfigSpec and Config instance</li>
 *   <li>Forge loads values from config file on startup</li>
 *   <li>Values can be modified at runtime via commands</li>
 *   <li>Changes are immediately reflected in explosion processing</li>
 * </ol>
 * 
 * <h2>Timing Modes</h2>
 * <p>All timing-based effects use the {@link Timing} enum:</p>
 * <ul>
 *   <li>{@link Timing#START START} - Effect applied immediately when explosion begins</li>
 *   <li>{@link Timing#END END} - Effect applied after all blocks are destroyed</li>
 *   <li>{@link Timing#START_END START_END} - Effect split 50/50 between start and end</li>
 *   <li>{@link Timing#SPREAD SPREAD} - Effect accumulated proportionally per block, applied per tick</li>
 * </ul>
 * 
 * @author justoboy
 * @see ExplosionState
 * @see ExplosionProcessor
 */
@Mod.EventBusSubscriber(modid = ChunkedExplosions.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModConfig {
    
    /**
     * The Forge configuration specification defining all config options.
     * Used by Forge to create and validate the config file.
     */
    public static final ForgeConfigSpec CONFIG_SPEC;
    
    /**
     * The common configuration instance containing all config values.
     * Accessed via static getter methods throughout the mod.
     */
    private static final Config COMMON_CONFIG;

    /**
     * Static initializer that creates the configuration specification.
     * This is called when the class is first loaded by Forge.
     */
    static {
        Pair<Config, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(Config::new);
        COMMON_CONFIG = specPair.getLeft();
        CONFIG_SPEC = specPair.getRight();
    }

    /**
     * Inner class containing all configuration values.
     * Each field is a ForgeConfigSpec value that can be read/written at runtime.
     */
    public static class Config {
        /** Enable/disable chunked explosions feature */
        public final ForgeConfigSpec.BooleanValue enable;
        
        /** Maximum blocks destroyed per explosion per tick */
        public final ForgeConfigSpec.ConfigValue<Integer> blocksPerExplosionTick;
        
        /** Maximum explosions moved from awaiting to active queue per tick */
        public final ForgeConfigSpec.ConfigValue<Integer> explosionsPerTick;
        
        /** Global maximum blocks destroyed per tick across all explosions */
        public final ForgeConfigSpec.ConfigValue<Integer> maxBlocksPerTick;
        
        /** Whether to suppress chain reactions from chunked explosions */
        public final ForgeConfigSpec.BooleanValue cascadeSuppression;
        
        /** Timing mode for entity damage application */
        public final ForgeConfigSpec.EnumValue<Timing> damageTiming;
        
        /** Timing mode for explosion sound playback */
        public final ForgeConfigSpec.EnumValue<Timing> soundTiming;
        
        /** Whether to split sound volume between timing phases */
        public final ForgeConfigSpec.BooleanValue soundVolumeSplit;
        
        /** Timing mode for explosion particle spawning */
        public final ForgeConfigSpec.EnumValue<Timing> particleTiming;
        
        /** Whether to split particle count between timing phases */
        public final ForgeConfigSpec.BooleanValue particleSplit;
        
        /** Timing mode for entity knockback application */
        public final ForgeConfigSpec.EnumValue<Timing> knockbackTiming;

        /**
         * Constructs the configuration with all values and their defaults.
         * 
         * @param builder The ForgeConfigSpec builder to configure
         */
        Config(ForgeConfigSpec.Builder builder) {
            builder.push("general");
            
            // Enable/disable chunked explosions
            enable = builder.comment(CommandComments.getComment("enable"))
                    .define("enable", true);
            
            // Performance settings
            blocksPerExplosionTick = builder.comment(CommandComments.getComment("blocksPerExplosionTick"))
                    .defineInRange("blocksPerExplosionTick", 16, 0, Integer.MAX_VALUE);
            explosionsPerTick = builder.comment(CommandComments.getComment("explosionsPerTick"))
                    .defineInRange("explosionsPerTick", 1024, 0, Integer.MAX_VALUE);
            maxBlocksPerTick = builder.comment(CommandComments.getComment("maxBlocksPerTick"))
                    .defineInRange("maxBlocksPerTick", 16384, 0, Integer.MAX_VALUE);
            // Feature settings
            cascadeSuppression = builder.comment(CommandComments.getComment("cascadeSuppression"))
                    .define("cascadeSuppression", false);
            
            // Timing settings
            damageTiming = builder.comment(CommandComments.getComment("damageTiming"))
                    .defineEnum("damageTiming", Timing.SPREAD);
            soundTiming = builder.comment(CommandComments.getComment("soundTiming"))
                    .defineEnum("soundTiming", Timing.SPREAD);
            soundVolumeSplit = builder.comment(CommandComments.getComment("soundVolumeSplit"))
                    .define("soundVolumeSplit", true);
            particleTiming = builder.comment(CommandComments.getComment("particleTiming"))
                    .defineEnum("particleTiming", Timing.SPREAD);
            particleSplit = builder.comment(CommandComments.getComment("particleSplit"))
                    .define("particleSplit", true);
            knockbackTiming = builder.comment(CommandComments.getComment("knockbackTiming"))
                    .defineEnum("knockbackTiming", Timing.SPREAD);
            
            builder.pop();
        }
    }

    /**
     * Timing modes for explosion effects.
     * 
     * <p>Determines when effects (damage, sound, particles, knockback) are applied
     * during the chunked explosion process:</p>
     * 
     * <table border="1">
     * <tr><th>Mode</th><th>Description</th></tr>
     * <tr><td>START</td><td>Effect applied immediately when explosion begins (100%)</td></tr>
     * <tr><td>END</td><td>Effect applied after all blocks are destroyed (100%)</td></tr>
     * <tr><td>START_END</td><td>Effect split 50/50 between start and end</td></tr>
     * <tr><td>SPREAD</td><td>Effect accumulated per block, applied once per tick</td></tr>
     * </table>
     * 
     * @see ExplosionState#applyDamage(ServerLevel)
     * @see ExplosionState#applyKnockback(ServerLevel)
     * @see ExplosionState#playSound()
     * @see ExplosionState#spawnParticles()
     */
    public enum Timing { 
        /** Effect applied immediately when explosion begins */
        START, 
        
        /** Effect applied after all blocks are destroyed */
        END, 
        
        /** Effect split between start and end phases */
        START_END, 
        
        /** Effect accumulated proportionally per block, applied per tick */
        SPREAD 
    }

    // === Configuration Accessor Methods ===
    
    /**
     * Gets the current enable status for chunked explosions.
     * @return true if chunked explosions are enabled, false otherwise
     */
    public static boolean getEnable() {
        return COMMON_CONFIG.enable.get();
    }
    
    /**
     * Sets the enable status for chunked explosions.
     * @param value true to enable, false to disable
     */
    public static void setEnable(boolean value) {
        COMMON_CONFIG.enable.set(value);
    }

    /**
     * Gets the maximum blocks destroyed per explosion per tick.
     * @return max blocks per explosion per tick (0 = unlimited)
     */
    public static int getBlocksPerExplosionTick() { 
        return COMMON_CONFIG.blocksPerExplosionTick.get(); 
    }
    
    /**
     * Sets the maximum blocks destroyed per explosion per tick.
     * @param value max blocks per explosion per tick (0 = unlimited)
     */
    public static void setBlocksPerExplosionTick(int value) { 
        COMMON_CONFIG.blocksPerExplosionTick.set(value); 
    }

    /**
     * Gets the maximum explosions moved from awaiting to active queue per tick.
     * @return max explosions per tick
     */
    public static int getExplosionsPerTick() { 
        return COMMON_CONFIG.explosionsPerTick.get(); 
    }
    
    /**
     * Sets the maximum explosions moved from awaiting to active queue per tick.
     * @param value max explosions per tick
     */
    public static void setExplosionsPerTick(int value) { 
        COMMON_CONFIG.explosionsPerTick.set(value); 
    }

    /**
     * Gets the global maximum blocks destroyed per tick across all explosions.
     * @return max blocks per tick (0 = unlimited)
     */
    public static int getMaxBlocksPerTick() { 
        return COMMON_CONFIG.maxBlocksPerTick.get(); 
    }
    
    /**
     * Sets the global maximum blocks destroyed per tick across all explosions.
     * @param value max blocks per tick (0 = unlimited)
     */
    public static void setMaxBlocksPerTick(int value) { 
        COMMON_CONFIG.maxBlocksPerTick.set(value); 
    }

    /**
     * Gets the cascade suppression setting.
     * @return true if cascade suppression is enabled, false otherwise
     */
    public static boolean getCascadeSuppression() { 
        return COMMON_CONFIG.cascadeSuppression.get(); 
    }
    
    /**
     * Sets the cascade suppression setting.
     * @param value true to suppress chain reactions, false to allow them
     */
    public static void setCascadeSuppression(boolean value) { 
        COMMON_CONFIG.cascadeSuppression.set(value); 
    }

    /**
     * Gets the current damage timing mode.
     * @return the damage timing mode
     */
    public static Timing getDamageTiming() { 
        return COMMON_CONFIG.damageTiming.get(); 
    }
    
    /**
     * Sets the damage timing mode.
     * @param value the new damage timing mode
     */
    public static void setDamageTiming(Timing value) { 
        COMMON_CONFIG.damageTiming.set(value); 
    }

    /**
     * Gets the current sound timing mode.
     * @return the sound timing mode
     */
    public static Timing getSoundTiming() { 
        return COMMON_CONFIG.soundTiming.get(); 
    }
    
    /**
     * Sets the sound timing mode.
     * @param value the new sound timing mode
     */
    public static void setSoundTiming(Timing value) { 
        COMMON_CONFIG.soundTiming.set(value); 
    }

    /**
     * Gets the sound volume split setting.
     * @return true if volume is split between phases, false for full volume each phase
     */
    public static boolean getSoundVolumeSplit() { 
        return COMMON_CONFIG.soundVolumeSplit.get(); 
    }
    
    /**
     * Sets the sound volume split setting.
     * @param value true to split volume, false for full volume each phase
     */
    public static void setSoundVolumeSplit(boolean value) { 
        COMMON_CONFIG.soundVolumeSplit.set(value); 
    }

    /**
     * Gets the current particle timing mode.
     * @return the particle timing mode
     */
    public static Timing getParticleTiming() { 
        return COMMON_CONFIG.particleTiming.get(); 
    }
    
    /**
     * Sets the particle timing mode.
     * @param value the new particle timing mode
     */
    public static void setParticleTiming(Timing value) { 
        COMMON_CONFIG.particleTiming.set(value); 
    }

    /**
     * Gets the particle split setting.
     * @return true if particle count is split between phases, false for full count each phase
     */
    public static boolean getParticleSplit() { 
        return COMMON_CONFIG.particleSplit.get(); 
    }
    
    /**
     * Sets the particle split setting.
     * @param value true to split particles, false for full count each phase
     */
    public static void setParticleSplit(boolean value) { 
        COMMON_CONFIG.particleSplit.set(value); 
    }

    /**
     * Gets the current knockback timing mode.
     * @return the knockback timing mode
     */
    public static Timing getKnockbackTiming() { 
        return COMMON_CONFIG.knockbackTiming.get(); 
    }
    
    /**
     * Sets the knockback timing mode.
     * @param value the new knockback timing mode
     */
    public static void setKnockbackTiming(Timing value) { 
        COMMON_CONFIG.knockbackTiming.set(value); 
    }
}
