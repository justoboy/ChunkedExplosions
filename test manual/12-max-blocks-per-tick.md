# Test 12: Max Blocks Per Tick Verification

## Purpose
Verify that the global `maxBlocksPerTick` configuration is respected and limits total block destruction across all explosions.

## Prerequisites
- Multiple TNT blocks available
- Flat world with breakable blocks (stone, dirt)
- Ability to observe block counts

## Setting Configuration

Set restrictive values to verify the limit:
```
maxBlocksPerTick=20
blocksPerExplosionTick=50
explosionsPerTick=10
damageTiming=START
soundTiming=START
particleTiming=START
```

## Test Setup

1. Build a 30x30 platform of stone or dirt

2. Place 5 TNT blocks arranged to potentially destroy many blocks:
   ```
   [TNT]   [TNT]
       [TNT]
   [TNT]   [TNT]
   ```

3. Record initial block count in area

## Execution

1. Prime all 5 TNT blocks simultaneously

2. Observe first tick closely:
   - Count blocks destroyed in first tick
   - Note how many explosions started processing

3. Continue observing subsequent ticks

4. Record total time to complete all explosions

## Expected Results

### First Tick
- Total blocks destroyed should NOT exceed `maxBlocksPerTick=20`
- Multiple explosions may contribute blocks (up to global cap)
- Once cap reached, remaining explosions wait for next tick

### Subsequent Ticks
- Each tick respects global cap of 20 blocks
- Explosions continue processing on subsequent ticks
- All explosions eventually complete

### If maxBlocksPerTick Was Ignored:
- All 5 TNT would destroy ~200-300 blocks in first tick
- TPS would drop significantly
- Visual burst of block destruction

## Pass Criteria

- [ ] First tick destroys at most 20 blocks
- [ ] Each tick destroys at most 20 blocks (once multiple explosions active)
- [ ] All explosions eventually complete
- [ ] No more than 20 blocks destroyed in any single tick

## Anomaly Detection

### Issues to Watch For:
- **Cap exceeded:** More than 20 blocks destroyed in any tick
- **Single explosion ignores other:** One explosion destroys 50 blocks when it should be limited
- **Cap per explosion:** Each explosion limited but global cap not enforced across all

## Configuration Effects

When `maxBlocksPerTick` is HIGH (unlimited):
```
maxBlocksPerTick=10000
- All 5 TNT could destroy all blocks in first tick
- Visual burst
- TPS may drop
```

When `maxBlocksPerTick` is LOW (restrictive):
```
maxBlocksPerTick=20
- Blocks destroyed slowly over many ticks
- Smooth performance
- May take longer to complete
```

## Documentation

For each test:
1. Record `maxBlocksPerTick` setting
2. Count blocks destroyed in first tick
3. Count blocks destroyed in subsequent ticks
4. Verify no tick exceeds the limit
5. Document total time to completion

## Related Tests

- [13-blocks-per-explosion-tick.md](13-blocks-per-explosion-tick.md) - Tests per-explosion limit
- [04-timing-modes-start.md](04-timing-modes-start.md) - Effects timing
