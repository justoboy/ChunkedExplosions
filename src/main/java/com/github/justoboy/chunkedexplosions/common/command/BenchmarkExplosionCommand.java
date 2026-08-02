package com.github.justoboy.chunkedexplosions.common.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.resources.ResourceLocation;

/**
 * Command to run automated explosion benchmarks.
 */
public class BenchmarkExplosionCommand {

    static {
        CommandComments.addComment("benchmarkexplosion", "Run explosion benchmark. Usage: benchmarkexplosion <iterations>");
    }

    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext buildContext) {
        return Commands.literal("benchmarkexplosion")
                .then(Commands.argument("iterations", IntegerArgumentType.integer(1, 100))
                        .executes(BenchmarkExplosionCommand::runBenchmark));
    }

    private static int runBenchmark(CommandContext<CommandSourceStack> context) {
        int iterations = IntegerArgumentType.getInteger(context, "iterations");
        ServerLevel level = context.getSource().getLevel();

        context.getSource().sendSuccess(() -> Component.literal("=== Explosion Benchmark ==="), false);
        context.getSource().sendSuccess(() -> Component.literal("Running %d iterations...".formatted(iterations)), false);
        context.getSource().sendSuccess(() -> Component.literal(""), false);

        context.getSource().sendSuccess(() -> Component.literal("Spawning TNT at (%.0f, %.0f, %.0f)".formatted(
                context.getSource().getPosition().x,
                context.getSource().getPosition().y,
                context.getSource().getPosition().z
        )), false);
        context.getSource().sendSuccess(() -> Component.literal(""), false);
        context.getSource().sendSuccess(() -> Component.literal(
                "Note: Full benchmark requires tracking explosion completion. " +
                "Use /chunkedexplosions explosionstats to monitor queue during testing."
        ), false);

        @SuppressWarnings("unchecked")
        EntityType<PrimedTnt> entityType = (EntityType<PrimedTnt>) (EntityType<?>) BuiltInRegistries.ENTITY_TYPE.get(
                ResourceLocation.fromNamespaceAndPath("minecraft", "primed_tnt"));
        
        if (entityType == null) {
            context.getSource().sendFailure(Component.literal("Failed to get TNT entity type."));
            return 0;
        }
        
        for (int i = 0; i < iterations; i++) {
            PrimedTnt tnt = new PrimedTnt(entityType, level);
            tnt.setPos(
                context.getSource().getPosition().x + (i * 2),
                context.getSource().getPosition().y + 1,
                context.getSource().getPosition().z
            );
            level.addFreshEntity(tnt);
        }

        context.getSource().sendSuccess(() -> Component.literal("Spawned %d TNT entities.".formatted(iterations)), true);
        context.getSource().sendSuccess(() -> Component.literal(""), false);
        context.getSource().sendSuccess(() -> Component.literal("Monitor progress with /chunkedexplosions explosionstats"), false);

        return 1;
    }
}
