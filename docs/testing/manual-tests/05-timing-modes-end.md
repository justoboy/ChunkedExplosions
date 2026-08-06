# Test 1.6: Timing Modes - END

## Purpose
Verify that END timing applies all effects after all blocks have been destroyed.

## Prerequisites
- Configured timing: `damageTiming=END`, `knockbackTiming=END`, `soundTiming=END`, `particleTiming=END`
- Low `blocksPerExplosionTick` value (recommended: 1-5) to clearly observe timing differences

## Setting Configuration

Set the following configuration values:
```
damageTiming=END
knockbackTiming=END
soundTiming=END
particleTiming=END
blocksPerExplosionTick=1    # Low value to observe delay
maxBlocksPerTick=100
```

## Test Setup

### Entity Setup
Place test entities 2 blocks from center:
```
        [Entity]
           |
    [Entity] TNT [Entity]
           |
        [Entity]
```

## Execution

1. Record entity health BEFORE explosion

2. Place TNT at center

3. Prepare to observe effects sequence

4. Prime TNT

5. Observe the ORDER of effects:
   - When block destruction begins
   - When each block is destroyed
   - When damage is finally applied

## Expected Results

### Effect Order (END timing)
```
Frame 0:  TNT primed
Frame X:  Explosion intercepted by mod
Frame Y:  Explosion enters active queue
          ──► No damage applied (waiting for END)
          ──► No knockback (waiting for END)
          ──► No sound (waiting for END)
          ──► No particles (waiting for END)
Frame Y+1: Block 1 destroyed
Frame Y+2: Block 2 destroyed
Frame Y+3: Block 3 destroyed
...      (blocks continue...)
Frame Y+N: All blocks destroyed
          ──► DAMAGE APPLIED (now!)
          ──► KNOCKBACK APPLIED (now!)
          ──► SOUND PLAYS (now!)
          ──► PARTICLES SPAWN (now!)
```

### Observable Symptoms

**During destruction:**
- Blocks destroy one at a time
- NO sound plays
- NO particles spawn
- Entities take NO damage yet
- Entities are NOT pushed yet

**At END (after last block):**
- Explosion sound plays ONCE
- Explosion particles spawn
- All entities lose health at once
- All entities are pushed simultaneously

## Pass Criteria

- [ ] Sound plays AFTER all blocks are destroyed
- [ ] Particles spawn AFTER all blocks are destroyed
- [ ] Damage applied AFTER all blocks are destroyed
- [ ] Knockback applied AFTER all blocks are destroyed
- [ ] No damage during block destruction ticks
- [ ] No sound during block destruction ticks
- [ ] No particles during block destruction ticks

## Anomaly Check

Watch for these issues:
- **Early effects:** Damage/sound/particles happen before all blocks destroyed
- **Missing effects:** No effects at end
- **Partial effects:** Only some effects delayed, others happen at start

## Configuration Effects

### Low blocksPerExplosionTick = 1
With `blocksPerExplosionTick=1`, a typical TNT explosion destroying 50 blocks will take:
- 50 ticks to complete (approximately 2.5 seconds)
- No effects during this entire time
- All effects at once at the end

### High blocksPerExplosionTick = 50
With `blocksPerExplosionTick=50`, same explosion will complete in ~1 tick:
- Effects will appear at nearly same time as block destruction
- May look similar to END timing being "at end"
- Harder to distinguish visually

## Documentation

For each test:
1. Record config values used
2. Count blocks destroyed before effects applied
3. Note any anomalies
4. Record if effects applied at correct time
