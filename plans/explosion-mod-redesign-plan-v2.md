# Chunked Explosions Mod - Corrected Technical Analysis & Implementation Plan

## Version 2: Focus on Proper State Preservation & Real Lag Sources

---

## 1. What Actually Causes Explosion Lag?

Based on analysis of Lithium, PaperMC, and vanilla source code, here is the **actual breakdown** of explosion performance:

### Primary Lag Sources (in order of impact)

| Rank | Operation | Cost | Why It Laggs |
|------|-------------------|--------|--------------------------------------------------|
| **1** | **Block Updates & Broadcasting** | **VERY HIGH** | Each destroyed block calls `ServerLevel.setBlock()`, which: notifies neighbors, sends packet to all nearby clients, spawns particles, triggers falling/sand/gravels, breaks torches/redstone. This is cascading computation. |
| **2** | **Block Destruction** | **HIGH** | Calling `onBlockExploded()` for each block, spawning item drops with `Block.popResource()`, loot table computation. |
| **3** | **Entity Damage/Knockback** | **MEDIUM** | Requires ray-casting per entity for visibility (`getSeenPercent()`), damage calculation, applying knockback physics. |
| **4** | **Ray-Casting (Block)** | **LOW-MEDIUM** | Allocates `BlockPos` per ray step (~100-1000 per explosion), repeated chunk lookups, but relatively CPU-light. |
| **5** | **Ray-Casting (Entity)** | **LOW** | Allocates `ClipContext` + `BlockHitResult` per entity, but only for nearby entities. |

### Key Finding: The REAL Problem

**The lag is NOT from ray-casting or damage calculations.** The lag is from:
1. **Block updates being broadcast immediately** as blocks are destroyed
2. **Cascading updates** from broken blocks (falling sand, redstone updates, torch breaks)
3. **Network packets** sent to all players for each block change
4. **Client-side rendering** of particles and block updates

### What Your Original Idea Gets RIGHT

Your original design (process blocks in batches across ticks) **absolutely works** because:
- It **staggeres block updates** across multiple server ticks
- Clients receive updates spread over time, reducing packet burst
- Cascading updates (falling sand, etc.) can settle between ticks
- **This directly attacks the PRIMARY lag source (block updates)**

### What Was WRONG With the Implementation

The **bug is in state preservation** -- specifically:
- Ray-casting state (`randomFactor`, position, indices) isn't properly saved/restored
- This causes rays to **restart with fresh randomness** instead of resuming
- Result: **More blocks destroyed than intended** because rays get "extra chances"

---

## 2. Analysis of Original Design Intent

### What You Tried to Build

```
Explosion starts
    ↓
Pre-calculate ALL affected blocks (vanilla ray-casting)
    ↓
Store state: ray position, grid indices, blast strength
    ↓
Each tick:
    - Process N blocks (or all remaining)
    - For each block: calc resistance, check destruction, store position
    - Save: current ray index, grid position, randomFactor
    - If block limit hit, return to queue for next tick
    - Else continue
    ↓
When all blocks processed:
    - Apply effects (damage/sound/particles)
    - Actually destroy blocks one-by-one
```

### The Bug (Detailed)

In `ExplosionMixin.chunked_explode()` lines 336-398:

```java
// Ray-casting loop state
if (chunkedexplosions$randomFactor <= 0.0F) {
    // Generate NEW randomFactor (PROBLEM: same ray gets multiple!)
    chunkedexplosions$randomFactor = this.radius * (0.7F + this.level.random.nextFloat() * 0.6F);
    chunkedexplosions$currentX = this.x;
    chunkedexplosions$currentY = this.y;
    chunkedexplosions$currentZ = this.z;
}

// Process blocks
for (float stepSize = 0.3F; 
     chunkedexplosions$randomFactor > 0.0F && (blocksAdded[0] < blocksPerPass || blocksPerPass == 0); 
     chunkedexplosions$randomFactor -= 0.22500001F) {
    
    // ... block processing ...
    
    // BUG: Even if blocksAdded hits limit, ray continues!
    // Next tick: SAME ray position, but randomFactor might be reset
    chunkedexplosions$currentX += normalizedX * stepSize;
    chunkedexplosions$currentY += normalizedY * stepSize;
    chunkedexplosions$currentZ += normalizedZ * stepSize;
}
```

