# Chunked Explosions - Manual Test Guide

## Overview

This directory contains step-by-step test procedures for manually verifying that the Chunked Explosions mod works correctly and produces results identical to vanilla Minecraft explosions.

## How to Use These Tests

1. **Read the Test Plan:** Start with `plans/test-plan.md` for the complete testing strategy.

2. **Follow Test Files:** Each file in this directory is a self-contained test:
   - Read the purpose and setup requirements
   - Follow the execution steps
   - Check pass criteria
   - Document any anomalies

3. **Record Results:** For each test, document:
   - Configuration values used
   - Observed results
   - Pass/fail status
   - Any anomalies found

## Test Catalog

### Block Destruction Tests

| File | Test | Description |
|-- |--|--|
| [01-single-tnt-block-destruction.md](01-single-tnt-block-destruction.md) | Block Destruction | Verify craters match vanilla |

### Entity Effect Tests

| File | Test | Description |
|-- |--|--|
| [02-single-tnt-entity-damage.md](02-single-tnt-entity-damage.md) | Entity Damage | Verify damage matches vanilla |
| [03-single-tnt-knockback.md](03-single-tnt-knockback.md) | Knockback | Verify knockback matches vanilla |

### Timing Mode Tests

| File | Test | Description |
|-- |--|--|
| [04-timing-modes-start.md](04-timing-modes-start.md) | START Timing | Verify effects at start |
| [05-timing-modes-end.md](05-timing-modes-end.md) | END Timing | Verify effects at end |
| [06-timing-modes-start_end.md](06-timing-modes-start_end.md) | START_END Timing | Verify 50/50 split |
| [07-timing-modes-spread.md](07-timing-modes-spread.md) | SPREAD Timing | Verify incremental effects |

### Core Behavior Tests

| File | Test | Description |
|-- |--|--|
| [08-determinism-test.md](08-determinism-test.md) | Determinism | Verify same seed = same results |

### Stress Tests

| File | Test | Description |
|-- |--|--|
| [10-tnt-cannon-stress-test.md](10-tnt-cannon-stress-test.md) | TNT Cannon | Stress test with large cannon |
| [11-chain-reaction-test.md](11-chain-reaction-test.md) | Chain Reaction | Test chain-reaction explosions |

### Configuration Tests

| File | Test | Description |
|-- |--|--|
| [12-max-blocks-per-tick.md](12-max-blocks-per-tick.md) | Max Blocks Per Tick | Verify global cap |
| [13-blocks-per-explosion-tick.md](13-blocks-per-explosion-tick.md) | Blocks Per Explosion | Verify per-explosion limit |

## Testing Workflow

1. **Start Simple:** Begin with single TNT tests (01-03)
2. **Verify Core:** Ensure basic mechanics work (04-08)
3. **Stress Test:** Move to stress tests (10-11)  
4. **Configuration:** Test all configuration options (12-13)

## Required Tools

### Optional Mods/Tools for Testing
- **F3 Debug Screen:** Built-in vanilla (shows coordinates, TPS)
- **WorldEdit:** For quick world building (optional)
- **Structure Block:** For saving/loading test areas
- **Item Counts Mod:** To count dropped items

### Recommended Setup
- Creative mode world
- Flat world preset for consistent testing
- Sufficient clearance area (50x50 minimum)
- Ability to reset world easily

## Reporting Results

For each test, document:
1. **Test ID:** Which test file was used
2. **Configuration:** All config values during test
3. **Expected:** What should happen
4. **Observed:** What actually happened
5. **Result:** PASS / FAIL / PARTIAL
6. **Notes:** Any anomalies or unusual observations

## Troubleshooting

### Common Issues

**Issue: Results differ from vanilla**
- Check if deterministic seed is being used
- Verify explosion radius matches expected value
- Ensure no other mods interfering

**Issue: Performance issues**
- Increase `blocksPerExplosionTick` 
- Reduce `maxBlocksPerTick` if TPS drops too low
- Check for queue buildup

**Issue: Visual glitches**
- Verify chunks are re-syncing properly
- Check for concurrent explosion processing conflicts

## Dev Commands for Testing

The following commands have been implemented to assist with testing. All commands are available under `/chunkedexplosions`.

### Environment Setup Commands

| Command | Description |
|-- |--|
| `testcube [position] [size] [block]` | Create a uniform block cube for testing (defaults: size=5, block=minecraft:dirt) |
| `sptestentity <entity> <count> <radius> <angle> [position]` | Spawn test entities with movement speed set to 0 (line formation when angle=0, circular when angle>0) |
| `sptestentity damageReport` | Report health of all test entities |
| `sptestentity positionReport` | Report positions and motion of all test entities |

### Explosion Testing Commands

| Command | Description |
|-- |--|
| `spawnexplosion [position] [radius]` | Spawn an explosion at position with specified radius (defaults to 4.0) |
| `explosionstats` | Show current queue statistics and configuration settings |
| `benchmarkexplosion <iterations> [position]` | Stress test by spawning multiple explosions |

### Entity Effect Testing Commands

| Command | Description |
|-- |--|
| `sptestentity` | Spawn test entities for damage/knockback testing |
| `spawnexplosion` | Trigger test explosions with optional position |

### Example Usage

```bash
# Create test environment (defaults to 5x5x5 dirt cube at player position)
/chunkedexplosions testcube

# Create larger test cube
/chunkedexplosions testcube 30 stone

# Spawn test entities in a circle (movement speed set to 0)
/chunkedexplosions sptestentity iron_golem 8 4 45

# Spawn test entities in a line
/chunkedexplosions sptestentity iron_golem 5 2 0

# Trigger test explosion at player position (default radius 4.0)
/chunkedexplosions spawnexplosion

# Trigger test explosion with custom radius
/chunkedexplosions spawnexplosion 10.0

# Monitor queue during stress testing
/chunkedexplosions explosionstats

# Run stress test with multiple explosions
/chunkedexplosions benchmarkexplosion 100

# Report test entity positions
/chunkedexplosions sptestentity positionReport
```

All entities spawned via `/chunkedexplosions sptestentity` have their movement speed set to 0, ensuring they stay perfectly still for accurate damage/knockback testing while still allowing them to receive knockback and respond to physics.

## Implemented Dev Commands

The following dev commands are fully implemented:

1. `testcube` - Create test environments (with optional position, size, and block parameters)
2. `sptestentity` - Spawn stationary test entities (movement speed set to 0)
3. `sptestentity damageReport` - Report entity health
4. `sptestentity positionReport` - Report entity positions and motion
5. `spawnexplosion` - Trigger test explosions via level.explode()
6. `explosionstats` - Monitor queue and display configuration
7. `benchmarkexplosion` - Stress testing

## Removed Commands

The following commands have been removed:

| Command | Reason |
|---|---|
| `testclear` | Replaced by `testcube` (testcube now handles both creation and clearing) |
| `recordexplosion` | Removed - not implemented |
| `compareexplosion` | Removed - not implemented |

## Next Steps

After completing all manual tests:
1. Report any failures or anomalies
2. Use dev commands to automate repeatable tests
3. Compare results with vanilla behavior
4. Document final testing recommendations
