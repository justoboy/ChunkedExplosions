package com.github.justoboy.chunkedexplosions.common.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class ChunkedExplosionsCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(
                Commands.literal("chunkedexplosions")
                        .then(EnableCommand.register(buildContext))
                        .then(HelpCommand.register(buildContext))
                        .then(ExplosionsPerTickCommand.register(buildContext))
                        .then(BlocksPerExplosionTickCommand.register(buildContext))
                        .then(DamageTimingCommand.register(buildContext))
                        .then(DamageMethodCommand.register(buildContext))
                        .then(SoundTimingCommand.register(buildContext))
                        .then(SoundVolumeSplitCommand.register(buildContext))
                        .then(ParticleTimingCommand.register(buildContext))
                        .then(KnockbackTimingCommand.register(buildContext))
                        .then(KnockbackMethodCommand.register(buildContext))
        );
    }
}