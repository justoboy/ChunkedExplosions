package com.github.justoboy.chunkedexplosions.common.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import com.github.justoboy.chunkedexplosions.core.ModConfig;

public class SoundVolumeSplitCommand {

    static {
        CommandComments.addComment("soundVolumeSplit", "Whether the explosion sound volume is split when soundTiming is set to START_END or SPREAD.");
    }

    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext ignoredBuildContext) {
        return Commands.literal("soundVolumeSplit")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(context -> setValue(context, BoolArgumentType.getBool(context, "value"))))
                .executes(SoundVolumeSplitCommand::sendValueMessage);
    }

    private static int setValue(CommandContext<CommandSourceStack> context, boolean value) {
        ModConfig.setSoundVolumeSplit(value);
        sendValueMessage(context);
        return 1;
    }

    private static int sendValueMessage(CommandContext<CommandSourceStack> context) {
        String message = ModConfig.getSoundVolumeSplit() ? "enabled" : "disabled";
        context.getSource().sendSuccess(() -> Component.literal("Sound volume split: " + message), true);
        return 1;
    }
}