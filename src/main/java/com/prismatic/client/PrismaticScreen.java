package com.prismatic.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** The Right Shift menu. Mouse is read straight from GLFW so it doesn't depend on Screen input signatures. */
public class PrismaticScreen extends Screen {
	private enum Mode { LIST, SETTINGS, SPOTIFY }

	private static final String[] CATS = {"All", "Enabled", "HUD", "Visuals", "Performance", "Utility"};
	private static final int PANEL = 0xFF07051A, SIDE = 0xFF110E2B, CARD = 0xFF130F2E, CARD_HOVER = 0xFF1B1542,
		BORDER = 0xFF2B2266, ACTIVE = 0xFF2C2160, TEXT = 0xFFF1ECFF, MUTED = 0xFFA99CDB, DIM = 0xFF5F5690, CHIP = 0xFF21184F,
		FIELD = 0xFF130F2E;

	private Mode mode = Mode.LIST;
	private int catIdx, scroll;
	private Modules.Mod editing;
	private TextFieldWidget search;
	private String dragKey, listenKey;
	private boolean wasDown, down, clicked;
	private int mx, my;
	private float partial;
	private int px, py, pw, ph, sideW, cx, cw;

	public PrismaticScreen() {
		super(Text.literal("Prismatic Client"));
	}

	public boolean shouldPause() {
		return false;
	}

	public boolean shouldCloseOnEsc() {
		return listenKey == null;
	}

	private void layout() {
		pw = Math.min(width - 16, 640);
		ph = Math.min(height - 16, 380);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
		sideW = pw < 380 ? 72 : 112;
		cx = px + sideW + 14;
		cw = pw - sideW - 28;
	}

