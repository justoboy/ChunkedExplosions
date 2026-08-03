package com.github.justoboy.chunkedexplosions.mixin.world.level;

import com.github.justoboy.chunkedexplosions.iduck.world.level.IExplosionDuck;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;

import java.util.Map;

@Mixin(Explosion.class)
public abstract class ExplosionMixin implements IExplosionDuck {

    @Final @Shadow private Level level;
    @Final @Shadow private Entity source;
    @Final @Shadow private double x;
    @Final @Shadow private double y;
    @Final @Shadow private double z;
    @Final @Shadow private float radius;
    @Shadow @Final private DamageSource damageSource;
    @Shadow @Final private Map<Player, Vec3> hitPlayers;
    @Shadow @Final private Explosion.BlockInteraction blockInteraction;
    @Shadow @Final private boolean fire;

    protected ExplosionMixin() {
    }

    @Override
    public Level chunked_getLevel() {
        return this.level;
    }

    @Override
    public Entity chunked_getSource() {
        return this.source;
    }

    @Override
    public double chunked_getX() {
        return this.x;
    }

    @Override
    public double chunked_getY() {
        return this.y;
    }

    @Override
    public double chunked_getZ() {
        return this.z;
    }

    @Override
    public float chunked_getRadius() {
        return this.radius;
    }

    @Override
    public boolean chunked_isFire() {
        return this.fire;
    }

    @Override
    public Explosion.BlockInteraction chunked_getBlockInteraction() {
        return this.blockInteraction;
    }

    @Override
    public DamageSource chunked_getDamageSource() {
        return this.damageSource;
    }

    @Override
    public Vec3 chunked_getPosition() {
        return new Vec3(this.x, this.y, this.z);
    }

    /**
     * DEPRECATED: This method is no longer used. All explosion processing is handled by
     * ExplosionState which pre-calculates all blocks to destroy and processes them over
     * multiple ticks. This method exists only for IExplosionDuck interface compatibility.
     */
    @Override
    public void chunked_explode() {
        // This method should never be called in normal operation.
        // The ExplosionState system handles all pre-calculation in ExplosionState.preCalculate()
        // and processes block destruction in ExplosionState.processTick().
    }

    /**
     * DEPRECATED: This method is no longer used. All explosion finalization is handled by
     * ExplosionState. This method exists only for IExplosionDuck interface compatibility.
     */
    @Override
    public void chunked_finalize() {
        // This method should never be called in normal operation.
        // The ExplosionState system handles all finalization in ExplosionState.
    }
}
