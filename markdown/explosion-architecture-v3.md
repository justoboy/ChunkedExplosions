# Chunked Explosions Mod - Architecture v3

## Dual-Queue Explosion Processing System

---

## 1. Overview

This document defines the architecture for the redesigned Chunked Explosions mod. The core innovation is a **dual-queue system** that separates explosion pre-calculation from block destruction, allowing for smooth, predictable performance even under heavy explosion loads.

### Core Principles

1. **Pre-calculate Everything First** - Ray-casting and entity calculations happen instantly when explosion is queued
2. **Throttle Block Destruction** - Only block destruction is spread across ticks
3. **Dual Queues** - Awaiting queue (pending pre-calculation) and Active queue (ready to destroy blocks)
4. **Three-Level Throttling** - Explosions per tick, blocks per explosion per tick, global block cap

---

## 2. Queue Architecture

### 2.1 Queue Structure

```
┌─────────────────────────────────────────────────────────────────────┐
│                        EXPLOSION PROCESSING                         │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌───────────────────┐         ┌───────────────────┐              │
│  │   AWAITING QUEUE  │         │    ACTIVE QUEUE   │              │
│  │  (preCalcPending) │ ──────► │ (preCalcComplete) │              │
│  ├───────────────────┤         ├───────────────────┤              │
│  │ • New explosions  │         │ • Pre-calculated  │              │
│  │ • Not calculated  │         │ • Ready to process│              │
│  │ • FIFO order      │         │ • Being processed │              │
│  └───────────────────┘         └───────────────────┘              │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.2 Queue Transitions

```
┌────────────────────────────────────────────────────────────────┐
│                    EXPLOSION LIFECYCLE                         │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  1. EXPLOSION CREATED                                          │
│     ────────────────────                                       │
│     Vanilla explosion intercepted → Cancelled → Wrapped        │
│                                                                │
│  2. AWAITING QUEUE                                             │
│     ────────────────────                                       │
│     Explosion waits here until active queue has space         │
│     No processing happens yet                                  │
│                                                                │
│  3. MOVE TO ACTIVE QUEUE                                       │
│     ────────────────────────                                   │
│     Pre-calculate ALL affected blocks (instant)               │
│     Pre-calculate ALL entity effects (instant)                │
│     Move to active queue                                       │
│                                                                │
│  4. ACTIVE QUEUE PROCESSING                                    │
│     ──────────────────────────                                 │
│     Each tick:                                                 │
│       - Process each explosion in queue                        │
│       - Destroy up to N blocks per explosion                   │
│       - Respect global block cap                               │
│                                                                │
│  5. COMPLETION                                                 │
│     ────────────                                               │
│     All blocks destroyed                                       │
│     All effects applied                                        │
│     Remove from active queue                                   │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

---

## 3. Configuration

### 3.1 Throttling Settings

| Setting                  | Default | Description                                       |
|--------------------------|---------|---------------------------------------------------|
| `explosionsPerTick`      | 1024    | Maximum explosions in active queue                |
| `blocksPerExplosionTick` | 16      | Blocks each active explosion can destroy per tick |
| `maxBlocksPerTick`       | 16384   | Global cap on total blocks destroyed per tick     |

### 3.2 Effect Timing Settings

| Setting            | Options                       | Description                                |
|--------------------|-------------------------------|--------------------------------------------|
| `damageTiming`     | START, END, SPREAD, START_END | When to apply entity damage                |
| `soundTiming`      | START, END, SPREAD, START_END | When to play explosion sound               |
| `soundVolumeSplit` | true/false                    | Split sound volume across spread/start_end |
| `particleTiming`   | START, END, SPREAD, START_END | When to spawn particles                    |
| `knockbackTiming`  | START, END, SPREAD, START_END | When to apply knockback                    |

### 3.3 Optional Settings

| Setting              | Default | Description                             |
|----------------------|---------|-----------------------------------------|
| `cascadeSuppression` | False   | Suppress falling blocks, redstone, etc. |

---

## 4. Core Components

### 4.1 ExplosionProcessor

**Responsibility:** Orchestrates the dual-queue system and tick-based processing

**Key Methods:**
- `onExplosionStart(Explosion)` - Intercept new explosions, add to awaiting queue
- `onServerTick()` - Main processing loop
- `tryMoveToActiveQueue()` - Move explosions from awaiting to active
- `processActiveQueue()` - Process all active explosions for this tick

**State:**
- `awaitingQueue: Queue<ExplosionState>` - Pending pre-calculation
- `activeQueue: Queue<ExplosionState>` - Ready to destroy blocks
- `blocksDestroyedThisTick: int` - Track against maxBlocksPerTick

### 4.2 ExplosionState

**Responsibility:** Encapsulates all data for a single explosion

