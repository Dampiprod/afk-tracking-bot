package com.nikita.afk;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class AfkTrackingMod implements ClientModInitializer {

    public static String TELEGRAM_TOKEN = "";
    public static String CHAT_ID = "";

    // Публичные переменные для управления спамом из Телеграма
    public static boolean isSpamming = false;
    public static long lastSpamTime = 0;

    @Override
    public void onInitializeClient() {
        ConfigManager.load(); // Загружаем токен при старте

        // Перехват чата (команды с \)
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (message.startsWith("\\")) {
                handleCommand(message.substring(1).trim());
                return false;
            }
            return true;
        });

        // Приветствие при заходе в мир
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.player != null && TELEGRAM_TOKEN.isEmpty()) {
                client.player.sendMessage(Text.literal("§c[TG] Впиши свой токен и ID: §e\\tg_setup <токен> <id>"), false);
            }
        });

        // Спамер (каждый тик)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (isSpamming && client.player != null) {
                if (System.currentTimeMillis() - lastSpamTime > 300000) { // 5 минут
                    TelegramSender.sendStatus();
                    TelegramSender.sendInventory();
                    lastSpamTime = System.currentTimeMillis();
                    client.player.sendMessage(Text.literal("§d[TG] Авто-отчет отправлен."), true);
                }
            }
        });

        TelegramListener.start(); // Запускаем слушателя ТГ
        System.out.println("✅ AFK Tracking загружен!");
    }

    private void handleCommand(String command) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        if (command.startsWith("tg_setup ")) {
            String[] args = command.split(" ");
            if (args.length == 3) {
                TELEGRAM_TOKEN = args[1];
                CHAT_ID = args[2];
                ConfigManager.save(); // Сохраняем навсегда
                client.player.sendMessage(Text.literal("§a[TG] Токен и ID сохранены в файл!"), false);
            }
        } else if (command.equals("tg_status")) {
            TelegramSender.sendStatus();
        } else if (command.equals("tg_inv")) {
            TelegramSender.sendInventory();
        } else if (command.equals("tg_spam on")) {
            isSpamming = true;
            lastSpamTime = 0;
            client.player.sendMessage(Text.literal("§a[TG] Спамер ВКЛЮЧЕН (каждые 5 мин)."), false);
        } else if (command.equals("tg_spam off")) {
            isSpamming = false;
            client.player.sendMessage(Text.literal("§c[TG] Спамер ВЫКЛЮЧЕН."), false);
        }
    }
}