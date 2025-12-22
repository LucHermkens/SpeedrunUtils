package nl.luchermkens.speedrunutils.mixin;

import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import nl.luchermkens.speedrunutils.RunStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public class PlayerJoinMixin {
    @Inject(at = @At("TAIL"), method = "onPlayerConnect")
    private void onPlayerJoin(ClientConnection connection, ServerPlayerEntity player, ConnectedClientData clientData, CallbackInfo ci) {
        RunStateManager manager = RunStateManager.getInstance();

        // Track player name
        manager.addPlayerName(player.getName().getString());

        // Apply blindness if run hasn't started yet
        if (manager.getState() == RunStateManager.RunState.NOT_STARTED) {
            manager.applyBlindness(player);
            player.sendMessage(Text.literal("§eWaiting for someone to run §6/startrun§e..."), false);
        } else if (manager.getState() == RunStateManager.RunState.PAUSED) {
            manager.applyBlindness(player);
            player.sendMessage(Text.literal("§eRun is currently paused."), false);
        }
    }
}
