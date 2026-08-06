# Test 1.5: Timing Modes - START

## Purpose
Verify that START timing applies all effects immediately when explosion enters the active queue.

## Prerequisites
- Configured timing: `damageTiming=START`, `knockbackTiming=START`, `soundTiming=START`, `particleTiming=START`
- Low `blocksPerExplosionTick` value (recommended: 1-5) to clearly observe timing differences

## Setting Configuration

Set the following configuration values:
```
# Via config file or commands
damageTiming=START
knockbackTiming=START
soundTiming=START
particleTiming=START
blocksPerExplosionTick=1    # Low value to observe block destruction
maxBlocksPerTick=100        # High enough to not interfere
```

## Test Setup

### World Preparation
1. Create a flat world with concrete/stone platform
2. Build a platform at least 20x20

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

5. Observe and record the ORDER of effects:
   - When damage is applied
   - When sound plays
   - When particles spawn
   - When block destruction begins

## Expected Results

### Effect Order (START timing)
```
Frame 0:  TNT primed
Frame X:  Explosion intercepted by mod
Frame Y:  Explosion enters active queue
          ──► DAMAGE APPLIED (immediately)
          ──► KNOCKBACK APPLIED (immediately)
          ──► SOUND PLAYS (immediately)
          ──► PARTICLES SPAWN (immediately)
Frame Y+1: Block 1 destroyed
Frame Y+2: Block 2 destroyed
Frame Y+3: Block 3 destroyed
...      (blocks continue destroying over subsequent ticks)
```

### Observable Symptoms

**At START (pre-block-destroy):**
- Entities immediately lose health
- Entities are pushed by knockback
- Explosion sound plays once
- Explosion particles (EXPLOSION or EXPLOSION_EMITTER) spawn

**During destruction:**
- No additional sound
- No additional particles
- No additional damage (entities already marked as damaged)
- Blocks destroy one at a time (due to low blocksPerExplosionTick)

## Pass Criteria

- [ ] Sound plays BEFORE any block is destroyed
- [ ] Particles spawn BEFORE any block is destroyed
- [ ] Damage applied BEFORE any block is destroyed
- [ ] Knockback applied BEFORE any block is destroyed
- [ ] No additional damage during block destruction
- [ ] No additional sound during block destruction
- [ ] No additional particles during block destruction

## Comparison with Other Timing Modes

### START vs END (START timing should be faster):
```
START:  [Effects] ---[Blocks]---[Blocks]---[Blocks]
END:    [Blocks]---[Blocks]---[Blocks] ---[Effects]
```

### START with vs without protection:
```
START:  [Damage@0.5] ----[Effects@1.0]
START:  [Effects@1.0] ----[Blocks...]
```

## Anomaly Check

Watch for these issues:
- **Late effects:** Damage/sound/particles happen after blocks start destroying
- **Extra effects:** Multiple sounds or multiple damage applications
- **No damage:** Entities take no damage at all
- **No sound:** No sound plays at all
- **No particles:** No particles spawn at all

## Documentation

For each test:
1. Record config values used
2. Note whether all effects happened correctly at start
3. Document any delays observed
4. Record any anomalies
