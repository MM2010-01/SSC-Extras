package sscextras.player;

import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public interface PlayerBrain {
    World world();
    void think();
    Vec3d movement();
    void stop();
}