**Lifecycle:**
1. Created when explosion is intercepted
2. Pre-calculated when moving to active queue
3. Processed each tick until complete
4. Removed when finished

**State (Pre-calculation Phase):**
- `original: Explosion` - Reference to vanilla explosion
- `level: Level` - World reference
- `source: Entity` - Explosion source
- `position: Vec3` - Explosion position
- `radius: float` - Explosion radius
- `blocksToDestroy: Set<BlockPos>` - Pre-calculated block positions
- `affectedEntities: List<EntityInfo>` - Pre-calculated entity data
- `preCalculationComplete: boolean`

**State (Processing Phase):**
- `currentBlockIndex: int` - Next block to destroy
- `damagedEntities: Set<Entity>` - Already damaged entities
- `knockedBackEntities: Set<Entity>` - Already knocked back entities
- `effectsComplete: boolean`

### 4.3 BlockDestroyer

**Responsibility:** Handle block destruction and drop calculation

**Key Methods:**
- `destroyBlock(BlockPos)` - Destroy a single block
- `calculateDrops(BlockPos, BlockState)` - Calculate item drops
- `applyFire(BlockPos)` - Place fire if enabled

**Features:**
- Respects block interaction mode (KEEP, DESTROY, DESTROY_WITH_DECAY)
- Calculates loot tables
- Spawns item entities
- Optional cascade suppression

### 4.4 EntityEffectApplier

**Responsibility:** Handle entity damage and knockback

**Key Methods:**
- `applyDamage(Entity, float)` - Apply damage to entity
- `applyKnockback(Entity, Vec3)` - Apply knockback to entity
- `calculateVisibility(Entity)` - Calculate line-of-sight visibility

**Features:**
- Pre-calculated visibility percentages
- Protection enchantment dampening
- Configurable damage timing (START, END, SPREAD, START_END)

### 4.5 SoundController

**Responsibility:** Handle explosion sound playback

**Key Methods:**
- `playSound(Level, Vec3, float volume)` - Play explosion sound with specified volume
- `calculateVolume(List<BlockPos>)` - Calculate volume based on blocks destroyed

**Features:**
- Volume splitting controlled by `soundVolumeSplit` config
- Configurable timing (START, END, SPREAD, START_END)
- Optional sound delay

**Volume Behavior:**
- `soundVolumeSplit = false`: Full volume at each timing point
- `soundVolumeSplit = true`: Volume split across timing points (e.g., 50% at start, 50% at end for START_END)

### 4.6 ParticleController

**Responsibility:** Handle particle spawning

**Key Methods:**
- `spawnParticles(Level, Vec3)` - Spawn explosion particles
- `spawnBlockBreakParticles(Level, BlockPos)` - Spawn block break particles

**Features:**
- Configurable timing (START, END, SPREAD, START_END)
- Emitter particles for large explosions
- Optional rate limiting

---

## 5. Processing Flow

### 5.1 Tick Processing Algorithm

