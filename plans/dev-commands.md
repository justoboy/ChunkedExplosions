# Dev Commands for Testing

## Overview

This document outlines the dev commands implemented to facilitate consistent, automated testing of the Chunked Explosions mod. These tools reduce reliance on manual positioning and provide repeatable test scenarios.

---

## Implemented Dev Commands

### Environment Setup Commands

#### `/chunkedexplosions testcube <size> <block> [x] [y] [z]`

Create a cube of uniform blocks for consistent testing.

**Examples:**
```
/chunkedexplosions testcube 20 stone                    # 20x20x20 cube of stone
/chunkedexplosions testcube 10 dirt ~ ~1 ~              # 10x10x10 dirt cube, 1 block above player
/chunkedexplosions testcube 50 glass 0 64 0             # Large glass cube at world origin
```

#### `/chunkedexplosions testclear <size> [block] [x] [y] [z]`

Clear a cubic region for fresh testing.

**Examples:**
```
/chunkedexplosions testclear 50                         # Clear 50x50x50 region around player
/chunkedexplosions testclear 30 air                     # Clear 30x30x30 region
/chunkedexplosions testclear 20 stone ~ ~1 ~            # Fill 20x20x20 region with stone
```

#### `/chunkedexplosions sptestentity <entity> <count> <radius> [angle]`

Spawn test entities at precise positions. All entities spawn with `NoAI:1b` automatically to ensure they stay perfectly still for consistent testing.

**Parameters:**
- `entity`: Entity type (e.g., `iron_golem`, `zombie`)
- `count`: Number of entities to spawn (1-64)
- `radius`: Distance from center position (0.1-64.0)
- `angle`: Angle spacing in degrees (0-360). Use 0 to stack all at one position.

**Examples:**
```
/chunkedexplosions sptestentity iron_golem 8 4 45       # 8 golems in circle, 4 blocks away
/chunkedexplosions sptestentity iron_golem 5 2 0        # 5 golems stacked at same position, 2 blocks away
```

---

### Explosion Testing Commands

#### `/chunkedexplosions spawnexplosion [radius]`

Spawn an explosion for testing. Logs the seed used for reproducibility.

**Examples:**
```
/chunkedexplosions spawnexplosion                       # default TNT radius (4.0) at player position
/chunkedexplosions spawnexplosion 7                     # Larger explosion with radius 7
```

#### `/chunkedexplosions explosionstats`

Display current explosion queue statistics.

**Output Example:**
```
=== Explosion Queue Statistics ===

  Awaiting Queue:   3
  Active Queue:     2
  Total Pending:    5

  Blocks This Tick:  24
  Remaining:         176
```

---

### Entity Damage/Health Commands

#### `/chunkedexplosions testentitydamage`

Report health of all alive entities for damage verification.

**Output Example:**
```
=== Entity Health Report ===

  minecraft:iron_golem: Entity [ID=123, Health=180.0]
  minecraft:iron_golem: Entity [ID=124, Health=150.0]

Total alive entities: 2
```

---

### Block Destruction Recording Commands

#### `/chunkedexplosions recordexplosion <start|stop|report|clear>`

Record blocks destroyed by explosions for comparison testing.

**Subcommands:**
- `start`: Begin recording
- `stop`: Stop recording
- `report`: Show recorded blocks
- `clear`: Clear recorded blocks

**Examples:**
```
/chunkedexplosions recordexplosion start
# ... prime TNT ...
/chunkedexplosions recordexplosion stop
/chunkedexplosions recordexplosion report
```

#### `/chunkedexplosions compareexplosion <baseline|history [name]|comparebaseline>`

Compare explosion block destruction between runs.

**Subcommands:**
- `baseline set`: Set current recorded blocks as baseline
- `baseline clear`: Clear the baseline
- `history [name]`: Save current recording to history with given name
- `history list`: List saved history entries
- `comparebaseline`: Compare current recording with baseline

**Examples:**
```
# After recording an explosion:
/chunkedexplosions compareexplosion baseline set

# Record another explosion and compare:
/chunkedexplosions compareexplosion comparebaseline

# Save multiple runs:
/chunkedexplosions compareexplosion history run1
```

---

### Benchmark Commands

#### `/chunkedexplosions benchmarkexplosion <iterations>`

Run automated explosion benchmarks. Spawns multiple TNT entities to stress-test the system.

**Examples:**
```
/chunkedexplosions benchmarkexplosion 10               # Spawn 10 TNT for benchmark
/chunkedexplosions benchmarkexplosion 50               # Spawn 50 TNT for larger stress test
```

**Note:** This is a simplified benchmark that spawns TNT. Use `/explosionstats` to monitor queue during testing.

---

## Usage Examples

### Complete Testing Workflow

**1. Setup Test Environment:**
```
/chunkedexplosions testclear 50
/chunkedexplosions testcube 30 stone
```

**2. Spawn Test Entities:**
```
/chunkedexplosions sptestentity iron_golem 5 4 45
```

**3. Start Recording:**
```
/chunkedexplosions recordexplosion start
```

**4. Run Test:**
- Prime TNT
- Wait for explosion to complete

**5. Analyze Results:**
```
/chunkedexplosions recordexplosion stop
/chunkedexplosions recordexplosion report
/chunkedexplosions testentitydamage
```

**6. Set Baseline for Comparison:**
```
/chunkedexplosions compareexplosion baseline set
```

---

## Implementation Notes

- All entity spawning (`/sptestentity`) automatically applies `NoAI:1b` to ensure stationary entities
- Block recording (`/recordexplosion`) is integrated into the explosion processing system
- Comparison (`/compareexplosion`) tracks exact block positions destroyed
- Commands are designed for repeatable, consistent testing

---

## Command Registration

All commands are registered under `/chunkedexplosions`:
- Configuration commands (`explosionsPerTick`, `blocksPerExplosionTick`, etc.)
- Test commands (`testcube`, `testclear`, `sptestentity`, etc.)

The mod includes 9 test/dev commands total:
1. `testcube` - Create test environments
2. `testclear` - Clear test areas
3. `sptestentity` - Spawn test entities
4. `spawnexplosion` - Trigger test explosions
5. `explosionstats` - Monitor queue
6. `testentitydamage` - Check entity health
7. `recordexplosion` - Record block destruction
8. `compareexplosion` - Compare results
9. `benchmarkexplosion` - Stress testing
