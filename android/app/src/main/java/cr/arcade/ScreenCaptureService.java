package com.cr.arcade;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.core.app.NotificationCompat;

import java.nio.ByteBuffer;

public class ScreenCaptureService extends Service {

    private static final String TAG = "ScreenCaptureService";
    private static final String CHANNEL_ID = "screen_capture_channel";
    private static final int NOTIFICATION_ID = 1001;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    
    private int screenWidth;
    private int screenHeight;
    private int screenDensity;
    
    private boolean isCapturing = false;
    private ScreenCaptureListener listener;

    public interface ScreenCaptureListener {
        void onScreenCaptured(Bitmap bitmap);
        void onTouchDetected(int x, int y);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        
        createNotificationChannel();
        startForegroundService();

        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;
        
        backgroundThread = new HandlerThread("ScreenCapture");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void startForegroundService() {
        Notification notification = createNotification();
        
        // ДЛЯ ВСЕХ ВЕРСИЙ Android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8-9
            startForeground(NOTIFICATION_ID, notification);
        } else {
            // Android 7 и ниже
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Запись экрана",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Используется для записи макросов");
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("CR ARCADE")
                .setContentText("🔴 Запись макроса...")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    public void startCapture(MediaProjection mediaProjection, ScreenCaptureListener listener) {
        this.listener = listener;
        this.mediaProjection = mediaProjection;
        isCapturing = true;
        
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
        
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                if (!isCapturing) return;
                Image image = reader.acquireLatestImage();
                if (image != null) {
                    Bitmap bitmap = imageToBitmap(image);
                    image.close();
                    if (bitmap != null && listener != null) {
                        listener.onScreenCaptured(bitmap);
                        detectTouch(bitmap);
                    }
                }
            }
        }, backgroundHandler);
        
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenCapture",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null, null
        );
        
        Log.d(TAG, "✅ Screen capture started");
    }

    private Bitmap imageToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * screenWidth;
        
        Bitmap bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
        );
        bitmap.copyPixelsFromBuffer(buffer);
        
        return Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight);
    }

    private Bitmap lastBitmap = null;
    private long lastTouchTime = 0;

    private void detectTouch(Bitmap currentBitmap) {
        if (lastBitmap == null) {
            lastBitmap = currentBitmap.copy(currentBitmap.getConfig(), false);
            return;
        }
        
        int touchX = -1, touchY = -1;
        boolean touchDetected = false;
        
        int step = 30;
        for (int y = 0; y < screenHeight && !touchDetected; y += step) {
            for (int x = 0; x < screenWidth && !touchDetected; x += step) {
                int pixel1 = lastBitmap.getPixel(x, y);
                int pixel2 = currentBitmap.getPixel(x, y);
                if (pixel1 != pixel2) {
                    touchX = x;
                    touchY = y;
                    touchDetected = true;
                }
            }
        }
        
        long currentTime = System.currentTimeMillis();
        if (touchDetected && (currentTime - lastTouchTime > 300)) {
            lastTouchTime = currentTime;
            if (listener != null) {
                listener.onTouchDetected(touchX, touchY);
                Log.d(TAG, "👆 КЛИК ОБНАРУЖЕН: (" + touchX + ", " + touchY + ")");
            }
        }
        
        lastBitmap.recycle();
        lastBitmap = currentBitmap.copy(currentBitmap.getConfig(), false);
    }

    public void stopCapture() {
        isCapturing = false;
        
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        
        if (lastBitmap != null) {
            lastBitmap.recycle();
            lastBitmap = null;
        }
        
        Log.d(TAG, "⏹ Screen capture stopped");
        
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        stopCapture();
        if (backgroundThread != null) {
            backgroundThread.quit();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
                }
