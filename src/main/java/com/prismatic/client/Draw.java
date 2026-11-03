package com.prismatic.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

/** Small drawing helpers (ARGB colors) and the Prismatic UI font. */
public final class Draw {
	public static final int[] STOPS = {0xFF22D3EE, 0xFF3B82F6, 0xFF8B5CF6, 0xFFD946EF, 0xFFF472B6};
	public static final Identifier UI = Identifier.of("prismatic_client", "ui");

	public static int gradient(float t) {
		t = Math.max(0f, Math.min(1f, t));
		float seg = t * (STOPS.length - 1);
		int i = Math.min((int) seg, STOPS.length - 2);
		float f = seg - i;
		int a = STOPS[i], b = STOPS[i + 1];
		int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * f);
		int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * f);
		int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * f);
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}

	// ---- text (Poppins UI font) --------------------------------------
	public static MutableText t(String s) {
		return Text.literal(s).setStyle(Style.EMPTY.withFont(new StyleSpriteSource.Font(UI)));
	}

	public static int w(TextRenderer tr, String s) {
		return tr.getWidth(t(s));
	}

	public static void text(DrawContext c, TextRenderer tr, String s, int x, int y, int color) {
		c.drawText(tr, t(s), x, y, color, false);
	}

	public static void textS(DrawContext c, TextRenderer tr, String s, int x, int y, int color) {
		c.drawText(tr, t(s), x, y, color, true);
	}

	public static void textCentered(DrawContext c, TextRenderer tr, String s, int cx, int y, int color) {
		text(c, tr, s, cx - w(tr, s) / 2, y, color);
	}

	public static String trim(TextRenderer tr, String s, int maxW) {
		if (w(tr, s) <= maxW) return s;
		String cut = s;
		while (cut.length() > 1 && w(tr, cut + "...") > maxW) cut = cut.substring(0, cut.length() - 1);
		return cut + "...";
	}

	public static void scaled(DrawContext c, TextRenderer tr, String s, float x, float y, float sc, int color) {
		Matrix3x2fStack m = c.getMatrices();
		m.pushMatrix();
		m.translate(x, y);
		m.scale(sc, sc);
		text(c, tr, s, 0, 0, color);
		m.popMatrix();
	}

	public static void gradientText(DrawContext c, TextRenderer tr, String s, float x, float y, float sc) {
		Matrix3x2fStack m = c.getMatrices();
		m.pushMatrix();
		m.translate(x, y);
		m.scale(sc, sc);
		for (int i = 0; i < s.length(); i++) {
			int cx = i == 0 ? 0 : w(tr, s.substring(0, i));
			text(c, tr, s.substring(i, i + 1), cx, 0, gradient(s.length() <= 1 ? 0 : i / (float) (s.length() - 1)));
		}
		m.popMatrix();
	}

	// ---- shapes -------------------------------------------------------
	/** Rounded-looking box (corners trimmed by 1px). */
	public static void box(DrawContext c, int x, int y, int w, int h, int argb) {
		c.fill(x + 1, y, x + w - 1, y + h, argb);
		c.fill(x, y + 1, x + 1, y + h - 1, argb);
		c.fill(x + w - 1, y + 1, x + w, y + h - 1, argb);
	}

	private static int inset(int d, int r) {
		double dx = r - d - 0.5;
		return (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dx * dx)));
	}

	public static void round(DrawContext c, int x, int y, int w, int h, int r, int argb) {
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
		for (int d = 0; d < r; d++) {
			int in = inset(d, r);
			c.fill(x + in, y + d, x + w - in, y + d + 1, argb);
			c.fill(x + in, y + h - 1 - d, x + w - in, y + h - d, argb);
		}
		c.fill(x, y + r, x + w, y + h - r, argb);
	}

	public static void outlined(DrawContext c, int x, int y, int w, int h, int r, int fill, int border) {
		round(c, x, y, w, h, r, border);
		round(c, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
	}

	/** Horizontal gradient pill / rounded rectangle. */
	public static void gradientRound(DrawContext c, int x, int y, int w, int h, int r) {
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
		for (int i = 0; i < w; i++) {
			int in = 0;
			if (i < r) in = inset(i, r);
			else if (i >= w - r) in = inset(w - 1 - i, r);
			c.fill(x + i, y + in, x + i + 1, y + h - in, gradient(w <= 1 ? 0 : i / (float) (w - 1)));
		}
	}

	public static void circleGradient(DrawContext c, int cx, int cy, int r) {
		for (int dx = -r; dx <= r; dx++) {
			int half = (int) Math.round(Math.sqrt(Math.max(0, r * r - dx * dx)));
			c.fill(cx + dx, cy - half, cx + dx + 1, cy + half, gradient((dx + r) / (2f * r)));
		}
	}

	/** Horizontal gradient strip. */
	public static void gradientStrip(DrawContext c, int x, int y, int w, int h) {
		for (int i = 0; i < w; i++) {
			c.fill(x + i, y, x + i + 1, y + h, gradient(w <= 1 ? 0 : i / (float) (w - 1)));
		}
	}

	public static boolean inside(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private Draw() {
	}
}
