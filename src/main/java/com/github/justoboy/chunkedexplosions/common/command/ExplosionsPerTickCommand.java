package com.github.justoboy.chunkedexplosions.common.command;

import com.github.justoboy.chunkedexplosions.core.ModConfig;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Command to configure the maximum number of explosions processed per server tick.
 * <p>
 * This controls how many explosions can be in the active processing queue at once.
 * Setting this to 0 removes the limit.
 * </p>
 * 
 * <h2>Usage</h2>
 * <ul>
 *   <li>{@code /chunkedexplosions explosionsPerTick} - Get current limit</li>
 *   <li>{@code /chunkedexplosions explosionsPerTick <value>} - Set limit (0 for no limit)</li>
 * </ul>
 * 
 * <h2>Performance Considerations</h2>
 * <ul>
 *   <li>Higher values allow more explosions to be processed simultaneously</li>
 *   <li>May increase server load during large explosion events</li>
 *   <li>Lower values may cause explosions to queue up and process slower</li>
 * </ul>
 */
public class ExplosionsPerTickCommand {

    static {
        CommandComments.addComment("explosionsPerTick", "Maximum number of explosions updated per server tick (0 for no limit).");
    }
    
    /**
     * Registers the explosionsPerTick command with the command dispatcher.
     * 
     * @param ignoredBuildContext the command build context (unused for this command)
     * @return an argument builder for the explosionsPerTick command
     */
    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext ignoredBuildContext) {
        return Commands.literal("explosionsPerTick")
                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                        .suggests(SuggestionProviders::integerSuggestions)
                        .executes(context -> setValue(context, IntegerArgumentType.getInteger(context, "value"))))
        .executes(ExplosionsPerTickCommand::sendValueMessage);
    }

    /**
     * Sets the maximum number of explosions per tick.
     * 
     * @param context the command context
     * @param value the maximum explosions per tick (0 for no limit)
     * @return the command execution result (1 for success, 0 for failure)
     */
    private static int setValue(CommandContext<CommandSourceStack> context, int value) {
        if (value >= 0) {
            ModConfig.setExplosionsPerTick(value);
            sendValueMessage(context);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Explosions per tick must be a non-negative integer."));
            return 0;
        }
    }

    /**
     * Sends a message showing the current explosions per tick limit.
     * 
     * @param context the command context
     * @return the command execution result (1 for success)
     */
    private static int sendValueMessage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("Explosions per tick: " + ModConfig.getExplosionsPerTick()), true);
        return 1;
    }
}