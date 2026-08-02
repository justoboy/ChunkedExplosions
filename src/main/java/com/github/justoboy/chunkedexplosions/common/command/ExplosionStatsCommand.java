package com.github.justoboy.chunkedexplosions.common.command;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Command to display current explosion queue statistics.
 * <p>
 * This command shows detailed information about the current state of the
 * explosion processing system, including queue sizes and processing status.
 * </p>
 * 
 * <h2>Usage</h2>
 * <ul>
 *   <li>{@code /chunkedexplosions explosionstats} - Display queue statistics</li>
 * </ul>
 * 
 * <h2>Output Example</h2>
 * <pre>{@code
 * Explosion Queue Statistics:
 *   Awaiting Queue:     3
 *   Active Queue:       2
 *   Total Pending:      5
 *   Blocks this Tick:   24
 *   Remaining:          176
 * }</pre>
 */
public class ExplosionStatsCommand {

    static {
        CommandComments.addComment("explosionstats", "Display current explosion queue statistics and processing status.");
    }

    /**
     * Registers the explosionstats command with the command dispatcher.
     */
    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext buildContext) {
        return Commands.literal("explosionstats")
                .executes(ExplosionStatsCommand::sendStatsMessage);
    }

    /**
     * Sends the explosion statistics message.
     * 
     * @param context the command context
     * @return the command execution result
     */
    private static int sendStatsMessage(CommandContext<CommandSourceStack> context) {
        var processor = com.github.justoboy.chunkedexplosions.ChunkedExplosions.getExplosionProcessor();
        
        if (processor == null) {
            context.getSource().sendSuccess(() -> Component.literal("Explosion processor is not initialized."), true);
            return 1;
        }
        
        context.getSource().sendSuccess(() -> Component.literal("=== Explosion Queue Statistics ==="), false);
        context.getSource().sendSuccess(() -> Component.literal(""), false);
        
        int awaitingSize = processor.getAwaitingQueueSize();
        int activeSize = processor.getActiveQueueSize();
        int totalPending = processor.getTotalPendingExplosions();
        int blocksThisTick = processor.getBlocksDestroyedThisTick();
        int remainingBlocks = processor.getRemainingBlocksThisTick();
        
        context.getSource().sendSuccess(() -> Component.literal("  Awaiting Queue:  " + awaitingSize), false);
        context.getSource().sendSuccess(() -> Component.literal("  Active Queue:    " + activeSize), false);
        context.getSource().sendSuccess(() -> Component.literal("  Total Pending:   " + totalPending), false);
        context.getSource().sendSuccess(() -> Component.literal(""), false);
        context.getSource().sendSuccess(() -> Component.literal("  Blocks This Tick: " + blocksThisTick), false);
        context.getSource().sendSuccess(() -> Component.literal("  Remaining:        " + remainingBlocks), false);
        context.getSource().sendSuccess(() -> Component.literal(""), false);
        
        if (totalPending == 0) {
            context.getSource().sendSuccess(() -> Component.literal("  No explosions pending."), false);
        } else {
            context.getSource().sendSuccess(() -> Component.literal("  Processor Status: " + processor), false);
        }
        
        return 1;
    }
}
