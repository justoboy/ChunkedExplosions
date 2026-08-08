package com.github.justoboy.chunkedexplosions.common.world.level;

import com.github.justoboy.chunkedexplosions.core.ModConfig;
import com.github.justoboy.chunkedexplosions.iduck.world.level.IExplosionDuck;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.ProtectionEnchantment;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Encapsulates all data for a single chunked explosion.
 * 
 * Lifecycle:
 * 1. Created when explosion is intercepted (awaiting pre-calculation)
 * 2. Pre-calculated when moving to active queue (ray-casting + entity effects)
 * 3. Processed each tick until complete (block destruction)
 * 4. Removed when finished
 * 
 * State is divided into:
 * - Immutable Data: Original explosion parameters (never change)
 * - Pre-calculated Data: Computed once during pre-calculation phase
 * - Processing State: Updated during tick-based processing
 * - Configuration: Settings from ModConfig
 */
public class ExplosionState {

    private static final Logger LOGGER = LogUtils.getLogger();

    // === Immutable Data (set once, never changed) ===

    /** Reference to the original vanilla explosion */
    private final Explosion originalExplosion;

    /** The level where the explosion occurs */
    private final Level level;

    /** The entity that caused the explosion (null if none) */
    private final Entity source;

    /** Explosion center position */
    private final Vec3 position;

    /** Explosion radius */
    private final float radius;

    /** Whether to set fire to nearby blocks */
    private final boolean fire;

    /** Block interaction mode */
    private final Explosion.BlockInteraction blockInteraction;

    // === Pre-calculated Data (computed once during pre-calculation) ===

    /** Pre-calculated set of blocks to destroy */
    private Set<BlockPos> blocksToDestroy;

    /** Ordered list of blocks for efficient iteration (populated after pre-calculation) */
    private List<BlockPos> blocksList;

    /** Pre-calculated entity effects data */
    private List<EntityInfo> affectedEntities;

    /** Whether pre-calculation is complete */
    private boolean preCalculationComplete;

    /** Block destroyer instance for handling block destruction */
    private BlockDestroyer blockDestroyer;

    // === Processing State (updated during tick processing) ===

    /** Index of the next block to destroy in blocksToDestroy */
    private int currentBlockIndex;

    /** Count of blocks destroyed so far */
    private int blocksDestroyed;

    /** Set of entities that have been damaged */
    private final Set<Entity> damagedEntities;

    /** Set of entities that have been knocked back */
    private final Set<Entity> knockedBackEntities;

    /** Accumulated damage per entity for SPREAD timing */
    private final Map<Entity, Float> accumulatedDamage;

    /** Accumulated knockback per entity for SPREAD timing */
    private final Map<Entity, Vec3> accumulatedKnockback;

    /** Whether all effects (damage/knockback/sound/particles) are complete */
    private boolean effectsComplete;

    /** Whether the sound has been played */
    private boolean soundPlayed;

    /** Whether particles have been spawned */
    private boolean particlesSpawned;

    /** Track blocks that contributed to particle accumulation for SPREAD timing */
    private int particlesAccumulatedBlocks;

    /** Fractional particle accumulator for SPREAD timing with particleSplit=true */
    private float particleFractionAccumulator;

    /** Track blocks destroyed this tick for SPREAD timing calculations */
    private int blocksDestroyedThisTick;

    // === Configuration (read from ModConfig) ===

    /** Current damage timing mode */
    private ModConfig.Timing damageTiming;

    /** Current knockback timing mode */
    private ModConfig.Timing knockbackTiming;

    /** Current sound timing mode */
    private ModConfig.Timing soundTiming;

    /** Current particle timing mode */
    private ModConfig.Timing particleTiming;

    /** Current blocks per explosion per tick */
    private int blocksPerExplosionTick;

    /**
     * Creates a new ExplosionState from a vanilla explosion.
     * All immutable data is initialized here; pre-calculation happens later.
     */
    public ExplosionState(Explosion explosion) {
        LOGGER.info("EXPLOSION_STATE_CONSTRUCTOR: Creating state for explosion at {}, radius {}", 
                   ((IExplosionDuck) explosion).chunked_getPosition(), 
                   ((IExplosionDuck) explosion).chunked_getRadius());
        this.originalExplosion = explosion;
        IExplosionDuck duck = (IExplosionDuck) explosion;
        this.level = duck.chunked_getLevel();
        this.source = duck.chunked_getSource();
        this.position = duck.chunked_getPosition();
        this.radius = duck.chunked_getRadius();
        this.fire = duck.chunked_isFire();
        this.blockInteraction = duck.chunked_getBlockInteraction();
        
        this.blocksToDestroy = Sets.newHashSet();
        this.blocksList = Lists.newArrayList();
        this.affectedEntities = Lists.newArrayList();
        this.preCalculationComplete = false;
        
        // Initialize block destroyer
        this.blockDestroyer = new BlockDestroyer(blockInteraction, radius, source, fire);
        
        this.currentBlockIndex = 0;
        this.blocksDestroyed = 0;
        this.damagedEntities = Sets.newHashSet();
        this.knockedBackEntities = Sets.newHashSet();
        this.accumulatedDamage = Maps.newHashMap();
        this.accumulatedKnockback = Maps.newHashMap();
        this.effectsComplete = false;
        this.soundPlayed = false;
        this.particlesSpawned = false;
        this.particlesAccumulatedBlocks = 0;
        this.particleFractionAccumulator = 0.0F;
        this.blocksDestroyedThisTick = 0;
        
        this.damageTiming = ModConfig.getDamageTiming();
        this.knockbackTiming = ModConfig.getKnockbackTiming();
        this.soundTiming = ModConfig.getSoundTiming();
        this.particleTiming = ModConfig.getParticleTiming();
        this.blocksPerExplosionTick = ModConfig.getBlocksPerExplosionTick();
    }

