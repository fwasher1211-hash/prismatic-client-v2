package com.prismatic.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads what Spotify is playing from Windows' media controls (no login needed)
 * and fetches synced lyrics from LRCLIB (free, no key).
 */
public final class SpotifyService {
	public static final class Line {
		public final long t;
		public final String text;

		Line(long t, String text) {
			this.t = t;
			this.text = text;
		}
	}

	public static volatile boolean hasTrack, playing;
	public static volatile String title = "", artist = "", album = "";
	public static volatile long durationMs, basePos, baseUpd, baseRecv;
	public static volatile List<Line> lyrics = List.of();
	public static volatile String lyricsState = "idle"; // idle | loading | found | none

	private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
	private static final Map<String, List<Line>> CACHE = new HashMap<>();
	private static volatile boolean wanted;
	private static Thread worker;
	private static Process proc;
	private static String lastKey = "";

	private static final String SCRIPT = String.join("\n",
		"$ErrorActionPreference='SilentlyContinue'",
		"[Console]::OutputEncoding=[System.Text.Encoding]::UTF8",
		"Add-Type -AssemblyName System.Runtime.WindowsRuntime",
		"$asTaskGeneric=([System.WindowsRuntimeSystemExtensions].GetMethods()|Where-Object{$_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'})[0]",
		"function Await($op,$type){$m=$asTaskGeneric.MakeGenericMethod($type);$t=$m.Invoke($null,@($op));$t.Wait(-1)|Out-Null;$t.Result}",
		"[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]",
		"[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties,Windows.Media.Control,ContentType=WindowsRuntime]",
		"$mgr=Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])",
		"while($true){",
		"  $out=@{ok=$false}",
		"  try{",
		"    $sess=$null",
		"    foreach($s in $mgr.GetSessions()){ if($s.SourceAppUserModelId -like '*Spotify*'){ $sess=$s; break } }",
		"    if($sess -ne $null){",
		"      $p=Await ($sess.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])",
		"      $tl=$sess.GetTimelineProperties(); $pi=$sess.GetPlaybackInfo()",
		"      $out=@{ok=$true;title=$p.Title;artist=$p.Artist;album=$p.AlbumTitle;status=[string]$pi.PlaybackStatus;pos=[long]$tl.Position.TotalMilliseconds;end=[long]$tl.EndTime.TotalMilliseconds;start=[long]$tl.StartTime.TotalMilliseconds;upd=[long]$tl.LastUpdatedTime.ToUnixTimeMilliseconds()}",
		"    }",
		"  }catch{}",
		"  [Console]::Out.WriteLine(($out|ConvertTo-Json -Compress)); [Console]::Out.Flush()",
		"  Start-Sleep -Milliseconds 400",
		"}");

	public static synchronized void setActive(boolean on) {
		if (on == wanted) return;
		wanted = on;
		if (on && WINDOWS) {
			worker = new Thread(SpotifyService::loop, "prismatic-spotify");
			worker.setDaemon(true);
			worker.start();
		} else if (!on) {
			stopProc();
			hasTrack = false;
		}
	}

	public static boolean supported() {
		return WINDOWS;
	}

	private static void stopProc() {
		Process p = proc;
		if (p != null) p.destroyForcibly();
		proc = null;
	}

