# Test 1.8: Timing Modes - SPREAD

## Purpose
Verify that SPREAD timing applies effects incrementally as blocks are destroyed.

## Prerequisites
- Configured timing: `damageTiming=SPREAD`, `knockbackTiming=SPREAD`, `soundTiming=SPREAD`, `particleTiming=SPREAD`

## Setting Configuration

Set the following configuration values:
```
damageTiming=SPREAD
knockbackTiming=SPREAD
soundTiming=SPREAD
particleTiming=SPREAD
blocksPerExplosionTick=1    # Very low to observe accumulation
maxBlocksPerTick=100
soundVolumeSplit=true
particleSplit=true
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

2. Record entity positions before explosion

3. Place TNT at center

4. Observe each tick carefully

5. Document effects as they accumulate

## Expected Results

### Effect Order (SPREAD timing)
```
Frame 0:  TNT primed
Frame X:  Explosion intercepted
Frame Y:  Explosion enters active queue
          ──► No effects yet (accumulation phase starts)
Frame Y+1: Block 1 destroyed
          ──► Accumulate 1/N of total damage for each entity
          ──► Accumulate 1/N of total knockback for each entity
Frame Y+2: Block 2 destroyed
          ──► Accumulate another 1/N chunk
Frame Y+3: Block 3 destroyed
          ──► Apply accumulated effects once
          ──► Clear accumulators
          ──► Reset block counter
...      (repeat each tick)
Frame Z:  All N blocks destroyed
          ──► Apply any remaining accumulated effects
```

### Observable Symptoms

**During each tick with block destruction:**
- Some blocks destroyed this tick (max blocksPerExplosionTick)
- Accumulated damage applied ONCE per tick (not per block)
- Accumulated knockback applied ONCE per tick
- Sound plays based on blocks destroyed this tick
- Particles spawn based on blocks destroyed this tick

**Between ticks:**
- No effects occur
- Entity health unchanged
- Entity positions unchanged

**Key difference from START/END/START_END:**
- Effects happen MULTIPLE TIMES (spread across ticks)
- Not just at START or END
- Each tick with block destruction may trigger effects

## Pass Criteria

### Damage (SPREAD)
- [ ] Damage is applied MULTIPLE TIMES across ticks
- [ ] Damage per tick is proportional to blocks destroyed that tick
- [ ] Total damage equals vanilla damage
- [ ] No damage at START (before blocks)
- [ ] No single large damage event at END

### Accumulation Behavior
- [ ] Each tick accumulates effects separately
- [ ] Effects applied once per tick (not per block)
- [ ] Accumulators reset after application
- [ ] Total accumulated equals expected total

### Sound (SPREAD)
- [ ] Sound plays MULTIPLE TIMES (once per tick with blocks)
- [ ] Volume proportional to blocks destroyed that tick
- [ ] Total sound energy ≈ single explosion sound

### Particles (SPREAD)
- [ ] Particles spawn MULTIPLE TIMES (once per tick with blocks)
- [ ] Count proportional to blocks destroyed that tick
- [ ] Total particles ≈ vanilla particle count

## Anomaly Check

Watch for these issues:
- **All at once:** Effects happen all at once (appears as START, END, or START_END)
- **Per-block application:** Effects applied per block instead of accumulated per tick
- **No accumulation:** Each tick resets without accumulating
- **Double application:** Same effect applied multiple times within same tick
- **Infinite accumulation:** Accumulator never resets, all effects at very end

## Verification with Low blocksPerExplosionTick

With `blocksPerExplosionTick=1`:
- 1 block destroyed per tick
- 1/N damage accumulated per tick
- Applied once per tick
- If 50 blocks total: 50 ticks of partial damage

With `blocksPerExplosionTick=50`:
- 50 blocks destroyed per tick
- All damage accumulated in 1 tick
- Applied once
- Looks like START_END or END timing!

## Configuration Effects

### soundVolumeSplit=true
- Each tick: volume = (blocks_this_tick / total_blocks) × full_volume
- If 5 blocks this tick out of 50 total: volume = 5/50 × 4.0 = 0.4

### soundVolumeSplit=false
- Each tick: volume = full_volume
- May result in much higher total sound

## Test with Multi-Tick Observation

To properly verify SPREAD timing:

1. Set `blocksPerExplosionTick=1`
2. Place TNT with entity at 2 blocks distance
3. Start F3 debug recording
4. Prime TNT
5. Watch F3 damage log over multiple ticks
6. Verify damage appears incrementally

Expected log pattern (50 blocks, damage=15):
```
Tick Y+1: Block 1 destroyed, +0.3 damage applied
Tick Y+2: Block 2 destroyed, +0.3 damage applied
Tick Y+3: Block 3 destroyed, +0.3 damage applied
...
Tick Y+50: Block 50 destroyed, +0.3 damage applied
Total: 15 damage (correct)
```

## Documentation

For each test:
1. Record config values used
2. Note blocksPerExplosionTick setting
3. Count number of ticks with damage application
4. Verify total damage equals expected
5. Document any accumulation issues
