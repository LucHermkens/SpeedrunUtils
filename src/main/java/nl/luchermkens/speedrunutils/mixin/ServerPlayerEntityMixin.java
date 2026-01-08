package nl.luchermkens.speedrunutils.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import nl.luchermkens.speedrunutils.FreezeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerPlayerEntity.class, priority = 2000)
public abstract class ServerPlayerEntityMixin {
    /**
     * Prevent frozen players from ticking entirely to avoid iterator/state issues.
     * This prevents ConcurrentModificationException and NoSuchElementException
     * that occur when entity collections (like status effects) are modified
     * during iteration.
     *
     * High priority (2000) ensures this runs before other mods' mixins.
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 1)
    private void speedrunutils$preventFrozenTick(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;

        if (FreezeManager.isFrozen(player)) {
            // Cancel the entire tick for frozen players
            // The FreezeManager.tick() will handle position enforcement separately
            ci.cancel();
        }
    }

    /**
     * Also prevent playerTick (the player-specific tick logic) for frozen players.
     * This is a safety measure in case tick() still gets called somehow.
     */
    @Inject(method = "playerTick", at = @At("HEAD"), cancellable = true, require = 0)
    private void speedrunutils$preventFrozenPlayerTick(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;

        if (FreezeManager.isFrozen(player)) {
            ci.cancel();
        }
    }
}
