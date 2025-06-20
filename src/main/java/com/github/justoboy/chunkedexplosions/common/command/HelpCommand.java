package com.github.justoboy.chunkedexplosions.common.command;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class HelpCommand {

    static {
        CommandComments.addComment("help", "Displays available commands.");
    }
    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext ignoredBuildContext) {
        return Commands.literal("help")
                .executes(HelpCommand::sendHelpMessage);
    }

    private static int sendHelpMessage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("Available commands:"), false);

        CommandComments.COMMAND_COMMENTS.forEach((command, comment) ->
                context.getSource().sendSuccess(() -> Component.literal("/chunkedexplosions " + command + ": " + comment), false)
        );

        return 1;
    }
}