    // === Pre-calculation ===

    /**
     * Performs pre-calculation of all explosion effects.
     * This includes:
     * - Deterministic ray-casting to determine which blocks to destroy
     * - Pre-calculating entity visibility, damage, and knockback
     * 
     * Uses deterministic random seed based on source entity ID for reproducible results.
     */
    public void preCalculate() {
        if (preCalculationComplete) {
            return;
        }

        LOGGER.debug("Pre-calculating explosion at {}, radius {}", position, radius);

        // Perform deterministic ray-casting
        this.blocksToDestroy = performRayCasting();

        // Pre-calculate entity effects
        this.affectedEntities = preCalculateEntityEffects();

        // Mark pre-calculation as complete
        this.preCalculationComplete = true;

        // Convert blocksToDestroy to ordered list for efficient O(1) access
        blocksList = Lists.newArrayList(blocksToDestroy);
        
        LOGGER.debug("Pre-calculation complete: {} blocks, {} entities",
                blocksToDestroy.size(), affectedEntities.size());
    }

    /**
     * Performs ray-casting using a 16x16x16 grid surface rays algorithm.
     * This matches the vanilla Explosion.explode() method exactly.
     * 
     * @return Set of blocks to destroy
     */
    private Set<BlockPos> performRayCasting() {
        LOGGER.debug("EXPLOSION_RAYCAST_START: Starting ray-casting for explosion at {}, radius {}", position, radius);

        Set<BlockPos> result = Sets.newHashSet();
        int gridSize = 16; // 16x16x16 grid (indices 0-15)
        
        // Use the level's random source to match vanilla behavior exactly
        RandomSource random = this.level.random;

        // Iterate over the surface of the 16x16x16 grid (matches vanilla exactly)
        // Vanilla: for(int j = 0; j < 16; ++j) { for(int k = 0; k < 16; ++k) { for(int l = 0; l < 16; ++l) {
        //          if (j == 0 || j == 15 || k == 0 || k == 15 || l == 0 || l == 15) { ... }}}
        for (int xIndex = 0; xIndex < gridSize; xIndex++) {
            for (int yIndex = 0; yIndex < gridSize; yIndex++) {
                for (int zIndex = 0; zIndex < gridSize; zIndex++) {
                  
                    // Only process surface points (at least one coordinate at boundary) - matches vanilla
                    if (xIndex == 0 || xIndex == gridSize - 1 ||
                        yIndex == 0 || yIndex == gridSize - 1 ||
                        zIndex == 0 || zIndex == gridSize - 1) {

                        // Calculate normalized coordinates within the grid
                        // Vanilla: (float)j / 15.0F * 2.0F - 1.0F
                        double normalizedX = (double)xIndex / 15.0F * 2.0F - 1.0F;
                        double normalizedY = (double)yIndex / 15.0F * 2.0F - 1.0F;
                        double normalizedZ = (double)zIndex / 15.0F * 2.0F - 1.0F;

                        // Normalize to unit length (direction vector)
                        // Vanilla: Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2); d0 /= d3; etc.
                        double distanceFromCenter = Math.sqrt(
                                normalizedX * normalizedX +
                                normalizedY * normalizedY +
                                normalizedZ * normalizedZ);
                        
                        // Skip center point (no direction) - should only happen at exact center
                        if (distanceFromCenter < 0.0001) continue;
                        
                        normalizedX /= distanceFromCenter;
                        normalizedY /= distanceFromCenter;
                        normalizedZ /= distanceFromCenter;

                        // Generate blast strength (matches vanilla exactly)
                        // Vanilla: this.radius * (0.7F + this.level.random.nextFloat() * 0.6F)
                        float blastStrength = radius * (0.7f + random.nextFloat() * 0.6f);
                        
                        // March ray until blast strength exhausted
                        double currentX = position.x;
                        double currentY = position.y;
                        double currentZ = position.z;
                        
                        // March along ray in 0.3 block increments (matches vanilla exactly)
                        // Vanilla: for(float f1 = 0.3F; f > 0.0F; f -= 0.22500001F)
                        for (float stepSize = 0.3f; blastStrength > 0.0f; blastStrength -= 0.22500001F) {
                            BlockPos blockPos = BlockPos.containing(currentX, currentY, currentZ);

                            // Check world bounds (matches vanilla)
                            if (!level.isInWorldBounds(blockPos)) {
                                break;
                            }
                            
                            // Get block state and fluid state (matches vanilla)
                            BlockState blockState = level.getBlockState(blockPos);
                            FluidState fluidState = level.getFluidState(blockPos);
                            
                            // Calculate explosion resistance (matches vanilla ExplosionDamageCalculator)
                            // Vanilla: Optional<Float> optional = this.damageCalculator.getBlockExplosionResistance(this, this.level, blockpos, blockstate, fluidstate);
                            Optional<Float> optional = getBlockExplosionResistance(blockState, fluidState, blockPos);
                            if (optional.isPresent()) {
                                // Vanilla: f -= (optional.get() + 0.3F) * 0.3F
                                blastStrength -= (optional.get() + 0.3F) * 0.3F;
                            }

                            // Check if block should be destroyed (matches vanilla exactly)
                            // Vanilla: if (f > 0.0F && this.damageCalculator.shouldBlockExplode(this, this.level, blockpos, blockstate, f))
                            if (blastStrength > 0.0F && shouldBlockExplode(blockState, blastStrength)) {
                                result.add(blockPos);
                            }

                            // Advance ray position (matches vanilla: += direction * 0.3)
                            currentX += normalizedX * 0.3;
                            currentY += normalizedY * 0.3;
                            currentZ += normalizedZ * 0.3;
                        }
                    }
                }
            }
        }

        LOGGER.debug("EXPLOSION_RAYCAST_END: Ray-casting complete for explosion at {}, radius {}. Blocks to destroy: {} blocks",
                position, radius, result.size());
//        LOGGER.debug("EXPLOSION_RAYCAST_BLOCKS: Block positions to destroy: {}", result);

        return result;
    }

