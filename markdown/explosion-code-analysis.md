# Vanilla Minecraft Explosion Code Analysis

## Overview

This document provides a detailed explanation of how explosions work in vanilla Minecraft (version 1.20.1). The explosion system is primarily located in the `net.minecraft.world.level` package and consists of several interconnected classes that handle block destruction, entity damage, particle effects, and loot calculation.

---

## Core Components

### 1. Explosion Class (`Explosion.java`)

The main `Explosion` class is the heart of the explosion system. It handles:
- Block destruction calculations
- Entity damage and knockback
- Particle effects generation
- Loot drop calculations

#### Key Fields

| Field | Type | Description |
|-------|------|-------------|
| `fire` | `boolean` | Whether the explosion should create fire on destroyed blocks |
| `blockInteraction` | `BlockInteraction` | Enum defining how blocks are affected (KEEP, DESTROY, DESTROY_WITH_DECAY) |
| `random` | `RandomSource` | Random number generator for procedural calculations |
| `level` | `Level` | Reference to the world where the explosion occurs |
| `position` | `Vec3` | 3D position coordinates (x, y, z) of the explosion center |
| `source` | `Entity` | The entity that caused the explosion (TNT, creeper, etc.) |
| `radius` | `float` | The explosion radius in blocks |
| `damageSource` | `DamageSource` | The damage type used for killing entities |
| `damageCalculator` | `ExplosionDamageCalculator` | Calculates explosion resistance for blocks |
| `toBlow` | `ObjectArrayList<BlockPos>` | List of block positions to be destroyed |
| `hitPlayers` | `Map<Player, Vec3>` | Players affected and their knockback vectors |

#### Constructor

```java
/**
 * Creates a new explosion instance.
 * 
 * @param level The world level where the explosion occurs
 * @param source The entity that caused the explosion (can be null for natural explosions)
 * @param x, y, z The 3D position of the explosion center
 * @param radius The blast radius (TNT = 4.0, Creeper = 3.0, Wither = 7.0)
 * @param fire Whether fire should be created on destroyed blocks
 * @param blockInteraction How blocks should be affected
 * @param damageSource Optional custom damage source
 * @param damageCalculator Optional custom damage calculator
 */
public Explosion(
    Level level,
    @Nullable Entity source,
    double x, double y, double z,
    float radius,
    boolean fire,
    BlockInteraction blockInteraction,
    @Nullable DamageSource damageSource,
    @Nullable ExplosionDamageCalculator damageCalculator
)
```

---

### 2. Explosion Block Interaction Types (`BlockInteraction` enum)

```java
public static enum BlockInteraction {
    /** Blocks are not affected at all */
    KEEP,
    
    /** Blocks are destroyed without any decay chance */
    DESTROY,
    
    /** Blocks are destroyed with item decay (items have chance to not drop) */
    DESTROY_WITH_DECAY
}
```

---

## Explosion Algorithm Explained

### Phase 1: Ray Casting for Block Destruction

The `explode()` method begins with a ray-casting algorithm to determine which blocks will be destroyed:

