package com.github.justoboy.chunkedexplosions.common.command;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to track and report entity health for testing explosion damage.
 */
public class TestEntityDamageCommand {

    static {
        CommandComments.addComment("testentitydamage", "Report health of all entities of a specified type for damage verification.");
    }

    // Track initial health for damage comparison
    private static final List<EntityHealthRecord> initialHealthRecords = new ArrayList<>();

    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext buildContext) {
        return Commands.literal("testentitydamage")
                .executes(TestEntityDamageCommand::reportAllEntities);
    }

    private static int reportAllEntities(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        
        context.getSource().sendSuccess(() -> Component.literal("=== Entity Health Report ==="), false);
        context.getSource().sendSuccess(() -> Component.literal(""), false);
        
        int count = 0;
        
        for (Entity entity : level.getAllEntities()) {
            if (entity.isAlive()) {
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
        context.getSource().sendSuccess(() -> Component.literal("Total alive entities: %d".formatted(finalCount)), false);
        
        return 1;
    }
    
    private static float getHealth(Entity entity) {
        if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
            return living.getHealth();
        }
        return 0;
    }

    /**
     * Helper record for tracking entity health.
     */
    private record EntityHealthRecord(int entityId, float health, String entityType) {}
}
