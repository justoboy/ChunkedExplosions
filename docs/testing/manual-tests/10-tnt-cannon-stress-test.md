# Test 3.2: TNT Cannon Stress Test

## Purpose
Verify that the mod handles large-scale TNT cannon explosions correctly with stable performance.

## Prerequisites
- Knowledge of building TNT cannons
- Access to creative mode for building
- Ability to observe TPS (F3 debug screen or mod)

## Building a Test Cannon

Build a medium-sized TNT cannon:

**Option A: Simple 5-TNT Cannon**
```
   [TNT][TNT][TNT][TNT][TNT]  <- Main charge
         [Lever]
   [TNT][TNT][TNT]              <- Primers
         [Lever]
   [Block base]
```

**Option B: Large 20-TNT Cannon**
```
   [TNT]x10  <- Main charge (10 TNT in line)
       [Lever]
   [TNT]x5   <- Primers (5 TNT)
       [Lever]
   [Block base]
```

## Configuration

Set recommended configuration for stress test:
```
blocksPerExplosionTick=50
explosionsPerTick=100
maxBlocksPerTick=1000
damageTiming=START
soundTiming=START
particleTiming=START
```

## Execution

1. Build cannon on flat surface with clear area around it

2. Record TPS before firing (should be 20.0 or 19.5+):
   ```
   F3 screen shows TPS line
   ```

3. Prime the cannon (use lever or command)

4. Observe during explosion sequence:
   - Watch TPS continuously
   - Note any stuttering or freezing
   - Observe crater formation

5. After all explosions complete:
   - Record final TPS
   - Measure crater dimensions
   - Check for missing or duplicated blocks

## Expected Results

### Performance
- **Initial TPS:** 20.0 or 19.5+ (normal)
- **During explosions:** TPS may dip briefly but should remain above 18
- **Final TPS:** Should return to 20.0 or 19.5+
- **No freezes:** No frame should freeze for more than ~500ms

### Crater
- Crater should form correctly
- Shape should match TNT cannon design
- No missing chunks or visual glitches
- All blocks within range destroyed

### Block Drops
- Items should spawn correctly
- No duplicate items
- No missing drops (within reasonable variance)

## Pass Criteria

- [ ] TPS never drops below 18 during entire sequence
- [ ] No client-side freezing (>500ms per frame)
- [ ] Crater forms correctly
- [ ] Crater shape matches TNT cannon design
- [ ] No missing or duplicated blocks
- [ ] All TNT explodes
- [ ] Items spawn correctly
- [ ] Server handles queue without overflow

## Anomaly Detection

### TPS Issues
- TPS drops below 18: Configuration may need tuning
- TPS doesn't recover: Queue may be backing up
- Intermittent drops: May indicate burst processing

### Visual Issues
- Missing blocks: Block destruction failing
- Duplication: Item/entity duplication bug
- Crash: Fatal error during processing

### Queue Issues
- "Queue overflow" message: maxQueueSize too low
- Explosions not completing: blocksPerExplosionTick too low
- Long processing time: Queue size or global cap too restrictive

## Configuration Adjustments

### If TPS drops too low:
```
# Reduce blocks per tick
blocksPerExplosionTick=25

# Reduce global cap
maxBlocksPerTick=500
```

### If explosions queue up too long:
```
# Increase throughput
blocksPerExplosionTick=100
maxBlocksPerTick=2000
```

### If queue overflows:
```
# Increase queue size
maxQueueSize=1000
```

## Documentation

For each test:
1. Record TNT count used
2. Record TPS before, during, and after
3. Document any anomalies
4. Measure final crater dimensions
5. Note configuration values used
6. Take screenshots

## Performance Comparison Table

| TNT Count | blocksPerTick | TPS Min | Notes |
|----------|---------------|-----|---|
| 5        | 50            | ?   | Record baseline |
| 10       | 50            | ?   | Medium load |
| 20       | 50            | ?   | High load |
| 50       | 50            | ?   | Extreme load |

## Notes

- Large TNT cannons may take 10-20 seconds to complete with low blocksPerExplosionTick
- Document actual completion time for each test
- Compare completion time vs TPS performance
- Consider using vanilla world as comparison baseline
