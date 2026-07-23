package com.cr.arcade;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OverlayService extends Service {
    
    private static final String CHANNEL_ID = "overlay_service_channel";
    private static final int NOTIFICATION_ID = 1;
    
    // Для захвата экрана
    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private ExecutorService executorService;
    private Handler mainHandler;
    private boolean isCapturing = false;
    
    // Интервал сканирования (мс)
    private static final int SCAN_INTERVAL = 2000;
    private Handler scanHandler = new Handler(Looper.getMainLooper());
    private Runnable scanRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        
        // Инициализация для захвата экрана
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Проверяем, есть ли данные для захвата экрана
        if (intent != null && intent.getAction() != null) {
            if (intent.getAction().equals("START_CAPTURE")) {
                int resultCode = intent.getIntExtra("resultCode", 0);
                Intent data = intent.getParcelableExtra("data");
                if (resultCode != 0 && data != null) {
                    startScreenCapture(resultCode, data);
                }
            }
            if (intent.getAction().equals("STOP_CAPTURE")) {
                stopScreenCapture();
            }
        }
        
        // Создаем уведомление
        Notification notification = createNotification();
        startForeground(NOTIFICATION_ID, notification);
        
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopScreenCapture();
        stopForeground(true);
        if (executorService != null) {
            executorService.shutdown();
        }
        scanHandler.removeCallbacks(scanRunnable);
    }

    // ==================== УВЕДОМЛЕНИЕ ====================
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "CR Arcade Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Фоновая работа оверлея CR Arcade");
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 
                0, 
                notificationIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Кнопка остановки оверлея
        Intent stopIntent = new Intent(this, OverlayService.class);
        stopIntent.setAction("STOP_SERVICE");
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🎮 CR Arcade")
                .setContentText("Оверлей активен. Нажмите для открытия")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET);

        // Добавляем кнопку "Стоп" для Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            builder.addAction(
                    new NotificationCompat.Action.Builder(
                            android.R.drawable.ic_menu_close_clear_cancel,
                            "Стоп",
                            stopPendingIntent
                    ).build()
            );
        }

        return builder.build();
    }

    // ==================== ЗАХВАТ ЭКРАНА ====================
    
    public void startScreenCapture(int resultCode, Intent data) {
        if (isCapturing) return;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaProjection = projectionManager.getMediaProjection(resultCode, data);
            if (mediaProjection == null) return;
            
            WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            Display display = windowManager.getDefaultDisplay();
            DisplayMetrics metrics = new DisplayMetrics();
            display.getMetrics(metrics);
            
            int width = metrics.widthPixels;
            int height = metrics.heightPixels;
            int density = metrics.densityDpi;
            
            imageReader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2);
            
            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "ScreenCapture",
                    width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(),
                    null, null
            );
            
            isCapturing = true;
            startScanning();
            
            // Показываем тост
            mainHandler.post(() -> 
                Toast.makeText(this, "🔍 Захват экрана запущен", Toast.LENGTH_SHORT).show()
            );
        }
    }
    
    public void stopScreenCapture() {
        isCapturing = false;
        scanHandler.removeCallbacks(scanRunnable);
        
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        
        mainHandler.post(() -> 
            Toast.makeText(this, "⏹ Захват экрана остановлен", Toast.LENGTH_SHORT).show()
        );
    }
    
    private void startScanning() {
        scanRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isCapturing) return;
                captureFrame();
                scanHandler.postDelayed(this, SCAN_INTERVAL);
            }
        };
        scanHandler.post(scanRunnable);
    }
    
    private void captureFrame() {
        if (imageReader == null) return;
        
        Image image = imageReader.acquireLatestImage();
        if (image == null) return;
        
        // Обработка изображения в фоне
        executorService.execute(() -> {
            try {
                Image.Plane[] planes = image.getPlanes();
                ByteBuffer buffer = planes[0].getBuffer();
                
                // Здесь можно добавить распознавание текста
                // и поиск ников из базы
                
                int width = image.getWidth();
                int height = image.getHeight();
                
                // Отправляем результат в MainActivity
                Intent intent = new Intent("SCREEN_CAPTURE_RESULT");
                intent.putExtra("width", width);
                intent.putExtra("height", height);
                // Здесь можно добавить данные о найденных никах
                sendBroadcast(intent);
                
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                image.close();
            }
        });
    }
    
    public boolean isCapturing() {
        return isCapturing;
    }

    // ==================== УПРАВЛЕНИЕ СЕРВИСОМ ====================
    
    public static void startService(Context context) {
        Intent intent = new Intent(context, OverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }
    
    public static void stopService(Context context) {
        Intent intent = new Intent(context, OverlayService.class);
        context.stopService(intent);
    }
    
    public static void startScreenCapture(Context context, int resultCode, Intent data) {
        Intent intent = new Intent(context, OverlayService.class);
        intent.setAction("START_CAPTURE");
        intent.putExtra("resultCode", resultCode);
        intent.putExtra("data", data);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }
    
    public static void stopScreenCapture(Context context) {
        Intent intent = new Intent(context, OverlayService.class);
        intent.setAction("STOP_CAPTURE");
        context.startService(intent);
    }
}
