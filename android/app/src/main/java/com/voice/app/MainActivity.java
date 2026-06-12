package com.voice.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    private static final int REQUEST_MICROPHONE = 100;
    private static final int REQUEST_OVERLAY_PERMISSION = 101;
    private static final String CHANNEL_ID = "voice_channel";
    private static final int NOTIFICATION_ID = 1;
    
    private WindowManager windowManager;
    private ImageButton floatingCircle;
    private FrameLayout floatingWindow;
    private WindowManager.LayoutParams circleParams;
    private WindowManager.LayoutParams windowParams;
    private boolean isWindowVisible = false;
    private float startX, startY;
    private int initialX, initialY;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Запрос микрофона
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MICROPHONE);
        }

        // Запрос разрешения на плавающее окно
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
            } else {
                createFloatingCircle();
            }
        } else {
            createFloatingCircle();
        }
        
        // ЗАПУСКАЕМ FOREGROUND SERVICE — ЭТО ГЛАВНОЕ!
        startForegroundService();
        
        // Настройка WebView для микрофона
        if (bridge != null && bridge.getWebView() != null) {
            bridge.getWebView().setWebChromeClient(new WebChromeClient() {
                @Override
                public void onPermissionRequest(android.webkit.PermissionRequest request) {
                    request.grant(new String[]{android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                }
            });
        }
    }

    private void startForegroundService() {
        // Создаём канал уведомлений (для Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Голосовой чат",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Приложение использует микрофон для голосового чата");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
        
        // Intent для открытия приложения при нажатии на уведомление
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        // Создаём уведомление
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🎤 Голосовой чат активен")
                .setContentText("Микрофон работает в фоне")
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
        
        // Запускаем foreground service
        startForeground(NOTIFICATION_ID, notification);
    }

    private void createFloatingCircle() {
        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }
        
        floatingCircle = new ImageButton(this);
        floatingCircle.setImageResource(android.R.drawable.ic_menu_camera);
        
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(Color.parseColor("#4CAF50"));
        drawable.setStroke(3, Color.WHITE);
        floatingCircle.setBackground(drawable);
        floatingCircle.setPadding(20, 20, 20, 20);
        floatingCircle.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        
        circleParams = new WindowManager.LayoutParams(
                70, 70, layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        circleParams.gravity = Gravity.TOP | Gravity.START;
        circleParams.x = 100;
        circleParams.y = 200;
        
        floatingCircle.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = event.getRawX();
                    startY = event.getRawY();
                    initialX = circleParams.x;
                    initialY = circleParams.y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    circleParams.x = initialX + (int) (event.getRawX() - startX);
                    circleParams.y = initialY + (int) (event.getRawY() - startY);
                    if (windowManager != null) {
                        windowManager.updateViewLayout(floatingCircle, circleParams);
                    }
                    return true;
            }
            return false;
        });
        
        floatingCircle.setOnClickListener(v -> {
            showFloatingWindow();
            floatingCircle.setVisibility(View.GONE);
        });
        
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager != null) {
            windowManager.addView(floatingCircle, circleParams);
            Toast.makeText(this, "🔘 Кружок создан", Toast.LENGTH_SHORT).show();
        }
    }

    private void showFloatingWindow() {
        if (isWindowVisible) return;
        
        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }
        
        floatingWindow = new FrameLayout(this);
        floatingWindow.setBackgroundColor(Color.parseColor("#1E1E1E"));
        floatingWindow.setElevation(20);
        
        GradientDrawable border = new GradientDrawable();
        border.setColor(Color.parseColor("#2C2C2C"));
        border.setStroke(3, Color.parseColor("#4CAF50"));
        border.setCornerRadius(20);
        floatingWindow.setBackground(border);
        
        WebView webView = new WebView(this);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setDomStorageEnabled(true);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(android.webkit.PermissionRequest request) {
                request.grant(new String[]{android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            }
        });
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("https://crconferensimessenger.vercel.app/");
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        
        Button closeButton = new Button(this);
        closeButton.setText("✕");
        closeButton.setTextColor(Color.WHITE);
        closeButton.setBackgroundColor(Color.parseColor("#4CAF50"));
        closeButton.setPadding(20, 15, 20, 15);
        
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        closeParams.gravity = Gravity.TOP | Gravity.END;
        closeParams.setMargins(0, 10, 10, 0);
        closeButton.setLayoutParams(closeParams);
        
        closeButton.setOnClickListener(v -> hideFloatingWindow());
        
        floatingWindow.addView(webView);
        floatingWindow.addView(closeButton);
        
        windowParams = new WindowManager.LayoutParams(
                600, 800, layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        windowParams.gravity = Gravity.CENTER;
        
        if (windowManager != null) {
            windowManager.addView(floatingWindow, windowParams);
            isWindowVisible = true;
        }
    }

    private void hideFloatingWindow() {
        if (floatingWindow != null && windowManager != null) {
            windowManager.removeView(floatingWindow);
            floatingWindow = null;
            isWindowVisible = false;
        }
        if (floatingCircle != null) {
            floatingCircle.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (floatingCircle != null) {
            floatingCircle.setVisibility(View.GONE);
        }
        if (floatingWindow != null) {
            hideFloatingWindow();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (floatingCircle != null && !isWindowVisible) {
            floatingCircle.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_MICROPHONE && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "🎤 Микрофон разрешён", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "🎤 Микрофон НЕ разрешён", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Останавливаем foreground service
        stopForeground(true);
        if (floatingCircle != null && windowManager != null) {
            windowManager.removeView(floatingCircle);
        }
        if (floatingWindow != null && windowManager != null) {
            windowManager.removeView(floatingWindow);
        }
    }
}
