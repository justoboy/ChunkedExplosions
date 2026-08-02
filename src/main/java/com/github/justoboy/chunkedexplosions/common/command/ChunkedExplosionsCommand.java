package com.github.justoboy.chunkedexplosions.common.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Registers all chunked explosions commands under the {@code /chunkedexplosions} command.
 * <p>
 * This command serves as the main entry point for all chunked explosions configuration
 * and control commands. All subcommands are registered under this main command.
 * </p>
 * 
 * <h2>Available Commands</h2>
 * <ul>
 *   <li>{@code enable} - Enable or disable chunked explosions</li>
 *   <li>{@code help} - Display available commands with descriptions</li>
 *   <li>{@code explosionsPerTick} - Maximum explosions processed per tick</li>
 *   <li>{@code blocksPerExplosionTick} - Maximum blocks destroyed per tick per explosion</li>
 *   <li>{@code maxBlocksPerTick} - Global maximum blocks destroyed per tick</li>
 *   <li>{@code maxQueueSize} - Maximum pending explosions in queue</li>
 *   <li>{@code damageTiming} - When damage is applied to entities</li>
 *   <li>{@code soundTiming} - When explosion sound is played</li>
 *   <li>{@code soundVolumeSplit} - Whether sound volume is split for multi-stage timing</li>
 *   <li>{@code particleTiming} - When particles are spawned</li>
 *   <li>{@code particleSplit} - Whether particle count is split proportionally or spawned fully each tick</li>
 *   <li>{@code knockbackTiming} - When knockback is applied to entities</li>
 * </ul>
 */
public class ChunkedExplosionsCommand {

    /**
     * Registers all chunked explosions commands with the command dispatcher.
     * <p>
     * This method registers the main {@code /chunkedexplosions} command with all
     * its subcommands. Each subcommand is registered with its respective argument
     * builder to provide command-line interface functionality.
     * </p>
     * 
     * @param dispatcher the command dispatcher to register commands with
     * @param buildContext the command build context containing registry access
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(
                Commands.literal("chunkedexplosions")
                        .then(EnableCommand.register(buildContext))
                        .then(HelpCommand.register(buildContext))
                        .then(ExplosionsPerTickCommand.register(buildContext))
                        .then(BlocksPerExplosionTickCommand.register(buildContext))
                        .then(MaxBlocksPerTickCommand.register(buildContext))
                        .then(MaxQueueSizeCommand.register(buildContext))
                        .then(DamageTimingCommand.register(buildContext))
                        .then(SoundTimingCommand.register(buildContext))
                        .then(SoundVolumeSplitCommand.register(buildContext))
                        .then(ParticleTimingCommand.register(buildContext))
                        .then(ParticleSplitCommand.register(buildContext))
                        .then(KnockbackTimingCommand.register(buildContext))
        );
    }
}