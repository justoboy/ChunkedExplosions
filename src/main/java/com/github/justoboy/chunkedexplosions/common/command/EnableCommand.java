package com.github.justoboy.chunkedexplosions.common.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import com.github.justoboy.chunkedexplosions.core.ModConfig;

public class EnableCommand {

    static {
        CommandComments.addComment("enable", "Enable or disable chunked explosions.");
    }

    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext buildContext) {
        return Commands.literal("enable")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(context -> execute(context, BoolArgumentType.getBool(context, "value"))))
        .executes(EnableCommand::sendUsageMessage);
    }

    private static int execute(CommandContext<CommandSourceStack> context, boolean value) {
        // Set the enable state in your mod's configuration or a global variable
        ModConfig.setEnable(value);

        String message = value ? "Enabled!" : "Disabled!";
        context.getSource().sendSuccess(() -> Component.literal("Chunked Explosions " + message), true);
        return 1;
    }

    private static int sendUsageMessage(CommandContext<CommandSourceStack> context) {
        String message = ModConfig.isEnable() ? "Enabled." : "Disabled.";
        context.getSource().sendSuccess(() -> Component.literal("Chunked Explosions are " + message), true);
        return 1;
    }
}