```java
/**
 * Ray casting algorithm to find blocks affected by the explosion.
 * Iterates through the surface of a 16x16x16 cube and fires rays
 * inward to simulate blast propagation.
 */
public void explode() {
    // Emit game event for mod compatibility
    this.level.gameEvent(this.source, GameEvent.EXPLODE, new Vec3(this.x, this.y, this.z));
    
    // Set to store unique block positions that will be destroyed
    Set<BlockPos> affectedBlocks = Sets.newHashSet();
    
    // Iterate through the surface of a 16x16x16 cube
    // This creates a grid of rays emanating from the explosion center
    for(int i = 0; i < 16; ++i) {
        for(int j = 0; j < 16; ++j) {
            for(int k = 0; k < 16; ++k) {
                // Only process edges (surface of the cube)
                if (i == 0 || i == 15 || j == 0 || j == 15 || k == 0 || k == 15) {
                    
                    // Calculate normalized direction vector from center
                    double directionX = (double)((float)i / 15.0F * 2.0F - 1.0F);
                    double directionY = (double)((float)j / 15.0F * 2.0F - 1.0F);
                    double directionZ = (double)((float)k / 15.0F * 2.0F - 1.0F);
                    
                    // Normalize the direction vector (make length = 1)
                    double vectorLength = Math.sqrt(directionX * directionX + 
                                                   directionY * directionY + 
                                                   directionZ * directionZ);
                    directionX /= vectorLength;
                    directionY /= vectorLength;
                    directionZ /= vectorLength;
                    
                    // Calculate initial blast strength with random variation
                    // 0.7 to 1.3 multiplier for procedural variation
                    float blastStrength = this.radius * (0.7F + this.level.random.nextFloat() * 0.6F);
                    
                    // Position variables for ray traversal
                    double rayX = this.x;
                    double rayY = this.y;
                    double rayZ = this.z;
                    
                    // March along the ray in 0.3 block increments
                    for(float strength = 0.3F; blastStrength > 0.0F; blastStrength -= 0.3F) {
                        BlockPos blockPos = BlockPos.containing(rayX, rayY, rayZ);
                        BlockState blockState = this.level.getBlockState(blockPos);
                        FluidState fluidState = this.level.getFluidState(blockPos);
                        
                        // Stop if outside world bounds
                        if (!this.level.isInWorldBounds(blockPos)) {
                            break;
                        }
                        
                        // Calculate how much this block resists the explosion
                        Optional<Float> resistance = this.damageCalculator.getBlockExplosionResistance(
                            this, this.level, blockPos, blockState, fluidState
                        );
                        
                        // Reduce blast strength by block resistance
                        if (resistance.isPresent()) {
                            // Additional 0.3F damage reduction per block
                            blastStrength -= (resistance.get() + 0.3F) * 0.3F;
                        }
                        
                        // If blast is still strong enough, add block to destruction list
                        if (blastStrength > 0.0F && 
                            this.damageCalculator.shouldBlockExplode(
                                this, this.level, blockPos, blockState, blastStrength
                            )) {
                            affectedBlocks.add(blockPos);
                        }
                        
                        // Advance along the ray
                        rayX += directionX * 0.3;
                        rayY += directionY * 0.3;
                        rayZ += directionZ * 0.3;
                    }
                }
            }
        }
    }
    
    // Add calculated blocks to the explosion's destruction list
    this.toBlow.addAll(affectedBlocks);
    
    // ... entity damage calculation follows ...
}
```

#### How Ray Casting Works

1. A 16x16x16 cube surface is created around the explosion center
2. From each edge position, a ray is fired toward the explosion center
3. The ray advances in 0.3-block increments
4. At each step:
   - Block's explosion resistance is queried
   - Blast strength is reduced by resistance
   - If still positive, the block is marked for destruction
5. This simulates blast waves propagating through air and block density

---

### Phase 2: Entity Damage Calculation

After determining which blocks are affected, the explosion calculates damage to nearby entities:

