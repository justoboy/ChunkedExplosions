package com.github.justoboy.chunkedexplosions.common.world.level;

import com.github.justoboy.chunkedexplosions.core.ModConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Orchestrates the dual-queue explosion processing system.
 * 
 * <h2>Overview</h2>
 * <p>The ExplosionProcessor is the central component that manages all chunked explosions
 * across all dimensions. It implements a dual-queue architecture to efficiently handle
 * explosions while maintaining performance limits.</p>
 * 
 * <h2>Queue Architecture</h2>
 * <pre>
 *   ┌─────────────────────────┐         ┌─────────────────────────┐
 *   │    AWAITING QUEUE       │ ──────► │     ACTIVE QUEUE        │
 *   │  (preCalcPending)       │         │  (preCalcComplete)      │
 *   │  • New explosions       │         │  • Pre-calculated       │
 *   │  • Not calculated       │         │  • Ready to process     │
 *   │  • FIFO order           │         │  • Being processed      │
 *   └─────────────────────────┘         └─────────────────────────┘
 * </pre>
 * 
 * <h2>Processing Flow</h2>
 * <ol>
 *   <li><strong>Add Explosion:</strong> When a vanilla explosion is intercepted,
 *       it's converted to an ExplosionState and added to the awaiting queue.</li>
 *   <li><strong>Move to Active:</strong> Each tick, explosions are moved from awaiting
 *       to active queue when space is available (limited by explosionsPerTick).</li>
 *   <li><strong>Pre-calculate:</strong> Before moving to active queue, the explosion
 *       is pre-calculated (ray-casting and entity effects).</li>
 *   <li><strong>Apply START Effects:</strong> START timing effects are applied
 *       when explosion enters active queue.</li>
 *   <li><strong>Process Tick:</strong> Each tick, active explosions process their
 *       block destruction (limited by blocksPerExplosionTick and maxBlocksPerTick).</li>
 *   <li><strong>Apply SPREAD Effects:</strong> SPREAD timing effects are applied
 *       once per tick after block destruction.</li>
 *   <li><strong>Complete:</strong> When all blocks are destroyed, END timing effects
 *       are applied and the explosion is removed from the queue.</li>
 * </ol>
 * 
 * <h2>Per-Dimension Processing</h2>
 * <p>The processor iterates over all ServerLevels (dimensions) and processes only
 * explosions that belong to each specific dimension. This prevents cross-dimensional
 * desync issues where explosions in one dimension could affect processing in another.</p>
 * 
 * <h2>Performance Controls</h2>
 * <ul>
 *   <li><strong>explosionsPerTick:</strong> Limits how many explosions can move from
 *       awaiting to active queue per tick.</li>
 *   <li><strong>blocksPerExplosionTick:</strong> Limits how many blocks each explosion
 *       can destroy per tick.</li>
 *   <li><strong>maxBlocksPerTick:</strong> Global limit on blocks destroyed per tick
 *       across all explosions.</li>
 * </ul>
 * 
 * @author justoboy
 * @see ExplosionState
 * @see ModConfig
 */
public class ExplosionProcessor {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Queue of explosions waiting to be pre-calculated */
    private final Queue<ExplosionState> awaitingQueue = new ArrayDeque<>();

    /** Queue of explosions ready to be processed */
    private final Queue<ExplosionState> activeQueue = new ArrayDeque<>();

    /** Total blocks destroyed this tick (for global cap) */
    private int blocksDestroyedThisTick;

    /** Whether the processor has been initialized */
    private boolean initialized = false;

    public ExplosionProcessor() {
        this.initialized = true;
        this.blocksDestroyedThisTick = 0;
    }

    /**
     * Adds a new explosion to the awaiting queue.
     * The explosion will be pre-calculated when space becomes available in the active queue.
     * 
     * @param level the server level
     * @param explosion the vanilla explosion
     * @return the created ExplosionState
     */
    public ExplosionState addExplosion(ServerLevel level, Explosion explosion) {
        if (!initialized) {
            LOGGER.warn("Cannot add explosion to uninitialized processor");
            return null;
        }

        ExplosionState state = new ExplosionState(explosion);
        awaitingQueue.add(state);
        LOGGER.debug("Added explosion to awaiting queue: {} total", awaitingQueue.size());
        return state;
    }