```
┌────────────────────────────────────────────────────────────────┐
│                    SERVER TICK                                 │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  START TICK                                                    │
│     │                                                          │
│     ▼                                                          │
│  Reset blocksDestroyedThisTick = 0                            │
│     │                                                          │
│     ▼                                                          │
│  tryMoveToActiveQueue()                                        │
│     │                                                          │
│     ├─► While (activeQueue.size < explosionsPerTick)          │
│     │   └─► While (!awaitingQueue.isEmpty())                  │
│     │       └─► Pre-calculate explosion                       │
│     │       └─► Move to active queue                          │
│     │                                                          │
│     ▼                                                          │
│  Process Active Queue                                          │
│     │                                                          │
│     ├─► For each explosion in active queue:                   │
│     │   │                                                      │
│     │   ├─► If (blocksDestroyedThisTick >= maxBlocksPerTick)  │
│     │   │   └─► BREAK (stop processing this tick)             │
│     │   │                                                      │
│     │   ├─► Process explosion:                                 │
│     │   │   │                                                  │
│     │   │   ├─► While (blocksThisExplosion < blocksPerExplosionTick) │
│     │   │   │   └─► Destroy next block                         │
│     │   │   │   └─► blocksThisExplosion++                      │
│     │   │   │   └─► blocksDestroyedThisTick++                  │
│     │   │   │                                                  │
│     │   │   └─► If (explosion finished)                        │
│     │   │       └─► Mark for removal                           │
│     │   │                                                      │
│     │   └─► Next explosion                                     │
│     │                                                          │
│     ▼                                                          │
│  Remove Completed Explosions                                   │
│     │                                                          │
│     ├─► For each marked explosion:                            │
│     │   └─► Remove from active queue                          │
│     │                                                          │
│     ▼                                                          │
│  END TICK                                                      │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

### 5.2 Pre-Calculation Flow

```
┌────────────────────────────────────────────────────────────────┐
│                    PRE-CALCULATION                             │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  START PRE-CALCULATION                                         │
│     │                                                          │
│     ▼                                                          │
│  Initialize Deterministic RNG                                  │
│     │  (seed = source.getId() ^ System.nanoTime())            │
│     ▼                                                          │
│  Calculate Block Destruction                                   │
│     │                                                          │
│     ├─► For each ray in 16x16x16 grid surface:                │
│     │   │                                                      │
│     │   ├─► Calculate ray direction (normalized)              │
│     │   ├─► Generate random blast strength                    │
│     │   ├─► March ray until blast strength exhausted          │
│     │   │   │                                                  │
│     │   │   ├─► Get block state                               │
│     │   │   ├─► Calculate resistance                          │
│     │   │   ├─► Reduce blast strength                         │
│     │   │   ├─► If (blast strength > 0) add to blocksToDestroy │
│     │   │   └─► Advance ray position                          │
│     │   │                                                      │
│     │   └─► Next ray                                           │
│     │                                                          │
│     ▼                                                          │
│  Calculate Entity Effects                                      │
│     │                                                          │
│     ├─► Get all entities within blast radius                  │
│     │                                                          │
│     ├─► For each entity:                                      │
│     │   │                                                      │
│     │   ├─► Calculate distance from explosion                 │
│     │   ├─► Calculate visibility (getSeenPercent)             │
│     │   ├─► Calculate impact factor                           │
│     │   ├─► Calculate damage                                  │
│     │   ├─► Calculate knockback vector                        │
│     │   └─► Store in affectedEntities                         │
│     │                                                          │
│     ▼                                                          │
│  Mark Pre-Calculation Complete                                 │
│     │                                                          │
│     ▼                                                          │
│  MOVE TO ACTIVE QUEUE                                          │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

---

## 6. Effect Timing Modes

### 6.1 Damage Timing

| Mode        | Behavior                                             |
|-------------|------------------------------------------------------|
| `START`     | Apply full damage when explosion enters active queue |
| `END`       | Apply full damage when all blocks destroyed          |
| `SPREAD`    | Apply damage proportionally as blocks are destroyed  |
| `START_END` | 50% damage at start, 50% at end                      |

### 6.2 Sound Timing

| Mode        | Behavior                                                                                                                                                          |
|-------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `START`     | Play sound when explosion enters active queue                                                                                                                     |
| `END`       | Play sound when all blocks destroyed                                                                                                                              |
| `SPREAD`    | Play partial sounds as blocks are destroyed, accumulated and applied once per tick                                                                                |
| `START_END` | Play sound at start and end. Volume controlled by `soundVolumeSplit`: if false, full volume at both times; if true, split volume (e.g., 50% at start, 50% at end) |

### 6.3 Particle Timing

| Mode        | Behavior                                                                                      |
|-------------|-----------------------------------------------------------------------------------------------|
| `START`     | Spawn particles when explosion enters active queue                                            |
| `END`       | Spawn particles when all blocks destroyed                                                     |
| `SPREAD`    | Spawn particles proportionally as blocks are destroyed, accumulated and applied once per tick |
| `START_END` | 50% particles at start, 50% at end                                                            |

### 6.4 Knockback Timing

| Mode        | Behavior                                                                                      |
|-------------|-----------------------------------------------------------------------------------------------|
| `START`     | Apply knockback when explosion enters active queue                                            |
| `END`       | Apply knockback when all blocks destroyed                                                     |
| `SPREAD`    | Apply knockback proportionally as blocks are destroyed, accumulated and applied once per tick |
| `START_END` | 50% knockback at start, 50% at end                                                            |

### 6.5 Method Modes

| Mode     | Behavior                                   |
|----------|--------------------------------------------|
| `SPREAD` | Effects applied incrementally per block    |
| `ONCE`   | Full effect applied at once (START or END) |

---

## 7. Data Structures

### 7.1 ExplosionState

```
ExplosionState
├── Immutable Data
│   ├── original: Explosion
│   ├── level: Level
│   ├── source: Entity
│   ├── position: Vec3
│   ├── radius: float
│   ├── fire: boolean
│   └── blockInteraction: BlockInteraction
│
├── Pre-calculated Data
│   ├── blocksToDestroy: Set<BlockPos>
│   ├── affectedEntities: List<EntityInfo>
│   └── preCalculationComplete: boolean
│
├── Processing State
│   ├── currentBlockIndex: int
│   ├── damagedEntities: Set<Entity>
│   ├── knockedBackEntities: Set<Entity>
│   ├── effectsComplete: boolean
│   └── blocksDestroyed: int
│
└── Configuration
    ├── settings: ExplosionSettings
    └── damageTiming: TimingMode
```

