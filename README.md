# 🎮 Minecraft Server Deployment

🇷🇺 [Русская версия](README_ru.md) | 🇬🇧 [English Version](README.md) | 🇬🇷 [Ελληνική έκδοση](README_el.md)

Cross-platform (Java + Bedrock) Minecraft server with automated deployment via Jenkins (CI/CD).

## 🌟 Key Features

### 1. Cross-platform Support
The server uses the **Paper** core and **Geyser** + **Floodgate** plugins. 
Both worlds are merged — you can play from any device:
- **Java Edition (PC):** `<SERVER_IP>:25566`
- **Bedrock Edition (Phones/Consoles):** `<SERVER_IP>:19132`

### 2. Internal Authentication System (AuthMe)
The server runs in offline mode (`ONLINE_MODE=false`), making it convenient for local network play.
To protect players' inventory and builds, the **AuthMeReloaded** plugin is used:
- Java players create their password upon first login: `/register password password`, and then authenticate using `/login password`.
- Bedrock players also **must register**, using chat commands (just like Java players). Temporarily, auto-login for them is disabled due to version incompatibility.

### 3. Automatic Administrator Rights (LuckPerms Daemon)
A player with the username **`papa`** is the superadmin and automatically receives and retains vanilla operator rights upon login. 
For all other admins, you can simply issue the `/op <nickname>` command in-game. A background daemon (`scripts/auto-perms.sh`) automatically scans for newly opped players every night at 03:00 AM. It grants them the full `admin` group in **LuckPerms** and immediately revokes their vanilla OP for security.

### 4. Interactive Web Map (Squaremap)
The server runs the Squaremap plugin, which generates a fast and smooth 2D map of your world in real-time.
🌍 **`http://<SERVER_IP>:25581`**

### 5. Anti-grief Protection (CoreProtect)
> [!WARNING]
> The plugin is temporarily disabled as the developers have not yet released a version compatible with Minecraft 26.2. We will bring it back to the build as soon as the update is released.

All player actions (placed/broken blocks, chest interactions) are logged. The administrator (`papa`) can see who broke a block and rollback any changes.

### 6. Web Interface for File Management (FileBrowser)
Along with the server, a convenient web panel is deployed for managing files, mods, and configurations without SSH:
🌐 **`http://<SERVER_IP>:25580`**
- Default login: `admin`
- Default password: `admin`

### 7. Essential Game Plugins
- **Geyser & Floodgate:** Enable cross-play between Java and Bedrock (mobile/console) editions.

- **LuckPerms:** Advanced permissions management system for configuring roles and commands.
- **EssentialsX:** Provides hundreds of vital server commands (e.g., `/home`, `/spawn`, `/tpa`).
- **GSit:** A fun addition that allows players to sit on stairs/slabs, lay down, or crawl anywhere.
- **AxGraves:** Protects items on death by placing a grave, preventing item despawn or theft.
- **FastAsyncWorldEdit (FAWE):** High-performance map editing tool, perfect for creative building.

### 8. Analytics & Prometheus Monitoring (Warden)
A custom-built `WardenLog` plugin and a standalone Python service monitor server health and log specific gameplay events in real-time.
- **Prometheus Exporter:** Available at `http://<SERVER_IP>:8000/metrics`. Tracks server liveness, player count, and ping.
- **TNT Tracking:** Automatically logs who placed or exploded TNT blocks and where.

> [!NOTE]  
> 🛠️ **Looking for technical details?**  
> Architecture, Jenkins CI/CD pipeline, automated backups, and script internals are detailed in the [Technical Documentation (TECHNICAL.md)](TECHNICAL.md).

---

## 🎮 Beginner's Guide (In Simple Terms)

If you have never played on servers before and don't know where to enter all those confusing slash (`/`) commands — don't be afraid, it's very simple! You don't need any hacker screens. All commands are entered **directly in the Minecraft game itself, in the game chat**.

