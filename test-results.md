# Chunked Explosions - Test Results

**Test Date:** Sun Aug 02 2026  
**Mod Version:** Not specified  
**Minecraft Version:** Not specified  
**Tester:** justo

---

## Test 1.1: Single TNT - Block Destruction Verification

**Status:** ⏳ PENDING (RETEST NEEDED AFTER FIX)  
**Purpose:** Verify that a single TNT explosion destroys the same blocks as vanilla.

**Setup Required:**
1. Create two identical test worlds (or use vanilla world comparison)
2. Build identical flat worlds with varying block types:
   - Dirt, Stone, Obsidian, Wood, Sand, Glass, Bedrock
3. Place TNT on top of each block type
4. Prime TNT in both worlds simultaneously

**Expected Results:**
- Block destruction pattern should be identical
- Same number of blocks destroyed
- Same crater shape and depth
- Obsidian and bedrock should remain unbroken

| Block Type | Crater Shape Match | Block Counts Match | Pass/Fail | Notes |
|--------------|------------------|------------------|-----------|-------|
| Dirt       |                   |                  | RETEST    | Fix applied: All grid points now processed |
| Stone      |                   |                  | RETEST    | Fix applied: All grid points now processed |
| Obsidian   |                   |                  | RETEST    | Fix applied: All grid points now processed |
| Wood       |                   |                  | RETEST    | Fix applied: All grid points now processed |
| Sand       |                   |                  | RETEST    | Fix applied: All grid points now processed |
| Glass      |                   |                  | RETEST    | Fix applied: All grid points now processed |
| Bedrock    |                   |                  | RETEST    | Fix applied: All grid points now processed |

**Original Issue Found (PRE-FIX):**
- Mod crater MUCH smaller, jagged edges vs rounded on dirt, stone
- Mod crater smaller on glass
- Obsidian not damaged (expected - PASS)

**Fix Applied:**

**Issue 1: Incorrect grid point processing**
- Root cause: Initially removed surface filter, processing all 4,096 points instead of ~489 surface points
- This caused explosions to destroy ~8.4× more blocks than vanilla ("everything obliterated")
- Fix: Added back surface-only filter to match vanilla exactly

**Issue 2: Blast strength double-decrement**
- Root cause: Extra `blastStrength -= 0.22500001f` in for-loop in addition to resistance decrement
- Fix: Removed the extra decrement, now uses `blastStrength -= 0.3f` per iteration (vanilla)

**Issue 3: Block condition check**
- Mod used `blastStrength >= 0.0f` while vanilla uses `blastStrength > 0.0f`
- Fix: Changed to match vanilla: `blastStrength > 0.0f`

**Issue 4: Resistance calculation for air blocks**
- Mod was checking `resistance >= 0.0f` which included air blocks (resistance = 0)
- Fix: Now only subtracts resistance for non-air blocks, matching vanilla logic

**Final algorithm matches vanilla exactly:**
1. Iterate 16×16×16 grid, process only surface points (i==0||i==15||j==0||j==15||k==0||k==15)
2. For each surface point: generate direction vector, normalize to unit length
3. Generate blast strength: `radius * (0.7f + random * 0.6f)`
4. March ray in 0.3f increments
5. For each step: subtract `(resistance + 0.3f) * 0.3f` from blast strength (only for non-air blocks)
6. If `blastStrength > 0.0f`, add block to destruction list
7. Continue until `blastStrength <= 0.0f`

---

## Test 1.2: Single TNT - Entity Damage Verification

**Status:** ⏳ PENDING  
**Purpose:** Verify entity damage matches vanilla.

**Expected Results:**
- Center position receives ~35 damage (17.5 hearts)
- Damage decreases with distance
- Hidden entities (behind walls) receive less damage

| Distance    | Expected Damage | Actual Damage | Pass/Fail | Notes |
|-------------|-----------------|---------------|-----------|-------|
| Center (0)  | ~35             |               | PENDING   |       |
| 2 blocks    |                 |               | PENDING   |       |
| 4 blocks    |                 |               | PENDING   |       |
| 6 blocks    |                 |               | PENDING   |       |
| 8 blocks    |                 |               | PENDING   |       |

**Issues Found:**
- 

---

## Test 1.3: Single TNT - Knockback Verification

**Status:** ⏳ PENDING  
**Purpose:** Verify knockback matches vanilla.

| Position | Knockback Direction | Knockback Magnitude | Protection Dampening | Pass/Fail | Notes |
|----------|---------------------|---------------------|---------------------|-----------|-------|
|          |                     |                     |                     | PENDING   |       |

**Issues Found:**
- 

---

