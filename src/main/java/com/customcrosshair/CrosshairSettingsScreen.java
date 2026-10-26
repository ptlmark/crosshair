package com.customcrosshair;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Settings panel that mirrors the Anubis module page: header with back button + master toggle,
 * a "Settings" section with "Reset all", and one row per setting.
 */
public final class CrosshairSettingsScreen extends Screen {
	// Palette sampled from the Anubis dark card.
	private static final int CARD = 0xFF1B1C23;
	private static final int CARD_EDGE = 0xFF2B2D37;
	private static final int FIELD = 0xFF2A2C36;
	private static final int FIELD_HOVER = 0xFF343743;
	private static final int DIVIDER = 0x14FFFFFF;
	private static final int TEXT = 0xFFF2F4F8;
	private static final int MUTED = 0xFF9AA0AD;
	private static final int SWITCH_OFF = 0xFF3A3F4A;
	private static final int KNOB = 0xFFF5F6F8;
	private static final int ACCENT = 0xFF3478F6;

	private static final int PANEL_W = 300;
	private static final int PAD = 14;
	private static final int TITLE_H = 54;
	private static final int ROW_H = 28;
	private static final int SLIDER_H = 40;

	private final CrosshairConfig cfg = CrosshairConfig.get();
	private final CrosshairConfig defaults = new CrosshairConfig();
	private final List<Row> rows = new ArrayList<>();

	private int px, py, pw, ph;
	private float scroll;
	private float contentHeight;

	private SliderRow draggingSlider;
	private DropdownRow openDropdown;
	private ColorRow openColor;
	private int pickerX, pickerY;
	private float hue, sat, val, alpha;
	private int pickerDrag; // 0 none, 1 SV square, 2 hue, 3 alpha
	private boolean draggingScrollbar;

	public CrosshairSettingsScreen() {
		super(Component.literal("Custom Crosshair"));
	}

	@Override
	protected void init() {
		rows.clear();
		rows.add(new ToggleRow("Show in HUD", () -> cfg.showInHud, v -> cfg.showInHud = v, defaults.showInHud));
		rows.add(new ToggleRow("Toggle Message", () -> cfg.toggleMessage, v -> cfg.toggleMessage = v, defaults.toggleMessage));
		rows.add(new DropdownRow("Style", CrosshairConfig.STYLES, () -> cfg.style, v -> cfg.style = v, defaults.style));
		rows.add(new SliderRow("Size", 1, 20, 1, () -> cfg.size, v -> cfg.size = v, defaults.size));
		rows.add(new SliderRow("Gap", 0, 16, 1, () -> cfg.gap, v -> cfg.gap = v, defaults.gap).visibleWhen(cfg::hasArms));
		rows.add(new SliderRow("Thickness", 1, 5, 1, () -> cfg.thickness, v -> cfg.thickness = v, defaults.thickness));
		rows.add(new ToggleRow("Center Dot", () -> cfg.centerDot, v -> cfg.centerDot = v, defaults.centerDot)
				.visibleWhen(() -> !cfg.style.equals("Dot")));
		rows.add(new ToggleRow("Outline", () -> cfg.outline, v -> cfg.outline = v, defaults.outline));
		rows.add(new DropdownRow("Color", CrosshairConfig.COLOR_MODES, () -> cfg.colorMode, v -> cfg.colorMode = v, defaults.colorMode));
		rows.add(new ColorRow("Custom Color", () -> cfg.color, v -> cfg.color = v, defaults.color)
				.visibleWhen(() -> cfg.colorMode.equals("Custom")));
		rows.add(new ToggleRow("Target Tint", () -> cfg.targetTint, v -> cfg.targetTint = v, defaults.targetTint));
		rows.add(new ColorRow("Target Color", () -> cfg.targetColor, v -> cfg.targetColor = v, defaults.targetColor)
				.visibleWhen(() -> cfg.targetTint));
		rows.add(new ToggleRow("Dynamic Gap", () -> cfg.dynamicGap, v -> cfg.dynamicGap = v, defaults.dynamicGap));
		rows.add(new SliderRow("Spread", 1, 12, 1, () -> cfg.spread, v -> cfg.spread = v, defaults.spread)
				.visibleWhen(() -> cfg.dynamicGap && !cfg.style.equals("Dot")));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		cfg.save();
		super.onClose();
	}

	// ------------------------------------------------------------------ rendering

