package com.voice.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    private static final int REQUEST_MICROPHONE = 100;
    private static final int REQUEST_CAMERA = 102;
    private static final int REQUEST_OVERLAY_PERMISSION = 101;
    
    private WindowManager windowManager;
    public static ImageButton floatingCircle;
    private FrameLayout overlayLayout;
    private WebView webView;
    private WindowManager.LayoutParams circleParams;
    private WindowManager.LayoutParams overlayParams;
    private boolean isOverlayVisible = false;
    
    private float startX, startY;
    private int initialX, initialY;
    private boolean isDragging = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Запрос микрофона
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MICROPHONE);
        }
        
        // Запрос камеры
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
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
        
        // Запуск Foreground Service
        Intent serviceIntent = new Intent(this, VoiceForegroundService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
        
        // Настройка WebView для микрофона и камеры
        if (bridge != null && bridge.getWebView() != null) {
            bridge.getWebView().setWebChromeClient(new WebChromeClient() {
                @Override
                public void onPermissionRequest(android.webkit.PermissionRequest request) {
                    request.grant(new String[]{
                        android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE,
                        android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE
                    });
                }
            });
        }
    }

    private void createFloatingCircle() {
        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }
        
        floatingCircle = new ImageButton(this);
        
        // Иконка геймпада
        floatingCircle.setImageBitmap(createXboxGamepadBitmap());
        
        // Красный фон с обводкой (размер 136x136)
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(Color.parseColor("#CC0000"));
        drawable.setStroke(6, Color.parseColor("#FF6666"));
        floatingCircle.setBackground(drawable);
        floatingCircle.setPadding(25, 25, 25, 25);
        floatingCircle.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        
        circleParams = new WindowManager.LayoutParams(
                136, 136, layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        circleParams.gravity = Gravity.TOP | Gravity.START;
        circleParams.x = 100;
        circleParams.y = 200;
        
        floatingCircle.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getRawX();
                        startY = event.getRawY();
                        initialX = circleParams.x;
                        initialY = circleParams.y;
                        isDragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - startX;
                        float deltaY = event.getRawY() - startY;
                        if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                            isDragging = true;
                        }
                        circleParams.x = initialX + (int) deltaX;
                        circleParams.y = initialY + (int) deltaY;
                        if (windowManager != null) {
                            windowManager.updateViewLayout(floatingCircle, circleParams);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!isDragging) {
                            floatingCircle.setVisibility(View.GONE);
                            showOverlay();
                        }
                        return true;
                }
                return false;
            }
        });
        
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager != null) {
            windowManager.addView(floatingCircle, circleParams);
            Toast.makeText(this, "🎮 Красный кружок с геймпадом создан", Toast.LENGTH_SHORT).show();
        }
    }
    
    private Bitmap createXboxGamepadBitmap() {
        int size = 90;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(6);
        
        float centerX = size / 2f;
        float centerY = size / 2f;
        
        // Корпус геймпада
        float rectWidth = 65;
        float rectHeight = 45;
        float left = centerX - rectWidth / 2;
        float top = centerY - rectHeight / 2;
        float right = centerX + rectWidth / 2;
        float bottom = centerY + rectHeight / 2;
        canvas.drawRoundRect(left, top, right, bottom, 18, 18, paint);
        
        // Левая и правая части геймпада
        canvas.drawCircle(centerX - 25, centerY, 12, paint);
        canvas.drawCircle(centerX + 25, centerY, 12, paint);
        
        // Крестовина
        paint.setStrokeWidth(5);
        canvas.drawLine(centerX - 18, centerY - 8, centerX - 18, centerY + 8, paint);
        canvas.drawLine(centerX - 22, centerY, centerX - 14, centerY, paint);
        
        // Кнопки ABXY
        canvas.drawCircle(centerX + 18, centerY - 6, 5, paint);
        canvas.drawCircle(centerX + 18, centerY + 6, 5, paint);
        canvas.drawCircle(centerX + 26, centerY, 5, paint);
        canvas.drawCircle(centerX + 10, centerY, 5, paint);
        
        return bitmap;
    }

    private void showOverlay() {
        if (isOverlayVisible) return;
        
        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }
        
        overlayLayout = new FrameLayout(this);
        overlayLayout.setBackgroundColor(Color.parseColor("#DD1E1E1E"));
        overlayLayout.setPadding(15, 15, 15, 15);
        
        // WebView
        webView = new WebView(this);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(false);
        webSettings.setAllowContentAccess(false);
        
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(android.webkit.PermissionRequest request) {
                request.grant(new String[]{
                    android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE,
                    android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE
                });
            }
        });
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("https://crconferensimessenger.vercel.app/");
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        
        // Панель кнопок вверху
        LinearLayout buttonPanel = new LinearLayout(this);
        buttonPanel.setOrientation(LinearLayout.HORIZONTAL);
        buttonPanel.setBackgroundColor(0xCC000000);
        buttonPanel.setPadding(20, 15, 20, 15);
        
        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonPanel.setLayoutParams(panelParams);
        
        // Кнопка сворачивания в кружок (красивая)
        Button minimizeButton = new Button(this);
        minimizeButton.setText("🔘 СВЕРНУТЬ");
        minimizeButton.setTextSize(14);
        minimizeButton.setTextColor(Color.WHITE);
        minimizeButton.setBackgroundColor(Color.parseColor("#4CAF50"));
        minimizeButton.setPadding(25, 12, 25, 12);
        minimizeButton.setAllCaps(false);
        LinearLayout.LayoutParams minParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        minParams.setMargins(0, 0, 10, 0);
        minimizeButton.setLayoutParams(minParams);
        minimizeButton.setOnClickListener(v -> {
            hideOverlay();
            if (floatingCircle != null) {
                floatingCircle.setVisibility(View.VISIBLE);
            }
        });
        
        // Кнопка закрытия приложения (красная корзина)
        Button closeButton = new Button(this);
        closeButton.setText("🗑 ЗАКРЫТЬ");
        closeButton.setTextSize(14);
        closeButton.setTextColor(Color.WHITE);
        closeButton.setBackgroundColor(Color.parseColor("#DD2C00"));
        closeButton.setPadding(25, 12, 25, 12);
        closeButton.setAllCaps(false);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        closeButton.setLayoutParams(closeParams);
        closeButton.setOnClickListener(v -> {
            // Закрываем оверлей
            hideOverlay();
            // Удаляем кружок
            if (floatingCircle != null && windowManager != null) {
                windowManager.removeView(floatingCircle);
                floatingCircle = null;
            }
            // Закрываем приложение
            finishAffinity();
        });
        
        buttonPanel.addView(minimizeButton);
        buttonPanel.addView(closeButton);
        
        overlayLayout.addView(webView);
        overlayLayout.addView(buttonPanel);
        
        // Окно на весь экран
        overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        overlayParams.gravity = Gravity.CENTER;
        
        if (windowManager != null) {
            windowManager.addView(overlayLayout, overlayParams);
            isOverlayVisible = true;
        }
    }

    private void hideOverlay() {
        if (overlayLayout != null && windowManager != null) {
            if (webView != null) {
                webView.loadUrl("about:blank");
            }
            windowManager.removeView(overlayLayout);
            overlayLayout = null;
            isOverlayVisible = false;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (floatingCircle != null && !isOverlayVisible) {
            floatingCircle.setVisibility(View.GONE);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (floatingCircle != null && !isOverlayVisible) {
            floatingCircle.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_MICROPHONE && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "🎤 Микрофон разрешён", Toast.LENGTH_SHORT).show();
            }
        }
        if (requestCode == REQUEST_CAMERA && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "📷 Камера разрешена", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingCircle != null && windowManager != null) {
            windowManager.removeView(floatingCircle);
        }
        if (overlayLayout != null && windowManager != null) {
            windowManager.removeView(overlayLayout);
        }
    }
}
