package com.github.justoboy.chunkedexplosions.common.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import com.github.justoboy.chunkedexplosions.core.ModConfig;

/**
 * Command to configure whether explosion particle count is split when using SPREAD timing mode.
 * <p>
 * When {@code particleTiming} is set to SPREAD, this setting determines
 * whether the particle count is split proportionally across ticks or spawned at full count each tick.
 * </p>
 * 
 * <h2>Usage</h2>
 * <ul>
 *   <li>{@code /chunkedexplosions particleSplit} - Get current split setting</li>
 *   <li>{@code /chunkedexplosions particleSplit true} - Enable particle splitting</li>
 *   <li>{@code /chunkedexplosions particleSplit false} - Disable particle splitting</li>
 * </ul>
 * 
 * <h2>Split Behavior</h2>
 * <ul>
 *   <li><b>Enabled (true):</b> Particle count is split proportionally
 *     <ul>
 *       <li>SPREAD: Particles distributed based on % of blocks destroyed each tick</li>
 *     </ul>
 *   </li>
 *   <li><b>Disabled (false):</b> Full particle count is spawned each tick
 *     <ul>
 *       <li>SPREAD: Full particle count spawned every tick that destroys blocks</li>
 *     </ul>
 *   </li>
 * </ul>
 * 
 * <h2>Recommendations</h2>
 * <ul>
 *   <li>Enable splitting for smoother particle distribution over time</li>
 *   <li>Disable splitting for more impactful particles each tick</li>
 * </ul>
 */
public class ParticleSplitCommand {

    static {
        CommandComments.addComment("particleSplit", "Whether the explosion particle count is split proportionally when particleTiming is set to SPREAD. Options: true, false");
    }
    
    /**
     * Registers the particleSplit command with the command dispatcher.
     * 
     * @param ignoredBuildContext the command build context (unused for this command)
     * @return an argument builder for the particleSplit command
     */
    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext ignoredBuildContext) {
        return Commands.literal("particleSplit")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .suggests(SuggestionProviders::boolSuggestions)
                        .executes(context -> setValue(context, BoolArgumentType.getBool(context, "value"))))
                .executes(ParticleSplitCommand::sendValueMessage);
    }

    /**
     * Sets whether particle count should be split.
     * 
     * @param context the command context
     * @param value true to split particles proportionally, false for full count each tick
     * @return the command execution result (1 for success)
     */
    private static int setValue(CommandContext<CommandSourceStack> context, boolean value) {
        ModConfig.setParticleSplit(value);
        sendValueMessage(context);
        return 1;
    }

    /**
     * Sends a message showing the current particle split setting.
     * 
     * @param context the command context
     * @return the command execution result (1 for success)
     */
    private static int sendValueMessage(CommandContext<CommandSourceStack> context) {
        String message = ModConfig.getParticleSplit() ? "enabled" : "disabled";
        context.getSource().sendSuccess(() -> Component.literal("Particle split: " + message), true);
        return 1;
    }
}
