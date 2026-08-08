package com.github.justoboy.chunkedexplosions.common.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.CompletableFuture;

/**
 * Command to create test environments with uniform blocks.
 * Usage: testcube [position] [size] [block]
 */
public class TestCubeCommand {

    static {
        CommandComments.addComment("testcube", "Create a cube of uniform blocks. Usage: testcube [position] [size] [block]");
    }

    private static final SuggestionProvider<CommandSourceStack> POSITION_SUGGESTER = (context, builder) -> {
        Vec3 pos = context.getSource().getPosition();
        builder.suggest(String.format("%.1f %.1f %.1f", pos.x, pos.y, pos.z));
        return builder.buildFuture();
    };

    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext buildContext) {
        return Commands.literal("testcube")
                // testcube [<position>] [<size>] [<block>]
                // Position defaults to player position, size defaults to 5, block defaults to minecraft:dirt
                .then(Commands.argument("position", Vec3Argument.vec3())
                        .suggests(POSITION_SUGGESTER)
                        .then(Commands.argument("size", IntegerArgumentType.integer(1, 100))
                                .then(Commands.argument("block", StringArgumentType.greedyString())
                                        .suggests(SuggestionProviders::blockSuggestions)
                                        .executes(TestCubeCommand::createCubeAtPosWithSizeAndBlock)
                                )
                                .executes(TestCubeCommand::createCubeAtPosWithSize)
                        )
                        .executes(TestCubeCommand::createCubeAtPos)
                )
                .executes(TestCubeCommand::createCubeDefault);
    }

    private static int createCubeDefault(CommandContext<CommandSourceStack> context) {
        var pos = context.getSource().getPosition();
        return createCube(context, 5, "minecraft:dirt", pos.x, pos.y, pos.z);
    }

    private static int createCubeAtPos(CommandContext<CommandSourceStack> context) {
        Vec3 pos = Vec3Argument.getVec3(context, "position");
        return createCube(context, 5, "minecraft:dirt", pos.x, pos.y, pos.z);
    }

    private static int createCubeAtPosWithSize(CommandContext<CommandSourceStack> context) {
        int size = IntegerArgumentType.getInteger(context, "size");
        Vec3 pos = Vec3Argument.getVec3(context, "position");
        return createCube(context, size, "minecraft:dirt", pos.x, pos.y, pos.z);
    }

    private static int createCubeAtPosWithSizeAndBlock(CommandContext<CommandSourceStack> context) {
        int size = IntegerArgumentType.getInteger(context, "size");
        String blockName = StringArgumentType.getString(context, "block");
        Vec3 pos = Vec3Argument.getVec3(context, "position");
        return createCube(context, size, blockName, pos.x, pos.y, pos.z);
    }

    private static BlockState parseBlockState(String blockName) {
        ResourceLocation blockKey = ResourceLocation.tryParse(blockName);
        if (blockKey == null) {
            return Blocks.STONE.defaultBlockState();
        }
        var block = BuiltInRegistries.BLOCK.getOptional(blockKey).orElse(Blocks.STONE);
        return block.defaultBlockState();
    }

    private static int createCube(CommandContext<CommandSourceStack> context,
                                  int size, String blockName,
                                  double centerX, double centerY, double centerZ) {
        ServerLevel level = context.getSource().getLevel();
        BlockState blockState = parseBlockState(blockName);

        // Calculate start and end positions to create exactly 'size' blocks per dimension
        // Use symmetric centering around the given position
        int startX = (int) Math.floor(centerX - size / 2.0);
        int endX = startX + size - 1;
        
        int startY = (int) Math.floor(centerY - size / 2.0);
        int endY = startY + size - 1;
        
        int startZ = (int) Math.floor(centerZ - size / 2.0);
        int endZ = startZ + size - 1;

        int blocksPlaced = 0;

        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                for (int z = startZ; z <= endZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.isInWorldBounds(pos)) {
                        level.setBlock(pos, blockState, 3);
                        blocksPlaced++;
                    }
                }
            }
        }

        String blockDisplayName = BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString();
        final int finalBlocksPlaced = blocksPlaced;
        context.getSource().sendSuccess(() -> Component.literal(
                "Created %dx%dx%d %s cube at (%.0f, %.0f, %.0f)".formatted(
                        endX - startX + 1, endY - startY + 1, endZ - startZ + 1,
                        blockDisplayName, centerX, centerY, centerZ)), true);
        context.getSource().sendSuccess(() -> Component.literal("Placed %d blocks".formatted(finalBlocksPlaced)), true);

        return 1;
    }
}
