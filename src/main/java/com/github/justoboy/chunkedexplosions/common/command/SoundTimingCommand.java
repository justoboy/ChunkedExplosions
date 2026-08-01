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
 * Command to configure when the explosion sound is played.
 * <p>
 * This controls the timing of sound playback during explosion processing.
 * The available modes determine when the sound is heard relative to the
 * explosion's lifecycle.
 * </p>
 * 
 * <h2>Usage</h2>
 * <ul>
 *   <li>{@code /chunkedexplosions soundTiming} - Get current sound timing</li>
 *   <li>{@code /chunkedexplosions soundTiming <mode>} - Set sound timing</li>
 * </ul>
 * 
 * <h2>Timing Modes</h2>
 * <ul>
 *   <li><b>START:</b> Sound is played immediately when the explosion begins</li>
 *   <li><b>END:</b> Sound is played when the explosion finishes processing</li>
 *   <li><b>START_END:</b> Sound is split between START and END phases</li>
 *   <li><b>SPREAD:</b> Sound is accumulated and played once per tick during processing</li>
 * </ul>
 * 
 * <h2>Sound Volume Split</h2>
 * <p>
 * When using START_END or SPREAD timing, the {@code soundVolumeSplit} setting
 * determines whether the sound volume is split between phases.
 * </p>
 */
public class SoundTimingCommand {

    static {
        CommandComments.addComment("soundTiming", "When the explosion sound is played. Options: START, END, START_END, SPREAD");
    }
    
    /**
     * Registers the soundTiming command with the command dispatcher.
     * 
     * @param ignoredBuildContext the command build context (unused for this command)
     * @return an argument builder for the soundTiming command
     */
    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext ignoredBuildContext) {
        return Commands.literal("soundTiming")
                .then(Commands.argument("value", StringArgumentType.word())
                        .suggests(SuggestionProviders::timingSuggestions)
                        .executes(context -> setValue(context, StringArgumentType.getString(context, "value"))))
                .executes(SoundTimingCommand::sendValueMessage);
    }

    /**
     * Sets the sound timing mode.
     * 
     * @param context the command context
     * @param value the timing mode as a string (case-insensitive)
     * @return the command execution result (1 for success, 0 for failure)
     */
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

    /**
     * Sends a message showing the current sound timing mode.
     * 
     * @param context the command context
     * @return the command execution result (1 for success)
     */
    private static int sendValueMessage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("Sound timing: " + ModConfig.getSoundTiming()), true);
        return 1;
    }
}