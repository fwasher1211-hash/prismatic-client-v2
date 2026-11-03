package com.prismatic.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.List;

/** Karaoke-style synced lyrics: current line fills with color, next line shown dimmer. */
public final class LyricsHud {
	/** Set by the menu while editing Now playing, so you can preview without music. */
	public static boolean demo;

	private static final Identifier SMOOTH = Identifier.of("prismatic_client", "smooth");
	private static final Identifier UNIFORM = Identifier.of("minecraft", "uniform");

	private static MutableText styled(String s, Config.Spotify c) {
		Style st = Style.EMPTY;
		switch (c.font) {
			case "smooth":
				st = st.withFont(new StyleSpriteSource.Font(SMOOTH));
				break;
			case "uniform":
				st = st.withFont(new StyleSpriteSource.Font(UNIFORM));
				break;
			default:
				break;
		}
		if (c.bold) st = st.withBold(true);
		return Text.literal(s).setStyle(st);
	}

	private static int solid(String highlight) {
		switch (highlight) {
			case "cyan":
				return 0xFF22D3EE;
			case "violet":
				return 0xFF8B5CF6;
			case "white":
				return 0xFFFFFFFF;
			default:
				return 0xFFFF4FA3;
		}
	}

	private static void draw(DrawContext ctx, TextRenderer tr, String s, float scale, float cx, float y,
							 float fill, int baseColor, Config.Spotify c) {
		if (s.isEmpty()) return;
		float maxW = ctx.getScaledWindowWidth() * 0.94f;
		int w = Math.max(1, tr.getWidth(styled(s, c)));
		float sc = Math.min(scale, maxW / w);
		Matrix3x2fStack m = ctx.getMatrices();
		m.pushMatrix();
		m.translate(cx - w * sc / 2f, y);
		m.scale(sc, sc);

		int n = Math.max(0, Math.min(s.length(), Math.round(fill * s.length())));
		String pre = s.substring(0, n), post = s.substring(n);
		int pw = pre.isEmpty() ? 0 : tr.getWidth(styled(pre, c));
		if (!post.isEmpty()) ctx.drawText(tr, styled(post, c), pw, 0, baseColor, c.shadow);
		if (n > 0) {
			if ("prismatic".equals(c.highlight)) {
				for (int i = 0; i < n; i++) {
					int x = i == 0 ? 0 : tr.getWidth(styled(s.substring(0, i), c));
					float t = s.length() <= 1 ? 0 : i / (float) (s.length() - 1);
					ctx.drawText(tr, styled(s.substring(i, i + 1), c), x, 0, Draw.gradient(t), c.shadow);
				}
			} else {
				ctx.drawText(tr, styled(pre, c), 0, 0, solid(c.highlight), c.shadow);
			}
		}
		m.popMatrix();
	}

	private static float baseY(DrawContext ctx, Config.Spotify c) {
		int h = ctx.getScaledWindowHeight();
		switch (c.position) {
			case "middle":
				return h / 2f - 24;
			case "bottom":
				return h - 96;
			default:
				return 26;
		}
	}

	public static void render(DrawContext ctx, MinecraftClient mc) {
		Config.Spotify c = Config.spotify();
		TextRenderer tr = mc.textRenderer;
		float cx = ctx.getScaledWindowWidth() / 2f;
		float y = baseY(ctx, c);
		float big = 2.0f * c.scale;
		float small = 1.5f * c.scale;
		int white = 0xE6F2F2F7, dim = 0x99C8C8D4;

		if (!SpotifyService.hasTrack) {
			if (demo) {
				draw(ctx, tr, "I really want to come kick it with you.", big, cx, y,
					(System.currentTimeMillis() % 4000) / 4000f, white, c);
				if (c.nextLine) draw(ctx, tr, "You'll be my American Boy", small, cx, y + 11 * big, 0, dim, c);
			} else if (!SpotifyService.supported()) {
				draw(ctx, tr, "Now playing works on Windows only", 1.0f, cx, y, 0, white, c);
			}
			return;
		}

		List<SpotifyService.Line> ly = SpotifyService.lyrics;
		if (ly.isEmpty()) {
			String info = SpotifyService.lyricsState.equals("loading") ? "Loading lyrics..." : SpotifyService.title + " - " + SpotifyService.artist;
			draw(ctx, tr, info, big * 0.6f, cx, y, 1f, white, c);
			return;
		}

		long pos = SpotifyService.positionMs() + c.delayMs;
		int idx = -1;
		for (int i = 0; i < ly.size(); i++) {
			if (ly.get(i).t <= pos) idx = i;
			else break;
		}
		String cur = idx >= 0 ? ly.get(idx).text : "";
		String next = idx + 1 < ly.size() ? ly.get(idx + 1).text : "";
		float fill = 1f;
		if (idx >= 0 && idx + 1 < ly.size()) {
			long dur = ly.get(idx + 1).t - ly.get(idx).t;
			fill = Math.min(1f, (pos - ly.get(idx).t) / Math.max(1f, dur * 0.85f));
		}
		if (cur.isEmpty()) {
			cur = "...";
			fill = 0f;
		}
		draw(ctx, tr, cur, big, cx, y, fill, white, c);
		if (c.nextLine && !next.isEmpty()) draw(ctx, tr, next, small, cx, y + 11 * big, 0f, dim, c);
	}

	private LyricsHud() {
	}
}
