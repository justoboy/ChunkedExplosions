package com.github.justoboy.chunkedexplosions.common.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.CompletableFuture;

/**
 * Command to run automated explosion benchmarks.
 * Usage: benchmarkexplosion <iterations> [position]
 */
public class BenchmarkExplosionCommand {

    static {
        CommandComments.addComment("benchmarkexplosion", "Run explosion benchmark. Usage: benchmarkexplosion <iterations> [position]");
    }

    private static final SuggestionProvider<CommandSourceStack> POSITION_SUGGESTER = (context, builder) -> {
        Vec3 pos = context.getSource().getPosition();
        builder.suggest(String.format("%.1f %.1f %.1f", pos.x, pos.y, pos.z));
        return builder.buildFuture();
    };

    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext buildContext) {
        return Commands.literal("benchmarkexplosion")
                .then(Commands.argument("iterations", IntegerArgumentType.integer(1, 100))
                        .then(Commands.argument("position", Vec3Argument.vec3())
                                .suggests(POSITION_SUGGESTER)
                                .executes(BenchmarkExplosionCommand::runBenchmarkAtPos)
                        )
                        .executes(BenchmarkExplosionCommand::runBenchmark));
    }

    private static int runBenchmark(CommandContext<CommandSourceStack> context) {
        int iterations = IntegerArgumentType.getInteger(context, "iterations");
        Vec3 pos = context.getSource().getPosition();
        return runBenchmarkAtPosition(context, iterations, pos);
    }

    private static int runBenchmarkAtPos(CommandContext<CommandSourceStack> context) {
        int iterations = IntegerArgumentType.getInteger(context, "iterations");
        Vec3 pos = Vec3Argument.getVec3(context, "position");
        return runBenchmarkAtPosition(context, iterations, pos);
    }

    private static int runBenchmarkAtPosition(CommandContext<CommandSourceStack> context, int iterations, Vec3 pos) {
        ServerLevel level = context.getSource().getLevel();

        context.getSource().sendSuccess(() -> Component.literal("=== Explosion Benchmark ==="), false);
        context.getSource().sendSuccess(() -> Component.literal("Running %d iterations...".formatted(iterations)), false);
        context.getSource().sendSuccess(() -> Component.literal(""), false);

        context.getSource().sendSuccess(() -> Component.literal("Spawning TNT at (%.0f, %.0f, %.0f)".formatted(pos.x, pos.y, pos.z)), false);
        context.getSource().sendSuccess(() -> Component.literal(""), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "Note: Full benchmark requires tracking explosion completion. " +
                "Use /chunkedexplosions explosionstats to monitor queue during testing."
        ), false);

        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(
                ResourceLocation.fromNamespaceAndPath("minecraft", "primed_tnt"));
        
        if (entityType == null) {
            context.getSource().sendFailure(Component.literal("Failed to get TNT entity type."));
            return 0;
        }
        
        for (int i = 0; i < iterations; i++) {
            PrimedTnt tnt = new PrimedTnt((EntityType<PrimedTnt>) entityType, level);
            tnt.setPos(pos.x + (i * 2), pos.y + 1, pos.z);
            level.addFreshEntity(tnt);
        }

        context.getSource().sendSuccess(() -> Component.literal("Spawned %d TNT entities.".formatted(iterations)), true);
        context.getSource().sendSuccess(() -> Component.literal(""), false);
        context.getSource().sendSuccess(() -> Component.literal("Monitor progress with /chunkedexplosions explosionstats"), false);

        return 1;
    }
}