```java
/**
 * Calculate and apply damage/knockback to all entities within blast radius.
 */
// Calculate bounding box that encompasses all entities within blast radius
float blastRadius = this.radius * 2.0F;
int minX = Mth.floor(this.x - blastRadius - 1.0D);
int maxX = Mth.floor(this.x + blastRadius + 1.0D);
int minY = Mth.floor(this.y - blastRadius - 1.0D);
int maxY = Mth.floor(this.y + blastRadius + 1.0D);
int minZ = Mth.floor(this.z - blastRadius - 1.0D);
int maxZ = Mth.floor(this.z + blastRadius + 1.0D);

// Get all entities in the bounding box
List<Entity> nearbyEntities = this.level.getEntities(this.source, 
    new AABB(minX, minY, minZ, maxX, maxY, maxZ)
);

// Forge event hook - allows mods to modify affected entities list
net.minecraftforge.event.ForgeEventFactory.onExplosionDetonate(
    this.level, this, nearbyEntities, blastRadius
);

// Position vector for distance calculations
Vec3 explosionPosition = new Vec3(this.x, this.y, this.z);

// Process each entity
for(Entity entity : nearbyEntities) {
    // Skip entities that ignore explosions (invulnerable, etc.)
    if (!entity.ignoreExplosion()) {
        
        // Calculate normalized distance (1.0 = edge of blast, 0.0 = center)
        double normalizedDistance = Math.sqrt(entity.distanceToSqr(explosionPosition)) / blastRadius;
        
        // Only damage entities within blast radius
        if (normalizedDistance <= 1.0D) {
            
            // Calculate direction vector from explosion to entity
            double dirX = entity.getX() - this.x;
            double dirY = (entity instanceof PrimedTnt ? entity.getY() : entity.getEyeY()) - this.y;
            double dirZ = entity.getZ() - this.z;
            
            // Normalize direction vector
            double directionLength = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
            if (directionLength != 0.0D) {
                dirX /= directionLength;
                dirY /= directionLength;
                dirZ /= directionLength;
                
                // Calculate visibility percentage (line of sight to explosion)
                double visibilityPercent = getSeenPercent(explosionPosition, entity);
                
                // Final impact factor: distance based decay * visibility
                double impactFactor = (1.0D - normalizedDistance) * visibilityPercent;
                
                // Damage formula: quadratic based on impact factor
                // Higher impact = exponentially more damage
                double damage = (impactFactor * impactFactor + impactFactor) / 2.0D * 7.0D * blastRadius + 1.0D;
                
                // Apply damage
                entity.hurt(this.getDamageSource(), (float)((int)damage));
                
                // Calculate knockback
                double finalKnockback = impactFactor;
                
                // Apply protection enchantment dampening for living entities
                if (entity instanceof LivingEntity) {
                    LivingEntity livingEntity = (LivingEntity)entity;
                    // Protection enchantments reduce knockback based on enchantment level
                    finalKnockback = ProtectionEnchantment.getExplosionKnockbackAfterDampener(
                        livingEntity, impactFactor
                    );
                }
                
                // Apply knockback as velocity change
                dirX *= finalKnockback;
                dirY *= finalKnockback;
                dirZ *= finalKnockback;
                
                Vec3 knockbackVector = new Vec3(dirX, dirY, dirZ);
                entity.setDeltaMovement(entity.getDeltaMovement().add(knockbackVector));
                
                // Track player knockback (used for later processing)
                if (entity instanceof Player) {
                    Player player = (Player)entity;
                    if (!player.isSpectator() && (!player.isCreative() || !player.getAbilities().flying)) {
                        this.hitPlayers.put(player, knockbackVector);
                    }
                }
            }
        }
    }
}
```

#### Damage Formula Analysis

The damage formula `(impactFactor² + impactFactor) / 2.0D * 7.0D * blastRadius + 1.0D` creates a quadratic damage curve:

| Distance | Impact Factor | Damage (radius=4) |
|----------|---------------|-------------------|
| Center   | 1.0           | 35.0 (18 hearts)  |
| 50%      | 0.5           | 14.9 (7.5 hearts) |
| 75%      | 0.25          | 6.4 (3.2 hearts)  |
| Edge     | 0.0           | 1.0 (0.5 hearts)  |

#### Visibility Calculation (`getSeenPercent`)

```java
/**
 * Calculate what percentage of the explosion is visible from the entity's position.
 * Uses ray casting to sample points on the entity's bounding box.
 * 
 * @param explosionPos The explosion center position
 * @param entity The entity to check visibility for
 * @return Float between 0.0 (completely hidden) and 1.0 (fully visible)
 */
public static float getSeenPercent(Vec3 explosionPos, Entity entity) {
    AABB boundingBox = entity.getBoundingBox();
    
    // Calculate step sizes based on entity size
    double deltaX = boundingBox.maxX - boundingBox.minX;
    double deltaY = boundingBox.maxY - boundingBox.minY;
    double deltaZ = boundingBox.maxZ - boundingBox.minZ;
    
    // Higher resolution for smaller entities
    double stepX = 1.0D / (deltaX * 2.0D + 1.0D);
    double stepY = 1.0D / (deltaY * 2.0D + 1.0D);
    double stepZ = 1.0D / (deltaZ * 2.0D + 1.0D);
    
    int visibleRayCount = 0;
    int totalRayCount = 0;
    double offsetZ = (1.0D - Math.floor(1.0D / stepX) * stepX) / 2.0D;
    double offsetZ2 = (1.0D - Math.floor(1.0D / stepZ) * stepZ) / 2.0D;
    
    // Cast rays from multiple points on the entity's surface
    for(double x = 0.0D; x <= 1.0D; x += stepX) {
        for(double y = 0.0D; y <= 1.0D; y += stepY) {
            for(double z = 0.0D; z <= 1.0D; z += stepZ) {
                Interpolate position on bounding box surface
                double sampleX = Mth.lerp(x, boundingBox.minX, boundingBox.maxX);
                double sampleY = Mth.lerp(y, boundingBox.minY, boundingBox.maxY);
                double sampleZ = Mth.lerp(z, boundingBox.minZ, boundingBox.maxZ);
                
                Vec3 samplePoint = new Vec3(sampleX + offsetZ, sampleY, sampleZ + offsetZ2);
                
                // Check if ray from sample point to explosion hits solid blocks
                if (entity.level().clip(new ClipContext(
                    samplePoint, 
                    explosionPos, 
                    ClipContext.Block.COLLIDER, 
                    ClipContext.Fluid.NONE, 
                    entity
                )).getType() == HitResult.Type.MISS) {
                    visibleRayCount++;
                }
                
                totalRayCount++;
            }
        }
    }
    
    return (float)visibleRayCount / (float)totalRayCount;
}
```

