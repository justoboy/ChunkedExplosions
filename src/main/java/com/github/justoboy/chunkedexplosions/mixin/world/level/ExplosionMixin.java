package com.github.justoboy.chunkedexplosions.mixin.world.level;

import com.github.justoboy.chunkedexplosions.core.ModConfig;
import com.github.justoboy.chunkedexplosions.iduck.world.level.IExplosionDuck;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ProtectionEnchantment;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.*;

import java.util.*;

import static net.minecraft.world.level.Explosion.getSeenPercent;

@Mixin(Explosion.class)
public abstract class ExplosionMixin implements IExplosionDuck {

    @Unique private static final Logger chunkedexplosions$LOGGER = LogUtils.getLogger();

    @Final @Shadow private Level level;
    @Final @Shadow private Entity source;
    @Final @Shadow private double x;
    @Final @Shadow private double y;
    @Final @Shadow private double z;
    @Final @Shadow private float radius;
    @Final @Shadow private ExplosionDamageCalculator damageCalculator;
    @Final @Shadow private ObjectArrayList<BlockPos> toBlow = new ObjectArrayList<>();
    @Shadow @Final private DamageSource damageSource;
    @Shadow @Final private Map<Player, Vec3> hitPlayers;

    protected ExplosionMixin() {
        chunkedexplosions$gridNormalizationFactor = 2.0F / chunkedexplosions$gridSize;
    }

    @Shadow public abstract void clearToBlow();
    @Shadow public abstract boolean interactsWithBlocks();
    @Shadow public abstract LivingEntity getIndirectSourceEntity();
    @Final @Shadow private Explosion.BlockInteraction blockInteraction;
    @Final @Shadow private boolean fire;
    @Final @Shadow private RandomSource random;
    @Shadow private static void addBlockDrops(ObjectArrayList<Pair<ItemStack, BlockPos>> droppedItems, ItemStack itemStackToAdd, BlockPos blockPos) {}

    @Unique private boolean chunkedexplosions$initialized = false;
    @Unique private int chunkedexplosions$lastUpdatedIndex = 0;
    // Define the size of the explosion grid starting with 0 (15 = 16x16x16)
    @Unique private final int chunkedexplosions$gridSize = 15;
    @Unique private final float chunkedexplosions$gridNormalizationFactor;
    @Unique private int chunkedexplosions$xIndex = 0;
    @Unique private int chunkedexplosions$yIndex = 0;
    @Unique private int chunkedexplosions$zIndex = 0;
    @Unique private float chunkedexplosions$randomFactor = 0.0F;
    @Unique private double chunkedexplosions$currentX = this.x;
    @Unique private double chunkedexplosions$currentY = this.y;
    @Unique private double chunkedexplosions$currentZ = this.z;
    @Unique private final List<Entity> chunkedexplosions$damagedEntities = Lists.newArrayList();
    @Unique private final List<Entity> chunkedexplosions$knockedBackEntities = Lists.newArrayList();
    @Unique private final ObjectArrayList<Pair<ItemStack, BlockPos>> chunkedexplosions$droppedItems = new ObjectArrayList<>();
    @Unique private final Map<BlockPos, Map<String, Object>> chunkedexplosions$processedBlocks = new HashMap<>();
//    @Unique private int chunkedexplosions$passes = 0;


    @Unique
    private Explosion chunkedexplosions$self() {
        return (Explosion) (Object) this;
    }

