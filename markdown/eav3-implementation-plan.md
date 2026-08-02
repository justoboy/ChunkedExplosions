## Architecture Review: Ready for Implementation

The architecture document is solid and comprehensive. Here's what's covered:

### ✅ Complete Sections
- Dual-queue system architecture
- Configuration reference with all timing modes
- Core components (ExplosionProcessor, ExplosionState, BlockDestroyer, EntityEffectApplier, SoundController, ParticleController)
- Processing flow diagrams
- Effect timing modes (START, END, SPREAD, START_END)
- Data structures
- Extension points
- Performance considerations
- Migration guide
- Glossary

### 📝 Minor Additions (Optional, Can Be Added Later)
- Detailed SPREAD accumulation logic (how blocks are counted per tick)
- Edge case handling (explosions with 0 blocks, empty queues)
- Testing strategy section

These can be addressed during implementation as they arise.

---

## Implementation Prompt for Coding AI

Below is a detailed prompt you can provide to a coding AI:

---

**Prompt:**

You are tasked with implementing a redesigned explosion processing system for a Minecraft Forge mod. Read the architecture document at [`/explosion-architecture-v3.md`](plans/explosion-architecture-v3.md) for complete technical specifications.

### Project Context
- **Mod Name:** Chunked Explosions
- **Minecraft Version:** 1.20.1
- **Loader:** Forge
- **Current State:** Existing implementation with bugs in ray-casting state preservation

### Implementation Phases

**Phase 1: Core Data Structures**
1. Create `ExplosionState` class to encapsulate all explosion data
    - Pre-calculated blocks to destroy
    - Pre-calculated entity effects
    - Processing state tracking
2. Create `EntityInfo` class for pre-calculated entity data
3. Implement deterministic ray-casting algorithm (16x16x16 grid surface rays)
4. Test: Verify same explosion produces identical results across runs

**Phase 2: Dual-Queue System**
1. Create `ExplosionProcessor` class
    - `awaitingQueue` for pending explosions
    - `activeQueue` for ready-to-process explosions
2. Implement `tryMoveToActiveQueue()` method
    - Move explosions from awaiting to active when space available
    - Trigger pre-calculation during move
3. Implement `onServerTick()` method
    - Process active queue each tick
    - Respect `explosionsPerTick`, `blocksPerExplosionTick`, `maxBlocksPerTick` limits
4. Test: Verify queue behavior with multiple simultaneous explosions

**Phase 3: Block Destruction**
1. Create `BlockDestroyer` class
    - `destroyBlock(BlockPos)` method
    - Loot calculation
    - Fire placement (if enabled)
2. Integrate with `ExplosionState` processing
3. Test: Verify blocks are destroyed correctly and drops spawn

**Phase 4: Entity Effects**
1. Create `EntityEffectApplier` class
    - Pre-calculate visibility, damage, knockback during pre-calculation
    - `applyDamage(Entity, float)` method
    - `applyKnockback(Entity, Vec3)` method
2. Implement timing modes: START, END, SPREAD, START_END
3. Test: Verify damage and knockback match vanilla behavior

**Phase 5: Sound and Particles**
1. Create `SoundController` class
    - `playSound(Level, Vec3, float volume)` method
    - Implement `soundVolumeSplit` behavior
    - Timing modes: START, END, SPREAD, START_END
2. Create `ParticleController` class
    - `spawnParticles(Level, Vec3)` method
    - Timing modes: START, END, SPREAD, START_END
3. Test: Verify sounds and particles play at correct times

**Phase 6: Integration with Existing Code**
1. Update `ExplosionMixin` to:
    - Intercept explosions via `ExplosionEvent.Start`
    - Cancel vanilla explosion
    - Create `ExplosionState` and add to awaiting queue
2. Update `ChunkedExplosions` main class to:
    - Register server tick handler
    - Call `ExplosionProcessor.onServerTick()` each tick
3. Update configuration to match new settings
4. Test: Verify full integration with game

**Phase 7: Command Updates**
1. Update existing commands to match new configuration:
    - `ExplosionsPerTickCommand`
    - `BlocksPerExplosionTickCommand`
2. Add new commands if needed:
    - `MaxBlocksPerTickCommand`
3. Test: Verify commands work correctly

**Phase 8: Edge Cases and Optimization**
1. Handle edge cases:
    - Explosions with 0 blocks
    - Empty queues
    - Queue overflow
2. Optimize:
    - Memory usage for large explosion counts
    - CPU usage during pre-calculation
3. Test: Stress test with massive TNT cannons

### Configuration Defaults
```toml
explosionsPerTick = 1024
blocksPerExplosionTick = 16
maxBlocksPerTick = 16384
cascadeSuppression = false
soundVolumeSplit = true
```

### Key Requirements
1. **Deterministic Results:** Same explosion settings = same results every time
2. **Smooth Performance:** Block destruction spread across ticks
3. **Backward Compatibility:** Preserve existing config names where possible
4. **Vanilla Behavior:** Match vanilla explosion behavior unless configured otherwise

### Testing Checklist
- [ ] Single TNT explosion behaves correctly
- [ ] Multiple simultaneous explosions process correctly
- [ ] Queue doesn't overflow under heavy load
- [ ] SPREAD timing accumulates and applies once per tick
- [ ] START_END timing splits effects 50/50
- [ ] soundVolumeSplit affects volume distribution
- [ ] Deterministic: same seed produces same results
- [ ] Server TPS remains stable under explosion load

Start with Phase 1 and proceed sequentially. Each phase must be tested before moving to the next.