---

### Phase 3: Explosion Finalization (`finalizeExplosion`)

After calculating damage, the explosion processes block destruction:

```java
/**
 * Complete the explosion by destroying blocks, dropping items, and creating effects.
 * 
 * @param spawnParticles Whether to spawn explosion particles
 */
public void finalizeExplosion(boolean spawnParticles) {
    // Play explosion sound on client
    if (this.level.isClientSide) {
        this.level.playLocalSound(
            this.x, this.y, this.z, 
            SoundEvents.GENERIC_EXPLODE, 
            SoundSource.BLOCKS, 
            4.0F, 
            (1.0F + (this.level.random.nextFloat() - this.level.random.nextFloat()) * 0.2F) * 0.7F, 
            false
        );
    }
    
    boolean interactsWithBlocks = this.interactsWithBlocks();
    
    // Spawn particle effects
    if (spawnParticles) {
        if (!(this.radius < 2.0F) && interactionsWithBlocks) {
            // Large explosions get emitter particles
            this.level.addParticle(ParticleTypes.EXPLOSION_EMITTER, this.x, this.y, this.z, 1.0D, 0.0D, 0.0D);
        } else {
            // Small explosions get standard particles
            this.level.addParticle(ParticleTypes.EXPLOSION, this.x, this.y, this.z, 1.0D, 0.0D, 0.0D);
        }
    }
    
    // Process block destruction
    if (interactsWithBlocks) {
        ObjectArrayList<Pair<ItemStack, BlockPos>> itemDrops = new ObjectArrayList<>();
        boolean sourceIsPlayer = this.getIndirectSourceEntity() instanceof Player;
        
        // Randomize order to prevent predictable drops
        Util.shuffle(this.toBlow, this.level.random);
        
        for(BlockPos blockPos : this.toBlow) {
            BlockState blockState = this.level.getBlockState(blockPos);
            Block block = blockState.getBlock();
            
            // Skip air blocks
            if (!blockState.isAir()) {
                BlockPos immutablePos = blockPos.immutable();
                this.level.getProfiler().push("explosion_blocks");
                
                // Check if block should drop items when exploded
                if (blockState.canDropFromExplosion(this.level, blockPos, this)) {
                    Level currentLevel = this.level;
                    if (currentLevel instanceof ServerLevel) {
                        ServerLevel serverLevel = (ServerLevel)currentLevel;
                        BlockEntity blockEntity = blockState.hasBlockEntity() ? 
                            this.level.getBlockEntity(blockPos) : null;
                        
                        // Build loot parameters for drop calculation
                        LootParams.Builder lootParamsBuilder = (new LootParams.Builder(serverLevel))
                            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(blockPos))
                            .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                            .withOptionalParameter(LootContextParams.BLOCK_ENTITY, blockEntity)
                            .withOptionalParameter(LootContextParams.THIS_ENTITY, this.source);
                        
                        // Add explosion radius for decay calculations
                        if (this.blockInteraction == Explosion.BlockInteraction.DESTROY_WITH_DECAY) {
                            lootParamsBuilder.withParameter(LootContextParams.EXPLOSION_RADIUS, this.radius);
                        }
                        
                        // Handle pre-break events
                        blockState.spawnAfterBreak(serverLevel, blockPos, ItemStack.EMPTY, sourceIsPlayer);
                        
                        // Calculate drops and combine with existing drops
                        blockState.getDrops(lootParamsBuilder).forEach((dropStack) -> {
                            addBlockDrops(itemDrops, dropStack, immutablePos);
                        });
                    }
                }
                
                // Call block's explosion handler (e.g., TNT priming)
                blockState.onBlockExploded(this.level, blockPos, this);
                this.level.getProfiler().pop();
            }
        }
        
        // Spawn all collected item drops
        for(Pair<ItemStack, BlockPos> dropPair : itemDrops) {
            Block.popResource(this.level, dropPair.getSecond(), dropPair.getFirst());
        }
    }
    
    // Create fire if enabled
    if (this.fire) {
        for(BlockPos blockPos : this.toBlow) {
            // 1/3 chance per block, must be air with solid block below
            if (this.random.nextInt(3) == 0 && 
                this.level.getBlockState(blockPos).isAir() && 
                this.level.getBlockState(blockPos.below()).isSolidRender(this.level, blockPos.below())) {
                this.level.setBlockAndUpdate(blockPos, BaseFireBlock.getState(this.level, blockPos));
            }
        }
    }
}
```

