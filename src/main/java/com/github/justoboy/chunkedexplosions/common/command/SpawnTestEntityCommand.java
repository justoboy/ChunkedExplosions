package com.github.justoboy.chunkedexplosions.common.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.concurrent.CompletableFuture;

/**
 * Command to spawn test entities at precise positions for explosion testing.
 * All spawned entities have their movement speed set to 0 to keep them stationary
 * while still allowing them to receive knockback and respond to physics.
 *
 * Usage: /chunkedexplosions sptestentity <entity> <count> <radius> <angle> [position]
 *
 * Examples:
 * /chunkedexplosions sptestentity iron_golem 8 4 45     - 8 golems in circle, 4 blocks away
 * /chunkedexplosions sptestentity iron_golem 5 2 0 0 64 0  - 5 golems at world origin
 */
public class SpawnTestEntityCommand {

    /** NBT tag key for marking test entities */
    private static final String TEST_ENTITY_TAG = "chunkedexplosions_test_entity";

    static {
        CommandComments.addComment("sptestentity", "Spawn stationary test entities with movement speed set to 0. Usage: sptestentity <entity> <count> <radius> <angle> [position]");
    }

    private static final SuggestionProvider<CommandSourceStack> POSITION_SUGGESTER = (context, builder) -> {
        Vec3 pos = context.getSource().getPosition();
        builder.suggest(String.format("%.1f %.1f %.1f", pos.x, pos.y, pos.z));
        return builder.buildFuture();
    };

    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext buildContext) {
        return Commands.literal("sptestentity")
                .then(Commands.argument("entity", ResourceArgument.resource(buildContext, Registries.ENTITY_TYPE))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(0.1, 64.0))
                                        .then(Commands.argument("angle", DoubleArgumentType.doubleArg(0, 360))
                                                .then(Commands.argument("position", Vec3Argument.vec3())
                                                        .suggests(POSITION_SUGGESTER)
                                                        .executes(SpawnTestEntityCommand::spawnTestEntitiesAtPos)
                                                )
                                                .executes(SpawnTestEntityCommand::spawnTestEntities)
                                        )
                                )
                        )
                );
    }

    private static int spawnTestEntities(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        EntityType<?> entityType = ResourceArgument.getEntityType(context, "entity").value();
        int count = IntegerArgumentType.getInteger(context, "count");
        double radius = DoubleArgumentType.getDouble(context, "radius");
        double angle = DoubleArgumentType.getDouble(context, "angle");
        
        Vec3 centerPos = context.getSource().getPosition();
        
        return spawnAtPosition(context, entityType, count, radius, angle, centerPos.x, centerPos.y, centerPos.z);
    }

    private static int spawnTestEntitiesAtPos(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        EntityType<?> entityType = ResourceArgument.getEntityType(context, "entity").value();
        int count = IntegerArgumentType.getInteger(context, "count");
        double radius = DoubleArgumentType.getDouble(context, "radius");
        double angle = DoubleArgumentType.getDouble(context, "angle");
        Vec3 centerPos = Vec3Argument.getVec3(context, "position");
        
        return spawnAtPosition(context, entityType, count, radius, angle, centerPos.x, centerPos.y, centerPos.z);
    }

    private static int spawnAtPosition(CommandContext<CommandSourceStack> context, EntityType<?> entityType, int count, double radius, double angle, double x, double y, double z) {
        ServerLevel level = context.getSource().getLevel();
        
        for (int i = 0; i < count; i++) {
            double spawnX, spawnZ;
        
            if (angle <= 0) {
                // Line formation: spawn along Z-axis with radius as spacing
                // Center the line around the center position
                double offsetFromCenter = i - (count - 1) / 2.0;
                spawnX = x;
                spawnZ = z + offsetFromCenter * radius;
            } else {
                // Circular formation: spawn in a circle
                double angleOffset = i * Math.toRadians(angle);
                spawnX = x + radius * Math.sin(angleOffset);
                spawnZ = z + radius * Math.cos(angleOffset);
            }
        
            double spawnY = y + 1.0;
        
            Entity entity = entityType.create(level);
            if (entity != null) {
                entity.setPos(spawnX, spawnY, spawnZ);
                entity.setDeltaMovement(0, 0, 0);
          
                // Set movement speed to 0 for LivingEntity to keep them stationary
                // while still allowing them to receive knockback and respond to physics
                if (entity instanceof LivingEntity living) {
                    living.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
                    // Set health to max for LivingEntity
                    living.setHealth(living.getMaxHealth());
                }
          
                // Mark entity as a test entity with custom NBT tag
                // getPersistentData() returns the tag directly, so we modify it in place
                entity.getPersistentData().putBoolean(TEST_ENTITY_TAG, true);
          
                level.addFreshEntity(entity);
            }
        }
        
        String positionDesc = (radius == 0) ? "at center" : "at distance " + String.format("%.1f", radius);
        context.getSource().sendSuccess(() -> Component.literal(
                "Spawned " + count + " " + entityType + " entities " + positionDesc + " (movement speed set to 0)"), true);
        
        return 1;
    }
}
