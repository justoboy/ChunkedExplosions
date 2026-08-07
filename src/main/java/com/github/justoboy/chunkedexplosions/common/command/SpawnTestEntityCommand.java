package com.github.justoboy.chunkedexplosions.common.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command to spawn test entities at precise positions for explosion testing.
 * All spawned entities have NoAI:1b applied automatically to ensure they stay perfectly still.
 *
 * Usage: /chunkedexplosions sptestentity <entity> <count> <radius> <angle> [position]
 *        /chunkedexplosions sptestentity damageReport
 *
 * Examples:
 * /chunkedexplosions sptestentity iron_golem 8 4 45     - 8 golems in circle, 4 blocks away
 * /chunkedexplosions sptestentity iron_golem 5 2 0 0 64 0  - 5 golems at world origin
 * /chunkedexplosions sptestentity damageReport          - Report health of spawned test entities
 */
public class SpawnTestEntityCommand {

    private static final Logger LOGGER = LogManager.getLogger();
    
    /** NBT tag key for marking test entities */
    private static final String TEST_ENTITY_TAG = "chunkedexplosions_test_entity";

    static {
        CommandComments.addComment("sptestentity", "Spawn stationary test entities with NoAI:1b. Usage: sptestentity <entity> <count> <radius> <angle> [position] or sptestentity damageReport");
    }

    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext buildContext) {
        return Commands.literal("sptestentity")
                .then(Commands.literal("damageReport")
                        .executes(SpawnTestEntityCommand::reportDamage))
                .then(Commands.argument("entity", StringArgumentType.word())
                        .suggests(SuggestionProviders::entitySuggestions)
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(0.1, 64.0))
                                        .then(Commands.argument("angle", DoubleArgumentType.doubleArg(0, 360))
                                                .then(Commands.argument("position", Vec3Argument.vec3())
                                                        .executes(SpawnTestEntityCommand::spawnTestEntitiesAtPos)
                                                )
                                                .executes(SpawnTestEntityCommand::spawnTestEntities)
                                        )
                                )
                        )
                );
    }

    private static int spawnTestEntities(CommandContext<CommandSourceStack> context) {
        String entityType = StringArgumentType.getString(context, "entity");
        int count = IntegerArgumentType.getInteger(context, "count");
        double radius = DoubleArgumentType.getDouble(context, "radius");
        double angle = DoubleArgumentType.getDouble(context, "angle");
        
        Vec3 centerPos = context.getSource().getPosition();
        
        return spawnAtPosition(context, entityType, count, radius, angle, centerPos.x, centerPos.y, centerPos.z);
    }

    private static int spawnTestEntitiesAtPos(CommandContext<CommandSourceStack> context) {
        String entityType = StringArgumentType.getString(context, "entity");
        int count = IntegerArgumentType.getInteger(context, "count");
        double radius = DoubleArgumentType.getDouble(context, "radius");
        double angle = DoubleArgumentType.getDouble(context, "angle");
        Vec3 centerPos = Vec3Argument.getVec3(context, "position");
        
        return spawnAtPosition(context, entityType, count, radius, angle, centerPos.x, centerPos.y, centerPos.z);
    }
    
    private static int spawnAtPosition(CommandContext<CommandSourceStack> context, String entityType, int count, double radius, double angle, double x, double y, double z) {
        ServerLevel level = context.getSource().getLevel();
        
        ResourceLocation entityKey = ResourceLocation.tryParse(entityType);
        if (entityKey == null) {
            context.getSource().sendFailure(Component.literal("Invalid entity type: " + entityType));
            return 0;
        }
        
        EntityType<?> entityTypeObj = BuiltInRegistries.ENTITY_TYPE.getOptional(entityKey).orElse(null);
        if (entityTypeObj == null) {
            context.getSource().sendFailure(Component.literal("Unknown entity type: " + entityType));
            return 0;
        }
        
        List<Entity> spawnedEntities = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            double angleOffset = angle > 0 ? i * Math.toRadians(angle) : 0;
            
            double spawnX = x + radius * Math.sin(angleOffset);
            double spawnZ = z + radius * Math.cos(angleOffset);
            double spawnY = y + 1.0;
            
            Entity entity = entityTypeObj.create(level);
            if (entity != null) {
                entity.setPos(spawnX, spawnY, spawnZ);
                entity.setDeltaMovement(0, 0, 0);
                
                // Apply NoAI to Mob entities (iron golems, zombies, etc.)
                if (entity instanceof Mob mob) {
                    mob.setNoAi(true);
                }
                
                // Set health to max for LivingEntity
                if (entity instanceof LivingEntity living) {
                    living.setHealth(living.getMaxHealth());
                }
                
                // Mark entity as a test entity with custom NBT tag
                // getPersistentData() returns the tag directly, so we modify it in place
                entity.getPersistentData().putBoolean(TEST_ENTITY_TAG, true);
                
                level.addFreshEntity(entity);
                spawnedEntities.add(entity);
            }
        }
        
        // Also run command to ensure NoAI:1b is set for all spawned entities
        // Use exact entity IDs for precise targeting instead of limit/nearest which could match old entities
        if (!spawnedEntities.isEmpty()) {
            String idSelector = spawnedEntities.stream()
                    .map(Entity::getId)
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            String noAiCommand = "data merge entity @e[id=" + idSelector + "] {NoAI:1b}";
            
            try {
                level.getServer().getCommands().performPrefixedCommand(context.getSource(), noAiCommand);
            } catch (Exception e) {
                LOGGER.warn("Failed to apply NoAI tag via command: {}", e.getMessage());
            }
        }
        
        String positionDesc = (radius == 0) ? "at center" : "at distance " + String.format("%.1f", radius);
        context.getSource().sendSuccess(() -> Component.literal(
                "Spawned " + count + " " + entityType + " entities " + positionDesc + " (NoAI:1b applied)"), true);
        
        if (!spawnedEntities.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("Entity IDs: " + getEntityIds(spawnedEntities)), true);
        }
        
        return 1;
    }
    
    private static int reportDamage(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        
        context.getSource().sendSuccess(() -> Component.literal("=== Test Entity Health Report ==="), false);
        context.getSource().sendSuccess(() -> Component.literal(""), false);
        
        int count = 0;
        
        for (Entity entity : level.getAllEntities()) {
            // Only report entities marked as test entities
            if (entity.isAlive() && entity.getPersistentData().getBoolean(TEST_ENTITY_TAG)) {
                String entityType = entity.getType().toString();
                String entityName = entity.getName().getString();
                float health = getHealth(entity);
                
                context.getSource().sendSuccess(() -> Component.literal(
                        "  %s: %s [ID=%d, Health=%.1f]".formatted(
                                entityType, entityName, entity.getId(), health)), false);
                count++;
            }
        }
        
        context.getSource().sendSuccess(() -> Component.literal(""), false);
        final int finalCount = count;
        if (finalCount == 0) {
            context.getSource().sendSuccess(() -> Component.literal("No test entities found. Use sptestentity to spawn test entities first."), false);
        } else {
            context.getSource().sendSuccess(() -> Component.literal("Total test entities: %d".formatted(finalCount)), false);
        }
        
        return 1;
    }
    
    private static float getHealth(Entity entity) {
        if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
            return living.getHealth();
        }
        return 0;
    }

    private static String getEntityIds(List<Entity> entities) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < entities.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(entities.get(i).getId());
        }
        sb.append("]");
        return sb.toString();
    }
}
