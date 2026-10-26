package com.customcrosshair;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** Draws the crosshair. Geometry and animation follow Anubis's Custom Crosshair module. */
public final class CrosshairRenderer {
	private static final int OUTLINE = 0x8C000000;
	private static final int TRACK_ARGB = 0xCC16181D;
	private static final int ACCENT = 0xFF3478F6;
	private static final float DIAGONAL = 0.70710677F;
	private static final double FULL_SPREAD_SPEED = 0.28;
	private static final float TINT_RATE = 14.0F;
	private static final float SPREAD_RATE = 12.0F;
	private static final float FADE_RATE = 18.0F;

	private static float tint;
	private static float extraGap;
	private static float barAlpha;
	private static float barFill = 1.0F;

	private CrosshairRenderer() {
	}

	public static void resetMotion() {
		tint = 0.0F;
		extraGap = 0.0F;
		barAlpha = 0.0F;
		barFill = 1.0F;
	}

	private record Geometry(float cx, float cy, float size, float open, float thick, float below) {
	}

	public static void render(GuiGraphics g, float dt) {
		Minecraft mc = Minecraft.getInstance();
		CrosshairConfig cfg = CrosshairConfig.get();
		drawHudLabel(g, mc, cfg);

		LocalPlayer player = mc.player;
		if (player == null || !isVisible(mc)) return;

		Window window = mc.getWindow();
		double guiScale = window.getGuiScale();
		float screenW = window.getWidth();
		float screenH = window.getHeight();
		float scale = Math.max(1.0F, screenH / 1080.0F);

		updateMotion(mc, player, cfg, dt);
		Geometry geo = geometry(cfg, screenW * 0.5F, screenH * 0.5F, scale);
		int fill = currentColor(cfg);
		float fillAlpha = (float) (fill >>> 24 & 255) / 255.0F;
		float edge = cfg.outline ? Math.max(1.0F, (float) Math.round(scale)) : 0.0F;

		// Draw in real framebuffer pixels so sizes are independent of the GUI scale setting.
		g.pose().pushMatrix();
		try {
			float inv = (float) (1.0 / guiScale);
			g.pose().scale(inv, inv);
			if (edge > 0.0F) {
				drawShape(g, cfg, geo, withAlpha(OUTLINE, fillAlpha), edge);
			}
			drawShape(g, cfg, geo, fill, 0.0F);
			drawCooldown(g, mc, player, geo, fill, edge, scale, dt);
		} finally {
			g.pose().popMatrix();
		}
	}

	private static void drawHudLabel(GuiGraphics g, Minecraft mc, CrosshairConfig cfg) {
		if (!cfg.showInHud || mc.options.hideGui || mc.screen instanceof CrosshairSettingsScreen) return;
		String text = "Custom Crosshair";
		int w = mc.font.width(text);
		int x = g.guiWidth() - w - 8;
		g.fill(x - 4, 4, g.guiWidth() - 2, 16, 0x80000000);
		g.fill(g.guiWidth() - 4, 4, g.guiWidth() - 2, 16, ACCENT);
		g.drawString(mc.font, text, x, 6, 0xFFF2F4F8, false);
	}

	private static boolean isVisible(Minecraft mc) {
		if (mc.level == null || mc.gameMode == null || mc.options.hideGui) return false;
		if (mc.screen != null && !(mc.screen instanceof ChatScreen) && !(mc.screen instanceof CrosshairSettingsScreen)) {
			return false;
		}
		if (!mc.options.getCameraType().isFirstPerson()) return false;
		return mc.gameMode.getPlayerMode() != GameType.SPECTATOR || spectatorCanInteract(mc);
	}

