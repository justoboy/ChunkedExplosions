# Chunked Explosions - Manual Test Tracking

## Test Execution Log

| # | Test Name | Status | Result | Notes | Date |
|---|---|---|---|---|---|
| 01 | Single TNT Block Destruction | PARTIAL | Obsidian protected (0 blocks), but inconsistent block counts due to positioning issues | 2026-08-07 |
| 02 | Single TNT Entity Damage | PENDING | - | | | | 03 | Single TNT Knockback | PASSED | Identical knockback to vanilla (1.744 blocks radially outward for all 4 entities) | 2026-08-08 |
| 04 | Timing Modes - START | PENDING | - | | |
| 05 | Timing Modes - END | PENDING | - | | |
| 06 | Timing Modes - START_END | PENDING | - | | |
| 07 | Timing Modes - SPREAD | PENDING | - | | |
| 08 | Determinism Test | PENDING | - | | |
| 09 | TNT Cannon Stress Test | PENDING | - | | |
| 10 | Chain Reaction Test | PENDING | - | | |
| 11 | Max Blocks Per Tick Config | PENDING | - | | |
| 12 | Blocks Per Explosion Tick Config | PENDING | - | | |

## Known Issues / Bug Fixes

| # | Date | Test | Description | Severity | Status | Fix |
|---|---|---|---|---|---|---| | 01 | 2026-08-07 | 01 | `spawnexplosion` command spawns pig instead of TNT due to unsafe EntityType cast corrupting entity data watcher | CRITICAL | FIXED | Changed to use positional constructor `new PrimedTnt(level, x, y, z, null)` instead of registry lookup + unsafe cast |
| | 02 | 2026-08-08 | All | Explosion damage formula incorrect: used `radius` instead of `radius * 2.0F` for base damage calculation | CRITICAL | FIXED | Fixed damage formula in `ExplosionState.java` and `ExplosionProcessor.java` to use `radius * 2.0F` |

## Available Commands Reference

| Command | Description |
|---|---|
| `testcube [position] [size] [block]` | Create a cube of uniform blocks for testing (defaults: size=5, block=minecraft:dirt) |
| `sptestentity <entity> <count> <radius> <angle> [position]` | Spawn test entities (movement speed set to 0, line formation when angle<=0) |
| `sptestentity damageReport` | Report health of all alive entities |
| `sptestentity positionReport` | Report positions and motion of all entities |
| `spawnexplosion [position] [radius]` | Create explosion directly at position using level.explode() (instant explosion) |
| `explosionstats` | Display current explosion queue statistics and configuration |
| `benchmarkexplosion <iterations> [position]` | Stress test by spawning multiple TNT |
| `testentitydamage` | Compare damage between vanilla and chunked explosions |
| `testentityposition` | Report entity positions |

**Removed Commands:** `testclear`, `recordexplosion`, `compareexplosion`, `spawntnt`

---

## Test 01: Single TNT Block Destruction

**Status:** PENDING

**Purpose:** Verify that a single TNT explosion destroys the same blocks as vanilla Minecraft.

### Prerequisites
- Creative mode world
- Flat area at least 50x50 blocks

### Steps to Run
1. Open your Minecraft world (creative mode recommended)
2. Create a test cube (testcube replaces testclear): `/chunkedexplosions testcube 50 stone`
3. Stand at the center of the cube
4. Use spawnexplosion to create an explosion at your position: `/chunkedexplosions spawnexplosion`
5. Observe the crater formation (explodes instantly)

### What to Check
- [ ] Crater is roughly circular/oval from top view
- [ ] Maximum depth is approximately 4 blocks in center
- [ ] Maximum radius is approximately 4 blocks from center
- [ ] 30-70 blocks destroyed (typical TNT range)
- [ ] No blocks beyond explosion radius destroyed
- [ ] Obsidian (if placed) remains unbroken
- [ ] Bedrock (if placed) remains unbroken

### Expected Result
A clean, roughly spherical crater with consistent block destruction pattern matching vanilla Minecraft TNT behavior.

### Results Log
- Blocks destroyed: ___
- Crater shape: ___
- Pass/Fail: ___
- Notes: ___

---

## Test 02: Single TNT Entity Damage

**Status:** PENDING

**Purpose:** Verify that entity damage from explosions matches vanilla Minecraft.

### Prerequisites
- Creative mode world
- Flat area with concrete/stone platform (30x30 minimum)

### Steps to Run
1. Create test platform: `/chunkedexplosions testcube 30 stone`
2. Spawn test entities at various distances using `/chunkedexplosions sptestentity`:
   - Line formation: `/chunkedexplosions sptestentity iron_golem 5 4 0` (5 golems in a line, 2 blocks apart)
   - Circle formation: `/chunkedexplosions sptestentity iron_golem 8 4 45` (8 golems in a circle, 4 blocks away)
