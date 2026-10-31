package com.nikita.afk;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import java.io.File;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class TelegramSender {

    // === НАСТРОЙКИ ПРОКСИ ===
    private static final boolean USE_PROXY = true;
    private static final int PROXY_PORT = 40000; // Порт Hiddify (или 40000 для WARP)

    public static void sendStatus() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;

        String pos = String.format("X: %.1f, Y: %.1f, Z: %.1f", player.getX(), player.getY(), player.getZ());
        String text = String.format("📊 *Статус:*\n❤️ ХП: %.1f\n🍗 Голод: %d\n✨ Опыт: %d\n📍 %s",
                player.getHealth(), player.getHungerManager().getFoodLevel(), player.experienceLevel, pos);
        sendMessage(text);
    }

    public static void sendInventory() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;

        StringBuilder sb = new StringBuilder("🎒 *Инвентарь:*\n");
        for (int i = 0; i < player.getInventory().size(); i++) {
            var stack = player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                sb.append("- ").append(stack.getName().getString()).append(" x").append(stack.getCount()).append("\n");
            }
        }
        sendMessage(sb.toString());
    }

    public static void sendScreenshot() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || AfkTrackingMod.TELEGRAM_TOKEN.isEmpty()) return;

        System.out.println("📸 [TG] Начинаю делать скриншот...");

        try {
            File gameDir = client.runDirectory;
            File screenshotsDir = new File(gameDir, "screenshots");
            if (!screenshotsDir.exists()) screenshotsDir.mkdirs();

            File screenshotFile = new File(screenshotsDir, "tg_screenshot.png");
            if (screenshotFile.exists()) screenshotFile.delete();

            client.execute(() -> ScreenshotRecorder.saveScreenshot(gameDir, "tg_screenshot.png", client.getFramebuffer(), 1, text -> {}));

            int attempts = 0;
            while ((!screenshotFile.exists() || screenshotFile.length() == 0) && attempts < 50) {
                Thread.sleep(100);
                attempts++;
            }

            if (!screenshotFile.exists() || screenshotFile.length() == 0) {
                System.out.println("❌ [TG] Ошибка: Файл скриншота пустой или не создался!");
                return;
            }

            System.out.println("📤 [TG] Скриншот создан, отправляю в ТГ... Размер: " + screenshotFile.length() + " байт");

            // Формируем запрос Multipart/Form-Data вручную
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            byte[] fileBytes = Files.readAllBytes(screenshotFile.toPath());

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            String header1 = "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n" +
                    AfkTrackingMod.CHAT_ID + "\r\n" +
                    "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"photo\"; filename=\"tg_screenshot.png\"\r\n" +
                    "Content-Type: image/png\r\n\r\n";
            baos.write(header1.getBytes(StandardCharsets.UTF_8));
            baos.write(fileBytes);
            baos.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

            HttpClient.Builder builder = HttpClient.newBuilder();
            if (USE_PROXY) {
                builder.proxy(ProxySelector.of(new InetSocketAddress("127.0.0.1", PROXY_PORT)));
            }
            HttpClient httpClient = builder.build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.telegram.org/bot" + AfkTrackingMod.TELEGRAM_TOKEN + "/sendPhoto"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(baos.toByteArray()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("✅ [TG] Ответ от Telegram: " + response.statusCode());

        } catch (Exception e) {
            System.out.println("❌ [TG] Ошибка при скриншоте: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void sendMessage(String text) {
        if (AfkTrackingMod.TELEGRAM_TOKEN.isEmpty() || AfkTrackingMod.CHAT_ID.isEmpty()) return;

        new Thread(() -> {
            try {
                String url = "https://api.telegram.org/bot" + AfkTrackingMod.TELEGRAM_TOKEN + "/sendMessage";
                String json = String.format("{\"chat_id\":\"%s\",\"text\":\"%s\",\"parse_mode\":\"Markdown\"}",
                        AfkTrackingMod.CHAT_ID, text.replace("\n", "\\n").replace("\"", "\\\""));

                HttpClient.Builder builder = HttpClient.newBuilder();
                if (USE_PROXY) {
                    builder.proxy(ProxySelector.of(new InetSocketAddress("127.0.0.1", PROXY_PORT)));
                }

                HttpClient client = builder.build();
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json)).build();
                client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                System.out.println("❌ [TG] Ошибка отправки текста: " + e.getMessage());
            }
        }).start();
    }
}