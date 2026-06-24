package com.voice.app;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalProxyServer {
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private boolean isRunning = false;
    private int port = 8080;

    public void start() {
        if (isRunning) return;
        isRunning = true;
        threadPool = Executors.newCachedThreadPool();

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                android.util.Log.d("Proxy", "Локальный прокси запущен на порту " + port);

                while (isRunning) {
                    Socket clientSocket = serverSocket.accept();
                    threadPool.execute(() -> handleClient(clientSocket));
                }
            } catch (IOException e) {
                android.util.Log.e("Proxy", "Ошибка прокси: " + e.getMessage());
            }
        }).start();
    }

    public void stop() {
        isRunning = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {}
        if (threadPool != null) threadPool.shutdownNow();
        android.util.Log.d("Proxy", "Прокси остановлен");
    }

    private void handleClient(Socket clientSocket) {
        try {
            InputStream clientIn = clientSocket.getInputStream();
            OutputStream clientOut = clientSocket.getOutputStream();

            // Читаем первый запрос от клиента
            byte[] buffer = new byte[8192];
            int bytesRead = clientIn.read(buffer);
            if (bytesRead <= 0) return;

            // Разбираем HTTP-запрос
            String request = new String(buffer, 0, bytesRead);
            String[] lines = request.split("\r\n");
            String[] parts = lines[0].split(" ");
            if (parts.length < 3) return;

            String method = parts[0];
            String urlStr = parts[1];

            // Формируем целевой URL
            String targetUrl;
            if (urlStr.startsWith("http://")) {
                targetUrl = urlStr;
            } else if (urlStr.startsWith("https://")) {
                targetUrl = urlStr;
            } else {
                targetUrl = "https://" + urlStr;
            }

            // Создаем соединение с целевым сервером
            URL url = new URL(targetUrl);
            String host = url.getHost();
            int port = url.getPort() != -1 ? url.getPort() : (url.getProtocol().equals("https") ? 443 : 80);

            Socket targetSocket = new Socket(host, port);
            OutputStream targetOut = targetSocket.getOutputStream();

            // Передаём запрос на целевой сервер
            targetOut.write(buffer, 0, bytesRead);
            targetOut.flush();

            // Передаём ответ клиенту
            byte[] responseBuffer = new byte[8192];
            InputStream targetIn = targetSocket.getInputStream();
            while (true) {
                int len = targetIn.read(responseBuffer);
                if (len <= 0) break;
                clientOut.write(responseBuffer, 0, len);
                clientOut.flush();
            }

            targetSocket.close();
            clientSocket.close();

        } catch (Exception e) {
            android.util.Log.e("Proxy", "Ошибка обработки клиента: " + e.getMessage());
        }
    }

    public boolean isRunning() {
        return isRunning;
    }
}
