package com.prismatic.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/** Tick-driven features: flight, snap look, quick action, particles, plus tooltip and pause banner. */
public final class Features {
	private static boolean snapPrev, quickPrev, flightApplied, particlesOverridden;
	private static int savedParticles = -1;

	private static boolean down(long win, int key) {
		return key > 0 && GLFW.glfwGetKey(win, key) == GLFW.GLFW_PRESS;
	}

	public static void tick(MinecraftClient mc) {
		if (mc.getWindow() == null) return;
		long win = mc.getWindow().getHandle();
		PlayerEntity p = mc.player;

		// particles (enum class is looked up from the option itself, so no import is needed)
		var particleOpt = mc.options.getParticles();
		var modes = particleOpt.getValue().getDeclaringClass().getEnumConstants();
		if (Config.isEnabled("particles")) {
			int idx = Math.max(0, Math.min(modes.length - 1, Math.round(Config.num("particles.level", 2))));
			if (!particlesOverridden) {
				savedParticles = particleOpt.getValue().ordinal();
				particlesOverridden = true;
			}
			if (particleOpt.getValue() != modes[idx]) particleOpt.setValue(modes[idx]);
		} else if (particlesOverridden) {
			if (savedParticles >= 0 && savedParticles < modes.length) particleOpt.setValue(modes[savedParticles]);
			particlesOverridden = false;
		}

		if (p == null) {
			flightApplied = false;
			return;
		}
		boolean creativeFly = p.isCreative() || p.isSpectator();

		// flight speed
		if (Config.isEnabled("flightspeed") && creativeFly) {
			p.getAbilities().setFlySpeed(0.05f * Config.num("flightspeed.mult", 3f));
			flightApplied = true;
		} else if (flightApplied) {
			p.getAbilities().setFlySpeed(0.05f);
			flightApplied = false;
		}

		// bedrock-style flight: stop instantly when you let go of the keys
		if (Config.isEnabled("bedrockflight") && creativeFly && p.getAbilities().flying && mc.currentScreen == null) {
			var o = mc.options;
			boolean move = o.forwardKey.isPressed() || o.backKey.isPressed() || o.leftKey.isPressed() || o.rightKey.isPressed();
			boolean vert = o.jumpKey.isPressed() || o.sneakKey.isPressed();
			Vec3d v = p.getVelocity();
			if (!move || !vert) p.setVelocity(move ? v.x : 0, vert ? v.y : 0, move ? v.z : 0);
		}

		boolean noScreen = mc.currentScreen == null;

		// snap look
		boolean snap = noScreen && Config.isEnabled("snaplook") && down(win, Math.round(Config.num("snaplook.key", 86)));
		if (snap && !snapPrev) {
			float[] steps = {15f, 45f, 90f};
			float[] pitches = {0f, 45f, 75f, 90f};
			float step = steps[Math.max(0, Math.min(2, Math.round(Config.num("snaplook.step", 1))))];
			float pitch = pitches[Math.max(0, Math.min(3, Math.round(Config.num("snaplook.pitch", 0))))];
			p.setYaw(Math.round(p.getYaw() / step) * step);
			p.setPitch(pitch);
		}
		snapPrev = snap;

		// quick action
		boolean quick = noScreen && Config.isEnabled("quickaction") && down(win, Math.round(Config.num("quickaction.key", 71)));
		if (quick && !quickPrev && mc.getNetworkHandler() != null) {
			String msg = Config.text("quick", "gg");
			if (msg.startsWith("/")) mc.getNetworkHandler().sendChatCommand(msg.substring(1));
			else mc.getNetworkHandler().sendChatMessage(msg);
		}
		quickPrev = quick;
	}

	/** Shulker preview: lists the box contents in the item tooltip. */
	public static void tooltip(ItemStack stack, List<Text> lines) {
		if (!Config.isEnabled("shulkerpreview")) return;
		ContainerComponent cc = stack.get(DataComponentTypes.CONTAINER);
		if (cc == null) return;
		int n = 0;
		for (ItemStack s : cc.iterateNonEmpty()) {
			if (n++ >= 27) break;
			lines.add(Text.literal(String.format(Locale.ROOT, " %dx %s", s.getCount(), s.getName().getString())).formatted(Formatting.GRAY));
		}
		if (n == 0) lines.add(Text.literal(" (empty)").formatted(Formatting.DARK_GRAY));
	}

	/** Prismatic game menu: a branded banner over the vanilla pause screen. */
	public static void pauseBanner(DrawContext ctx, int width) {
		if (!Config.isEnabled("gamemenu")) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		var tr = mc.textRenderer;
		ctx.fill(0, 0, width, 26, 0xEE07051A);
		Draw.gradientStrip(ctx, 0, 26, width, 2);
		Draw.gradientText(ctx, tr, "PRISMATIC", 10, 9, 1.3f);
		String right = mc.getCurrentFps() + " FPS";
		var info = mc.getCurrentServerEntry();
		right += "  |  " + (info == null ? "Singleplayer" : info.address);
		Draw.text(ctx, tr, right, width - 10 - Draw.w(tr, right), 9, 0xFFA99CDB);
	}

	private Features() {
	}
}
