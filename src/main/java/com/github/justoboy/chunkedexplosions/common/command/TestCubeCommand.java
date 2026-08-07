package com.github.justoboy.chunkedexplosions.common.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
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

/**
 * Command to create test environments with uniform blocks.
 */
public class TestCubeCommand {

    static {
        CommandComments.addComment("testcube", "Create a cube of uniform blocks. Usage: testcube <size> <block> [position]");
    }

    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext buildContext) {
        return Commands.literal("testcube")
                .then(Commands.argument("size", IntegerArgumentType.integer(1, 100))
                        .then(Commands.argument("block", StringArgumentType.word())
                                .suggests(SuggestionProviders::blockSuggestions)
                                .executes(TestCubeCommand::createCubeAtPlayer)
                                .then(Commands.argument("position", Vec3Argument.vec3())
                                        .executes(TestCubeCommand::createCubeAtPos)
                                )
                        )
                );
    }

    private static int createCubeAtPlayer(CommandContext<CommandSourceStack> context) {
        int size = IntegerArgumentType.getInteger(context, "size");
        String blockName = StringArgumentType.getString(context, "block");
        var pos = context.getSource().getPosition();
        return createCube(context, size, blockName, pos.x, pos.y, pos.z);
    }

    private static int createCubeAtPos(CommandContext<CommandSourceStack> context) {
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

        int halfSize = size / 2;
        int startX = (int) Math.floor(centerX - halfSize);
        int endX = (int) Math.ceil(centerX + halfSize);
        int startY = (int) Math.floor(centerY - halfSize);
        int endY = (int) Math.ceil(centerY + halfSize);
        int startZ = (int) Math.floor(centerZ - halfSize);
        int endZ = (int) Math.ceil(centerZ + halfSize);

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
