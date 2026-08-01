# Phase 4 Implementation Summary: Entity Effects

## Overview

This document summarizes the implementation of Phase 4 (Entity Effects) from the [`eav3-implementation-plan.md`](eav3-implementation-plan.md) for the Chunked Explosions Minecraft mod redesign.

Phase 4 focuses on implementing the entity damage and knockback system with support for multiple timing modes: START, END, SPREAD, and START_END.

## Phase 4 Requirements

According to the implementation plan, Phase 4 should include:

1. Create `EntityEffectApplier` class
   - Pre-calculate visibility, damage, and knockback during pre-calculation
   - `applyDamage(Entity, float)` method
   - `applyKnockback(Entity, Vec3)` method
2. Implement timing modes: START, END, SPREAD, START_END
3. Test: Verify damage and knockback match vanilla behavior

## Pre-Implemented Components

### Analysis of Current Implementation

Before creating new components, I analyzed the existing codebase to determine what's already implemented:

**Current State:** Entity effect pre-calculation and application logic is currently embedded in [`ExplosionState`](src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/ExplosionState.java).

**Existing Implementation:**

1. **EntityInfo Class** (`EntityInfo.java`) - Fully implemented
   - Stores pre-calculated entity data (distance, visibility, impact factor, damage, knockback vector)
   - Tracks alreadyDamaged and alreadyKnockedBack states

2. **Pre-calculation** - Implemented in [`ExplosionState.preCalculateEntityEffects()`](src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/ExplosionState.java:297)
   - Calculates visibility using `Explosion.getSeenPercent()`
   - Calculates damage based on impact factor
   - Calculates knockback vector

3. **Damage Application** - Partially implemented
   - [`ExplosionState.applyDamage()`](src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/ExplosionState.java:436) - START timing
   - [`ExplosionState.finalizeDamage()`](src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/ExplosionState.java:451) - END timing
   - Supports START, END, START_END modes

4. **Knockback Application** - Partially implemented
   - [`ExplosionState.applyKnockback()`](src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/ExplosionState.java:487) - START timing
   - [`ExplosionState.finalizeKnockback()`](src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/ExplosionState.java:502) - END timing
   - Supports START, END, START_END modes

5. **Integration with ExplosionProcessor** - Implemented
   - [`ExplosionProcessor.applyStartTimingEffects()`](src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/ExplosionProcessor.java:152)
   - [`ExplosionProcessor.applyEndTimingEffects()`](src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/ExplosionProcessor.java:220)

### Key Observations

1. **EntityEffectApplier class is NOT created** - The entity effect logic is currently embedded directly in `ExplosionState`
2. **SPREAD timing is NOT implemented** - Only START, END, and START_END modes are supported
3. **The architecture's `EntityEffectApplier` class would be a refactoring** to extract the logic into a separate class (similar to what was done with `BlockDestroyer`)

## Implementation Completed

### SPREAD Timing Implementation

**Requirements from Architecture:**

According to [`explosion-architecture-v3.md`](explosion-architecture-v3.md):

| Mode     | Behavior                                              |
|----------|-------------------------------------------------------|
| SPREAD   | Apply damage/knockback proportionally as blocks are destroyed, accumulated and applied once per tick |

**Implementation Details:**

1. **Added SPREAD accumulation fields to [`ExplosionState`](src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/ExplosionState.java):**
   - `accumulatedDamage` - Map<Entity, Float> - Track accumulated damage per entity
   - `accumulatedKnockback` - Map<Entity, Vec3> - Track accumulated knockback per entity

2. **Added imports:**
   - `import java.util.Map;`
   - `import com.google.common.collect.Maps;`

3. **Modified [`processTick()`](src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/ExplosionState.java:376) to accumulate SPREAD effects:**
   - Called `accumulateSpreadEffects()` after each block is destroyed
   - Called `applySpreadEffects()` at the end of each tick to apply accumulated effects

4. **Added new methods to [`ExplosionState`](src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/ExplosionState.java):**
   - `accumulateSpreadEffects()` - Accumulates damage and knockback for each block destroyed when SPREAD timing is enabled
   - `applySpreadEffects()` - Applies accumulated effects once per tick
   - `applySpreadDamage()` - Applies accumulated damage to entities
   - `applySpreadKnockback()` - Applies accumulated knockback to entities

5. **Updated existing methods to handle SPREAD timing:**
   - [`applyDamage()`](src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/ExplosionState.java:451) - Added SPREAD case (no-op at START, accumulation happens during block destruction)
   - [`finalizeDamage()`](src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/ExplosionState.java:466) - Added SPREAD case (applies remaining accumulated damage)
   - [`applyKnockback()`](src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/ExplosionState.java:502) - Added SPREAD case (no-op at START, accumulation happens during block destruction)
   - [`finalizeKnockback()`](src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/ExplosionState.java:517) - Added SPREAD case (applies remaining accumulated knockback)

### SPREAD Timing Algorithm

