package nl.luchermkens.speedrunutils.mixin;

import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
import nl.luchermkens.speedrunutils.RunStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnderDragonFight.class)
public class EnderDragonFightMixin {
    @Inject(method = "dragonKilled", at = @At("HEAD"))
    private void onDragonKilled(EnderDragonEntity dragon, CallbackInfo ci) {
        RunStateManager manager = RunStateManager.getInstance();
        if (manager.getState() == RunStateManager.RunState.RUNNING) {
            manager.setDragonKilled(true);
        }
    }
}
