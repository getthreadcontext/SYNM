# SynM - Server Management Mod

SynM is a Minecraft Fabric mod that provides a web-based player management portal. It works with both dedicated servers and local/LAN worlds, allowing server administrators to monitor and manage players through a convenient web interface accessible at port 4444.

## Features

### Web Portal (Port 4444)
- **Player List**: View all online and offline players who have joined the server
- **Player Details**: Click on any player to view detailed information including:
  - Health status and percentage
  - Food level and saturation
  - Game mode
  - Experience level
  - Current position and dimension
  - **Complete inventory view** (hotbar, main inventory, armor, offhand)
  - First joined and last seen timestamps

### Player Management Actions (Online Players Only)
- **Heal**: Restore player to full health with regeneration effect
- **Feed**: Fill player's hunger and saturation to maximum
- **Game Mode Changes**: Switch between Creative and Survival modes
- **Teleportation**: Teleport players to specific coordinates
- **Kick**: Remove players from the server with custom reason

### Real-time Updates
- Auto-refresh player data every 5 seconds
- Health bars with visual indicators
- Online/offline status indicators
- Responsive design for desktop and mobile

## Support

If you encounter any issues:
1. Check the Minecraft game logs for error messages
2. Try accessing the web portal on the fallback port 4445
3. Make sure you're using the correct URL for your setup type