    /**
     * Calculates the explosion resistance of a block, matching vanilla ExplosionDamageCalculator.
     * Returns the max of block and fluid resistance.
     */
    private Optional<Float> getBlockExplosionResistance(BlockState blockState, FluidState fluidState, BlockPos blockPos) {
        // Get block resistance (matches vanilla ExplosionDamageCalculator)
        float blockResistance = blockState.getExplosionResistance(level, blockPos, originalExplosion);
        
        // Get fluid resistance (matches vanilla ExplosionDamageCalculator)
        float fluidResistance = fluidState.getExplosionResistance(level, blockPos, originalExplosion);
        
        // Return max of both (matches vanilla: Math.max(blockResistance, fluidResistance))
        float resistance = Math.max(blockResistance, fluidResistance);
        
        // Only return if resistance is valid (not infinite - blocks like bedrock have 3000 resistance)
        if (resistance >= 3000.0F) {
            return Optional.empty();
        }
        
        return Optional.of(resistance);
    }

    /**
     * Determines if a block should be destroyed by the explosion.
     * This matches vanilla ExplosionDamageCalculator.shouldBlockExplode() behavior.
     *
     * @param blockState The block state to check
     * @param blastStrength The remaining blast strength at this position
     * @return true if the block should be destroyed
     */
    private boolean shouldBlockExplode(BlockState blockState, float blastStrength) {
        // Vanilla behavior: block is destroyed if blastStrength > 0 and block is not air
        if (blockState.isAir()) {
            return false;
        }
        
        // The block should be destroyed if blast strength is still positive
        // This matches vanilla: if (f > 0.0F && this.damageCalculator.shouldBlockExplode(...))
        return blastStrength > 0.0F;
    }

    /**
     * Pre-calculates entity effects including visibility, damage, and knockback.
     * This is done once during pre-calculation to avoid re-computing during processing.
     */
    private List<EntityInfo> preCalculateEntityEffects() {
        List<EntityInfo> entities = new ObjectArrayList<>();
        
        float effectiveRadius = radius * 2.0f;
        int minX = (int) Math.floor(position.x - effectiveRadius - 1);
        int maxX = (int) Math.floor(position.x + effectiveRadius + 1);
        int minY = (int) Math.floor(position.y - effectiveRadius - 1);
        int maxY = (int) Math.floor(position.y + effectiveRadius + 1);
        int minZ = (int) Math.floor(position.z - effectiveRadius - 1);
        int maxZ = (int) Math.floor(position.z + effectiveRadius + 1);

        AABB blastBox = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        Vec3 explosionCenter = position;

        // Get all entities within the bounding box
        List<Entity> affectedEntities = level.getEntities(source, blastBox);

        for (Entity entity : affectedEntities) {
            if (entity.ignoreExplosion()) {
                continue;
            }

            // Calculate distance to explosion center
            double distanceToEntity = Math.sqrt(entity.distanceToSqr(explosionCenter)) / effectiveRadius;
            
            if (distanceToEntity > 1.0) {
                continue;
            }

            // Calculate visibility factor
            double visibility = Explosion.getSeenPercent(explosionCenter, entity);

            // Calculate impact factor
            double impactFactor = (1.0 - distanceToEntity) * visibility;

            // Calculate damage
            // Vanilla formula uses radius * 2.0F (stored in f2)
            float damage = (float) ((int) ((impactFactor * impactFactor + impactFactor) / 2.0 * 7.0 * (radius * 2.0) + 1));

            // Calculate knockback vector
            double deltaX = entity.getX() - position.x;
            double deltaY = (entity instanceof PrimedTnt ? entity.getY() : entity.getEyeY()) - position.y;
            double deltaZ = entity.getZ() - position.z;
            
            double distanceFromCenter = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
            Vec3 knockbackVector = Vec3.ZERO;
            
            if (distanceFromCenter != 0.0) {
                deltaX /= distanceFromCenter;
                deltaY /= distanceFromCenter;
                deltaZ /= distanceFromCenter;
                
                knockbackVector = new Vec3(deltaX, deltaY, deltaZ);
            }

            entities.add(new EntityInfo(entity, (float) distanceToEntity, (float) visibility, 
                    (float) impactFactor, damage, knockbackVector));
        }

        return entities;
    }