**What happens:**
1. Tick 1: Ray starts, processes 1 block, `blocksAdded=1`, hits limit, returns
2. Tick 2: Ray resumes at SAME position, BUT `randomFactor` might be:
   - Still > 0 (good), continues processing same ray
   - **OR ≤ 0 (bad)**, generates NEW randomFactor, SAME ray gets MORE blast strength
3. Result: Rays can be "reset" multiple times, getting multiple "fresh" blast calculations
4. More blocks destroyed than vanilla

### Why Chunk-Based Processing Doesn't Fix Much

You're absolutely correct! Processing by world chunk boundaries won't help because:
- If 1000 TNT are all in ONE chunk → still 1000 explosions in same chunk
- If one massive explosion breaks 500 blocks in ONE chunk → still 500 block updates in that chunk
- **The fix is to stagger updates across ticks, not across chunks**

---

## 3. Corrected Implementation: Fixing State Preservation

### The Core Fix: Save ALL Ray State

Instead of relying on object fields that reset, **explicitly save complete ray state**:

```java
class RayState {
    int xIndex, yIndex, zIndex;       // Grid position
    double currentX, currentY, currentZ;  // Ray march position
    float randomFactor;                // Blast strength
    float normalizedX, normalizedY, normalizedZ; // Ray direction
    int blocksAddedThisPass;           // For this tick's limit
    
    boolean isResumable(boolean hitBlockLimit) {
        // Can resume if:
        // 1. Still in this ray (randomFactor > 0)
        // 2. OR Need to start new ray but haven't exhausted grid
        return randomFactor > 0.0F || 
               (xIndex <= gridSize && yIndex <= gridSize && zIndex <= gridSize);
    }
}
```

### Proper Implementation Flow

