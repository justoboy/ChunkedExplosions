# Chunked Explosions - Testing Plan

## Overview

This document outlines all tests required to verify the Chunked Explosions mod functions correctly and produces identical results to vanilla Minecraft explosions. The tests are divided into three categories:

1. **Manual Tests** - In-game testing procedures for visual and behavioral verification
2. **Automated Tests** - Dev blocks and commands for repeatable, consistent testing
3. **Integration Tests** - Testing complex scenarios like TNT cannons and chain reactions

---

## Part 1: Manual Tests

Create test scenarios that can be run in a creative/survival world to verify visual and functional correctness.

### Test 1.1: Single TNT - Block Destruction Verification

**Purpose:** Verify that a single TNT explosion destroys the same blocks as vanilla.

**Setup:**
1. Create two identical test worlds (or use vanilla world comparison)
2. Build identical flat worlds with varying block types:
   ```
   [Dirt] [Stone] [Obsidian] [Wood] [Sand] [Glass] [Bedrock]
   ```
3. Place TNT on top of each block type
4. Prime TNT in both worlds simultaneously

**Expected Results:**
- Block destruction pattern should be identical
- Same number of blocks destroyed
- Same crater shape and depth
- Obsidian and bedrock should remain unbroken

**Pass Criteria:**
- [ ] Crater shapes match exactly
- [ ] Block counts match
- [ ] Resistant blocks (obsidian, bedrock) behave the same

### Test 1.2: Single TNT - Entity Damage Verification

**Purpose:** Verify entity damage matches vanilla.

**Setup:**
1. Create flat world with concrete blocks
2. Place 10 iron golems or players at different distances from TNT center:
   - 0 blocks (center)
   - 2 blocks away
   - 4 blocks away
   - 6 blocks away
   - 8 blocks away (edge of radius)
3. Record health before explosion
4. Prime TNT and observe

**Expected Results:**
- Damage should match vanilla formula: `(impact² + impact) / 2 × 7 × radius + 1`
- Damage decreases with distance
- Visibility (line of sight) affects damage

**Pass Criteria:**
- [ ] Center position receives ~35 damage (17.5 hearts)
- [ ] Damage curve matches vanilla
- [ ] Hidden entities (behind walls) receive less damage

### Test 1.3: Single TNT - Knockback Verification

**Purpose:** Verify knockback matches vanilla.

**Setup:**
1. Create flat world with no friction blocks
2. Place iron golems or players at various positions around TNT
3. Use `/gamerule doImmediateMobSpawn false` to prevent spawning interference
4. Prime TNT and measure knockback distance

**Expected Results:**
- Knockback vector should point away from explosion center
- Magnitude decreases with distance
- Protection enchantments reduce knockback

**Pass Criteria:**
- [ ] Knockback direction matches vanilla
- [ ] Knockback magnitude matches vanilla
- [ ] Protection enchantments dampen knockback correctly

### Test 1.4: Single TNT - Item Drops Verification

**Purpose:** Verify item drops and quantities match vanilla.

**Setup:**
1. Create test area with blocks that drop items (ores, wood, etc.)
2. Place TNT
3. Use data pack or command to track drops
4. Compare mod vs vanilla

**Expected Results:**
- Same items dropped
- Same quantities (considering randomness)

**Pass Criteria:**
- [ ] Item types match
- [ ] Item quantities within reasonable variance

### Test 1.5: Timing Modes - START

**Purpose:** Verify START timing applies effects immediately.

**Setup:**
1. Set config: `damageTiming=START`, `knockbackTiming=START`, `soundTiming=START`, `particleTiming=START`
2. Prime TNT
3. Observe timing of effects

**Expected Results:**
- Damage applied immediately on priming
- Knockback applied immediately
- Sound plays immediately
- Particles spawn immediately
- Blocks destroy over time (throttled)

**Pass Criteria:**
- [ ] All effects happen before block destruction starts
- [ ] Visual order matches config

### Test 1.6: Timing Modes - END