	@Override
	public void render(GuiGraphics g, int mx, int my, float partialTick) {
		// Intentionally no vanilla background: the live crosshair stays visible in the centre.
		pw = PANEL_W;
		ph = Math.min(height - 24, 470);
		px = width - pw - 24;
		py = (height - ph) / 2;

		rounded(g, px - 1, py - 1, px + pw + 1, py + ph + 1, 13, CARD_EDGE);
		rounded(g, px, py, px + pw, py + ph, 12, CARD);

		renderTitleBar(g, mx, my);

		int bodyTop = py + TITLE_H;
		int bodyBottom = py + ph - 8;
		g.enableScissor(px, bodyTop, px + pw, bodyBottom);
		float y = bodyTop - scroll;
		y += renderIntro(g, mx, my, (int) y);
		for (Row row : rows) {
			if (!row.visible()) continue;
			row.x = px + PAD;
			row.w = pw - PAD * 2 - 6;
			row.y = (int) y;
			int h = row.height();
			if (row.y + h >= bodyTop && row.y <= bodyBottom) {
				row.render(g, mx, my);
			}
			y += h;
		}
		contentHeight = y + scroll - bodyTop + 6;
		g.disableScissor();
		float maxScroll = Math.max(0.0F, contentHeight - (bodyBottom - bodyTop));
		scroll = Math.max(0.0F, Math.min(maxScroll, scroll));
		if (maxScroll > 0.0F) {
			renderScrollbar(g, bodyTop, bodyBottom, maxScroll);
		}

		if (openDropdown != null) renderDropdownPopup(g, mx, my);
		if (openColor != null) renderColorPicker(g, mx, my);
	}

	private void renderTitleBar(GuiGraphics g, int mx, int my) {
		int bx = px + PAD, by = py + 14;
		boolean backHover = inside(mx, my, bx, by, 24, 24);
		rounded(g, bx, by, bx + 24, by + 24, 12, backHover ? FIELD_HOVER : FIELD);
		g.drawString(font, "<", bx + 9, by + 8, TEXT, false);

		g.pose().pushMatrix();
		g.pose().translate(px + PAD + 34, py + 19);
		g.pose().scale(1.25F, 1.25F);
		g.drawString(font, "Custom Crosshair", 0, 0, TEXT, false);
		g.pose().popMatrix();

		drawSwitch(g, px + pw - PAD - 34, py + 17, cfg.enabled);
	}

	private int renderIntro(GuiGraphics g, int mx, int my, int y) {
		int startY = y;
		for (String line : wrap("Draws a configurable crosshair in place of the vanilla one.", pw - PAD * 2)) {
			g.drawString(font, line, px + PAD, y + 2, MUTED, false);
			y += font.lineHeight + 2;
		}
		y += 12;
		g.drawString(font, "Settings", px + PAD, y + 5, MUTED, false);
		String reset = "Reset all";
		int rw = font.width(reset) + 14;
		int rx = px + pw - PAD - 6 - rw;
		boolean hover = inside(mx, my, rx, y, rw, 18);
		rounded(g, rx, y, rx + rw, y + 18, 5, hover ? FIELD_HOVER : FIELD);
		g.drawString(font, reset, rx + 7, y + 5, TEXT, false);
		resetBox[0] = rx; resetBox[1] = y; resetBox[2] = rw; resetBox[3] = 18;
		y += 26;
		return y - startY;
	}

	private final int[] resetBox = new int[4];

	private void renderScrollbar(GuiGraphics g, int top, int bottom, float maxScroll) {
		int trackH = bottom - top;
		float viewRatio = trackH / (trackH + maxScroll);
		int thumbH = Math.max(24, (int) (trackH * viewRatio));
		int thumbY = top + (int) ((trackH - thumbH) * (scroll / maxScroll));
		int x = px + pw - 8;
		g.fill(x, top, x + 3, bottom, 0x14FFFFFF);
		rounded(g, x, thumbY, x + 3, thumbY + thumbH, 1, 0x66FFFFFF);
	}

	// ------------------------------------------------------------------ popups

