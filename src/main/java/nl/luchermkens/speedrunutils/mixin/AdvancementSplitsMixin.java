package nl.luchermkens.speedrunutils.mixin;

import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import nl.luchermkens.speedrunutils.RunStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancementTracker.class)
public class AdvancementSplitsMixin {
    @Shadow
    private ServerPlayerEntity owner;

    @Inject(method = "grantCriterion", at = @At("RETURN"))
    private void onAdvancementGrant(AdvancementEntry advancement, String criterionName, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            RunStateManager manager = RunStateManager.getInstance();

            if (manager.getState() != RunStateManager.RunState.RUNNING) return;

            String advancementId = advancement.id().toString();

            // Check for "Acquire Hardware" - getting iron ingot
            if (advancementId.contains("story/smelt_iron") &&
                !manager.hasSplit(RunStateManager.Split.IRON_RETRIEVED)) {
                manager.recordSplit(RunStateManager.Split.IRON_RETRIEVED,
                    owner.getEntityWorld().getServer());
            }

            // Check for "Eye Spy" - entering stronghold
            if (advancementId.contains("story/follow_ender_eye") &&
                !manager.hasSplit(RunStateManager.Split.FOUND_STRONGHOLD)) {
                manager.recordSplit(RunStateManager.Split.FOUND_STRONGHOLD,
                    owner.getEntityWorld().getServer());
            }

            // Check for "A Terrible Fortress" - entering a nether fortress
            // Vanilla advancement id: minecraft:nether/find_fortress
            if (advancementId.contains("nether/find_fortress") &&
                !manager.hasSplit(RunStateManager.Split.ENTERED_NETHER_FORTRESS)) {
                manager.recordSplit(RunStateManager.Split.ENTERED_NETHER_FORTRESS,
                    owner.getEntityWorld().getServer());
            }
        }
    }
}


