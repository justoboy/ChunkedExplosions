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
        CommandComments.addComment("explosionsPerTick", "The amount of explosions to update per game tick. (0 for unlimited)");
    }

    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext buildContext) {
        return Commands.literal("explosionsPerTick")
                .then(Commands.argument("value", IntegerArgumentType.integer(0, 10000))
                        .executes(context -> execute(context, IntegerArgumentType.getInteger(context, "value"))))
        .executes(ExplosionsPerTickCommand::sendUsageMessage);
    }

    private static int execute(CommandContext<CommandSourceStack> context, int value) {
        // Set the enable state in your mod's configuration or a global variable
        ModConfig.setExplosionsPerTick(value);

        context.getSource().sendSuccess(() -> Component.literal("Explosions per tick: " + value), true);
        return 1;
    }

    private static int sendUsageMessage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("Explosions per tick: " + ModConfig.getExplosionsPerTick()), true);
        return 1;
    }
}