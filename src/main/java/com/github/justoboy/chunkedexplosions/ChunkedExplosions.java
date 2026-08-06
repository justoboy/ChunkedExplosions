package com.github.justoboy.chunkedexplosions;

import com.github.justoboy.chunkedexplosions.common.command.CompareTimingModeExplosionCommand;
import com.github.justoboy.chunkedexplosions.common.world.level.ExplosionProcessor;
import com.github.justoboy.chunkedexplosions.core.ModConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(ChunkedExplosions.MODID)
public class ChunkedExplosions {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "chunkedexplosions";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    // Define the static instance variable
    public static ChunkedExplosions INSTANCE;

    // The explosion processor that manages the dual-queue system
    private ExplosionProcessor explosionProcessor;

    public ChunkedExplosions(FMLJavaModLoadingContext context) {
        // Assign the instance inside the constructor
        INSTANCE = this;

        // Initialize the explosion processor
        this.explosionProcessor = new ExplosionProcessor();

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register the event handler for explosions
        MinecraftForge.EVENT_BUS.addListener(this::onExplosionStart);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        context.registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, ModConfig.CONFIG_SPEC);
    }

    /**
     * Gets the explosion processor instance.
     * This is used by commands and other systems to access the processor.
     */
    public static ExplosionProcessor getExplosionProcessor() {
        return ChunkedExplosions.INSTANCE != null ? ChunkedExplosions.INSTANCE.explosionProcessor : null;
    }

    private void onExplosionStart(ExplosionEvent.Start event) {
        if (ModConfig.getEnable()) {
            Explosion explosion = event.getExplosion();
            Level level = event.getLevel();

            if (level instanceof ServerLevel serverLevel) {
                // Add to the explosion processor's awaiting queue
                if (getExplosionProcessor() != null) {
                    getExplosionProcessor().addExplosion(serverLevel, explosion);
                    LOGGER.debug("Added explosion to awaiting queue. Total pending: {}", 
                            getExplosionProcessor().getTotalPendingExplosions());
                }
                // Cancel the original explosion
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            if (getExplosionProcessor() == null) {
                return;
            }
            
            // 1. CRITICAL FIX: Process global explosion logic exactly ONCE per tick.
            // Pass the server instance so the processor can iterate over all dimensions internally.
            // This prevents the 3x tick desync bug where timers decreased 3x faster.
            getExplosionProcessor().onServerTick(event.getServer());

            // 2. Advance your command test engine exactly ONCE per tick.
            // Pass the main server instance so your manager can extract the correct dimension dynamically.
            CompareTimingModeExplosionCommand.onServerTick(event.getServer());
        }
    }
}