**Purpose:** Verify END timing applies effects after all blocks destroyed.

**Setup:**
1. Set config: `damageTiming=END`, `knockbackTiming=END`, `soundTiming=END`, `particleTiming=END`
2. Prime TNT with low `blocksPerExplosionTick` (e.g., 1) to observe delay
3. Observe timing of effects

**Expected Results:**
- No damage until all blocks destroyed
- No knockback until all blocks destroyed
- Sound plays after last block
- Particles spawn after last block

**Pass Criteria:**
- [ ] Effects wait for complete block destruction
- [ ] No early damage/knockback

### Test 1.7: Timing Modes - START_END

**Purpose:** Verify START_END timing splits effects 50/50.

**Setup:**
1. Set config: `damageTiming=START_END`, `soundVolumeSplit=true`
2. Prime TNT
3. Observe two damage events

**Expected Results:**
- 50% damage at START
- 50% damage at END
- Sound split 50% start, 50% end (volume affected)
- Particles split 50% start, 50% end

**Pass Criteria:**
- [ ] Two damage events observed
- [ ] Total damage equals vanilla damage
- [ ] Sound volume split correctly

### Test 1.8: Timing Modes - SPREAD

**Purpose:** Verify SPREAD timing applies effects incrementally.

**Setup:**
1. Set config: `damageTiming=SPREAD`, `knockbackTiming=SPREAD`
2. Prime TNT with low `blocksPerExplosionTick`
3. Observe damage accumulation

**Expected Results:**
- Damage accumulated per block destroyed
- Applied once per tick
- Total damage equals vanilla damage

**Pass Criteria:**
- [ ] Damage spreads across ticks
- [ ] Total matches vanilla

### Test 1.9: Determinism Test

**Purpose:** Verify same seed produces identical results.

**Setup:**
1. Set up identical starting conditions
2. Run same explosion 3 times
3. Compare block destruction patterns

**Expected Results:**
- Identical results each run (same seed = same RNG)

**Pass Criteria:**
- [ ] All 3 runs produce identical craters
- [ ] Same blocks destroyed in same order

---

## Part 2: Developer Tools for Testing

### Dev Block: TestCubeSpawner

**Purpose:** Create standardized test environments for consistent explosion testing.

**Implementation:**
```java
// Command: /testcube <size> <material> [center]
// Spawns a cube of specified material centered at player position
// Example: /testcube 20 dirt
public class TestCubeSpawner implements CommandHandler {
    public void execute(ServerCommandSource source, int size, String material, boolean centerAtPlayer) {
        // Calculate world position
        // Get block state by material name
        // Build cube around position
        // Mark chunks for re-sync
    }
}
```

**Commands to Add:**
- `/testcube <size> <material>` - Spawn a cube of blocks
- `/testclear <size>` - Clear a region (replace with air)
- `/testreset` - Reset test area to default state

### Dev Block: DeterministicTNT

**Purpose:** Create TNT that always produces identical explosions.

**Implementation:**
A custom TNT block/entity that uses a fixed seed:
```java
public class DeterministicPrimedTnt extends PrimedTnt {
    private static final long FIXED_SEED = 12345L; // Or configurable
    
    @Override
    protected void explode() {
        // Use fixed seed for ray-casting
        // Ensure identical results every time
    }
}
```

**Commands to Add:**
- `/spawntnt <seed>` - Spawn TNT with specific seed
- `/spawnvanillatnt` - Spawn vanilla TNT (for comparison)

### Dev Block: ExplosionComparator

**Purpose:** Compare two explosions and report differences.

**Implementation:**
```java
// Command: /compareexplosion <mode>
// Modes: snapshot, live-compare, history
public class ExplosionComparator implements CommandHandler {
    // Track block destroyed in current explosion
    // Compare with vanilla or previous explosion
    // Output differences
}
```

**Commands to Add:**
- `/explosioncompare snapshot` - Take snapshot of current explosion
- `/explosioncompare compare` - Compare with previously taken snapshot
- `/explosioncompare report` - Generate comparison report