3. Stand at the center position (0,0,0 relative to entities)
4. Use spawnexplosion at your position: `/chunkedexplosions spawnexplosion`
5. Record entity health before and after using `/chunkedexplosions sptestentity damageReport`

### What to Check
- [ ] Center entity (closest to TNT) receives ~35 damage (dies)
- [ ] 2-block distance entity receives ~12-18 damage
- [ ] 4-block distance entity receives ~5-9 damage
- [ ] 6-block distance entity receives ~2-4 damage
- [ ] 8-block distance entity receives ~0-2 damage
- [ ] Damage decreases with distance

### Expected Result
Damage follows the vanilla formula with proper distance-based attenuation.

### Results Log
- Center entity damage: ___
- 2-block entity damage: ___
- 4-block entity damage: ___
- Pass/Fail: ___
- Notes: ___

---

## Test 03: Single TNT Knockback

**Status:** PENDING

**Purpose:** Verify that knockback from explosions matches vanilla Minecraft.

### Prerequisites
- Creative mode world
- Flat concrete or glass platform (50x50 minimum)

### Steps to Run
1. Create platform: `/chunkedexplosions testcube 50 glass`
2. Spawn entities at cardinal positions around center:
   - `/chunkedexplosions sptestentity iron_golem 4 4 0` (4 entities, 4 blocks away, at 0 degree intervals)
3. Note exact starting positions (use F3 debug screen)
4. Stand at center and use: `/chunkedexplosions spawnexplosion`
5. After entities stop moving, record final positions

### What to Check
- [ ] All entities pushed AWAY from explosion center
- [ ] Closer entities receive more knockback
- [ ] Farther entities receive less knockback
- [ ] Direction matches explosion-to-entity vector

### Expected Result
Knockback vectors point radially outward from explosion center with magnitude proportional to distance.

### Results Log
- Entity starting positions:
  - ID=694: (0.500, 161.000, 179.500) - North
  - ID=695: (4.500, 161.000, 175.500) - East
  - ID=696: (0.500, 161.000, 171.500) - South
  - ID=697: (-3.500, 161.000, 175.500) - West
- Entity final positions:
  - ID=694: (0.500, 161.000, 181.244) - North (+1.744 Z)
  - ID=695: (6.244, 161.000, 175.500) - East (+1.744 X)
  - ID=696: (0.500, 161.000, 169.756) - South (-1.744 Z)
  - ID=697: (-5.244, 161.000, 175.500) - West (-1.744 X)
- Knockback directions correct: YES (all radially outward)
- Knockback distance: 1.744 blocks (identical to vanilla)
- Pass/Fail: PASS
- Notes: Modded knockback is EXACTLY identical to vanilla - same distance (1.744 blocks), same direction (radially outward), same symmetry across all 4 cardinal positions.

---

## Test 04: Timing Modes - START

**Status:** PENDING

**Purpose:** Verify that START timing applies all effects immediately when explosion enters the active queue.

### Prerequisites
- Config file modified with:
  ```
  damageTiming=START
  knockbackTiming=START
  soundTiming=START
  particleTiming=START
  blocksPerExplosionTick=1
  maxBlocksPerTick=100
  ```

### Steps to Run
1. Set config values as shown above
2. Create test area: `/chunkedexplosions testcube 30 stone`
3. Stand at center
4. Spawn entities 2 blocks from center: `/chunkedexplosions sptestentity iron_golem 4 2 90`
5. Use `/chunkedexplosions spawnexplosion` at center
7. Observe the ORDER of effects

### What to Check
- [ ] Sound plays BEFORE any block is destroyed
- [ ] Particles spawn BEFORE any block is destroyed
- [ ] Damage applied BEFORE any block is destroyed
- [ ] Knockback applied BEFORE any block is destroyed
- [ ] No additional damage during block destruction
- [ ] No additional sound during block destruction
- [ ] No additional particles during block destruction

### Expected Result
All effects (sound, particles, damage, knockback) happen immediately at explosion start, then blocks destroy slowly (1 per tick due to config).

### Results Log
- Effect order observed: ___
- Pass/Fail: ___
- Notes: ___

---

## Test 05: Timing Modes - END

**Status:** PENDING

**Purpose:** Verify that END timing applies all effects after all blocks have been destroyed.

### Prerequisites
- Config file modified with:
  ```
  damageTiming=END
  knockbackTiming=END
  soundTiming=END
  particleTiming=END
  blocksPerExplosionTick=1
  maxBlocksPerTick=100
  ```

### Steps to Run
1. Set config values as shown above
2. Create test area: `/chunkedexplosions testcube 30 stone`
3. Stand at center
4. Spawn entities 2 blocks from center: `/chunkedexplosions sptestentity iron_golem 4 2 90`
5. Use `/chunkedexplosions spawnexplosion` at center
7. Observe the ORDER of effects

