package com.github.justoboy.chunkedexplosions.common.command;

import com.github.justoboy.chunkedexplosions.core.ModConfig;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class KnockbackMethodCommand {

    static {
        CommandComments.addComment("knockbackMethod", "Whether explosion knockback to entities is applied all at once when entering the blast radius or spread over time as the explosion occurs.");
    }

    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext ignoredBuildContext) {
        return Commands.literal("knockbackMethod")
                .then(Commands.argument("value", StringArgumentType.word())
                        .suggests(SuggestionProviders::methodSuggestions)
                        .executes(context -> setValue(context, StringArgumentType.getString(context, "value"))))
                .executes(KnockbackMethodCommand::sendValueMessage);
    }

    private static int setValue(CommandContext<CommandSourceStack> context, String value) {
        try {
            ModConfig.Method method = ModConfig.Method.valueOf(value.toUpperCase());
            ModConfig.setKnockbackMethod(method);
            sendValueMessage(context);
            return 1;
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.literal("/chunkedexplosions knockbackMethod <SPREAD | ONCE>"));
            return 0;
        }
    }

    private static int sendValueMessage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("Knockback method: " + ModConfig.getKnockbackMethod()), true);
        return 1;
    }
}