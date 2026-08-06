package com.github.justoboy.chunkedexplosions.common.command;

import com.github.justoboy.chunkedexplosions.core.ModConfig;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Command to compare explosion block destruction between different blockPerExplosionTick settings.
 * Tests that blockPerExplosionTick=0 and blockPerExplosionTick=1 destroy the same blocks.
 */
public class CompareTimingModeExplosionCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger("CompareTimingModeExplosion");

    static {
        CommandComments.addComment("comparetimingmodeexplosion", "Compare block destruction between blockPerExplosionTick=0 and blockPerExplosionTick=1. Usage: comparetimingmodeexplosion [size] [block] [explosionRadius]");
    }

    private static final Map<String, TestState> activeTests = new ConcurrentHashMap<>();

    private static class TestState {
        Set<BlockPos> firstRunDestroyed = new HashSet<>();
        Set<BlockPos> test1OnlyPositions = new HashSet<>();
        Set<BlockPos> test2OnlyPositions = new HashSet<>();
        String results = null;
        TestPhase phase = TestPhase.BEFORE_START;
        int size;
        String blockName;
        int radius;
        int centerX, centerY, centerZ;
        int originalBlocksPerExplosionTick;
        int ticksToWait;
        public ResourceKey<Level> targetDimension;
        
        boolean isComplete() {
            return phase == TestPhase.DONE;
        }
    }

    private enum TestPhase {
        BEFORE_START(60),        
        CREATE_CUBE_1(20),
        WAIT_FOR_CUBE_1(60),     
        WAIT_FOR_EXPLOSION_1(200), 
        WAIT_FOR_RECREATE(60),   
        CREATE_CUBE_2(60),       
        WAIT_FOR_EXPLOSION_2(200), 
        COMPARING(20),           
        DONE(20);                
        
        private final int initialTicks;
        
        TestPhase(int initialTicks) {
            this.initialTicks = initialTicks;
        }
        
        public int getInitialTicks() {
            return initialTicks;
        }
    }

    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext buildContext) {
        return Commands.literal("comparetimingmodeexplosion")
                .then(Commands.argument("size", IntegerArgumentType.integer(3, 20))
                        .then(Commands.argument("block", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 10))
                                        .executes(CompareTimingModeExplosionCommand::runComparison)
                                )
                                .executes(CompareTimingModeExplosionCommand::runComparisonWithDefaultRadius)
                        )
                        .executes(CompareTimingModeExplosionCommand::runComparisonWithDefaultBlockAndRadius)
                )
                .executes(CompareTimingModeExplosionCommand::runComparisonWithDefaults);
    }

    public static void onServerTick(MinecraftServer server) {
        // Cleanly process your map using an explicit iterator to prevent ConcurrentModificationExceptions
        Iterator<Map.Entry<String, TestState>> iterator = activeTests.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, TestState> entry = iterator.next();
            TestState state = entry.getValue();

            // Dynamically grab the exact level this specific test was started in
            // Example assumes state stores a ResourceKey<Level> targetDimension property.
            // Fallback to Overworld if not specified: server.getLevel(Level.OVERWORLD)
            ServerLevel serverLevel = server.getLevel(state.targetDimension);
            if (serverLevel == null) {
                continue;
            }

            if (state.ticksToWait == state.phase.initialTicks) {
                LOGGER.info("Phase: {} ({} ticks)", state.phase, state.ticksToWait);
            }
            
            if (state.phase == TestPhase.DONE) {
                state.ticksToWait--;
                if (state.ticksToWait <= 0) {
                    LOGGER.info("Cleaning up completed test");
                    iterator.remove(); // Safe removal via iterator
                }
                continue;
            }
            
            if (state.phase == TestPhase.COMPARING) {
                state.ticksToWait--;
                if (state.ticksToWait <= 0) {
                    LOGGER.info("Transitioning: COMPARING -> DONE");
                    state.phase = TestPhase.DONE;
                    state.ticksToWait = 20;
                }
                continue;
            }
            
            if (state.ticksToWait > 0) {
                state.ticksToWait--;
                
                // CRITICAL: Only transition states if the timer hit exactly zero
                if (state.ticksToWait <= 0) {
                    if (state.phase == TestPhase.BEFORE_START) {
                        LOGGER.info("Transitioning: BEFORE_START -> CREATE_CUBE_1");
                        state.phase = TestPhase.CREATE_CUBE_1;
                        state.ticksToWait = 20;
                    } else if (state.phase == TestPhase.CREATE_CUBE_1) {
                        LOGGER.info("Transitioning: CREATE_CUBE_1 -> WAIT_FOR_CUBE_1");
                        ModConfig.setBlocksPerExplosionTick(0);
                        createTestCube(serverLevel, state.centerX, state.centerY, state.centerZ, state.size, state.blockName);
                        LOGGER.info("First cube spawned");
                        state.phase = TestPhase.WAIT_FOR_CUBE_1;
                        state.ticksToWait = 60; // This 60 ticks will now properly be respected next frame
                    } else if (state.phase == TestPhase.WAIT_FOR_CUBE_1) {
                        LOGGER.info("Transitioning: WAIT_FOR_CUBE_1 -> WAIT_FOR_EXPLOSION_1");
                        state.phase = TestPhase.WAIT_FOR_EXPLOSION_1;
                        spawnExplosionAt(serverLevel, state.centerX, state.centerY, state.centerZ, state.radius);
                        LOGGER.info("First explosion spawned (mode: blockPerExplosionTick=0)");
                        state.ticksToWait = 200;
                    } else if (state.phase == TestPhase.WAIT_FOR_EXPLOSION_1) {
                        LOGGER.info("Transitioning: WAIT_FOR_EXPLOSION_1 -> WAIT_FOR_RECREATE");
                        state.firstRunDestroyed = getDestroyedBlocks(serverLevel, state.centerX, state.centerY, state.centerZ, state.size);
                        LOGGER.info("First run complete: {} blocks destroyed", state.firstRunDestroyed.size());
                        state.phase = TestPhase.WAIT_FOR_RECREATE;
                        state.ticksToWait = 60;
                    } else if (state.phase == TestPhase.WAIT_FOR_RECREATE) {
                        LOGGER.info("Transitioning: WAIT_FOR_RECREATE -> CREATE_CUBE_2");
                        createTestCube(serverLevel, state.centerX, state.centerY, state.centerZ, state.size, state.blockName);
                        LOGGER.info("Second cube spawned");
                        ModConfig.setBlocksPerExplosionTick(1);
                        state.phase = TestPhase.CREATE_CUBE_2;
                        state.ticksToWait = 60;
                    } else if (state.phase == TestPhase.CREATE_CUBE_2) {
                        LOGGER.info("Transitioning: CREATE_CUBE_2 -> WAIT_FOR_EXPLOSION_2");
                        state.phase = TestPhase.WAIT_FOR_EXPLOSION_2;
                        spawnExplosionAt(serverLevel, state.centerX, state.centerY, state.centerZ, state.radius);
                        LOGGER.info("Second explosion spawned (mode: blockPerExplosionTick=1)");
                        state.ticksToWait = 200;
                    } else if (state.phase == TestPhase.WAIT_FOR_EXPLOSION_2) {
                        LOGGER.info("Transitioning: WAIT_FOR_EXPLOSION_2 -> COMPARING");
                        Set<BlockPos> secondRunDestroyed = getDestroyedBlocks(serverLevel, state.centerX, state.centerY, state.centerZ, state.size);
                        LOGGER.info("Second run complete: {} blocks destroyed", secondRunDestroyed.size());
                        
                        Set<BlockPos> common = new HashSet<>(state.firstRunDestroyed);
                        common.retainAll(secondRunDestroyed);

                        Set<BlockPos> onlyInTest1 = new HashSet<>(state.firstRunDestroyed);
                        onlyInTest1.removeAll(secondRunDestroyed);

                        Set<BlockPos> onlyInTest2 = new HashSet<>(secondRunDestroyed);
                        onlyInTest2.removeAll(state.firstRunDestroyed);

                        int totalTest1 = state.firstRunDestroyed.size();
                        int totalTest2 = secondRunDestroyed.size();
                        int overlap = common.size();

                        double matchPercentage = (totalTest1 > 0) ? (100.0 * overlap / totalTest1) : 0;

                        state.results = String.format(
                                "=== Comparison Results ===\n" +
                                "  blockPerExplosionTick=0 destroyed: %d blocks\n" +
                                "  blockPerExplosionTick=1 destroyed: %d blocks\n" +
                                "  Common blocks: %d\n" +
                                "  Only in test 1 (mode 0): %d\n" +
                                "  Only in test 2 (mode 1): %d\n" +
                                "\n" +
                                "%s\n" +
                                "  Match: %.1f%%",
                                totalTest1, totalTest2, overlap,
                                onlyInTest1.size(), onlyInTest2.size(),
                                (state.firstRunDestroyed.equals(secondRunDestroyed)) ? 
                                    "SUCCESS: Both settings destroyed IDENTICAL blocks!" :
                                    ((totalTest1 == totalTest2) ? "PARTIAL: Same count but different positions" : "MISMATCH: Different number of blocks destroyed"),
                                matchPercentage
                        );
                        
                        state.test1OnlyPositions = onlyInTest1;
                        state.test2OnlyPositions = onlyInTest2;
                        
                        state.phase = TestPhase.COMPARING;
                        state.ticksToWait = 20;
                    }
                }
            }
        }
    }

    private static String getPlayerContextKey(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return "player_" + player.getUUID().toString();
        }
        return "console";
    }

    private static int runComparisonWithDefaults(CommandContext<CommandSourceStack> context) {
        return runComparison(context, 5, "dirt", 4);
    }

    private static int runComparisonWithDefaultBlockAndRadius(CommandContext<CommandSourceStack> context) {
        int size = IntegerArgumentType.getInteger(context, "size");
        return runComparison(context, size, "dirt", 4);
    }

    private static int runComparisonWithDefaultRadius(CommandContext<CommandSourceStack> context) {
        int size = IntegerArgumentType.getInteger(context, "size");
        String blockName = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "block");
        return runComparison(context, size, blockName, 4);
    }

    private static int runComparison(CommandContext<CommandSourceStack> context) {
        int size = IntegerArgumentType.getInteger(context, "size");
        String blockName = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "block");
        int radius = IntegerArgumentType.getInteger(context, "radius");
        return runComparison(context, size, blockName, radius);
    }

    private static int runComparison(CommandContext<CommandSourceStack> context, int size, String blockName, int radius) {
        ServerLevel level = context.getSource().getLevel();
        var playerPos = context.getSource().getPosition();
        int centerX = (int) Math.floor(playerPos.x);
        int centerY = (int) Math.floor(playerPos.y);
        int centerZ = (int) Math.floor(playerPos.z);

        String testKey = getPlayerContextKey(context.getSource());
        TestState existingState = activeTests.get(testKey);
        
        if (existingState != null && !existingState.isComplete()) {
            context.getSource().sendFailure(Component.literal("A test is already in progress. Use '/chunkedexplosions comparetimingstatus' to check status."));
            return 0;
        }

        TestState finalExistingState = existingState;
        if (finalExistingState != null && finalExistingState.isComplete()) {
            if (finalExistingState.results != null) {
                context.getSource().sendSuccess(() -> Component.literal("Previous test results:"), false);
                for (String line : finalExistingState.results.split("\n")) {
                    context.getSource().sendSuccess(() -> Component.literal(line), false);
                }
                if (!finalExistingState.test1OnlyPositions.isEmpty()) {
                    context.getSource().sendSuccess(() -> Component.literal(""), false);
                    context.getSource().sendSuccess(() -> Component.literal("Blocks only in test 1 (mode 0):"), false);
                    int count = 0;
                    int remaining1 = finalExistingState.test1OnlyPositions.size() - 15;
                    for (BlockPos pos : finalExistingState.test1OnlyPositions) {
                        if (count >= 15) {
                            context.getSource().sendSuccess(() -> Component.literal(String.format("  ... and %d more", remaining1)), false);
                            break;
                        }
                        context.getSource().sendSuccess(() -> Component.literal(String.format("  (%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ())), false);
                        count++;
                    }
                }
                if (!finalExistingState.test2OnlyPositions.isEmpty()) {
                    context.getSource().sendSuccess(() -> Component.literal(""), false);
                    context.getSource().sendSuccess(() -> Component.literal("Blocks only in test 2 (mode 1):"), false);
                    int count = 0;
                    int remaining2 = finalExistingState.test2OnlyPositions.size() - 15;
                    for (BlockPos pos : finalExistingState.test2OnlyPositions) {
                        if (count >= 15) {
                            context.getSource().sendSuccess(() -> Component.literal(String.format("  ... and %d more", remaining2)), false);
                            break;
                        }
                        context.getSource().sendSuccess(() -> Component.literal(String.format("  (%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ())), false);
                        count++;
                    }
                }
            }
            activeTests.remove(testKey);
            existingState = null;
        }

        context.getSource().sendSuccess(() -> Component.literal("=== Explosion Timing Mode Comparison ==="), false);
        context.getSource().sendSuccess(() -> Component.literal("Testing block destruction with blockPerExplosionTick=0 vs blockPerExplosionTick=1"), false);
        context.getSource().sendSuccess(() -> Component.literal(""), false);
        context.getSource().sendSuccess(() -> Component.literal("Test parameters:"), false);
        context.getSource().sendSuccess(() -> Component.literal("  Cube size: %dx%dx%d".formatted(size, size, size)), false);
        context.getSource().sendSuccess(() -> Component.literal("  Block type: %s".formatted(blockName)), false);
        context.getSource().sendSuccess(() -> Component.literal("  Explosion radius: %d".formatted(radius)), false);
        context.getSource().sendSuccess(() -> Component.literal("  Center: (%d, %d, %d)".formatted(centerX, centerY, centerZ)), false);
        context.getSource().sendSuccess(() -> Component.literal(""), false);

        int originalSetting = ModConfig.getBlocksPerExplosionTick();

        TestState state = new TestState();
        state.size = size;
        state.blockName = blockName;
        state.radius = radius;
        state.centerX = centerX;
        state.centerY = centerY;
        state.centerZ = centerZ;
        state.originalBlocksPerExplosionTick = originalSetting;
        state.phase = TestPhase.BEFORE_START;
        state.ticksToWait = 60;
        state.targetDimension = level.dimension();
        activeTests.put(testKey, state);

        context.getSource().sendSuccess(() -> Component.literal(String.format("IMPORTANT: Step away from position (%d, %d, %d)!", centerX, centerY, centerZ)), true);
        context.getSource().sendSuccess(() -> Component.literal(""), false);
        context.getSource().sendSuccess(() -> Component.literal("Test starts in 3 seconds..."), false);
        context.getSource().sendSuccess(() -> Component.literal("  - A %dx%dx%d %s cube will spawn around you".formatted(size, size, size, blockName)), false);
        context.getSource().sendSuccess(() -> Component.literal("  - First explosion (blockPerExplosionTick=0) in ~9 seconds"), false);
        context.getSource().sendSuccess(() -> Component.literal("  - Second explosion (blockPerExplosionTick=1) in ~19 seconds"), false);
        context.getSource().sendSuccess(() -> Component.literal("  - Results shown in ~25 seconds"), false);
        context.getSource().sendSuccess(() -> Component.literal(""), false);
        context.getSource().sendSuccess(() -> Component.literal("Use '/chunkedexplosions comparetimingstatus' to check progress."), false);

        return 1;
    }

    private static final String STATUS_COMMAND = "comparetimingstatus";
    
    static {
        CommandComments.addComment(STATUS_COMMAND, "Check status of running timing mode comparison test.");
    }
    
    public static ArgumentBuilder<CommandSourceStack, ?> registerStatusCommand(CommandBuildContext buildContext) {
        return Commands.literal(STATUS_COMMAND)
                .executes(context -> {
                    String testKey = getPlayerContextKey(context.getSource());
                    TestState state = activeTests.get(testKey);
                    
                    if (state == null) {
                        context.getSource().sendSuccess(() -> Component.literal("No test in progress."), true);
                        return 1;
                    }
                    
                    context.getSource().sendSuccess(() -> Component.literal("=== Test Status ==="), false);
                    context.getSource().sendSuccess(() -> Component.literal("  Size: %dx%dx%d".formatted(state.size, state.size, state.size)), false);
                    context.getSource().sendSuccess(() -> Component.literal("  Block: %s".formatted(state.blockName)), false);
                    context.getSource().sendSuccess(() -> Component.literal("  Radius: %d".formatted(state.radius)), false);
                    context.getSource().sendSuccess(() -> Component.literal("  Time remaining: %.1f seconds".formatted(state.ticksToWait / 20.0)), false);
                    
                    switch (state.phase) {
                        case BEFORE_START -> {
                            context.getSource().sendSuccess(() -> Component.literal("  Phase: COUNTDOWN - Move away!"), false);
                            context.getSource().sendSuccess(() -> Component.literal("  Test starting in %.1f seconds...".formatted(state.ticksToWait / 20.0)), false);
                        }
                        case CREATE_CUBE_1 -> {
                            context.getSource().sendSuccess(() -> Component.literal("  Phase: Spawning test cube..."), false);
                        }
                        case WAIT_FOR_CUBE_1 -> {
                            context.getSource().sendSuccess(() -> Component.literal("  Phase: Waiting for cube to register..."), false);
                            context.getSource().sendSuccess(() -> Component.literal("  First explosion in %.1f seconds...".formatted(state.ticksToWait / 20.0)), false);
                        }
                        case WAIT_FOR_EXPLOSION_1 -> {
                            context.getSource().sendSuccess(() -> Component.literal("  Phase: First explosion processing (blockPerExplosionTick=0)..."), false);
                        }
                        case WAIT_FOR_RECREATE -> {
                            context.getSource().sendSuccess(() -> Component.literal("  Phase: Pause between tests..."), false);
                            context.getSource().sendSuccess(() -> Component.literal("  First run: %d blocks destroyed".formatted(state.firstRunDestroyed.size())), false);
                            context.getSource().sendSuccess(() -> Component.literal("  Next phase: %.1f seconds".formatted(state.ticksToWait / 20.0)), false);
                        }
                        case CREATE_CUBE_2 -> {
                            context.getSource().sendSuccess(() -> Component.literal("  Phase: Spawning second test cube..."), false);
                        }
                        case WAIT_FOR_EXPLOSION_2 -> {
                            context.getSource().sendSuccess(() -> Component.literal("  Phase: Second explosion processing (blockPerExplosionTick=1)..."), false);
                        }
                        case COMPARING, DONE -> {
                            if (state.results != null) {
                                context.getSource().sendSuccess(() -> Component.literal("  Phase: COMPLETE"), false);
                                for (String line : state.results.split("\n")) {
                                    context.getSource().sendSuccess(() -> Component.literal(line), false);
                                }
                            }
                        }
                    }
                    
                    return 1;
                });
    }

    private static void createTestCube(ServerLevel level, int centerX, int centerY, int centerZ, int size, String blockName) {
        BlockState blockState = parseBlockState(blockName);
        int halfSize = size / 2;
        int startX = centerX - halfSize;
        int endX = centerX + halfSize;
        int startY = centerY - halfSize;
        int endY = centerY + halfSize;
        int startZ = centerZ - halfSize;
        int endZ = centerZ + halfSize;

        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                for (int z = startZ; z <= endZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.isInWorldBounds(pos)) {
                        level.setBlock(pos, blockState, 3);
                    }
                }
            }
        }
        LOGGER.info("Spawned {}x{}x{} {} cube at {} {} {}", size, size, size, blockName, centerX, centerY, centerZ);
    }

    private static BlockState parseBlockState(String blockName) {
        if (blockName == null || blockName.isEmpty()) {
            return Blocks.DIRT.defaultBlockState();
        }
        var blockKey = ResourceLocation.tryParse(blockName);
        if (blockKey == null) {
            return Blocks.DIRT.defaultBlockState();
        }
        var block = BuiltInRegistries.BLOCK.getOptional(blockKey).orElse(Blocks.DIRT);
        return block.defaultBlockState();
    }

    private static void spawnExplosionAt(ServerLevel level, int x, int y, int z, int radius) {
        PrimedTnt tnt = new PrimedTnt(EntityType.TNT, level);
        tnt.setPos(x + 0.5, y + 0.5, z + 0.5);
        tnt.setFuse(1);
        level.addFreshEntity(tnt);
    }

    private static Set<BlockPos> getDestroyedBlocks(ServerLevel level, int centerX, int centerY, int centerZ, int size) {
        Set<BlockPos> destroyed = new HashSet<>();
        int halfSize = size / 2;

        for (int x = centerX - halfSize; x <= centerX + halfSize; x++) {
            for (int y = centerY - halfSize; y <= centerY + halfSize; y++) {
                for (int z = centerZ - halfSize; z <= centerZ + halfSize; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.isInWorldBounds(pos) && level.isEmptyBlock(pos)) {
                        destroyed.add(pos);
                    }
                }
            }
        }

        return destroyed;
    }
}
