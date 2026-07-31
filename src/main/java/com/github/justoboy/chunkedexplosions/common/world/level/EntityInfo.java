package com.github.justoboy.chunkedexplosions.common.world.level;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Pre-calculated entity data for explosion effects.
 * Stores all the computed values needed to apply damage and knockback
 * during the processing phase without re-computing visibility or impact.
 */
public class EntityInfo {

    /** The entity to affect */
    private final Entity entity;

    /** Distance from the entity to the explosion center */
    private final float distance;

    /** Visibility factor (0.0 to 1.0) - how much of the explosion the entity sees */
    private final float visibility;

    /** Combined impact factor based on distance and visibility */
    private final float impactFactor;

    /** Calculated damage value */
    private final float damage;

    /** Pre-calculated knockback vector */
    private final Vec3 knockbackVector;

    /** Whether this entity has been damaged yet */
    private boolean alreadyDamaged;

    /** Whether this entity has been knocked back yet */
    private boolean alreadyKnockedBack;

    /**
     * Creates a new EntityInfo with pre-calculated explosion effect data.
     *
     * @param entity            the entity to affect
     * @param distance          distance from explosion center (normalized)
     * @param visibility        visibility factor (0.0 to 1.0)
     * @param impactFactor      combined impact factor
     * @param damage            calculated damage value
     * @param knockbackVector   pre-calculated knockback vector
     */
    public EntityInfo(Entity entity, float distance, float visibility, float impactFactor, float damage, Vec3 knockbackVector) {
        this.entity = entity;
        this.distance = distance;
        this.visibility = visibility;
        this.impactFactor = impactFactor;
        this.damage = damage;
        this.knockbackVector = knockbackVector;
        this.alreadyDamaged = false;
        this.alreadyKnockedBack = false;
    }

    public Entity getEntity() {
        return entity;
    }

    public float getDistance() {
        return distance;
    }

    public float getVisibility() {
        return visibility;
    }

    public float getImpactFactor() {
        return impactFactor;
    }

    public float getDamage() {
        return damage;
    }

    public Vec3 getKnockbackVector() {
        return knockbackVector;
    }

    public boolean isAlreadyDamaged() {
        return alreadyDamaged;
    }

    public void setAlreadyDamaged(boolean damaged) {
        this.alreadyDamaged = damaged;
    }

    public boolean isAlreadyKnockedBack() {
        return alreadyKnockedBack;
    }

    public void setAlreadyKnockedBack(boolean knockedBack) {
        this.alreadyKnockedBack = knockedBack;
    }

    @Override
    public String toString() {
        return "EntityInfo{entity=" + entity.getName().getString() +
               ", distance=" + distance +
               ", visibility=" + visibility +
               ", damage=" + damage +
               ", alreadyDamaged=" + alreadyDamaged +
               ", alreadyKnockedBack=" + alreadyKnockedBack +
               "}";
    }
}
