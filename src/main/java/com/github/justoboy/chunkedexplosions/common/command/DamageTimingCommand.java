package com.github.justoboy.chunkedexplosions.common.command;

import com.github.justoboy.chunkedexplosions.core.ModConfig;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class DamageTimingCommand {

    static {
        CommandComments.addComment("damageTiming", "When damage from explosions is applied to entities.");
    }

    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext ignoredBuildContext) {
        return Commands.literal("damageTiming")
                .then(Commands.argument("value", StringArgumentType.word())
                        .suggests(SuggestionProviders::timingSuggestions)
                        .executes(context -> setValue(context, StringArgumentType.getString(context, "value"))))
        .executes(DamageTimingCommand::sendValueMessage);
    }

    private static int setValue(CommandContext<CommandSourceStack> context, String value) {
        try {
            ModConfig.Timing timing = ModConfig.Timing.valueOf(value.toUpperCase());
            ModConfig.setDamageTiming(timing);
            sendValueMessage(context);
            return 1;
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.literal("/chunkedexplosions damageTiming <START | END | START_END | SPREAD>"));
            return 0;
        }
    }

    private static int sendValueMessage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("Damage timing: " + ModConfig.getDamageTiming()), true);
        return 1;
    }
}