	private void renderDropdownPopup(GuiGraphics g, int mx, int my) {
		DropdownRow d = openDropdown;
		int x = d.buttonX(), w = DropdownRow.BUTTON_W;
		int h = d.options.length * 22 + 8;
		int y = d.buttonY() + 24;
		if (y + h > height - 4) y = d.buttonY() - h - 4;
		rounded(g, x - 1, y - 1, x + w + 1, y + h + 1, 7, CARD_EDGE);
		rounded(g, x, y, x + w, y + h, 6, 0xFF22242D);
		for (int i = 0; i < d.options.length; i++) {
			int oy = y + 4 + i * 22;
			boolean hover = inside(mx, my, x + 3, oy, w - 6, 22);
			boolean selected = d.options[i].equals(d.getter.get());
			if (hover) rounded(g, x + 3, oy, x + w - 3, oy + 22, 4, FIELD_HOVER);
			g.drawString(font, d.options[i], x + 10, oy + 7, selected ? ACCENT : TEXT, false);
		}
	}

	private int[] dropdownBounds() {
		DropdownRow d = openDropdown;
		int x = d.buttonX();
		int h = d.options.length * 22 + 8;
		int y = d.buttonY() + 24;
		if (y + h > height - 4) y = d.buttonY() - h - 4;
		return new int[] {x, y, DropdownRow.BUTTON_W, h};
	}

	private static final int PICK_W = 160, PICK_SV_H = 96, PICK_BAR_H = 12;

	private void renderColorPicker(GuiGraphics g, int mx, int my) {
		int x = pickerX, y = pickerY;
		int totalH = 10 + PICK_SV_H + 8 + PICK_BAR_H + 8 + PICK_BAR_H + 10 + 14 + 10;
		rounded(g, x - 1, y - 1, x + PICK_W + 21, y + totalH + 1, 8, CARD_EDGE);
		rounded(g, x, y, x + PICK_W + 20, y + totalH, 7, 0xFF22242D);
		int ix = x + 10, iy = y + 10;

		// Saturation/value square: one vertical gradient per 2px column.
		for (int cx = 0; cx < PICK_W; cx += 2) {
			float s = cx / (float) (PICK_W - 1);
			int top = 0xFF000000 | hsv(hue, s, 1.0F);
			g.fillGradient(ix + cx, iy, ix + Math.min(PICK_W, cx + 2), iy + PICK_SV_H, top, 0xFF000000);
		}
		int mxs = ix + Math.round(sat * (PICK_W - 1));
		int mys = iy + Math.round((1.0F - val) * (PICK_SV_H - 1));
		g.fill(mxs - 3, mys - 3, mxs + 4, mys + 4, 0xFF000000);
		g.fill(mxs - 2, mys - 2, mxs + 3, mys + 3, 0xFFFFFFFF);
		g.fill(mxs - 1, mys - 1, mxs + 2, mys + 2, 0xFF000000 | hsv(hue, sat, val));

		// Hue bar.
		int hy = iy + PICK_SV_H + 8;
		for (int cx = 0; cx < PICK_W; cx += 2) {
			g.fill(ix + cx, hy, ix + Math.min(PICK_W, cx + 2), hy + PICK_BAR_H, 0xFF000000 | hsv(cx / (float) PICK_W, 1.0F, 1.0F));
		}
		int hx = ix + Math.round(hue * (PICK_W - 1));
		g.fill(hx - 1, hy - 2, hx + 2, hy + PICK_BAR_H + 2, 0xFFFFFFFF);

		// Alpha bar.
		int ay = hy + PICK_BAR_H + 8;
		g.fill(ix, ay, ix + PICK_W, ay + PICK_BAR_H, 0xFF14151A);
		int rgb = hsv(hue, sat, val);
		for (int cx = 0; cx < PICK_W; cx += 2) {
			int a = Math.round(255.0F * cx / (PICK_W - 1));
			g.fill(ix + cx, ay, ix + Math.min(PICK_W, cx + 2), ay + PICK_BAR_H, a << 24 | rgb);
		}
		int axp = ix + Math.round(alpha * (PICK_W - 1));
		g.fill(axp - 1, ay - 2, axp + 2, ay + PICK_BAR_H + 2, 0xFFFFFFFF);

		int packed = packed();
		String hex = String.format(Locale.ROOT, "#%08X", (packed << 8 | packed >>> 24));
		g.fill(ix, ay + PICK_BAR_H + 10, ix + 14, ay + PICK_BAR_H + 24, 0xFF14151A);
		g.fill(ix + 1, ay + PICK_BAR_H + 11, ix + 13, ay + PICK_BAR_H + 23, packed);
		g.drawString(font, hex, ix + 20, ay + PICK_BAR_H + 13, MUTED, false);
	}

	private int[] pickerBounds() {
		int totalH = 10 + PICK_SV_H + 8 + PICK_BAR_H + 8 + PICK_BAR_H + 10 + 14 + 10;
		return new int[] {pickerX, pickerY, PICK_W + 20, totalH};
	}

