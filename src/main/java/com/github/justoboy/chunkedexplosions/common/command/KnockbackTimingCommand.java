package com.github.justoboy.chunkedexplosions.common.command;

import com.github.justoboy.chunkedexplosions.core.ModConfig;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class KnockbackTimingCommand {

    static {
        CommandComments.addComment("knockbackTiming", "When knockback from explosions is applied to entities.");
    }

    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext ignoredBuildContext) {
        return Commands.literal("knockbackTiming")
                .then(Commands.argument("value", StringArgumentType.word())
                        .executes(context -> setValue(context, StringArgumentType.getString(context, "value"))))
                .executes(KnockbackTimingCommand::sendValueMessage);
    }

    private static int setValue(CommandContext<CommandSourceStack> context, String value) {
        try {
            ModConfig.Timing timing = ModConfig.Timing.valueOf(value.toUpperCase());
            ModConfig.setKnockbackTiming(timing);
            sendValueMessage(context);
            return 1;
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.literal("/chunkedexplosions knockbackTiming <START|END|START_END|SPREAD>"));
            return 0;
        }
    }

    private static int sendValueMessage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("Knockback timing: " + ModConfig.getKnockbackTiming()), true);
        return 1;
    }
}