---

## Supporting Classes

### ExplosionDamageCalculator

The base class for calculating block-specific explosion resistance:

```java
public class ExplosionDamageCalculator {
    
    /**
     * Get the explosion resistance of a block at a specific position.
     * 
     * @param explosion The explosion causing damage
     * @param levelAccessor The level being accessed
     * @param blockPos Position of the block
     * @param blockState The block's state
     * @param fluidState The fluid state at the position (for water/lava)
     * @return Optional containing resistance value, or empty if air/empty
     */
    public Optional<Float> getBlockExplosionResistance(
        Explosion explosion, 
        BlockGetter levelAccessor, 
        BlockPos blockPos, 
        BlockState blockState, 
        FluidState fluidState
    ) {
        // Return empty for air blocks (they offer no resistance)
        return blockState.isAir() && fluidState.isEmpty() ? 
            Optional.empty() : 
            // Return max of block and fluid resistance
            Optional.of(Math.max(
                blockState.getExplosionResistance(levelAccessor, blockPos, explosion),
                fluidState.getExplosionResistance(levelAccessor, blockPos, explosion)
            ));
    }
    
    /**
     * Determine if a block should be destroyed by the explosion.
     * 
     * @param explosion The explosion causing damage
     * @param levelAccessor The level being accessed
     * @param blockPos Position of the block
     * @param blockState The block's state
     * @param remainingBlast The remaining blast strength at this position
     * @return true if the block should be destroyed
     */
    public boolean shouldBlockExplode(
        Explosion explosion, 
        BlockGetter levelAccessor, 
        BlockPos blockPos, 
        BlockState blockState, 
        float remainingBlast
    ) {
        // Default: all blocks with sufficient blast are destroyed
        return true;
    }
}
```

### EntityBasedExplosionDamageCalculator

A variant that uses the explosion source entity to modify resistance calculations:

```java
public class EntityBasedExplosionDamageCalculator extends ExplosionDamageCalculator {
    private final Entity explosionSource;
    
    public EntityBasedExplosionDamageCalculator(Entity source) {
        this.explosionSource = source;
    }
    
    @Override
    public Optional<Float> getBlockExplosionResistance(
        Explosion explosion, 
        BlockGetter levelAccessor, 
        BlockPos blockPos, 
        BlockState blockState, 
        FluidState fluidState
    ) {
        // Get base resistance
        return super.getBlockExplosionResistance(
            explosion, levelAccessor, blockPos, blockState, fluidState
        ).map((baseResistance) -> {
            // Apply entity-specific modifications (e.g., creeper, TNT, Wither)
            return this.explosionSource.getBlockExplosionResistance(
                explosion, levelAccessor, blockPos, blockState, fluidState, baseResistance
            );
        });
    }
    
    @Override
    public boolean shouldBlockExplode(
        Explosion explosion, 
        BlockGetter levelAccessor, 
        BlockPos blockPos, 
        BlockState blockState, 
        float remainingBlast
    ) {
        // Let the source entity decide (e.g., Wither can destroy bedrock)
        return this.explosionSource.shouldBlockExplode(
            explosion, levelAccessor, blockPos, blockState, remainingBlast
        );
    }
}
```

