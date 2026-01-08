package nl.luchermkens.speedrunutils;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public record FreezeState(
    RegistryKey<World> worldKey,
    Vec3d pos,
    float yaw,
    float pitch,
    int air,
    int fireTicks
) {}
