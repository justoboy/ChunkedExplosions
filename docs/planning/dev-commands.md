# Dev Commands for Testing

## Overview

This document outlines the dev commands implemented to facilitate consistent, automated testing of the Chunked Explosions mod. These tools reduce reliance on manual positioning and provide repeatable test scenarios.

---

## Implemented Dev Commands

### Environment Setup Commands

#### `/chunkedexplosions testcube [position] [size] [block]`

Create a cube of uniform blocks for consistent testing.

**Parameters:**
- `position`: Center position as `x y z` (optional, defaults to player position)
  - Supports relative coordinates: `~ ~ ~` for current position, `~1 ~ ~-1` for offsets
- `size`: Cube size (1-100, default: 5)
- `block`: Block type (e.g., `stone`, `dirt`, `minecraft:glass`, default: `minecraft:dirt`)

**Examples:**
```
/chunkedexplosions testcube                              # 5x5x5 dirt cube at player position
/chunkedexplosions testcube 20 stone                    # 20x20x20 cube of stone at player
/chunkedexplosions testcube 10 dirt ~ ~1 ~              # 10x10x10 dirt cube, 1 block above player
/chunkedexplosions testcube 50 glass 0 64 0             # Large glass cube at world origin
```

---

### Entity Commands

#### `/chunkedexplosions sptestentity <entity> <count> <radius> <angle> [position]`

Spawn test entities at precise positions. All entities spawn with movement speed set to 0 to ensure they stay perfectly still for consistent testing while still allowing them to receive knockback and respond to physics.

**Parameters:**
- `entity`: Entity type (e.g., `iron_golem`, `zombie`)
- `count`: Number of entities to spawn (1-64)
- `radius`: Distance from center position (0.1-64.0)
- `angle`: Angle spacing in degrees (0-360). Use 0 or less for line formation, positive values for circular formation.
- `position`: Center position as `x y z` (optional, defaults to player position)
  - Supports relative coordinates: `~ ~ ~` for current position, `~1 ~ ~-1` for offsets

**Examples:**
```
/chunkedexplosions sptestentity iron_golem 8 4 45                    # 8 golems in circle, 4 blocks away from player
/chunkedexplosions sptestentity iron_golem 5 2 0 ~ ~1 ~              # 5 golems in a line, 1 block above player
/chunkedexplosions sptestentity iron_golem 5 2 0 0 64 0              # 5 golems at world origin
```

#### `/chunkedexplosions sptestentity damageReport`

Report health of all alive entities for damage verification.

**Output Example:**
```
=== Entity Health Report ===

  minecraft:iron_golem: Entity [ID=123, Health=180.0]
  minecraft:iron_golem: Entity [ID=124, Health=150.0]

Total alive entities: 2
```

#### `/chunkedexplosions sptestentity positionReport`

Report positions and motion of all alive entities for position verification.

**Output Example:**
```
=== Entity Position Report ===

  minecraft:iron_golem: Entity [ID=123, Position=(10.5, 64.0, 20.3), Motion=(0.0, 0.0, 0.0)]
  minecraft:iron_golem: Entity [ID=124, Position=(12.0, 64.0, 18.7), Motion=(0.0, 0.0, 0.0)]

Total alive entities: 2
```

---

### Explosion Testing Commands

#### `/chunkedexplosions spawnexplosion [position] [radius]`

Spawn a TNT explosion for testing using `level.explode()`. Logs the seed used for reproducibility.

**Parameters:**
- `position`: Explosion center position as `x y z` (optional, defaults to player position)
  - Supports relative coordinates: `~ ~ ~` for current position, `~1 ~ ~-1` for offsets
- `radius`: Explosion radius (0.1-20.0, default: 4.0 for TNT)

