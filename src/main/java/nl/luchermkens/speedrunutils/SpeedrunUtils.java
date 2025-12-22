package nl.luchermkens.speedrunutils;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.entity.mob.BlazeEntity;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import nl.luchermkens.speedrunutils.command.StartRunCommand;
import nl.luchermkens.speedrunutils.command.PauseRunCommand;
import nl.luchermkens.speedrunutils.command.NewRunCommand;
import nl.luchermkens.speedrunutils.command.StopRunCommand;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpeedrunUtils implements ModInitializer {
	public static final String MOD_ID = "speedrunutils";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing SpeedrunUtils mod...");

		// Register commands
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			StartRunCommand.register(dispatcher);
			PauseRunCommand.register(dispatcher);
			NewRunCommand.register(dispatcher);
			StopRunCommand.register(dispatcher);
		});

		// Update timer every tick
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			RunStateManager manager = RunStateManager.getInstance();
			manager.updateTimer(server);
			manager.updateAggregateItemSplits(server);
		});

		// Listen for entity deaths (for blaze kill detection)
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (entity instanceof BlazeEntity && !entity.getEntityWorld().isClient()) {
				RunStateManager manager = RunStateManager.getInstance();

				if (manager.getState() == RunStateManager.RunState.RUNNING &&
					!manager.hasSplit(RunStateManager.Split.FIRST_BLAZE_KILLED)) {

					// Check if killed by a player
					if (damageSource.getAttacker() instanceof ServerPlayerEntity) {
						manager.recordSplit(RunStateManager.Split.FIRST_BLAZE_KILLED,
							entity.getEntityWorld().getServer());
					}
				}
			}
		});

		// Listen for dimension changes (including end portal)
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
			RunStateManager manager = RunStateManager.getInstance();

			if (manager.getState() != RunStateManager.RunState.RUNNING) return;

			// Record splits for dimension changes
			if (destination.getRegistryKey().equals(World.NETHER) &&
				!manager.hasSplit(RunStateManager.Split.ENTERED_NETHER)) {
				manager.recordSplit(RunStateManager.Split.ENTERED_NETHER, destination.getServer());
			}

			if (destination.getRegistryKey().equals(World.END) &&
				!manager.hasSplit(RunStateManager.Split.ENTERED_END)) {
				manager.recordSplit(RunStateManager.Split.ENTERED_END, destination.getServer());
			}

			// Check for run completion
			if (!manager.isDragonKilled()) return;

			// Check if leaving End to Overworld (completing the run)
			if (!origin.getRegistryKey().equals(World.END)) return;
			if (!destination.getRegistryKey().equals(World.OVERWORLD)) return;

			// Run completed!
			manager.completeRun();

			String time = manager.getFormattedTime();
			destination.getServer().getPlayerManager().broadcast(Text.literal("§6§l=== RUN COMPLETED ==="), false);
			destination.getServer().getPlayerManager().broadcast(Text.literal("§aFinal Time: §e" + time), false);
			destination.getServer().getPlayerManager().broadcast(Text.literal("§6Use §e/newrun §6to start a new speedrun!"), false);

			// Automatically save the completed run
			manager.saveRunToFile(destination.getServer());

			LOGGER.info("Speedrun completed in: {}", time);
		});

		LOGGER.info("SpeedrunUtils mod initialized successfully!");
	}
}
