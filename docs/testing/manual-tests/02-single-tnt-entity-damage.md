# Test 1.2: Single TNT - Entity Damage Verification

## Purpose
Verify that entity damage from explosions matches vanilla Minecraft.

## Setup

### World Preparation
1. Create a flat world (superflat preset works well)
2. Build a concrete or stone platform at least 30x30 blocks
3. Disable mob spawning to avoid interference:
   ```
   /gamerule doMobSpawning false
   ```

### Entity Positioning

**Manual Method:**
Set up test entities at exact distances from the center point using `/summon`:
```
/summon iron_golem ~4 ~ ~ {Health:200.0f}
/summon iron_golem ~2 ~ ~ {Health:200.0f}
/summon iron_golem ~0 ~ ~ {Health:200.0f}
/summon iron_golem ~-2 ~ ~ {Health:200.0f}
/summon iron_golem ~-4 ~ ~ {Health:200.0f}
```

**Dev Command Method (Recommended):**
Use the `/sptestentity` command to spawn entities in a precise pattern:
```
# Spawn 5 iron golems in a line, 2 blocks apart
/chunkedexplosions sptestentity iron_golem 5 4 0

# Spawn 8 iron golems in a circle, 4 blocks away
/chunkedexplosions sptestentity iron_golem 8 4 45

# Spawn at specific height
/chunkedexplosions sptestentity iron_golem 5 2 0
```

### Health Recording
**Manual Method:**
Before explosion, record health using F3 debug screen.

**Dev Command Method:**
Use `/sptestentity damageReport` before and after the explosion to automatically track health changes:
```
# Before explosion
/chunkedexplosions sptestentity damageReport

# After explosion
/chunkedexplosions sptestentity damageReport
```

## Execution

### Using Dev Commands

1. **Prepare environment:**
   ```
   /chunkedexplosions testcube 40 stone
   ```

2. **Spawn test entities:**
   ```
   /chunkedexplosions sptestentity iron_golem 5 4 0
   ```

3. **Record initial health:**
   ```
   /chunkedexplosions sptestentity damageReport
   ```

4. **Stand at center and spawn explosion:**
   ```
   /chunkedexplosions spawnexplosion
   ```

5. **Explosion triggers instantly**

6. **Check damage:**
   ```
   /chunkedexplosions sptestentity damageReport
   ```

### Manual Execution

1. Ensure all entities have full health (20 hearts / 10 damage points for golems, 20 hearts for players)

2. Stand at exact center (0, 0, 0 relative to platform)

3. Prepare to record damage (have screenshots ready or use mod)

4. Use `/chunkedexplosions spawnexplosion`
   Note: The spawnexplosion command now takes position first, then radius.

5. **Explosion triggers instantly**

6. Record health AFTER explosion using `/chunkedexplosions sptestentity damageReport`

7. If entities survive, reset health for next test

## Expected Results

### Damage Calculation Formula
Vanilla damage formula:
```
damage = ((impact² + impact) / 2 × 7 × radius + 1)

where:
  impact = visibility × (1 - distance/radius)
  visibility = line-of-sight percentage (0.0 to 1.0)
```

### Expected Damage at Various Distances (TNT radius=4, full visibility)

| Distance | Impact Factor | Expected Damage | Hearts Remaining |
|----|---|---|--|
| 0 (center) | 1.0 | ~35 | -15 (death) |
| 2 blocks | ~0.75 | ~15 | 5 remaining (for golem) |
| 4 blocks | ~0.5 | ~7 | 13 remaining (for golem) |
| 6 blocks | ~0.25 | ~3 | 17 remaining (for golem) |
| 8 blocks | 0.0 | ~1 | 19 remaining (for golem) |

### Visibility Impact
If an entity is behind a solid block:
- Visibility drops to ~0.0-0.3 depending on block coverage
- Damage decreases proportionally

## Pass Criteria

- [ ] Center entity receives ~35 damage (dies from TNT)
- [ ] 2-block entity receives ~12-18 damage
- [ ] 4-block entity receives ~5-9 damage
- [ ] 6-block entity receives ~2-4 damage
- [ ] 8-block entity receives ~0-2 damage
- [ ] Hidden entity (behind wall) receives significantly less damage

## Anomaly Check

Watch for these issues:
- **Excessive damage:** Any entity receiving significantly more damage than expected
- **Insufficient damage:** Any entity receiving significantly less damage
- **All-or-nothing:** If all entities receive identical damage regardless of distance
- **No visibility effect:** If hidden entities receive same damage as visible ones

## Configuration Notes

If using START_END timing:
- Damage should be split across two events
- Total still equals expected damage
- First 50% at start, second 50% at end

If using SPREAD timing:
- Damage accumulates across block destruction
- Apply once per tick after block destruction
- Total still equals expected damage

## Documentation

For each test:
1. Record initial health of all entities
2. Record final health after explosion
3. Calculate damage taken
4. Compare to expected values
5. Document any anomalies
