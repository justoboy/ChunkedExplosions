package com.github.justoboy.chunkedexplosions.common.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
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

/**
 * Command to spawn explosions for testing purposes.
 * Usage: spawnexplosion [radius] [position]
 * 
 * Note: The radius parameter is informational only. TNT always explodes with
 * its default radius (4.0). The position parameter controls where the TNT spawns.
 */
public class SpawnExplosionCommand {

    static {
        CommandComments.addComment("spawnexplosion", "Spawn an explosion for testing. Usage: spawnexplosion [radius] [position]. Note: radius is informational only; TNT uses default radius (4.0).");
    }

    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext buildContext) {
        return Commands.literal("spawnexplosion")
                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(0.1, 20.0))
                        .then(Commands.argument("position", Vec3Argument.vec3())
                                .executes(SpawnExplosionCommand::spawnWithRadiusAndPos)
                        )
                        .executes(SpawnExplosionCommand::spawnWithRadius)
                )
                .then(Commands.argument("position", Vec3Argument.vec3())
                        .executes(SpawnExplosionCommand::spawnDefaultAtPos)
                )
                .executes(SpawnExplosionCommand::spawnDefault);
    }

    private static int spawnDefault(CommandContext<CommandSourceStack> context) {
        Vec3 pos = context.getSource().getPosition();
        spawnTntExplosion(context.getSource().getLevel(), pos);
        
        context.getSource().sendSuccess(() -> Component.literal(
                "Spawned TNT explosion at (%.1f, %.1f, %.1f) with default radius 4.0".formatted(pos.x, pos.y, pos.z)), true);
        return 1;
    }

    private static int spawnWithRadius(CommandContext<CommandSourceStack> context) {
        double radius = DoubleArgumentType.getDouble(context, "radius");
        Vec3 pos = context.getSource().getPosition();
        spawnTntExplosion(context.getSource().getLevel(), pos);
        
        context.getSource().sendSuccess(() -> Component.literal(
                "Spawned TNT explosion at (%.1f, %.1f, %.1f) with default radius 4.0 (specified: %.1f)".formatted(pos.x, pos.y, pos.z, radius)), true);
        return 1;
    }

    private static int spawnDefaultAtPos(CommandContext<CommandSourceStack> context) {
        Vec3 pos = Vec3Argument.getVec3(context, "position");
        spawnTntExplosion(context.getSource().getLevel(), pos);
        
        context.getSource().sendSuccess(() -> Component.literal(
                "Spawned TNT explosion at (%.1f, %.1f, %.1f) with default radius 4.0".formatted(pos.x, pos.y, pos.z)), true);
        return 1;
    }

    private static int spawnWithRadiusAndPos(CommandContext<CommandSourceStack> context) {
        double radius = DoubleArgumentType.getDouble(context, "radius");
        Vec3 pos = Vec3Argument.getVec3(context, "position");
        spawnTntExplosion(context.getSource().getLevel(), pos);
        
        context.getSource().sendSuccess(() -> Component.literal(
                "Spawned TNT explosion at (%.1f, %.1f, %.1f) with default radius 4.0 (specified: %.1f)".formatted(pos.x, pos.y, pos.z, radius)), true);
        return 1;
    }

    /**
     * Spawns a TNT entity at the given position.
     * The TNT will explode after 80 ticks (4 seconds), triggering the mod's
     * mixin interception for proper chunked explosion handling.
     *
     * @param level the server level
     * @param pos the TNT spawn position
     */
    private static void spawnTntExplosion(ServerLevel level, Vec3 pos) {
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(
                ResourceLocation.fromNamespaceAndPath("minecraft", "primed_tnt"));
        if (entityType == null) {
            return;
        }
        
        PrimedTnt tnt = new PrimedTnt((EntityType<PrimedTnt>) entityType, level);
        tnt.setPos(pos.x, pos.y, pos.z);
        // Set fuse to 80 ticks (4 seconds) to give time for positioning
        tnt.setFuse(80);
        level.addFreshEntity(tnt);
    }
}