**Examples:**
```
/chunkedexplosions spawnexplosion                    # default TNT radius (4.0) at player position
/chunkedexplosions spawnexplosion 7                  # Larger explosion with radius 7 at player
/chunkedexplosions spawnexplosion 4 ~ ~1 ~           # TNT radius, 1 block ahead of player
/chunkedexplosions spawnexplosion 4 0 64 0           # TNT radius explosion at world origin
```

#### `/chunkedexplosions explosionstats`

Display current explosion queue statistics and configuration settings.

**Output Example:**
```
=== Explosion Queue Statistics ===

  Awaiting Queue:   3
  Active Queue:     2
  Total Pending:    5

  Blocks This Tick:  24
  Remaining:         176

=== Current Settings ===

  Explosions Per Tick:     1
  Blocks Per Explosion:    100
  Max Blocks Per Tick:     500
```

---

### Benchmark Commands

#### `/chunkedexplosions benchmarkexplosion <iterations> [position]`

Run automated explosion benchmarks. Spawns multiple explosions using `level.explode()` to stress-test the system.

**Parameters:**
- `iterations`: Number of explosions to spawn (1-100)
- `position`: Spawn position as `x y z` (optional, defaults to player position)
  - Supports relative coordinates: `~ ~ ~` for current position, `~1 ~ ~-1` for offsets

**Examples:**
```
/chunkedexplosions benchmarkexplosion 10                    # Spawn 10 explosions at player position
/chunkedexplosions benchmarkexplosion 50 ~ ~1 ~             # Spawn 50 explosions, 1 block ahead of player
/chunkedexplosions benchmarkexplosion 50 0 64 0             # Spawn 50 explosions at world origin
```

**Note:** This is a simplified benchmark that spawns explosions. Use `/explosionstats` to monitor queue during testing.

---

## Usage Examples

### Complete Testing Workflow

**1. Setup Test Environment:**
```
/chunkedexplosions testcube 30 stone 0 64 0
```

**2. Spawn Test Entities:**
```
/chunkedexplosions sptestentity iron_golem 5 4 45 0 65 0
```

**3. Run Test:**
- Prime TNT or use `/chunkedexplosions spawnexplosion 4 0 64 0`
- Wait for explosion to complete

**4. Analyze Results:**
```
/chunkedexplosions sptestentity damageReport
```

---

## Implementation Notes

- All entity spawning (`/sptestentity`) sets movement speed to 0 to ensure stationary entities while still allowing them to receive knockback and respond to physics
- Commands are designed for repeatable, consistent testing
- The awaiting queue is now unlimited (maxQueueSize configuration removed)
- `explosionsPerTick` controls the active queue size (how many explosions move from awaiting to active per tick)
- All position arguments support relative coordinates using `~` notation (e.g., `~ ~ ~` for current position, `~1 ~ ~-1` for offsets)
- The `spawnexplosion` command now uses `level.explode()` instead of spawning TNT entities
- The `spawnexplosion` command argument order changed from `[radius] [position]` to `[position] [radius]`
- The `testcube` command now has optional parameters with defaults (size=5, block=minecraft:dirt)
- The `sptestentity` command now supports line formation when angle<=0
- The `sptestentity positionReport` subcommand was added for position/motion reporting

---

## Command Registration

All commands are registered under `/chunkedexplosions`:
- Configuration commands (`explosionsPerTick`, `blocksPerExplosionTick`, etc.)
- Test commands (`testcube`, `sptestentity`, `spawnexplosion`, etc.)

The mod includes the following test/dev commands:
1. `testcube` - Create test environments (with optional position, size, and block parameters with defaults)
2. `sptestentity` - Spawn test entities and report damage (with optional position, line formation when angle<=0)
3. `sptestentity damageReport` - Report entity health
4. `sptestentity positionReport` - Report entity positions and motion (NEW)
5. `spawnexplosion` - Trigger test explosions using level.explode() (argument order changed to [position] [radius])
6. `explosionstats` - Monitor queue and display configuration
7. `benchmarkexplosion` - Stress testing (with optional position)
