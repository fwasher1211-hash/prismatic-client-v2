package com.prismatic.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

/** Moves, scales or hides vanilla HUD pieces, plus the custom crosshair and smooth hotbar. */
public final class HudTweaks {
	private static float hotbarPos = -1f;
	private static long lastNs;

	public static void register() {
		wrap(VanillaHudElements.SCOREBOARD, "scoreboard", 1f, 0.5f);
		wrap(VanillaHudElements.BOSS_BAR, "bossbars", 0.5f, 0f);
		wrap(VanillaHudElements.TITLE_AND_SUBTITLE, "titles", 0.5f, 0.5f);
		wrap(VanillaHudElements.PLAYER_LIST, "compacttab", 0.5f, 0f);

		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, prev -> (ctx, tc) -> {
			if (Config.isEnabled("crosshair")) crosshair(ctx);
			else prev.render(ctx, tc);
		});

		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Identifier.of("prismatic_client", "hotbar"),
			(ctx, tc) -> hotbar(ctx));
	}

	private static void wrap(Identifier id, String key, float fx, float fy) {
		HudElementRegistry.replaceElement(id, (HudElement prev) -> (ctx, tc) -> {
			if (!Config.isEnabled(key)) {
				prev.render(ctx, tc);
				return;
			}
			if (Config.flag(key + ".hide", false)) return;
			float scale = Config.num(key + ".scale", key.equals("compacttab") ? 0.75f : 1f);
			float ox = Config.num(key + ".x", 0), oy = Config.num(key + ".y", 0);
			float sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
			float pivotX = sw * fx, pivotY = sh * fy;
			Matrix3x2fStack m = ctx.getMatrices();
			m.pushMatrix();
			m.translate(ox + pivotX, oy + pivotY);
			m.scale(scale, scale);
			m.translate(-pivotX, -pivotY);
			prev.render(ctx, tc);
			m.popMatrix();
		});
	}

	private static int crosshairColor(int idx) {
		switch (idx) {
			case 1:
				return 0xFFFFFFFF;
			case 2:
				return 0xFF22D3EE;
			case 3:
				return 0xFFF472B6;
			case 4:
				return 0xFF8B5CF6;
			case 5:
				return 0xFF4ADE80;
			default: {
				float t = (System.currentTimeMillis() % 4000L) / 2000f;
				return Draw.gradient(t > 1f ? 2f - t : t);
			}
		}
	}

	private static void crosshair(DrawContext ctx) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!mc.options.getPerspective().isFirstPerson()) return;
		int style = Math.round(Config.num("crosshair.style", 0));
		int size = Math.round(Config.num("crosshair.size", 5));
		int th = Math.round(Config.num("crosshair.thick", 1));
		int gap = style == 3 ? 0 : Math.round(Config.num("crosshair.gap", 2));
		int col = crosshairColor(Math.round(Config.num("crosshair.color", 0)));
		int cx = ctx.getScaledWindowWidth() / 2, cy = ctx.getScaledWindowHeight() / 2;
		int h = th / 2;
		int shadow = 0xAA000000;

		if (style == 1) {
			int r = th + 1;
			ctx.fill(cx - r / 2 - 1, cy - r / 2 - 1, cx - r / 2 + r + 1, cy - r / 2 + r + 1, shadow);
			ctx.fill(cx - r / 2, cy - r / 2, cx - r / 2 + r, cy - r / 2 + r, col);
		} else if (style == 2) {
			int r = size + 2;
			for (int i = 0; i < 72; i++) {
				double a = i * Math.PI * 2 / 72;
				int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
				ctx.fill(x - h, y - h, x - h + th, y - h + th, col);
			}
		} else {
			// top, bottom, left, right arms
			int[][] arms = {
				{cx - h, cy - gap - size, th, size},
				{cx - h, cy + gap + 1, th, size},
				{cx - gap - size, cy - h, size, th},
				{cx + gap + 1, cy - h, size, th}
			};
			for (int[] a : arms) ctx.fill(a[0] - 1, a[1] - 1, a[0] + a[2] + 1, a[1] + a[3] + 1, shadow);
			for (int[] a : arms) ctx.fill(a[0], a[1], a[0] + a[2], a[1] + a[3], col);
			if (style == 3) ctx.fill(cx - h, cy - h, cx - h + th, cy - h + th, col);
		}
	}

	private static void hotbar(DrawContext ctx) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!Config.isEnabled("smoothhotbar") || mc.player == null || mc.options.hudHidden) {
			hotbarPos = -1f;
			return;
		}
		int slot = mc.player.getInventory().getSelectedSlot();
		long now = System.nanoTime();
		float dt = lastNs == 0 ? 0.016f : Math.min(0.1f, (now - lastNs) / 1_000_000_000f);
		lastNs = now;
		if (hotbarPos < 0) hotbarPos = slot;
		float speed = Config.num("smoothhotbar.speed", 10f);
		hotbarPos += (slot - hotbarPos) * Math.min(1f, speed * dt);
		if (Math.abs(slot - hotbarPos) < 0.01f) hotbarPos = slot;

		int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
		int x = Math.round(sw / 2f - 92 + hotbarPos * 20f), y = sh - 23;
		float t = hotbarPos / 8f;
		int c1 = Draw.gradient(t), c2 = Draw.gradient(Math.min(1f, t + 0.25f));
		// glowing frame that slides between slots
		ctx.fill(x, y, x + 24, y + 2, c1);
		ctx.fill(x, y + 21, x + 24, y + 23, c2);
		ctx.fill(x, y + 2, x + 2, y + 21, c1);
		ctx.fill(x + 22, y + 2, x + 24, y + 21, c2);
	}

	private HudTweaks() {
	}
}