    /**
     * Main processing method called each server tick.
     * Accepts MinecraftServer to iterate over all dimensions internally.
     * 
     * Processing order:
     * 1. Reset blocksDestroyedThisTick counter
     * 2. Try to move explosions from awaiting to active queue
     * 3. Process all active explosions for this tick
     * 4. Remove completed explosions
     */
    public void onServerTick(MinecraftServer server) {
        if (!initialized) {
            return;
        }

        // Reset the block counter for this tick
        blocksDestroyedThisTick = 0;

        // Update config values for all states
        updateConfig();

        // Process all dimensions - iterate over all ServerLevels
        // CRITICAL: Only process explosions that belong to the current dimension
        for (ServerLevel serverLevel : server.getAllLevels()) {
            if (serverLevel != null && !serverLevel.isClientSide()) {
                tryMoveToActiveQueueForDimension(serverLevel);
                processActiveQueueForDimension(serverLevel);
            }
        }
    }

    /**
     * Updates configuration values for all active explosion states.
     */
    private void updateConfig() {
        for (ExplosionState state : activeQueue) {
            state.updateConfig();
        }
        for (ExplosionState state : awaitingQueue) {
            state.updateConfig();
        }
    }

    /**
     * Applies START timing effects for an explosion.
     * This includes damage, knockback, sound, and particles based on config.
     */
    private void applyStartTimingEffects(ExplosionState state, ServerLevel level) {
        // Apply damage (START timing)
        state.applyDamage(level);

        // Apply knockback (START timing)
        state.applyKnockback(level);

        // Play sound (START timing)
        state.playSound();

        // Spawn particles (START timing)
        state.spawnParticles();
    }

    /**
     * Moves explosions from the awaiting queue to the active queue when space is available.
     * Only processes explosions that belong to the specified dimension.
     * 
     * Logic:
     * - While (activeQueue.size < explosionsPerTick) AND (!awaitingQueue.isEmpty())
     *   - Check if explosion belongs to this dimension
     *   - If yes: Pre-calculate explosion, apply START timing effects, move to active queue
     *   - If no: Skip this explosion (it belongs to a different dimension)
     * 
     * @param serverLevel the dimension to process explosions for
     */
    private void tryMoveToActiveQueueForDimension(ServerLevel serverLevel) {
        int maxExplosions = ModConfig.getExplosionsPerTick();
        
        // Handle empty awaiting queue
        if (awaitingQueue.isEmpty()) {
            return;
        }
        
        // We need to process the queue carefully to skip explosions from other dimensions
        // Use a temporary queue to hold explosions that don't belong to this dimension
        Queue<ExplosionState> otherDimensionExplosions = new ArrayDeque<>();
        
        while (activeQueue.size() < maxExplosions && !awaitingQueue.isEmpty()) {
            ExplosionState state = awaitingQueue.poll();
            if (state == null) {
                break;
            }
            
            // Check if this explosion belongs to the current dimension
            if (!state.getLevel().dimension().equals(serverLevel.dimension())) {
                // This explosion belongs to a different dimension, hold it for later
                otherDimensionExplosions.add(state);
                continue;
            }
            
            // Pre-calculate all explosion effects
            state.preCalculate();

            // Apply START timing effects
            applyStartTimingEffects(state, serverLevel);

            // Move to active queue
            activeQueue.add(state);

            LOGGER.debug("Moved explosion to active queue: {} active, {} awaiting",
                    activeQueue.size(), awaitingQueue.size() + otherDimensionExplosions.size());
        }
        
        // Put back explosions from other dimensions
        while (!otherDimensionExplosions.isEmpty()) {
            awaitingQueue.add(otherDimensionExplosions.poll());
        }
    }

