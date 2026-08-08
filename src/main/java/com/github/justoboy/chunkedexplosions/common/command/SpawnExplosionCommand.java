package com.github.justoboy.chunkedexplosions.common.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.CompletableFuture;

/**
 * Command to spawn explosions for testing purposes.
 * Usage: spawnexplosion [position] [radius]
 *
 * This command uses level.explode() to create explosions with the specified radius.
 */
public class SpawnExplosionCommand {

    static {
        CommandComments.addComment("spawnexplosion", "Spawn an explosion for testing. Usage: spawnexplosion [position] [radius]. Radius is the explosion size in blocks.");
    }

    private static final SuggestionProvider<CommandSourceStack> POSITION_SUGGESTER = (context, builder) -> {
        // Suggest the player's current position as absolute coordinates
        Vec3 pos = context.getSource().getPosition();
        builder.suggest(String.format("%.1f %.1f %.1f", pos.x, pos.y, pos.z));
        return builder.buildFuture();
    };

    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext buildContext) {
        return Commands.literal("spawnexplosion")
                .then(Commands.argument("position", Vec3Argument.vec3())
                        .suggests(POSITION_SUGGESTER)
                        .executes(SpawnExplosionCommand::spawnDefaultAtPos)
                        .then(Commands.argument("radius", DoubleArgumentType.doubleArg(0.1, 1000.0))
                                .executes(SpawnExplosionCommand::spawnAtPosWithRadius)
                        )
                )
                .executes(SpawnExplosionCommand::spawnDefault);
    }

    private static int spawnDefault(CommandContext<CommandSourceStack> context) {
        Vec3 pos = context.getSource().getPosition();
        explodeAtPosition(context.getSource().getLevel(), pos, 4.0f);
        
        context.getSource().sendSuccess(() -> Component.literal(
                "Spawned explosion at (%.1f, %.1f, %.1f) with radius 4.0".formatted(pos.x, pos.y, pos.z)), true);
        return 1;
    }

    private static int spawnDefaultAtPos(CommandContext<CommandSourceStack> context) {
        Vec3 pos = Vec3Argument.getVec3(context, "position");
        explodeAtPosition(context.getSource().getLevel(), pos, 4.0f);
        
        context.getSource().sendSuccess(() -> Component.literal(
                "Spawned explosion at (%.1f, %.1f, %.1f) with radius 4.0".formatted(pos.x, pos.y, pos.z)), true);
        return 1;
    }

    private static int spawnAtPosWithRadius(CommandContext<CommandSourceStack> context) {
        Vec3 pos = Vec3Argument.getVec3(context, "position");
        float radius = (float) DoubleArgumentType.getDouble(context, "radius");
        explodeAtPosition(context.getSource().getLevel(), pos, radius);
        
        context.getSource().sendSuccess(() -> Component.literal(
                "Spawned explosion at (%.1f, %.1f, %.1f) with radius %.1f".formatted(pos.x, pos.y, pos.z, radius)), true);
        return 1;
    }

    /**
     * Creates an explosion at the given position with the specified radius.
     * Uses level.explode() which triggers the mod's mixin for chunked explosion handling.
     *
     * @param level the server level
     * @param pos the explosion center position
     * @param radius the explosion radius in blocks
     */
    private static void explodeAtPosition(ServerLevel level, Vec3 pos, float radius) {
        // Use level.explode() to create an explosion with the specified radius
        // This triggers the mod's ExplosionMixin for chunked explosion handling
        // Signature: explode(Entity, double x, double y, double z, float radius, boolean causeFire, ExplosionInteraction)
        level.explode(
                null,                           // Exploding entity (null = no source)
                pos.x, pos.y, pos.z,            // Explosion center coordinates
                radius,                         // Explosion radius
                false,                          // causeFire (false = no fire)
                Level.ExplosionInteraction.TNT  // TNT interaction type (destroys blocks)
        );
    }
}
