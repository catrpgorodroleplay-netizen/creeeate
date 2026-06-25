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

import com.hexjoker.tinyvpn.TinyVpn;

import java.net.InetSocketAddress;

public class VpnService extends android.net.VpnService implements Runnable {

    private static final String TAG = "VpnService";
    private static final int NOTIFICATION_ID = 2002;
    private static final String CHANNEL_ID = "vpn_channel";

    public static boolean isRunning = false;

    private ParcelFileDescriptor vpnInterface;
    private Thread vpnThread;
    private TinyVpn tinyVpn;

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
        if (isRunning) {
            Log.d(TAG, "VPN уже запущен");
            return;
        }

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

            // 2. Запускаем TinyVPN
            tinyVpn = new TinyVpn();
            tinyVpn.start(vpnInterface.getFileDescriptor(), 
                    InetSocketAddress.createUnresolved(proxyHost, proxyPort));

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

        if (tinyVpn != null) {
            try {
                tinyVpn.stop();
                tinyVpn = null;
            } catch (Exception e) {
                Log.e(TAG, "Ошибка остановки TinyVPN: " + e.getMessage());
            }
        }

        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (Exception e) {}
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
        while (isRunning) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            }
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
