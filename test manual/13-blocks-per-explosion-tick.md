# Test 13: Blocks Per Explosion Per Tick Verification

## Purpose
Verify that `blocksPerExplosionTick` correctly limits blocks destroyed by each individual explosion per tick.

## Prerequisites
- Single TNT block
- Flat world with breakable blocks
- Ability to observe block counts

## Setting Configuration

Set very restrictive value to easily observe limit:
```
blocksPerExplosionTick=5
maxBlocksPerTick=1000
explosionsPerTick=10
damageTiming=START
soundTiming=START
particleTiming=START
```

## Test Setup

1. Build 20x20 platform of stone or dirt

2. Place single TNT at center

3. Prepare to count blocks per tick

## Execution

1. Prime TNT

2. Observe and count:
   - Block 1 destroyed: Tick X
   - Block 2 destroyed: Tick X
   - ...
   - Block 5 destroyed: Tick X
   - Block 6 destroyed: Tick X+1 (next tick!)

3. Continue observing until explosion completes

4. Record total ticks to complete

## Expected Results

### With blocksPerExplosionTick=5

A typical TNT destroys ~30-70 blocks. With limit of 5 per tick:

| Tick | Blocks This Tick | Cumulative |
|----|--|---|
| X | 5 | 5 |
| X+1 | 5 | 10 |
| X+2 | 5 | 15 |
| X+3 | 5 | 20 |
| X+4 | 5 | 25 |
| X+5 | 5 | 30 |
| X+6 | 5 | 35 |
| X+7 | 5 | 40 |
| X+8 | 5 | 45 |
| X+9 | 5 | 50 |
| X+10 | remainder | complete |

Each tick processes exactly 5 blocks (until complete).

### If limit Was Ignored:
- All 30-70 blocks destroyed in single tick
- No spreading across ticks

## Pass Criteria

- [ ] First tick destroys exactly `blocksPerExplosionTick` blocks (5)
- [ ] Each subsequent tick destroys max `blocksPerExplosionTick` blocks
- [ ] No tick shows more than limit for single explosion
- [ ] Explosion eventually completes

## Anomaly Detection

### Issues to Watch For:
- **Limit exceeded:** Single tick destroys more than 5 blocks from single explosion
- **No spreading:** All blocks destroyed in single tick
- **Stuck:** Explosion never completes (blocksPerExplosionTick=0 or very low)

## Configuration Effects

### Low Value (blocksPerExplosionTick=1)
- Very slow: 50 ticks for 50 blocks
- Extremely smooth performance
- Takes seconds to complete

### Medium Value (blocksPerExplosionTick=16)
- Default: ~3 ticks for 50 blocks
- Good balance

### High Value (blocksPerExplosionTick=100)
- Fast: 1 tick for 50 blocks
- May cause TPS impact
- Appears instantaneous

## Testing with Multiple Explosions

To verify this limit applies PER EXPLOSION (not global):

1. Set `blocksPerExplosionTick=5` and `maxBlocksPerTick=1000`

2. Prime 5 TNT simultaneously

3. First tick should show:
   - Explosion 1: 5 blocks
   - Explosion 2: 5 blocks
   - Explosion 3: 5 blocks
   - Explosion 4: 5 blocks
   - Explosion 5: 5 blocks
   - Total: 25 blocks (5 per explosion)

This verifies that `maxBlocksPerTick=1000` allows all 5 to process, each limited to 5 blocks.

## Documentation

For each test:
1. Record `blocksPerExplosionTick` setting
2. Count blocks destroyed in first tick
3. Count blocks in each subsequent tick
4. Verify per-explosion limit respected
5. Record total ticks to completion

## Related Tests

- [12-max-blocks-per-tick.md](12-max-blocks-per-tick.md) - Tests global cap across all explosions
