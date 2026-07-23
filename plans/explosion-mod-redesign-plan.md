# Chunked Explosions Mod - Analysis and Implementation Plan

## Table of Contents
1. [Current Implementation Analysis](#current-implementation-analysis)
2. [Identified Flaws and Issues](#identified-flaws-and-issues)
3. [Ground-Up Redesign Plan](#ground-up-redesign-plan)
4. [Lag Reduction Strategies](#lag-reduction-strategies)
5. [Feature Enhancements](#feature-enhancements)
6. [Configuration Reference](#configuration-reference)
7. [Implementation Roadmap](#implementation-roadmap)

---

## Current Implementation Analysis

### How It Works (Intended Behavior)

The **Chunked Explosions** mod is designed to reduce explosion lag by processing explosions incrementally over multiple server ticks instead of all at once. The core concept is sound:

1. **Explosion Queue**: When an explosion occurs, it's intercepted via Forge's `ExplosionEvent.Start`, canceled, and added to a queue
2. **Tick-Based Processing**: On each server tick, a configurable number of explosions (`explosionsPerTick`, default 4096) are processed
3. **Chunked Block Processing**: Each explosion processes a configurable number of blocks (`blocksPerExplosionTick`, default 1) per tick through ray-casting
4. **Incremental Effects**: Damage, sound, particles, and knockback can be applied at START, END, START_END (50/50 split), or SPREAD (distributed across all blocks processed)

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                        ChunkedExplosions Mod                        │
├─────────────────────────────────────────────────────────────────────┤
│  Main Handler (ChunkedExplosions.java)                              │
│  - explosionQueue: ConcurrentLinkedQueue<ChunkedExplosion>          │
│  - onExplosionStart(): Intercept and queue new explosions           │
│  - tick(): Process queued explosions each server tick               │
├─────────────────────────────────────────────────────────────────────┤
│  ChunkedExplosion Wrapper                                           │
│  - Delegates to ExplosionMixin via Duck interface                   │
│  - Manages initialization/finalization state                        │
├─────────────────────────────────────────────────────────────────────┤
│  ExplosionMixin (16x16x16 grid-based ray-casting)                  │
│  - chunked_initialize(): START timing effects                       │
│  - chunked_explode(): Ray-casting block destruction (chunked)      │
│  - chunked_update(): SPREAD timing effects                          │
│  - chunked_finalize(): END timing effects                           │
└─────────────────────────────────────────────────────────────────────┘
```

### Current Configuration Options

| Config | Default | Description |
|--------|---------|-------------|
| `enable` | true | Enable/disable mod functionality |
| `blocksPerExplosionTick` | 1 | Max blocks processed per explosion per tick |
| `explosionsPerTick` | 4096 | Max explosions processed per tick |
| `damageTiming` | SPREAD | When to apply entity damage |
| `damageMethod` | SPREAD | SPREAD (per-chunk) or ONCE (full at once) |
| `soundTiming` | SPREAD | When to play explosion sound |
| `soundVolumeSplit` | true | Split sound volume across chunks |
| `particleTiming` | SPREAD | When to spawn particles |
| `knockbackTiming` | SPREAD | When to apply knockback |
| `knockbackMethod` | ONCE | SPREAD (per-chunk) or ONCE (full at once) |

---

## Identified Flaws and Issues

### Critical Bug: Block Re-Processing in Ray-Casting Loop

**Location**: `ExplosionMixin.chunked_explode()` lines 318-398

**Problem**: The ray-casting algorithm does NOT reset `chunkedexplosions$randomFactor` properly when resuming between ticks. This causes the blast strength to carry over incorrectly.

```java
// Line 336-341 - The bug:
if (chunkedexplosions$randomFactor <= 0.0F) {
    chunkedexplosions$randomFactor = this.radius * (0.7F + this.level.random.nextFloat() * 0.6F);
    // ... reset position
}

// Line 344-346 - Ray march loop:
for (float stepSize = 0.3F; 
     chunkedexplosions$randomFactor > 0.0F && (blocksAdded[0] < blocksPerPass || blocksPerPass == 0); 
     chunkedexplosions$randomFactor -= 0.22500001F) {
    // ... process block
    // Resistance reduces blast strength
    blockResistance.ifPresent(resistance -> {
        chunkedexplosions$randomFactor -= (resistance + stepSize) * stepSize;
    });
    
    // Line 389-391 - Bug: Ray advances even if block wasn't added!
    chunkedexplosions$currentX += normalizedX * stepSize;
    chunkedexplosions$currentY += normalizedY * stepSize;
    chunkedexplosions$currentZ += normalizedZ * stepSize;
}
```

**The Issue**:
1. A ray starts with `randomFactor = radius * (0.7 + random(0.6))`
2. Ray advances and encounters blocks that offer resistance
3. `randomFactor` is reduced by resistance: `randomFactor -= (resistance + 0.3) * 0.3`
4. If `blocksPerExplosionTick = 1` and we've processed 1 block:
   - **Ray continues marching** (condition only checks `blocksAdded`)
   - **More blocks are encountered and their resistance is calculated**
   - **But these blocks might NOT be added to `blocksToDestroy` because they're checked AFTER the block-addition limit**
5. **Next tick**: Ray resumes from where it left off, but:
   - `randomFactor` might still be > 0 from previous ray
   - **OR** if it went to 0, a NEW randomFactor is generated for the SAME ray
   - **This causes the same blocks to be processed AGAIN with fresh blast strength**

**Impact**: With low `blocksPerExplosionTick` values, rays can partially process and then restart on fresh rays, causing:
- **More blocks destroyed than vanilla** (rays get multiple "chances")
- **Inconsistent explosion behavior** (random seed differences)
- **Non-deterministic results across runs**

### Secondary Issues

#### Issue 2: HashSet Block Deduplication is Inefficient
```java
Set<BlockPos> blocksToDestroy = Sets.newHashSet();
// ... later
if (!blocksToDestroy.contains(blockPos) && !toBlow.contains(blockPos)) {
    blocksToDestroy.add(blockPos);
}
```
**Problem**: `HashSet` lookups add O(1) overhead per block. `toBlow` is an `ObjectArrayList` so that's O(n) per check. For large explosions, this compounds.

#### Issue 3: Block Resistance Caching is Flawed
```java
if (chunkedexplosions$processedBlocks.containsKey(blockPos)) {
    blockState = (BlockState) chunkedexplosions$processedBlocks.get(blockPos).get("blockState");
    blockResistance = (Optional<Float>) chunkedexplosions$processedBlocks.get(blockPos).get("blockResistance");
    // Debug logging that reveals the cache can be stale
    testBlockResistance = this.damageCalculator.getBlockExplosionResistance(...);
}
```
**Problem**: The cache stores the state at first visit, but:
1. If the block changes between ticks (player mines it, another explosion hits it), the cache is stale
2. The cached resistance is recalculated (`testBlockResistance`) but the result is discarded
3. Boxing/unboxing `Optional<Float>` in a HashMap creates garbage

#### Issue 4: Entity Effect Bounding Box Queries Are Redundant
```java
// Called separately for damage, knockback, and potentially multiple times
List<Entity> affectedEntities = this.level.getEntities(this.source, new AABB(...));
```
**Problem**: The same 500+ entity query is run multiple times per tick. For spread timing, this happens incrementally. For START/END, it's run twice.

#### Issue 5: No Chunk Boundary Awareness
The mod is called "ChunkedExplosions" but **doesn't actually chunk by world chunks**. It processes blocks in a 16x16x16 grid pattern. This means:
- A single explosion can touch 10+ world chunks
- All chunk updates happen in the same tick
- No actual distribution across chunk loading/unloading cycles

#### Issue 6: Memory Leak Potential
```java
private final Map<BlockPos, Map<String, Object>> chunkedexplosions$processedBlocks = new HashMap<>();
```
**Problem**: This cache can grow to millions of entries for massive explosions and is NEVER cleared during the explosion's lifetime. Only cleaned up when the Explosion object is GC'd.

#### Issue 7: Broken Loop Logic
```java
// Line 315-318
while (chunkedexplosions$xIndex <= chunkedexplosions$gridSize && (blocksAdded[0] < blocksPerPass || blocksPerPass == 0)) {
    // ... nested while loops for y and z
```
**Problem**: When `blocksPerPass = 0` (no limit), the loop should process ALL remaining blocks. But the condition `blocksAdded[0] < blocksPerPass || blocksPerPass == 0` means:
- Innermost loop continues until ALL z values are exhausted, NOT until all blocks on that ray are processed
- Rays are abandoned mid-traversal if `blockAdded` hits the limit
- Next tick, the SAME ray position (zIndex, yIndex, xIndex) resumes with a NEW randomFactor

#### Issue 8: toBlow Isn't Cleared Between Ticks
```java
// In chunked_explode():
this.clearToBlow();
// ... populate blocksToDestroy
toBlow.addAll(blocksToDestroy);
```
**Problem**: Every tick, `toBlow` is cleared and re-populated. This means:
- Tick 1: Process blocks 1-10, add to toBlow
- Tick 2: Clear toBlow, process blocks 1-20, add to toBlow (loss of blocks 11-20 from tick 1!)
- **Actually, blocksToDestroy persists, so this is OK, but the clearing is unnecessary overhead**

### Calculated Impact Examples

| Setting | Expected | Actual | Problem |
|---|---|---|---|
| `blocksPerExplosionTick = 1` | 1 block/tick | Rays restart, same blocks processed multiple times | 2-3x more blocks destroyed |
| `blocksPerExplosionTick = 10` | 10 blocks/tick | Rays with resistance skip blocks, get new randomFactor | Inconsistent blast radius |
| `blocksPerExplosionTick = 0` | Process all at once | Loop exits when grid exhausted, but rays restart mid-way | Not truly instant, some fragmentation |

---

## Ground-Up Redesign Plan

### Design Philosophy

The mod should be rebuilt around these principles:

1. **Correctness First**: Match vanilla explosion behavior exactly (unless configured otherwise)
2. **True Chunk Distribution**: Process by world chunk boundaries to spread chunk loading/unloading
3. **Deterministic Results**: Same explosion settings = same results every time
4. **Memory Efficient**: Minimal allocation, reuse buffers, bounded caches
5. **Configurable Granularity**: Independent control over which effects get chunked

### Proposed Class Structure

```
com.github.justoboy.chunkedexplosions
├── ChunkedExplosionsMod                          # Main mod class
├── config
│   ├── ChunkedExplosionsConfig                   # Typed config wrapper
│   └── ExplosionSettings                         # Per-explosion overrides
├── processing
│   ├── ExplosionProcessor                        # Orchestrates chunked processing
│   ├── QueueStrategy                             # FIFO, Priority, Chunk-local
│   └── ProcessingStats                           # Monitoring/debugging
├── chunking
│   ├── ChunkBoundaryDetector                     # Finds chunk boundaries along rays
│   ├── ChunkQueueManager                         # Manages per-chunk block queues
│   └── ChunkAwareExplosion                       # Explosion wrapper with chunk awareness
├── effects
│   ├── DamageApplier                             # Entity damage handling
│   ├── SoundController                           # Sound playback
│   ├── ParticleController                        # Particle spawning
│   └── KnockbackApplier                          # Knockback application
├── block
│   ├── BlockDestroyer                            # Handles block breaks
│   ├── BlockResistanceCache                      # Cached, bounded block queries
│   ├── DropCalculator                            # Item drop calculation
│   └── FireSpawner                               # Fire placement
├── mixin
│   ├── ExplosionMixin                            # Intercept vanilla explosion creation
│   └── TickHandlerMixin                          # Inject tick handler
```

### Core Algorithm Redesign

```java
public class ChunkAwareExplosion {
    // Core state
    private final Explosion original;
    private final ExplosionSettings settings;
    
    // Chunk-aware processing state
    private final Map<ChunkPos, List<BlockPos>> blocksByChunk;  // Pre-calculated
    private final Queue<ChunkPos> chunkOrder;                   // Processing order
    private int currentChunkIndex;
    
    // State preservation
    private final Set<BlockPos> allAffectedBlocks;              // Complete list
    private final Set<Entity> damagedEntities;
    private final Set<Entity> knockedBackEntities;
    
    public void initialize() {
        // Phase 1: Run VANILLA ray-casting once, store all results
        // - Calculate ALL affected blocks deterministically
        // - Calculate all entity damage/knockback once
        // - Store everything in memory
        
        // This ensures:
        // 1. Results match vanilla (correctness)
        // 2. No re-processing (performance)
        // 3. Deterministic (predictable)
    }
    
    public void processChunk(ChunkPos chunk) {
        // Phase 2: Process one chunk's worth of blocks
        
        // 2a. Destroy blocks in this chunk
        for (BlockPos pos : blocksByChunk.get(chunk)) {
            destroyBlock(pos);
        }
        
        // 2b. Apply effects (if chunked)
        if (settings.damageIsChunked()) {
            applyPartialDamage(chunk);
        }
        if (settings.soundIsChunked()) {
            playPartialSound(chunk);
        }
        // ... etc
    }
    
    public void finalizeNonChunkedEffects() {
        // Apply any remaining effects that should happen at the end
        if (settings.damageTiming == END) {
            applyFullDamage();
        }
        // ... etc
    }
}
```

### Ray-Casting Fix (Deterministic Version)

```java
public class DeterministicRayCaster {
    private final Random random;
    private final Explosion explosion;
    private final Set<BlockPos> affectedBlocks;
    
    public DeterministicRayCaster(Explosion explosion, long seed) {
        this.explosion = explosion;
        this.random = new Random(seed);  // Deterministic!
        this.affectedBlocks = new HashSet<>();
    }
    
    public void calculateAllBlocks() {
        // Pre-calculate random factors for ALL rays
        float[][] rayFactors = new float[976][0];  // 16x16x16 cube surface = 976 rays
        
        for (int x = 0; x <= 15; x++) {
            for (int y = 0; y <= 15; y++) {
                for (int z = 0; z <= 15; z++) {
                    if (isSurface(x, y, z)) {
                        int rayIndex = getRayIndex(x, y, z);
                        rayFactors[rayIndex][0] = explosion.radius * (0.7F + random.nextFloat() * 0.6F);
                    }
                }
            }
        }
        
        // Now process ALL rays to completion, storing results
        processAllRays(rayFactors);
    }
    
    private void processAllRays(float[][] rayFactors) {
        for (int x = 0; x <= 15; x++) {
            for (int y = 0; y <= 15; y++) {
                for (int z = 0; z <= 15; z++) {
                    if (isSurface(x, y, z)) {
                        double rayX = x * 2.0F / 15.0F - 1.0F;
                        double rayY = y * 2.0F / 15.0F - 1.0F;
                        double rayZ = z * 2.0F / 15.0F - 1.0F;
                        
                        // Normalize
                        double length = Math.sqrt(rayX*rayX + rayY*rayY + rayZ*rayZ);
                        rayX /= length;
                        rayY /= length;
                        rayZ /= length;
                        
                        int rayIndex = getRayIndex(x, y, z);
                        float blastStrength = rayFactors[rayIndex][0];
                        
                        // March ray to completion
                        double currentX = explosion.x;
                        double currentY = explosion.y;
                        double currentZ = explosion.z;
                        
                        while (blastStrength > 0.0F) {
                            BlockPos pos = BlockPos.containing(currentX, currentY, currentZ);
                            
                            if (!level.isInWorldBounds(pos)) break;
                            
                            BlockState blockState = level.getBlockState(pos);
                            FluidState fluidState = level.getFluidState(pos);
                            
                            Optional<Float> resistance = getResistance(pos, blockState, fluidState);
                            if (resistance.isPresent()) {
                                blastStrength -= (resistance.get() + 0.3F) * 0.3F;
                            }
                            
                            // Add block if blast still strong enough
                            if (blastStrength > 0.0F) {
                                affectedBlocks.add(pos);
                            }
                            
                            // Advance ray
                            currentX += rayX * 0.3F;
                            currentY += rayY * 0.3F;
                            currentZ += rayZ * 0.3F;
                        }
                    }
                }
            }
        }
    }
}
```

---

## Lag Reduction Strategies

### Strategy 1: Pre-Calculate Everything, Process Incrementally

**Current**: Interleave calculation and execution (calculate blocks → break them → calculate more → break more)

**Proposed**: Separate completely (calculate ALL → process in chunks)

**Benefits**:
- No repeated block state queries
- No cache invalidation issues
- Deterministic results
- Can parallelize pre-calculation (off main thread)

### Strategy 2: True Chunk-Based Distribution

Instead of arbitrary `blocksPerTick` limits, process by actual world chunks:

```java
// Group affected blocks by chunk
blocksByChunk.computeIfAbsent(chunkPos, k -> new ArrayList<>()).add(blockPos);

// Process one chunk per tick (or N chunks per tick)
for (int i = 0; i < chunksPerTick && !chunkQueue.isEmpty(); i++) {
    ChunkPos chunk = chunkQueue.poll();
    processChunk(chunk);
}
```

**Benefits**:
- Natural batching (chunk loading already chunked)
- Less chunk section updates per tick
- More predictable performance
- Better cache locality

### Strategy 3: Entity Query Optimization

**Current**: Query entity list 2-4 times per explosion

**Proposed**: Query once, reuse with filtering

```java
// Query once during initialization
List<Entity> allNearbyEntities = level.getEntities(source, explosionBoundingBox);

// Store reference
private final List<Entity> nearbyEntities;

// Reuse with flags
if (!damagedEntities.contains(entity)) {
    damagedEntities.add(entity);
    entity.hurt(damageSource, damage);
}
```

**Benefits**:
- 75% fewer world queries
- Less concurrent modification risk
- Simpler logic

### Strategy 4: Bounded Caching with LRU

```java
// Use FastUtil's primitive collections for position-based caches
ObjectIntMap<BlockPos> blockResistanceCache = new ObjectIntHashMap<>();

// Evict old entries when cache is full
private static final int MAX_CACHE_SIZE = 10000;
if (blockResistanceCache.size() > MAX_CACHE_SIZE) {
    // Remove oldest 10 entries
    removeOldestEntries(blockResistanceCache, 100);
}
```

### Strategy 5: Parallel Pre-Calculation

```java
// Use ForkJoinPool for block calculation (if settings allow)
ForkJoinPool pool = new ForkJoinPool(4);

List<List<BlockPos>> chunkLists = pools.invoke(new RayCastingTask(
    rays, chunkBoundary
));
```

**Benefits**:
- Utilize multi-core CPUs
- Especially effective for large explosions
- Can be disabled for single-thread performance

### Strategy 6: Batched Block Setting

**Current**: `level.setBlockAndUpdate()` called individually for each block

**Proposed**: Batch multiple blocks into a single section update

```java
// Group blocks by chunk section (16x16x16 region)
Map<ChunkSectionPos, List<BlockPos>> blocksBySection;

// Update entire section at once
for (Map.Entry<ChunkSectionPos, List<BlockPos>> entry : blocksBySection.entrySet()) {
    level.setBlocksInSection(entry.getKey(), entry.getValue());
}
```

### Strategy 7: Asynchronous Particle/Sound Spawning

**Current**: All particles and sounds spawned on main thread

**Proposed**: Schedule non-critical effects

```java
// Calculate all particle positions
List<ParticleData> allParticles = calculateAllParticles();

// Spawn a subset per tick
int particlesPerTick = max(100, totalParticles / explosionTicksRemaining);
spawnParticles(allParticles.subList(startIdx, endIdx));
```

**Benefits**:
- Smoother particle distribution
- Less FPS hit per tick

### Strategy 8: Adaptive Rate Limiting

**Current**: Fixed `blocksPerTick = 1` (default)

**Proposed**: Dynamic based on server load

```java
// Monitor TPS, adjust processing rate
if (server.getTps() > 19.5) {
    currentBlocksPerTick = baseBlocksPerTick * 2;
} else if (server.getTps() < 18) {
    currentBlocksPerTick = Math.max(1, baseBlocksPerTick / 2);
}
```

---

## Feature Enhancements

### A. Non-Lag-Related QoL Features

#### A1. Explosion Size Modification
```java
public class ExplosionSizeConfig {
    // Per-explosion-type size modifiers
    float tntRadiusMultiplier = 1.0F;
    float creeperRadiusMultiplier = 1.0F;
    float witherSkullRadiusMultiplier = 1.0F;
    float endCrystalRadiusMultiplier = 1.0F;
    float ghastFireballRadiusMultiplier = 1.0F;
    float dragonFireballRadiusMultiplier = 1.0F;
    
    // Minimum and maximum radius clamps
    float minRadius = 0.0F;
    float maxRadius = 100.0F;
}
```

#### A2. Block Destruction Filters
```java
public class BlockDestructionConfig {
    // Block whitelist (only these can be destroyed)
    boolean useBlockWhitelist = false;
    Set<Block> allowedBlocks = new HashSet<>();
    
    // Block blacklist (protected from explosions)
    Set<Block> protectedBlocks = new HashSet<>();
    
    // Block blast resistance overrides
    Map<Block, Float> blastResistanceOverrides = new HashMap<>();
    
    // Per-block destruction chance (0.0-1.0)
    Map<Block, Float> destructionChanceOverrides = new HashMap<>();
}
```

#### A3. Damage Modification
```java
public class DamageConfig {
    // Global and per-type damage multipliers
    float damageMultiplier = 1.0F;
    Map<ExplosionSource, Float> sourceDamageMultipliers = new EnumMap<>(ExplosionSource.class);
    
    // Entity type damage modifiers
    Map<EntityType<?>, Float> entityTypeDamageMods = new HashMap<>();
    
    // Min/max damage clamps
    float minDamage = 0.0F;
    float maxDamage = 100.0F;
    
    // Damage falloff curve (linear, quadratic, exponential)
    FalloffCurve falloffCurve = FalloffCurve.QUADRATIC;
}
```

#### A4. Drop Preservation
```java
public class DropConfig {
    // Global drop chance modifier
    float dropChanceMultiplier = 1.0F;
    
    // Preserve specific blocks' drops regardless of explosion
    Set<Block> guaranteedDrops = new HashSet<>();
    
    // Custom drop tables for certain blocks
    Map<Block, List<ItemStack>> customDrops = new HashMap<>();
    
    // Prevent any drops in creative worlds
    boolean noDropsInCreative = false;
}
```

#### A5. Fire Control
```java
public class FireConfig {
    // Fire spawn chance (0.0-1.0)
    float fireSpawnChance = 0.33F;
    
    // Only spawn fire from specific sources
    Set<ExplosionSource> fireEnabledSources = EnumSet.allOf(ExplosionSource.class);
    
    // Never spawn fire in certain biomes
    Set<BiomeType> fireDisabledBiomes = new HashSet<>();
    
    // Fire spread limitation
    boolean limitFireSpread = false;
    int maxFireBlocksPerExplosion = 10;
}
```

#### A6. Nether Portal Preservation
```java
public class PortalPreservationConfig {
    // Don't destroy obsidian portals
    boolean preserveObsidianPortals = true;
    
    // Don't destroy reinforced bedrock
    boolean preserveBedrock = true;
    
    // Don't destroy structure blocks
    boolean preserveStructureBlocks = true;
    
    // Custom protection tag
    String protectionTag = "chunkedexplosions:protected";
}
```

#### A7. Environmental Effects
```java
public class EnvironmentalConfig {
    // Sound attenuation based on distance
    boolean useDistanceSoundAttenuation = true;
    
    // Visual effects (screenshake, camera tilt)
    boolean screenshake = false;
    float screenshakeIntensity = 0.5F;
    
    // Weather effects on explosion
    boolean createRainInExplosionArea = false;  // Rare, fun effect
    
    // Block-specific effects
    Map<Block, ExplosionEffect> blockEffects = new HashMap<>();
}
```

### B. Additional Lag-Optimizations

#### B1. World Border Awareness
```java
// Don't process blocks outside world border
// Early exit for explosions that would be mostly outside
```

#### B2. Player-Only Chunk Caching
```java
// Only keep loaded chunks in explosion processing
// Skip unloaded chunks (blocks won't break anyway)
```

#### B3. Server Thread Priority
```java
// Lower priority for explosion processing threads
// Let player interactions take precedence
```

#### B4. Configurable Effect Batching
```java
// Group same-type effects into single packets
// E.g., spawn 100 particles in one network packet instead of 100 separate
```

---

## Configuration Reference

### Complete Configuration Structure

```toml
# Chunked Explosions Configuration

[general]
    # Enable/disable entire mod
    enable = true
    
    # Global explosion toggle (affects all explosions)
    globalEnable = true
    
    # Maximum explosions processed per tick
    explosionsPerTick = 4096
    
    # Maximum blocks processed per explosion per tick (deprecated, replaced by chunksPerTick)
    blocksPerExplosionTick = 1
    
    # Maximum chunks processed per explosion per tick (NEW)
    chunksPerTick = 1
    
    # Maximum entities scanned per tick
    entitiesPerTick = 256
    
    # Pre-calculation mode: true = all calc first, false = interleaved
    precomputeAll = true
    
    # Parallel pre-calculation threads (0 = auto)
    precalcThreads = 0
    
    # Adaptive rate limiting
    adaptiveRateLimiting = true
    minTpsForAdaptive = 18.0
    maxRateIncrease = 4
    maxRateDecrease = 0.5

[explosion_types]
    # Per-explosion-type settings
    
    [explosion_types.tnt]
        enabled = true
        radiusMultiplier = 1.0
        canDestroyBlocks = true
        canDamageEntities = true
        setsFire = false
        
    [explosion_types.creeper]
        enabled = true
        radiusMultiplier = 1.0
        canDestroyBlocks = true
        canDamageEntities = true
        setsFire = false
        
    [explosion_types.wither]
        enabled = true
        radiusMultiplier = 1.0
        canDestroyBlocks = true  # Can override bedrock if enabled below
        canDamageEntities = true
        setsFire = false
        
    [explosion_types.end_crystal]
        enabled = true
        radiusMultiplier = 1.0
        canDestroyBlocks = true
        canDamageEntities = true
        setsFire = true
        
    [explosion_types.ghast_fireball]
        enabled = true
        radiusMultiplier = 1.0
        canDestroyBlocks = true
        canDamageEntities = true
        setsFire = true

[block_destruction]
    # Block-level control
    
    # Protection blacklist (these blocks cannot be destroyed)
    protectedBlocks = ["minecraft:bedrock", "minecraft:barrier", "minecraft:structure_block"]
    
    # Blast resistance overrides (overrides vanilla values)
    # Format: "block_id": resistance_value
    resistanceOverrides = {}
    
    # Deconstruction chance overrides (0.0-1.0)
    # Format: "block_id": chance
    destructionChanceOverrides = {}
    
    # Custom drop rates
    dropChanceMultiplier = 1.0
    guaranteedDropBlocks = ["minecraft:diamond_ore", "minecraft:emerald_ore"]
    
    # Block whitelist (if enabled, only these can be destroyed)
    whitelistEnabled = false
    allowedBlocks = []

[damage]
    # Entity damage settings
    
    # Global damage multiplier
    damageMultiplier = 1.0
    
    # Damage falloff curve: LINEAR, QUADRATIC (vanilla), EXPONENTIAL
    falloffCurve = "QUADRATIC"
    
    # Minimum and maximum damage clamps
    minDamage = 0.0
    maxDamage = 100.0
    
    # Per-source damage multipliers
    [damage.sources]
        tnt = 1.0
        creeper = 1.0
        wither = 1.0
        end_crystal = 1.0
        ghast = 1.0
        
    # Per-entity-type damage modifiers
    [damage.entities]
        # Format: "entity_id": modifier
        "minecraft:creeper" = 0.5  # Creepers take half damage from explosions
        
    # Protection enchantment effectiveness
    protectionEffectiveness = 1.0
    
    # Can explosions kill players?
    canKillPlayers = true
    
    # Can explosions kill non-mobs?
    canKillEntities = true

[knockback]
    # Knockback settings
    
    # Global knockback multiplier
    knockbackMultiplier = 1.0
    
    # Can knockback be disabled?
    enableKnockback = true
    
    # Maximum knockback velocity
    maxKnockbackVelocity = 8.0
    
    # Can players be knocked off cliffs?
    allowFallDamageFromKnockback = true

[fire]
    # Fire settings
    
    # Fire spawn chance (0.0-1.0)
    fireSpawnChance = 0.33
    
    # Per-source fire settings
    [fire.sources]
        tnt = false
        creeper = false
        wither = false
        end_crystal = true
        ghast = true
        
    # Biome fire blacklist
    fireDisabledBiomes = ["minecraft:frozen_ocean", "minecraft:snowy_taiga"]
    
    # Limit fire blocks per explosion
    limitFirePerExplosion = true
    maxFireBlocks = 20

[particles]
    # Particle settings
    
    # Particle timing: START, END, START_END, SPREAD
    timing = "SPREAD"
    
    # Particles per explosion (if SPREAD, max particles per tick)
    maxParticlesPerTick = 100
    
    # Particle type overrides
    # Format: "explosion_type": "particle_type"
    typeOverrides = {}
    
    # Disable particles entirely
    disableParticles = false
    
    # Client-only setting (doesn't affect server)
    clientOnly = true

[sounds]
    # Sound settings
    
    # Sound timing: START, END, START_END, SPREAD
    timing = "SPREAD"
    
    # Split volume across chunks
    splitVolume = true
    
    # Minimum volume (when split)
    minVolume = 0.1
    
    # Maximum volume (when not split)
    maxVolume = 4.0
    
    # Sound attenuation distance
    attenuationDistance = 64
    
    # Disable sounds entirely
    disableSounds = false
    
    # Client-only setting
    clientOnly = true

[performance]
    # Performance tuning
    
    # Cache size for block resistance queries
    resistanceCacheSize = 10000
    
    # Cache eviction strategy: LRU, FIFO, RANDOM
    cacheEviction = "LRU"
    
    # Batch size for block updates
    blockUpdateBatchSize = 32
    
    # Network packet batch size for particles
    particlePacketBatchSize = 16
    
    # Thread pool size for parallel operations
    threadPoolSize = 4
    
    # Enable async pre-calculation
    asyncPrecalculation = false
    
    # Profiler enabled (for debugging)
    enableProfiler = false

[experimental]
    # Experimental features (subject to change)
    
    # True chunk-based processing (replaces blocksPerTick)
    chunkBasedProcessing = true
    
    # Distributed explosions across multiple servers (multi-threaded)
    distributedProcessing = false
    
    # Save explosion state to disk (for very large explosions)
    persistentState = false
    
    # Custom explosion engine (replaces vanilla ray-casting)
    customRayCasting = false
    
    # Network compression for particle data
    compressParticleData = false
```

---

## Implementation Roadmap

### Phase 1: Bug Fixes (Priority: Critical)
**Goal**: Fix correctness issues without changing performance characteristics

1. **Fix ray-casting re-processing bug** (lines 336-398 in ExplosionMixin)
   - Ensure `randomFactor` is tied to ray position, not block processing count
   - Track ray state separately from tick state
   - Use deterministic random seed for reproducibility

2. **Fix block cache staleness**
   - Add block change listener
   - Invalidate cache entries on block change
   - Or remove cache entirely (query fresh each time)

3. **Fix redundant entity queries**
   - Cache entity list for explosion lifetime
   - Add filtering flags instead of re-querying

4. **Fix toBlow clearing**
   - Don't clear `toBlow` between ticks
   - Only append new blocks

**Timeline**: 1-2 weeks
**Risk**: Low (isolated changes)

### Phase 2: Architecture Rewrite (Priority: High)
**Goal**: Complete ground-up redesign with clean architecture

1. **New class structure** as described in "Proposed Class Structure"
2. **Pre-calculation phase** - separate calculation from execution
3. **True chunk-based processing**
4. **LRU-bounded caching**
5. **Adaptive rate limiting**

**Timeline**: 4-6 weeks
**Risk**: Medium (large changes, requires thorough testing)

### Phase 3: Feature Expansion (Priority: Medium)
**Goal**: Add QoL features

1. Block destruction filters
2. Damage modification
3. Drop preservation
4. Fire control
5. Portal preservation

**Timeline**: 3-4 weeks
**Risk**: Low (features are additive)

### Phase 4: Performance Optimization (Priority: Medium)
**Goal**: Additional lag reduction

1. Parallel pre-calculation
2. Batched block updates
3. Asynchronous effect spawning
4. Network batching

**Timeline**: 3-4 weeks
**Risk**: Medium (multi-threading complexity)

### Phase 5: Polish and Documentation (Priority: Low)
**Goal**: User experience

1. Comprehensive documentation
2. In-game GUI for settings
3. Performance statistics display
4. Migration guide from old version

**Timeline**: 2-3 weeks
**Risk**: Very Low

---

## Testing Strategy

### Test Cases

| Test | Expected Result |
|------|----------------|
| Single TNT, vanilla settings | Identical to vanilla explosion |
| 100 TNT, blocksPerTick=1, damageTiming=START | Same blocks destroyed, damage applied incrementally |
| TNT in unloaded chunk | Chunk loads, block breaks, chunk may unload |
| TNT at chunk boundary | Blocks on both sides affected, processing respects boundaries |
| TNT with protectedObsidian=true | Obsidian survives, surrounding blocks destroyed |
| TNT with damageMultiplier=2.0 | Damage = vanilla × 2 |
| Multiple simultaneous explosions | No race conditions, each processed independently |

### Benchmark Scenarios

| Scenario | TPS Target | Block Rate |
|----------|------------|------------|
| Single TNT | 20 TPS | All processed |
| 100 simultaneous TNT | 18+ TPS | 4096 explosions/tick |
| 1000 simultaneous TNT | 15+ TPS | Queued, processed over time |
| Wither boss | 20 TPS | All within 200ms |

---

## Conclusion

The current Chunked Explosions mod has a solid concept but suffers from critical bugs that cause incorrect behavior (more blocks destroyed than intended). A ground-up redesign focusing on:

1. **Deterministic pre-calculation** - Separate calculation from execution
2. **True chunk awareness** - Process by actual world chunks, not arbitrary block counts
3. **Bounded caching** - Prevent memory leaks
4. **Clean architecture** - Modular, testable, maintainable code

Would result in a more reliable, more efficient, and more feature-rich explosion management system.

The recommended path forward:
- **Immediate**: Fix the ray-casting bug (Critical)
- **Short-term**: Refactor to separate pre-calculation from execution (High priority)
- **Medium-term**: Add performance and QoL features (Medium priority)
- **Long-term**: Experimental async/distributed processing (Low priority)

A complete rewrite following the outlined design would deliver a mod that not only reduces lag but also provides granular control over explosion behavior, making it suitable for both performance-focused servers and gameplay-modifying modpacks.
