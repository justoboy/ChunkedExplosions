# Test 1.7: Timing Modes - START_END

## Purpose
Verify that START_END timing splits effects 50/50 between start and end of explosion.

## Prerequisites
- Configured timing: `damageTiming=START_END`, `soundTiming=START_END`, `particleTiming=START_END`, `knockbackTiming=START_END`
- `soundVolumeSplit=true` and `particleSplit=true` for proper splitting

## Setting Configuration

Set the following configuration values:
```
damageTiming=START_END
knockbackTiming=START_END
soundTiming=START_END
particleTiming=START_END
soundVolumeSplit=true
particleSplit=true
blocksPerExplosionTick=1    # Low value to observe both phases
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

3. Prepare to observe both phases

4. Prime TNT

5. Observe and document:
   - Effects at START (when explosion enters active queue)
   - Effects at END (when all blocks destroyed)

## Expected Results

### Effect Order (START_END timing)
```
Frame 0:  TNT primed
Frame X:  Explosion intercepted
Frame Y:  Explosion enters active queue
          ──► 50% DAMAGE APPLIED (FIRST HALF)
          ──► 50% KNOCKBACK APPLIED (FIRST HALF)
          ──► 50% SOUND PLAYS (volume=2.0 instead of 4.0)
          ──► 50% PARTICLES SPAWN (2 instead of 4)
Frame Y+1: Block 1 destroyed (first of N blocks)
Frame Y+2: Block 2 destroyed
Frame Y+3: Block 3 destroyed
...      (all blocks destroyed)
Frame Z:  All blocks destroyed
          ──► 50% DAMAGE APPLIED (SECOND HALF)
          ──► 50% KNOCKBACK APPLIED (SECOND HALF)
          ──► 50% SOUND PLAYS (volume=2.0)
          ──► 50% PARTICLES SPAWN (2 more)
```

### Observable Symptoms

**At START (first half):**
- Entities lose ~50% of expected damage
- Entities are pushed ~50% of expected knockback
- Sound plays at ~half volume (notice this?)
- ~Half the normal particle count spawns

**During destruction:**
- No additional effects (effects already applied)
- Blocks destroy normally

**At END (second half):**
- Entity receives remaining 50% damage
- Final push of remaining 50% knockback
- Another sound plays at ~half volume
- Additional particles spawn (completing full particle count)

## Pass Criteria

### Damage (START_END)
- [ ] First damage event: ~50% of total damage
- [ ] Second damage event: ~50% of total damage
- [ ] Total damage equals vanilla damage
- [ ] Two distinct damage events observed

### Sound (START_END)
- [ ] Sound plays TWICE (once at start, once at end)
- [ ] Each occurrence: ~half volume
- [ ] Combined volume: full volume (or less with split=false)

### Particles (START_END)
- [ ] Particles spawn TWICE (half at start, half at end)
- [ ] Total particle count equals vanilla particle count

### Knockback (START_END)
- [ ] First knockback event: ~50% of expected
- [ ] Second knockback event: ~50% of expected
- [ ] Total displacement equals expected

#### Sound Volume Split

When `soundVolumeSplit=true`:
- First sound: 50% volume (~2.0)
- Second sound: 50% volume (~2.0)

When `soundVolumeSplit=false`:
- First sound: 100% volume (4.0)
- Second sound: 100% volume (4.0)
- Note: This may result in double the audible volume!

## Anomaly Check

Watch for these issues:
- **All or nothing:** Effects happen all at once (appears as START or END instead of START_END)
- **Missing phases:** Only start or only end effects observed
- **Wrong split:** Not 50/50 split (e.g., 70/30 or 30/70)
- **Extra sound:** More than 2 sound events
- **Missing sound:** Only 1 sound event instead of 2

## Comparison Table

| Configuration | At START | At END | Total |
|---------------|----|---|---|
| START | 100% | 0% | 100% |
| END | 0% | 100% | 100% |
| START_END (split=true) | 50% | 50% | 100% |
| START_END (split=false) | 100% | 100% | 200% |

## Configuration Effects

### With blocksPerExplosionTick=1 and 50 blocks:
- START occurs immediately when explosion queued
- END occurs after ~50 ticks
- If you look away, you may miss START effects!

### With blocksPerExplosionTick=50 and 50 blocks:
- START and END occur nearly simultaneously (1 tick apart)
- Effectively appears as single event
- Harder to distinguish START_END from START or END

## Notes

- **Sound Volume:** May be subtle whether soundVolumeSplit=true or false
- **Particle Count:** May be hard to count exact particles
- **Damage:** Most verifiable - use exact health tracking for best evidence
- **Knockback:** Position before/after can confirm split

## Documentation

For each test:
1. Record config values used
2. Document first damage amount (should be ~50%)
3. Document second damage amount (should be ~50%)
4. Total should equal expected vanilla damage
5. If soundVolumeSplit=false, note that twice full sounds play
6. Document any anomalies
