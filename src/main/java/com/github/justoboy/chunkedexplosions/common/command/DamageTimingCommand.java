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
 * Command to configure when damage from explosions is applied to entities.
 * <p>
 * This controls the timing of damage application during explosion processing.
 * The available modes determine when and how damage is distributed over time.
 * </p>
 * 
 * <h2>Usage</h2>
 * <ul>
 *   <li>{@code /chunkedexplosions damageTiming} - Get current timing mode</li>
 *   <li>{@code /chunkedexplosions damageTiming <mode>} - Set timing mode</li>
 * </ul>
 * 
 * <h2>Timing Modes</h2>
 * <ul>
 *   <li><b>START:</b> All damage is applied immediately when the explosion begins (100%)</li>
 *   <li><b>END:</b> All damage is applied when the explosion finishes processing (100%)</li>
 *   <li><b>START_END:</b> Damage is split between START and END phases (50% each, 100% total)</li>
 *   <li><b>SPREAD:</b> Damage is accumulated proportionally per block and applied once per tick (100% total)</li>
 * </ul>
 */
public class DamageTimingCommand {

    static {
        CommandComments.addComment("damageTiming", "When damage from explosions is applied to entities. Options: START, END, START_END, SPREAD");
    }
    
    /**
     * Registers the damageTiming command with the command dispatcher.
     * 
     * @param ignoredBuildContext the command build context (unused for this command)
     * @return an argument builder for the damageTiming command
     */
    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext ignoredBuildContext) {
        return Commands.literal("damageTiming")
                .then(Commands.argument("value", StringArgumentType.word())
                        .suggests(SuggestionProviders::timingSuggestions)
                        .executes(context -> setValue(context, StringArgumentType.getString(context, "value"))))
        .executes(DamageTimingCommand::sendValueMessage);
    }

    /**
     * Sets the damage timing mode.
     * 
     * @param context the command context
     * @param value the timing mode as a string (case-insensitive)
     * @return the command execution result (1 for success, 0 for failure)
     */
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

    /**
     * Sends a message showing the current damage timing mode.
     * 
     * @param context the command context
     * @return the command execution result (1 for success)
     */
    private static int sendValueMessage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("Damage timing: " + ModConfig.getDamageTiming()), true);
        return 1;
    }
}