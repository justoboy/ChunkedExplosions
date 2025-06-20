package com.github.justoboy.chunkedexplosions.common.command;

import com.github.justoboy.chunkedexplosions.core.ModConfig;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class BlocksPerExplosionTickCommand {

    static {
        CommandComments.addComment("blocksPerExplosionTick", "Maximum number of blocks updated per server tick by each explosion (0 for no limit).");
    }

    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext ignoredBuildContext) {
        return Commands.literal("blocksPerExplosionTick")
                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                        .suggests(SuggestionProviders::integerSuggestions)
                        .executes(context -> setValue(context, IntegerArgumentType.getInteger(context, "value"))))
        .executes(BlocksPerExplosionTickCommand::sendValueMessage);
    }

    private static int setValue(CommandContext<CommandSourceStack> context, int value) {
        if (value >= 0) {
            ModConfig.setBlocksPerExplosionTick(value);
            sendValueMessage(context);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Blocks per explosion tick must be a non-negative integer."));
            return 0;
        }
    }

    private static int sendValueMessage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("Blocks per explosion tick: " + ModConfig.getBlocksPerExplosionTick()), true);
        return 1;
    }
}