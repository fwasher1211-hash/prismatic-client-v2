package com.prismatic.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Replaces Minecraft's title screen when the "Prismatic title screen" module is on. */
public class PrismaticTitleScreen extends Screen {
	private boolean wasDown;

	public PrismaticTitleScreen() {
		super(Text.literal("Prismatic Client"));
	}

	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		MinecraftClient mc = MinecraftClient.getInstance();
		TextRenderer tr = mc.textRenderer;
		boolean down = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		boolean clicked = down && !wasDown;
		wasDown = down;

		// background: deep navy fading into violet
		int bands = 48;
		for (int i = 0; i < bands; i++) {
			float t = i / (float) (bands - 1);
			int r = (int) (7 + 42 * t * t), g = (int) (5 + 10 * t * t), b = (int) (26 + 50 * t * t);
			ctx.fill(0, height * i / bands, width, height * (i + 1) / bands + 1, 0xFF000000 | (r << 16) | (g << 8) | b);
		}

		float sc = 4f;
		String title = "PRISMATIC";
		int tw = (int) (Draw.w(tr, title) * sc);
		Draw.gradientText(ctx, tr, title, (width - tw) / 2f, height / 2f - 90, sc);
		Draw.textCentered(ctx, tr, "C L I E N T", width / 2, height / 2 - 44, 0xFFA99CDB);

		String[] labels = {"Singleplayer", "Multiplayer", "Options", "Quit Game"};
		int bw = 170, bh = 24, bx = (width - bw) / 2, by = height / 2 - 22;
		for (int i = 0; i < labels.length; i++) {
			int y = by + i * (bh + 6);
			boolean hover = Draw.inside(mouseX, mouseY, bx, y, bw, bh);
			if (hover) {
				Draw.gradientRound(ctx, bx - 1, y - 1, bw + 2, bh + 2, 8);
				Draw.round(ctx, bx + 1, y + 1, bw - 2, bh - 2, 7, 0xFF1B1542);
			} else {
				Draw.outlined(ctx, bx, y, bw, bh, 8, 0xFF130F2E, 0xFF2B2266);
			}
			Draw.textCentered(ctx, tr, labels[i], width / 2, y + (bh - 8) / 2, 0xFFF1ECFF);
			if (clicked && hover) {
				clicked = false;
				switch (i) {
					case 0:
						mc.setScreen(new SelectWorldScreen(this));
						break;
					case 1:
						mc.setScreen(new MultiplayerScreen(this));
						break;
					case 2:
						mc.setScreen(new OptionsScreen(this, mc.options));
						break;
					default:
						mc.scheduleStop();
						break;
				}
				return;
			}
		}
		String foot = "Prismatic Client  |  Fabric 1.21.11";
		Draw.text(ctx, tr, foot, 8, height - 14, 0xFF5F5690);
	}
}
