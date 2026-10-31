package com.nikita.afk;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class TelegramListener {
    private static long lastUpdateId = 0;
    private static boolean isRunning = false;

    // НАСТРОЙКИ ПРОКСИ (включи, если не работает без ВПНа)
    private static final boolean USE_PROXY = true; // Включи true
    private static final int PROXY_PORT = 40000; // Порт Hiddify (или 40000 для WARP)

    public static void start() {
        if (isRunning) return;
        isRunning = true;

        new Thread(() -> {
            System.out.println("👂 Слушатель Telegram запущен!");
            while (isRunning) {
                if (AfkTrackingMod.TELEGRAM_TOKEN.isEmpty()) {
                    try { Thread.sleep(3000); } catch (Exception e) {}
                    continue;
                }

                try {
                    String url = "https://api.telegram.org/bot" + AfkTrackingMod.TELEGRAM_TOKEN + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=10";

                    HttpClient.Builder builder = HttpClient.newBuilder();
                    if (USE_PROXY) {
                        builder.proxy(ProxySelector.of(new InetSocketAddress("127.0.0.1", PROXY_PORT)));
                    }

                    HttpClient client = builder.build();
                    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                    // Если Telegram вернул ошибку (например, неверный токен)
                    if (response.statusCode() != 200) {
                        System.out.println("❌ Ошибка ТГ (Код " + response.statusCode() + "): " + response.body());
                        Thread.sleep(5000);
                        continue;
                    }

                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    if (!json.has("result")) continue;

                    JsonArray updates = json.getAsJsonArray("result");
                    for (int i = 0; i < updates.size(); i++) {
                        JsonObject update = updates.get(i).getAsJsonObject();
                        lastUpdateId = update.get("update_id").getAsLong();

                        if (update.has("message")) {
                            String text = update.get("message").getAsJsonObject().get("text").getAsString();
                            System.out.println("📩 Получена команда из ТГ: " + text);

                            if (text.equals("/status")) {
                                TelegramSender.sendStatus();
                            } else if (text.equals("/screen")) {
                                TelegramSender.sendScreenshot();
                            } else if (text.equals("/inv")) {
                                TelegramSender.sendInventory();
                            } else if (text.equals("/spam on")) {
                                AfkTrackingMod.isSpamming = true;
                                AfkTrackingMod.lastSpamTime = 0;
                                TelegramSender.sendMessage("✅ Авто-спамер ВКЛЮЧЕН из ТГ.");
                            } else if (text.equals("/spam off")) {
                                AfkTrackingMod.isSpamming = false;
                                TelegramSender.sendMessage("🛑 Авто-спамер ВЫКЛЮЧЕН из ТГ.");
                            }
                        }
                    }
                } catch (Exception e) {
                    // Теперь мы ВИДИМ ошибку, если сеть не работает!
                    System.out.println("❌ Слушатель ТГ не может подключиться: " + e.getMessage());
                }
                try { Thread.sleep(2000); } catch (Exception e) {}
            }
        }).start();
    }
}