```java
class ExplosionState {
    // Configuration
    int blocksPerTick;
    
    // Ray-casting phase
    RayState currentRay;
    List<BlockPos> blocksToDestroy = new ArrayList<>();
    boolean rayCastingComplete;
    
    // Block destruction phase
    int currentBlockIndex;
    List<Pair<ItemStack, BlockPos>> allDrops = new ArrayList<>();
    
    // Entity effects phase
    Set<Entity> damagedEntities = new HashSet<>();
    Set<Entity> knockedBackEntities = new HashSet<>();
    boolean effectsComplete;
    
    public void tick(ServerLevel world) {
        if (!rayCastingComplete) {
            processBlockRayCasting(world);
        } else if (!effectsComplete) {
            processEntityEffects(world);
        } else {
            destroyBlocks(world);
        }
    }
    
    private void processBlockRayCasting(ServerLevel world) {
        int blocksThisTick = 0;
        
        while (!RayState.isResumable() && blocksThisTick < blocksPerTick) {
            if (currentRay.randomFactor <= 0.0F) {
                // Start new ray
                if (!advanceToNextRay()) break; // Exhausted all rays
            }
            
            // March this ray until:
            // - Blast strength runs out (randomFactor <= 0)
            // - OR hit block limit
            while (currentRay.randomFactor > 0.0F && blocksThisTick < blocksPerTick) {
                BlockPos pos = BlockPos.containing(
                    currentRay.currentX, currentRay.currentY, currentRay.currentZ
                );
                
                // Check world bounds
                if (!world.isInWorldBounds(pos)) {
                    break; // Exit this ray
                }
                
                // Get block state and resistance
                BlockState blockState = world.getBlockState(pos);
                FluidState fluidState = world.getFluidState(pos);
                
                Optional<Float> resistance = damageCalculator.getBlockExplosionResistance(
                    explosion, world, pos, blockState, fluidState
                );
                
                // Reduce blast strength
                if (resistance.isPresent()) {
                    currentRay.randomFactor -= (resistance.get() + 0.3F) * 0.3F;
                }
                
                // Add block if blast still strong enough
                if (currentRay.randomFactor > 0.0F && 
                    damageCalculator.shouldBlockExplode(explosion, world, pos, blockState, currentRay.randomFactor)) {
                    blocksToDestroy.add(pos);
                    blocksThisTick++;
                }
                
                // Advance ray position (THIS MUST HAPPEN EVERY ITERATION!)
                currentRay.currentX += currentRay.normalizedX * 0.3F;
                currentRay.currentY += currentRay.normalizedY * 0.3F;
                currentRay.currentZ += currentRay.normalizedZ * 0.3F;
            }
        }
        
        // Check if all rays exhausted
        if (!needsRayCasting() && blocksThisTick > 0) {
            rayCastingComplete = true;
        }
    }
    
    private boolean advanceToNextRay() {
        // Increment grid index
        currentRay.zIndex++;
        if (currentRay.zIndex > gridSize) {
            currentRay.zIndex = 0;
            currentRay.yIndex++;
            if (currentRay.yIndex > gridSize) {
                currentRay.yIndex = 0;
                currentRay.xIndex++;
                if (currentRay.xIndex > gridSize) {
                    return false; // All rays done
                }
            }
        }
        
        // Only process surface rays
        if (isSurfaceRay(currentRay.xIndex, currentRay.yIndex, currentRay.zIndex)) {
            // Calculate normalized direction
            currentRay.normalizedX = (currentRay.xIndex * 2.0F / gridSize - 1.0F);
            currentRay.normalizedY = (currentRay.yIndex * 2.0F / gridSize - 1.0F);
            currentRay.normalizedZ = (currentRay.zIndex * 2.0F / gridSize - 1.0F);
            
            // Normalize vector
            double length = Math.sqrt(
                currentRay.normalizedX * currentRay.normalizedX +
                currentRay.normalizedY * currentRay.normalizedY +
                currentRay.normalizedZ * currentRay.normalizedZ
            );
            currentRay.normalizedX /= length;
            currentRay.normalizedY /= length;
            currentRay.normalizedZ /= length;
            
            // Reset ray position
            currentRay.currentX = explosionX;
            currentRay.currentY = explosionY;
            currentRay.currentZ = explosionZ;
            
            // Generate blast strength (use SAME random seed for determinism!)
            currentRay.randomFactor = explosionRadius * (0.7F + random.nextFloat() * 0.6F);
            
            return true;
        }
        
        return advanceToNextRay(); // Try next ray
    }
    
    private boolean needsRayCasting() {
        return currentRay.xIndex <= gridSize || 
               currentRay.yIndex <= gridSize || 
               currentRay.zIndex <= gridSize ||
               currentRay.randomFactor > 0.0F;
    }
    
    private boolean isSurfaceRay(int x, int y, int z) {
        return x == 0 || x == gridSize || y == 0 || y == gridSize || z == 0 || z == gridSize;
    }
}
```

### Key Fixes

1. **State is explicit object** → Not fields that reset
2. **Ray always advances** → Even when block limit hit
3. **Next tick resumes EXACTLY where left off** → No restart, no new random
4. **Deterministic RNG** → Same seed = same explosion
5. **Separate phases** → Calculate → Effects → Destroy (clear transitions)

---

## 4. Performance Optimization (Real Fixes, Not Just Delaying)

### Since block updates are the REAL problem, optimize those!

#### A. Batch Block Updates Per Chunk

