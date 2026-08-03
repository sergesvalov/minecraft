#!/bin/bash
# Script to apply post-settings to the server after the first run

# --- Settings ---
SERVER_DIR="/opt/minecraft"
TIMEZONE="Europe/Athens"
AUTHME_TIMEOUT="120"
# -----------------

echo "=== Applying manual server settings ==="

echo "1. Configuring timezone ($TIMEZONE) and time synchronization (NTP)..."
sudo timedatectl set-timezone "$TIMEZONE"
sudo timedatectl set-ntp true

echo "2. Checking AuthMe configuration file..."
AUTHME_CONFIG="${SERVER_DIR}/data/plugins/AuthMe/config.yml"

if [ -f "$AUTHME_CONFIG" ]; then
    echo "Applying fixes to AuthMe config..."
    # Allow dot in nicknames for Bedrock players (Floodgate)
    sudo sed -i "s/allowedNicknameCharacters: '\[a-zA-Z0-9_\]\*'/allowedNicknameCharacters: '\[a-zA-Z0-9_.\]\*'/g" "$AUTHME_CONFIG"
    
    # Increase password entry timeout
    sudo sed -i "s/timeout: 30/timeout: $AUTHME_TIMEOUT/g" "$AUTHME_CONFIG"
    
    # Allow up to 4 accounts/connections from a single IP (for family/friends on same network)
    sudo sed -i -E "s/^[[:space:]]*maxRegPerIp:.*/    maxRegPerIp: 4/g" "$AUTHME_CONFIG"
    sudo sed -i -E "s/^[[:space:]]*maxJoinPerIp:.*/    maxJoinPerIp: 4/g" "$AUTHME_CONFIG"
    
    # Enable automatic login (passwordless) for Bedrock players via Floodgate
    if grep -q "floodgate:" "$AUTHME_CONFIG"; then
        sudo sed -i -E "s/^[[:space:]]*floodgate:.*/    floodgate: true/g" "$AUTHME_CONFIG"
    elif grep -q "^Hooks:" "$AUTHME_CONFIG"; then
        # If option is missing but Hooks section exists, add it there
        sudo sed -i -E "/^Hooks:/a \\    floodgate: true" "$AUTHME_CONFIG"
    else
        # Otherwise, add the entire section
        echo -e "\nHooks:\n    floodgate: true" | sudo tee -a "$AUTHME_CONFIG" > /dev/null
    fi
    
    echo "Restarting server to apply settings..."
    sudo docker restart mc-paper-geyser
    echo "AuthMe settings successfully applied!"
else
    echo "File $AUTHME_CONFIG not found!"
    echo "Warning: The server (or AuthMe plugin) must be run at least once for the configuration to be generated."
fi

echo "=== Done! ==="
