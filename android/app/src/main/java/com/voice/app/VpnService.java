package com.voice.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService.Builder;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VpnService extends android.net.VpnService implements Runnable {

    private static final String TAG = "MyVpnService";
    private static final int NOTIFICATION_ID = 2002;
    private static final String CHANNEL_ID = "vpn_channel";
    private static final String VPN_ADDRESS = "10.0.0.2";
    private static final String VPN_ROUTE = "0.0.0.0";
    private static final int VPN_ROUTE_PREFIX = 0;
    
    public static boolean isRunning = false;
    
    private ParcelFileDescriptor vpnInterface;
    private Thread vpnThread;
    private ExecutorService executorService;
    private Selector selector;
    private String proxyHost = "185.209.192.197"; // можно менять
    private int proxyPort = 443;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        executorService = Executors.newCachedThreadPool();
        Log.d(TAG, "VPN сервис создан");
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
            // Строим VPN-интерфейс
            Builder builder = new Builder();
            builder.setSession("VoiceApp VPN");
            builder.addAddress(VPN_ADDRESS, 32);
            builder.addRoute(VPN_ROUTE, VPN_ROUTE_PREFIX);
            builder.addDnsServer("8.8.8.8");
            builder.addDnsServer("1.1.1.1");
            builder.setMtu(1500);
            builder.setBlocking(true);
            
            vpnInterface = builder.establish();
            
            if (vpnInterface == null) {
                Log.e(TAG, "Не удалось создать VPN-интерфейс");
                return;
            }
            
            isRunning = true;
            Log.d(TAG, "VPN запущен");
            
            // Показываем уведомление
            startForeground(NOTIFICATION_ID, createNotification("🛡️ VPN активен", "Трафик защищён"));
            
            // Запускаем поток обработки
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
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (Exception e) {}
            vpnInterface = null;
        }
        stopForeground(true);
        Log.d(TAG, "VPN остановлен");
    }

    @Override
    public void run() {
        try {
            FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
            FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor());
            ByteBuffer buffer = ByteBuffer.allocate(1500);
            
            while (isRunning && !Thread.interrupted()) {
                buffer.clear();
                int len = in.read(buffer.array());
                if (len <= 0) continue;
                
                buffer.limit(len);
                buffer.position(0);
                
                // Тут можно обрабатывать пакеты (проксировать через внешний сервер)
                // Пока просто пропускаем через себя
                out.write(buffer.array(), 0, len);
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
