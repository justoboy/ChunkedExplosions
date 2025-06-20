package com.github.justoboy.chunkedexplosions.common.command;

import com.github.justoboy.chunkedexplosions.core.ModConfig;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class DamageMethodCommand {

    static {
        CommandComments.addComment("damageMethod", "Whether explosion damage to entities is applied all at once when entering the blast radius or spread over time as the explosion occurs.");
    }

    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext ignoredBuildContext) {
        return Commands.literal("damageMethod")
                .then(Commands.argument("value", StringArgumentType.word())
                        .executes(context -> setValue(context, StringArgumentType.getString(context, "value"))))
                .executes(DamageMethodCommand::sendValueMessage);
    }

    private static int setValue(CommandContext<CommandSourceStack> context, String value) {
        try {
            ModConfig.Method method = ModConfig.Method.valueOf(value.toUpperCase());
            ModConfig.setDamageMethod(method);
            sendValueMessage(context);
            return 1;
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.literal("/chunkedexplosions damageMethod <SPREAD|ONCE>"));
            return 0;
        }
    }

    private static int sendValueMessage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("Damage method: " + ModConfig.getDamageMethod()), true);
        return 1;
    }
}