package com.prismatic.client;

import java.util.ArrayList;
import java.util.List;

/** Every module in the Prismatic menu. "ready" ones really work; the rest show SOON. */
public final class Modules {
	public enum Type { SLIDER, TOGGLE, CYCLE, KEY }

	public static final class Setting {
		public final String key, label, unit;
		public final Type type;
		public final float min, max, step, def;
		public final String[] names;

		Setting(String key, String label, Type type, float min, float max, float step, float def, String unit, String[] names) {
			this.key = key;
			this.label = label;
			this.type = type;
			this.min = min;
			this.max = max;
			this.step = step;
			this.def = def;
			this.unit = unit;
			this.names = names;
		}
	}

	public static final class Mod {
		public final String id, name, desc, cat;
		public final boolean ready;
		public final List<Setting> settings = new ArrayList<>();
		public String hint = "";

		Mod(String id, String name, String desc, String cat, boolean ready) {
			this.id = id;
			this.name = name;
			this.desc = desc;
			this.cat = cat;
			this.ready = ready;
		}

		public Mod slider(String key, String label, float min, float max, float step, float def, String unit) {
			settings.add(new Setting(key, label, Type.SLIDER, min, max, step, def, unit, null));
			return this;
		}

		public Mod toggle(String key, String label, boolean def) {
			settings.add(new Setting(key, label, Type.TOGGLE, 0, 1, 1, def ? 1 : 0, "", null));
			return this;
		}

		public Mod cycle(String key, String label, int def, String... names) {
			settings.add(new Setting(key, label, Type.CYCLE, 0, names.length - 1, 1, def, "", names));
			return this;
		}

		public Mod key(String key, String label, int defKey) {
			settings.add(new Setting(key, label, Type.KEY, 0, 400, 1, defKey, "", null));
			return this;
		}

		public Mod hint(String h) {
			hint = h;
			return this;
		}

		public boolean editable() {
			return ready && (!settings.isEmpty() || !hint.isEmpty() || id.equals("nowplaying"));
		}
	}

	public static final List<Mod> ALL = new ArrayList<>();

	private static Mod add(String id, String name, String desc, String cat, boolean ready) {
		Mod m = new Mod(id, name, desc, cat, ready);
		ALL.add(m);
		return m;
	}

	private static void layoutSettings(Mod m, String id, boolean hide) {
		if (hide) m.toggle(id + ".hide", "Hide completely", false);
		m.slider(id + ".scale", "Scale", 0.5f, 1.5f, 0.05f, 1f, "x");
		m.slider(id + ".x", "Move left / right", -300, 300, 5, 0, "");
		m.slider(id + ".y", "Move up / down", -300, 300, 5, 0, "");
	}

