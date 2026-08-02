package com.github.justoboy.chunkedexplosions.common.command;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.HashSet;
import java.util.Set;

/**
 * Command to record blocks destroyed by explosions for comparison testing.
 */
public class RecordExplosionCommand {

    static {
        CommandComments.addComment("recordexplosion", "Record blocks destroyed by explosions. Usage: recordexplosion <start|stop|report|clear>");
    }

    private static final Set<BlockPos> recordedBlocks = new HashSet<>();
    private static boolean isRecording = false;

    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext buildContext) {
        return Commands.literal("recordexplosion")
                .then(Commands.literal("start")
                        .executes(RecordExplosionCommand::startRecording))
                .then(Commands.literal("stop")
                        .executes(RecordExplosionCommand::stopRecording))
                .then(Commands.literal("report")
                        .executes(RecordExplosionCommand::reportRecorded))
                .then(Commands.literal("clear")
                        .executes(RecordExplosionCommand::clearRecorded));
    }

    private static int startRecording(CommandContext<CommandSourceStack> context) {
        if (isRecording) {
            context.getSource().sendFailure(Component.literal("Already recording. Use 'stop' first."));
            return 0;
        }
        recordedBlocks.clear();
        isRecording = true;
        context.getSource().sendSuccess(() -> Component.literal("Now recording blocks destroyed by explosions."), true);
        context.getSource().sendSuccess(() -> Component.literal("Use 'stop' to finish recording, or 'clear' to reset."), true);
        return 1;
    }

    private static int stopRecording(CommandContext<CommandSourceStack> context) {
        if (!isRecording) {
            context.getSource().sendFailure(Component.literal("Not currently recording."));
            return 0;
        }
        isRecording = false;
        context.getSource().sendSuccess(() -> Component.literal("Recording stopped. %d blocks recorded.".formatted(recordedBlocks.size())), true);
        context.getSource().sendSuccess(() -> Component.literal("Use 'report' to see the list, or 'clear' to reset."), true);
        return 1;
    }

    private static int reportRecorded(CommandContext<CommandSourceStack> context) {
        if (recordedBlocks.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No blocks recorded."), true);
            return 1;
        }

        context.getSource().sendSuccess(() -> Component.literal("=== Recorded Blocks ===".formatted(recordedBlocks.size())), false);
        context.getSource().sendSuccess(() -> Component.literal("Total blocks: %d".formatted(recordedBlocks.size())), false);
        context.getSource().sendSuccess(() -> Component.literal(""), false);

        // Show first 50 blocks
        int count = 0;
        for (BlockPos pos : recordedBlocks) {
            if (count >= 50) {
                context.getSource().sendSuccess(() -> Component.literal("... and %d more blocks".formatted(recordedBlocks.size() - 50)), false);
                break;
            }
            context.getSource().sendSuccess(() -> Component.literal("  (%d, %d, %d)".formatted(pos.getX(), pos.getY(), pos.getZ())), false);
            count++;
        }

        return 1;
    }

    private static int clearRecorded(CommandContext<CommandSourceStack> context) {
        int cleared = recordedBlocks.size();
        recordedBlocks.clear();
        context.getSource().sendSuccess(() -> Component.literal("Cleared %d recorded blocks.".formatted(cleared)), true);
        return 1;
    }

    // Helper method to record a block (to be called from ExplosionState during block destruction)
    public static void recordBlockDestroyed(BlockPos pos) {
        if (isRecording) {
            recordedBlocks.add(pos.immutable());
        }
    }

    public static boolean isRecording() {
        return isRecording;
    }

    public static Set<BlockPos> getRecordedBlocks() {
        return new HashSet<>(recordedBlocks);
    }
}