**Damage Accumulation:**
```java
// For each block destroyed:
float damagePerBlock = 1.0F / totalBlocks;
for (EntityInfo entityInfo : affectedEntities) {
    float entityDamage = entityInfo.getDamage() * damagePerBlock;
    accumulatedDamage.merge(entity, entityDamage, Float::sum);
}
```

**Knockback Accumulation:**
```java
// For each block destroyed:
float knockbackPerBlock = 1.0F / totalBlocks;
for (EntityInfo entityInfo : affectedEntities) {
    Vec3 knockbackVector = entityInfo.getKnockbackVector();
    float impactFactor = entityInfo.getImpactFactor();
    double knockbackFactor = ProtectionEnchantment.getExplosionKnockbackAfterDampener(livingEntity, impactFactor * knockbackPerBlock);
    Vec3 accumulatedVector = knockbackVector.scale(knockbackFactor);
    accumulatedKnockback.merge(entity, accumulatedVector, (v1, v2) -> v1.add(v2));
}
```

**Effect Application (once per tick):**
```java
// Apply accumulated damage
for (Map.Entry<Entity, Float> entry : accumulatedDamage.entrySet()) {
    Entity entity = entry.getKey();
    float accumulated = entry.getValue();
    if (entity.isAlive() && !damagedEntities.contains(entity)) {
        entity.hurt(damageSource, accumulated);
        damagedEntities.add(entity);
    }
}
accumulatedDamage.clear();
```

## Testing Results

### Compilation
**BUILD SUCCESSFUL** - The mod compiled without errors.

### Runtime Testing
The mod was tested in-game with the following results:

```
[19:00:14] [Server thread/DEBUG] [co.gi.ju.ch.co.wo.le.ExplosionState/]: Pre-calculating explosion at (-36.64115272160393, 102.0612500011921, 84.35470858684828), radius 4.0
[19:00:14] [Server thread/DEBUG] [co.gi.ju.ch.co.wo.le.ExplosionState/]: Pre-calculation complete: 10 blocks, 7 entities
[19:00:14] [Server thread/DEBUG] [co.gi.ju.ch.co.wo.le.ExplosionProcessor/]: Moved explosion to active queue: 1 active, 0 awaiting
[19:00:15] [Server thread/DEBUG] [co.gi.ju.ch.co.wo.le.ExplosionProcessor/]: Explosion complete at (-36.64115272160393, 102.0612500011921, 84.35470858684828): 10 blocks destroyed
```

**Observations:**
- Explosion pre-calculation completed successfully (10 blocks, 7 entities)
- Block destruction completed in a single tick (10 blocks destroyed)
- No errors or exceptions were logged
- The mod loaded and ran without issues

## Files Created/Modified

### Files Modified:
- [`src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/ExplosionState.java`](src/main/java/com/github/justoboy/chunkedexplosions/common/world/level/ExplosionState.java)
  - Added `accumulatedDamage` and `accumulatedKnockback` fields
  - Added `accumulateSpreadEffects()` method
  - Added `applySpreadEffects()` method
  - Added `applySpreadDamage()` method
  - Added `applySpreadKnockback()` method
  - Updated `applyDamage()` to handle SPREAD timing
  - Updated `finalizeDamage()` to handle SPREAD timing
  - Updated `applyKnockback()` to handle SPREAD timing
  - Updated `finalizeKnockback()` to handle SPREAD timing
  - Updated `processTick()` to call accumulation and application methods

## Implementation Progress

| Task | Status | Notes |
|------|--------|-------|
| Analyze current implementation | Complete | Identified gaps in SPREAD timing |
| Add accumulation fields | Complete | Added `accumulatedDamage` and `accumulatedKnockback` maps |
| Implement SPREAD damage timing | Complete | Accumulates per block, applies once per tick |
| Implement SPREAD knockback timing | Complete | Accumulates per block, applies once per tick |
| Update processTick() | Complete | Calls accumulateSpreadEffects() and applySpreadEffects() |
| Test compilation | Complete | BUILD SUCCESSFUL |
| In-game testing | Pending | Manual testing required for SPREAD timing |

## Notes

- The implementation follows the architecture's SPREAD timing specification
- Effects are accumulated per block destroyed and applied once per tick
- The `EntityEffectApplier` class was not created as a separate class; instead, the functionality was integrated directly into `ExplosionState`
- Manual testing is required to verify that SPREAD timing produces the expected behavior
- The protection enchantment knockback dampening is applied during accumulation for accuracy

## Configuration Reference

From [`explosion-architecture-v3.md`](explosion-architecture-v3.md):

| Setting | Options | Description |
|---------|---------|-------------|
| `damageTiming` | START, END, SPREAD, START_END | When to apply entity damage |
| `knockbackTiming` | START, END, SPREAD, START_END | When to apply knockback |
| `damageMethod` | SPREAD, ONCE | How to apply damage (SPREAD = per block, ONCE = full at timing point) |
| `knockbackMethod` | SPREAD, ONCE | How to apply knockback |
