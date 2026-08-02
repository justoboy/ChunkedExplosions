# Test 1.1: Single TNT - Block Destruction Verification

## Purpose
Verify that a single TNT explosion destroys the same blocks as vanilla Minecraft.

## Setup

### World Preparation
1. Create a new creative world (or use an existing one for testing)
2. Build or fly to a flat area at least 50x50 blocks
3. Ensure you are in creative mode

### Build Test Pattern
Create the following materials side by side:
```
[Dirt] [Stone] [Obsidian] [Wood] [Sand] [Glass] [Bedrock]
```

Each material should be:
- 15x15 blocks in area
- 5 blocks thick (y, y+1, y+2, y+3, y+4)

### Position TNT
Place TNT in the center of each material type at the same height. Example layout:
```
         TNT
     Dirt[4][5][6]
         TNT
     Stone[4][5][6]
         TNT
     Obsidian[4][5][6]
         ... etc
```

## Execution

### Using Dev Commands for Consistent Testing

1. **Prepare Test Area:**
   ```
   /chunkedexplosions testclear 50
   /chunkedexplosions testcube 30 stone
   ```

2. **Start Recording:**
   ```
   /chunkedexplosions recordexplosion start
   ```

3. **Place and Prime TNT:**
   - Place TNT at center of the cube
   - Prime TNT

4. **Stop Recording:**
   ```
   /chunkedexplosions recordexplosion stop
   ```

5. **View Results:**
   ```
   /chunkedexplosions recordexplosion report
   ```

6. **Set Baseline (optional - for comparison):**
   ```
   /chunkedexplosions compareexplosion baseline set
   ```

7. **Save to History:**
   ```
   /chunkedexplosons compareexplosion history test1
   ```

### Manual Testing (Without Commands)

1. Record the block count BEFORE explosion:
   - Use a mod like WorldEdit or vanilla commands
   - Take a screenshot with F1

2. Place and prime TNT

3. Wait for all explosions to complete

4. Record the block count AFTER explosion

## Expected Results

### Crater Shape
- All non-resistant materials (dirt, stone, wood, sand, glass) should create similar crater patterns
- Crater should be roughly circular/oval when viewed from top
- Maximum depth: approximately 4 blocks in center
- Maximum radius: approximately 4 blocks from center

### Block Resistance
| Material   | Should Destroy? | Notes                          |
|------------|-----------------|--------------------------------|
| Dirt       | Yes             | Full destruction               |
| Stone      | Yes             | Full destruction               |
| Obsidian   | No              | Should remain completely       |
| Wood       | Yes             | Full destruction               |
| Sand       | Yes             | Full destruction, may fall     |
| Glass      | Yes             | Full destruction               |
| Bedrock    | No              | Should remain completely       |

### Block Count Verification
A typical TNT explosion at center of block mass destroys:
- **30-70 blocks** depending on random seed
- Document the exact count for each explosion

## Pass Criteria

- [ ] Obsidian blocks remain unbroken
- [ ] Bedrock blocks remain unbroken
- [ ] All other materials (dirt, stone, wood, sand, glass) are destroyed
- [ ] Crater shapes are roughly consistent across identical materials
- [ ] No more than 70 blocks destroyed in any single explosion
- [ ] No fewer than 30 blocks destroyed in any single explosion (for full TNT)

## Anomaly Check

After testing, check for these issues:
- **Missing blocks:** Any craters with significantly fewer blocks
- **Extra blocks:** Any craters with unexplained additional destruction
- **Shape distortion:** Craters that are elliptical or irregular
- **Resistance issues:** Obsidian or bedrock that was destroyed
- **Block duplication:** Any duplicate items or blocks appearing

## Comparison with Vanilla

If you have access to a vanilla Minecraft instance:
1. Build the exact same setup in vanilla
2. Prime TNT in same positions
3. Take screenshots of both worlds
4. Compare crater patterns side by side

### Automated Comparison (Recommended)

For automated comparison:
1. In chunked explosions world:
   ```
   /chunkedexplosons recordexplosion start
   [Prime TNT]
   /chunkedexplosons recordexplosion stop
   /chunkedexplosons compareexplosion history vanilla_test
   ```

2. Compare multiple runs:
   ```
   /chunkedexplosons compareexplosion compare
   ```
   This will show match percentage and list different blocks.

## Notes

- Results may vary slightly due to random seed differences
- For exact determinism testing, use the `/spawntnt <seed>` command if available
- Document any deviations from expected behavior