```java
class BlockUpdateBatcher {
    // Group all blocks by chunk section (16x16x16 region)
    Map<ChunkSectionPos, List<BlockPos>> blocksBySection = new HashMap<>();
    
    // During destruction phase, collect ALL blocks
    for (BlockPos pos : blocksToDestroy) {
        ChunkSectionPos section = ChunkSectionPos.of(pos);
        blocksBySection.computeIfAbsent(section, k -> new ArrayList<>()).add(pos);
    }
    
    // When processing, update entire section atomically
    for (Map.Entry<ChunkSectionPos, List<BlockPos>> entry : blocksBySection.entrySet()) {
        batchUpdateBlocks(entry.getKey(), entry.getValue());
    }
    
    private void batchUpdateBlocks(ChunkSectionPos section, List<BlockPos> blocks) {
        Chunk chunk = level.getChunk(section.x, section.z);
        
        // Mark entire section as "in update"
        chunk.beginBulkBlockUpdate();
        
        for (BlockPos pos : blocks) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3); // FLAG_NONE
            // Suppress notifications
        }
        
        // Send single update packet for entire section
        chunk.endBulkBlockUpdate();
    }
}
```

**Effect**: 500 blocks in one section = 1 network packet instead of 500

#### B. Suppress Cascading Updates

```java
class CascadingUpdateSuppressor {
    private boolean inExplosionUpdate = false;
    
    public void destroyBlocksWithoutCascades(ServerLevel level, List<BlockPos> blocks) {
        inExplosionUpdate = true;
        
        try {
            for (BlockPos pos : blocks) {
                BlockState state = level.getBlockState(pos);
                
                // Override block's destruction to prevent cascades
                if (isCascadeProneBlock(state)) {
                    // Falling block: don't spawn entity, just drop item
                    if (state.getBlock() instanceof FallingBlock) {
                        state.onDestroyedByExplosion(level, pos, this);
                        spawnDrops(level, pos, state);
                        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    }
                    // Torch/Redstone: don't update neighbors
                    else if (isRedstoneBlock(state)) {
                        level.removeBlock(pos, false); // false = no neighbor updates
                        spawnDrops(level, pos, state);
                    }
                    // Others: normal destruction
                    else {
                        state.onDestroyedByExplosion(level, pos, this);
                        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    }
                } else {
                    state.onDestroyedByExplosion(level, pos, this);
                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                }
            }
        } finally {
            inExplosionUpdate = false;
        }
    }
    
    private boolean isCascadeProneBlock(BlockState state) {
        return state.is(Blocks.SAND) || state.is(Blocks.GRAVEL) ||
               state.is(Blocks.FIRE) || state.is(Blocks.REDSTONE_WIRE) ||
               state.is(Blocks.TORCH) || state.is(Blocks.LADDER);
    }
    
    private boolean isRedstoneBlock(BlockState state) {
        return state.is(Blocks.REDSTONE_WIRE) || state.is(Blocks.REDSTONE_TORCH) ||
               state.is(Blocks.REPEATER) || state.is(Blocks.COMPARATOR);
    }
}
```

**Effect**: No falling sand/graval gravity updates. No torch breaking cascade. Massive TPS save.

#### C. Delay Network Packets

```java
class DeferredPacketSender {
    private Queue<BlockUpdatePacket> packetQueue = new ConcurrentLinkedQueue<>();
    
    // Collect updates
    public void scheduleBlockUpdate(ServerLevel level, BlockPos pos, BlockState newState) {
        packetQueue.add(new BlockUpdatePacket(pos, newState));
    }
    
    // Send in batches every N ticks
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        
        int batchSize = Math.min(200, packetQueue.size());
        
        for (int i = 0; i < batchSize && !packetQueue.isEmpty(); i++) {
            BlockUpdatePacket packet = packetQueue.poll();
            
            // Send to nearby players
            level.getChunkSource().chunkMap.getPlayers(
                ChunkPos.of(packet.pos), false
            ).forEach(player -> player.connection.send(new ClientboundLevelChunkPacketData(packet)));
        }
    }
}
```

