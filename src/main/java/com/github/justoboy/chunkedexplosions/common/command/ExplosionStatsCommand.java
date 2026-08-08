package com.github.justoboy.chunkedexplosions.common.command;

import com.github.justoboy.chunkedexplosions.core.ModConfig;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * Command to display current explosion queue statistics and configuration.
 * 
 * <p>This command shows detailed information about the current state of the
 * explosion processing system, including queue sizes, block counts, and current settings.</p>
 * 
 * <h2>Usage</h2>
 * <ul>
 *   <li>{@code /chunkedexplosions explosionstats} - Display queue statistics and config</li>
 * </ul>
 * 
 * <h2>Output Example</h2>
 * <pre>{@code
 * === Explosion Queue Statistics ===
 * 
 *   Awaiting Queue:  3
 *   Active Queue:    2
 *   Total Pending:   5
 * 
 *   Blocks This Tick:     24
 *   Blocks Remaining:     176
 *   Total Queued Blocks:  512
 * 
 * === Current Settings ===
 *   Blocks Per Explosion Tick:  16
 *   Explosions Per Tick:        1024
 *   Max Blocks Per Tick:        16384
 *   Damage Timing:              SPREAD
 *   Knockback Timing:           SPREAD
 *   Sound Timing:               SPREAD
 *   Sound Volume Split:         true
 *   Particle Timing:            SPREAD
 *   Particle Split:             true
 *   Cascade Suppression:        false
 * }</pre>
 */
public class ExplosionStatsCommand {

    static {
        CommandComments.addComment("explosionstats", "Display current explosion queue statistics and configuration settings.");
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
        
        // === Queue Statistics ===
        context.getSource().sendSuccess(() -> Component.literal("=== Explosion Queue Statistics ==="), false);
        context.getSource().sendSuccess(() -> Component.literal(""), false);
        
        int awaitingSize = processor.getAwaitingQueueSize();
        int activeSize = processor.getActiveQueueSize();
        int totalPending = processor.getTotalPendingExplosions();
        int blocksThisTick = processor.getBlocksDestroyedThisTick();
        int blocksRemaining = processor.getTotalRemainingBlocks();
        int totalQueuedBlocks = processor.getTotalQueuedBlocks();
        
        context.getSource().sendSuccess(() -> Component.literal("  Awaiting Queue:      " + awaitingSize), false);
        context.getSource().sendSuccess(() -> Component.literal("  Active Queue:        " + activeSize), false);
        context.getSource().sendSuccess(() -> Component.literal("  Total Pending:       " + totalPending), false);
        context.getSource().sendSuccess(() -> Component.literal(""), false);
        context.getSource().sendSuccess(() -> Component.literal("  Blocks This Tick:    " + blocksThisTick), false);
        context.getSource().sendSuccess(() -> Component.literal("  Blocks Remaining:    " + blocksRemaining), false);
        context.getSource().sendSuccess(() -> Component.literal("  Total Queued Blocks: " + totalQueuedBlocks), false);
        context.getSource().sendSuccess(() -> Component.literal(""), false);
        
        if (totalPending == 0) {
            context.getSource().sendSuccess(() -> Component.literal("  No explosions pending."), false);
        } else {
            context.getSource().sendSuccess(() -> Component.literal("  Processor Status: " + processor), false);
        }
        
        // === Current Settings ===
        context.getSource().sendSuccess(() -> Component.literal(""), false);
        context.getSource().sendSuccess(() -> Component.literal("=== Current Settings ==="), false);
        context.getSource().sendSuccess(() -> Component.literal(""), false);
        
        context.getSource().sendSuccess(() -> Component.literal("  Blocks Per Explosion Tick:  " + ModConfig.getBlocksPerExplosionTick()), false);
        context.getSource().sendSuccess(() -> Component.literal("  Explosions Per Tick:        " + ModConfig.getExplosionsPerTick()), false);
        context.getSource().sendSuccess(() -> Component.literal("  Max Blocks Per Tick:        " + ModConfig.getMaxBlocksPerTick()), false);
        context.getSource().sendSuccess(() -> Component.literal(""), false);
        context.getSource().sendSuccess(() -> Component.literal("  Damage Timing:              " + Objects.toString(ModConfig.getDamageTiming(), "N/A")), false);
        context.getSource().sendSuccess(() -> Component.literal("  Knockback Timing:           " + Objects.toString(ModConfig.getKnockbackTiming(), "N/A")), false);
        context.getSource().sendSuccess(() -> Component.literal("  Sound Timing:               " + Objects.toString(ModConfig.getSoundTiming(), "N/A")), false);
        context.getSource().sendSuccess(() -> Component.literal("  Sound Volume Split:         " + ModConfig.getSoundVolumeSplit()), false);
        context.getSource().sendSuccess(() -> Component.literal("  Particle Timing:            " + Objects.toString(ModConfig.getParticleTiming(), "N/A")), false);
        context.getSource().sendSuccess(() -> Component.literal("  Particle Split:             " + ModConfig.getParticleSplit()), false);
        context.getSource().sendSuccess(() -> Component.literal(""), false);
        context.getSource().sendSuccess(() -> Component.literal("  Cascade Suppression:        " + ModConfig.getCascadeSuppression()), false);
        context.getSource().sendSuccess(() -> Component.literal("  Chunked Explosions Enabled: " + ModConfig.getEnable()), false);
        
        return 1;
    }
}
