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

    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext ignoredBuildContext) {
        return Commands.literal("enable")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(context -> setValue(context, BoolArgumentType.getBool(context, "value"))))
        .executes(EnableCommand::sendValueMessage);
    }

    private static int setValue(CommandContext<CommandSourceStack> context, boolean value) {
        ModConfig.setEnable(value);
        sendValueMessage(context);
        return 1;
    }

    private static int sendValueMessage(CommandContext<CommandSourceStack> context) {
        String message = ModConfig.getEnable() ? "enabled" : "disabled";
        context.getSource().sendSuccess(() -> Component.literal("Chunked Explosions: " + message), true);
        return 1;
    }
}