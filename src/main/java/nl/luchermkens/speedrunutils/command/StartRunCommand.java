package nl.luchermkens.speedrunutils.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import nl.luchermkens.speedrunutils.RunStateManager;

public class StartRunCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("startrun")
            .executes(StartRunCommand::execute));
    }

    private static int execute(CommandContext<ServerCommandSource> context) {
        RunStateManager manager = RunStateManager.getInstance();

        if (manager.getState() != RunStateManager.RunState.NOT_STARTED) {
            context.getSource().sendError(Text.literal("Run has already started!"));
            return 0;
        }

        manager.setState(RunStateManager.RunState.COUNTDOWN);

        // Start countdown in a separate thread
        new Thread(() -> {
            try {
                for (int i = 3; i > 0; i--) {
                    final int count = i;
                    context.getSource().getServer().execute(() -> {
                        for (ServerPlayerEntity player : context.getSource().getServer().getPlayerManager().getPlayerList()) {
                            player.sendMessage(Text.literal("§e" + count + "..."), true);
                            player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 1.0f, 1.0f);
                        }
                    });
                    Thread.sleep(1000);
                }

                // Start the run
                context.getSource().getServer().execute(() -> {
                    manager.startRun(context.getSource().getServer());

                    for (ServerPlayerEntity player : context.getSource().getServer().getPlayerManager().getPlayerList()) {
                        manager.removeBlindness(player);
                        player.sendMessage(Text.literal("§aGO! Timer started!"), true);
                        player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.0f, 1.0f);
                    }
                });
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

        return 1;
    }
}
