package com.voice.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.littleshoot.proxy.impl.ThreadPoolConfiguration;

public class LittleProxyService extends Service {

    private static final String CHANNEL_ID = "proxy_channel";
    private static final int NOTIFICATION_ID = 3001;
    private static final String TAG = "LittleProxyService";

    public static boolean isRunning = false;
    private HttpProxyServer proxyServer;
    private static String proxyHost = "";
    private static int proxyPort = 0;
    private static String proxySecret = "";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Log.d(TAG, "Сервис создан");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;

        String action = intent.getAction();
        if ("START".equals(action)) {
            proxyHost = intent.getStringExtra("host");
            proxyPort = intent.getIntExtra("port", 0);
            proxySecret = intent.getStringExtra("secret");
            startProxyServer();
        } else if ("STOP".equals(action)) {
            stopProxyServer();
            stopSelf();
        }
        return START_STICKY;
    }

    private void startProxyServer() {
        if (isRunning) {
            Log.d(TAG, "Прокси уже запущен");
            return;
        }

        if (proxyHost.isEmpty() || proxyPort == 0) {
            Toast.makeText(this, "❌ Нет данных прокси", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            ThreadPoolConfiguration threadPoolConfig = new ThreadPoolConfiguration()
                    .withAcceptorThreads(1)
                    .withClientToProxyWorkerThreads(4)
                    .withProxyToServerWorkerThreads(4);

            proxyServer = DefaultHttpProxyServer.bootstrap()
                    .withPort(8080)
                    .withAllowLocalOnly(true)
                    .withThreadPoolConfiguration(threadPoolConfig)
                    .withListenOnAllAddresses(false)
                    .withTransparentProxy(true)
                    .start();

            isRunning = true;
            Log.d(TAG, "Прокси запущен на порту 8080");
            Toast.makeText(this, "🔒 Прокси запущен (localhost:8080)", Toast.LENGTH_SHORT).show();

            // Показываем уведомление
            startForeground(NOTIFICATION_ID, createNotification());

        } catch (Exception e) {
            Log.e(TAG, "Ошибка запуска прокси: " + e.getMessage());
            Toast.makeText(this, "❌ Ошибка прокси: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopProxyServer() {
        if (proxyServer != null) {
            try {
                proxyServer.abort();
                proxyServer = null;
            } catch (Exception e) {
                Log.e(TAG, "Ошибка остановки прокси: " + e.getMessage());
            }
        }
        isRunning = false;
        Log.d(TAG, "Прокси остановлен");
        Toast.makeText(this, "🔓 Прокси остановлен", Toast.LENGTH_SHORT).show();
        stopForeground(true);
    }

    private Notification createNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🛡️ Прокси активен")
                .setContentText("localhost:8080 → " + proxyHost + ":" + proxyPort)
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Прокси-сервер",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Управление прокси-сервером");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopProxyServer();
    }

    // Статический метод для получения настроек прокси
    public static ProxyData getProxyData() {
        if (isRunning && !proxyHost.isEmpty() && proxyPort > 0) {
            return new ProxyData(proxyHost, proxyPort, proxySecret);
        }
        return null;
    }

    public static class ProxyData {
        public String host;
        public int port;
        public String secret;

        public ProxyData(String host, int port, String secret) {
            this.host = host;
            this.port = port;
            this.secret = secret;
        }
    }
    }
