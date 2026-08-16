#!/bin/bash
# This script is automatically mounted into the container and executed at startup.
# It patches the Geyser configuration to fix "ClientDisconnection-41 Bat" MTU issues.

echo "[Geyser-AutoPatch] Starting background script to wait for configuration..."

(
  GEYSER_CONFIG="/data/plugins/Geyser-Spigot/config.yml"
  
  # Wait up to 5 minutes until the server generates the file
  for i in {1..150}; do
    if [ -f "$GEYSER_CONFIG" ]; then
      # Check if the file is already patched to mtu 1300
      if ! grep -q "mtu: 1300" "$GEYSER_CONFIG"; then
        echo "[Geyser-AutoPatch] File found! Applying MTU patch to fix Bedrock Disconnects..."
        
        # Patch the mtu setting to a lower value to avoid UDP fragmentation over Wi-Fi
        sed -i -E "s/^[[:space:]]*mtu:.*/mtu: 1300/g" "$GEYSER_CONFIG"
        
        echo "[Geyser-AutoPatch] Reloading Geyser via RCON..."
        # Wait a bit to ensure RCON is fully up
        sleep 5
        rcon-cli geyser reload
        echo "[Geyser-AutoPatch] Successfully patched and applied!"
      else
        echo "[Geyser-AutoPatch] File already contains correct MTU settings (mtu: 1300)."
      fi
      break
    fi
    sleep 2
  done
) &
