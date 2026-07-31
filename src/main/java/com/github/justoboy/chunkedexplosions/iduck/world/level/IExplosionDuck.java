package com.github.justoboy.chunkedexplosions.iduck.world.level;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public interface IExplosionDuck {
    void chunked_explode();
    void chunked_update();
    void chunked_finalize();
    
    // Accessor methods for private Explosion fields
    Level chunked_getLevel();
    Entity chunked_getSource();
    double chunked_getX();
    double chunked_getY();
    double chunked_getZ();
    float chunked_getRadius();
    boolean chunked_isFire();
    Explosion.BlockInteraction chunked_getBlockInteraction();
    DamageSource chunked_getDamageSource();
    Vec3 chunked_getPosition();
}