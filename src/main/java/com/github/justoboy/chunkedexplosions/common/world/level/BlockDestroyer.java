package com.github.justoboy.chunkedexplosions.common.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Handles block destruction and drop calculation for chunked explosions.
 * 
 * This class encapsulates all block-related operations including:
 * - Block destruction
 * - Loot calculation and spawning
 * - Fire placement (if enabled)
 * - Block interaction mode handling
 * 
 * @see ExplosionState for integration with the explosion processing system
 */
public class BlockDestroyer {

    /** Block interaction mode (KEEP, DESTROY, DESTROY_WITH_DECAY) */
    private final Explosion.BlockInteraction blockInteraction;

    /** Explosion radius for decay calculations */
    private final float radius;

    /** The entity that caused the explosion */
    private final Entity source;

    /** Whether to place fire after block destruction */
    private final boolean fire;

    /**
     * Creates a new BlockDestroyer with the specified configuration.
     *
     * @param blockInteraction the block interaction mode
     * @param radius           the explosion radius
     * @param source           the explosion source entity
     * @param fire             whether to place fire after destruction
     */
    public BlockDestroyer(Explosion.BlockInteraction blockInteraction, float radius, Entity source, boolean fire) {
        this.blockInteraction = blockInteraction;
        this.radius = radius;
        this.source = source;
        this.fire = fire;
    }

    /**
     * Destroys a single block and handles drops and fire placement.
     * 
     * This method:
     * 1. Checks if the block should be destroyed based on interaction mode
     * 2. Calculates and spawns item drops using loot tables
     * 3. Sets the block to air
     * 4. Places fire if enabled and the block is now air
     *
     * @param level the server level
     * @param pos   the position of the block to destroy
     */
    public void destroyBlock(ServerLevel level, BlockPos pos) {
        // Check if blocks should be destroyed at all
        if (blockInteraction == Explosion.BlockInteraction.KEEP) {
            return;
        }

        BlockState blockState = level.getBlockState(pos);

        // Skip if block is already air
        if (blockState.isAir()) {
            return;
        }

        // Get block entity for loot context
        BlockEntity blockEntity = blockState.hasBlockEntity() ? level.getBlockEntity(pos) : null;

        // Create loot context builder
        LootParams.Builder lootContextBuilder = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                .withOptionalParameter(LootContextParams.BLOCK_ENTITY, blockEntity)
                .withOptionalParameter(LootContextParams.THIS_ENTITY, source);

        // Add explosion radius for DESTROY_WITH_DECAY mode
        if (blockInteraction == Explosion.BlockInteraction.DESTROY_WITH_DECAY) {
            lootContextBuilder.withParameter(LootContextParams.EXPLOSION_RADIUS, radius);
        }

        // Spawn drops using Minecraft's loot system
        // First call spawnAfterBreak for side effects (experience orbs, etc.)
        blockState.spawnAfterBreak(level, pos, ItemStack.EMPTY, 
                source instanceof net.minecraft.world.entity.player.Player);
        
        // Then get and spawn all drops
        blockState.getDrops(lootContextBuilder).forEach(itemStack -> 
                Block.popResource(level, pos, itemStack));

        // Set block to air or fire based on configuration
        if (fire) {
            level.setBlock(pos, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
            level.gameEvent(source, GameEvent.BLOCK_PLACE, pos);
        } else {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        
        // Notify game event listeners
        level.gameEvent(source, GameEvent.BLOCK_DESTROY, pos);
    }

    /**
     * Checks if this destroyer will destroy blocks.
     *
     * @return true if blocks will be destroyed, false if KEEP mode
     */
    public boolean interactsWithBlocks() {
        return blockInteraction != Explosion.BlockInteraction.KEEP;
    }

    /**
     * Gets the block interaction mode.
     *
     * @return the block interaction mode
     */
    public Explosion.BlockInteraction getBlockInteraction() {
        return blockInteraction;
    }

    /**
     * Gets whether fire placement is enabled.
     *
     * @return true if fire should be placed
     */
    public boolean isFire() {
        return fire;
    }

    /**
     * Gets the explosion radius.
     *
     * @return the explosion radius
     */
    public float getRadius() {
        return radius;
    }

    /**
     * Gets the explosion source entity.
     *
     * @return the source entity
     */
    public Entity getSource() {
        return source;
    }
}
