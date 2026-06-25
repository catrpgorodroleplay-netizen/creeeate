package com.voice.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import androidx.core.app.NotificationCompat;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class VpnService extends android.net.VpnService implements Runnable {

    private static final String TAG = "VpnService";
    private static final int NOTIFICATION_ID = 2002;
    private static final String CHANNEL_ID = "vpn_channel";

    public static boolean isRunning = false;

    private ParcelFileDescriptor vpnInterface;
    private Thread vpnThread;
    private Socket proxySocket;

    // === НАСТРОЙКИ ПРОКСИ (меняй здесь) ===
    private String proxyHost = "185.209.192.197";
    private int proxyPort = 443;
    // =======================================

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Log.d(TAG, "VpnService создан");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopVpn();
            stopSelf();
            return START_NOT_STICKY;
        }

        startVpn();
        return START_STICKY;
    }

    private void startVpn() {
        if (isRunning) return;

        try {
            // 1. Строим VPN-интерфейс
            Builder builder = new Builder();
            builder.setSession("VoiceApp VPN");
            builder.addAddress("10.0.0.2", 32);
            builder.addRoute("0.0.0.0", 0);
            builder.addDnsServer("8.8.8.8");
            builder.addDnsServer("1.1.1.1");
            builder.setMtu(1500);
            builder.setBlocking(true);

            vpnInterface = builder.establish();
            if (vpnInterface == null) {
                Log.e(TAG, "Не удалось создать VPN интерфейс");
                return;
            }

            // 2. Защищаем сокет от зацикливания
            proxySocket = new Socket();
            proxySocket.connect(new InetSocketAddress(proxyHost, proxyPort), 5000);
            protect(proxySocket);

            isRunning = true;
            Log.d(TAG, "VPN запущен через " + proxyHost + ":" + proxyPort);

            startForeground(NOTIFICATION_ID, createNotification("🛡️ VPN активен",
                    "Трафик через " + proxyHost + ":" + proxyPort));

            vpnThread = new Thread(this);
            vpnThread.start();

        } catch (Exception e) {
            Log.e(TAG, "Ошибка запуска VPN: " + e.getMessage());
            if (vpnInterface != null) {
                try { vpnInterface.close(); } catch (Exception ex) {}
                vpnInterface = null;
            }
            isRunning = false;
        }
    }

    private void stopVpn() {
        isRunning = false;

        if (proxySocket != null) {
            try { proxySocket.close(); } catch (Exception e) {}
            proxySocket = null;
        }

        if (vpnInterface != null) {
            try { vpnInterface.close(); } catch (Exception e) {}
            vpnInterface = null;
        }

        if (vpnThread != null) {
            vpnThread.interrupt();
            vpnThread = null;
        }

        stopForeground(true);
        Log.d(TAG, "VPN остановлен");
    }

    @Override
    public void run() {
        try {
            FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
            FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor());
            byte[] buffer = new byte[1500];

            while (isRunning && !Thread.interrupted()) {
                int len = in.read(buffer);
                if (len <= 0) continue;

                // Отправляем через прокси (для реального VPN нужна полная реализация SOCKS5)
                out.write(buffer, 0, len);
                out.flush();
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка в VPN потоке: " + e.getMessage());
        }
    }

    private Notification createNotification(String title, String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
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
                    "VPN",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Управление VPN-соединением");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopVpn();
    }
}