	private int packed() {
		return (Math.round(alpha * 255.0F) << 24) | hsv(hue, sat, val);
	}

	private void applyPicker() {
		if (openColor != null) openColor.setter.accept(packed());
	}

	private void openPickerFor(ColorRow row) {
		openColor = row;
		int argb = row.getter.get();
		float r = (argb >> 16 & 255) / 255.0F, gg = (argb >> 8 & 255) / 255.0F, b = (argb & 255) / 255.0F;
		float max = Math.max(r, Math.max(gg, b)), min = Math.min(r, Math.min(gg, b));
		float d = max - min;
		float h;
		if (d == 0.0F) h = 0.0F;
		else if (max == r) h = (((gg - b) / d) % 6.0F) / 6.0F;
		else if (max == gg) h = ((b - r) / d + 2.0F) / 6.0F;
		else h = ((r - gg) / d + 4.0F) / 6.0F;
		if (h < 0.0F) h += 1.0F;
		hue = h;
		sat = max == 0.0F ? 0.0F : d / max;
		val = max;
		alpha = (argb >>> 24 & 255) / 255.0F;
		int totalH = 10 + PICK_SV_H + 8 + PICK_BAR_H + 8 + PICK_BAR_H + 10 + 14 + 10;
		pickerX = Math.max(4, Math.min(width - PICK_W - 24, row.x + row.w - PICK_W - 20));
		pickerY = row.y + ROW_H + 2;
		if (pickerY + totalH > height - 4) pickerY = Math.max(4, row.y - totalH - 2);
	}

	/** HSV -> packed 0xRRGGBB. */
	private static int hsv(float h, float s, float v) {
		h = (h - (float) Math.floor(h)) * 6.0F;
		int i = (int) Math.floor(h);
		float f = h - i;
		float p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
		float r, g, b;
		switch (i % 6) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}
		return Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x(), my = event.y();
		if (event.button() != 0) return super.mouseClicked(event, doubleClick);

		if (openDropdown != null) {
			int[] b = dropdownBounds();
			if (inside(mx, my, b[0], b[1], b[2], b[3])) {
				int idx = (int) ((my - b[1] - 4) / 22);
				if (idx >= 0 && idx < openDropdown.options.length) {
					openDropdown.setter.accept(openDropdown.options[idx]);
					cfg.save();
				}
			}
			openDropdown = null;
			return true;
		}
		if (openColor != null) {
			int[] b = pickerBounds();
			if (inside(mx, my, b[0], b[1], b[2], b[3])) {
				int ix = pickerX + 10, iy = pickerY + 10;
				int hy = iy + PICK_SV_H + 8, ay = hy + PICK_BAR_H + 8;
				if (inside(mx, my, ix, iy, PICK_W, PICK_SV_H)) pickerDrag = 1;
				else if (inside(mx, my, ix, hy - 2, PICK_W, PICK_BAR_H + 4)) pickerDrag = 2;
				else if (inside(mx, my, ix, ay - 2, PICK_W, PICK_BAR_H + 4)) pickerDrag = 3;
				dragPicker(mx, my);
				return true;
			}
			cfg.save();
			openColor = null;
			return true;
		}