	@Override
	protected void init() {
		layout();
		search = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, cx + 9, py + 36, cw - 18, 18, Text.literal("Search"));
		search.setDrawsBackground(false);
		search.setMaxLength(32);
		search.setChangedListener(s -> scroll = 0);
		addDrawableChild(search);
		setInitialFocus(search);
	}

	/** Not annotated on purpose: if the mouse wheel signature ever changes, the menu still compiles. */
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scroll -= (int) (verticalAmount * 24);
		return true;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		MinecraftClient mc = MinecraftClient.getInstance();
		TextRenderer tr = mc.textRenderer;
		long win = mc.getWindow().getHandle();
		down = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		clicked = down && !wasDown;
		if (!down && wasDown) {
			dragKey = null;
			Config.save();
		}
		wasDown = down;
		mx = mouseX;
		my = mouseY;
		partial = delta;
		LyricsHud.demo = mode == Mode.SPOTIFY;
		layout();

		// listening for a key binding
		if (listenKey != null) {
			for (int k = 32; k <= 348; k++) {
				if (k == GLFW.GLFW_KEY_RIGHT_SHIFT) continue;
				if (GLFW.glfwGetKey(win, k) == GLFW.GLFW_PRESS) {
					if (k != GLFW.GLFW_KEY_ESCAPE) Config.setNum(listenKey, k);
					listenKey = null;
					Config.save();
					break;
				}
			}
		}

		ctx.fill(0, 0, width, height, 0x99050310);
		Draw.round(ctx, px, py, pw, ph, 7, BORDER);
		Draw.round(ctx, px + 1, py + 1, pw - 2, ph - 2, 6, PANEL);
		Draw.round(ctx, px + 1, py + 1, sideW, ph - 2, 6, SIDE);
		ctx.fill(px + 8, py + 1, px + 1 + sideW, py + ph - 1, SIDE);

		sidebar(ctx, tr, mc);
		boolean listing = mode == Mode.LIST;
		search.visible = listing;
		search.active = listing;
		if (!listing) search.setFocused(false);

		if (mode == Mode.LIST) list(ctx, tr);
		else if (mode == Mode.SETTINGS) settings(ctx, tr);
		else spotify(ctx, tr);
	}

	// ---- sidebar -----------------------------------------------------
	private void sidebar(DrawContext ctx, TextRenderer tr, MinecraftClient mc) {
		int sx = px + 10;
		Draw.gradientRound(ctx, sx, py + 12, 20, 20, 6);
		Draw.textCentered(ctx, tr, "P", sx + 10, py + 18, 0xFF07051A);
		Draw.text(ctx, tr, "Prismatic", sx + 26, py + 17, TEXT);

		String[] nav = {"Modules", "Lyrics"};
		int ny = py + 48;
		for (int i = 0; i < nav.length; i++) {
			boolean active = (i == 0 && mode != Mode.SPOTIFY) || (i == 1 && mode == Mode.SPOTIFY);
			int nw = sideW - 16, nh = 22;
			boolean hover = Draw.inside(mx, my, px + 8, ny, nw, nh);
			if (active) Draw.round(ctx, px + 8, ny, nw, nh, 6, ACTIVE);
			else if (hover) Draw.round(ctx, px + 8, ny, nw, nh, 6, 0xFF1A1540);
			Draw.text(ctx, tr, nav[i], px + 18, ny + 7, active ? TEXT : MUTED);
			if (clicked && hover) {
				clicked = false;
				mode = i == 0 ? Mode.LIST : Mode.SPOTIFY;
				scroll = 0;
			}
			ny += 26;
		}

		// user card
		int uy = py + ph - 40;
		Draw.round(ctx, px + 8, uy, sideW - 16, 32, 6, 0xFF0A0820);
		Draw.circleGradient(ctx, px + 21, uy + 16, 8);
		String name = mc.player != null ? mc.player.getName().getString() : "Player";
		Draw.text(ctx, tr, Draw.trim(tr, name, sideW - 50), px + 34, uy + 6, TEXT);
		Draw.text(ctx, tr, "Right Shift", px + 34, uy + 17, MUTED);
	}

	// ---- module list -------------------------------------------------
	private List<Modules.Mod> filtered() {
		String q = search.getText().trim().toLowerCase(Locale.ROOT);
		String cat = CATS[catIdx];
		List<Modules.Mod> out = new ArrayList<>();
		for (Modules.Mod m : Modules.ALL) {
			if (cat.equals("Enabled")) {
				if (!m.ready || !Config.isEnabled(m.id)) continue;
			} else if (!cat.equals("All") && !m.cat.equals(cat)) continue;
			if (!q.isEmpty() && !(m.name.toLowerCase(Locale.ROOT).contains(q) || m.desc.toLowerCase(Locale.ROOT).contains(q))) continue;
			out.add(m);
		}
		out.sort(Comparator.comparing((Modules.Mod m) -> !m.ready));
		return out;
	}

	private void list(DrawContext ctx, TextRenderer tr) {
		Draw.scaled(ctx, tr, "Modules", cx, py + 11, 1.5f, TEXT);
		int enabled = 0, working = 0;
		for (Modules.Mod m : Modules.ALL) {
			if (m.ready) working++;
			if (m.ready && Config.isEnabled(m.id)) enabled++;
		}
		String count = enabled + " on  |  " + working + " working";
		Draw.text(ctx, tr, count, cx + cw - Draw.w(tr, count), py + 16, MUTED);

		// search bar
		boolean focused = search.isFocused();
		Draw.outlined(ctx, cx, py + 34, cw, 20, 6, FIELD, focused ? 0xFF8B5CF6 : BORDER);
		search.setX(cx + 9);
		search.setY(py + 35);
		search.render(ctx, mx, my, partial);
		if (search.getText().isEmpty() && !focused) Draw.text(ctx, tr, "Search modules", cx + 9, py + 40, DIM);

		// category chips
		int chipX = cx, chipY = py + 60;
		for (int i = 0; i < CATS.length; i++) {
			int cwid = Draw.w(tr, CATS[i]) + 18;
			boolean hover = Draw.inside(mx, my, chipX, chipY, cwid, 15);
			if (i == catIdx) {
				Draw.gradientRound(ctx, chipX, chipY, cwid, 15, 7);
				Draw.text(ctx, tr, CATS[i], chipX + 9, chipY + 4, 0xFF07051A);
			} else {
				Draw.round(ctx, chipX, chipY, cwid, 15, 7, hover ? 0xFF2D2366 : CHIP);
				Draw.text(ctx, tr, CATS[i], chipX + 9, chipY + 4, TEXT);
			}
			if (clicked && hover) {
				catIdx = i;
				scroll = 0;
				clicked = false;
			}
			chipX += cwid + 5;
		}

		// cards
		List<Modules.Mod> mods = filtered();
		int gx = cx, gy = py + 82, gw = cw, gh = py + ph - 12 - gy;
		int gap = 6, cols = gw >= 380 ? 2 : 1, cardW = (gw - gap * (cols - 1)) / cols, ch = 30;
		int rows = (mods.size() + cols - 1) / cols;
		int content = rows * (ch + gap);
		int max = Math.max(0, content - gh);
		scroll = Math.max(0, Math.min(max, scroll));

		if (mods.isEmpty()) {
			Draw.textCentered(ctx, tr, "No modules match your search", gx + gw / 2, gy + 20, DIM);
			return;
		}

		ctx.enableScissor(gx, gy, gx + gw, gy + gh);
		Mode next = mode;
		Modules.Mod openMod = null;
		for (int i = 0; i < mods.size(); i++) {
			Modules.Mod m = mods.get(i);
			int x = gx + (i % cols) * (cardW + gap);
			int y = gy + (i / cols) * (ch + gap) - scroll;
			if (y + ch < gy || y > gy + gh) continue;
			boolean hover = Draw.inside(mx, my, gx, gy, gw, gh) && Draw.inside(mx, my, x, y, cardW, ch);
			Draw.outlined(ctx, x, y, cardW, ch, 6, hover && m.ready ? CARD_HOVER : CARD, BORDER);

			int idx = Modules.ALL.indexOf(m);
			Draw.round(ctx, x + 7, y + 6, 18, 18, 5, m.ready ? Draw.gradient((idx % 7) / 6f) : 0xFF2A2450);
			Draw.textCentered(ctx, tr, m.name.substring(0, 1).toUpperCase(Locale.ROOT), x + 16, y + 11, m.ready ? 0xFF07051A : DIM);
			Draw.text(ctx, tr, Draw.trim(tr, m.name, cardW - 100), x + 32, y + 6, m.ready ? TEXT : DIM);
			Draw.text(ctx, tr, Draw.trim(tr, m.desc, cardW - 92), x + 32, y + 17, m.ready ? MUTED : 0xFF4A4378);

			if (!m.ready) {
				Draw.text(ctx, tr, "SOON", x + cardW - 10 - Draw.w(tr, "SOON"), y + 11, DIM);
				continue;
			}
			boolean on = Config.isEnabled(m.id);
			int tx = x + cardW - 32, ty = y + (ch - 11) / 2;
			toggle(ctx, tx, ty, on);
			if (m.editable()) {
				int ex = tx - 10 - Draw.w(tr, "Edit");
				boolean hEdit = Draw.inside(mx, my, ex - 3, y + 6, Draw.w(tr, "Edit") + 6, 18) && Draw.inside(mx, my, gx, gy, gw, gh);
				Draw.text(ctx, tr, "Edit", ex, y + 11, hEdit ? 0xFFD946EF : 0xFF8B5CF6);
				if (clicked && hEdit) {
					clicked = false;
					if (m.id.equals("nowplaying")) next = Mode.SPOTIFY;
					else {
						next = Mode.SETTINGS;
						openMod = m;
					}
					continue;
				}
			}
			if (clicked && hover) {
				Config.setEnabled(m.id, !on);
				clicked = false;
			}
		}
		ctx.disableScissor();
		if (next != mode) {
			mode = next;
			editing = openMod;
			scroll = 0;
		}

		if (max > 0) {
			int barH = Math.max(18, gh * gh / content);
			int barY = gy + (gh - barH) * scroll / max;
			ctx.fill(gx + gw + 3, gy, gx + gw + 5, gy + gh, 0xFF1A1540);
			ctx.fill(gx + gw + 3, barY, gx + gw + 5, barY + barH, 0xFF8B5CF6);
		}
	}

	private void toggle(DrawContext ctx, int x, int y, boolean on) {
		int w = 24, h = 12;
		if (on) {
			Draw.gradientRound(ctx, x, y, w, h, 6);
			Draw.round(ctx, x + w - 10, y + 2, 8, 8, 4, 0xFFFFFFFF);
		} else {
			Draw.round(ctx, x, y, w, h, 6, 0xFF2C2556);
			Draw.round(ctx, x + 2, y + 2, 8, 8, 4, 0xFFB8B0E0);
		}
	}

	private boolean button(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, String label) {
		boolean hover = Draw.inside(mx, my, x, y, w, h);
		Draw.outlined(ctx, x, y, w, h, 5, hover ? 0xFF241C5A : FIELD, hover ? 0xFF8B5CF6 : BORDER);
		Draw.text(ctx, tr, label, x + (w - Draw.w(tr, label)) / 2, y + (h - 8) / 2, TEXT);
		if (clicked && hover) {
			clicked = false;
			return true;
		}
		return false;
	}

	// ---- generic module settings --------------------------------------
	private static String keyName(int k) {
		if (k == GLFW.GLFW_KEY_SPACE) return "Space";
		if (k == GLFW.GLFW_KEY_LEFT_SHIFT) return "Left Shift";
		if (k == GLFW.GLFW_KEY_LEFT_CONTROL || k == GLFW.GLFW_KEY_RIGHT_CONTROL) return "Ctrl";
		if (k == GLFW.GLFW_KEY_LEFT_ALT || k == GLFW.GLFW_KEY_RIGHT_ALT) return "Alt";
		if (k == GLFW.GLFW_KEY_TAB) return "Tab";
		if (k >= GLFW.GLFW_KEY_F1 && k <= GLFW.GLFW_KEY_F12) return "F" + (k - GLFW.GLFW_KEY_F1 + 1);
		String n = GLFW.glfwGetKeyName(k, 0);
		return n == null ? "Key " + k : n.toUpperCase(Locale.ROOT);
	}

	private static String fmt(float v, float step) {
		if (step >= 1f) return String.valueOf(Math.round(v));
		if (step >= 0.1f) return String.format(Locale.ROOT, "%.1f", v);
		return String.format(Locale.ROOT, "%.2f", v);
	}

	private void control(DrawContext ctx, TextRenderer tr, Modules.Setting s, int x, int y, int w) {
		float cur = Config.num(s.key, s.def);
		switch (s.type) {
			case TOGGLE: {
				boolean on = cur >= 0.5f;
				toggle(ctx, x + w - 24, y + 3, on);
				if (clicked && Draw.inside(mx, my, x + w - 28, y, 30, 18)) {
					Config.setNum(s.key, on ? 0 : 1);
					Config.save();
					clicked = false;
				}
				break;
			}
			case CYCLE: {
				int idx = Math.max(0, Math.min(s.names.length - 1, Math.round(cur)));
				if (button(ctx, tr, x, y, w, 18, s.names[idx])) {
					Config.setNum(s.key, (idx + 1) % s.names.length);
					Config.save();
				}
				break;
			}
			case KEY: {
				boolean listening = s.key.equals(listenKey);
				if (button(ctx, tr, x, y, w, 18, listening ? "Press a key..." : keyName(Math.round(cur)))) {
					listenKey = s.key;
				}
				break;
			}
			default: {
				int tw = w - 52;
				int sy = y + 8;
				Draw.round(ctx, x, sy, tw, 3, 1, 0xFF2C2556);
				float t = (cur - s.min) / (s.max - s.min);
				t = Math.max(0f, Math.min(1f, t));
				Draw.gradientStrip(ctx, x, sy, Math.max(1, (int) (tw * t)), 3);
				int kx = x + (int) (tw * t);
				Draw.round(ctx, kx - 3, sy - 3, 7, 9, 3, 0xFFFFFFFF);
				Draw.text(ctx, tr, fmt(cur, s.step) + s.unit, x + tw + 8, y + 5, TEXT);
				if (clicked && Draw.inside(mx, my, x - 4, sy - 6, tw + 8, 16)) dragKey = s.key;
				if (s.key.equals(dragKey) && down) {
					float nt = Math.max(0f, Math.min(1f, (mx - x) / (float) tw));
					float v = s.min + nt * (s.max - s.min);
					v = Math.round(v / s.step) * s.step;
					v = Math.max(s.min, Math.min(s.max, v));
					Config.setNum(s.key, v);
				}
			}
		}
	}

	private void settings(DrawContext ctx, TextRenderer tr) {
		Modules.Mod m = editing;
		if (m == null) {
			mode = Mode.LIST;
			return;
		}
		if (button(ctx, tr, cx + cw - 60, py + 12, 60, 18, "< Back")) {
			mode = Mode.LIST;
			Config.save();
			return;
		}
		Draw.scaled(ctx, tr, m.name, cx, py + 11, 1.5f, TEXT);
		Draw.text(ctx, tr, m.desc, cx, py + 32, MUTED);

		int y = py + 56, ctlW = Math.min(190, cw - 100);
		for (Modules.Setting s : m.settings) {
			Draw.text(ctx, tr, s.label, cx, y + 5, MUTED);
			control(ctx, tr, s, cx + cw - ctlW, y, ctlW);
			y += 24;
		}
		if (!m.hint.isEmpty()) {
			y += 6;
			for (String line : m.hint.split("\n")) {
				Draw.text(ctx, tr, line, cx, y, line.startsWith("/") ? 0xFF8B5CF6 : MUTED);
				y += 12;
			}
		}
		if (m.id.equals("quickaction")) {
			y += 4;
			Draw.text(ctx, tr, "Current message: " + Draw.trim(tr, Config.text("quick", "gg"), cw - 120), cx, y, TEXT);
		}
	}

	// ---- Now playing settings ---------------------------------------
	private static final String[] FONTS = {"smooth", "minecraft", "uniform"};
	private static final String[] FONT_NAMES = {"Poppins", "Minecraft", "Uniform"};
	private static final String[] HILITE = {"prismatic", "pink", "cyan", "violet", "white"};
	private static final String[] HILITE_NAMES = {"Prismatic", "Pink", "Cyan", "Violet", "White"};
	private static final String[] POS = {"top", "middle", "bottom"};
	private static final String[] POS_NAMES = {"Top", "Middle", "Bottom"};

	private static int idx(String[] arr, String v) {
		for (int i = 0; i < arr.length; i++) if (arr[i].equals(v)) return i;
		return 0;
	}

	private void spotify(DrawContext ctx, TextRenderer tr) {
		Config.Spotify c = Config.spotify();
		int x = cx, y = py + 56, rh = 24, ctl = cx + cw - 160, cwid = 160;

		Draw.scaled(ctx, tr, "Now playing", cx, py + 11, 1.5f, TEXT);
		Draw.text(ctx, tr, "Synced Spotify lyrics (preview shows while you edit)", cx, py + 32, MUTED);
		if (!SpotifyService.supported()) {
			Draw.text(ctx, tr, "Spotify detection works on Windows only", cx, py + 42, 0xFFF472B6);
		}

		// font size slider
		Draw.text(ctx, tr, "Font size", x, y + 5, MUTED);
		int sx = ctl, sw = cwid - 52, sy = y + 8;
		Draw.round(ctx, sx, sy, sw, 3, 1, 0xFF2C2556);
		float t = (c.scale - 0.5f) / 2.5f;
		Draw.gradientStrip(ctx, sx, sy, Math.max(1, (int) (sw * t)), 3);
		int kx = sx + (int) (sw * t);
		Draw.round(ctx, kx - 3, sy - 3, 7, 9, 3, 0xFFFFFFFF);
		Draw.text(ctx, tr, Math.round(c.scale * 100) + "%", sx + sw + 8, y + 5, TEXT);
		if (clicked && Draw.inside(mx, my, sx - 4, sy - 6, sw + 8, 16)) dragKey = "spotify.size";
		if ("spotify.size".equals(dragKey) && down) {
			float nt = Math.max(0f, Math.min(1f, (mx - sx) / (float) sw));
			c.scale = Math.round((0.5f + nt * 2.5f) * 20f) / 20f;
		}
		y += rh;

		Draw.text(ctx, tr, "Font style", x, y + 5, MUTED);
		if (button(ctx, tr, ctl, y, cwid, 18, FONT_NAMES[idx(FONTS, c.font)])) {
			c.font = FONTS[(idx(FONTS, c.font) + 1) % FONTS.length];
			Config.save();
		}
		y += rh;
		Draw.text(ctx, tr, "Highlight color", x, y + 5, MUTED);
		if (button(ctx, tr, ctl, y, cwid, 18, HILITE_NAMES[idx(HILITE, c.highlight)])) {
			c.highlight = HILITE[(idx(HILITE, c.highlight) + 1) % HILITE.length];
			Config.save();
		}
		y += rh;
		Draw.text(ctx, tr, "Screen position", x, y + 5, MUTED);
		if (button(ctx, tr, ctl, y, cwid, 18, POS_NAMES[idx(POS, c.position)])) {
			c.position = POS[(idx(POS, c.position) + 1) % POS.length];
			Config.save();
		}
		y += rh;
		Draw.text(ctx, tr, "Bold text", x, y + 5, MUTED);
		if (button(ctx, tr, ctl, y, cwid, 18, c.bold ? "On" : "Off")) {
			c.bold = !c.bold;
			Config.save();
		}
		y += rh;
		Draw.text(ctx, tr, "Text shadow", x, y + 5, MUTED);
		if (button(ctx, tr, ctl, y, cwid, 18, c.shadow ? "On" : "Off")) {
			c.shadow = !c.shadow;
			Config.save();
		}
		y += rh;
		Draw.text(ctx, tr, "Show next line", x, y + 5, MUTED);
		if (button(ctx, tr, ctl, y, cwid, 18, c.nextLine ? "On" : "Off")) {
			c.nextLine = !c.nextLine;
			Config.save();
		}
		y += rh;
		Draw.text(ctx, tr, "Lyrics delay", x, y + 5, MUTED);
		if (button(ctx, tr, ctl, y, 34, 18, "-100")) {
			c.delayMs = Math.max(-5000, c.delayMs - 100);
			Config.save();
		}
		Draw.textCentered(ctx, tr, c.delayMs + " ms", ctl + cwid / 2, y + 5, TEXT);
		if (button(ctx, tr, ctl + cwid - 34, y, 34, 18, "+100")) {
			c.delayMs = Math.min(5000, c.delayMs + 100);
			Config.save();
		}
	}
}