    // === Processing Methods ===

    /**
     * Processes this explosion for one tick.
     * Destroys up to blocksPerExplosionTick blocks.
     * 
     * @return true if all blocks have been destroyed
     */
    public boolean processTick(ServerLevel serverLevel) {
//        LOGGER.debug("EXPLOSION_TICK_START: Processing tick for explosion at {}, radius {}. Current block index: {}/{}",
//                position, radius, currentBlockIndex, blocksToDestroy.size());

        if (!preCalculationComplete) {
            preCalculate();
        }

        if (blocksToDestroy.isEmpty()) {
            // Apply SPREAD effects if any accumulated
            applySpreadEffects(serverLevel);
            effectsComplete = true;
            return true;
        }

        int blocksThisTick = 0;
        int totalBlocks = blocksToDestroy.size();
        this.blocksDestroyedThisTick = 0;

        // Process up to blocksPerExplosionTick blocks (0 means unlimited)
//        LOGGER.debug("EXPLOSION_TICK_LOOP_START: Starting block destruction loop for tick. Blocks to process this tick: {}, blocksPerExplosionTick: {}",
//                totalBlocks - currentBlockIndex, blocksPerExplosionTick);
        while ((blocksPerExplosionTick == 0 || blocksThisTick < blocksPerExplosionTick) && currentBlockIndex < totalBlocks) {
            // Get the next block to destroy
            BlockPos blockPos = getCurrentBlock();
            if (blockPos == null) {
                break;
            }

            // Destroy the block
            destroyBlock(serverLevel, blockPos);
        
            // Accumulate SPREAD timing effects for this block
            accumulateSpreadEffects();
    
            // Accumulate SPREAD timing particle effects for this block
            accumulateParticleEffects();
        
            currentBlockIndex++;
            blocksDestroyed++;
            blocksThisTick++;
            this.blocksDestroyedThisTick++;
        }

//        LOGGER.debug("EXPLOSION_TICK_LOOP_END: Block destruction loop complete for tick. Blocks destroyed this tick: {}. Total progress: {}/{}",
//                blocksDestroyedThisTick, blocksDestroyed, totalBlocks);

        // Apply SPREAD timing effects (accumulated per tick)
        applySpreadEffects(serverLevel);

        // Check if all blocks are destroyed
        if (currentBlockIndex >= totalBlocks) {
//            LOGGER.debug("EXPLOSION_TICK_COMPLETE: Explosion at {}, radius {} has completed all block destruction. Total blocks destroyed: {}",
//                    position, radius, blocksDestroyed);
            effectsComplete = true;
            return true;
        }

//        LOGGER.debug("EXPLOSION_TICK_RETURN: Explosion at {}, radius {} continues. Progress: {}/{}",
//                position, radius, blocksDestroyed, totalBlocks);
        return false;
    }

    /**
     * Gets the current block to process without advancing the index.
     * Uses the pre-computed blocksList for O(1) access instead of O(n) iteration.
     */
    private BlockPos getCurrentBlock() {
        if (blocksList == null || currentBlockIndex >= blocksList.size()) {
            return null;
        }
        
        // O(1) access from the pre-computed list
        return blocksList.get(currentBlockIndex);
    }

    /**
     * Destroys a single block using the BlockDestroyer.
     */
    private void destroyBlock(ServerLevel serverLevel, BlockPos blockPos) {
//        LOGGER.debug("EXPLOSION_BLOCK_DESTROYED: Destroying block at position {} (block destroyed count: {}/{})",
//                blockPos, blocksDestroyed + 1, blocksToDestroy.size());
        blockDestroyer.destroyBlock(serverLevel, blockPos);
    }

    // === Entity Effect Methods ===

