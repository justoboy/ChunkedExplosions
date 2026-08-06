# ChunkedExplosions API Reference

This document provides detailed API documentation for all public classes and methods in the ChunkedExplosions mod.

## Table of Contents

- [Core Classes](#core-classes)
- [World/Level Classes](#worldlevel-classes)
- [Command Classes](#command-classes)
- [Utility Classes](#utility-classes)
- [Interfaces](#interfaces)

---

## Core Classes

### [`ChunkedExplosions`](../src/main/java/com/github/justoboy/chunkedexplosions/ChunkedExplosions.java)

The main mod entry point. Handles initialization, event registration, and provides global access to the explosion processor.

#### Fields

| Field | Type | Description |
|-------|------|-------------|
| `MODID` | `String` | The mod identifier: `"chunkedexplosions"` |
| `INSTANCE` | `ChunkedExplosions` | Static reference to the mod instance |
| `LOGGER` | `Logger` | SLF4J logger for this class |

#### Methods

| Method | Return | Description |
|--------|--------|-------------|
| `getExplosionProcessor()` | `ExplosionProcessor` | Gets the explosion processor instance |

#### Event Handlers

| Method | Event | Description |
|--------|-------|-------------|
| `onExplosionStart(ExplosionEvent.Start)` | `ExplosionEvent.Start` | Intercepts and queues explosions |
| `onServerTick(TickEvent.ServerTickEvent)` | `TickEvent.ServerTickEvent` | Processes explosions each tick |

---

### [`ModConfig`](../src/main/java/com/github/justoboy/chunkedexplosions/core/ModConfig.java)

Manages all configuration settings for the mod.

#### Timing Enum

```java
public enum Timing {
    START,      // Effect applied immediately
    END,        // Effect applied after completion
    START_END,  // Effect split 50/50
    SPREAD      // Effect accumulated per block
}
```

#### Static Methods

| Method | Return | Description |
|--------|--------|-------------|
| `getEnable()` | `boolean` | Get enable status |
| `setEnable(boolean)` | `void` | Set enable status |
| `getBlocksPerExplosionTick()` | `int` | Get blocks per explosion per tick |
| `setBlocksPerExplosionTick(int)` | `void` | Set blocks per explosion per tick |
| `getExplosionsPerTick()` | `int` | Get explosions per tick |
| `setExplosionsPerTick(int)` | `void` | Set explosions per tick |
| `getMaxBlocksPerTick()` | `int` | Get max blocks per tick |
| `setMaxBlocksPerTick(int)` | `void` | Set max blocks per tick |
| `getMaxQueueSize()` | `int` | Get max queue size |
| `setMaxQueueSize(int)` | `void` | Set max queue size |
| `getDamageTiming()` | `Timing` | Get damage timing mode |
| `setDamageTiming(Timing)` | `void` | Set damage timing mode |
| `getSoundTiming()` | `Timing` | Get sound timing mode |
| `setSoundTiming(Timing)` | `void` | Set sound timing mode |
| `getSoundVolumeSplit()` | `boolean` | Get sound volume split setting |
| `setSoundVolumeSplit(boolean)` | `void` | Set sound volume split setting |
| `getParticleTiming()` | `Timing` | Get particle timing mode |
| `setParticleTiming(Timing)` | `void` | Set particle timing mode |
| `getParticleSplit()` | `boolean` | Get particle split setting |
| `setParticleSplit(boolean)` | `void` | Set particle split setting |
| `getKnockbackTiming()` | `Timing` | Get knockback timing mode |
| `setKnockbackTiming(Timing)` | `void` | Set knockback timing mode |
| `getCascadeSuppression()` | `boolean` | Get cascade suppression setting |
| `setCascadeSuppression(boolean)` | `void` | Set cascade suppression setting |

---

## World/Level Classes

### [`ExplosionProcessor`](../src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/ExplosionProcessor.java)

Manages the dual-queue system for processing explosions across all dimensions.

#### Fields

| Field | Type | Description |
|-------|------|-------------|
| `awaitingQueue` | `Queue<ExplosionState>` | Queue of explosions waiting pre-calculation |
| `activeQueue` | `Queue<ExplosionState>` | Queue of explosions being processed |
| `blocksDestroyedThisTick` | `int` | Counter for global block cap |

#### Methods

| Method | Return | Description |
|--------|--------|-------------|
| `addExplosion(ServerLevel, Explosion)` | `ExplosionState` | Adds a new explosion to the awaiting queue |
| `onServerTick(MinecraftServer)` | `void` | Main processing method called each tick |
| `getAwaitingQueueSize()` | `int` | Gets the awaiting queue size |
| `getActiveQueueSize()` | `int` | Gets the active queue size |
| `getTotalPendingExplosions()` | `int` | Gets total pending explosions |
| `getBlocksDestroyedThisTick()` | `int` | Gets blocks destroyed this tick |
| `getRemainingBlocksThisTick()` | `int` | Gets remaining blocks before cap |
| `isEmpty()` | `boolean` | Checks if all queues are empty |
| `clear()` | `void` | Clears all queues |

---

### [`ExplosionState`](../src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/ExplosionState.java)

Encapsulates all data and behavior for a single chunked explosion.

#### Lifecycle

1. **Creation**: Created from vanilla explosion in awaiting queue
2. **Pre-calculation**: Ray-casting and entity effects computed
3. **Processing**: Blocks destroyed over multiple ticks
4. **Completion**: Final effects applied and state removed

#### Key Methods

| Method | Return | Description |
|--------|--------|-------------|
| `preCalculate()` | `void` | Performs ray-casting and entity effect pre-calculation |
| `processTick(ServerLevel)` | `boolean` | Processes one tick of block destruction |
| `applyDamage(ServerLevel)` | `void` | Applies damage based on timing mode |
| `finalizeDamage(ServerLevel)` | `void` | Finalizes damage for END timing |
| `applyKnockback(ServerLevel)` | `void` | Applies knockback based on timing mode |
| `finalizeKnockback(ServerLevel)` | `void` | Finalizes knockback for END timing |
| `playSound()` | `void` | Plays explosion sound based on timing |
| `finalizeSound()` | `void` | Finalizes sound for END timing |
| `spawnParticles()` | `void` | Spawns particles based on timing |
| `finalizeParticles()` | `void` | Finalizes particles for END timing |
| `isComplete()` | `boolean` | Checks if explosion processing is complete |
| `getBlocksDestroyed()` | `int` | Gets count of destroyed blocks |
| `getLevel()` | `Level` | Gets the explosion's level |
| `getPosition()` | `Vec3` | Gets explosion center position |

---

### [`BlockDestroyer`](../src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/BlockDestroyer.java)

Handles block destruction and loot spawning for explosions.

#### Constructor

```java
public BlockDestroyer(
    Explosion.BlockInteraction blockInteraction,
    float radius,
    Entity source,
    boolean fire
)
```

#### Methods

| Method | Return | Description |
|--------|--------|-------------|
| `destroyBlock(ServerLevel, BlockPos)` | `void` | Destroys a single block with drops |
| `interactsWithBlocks()` | `boolean` | Checks if destroyer will destroy blocks |
| `getBlockInteraction()` | `BlockInteraction` | Gets the block interaction mode |
| `isFire()` | `boolean` | Checks if fire placement is enabled |
| `getRadius()` | `float` | Gets the explosion radius |
| `getSource()` | `Entity` | Gets the explosion source entity |

---

### [`EntityInfo`](../src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/EntityInfo.java)

Pre-calculated entity effect data for efficient processing.

#### Fields

| Field | Type | Description |
|-------|------|-------------|
| `entity` | `Entity` | The entity to affect |
| `distance` | `float` | Normalized distance from explosion |
| `visibility` | `float` | Visibility factor (0.0-1.0) |
| `impactFactor` | `float` | Combined distance/visibility impact |
| `damage` | `float` | Calculated damage value |
| `knockbackVector` | `Vec3` | Pre-calculated knockback direction |

#### Methods

| Method | Return | Description |
|--------|--------|-------------|
| `getEntity()` | `Entity` | Gets the entity |
| `getDistance()` | `float` | Gets normalized distance |
| `getVisibility()` | `float` | Gets visibility factor |
| `getImpactFactor()` | `float` | Gets impact factor |
| `getDamage()` | `float` | Gets calculated damage |
| `getKnockbackVector()` | `Vec3` | Gets knockback vector |

---

## Command Classes

### [`ChunkedExplosionsCommand`](../src/main/java/com/github/justoboy/chunkedexplosions/common/command/ChunkedExplosionsCommand.java)

Main command router that registers all subcommands.

#### Methods

| Method | Return | Description |
|--------|--------|-------------|
| `register(CommandDispatcher, CommandBuildContext)` | `void` | Registers all commands |

### [`HelpCommand`](../src/main/java/com/github/justoboy/chunkedexplosions/common/command/HelpCommand.java)

Provides help functionality for all commands.

#### Methods

| Method | Return | Description |
|--------|--------|-------------|
| `register(CommandBuildContext)` | `ArgumentBuilder` | Registers the help command |

### [`EnableCommand`](../src/main/java/com/github/justoboy/chunkedexplosions/common/command/EnableCommand.java)

Toggles chunked explosions on/off.

#### Methods

| Method | Return | Description |
|--------|--------|-------------|
| `register(CommandBuildContext)` | `ArgumentBuilder` | Registers the enable command |

### Timing Commands

All timing commands follow the same pattern:

| Command | Description |
|---------|-------------|
| [`DamageTimingCommand`](../src/main/java/com/github/justoboy/chunkedexplosions/common/command/DamageTimingCommand.java) | Configure damage timing mode |
| [`SoundTimingCommand`](../src/main/java/com/github/justoboy/chunkedexplosions/common/command/SoundTimingCommand.java) | Configure sound timing mode |
| [`ParticleTimingCommand`](../src/main/java/com/github/justoboy/chunkedexplosions/common/command/ParticleTimingCommand.java) | Configure particle timing mode |
| [`KnockbackTimingCommand`](../src/main/java/com/github/justoboy/chunkedexplosions/common/command/KnockbackTimingCommand.java) | Configure knockback timing mode |

### Performance Commands

| Command | Description |
|---------|-------------|
| [`ExplosionsPerTickCommand`](../src/main/java/com/github/justoboy/chunkedexplosions/common/command/ExplosionsPerTickCommand.java) | Configure explosions per tick |
| [`BlocksPerExplosionTickCommand`](../src/main/java/com/github/justoboy/chunkedexplosions/common/command/BlocksPerExplosionTickCommand.java) | Configure blocks per explosion per tick |
| [`MaxBlocksPerTickCommand`](../src/main/java/com/github/justoboy/chunkedexplosions/common/command/MaxBlocksPerTickCommand.java) | Configure global max blocks per tick |
| [`MaxQueueSizeCommand`](../src/main/java/com/github/justoboy/chunkedexplosions/common/command/MaxQueueSizeCommand.java) | Configure max queue size |

### Boolean Option Commands

| Command | Description |
|---------|-------------|
| [`SoundVolumeSplitCommand`](../src/main/java/com/github/justoboy/chunkedexplosions/common/command/SoundVolumeSplitCommand.java) | Configure sound volume splitting |
| [`ParticleSplitCommand`](../src/main/java/com/github/justoboy/chunkedexplosions/common/command/ParticleSplitCommand.java) | Configure particle count splitting |

### Test Commands

| Command | Description |
|---------|-------------|
| [`SpawnExplosionCommand`](../src/main/java/com/github/justoboy/chunkedexplosions/common/command/SpawnExplosionCommand.java) | Spawn test explosion |
| [`TestCubeCommand`](../src/main/java/com/github/justoboy/chunkedexplosions/common/command/TestCubeCommand.java) | Create test cube environment |
| [`TestClearCommand`](../src/main/java/com/github/justoboy/chunkedexplosions/common/command/TestClearCommand.java) | Clear test area |
| [`ExplosionStatsCommand`](../src/main/java/com/github/justoboy/chunkedexplosions/common/command/ExplosionStatsCommand.java) | Show queue statistics |
| [`SpawnTestEntityCommand`](../src/main/java/com/github/justoboy/chunkedexplosions/common/command/SpawnTestEntityCommand.java) | Spawn test entities |
| [`TestEntityDamageCommand`](../src/main/java/com/github/justoboy/chunkedexplosions/common/command/TestEntityDamageCommand.java) | Test entity damage calculations |

### Benchmark Commands

| Command | Description |
|---------|-------------|
| [`BenchmarkExplosionCommand`](../src/main/java/com/github/justoboy/chunkedexplosions/common/command/BenchmarkExplosionCommand.java) | Run benchmark tests |
| [`CompareExplosionCommand`](../src/main/java/com/github/justoboy/chunkedexplosions/common/command/CompareExplosionCommand.java) | Compare explosion behaviors |
| [`CompareTimingModeExplosionCommand`](../src/main/java/com/github/justoboy/chunkedexplosions/common/command/CompareTimingModeExplosionCommand.java) | Compare timing modes |
| [`RecordExplosionCommand`](../src/main/java/com/github/justoboy/chunkedexplosions/common/command/RecordExplosionCommand.java) | Record explosion data |

---

## Utility Classes

### [`CommandComments`](../src/main/java/com/github/justoboy/chunkedexplosions/common/command/CommandComments.java)

Centralized command descriptions for help system.

#### Methods

| Method | Return | Description |
|--------|--------|-------------|
| `addComment(String, String)` | `void` | Adds/updates command description |
| `getComment(String)` | `String` | Gets command description |

### [`SuggestionProviders`](../src/main/java/com/github/justoboy/chunkedexplosions/common/command/SuggestionProviders.java)

Provides tab completion suggestions for command arguments.

#### Methods

| Method | Return | Description |
|--------|--------|-------------|
| `timingSuggestions(...)` | `CompletableFuture<Suggestions>` | Provides timing mode suggestions |
| `boolSuggestions(...)` | `CompletableFuture<Suggestions>` | Provides boolean suggestions |
| `integerSuggestions(...)` | `CompletableFuture<Suggestions>` | Provides common integer suggestions |
| `commandSuggestions(...)` | `CompletableFuture<Suggestions>` | Provides command name suggestions |

### [`ModCommands`](../src/main/java/com/github/justoboy/chunkedexplosions/core/ModCommands.java)

Forge event listener for command registration.

#### Methods

| Method | Return | Description |
|--------|--------|-------------|
| `registerCommands(RegisterCommandsEvent)` | `void` | Registers all mod commands |

---

## Interfaces

### [`IExplosionDuck`](../src/main/java/com/github/justoboy/chunkedexplosions/iduck/world/level/IExplosionDuck.java)

Duck typing interface for accessing vanilla Explosion internals.

#### Methods

| Method | Return | Description |
|--------|--------|-------------|
| `chunked_getLevel()` | `Level` | Gets the explosion's level |
| `chunked_getSource()` | `Entity` | Gets the explosion source |
| `chunked_getX()` | `double` | Gets explosion X coordinate |
| `chunked_getY()` | `double` | Gets explosion Y coordinate |
| `chunked_getZ()` | `double` | Gets explosion Z coordinate |
| `chunked_getRadius()` | `float` | Gets explosion radius |
| `chunked_isFire()` | `boolean` | Checks if explosion creates fire |
| `chunked_getBlockInteraction()` | `BlockInteraction` | Gets block interaction mode |
| `chunked_getDamageSource()` | `DamageSource` | Gets the damage source |
| `chunked_getPosition()` | `Vec3` | Gets explosion position |
| `chunked_explode()` | `void` | Deprecated - no longer used |
| `chunked_finalize()` | `void` | Deprecated - no longer used |

---

## Mixin Classes

### [`ExplosionMixin`](../src/main/java/com/github/justoboy/chunkedexplosions/mixin/world/level/ExplosionMixin.java)

Mixin implementation that applies IExplosionDuck to vanilla Explosion.

#### Fields

Mirrors all private fields from vanilla Explosion:
- `level` - The explosion's level
- `source` - The explosion source entity
- `x, y, z` - Explosion center coordinates
- `radius` - Explosion radius
- `damageSource` - Damage source for entities
- `hitPlayers` - Player hit data
- `blockInteraction` - Block interaction mode
- `fire` - Fire creation flag

#### Implementation

All IExplosionDuck methods are implemented to return the corresponding vanilla Explosion field values.

---

## Usage Examples

### Accessing the Explosion Processor

```java
// Get the processor instance
ExplosionProcessor processor = ChunkedExplosions.getExplosionProcessor();

// Check queue status
int pending = processor.getTotalPendingExplosions();
int blocksRemaining = processor.getRemainingBlocksThisTick();

// Clear all queues
processor.clear();
```

### Reading Configuration

```java
// Read current settings
boolean enabled = ModConfig.getEnable();
int blocksPerTick = ModConfig.getBlocksPerExplosionTick();
ModConfig.Timing damageTiming = ModConfig.getDamageTiming();

// Modify settings (changes take effect immediately)
ModConfig.setEnable(false);
ModConfig.setBlocksPerExplosionTick(32);
ModConfig.setDamageTiming(ModConfig.Timing.START);
```

### Creating a Custom Command

```java
public class MyCustomCommand {
    static {
        CommandComments.addComment("mycommand", "My custom command description.");
    }
    
    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext ctx) {
        return Commands.literal("mycommand")
            .then(Commands.argument("value", IntegerArgumentType.integer())
                .executes(context -> {
                    int value = IntegerArgumentType.getInteger(context, "value");
                    // Your logic here
                    return 1;
                }));
    }
}
```

---

*This API reference is maintained by the ChunkedExplosions team. For updates, please open an issue or pull request.*