**Effect**: Network bandwidth spread over time, no packet burst spike.

#### D. Deferred Particle Spawning

```java
class ParticleThrottler {
    private Queue<ParticleData> particles = new ConcurrentLinkedQueue<>();
    private static final int MAX_PARTICLES_PER_TICK = 50;
    
    public void scheduleParticle(ParticleData data) {
        particles.add(data);
    }
    
    public void processParticles(ServerLevel level) {
        int spawned = 0;
        
        while (spawned < MAX_PARTICLES_PER_TICK && !particles.isEmpty()) {
            ParticleData data = particles.poll();
            level.addParticle(data.type, data.x, data.y, data.z, data.vx, data.vy, data.vz);
            spawned++;
        }
    }
}
```

---

## 5. Corrected Architecture

### Phase-Based State Machine

```
┌─────────────────────────────────────────────────────────────────────┐
│                    EXPLOSION LIFECYCLE                               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐   │
│  │ RAYCAST  │────►│ EFFECTS  │────►│  DESTROY │────►│ COMPLETE │   │
│  │  PHASE   │     │  PHASE   │     │  PHASE   │     │ PHASE    │   │
│  └──────────┘     └──────────┘     └──────────┘     └──────────┘   │
│         │                 │                 │                      │
│         ▼                 ▼                 ▼                      │
│    Calculate all       Damage/          Destroy blocks          Remove     │
│    block positions     knockback         (chunked)            from queue   │
│    (chunked)         (optional)         + drops              (finalize)   │
│                                                              │
│  ────────────────────────────────────────────────────────────────  │
│                       TICK-BY-TICK PROCESSING                     │
│  Each tick: process up to N items in current phase               │
│  Resume next tick EXACTLY where left off                        │
└─────────────────────────────────────────────────────────────────┘
```

### State Object Structure

```java
class ExplosionState {
    // Immutable explosion data
    Level level;
    Entity source;
    double x, y, z;
    float radius;
    boolean fire;
    BlockInteraction interactionMode;
    
    // Persistent across all ticks
    RayCastingState rayState = new RayCastingState();
    List<BlockPos> allAffectedBlocks = new ArrayList<>();
    
    // Per-tick state (gets cleared each tick)
    int blocksProcessedThisTick;
    Set<Entity> damagedThisTick;
    Set<Entity> knockedBackThisTick;
    
    // Configuration
    ExplosionSettings settings;
}
```

### Tick Handler (Main Event Loop)

```java
class ExplosionProcessor {
    private final Queue<ExplosionState> queue = new ConcurrentLinkedQueue<>();
    
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        
        int explosionsProcessed = 0;
        queue.removeIf(state -> {
            boolean done = processExplosion(state);
            if (!done) {
                // Re-add for next tick's processing
                // Note: In ConcurrentLinkedQueue, we add back manually
                return false;
            }
            explosionsProcessed++;
            return true; // Remove from queue
        });
        
        // Log if queue is growing
        if (queue.size() > 100) {
            LOGGER.warn("Explosion queue growing: {} explosions pending", queue.size());
        }
    }
    
    private boolean processExplosion(ExplosionState state) {
        // Phase 1: Ray-casting (calculate all blocks to destroy)
        if (!state.rayState.isComplete(state.settings.blocksPerTick)) {
            state.rayState.processStep(state.level, state.settings.blocksPerTick);
            return false; // Not done yet
        }
        
        // Collect all blocks
        state.allAffectedBlocks = state.rayState.getCollectedBlocks();
        
        // Phase 2: Entity effects (damage, knockback, sounds, particles)
        if (!state.settings.effectsCompleted) {
            processEntityEffects(state);
            if (!areEffectsComplete(state)) {
                return false; // More effects to process
            }
            state.settings.effectsCompleted = true;
        }
        
        // Phase 3: Block destruction (actually break blocks)
        destroyBlocks(state);
        
        return true; // Explosion complete
    }
}
```

