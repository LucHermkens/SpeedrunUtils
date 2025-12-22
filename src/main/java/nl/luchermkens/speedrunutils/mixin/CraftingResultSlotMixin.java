package nl.luchermkens.speedrunutils.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.server.network.ServerPlayerEntity;
import nl.luchermkens.speedrunutils.RunStateManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CraftingResultSlot.class)
public class CraftingResultSlotMixin {
    @Shadow @Final private PlayerEntity player;

    // Normal click pickup crafting
    @Inject(method = "onTakeItem", at = @At("TAIL"))
    private void speedrunutils$onTakeItem(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        tryRecordSplit(player, stack);
    }

    // Shift-click crafting (quick-move) hits takeStack() instead of onTakeItem()
    @Inject(method = "takeStack", at = @At("RETURN"))
    private void speedrunutils$takeStack(int amount, CallbackInfoReturnable<ItemStack> cir) {
        tryRecordSplit(this.player, cir.getReturnValue());
    }

    // Some crafting flows (notably quick-move crafting) call onCrafted without invoking onTakeItem/takeStack.
    @Inject(method = "onCrafted(Lnet/minecraft/item/ItemStack;)V", at = @At("TAIL"))
    private void speedrunutils$onCrafted(ItemStack stack, CallbackInfo ci) {
        tryRecordSplit(this.player, stack);
    }

    private static void tryRecordSplit(PlayerEntity player, ItemStack stack) {
        if (!(player instanceof ServerPlayerEntity serverPlayer) || player.getEntityWorld().isClient()) return;

        RunStateManager manager = RunStateManager.getInstance();
        if (manager.getState() != RunStateManager.RunState.RUNNING) return;

        if (stack.isOf(Items.ENDER_EYE) && !manager.hasSplit(RunStateManager.Split.CRAFTED_FIRST_ENDER_EYE)) {
            manager.recordSplit(RunStateManager.Split.CRAFTED_FIRST_ENDER_EYE, serverPlayer.getEntityWorld().getServer());
        }
    }
}


