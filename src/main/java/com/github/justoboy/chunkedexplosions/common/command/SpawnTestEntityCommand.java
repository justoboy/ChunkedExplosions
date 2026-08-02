package com.github.justoboy.chunkedexplosions.common.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to spawn test entities at precise positions for explosion testing.
 * All spawned entities have NoAI:1b applied automatically to ensure they stay perfectly still.
 * 
 * Usage: /chunkedexplosions sptestentity <entity> <count> <radius> <angle>
 * 
 * Examples:
 * /chunkedexplosions sptestentity iron_golem 8 4 45     - 8 golems in circle, 4 blocks away
 * /chunkedexplosions sptestentity iron_golem 5 2 0      - 5 golems stacked at same position, 2 blocks away
 */
public class SpawnTestEntityCommand {

    static {
        CommandComments.addComment("sptestentity", "Spawn stationary test entities with NoAI:1b. Usage: sptestentity <entity> <count> <radius> <angle>");
    }

    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext buildContext) {
        return Commands.literal("sptestentity")
                .then(Commands.argument("entity", StringArgumentType.word())
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(0.1, 64.0))
                                        .then(Commands.argument("angle", DoubleArgumentType.doubleArg(0, 360))
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
                
                level.addFreshEntity(entity);
                spawnedEntities.add(entity);
            }
        }
        
        // Also run command to ensure NoAI:1b is set for all spawned entities
        String entitySelector = String.format("type=%s,limit=%d,sort=nearest", entityType, count);
        String noAiCommand = "data merge entity @" + entitySelector + " {NoAI:1b}";
        
        try {
            level.getServer().getCommands().performPrefixedCommand(context.getSource(), noAiCommand);
        } catch (Exception e) {
            // Command might fail if entities moved, but they were spawned with 0 velocity and setNoAi(true)
        }
        
        String positionDesc = (radius == 0) ? "at center" : "at distance " + String.format("%.1f", radius);
        context.getSource().sendSuccess(() -> Component.literal(
                "Spawned " + count + " " + entityType + " entities " + positionDesc + " (NoAI:1b applied)"), true);
        
        if (!spawnedEntities.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("Entity IDs: " + getEntityIds(spawnedEntities)), true);
        }
        
        return 1;
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