	private static boolean spectatorCanInteract(Minecraft mc) {
		HitResult hit = mc.hitResult;
		if (hit instanceof EntityHitResult entityHit) {
			return entityHit.getEntity() instanceof MenuProvider;
		}
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = blockHit.getBlockPos();
			return mc.level.getBlockState(pos).getMenuProvider(mc.level, pos) != null;
		}
		return false;
	}

	private static void updateMotion(Minecraft mc, LocalPlayer player, CrosshairConfig cfg, float dt) {
		Entity picked = mc.crosshairPickEntity;
		boolean onTarget = cfg.targetTint && picked instanceof LivingEntity living && living.isAlive();
		tint = approach(tint, onTarget ? 1.0F : 0.0F, TINT_RATE, dt);

		float wanted = 0.0F;
		if (cfg.dynamicGap) {
			double moved = Math.hypot(player.getX() - player.xo, player.getZ() - player.zo);
			float moving = (float) Math.min(1.0, moved / FULL_SPREAD_SPEED);
			float swing = 1.0F - player.getAttackStrengthScale(0.0F);
			wanted = cfg.spread * Math.max(moving, swing);
		}
		extraGap = approach(extraGap, wanted, SPREAD_RATE, dt);
	}

	private static float approach(float current, float target, float rate, float dt) {
		float next = current + (target - current) * (1.0F - (float) Math.exp(-rate * dt));
		return Math.abs(target - next) < 0.001F ? target : next;
	}

	public static int currentColor(CrosshairConfig cfg) {
		int base = cfg.colorMode.equals("Accent") ? ACCENT : cfg.color;
		return tint <= 0.0F ? base : lerpColor(base, cfg.targetColor, tint);
	}

	/** Used by the settings screen to preview the target tint without needing a target. */
	public static float tintForPreview() {
		return tint;
	}

	private static Geometry geometry(CrosshairConfig cfg, float cx, float cy, float scale) {
		float thick = Math.max(1.0F, (float) Math.round(cfg.thickness * scale));
		float length = Math.max(1.0F, (float) Math.round(cfg.size * scale));
		float baseGap = cfg.hasArms() ? cfg.gap : 0.0F;
		float open = (float) Math.round((baseGap + extraGap) * scale);
		float maxExtra = cfg.dynamicGap && !cfg.style.equals("Dot") ? cfg.spread : 0.0F;
		float below = reachBelow(cfg, length, (float) Math.round((baseGap + maxExtra) * scale), thick);
		return new Geometry(cx, cy, length, open, thick, below);
	}

	private static float reachBelow(CrosshairConfig cfg, float length, float open, float thick) {
		float below = switch (cfg.style) {
			case "Dot" -> length * 0.5F;
			case "Circle" -> length + open + thick * 0.5F;
			case "X" -> (open + length) * DIAGONAL + thick * 0.5F;
			default -> open + length + thick * 0.5F;
		};
		return cfg.centerDot ? Math.max(below, thick * 0.5F) : below;
	}

	private static boolean armsJoined(CrosshairConfig cfg, Geometry g) {
		return (cfg.style.equals("Cross") || cfg.style.equals("T")) && g.open() < 1.0F;
	}

	private static void drawShape(GuiGraphics gfx, CrosshairConfig cfg, Geometry g, int color, float grow) {
		switch (cfg.style) {
			case "Dot" -> disc(gfx, g.cx(), g.cy(), Math.max(1.0F, g.size() * 0.5F) + grow, 0.0F, color);
			case "Circle" -> {
				float r = g.size() + g.open();
				float half = g.thick() * 0.5F + grow;
				disc(gfx, g.cx(), g.cy(), r + half, Math.max(0.0F, r - half), color);
			}
			case "X" -> drawDiagonals(gfx, g, color, grow);
			default -> drawArms(gfx, g, color, grow, !cfg.style.equals("T"));
		}

		if (cfg.centerDot && !cfg.style.equals("Dot") && !armsJoined(cfg, g)) {
			float x0 = (float) Math.floor(g.cx() - g.thick() * 0.5F);
			float y0 = (float) Math.floor(g.cy() - g.thick() * 0.5F);
			rect(gfx, x0, y0, x0 + g.thick(), y0 + g.thick(), color, grow);
		}
	}

	private static void drawArms(GuiGraphics gfx, Geometry g, int color, float grow, boolean top) {
		float x0 = (float) Math.floor(g.cx() - g.thick() * 0.5F);
		float x1 = x0 + g.thick();
		float y0 = (float) Math.floor(g.cy() - g.thick() * 0.5F);
		float y1 = y0 + g.thick();
		float near = g.open();
		float far = g.open() + g.size();
		if (near < 1.0F) {
			rect(gfx, x0 - far, y0, x1 + far, y1, color, grow);
			rawFill(gfx, x0 - grow, y1 + grow, x1 + grow, y1 + far + grow, color);
			if (top) {
				rawFill(gfx, x0 - grow, y0 - far - grow, x1 + grow, y0 - grow, color);
			}
		} else {
			rect(gfx, x0 - far, y0, x0 - near, y1, color, grow);
			rect(gfx, x1 + near, y0, x1 + far, y1, color, grow);
			rect(gfx, x0, y1 + near, x1, y1 + far, color, grow);
			if (top) {
				rect(gfx, x0, y0 - far, x1, y0 - near, color, grow);
			}
		}
	}

	private static void drawDiagonals(GuiGraphics gfx, Geometry g, int color, float grow) {
		float near = Math.max(0.0F, g.open() - grow) * DIAGONAL;
		float far = (g.open() + g.size() + grow) * DIAGONAL;
		float halfWidth = (g.thick() + grow * 2.0F) * 0.5F / DIAGONAL;
		int rowStart = Math.round(near);
		int rowEnd = Math.max(rowStart + 1, Math.round(far));
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sy = -1; sy <= 1; sy += 2) {
				for (int row = rowStart; row < rowEnd; row++) {
					float centreX = g.cx() + sx * (row + 0.5F);
					int y = Math.round(g.cy() + (sy > 0 ? row : -row - 1));
					gfx.fill(Math.round(centreX - halfWidth), y, Math.round(centreX + halfWidth), y + 1, color);
				}
			}
		}
	}

	/** Filled ring (or disc when inner is 0) built from horizontal spans. */
	private static void disc(GuiGraphics gfx, float cx, float cy, float outer, float inner, int color) {
		int reach = (int) Math.ceil(outer);
		for (int y = -reach; y < reach; y++) {
			float dy = y + 0.5F;
			float outerSq = outer * outer - dy * dy;
			if (outerSq <= 0.0F) continue;
			float xo = (float) Math.sqrt(outerSq);
			int py = Math.round(cy + y);
			float innerSq = inner * inner - dy * dy;
			if (inner > 0.0F && innerSq > 0.0F) {
				float xi = (float) Math.sqrt(innerSq);
				gfx.fill(Math.round(cx - xo), py, Math.round(cx - xi), py + 1, color);
				gfx.fill(Math.round(cx + xi), py, Math.round(cx + xo), py + 1, color);
			} else {
				gfx.fill(Math.round(cx - xo), py, Math.round(cx + xo), py + 1, color);
			}
		}
	}

	private static void rect(GuiGraphics gfx, float x0, float y0, float x1, float y1, int color, float grow) {
		rawFill(gfx, x0 - grow, y0 - grow, x1 + grow, y1 + grow, color);
	}

	private static void rawFill(GuiGraphics gfx, float x0, float y0, float x1, float y1, int color) {
		gfx.fill(Math.round(x0), Math.round(y0), Math.round(x1), Math.round(y1), color);
	}

	private static void drawCooldown(GuiGraphics gfx, Minecraft mc, LocalPlayer player, Geometry g, int fill,
			float edge, float scale, float dt) {
		boolean wanted = mc.options.attackIndicator().get() == AttackIndicatorStatus.CROSSHAIR;
		float charge = player.getAttackStrengthScale(0.0F);
		boolean ready = wanted && readyOnTarget(mc, player, charge);
		barFill = ready ? 1.0F : Math.min(1.0F, charge);
		barAlpha = approach(barAlpha, !wanted || (!ready && !(charge < 1.0F)) ? 0.0F : 1.0F, FADE_RATE, dt);
		if (barAlpha < 0.01F) return;

		float w = Math.max(12.0F, 2.0F * (float) Math.round(10.0F * scale));
		float h = Math.max(2.0F, (float) Math.round(3.0F * scale));
		float x0 = (float) Math.floor(g.cx() - w * 0.5F);
		float y0 = (float) Math.round(g.cy() + g.below() + edge + 5.0F * scale);
		float alpha = barAlpha * (float) (fill >>> 24 & 255) / 255.0F;
		if (edge > 0.0F) {
			rawFill(gfx, x0 - edge, y0 - edge, x0 + w + edge, y0 + h + edge, withAlpha(OUTLINE, alpha));
		}
		rawFill(gfx, x0, y0, x0 + w, y0 + h, withAlpha(TRACK_ARGB, alpha));
		float filled = (float) Math.round(w * barFill);
		if (filled >= 1.0F) {
			rawFill(gfx, x0, y0, x0 + filled, y0 + h, withAlpha(fill | 0xFF000000, alpha));
		}
	}

	private static boolean readyOnTarget(Minecraft mc, LocalPlayer player, float charge) {
		if (charge < 1.0F) return false;
		Entity picked = mc.crosshairPickEntity;
		return picked instanceof LivingEntity target && target.isAlive()
				&& player.getCurrentItemAttackStrengthDelay() > 5.0F;
	}

	public static int withAlpha(int argb, float alpha) {
		int a = Math.round((float) (argb >>> 24 & 255) * Math.max(0.0F, Math.min(1.0F, alpha)));
		return a << 24 | argb & 0xFFFFFF;
	}

	public static int lerpColor(int c1, int c2, float t) {
		int a = (int) ((c1 >>> 24) + ((c2 >>> 24) - (c1 >>> 24)) * t);
		int r = (int) ((c1 >> 16 & 255) + ((c2 >> 16 & 255) - (c1 >> 16 & 255)) * t);
		int gr = (int) ((c1 >> 8 & 255) + ((c2 >> 8 & 255) - (c1 >> 8 & 255)) * t);
		int b = (int) ((c1 & 255) + ((c2 & 255) - (c1 & 255)) * t);
		return a << 24 | r << 16 | gr << 8 | b;
	}
}
