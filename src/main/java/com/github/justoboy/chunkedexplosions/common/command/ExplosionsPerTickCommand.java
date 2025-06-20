package com.github.justoboy.chunkedexplosions.common.command;

import com.github.justoboy.chunkedexplosions.core.ModConfig;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class ExplosionsPerTickCommand {

    static {
        CommandComments.addComment("explosionsPerTick", "Maximum number of explosions updated per server tick (0 for no limit).");
    }

    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext ignoredBuildContext) {
        return Commands.literal("explosionsPerTick")
                .then(Commands.argument("value", IntegerArgumentType.integer())
                        .executes(context -> setValue(context, IntegerArgumentType.getInteger(context, "value"))))
        .executes(ExplosionsPerTickCommand::sendValueMessage);
    }

    private static int setValue(CommandContext<CommandSourceStack> context, int value) {
        if (value >= 0) {
            ModConfig.setExplosionsPerTick(value);
            sendValueMessage(context);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Explosions per tick must be a non-negative integer."));
            return 0;
        }
    }

    private static int sendValueMessage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("Explosions per tick: " + ModConfig.getExplosionsPerTick()), true);
        return 1;
    }
}