### 7.2 EntityInfo

```
EntityInfo
├── entity: Entity
├── distance: float
├── visibility: float
├── impactFactor: float
├── damage: float
├── knockbackVector: Vec3
└── alreadyDamaged: boolean
```

---

## 8. Extension Points

### 8.1 Custom Effect Types

New effect types can be added by implementing the `EffectApplier` interface:

```
interface EffectApplier {
    void onPreCalculate(ExplosionState state)
    void onStart(ExplosionState state)
    void onBlockDestroyed(ExplosionState state, BlockPos pos)
    void onEnd(ExplosionState state)
}
```

### 8.2 Custom Queue Strategies

Queue processing can be customized by implementing `QueueStrategy`:

```
interface QueueStrategy {
    List<ExplosionState> selectExplosionsForProcessing(
        Queue<ExplosionState> activeQueue,
        int maxExplosions,
        int maxBlocks
    )
}
```

Built-in strategies:
- `FIFOQueueStrategy` - Process in order (default)
- `PriorityQueueStrategy` - Process by explosion size
- `ChunkLocalQueueStrategy` - Group by chunk location

### 8.3 Custom Block Interaction Modes

New block interaction modes can be added:

```
enum BlockInteraction {
    KEEP,           // No blocks destroyed
    DESTROY,        // All blocks destroyed
    DESTROY_WITH_DECAY,  // Blocks destroyed with item decay
    CUSTOM          // Custom handler
}
```

---

## 9. Performance Considerations

### 9.1 Memory Usage

- **Awaiting Queue**: Minimal memory (just explosion parameters)
- **Active Queue**: Full pre-calculated data
- **Estimated Memory**: ~1KB per explosion in active queue

### 9.2 CPU Usage

- **Pre-calculation**: O(rays × steps) per explosion, done instantly
- **Block Destruction**: O(blocksPerExplosionTick) per explosion per tick
- **Entity Effects**: O(affectedEntities) once per explosion

### 9.3 Network Impact

- **Block Updates**: Spread across ticks, no burst
- **Entity Packets**: Configurable timing
- **Sound/Packets**: Configurable timing

### 9.4 Recommendations

- `explosionsPerTick = 1024` - Good for most servers, balances throughput and memory
- `blocksPerExplosionTick = 16` - Good balance between speed and smoothness
- `maxBlocksPerTick = 16384` - Prevents starvation while allowing reasonable throughput
- Increase `blocksPerExplosionTick` for faster explosions if TPS allows
- Decrease `blocksPerExplosionTick` for smoother performance on lower-end servers

---

## 10. Future Enhancements

### 10.1 Planned Features

- [ ] Cascade suppression (falling blocks, redstone)
- [ ] Adaptive rate limiting based on server TPS
- [ ] Explosion statistics and monitoring

### 10.2 Potential Features

- [ ] Asynchronous pre-calculation (off main thread)
- [ ] Custom explosion shapes (sphere, cube, line)
- [ ] Block replacement (explode into specific blocks)

---

## 11. Migration from v2

### 11.1 Configuration Changes

| Old Config               | New Config               | Notes                                                    |
|--------------------------|--------------------------|----------------------------------------------------------|
| `blocksPerExplosionTick` | `blocksPerExplosionTick` | Default changed from 1 to 16                             |
| `explosionsPerTick`      | `explosionsPerTick`      | Default changed from 4096 to 1024, now active queue size |
| N/A                      | `maxBlocksPerTick`       | New setting, default 16384                               |
| N/A                      | `cascadeSuppression`     | New setting, default False                               |
| N/A                      | `soundVolumeSplit`       | New setting for START_END/SPREAD timing, default true    |

### 11.2 Behavior Changes

- **Pre-calculation**: Now instant instead of interleaved
- **State Preservation**: Fixed - deterministic results
- **Ray-casting**: No longer chunked across ticks
- **Block Destruction**: Only throttled operation
- **SPREAD Timing**: Effects accumulated per tick instead of per block (reduces function calls)
- **START_END Timing**: New timing mode - 50% at start, 50% at end

---

## Appendix A: Glossary

| Term                | Definition                                                              |
|---------------------|-------------------------------------------------------------------------|
| **Pre-calculation** | Ray-casting and entity effect calculation done before block destruction |
| **Active Queue**    | Explosions ready to destroy blocks                                      |
| **Awaiting Queue**  | Explosions waiting to be pre-calculated                                 |
| **Ray-casting**     | Algorithm to determine which blocks are affected by explosion           |
| **Blast Strength**  | Randomized explosion power per ray                                      |
| **Impact Factor**   | Combined distance and visibility for entity damage                      |
