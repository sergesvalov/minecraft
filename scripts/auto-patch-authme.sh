#!/bin/bash
# This script is automatically mounted into the container and executed at startup.
# It waits for the AuthMe config file to appear and patches it "on the fly".

echo "[AuthMe-AutoPatch] Starting background script to wait for configuration..."

(
  AUTHME_CONFIG="/data/plugins/AuthMe/config.yml"
  
  # Wait up to 5 minutes (150 * 2 sec) until the server generates the file
  for i in {1..150}; do
    if [ -f "$AUTHME_CONFIG" ]; then
      # Check if the file is already patched (look for floodgate: true)
      if ! grep -q "floodgate: true" "$AUTHME_CONFIG"; then
        echo "[AuthMe-AutoPatch] File found! Applying patches..."
        
        # Reliable string replacement (replace the whole line regardless of whitespace)
        sed -i -E "s/^[[:space:]]*allowedNicknameCharacters:.*/    allowedNicknameCharacters: '[a-zA-Z0-9_.]*'/g" "$AUTHME_CONFIG"
        sed -i -E "s/^[[:space:]]*timeout:.*/        timeout: 120/g" "$AUTHME_CONFIG"
        sed -i -E "s/^[[:space:]]*maxRegPerIp:.*/    maxRegPerIp: 4/g" "$AUTHME_CONFIG"
        sed -i -E "s/^[[:space:]]*maxJoinPerIp:.*/    maxJoinPerIp: 4/g" "$AUTHME_CONFIG"
        
        if grep -q "floodgate:" "$AUTHME_CONFIG"; then
            sed -i -E "s/^[[:space:]]*floodgate:.*/    floodgate: true/g" "$AUTHME_CONFIG"
        elif grep -q "^Hooks:" "$AUTHME_CONFIG"; then
            sed -i -E "/^Hooks:/a \\    floodgate: true" "$AUTHME_CONFIG"
        else
            echo -e "\nHooks:\n    floodgate: true" >> "$AUTHME_CONFIG"
        fi
        
        echo "[AuthMe-AutoPatch] Reloading plugin via RCON..."
        # Wait a bit to ensure the server has fully started plugins and RCON
        sleep 5
        rcon-cli authme reload
        echo "[AuthMe-AutoPatch] Successfully patched and applied!"
      else
        echo "[AuthMe-AutoPatch] File already contains correct settings (floodgate: true)."
      fi
      break
    fi
    sleep 2
  done
) &
