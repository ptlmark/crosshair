package com.customcrosshair.mixin;

import com.customcrosshair.CrosshairConfig;
import com.customcrosshair.CrosshairRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the vanilla crosshair with the custom one while the module is enabled. */
@Mixin(Gui.class)
public abstract class MixinGui {
	@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
	private void customcrosshair$replaceCrosshair(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		CrosshairConfig config = CrosshairConfig.get();
		if (!config.enabled) {
			return;
		}
		float dt = Math.min(Math.max(deltaTracker.getRealtimeDeltaTicks() / 20.0F, 0.0F), 0.1F);
		CrosshairRenderer.render(graphics, dt);
		ci.cancel();
	}
}
