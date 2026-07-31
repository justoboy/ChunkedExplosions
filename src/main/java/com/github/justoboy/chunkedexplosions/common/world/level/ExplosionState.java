package com.github.justoboy.chunkedexplosions.common.world.level;

import com.github.justoboy.chunkedexplosions.core.ModConfig;
import com.github.justoboy.chunkedexplosions.iduck.world.level.IExplosionDuck;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ProtectionEnchantment;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

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

    /** Pre-calculated entity effects data */
    private List<EntityInfo> affectedEntities;

    /** Whether pre-calculation is complete */
    private boolean preCalculationComplete;

    // === Processing State (updated during tick processing) ===

    /** Index of the next block to destroy in blocksToDestroy */
    private int currentBlockIndex;

    /** Count of blocks destroyed so far */
    private int blocksDestroyed;

    /** Set of entities that have been damaged (for ONCE method) */
    private final Set<Entity> damagedEntities;

    /** Set of entities that have been knocked back (for ONCE method) */
    private final Set<Entity> knockedBackEntities;

    /** Whether all effects (damage/knockback/sound/particles) are complete */
    private boolean effectsComplete;

    /** Whether the sound has been played */
    private boolean soundPlayed;

    /** Whether particles have been spawned */
    private boolean particlesSpawned;

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
        this.originalExplosion = explosion;
        IExplosionDuck duck = (IExplosionDuck) explosion;
        this.level = duck.chunked_getLevel();
        this.source = duck.chunked_getSource();
        this.position = duck.chunked_getPosition();
        this.radius = duck.chunked_getRadius();
        this.fire = duck.chunked_isFire();
        this.blockInteraction = duck.chunked_getBlockInteraction();
        
        this.blocksToDestroy = Sets.newHashSet();
        this.affectedEntities = Lists.newArrayList();
        this.preCalculationComplete = false;
        
        this.currentBlockIndex = 0;
        this.blocksDestroyed = 0;
        this.damagedEntities = Sets.newHashSet();
        this.knockedBackEntities = Sets.newHashSet();
        this.effectsComplete = false;
        this.soundPlayed = false;
        this.particlesSpawned = false;
        
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

        LOGGER.debug("Pre-calculation complete: {} blocks, {} entities", 
                blocksToDestroy.size(), affectedEntities.size());
    }

    /**
     * Performs deterministic ray-casting using a 16x16x16 grid surface rays algorithm.
     * This replaces the vanilla ray-casting which used non-deterministic RandomSource.
     * 
     * @return Set of blocks to destroy
     */
    private Set<BlockPos> performRayCasting() {
        Set<BlockPos> result = Sets.newHashSet();
        int gridSize = 15; // 16x16x16 grid (indices 0-15)
        float gridNormalizationFactor = 2.0f / gridSize;
        
        // Use deterministic seed: source entity ID XOR position coordinates
        long seed = (source != null ? source.getId() : 0)
                     ^ Double.hashCode(position.x)
                     ^ Double.hashCode(position.y)
                     ^ Double.hashCode(position.z)
                     ^ Float.floatToIntBits(radius);
        
        // Use SplittableRandom for deterministic iteration
        SplittableRandom random = new SplittableRandom(seed);

        // Iterate over the surface of the 16x16x16 grid
        for (int xIndex = 0; xIndex <= gridSize; xIndex++) {
            for (int yIndex = 0; yIndex <= gridSize; yIndex++) {
                for (int zIndex = 0; zIndex <= gridSize; zIndex++) {
                    // Only process surface points (at least one coordinate at boundary)
                    if (xIndex == 0 || xIndex == gridSize ||
                        yIndex == 0 || yIndex == gridSize ||
                        zIndex == 0 || zIndex == gridSize) {

                        // Calculate normalized coordinates within the grid
                        double normalizedX = xIndex * gridNormalizationFactor - 1.0;
                        double normalizedY = yIndex * gridNormalizationFactor - 1.0;
                        double normalizedZ = zIndex * gridNormalizationFactor - 1.0;

                        // Normalize to unit length (direction vector)
                        double distanceFromCenter = Math.sqrt(
                                normalizedX * normalizedX + 
                                normalizedY * normalizedY + 
                                normalizedZ * normalizedZ);
                        
                        if (distanceFromCenter == 0) continue;
                        
                        normalizedX /= distanceFromCenter;
                        normalizedY /= distanceFromCenter;
                        normalizedZ /= distanceFromCenter;

                        // Generate deterministic blast strength
                        float blastStrength = radius * (0.7f + random.nextFloat() * 0.6f);
                        
                        // March ray until blast strength exhausted
                        double currentX = position.x;
                        double currentY = position.y;
                        double currentZ = position.z;
                        
                        for (float stepSize = 0.3f; blastStrength > 0.0f; blastStrength -= 0.22500001f) {
                            BlockPos blockPos = BlockPos.containing(currentX, currentY, currentZ);

                            // Check world bounds
                            if (!level.isInWorldBounds(blockPos)) {
                                break;
                            }

                            // Get block state and resistance
                            BlockState blockState = level.getBlockState(blockPos);
                            float resistance = getBlockExplosionResistance(blockState, blockPos);
                            
                            // Reduce blast strength by resistance
                            blastStrength -= (resistance + stepSize) * stepSize;

                            // If blast strength is still positive, add block
                            if (blastStrength >= 0.0f && !blockState.isAir()) {
                                result.add(blockPos);
                            }

                            // Advance ray position
                            currentX += normalizedX * 0.3;
                            currentY += normalizedY * 0.3;
                            currentZ += normalizedZ * 0.3;
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
      * Gets the explosion resistance of a block state.
      */
     private float getBlockExplosionResistance(BlockState blockState, BlockPos blockPos) {
         if (blockInteraction == Explosion.BlockInteraction.KEEP) {
             return Float.MAX_VALUE;
         }
         return blockState.getExplosionResistance(level, blockPos, originalExplosion);
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
            float damage = (float) ((int) ((impactFactor * impactFactor + impactFactor) / 2.0 * 7.0 * radius + 1));

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
        if (!preCalculationComplete) {
            preCalculate();
        }

        if (blocksToDestroy.isEmpty()) {
            effectsComplete = true;
            return true;
        }

        int blocksThisTick = 0;
        int totalBlocks = blocksToDestroy.size();

        // Process up to blocksPerExplosionTick blocks
        while (blocksThisTick < blocksPerExplosionTick && currentBlockIndex < totalBlocks) {
            // Get the next block to destroy
            BlockPos blockPos = getCurrentBlock();
            if (blockPos == null) {
                break;
            }

            // Destroy the block
            destroyBlock(serverLevel, blockPos);
            
            currentBlockIndex++;
            blocksDestroyed++;
            blocksThisTick++;
        }

        // Check if all blocks are destroyed
        if (currentBlockIndex >= totalBlocks) {
            effectsComplete = true;
            return true;
        }

        return false;
    }

    /**
     * Gets the current block to process without advancing the index.
     */
    private BlockPos getCurrentBlock() {
        if (currentBlockIndex >= blocksToDestroy.size()) {
            return null;
        }
        
        // Convert from Set to ordered access
        // Since we're using HashSet, we need to iterate to the index
        int currentIndex = 0;
        for (BlockPos pos : blocksToDestroy) {
            if (currentIndex == currentBlockIndex) {
                return pos;
            }
            currentIndex++;
        }
        return null;
    }

    /**
     * Destroys a single block and handles drops/fire.
     */
    private void destroyBlock(ServerLevel serverLevel, BlockPos blockPos) {
        BlockState blockState = serverLevel.getBlockState(blockPos);
        
        if (blockState.isAir()) {
            return;
        }

        // Get block entity for loot
        BlockEntity blockEntity = blockState.hasBlockEntity() ? serverLevel.getBlockEntity(blockPos) : null;

        // Create loot context
        LootParams.Builder lootContextBuilder = (new LootParams.Builder(serverLevel))
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(blockPos))
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                .withOptionalParameter(LootContextParams.BLOCK_ENTITY, blockEntity)
                .withOptionalParameter(LootContextParams.THIS_ENTITY, source);

        if (blockInteraction == Explosion.BlockInteraction.DESTROY_WITH_DECAY) {
            lootContextBuilder.withParameter(LootContextParams.EXPLOSION_RADIUS, radius);
        }

        // Spawn drops
        blockState.spawnAfterBreak(serverLevel, blockPos, ItemStack.EMPTY, 
                getIndirectSourceEntity() instanceof Player);
        blockState.getDrops(lootContextBuilder).forEach((itemStack) -> 
                Block.popResource(serverLevel, blockPos, itemStack));

        // Set block to air
        serverLevel.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 
                Block.UPDATE_ALL);
    }

    // === Entity Effect Methods ===

    /**
      * Applies damage to entities based on the configured timing mode.
      */
     public void applyDamage(ServerLevel serverLevel) {
         if (damageTiming == ModConfig.Timing.START) {
             applyAllDamage(1.0F);
         } else if (damageTiming == ModConfig.Timing.START_END) {
             if (ModConfig.getDamageMethod() == ModConfig.Method.ONCE) {
                 applyAllDamage(1.0F);
             } else {
                 applyAllDamage(0.5F);
             }
         }
     }

     /**
      * Finalizes damage application (END timing).
      */
     public void finalizeDamage(ServerLevel serverLevel) {
         if (damageTiming == ModConfig.Timing.END) {
             applyAllDamage(1.0F);
         } else if (damageTiming == ModConfig.Timing.START_END) {
             if (ModConfig.getDamageMethod() == ModConfig.Method.ONCE) {
                 applyAllDamage(1.0F);
             } else {
                 applyAllDamage(0.5F);
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
                 if (spread != 1.0F) {
                     damage *= spread;
                 }
              
                 // Use the original explosion's damage source via Duck interface
                 DamageSource damageSource = ((IExplosionDuck) originalExplosion).chunked_getDamageSource();
                 entity.hurt(damageSource, damage);
                 damagedEntities.add(entity);
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
             if (ModConfig.getKnockbackMethod() == ModConfig.Method.ONCE) {
                 applyAllKnockback(1.0F);
             } else {
                 applyAllKnockback(0.5F);
             }
         }
     }

     /**
      * Finalizes knockback application (END timing).
      */
     public void finalizeKnockback(ServerLevel serverLevel) {
         if (knockbackTiming == ModConfig.Timing.END) {
             applyAllKnockback(1.0F);
         } else if (knockbackTiming == ModConfig.Timing.START_END) {
             if (ModConfig.getKnockbackMethod() == ModConfig.Method.ONCE) {
                 applyAllKnockback(1.0F);
             } else {
                 applyAllKnockback(0.5F);
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
            
                 if (spread != 1.0F) {
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
                 knockedBackEntities.add(entity);
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
     * Spawns explosion particles based on the configured timing mode.
     */
    public void spawnParticles() {
        if (particlesSpawned) {
            return;
        }

        if (particleTiming == ModConfig.Timing.START || particleTiming == ModConfig.Timing.START_END) {
            spawnParticlesInternal();
        }
    }

    /**
     * Finalizes particle spawning (END timing).
     */
    public void finalizeParticles() {
        if (particlesSpawned) {
            return;
        }

        if (particleTiming == ModConfig.Timing.END || particleTiming == ModConfig.Timing.START_END) {
            spawnParticlesInternal();
        }
    }

    /**
     * Internally spawns explosion particles.
     */
    private void spawnParticlesInternal() {
        if (particlesSpawned) {
            return;
        }

        if (!(radius < 2.0F) && interactsWithBlocks()) {
            level.addParticle(ParticleTypes.EXPLOSION_EMITTER, position.x, position.y, position.z, 1.0, 0.0, 0.0);
        } else {
            level.addParticle(ParticleTypes.EXPLOSION, position.x, position.y, position.z, 1.0, 0.0, 0.0);
        }
        particlesSpawned = true;
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