    @Unique
    private void chunkedexplosions$damageEntities(double spread) {
        // Calculate the effective radius for entity damage
        float effectiveRadius = this.radius * 2.0F;

        // Define the bounding box for entities within the explosion range
        int minX = Mth.floor(this.x - (double) effectiveRadius - 1D);
        int maxX = Mth.floor(this.x + (double) effectiveRadius + 1D);
        int minY = Mth.floor(this.y - (double) effectiveRadius - 1D);
        int maxY = Mth.floor(this.y + (double) effectiveRadius + 1D);
        int minZ = Mth.floor(this.z - (double) effectiveRadius - 1D);
        int maxZ = Mth.floor(this.z + (double) effectiveRadius + 1D);

        // Get all entities within the bounding box
        List<Entity> affectedEntities = this.level.getEntities(this.source, new AABB(minX, minY, minZ, maxX, maxY, maxZ));

        // Trigger a Forge event to allow mods to modify the list of affected entities
        ForgeEventFactory.onExplosionDetonate(this.level, chunkedexplosions$self(), affectedEntities, effectiveRadius);

        // Vector representing the center of the explosion
        Vec3 explosionCenter = new Vec3(this.x, this.y, this.z);

        // Iterate over each entity within the bounding box
        for (Entity entity : affectedEntities) {
            boolean canSingleDamage = ModConfig.getDamageMethod() == ModConfig.Method.ONCE && !chunkedexplosions$damagedEntities.contains(entity);
            boolean isSpread = ModConfig.getDamageMethod() == ModConfig.Method.SPREAD;
            if (canSingleDamage || isSpread) {
                if (!entity.ignoreExplosion()) {
                    double distanceToEntity = Math.sqrt(entity.distanceToSqr(explosionCenter)) / (double) effectiveRadius;
                    if (distanceToEntity <= 1D) {
                        // Calculate relative position of the entity to the explosion center
                        double deltaX = entity.getX() - this.x;
                        double deltaY = (entity instanceof PrimedTnt ? entity.getY() : entity.getEyeY()) - this.y;
                        double deltaZ = entity.getZ() - this.z;

                        // Normalize the distance vector
                        double distanceFromCenter = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
                        if (distanceFromCenter != 0.0D) {
                            // Calculate the percentage of the entity being seen by the explosion
                            double visibilityFactor = getSeenPercent(explosionCenter, entity);

                            // Calculate the effective damage to apply to the entity
                            double effectiveDamage = (1D - distanceToEntity) * visibilityFactor;
                            float finalDamage = (float) ((int) ((effectiveDamage * effectiveDamage + effectiveDamage) / 2.0D * 7.0D * (double) this.radius + 1D));
                            if (canSingleDamage) {
                                chunkedexplosions$damagedEntities.add(entity);
                            } else {
                                finalDamage *= (float) spread;
                            }
                            entity.hurt(this.damageSource, finalDamage);
                        }
                    }
                }
            }
        }
    }

    @Unique
    private void chunkedexplosions$playSound(double spread) {
        // Play sound
        float volume = 4.0F;
        if (ModConfig.getSoundVolumeSplit()) {
            volume *= (float) spread;
        }
        this.level.playLocalSound(this.x, this.y, this.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, volume,
                (1.0F + (this.level.random.nextFloat() - this.level.random.nextFloat()) * 0.2F) * 0.7F, false);
    }

    @Unique
    private void chunkedexplosions$emitParticles() {
        // Check if the explosion interacts with blocks
        boolean interactsWithBlocks = this.interactsWithBlocks();
        // Add particles
        if (!(this.radius < 2.0F) && interactsWithBlocks) {
            this.level.addParticle(ParticleTypes.EXPLOSION_EMITTER, this.x, this.y, this.z, 1D, 0.0D, 0.0D);
        } else {
            this.level.addParticle(ParticleTypes.EXPLOSION, this.x, this.y, this.z, 1D, 0.0D, 0.0D);
        }
    }