### What to Check
- [ ] NO sound plays during block destruction
- [ ] NO particles spawn during block destruction
- [ ] NO damage applied during block destruction
- [ ] NO knockback applied during block destruction
- [ ] Sound plays AFTER all blocks are destroyed
- [ ] Particles spawn AFTER all blocks are destroyed
- [ ] Damage applied AFTER all blocks are destroyed
- [ ] Knockback applied AFTER all blocks are destroyed

### Expected Result
Blocks destroy first (slowly, 1 per tick), then ALL effects happen at once at the end.

### Results Log
- Effect order observed: ___
- Pass/Fail: ___
- Notes: ___

---

## Test 06: Timing Modes - START_END

**Status:** PENDING

**Purpose:** Verify that START_END timing splits effects 50/50 between start and end of explosion.

### Prerequisites
- Config file modified with:
  ```
  damageTiming=START_END
  knockbackTiming=START_END
  soundTiming=START_END
  particleTiming=START_END
  soundVolumeSplit=true
  particleSplit=true
  blocksPerExplosionTick=1
  maxBlocksPerTick=100
  ```

### Steps to Run
1. Set config values as shown above
2. Create test area: `/chunkedexplosions testcube 30 stone`
3. Stand at center
4. Spawn entities 2 blocks from center: `/chunkedexplosions sptestentity iron_golem 4 2 90`
5. Use `/chunkedexplosions spawnexplosion` at center
7. Observe TWO phases of effects

### What to Check
- [ ] First damage event: ~50% of total damage
- [ ] Second damage event: ~50% of total damage
- [ ] Total damage equals vanilla damage
- [ ] Two distinct damage events observed
- [ ] Sound plays TWICE (once at start, once at end)
- [ ] Each sound at ~half volume
- [ ] Particles spawn TWICE (half at start, half at end)
- [ ] Total particle count equals vanilla count

### Expected Result
Effects split into two roughly equal phases - one at explosion start, one after all blocks destroyed.

### Results Log
- First phase damage: ___
- Second phase damage: ___
- Sound count: ___
- Pass/Fail: ___
- Notes: ___

---

## Test 07: Timing Modes - SPREAD

**Status:** PENDING

**Purpose:** Verify that SPREAD timing applies effects incrementally as blocks are destroyed.

### Prerequisites
- Config file modified with:
  ```
  damageTiming=SPREAD
  knockbackTiming=SPREAD
  soundTiming=SPREAD
  particleTiming=SPREAD
  blocksPerExplosionTick=1
  maxBlocksPerTick=100
  soundVolumeSplit=true
  particleSplit=true
  ```

### Steps to Run
1. Set config values as shown above
2. Create test area: `/chunkedexplosions testcube 30 stone`
3. Stand at center
4. Spawn entities 2 blocks from center: `/chunkedexplosions sptestentity iron_golem 4 2 90`
5. Use `/chunkedexplosions spawnexplosion` at center
6. Watch F3 debug screen for multiple damage events over multiple ticks

### What to Check
- [ ] Damage is applied MULTIPLE TIMES across ticks
- [ ] Damage per tick is proportional to blocks destroyed that tick
- [ ] Total damage equals vanilla damage
- [ ] No damage at START (before blocks)
- [ ] No single large damage event at END
- [ ] Sound plays MULTIPLE TIMES
- [ ] Particles spawn MULTIPLE TIMES

### Expected Result
Effects accumulate per tick as blocks are destroyed, applied once per tick (not per block).

### Results Log
- Number of damage events: ___
- Total damage: ___
- Pass/Fail: ___
- Notes: ___

---

## Test 08: Determinism Test

**Status:** PENDING

**Purpose:** Verify that the same explosion configuration produces identical results every time.

### Prerequisites
- Empty test world or resettable test area
- Config set to:
  ```
  blocksPerExplosionTick=50
  maxBlocksPerTick=1000
  damageTiming=START
  knockbackTiming=START
  ```

### Steps to Run
1. Create test platform: `/chunkedexplosions testcube 50 stone`
2. Stand at exact position (e.g., X=0, Y=64, Z=0 relative to platform)
3. Use `/chunkedexplosions spawnexplosion` at that position
4. Record: block count, crater shape (screenshot)
6. Reset platform to stone
7. Repeat 3 times total

### What to Check
- [ ] Block counts identical across all 3 runs
- [ ] Crater shapes match exactly (screenshot comparison)
- [ ] No variance in destruction pattern

### Expected Result
All 3 runs produce IDENTICAL craters - same blocks destroyed, same positions, same count.

### Results Log
- Run 1 block count: ___
- Run 2 block count: ___
- Run 3 block count: ___
- Craters identical: Y/N
- Pass/Fail: ___
- Notes: ___