### Dev Command: SpawnTestEntities

**Purpose:** Spawn standardized test entities at precise locations.

**Command: `/sptestentity <type> <radius> <count>`**

**Example:**
```
/sptestentity iron_golem 20 10
/sptestentity player 8 1
```

**Spawns entities at exact distances from next explosion.**

### Dev Command: Explosion Benchmark

**Purpose:** Run automated explosion benchmark and output metrics.

**Command: `/benchmarkexplosion <iterations> <blocksPerTick>`**

**Output:**
- Average TPS impact
- Blocks per second
- Time to complete explosion
- Memory allocations

### Dev Command: BlockComparison

**Purpose:** Compare block destruction between mod and vanilla.

**Command: `/compareblocks <explosionId>`**

**Output:**
- List of blocks destroyed by mod
- List of blocks destroyed by vanilla
- Differences (mod unique, vanilla unique, common)
- Match percentage

---

## Part 3: Integration Tests

### Test 3.1: Multiple Simultaneous Explosions

**Purpose:** Verify queue behavior under heavy load.

**Setup:**
1. Place 100 TNT blocks in 10x10 grid
2. Prime all at once
3. Monitor queue sizes

**Expected Results:**
- No queue overflow
- Explosion processing remains smooth
- TPS stays above 19.5

**Pass Criteria:**
- [ ] All explosions processed
- [ ] No TPS drop below 18
- [ ] Queue sizes reasonable (< 100 active, < 500 awaiting)

### Test 3.2: TNT Cannon Stress Test

**Purpose:** Verify mod handles TNT cannons correctly.

**Setup:**
1. Build large TNT cannon
2. Fire 50+ TNT in rapid succession
3. Observe block destruction and performance

**Expected Results:**
- Craters form correctly
- Performance remains stable
- No block duplication or loss

**Pass Criteria:**
- [ ] Craters match vanilla shape
- [ ] TPS stays stable
- [ ] No visual artifacts

### Test 3.3: Chain Reaction Test

**Purpose:** Verify chain-reaction explosions are handled.

**Setup:**
1. Build chain reaction (TNT triggers more TNT)
2. Prime first TNT
3. Observe behavior

**Expected Results:**
- All explosions queued and processed
- No infinite loops

**Pass Criteria:**
- [ ] All TNT explodes
- [ ] No queue overflow

### Test 3.4: Multi-World (Dimension) Test

**Purpose:** Verify explosions work across dimensions.

**Setup:**
1. Place TNT in Overworld
2. Place TNT in Nether
3. Place TNT in End
4. Prime all simultaneously

**Expected Results:**
- All dimensions process correctly
- No cross-dimension interference

**Pass Criteria:**
- [ ] Each dimension handled independently
- [ ] No crashes or errors

---

## Part 4: Configuration Verification Tests

### Test 4.1: MaxBlocksPerTick Verification

**Tests that global block cap is respected.**

**Setup:**
1. Set `maxBlocksPerTick=100`
2. Prime massive explosion (should destroy 1000+ blocks)
3. Count blocks destroyed in first tick

**Pass Criteria:**
- [ ] No more than 100 blocks destroyed in any tick

### Test 4.2: BlocksPerExplosionTick Verification

**Tests per-explosion rate limiting.**

**Setup:**
1. Set `blocksPerExplosionTick=5`
2. Prime single TNT
3. Observe blocks destroyed per tick

**Pass Criteria:**
- [ ] Max 5 blocks per tick per explosion
- [ ] Explosion still completes eventually

### Test 4.3: ExplosionsPerTick Verification

**Tests active queue size limit.**

**Setup:**
1. Set `explosionsPerTick=2`
2. Prime 10 TNT simultaneously
3. Observe queue sizes

**Pass Criteria:**
- [ ] Active queue never exceeds 2
- [ ] Awaiting queue backs up correctly

### Test 4.4: MaxQueueSize Verification

**Tests overflow protection.**

