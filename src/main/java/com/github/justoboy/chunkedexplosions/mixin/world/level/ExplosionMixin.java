package com.github.justoboy.chunkedexplosions.mixin.world.level;

import com.github.justoboy.chunkedexplosions.core.ModConfig;
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
import net.minecraft.world.entity.item.ItemEntity;
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
public abstract class ExplosionMixin {

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

    @Unique private ObjectArrayList<BlockPos> chunkedexplosions$toFinalize = new ObjectArrayList<>();
    @Unique private int chunkedexplosions$xIndex = 0;
    @Unique private int chunkedexplosions$yIndex = 0;
    @Unique private int chunkedexplosions$zIndex = 0;

    @Unique
    private Explosion chunkedexplosions$self() {
        return (Explosion) (Object) this;
    }

    @Unique
    private static void chunkedexplosions$addBlockDrops(ObjectArrayList<Pair<ItemStack, BlockPos>> droppedItems, ItemStack itemStackToAdd, BlockPos blockPos) {
        int numberOfExistingItems = droppedItems.size();
        // Iterate over the existing items to check for mergability
        for (int index = 0; index < numberOfExistingItems; ++index) {
            Pair<ItemStack, BlockPos> existingPair = droppedItems.get(index);
            ItemStack existingItemStack = existingPair.getFirst();
            // Check if the item stacks can be merged
            if (ItemEntity.areMergable(existingItemStack, itemStackToAdd)) {
                // Merge the item stacks
                ItemStack mergedItemStack = ItemEntity.merge(existingItemStack, itemStackToAdd, 16);
                droppedItems.set(index, Pair.of(mergedItemStack, existingPair.getSecond()));
                // If the new item stack is empty after merging, return early
                if (itemStackToAdd.isEmpty()) {
                    return;
                }
            }
        }
        // If no merge occurred, add the new item stack to the list
        droppedItems.add(Pair.of(itemStackToAdd, blockPos));
    }

    @Unique
    private void chunked_explode() {
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

                        // Calculate a random factor for explosion damage
                        float randomFactor = this.radius * (0.7F + this.level.random.nextFloat() * 00.6F);

                        // Initialize position variables
                        double currentX = this.x;
                        double currentY = this.y;
                        double currentZ = this.z;

                        // Iterate through the explosion radius
                        for (float stepSize = 0.3F; randomFactor > 0.0F; randomFactor -= 0.225F) {
                            BlockPos blockPos = BlockPos.containing(currentX, currentY, currentZ);
                            BlockState blockState = this.level.getBlockState(blockPos);
                            FluidState fluidState = this.level.getFluidState(blockPos);

                            // Check if the position is within world bounds
                            if (!this.level.isInWorldBounds(blockPos)) {
                                break;
                            }

                            // Calculate block resistance and check if it should be destroyed
                            Optional<Float> blockResistance = this.damageCalculator.getBlockExplosionResistance(chunkedexplosions$self(), this.level, blockPos, blockState, fluidState);
                            if (blockResistance.isPresent()) {
                                randomFactor -= (blockResistance.get() + 0.3F) * 0.3F;
                            }

                            // Add the block to the set of blocks to destroy if it should be exploded
                            if (randomFactor > 0.0F && this.damageCalculator.shouldBlockExplode(chunkedexplosions$self(), this.level, blockPos, blockState, randomFactor)) {
                                blocksToDestroy.add(blockPos);
                                blocksPassed++;
                            }

                            // Move to the next position in the grid
                            currentX += normalizedX * 0.3D;
                            currentY += normalizedY * 0.3D;
                            currentZ += normalizedZ * 0.3D;
                        }
                    }
                    chunkedexplosions$zIndex++;
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

