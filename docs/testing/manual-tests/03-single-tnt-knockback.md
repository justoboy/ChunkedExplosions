# Test 1.3: Single TNT - Knockback Verification

## Purpose
Verify that knockback from explosions matches vanilla Minecraft.

## Setup

### World Preparation
1. Create a flat world with minimal friction surfaces
2. Build a large concrete or glass platform (50x50 minimum)
3. Position lighting to avoid mob spawning

### Entity Positioning
Place test entities at exact positions around the center point:

```
        [Entity 1]
           |
           |
[Entity 2]--TNT--[Entity 3]
           |
           |
        [Entity 4]
```

Positions (relative to TNT at 0,0,0):
- Entity 1: 4 blocks north (x=0, z=4)
- Entity 2: 4 blocks west (x=-4, z=0)  
- Entity 3: 4 blocks east (x=4, z=0)
- Entity 4: 4 blocks south (x=0, z=-4)

### Record Initial Positions
Use F3 to record exact coordinates:
- X: horizontal position
- Y: vertical position
- Z: depth position

## Execution

1. Ensure entities have no initial velocity (not moving)

2. Reset entity position to exact starting point

3. Record F3 coordinates precisely

4. Stand at center and use: `/chunkedexplosions spawnexplosion`
   Note: The spawnexplosion command now takes position first, then radius.

5. **Explosion triggers instantly**
6. After entities stop moving, record final positions

7. Calculate knockback vector:
   ```
   delta_x = final_x - initial_x
   delta_z = final_z - initial_z
   total_distance = sqrt(delta_x² + delta_z²)
   ```

## Expected Results

### Knockback Direction
Knockback should push entities AWAY from explosion center:
- Entity at (0, 4) should move further north (positive Z)
- Entity at (-4, 0) should move further west (negative X)
- Entity at (4, 0) should move further east (positive X)
- Entity at (0, -4) should move further south (negative Z)

### Knockback Magnitude
Using formula: `impact_factor = visibility × (1 - distance/radius)`

For entity at 4 blocks distance from TNT (radius=4):
- Normalized distance = 4/4 = 1.0
- Impact factor ≈ (1 - 1.0) × visibility = ~0.0 to ~0.25
- Expected knockback distance: ~0.5 to 2 blocks

For entity at 2 blocks distance:
- Normalized distance = 2/4 = 0.5
- Impact factor ≈ (1 - 0.5) × visibility = ~0.25 to ~0.5
- Expected knockback distance: ~1 to 3 blocks

For entity at center (0 blocks):
- Normalized distance = 0/4 = 0
- Impact factor ≈ (1 - 0) × visibility = ~0.7 to ~1.0
- Expected knockback distance: ~2 to 6 blocks

### Protection Enchantment Effect
If testing with players wearing protection armor:
- Protection enchantments reduce knockback
- Enchantment level IV provides maximum reduction (~70-80%)
- Vanilla knockback dampening formula: `factor = 10 / (10 + protection_level)`

## Pass Criteria

- [ ] All entities are pushed AWAY from explosion center
- [ ] No entity is pulled toward explosion
- [ ] Closer entities receive more knockback
- [ ] Farther entities receive less knockback
- [ ] Direction is consistent with explosion-to-entity vector

## Anomaly Check

Watch for these issues:
- **Inverted knockback:** Entities are pulled toward explosion
- **Random direction:** Knockback direction varies unpredictably
- **No knockback:** Entities are damaged but not pushed
- **Excessive knockback:** Entities pushed more than 10 blocks
- **No distance correlation:** All entities receive same knockback

## Direction Verification

After explosion, verify direction matches expected:
```
For entity at (4, 0) relative to TNT at (0, 0):
  - Final position should be approximately (4+, 0)
  - X coordinate should increase (positive knockback)
  - Z coordinate should stay roughly same

For entity at (0, 4) relative to TNT at (0, 0):
  - Final position should be approximately (0, 4+)
  - Z coordinate should increase (positive knockback)
  - X coordinate should stay roughly same
```

## Configuration Notes

If using START_END timing for knockback:
- First 50% of knockback applied at start
- Second 50% applied at end
- Total still equals expected knockback

If using SPREAD timing:
- Knockback accumulates gradually
- Applied once per tick
- Total still equals expected knockback

## Documentation

For each test:
1. Record initial coordinates of all entities
2. Record final coordinates after movement stops
3. Calculate delta for each entity
4. Verify direction matches expected
5. Calculate total distance moved
6. Document any anomalies
