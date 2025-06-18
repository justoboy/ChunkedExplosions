package com.github.justoboy.chunkedexplosions.mixin.world.level;

import com.github.justoboy.chunkedexplosions.core.ModConfig;
import com.github.justoboy.chunkedexplosions.iduck.world.level.IExplosionDuck;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.Util;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

    @Shadow public abstract void clearToBlow();
    @Shadow public abstract boolean interactsWithBlocks();
    @Shadow public abstract LivingEntity getIndirectSourceEntity();
    @Final @Shadow private Explosion.BlockInteraction blockInteraction;
    @Final @Shadow private boolean fire;
    @Final @Shadow private RandomSource random;
    @Shadow private static void addBlockDrops(ObjectArrayList<Pair<ItemStack, BlockPos>> droppedItems, ItemStack itemStackToAdd, BlockPos blockPos) {}

    @Unique private ObjectArrayList<BlockPos> chunkedexplosions$toFinalize = new ObjectArrayList<>();
    @Unique private int chunkedexplosions$xIndex = 0;
    @Unique private int chunkedexplosions$yIndex = 0;
    @Unique private int chunkedexplosions$zIndex = 0;
    @Unique private float chunkedexplosions$randomFactor = 0.0F;
    @Unique private double chunkedexplosions$currentX = this.x;
    @Unique private double chunkedexplosions$currentY = this.y;
    @Unique private double chunkedexplosions$currentZ = this.z;


    @Unique
    private Explosion chunkedexplosions$self() {
        return (Explosion) (Object) this;
    }

    @Override
    public void chunked_explode() {
        int blocksPerPass = ModConfig.getBlocksPerExplosionTick(); // Number of blocks to destroy per function pass
        int blocksPassed = 0;
        this.clearToBlow();
        // Trigger a game event for explosion at the specified position
        this.level.gameEvent(this.source, GameEvent.EXPLODE, new Vec3(this.x, this.y, this.z));

        // Set to store block positions that will be destroyed
        Set<BlockPos> blocksToDestroy = Sets.newHashSet();

        // Define the size of the explosion grid (16x16x16)
        int gridSize = 16;
        // Iterate over each point in the 3D grid
        while (chunkedexplosions$xIndex < gridSize && (blocksPassed < blocksPerPass || blocksPerPass == 0)) {
            while (chunkedexplosions$yIndex < gridSize && (blocksPassed < blocksPerPass || blocksPerPass == 0)) {
                while (chunkedexplosions$zIndex < gridSize && (blocksPassed < blocksPerPass || blocksPerPass == 0)) {
                    if (chunkedexplosions$xIndex == 0 || chunkedexplosions$xIndex == gridSize - 1 || chunkedexplosions$yIndex == 0 || chunkedexplosions$yIndex == gridSize - 1 || chunkedexplosions$zIndex == 0 || chunkedexplosions$zIndex == gridSize - 1) {
                        // Calculate normalized coordinates within the grid
                        double normalizedX = ((float) chunkedexplosions$xIndex / gridSize * 2.0F) - 1.0F;
                        double normalizedY = ((float) chunkedexplosions$yIndex / gridSize * 2.0F) - 1.0F;
                        double normalizedZ = ((float) chunkedexplosions$zIndex / gridSize * 2.0F) - 1.0F;

                        // Calculate the distance from the center of the grid
                        double distanceFromCenter = Math.sqrt(normalizedX * normalizedX + normalizedY * normalizedY + normalizedZ * normalizedZ);

                        // Normalize the coordinates to unit length
                        normalizedX /= distanceFromCenter;
                        normalizedY /= distanceFromCenter;
                        normalizedZ /= distanceFromCenter;


                        // Initialize position and randomFactor if first pass of current zIndex
                        if (chunkedexplosions$randomFactor <= 0.0F) {
                            chunkedexplosions$randomFactor = this.radius * (0.7F + this.level.random.nextFloat() * 00.6F);
                            chunkedexplosions$currentX = this.x;
                            chunkedexplosions$currentY = this.y;
                            chunkedexplosions$currentZ = this.z;
                        }

//                        chunkedexplosions$LOGGER.info("Processing explosion tick ({}, {}, {})", normalizedX, normalizedY, normalizedZ);
                        // Iterate through the explosion radius
                        for (float stepSize = 0.3F; chunkedexplosions$randomFactor > 0.0F && (blocksPassed < blocksPerPass || blocksPerPass == 0); chunkedexplosions$randomFactor -= 0.225F) {
                            BlockPos blockPos = BlockPos.containing(chunkedexplosions$currentX, chunkedexplosions$currentY, chunkedexplosions$currentZ);
                            BlockState blockState = this.level.getBlockState(blockPos);
                            FluidState fluidState = this.level.getFluidState(blockPos);

                            // Check if the position is within world bounds
                            if (!this.level.isInWorldBounds(blockPos)) {
                                break;
                            }

                            // Calculate block resistance and check if it should be destroyed
                            Optional<Float> blockResistance = this.damageCalculator.getBlockExplosionResistance(chunkedexplosions$self(), this.level, blockPos, blockState, fluidState);
                            blockResistance.ifPresent(aFloat -> chunkedexplosions$randomFactor -= (aFloat + 0.3F) * 0.3F);

                            // Add the block to the set of blocks to destroy if it should be exploded
                            if (chunkedexplosions$randomFactor > 0.0F && this.damageCalculator.shouldBlockExplode(chunkedexplosions$self(), this.level, blockPos, blockState, chunkedexplosions$randomFactor)) {
                                blocksToDestroy.add(blockPos);
                                blocksPassed++;
                            }

                            // Move to the next position in the grid
                            chunkedexplosions$currentX += normalizedX * 0.3D;
                            chunkedexplosions$currentY += normalizedY * 0.3D;
                            chunkedexplosions$currentZ += normalizedZ * 0.3D;
                        }
                    }
                    if (chunkedexplosions$randomFactor <= 0.0F) {
                        chunkedexplosions$zIndex++;
                    }
                }
                if (chunkedexplosions$zIndex >= gridSize) {
                    chunkedexplosions$zIndex = 0;
                    chunkedexplosions$yIndex++;
                }
            }
            if (chunkedexplosions$yIndex >= gridSize) {
                chunkedexplosions$yIndex = 0;
                chunkedexplosions$xIndex++;
            }
        }

        // Add all blocks marked for destruction to the explosion's list of affected blocks
        this.toBlow.addAll(blocksToDestroy);
        this.chunkedexplosions$toFinalize.addAll(blocksToDestroy);

        // Calculate the effective radius for entity damage and knockback
        float effectiveRadius = this.radius * 2.0F;

        // Define the bounding box for entities within the explosion range
        int minX = Mth.floor(this.x - (double) effectiveRadius - 1.0D);
        int maxX = Mth.floor(this.x + (double) effectiveRadius + 1.0D);
        int minY = Mth.floor(this.y - (double) effectiveRadius - 1.0D);
        int maxY = Mth.floor(this.y + (double) effectiveRadius + 1.0D);
        int minZ = Mth.floor(this.z - (double) effectiveRadius - 1.0D);
        int maxZ = Mth.floor(this.z + (double) effectiveRadius + 1.0D);

        // Get all entities within the bounding box
        List<Entity> affectedEntities = this.level.getEntities(this.source, new AABB(minX, minY, minZ, maxX, maxY, maxZ));

        // Trigger a Forge event to allow mods to modify the list of affected entities
        ForgeEventFactory.onExplosionDetonate(this.level, chunkedexplosions$self(), affectedEntities, effectiveRadius);

        // Vector representing the center of the explosion
        Vec3 explosionCenter = new Vec3(this.x, this.y, this.z);

        // Iterate over each entity within the bounding box
        for (Entity entity : affectedEntities) {
            if (!entity.ignoreExplosion()) {
                double distanceToEntity = Math.sqrt(entity.distanceToSqr(explosionCenter)) / (double) effectiveRadius;
                if (distanceToEntity <= 1.0D) {
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
                        double effectiveDamage = (1.0D - distanceToEntity) * visibilityFactor;
                        float finalDamage = (float) ((int) ((effectiveDamage * effectiveDamage + effectiveDamage) / 2.0D * 7.0D * (double) this.radius + 1.0D));
                        entity.hurt(this.damageSource, finalDamage);

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

    @Override
    public void chunked_finalize() {
        // Check if the explosion interacts with blocks
        boolean interactsWithBlocks = this.interactsWithBlocks();
        // Play sound and add particles on the client side if required
        if (this.level.isClientSide) {
            this.level.playLocalSound(this.x, this.y, this.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0F,
                    (1.0F + (this.level.random.nextFloat() - this.level.random.nextFloat()) * 0.2F) * 0.7F, false);
            if (!(this.radius < 2.0F) && interactsWithBlocks) {
                this.level.addParticle(ParticleTypes.EXPLOSION_EMITTER, this.x, this.y, this.z, 1.0D, 0.0D, 0.0D);
            } else {
                this.level.addParticle(ParticleTypes.EXPLOSION, this.x, this.y, this.z, 1.0D, 0.0D, 0.0D);
            }
        }

        // Process block destruction and item drops if the explosion interacts with blocks
        if (interactsWithBlocks) {
            ObjectArrayList<Pair<ItemStack, BlockPos>> droppedItems = new ObjectArrayList<>();
            boolean isIndirectSourcePlayer = this.getIndirectSourceEntity() instanceof Player;

            // Shuffle the list of blocks to blow up for randomness
            Util.shuffle(this.chunkedexplosions$toFinalize, this.level.random);

            // Iterate over each block position marked for destruction
            for (BlockPos blockPos : this.chunkedexplosions$toFinalize) {
                BlockState blockState = this.level.getBlockState(blockPos);
                Block block = blockState.getBlock();

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
                            blockState.getDrops(lootContextBuilder).forEach((itemStack) -> addBlockDrops(droppedItems, itemStack, immutableBlockPos));
                        }
                    }

                    // Destroy the block by calling onBlockExploded
                    blockState.onBlockExploded(this.level, blockPos, this.chunkedexplosions$self());

                    this.level.getProfiler().pop();
                }
            }

            // Spawn collected items in the world
            for (Pair<ItemStack, BlockPos> itemPair : droppedItems) {
                Block.popResource(this.level, itemPair.getSecond(), itemPair.getFirst());
            }
        }

        // Set fire to nearby air blocks if the explosion should set fire
        if (this.fire) {
            for (BlockPos blockPos : this.chunkedexplosions$toFinalize) {
                if (this.random.nextInt(3) == 0 && this.level.getBlockState(blockPos).isAir() &&
                        this.level.getBlockState(blockPos.below()).isSolidRender(this.level, blockPos.below())) {
                    this.level.setBlockAndUpdate(blockPos, BaseFireBlock.getState(this.level, blockPos));
                }
            }
        }
    }
}