    @Unique
    private void chunkedexplosions$knockBackEntities(double spread) {
        // Calculate the effective radius for entity knockback
        float effectiveRadius = this.radius * 2.0F;

        // Define the bounding box for entities within the explosion range
        int minX = Mth.floor(this.x - (double) effectiveRadius - 1D);
        int maxX = Mth.floor(this.x + (double) effectiveRadius + 1D);
        int minY = Mth.floor(this.y - (double) effectiveRadius - 1D);
        int maxY = Mth.floor(this.y + (double) effectiveRadius + 1D);
        int minZ = Mth.floor(this.z - (double) effectiveRadius - 1D);
        int maxZ = Mth.floor(this.z + (double) effectiveRadius + 1D);

        // Get all entities within the bounding box
        List<Entity> affectedEntities = this.level.getEntities(this.source, new AABB(minX, minY, minZ, maxX, maxY, maxZ));

        // Trigger a Forge event to allow mods to modify the list of affected entities
        ForgeEventFactory.onExplosionDetonate(this.level, chunkedexplosions$self(), affectedEntities, effectiveRadius);

        // Vector representing the center of the explosion
        Vec3 explosionCenter = new Vec3(this.x, this.y, this.z);

        // Iterate over each entity within the bounding box
        for (Entity entity : affectedEntities) {
            boolean canSingleKnockBack = ModConfig.getKnockbackMethod() == ModConfig.Method.ONCE && !chunkedexplosions$knockedBackEntities.contains(entity);
            boolean isSpread = ModConfig.getKnockbackMethod() == ModConfig.Method.SPREAD;
            if (canSingleKnockBack || isSpread) {
                if (!entity.ignoreExplosion()) {
                    double distanceToEntity = Math.sqrt(entity.distanceToSqr(explosionCenter)) / (double) effectiveRadius;
                    if (distanceToEntity <= 1D) {
                        // Calculate relative position of the entity to the explosion center
                        double deltaX = entity.getX() - this.x;
                        double deltaY = (entity instanceof PrimedTnt ? entity.getY() : entity.getEyeY()) - this.y;
                        double deltaZ = entity.getZ() - this.z;

                        // Normalize the distance vector
                        double distanceFromCenter = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
                        if (distanceFromCenter != 0.0D) {
                            deltaX /= distanceFromCenter;
                            deltaY /= distanceFromCenter;
                            deltaZ /= distanceFromCenter;

                            // Calculate the percentage of the entity being seen by the explosion
                            double visibilityFactor = getSeenPercent(explosionCenter, entity);

                            // Calculate the effective damage to apply to the entity
                            double effectiveDamage = (1D - distanceToEntity) * visibilityFactor;

                            if (canSingleKnockBack) {
                                chunkedexplosions$knockedBackEntities.add(entity);
                            } else {
                                effectiveDamage *= spread;
                            }

                            // Calculate knockback for the entity
                            double knockbackFactor;
                            if (entity instanceof LivingEntity livingEntity) {
                                knockbackFactor = ProtectionEnchantment.getExplosionKnockbackAfterDampener(livingEntity, effectiveDamage);
                            } else {
                                knockbackFactor = effectiveDamage;
                            }

                            // Apply knockback to the entity
                            deltaX *= knockbackFactor;
                            deltaY *= knockbackFactor;
                            deltaZ *= knockbackFactor;

                            Vec3 knockbackVector = new Vec3(deltaX, deltaY, deltaZ);
                            entity.setDeltaMovement(entity.getDeltaMovement().add(knockbackVector));

                            // Handle player-specific logic for knockback
                            if (entity instanceof Player player) {
                                if (!player.isSpectator() && (!player.isCreative() || !player.getAbilities().flying)) {
                                    this.hitPlayers.put(player, knockbackVector);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Unique
    public void chunked_initialize() {
        // Initialize entity damage
        if (ModConfig.getDamageTiming() == ModConfig.Timing.START) {
            chunkedexplosions$damageEntities(1D);
        } else if (ModConfig.getDamageTiming() == ModConfig.Timing.START_END) {
            if (ModConfig.getDamageMethod() == ModConfig.Method.ONCE) {
                chunkedexplosions$damageEntities(1D);
            } else {
                chunkedexplosions$damageEntities(0.5D);
            }
        }
        // Initialize sounds
        if (ModConfig.getSoundTiming() == ModConfig.Timing.START) {
            chunkedexplosions$playSound(1D);
        } else if (ModConfig.getSoundTiming() == ModConfig.Timing.START_END) {
            if (!ModConfig.getSoundVolumeSplit()) {
                chunkedexplosions$playSound(1D);
            } else {
                chunkedexplosions$playSound(0.5D);
            }
        }
        // Initialize particles
        if (ModConfig.getParticleTiming() == ModConfig.Timing.START || ModConfig.getParticleTiming() == ModConfig.Timing.START_END) {
            chunkedexplosions$emitParticles();
        }
        // Initialize entity knockback
        if (ModConfig.getKnockbackTiming() == ModConfig.Timing.START) {
            chunkedexplosions$knockBackEntities(1D);
        } else if (ModConfig.getKnockbackTiming() == ModConfig.Timing.START_END) {
            if (ModConfig.getKnockbackMethod() == ModConfig.Method.ONCE) {
                chunkedexplosions$knockBackEntities(1D);
            } else {
                chunkedexplosions$knockBackEntities(0.5D);
            }
        }
    }

    @Override
    public void chunked_explode() {
        if (!chunkedexplosions$initialized) {
            chunkedexplosions$initialized = true;
            chunked_initialize();
        }
        int blocksPerPass = ModConfig.getBlocksPerExplosionTick(); // Number of blocks to destroy per function pass
        final int[] blocksAdded = {0};
        this.clearToBlow();

        // Trigger a game event for explosion at the specified position
        this.level.gameEvent(this.source, GameEvent.EXPLODE, new Vec3(this.x, this.y, this.z));

        // Set to store block positions that will be destroyed
        Set<BlockPos> blocksToDestroy = Sets.newHashSet();

        // Iterate over each point in the 3D grid
        while (chunkedexplosions$xIndex <= chunkedexplosions$gridSize && (blocksAdded[0] < blocksPerPass || blocksPerPass == 0)) {
            while (chunkedexplosions$yIndex <= chunkedexplosions$gridSize && (blocksAdded[0] < blocksPerPass || blocksPerPass == 0)) {
                while (chunkedexplosions$zIndex <= chunkedexplosions$gridSize && (blocksAdded[0] < blocksPerPass || blocksPerPass == 0)) {
                    if (chunkedexplosions$xIndex == 0 || chunkedexplosions$xIndex == chunkedexplosions$gridSize ||
                        chunkedexplosions$yIndex == 0 || chunkedexplosions$yIndex == chunkedexplosions$gridSize ||
                        chunkedexplosions$zIndex == 0 || chunkedexplosions$zIndex == chunkedexplosions$gridSize) {

                        // Calculate normalized coordinates within the grid
                        double normalizedX = chunkedexplosions$xIndex * chunkedexplosions$gridNormalizationFactor - 1.0F;
                        double normalizedY = chunkedexplosions$yIndex * chunkedexplosions$gridNormalizationFactor - 1.0F;
                        double normalizedZ = chunkedexplosions$zIndex * chunkedexplosions$gridNormalizationFactor - 1.0F;

                        // Calculate the distance from the center of the grid
                        double distanceFromCenter = Math.sqrt(normalizedX * normalizedX + normalizedY * normalizedY + normalizedZ * normalizedZ);

                        // Normalize the coordinates to unit length
                        normalizedX /= distanceFromCenter;
                        normalizedY /= distanceFromCenter;
                        normalizedZ /= distanceFromCenter;

                        // Initialize position and randomFactor if first pass of current zIndex
                        if (chunkedexplosions$randomFactor <= 0.0F) {
                            chunkedexplosions$randomFactor = this.radius * (0.7F + this.level.random.nextFloat() * 0.6F);
                            chunkedexplosions$currentX = this.x;
                            chunkedexplosions$currentY = this.y;
                            chunkedexplosions$currentZ = this.z;
//                            chunkedexplosions$LOGGER.info("first pass z: {}, rf: {}", chunkedexplosions$zIndex, chunkedexplosions$randomFactor);
                        }

                        // Iterate through the explosion radius
                        for (float stepSize = 0.3F; chunkedexplosions$randomFactor > 0.0F && (blocksAdded[0] < blocksPerPass || blocksPerPass == 0); chunkedexplosions$randomFactor -= 0.22500001F) {
                            BlockPos blockPos = BlockPos.containing(chunkedexplosions$currentX, chunkedexplosions$currentY, chunkedexplosions$currentZ);

                            // Check if the position is within world bounds
                            if (!this.level.isInWorldBounds(blockPos)) {
                                break;
                            }
                            BlockState blockState;
                            Optional<Float> blockResistance;
                            Optional<Float> testBlockResistance;
                            if (chunkedexplosions$processedBlocks.containsKey(blockPos)) {
                                // Load block state and resistance
                                blockState = (BlockState) chunkedexplosions$processedBlocks.get(blockPos).get("blockState");
                                //noinspection unchecked
                                blockResistance = (Optional<Float>) chunkedexplosions$processedBlocks.get(blockPos).get("blockResistance");
                                testBlockResistance = this.damageCalculator.getBlockExplosionResistance(chunkedexplosions$self(), this.level, blockPos, this.level.getBlockState(blockPos), this.level.getFluidState(blockPos));
                                if (!blockResistance.equals(testBlockResistance)) {
                                    chunkedexplosions$LOGGER.info("Used: {}, calculated: {}", blockResistance, testBlockResistance);
                                }
                            } else {
                                // Get block states
                                blockState = this.level.getBlockState(blockPos);
                                FluidState fluidState = this.level.getFluidState(blockPos);
                                // Calculate block resistance
                                blockResistance = this.damageCalculator.getBlockExplosionResistance(chunkedexplosions$self(), this.level, blockPos, blockState, fluidState);
                                // Store block state and resistance
                                Map<String, Object> blockMap = new HashMap<>();
                                blockMap.put("blockState", blockState);
                                blockMap.put("blockResistance", blockResistance);
                                chunkedexplosions$processedBlocks.put(blockPos, blockMap);
                            }

                            blockResistance.ifPresent(resistance -> {
                                chunkedexplosions$randomFactor -= (resistance + stepSize) * stepSize; // Use stepSize in the calculation
                            });

                            // Check if the block hasn't already been marked to be destroyed
                            if (!blocksToDestroy.contains(blockPos) && !toBlow.contains(blockPos)) {
                                // Check if the block should be destroyed
                                if (chunkedexplosions$randomFactor > 0.0F && this.damageCalculator.shouldBlockExplode(chunkedexplosions$self(), this.level, blockPos, blockState, chunkedexplosions$randomFactor)) {
                                    blocksToDestroy.add(blockPos);
                                    blocksAdded[0]++;
//                                        chunkedexplosions$LOGGER.info("current_pos: {}; rf: {};, index: ({}, {}, {}); blocks: {}/{}; passes: {}",
//                                                blockPos, chunkedexplosions$randomFactor,
//                                                chunkedexplosions$xIndex, chunkedexplosions$yIndex, chunkedexplosions$zIndex,
//                                                blocksAdded[0], blocksPerPass, chunkedexplosions$passes);
                                }
                            }

                            // Move to the next position in the grid
                            chunkedexplosions$currentX += normalizedX * stepSize;
                            chunkedexplosions$currentY += normalizedY * stepSize;
                            chunkedexplosions$currentZ += normalizedZ * stepSize;
//                            chunkedexplosions$passes += 1;
                        }
                    }
                    if (chunkedexplosions$randomFactor <= 0.0F) {
                        chunkedexplosions$zIndex++;
//                        chunkedexplosions$LOGGER.info("next z pass: {}", chunkedexplosions$zIndex);
                    }
                }
                if (chunkedexplosions$zIndex > chunkedexplosions$gridSize) {
                    chunkedexplosions$zIndex = 0;
                    chunkedexplosions$yIndex++;
                }
            }
            if (chunkedexplosions$yIndex > chunkedexplosions$gridSize) {
                chunkedexplosions$yIndex = 0;
                chunkedexplosions$xIndex++;
            }
        }

        // Add all blocks marked for destruction to the explosion's list of affected blocks
        toBlow.addAll(blocksToDestroy);
        // Check if the explosion interacts with blocks
        boolean interactsWithBlocks = this.interactsWithBlocks();

        // Process block destruction and item drops if the explosion interacts with blocks
        if (interactsWithBlocks) {
            boolean isIndirectSourcePlayer = this.getIndirectSourceEntity() instanceof Player;

            // Iterate over each block position marked for destruction
            for (BlockPos blockPos : blocksToDestroy) {
                BlockState blockState = this.level.getBlockState(blockPos);

                // Check if the block is not air
                if (!blockState.isAir()) {
                    BlockPos immutableBlockPos = blockPos.immutable();
                    this.level.getProfiler().push("explosion_blocks");

                    // Check if the block can drop items from the explosion
                    if (blockState.canDropFromExplosion(this.level, blockPos, this.chunkedexplosions$self())) {
                        Level world = this.level;
                        if (world instanceof ServerLevel serverWorld) {

                            // Get the block entity if it exists
                            BlockEntity blockEntity = blockState.hasBlockEntity() ? this.level.getBlockEntity(blockPos) : null;

                            // Create a loot context builder for dropping items
                            LootParams.Builder lootContextBuilder = (new LootParams.Builder(serverWorld))
                                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(blockPos))
                                    .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                                    .withOptionalParameter(LootContextParams.BLOCK_ENTITY, blockEntity)
                                    .withOptionalParameter(LootContextParams.THIS_ENTITY, this.source);

                            // Add explosion radius to the loot context if needed
                            if (this.blockInteraction == Explosion.BlockInteraction.DESTROY_WITH_DECAY) {
                                lootContextBuilder.withParameter(LootContextParams.EXPLOSION_RADIUS, this.radius);
                            }

                            // Spawn items after breaking the block
                            blockState.spawnAfterBreak(serverWorld, blockPos, ItemStack.EMPTY, isIndirectSourcePlayer);

                            // Collect drops from the block using the loot context
                            blockState.getDrops(lootContextBuilder).forEach((itemStack) -> addBlockDrops(chunkedexplosions$droppedItems, itemStack, immutableBlockPos));
                        }
                    }

                    // Destroy the block by calling onBlockExploded
                    blockState.onBlockExploded(this.level, blockPos, this.chunkedexplosions$self());

                    this.level.getProfiler().pop();
                }
            }
        }

        // Set fire to nearby air blocks if the explosion should set fire
        if (this.fire) {
            for (BlockPos blockPos : blocksToDestroy) {
                if (this.random.nextInt(3) == 0 && this.level.getBlockState(blockPos).isAir() &&
                        this.level.getBlockState(blockPos.below()).isSolidRender(this.level, blockPos.below())) {
                    this.level.setBlockAndUpdate(blockPos, BaseFireBlock.getState(this.level, blockPos));
                }
            }
        }
    }

    @Override
    public void chunked_update() {
        int totalIndex = chunkedexplosions$gridSize * chunkedexplosions$gridSize * chunkedexplosions$gridSize;
        int currentIndex = (chunkedexplosions$xIndex * chunkedexplosions$gridSize * chunkedexplosions$gridSize) + (chunkedexplosions$yIndex * chunkedexplosions$gridSize) + chunkedexplosions$zIndex;
        if (currentIndex != chunkedexplosions$lastUpdatedIndex) {
            double updateSpread = (double) (currentIndex - chunkedexplosions$lastUpdatedIndex) / totalIndex;
            // Update entity damage
            if (ModConfig.getDamageTiming() == ModConfig.Timing.SPREAD) {
                if (ModConfig.getDamageMethod() == ModConfig.Method.ONCE) {
                    chunkedexplosions$damageEntities(1D);
                } else {
                    chunkedexplosions$damageEntities(updateSpread);
                }
            }
            // Update sounds
            if (ModConfig.getSoundTiming() == ModConfig.Timing.SPREAD) {
                if (!ModConfig.getSoundVolumeSplit()) {
                    chunkedexplosions$playSound(1D);
                } else {
                    chunkedexplosions$playSound(updateSpread);
                }
            }
            // Update particles
            if (ModConfig.getParticleTiming() == ModConfig.Timing.SPREAD) {
                chunkedexplosions$emitParticles();
            }
            // Update entity knockback
            if (ModConfig.getKnockbackTiming() == ModConfig.Timing.SPREAD) {
                if (ModConfig.getKnockbackMethod() == ModConfig.Method.ONCE) {
                    chunkedexplosions$knockBackEntities(1D);
                } else {
                    chunkedexplosions$knockBackEntities(updateSpread);
                }
            }
            chunkedexplosions$lastUpdatedIndex = currentIndex;
        }
    }

    @Override
    public void chunked_finalize() {
        // Finalize entity damage
        if (ModConfig.getDamageTiming() == ModConfig.Timing.END) {
            chunkedexplosions$damageEntities(1D);
        } else if (ModConfig.getDamageTiming() == ModConfig.Timing.START_END) {
            if (ModConfig.getDamageMethod() == ModConfig.Method.ONCE) {
                chunkedexplosions$damageEntities(1D);
            } else {
                chunkedexplosions$damageEntities(0.5D);
            }
        }
        // Finalize sounds
        if (ModConfig.getSoundTiming() == ModConfig.Timing.END) {
            chunkedexplosions$playSound(1D);
        } else if (ModConfig.getSoundTiming() == ModConfig.Timing.START_END) {
            if (!ModConfig.getSoundVolumeSplit()) {
                chunkedexplosions$playSound(1D);
            } else {
                chunkedexplosions$playSound(0.5D);
            }
        }
        // Finalize particles
        if (ModConfig.getParticleTiming() == ModConfig.Timing.END || ModConfig.getParticleTiming() == ModConfig.Timing.START_END) {
            chunkedexplosions$emitParticles();
        }
        // Finalize entity knockback
        if (ModConfig.getKnockbackTiming() == ModConfig.Timing.END) {
            chunkedexplosions$knockBackEntities(1D);
        } else if (ModConfig.getKnockbackTiming() == ModConfig.Timing.START_END) {
            if (ModConfig.getKnockbackMethod() == ModConfig.Method.ONCE) {
                chunkedexplosions$knockBackEntities(1D);
            } else {
                chunkedexplosions$knockBackEntities(0.5D);
            }
        }
        // Spawn collected items in the world
        for (Pair<ItemStack, BlockPos> itemPair : chunkedexplosions$droppedItems) {
            Block.popResource(this.level, itemPair.getSecond(), itemPair.getFirst());
        }
    }
}
