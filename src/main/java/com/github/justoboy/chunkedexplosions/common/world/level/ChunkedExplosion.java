package com.github.justoboy.chunkedexplosions.common.world.level;
import com.github.justoboy.chunkedexplosions.iduck.world.level.IExplosionDuck;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Explosion;
import org.slf4j.Logger;

import java.util.List;

public class ChunkedExplosion {
    private final Explosion explosion;
    private boolean initialized = false;
    private static final Logger LOGGER = LogUtils.getLogger();

    public ChunkedExplosion(Explosion explosion) {
        this.explosion = explosion;
    }

    public boolean tick() {
        List<BlockPos> toBlow = this.explosion.getToBlow();
        if (!initialized || !toBlow.isEmpty()) {
            initialized = true;
            // Cast the Explosion instance to IExplosionDuck and call chunked_explode
            ((IExplosionDuck) explosion).chunked_explode();
            return false;
        }
        return true;
    }

    public void finalizeExplosion() { ((IExplosionDuck) explosion).chunked_finalize(); }

    public void update() { ((IExplosionDuck) explosion).chunked_update(); }
}
