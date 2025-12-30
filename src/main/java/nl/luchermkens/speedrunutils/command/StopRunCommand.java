package nl.luchermkens.speedrunutils.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import nl.luchermkens.speedrunutils.RunStateManager;

public class StopRunCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("stoprun")
            .executes(StopRunCommand::execute));
    }

    private static int execute(CommandContext<ServerCommandSource> context) {
        RunStateManager manager = RunStateManager.getInstance();

        if (manager.getState() == RunStateManager.RunState.NOT_STARTED) {
            context.getSource().sendError(Text.literal("No run to stop!"));
            return 0;
        }

        manager.stopRun(context.getSource().getServer());

        // Ensure players aren't left blind if stopping from a paused run
        for (ServerPlayerEntity player : context.getSource().getServer().getPlayerManager().getPlayerList()) {
            manager.removeBlindness(player);
        }

        context.getSource().getServer().getPlayerManager().broadcast(
            Text.literal("§cRun stopped. Timer cleared."),
            false
        );

        return 1;
    }
}