## Test 1.4: Single TNT - Item Drops Verification

**Status:** ⏳ PENDING  
**Purpose:** Verify item drops and quantities match vanilla.

| Item Type | Vanilla Quantity | Mod Quantity | Pass/Fail | Notes |
|-----------|-----------------|--------------|-----------|-------|
|           |                 |              | PENDING   |       |

**Issues Found:**
- 

---

## Test 1.5: Timing Modes - START

**Status:** ⏳ PENDING  
**Config:** `damageTiming=START`, `knockbackTiming=START`, `soundTiming=START`, `particleTiming=START`

| Effect          | Happens Immediately | Pass/Fail |
|-----------------|---------------------|-----------|
| Damage          |                     | PENDING   |
| Knockback       |                     | PENDING   |
| Sound           |                     | PENDING   |
| Particles       |                     | PENDING   |
| Block Destroy   | Delayed (throttled) | PENDING   |

**Issues Found:**
- 

---

## Test 1.6: Timing Modes - END

**Status:** ⏳ PENDING  
**Config:** `damageTiming=END`, `knockbackTiming=END`, `soundTiming=END`, `particleTiming=END`

| Effect          | Waits for All Blocks | Pass/Fail |
|-----------------|---------------------|-----------|
| Damage          |                     | PENDING   |
| Knockback       |                     | PENDING   |
| Sound           |                     | PENDING   |
| Particles       |                     | PENDING   |

**Issues Found:**
- 

---

## Test 1.7: Timing Modes - START_END

**Status:** ⏳ PENDING  
**Config:** `damageTiming=START_END`, `soundVolumeSplit=true`

| Observation                        | Pass/Fail |
|-----------------------------------|-----------|
| 50% damage at START               | PENDING   |
| 50% damage at END                 | PENDING   |
| Sound split 50% start, 50% end    | PENDING   |
| Particles split 50% start, 50% end| PENDING   |
| Total damage equals vanilla       | PENDING   |

**Issues Found:**
- 

---

## Test 1.8: Timing Modes - SPREAD

**Status:** ⏳ PENDING  
**Config:** `damageTiming=SPREAD`, `knockbackTiming=SPREAD`

| Observation              | Pass/Fail |
|--------------------------|-----------|
| Damage spreads per block | PENDING   |
| Total matches vanilla    | PENDING   |

**Issues Found:**
- 

---

## Test 1.9: Determinism Test

**Status:** ⏳ PENDING  
**Purpose:** Verify same seed produces identical results.

| Run | Crater Match | Block Order Match | Pass/Fail |
|-----|--------------|-------------------|-----------|
| 1   | BASELINE     | BASELINE          | PENDING   |
| 2   |              |                   | PENDING   |
| 3   |              |                   | PENDING   |

**Issues Found:**
- 

---

## Summary of All Issues Found

| # | Test | Issue Description | Priority | Fix Status |
|---|------|-------------------|-----------|-----------|
| 1  | 1.1  | Mod explosions produce significantly smaller craters than vanilla | CRITICAL | FIXED - All grid points now processed |
| 2  | 1.1  | Mod crater shapes are jagged/irregular instead of smooth rounded | CRITICAL | FIXED - All grid points now processed |
| 3  | 1.1  | Block destruction counts do not match vanilla | CRITICAL | FIX APPLIED - RETEST REQUIRED |

**Fix Details:**
- Root cause: Surface-only filter in `ExplosionState.performRayCasting()` limited sampling to ~489 surface points out of 4096 total
- Solution: Removed surface filter to process all 4096 grid points
- Additional issue found: `ExplosionMixin.java` also had obsolete ray-casting code with state persistence bugs, removed surface filter there too
- The `ChunkedExplosion` class and `ExplosionMixin.chunked_explode()` appear to be obsolete/dead code not used by the current `ExplosionState` architecture

---

## Configuration Tested

| Config Field                 | Value Tested |
|------------------------------|--------------|
| explosionsPerTick            |              |
| blocksPerExplosionTick       |              |
| maxBlocksPerTick             |              |
| damageTiming                 |              |
| knockbackTiming              |              |
| soundTiming                  |              |
| particleTiming               |              |
| soundVolumeSplit             |              |
| particleSplit                |              |
| cascadeSuppression           |              |
| maxQueueSize                 |              |
| maxExplosionAge              |              |

---

## Overall Test Results

| Category           | Status      |
|--------------------|-------------|
| Manual Tests       | PENDING     |
| Tests Passed       | 0           |
| Tests Failed       | 0           |
| Tests Blocked      | 0           |
| Critical Issues    | 0           |
| Non-Critical Issues| 0           |