    /**
      * Applies damage to entities based on the configured timing mode.
      */
      public void applyDamage(ServerLevel serverLevel) {
          if (damageTiming == ModConfig.Timing.START) {
              applyAllDamage(1.0F);
          } else if (damageTiming == ModConfig.Timing.START_END) {
              applyAllDamage(0.5F);
          }
          // SPREAD: accumulation happens during block destruction
          // END: handled in finalizeDamage()
      }

      /**
       * Finalizes damage application (END timing).
       */
      public void finalizeDamage(ServerLevel serverLevel) {
          if (damageTiming == ModConfig.Timing.END) {
              applyAllDamage(1.0F);
          } else if (damageTiming == ModConfig.Timing.START_END) {
              applyAllDamage(0.5F);
          } else if (damageTiming == ModConfig.Timing.SPREAD) {
              if (!accumulatedDamage.isEmpty()) {
                  applySpreadDamage(serverLevel);
              } else if (!affectedEntities.isEmpty()) {
                  // No blocks were destroyed but entities were detected (e.g., explosion in air)
                  // Apply full damage to all affected entities
                  applyAllDamage(1.0F);
              }
          }
      }

     /**
      * Applies damage to all affected entities.
      */
      private void applyAllDamage(float spread) {
          for (EntityInfo entityInfo : affectedEntities) {
              Entity entity = entityInfo.getEntity();
            
              if (entity.isAlive() && !damagedEntities.contains(entity)) {
                  float damage = entityInfo.getDamage();
                  if (Math.abs(spread - 1.0F) > 1e-6F) {
                      damage *= spread;
                  }
                              // Use the original explosion's damage source via Duck interface
                  DamageSource damageSource = ((IExplosionDuck) originalExplosion).chunked_getDamageSource();
                  entity.hurt(damageSource, damage);
                  // Only mark as damaged for full applications (1.0); partial applications (0.5 or SPREAD)
                  // should not mark entities so they can receive subsequent applications
                  if (Math.abs(spread - 1.0F) <= 1e-6F) {
                      damagedEntities.add(entity);
                  }
              }
          }
      }

    /**
      * Applies knockback to entities based on the configured timing mode.
      */
      public void applyKnockback(ServerLevel serverLevel) {
          if (knockbackTiming == ModConfig.Timing.START) {
              applyAllKnockback(1.0F);
          } else if (knockbackTiming == ModConfig.Timing.START_END) {
              applyAllKnockback(0.5F);
          }
          // SPREAD: accumulation happens during block destruction
          // END: handled in finalizeKnockback()
      }

      /**
       * Finalizes knockback application (END timing).
       */
      public void finalizeKnockback(ServerLevel serverLevel) {
          if (knockbackTiming == ModConfig.Timing.END) {
              applyAllKnockback(1.0F);
          } else if (knockbackTiming == ModConfig.Timing.START_END) {
              applyAllKnockback(0.5F);
          } else if (knockbackTiming == ModConfig.Timing.SPREAD) {
              if (!accumulatedKnockback.isEmpty()) {
                  applySpreadKnockback(serverLevel);
              }
          }
      }
 
      /**
       * Applies knockback to all affected entities.
       */
      private void applyAllKnockback(float spread) {
          for (EntityInfo entityInfo : affectedEntities) {
              Entity entity = entityInfo.getEntity();
          
              if (entity.isAlive() && !knockedBackEntities.contains(entity)) {
                  Vec3 knockbackVector = entityInfo.getKnockbackVector();
                  float impactFactor = entityInfo.getImpactFactor();
          
                  if (Math.abs(spread - 1.0F) > 1e-6F) {
                      impactFactor *= spread;
                  }
          
                  double knockbackFactor;
                  if (entity instanceof LivingEntity livingEntity) {
                      knockbackFactor = ProtectionEnchantment.getExplosionKnockbackAfterDampener(livingEntity, impactFactor);
                  } else {
                      knockbackFactor = impactFactor;
                  }
          
                  Vec3 deltaMovement = knockbackVector.scale(knockbackFactor);
                  entity.setDeltaMovement(entity.getDeltaMovement().add(deltaMovement));
                  // Only mark as knocked back for full applications (1.0); partial applications (0.5 or SPREAD)
                  // should not mark entities so they can receive subsequent applications
                  if (Math.abs(spread - 1.0F) <= 1e-6F) {
                      knockedBackEntities.add(entity);
                  }
              }
          }
      }
 
    /**
     * Applies accumulated SPREAD timing effects once per tick.
     * This method is called after each tick's block destruction is complete.
     * It applies the accumulated damage, knockback, sound, and particles, then clears the accumulators.
     */
    private void applySpreadEffects(ServerLevel serverLevel) {
        // Apply SPREAD damage if timing mode is SPREAD
        if (damageTiming == ModConfig.Timing.SPREAD && !accumulatedDamage.isEmpty()) {
            applySpreadDamage(serverLevel);
        }
        
        // Apply SPREAD knockback if timing mode is SPREAD
        if (knockbackTiming == ModConfig.Timing.SPREAD && !accumulatedKnockback.isEmpty()) {
            applySpreadKnockback(serverLevel);
        }
        
        // Apply SPREAD sound if timing mode is SPREAD
        if (soundTiming == ModConfig.Timing.SPREAD && blocksDestroyedThisTick > 0) {
            applySpreadSound();
        }
        
        // Apply SPREAD particles if timing mode is SPREAD
        if (particleTiming == ModConfig.Timing.SPREAD && particlesAccumulatedBlocks > 0) {
            applySpreadParticles();
        }
    }
 