### 1. How to join and register?
The server uses an independent security system, so your account will be tied to a personal password.

**For PC players (Java Edition):**
1. Go to Multiplayer and add the server at: `<SERVER_IP>:25566`.
2. Connect to the server. At first, you will not be able to move.
3. Open the chat by pressing the English letter **`T`** (or Russian **`Е`**) on your keyboard. A dark input line will appear at the bottom.
4. Write the registration command, creating a password (you need to enter it twice separated by a space), and press Enter: 
   `/register your_password your_password`
5. **Done!** Next time you join the server, press `T` and write:
   `/login your_password`

**For Phone and Console players (Bedrock Edition):**
1. Add the server at: `<SERVER_IP>`, port `19132`.
2. Connect to the server.
3. Open the chat (the message button at the top of the screen on your phone or a special button on your gamepad).
4. Complete the registration, just like Java players. Write:
   `/register your_password your_password`
5. Next time you join, write:
   `/login your_password`

### 2. How to find out who broke your house or robbed a chest?
> [!WARNING]
> This feature is temporarily unavailable on version 26.2 due to the lack of a plugin update from the authors.

The server has a hidden "dashcam" (CoreProtect) that remembers every step of all players.
If a disaster happens:
1. Press `T` and write the command `/co i` (English letters). You will enter inspector mode.
2. Now **left-click** (try to hit/break) any block or an empty space where the stolen block used to be. A text will appear in the chat with the name of the thief and the time of the theft.
3. **Right-click** (try to use) on a chest to see who took items from it and when.
4. To turn off inspector mode and build normally again, press `T` again and write `/co i`.

### 3. Where to see the web map?
You don't even need to open Minecraft for this! 
Just open your regular browser (Chrome, Safari, Yandex) on your computer or phone and go to this link:
🌍 **`http://<SERVER_IP>:25581`**
There you will see your entire world from a bird's-eye view, and you can see the movements of other players in real-time.

### 4. How to protect yourself from unwanted teleportation?
Any player can protect themselves from unwanted teleports by typing `/tptoggle` in the chat.
- After typing this, no one (including administrators) will be able to teleport to you.
- To disable the protection and allow teleports again, type `/tptoggle` once more.

### 5. Fast Building (WorldEdit / FAWE)
The server has the **FastAsyncWorldEdit (FAWE)** plugin to help you build massive structures instantly. Note: These commands are only available to admins (`/op`).

**How to select an area:**
1. Type `//wand` in chat to get a wooden axe.
2. **Left-click** a block with the axe to set **Point 1**.
3. **Right-click** a different block to set **Point 2**. This creates an invisible box between the two points.

**Basic Commands:**
- `//set stone` — Fills the selected box with stone (you can use any block name like `glass`, `air`, `dirt`).
- `//replace dirt grass_block` — Replaces only dirt with grass blocks in the selection.
- `//undo` — Undoes your last action.
- `//redo` — Redoes an undone action.
- `//copy` — Copies the selected structure (relative to where you are standing).
- `//paste` — Pastes the copied structure.

### 6. Fun Animations (GSit)
You can sit, lay down, or crawl anywhere in the world! 
- Type `/sit` to sit right where you are, or simply **right-click** on stairs/slabs with an empty hand.
- Type `/lay` to lay down.
- Type `/crawl` to start crawling.
- Type `/spin` to spin.

### 7. Death and Graves (AxGraves)
Don't worry about losing your items if you die! 
When you die, a grave is automatically created with all your items and experience inside. It protects your items from disappearing or being stolen by others.
- To collect your items, just return to your death location and **right-click** your grave or break it.

### 8. Voice Chat & Music (OpenAudioMc)
You can talk to other players nearby using your microphone! No mods are required.
- Type `/oa accept` in the game chat.
- You will receive a link to open a web page in your browser.
- Open it on your phone or PC, allow microphone access, and you're good to go!