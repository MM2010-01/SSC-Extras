package sscextras.drake;

import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

interface FeralBrain {
    World world();
    void think();
    Vec3d movement();
    default void stop() { }
}
