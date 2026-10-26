package com.customcrosshair;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class CustomCrosshairMod implements ClientModInitializer {
	public static final String MOD_ID = "customcrosshair";

	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

	/** "`" (grave accent) by default; can be rebound in Options > Controls. */
	public static KeyMapping openKey;

	@Override
	public void onInitializeClient() {
		CrosshairConfig.get();
		openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key." + MOD_ID + ".open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.consumeClick()) {
				if (client.screen == null && client.level != null) {
					client.setScreen(new CrosshairSettingsScreen());
				}
			}
		});
	}

	/** Turns the crosshair on/off, with the optional toggle message. */
	public static void setEnabled(boolean value) {
		CrosshairConfig config = CrosshairConfig.get();
		if (config.enabled == value) return;
		config.enabled = value;
		CrosshairRenderer.resetMotion();
		config.save();
		Minecraft mc = Minecraft.getInstance();
		if (config.toggleMessage && mc.player != null) {
			Component state = Component.literal(value ? "enabled" : "disabled")
					.withStyle(value ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED);
			mc.player.displayClientMessage(Component.literal("Custom Crosshair ").append(state), true);
		}
	}
}
