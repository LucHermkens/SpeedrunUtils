# SpeedrunUtils Mod

A Minecraft Fabric mod for managing speedruns with built-in timer, blindness mechanics, and run tracking.

**Note:** This mod is still in development and its documentation is AI-generated.

<!--
clear; dockerr stop minecraft; rm -rf /services/volumes/minecraft/world; sudo truncate -s 0 "/var/lib/docker/containers/$(docker inspect -f '{{.Id}}' minecraft)/local-logs/container.log"; dockerr up -d minecraft; docker logs -f minecraft
 -->

## Features

### Commands

#### `/startrun`

- **Permission Level:** 0 (all players)
- **Description:** Starts a speedrun with a 3-second countdown
- **Behavior:**
  - Can only be run once per speedrun (before the run starts)
  - Displays a 3-second countdown to all players
  - Removes blindness effect from all players
  - Starts the scoreboard timer
  - Plays sound effects for countdown and start

#### `/pauserun`

- **Permission Level:** 0 (all players)
- **Description:** Pauses or resumes the current speedrun
- **Behavior:**
  - When pausing:
    - Stops the timer
    - Applies blindness to all players
  - When resuming:
    - Continues the timer
    - Removes blindness from all players

#### `/stoprun`

- **Permission Level:** 0 (all players)
- **Description:** Stops the current speedrun and clears the timer
- **Behavior:**
  - Stops the run (if one is active)
  - Clears the timer
  - Removes blindness from all players (in case the run was paused)
  - Broadcasts: “Run stopped. Timer cleared.”

#### `/newrun`

- **Permission Level:** 0 (all players)
- **Description:** Saves the current run and prepares for a new speedrun
- **Behavior:**
  - Saves the current run data to `speedruns.txt` in the server root
  - Records: timestamp, player names, time, and completion status
  - Resets the run state
  - Applies blindness to all players
  - Notifies players about server restart for world regeneration

## Gameplay Flow

1. **Server Start:** All players join with blindness effect applied
2. **Run Start:** Any player runs `/startrun` to begin the countdown
3. **Speedrun:** Players complete the game while the timer runs
4. **Completion:** Timer automatically stops when a player exits through the End portal after defeating the Ender Dragon
5. **New Run:** Operator runs `/newrun` to save results and prepare for the next run

## Automatic Features

- **Blindness on Join:** Players automatically receive blindness when joining before a run starts
- **Timer Display:** Action bar shows elapsed time with milliseconds (MM:SS.mmm or H:MM:SS.mmm format) at the bottom of the screen, updating 10 times per second
- **Auto-Complete Detection:** Run automatically completes when:
  - The Ender Dragon is defeated
  - A player teleports through the End portal back to the Overworld
- **Run Tracking:** All runs are logged to `speedruns.txt` with:
  - Date and time
  - Player names
  - Final time (formatted as HH:MM:SS or MM:SS)
  - Completion status

## Installation

1. Install Fabric Loader for Minecraft 1.21.11
2. Install Fabric API
3. Place the mod JAR in your `mods` folder
4. Start the server

## Building

```bash
./gradlew build
```

The built JAR will be in `build/libs/`

## Requirements

- Minecraft 1.21.11
- Fabric Loader >= 0.18.3
- Fabric API
- Java 21+

## License

CC0-1.0
