# ChunkedExplosions Developer Guide

This guide provides comprehensive documentation for developers who want to understand, modify, or extend the ChunkedExplosions mod.

## Table of Contents

1. [Getting Started](#getting-started)
2. [Architecture Overview](#architecture-overview)
3. [Core Components](#core-components)
4. [Explosion Processing Pipeline](#explosion-processing-pipeline)
5. [Timing Modes Deep Dive](#timing-modes-deep-dive)
6. [Command System](#command-system)
7. [Extending the Mod](#extending-the-mod)
8. [Debugging and Testing](#debugging-and-testing)
9. [Performance Considerations](#performance-considerations)
10. [Code Conventions](#code-conventions)

---

## Getting Started

### Prerequisites

- Java 17 or higher
- IntelliJ IDEA or VS Code with Java extensions
- Gradle 7.x or higher
- Minecraft Forge 1.20.1 (47.4.2)

### Building the Project

```bash
# Clone the repository
git clone https://github.com/justoboy/chunkedexplosions.git
cd chunkedexplosions

# Build the mod
./gradlew build

# Run in development environment
./gradlew runClient
```

### Project Structure

```
src/main/java/com/github/justoboy/chunkedexplosions/
├── ChunkedExplosions.java          # Main mod entry point
├── core/
│   ├── ModCommands.java            # Forge event listener for command registration
│   └── ModConfig.java              # Configuration management with ForgeConfigSpec
├── common/
│   ├── command/                    # Command implementations
│   │   ├── ChunkedExplosionsCommand.java  # Main command router
│   │   ├── CommandComments.java    # Centralized command descriptions
│   │   ├── SuggestionProviders.java       # Tab completion providers
│   │   ├── HelpCommand.java        # Interactive help system
│   │   ├── EnableCommand.java      # Feature toggle command
│   │   ├── *TimingCommand.java     # Timing configuration commands
│   │   ├── *Command.java           # Performance configuration commands
│   │   └── ...                     # Test and benchmark commands
│   └── world/level/                # Explosion processing logic
│       ├── ExplosionProcessor.java # Dual-queue management
│       ├── ExplosionState.java     # Individual explosion state
│       ├── BlockDestroyer.java     # Block destruction handler
│       ├── ChunkedExplosion.java   # Legacy wrapper (deprecated)
│       └── EntityInfo.java         # Pre-calculated entity data
├── iduck/                          # Duck typing interfaces
│   └── world/level/
│       └── IExplosionDuck.java     # Access to vanilla Explosion internals
└── mixin/                          # SpongePowered mixins
    └── world/level/
        └── ExplosionMixin.java     # Mixin implementation for IExplosionDuck
```

---

## Architecture Overview

### The Problem

Vanilla Minecraft explosions process all blocks and entity effects instantaneously in a single tick. This causes:

- **TPS Drops**: Large explosions can take several milliseconds, causing server lag
- **Cascading Explosions**: TNT cannons with dozens of explosions cause massive lag spikes
- **Unpredictable Performance**: No control over processing distribution

### The Solution

ChunkedExplosions transforms instant explosions into controlled, tick-by-tick processes:

1. **Interception**: Catch explosion events before they execute
2. **Pre-calculation**: Determine all effects once using deterministic algorithms
3. **Queue Management**: Manage multiple explosions across ticks
4. **Progressive Processing**: Apply effects over multiple ticks
5. **Finalization**: Complete remaining effects when done

### Key Design Decisions

| Decision | Rationale |
|----------|----------|
| Dual-queue system | Separates pre-calculation from processing for better throughput |
| Per-dimension processing | Prevents cross-dimensional desync issues |
| Deterministic ray-casting | Ensures reproducible results across runs |
| Configurable timing modes | Allows players to tune behavior for their needs |
| Duck interface pattern | Cleanly accesses vanilla Explosion internals |

---

## Core Components

### ChunkedExplosions (Main Mod Class)

**File**: [`ChunkedExplosions.java`](../src/main/java/com/github/justoboy/chunkedexplosions/ChunkedExplosions.java)

**Responsibilities**:
- Mod initialization and lifecycle management
- Event bus registration
- Explosion event interception
- Server tick processing coordination

**Key Methods**:

```java
// Constructor - called when mod is loaded
public ChunkedExplosions(FMLJavaModLoadingContext context) {
    INSTANCE = this;                                    // Store instance for global access
    explosionProcessor = new ExplosionProcessor();      // Create processor
    MinecraftForge.EVENT_BUS.register(this);            // Register for events
    MinecraftForge.EVENT_BUS.addListener(this::onExplosionStart);
    context.registerConfig(ModConfig.Type.COMMON, ModConfig.CONFIG_SPEC);
}

// Called when any explosion starts
private void onExplosionStart(ExplosionEvent.Start event) {
    if (ModConfig.getEnable()) {
        // Create ExplosionState from vanilla explosion
        // Add to awaiting queue
        // Cancel original explosion
        event.setCanceled(true);
    }
}

// Called every server tick
@SubscribeEvent
public void onServerTick(TickEvent.ServerTickEvent event) {
    if (event.phase == TickEvent.Phase.END) {
        // Process all dimensions
        explosionProcessor.onServerTick(event.getServer());
    }
}
```

### ExplosionProcessor

**File**: [`ExplosionProcessor.java`](../src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/ExplosionProcessor.java)

**Responsibilities**:
- Manages awaiting and active queues
- Coordinates per-dimension processing
- Enforces global limits (maxBlocksPerTick)
- Applies timing effects at appropriate stages

**Queue Flow**:

```
┌─────────────────────────────────────────────────────────────┐
│                    Server Tick                               │
├─────────────────────────────────────────────────────────────┤
│ 1. Reset blocksDestroyedThisTick                            │
│ 2. For each dimension:                                       │
│    a. tryMoveToActiveQueueForDimension()                    │
│       - While activeQueue < explosionsPerTick:              │
│         - Pop from awaiting queue                           │
│         - Skip if wrong dimension                           │
│         - Pre-calculate explosion                           │
│         - Apply START timing effects                        │
│         - Add to active queue                               │
│    b. processActiveQueueForDimension()                      │
│       - For each explosion in active queue:                 │
│         - Skip if wrong dimension                           │
│         - Process tick (destroy blocks)                     │
│         - Apply SPREAD effects                              │
│         - Mark complete if done                             │
│       - Apply END timing effects to completed               │
│       - Remove completed explosions                         │
└─────────────────────────────────────────────────────────────┘
```

### ExplosionState

**File**: [`ExplosionState.java`](../src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/ExplosionState.java)

**Responsibilities**:
- Encapsulates all data for a single explosion
- Performs ray-casting for block determination
- Pre-calculates entity effects
- Handles per-tick processing
- Manages timing mode applications

**Data Organization**:

```java
public class ExplosionState {
    // Immutable Data (set once, never changed)
    private final Explosion originalExplosion;
    private final Level level;
    private final Entity source;
    private final Vec3 position;
    private final float radius;
    private final boolean fire;
    private final Explosion.BlockInteraction blockInteraction;

    // Pre-calculated Data (computed once)
    private Set<BlockPos> blocksToDestroy;
    private List<BlockPos> blocksList;
    private List<EntityInfo> affectedEntities;
    private boolean preCalculationComplete;

    // Processing State (updated during processing)
    private int currentBlockIndex;
    private int blocksDestroyed;
    private final Set<Entity> damagedEntities;
    private final Set<Entity> knockedBackEntities;
    private final Map<Entity, Float> accumulatedDamage;
    private final Map<Entity, Vec3> accumulatedKnockback;

    // Configuration (synced from ModConfig)
    private ModConfig.Timing damageTiming;
    private ModConfig.Timing knockbackTiming;
    // ... etc
}
```

### BlockDestroyer

**File**: [`BlockDestroyer.java`](../src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/BlockDestroyer.java)

**Responsibilities**:
- Handles individual block destruction
- Calculates and spawns loot drops
- Places fire if enabled
- Respects block interaction modes

**Block Interaction Modes**:

| Mode | Behavior |
|------|----------|
| KEEP | Blocks are not destroyed (used for testing) |
| DESTROY | Blocks are destroyed with normal drops |
| DESTROY_WITH_DECAY | Blocks destroyed with decay based on distance |

---

## Explosion Processing Pipeline

### Phase 1: Interception

```java
// In ChunkedExplosions.onExplosionStart()
Explosion explosion = event.getExplosion();
Level level = event.getLevel();

if (level instanceof ServerLevel serverLevel) {
    // Create new ExplosionState (copies explosion data)
    ExplosionState state = new ExplosionState(explosion);
    
    // Add to awaiting queue
    explosionProcessor.addExplosion(serverLevel, state);
    
    // Cancel the original explosion
    event.setCanceled(true);
}
```

### Phase 2: Pre-calculation

**Ray-Casting Algorithm** (matches vanilla exactly):

```java
private Set<BlockPos> performRayCasting() {
    Set<BlockPos> result = Sets.newHashSet();
    int gridSize = 16;  // 16x16x16 grid
    
    // Use level's random for determinism
    RandomSource random = this.level.random;
    
    // Iterate surface of 16x16x16 cube
    for (int xIndex = 0; xIndex < gridSize; xIndex++) {
        for (int yIndex = 0; yIndex < gridSize; yIndex++) {
            for (int zIndex = 0; zIndex < gridSize; zIndex++) {
                // Only process surface points
                if (xIndex == 0 || xIndex == gridSize - 1 ||
                    yIndex == 0 || yIndex == gridSize - 1 ||
                    zIndex == 0 || zIndex == gridSize - 1) {
                    
                    // Calculate normalized direction
                    double normalizedX = (double)xIndex / 15.0F * 2.0F - 1.0F;
                    double normalizedY = (double)yIndex / 15.0F * 2.0F - 1.0F;
                    double normalizedZ = (double)zIndex / 15.0F * 2.0F - 1.0F;
                    
                    // Normalize to unit length
                    double distance = Math.sqrt(
                        normalizedX * normalizedX +
                        normalizedY * normalizedY +
                        normalizedZ * normalizedZ);
                    
                    normalizedX /= distance;
                    normalizedY /= distance;
                    normalizedZ /= distance;
                    
                    // Generate blast strength
                    float blastStrength = radius * (0.7f + random.nextFloat() * 0.6f);
                    
                    // March ray along direction
                    while (blastStrength > 0.0f) {
                        // Check block at current position
                        // Calculate resistance
                        // Reduce blast strength
                        // Advance position by 0.3 blocks
                    }
                }
            }
        }
    }
    return result;
}
```

**Entity Pre-calculation**:

```java
private List<EntityInfo> preCalculateEntityEffects() {
    List<EntityInfo> entities = new ObjectArrayList<>();
    
    // Calculate bounding box (2x radius)
    float effectiveRadius = radius * 2.0f;
    AABB blastBox = new AABB(...);
    
    // Get all entities in box
    List<Entity> affectedEntities = level.getEntities(source, blastBox);
    
    for (Entity entity : affectedEntities) {
        if (entity.ignoreExplosion()) continue;
        
        // Calculate distance (normalized)
        double distance = sqrt(entity.distanceToSqr(center)) / effectiveRadius;
        if (distance > 1.0) continue;
        
        // Calculate visibility (ray-cast to entity)
        double visibility = Explosion.getSeenPercent(center, entity);
        
        // Calculate impact factor
        double impactFactor = (1.0 - distance) * visibility;
        
        // Calculate damage
        float damage = (float) ((int) ((impactFactor * impactFactor + impactFactor) 
                            / 2.0 * 7.0 * radius + 1));
        
        // Calculate knockback vector
        Vec3 knockbackVector = calculateKnockbackVector(entity, center);
        
        entities.add(new EntityInfo(entity, distance, visibility, 
                     impactFactor, damage, knockbackVector));
    }
    return entities;
}
```

### Phase 3: Processing

```java
public boolean processTick(ServerLevel serverLevel) {
    // Ensure pre-calculation is complete
    if (!preCalculationComplete) {
        preCalculate();
    }
    
    // Process up to blocksPerExplosionTick blocks
    int blocksThisTick = 0;
    while ((blocksPerExplosionTick == 0 || blocksThisTick < blocksPerExplosionTick)
           && currentBlockIndex < totalBlocks) {
        
        BlockPos blockPos = getCurrentBlock();
        destroyBlock(serverLevel, blockPos);
        
        // Accumulate SPREAD effects
        accumulateSpreadEffects();
        accumulateParticleEffects();
        
        currentBlockIndex++;
        blocksThisTick++;
    }
    
    // Apply accumulated SPREAD effects
    applySpreadEffects(serverLevel);
    
    // Check completion
    return currentBlockIndex >= totalBlocks;
}
```

---

## Timing Modes Deep Dive

### START Mode

Effects applied immediately when explosion moves to active queue.

```
Timeline: [START EFFECTS]----[Process Blocks]----[END]
```

**Use Case**: Instant feedback, vanilla-like feel

### END Mode

Effects applied only after all blocks are destroyed.

```
Timeline: [Process Blocks]----[END EFFECTS]
```

**Use Case**: Delayed feedback, dramatic effect

### START_END Mode

Effects split 50/50 between start and end.

```
Timeline: [50% EFFECTS]----[Process Blocks]----[50% EFFECTS]
```

**With soundVolumeSplit=true**:
- Start: 50% volume
- End: 50% volume

**With soundVolumeSplit=false**:
- Start: 100% volume
- End: 100% volume (doubled sound)

### SPREAD Mode

Effects accumulated per block and applied once per tick.

```
Tick 1: Destroy blocks 1-16
  - Accumulate 16/total damage
  - Apply accumulated damage
Tick 2: Destroy blocks 17-32
  - Accumulate 16/total damage
  - Apply accumulated damage
...
```

**Implementation Details**:

```java
private void accumulateSpreadEffects() {
    if (damageTiming != ModConfig.Timing.SPREAD) return;
    
    float damagePerBlock = 1.0F / totalBlocks;
    
    for (EntityInfo entityInfo : affectedEntities) {
        float entityDamage = entityInfo.getDamage() * damagePerBlock;
        accumulatedDamage.merge(entity, entityDamage, Float::sum);
    }
}

private void applySpreadEffects(ServerLevel serverLevel) {
    if (damageTiming == ModConfig.Timing.SPREAD && !accumulatedDamage.isEmpty()) {
        DamageSource damageSource = ...;
        for (Map.Entry<Entity, Float> entry : accumulatedDamage.entrySet()) {
            entry.getKey().hurt(damageSource, entry.getValue());
        }
        accumulatedDamage.clear();
    }
}
```

---

## Command System

### Command Registration Pattern

All commands follow this pattern:

```java
public class MyCommand {
    static {
        // Register description
        CommandComments.addComment("mycommand", "Description here.");
    }
    
    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext ctx) {
        return Commands.literal("mycommand")
            .then(Commands.argument("arg", Type)
                .executes(context -> execute(context, value)));
    }
    
    private static int execute(CommandContext<CommandSourceStack> context, Type value) {
        // Implementation
        return 1;
    }
}
```

### Main Command Router

[`ChunkedExplosionsCommand.java`](../src/main/java/com/github/justoboy/chunkedexplosions/common/command/ChunkedExplosionsCommand.java) registers all subcommands:

```java
public static void register(CommandDispatcher<CommandSourceStack> dispatcher, ...) {
    dispatcher.register(
        Commands.literal("chunkedexplosions")
            .then(EnableCommand.register(ctx))
            .then(HelpCommand.register(ctx))
            .then(ExplosionsPerTickCommand.register(ctx))
            // ... all other commands
    );
}
```

### Help System

The help system provides both overview and detailed help:

```java
// Overview: /chunkedexplosions help
/chunkedexplosions help: enable - Enable or disable chunked explosions
/chunkedexplosions help: damageTiming - Set damage timing mode
...

// Detailed: /chunkedexplosions help <command>
/chunkedexplosions help damageTiming
Usage:
  /chunkedexplosions damageTiming

Description: Set damage timing mode

Timing Options:
  START     - Damage applied when explosion begins (100%)
  END       - Damage applied when explosion finishes (100%)
  START_END - Damage split between start and end (50% each)
  SPREAD    - Damage accumulated proportionally per block
```

---

## Extending the Mod

### Adding a New Timing Mode

1. Add to [`ModConfig.Timing`](../src/main/java/com/github/justoboy/chunkedexplosions/core/ModConfig.java:66):

```java
public enum Timing { START, END, START_END, SPREAD, NEW_MODE }
```

2. Update all timing application methods in [`ExplosionState`](../src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/ExplosionState.java):

```java
public void applyDamage(ServerLevel serverLevel) {
    if (damageTiming == ModConfig.Timing.START) {
        applyAllDamage(1.0F);
    } else if (damageTiming == ModConfig.Timing.NEW_MODE) {
        // Your new logic here
    }
    // ...
}
```

3. Update help details in [`HelpCommand`](../src/main/java/com/github/justoboy/chunkedexplosions/common/command/HelpCommand.java):

```java
HelpDetails.register("damageTiming", new HelpDetails(List.of(
    HelpDetails.Section.builder()
        .title("Timing Options:")
        .line("  NEW_MODE    - Your new timing description")
        // ...
        .build()
)));
```

### Adding a New Configuration Option

1. Add to [`ModConfig.Config`](../src/main/java/com/github/justoboy/chunkedexplosions/core/ModConfig.java):

```java
public final ForgeConfigSpec.ConfigValue<Integer> myNewOption;

Config(ForgeConfigSpec.Builder builder) {
    builder.push("general");
    myNewOption = builder.comment("Description of my new option.")
        .defineInRange("myNewOption", 100, 0, Integer.MAX_VALUE);
    // ...
}
```

2. Add getter/setter:

```java
public static int getMyNewOption() { return COMMON_CONFIG.myNewOption.get(); }
public static void setMyNewOption(int value) { COMMON_CONFIG.myNewOption.set(value); }
```

3. Create command following the command pattern above.

---

## Debugging and Testing

### Logging

The mod uses SLF4J for logging:

```java
private static final Logger LOGGER = LogUtils.getLogger();

LOGGER.debug("Debug message");
LOGGER.info("Info message");
LOGGER.warn("Warning message");
LOGGER.error("Error message");
```

### Debug Logs

Key log messages to watch:

- `EXPLOSION_STATE_CONSTRUCTOR`: New explosion created
- `EXPLOSION_RAYCAST_START/END`: Ray-casting progress
- `EXPLOSION_TICK_START/END`: Tick processing progress
- `EXPLOSION_BLOCK_DESTROYED`: Block destruction (verbose)

### Test Commands

| Command | Purpose |
|---------|---------|
| `/chunkedexplosions testcube` | Create uniform block test environment |
| `/chunkedexplosions spawnexplosion` | Spawn explosion at player position |
| `/chunkedexplosions explosionstats` | View queue statistics |
| `/chunkedexplosions sptestentity` | Spawn test entities |

### Performance Testing

Use benchmark commands:

```
/chunkedexplosions benchmark          # Run benchmark
/chunkedexplosions comparetiming      # Compare timing modes
```

---

## Performance Considerations

### Memory Usage

- Each ExplosionState: ~5-10 KB (depending on block count)
- Queue overhead: Minimal (ArrayDeque)
- EntityInfo: ~64 bytes per entity

### CPU Usage

- Ray-casting: O(gridSize³) = O(4096) operations per explosion
- Per-tick processing: O(blocksPerExplosionTick) per explosion
- Entity effects: O(affectedEntities) per explosion

### Optimization Tips

1. **Lower blocksPerExplosionTick**: Reduces per-tick load
2. **Lower explosionsPerTick**: Reduces queue processing load
3. **Use START_END instead of SPREAD**: Less accumulation overhead
4. **Disable particleSplit**: Reduces particle calculations

### Bottleneck Analysis

Common bottlenecks:

1. **Ray-casting**: Use deterministic but efficient algorithms
2. **Loot calculation**: BlockDestroyer uses vanilla loot system
3. **Entity iteration**: Pre-calculate to avoid re-computation
4. **Block updates**: Batch updates where possible

---

## Code Conventions

### Naming Conventions

- **Classes**: PascalCase (`ExplosionState`)
- **Methods**: camelCase (`processTick()`)
- **Fields**: camelCase with prefix for clarity (`blocksToDestroy`)
- **Constants**: UPPER_SNAKE_CASE (`MODID`)
- **Private fields**: No special prefix (unlike some conventions)

### Comment Style

- **Class-level**: Block comment with description
- **Methods**: Javadoc-style `/** ... */`
- **Inline**: Single-line `//` for explanations

### Code Organization

Within classes:

1. Static fields
2. Instance fields
3. Constructor
4. Public methods
5. Private methods
6. Inner classes/interfaces

### Error Handling

- Use assertions for invariants
- Log warnings for recoverable errors
- Return null for optional results (document clearly)
- Never throw unchecked exceptions in event handlers

---

## Appendix: API Reference

### Key Classes Quick Reference

| Class | Key Methods | Purpose |
|-------|-------------|---------|
| [`ChunkedExplosions`](../src/main/java/com/github/justoboy/chunkedexplosions/ChunkedExplosions.java) | `getExplosionProcessor()` | Access processor instance |
| [`ExplosionProcessor`](../src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/ExplosionProcessor.java) | `addExplosion()`, `onServerTick()` | Queue management |
| [`ExplosionState`](../src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/ExplosionState.java) | `preCalculate()`, `processTick()` | Explosion processing |
| [`BlockDestroyer`](../src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/BlockDestroyer.java) | `destroyBlock()` | Block destruction |
| [`EntityInfo`](../src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/EntityInfo.java) | `getDamage()`, `getKnockbackVector()` | Entity effect data |
| [`IExplosionDuck`](../src/main/java/com/github/justoboy/chunkedexplosions/iduck/world/level/IExplosionDuck.java) | `chunked_getPosition()`, `chunked_getRadius()` | Access explosion data |

### Configuration Reference

| Config | Type | Default | Range |
|--------|------|---------|-------|
| enable | boolean | true | - |
| blocksPerExplosionTick | int | 16 | 0-MAX |
| explosionsPerTick | int | 1024 | 0-MAX |
| maxBlocksPerTick | int | 16384 | 0-MAX |
| cascadeSuppression | boolean | false | - |
| damageTiming | Timing | SPREAD | START/END/START_END/SPREAD |
| soundTiming | Timing | SPREAD | START/END/START_END/SPREAD |
| soundVolumeSplit | boolean | true | - |
| particleTiming | Timing | SPREAD | START/END/START_END/SPREAD |
| particleSplit | boolean | true | - |
| knockbackTiming | Timing | SPREAD | START/END/START_END/SPREAD |

---

*This developer guide is maintained by the ChunkedExplosions team. For questions or contributions, please open an issue or pull request.*
