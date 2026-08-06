# Test 1.9: Determinism Test

## Purpose
Verify that the same explosion configuration produces identical results every time. This is a critical test for the mod's deterministic RNG system.

## Background

Vanilla Minecraft uses `RandomSource` which may produce different results depending on:
- Current world state
- Other events happening
- Server tick timing
- Thread scheduling

The Chunked Explosions mod uses deterministic seeding based on:
- Source entity ID
- Explosion position
- Explosion radius

This ensures: "Same seed = Same results" every time.

## Prerequisites

- Ability to reset world or rebuild test area identically
- `blocksPerExplosionTick` set high enough to complete in few ticks
- Empty test world with no other entities or blocks

## Setting Configuration

```
blocksPerExplosionTick=50
maxBlocksPerTick=1000
damageTiming=START
knockbackTiming=START
soundTiming=START
particleTiming=START
```

## Test A: Block Destruction Determinism

### Setup
1. Create flat world with uniform block type (stone recommended)
2. Build a 50x50 platform at Y=64
3. Clear any nearby entities
4. Record F3 coordinates for TNT placement

### Execution
1. Place TNT at EXACT same position: X=0, Y=64, Z=0 (relative to platform)
2. Prime TNT
3. Record blocks destroyed count
4. Record crater shape (screenshot from same camera angle)
5. Reset platform to stone
6. Repeat 3 times

### Expected Results

Every run should produce:
- **Identical block count** (e.g., exactly 47 blocks destroyed each time)
- **Identical crater shape** (blocks at same positions)
- **Identical item drops** (same items at same positions)

### Pass Criteria
- [ ] Block counts are identical across all runs
- [ ] Crater shapes match exactly (screenshot comparison)
- [ ] No variance in destruction pattern

### Anomaly Detection

If results differ:
- Block counts differ: Deterministic RNG not working
- Crater shapes differ: Seed calculation has issues
- Some blocks always destroyed: May indicate non-random pattern

---

## Test B: Entity Damage Determinism

### Setup
1. Create flat world
2. Place 5 iron golems at exact positions around center
3. Use commands to ensure exact positions:
   ```
   /summon iron_golem ~2 ~ ~ {Health:200.0f}
   /summon iron_golem ~-2 ~ ~ {Health:200.0f}
   /summon iron_golem ~ ~2 ~ {Health:200.0f}
   /summon iron_golem ~ ~-2 ~ {Health:200.0f}
   /summon iron_golem ~ ~ ~ {Health:200.0f}  # Center
   ```

### Execution
1. Record health of all golems
2. Place TNT at exact center
3. Prime TNT
4. Record damage taken by each golem
5. Reset golems to full health
6. Repeat 3 times

### Expected Results

Every run should produce:
- **Identical damage** to each golem
- **Identical knockback** for each golem
- **Identical final positions**

### Pass Criteria
- [ ] Damage to each entity is identical across runs
- [ ] Knockback displacement is identical across runs
- [ ] Final positions are identical across runs

### Anomaly Detection

If results differ:
- Damage varies: Entity effect calculation not deterministic
- Knockback varies: Vector calculation uses non-deterministic values
- Position varies: Entity position affected by other factors

---

## Test C: Identical Seed Verification

### Purpose
Verify that using the same seed produces identical results.

### Setup
1. Create two identical test areas
2. Use `/spawntnt <seed>` command if available
3. If no command, manually ensure identical starting conditions

### Execution
1. In Area A: Place TNT at (X=0, Y=64, Z=0)
2. Prime TNT
3. Record results
4. Reset Area A or use Area B
5. Place TNT at same position (X=0, Y=64, Z=0)
6. Prime TNT
7. Compare with Area A results

### Expected Results
- IDENTICAL results in both areas (same seed = same seed)

### Pass Criteria
- [ ] Both areas show identical destruction
- [ ] Both areas show identical entity effects
- [ ] Screenshots match exactly

---

## Test D: Different Seeds Produce Different Results

### Purpose
Verify that changing seed produces different results.

### Execution
1. Place TNT at position (X=0, Y=64, Z=0), prime, record
2. Place TNT at position (X=10, Y=64, Z=0), prime, record
3. Compare patterns

### Expected Results
- Different positions = different seed = different patterns
- Results should DIFFER (not identical!)

### Pass Criteria
- [ ] Different positions produce different crater patterns
- [ ] Different blocks destroyed in each case
- [ ] Results are not identical

---

## Documentation

For each determinism test:
1. Record starting conditions (position, materials, entities)
2. Record results (block count, damage values, positions)
3. Compare with expected determinism
4. Document any anomalies
5. Note seed calculation method used

## Troubleshooting Determinism Issues

### Same position, different results:
- Check if seed includes position coordinates
- Verify RandomSource is being seeded properly
- Check if other world events affect RNG

### Very similar but not identical results:
- May indicate floating point precision issues
- Check if BlockPos containing uses consistent rounding
- Verify block destruction order is deterministic

### Completely random patterns:
- Deterministic RNG not being used
- Check splittable random seeding in ExplosionState
- Verify seed calculation formula