---

## Forge Integration

### ExplosionEvent

MinecraftForge provides hooks to intercept and modify explosions:

```java
// Event fired before the explosion occurs - CAN BE CANCELED
@Cancelable
public static class Start extends ExplosionEvent {
    public Start(Level level, Explosion explosion);
}

// Event fired after block/entity lists are determined - modifiable
public static class Detonate extends ExplosionEvent {
    public Detonate(Level level, Explosion explosion, List<Entity> entityList);
    
    // Get affected blocks (modifiable)
    public List<BlockPos> getAffectedBlocks();
    
    // Get affected entities (modifiable)
    public List<Entity> getAffectedEntities();
}
```

#### Usage Example

```java
// Listen for explosion start and cancel it
@SubscribeEvent
public void onExplosionStart(ExplosionEvent.Start event) {
    if (shouldPreventExplosion(event.getExplosion())) {
        event.setCanceled(true);
    }
}

// Modify affected blocks
@SubscribeEvent
public void onExplosionDetonate(ExplosionEvent.Detonate event) {
    // Remove obsidian from destruction list
    event.getAffectedBlocks().removeIf(pos -> 
        event.getLevel().getBlockState(pos).getBlock() == Blocks.OBSIDIAN
    );
}
```

---

## Loot and Survival Mechanics

### ExplosionCondition (Survives Explosion)

Loot table condition that determines if items survive explosions:

```java
public class ExplosionCondition implements LootItemCondition {
    static final ExplosionCondition INSTANCE = new ExplosionCondition();
    
    @Override
    public boolean test(LootContext context) {
        Float explosionRadius = context.getParamOrNull(LootContextParams.EXPLOSION_RADIUS);
        if (explosionRadius != null) {
            // Survival chance = 1 / explosion radius
            // Larger explosions = lower survival chance
            RandomSource random = context.getRandom();
            float survivalChance = 1.0F / explosionRadius;
            return random.nextFloat() <= survivalChance;
        }
        // If no radius, item always survives
        return true;
    }
}
```

### ApplyExplosionDecay

Loot table function that applies decay to items that survived:

```java
public class ApplyExplosionDecay extends LootItemConditionalFunction {
    
    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        Float explosionRadius = context.getParamOrNull(LootContextParams.EXPLOSION_RADIUS);
        if (explosionRadius != null) {
            RandomSource random = context.getRandom();
            float survivalChance = 1.0F / explosionRadius;
            int originalCount = stack.getCount();
            int surviving = 0;
            
            // Each item individually checks survival
            for(int i = 0; i < originalCount; i++) {
                if (random.nextFloat() <= survivalChance) {
                    surviving++;
                }
            }
            
            stack.setCount(surviving);
        }
        return stack;
    }
}
```

#### Example Survival Rates

| Explosion Source | Radius | Survival Chance |
|-----------------|--------|-----------------|
| TNT             | 4.0    | 25%             |
| Creeper         | 3.0    | 33%             |
| Wither          | 7.0    | 14%             |
| End Crystal     | 7.0    | 14%             |

---

### Particle Effects

#### Huge Explosion Particles

For large explosions (`radius >= 2.0`):

```java
@OnlyIn(Dist.CLIENT)
public class HugeExplosionParticle extends TextureSheetParticle {
    public HugeExplosionParticle(ClientLevel level, double x, double y, double z, 
                                  double motion, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0);
        this.lifetime = 6 + random.nextInt(4); // 6-10 ticks
        float color = random.nextFloat() * 0.6F + 0.4F; // Orange tint
        this.rCol = this.gCol = this.bCol = color;
        this.quadSize = 2.0F * (1.0F - motion * 0.5F); // Size based on motion
        this.setSpriteFromAge(sprites);
    }
}
```

#### Huge Explosion Seed Particles

Special particle that spawns child explosion particles:

