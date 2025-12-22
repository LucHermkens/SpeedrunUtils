package nl.luchermkens.speedrunutils.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import nl.luchermkens.speedrunutils.RunStateManager;

public class PauseRunCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("pauserun")
            .executes(PauseRunCommand::execute));
    }

    private static int execute(CommandContext<ServerCommandSource> context) {
        RunStateManager manager = RunStateManager.getInstance();

        if (manager.getState() == RunStateManager.RunState.RUNNING) {
            manager.pauseRun();

            // Apply blindness to all players
            for (ServerPlayerEntity player : context.getSource().getServer().getPlayerManager().getPlayerList()) {
                manager.applyBlindness(player);
            }

            // Store whether daylight cycle was enabled for restoration
            for (ServerWorld world : context.getSource().getServer().getWorlds()) {
                // Note: time freeze is visual only in this implementation
                // Full time freeze would require more complex mixin injections
                // TODO: Implement time freeze
            }

            context.getSource().getServer().getPlayerManager().broadcast(
                Text.literal("§eRun paused! Timer stopped."),
                false
            );
            return 1;
        } else if (manager.getState() == RunStateManager.RunState.PAUSED) {
            manager.resumeRun();

            // Remove blindness from all players
            for (ServerPlayerEntity player : context.getSource().getServer().getPlayerManager().getPlayerList()) {
                manager.removeBlindness(player);
            }

            context.getSource().getServer().getPlayerManager().broadcast(
                Text.literal("§aRun resumed! Timer continuing."),
                false
            );
            return 1;
        } else {
            context.getSource().sendError(Text.literal("No run in progress!"));
            return 0;
        }
    }
}
