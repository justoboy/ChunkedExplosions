# Test 3.3: Chain Reaction Test

## Purpose
Verify that chain-reaction explosions (TNT priming more TNT) are handled correctly by the mod's queuing system.

## Prerequisites
- Knowledge of TNT mechanics
- Ability to build chain reaction setups
- TPS monitoring (F3 screen)

## Understanding Chain Reactions in Minecraft

In vanilla Minecraft:
1. TNT explodes and destroys blocks
2. If it destroys another TNT block, that TNT becomes a `PrimedTnt` entity
3. The newly primed TNT continues its timer and explodes
4. This can create cascading chain reactions

In Chunked Explosions:
1. Original TNT is intercepted and queued
2. Newly spawned PrimedTnt entities should ALSO be intercepted
3. All explosions queued and processed in order
4. No uncontrolled chain reactions

## Chain Reaction Test 1: Simple Chain

### Setup

Build a simple chain:
```
[TNT 1] [TNT 2] [TNT 3]
```

Spacing: Place TNT within explosion radius of each other (4 blocks apart or less).

TNT 1 will destroy TNT 2, which will explode and destroy TNT 3.

### Execution

1. Place all 3 TNT blocks
2. Prime only TNT 1
3. Observe chain reaction
4. Monitor TPS throughout

### Expected Results

- TNT 1 explodes (intercepted by mod, queued)
- TNT 2 is primed by TNT 1 explosion
- TNT 2's explosion is intercepted by mod, queued
- TNT 3 is primed by TNT 2 explosion
- TNT 3's explosion is intercepted by mod, queued
- All explosions process through the queue
- No uncontrolled explosions
- TPS stays stable

### Pass Criteria

- [ ] All 3 TNT explode
- [ ] Chain reaction completes fully
- [ ] No explosion is missed or ignored
- [ ] TPS remains stable above 18
- [ ] Queue handles all explosions

### Anomaly Detection

Watch for these issues:

**Chain Stopped Early:**
- TNT 1 explodes, TNT 2 primed
- TNT 2 fails to prime TNT 3
- Only 2/3 TNT exploded

**Queue Overflow:**
- "Queue overflow" warning in logs
- Some TNT not exploding
- Explosions silently cancelled

**TPS Drop:**
- Chain reaction causes TPS to crash
- Processing takes too long
- Server becomes unresponsive

## Chain Reaction Test 2: Long Chain

### Setup

Create longer chain:
```
TNT-1 --TNT-2 --TNT-3 --TNT-4 --TNT-5 --TNT-6 --TNT-7 --TNT-8
```

Spacing: Each TNT 3 blocks from neighbor (within blast radius).

### Execution

1. Prime first TNT in chain
2. Observe entire chain
3. Count total explosions
4. Monitor TPS

### Expected Results

- All 8 TNT explode in sequence
- Each explosion correctly intercepted and queued
- Chain completes without issues
- Total processing time depends on configuration

### Pass Criteria

- [ ] All 8 TNT explode
- [ ] No unqueued explosions
- [ ] TPS stays stable

## Chain Reaction Test 3: Branching Chain

### Setup

Create branching chain:
```
     [TNT-2] [TNT-3]
        |     |
     [TNT-1]  [TNT-4]
        |
     [TNT-0]
```

Setup: TNT-0 explodes, primes TNT-1, which explodes and primes TNT-2, TNT-3, and TNT-4 simultaneously.

### Execution

1. Prime TNT-0
2. Observe branching chain
3. All 5 TNT should explode

### Expected Results

- TNT-0 explodes first
- TNT-1 primes from TNT-0 explosion
- TNT-1 explodes, primes TNT-2, TNT-3, TNT-4 simultaneously
- Multiple explosions queued in same tick
- All 5 TNT eventually explode

### Pass Criteria

- [ ] All 5 TNT explode
- [ ] Chain doesn't get stuck
- [ ] Multiple simultaneous explosions queued correctly

## Configuration Considerations

For chain reactions, ensure:

```
# Allow sufficient queue space
explosionsPerTick=100     # How many explosions can process per tick
maxQueueSize=1000         # Total pending explosion limit
maxBlocksPerTick=1000     # Shouldn't matter for chain test
```

### If Too Restrictive:

```
explosionsPerTick=2
maxQueueSize=5
```

Chain reactions may fail because:
- Queue fills up with first 5 explosions
- Later chain explosions rejected
- Chain stops prematurely

## Documentation

For each test:
1. Record chain configuration
2. Count total TNT in chain
3. Count total explosions that occurred
4. Note any stuck or missed explosions
5. Record TPS throughout
6. Document any anomalies

## Related Tests

- [10-tnt-cannon-stress-test.md](10-tnt-cannon-stress-test.md) - Large scale simultaneous explosions
- [02-single-tnt-entity-damage.md](02-single-tnt-entity-damage.md) - Verify TNT priming still works
