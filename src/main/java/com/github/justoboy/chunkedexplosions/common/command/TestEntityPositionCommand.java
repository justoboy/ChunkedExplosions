package com.github.justoboy.chunkedexplosions.common.command;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to report position of test entities spawned by sptestentity.
 *
 * Usage: /chunkedexplosions testentityposition
 */
public class TestEntityPositionCommand {

    /** NBT tag key for marking test entities */
    private static final String TEST_ENTITY_TAG = "chunkedexplosions_test_entity";

    static {
        CommandComments.addComment("testentityposition", "Report position of all spawned test entities. Usage: testentityposition");
    }

    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext buildContext) {
        return Commands.literal("testentityposition")
                .executes(TestEntityPositionCommand::reportPosition);
    }

    private static int reportPosition(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();

        context.getSource().sendSuccess(() -> Component.literal("=== Test Entity Position Report ==="), false);
        context.getSource().sendSuccess(() -> Component.literal(""), false);

        int[] count = {0};
        List<String> entityInfos = new ArrayList<>();

        for (Entity entity : level.getAllEntities()) {
            // Only report entities marked as test entities
            if (entity.isAlive() && entity.getPersistentData().getBoolean(TEST_ENTITY_TAG)) {
                String entityType = entity.getType().toString();
                Vec3 pos = entity.position();
                Vec3 motion = entity.getDeltaMovement();
                entityInfos.add(String.format("  %s: %s [ID=%d, Pos=(%.3f, %.3f, %.3f), Motion=(%.3f, %.3f, %.3f)]",
                        entityType, entity.getName().getString(), entity.getId(), pos.x, pos.y, pos.z, motion.x, motion.y, motion.z));
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
