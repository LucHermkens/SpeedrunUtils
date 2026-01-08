package nl.luchermkens.speedrunutils;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

public final class FreezeManager {
    private FreezeManager() {}

    private static final Map<UUID, FreezeState> FROZEN = new ConcurrentHashMap<>();
    private static final double SNAP_EPSILON_SQUARED = 1.0E-6;

    public static boolean isFrozen(ServerPlayerEntity player) {
        return FROZEN.containsKey(player.getUuid());
    }

    public static FreezeState get(ServerPlayerEntity player) {
        return FROZEN.get(player.getUuid());
    }

    public static void freeze(ServerPlayerEntity player) {
        if (player.hasVehicle()) player.stopRiding();

        ServerWorld world = (ServerWorld) player.getEntityWorld();
        FROZEN.put(player.getUuid(), new FreezeState(
            world.getRegistryKey(),
            new Vec3d(player.getX(), player.getY(), player.getZ()),
            player.getYaw(),
            player.getPitch(),
            player.getAir(),
            player.getFireTicks()
        ));

        // Make player invulnerable while frozen
        player.setInvulnerable(true);

        // Immediate snap (nice UX + stops "one last step")
        snapNow(player);
    }

    public static void unfreeze(ServerPlayerEntity player) {
        FROZEN.remove(player.getUuid());

        // Remove invulnerability when unfreezing
        player.setInvulnerable(false);
    }

    public static void unfreezeAll() {
        FROZEN.clear();
    }

    /** Call from a server tick event. */
    public static void tick(MinecraftServer server) {
        if (FROZEN.isEmpty()) return;

        Iterator<Map.Entry<UUID, FreezeState>> it = FROZEN.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, FreezeState> e = it.next();

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(e.getKey());
            if (player == null) { // offline
                it.remove();
                continue;
            }

            enforce(player, e.getValue());
        }
    }

    /** Hard-enforce "no movement" even from physics / knockback / pistons / etc. */
    public static void enforce(ServerPlayerEntity player, FreezeState state) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;

        ServerWorld targetWorld = server.getWorld(state.worldKey());
        if (targetWorld == null) return;

        // Disallow vehicle shenanigans while frozen
        if (player.hasVehicle()) player.stopRiding();

        // Kill motion + fall accumulation
        player.setVelocity(Vec3d.ZERO);
        player.fallDistance = 0.0f;

        // Pin oxygen + burning time (prevents drowning and fire damage ticking down)
        player.setAir(state.air());
        player.setFireTicks(state.fireTicks());

        // If something moved them, snap back (same world or cross-world)
        ServerWorld currentWorld = (ServerWorld) player.getEntityWorld();
        boolean wrongWorld = currentWorld != targetWorld;
        Vec3d currentPos = new Vec3d(player.getX(), player.getY(), player.getZ());
        double dist2 = wrongWorld ? Double.POSITIVE_INFINITY : currentPos.squaredDistanceTo(state.pos());

        if (wrongWorld || dist2 > SNAP_EPSILON_SQUARED) {
            // Signature in 1.21.11: teleport(world, x,y,z, flags, yaw,pitch, resetCamera)
            player.teleport(
                targetWorld,
                state.pos().x, state.pos().y, state.pos().z,
                Set.<PositionFlag>of(),
                state.yaw(), state.pitch(),
                false
            );
        } else {
            // Still keep their look locked
            player.setYaw(state.yaw());
            player.setPitch(state.pitch());
        }
    }

    /** Used by packet mixins to rubber-band instantly. */
    public static void snapNow(ServerPlayerEntity player) {
        FreezeState state = get(player);
        if (state == null) return;

        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;

        ServerWorld targetWorld = server.getWorld(state.worldKey());
        if (targetWorld == null) return;

        player.setVelocity(Vec3d.ZERO);
        player.fallDistance = 0.0f;

        player.teleport(
            targetWorld,
            state.pos().x, state.pos().y, state.pos().z,
            Set.<PositionFlag>of(),
            state.yaw(), state.pitch(),
            false
        );
    }
}
