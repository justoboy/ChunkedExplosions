# ChunkedExplosions

A Minecraft Forge mod that transforms instant explosions into controlled, tick-by-tick processes to prevent server lag and enable precise timing control.

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Command Reference](#command-reference)
- [Timing Modes](#timing-modes)
- [Development Guide](#development-guide)
- [Code Structure](#code-structure)
- [Testing](#testing)

## Overview

The ChunkedExplosions mod intercepts all explosion events in Minecraft and processes them over multiple server ticks instead of instantly. This provides several benefits:

1. **Lag Reduction**: Spreads computationally expensive explosion calculations across multiple ticks
2. **Deterministic Behavior**: Explosions are pre-calculated once using deterministic algorithms
3. **Flexible Timing**: Configure when damage, sound, particles, and knockback are applied
4. **Performance Control**: Fine-tune processing limits to maintain server TPS

## Features

### Core Features

- **Dual-Queue System**: Manages pending and active explosion queues with per-dimension processing
- **Deterministic Ray-Casting**: Uses a 16x16x16 grid surface ray-casting algorithm matching vanilla behavior
- **Configurable Processing**: Control blocks per tick, explosions per tick, and queue sizes
- **Multiple Timing Modes**: START, END, START_END, and SPREAD for damage, sound, particles, and knockback

### Timing Control

Each explosion effect can be independently configured:

| Effect | Timing Options | Description |
|--------|---------------|-------------|
| Damage | START, END, START_END, SPREAD | When entities take damage |
| Knockback | START, END, START_END, SPREAD | When entities are pushed back |
| Sound | START, END, START_END, SPREAD | When explosion sound plays |
| Particles | START, END, START_END, SPREAD | When explosion particles spawn |

## Architecture

### High-Level Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                    Explosion Event Triggered                     │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                  ChunkedExplosions.onExplosionStart()            │
│  • Intercepts explosion event                                   │
│  • Creates ExplosionState from vanilla explosion                 │
│  • Cancels original explosion                                   │
│  • Adds to awaiting queue                                       │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Server Tick (onServerTick)                    │
│  • Reset block counter                                          │
│  • Move explosions from awaiting to active queue                │
│  • Process active explosions (block destruction)                │
│  • Apply END timing effects for completed explosions            │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                   ExplosionState.preCalculate()                  │
│  • Ray-cast to determine blocks to destroy                      │
│  • Pre-calculate entity effects (visibility, damage, knockback) │
│  • Convert to ordered list for efficient iteration              │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                    ExplosionState.processTick()                  │
│  • Destroy up to blocksPerExplosionTick blocks                  │
│  • Accumulate SPREAD timing effects                             │
│  • Apply per-tick SPREAD effects                                │
│  • Track progress                                               │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                    ExplosionState.finalize*()                    │
│  • Apply END timing damage/knockback                            │
│  • Play final sound                                             │
│  • Spawn final particles                                        │
└─────────────────────────────────────────────────────────────────┘
```

### Queue Architecture

```
┌─────────────────────────────┐         ┌─────────────────────────────┐
│      AWAITING QUEUE         │ ──────► │       ACTIVE QUEUE          │
│   (preCalcPending)          │         │   (preCalcComplete)         │
│  • New explosions           │         │  • Pre-calculated           │
│  • Not calculated           │         │  • Ready to process         │
│  • FIFO order               │         │  • Being processed          │
└─────────────────────────────┘         └─────────────────────────────┘
```

### Key Classes

| Class | Purpose |
|-------|---------|
| [`ChunkedExplosions`](src/main/java/com/github/justoboy/chunkedexplosions/ChunkedExplosions.java) | Main mod entry point, event registration |
| [`ExplosionProcessor`](src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/ExplosionProcessor.java) | Manages dual-queue system, processes explosions |
| [`ExplosionState`](src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/ExplosionState.java) | Represents a single explosion, handles processing |
| [`BlockDestroyer`](src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/BlockDestroyer.java) | Handles block destruction and drops |
| [`EntityInfo`](src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/EntityInfo.java) | Pre-calculated entity effect data |
| [`ExplosionMixin`](src/main/java/com/github/justoboy/chunkedexplosions/mixin/world/level/ExplosionMixin.java) | Mixin for accessing explosion data |
| [`IExplosionDuck`](src/main/java/com/github/justoboy/chunkedexplosions/iduck/world/level/IExplosionDuck.java) | Interface for explosion data access |

## Quick Start

### Installation

1. Download the mod JAR file
2. Place it in your Minecraft `mods` folder
3. Launch the game with Forge installed

### Basic Usage

Once in-game, use the `/chunkedexplosions` command to access all features:

```
/chunkedexplosions help              # List all commands
/chunkedexplosions enable true       # Enable chunked explosions
/chunkedexplosions enable false      # Disable chunked explosions
```

### Default Configuration

The mod comes with sensible defaults:

- **Blocks per explosion tick**: 16
- **Explosions per tick**: 1024
- **Max blocks per tick**: 16384
- **Max queue size**: 10000
- **Damage timing**: SPREAD
- **Sound timing**: SPREAD
- **Particle timing**: SPREAD
- **Knockback timing**: SPREAD

## Configuration

All configuration can be done via commands or the Forge config file.

### Performance Settings

| Setting | Default | Description |
|---------|---------|-------------|
| `blocksPerExplosionTick` | 16 | Max blocks destroyed per explosion per tick (0 = unlimited) |
| `explosionsPerTick` | 1024 | Max explosions moved from awaiting to active queue per tick |
| `maxBlocksPerTick` | 16384 | Global max blocks destroyed per tick across all explosions |
| `maxQueueSize` | 10000 | Max pending explosions in queue (0 = unlimited) |

### Timing Settings

| Setting | Default | Description |
|---------|---------|-------------|
| `damageTiming` | SPREAD | When damage is applied to entities |
| `knockbackTiming` | SPREAD | When knockback is applied to entities |
| `soundTiming` | SPREAD | When explosion sound is played |
| `particleTiming` | SPREAD | When particles are spawned |
| `soundVolumeSplit` | true | Split volume between timing phases |
| `particleSplit` | true | Split particle count between timing phases |
| `cascadeSuppression` | false | Suppress chain reactions from chunked explosions |

## Command Reference

### Configuration Commands

| Command | Description |
|---------|-------------|
| `enable [true/false]` | Enable or disable chunked explosions |
| `explosionsPerTick [value]` | Set max explosions processed per tick |
| `blocksPerExplosionTick [value]` | Set max blocks destroyed per explosion per tick |
| `maxBlocksPerTick [value]` | Set global max blocks destroyed per tick |
| `maxQueueSize [value]` | Set max pending explosions in queue |
| `damageTiming [mode]` | Set damage timing mode |
| `knockbackTiming [mode]` | Set knockback timing mode |
| `soundTiming [mode]` | Set sound timing mode |
| `soundVolumeSplit [true/false]` | Enable/disable sound volume splitting |
| `particleTiming [mode]` | Set particle timing mode |
| `particleSplit [true/false]` | Enable/disable particle count splitting |

### Test Commands

| Command | Description |
|---------|-------------|
| `spawnexplosion` | Spawn a test explosion at current position |
| `testcube` | Create a uniform block cube for testing |
| `testclear` | Clear test area |
| `explosionstats` | Show queue statistics |
| `sptestentity` | Spawn test entities at precise positions |
| `testentitydamage` | Test entity damage calculations |

### Benchmark Commands

| Command | Description |
|---------|-------------|
| `benchmark` | Run benchmark tests |
| `compare` | Compare explosion behaviors |
| `comparetiming` | Compare different timing modes |

Use `/chunkedexplosions help <command>` for detailed help on any command.

## Timing Modes

### START Mode
Effects are applied immediately when the explosion begins processing.

```
Explosion starts → Apply 100% effect → Process blocks
```

### END Mode
Effects are applied after all blocks have been destroyed.

```
Process all blocks → Apply 100% effect
```

### START_END Mode
Effects are split between start and end (50% each by default).

```
Explosion starts → Apply 50% effect → Process blocks → Apply 50% effect
```

### SPREAD Mode
Effects are accumulated proportionally per block destroyed and applied once per tick.

```
For each block destroyed:
  - Accumulate 1/totalBlocks of effect
Once per tick:
  - Apply accumulated effect
```

**SPREAD Mode Benefits:**
- Smooth, progressive damage/knockback application
- Realistic visual feedback as explosion progresses
- Better performance distribution across ticks

## Development Guide

### Project Structure

```
src/main/java/com/github/justoboy/chunkedexplosions/
├── ChunkedExplosions.java          # Main mod class
├── core/
│   ├── ModCommands.java            # Command registration
│   └── ModConfig.java              # Configuration management
├── common/
│   ├── command/                    # All command implementations
│   │   ├── ChunkedExplosionsCommand.java  # Main command router
│   │   ├── HelpCommand.java        # Help system
│   │   ├── EnableCommand.java      # Enable/disable toggle
│   │   ├── *TimingCommand.java     # Timing configuration
│   │   └── ...                     # Other commands
│   └── world/level/                # Explosion processing
│       ├── ExplosionProcessor.java # Queue management
│       ├── ExplosionState.java     # Single explosion state
│       ├── BlockDestroyer.java     # Block destruction
│       ├── ChunkedExplosion.java   # Legacy wrapper
│       └── EntityInfo.java         # Entity effect data
├── iduck/                          # Duck interfaces
│   └── world/level/
│       └── IExplosionDuck.java     # Explosion data access
└── mixin/                          # Forge mixins
    └── world/level/
        └── ExplosionMixin.java     # Explosion data access
```

### Building

```bash
# Build the mod
./gradlew build

# Run Minecraft in development environment
./gradlew runClient
```

### Adding New Commands

1. Create a new command class in `common/command/`
2. Register the command description in a static block:
   ```java
   static {
       CommandComments.addComment("mycommand", "Description of my command.");
   }
   ```
3. Register the command in `ChunkedExplosionsCommand.register()`
4. Add detailed help in `HelpDetails.register()` if needed

### Modifying Explosion Behavior

The core explosion processing logic is in [`ExplosionState`](src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/ExplosionState.java):

- [`preCalculate()`](src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/ExplosionState.java:207) - Ray-casting and entity effect pre-calculation
- [`processTick()`](src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/ExplosionState.java:445) - Per-tick block destruction
- [`applyDamage()`](src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/ExplosionState.java:537) - Damage application
- [`applyKnockback()`](src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/ExplosionState.java:589) - Knockback application

## Code Structure

### Key Design Patterns

1. **Duck Pattern**: [`IExplosionDuck`](src/main/java/com/github/justoboy/chunkedexplosions/iduck/world/level/IExplosionDuck.java) interface provides access to vanilla Explosion private fields
2. **State Pattern**: [`ExplosionState`](src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/ExplosionState.java) encapsulates all explosion data and behavior
3. **Strategy Pattern**: Timing modes (START, END, START_END, SPREAD) are interchangeable strategies
4. **Command Pattern**: All in-game commands follow a consistent registration pattern

### Important Implementation Details

- **Deterministic Processing**: Ray-casting uses the level's random source for reproducible results
- **Per-Dimension Processing**: Each dimension is processed independently to prevent cross-dimensional desync
- **Efficient Data Structures**: Uses Guava and FastUtil collections for performance
- **Config Hot-Reload**: Configuration changes are applied to active explosions immediately

## Testing

### Manual Testing

See the [`test manual/`](test manual/) directory for detailed test procedures:

1. **Basic Tests**: Single TNT block destruction, entity damage, knockback
2. **Timing Tests**: Different timing mode combinations
3. **Stress Tests**: TNT cannons, chain reactions
4. **Performance Tests**: Max blocks per tick, queue overflow

### Automated Testing

Use the test commands to verify behavior:

```
/chunkedexplosions testcube        # Create test environment
/chunkedexplosions spawnexplosion  # Spawn test explosion
/chunkedexplosions explosionstats  # Check processing stats
```

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## Support

For issues, questions, or suggestions, please open an issue on GitHub.
