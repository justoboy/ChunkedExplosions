package justoboy.chunkedexplosions.world.level;
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
            this.explosion.explode();
            return false;
        }
        return true;
    }

    public void finalizeExplosion() {
        this.explosion.finalizeExplosion(true);
    }
}
