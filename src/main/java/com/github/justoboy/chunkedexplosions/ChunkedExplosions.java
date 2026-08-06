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
/**
 * ChunkedExplosions - Main Mod Entry Point
 * 
 * <p>This is the primary entry point for the ChunkedExplosions mod. The mod transforms
 * instant Minecraft explosions into controlled, tick-by-tick processes to prevent
 * server lag and enable precise timing control.</p>
 * 
 * <h2>Mod Lifecycle</h2>
 * <ol>
 *   <li>Constructor is called when Forge loads the mod</li>
 *   <li>ExplosionProcessor is initialized for queue management</li>
 *   <li>Event listeners are registered for explosion and tick events</li>
 *   <li>Config is registered for runtime configuration</li>
 * </ol>
 * 
 * <h2>Key Responsibilities</h2>
 * <ul>
 *   <li>Intercept explosion events before they execute</li>
 *   <li>Create ExplosionState objects for queued processing</li>
 *   <li>Coordinate server tick processing for all dimensions</li>
 *   <li>Provide global access to the explosion processor</li>
 * </ul>
 * 
 * @author justoboy
 * @version 1.0
 * @see ExplosionProcessor
 * @see ExplosionState
 * @see ModConfig
 */
public class ChunkedExplosions {

    /**
     * The mod identifier used throughout the project.
     * Must match the entry in META-INF/mods.toml.
     */
    public static final String MODID = "chunkedexplosions";

    /**
     * SLF4J logger for this mod class.
     * Used for debug, info, warn, and error logging throughout the mod.
     */
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Static instance reference to the mod class.
     * Provides global access to the mod's functionality from anywhere in the codebase.
     * Initialized in the constructor during mod loading.
     */
    public static ChunkedExplosions INSTANCE;

    /**
     * The explosion processor that manages the dual-queue system.
     * Handles all queued explosions across all dimensions.
     * Created during mod initialization.
     * 
     * @see ExplosionProcessor
     */
    private ExplosionProcessor explosionProcessor;

    /**
     * Constructor called by Forge during mod loading.
     * 
     * <p>This method performs the following initialization tasks:</p>
     * <ol>
     *   <li>Stores the mod instance for global access</li>
     *   <li>Creates the ExplosionProcessor for queue management</li>
     *   <li>Registers the mod's event bus handler</li>
     *   <li>Registers the explosion start event listener</li>
     *   <li>Registers the mod's configuration specification</li>
     * </ol>
     * 
     * @param context The FML mod loading context providing access to mod configuration
     */
    public ChunkedExplosions(FMLJavaModLoadingContext context) {
        // Assign the instance inside the constructor for global access
        INSTANCE = this;

        // Initialize the explosion processor - this is the core component
        // that manages all queued explosions across dimensions
        this.explosionProcessor = new ExplosionProcessor();

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register the event handler for explosions - this is where we intercept
        // all explosion events and convert them to chunked processing
        MinecraftForge.EVENT_BUS.addListener(this::onExplosionStart);

        // Register our mod's ForgeConfigSpec so that Forge can create and load
        // the config file for us. This allows runtime configuration of all
        // mod parameters via commands or config file.
        context.registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, ModConfig.CONFIG_SPEC);
    }

    /**
     * Gets the explosion processor instance.
     * 
     * <p>This is the primary way for other parts of the mod (commands, tests, etc.)
     * to access the explosion processing system. Returns null if the mod hasn't
     * been initialized yet.</p>
     * 
     * @return The ExplosionProcessor instance, or null if not initialized
     * @see ExplosionProcessor
     */
    public static ExplosionProcessor getExplosionProcessor() {
        return ChunkedExplosions.INSTANCE != null ? ChunkedExplosions.INSTANCE.explosionProcessor : null;
    }

    /**
     * Handles explosion start events by intercepting and queuing them.
     * 
     * <p>This method is called by Forge whenever any explosion event starts. It:</p>
     * <ol>
     *   <li>Checks if chunked explosions are enabled in config</li>
     *   <li>Verifies the level is a server level (not client)</li>
     *   <li>Adds the explosion to the processor's awaiting queue</li>
     *   <li>Cancels the original instant explosion</li>
     * </ol>
     * 
     * <p>If chunked explosions are disabled, this method does nothing and the
     * vanilla explosion proceeds normally.</p>
     * 
     * @param event The explosion start event from Forge
     * @see ExplosionEvent.Start
     * @see ExplosionProcessor#addExplosion(ServerLevel, Explosion)
     */
    private void onExplosionStart(ExplosionEvent.Start event) {
        // Only process if chunked explosions are enabled
        if (ModConfig.getEnable()) {
            Explosion explosion = event.getExplosion();
            Level level = event.getLevel();

            // Only process on the server side to prevent client-side desync
            if (level instanceof ServerLevel serverLevel) {
                // Add to the explosion processor's awaiting queue
                if (getExplosionProcessor() != null) {
                    getExplosionProcessor().addExplosion(serverLevel, explosion);
                    LOGGER.debug("Added explosion to awaiting queue. Total pending: {}", 
                            getExplosionProcessor().getTotalPendingExplosions());
                }
                // Cancel the original explosion - it will be processed chunked instead
                event.setCanceled(true);
            }
        }
    }

    /**
     * Handles server tick events to process queued explosions.
     * 
     * <p>This method is called every server tick and performs:</p>
     * <ol>
     *   <li>Processing all queued explosions across all dimensions</li>
     *   <li>Advancing the test command engine for comparison testing</li>
     * </ol>
     * 
     * <p><strong>Important:</strong> This only runs on the server tick END phase
     * to ensure all game logic for this tick is complete before processing explosions.</p>
     * 
     * @param event The server tick event from Forge
     * @see ExplosionProcessor#onServerTick(net.minecraft.server.MinecraftServer)
     * @see CompareTimingModeExplosionCommand#onServerTick(net.minecraft.server.MinecraftServer)
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        // Only process on the END phase to avoid interfering with other tick logic
        if (event.phase == TickEvent.Phase.END) {
            // Safety check - processor should always exist after mod init
            if (getExplosionProcessor() == null) {
                return;
            }
            
            // 1. Process global explosion logic exactly ONCE per tick.
            // Pass the server instance so the processor can iterate over all dimensions internally.
            // This prevents the 3x tick desync bug where timers decreased 3x faster.
            getExplosionProcessor().onServerTick(event.getServer());

            // 2. Advance the command test engine exactly ONCE per tick.
            // Pass the main server instance so the manager can extract the correct dimension dynamically.
            // This is used for the compare timing mode testing system.
            CompareTimingModeExplosionCommand.onServerTick(event.getServer());
        }
    }
}