```java
@OnlyIn(Dist.CLIENT)
public class HugeExplosionSeedParticle extends NoRenderParticle {
    private int life;
    private final int lifeTime = 8; // 8 ticks lifespan
    
    @Override
    public void tick() {
        // Spawn 6 child explosion particles around the seed
        for(int i = 0; i < 6; i++) {
            double offsetX = this.x + (random.nextDouble() - random.nextDouble()) * 4.0D;
            double offsetY = this.y + (random.nextDouble() - random.nextDouble()) * 4.0D;
            double offsetZ = this.z + (random.nextDouble() - random.nextDouble()) * 4.0D;
            this.level.addParticle(ParticleTypes.EXPLOSION, offsetX, offsetY, offsetZ, ...);
        }
        
        this.life++;
        if (this.life == this.lifeTime) {
            this.remove();
        }
    }
}
```

---

## Summary Flow Chart

```
┌─────────────────────────────────────────────────────────────┐
│                    EXPLOSION CREATED                        │
│  (TNT primed, Creeper explodes, Wither attacks, etc.)      │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                   Explosion.explode()                       │
│  1. Emit GameEvent.EXPLODE                                  │
│  2. Calculate block destruction via ray casting             │
│     - 16x16x16 cube surface sampling                        │
│     - 0.3 block ray increments                              │
│     - Block resistance subtraction                          │
│  3. Get entities in blast bounding box                      │
│  4. ForgeEventFactory.onExplosionDetonate()                 │
│  5. Calculate damage per entity:                            │
│     - Normalized distance (0-1)                             │
│     - Visibility percent (line of sight)                    │
│     - Quadratic damage formula                              │
│  6. Apply knockback with protection dampening               │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│              Explosion.finalizeExplosion()                  │
│  1. Play explosion sound (client side)                      │
│  2. Spawn particles (EXPLOSION / EXPLOSION_EMITTER)         │
│  3. Process block destruction:                              │
│     - Shuffle for randomness                                │
│     - Calculate loot with explosion_decay                   │
│     - Call Block.onBlockExploded()                          │
│     - Spawn item drops                                      │
│  4. Create fire if enabled (1/3 chance per position)        │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                    EXPLOSION COMPLETE                       │
└─────────────────────────────────────────────────────────────┘
```

---

## Key Configurable Values by Explosion Source

| Source | Radius | Fire | Interaction | Notes |
|--------|--------|------|-------------|-------|
| TNT | 4.0F | false | DESTROY_WITH_DECAY | Standard explosion |
| Creeper | 3.0F | false | DESTROY_WITH_DECAY | Smaller radius |
| Wither Skull | 1.0F | false | DESTROY_WITH_DECAY | Can destroy bedrock |
| Wither | 7.0F | false | DESTROY_WITH_DECAY | Large area damage |
| End Crystal | 7.0F | true | DESTROY_WITH_DECAY | Creates fire |
| Ghast Fireball | 1.0F | true | DESTROY_WITH_DECAY | Small but creates fire |
| Dragon Fireball | 3.0F | true | DESTROY_WITH_DECAY | Medium with fire |

---

## Block Resistance Values (Examples)

| Block | Resistance | Can Resist TNT? |
|-------|------------|-----------------|
| Bedrock | 3,600,000 | Yes |
| Obsidian | 1,200 | Yes |
| Ancient Debris | 1,200 | Yes |
| End Portal Frame | 3,600,000 | Yes |
| Ender Chest | 600 | No |
| Blackstone | 6.0 | No |
| Dirt | 0.5 | No |
| Wood | 2.0 | No |

*Explosion resistance threshold: ~4.0 blocks of blast power can destroy a block with equal resistance value*

---

## Modding Recommendations

For the **ChunkedExplosions** mod, consider the following modification points:

1. **Intercept at `explode()` method** - Redistribute `toBlow` list across chunks before processing
2. **Modify `shouldBlockExplode()`** - Add custom block destruction logic  
3. **Hook into `finalizeExplosion()`** - Control when and how chunk updates occur
4. **Use `ExplosionEvent.Detonate`** - Access and modify the final destruction list
5. **Custom `ExplosionDamageCalculator`** - Override resistance calculations for specific blocks

The key entry point for chunk-based optimization would be between the ray casting phase and the finalize phase, where `toBlow` contains the complete list but hasn't been processed yet.