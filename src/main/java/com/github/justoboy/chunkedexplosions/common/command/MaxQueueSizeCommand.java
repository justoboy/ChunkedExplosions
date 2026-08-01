package com.github.justoboy.chunkedexplosions.common.command;

import com.github.justoboy.chunkedexplosions.core.ModConfig;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class MaxQueueSizeCommand {

    static {
        CommandComments.addComment("maxQueueSize", "Maximum number of blocks that can be queued for destruction across all explosions (0 for no limit).");
    }

    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext ignoredBuildContext) {
        return Commands.literal("maxQueueSize")
                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                        .suggests(SuggestionProviders::integerSuggestions)
                        .executes(context -> setValue(context, IntegerArgumentType.getInteger(context, "value"))))
                .executes(MaxQueueSizeCommand::sendValueMessage);
    }

    private static int setValue(CommandContext<CommandSourceStack> context, int value) {
        if (value >= 0) {
            ModConfig.setMaxQueueSize(value);
            sendValueMessage(context);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Max queue size must be a non-negative integer."));
            return 0;
        }
    }

    private static int sendValueMessage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("Max queue size: " + ModConfig.getMaxQueueSize()), true);
        return 1;
    }
}