      /**
       * Applies accumulated damage to entities for SPREAD timing.
       * Damage is accumulated per block destroyed and applied once per tick.
       */
      private void applySpreadDamage(ServerLevel serverLevel) {
          DamageSource damageSource = ((IExplosionDuck) originalExplosion).chunked_getDamageSource();
          
          for (Map.Entry<Entity, Float> entry : accumulatedDamage.entrySet()) {
              Entity entity = entry.getKey();
              float accumulated = entry.getValue();
              
              if (entity.isAlive() && !damagedEntities.contains(entity)) {
                  // Reset invulnerability timer to allow continuous damage during SPREAD
                  if (entity instanceof LivingEntity living) {
                      living.invulnerableTime = 0;
                  }
                  entity.hurt(damageSource, accumulated);
                  damagedEntities.add(entity);
              }
          }
          
          // Clear accumulated damage for next tick
          accumulatedDamage.clear();
      }
 
     /**
      * Applies accumulated knockback to entities for SPREAD timing.
      * Knockback is accumulated per block destroyed and applied once per tick.
      */
     private void applySpreadKnockback(ServerLevel serverLevel) {
         for (Map.Entry<Entity, Vec3> entry : accumulatedKnockback.entrySet()) {
             Entity entity = entry.getKey();
             Vec3 accumulated = entry.getValue();
             
             if (entity.isAlive() && !knockedBackEntities.contains(entity)) {
                 entity.setDeltaMovement(entity.getDeltaMovement().add(accumulated));
                 knockedBackEntities.add(entity);
             }
         }
         
         // Clear accumulated knockback for next tick
         accumulatedKnockback.clear();
     }
 
     /**
      * Accumulates damage and knockback for SPREAD timing.
      * Called for each block destroyed when SPREAD timing is enabled.
      * Effects are accumulated and applied once per tick in applySpreadEffects().
      */
     private void accumulateSpreadEffects() {
         // Only accumulate if SPREAD timing is enabled
         if (damageTiming != ModConfig.Timing.SPREAD && knockbackTiming != ModConfig.Timing.SPREAD) {
             return;
         }
         
         int totalBlocks = blocksToDestroy.size();
         if (totalBlocks == 0) {
             return;
         }
         
         // Calculate the proportion of damage/knockback for this single block
         // Each block contributes 1/totalBlocks of the total effect
         float damagePerBlock = 1.0F / totalBlocks;
         float knockbackPerBlock = 1.0F / totalBlocks;
         
         // Accumulate damage for each entity
         if (damageTiming == ModConfig.Timing.SPREAD) {
             for (EntityInfo entityInfo : affectedEntities) {
                 Entity entity = entityInfo.getEntity();
                 float entityDamage = entityInfo.getDamage() * damagePerBlock;
                 
                 // Add to accumulated damage (or create new entry)
                 accumulatedDamage.merge(entity, entityDamage, Float::sum);
             }
         }
         
         // Accumulate knockback for each entity
         if (knockbackTiming == ModConfig.Timing.SPREAD) {
             for (EntityInfo entityInfo : affectedEntities) {
                 Entity entity = entityInfo.getEntity();
                 Vec3 knockbackVector = entityInfo.getKnockbackVector();
                 float impactFactor = entityInfo.getImpactFactor();
                 
                 // Apply protection enchantment dampening for knockback calculation
                 double knockbackFactor;
                 if (entity instanceof LivingEntity livingEntity) {
                     knockbackFactor = ProtectionEnchantment.getExplosionKnockbackAfterDampener(livingEntity, impactFactor * knockbackPerBlock);
                 } else {
                     knockbackFactor = impactFactor * knockbackPerBlock;
                 }
                 
                 Vec3 accumulatedVector = knockbackVector.scale(knockbackFactor);
                 
                 // Add to accumulated knockback (or create new entry)
                 accumulatedKnockback.merge(entity, accumulatedVector, (v1, v2) -> v1.add(v2));
             }
         }
     }
 
     // === Sound and Particle Methods ===

    /**
     * Plays the explosion sound based on the configured timing mode.
     */
    public void playSound() {
        if (soundPlayed) {
            return;
        }

        if (soundTiming == ModConfig.Timing.START) {
            playSoundInternal(1.0F);
        } else if (soundTiming == ModConfig.Timing.START_END) {
            if (!ModConfig.getSoundVolumeSplit()) {
                playSoundInternal(1.0F);
            } else {
                playSoundInternal(0.5F);
            }
        }
        // SPREAD: handled in applySpreadSound()
        // END: handled in finalizeSound()
    }

