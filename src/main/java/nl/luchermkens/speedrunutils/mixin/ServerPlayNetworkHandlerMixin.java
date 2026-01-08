package nl.luchermkens.speedrunutils.mixin;

import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import nl.luchermkens.speedrunutils.FreezeManager;
import nl.luchermkens.speedrunutils.FreezeState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @Shadow public abstract ServerPlayerEntity getPlayer();

    @Shadow public abstract void requestTeleport(double x, double y, double z, float yaw, float pitch);

    @Shadow public abstract void resetFloatingTicks();

    private void speedrunutils$snapAndCancel(ServerPlayerEntity player, CallbackInfo ci) {
        FreezeState state = FreezeManager.get(player);
        if (state == null) return;

        // Hard snap right away (prevents "client-side drifting" feeling)
        this.requestTeleport(state.pos().x, state.pos().y, state.pos().z, state.yaw(), state.pitch());
        this.resetFloatingTicks();

        // Also kill any server-side motion that might have been applied
        FreezeManager.enforce(player, state);

        ci.cancel();
    }

    @Inject(method = "onPlayerMove", at = @At("HEAD"), cancellable = true)
    private void speedrunutils$onPlayerMove(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        speedrunutils$snapAndCancel(this.getPlayer(), ci);
    }

    @Inject(method = "onVehicleMove", at = @At("HEAD"), cancellable = true)
    private void speedrunutils$onVehicleMove(VehicleMoveC2SPacket packet, CallbackInfo ci) {
        speedrunutils$snapAndCancel(this.getPlayer(), ci);
    }

    @Inject(method = "onPlayerInput", at = @At("HEAD"), cancellable = true)
    private void speedrunutils$onPlayerInput(PlayerInputC2SPacket packet, CallbackInfo ci) {
        speedrunutils$snapAndCancel(this.getPlayer(), ci);
    }

    @Inject(method = "onPlayerAction", at = @At("HEAD"), cancellable = true)
    private void speedrunutils$onPlayerAction(PlayerActionC2SPacket packet, CallbackInfo ci) {
        speedrunutils$snapAndCancel(this.getPlayer(), ci);
    }

    @Inject(method = "onPlayerInteractBlock", at = @At("HEAD"), cancellable = true)
    private void speedrunutils$onPlayerInteractBlock(PlayerInteractBlockC2SPacket packet, CallbackInfo ci) {
        speedrunutils$snapAndCancel(this.getPlayer(), ci);
    }

    @Inject(method = "onPlayerInteractItem", at = @At("HEAD"), cancellable = true)
    private void speedrunutils$onPlayerInteractItem(PlayerInteractItemC2SPacket packet, CallbackInfo ci) {
        speedrunutils$snapAndCancel(this.getPlayer(), ci);
    }

    @Inject(method = "onPlayerInteractEntity", at = @At("HEAD"), cancellable = true)
    private void speedrunutils$onPlayerInteractEntity(PlayerInteractEntityC2SPacket packet, CallbackInfo ci) {
        speedrunutils$snapAndCancel(this.getPlayer(), ci);
    }

    @Inject(method = "onHandSwing", at = @At("HEAD"), cancellable = true)
    private void speedrunutils$onHandSwing(HandSwingC2SPacket packet, CallbackInfo ci) {
        speedrunutils$snapAndCancel(this.getPlayer(), ci);
    }
}