	static {
		String H = "HUD", V = "Visuals", P = "Performance", U = "Utility";

		// ---- HUD ----
		add("fps", "FPS counter", "Keep your frame rate in view", H, true);
		add("coords", "Coordinates", "Your position in the world", H, true);
		add("ping", "Ping", "Your connection latency", H, true);
		add("clock", "Clock", "Local time, visible while playing", H, true);
		add("direction", "Direction", "Which way you are facing", H, true);
		add("speed", "Speed", "Your horizontal movement speed", H, true);
		add("memory", "Memory", "Watch Java heap usage", H, true);
		add("keystrokes", "Keystrokes", "Show movement keys and clicks", H, true);
		add("cps", "Clicks per second", "Track left and right clicks", H, true);
		add("combo", "Combo counter", "Consecutive melee hits", H, true);
		add("reach", "Reach display", "Distance to your last hit", H, true);
		add("armor", "Armor status", "Equipment icons and durability", H, true);
		add("potions", "Potion status", "Active effects and their timers", H, true);
		add("potcounter", "Pot counter", "Count usable splash potions", H, true);
		add("serverinfo", "Server info", "Server address you are on", H, true);
		add("biome", "Biome", "The biome at your position", H, true);
		add("worldclock", "World clock", "World day count and time", H, true);
		add("betterf3", "Better F3", "Compact debug info panel", H, true);
		add("cooldowns", "Cooldowns", "Swing and item use timers", H, true);
		add("foodpreview", "Food preview", "Hunger and saturation preview", H, true);
		add("itemcounter", "Item counter", "Track up to ten chosen items", H, true)
			.hint("Commands:\n/prismatic item add <item>   (e.g. golden_apple)\n/prismatic item remove <item>\n/prismatic item clear");
		add("playerprofile", "Player profile", "Info on the player you look at", H, true);
		add("teamview", "Team view", "Mark your teammates", H, true)
			.hint("Commands:\n/prismatic team add <player>\n/prismatic team remove <player>\n/prismatic team clear");
		add("tntcountdown", "TNT countdown", "Time left on lit TNT", H, true);
		add("waypoints", "Waypoints", "Named world markers", H, true)
			.hint("Commands:\n/prismatic wp add <name>   (saves your position)\n/prismatic wp remove <name>\n/prismatic wp list\n/prismatic wp clear");
		add("nowplaying", "Now playing", "Synced Spotify lyrics HUD", H, true);
		layoutSettings(add("scoreboard", "Scoreboard", "Move, scale or hide it", H, true), "scoreboard", true);
		layoutSettings(add("bossbars", "Boss bars", "Move, scale or hide them", H, true), "bossbars", true);
		layoutSettings(add("titles", "Titles", "Move, scale or hide titles", H, true), "titles", true);
		add("compacttab", "Compact tab", "Shrink the player list", H, true)
			.slider("compacttab.scale", "Scale", 0.5f, 1f, 0.05f, 0.75f, "x");
		add("smoothhotbar", "Smooth hotbar", "Slide the hotbar selection", H, true)
			.slider("smoothhotbar.speed", "Slide speed", 2, 20, 1, 10, "");

		// ---- Visuals ----
		add("crosshair", "Crosshair", "Customize your crosshair", V, true)
			.cycle("crosshair.style", "Style", 0, "Cross", "Dot", "Circle", "Plus")
			.slider("crosshair.size", "Size", 2, 16, 1, 5, "px")
			.slider("crosshair.thick", "Thickness", 1, 4, 1, 1, "px")
			.slider("crosshair.gap", "Gap", 0, 8, 1, 2, "px")
			.cycle("crosshair.color", "Color", 0, "Prismatic", "White", "Cyan", "Pink", "Violet", "Green");
		add("saturation", "Color saturation", "Adjust world color intensity", V, false);
		add("hittint", "Hit tint", "Choose the color of the hit flash", V, false);
		add("blockoutline", "Block outline", "Change the color and width", V, false);
		add("itemphysics", "Item physics", "Dropped items tumble", V, false);
		add("chunkborders", "Chunk borders", "Toggle chunk borders", V, false);
		add("fog", "Fog customizer", "Adjust distance fog", V, false);
		add("viewmodel", "Viewmodel", "Set each hand's item position", V, false);
		add("handsway", "Hand sway", "Reduce or remove hand sway", V, false);
		add("menublur", "Menu blur", "Set the strength of menu blur", V, false);
		add("timechanger", "Time changer", "Choose the time of day", V, false);
		add("shinypots", "Shiny pots", "Add an enchantment glint", V, false);
		add("clearwater", "Clear water", "See through water", V, false);
		add("hitboxes", "Hitboxes", "Show entity hitboxes", V, false);
		add("blockanimation", "Block animation", "Blocks you place grow in", V, false);
		add("legacyvisuals", "Legacy visuals", "1.7 animations", V, false);
		add("swingspeed", "Swing speed", "Slow down or speed up swings", V, false);
		add("smoothswap", "Smooth swap", "Items you move between slots", V, false);
		add("yourname", "Your name", "See your own nametag", V, false);

		// ---- Performance ----
		add("particles", "Particles", "Reduce particle effects", P, true)
			.cycle("particles.level", "Particle level", 2, "All", "Decreased", "Minimal");
		add("disableplants", "Disable plants", "Stop drawing grass and flowers", P, false);
		add("motionblur", "Motion blur", "Blend recent world frames", P, false);
		add("stretched", "Stretched resolution", "Use a competitive aspect ratio", P, false);

		// ---- Utility ----
		add("togglesneak", "Toggle sneak", "Press sneak once to stay sneaking", U, true);
		add("quickaction", "Quick action", "Send your saved chat with a key", U, true)
			.key("quickaction.key", "Hotkey", 71)
			.hint("Set the message in chat:\n/prismatic quick <message>   (start with / for a command)");
		add("snaplook", "Snap look", "Snap your view to a preset", U, true)
			.key("snaplook.key", "Hotkey", 86)
			.cycle("snaplook.step", "Snap yaw to", 1, "15 deg", "45 deg", "90 deg")
			.cycle("snaplook.pitch", "Pitch", 0, "Level", "45 down", "75 down", "Straight down");
		add("flightspeed", "Flight speed", "Fly faster in creative", U, true)
			.slider("flightspeed.mult", "Speed", 1, 10, 0.5f, 3, "x");
		add("bedrockflight", "Bedrock flight", "Instant stop while flying in creative", U, true);
		add("shulkerpreview", "Shulker preview", "Hover a shulker box to peek", U, true);
		add("gamemenu", "Prismatic game menu", "A polished pause menu", U, true);
		add("titlescreen", "Prismatic title screen", "Replace Minecraft's menu", U, true);
		add("freelook", "Free look", "Hold a key to look around", U, false);
		add("soundfilters", "Sound filters", "Adjust portal and thunder sounds", U, false);
		add("mctiers", "MCTiers", "Show each player's tier", U, false);
		add("legitchams", "Legit chams", "Draw players in a match", U, false);
	}

	private Modules() {
	}
}