		// Title bar.
		if (inside(mx, my, px + PAD, py + 14, 24, 24)) {
			onClose();
			return true;
		}
		if (inside(mx, my, px + pw - PAD - 34, py + 17, 34, 18)) {
			CustomCrosshairMod.setEnabled(!cfg.enabled);
			return true;
		}
		if (!inside(mx, my, px, py + TITLE_H, pw, ph - TITLE_H)) {
			return super.mouseClicked(event, doubleClick);
		}
		if (inside(mx, my, resetBox[0], resetBox[1], resetBox[2], resetBox[3])) {
			cfg.resetAll();
			CrosshairRenderer.resetMotion();
			cfg.save();
			return true;
		}
		for (Row row : rows) {
			if (row.visible() && inside(mx, my, row.x, row.y, row.w, row.height())) {
				row.click(mx, my);
				cfg.save();
				return true;
			}
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (pickerDrag != 0) {
			dragPicker(event.x(), event.y());
			return true;
		}
		if (draggingSlider != null) {
			draggingSlider.drag(event.x());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (pickerDrag != 0 || draggingSlider != null) {
			cfg.save();
		}
		pickerDrag = 0;
		draggingSlider = null;
		return super.mouseReleased(event);
	}

	private void dragPicker(double mx, double my) {
		int ix = pickerX + 10, iy = pickerY + 10;
		if (pickerDrag == 1) {
			sat = clamp01((float) ((mx - ix) / (PICK_W - 1)));
			val = 1.0F - clamp01((float) ((my - iy) / (PICK_SV_H - 1)));
		} else if (pickerDrag == 2) {
			hue = clamp01((float) ((mx - ix) / (PICK_W - 1)));
		} else if (pickerDrag == 3) {
			alpha = clamp01((float) ((mx - ix) / (PICK_W - 1)));
		} else {
			return;
		}
		applyPicker();
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		if (openDropdown != null || openColor != null) return true;
		scroll -= (float) (scrollY * 18.0);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (key == GLFW.GLFW_KEY_GRAVE_ACCENT) {
			onClose();
			return true;
		}
		if (key == GLFW.GLFW_KEY_ESCAPE && (openDropdown != null || openColor != null)) {
			openDropdown = null;
			openColor = null;
			return true;
		}
		return super.keyPressed(event);
	}

	// ------------------------------------------------------------------ rows

	private abstract class Row {
		final String label;
		int x, y, w;
		BooleanSupplier visible = () -> true;

		Row(String label) {
			this.label = label;
		}

		Row visibleWhen(BooleanSupplier supplier) {
			this.visible = supplier;
			return this;
		}

		boolean visible() {
			return visible.getAsBoolean();
		}

		int height() {
			return ROW_H;
		}

		abstract boolean modified();

		abstract void render(GuiGraphics g, int mx, int my);

		abstract void click(double mx, double my);

		/** Label with the "changed from default" dot on the left, like Anubis. */
		void drawLabel(GuiGraphics g, int ly) {
			if (modified()) {
				rounded(g, x - 9, ly + 3, x - 5, ly + 7, 2, ACCENT);
			}
			g.drawString(font, label, x, ly, TEXT, false);
		}

		void divider(GuiGraphics g) {
			g.fill(x, y + height() - 1, x + w, y + height(), DIVIDER);
		}
	}

	private final class ToggleRow extends Row {
		private final Supplier<Boolean> getter;
		private final Consumer<Boolean> setter;
		private final boolean def;

		ToggleRow(String label, Supplier<Boolean> getter, Consumer<Boolean> setter, boolean def) {
			super(label);
			this.getter = getter;
			this.setter = setter;
			this.def = def;
		}

		@Override
		boolean modified() {
			return getter.get() != def;
		}

		@Override
		void render(GuiGraphics g, int mx, int my) {
			drawLabel(g, y + 10);
			drawSwitch(g, x + w - 30, y + 6, getter.get());
			divider(g);
		}

		@Override
		void click(double mx, double my) {
			setter.accept(!getter.get());
			CrosshairRenderer.resetMotion();
		}
	}

	private final class DropdownRow extends Row {
		static final int BUTTON_W = 92;
		final String[] options;
		final Supplier<String> getter;
		final Consumer<String> setter;
		private final String def;

		DropdownRow(String label, String[] options, Supplier<String> getter, Consumer<String> setter, String def) {
			super(label);
			this.options = options;
			this.getter = getter;
			this.setter = setter;
			this.def = def;
		}

		int buttonX() {
			return x + w - BUTTON_W;
		}

		int buttonY() {
			return y + 3;
		}

		@Override
		boolean modified() {
			return !getter.get().equals(def);
		}

		@Override
		void render(GuiGraphics g, int mx, int my) {
			drawLabel(g, y + 10);
			boolean hover = inside(mx, my, buttonX(), buttonY(), BUTTON_W, 22);
			rounded(g, buttonX(), buttonY(), buttonX() + BUTTON_W, buttonY() + 22, 6, hover ? FIELD_HOVER : FIELD);
			g.drawString(font, getter.get(), buttonX() + 9, buttonY() + 7, TEXT, false);
			g.drawString(font, "v", buttonX() + BUTTON_W - 14, buttonY() + 7, MUTED, false);
			divider(g);
		}

		@Override
		void click(double mx, double my) {
			if (inside(mx, my, buttonX(), buttonY(), BUTTON_W, 22)) {
				openDropdown = this;
			}
		}
	}

	private final class SliderRow extends Row {
		private final float min, max, step, def;
		private final Supplier<Float> getter;
		private final Consumer<Float> setter;

		SliderRow(String label, float min, float max, float step, Supplier<Float> getter, Consumer<Float> setter, float def) {
			super(label);
			this.min = min;
			this.max = max;
			this.step = step;
			this.getter = getter;
			this.setter = setter;
			this.def = def;
		}

		@Override
		int height() {
			return SLIDER_H;
		}

		@Override
		boolean modified() {
			return getter.get() != def;
		}

		private int trackX() {
			return x;
		}

		private int trackW() {
			return w;
		}

		@Override
		void render(GuiGraphics g, int mx, int my) {
			drawLabel(g, y + 6);
			String value = Integer.toString(Math.round(getter.get()));
			int vw = font.width(value) + 12;
			rounded(g, x + w - vw, y + 3, x + w, y + 17, 5, FIELD);
			g.drawString(font, value, x + w - vw + 6, y + 6, MUTED, false);

			int ty = y + 27;
			float frac = (getter.get() - min) / (max - min);
			int kx = trackX() + Math.round(frac * trackW());
			rounded(g, trackX(), ty - 1, trackX() + trackW(), ty + 2, 1, SWITCH_OFF);
			if (kx > trackX()) rounded(g, trackX(), ty - 1, kx, ty + 2, 1, ACCENT);
			rounded(g, kx - 4, ty - 4, kx + 4, ty + 5, 4, KNOB);
		}

		@Override
		void click(double mx, double my) {
			draggingSlider = this;
			drag(mx);
		}

		void drag(double mx) {
			float frac = clamp01((float) ((mx - trackX()) / trackW()));
			float raw = min + frac * (max - min);
			float snapped = Math.round((raw - min) / step) * step + min;
			setter.accept(Math.max(min, Math.min(max, snapped)));
		}
	}

	private final class ColorRow extends Row {
		private final Supplier<Integer> getter;
		private final Consumer<Integer> setter;
		private final int def;

		ColorRow(String label, Supplier<Integer> getter, Consumer<Integer> setter, int def) {
			super(label);
			this.getter = getter;
			this.setter = setter;
			this.def = def;
		}

		@Override
		boolean modified() {
			return getter.get() != def;
		}

		@Override
		void render(GuiGraphics g, int mx, int my) {
			drawLabel(g, y + 10);
			int sx = x + w - 56, sy = y + 4;
			rounded(g, sx - 1, sy - 1, sx + 57, sy + 21, 6, CARD_EDGE);
			rounded(g, sx, sy, sx + 56, sy + 20, 5, 0xFF14151A);
			rounded(g, sx + 2, sy + 2, sx + 54, sy + 18, 4, 0xFF000000 | getter.get());
			divider(g);
		}

		@Override
		void click(double mx, double my) {
			if (inside(mx, my, x + w - 60, y + 2, 60, 24)) {
				openPickerFor(this);
			}
		}
	}

	// ------------------------------------------------------------------ helpers

	private void drawSwitch(GuiGraphics g, int x, int y, boolean on) {
		rounded(g, x, y, x + 30, y + 16, 8, on ? ACCENT : SWITCH_OFF);
		int kx = on ? x + 16 : x + 2;
		rounded(g, kx, y + 2, kx + 12, y + 14, 6, KNOB);
	}

	private List<String> wrap(String text, int maxWidth) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String trial = line.length() == 0 ? word : line + " " + word;
			if (font.width(trial) > maxWidth && line.length() > 0) {
				lines.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(trial);
			}
		}
		if (line.length() > 0) lines.add(line.toString());
		return lines;
	}

	private static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private static float clamp01(float v) {
		return Math.max(0.0F, Math.min(1.0F, v));
	}

	/** Filled rounded rectangle built from row spans (radius is clamped to half the shorter side). */
	private static void rounded(GuiGraphics g, int x0, int y0, int x1, int y1, int radius, int color) {
		int w = x1 - x0, h = y1 - y0;
		if (w <= 0 || h <= 0) return;
		int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
		if (r == 0) {
			g.fill(x0, y0, x1, y1, color);
			return;
		}
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			int inset = (int) Math.round(r - Math.sqrt(Math.max(0.0, r * (double) r - dy * dy)));
			g.fill(x0 + inset, y0 + i, x1 - inset, y0 + i + 1, color);
			g.fill(x0 + inset, y1 - i - 1, x1 - inset, y1 - i, color);
		}
		g.fill(x0, y0 + r, x1, y1 - r, color);
	}
}