    /**
      * Finalizes sound playback (END timing).
      */
     public void finalizeSound() {
         if (soundPlayed) {
             return;
         }

         if (soundTiming == ModConfig.Timing.END) {
             playSoundInternal(1.0F);
         } else if (soundTiming == ModConfig.Timing.START_END) {
             if (!ModConfig.getSoundVolumeSplit()) {
                 playSoundInternal(1.0F);
             } else {
                 playSoundInternal(0.5F);
             }
         } else if (soundTiming == ModConfig.Timing.SPREAD) {
             if (blocksDestroyedThisTick > 0) {
                 applySpreadSound();
             }
         }
     }

    /**
     * Internally plays the explosion sound.
     */
    private void playSoundInternal(float volumeMultiplier) {
        if (soundPlayed) {
            return;
        }

        float volume = 4.0F * volumeMultiplier;
        level.playLocalSound(
                position.x, position.y, position.z,
                SoundEvents.GENERIC_EXPLODE,
                SoundSource.BLOCKS,
                volume,
                (1.0F + (level.random.nextFloat() - level.random.nextFloat()) * 0.2F) * 0.7F,
                false
        );
        soundPlayed = true;
    }

    /**
     * Applies accumulated sound for SPREAD timing.
     * Sound is accumulated based on blocks destroyed this tick and played once per tick.
     */
    private void applySpreadSound() {
        if (blocksDestroyedThisTick > 0) {
            if (ModConfig.getSoundVolumeSplit()) {
                // Use proportion of blocks destroyed this tick
                int totalBlocks = blocksToDestroy.size();
                if (totalBlocks > 0) {
                    float volumeFraction = (float) blocksDestroyedThisTick / totalBlocks;
                    playSoundInternal(volumeFraction);
                }
            } else {
                // Full volume each tick that destroys blocks
                playSoundInternal(1.0F);
            }
        }
        blocksDestroyedThisTick = 0;
    }

    /**
     * Spawns explosion particles based on the configured timing mode.
     */
    public void spawnParticles() {
        if (particleTiming == ModConfig.Timing.START) {
            spawnParticlesInternal(1.0F);
        } else if (particleTiming == ModConfig.Timing.START_END) {
            // 50% at start with particleSplit handling
            if (ModConfig.getParticleSplit()) {
                spawnParticlesInternal(0.5F);
            } else {
                spawnParticlesInternal(1.0F);
            }
        }
        // SPREAD: accumulation happens during block destruction
        // END: handled in finalizeParticles()
    }

    /**
     * Finalizes particle spawning (END timing).
     */
    public void finalizeParticles() {
        if (particleTiming == ModConfig.Timing.END) {
            spawnParticlesInternal(1.0F);
        } else if (particleTiming == ModConfig.Timing.START_END) {
            // 50% at end with particleSplit handling
            if (ModConfig.getParticleSplit()) {
                spawnParticlesInternal(0.5F);
            } else {
                spawnParticlesInternal(1.0F);
            }
        } else if (particleTiming == ModConfig.Timing.SPREAD) {
            if (particlesAccumulatedBlocks > 0) {
                applySpreadParticles();
            }
            // Spawn any remaining fractional particles at the end
            if (particleFractionAccumulator >= 1.0F) {
                spawnParticlesInternal((int) particleFractionAccumulator);
            }
            particlesSpawned = true;
        }
    }

    /**
     * Internally spawns explosion particles with a multiplier.
     * @param multiplier Volume/particle multiplier (0.5F for half, 1.0F for full)
     */
    private void spawnParticlesInternal(float multiplier) {
        if (particlesSpawned) {
            return;
        }

        int baseCount = (radius < 2.0F || !interactsWithBlocks()) ? 1 : 4;
        int count;
        if (multiplier < 1.0F) {
            // For fractional spawns, calculate based on explosion size
            count = Math.max(1, (int) Math.ceil(baseCount * multiplier));
        } else {
            // Full spawn count
            count = baseCount;
        }

        // Spawn calculated particle count
        for (int i = 0; i < count; i++) {
            if (!(radius < 2.0F) && interactsWithBlocks()) {
                level.addParticle(ParticleTypes.EXPLOSION_EMITTER, position.x, position.y, position.z, 1.0, 0.0, 0.0);
            } else {
                level.addParticle(ParticleTypes.EXPLOSION, position.x, position.y, position.z, 1.0, 0.0, 0.0);
            }
        }
        particlesSpawned = true;
    }

