package com.github.justoboy.chunkedexplosions.common.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;

/**
 * Command to clear a test area for fresh explosion testing.
 */
public class TestClearCommand {

    static {
        CommandComments.addComment("testclear", "Clear a cubic region. Usage: testclear <size> [block]");
    }

    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext buildContext) {
        return Commands.literal("testclear")
                .then(Commands.argument("size", IntegerArgumentType.integer(1, 100))
                        .executes(TestClearCommand::clearAtPlayer)
                        .then(Commands.argument("block", StringArgumentType.word())
                                .executes(TestClearCommand::clearWithBlockAtPlayer)
                                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                        .executes(TestClearCommand::clearWithBlockAtPos)
                                                )
                                        )
                                )
                        )
                );
    }

    private static int clearAtPlayer(CommandContext<CommandSourceStack> context) {
        int size = IntegerArgumentType.getInteger(context, "size");
        var pos = context.getSource().getPosition();
        return clearArea(context, size, "air", pos.x, pos.y, pos.z);
    }

    private static int clearWithBlockAtPlayer(CommandContext<CommandSourceStack> context) {
        int size = IntegerArgumentType.getInteger(context, "size");
        String blockName = StringArgumentType.getString(context, "block");
        var pos = context.getSource().getPosition();
        return clearArea(context, size, blockName, pos.x, pos.y, pos.z);
    }

    private static int clearWithBlockAtPos(CommandContext<CommandSourceStack> context) {
        int size = IntegerArgumentType.getInteger(context, "size");
        String blockName = StringArgumentType.getString(context, "block");
        double x = DoubleArgumentType.getDouble(context, "x");
        double y = DoubleArgumentType.getDouble(context, "y");
        double z = DoubleArgumentType.getDouble(context, "z");
        return clearArea(context, size, blockName, x, y, z);
    }

    private static BlockState parseBlockState(String blockName) {
        if (blockName.equalsIgnoreCase("air")) {
            return Blocks.AIR.defaultBlockState();
        }
        ResourceLocation blockKey = ResourceLocation.tryParse(blockName);
        if (blockKey == null) {
            return Blocks.AIR.defaultBlockState();
        }
        var block = BuiltInRegistries.BLOCK.getOptional(blockKey).orElse(Blocks.AIR);
        return block.defaultBlockState();
    }

    private static int clearArea(CommandContext<CommandSourceStack> context,
                                 int size, String blockName,
                                 double centerX, double centerY, double centerZ) {
        ServerLevel level = context.getSource().getLevel();
        BlockState replaceState = parseBlockState(blockName);

        int halfSize = size / 2;
        int startX = (int) Math.floor(centerX - halfSize);
        int endX = (int) Math.ceil(centerX + halfSize);
        int startY = (int) Math.floor(centerY - halfSize);
        int endY = (int) Math.ceil(centerY + halfSize);
        int startZ = (int) Math.floor(centerZ - halfSize);
        int endZ = (int) Math.ceil(centerZ + halfSize);

        int blocksProcessed = 0;

        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                for (int z = startZ; z <= endZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.isInWorldBounds(pos)) {
                        level.setBlock(pos, replaceState, 3);
                        blocksProcessed++;
                    }
                }
            }
        }

        String actualBlockName = blockName.equalsIgnoreCase("air") ? "air" : blockName;
        final int finalBlocksProcessed = blocksProcessed;
        context.getSource().sendSuccess(() -> Component.literal(
                "Processed %dx%dx%d region at (%.0f, %.0f, %.0f) with %s".formatted(
                        endX - startX + 1, endY - startY + 1, endZ - startZ + 1,
                        centerX, centerY, centerZ, actualBlockName)), true);
        context.getSource().sendSuccess(() -> Component.literal("Processed %d blocks".formatted(finalBlocksProcessed)), true);

        return 1;
    }
}
