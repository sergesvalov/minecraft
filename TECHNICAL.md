# 🛠️ Technical Documentation (Minecraft Server Deployment)

This documentation describes the architecture, CI/CD process, backups, and server scripts. For gameplay information, see the main [README.md](README.md).

## 🛠️ Architecture and CI/CD (Jenkins)

Deployment is automated via **Jenkins** over SSH. Jenkins copies the necessary scripts and configurations to the target server and starts/updates the containers via `docker-compose`.

## 💾 Data Persistence

All game data is stored on the host server in the deployment directory:
`/opt/minecraft/data`

This ensures that data (worlds, plugins, settings) is fully preserved:
- During a hard server reboot.
- During repeated deployments of a new configuration via Jenkins.

### Automatic Backups
The server is configured to automatically create backups every day (at 2:00 AM). Backups are stored in the `backups` directory as `.tar` archives, and only the 3 most recent backups are kept to save space.

**How to restore the server from a backup:**
1. Stop the server: `docker-compose stop mc`
2. Rename the current `data` folder to `data_old` and create a new empty `data` folder.
3. Open the `backups` directory and extract the desired archive directly into the new `data` folder (using 7-Zip or built-in OS tools).
4. Start the server again: `docker-compose start mc`

## 🚀 How to Run

Everything works automatically through the `minecraft` pipeline on your Jenkins. Any changes in `docker-compose.yml` (for example, adding new plugins to `MODRINTH_PROJECTS`) will automatically apply to the server after a commit.

### Automatic Pre-configuration
The repository has a built-in script `scripts/auto-patch-authme.sh`. It runs automatically inside the server container and patches the AuthMe config "on the fly" as soon as it is generated:
1. Configures the plugin to allow phone players to enter without a password (Floodgate integration).
2. Allows the dot character `.` in nicknames (required for Bedrock players).
3. Increases the authentication timeout to 120 seconds.
4. Allows up to 4 accounts and connections from a single IP address (useful when the whole family plays from home on the same Wi-Fi).
5. Automatically sets the game time to day (`/time set day`) every time a player registers or logs into the server.

**How to use:**
You don't need to do anything! Everything works 100% automatically. The script runs in the background at container startup, waits for the config file to appear, modifies it, and instantly reloads the plugin. You no longer need to log in via SSH!

## 📊 Prometheus Metrics (Monitoring)

The `warden-monitor` container collects and exposes metrics in Prometheus format on port `8000` (`http://<SERVER_IP>:8000/metrics`).

### Available Metrics:

| Metric | Type | Description | Labels |
| --- | --- | --- | --- |
| `minecraft_server_online` | Gauge | Server status (1 = online, 0 = offline) | - |
| `minecraft_player_count` | Gauge | Number of players currently online | - |
| `minecraft_server_ping_ms` | Gauge | Server ping latency in milliseconds | - |
| `warden_tnt_placed_total` | Counter | Total number of TNT blocks placed | `player`, `world`, `x`, `y`, `z`, `time` |
| `warden_tnt_exploded_total` | Counter | Total number of TNT explosions | `source`, `world`, `x`, `y`, `z`, `time` |
| `warden_player_joins_total` | Counter | Total number of times players joined | `player`, `time` |
| `warden_player_quits_total` | Counter | Total number of times players quit | `player`, `time` |