    /**
     * Internally spawns explosion particles with a count.
     * @param count The number of particles to spawn (0 or negative means use default behavior)
     * @param markComplete Whether to mark particles as fully spawned (true for non-SPREAD timing)
     */
    private void spawnParticlesInternal(int count, boolean markComplete) {
        if (count <= 0) {
            // Default behavior: spawn single particle
            if (!(radius < 2.0F) && interactsWithBlocks()) {
                level.addParticle(ParticleTypes.EXPLOSION_EMITTER, position.x, position.y, position.z, 1.0, 0.0, 0.0);
            } else {
                level.addParticle(ParticleTypes.EXPLOSION, position.x, position.y, position.z, 1.0, 0.0, 0.0);
            }
        } else {
            // Spawn multiple particles for SPREAD timing
            for (int i = 0; i < count; i++) {
                if (!(radius < 2.0F) && interactsWithBlocks()) {
                    level.addParticle(ParticleTypes.EXPLOSION_EMITTER, position.x, position.y, position.z, 1.0, 0.0, 0.0);
                } else {
                    level.addParticle(ParticleTypes.EXPLOSION, position.x, position.y, position.z, 1.0, 0.0, 0.0);
                }
            }
        }
        if (markComplete) {
            particlesSpawned = true;
        }
    }

    /**
     * Accumulates particles for SPREAD timing.
     * Called for each block destroyed when SPREAD timing is enabled.
     * Particles are accumulated and applied once per tick in applySpreadParticles().
     */
    private void accumulateParticleEffects() {
        // Only accumulate if SPREAD timing is enabled
        if (particleTiming != ModConfig.Timing.SPREAD) {
            return;
        }

        particlesAccumulatedBlocks++;
    }

    /**
     * Applies accumulated particles for SPREAD timing.
     * Particles are accumulated per block destroyed and applied once per tick.
     * Uses fractional accumulator to prevent particle count inflation over many small ticks.
     */
    private void applySpreadParticles() {
        if (particlesAccumulatedBlocks > 0) {
            int totalBlocks = blocksToDestroy.size();
            if (totalBlocks > 0) {
                // Calculate how many particles to spawn based on blocks destroyed this tick
                int baseCount = (radius < 2.0F || !interactsWithBlocks()) ? 1 : 4;
                
                if (ModConfig.getParticleSplit()) {
                    // Split: accumulate fractional particles and spawn when >= 1
                    float fraction = (float) particlesAccumulatedBlocks / totalBlocks * baseCount;
                    particleFractionAccumulator += fraction;
                    
                    // Spawn particles while accumulator >= 1.0
                    while (particleFractionAccumulator >= 1.0F) {
                        int particlesToSpawn = (int) particleFractionAccumulator;
                        particleFractionAccumulator -= particlesToSpawn;
                        spawnParticlesInternal(particlesToSpawn);
                    }
                } else {
                    // Full: spawn full count each tick without marking particles as spawned
                    spawnParticlesInternal(baseCount, false);
                }
            }
            particlesAccumulatedBlocks = 0;
        }
    }

    // === Getters ===

    public Explosion getOriginalExplosion() {
        return originalExplosion;
    }

    public Level getLevel() {
        return level;
    }

    public Entity getSource() {
        return source;
    }

    public Vec3 getPosition() {
        return position;
    }

    public float getRadius() {
        return radius;
    }

    public boolean isFire() {
        return fire;
    }

    public Explosion.BlockInteraction getBlockInteraction() {
        return blockInteraction;
    }

    public Set<BlockPos> getBlocksToDestroy() {
        return blocksToDestroy;
    }

    public List<EntityInfo> getAffectedEntities() {
        return affectedEntities;
    }

    public boolean isPreCalculationComplete() {
        return preCalculationComplete;
    }

    public int getBlocksDestroyed() {
        return blocksDestroyed;
    }

    public int getRemainingBlocks() {
        return blocksToDestroy.size() - blocksDestroyed;
    }

    public boolean isEffectsComplete() {
        return effectsComplete;
    }

    public boolean isSoundPlayed() {
        return soundPlayed;
    }

    public boolean areParticlesSpawned() {
        return particlesSpawned;
    }

    public boolean isComplete() {
        return effectsComplete && currentBlockIndex >= blocksToDestroy.size();
    }

    public boolean interactsWithBlocks() {
        return blockInteraction != Explosion.BlockInteraction.KEEP;
    }

    public LivingEntity getIndirectSourceEntity() {
        return originalExplosion.getIndirectSourceEntity();
    }

    /**
     * Updates configuration values from ModConfig.
     * Call this at the start of each tick to get latest config.
     */
    public void updateConfig() {
        this.damageTiming = ModConfig.getDamageTiming();
        this.knockbackTiming = ModConfig.getKnockbackTiming();
        this.soundTiming = ModConfig.getSoundTiming();
        this.particleTiming = ModConfig.getParticleTiming();
        this.blocksPerExplosionTick = ModConfig.getBlocksPerExplosionTick();
    }

    @Override
    public String toString() {
        return "ExplosionState{position=" + position + 
               ", radius=" + radius + 
               ", blocksToDestroy=" + blocksToDestroy.size() +
               ", blocksDestroyed=" + blocksDestroyed +
               ", preCalculationComplete=" + preCalculationComplete +
               ", effectsComplete=" + effectsComplete +
               "}";
    }
}
