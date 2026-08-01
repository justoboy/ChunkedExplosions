package com.github.justoboy.chunkedexplosions.common.command;

import com.github.justoboy.chunkedexplosions.core.ModConfig;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class MaxBlocksPerTickCommand {

    static {
        CommandComments.addComment("maxBlocksPerTick", "Maximum number of blocks updated per server tick across all explosions (0 for no limit).");
    }

    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext ignoredBuildContext) {
        return Commands.literal("maxBlocksPerTick")
                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                        .suggests(SuggestionProviders::integerSuggestions)
                        .executes(context -> setValue(context, IntegerArgumentType.getInteger(context, "value"))))
                .executes(MaxBlocksPerTickCommand::sendValueMessage);
    }

    private static int setValue(CommandContext<CommandSourceStack> context, int value) {
        if (value >= 0) {
            ModConfig.setMaxBlocksPerTick(value);
            sendValueMessage(context);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Max blocks per tick must be a non-negative integer."));
            return 0;
        }
    }

    private static int sendValueMessage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("Max blocks per tick: " + ModConfig.getMaxBlocksPerTick()), true);
        return 1;
    }
}
