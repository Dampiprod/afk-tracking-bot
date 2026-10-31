package com.nikita.afk;

import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("afk_tracking.txt");

    public static void save() {
        try {
            String data = AfkTrackingMod.TELEGRAM_TOKEN + "\n" + AfkTrackingMod.CHAT_ID;
            Files.writeString(CONFIG_PATH, data);
        } catch (Exception e) {}
    }

    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String[] lines = Files.readString(CONFIG_PATH).split("\n");
                if (lines.length >= 2) {
                    AfkTrackingMod.TELEGRAM_TOKEN = lines[0].trim();
                    AfkTrackingMod.CHAT_ID = lines[1].trim();
                }
            }
        } catch (Exception e) {}
    }
}