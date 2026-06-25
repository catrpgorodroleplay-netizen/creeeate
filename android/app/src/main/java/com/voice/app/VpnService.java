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

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.InetSocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;

public class VpnService extends android.net.VpnService implements Runnable {

    private static final String TAG = "VpnService";
    private static final int NOTIFICATION_ID = 2002;
    private static final String CHANNEL_ID = "vpn_channel";

    public static boolean isRunning = false;

    private ParcelFileDescriptor vpnInterface;
    private Thread vpnThread;
    private DatagramChannel tunnel;
    private String proxyHost = "185.209.192.197";
    private int proxyPort = 443;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
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
            // 1. Строим VPN-интерфейс — весь трафик через нас
            Builder builder = new Builder();
            builder.setSession("VoiceApp VPN");
            builder.addAddress("10.0.0.2", 32);
            builder.addRoute("0.0.0.0", 0);  // ВЕСЬ трафик
            builder.addDnsServer("8.8.8.8");
            builder.addDnsServer("1.1.1.1");
            builder.setMtu(1500);
            builder.setBlocking(true);

            // Добавляем исключения для приложений (опционально)
            // builder.addDisallowedApplication("com.android.chrome");

            vpnInterface = builder.establish();
            if (vpnInterface == null) {
                Log.e(TAG, "Не удалось создать VPN интерфейс");
                return;
            }

            // 2. СОЗДАЁМ ТУННЕЛЬ К ПРОКСИ-СЕРВЕРУ
            tunnel = DatagramChannel.open();
            tunnel.connect(new InetSocketAddress(proxyHost, proxyPort));

            // 3. ЗАЩИЩАЕМ ТУННЕЛЬ — это САМОЕ ВАЖНОЕ!
            // Без этого пакеты будут зацикливаться
            boolean protectedSocket = protect(tunnel.socket());
            if (!protectedSocket) {
                Log.e(TAG, "Не удалось защитить сокет туннеля");
                tunnel.close();
                vpnInterface.close();
                return;
            }

            isRunning = true;
            Log.d(TAG, "VPN запущен, трафик через " + proxyHost + ":" + proxyPort);

            startForeground(NOTIFICATION_ID, createNotification("🛡️ VPN активен", "Трафик через прокси"));

            // 4. Запускаем обработку пакетов
            vpnThread = new Thread(this);
            vpnThread.start();

        } catch (Exception e) {
            Log.e(TAG, "Ошибка запуска VPN: " + e.getMessage());
        }
    }

    private void stopVpn() {
        isRunning = false;
        if (vpnThread != null) {
            vpnThread.interrupt();
            vpnThread = null;
        }
        if (tunnel != null) {
            try { tunnel.close(); } catch (Exception e) {}
            tunnel = null;
        }
        if (vpnInterface != null) {
            try { vpnInterface.close(); } catch (Exception e) {}
            vpnInterface = null;
        }
        stopForeground(true);
        Log.d(TAG, "VPN остановлен");
    }

    @Override
    public void run() {
        try {
            FileDescriptor fd = vpnInterface.getFileDescriptor();
            FileInputStream in = new FileInputStream(fd);
            FileOutputStream out = new FileOutputStream(fd);
            byte[] buffer = new byte[1500];

            while (isRunning && !Thread.interrupted()) {
                int len = in.read(buffer);
                if (len <= 0) continue;

                // Здесь нужно перенаправлять пакеты через tun2socks
                // Для простоты — просто пропускаем через туннель
                // В реальном проекте используй библиотеку tun2socks

                // Отправляем через защищённый сокет к прокси-серверу
                // (Это упрощённая версия, для полной реализации нужна tun2socks)
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