    /**
     * @author justoboy
     * @reason for handling chunked explosions
     */
    @Overwrite
    public void explode() {
        if (ModConfig.isEnable()) {
            this.chunked_explode();
        } else {
            this.level.gameEvent(this.source, GameEvent.EXPLODE, new Vec3(this.x, this.y, this.z));
            Set<BlockPos> set = Sets.newHashSet();
            int i = 16;

            for(int j = 0; j < 16; ++j) {
                for(int k = 0; k < 16; ++k) {
                    for(int l = 0; l < 16; ++l) {
                        if (j == 0 || j == 15 || k == 0 || k == 15 || l == 0 || l == 15) {
                            double d0 = (float)j / 15.0F * 2.0F - 1.0F;
                            double d1 = (float)k / 15.0F * 2.0F - 1.0F;
                            double d2 = (float)l / 15.0F * 2.0F - 1.0F;
                            double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
                            d0 /= d3;
                            d1 /= d3;
                            d2 /= d3;
                            float f = this.radius * (0.7F + this.level.random.nextFloat() * 0.6F);
                            double d4 = this.x;
                            double d6 = this.y;
                            double d8 = this.z;

                            for(float f1 = 0.3F; f > 0.0F; f -= 0.22500001F) {
                                BlockPos blockpos = BlockPos.containing(d4, d6, d8);
                                BlockState blockstate = this.level.getBlockState(blockpos);
                                FluidState fluidstate = this.level.getFluidState(blockpos);
                                if (!this.level.isInWorldBounds(blockpos)) {
                                    break;
                                }

                                Optional<Float> optional = this.damageCalculator.getBlockExplosionResistance(this.chunkedexplosions$self(), this.level, blockpos, blockstate, fluidstate);
                                if (optional.isPresent()) {
                                    f -= (optional.get() + 0.3F) * 0.3F;
                                }

                                if (f > 0.0F && this.damageCalculator.shouldBlockExplode(this.chunkedexplosions$self(), this.level, blockpos, blockstate, f)) {
                                    set.add(blockpos);
                                }

                                d4 += d0 * (double)0.3F;
                                d6 += d1 * (double)0.3F;
                                d8 += d2 * (double)0.3F;
                            }
                        }
                    }
                }
            }

            this.toBlow.addAll(set);
            float f2 = this.radius * 2.0F;
            int k1 = Mth.floor(this.x - (double)f2 - 1.0D);
            int l1 = Mth.floor(this.x + (double)f2 + 1.0D);
            int i2 = Mth.floor(this.y - (double)f2 - 1.0D);
            int i1 = Mth.floor(this.y + (double)f2 + 1.0D);
            int j2 = Mth.floor(this.z - (double)f2 - 1.0D);
            int j1 = Mth.floor(this.z + (double)f2 + 1.0D);
            List<Entity> list = this.level.getEntities(this.source, new AABB(k1, i2, j2, l1, i1, j1));
            net.minecraftforge.event.ForgeEventFactory.onExplosionDetonate(this.level, this.chunkedexplosions$self(), list, f2);
            Vec3 vec3 = new Vec3(this.x, this.y, this.z);

            for (Entity entity : list) {
                if (!entity.ignoreExplosion()) {
                    double d12 = Math.sqrt(entity.distanceToSqr(vec3)) / (double) f2;
                    if (d12 <= 1.0D) {
                        double d5 = entity.getX() - this.x;
                        double d7 = (entity instanceof PrimedTnt ? entity.getY() : entity.getEyeY()) - this.y;
                        double d9 = entity.getZ() - this.z;
                        double d13 = Math.sqrt(d5 * d5 + d7 * d7 + d9 * d9);
                        if (d13 != 0.0D) {
                            d5 /= d13;
                            d7 /= d13;
                            d9 /= d13;
                            double d14 = getSeenPercent(vec3, entity);
                            double d10 = (1.0D - d12) * d14;
                            entity.hurt(this.damageSource, (float) ((int) ((d10 * d10 + d10) / 2.0D * 7.0D * (double) f2 + 1.0D)));
                            double d11;
                            if (entity instanceof LivingEntity livingentity) {
                                d11 = ProtectionEnchantment.getExplosionKnockbackAfterDampener(livingentity, d10);
                            } else {
                                d11 = d10;
                            }

                            d5 *= d11;
                            d7 *= d11;
                            d9 *= d11;
                            Vec3 vec31 = new Vec3(d5, d7, d9);
                            entity.setDeltaMovement(entity.getDeltaMovement().add(vec31));
                            if (entity instanceof Player player) {
                                if (!player.isSpectator() && (!player.isCreative() || !player.getAbilities().flying)) {
                                    this.hitPlayers.put(player, vec31);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * @author justoboy
     * @reason for chunked explosions
     */
    @Overwrite
    public void finalizeExplosion(boolean shouldPlayParticles) {
        // Play sound and add particles on the client side if required
        if (this.level.isClientSide) {
            this.level.playLocalSound(this.x, this.y, this.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0F,
                    (1.0F + (this.level.random.nextFloat() - this.level.random.nextFloat()) * 0.2F) * 0.7F, false);
        }

        // Check if the explosion interacts with blocks
        boolean interactsWithBlocks = this.interactsWithBlocks();

        // Add particles based on the explosion radius and interaction flag
        if (shouldPlayParticles) {
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
                            blockState.getDrops(lootContextBuilder).forEach((itemStack) -> chunkedexplosions$addBlockDrops(droppedItems, itemStack, immutableBlockPos));
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
