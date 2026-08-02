package com.github.justoboy.chunkedexplosions.common.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Command to compare explosion block destruction between runs.
 */
public class CompareExplosionCommand {

    static {
        CommandComments.addComment("compareexplosion", "Compare recorded explosions. Usage: compareexplosion <baseline|history>");
    }

    private static final ConcurrentHashMap<String, Set<BlockPos>> explosionHistory = new ConcurrentHashMap<>();
    private static Set<BlockPos> storedBaseline = null;

    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext buildContext) {
        return Commands.literal("compareexplosion")
                .then(Commands.literal("baseline")
                        .then(Commands.literal("set")
                                .executes(CompareExplosionCommand::setBaseline)
                        )
                        .then(Commands.literal("clear")
                                .executes(CompareExplosionCommand::clearBaseline)
                        )
                )
                .then(Commands.literal("history")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(CompareExplosionCommand::saveAsHistory)
                                .then(Commands.literal("compare")
                                        .executes(CompareExplosionCommand::compareWithBaseline)
                                )
                        )
                        .then(Commands.literal("list")
                                .executes(CompareExplosionCommand::listHistory)
                        )
                )
                .then(Commands.literal("comparebaseline")
                        .executes(CompareExplosionCommand::compareWithBaseline)
                );
    }

    private static int setBaseline(CommandContext<CommandSourceStack> context) {
        Set<BlockPos> current = RecordExplosionCommand.getRecordedBlocks();
        if (current.isEmpty()) {
            context.getSource().sendFailure(Component.literal("No blocks recorded. Use 'recordexplosion start', trigger an explosion, then 'recordexplosion stop'."));
            return 0;
        }
        storedBaseline = new HashSet<>(current);
        context.getSource().sendSuccess(() -> Component.literal("Baseline set: %d blocks recorded.".formatted(storedBaseline.size())), true);
        return 1;
    }

    private static int clearBaseline(CommandContext<CommandSourceStack> context) {
        storedBaseline = null;
        context.getSource().sendSuccess(() -> Component.literal("Baseline cleared."), true);
        return 1;
    }

    private static int saveAsHistory(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        Set<BlockPos> current = RecordExplosionCommand.getRecordedBlocks();
        
        if (current.isEmpty()) {
            context.getSource().sendFailure(Component.literal("No blocks recorded."));
            return 0;
        }

        explosionHistory.put(name, new HashSet<>(current));
        context.getSource().sendSuccess(() -> Component.literal("Saved to history as '%s': %d blocks.".formatted(name, current.size())), true);
        return 1;
    }

    private static int compareWithBaseline(CommandContext<CommandSourceStack> context) {
        Set<BlockPos> current = RecordExplosionCommand.getRecordedBlocks();
        
        if (current.isEmpty()) {
            context.getSource().sendFailure(Component.literal("No blocks recorded for comparison."));
            return 0;
        }

        if (storedBaseline == null) {
            context.getSource().sendFailure(Component.literal("No baseline set. Use 'compareexplosion baseline set' first."));
            return 0;
        }

        // Calculate comparison
        Set<BlockPos> common = new HashSet<>(storedBaseline);
        common.retainAll(current);

        Set<BlockPos> baselineOnly = new HashSet<>(storedBaseline);
        baselineOnly.removeAll(current);

        Set<BlockPos> currentOnly = new HashSet<>(current);
        currentOnly.removeAll(storedBaseline);

        int totalBaseline = storedBaseline.size();
        int totalCurrent = current.size();
        int overlap = common.size();

        double matchPercentage = (totalBaseline > 0) ? (100.0 * overlap / totalBaseline) : 0;

        context.getSource().sendSuccess(() -> Component.literal("=== Explosion Comparison ==="), false);
        context.getSource().sendSuccess(() -> Component.literal(""), false);
        context.getSource().sendSuccess(() -> Component.literal("Baseline: %d blocks".formatted(totalBaseline)), false);
        context.getSource().sendSuccess(() -> Component.literal("Current:  %d blocks".formatted(totalCurrent)), false);
        context.getSource().sendSuccess(() -> Component.literal("Common:   %d blocks".formatted(overlap)), false);
        context.getSource().sendSuccess(() -> Component.literal("Baseline only: %d".formatted(baselineOnly.size())), false);
        context.getSource().sendSuccess(() -> Component.literal("Current only:  %d".formatted(currentOnly.size())), false);
        context.getSource().sendSuccess(() -> Component.literal(""), false);
        context.getSource().sendSuccess(() -> Component.literal("Match: %.1f%%".formatted(matchPercentage)), false);

        if (!baselineOnly.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal(""), false);
            context.getSource().sendSuccess(() -> Component.literal("Blocks in baseline but not current:"), false);
            int count = 0;
            for (BlockPos pos : baselineOnly) {
                if (count >= 20) {
                    context.getSource().sendSuccess(() -> Component.literal("... and %d more".formatted(baselineOnly.size() - 20)), false);
                    break;
                }
                context.getSource().sendSuccess(() -> Component.literal("  (%d, %d, %d)".formatted(pos.getX(), pos.getY(), pos.getZ())), false);
                count++;
            }
        }

        if (!currentOnly.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal(""), false);
            context.getSource().sendSuccess(() -> Component.literal("Blocks in current but not baseline:"), false);
            int count = 0;
            for (BlockPos pos : currentOnly) {
                if (count >= 20) {
                    context.getSource().sendSuccess(() -> Component.literal("... and %d more".formatted(currentOnly.size() - 20)), false);
                    break;
                }
                context.getSource().sendSuccess(() -> Component.literal("  (%d, %d, %d)".formatted(pos.getX(), pos.getY(), pos.getZ())), false);
                count++;
            }
        }

        return 1;
    }

    private static int listHistory(CommandContext<CommandSourceStack> context) {
        if (explosionHistory.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No history saved."), true);
            return 1;
        }

        context.getSource().sendSuccess(() -> Component.literal("=== Explosion History ==="), false);
        for (var entry : explosionHistory.entrySet()) {
            context.getSource().sendSuccess(() -> Component.literal("  %s: %d blocks".formatted(entry.getKey(), entry.getValue().size())), false);
        }
        return 1;
    }
}