    /**
     * Processes all active explosions for this tick that belong to the specified dimension.
     * 
     * Logic:
     * - For each explosion in active queue:
     *   - Check if explosion belongs to this dimension
     *   - If no: Skip this explosion
     *   - If yes: Process explosion normally
     *   - If (blocksDestroyedThisTick >= maxBlocksPerTick) BREAK
     *   - Mark for removal if complete
     * 
     * @param serverLevel the dimension to process explosions for
     */
    private void processActiveQueueForDimension(ServerLevel serverLevel) {
        // Handle empty active queue
        if (activeQueue.isEmpty()) {
            return;
        }
        
        int maxBlocksPerTick = ModConfig.getMaxBlocksPerTick();

        // Collect explosions to remove
        Queue<ExplosionState> explosionsToRemove = new ArrayDeque<>();

        for (ExplosionState state : activeQueue) {
            // Check if this explosion belongs to the current dimension
            if (!state.getLevel().dimension().equals(serverLevel.dimension())) {
                // This explosion belongs to a different dimension, skip it
                continue;
            }
            
            // Check global block cap
            if (maxBlocksPerTick > 0 && blocksDestroyedThisTick >= maxBlocksPerTick) {
                break;
            }

            // Check if explosion is already complete
            if (state.isComplete()) {
                explosionsToRemove.add(state);
                continue;
            }

            // Track blocks before processing
            int blocksBefore = state.getBlocksDestroyed();

            // Process this explosion
            boolean isComplete = state.processTick(serverLevel);

            // Calculate blocks destroyed this tick for this explosion
            int blocksThisTick = state.getBlocksDestroyed() - blocksBefore;
            blocksDestroyedThisTick += blocksThisTick;

            if (isComplete) {
                // Apply END timing effects
                applyEndTimingEffects(state, serverLevel);
                LOGGER.debug("Explosion complete at {}: {} blocks destroyed",
                        state.getPosition(), state.getBlocksDestroyed());
            }
        }

        // Remove completed explosions
        activeQueue.removeIf(state -> state.isComplete());
    }

    /**
     * Applies END timing effects for an explosion.
     * This includes final damage, knockback, sound, and particles based on config.
     */
    private void applyEndTimingEffects(ExplosionState state, ServerLevel level) {
        // Finalize damage (END timing)
        state.finalizeDamage(level);

        // Finalize knockback (END timing)
        state.finalizeKnockback(level);

        // Finalize sound (END timing)
        state.finalizeSound();

        // Finalize particles (END timing)
        state.finalizeParticles();
    }

    /**
     * Gets the number of explosions in the awaiting queue.
     */
    public int getAwaitingQueueSize() {
        return awaitingQueue.size();
    }

    /**
     * Gets the number of explosions in the active queue.
     */
    public int getActiveQueueSize() {
        return activeQueue.size();
    }

    /**
     * Gets the total number of pending explosions (awaiting + active).
     */
    public int getTotalPendingExplosions() {
        return awaitingQueue.size() + activeQueue.size();
    }

    /**
     * Gets the total blocks destroyed this tick.
     */
    public int getBlocksDestroyedThisTick() {
        return blocksDestroyedThisTick;
    }

    /**
     * Gets the remaining blocks this tick before hitting the global cap.
     */
    public int getRemainingBlocksThisTick() {
        int maxBlocksPerTick = ModConfig.getMaxBlocksPerTick();
        if (maxBlocksPerTick > 0) {
            return Math.max(0, maxBlocksPerTick - blocksDestroyedThisTick);
        }
        return Integer.MAX_VALUE;
    }

    /**
     * Checks if the processor is empty (no pending explosions).
     */
    public boolean isEmpty() {
        return awaitingQueue.isEmpty() && activeQueue.isEmpty();
    }

    /**
     * Clears all queues (useful for config changes or world reloads).
     */
    public void clear() {
        awaitingQueue.clear();
        activeQueue.clear();
        blocksDestroyedThisTick = 0;
        LOGGER.info("Cleared all explosion queues");
    }

    @Override
    public String toString() {
        return "ExplosionProcessor{active=" + activeQueue.size() + 
               ", awaiting=" + awaitingQueue.size() + 
               ", blocksThisTick=" + blocksDestroyedThisTick + "}";
    }
}
