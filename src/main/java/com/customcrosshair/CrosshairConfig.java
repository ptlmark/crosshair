package com.customcrosshair;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** All settings of the crosshair. Defaults match the Anubis module. */
public final class CrosshairConfig {
	public static final String[] STYLES = {"Cross", "T", "X", "Dot", "Circle"};
	public static final String[] COLOR_MODES = {"Custom", "Accent"};

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static CrosshairConfig instance;

	public boolean enabled = true;
	public boolean showInHud = true;
	public boolean toggleMessage = true;
	public String style = "Cross";
	public float size = 6.0F;
	public float gap = 2.0F;
	public float thickness = 2.0F;
	public boolean centerDot = false;
	public boolean outline = true;
	public String colorMode = "Custom";
	public int color = 0xFFF5F5F7;
	public boolean targetTint = false;
	public int targetColor = 0xFFFF453A;
	public boolean dynamicGap = false;
	public float spread = 4.0F;

	public static CrosshairConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("customcrosshair.json");
	}

	private static CrosshairConfig load() {
		Path path = file();
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				CrosshairConfig loaded = GSON.fromJson(reader, CrosshairConfig.class);
				if (loaded != null) {
					loaded.sanitize();
					return loaded;
				}
			} catch (Exception ignored) {
				// fall through to defaults
			}
		}
		return new CrosshairConfig();
	}

	public void save() {
		try (Writer writer = Files.newBufferedWriter(file())) {
			GSON.toJson(this, writer);
		} catch (IOException ignored) {
		}
	}

	private void sanitize() {
		if (!contains(STYLES, style)) style = "Cross";
		if (!contains(COLOR_MODES, colorMode)) colorMode = "Custom";
		size = Math.max(1.0F, Math.min(20.0F, size));
		gap = Math.max(0.0F, Math.min(16.0F, gap));
		thickness = Math.max(1.0F, Math.min(5.0F, thickness));
		spread = Math.max(1.0F, Math.min(12.0F, spread));
	}

	private static boolean contains(String[] values, String value) {
		for (String v : values) {
			if (v.equals(value)) return true;
		}
		return false;
	}

	/** Resets every setting except the on/off state (same as "Reset all" in Anubis). */
	public void resetAll() {
		CrosshairConfig d = new CrosshairConfig();
		showInHud = d.showInHud;
		toggleMessage = d.toggleMessage;
		style = d.style;
		size = d.size;
		gap = d.gap;
		thickness = d.thickness;
		centerDot = d.centerDot;
		outline = d.outline;
		colorMode = d.colorMode;
		color = d.color;
		targetTint = d.targetTint;
		targetColor = d.targetColor;
		dynamicGap = d.dynamicGap;
		spread = d.spread;
	}

	public boolean hasArms() {
		return style.equals("Cross") || style.equals("T") || style.equals("X");
	}
}
