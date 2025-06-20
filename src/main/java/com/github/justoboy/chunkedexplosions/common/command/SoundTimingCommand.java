package com.github.justoboy.chunkedexplosions.common.command;

import com.github.justoboy.chunkedexplosions.core.ModConfig;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class SoundTimingCommand {

    static {
        CommandComments.addComment("soundTiming", "When the explosion sound is played.");
    }

    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext ignoredBuildContext) {
        return Commands.literal("soundTiming")
                .then(Commands.argument("value", StringArgumentType.word())
                        .suggests(SuggestionProviders::timingSuggestions)
                        .executes(context -> setValue(context, StringArgumentType.getString(context, "value"))))
                .executes(SoundTimingCommand::sendValueMessage);
    }

    private static int setValue(CommandContext<CommandSourceStack> context, String value) {
        try {
            ModConfig.Timing timing = ModConfig.Timing.valueOf(value.toUpperCase());
            ModConfig.setSoundTiming(timing);
            sendValueMessage(context);
            return 1;
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.literal("/chunkedexplosions soundTiming <START | END | START_END | SPREAD>"));
            return 0;
        }
    }

    private static int sendValueMessage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("Sound timing: " + ModConfig.getSoundTiming()), true);
        return 1;
    }
}