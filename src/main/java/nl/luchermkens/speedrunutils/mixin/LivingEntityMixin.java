package nl.luchermkens.speedrunutils.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import nl.luchermkens.speedrunutils.FreezeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LivingEntity.class, priority = 2000)
public abstract class LivingEntityMixin {
    /**
     * Additional safety layer: prevent LivingEntity tick for frozen players.
     * This is a backup in case ServerPlayerEntity.tick() somehow still executes.
     *
     * The ConcurrentModificationException occurs when status effects are being
     * iterated/collected during the tick while something else modifies them.
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0)
    private void speedrunutils$preventFrozenLivingEntityTick(CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;

        // Only check for ServerPlayerEntity instances
        if (entity instanceof ServerPlayerEntity player) {
            if (FreezeManager.isFrozen(player)) {
                ci.cancel();
            }
        }
    }

    /**
     * Prevent the baseTick method as well for frozen players.
     * This method can also cause concurrent modification issues.
     */
    @Inject(method = "baseTick", at = @At("HEAD"), cancellable = true, require = 0)
    private void speedrunutils$preventFrozenBaseTick(CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;

        if (entity instanceof ServerPlayerEntity player) {
            if (FreezeManager.isFrozen(player)) {
                ci.cancel();
            }
        }
    }
}

