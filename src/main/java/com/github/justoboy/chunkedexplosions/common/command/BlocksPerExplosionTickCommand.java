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
 * Command to configure the maximum number of blocks destroyed per tick by each explosion.
 * <p>
 * This controls how many blocks each individual explosion can destroy per server tick.
 * Setting this to 0 removes the limit.
 * </p>
 * 
 * <h2>Usage</h2>
 * <ul>
 *   <li>{@code /chunkedexplosions blocksPerExplosionTick} - Get current limit</li>
 *   <li>{@code /chunkedexplosions blocksPerExplosionTick <value>} - Set limit (0 for no limit)</li>
 * </ul>
 * 
 * <h2>Performance Considerations</h2>
 * <ul>
 *   <li>Higher values make explosions complete faster but use more CPU per tick</li>
 *   <li>Lower values spread explosion processing over more ticks</li>
 *   <li>Recommended: 16-256 for balanced performance</li>
 * </ul>
 */
public class BlocksPerExplosionTickCommand {

    static {
        CommandComments.addComment("blocksPerExplosionTick", "Maximum number of blocks updated per server tick by each explosion (0 for no limit).");
    }
    
    /**
     * Registers the blocksPerExplosionTick command with the command dispatcher.
     * 
     * @param ignoredBuildContext the command build context (unused for this command)
     * @return an argument builder for the blocksPerExplosionTick command
     */
    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext ignoredBuildContext) {
        return Commands.literal("blocksPerExplosionTick")
                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                        .suggests(SuggestionProviders::integerSuggestions)
                        .executes(context -> setValue(context, IntegerArgumentType.getInteger(context, "value"))))
        .executes(BlocksPerExplosionTickCommand::sendValueMessage);
    }

    /**
     * Sets the maximum blocks per tick per explosion.
     * 
     * @param context the command context
     * @param value the maximum blocks per tick per explosion (0 for no limit)
     * @return the command execution result (1 for success, 0 for failure)
     */
    private static int setValue(CommandContext<CommandSourceStack> context, int value) {
        if (value >= 0) {
            ModConfig.setBlocksPerExplosionTick(value);
            sendValueMessage(context);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Blocks per explosion tick must be a non-negative integer."));
            return 0;
        }
    }

    /**
     * Sends a message showing the current blocks per tick per explosion limit.
     * 
     * @param context the command context
     * @return the command execution result (1 for success)
     */
    private static int sendValueMessage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("Blocks per explosion tick: " + ModConfig.getBlocksPerExplosionTick()), true);
        return 1;
    }
}