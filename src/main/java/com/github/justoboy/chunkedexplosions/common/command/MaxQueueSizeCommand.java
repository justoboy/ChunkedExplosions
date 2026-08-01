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
 * Command to configure the maximum number of pending explosions in the queue.
 * <p>
 * This controls how many explosions can be waiting to be pre-calculated and
 * processed. When the queue is full, new explosions will be rejected.
 * Setting this to 0 removes the limit.
 * </p>
 * 
 * <h2>Usage</h2>
 * <ul>
 *   <li>{@code /chunkedexplosions maxQueueSize} - Get current queue limit</li>
 *   <li>{@code /chunkedexplosions maxQueueSize <value>} - Set queue limit (0 for no limit)</li>
 * </ul>
 * 
 * <h2>Performance Considerations</h2>
 * <ul>
 *   <li>Higher values allow more explosions to queue up during peak events</li>
 *   <li>Lower values may cause explosions to be rejected during large events</li>
 *   <li>Recommended: 1000-10000 for balanced performance</li>
 * </ul>
 */
public class MaxQueueSizeCommand {

    static {
        CommandComments.addComment("maxQueueSize", "Maximum number of blocks that can be queued for destruction across all explosions (0 for no limit).");
    }
    
    /**
     * Registers the maxQueueSize command with the command dispatcher.
     * 
     * @param ignoredBuildContext the command build context (unused for this command)
     * @return an argument builder for the maxQueueSize command
     */
    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext ignoredBuildContext) {
        return Commands.literal("maxQueueSize")
                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                        .suggests(SuggestionProviders::integerSuggestions)
                        .executes(context -> setValue(context, IntegerArgumentType.getInteger(context, "value"))))
                .executes(MaxQueueSizeCommand::sendValueMessage);
    }

    /**
     * Sets the maximum queue size for pending explosions.
     * 
     * @param context the command context
     * @param value the maximum queue size (0 for no limit)
     * @return the command execution result (1 for success, 0 for failure)
     */
    private static int setValue(CommandContext<CommandSourceStack> context, int value) {
        if (value >= 0) {
            ModConfig.setMaxQueueSize(value);
            sendValueMessage(context);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Max queue size must be a non-negative integer."));
            return 0;
        }
    }

    /**
     * Sends a message showing the current maximum queue size.
     * 
     * @param context the command context
     * @return the command execution result (1 for success)
     */
    private static int sendValueMessage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("Max queue size: " + ModConfig.getMaxQueueSize()), true);
        return 1;
    }
}
