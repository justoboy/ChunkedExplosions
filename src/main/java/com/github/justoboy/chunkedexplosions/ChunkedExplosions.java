package com.github.justoboy.chunkedexplosions;

import com.github.justoboy.chunkedexplosions.core.ModConfig;
import com.google.common.collect.Sets;
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

import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import com.github.justoboy.chunkedexplosions.common.world.level.ChunkedExplosion;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(ChunkedExplosions.MODID)
public class ChunkedExplosions {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "chunkedexplosions";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();


    // Create a queue of explosions to process
    private final Queue<ChunkedExplosion> explosionQueue = new ConcurrentLinkedQueue<>();

    public ChunkedExplosions(FMLJavaModLoadingContext context) {
//        IEventBus modEventBus = context.getModEventBus();

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register the event handler for explosions
        MinecraftForge.EVENT_BUS.addListener(this::onExplosionStart);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        context.registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, ModConfig.CONFIG_SPEC);
    }

    private void onExplosionStart(ExplosionEvent.Start event) {
        if (ModConfig.getEnable()) {
            Explosion explosion = event.getExplosion();
            Level level = event.getLevel();

            if (level instanceof ServerLevel) {
                // Create a new queued explosion
                ChunkedExplosion queuedExplosion = new ChunkedExplosion(explosion);
                explosionQueue.add(queuedExplosion);
                LOGGER.info("Initializing explosion, spot {} in queue.", explosionQueue.size());
                // Cancel the original explosion
                event.setCanceled(true);
            }
        }
    }

    public void tick() {
        if (!explosionQueue.isEmpty()) {
            Set<ChunkedExplosion> updatedExplosions = Sets.newHashSet();
            Set<ChunkedExplosion> finalizedExplosions = Sets.newHashSet();
            for (int i = 0; i < ModConfig.getExplosionsPerTick() || (ModConfig.getExplosionsPerTick() == 0 && i < explosionQueue.size()); i++) {
                ChunkedExplosion queuedExplosion = explosionQueue.poll();
                if (queuedExplosion != null) {
                    if (queuedExplosion.tick()) {
                        finalizedExplosions.add(queuedExplosion);
                    } else {
                        explosionQueue.add(queuedExplosion);
                        updatedExplosions.add(queuedExplosion);
                    }
                }
            }
            for (ChunkedExplosion explosion : updatedExplosions) {
                explosion.update();
            }
            LOGGER.info("Finalizing {} explosions, {} left in queue.", finalizedExplosions.size(), explosionQueue.size());
            for (ChunkedExplosion explosion : finalizedExplosions) {
                explosion.finalizeExplosion();
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            tick();
        }
    }
}
