package nl.luchermkens.speedrunutils;

import com.mojang.brigadier.ParseResults;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.scoreboard.*;
import net.minecraft.scoreboard.number.BlankNumberFormat;
import net.minecraft.world.rule.GameRules;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public class RunStateManager {
    private static RunStateManager instance;

    // Split requirements (server-wide, across all online players)
    public static final int REQUIRED_BLAZE_RODS = 7;
    public static final int REQUIRED_ENDER_PEARLS = 14;

    private RunState state = RunState.NOT_STARTED;
    private long startTime = 0;
    private long pausedTime = 0;
    private long totalPausedDuration = 0;
    private Set<String> playerNames = new HashSet<>();
    private boolean dragonKilled = false;

    // Splits tracking
    private Map<Split, Long> splits = new LinkedHashMap<>();
    private ScoreboardObjective splitsObjective = null;
    private long lastAggregateItemCheckMs = 0;

    // Player position tracking for complete freeze
    private Map<UUID, double[]> frozenPositions = new HashMap<>();

    // Store previous game rule values for restoration
    private int previousFireSpreadRadius = 128;

    public enum Split {
        IRON_RETRIEVED("First Iron"),
        ENTERED_NETHER("Entered Nether"),
        ENTERED_NETHER_FORTRESS("Entered Nether Fortress"),
        FIRST_BLAZE_ROD("First Blaze Rod"),
        BLAZE_RODS_DONE("Blaze Rods Done"),
        FIRST_ENDER_PEARL("First Ender Pearl"),
        ENDER_PEARLS_DONE("Ender Pearls Done"),
        CRAFTED_FIRST_ENDER_EYE("First Ender Eye"),
        FOUND_STRONGHOLD("Stronghold Found"),
        ENTERED_END("Entered End");

        private final String displayName;

        Split(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum RunState {
        NOT_STARTED,
        COUNTDOWN,
        RUNNING,
        PAUSED,
        COMPLETED
    }

    private RunStateManager() {}

    public static RunStateManager getInstance() {
        if (instance == null) {
            instance = new RunStateManager();
        }
        return instance;
    }

    public RunState getState() {
        return state;
    }

    public void setState(RunState state) {
        this.state = state;
    }

    public void startRun(MinecraftServer server) {
        this.state = RunState.RUNNING;
        this.startTime = System.currentTimeMillis();
        this.totalPausedDuration = 0;
        this.dragonKilled = false;
        this.splits.clear();
        this.lastAggregateItemCheckMs = 0;

        // Setup scoreboard
        setupScoreboard(server);

        // Track player names
        playerNames.clear();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            playerNames.add(player.getName().getString());
        }
    }

    public void pauseRun() {
        if (state == RunState.RUNNING) {
            this.state = RunState.PAUSED;
            this.pausedTime = System.currentTimeMillis();
        }
    }

    public void resumeRun() {
        if (state == RunState.PAUSED) {
            this.state = RunState.RUNNING;
            this.totalPausedDuration += System.currentTimeMillis() - pausedTime;
        }
    }

    public void completeRun() {
        this.state = RunState.COMPLETED;
    }

    public void reset() {
        this.state = RunState.NOT_STARTED;
        this.startTime = 0;
        this.pausedTime = 0;
        this.totalPausedDuration = 0;
        this.playerNames.clear();
        this.dragonKilled = false;
        this.splits.clear();
        this.splitsObjective = null;
        this.lastAggregateItemCheckMs = 0;
    }

    /**
     * Stops the current run, clears the timer from the actionbar, and removes the splits scoreboard.
     */
    public void stopRun(MinecraftServer server) {
        // Stop the run
        this.state = RunState.NOT_STARTED;
        this.startTime = 0;
        this.pausedTime = 0;
        this.totalPausedDuration = 0;
        this.dragonKilled = false;
        this.splits.clear();
        this.lastAggregateItemCheckMs = 0;

        // Clear timer from actionbar immediately
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(Text.empty(), true);
        }

        // Remove splits scoreboard
        clearScoreboard(server);
    }

    public long getElapsedTime() {
        if (state == RunState.NOT_STARTED) {
            return 0;
        }

        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - startTime - totalPausedDuration;

        if (state == RunState.PAUSED) {
            elapsed -= (currentTime - pausedTime);
        }

        return elapsed;
    }

    public void updateTimer(MinecraftServer server) {
        if (state == RunState.RUNNING) {
            StringBuilder timeDisplay = new StringBuilder("§6Timer: §e" + getFormattedTime());

            // Add latest split if available
            if (!splits.isEmpty()) {
                Split latestSplit = null;
                for (Split split : Split.values()) {
                    if (splits.containsKey(split)) {
                        latestSplit = split;
                    }
                }
                if (latestSplit != null) {
                    timeDisplay.append(" §8| §7").append(latestSplit.getDisplayName())
                              .append(": §e").append(formatTime(splits.get(latestSplit)));
                }
            }

            // Send action bar message to all players
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                player.sendMessage(Text.literal(timeDisplay.toString()), true);
            }
        }
    }

    /**
     * Checks server-wide item totals (across all online players) and records
     * aggregate-item splits when thresholds are met.
     *
     * This is intentionally throttled to avoid scanning inventories every tick.
     */
    public void updateAggregateItemSplits(MinecraftServer server) {
        if (state != RunState.RUNNING) return;
        if (hasSplit(Split.BLAZE_RODS_DONE) && hasSplit(Split.ENDER_PEARLS_DONE)) return;

        long now = System.currentTimeMillis();
        if (now - lastAggregateItemCheckMs < 250) return; // throttle: ~4x/sec
        lastAggregateItemCheckMs = now;

        int totalBlazeRods = 0;
        int totalEnderPearls = 0;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            totalBlazeRods += countItemEverywhere(player, Items.BLAZE_ROD);
            totalEnderPearls += countItemEverywhere(player, Items.ENDER_PEARL);
        }

        if (totalBlazeRods >= 1 && !hasSplit(Split.FIRST_BLAZE_ROD)) {
            recordSplit(Split.FIRST_BLAZE_ROD, server);
        }
        if (totalBlazeRods >= REQUIRED_BLAZE_RODS && !hasSplit(Split.BLAZE_RODS_DONE)) {
            recordSplit(Split.BLAZE_RODS_DONE, server);
        }
        if (totalEnderPearls >= 1 && !hasSplit(Split.FIRST_ENDER_PEARL)) {
            recordSplit(Split.FIRST_ENDER_PEARL, server);
        }
        if (totalEnderPearls >= REQUIRED_ENDER_PEARLS && !hasSplit(Split.ENDER_PEARLS_DONE)) {
            recordSplit(Split.ENDER_PEARLS_DONE, server);
        }
    }

    private static int countItemEverywhere(ServerPlayerEntity player, Item item) {
        int count = 0;

        var inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            count += countInStack(inv.getStack(i), item);
        }

        var ec = player.getEnderChestInventory();
        for (int i = 0; i < ec.size(); i++) {
            count += countInStack(ec.getStack(i), item);
        }

        return count;
    }

    private static int countInStack(ItemStack stack, Item item) {
        if (stack == null || stack.isEmpty()) return 0;
        return stack.isOf(item) ? stack.getCount() : 0;
    }

    public void applyBlindness(ServerPlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(
            StatusEffects.BLINDNESS,
            999999,
            0,
            false,
            false,
            true
        ));
    }

    public void removeBlindness(ServerPlayerEntity player) {
        player.removeStatusEffect(StatusEffects.BLINDNESS);
    }

    /**
     * Freezes time in all worlds by disabling time advancement,
     * weather changes, fire spread, mob spawning, and other dynamic world changes.
     */
    public void freezeTime(MinecraftServer server) {
        // Store current game rule values from overworld before freezing
        ServerWorld overworld = server.getOverworld();
        previousFireSpreadRadius = overworld.getGameRules().getValue(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER) | 128;

        for (ServerWorld world : server.getWorlds()) {
            world.getGameRules().setValue(GameRules.ADVANCE_TIME, false, server);
            world.getGameRules().setValue(GameRules.ADVANCE_WEATHER, false, server);
            world.getGameRules().setValue(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0, server);
            world.getGameRules().setValue(GameRules.DO_MOB_SPAWNING, false, server);
        }

        // Execute /tick freeze command (suppress feedback by temporarily disabling sendCommandFeedback)
        boolean previousFeedback = overworld.getGameRules().getValue(GameRules.SEND_COMMAND_FEEDBACK);
        try {
            overworld.getGameRules().setValue(GameRules.SEND_COMMAND_FEEDBACK, false, server);
            ServerCommandSource commandSource = server.getCommandSource();
            ParseResults<ServerCommandSource> parseResults = server.getCommandManager().getDispatcher().parse("tick freeze", commandSource);
            server.getCommandManager().getDispatcher().execute(parseResults);
        } catch (Exception e) {
            SpeedrunUtils.LOGGER.warn("Failed to execute /tick freeze command: {}", e.getMessage());
        } finally {
            overworld.getGameRules().setValue(GameRules.SEND_COMMAND_FEEDBACK, previousFeedback, server);
        }

        // Freeze all players in place using the new FreezeManager
        frozenPositions.clear();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            FreezeManager.freeze(player);
        }
    }

    /**
     * Unfreezes time in all worlds by enabling time advancement, weather,
     * and restoring all dynamic world changes.
     */
    public void unfreezeTime(MinecraftServer server) {
        // Execute /tick unfreeze command (suppress feedback by temporarily disabling sendCommandFeedback)
        ServerWorld overworld = server.getOverworld();
        boolean previousFeedback = overworld.getGameRules().getValue(GameRules.SEND_COMMAND_FEEDBACK);
        try {
            overworld.getGameRules().setValue(GameRules.SEND_COMMAND_FEEDBACK, false, server);
            ServerCommandSource commandSource = server.getCommandSource();
            ParseResults<ServerCommandSource> parseResults = server.getCommandManager().getDispatcher().parse("tick unfreeze", commandSource);
            server.getCommandManager().getDispatcher().execute(parseResults);
        } catch (Exception e) {
            SpeedrunUtils.LOGGER.warn("Failed to execute /tick unfreeze command: {}", e.getMessage());
        } finally {
            overworld.getGameRules().setValue(GameRules.SEND_COMMAND_FEEDBACK, previousFeedback, server);
        }

        for (ServerWorld world : server.getWorlds()) {
            world.getGameRules().setValue(GameRules.ADVANCE_TIME, true, server);
            world.getGameRules().setValue(GameRules.ADVANCE_WEATHER, true, server);
            world.getGameRules().setValue(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, previousFireSpreadRadius, server);
            world.getGameRules().setValue(GameRules.DO_MOB_SPAWNING, true, server);
        }

        // Unfreeze all players using the new FreezeManager
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            FreezeManager.unfreeze(player);
        }

        frozenPositions.clear();
    }

    /**
     * Enforces player freeze by freezing any new players who join during pause/not-started.
     * The main freeze enforcement is now handled by FreezeManager.tick() which is called from SpeedrunUtils.
     */
    public void enforcePlayerFreeze(MinecraftServer server) {
        if (state != RunState.NOT_STARTED && state != RunState.PAUSED) return;

        // Freeze any new players who join during pause/not-started
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!FreezeManager.isFrozen(player)) {
                FreezeManager.freeze(player);
            }
        }
    }

    public Set<String> getPlayerNames() {
        return new HashSet<>(playerNames);
    }

    public void addPlayerName(String name) {
        playerNames.add(name);
    }

    public String getFormattedTime() {
        long totalMillis = getElapsedTime();
        long hours = totalMillis / 3600000;
        long minutes = (totalMillis % 3600000) / 60000;
        long seconds = (totalMillis % 60000) / 1000;
        long millis = totalMillis % 1000;

        if (hours > 0) {
            return String.format("%d:%02d:%02d.%03d", hours, minutes, seconds, millis);
        } else {
            return String.format("%d:%02d.%03d", minutes, seconds, millis);
        }
    }

    public boolean isDragonKilled() {
        return dragonKilled;
    }

    public void setDragonKilled(boolean killed) {
        this.dragonKilled = killed;
    }

    // Split management methods
    public void recordSplit(Split split, MinecraftServer server) {
        if (state != RunState.RUNNING) return;
        if (splits.containsKey(split)) return; // Already recorded

        long splitTime = getElapsedTime();
        splits.put(split, splitTime);

        // Update scoreboard
        updateScoreboard(server);

        // Broadcast split to all players
        String message = "§6[Split] §e" + split.getDisplayName() + " §8- §a" + formatTime(splitTime);
        server.getPlayerManager().broadcast(Text.literal(message), false);

        SpeedrunUtils.LOGGER.info("Split recorded: {} at {}", split.getDisplayName(), formatTime(splitTime));
    }

    public boolean hasSplit(Split split) {
        return splits.containsKey(split);
    }

    public Map<Split, Long> getSplits() {
        return new LinkedHashMap<>(splits);
    }

    private String formatTime(long millis) {
        long hours = millis / 3600000;
        long minutes = (millis % 3600000) / 60000;
        long seconds = (millis % 60000) / 1000;
        long ms = millis % 1000;

        if (hours > 0) {
            return String.format("%d:%02d:%02d.%03d", hours, minutes, seconds, ms);
        } else {
            return String.format("%d:%02d.%03d", minutes, seconds, ms);
        }
    }

    // Scoreboard management
    private void setupScoreboard(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();

        // Remove old objective if it exists (check both cached reference and by name)
        ScoreboardObjective existingObjective = splitsObjective;
        if (existingObjective == null) {
            existingObjective = scoreboard.getNullableObjective("speedrun_splits");
        }

        if (existingObjective != null) {
            if (scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR) == existingObjective) {
                scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, null);
            }
            scoreboard.removeObjective(existingObjective);
        }

        // Create new objective
        splitsObjective = scoreboard.addObjective(
            "speedrun_splits",
            ScoreboardCriterion.DUMMY,
            Text.literal("§6§lSpeedrun Splits"),
            ScoreboardCriterion.RenderType.INTEGER,
            true,
            null
        );

        // Hide the red score numbers on the right side
        splitsObjective.setNumberFormat(BlankNumberFormat.INSTANCE);

        // Display on sidebar
        scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, splitsObjective);

        // Start with empty scoreboard - splits will be added as they're achieved
    }

    private void clearScoreboard(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();

        // Prefer the cached objective, but also handle the case where it was nulled (e.g. via /newrun reset)
        ScoreboardObjective objective = this.splitsObjective;
        if (objective == null) {
            objective = scoreboard.getNullableObjective("speedrun_splits");
        }

        if (objective != null) {
            if (scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR) == objective) {
                scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, null);
            }
            scoreboard.removeObjective(objective);
        }

        this.splitsObjective = null;
    }

    private void updateScoreboard(MinecraftServer server) {
        if (splitsObjective == null) return;

        Scoreboard scoreboard = server.getScoreboard();

        // Clear all existing scores
        for (var entry : scoreboard.getScoreboardEntries(splitsObjective)) {
            scoreboard.removeScore(ScoreHolder.fromName(entry.owner()), splitsObjective);
        }

        // Only show completed splits
        int score = splits.size();
        for (Split split : Split.values()) {
            if (splits.containsKey(split)) {
                String displayName = split.getDisplayName();
                String timeStr = formatTime(splits.get(split));

                // Gray split name, green time
                String entryName = "§7" + displayName + ": §a" + timeStr;

                // Add score
                ScoreHolder holder = ScoreHolder.fromName(entryName);
                var scoreAccess = scoreboard.getOrCreateScore(holder, splitsObjective);
                scoreAccess.setScore(score);
                score--;
            }
        }
    }

    public void saveRunToFile(MinecraftServer server) {
        try {
            java.nio.file.Path serverRoot = server.getRunDirectory();
            java.nio.file.Path speedrunsFile = serverRoot.resolve("speedruns.txt");

            boolean isNewFile = !java.nio.file.Files.exists(speedrunsFile);

            try (FileWriter writer = new FileWriter(speedrunsFile.toFile(), true)) {
                if (isNewFile) {
                    writer.write("=== Speedrun Records ===\n\n");
                }

                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String timestamp = dateFormat.format(new Date());

                writer.write("Date: " + timestamp + "\n");
                writer.write("Players: " + String.join(", ", getPlayerNames()) + "\n");
                writer.write("Time: " + getFormattedTime() + "\n");
                writer.write("Status: " + (state == RunState.COMPLETED ? "COMPLETED" : "INCOMPLETE") + "\n");

                // Write splits
                if (!splits.isEmpty()) {
                    writer.write("Splits:\n");

                    // Find the longest split name for alignment
                    int maxNameLength = 0;
                    for (Split split : Split.values()) {
                        maxNameLength = Math.max(maxNameLength, split.getDisplayName().length());
                    }

                    for (Map.Entry<Split, Long> entry : splits.entrySet()) {
                        String name = entry.getKey().getDisplayName();
                        String time = formatTime(entry.getValue());

                        // Pad the name to align times
                        String paddedName = String.format("  - %-" + maxNameLength + "s", name);
                        writer.write(paddedName + ": " + time + "\n");
                    }
                }

                writer.write("----------------------------------------\n\n");

                SpeedrunUtils.LOGGER.info("Saved speedrun to file: {}", speedrunsFile.toAbsolutePath());

                // Broadcast to chat
                String status = state == RunState.COMPLETED ? "§aCOMPLETED" : "§cINCOMPLETE";
                server.getPlayerManager().broadcast(
                    Text.literal("§6[Speedrun] §fSaved run: §e" + getFormattedTime() + " §8(§7" + String.join(", ", getPlayerNames()) + "§8) " + status),
                    false
                );
            }
        } catch (IOException e) {
            SpeedrunUtils.LOGGER.error("Failed to save speedrun to file", e);
        }
    }
}