	private static void loop() {
		while (wanted) {
			try {
				String enc = Base64.getEncoder().encodeToString(SCRIPT.getBytes(StandardCharsets.UTF_16LE));
				ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
					"-ExecutionPolicy", "Bypass", "-EncodedCommand", enc);
				pb.redirectError(ProcessBuilder.Redirect.DISCARD);
				proc = pb.start();
				try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
					String line;
					while (wanted && (line = r.readLine()) != null) {
						handle(line.trim());
					}
				}
			} catch (Exception ignored) {
			}
			stopProc();
			if (!wanted) return;
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				return;
			}
		}
	}

	private static void handle(String json) {
		if (json.isEmpty() || json.charAt(0) != '{') return;
		try {
			JsonObject o = JsonParser.parseString(json).getAsJsonObject();
			if (!o.has("ok") || !o.get("ok").getAsBoolean()) {
				hasTrack = false;
				return;
			}
			String t = str(o, "title"), a = str(o, "artist");
			long now = System.currentTimeMillis();
			long pos = o.get("pos").getAsLong(), upd = o.get("upd").getAsLong();
			if (pos != basePos || upd != baseUpd) baseRecv = now;
			basePos = pos;
			baseUpd = upd;
			long end = o.get("end").getAsLong(), start = o.get("start").getAsLong();
			durationMs = Math.max(0, end - start);
			playing = "Playing".equalsIgnoreCase(str(o, "status"));
			title = t;
			artist = a;
			album = str(o, "album");
			hasTrack = !t.isEmpty();
			String key = a + "|" + t;
			if (hasTrack && !key.equals(lastKey)) {
				lastKey = key;
				fetchLyrics(key, t, a, album, durationMs);
			}
		} catch (Exception ignored) {
		}
	}

	private static String str(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e == null || e.isJsonNull() ? "" : e.getAsString();
	}

	public static long positionMs() {
		long now = System.currentTimeMillis();
		long p = basePos;
		if (playing) {
			long since = (baseUpd > 0 && Math.abs(now - baseUpd) < 3_600_000L) ? now - baseUpd : now - baseRecv;
			p += Math.max(0, since);
		}
		long max = durationMs > 0 ? durationMs : Long.MAX_VALUE;
		return Math.min(Math.max(p, 0), max);
	}

	// ---- lyrics ------------------------------------------------------
	private static void fetchLyrics(String key, String t, String a, String al, long dur) {
		lyrics = List.of();
		List<Line> cached;
		synchronized (CACHE) {
			cached = CACHE.get(key);
		}
		if (cached != null) {
			lyrics = cached;
			lyricsState = cached.isEmpty() ? "none" : "found";
			return;
		}
		lyricsState = "loading";
		Thread th = new Thread(() -> {
			List<Line> result = List.of();
			try {
				String q = "track_name=" + enc(t) + "&artist_name=" + enc(a);
				String lrc = null;
				String body = get("https://lrclib.net/api/get?" + q + (dur > 0 ? "&duration=" + dur / 1000 : ""));
				if (body != null) lrc = syncedOf(JsonParser.parseString(body).getAsJsonObject());
				if (lrc == null) {
					String s = get("https://lrclib.net/api/search?" + q);
					if (s != null) {
						JsonArray arr = JsonParser.parseString(s).getAsJsonArray();
						List<JsonObject> hits = new ArrayList<>();
						for (JsonElement e : arr) {
							JsonObject o = e.getAsJsonObject();
							if (syncedOf(o) != null) hits.add(o);
						}
						hits.sort(Comparator.comparingDouble(o -> Math.abs(dbl(o, "duration") * 1000 - dur)));
						if (!hits.isEmpty()) lrc = syncedOf(hits.get(0));
					}
				}
				if (lrc != null) result = parse(lrc);
			} catch (Exception ignored) {
			}
			synchronized (CACHE) {
				CACHE.put(key, result);
			}
			if (key.equals(lastKey)) {
				lyrics = result;
				lyricsState = result.isEmpty() ? "none" : "found";
			}
		}, "prismatic-lyrics");
		th.setDaemon(true);
		th.start();
	}

	private static double dbl(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e == null || e.isJsonNull() ? 0 : e.getAsDouble();
	}

	private static String syncedOf(JsonObject o) {
		JsonElement e = o.get("syncedLyrics");
		if (e == null || e.isJsonNull()) return null;
		String s = e.getAsString();
		return s.isBlank() ? null : s;
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	private static String get(String url) throws Exception {
		HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
			.header("User-Agent", "PrismaticClient/1.0 (Minecraft Fabric mod)").GET().build();
		HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		return res.statusCode() == 200 ? res.body() : null;
	}

	private static final Pattern TS = Pattern.compile("\\[(\\d+):(\\d+(?:\\.\\d+)?)]");

	static List<Line> parse(String lrc) {
		List<Line> out = new ArrayList<>();
		for (String raw : lrc.split("\\r?\\n")) {
			Matcher m = TS.matcher(raw);
			List<Long> times = new ArrayList<>();
			int end = 0;
			while (m.find()) {
				times.add((long) (Integer.parseInt(m.group(1)) * 60_000L + Double.parseDouble(m.group(2)) * 1000));
				end = m.end();
			}
			if (times.isEmpty()) continue;
			String text = raw.substring(end).trim();
			for (long t : times) out.add(new Line(t, text));
		}
		out.sort(Comparator.comparingLong(l -> l.t));
		return out;
	}

	private SpotifyService() {
	}
}
