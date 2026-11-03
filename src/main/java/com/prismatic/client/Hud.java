package com.prismatic.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/** Draws all working HUD modules (no mixins needed). */
public final class Hud {
	private static final Deque<Long> LEFT = new ArrayDeque<>(), RIGHT = new ArrayDeque<>();
	private static boolean lPrev, rPrev;
	private static int combo;
	private static long lastHit;
	private static double reach, speed, prevX, prevZ;
	private static boolean hasPrev;
	private static final String[] COMPASS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

	// ---- logic -------------------------------------------------------
	public static void tick(MinecraftClient mc) {
		PlayerEntity p = mc.player;
		if (p == null) {
			hasPrev = false;
			return;
		}
		if (hasPrev) {
			speed = Math.hypot(p.getX() - prevX, p.getZ() - prevZ) * 20.0;
		}
		prevX = p.getX();
		prevZ = p.getZ();
		hasPrev = true;
		if (p.hurtTime > 0 || System.currentTimeMillis() - lastHit > 3000) {
			combo = 0;
		}
	}

	public static void onAttack(PlayerEntity p, Entity target) {
		combo++;
		lastHit = System.currentTimeMillis();
		reach = p.distanceTo(target);
	}

	private static void pollMouse(MinecraftClient mc) {
		long w = mc.getWindow().getHandle();
		boolean l = GLFW.glfwGetMouseButton(w, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		boolean r = GLFW.glfwGetMouseButton(w, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
		long now = System.currentTimeMillis();
		if (mc.currentScreen == null) {
			if (l && !lPrev) LEFT.addLast(now);
			if (r && !rPrev) RIGHT.addLast(now);
		}
		lPrev = l;
		rPrev = r;
		while (!LEFT.isEmpty() && now - LEFT.peekFirst() > 1000) LEFT.pollFirst();
		while (!RIGHT.isEmpty() && now - RIGHT.peekFirst() > 1000) RIGHT.pollFirst();
	}

	static String pretty(String s) {
		StringBuilder b = new StringBuilder();
		for (String part : s.split("_")) {
			if (part.isEmpty()) continue;
			b.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
		}
		return b.toString().trim();
	}

	private static String compass(double yaw) {
		double c = ((yaw + 180.0) % 360.0 + 360.0) % 360.0;
		return COMPASS[(int) ((c + 22.5) / 45.0) % 8];
	}

	/** Adds the text line(s) a module wants to show. */
	private static void lines(String id, MinecraftClient mc, List<String> out) {
		PlayerEntity p = mc.player;
		switch (id) {
			case "fps":
				out.add(mc.getCurrentFps() + " FPS");
				break;
			case "coords":
				out.add(String.format(Locale.ROOT, "XYZ %.0f / %.0f / %.0f", p.getX(), p.getY(), p.getZ()));
				break;
			case "ping": {
				var net = mc.getNetworkHandler();
				var entry = net == null ? null : net.getPlayerListEntry(p.getUuid());
				out.add(entry == null ? "Ping --" : "Ping " + entry.getLatency() + " ms");
				break;
			}
			case "clock":
				out.add(LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a")));
				break;
			case "direction": {
				float yaw = MathHelper.wrapDegrees(p.getYaw());
				float compass = (yaw + 180f + 360f) % 360f;
				out.add(COMPASS[(int) ((compass + 22.5f) / 45f) % 8] + " " + Math.round(compass) + "\u00B0");
				break;
			}
			case "speed":
				out.add(String.format(Locale.ROOT, "%.1f b/s", speed));
				break;
			case "memory": {
				Runtime rt = Runtime.getRuntime();
				long used = (rt.totalMemory() - rt.freeMemory()) >> 20;
				out.add("Mem " + used + " / " + (rt.maxMemory() >> 20) + " MB");
				break;
			}
			case "cps":
				out.add("CPS " + LEFT.size() + " | " + RIGHT.size());
				break;
			case "combo":
				out.add("Combo " + combo);
				break;
			case "potcounter": {
				int n = 0;
				PlayerInventory inv = p.getInventory();
				for (int i = 0; i < inv.size(); i++) {
					ItemStack s = inv.getStack(i);
					if (s.isOf(Items.SPLASH_POTION)) n += s.getCount();
				}
				out.add("Pots " + n);
				break;
			}
			case "serverinfo": {
				var info = mc.getCurrentServerEntry();
				out.add(info == null ? "Singleplayer" : info.address);
				break;
			}
			case "biome": {
				if (mc.world == null) break;
				String name = mc.world.getBiome(p.getBlockPos()).getKey().map(k -> k.getValue().getPath()).orElse("unknown");
				out.add(pretty(name));
				break;
			}
			case "worldclock": {
				if (mc.world == null) break;
				long t = mc.world.getTime();
				long hour = (t / 1000 + 6) % 24;
				long min = (t % 1000) * 60 / 1000;
				out.add(String.format(Locale.ROOT, "Day %d  %02d:%02d", t / 24000 + 1, hour, min));
				break;
			}
			case "reach":
				out.add(String.format(Locale.ROOT, "Reach %.2f", reach));
				break;
			case "betterf3": {
				if (mc.world == null) break;
				BlockPos bp = p.getBlockPos();
				out.add(String.format(Locale.ROOT, "Block %d %d %d", bp.getX(), bp.getY(), bp.getZ()));
				out.add("Chunk " + (bp.getX() >> 4) + " " + (bp.getZ() >> 4));
				out.add(String.format(Locale.ROOT, "Yaw %.1f  Pitch %.1f", MathHelper.wrapDegrees(p.getYaw()), p.getPitch()));
				out.add("Facing " + compass(p.getYaw()));
				out.add("Light " + mc.world.getLightLevel(bp));
				break;
			}
			case "cooldowns": {
				out.add("Swing " + Math.round(p.getAttackCooldownProgress(0.0f) * 100) + "%");
				if (p.isUsingItem()) out.add(String.format(Locale.ROOT, "Using %.1fs", p.getItemUseTimeLeft() / 20.0));
				break;
			}
			case "foodpreview": {
				int food = p.getHungerManager().getFoodLevel();
				out.add(String.format(Locale.ROOT, "Food %d/20  Sat %.1f", food, p.getHungerManager().getSaturationLevel()));
				FoodComponent f = p.getMainHandStack().get(DataComponentTypes.FOOD);
				if (f != null) {
					out.add(String.format(Locale.ROOT, "Eat: +%d food (%d) +%.1f sat", f.nutrition(), Math.min(20, food + f.nutrition()), f.saturation()));
				}
				break;
			}
			case "itemcounter": {
				PlayerInventory inv = p.getInventory();
				for (String want : Config.items()) {
					int n = 0;
					for (int i = 0; i < inv.size(); i++) {
						ItemStack s = inv.getStack(i);
						if (!s.isEmpty() && Registries.ITEM.getId(s.getItem()).getPath().equals(want)) n += s.getCount();
					}
					out.add(pretty(want) + " x" + n);
				}
				break;
			}
			case "playerprofile": {
				if (mc.targetedEntity instanceof PlayerEntity t) {
					var net = mc.getNetworkHandler();
					var entry = net == null ? null : net.getPlayerListEntry(t.getUuid());
					out.add(t.getName().getString());
					out.add(String.format(Locale.ROOT, "HP %.1f (+%.1f)  Armor %d", t.getHealth(), t.getAbsorptionAmount(), t.getArmor()));
					out.add("Ping " + (entry == null ? "--" : entry.getLatency() + " ms") + "  Holding " + t.getMainHandStack().getName().getString());
				}
				break;
			}
			case "teamview": {
				if (mc.world == null) break;
				for (String name : Config.team()) {
					PlayerEntity found = null;
					for (PlayerEntity o : mc.world.getPlayers()) {
						if (o.getName().getString().equalsIgnoreCase(name)) {
							found = o;
							break;
						}
					}
					if (found == null) {
						out.add(name + "  --");
					} else {
						double dx = found.getX() - p.getX(), dz = found.getZ() - p.getZ();
						double yaw = Math.toDegrees(Math.atan2(-dx, dz));
						out.add(String.format(Locale.ROOT, "%s  %.0f hp  %.0fm %s", found.getName().getString(), found.getHealth(), p.distanceTo(found), compass(yaw)));
					}
				}
				break;
			}
			case "tntcountdown": {
				if (mc.world == null) break;
				List<TntEntity> tnts = new ArrayList<>();
				for (Entity e : mc.world.getEntities()) {
					if (e instanceof TntEntity t && p.distanceTo(t) < 48) tnts.add(t);
				}
				tnts.sort((a, b) -> Float.compare(p.distanceTo(a), p.distanceTo(b)));
				for (int i = 0; i < Math.min(3, tnts.size()); i++) {
					TntEntity t = tnts.get(i);
					out.add(String.format(Locale.ROOT, "TNT %.1fs  (%.0fm)", t.getFuse() / 20.0, p.distanceTo(t)));
				}
				break;
			}
			case "waypoints": {
				if (mc.world == null) break;
				String dim = mc.world.getRegistryKey().getValue().toString();
				for (Config.Waypoint w : Config.waypoints()) {
					if (!dim.equals(w.dim)) continue;
					double dx = w.x - p.getX(), dy = w.y - p.getY(), dz = w.z - p.getZ();
					double yaw = Math.toDegrees(Math.atan2(-dx, dz));
					out.add(String.format(Locale.ROOT, "%s  %.0fm %s", w.name, Math.sqrt(dx * dx + dy * dy + dz * dz), compass(yaw)));
				}
				break;
			}
			default:
				break;
		}
	}

	// ---- rendering ---------------------------------------------------
	public static void render(DrawContext ctx) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.options.hudHidden) return;
		pollMouse(mc);
		TextRenderer tr = mc.textRenderer;

		List<String> lines = new ArrayList<>();
		for (Modules.Mod m : Modules.ALL) {
			if (!m.ready || !Config.isEnabled(m.id)) continue;
			lines(m.id, mc, lines);
		}
		int y = 4, i = 0;
		for (String s : lines) {
			int w = Draw.w(tr, s) + 12;
			Draw.round(ctx, 4, y, w, 14, 4, 0xAA07051A);
			ctx.fill(5, y + 3, 7, y + 11, Draw.gradient(i / 6f));
			Draw.textS(ctx, tr, s, 10, y + 3, 0xFFFFFFFF);
			y += 16;
			i++;
		}

		int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
		if (Config.isEnabled("keystrokes")) drawKeys(ctx, mc, tr, 6, sh - 66);
		if (Config.isEnabled("armor")) drawArmor(ctx, mc, tr, sw - 70, sh - 96);
		if (Config.isEnabled("potions")) drawPotions(ctx, mc, tr, sw - 4, 4);
		if (Config.isEnabled("nowplaying")) LyricsHud.render(ctx, mc);
	}

	private static void key(DrawContext c, TextRenderer tr, int x, int y, int w, int h, String label, boolean down) {
		Draw.round(c, x, y, w, h, 4, down ? 0xDDD946EF : 0xAA07051A);
		Draw.text(c, tr, label, x + (w - Draw.w(tr, label)) / 2, y + (h - 8) / 2, 0xFFFFFFFF);
	}

	private static void drawKeys(DrawContext c, MinecraftClient mc, TextRenderer tr, int x, int y) {
		var o = mc.options;
		key(c, tr, x + 20, y, 18, 18, "W", o.forwardKey.isPressed());
		key(c, tr, x, y + 20, 18, 18, "A", o.leftKey.isPressed());
		key(c, tr, x + 20, y + 20, 18, 18, "S", o.backKey.isPressed());
		key(c, tr, x + 40, y + 20, 18, 18, "D", o.rightKey.isPressed());
		key(c, tr, x, y + 40, 28, 18, "LMB", lPrev);
		key(c, tr, x + 30, y + 40, 28, 18, "RMB", rPrev);
	}

	private static void drawArmor(DrawContext c, MinecraftClient mc, TextRenderer tr, int x, int y) {
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		for (int i = 0; i < slots.length; i++) {
			ItemStack s = mc.player.getEquippedStack(slots[i]);
			int yy = y + i * 20;
			if (s.isEmpty()) continue;
			Draw.round(c, x - 2, yy - 2, 66, 20, 4, 0xAA07051A);
			c.drawItem(s, x, yy);
			if (s.isDamageable()) {
				int left = s.getMaxDamage() - s.getDamage();
				Draw.textS(c, tr, left + "/" + s.getMaxDamage(), x + 20, yy + 4, 0xFFFFFFFF);
			}
		}
	}

	private static void drawPotions(DrawContext c, MinecraftClient mc, TextRenderer tr, int right, int y) {
		for (StatusEffectInstance e : mc.player.getStatusEffects()) {
			String name = e.getEffectType().value().getName().getString() + " " + (e.getAmplifier() + 1);
			int d = e.getDuration();
			String time = (d < 0 || d > 99999) ? "inf" : String.format(Locale.ROOT, "%d:%02d", d / 1200, (d / 20) % 60);
			String s = name + "  " + time;
			int w = Draw.w(tr, s) + 12;
			Draw.round(c, right - w, y, w, 14, 4, 0xAA07051A);
			Draw.textS(c, tr, s, right - w + 6, y + 3, 0xFFFFFFFF);
			y += 16;
		}
	}

	private Hud() {
	}
}
