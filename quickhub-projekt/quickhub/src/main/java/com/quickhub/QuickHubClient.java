package com.quickhub;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class QuickHubClient implements ClientModInitializer {
    private static KeyBinding openKey;
    private static KeyBinding scanKey;

    @Override
    public void onInitializeClient() {
        Config.load();
        Store.load();
        Favorites.load();
        Quick.load();
        Payments.load();

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("quickhub", "main"));
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.quickhub.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, category));
        scanKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.quickhub.scan", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, category));

        ClientReceiveMessageEvents.GAME.register(Payments::onMessage);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.player != null && client.currentScreen == null) {
                    client.setScreen(new HubScreen());
                }
            }
            while (scanKey.wasPressed()) {
                if (client.player != null && client.currentScreen == null) {
                    OrderScanner.start();
                }
            }
            OrderScanner.tick(client);
        });
    }
}
