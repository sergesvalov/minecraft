#!/bin/bash
# Script to apply post-settings to the server after the first run.
# Run it by hand on the host once the server has started at least once and generated
# the plugin configs. The itzg image does not run custom init scripts, so these patches
# are not applied automatically. Each step is safe to repeat.

# --- Settings ---
SERVER_DIR="/opt/minecraft"
TIMEZONE="Europe/Athens"
# -----------------

echo "=== Applying manual server settings ==="

echo "1. Configuring timezone ($TIMEZONE) and time synchronization (NTP)..."
sudo timedatectl set-timezone "$TIMEZONE"
sudo timedatectl set-ntp true

AUTHME_CONFIG="${SERVER_DIR}/data/plugins/AuthMe/config.yml"
AUTHME_COMMANDS="${SERVER_DIR}/data/plugins/AuthMe/commands.yml"
GEYSER_CONFIG="${SERVER_DIR}/data/plugins/Geyser-Spigot/config.yml"

echo "2. Checking AuthMe configuration files..."
if [ -f "$AUTHME_CONFIG" ]; then
    # Allow dot in nicknames for Bedrock players (Floodgate)
    sudo sed -i -E "s/^([[:space:]]*allowedNicknameCharacters:).*/\1 '[a-zA-Z0-9_.]*'/" "$AUTHME_CONFIG"
    # Allow up to 4 registrations from a single IP (family/friends on same network).
    # maxJoinPerIp stays 0: in AuthMe 0 means no limit.
    sudo sed -i -E "s/^([[:space:]]*maxRegPerIp:).*/\1 4/" "$AUTHME_CONFIG"
else
    echo "File $AUTHME_CONFIG not found!"
fi

if [ -f "$AUTHME_COMMANDS" ]; then
    # Make it day when a player registers or logs in
    sudo sed -i -E "s/^onLogin: \{\}$/onLogin:\n  makeday:\n    command: 'time set day'\n    executor: CONSOLE/; s/^onRegister: \{\}$/onRegister:\n  makeday:\n    command: 'time set day'\n    executor: CONSOLE/" "$AUTHME_COMMANDS"
else
    echo "File $AUTHME_COMMANDS not found!"
fi

echo "3. Checking Geyser configuration file..."
if [ -f "$GEYSER_CONFIG" ]; then
    # Lower MTU to avoid UDP fragmentation over Wi-Fi ("ClientDisconnection-41" for Bedrock players)
    sudo sed -i -E "s/^([[:space:]]*mtu:).*/\1 1300/" "$GEYSER_CONFIG"
else
    echo "File $GEYSER_CONFIG not found!"
fi

if [ ! -f "$AUTHME_CONFIG" ] || [ ! -f "$GEYSER_CONFIG" ]; then
    echo "Warning: the server must be run at least once for the plugin configs to be generated."
fi

echo "Restarting server to apply settings..."
sudo docker restart mc-paper-geyser

echo "=== Done! ==="
