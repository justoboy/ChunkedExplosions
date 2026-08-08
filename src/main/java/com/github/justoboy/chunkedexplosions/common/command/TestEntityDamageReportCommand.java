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
 * Command to report health of test entities spawned by sptestentity.
 *
 * Usage: /chunkedexplosions testentitydamage
 */
public class TestEntityDamageReportCommand {

    /** NBT tag key for marking test entities */
    private static final String TEST_ENTITY_TAG = "chunkedexplosions_test_entity";

    static {
        CommandComments.addComment("testentitydamage", "Report health of all spawned test entities. Usage: testentitydamage");
    }

    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext buildContext) {
        return Commands.literal("testentitydamage")
                .executes(TestEntityDamageReportCommand::reportDamage);
    }

    private static int reportDamage(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();

        context.getSource().sendSuccess(() -> Component.literal("=== Test Entity Health Report ==="), false);
        context.getSource().sendSuccess(() -> Component.literal(""), false);

        int[] count = {0};
        List<String> entityInfos = new ArrayList<>();

        for (Entity entity : level.getAllEntities()) {
            // Only report entities marked as test entities
            if (entity.isAlive() && entity.getPersistentData().getBoolean(TEST_ENTITY_TAG)) {
                String entityType = entity.getType().toString();
                float health = entity instanceof net.minecraft.world.entity.LivingEntity living ? living.getHealth() : -1;
                entityInfos.add(String.format("  %s: %s [ID=%d, Health=%.1f]", 
                        entityType, entity.getName().getString(), entity.getId(), health));
                count[0]++;
            }
        }

        // Sort by entity ID for consistent output
        entityInfos.sort(String::compareTo);

        for (String info : entityInfos) {
            context.getSource().sendSuccess(() -> Component.literal(info), false);
        }

        context.getSource().sendSuccess(() -> Component.literal(""), false);
        context.getSource().sendSuccess(() -> Component.literal("Total test entities: " + count[0]), false);

        return count[0];
    }
}