**Setup:**
1. Set `maxQueueSize=10`
2. Prime 20 TNT simultaneously
3. Observe rejection

**Pass Criteria:**
- [ ] After 10th, explosions are rejected
- [ ] Warning logged

---

## Part 5: Automated Test Suite (Future)

If a testing framework is added (e.g., Forge's test mod system), the following automated tests should be created:

### Test 5.1: `TestBlockDestruction`
- Spawn controlled environment
- Trigger explosion
- Count blocks destroyed
- Compare to expected

### Test 5.2: `TestEntityDamage`
- Spawn entities at fixed positions
- Trigger explosion
- Measure damage received
- Compare to vanilla

### Test 5.3: `TestKnockback`
- Spawn entities with zero velocity
- Trigger explosion
- Measure final position
- Calculate knockback vector
- Compare to vanilla

### Test 5.4: `TestDeterminism`
- Run same explosion 10 times
- Verify identical results
- Report any variance

### Test 5.5: `TestPerformance`
- Prime N explosions
- Measure TPS impact
- Verify within acceptable range

---

## Part 6: Manual Test World Setup Guide

### Creating a Test World

**Required Structure:**
```
/testworld
  /flat           - Flat world with block types
  /multiworld     - 3D environment for entity tests
  /stress         - Large scale testing
  /tncannon       - TNT cannon tests
```

### Setup Commands
```
# Clear test area
/testclear 50

# Create flat test world
/testcube 50 stone
/testcube 50 dirt
/testcube 50 stone
... (various block types)
```

### Recording Results
For each manual test:
1. Take before/after screenshots
2. Use F3 debug screen to record TPS
3. Count blocks destroyed (F3+A + block counter if available)
4. Note any anomalies

---

## Appendix A: Vanilla Reference Values

### TNT Explosion
- **Radius:** 4.0
- **Max Damage:** ~35 (17.5 hearts) at center
- **Max Knockback:** ~1.0 at center
- **Typical Blocks Destroyed:** 30-70

### Creeper Explosion
- **Radius:** 3.0
- **Max Damage:** ~23 (11.5 hearts) at center

### Wither Skull
- **Radius:** 1.0
- **Max Damage:** ~8 (4 hearts) at center

### Wither Explosion
- **Radius:** 7.0
- **Max Damage:** ~61 (30.5 hearts) at center

### End Crystal
- **Radius:** 7.0
- **Max Damage:** ~61 (30.5 hearts) at center
- **Creates fire**

### Block Resistance Thresholds
| Block | Resistance | Can Resist TNT (R=4) |
|---------|------------|---------------------|
| Bedrock | 3,600,000 | Yes |
| Obsidian | 1,200 | Yes |
| Ancient Debris | 1,200 | Yes |
| Blackstone | 6.0 | No |
| Stone | 6.0 | No |
| Dirt | 0.5 | No |
| Wood | 2.0 | No |

---

## Appendix B: Configuration Reference

| Config | Default | Description |
|--------|---------|-------------|
| `explosionsPerTick` | 1024 | Max explosions in active queue |
| `blocksPerExplosionTick` | 16 | Blocks each explosion destroys per tick |
| `maxBlocksPerTick` | 16384 | Global block destruction cap per tick |
| `damageTiming` | START | When damage is applied |
| `knockbackTiming` | START | When knockback is applied |
| `soundTiming` | START | When sound plays |
| `particleTiming` | START | When particles spawn |
| `soundVolumeSplit` | true | Split sound volume across timing points |
| `particleSplit` | true | Split particles across timing points |
| `cascadeSuppression` | false | Suppress falling blocks, redstone |
| `maxQueueSize` | -1 | Max pending explosions (-1 = unlimited) |
| `maxExplosionAge` | -1 | Max age before removal (-1 = unlimited) |

### Timing Modes
- `START` - Apply at beginning
- `END` - Apply at end
- `SPREAD` - Proportional across block destruction
- `START_END` - 50% at start, 50% at end

---

</content>