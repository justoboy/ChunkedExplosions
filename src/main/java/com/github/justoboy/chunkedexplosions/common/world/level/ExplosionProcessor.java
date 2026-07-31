package com.github.justoboy.chunkedexplosions.common.world.level;

import com.github.justoboy.chunkedexplosions.core.ModConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Orchestrates the dual-queue explosion processing system.
 * 
 * Queue Architecture:
 * 
 *   ┌─────────────────────────┐         ┌─────────────────────────┐
 *   │    AWAITING QUEUE       │ ──────► │     ACTIVE QUEUE        │
 *   │  (preCalcPending)       │         │  (preCalcComplete)      │
 *   │  • New explosions       │         │  • Pre-calculated       │
 *   │  • Not calculated       │         │  • Ready to process     │
 *   │  • FIFO order           │         │  • Being processed      │
 *   └─────────────────────────┘         └─────────────────────────┘
 * 
 * Processing Flow:
 * 1. New explosions added to awaiting queue
 * 2. tryMoveToActiveQueue() moves explosions when space available
 * 3. processActiveQueue() handles tick-based block destruction
 * 4. Completed explosions removed from active queue
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
     * 
     * Processing order:
     * 1. Reset blocksDestroyedThisTick counter
     * 2. Try to move explosions from awaiting to active queue
     * 3. Process all active explosions for this tick
     * 4. Remove completed explosions
     */
    public void onServerTick(ServerLevel level) {
        if (!initialized) {
            return;
        }

        // Reset the block counter for this tick
        blocksDestroyedThisTick = 0;

        // Update config values for all states
        updateConfig();

        // Try to move explosions from awaiting to active queue
        tryMoveToActiveQueue();

        // Process active queue
        processActiveQueue(level);

        // Remove completed explosions
        removeCompletedExplosions();
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
     * Moves explosions from the awaiting queue to the active queue when space is available.
     * Pre-calculates all explosion effects during the move.
     * 
     * Logic:
     * - While (activeQueue.size < explosionsPerTick) AND (!awaitingQueue.isEmpty())
     *   - Pre-calculate explosion
     *   - Move to active queue
     */
    public void tryMoveToActiveQueue() {
        int maxExplosions = ModConfig.getExplosionsPerTick();
        
        while (activeQueue.size() < maxExplosions && !awaitingQueue.isEmpty()) {
            ExplosionState state = awaitingQueue.peek();
            if (state == null) {
                break;
            }

            // Pre-calculate all explosion effects
            state.preCalculate();

            // Apply START timing effects
            applyStartTimingEffects(state);

            // Move to active queue
            awaitingQueue.poll();
            activeQueue.add(state);

            LOGGER.debug("Moved explosion to active queue: {} active, {} awaiting", 
                    activeQueue.size(), awaitingQueue.size());
        }
    }

    /**
      * Applies START timing effects for an explosion.
      * This includes damage, knockback, sound, and particles based on config.
      */
     private void applyStartTimingEffects(ExplosionState state) {
         // Apply damage (START timing)
         state.applyDamage(null);

         // Apply knockback (START timing)
         state.applyKnockback(null);

         // Play sound (START timing)
         state.playSound();

         // Spawn particles (START timing)
         state.spawnParticles();
     }

    /**
     * Processes all active explosions for this tick.
     * 
     * Logic:
     * - For each explosion in active queue:
     *   - If (blocksDestroyedThisTick >= maxBlocksPerTick) BREAK
     *   - Process explosion: destroy up to blocksPerExplosionTick blocks
     *   - Mark for removal if complete
     */
    private void processActiveQueue(ServerLevel level) {
        int maxBlocksPerTick = ModConfig.getMaxBlocksPerTick();

        // Collect explosions to remove
        Queue<ExplosionState> explosionsToRemove = new ArrayDeque<>();

        for (ExplosionState state : activeQueue) {
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
            boolean isComplete = state.processTick(level);

            // Calculate blocks destroyed this tick for this explosion
            int blocksThisTick = state.getBlocksDestroyed() - blocksBefore;
            blocksDestroyedThisTick += blocksThisTick;

            if (isComplete) {
                // Apply END timing effects
                applyEndTimingEffects(state);
                LOGGER.debug("Explosion complete at {}: {} blocks destroyed", 
                        state.getPosition(), state.getBlocksDestroyed());
                explosionsToRemove.add(state);
            }
        }

        // Remove completed explosions
        activeQueue.removeAll(explosionsToRemove);
    }

    /**
      * Applies END timing effects for an explosion.
      * This includes final damage, knockback, sound, and particles based on config.
      */
     private void applyEndTimingEffects(ExplosionState state) {
         // Finalize damage (END timing)
         state.finalizeDamage(null);

         // Finalize knockback (END timing)
         state.finalizeKnockback(null);

         // Finalize sound (END timing)
         state.finalizeSound();

         // Finalize particles (END timing)
         state.finalizeParticles();
     }

    /**
     * Removes completed explosions from the active queue.
     */
    private void removeCompletedExplosions() {
        Queue<ExplosionState> tempQueue = new ArrayDeque<>();
        int removedCount = 0;

        for (ExplosionState state : activeQueue) {
            if (state.isComplete()) {
                removedCount++;
            } else {
                tempQueue.add(state);
            }
        }

        if (removedCount > 0) {
            activeQueue.clear();
            activeQueue.addAll(tempQueue);
            LOGGER.debug("Removed {} completed explosions: {} remaining", 
                    removedCount, activeQueue.size());
        }
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