---

## Test 09: TNT Cannon Stress Test

**Status:** PENDING

**Purpose:** Verify that the mod handles large-scale TNT cannon explosions correctly with stable performance.

### Prerequisites
- Creative mode world
- Large flat area (100x100 minimum)
- TPS monitoring (F3 debug screen)

### Steps to Run
1. Build a medium TNT cannon (5-20 TNT blocks)
2. Set config:
   ```
   blocksPerExplosionTick=50
   explosionsPerTick=100
   maxBlocksPerTick=1000
   ```
3. Record TPS before firing
4. Prime the cannon
5. Observe during explosion sequence
6. Record final TPS

### What to Check
- [ ] TPS never drops below 18 during entire sequence
- [ ] No client-side freezing (>500ms per frame)
- [ ] Crater forms correctly
- [ ] All TNT explodes
- [ ] Items spawn correctly
- [ ] No queue overflow warnings

### Expected Result
All TNT explodes with stable TPS (above 18), correct crater formation, no visual glitches.

### Results Log
- TNT count: ___
- TPS before: ___
- TPS during (min): ___
- TPS after: ___
- All TNT exploded: Y/N
- Pass/Fail: ___
- Notes: ___

---

## Test 10: Chain Reaction Test

**Status:** PENDING

**Purpose:** Verify that chain-reaction explosions (TNT priming more TNT) are handled correctly.

### Prerequisites
- Creative mode world
- Flat area

### Steps to Run
1. Build simple chain: `[TNT-1] [TNT-2] [TNT-3]` (within 4 blocks of each other)
2. Prime only TNT-1
3. Observe chain reaction
4. Count total explosions
5. Monitor TPS

### What to Check
- [ ] All TNT in chain explodes
- [ ] Chain reaction completes fully
- [ ] No explosion is missed or ignored
- [ ] TPS stays stable above 18
- [ ] Queue handles all explosions

### Expected Result
All TNT in chain explodes sequentially, each properly intercepted and queued by the mod.

### Results Log
- TNT count: ___
- Explosions observed: ___
- TPS stable: Y/N
- Pass/Fail: ___
- Notes: ___

---

## Test 11: Max Blocks Per Tick Config

**Status:** PENDING

**Purpose:** Verify that the global `maxBlocksPerTick` configuration is respected.

### Prerequisites
- Config set to:
  ```
  maxBlocksPerTick=20
  blocksPerExplosionTick=50
  explosionsPerTick=10
  ```

### Steps to Run
1. Set config values as shown above
2. Create test platform: `/chunkedexplosions testcube 30 stone`
3. Stand at center
4. Use `/chunkedexplosions benchmarkexplosion 5` to spawn 5 TNT
5. Count blocks destroyed in first tick

### What to Check
- [ ] First tick destroys at most 20 blocks (maxBlocksPerTick limit)
- [ ] Each tick destroys at most 20 blocks
- [ ] All explosions eventually complete
- [ ] No more than 20 blocks destroyed in any single tick

### Expected Result
Global cap of 20 blocks per tick is enforced across all explosions.

### Results Log
- First tick block count: ___
- Max blocks per tick observed: ___
- Pass/Fail: ___
- Notes: ___

---

## Test 12: Blocks Per Explosion Tick Config

**Status:** PENDING

**Purpose:** Verify that `blocksPerExplosionTick` correctly limits blocks destroyed by each explosion per tick.

### Prerequisites
- Config set to:
  ```
  blocksPerExplosionTick=5
  maxBlocksPerTick=1000
  explosionsPerTick=10
  ```

### Steps to Run
1. Set config values as shown above
2. Create test platform: `/chunkedexplosions testcube 30 stone`
3. Stand at center
4. Use `/chunkedexplosions spawnexplosion` at center
5. Count blocks destroyed per tick

### What to Check
- [ ] First tick destroys exactly 5 blocks (blocksPerExplosionTick limit)
- [ ] Each subsequent tick destroys max 5 blocks
- [ ] No tick shows more than limit for single explosion
- [ ] Explosion eventually completes

### Expected Result
Each explosion limited to 5 blocks per tick, spreading destruction over multiple ticks.

### Results Log
- Blocks per tick: ___
- Total ticks: ___
- Pass/Fail: ___
- Notes: ___

---

## Critical Bug Log

| # | Date | Test | Description | Severity | Status |
|---|---|---|---|---|---|
| 01 | 2026-08-07 | 01 | `spawnexplosion` command spawns pig instead of TNT due to unsafe EntityType cast corrupting entity data watcher | CRITICAL | FIXED |

---

## Summary

| Category | Tests Run | Passed | Failed | Critical Bugs |
|---|---|---|---|---|
| Overall | /12 | | | |
