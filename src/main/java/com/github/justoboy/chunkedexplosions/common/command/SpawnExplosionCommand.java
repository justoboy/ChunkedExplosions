package com.github.justoboy.chunkedexplosions.common.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Command to spawn explosions for testing purposes.
 */
public class SpawnExplosionCommand {

    static {
        CommandComments.addComment("spawnexplosion", "Spawn an explosion for testing. Usage: spawnexplosion [radius]");
    }

    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext buildContext) {
        return Commands.literal("spawnexplosion")
                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(0.1, 20.0))
                        .executes(SpawnExplosionCommand::spawnWithRadius)
                )
                .executes(SpawnExplosionCommand::spawnDefault);
    }

    private static int spawnDefault(CommandContext<CommandSourceStack> context) {
        var pos = context.getSource().getPosition();
        context.getSource().sendSuccess(() -> Component.literal(
                "Spawning default TNT explosion at (%.1f, %.1f, %.1f)".formatted(pos.x, pos.y, pos.z)), true);
        return 1;
    }

    private static int spawnWithRadius(CommandContext<CommandSourceStack> context) {
        double radius = DoubleArgumentType.getDouble(context, "radius");
        var pos = context.getSource().getPosition();
        context.getSource().sendSuccess(() -> Component.literal(
                "Spawning explosion with radius %.1f at (%.1f, %.1f, %.1f)".formatted(radius, pos.x, pos.y, pos.z)), true);
        return 1;
    }
}
