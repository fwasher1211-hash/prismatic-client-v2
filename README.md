# Prismatic Client (Fabric 1.21.11)

HUD modules, a Right Shift menu with search, and a synced Spotify lyrics HUD.

## Build (GitHub Actions)
1. Upload everything in this folder to the **root** of a GitHub repo (keep the `.github` folder).
2. Open the **Actions** tab. The "Build Prismatic Client" run starts by itself (or press "Run workflow").
3. When it turns green, open the run and download the **prismatic-client** artifact. The `.jar` inside is the mod.

## Install
Put these in `.minecraft/mods`: Fabric Loader 0.18.x, **Fabric API for 1.21.11**, and `prismatic-client-1.0.0.jar`.

## Use
Press **Right Shift** in a world. Search modules, filter with the chips (All / Enabled / HUD / Visuals / Performance / Utility),
click a card to toggle it, and click **Edit** for a module's settings. Settings save to `.minecraft/config/prismatic_client.json`.

## Commands
- `/prismatic wp add|remove|list|clear <name>`   waypoints (shown by the Waypoints module)
- `/prismatic team add|remove|clear <player>`    teammates (Team view)
- `/prismatic item add|remove|clear <item>`      tracked items (Item counter, up to 10)
- `/prismatic quick <message>`                   message sent by the Quick action hotkey

## Working modules
HUD: FPS, Coordinates, Ping, Clock, Direction, Speed, Memory, Keystrokes, CPS, Combo, Reach, Armor, Potions, Pot counter,
Server info, Biome, World clock, Better F3, Cooldowns, Food preview, Item counter, Player profile, Team view, TNT countdown,
Waypoints, Now playing, Scoreboard, Boss bars, Titles, Compact tab, Smooth hotbar.
Visuals: Crosshair. Performance: Particles.
Utility: Toggle sneak, Quick action, Snap look, Flight speed, Bedrock flight, Shulker preview, Prismatic game menu, Prismatic title screen.

Modules marked SOON need deeper rendering hooks and are not built yet.

## Notes
- Now playing is Windows only: it reads Spotify from Windows media controls (no login) and gets lyrics from LRCLIB.
- The UI font is Poppins Medium (SIL OFL). To use another font, replace `assets/prismatic_client/font/poppins.ttf`.
- If the title screen module ever causes trouble, set `"titlescreen": false` in the config file.