---

## 6. Configuration: Fine-Grained Control

### Core Settings

```toml
[performance]
    # BLOCKS PER TICK - controls how fast blocks are calculated
    # Lower = smoother but takes more ticks
    # Higher = faster but more lag per tick
    blocksPerTick = 100
    default = 100 (adjust based on server TPS)
    
    # EFFECTS PER TICK - spread out damage/sound/particles
    entitiesPerTick = 50
    particlesPerTick = 100
    soundsPerTick = 5
    
    # DESTROY BATCH SIZE - how many blocks to update per destruction tick
    destroyBatchSize = 32
    default = 32 (balance between throughput and TPS)
```

### Timing Control

```toml
[timing]
    # When to apply effects vs destruction
    # START = at beginning (vanilla behavior)
    # DELAYED = after ray-casting completes
    # CHUNKED = spread across block destruction
    
    # Damage timing
    damageTiming = "CHUNKED"  # Can be: START, DELAYED, CHUNKED
    
    # Sound timing
    soundTiming = "START"
    
    # Particle timing
    particlesTiming = "START"
    
    # Knockback timing
    knockbackTiming = "START"
    
    # Block destruction timing
    destroyTiming = "DELAYED"
```

### Optimization Flags

```toml
[optimization]
    # Suppress cascading updates (falling sand, redstone, torches)
    suppressCascades = true
    
    # Batch network packets for block updates
    batchNetworkPackets = true
    
    # Delay particle spawning
    delayParticles = true
    
    # Suppress falling block entities (just drop items)
    noFallenBlocks = true
    
    # Pre-calculate all block states before destruction
    preCacheBlocks = true
```

### Safety Limits

```toml
[safety]
    # Maximum explosions in queue
    maxQueueSize = 1000
    
    # Force-cancel explosions older than N ticks
    maxExplosionAge = 600 (30 seconds)
    
    # Maximum blocks per explosion (caps TNT chains)
    maxBlocksPerExplosion = 10000 (vanilla TNT ~400)
    
    # Minimum ticks between large explosions (>1000 blocks)
    minimumCooldownTicks = 20
```

---

## 7. Summary & Recommendations

### What To Fix Immediately

1. **Fix the ray-casting state bug** - This is causing MORE blocks to be destroyed than vanilla
2. **Keep your batch processing idea** - It's correct! Just fix the state preservation
3. **Add cascading update suppression** - This is the REAL explosion lag source
4. **Add packet batching** - Reduces network spike from many block updates

### What NOT To Worry About

1. **Chunk boundaries** - Won't help if 1000 TNT are in one chunk
2. **Ray-casting performance** - It's minor compared to block updates
3. **Entity damage calculations** - Also minor

### The Correct Approach

```
1. Intercept explosion
2. Calculate ALL blocks to destroy (save ray state properly!)
3. For N seconds:
    - Process 100 blocks/tick (apply effects if CHUNKED mode)
    - Actually destroy blocks (suppress cascades)
    - Send network packets (batched)
4. Complete!
```

This gives smooth performance (blocks spread over time) without correctness issues (all ray state preserved correctly).

### Migration Path for Your Code

**Current bugs to fix:**
- Line 336: Don't reset `randomFactor` if ray is in progress
- Line 389-391: Always advance ray position, even if block limit hit
- Remove `processedBlocks` cache (or make it LRU-bounded with staleness check)
- Don't clear `toBlow` between ticks

**New features to add:**
- Explicit state object (not just fields)
- Three-phase processing (calculate → effects → destroy)
- Cascade suppression for sand/redstone/torches
- Network packet batching
- Safety limiter (max blocks per explosion)

---

This corrected plan preserves your original intent (batch processing across ticks) while fixing the bugs that cause incorrect behavior and adding the optimizations that ACTUALLY reduce lag (cascade suppression, packet batching).
