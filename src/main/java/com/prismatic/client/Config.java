package com.prismatic.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Saved to .minecraft/config/prismatic_client.json */
public final class Config {
	public static class Spotify {
		public float scale = 1.0f;          // font size multiplier
		public String font = "smooth";      // smooth | minecraft | uniform
		public String highlight = "prismatic"; // prismatic | pink | cyan | violet | white
		public String position = "top";     // top | middle | bottom
		public boolean shadow = true;
		public boolean bold = true;
		public boolean nextLine = true;
		public int delayMs = 0;             // shift lyrics earlier/later
	}

	public static class Waypoint {
		public String name = "";
		public String dim = "";
		public double x, y, z;
	}

	private static class Data {
		Map<String, Boolean> enabled = new HashMap<>();
		Spotify spotify = new Spotify();
		Map<String, Float> num = new HashMap<>();
		Map<String, String> text = new HashMap<>();
		List<Waypoint> waypoints = new ArrayList<>();
		List<String> team = new ArrayList<>();
		List<String> items = new ArrayList<>();
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Data data = new Data();

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("prismatic_client.json");
	}

	public static void load() {
		try {
			Path f = file();
			if (Files.exists(f)) {
				Data d = GSON.fromJson(Files.readString(f), Data.class);
				if (d != null) {
					if (d.enabled == null) d.enabled = new HashMap<>();
					if (d.spotify == null) d.spotify = new Spotify();
					if (d.num == null) d.num = new HashMap<>();
					if (d.text == null) d.text = new HashMap<>();
					if (d.waypoints == null) d.waypoints = new ArrayList<>();
					if (d.team == null) d.team = new ArrayList<>();
					if (d.items == null) d.items = new ArrayList<>();
					data = d;
				}
			} else {
				data.enabled.put("fps", true);
				save();
			}
		} catch (Exception e) {
			data = new Data();
		}
	}

	public static void save() {
		try {
			Files.writeString(file(), GSON.toJson(data));
		} catch (Exception ignored) {
		}
	}

	public static boolean isEnabled(String id) {
		return data.enabled.getOrDefault(id, false);
	}

	public static void setEnabled(String id, boolean on) {
		data.enabled.put(id, on);
		save();
	}

	public static Spotify spotify() {
		return data.spotify;
	}

	// ---- generic settings (sliders, toggles, cycles, keys) -------------
	public static float num(String key, float def) {
		Float v = data.num.get(key);
		return v == null ? def : v;
	}

	/** Does not save: call save() when finished (e.g. when a slider is released). */
	public static void setNum(String key, float value) {
		data.num.put(key, value);
	}

	public static boolean flag(String key, boolean def) {
		return num(key, def ? 1f : 0f) >= 0.5f;
	}

	public static String text(String key, String def) {
		String v = data.text.get(key);
		return v == null ? def : v;
	}

	public static void setText(String key, String value) {
		data.text.put(key, value);
		save();
	}

	public static List<Waypoint> waypoints() {
		return data.waypoints;
	}

	public static List<String> team() {
		return data.team;
	}

	public static List<String> items() {
		return data.items;
	}

	private Config() {
	}
}
