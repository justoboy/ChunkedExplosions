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
 * Command to configure when knockback from explosions is applied to entities.
 * <p>
 * This controls the timing of knockback application during explosion processing.
 * The available modes determine when and how knockback is distributed over time.
 * </p>
 * 
 * <h2>Usage</h2>
 * <ul>
 *   <li>{@code /chunkedexplosions knockbackTiming} - Get current knockback timing</li>
 *   <li>{@code /chunkedexplosions knockbackTiming <mode>} - Set knockback timing</li>
 * </ul>
 * 
 * <h2>Timing Modes</h2>
 * <ul>
 *   <li><b>START:</b> All knockback is applied immediately when the explosion begins (100%)</li>
 *   <li><b>END:</b> All knockback is applied when the explosion finishes processing (100%)</li>
 *   <li><b>START_END:</b> Knockback is split between START and END phases (50% each, 100% total)</li>
 *   <li><b>SPREAD:</b> Knockback is accumulated proportionally per block and applied once per tick (100% total)</li>
 * </ul>
 */
public class KnockbackTimingCommand {

    static {
        CommandComments.addComment("knockbackTiming", "When knockback from explosions is applied to entities. Options: START, END, START_END, SPREAD");
    }
    
    /**
     * Registers the knockbackTiming command with the command dispatcher.
     * 
     * @param ignoredBuildContext the command build context (unused for this command)
     * @return an argument builder for the knockbackTiming command
     */
    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext ignoredBuildContext) {
        return Commands.literal("knockbackTiming")
                .then(Commands.argument("value", StringArgumentType.word())
                        .suggests(SuggestionProviders::timingSuggestions)
                        .executes(context -> setValue(context, StringArgumentType.getString(context, "value"))))
                .executes(KnockbackTimingCommand::sendValueMessage);
    }

    /**
     * Sets the knockback timing mode.
     * 
     * @param context the command context
     * @param value the timing mode as a string (case-insensitive)
     * @return the command execution result (1 for success, 0 for failure)
     */
    private static int setValue(CommandContext<CommandSourceStack> context, String value) {
        try {
            ModConfig.Timing timing = ModConfig.Timing.valueOf(value.toUpperCase());
            ModConfig.setKnockbackTiming(timing);
            sendValueMessage(context);
            return 1;
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.literal("/chunkedexplosions knockbackTiming <START | END | START_END | SPREAD>"));
            return 0;
        }
    }

    /**
     * Sends a message showing the current knockback timing mode.
     * 
     * @param context the command context
     * @return the command execution result (1 for success)
     */
    private static int sendValueMessage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("Knockback timing: " + ModConfig.getKnockbackTiming()), true);
        return 1;
    }
}