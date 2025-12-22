package nl.luchermkens.speedrunutils.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import nl.luchermkens.speedrunutils.RunStateManager;
import nl.luchermkens.speedrunutils.SpeedrunUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class NewRunCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("newrun")
            .executes(NewRunCommand::execute));
    }

    private static int execute(CommandContext<ServerCommandSource> context) {
        RunStateManager manager = RunStateManager.getInstance();

        // Save the current run if it was completed
        if (manager.getState() == RunStateManager.RunState.COMPLETED) {
            manager.saveRunToFile(context.getSource().getServer());
        } else if (manager.getState() == RunStateManager.RunState.RUNNING ||
                   manager.getState() == RunStateManager.RunState.PAUSED) {
            manager.saveRunToFile(context.getSource().getServer()); // Save even if not completed, but mark as incomplete
        }

        // Reset the run state
        manager.reset();

        // Apply blindness to all current players
        for (ServerPlayerEntity player : context.getSource().getServer().getPlayerManager().getPlayerList()) {
            manager.applyBlindness(player);
        }

        context.getSource().getServer().getPlayerManager().broadcast(
            Text.literal("§6New run starting! Use /startrun when ready. Server will reset shortly..."),
            false
        );

        // Schedule world regeneration
        new Thread(() -> {
            try {
                Thread.sleep(5000); // Give 5 seconds notice
                context.getSource().getServer().execute(() -> {
                    context.getSource().getServer().getPlayerManager().broadcast(
                        Text.literal("§cRegenerating world... Players will be kicked."),
                        false
                    );

                    // Note: Actual world regeneration requires server restart
                    // TODO: Implement world regeneration
                    // This is typically handled by server management tools
                    context.getSource().getServer().getPlayerManager().broadcast(
                        Text.literal("§eWorld regeneration requires server restart. Please restart the server with a new world seed."),
                        false
                    );
                });
            } catch (InterruptedException e) {
                SpeedrunUtils.LOGGER.error("Error during new run countdown", e);
            }
        }).start();

        return 1;
    }
}

