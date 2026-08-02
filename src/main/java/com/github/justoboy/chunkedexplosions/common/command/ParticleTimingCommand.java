package com.github.justoboy.chunkedexplosions.common.command;

import com.github.justoboy.chunkedexplosions.core.ModConfig;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Command to configure when particles are created during explosion processing.
 * <p>
 * This controls the timing of particle effects during explosion processing.
 * The available modes determine when particles are spawned relative to the
 * explosion's lifecycle.
 * </p>
 * 
 * <h2>Usage</h2>
 * <ul>
 *   <li>{@code /chunkedexplosions particleTiming} - Get current particle timing</li>
 *   <li>{@code /chunkedexplosions particleTiming <mode>} - Set particle timing</li>
 * </ul>
 * 
 * <h2>Timing Modes</h2>
 * <ul>
 *   <li><b>START:</b> Particles are spawned immediately when the explosion begins (100%)</li>
 *   <li><b>END:</b> Particles are spawned when the explosion finishes processing (100%)</li>
 *   <li><b>START_END:</b> Particles are split between START and END phases (50% each, 100% total)</li>
 *   <li><b>SPREAD:</b> Particles are accumulated proportionally per block and spawned once per tick (100% total)</li>
 * </ul>
 * 
 * <h2>Particle Split</h2>
 * <p>
 * For SPREAD timing, particle count control is handled by {@code particleSplit}.
 * See {@code /chunkedexplosions particleSplit} for details.
 * </p>
 * 
 * <h2>Particle Types</h2>
 * <p>
 * The mod uses different particle types based on explosion properties:
 * <ul>
 *   <li><b>EXPLOSION_EMITTER:</b> Used for explosions that interact with blocks</li>
 *   <li><b>EXPLOSION:</b> Used for explosions in air or non-blocking areas</li>
 * </ul>
 * </p>
 */
public class ParticleTimingCommand {

    static {
        CommandComments.addComment("particleTiming", "When particles are created during the explosion. Options: START, END, START_END, SPREAD");
    }
    
    /**
     * Registers the particleTiming command with the command dispatcher.
     * 
     * @param ignoredBuildContext the command build context (unused for this command)
     * @return an argument builder for the particleTiming command
     */
    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext ignoredBuildContext) {
        return Commands.literal("particleTiming")
                .then(Commands.argument("value", StringArgumentType.word())
                        .suggests(SuggestionProviders::timingSuggestions)
                        .executes(context -> setValue(context, StringArgumentType.getString(context, "value"))))
                .executes(ParticleTimingCommand::sendValueMessage);
    }

    /**
     * Sets the particle timing mode.
     * 
     * @param context the command context
     * @param value the timing mode as a string (case-insensitive)
     * @return the command execution result (1 for success, 0 for failure)
     */
    private static int setValue(CommandContext<CommandSourceStack> context, String value) {
        try {
            ModConfig.Timing timing = ModConfig.Timing.valueOf(value.toUpperCase());
            ModConfig.setParticleTiming(timing);
            sendValueMessage(context);
            return 1;
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.literal("/chunkedexplosions particleTiming <START | END | START_END | SPREAD>"));
            return 0;
        }
    }

    /**
     * Sends a message showing the current particle timing mode.
     * 
     * @param context the command context
     * @return the command execution result (1 for success)
     */
    private static int sendValueMessage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("Particle timing: " + ModConfig.getParticleTiming()), true);
        return 1;
    }
}