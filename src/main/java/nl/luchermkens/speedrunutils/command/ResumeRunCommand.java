package nl.luchermkens.speedrunutils.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import nl.luchermkens.speedrunutils.RunStateManager;

public class ResumeRunCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("resumerun")
            .executes(ResumeRunCommand::execute));
    }

    private static int execute(CommandContext<ServerCommandSource> context) {
        RunStateManager manager = RunStateManager.getInstance();

        if (manager.getState() == RunStateManager.RunState.PAUSED) {
            manager.resumeRun();

            // Unfreeze time
            manager.unfreezeTime(context.getSource().getServer());

            // Remove blindness from all players
            for (ServerPlayerEntity player : context.getSource().getServer().getPlayerManager().getPlayerList()) {
                manager.removeBlindness(player);
            }

            context.getSource().getServer().getPlayerManager().broadcast(
                Text.literal("§aRun resumed! Timer continuing."),
                false
            );
            return 1;
        } else if (manager.getState() == RunStateManager.RunState.RUNNING) {
            context.getSource().sendError(Text.literal("Run is already running!"));
            return 0;
        } else {
            context.getSource().sendError(Text.literal("No run in progress!"));
            return 0;
        }
    }
}
