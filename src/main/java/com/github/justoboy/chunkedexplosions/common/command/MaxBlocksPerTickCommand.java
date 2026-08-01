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
 * Command to configure the global maximum number of blocks updated per server tick across all explosions.
 * <p>
 * This is a global cap that limits the total number of blocks that can be destroyed
 * across all active explosions in a single server tick. Setting this to 0 removes
 * the limit.
 * </p>
 * 
 * <h2>Usage</h2>
 * <ul>
 *   <li>{@code /chunkedexplosions maxBlocksPerTick} - Get current global limit</li>
 *   <li>{@code /chunkedexplosions maxBlocksPerTick <value>} - Set global limit (0 for no limit)</li>
 * </ul>
 * 
 * <h2>Performance Considerations</h2>
 * <ul>
 *   <li>This is a global cap affecting all explosions simultaneously</li>
 *   <li>Lower values reduce server load but may slow down large explosion events</li>
 *   <li>Higher values allow more simultaneous destruction but increase CPU usage</li>
 *   <li>Recommended: 1024-16384 for balanced performance</li>
 * </ul>
 * 
 * <h2>Interaction with Other Limits</h2>
 * <p>
 * The global {@code maxBlocksPerTick} limit is checked before the per-explosion
 * {@code blocksPerExplosionTick} limit. If the global limit is reached, no
 * additional blocks will be destroyed even if individual explosions have capacity.
 * </p>
 */
public class MaxBlocksPerTickCommand {

    static {
        CommandComments.addComment("maxBlocksPerTick", "Maximum number of blocks updated per server tick across all explosions (0 for no limit).");
    }
    
    /**
     * Registers the maxBlocksPerTick command with the command dispatcher.
     * 
     * @param ignoredBuildContext the command build context (unused for this command)
     * @return an argument builder for the maxBlocksPerTick command
     */
    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext ignoredBuildContext) {
        return Commands.literal("maxBlocksPerTick")
                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                        .suggests(SuggestionProviders::integerSuggestions)
                        .executes(context -> setValue(context, IntegerArgumentType.getInteger(context, "value"))))
                .executes(MaxBlocksPerTickCommand::sendValueMessage);
    }

    /**
     * Sets the global maximum blocks per tick limit.
     * 
     * @param context the command context
     * @param value the global maximum blocks per tick (0 for no limit)
     * @return the command execution result (1 for success, 0 for failure)
     */
    private static int setValue(CommandContext<CommandSourceStack> context, int value) {
        if (value >= 0) {
            ModConfig.setMaxBlocksPerTick(value);
            sendValueMessage(context);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Max blocks per tick must be a non-negative integer."));
            return 0;
        }
    }

    /**
     * Sends a message showing the current global max blocks per tick limit.
     * 
     * @param context the command context
     * @return the command execution result (1 for success)
     */
    private static int sendValueMessage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("Max blocks per tick: " + ModConfig.getMaxBlocksPerTick()), true);
        return 1;
    }
}
