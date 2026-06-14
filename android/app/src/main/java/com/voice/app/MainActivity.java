package com.voice.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
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
    private FrameLayout overlayLayout;
    private WebView webView;
    private WindowManager.LayoutParams overlayParams;
    private boolean isOverlayVisible = false;
    private boolean isOverlayCreated = false;
    
    // Кружок появляется только когда приложение свёрнуто
    private ImageButton floatingCircle;
    private WindowManager.LayoutParams circleParams;
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
            }
        }
        
        // Запуск Foreground Service
        Intent serviceIntent = new Intent(this, VoiceForegroundService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
        
        // Сразу создаём оверлей на весь экран (как главное окно)
        createFullscreenOverlay();
        
        // Кружок пока не создаём — он появится только при сворачивании
    }
    
    private void createFullscreenOverlay() {
        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }
        
        overlayLayout = new FrameLayout(this);
        overlayLayout.setBackgroundColor(Color.parseColor("#DD1E1E1E"));
        
        // WebView (единственный)
        webView = new WebView(this);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.grant(new String[]{
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE,
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE
                });
            }
        });
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("https://crconferensimessenger.vercel.app/");
        
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        
        // Красная кнопка ЗАКРЫТЬ (останавливает всё приложение)
        ImageButton closeButton = new ImageButton(this);
        closeButton.setImageDrawable(createCloseIcon());
        closeButton.setBackground(createCircleButtonBackground(Color.parseColor("#DD2C00")));
        closeButton.setPadding(20, 20, 20, 20);
        closeButton.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(
                70, 70,
                Gravity.TOP | Gravity.START);
        closeParams.setMargins(20, 40, 0, 0);
        closeButton.setLayoutParams(closeParams);
        closeButton.setOnClickListener(v -> {
            if (webView != null) {
                webView.loadUrl("about:blank");
            }
            if (windowManager != null && overlayLayout != null) {
                windowManager.removeView(overlayLayout);
            }
            if (floatingCircle != null && windowManager != null) {
                windowManager.removeView(floatingCircle);
            }
            finishAffinity();
        });
        
        // Зелёная кнопка СВЕРНУТЬ В КРУЖОК
        ImageButton minimizeButton = new ImageButton(this);
        minimizeButton.setImageDrawable(createMinimizeIcon());
        minimizeButton.setBackground(createCircleButtonBackground(Color.parseColor("#4CAF50")));
        minimizeButton.setPadding(20, 20, 20, 20);
        minimizeButton.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        
        FrameLayout.LayoutParams minParams = new FrameLayout.LayoutParams(
                70, 70,
                Gravity.TOP | Gravity.END);
        minParams.setMargins(0, 40, 20, 0);
        minimizeButton.setLayoutParams(minParams);
        minimizeButton.setOnClickListener(v -> {
            // Сворачиваем в кружок: просто меняем размер окна
            if (windowManager != null && overlayLayout != null && overlayParams != null) {
                // Меняем размер на маленький (кружок)
                overlayParams.width = 136;
                overlayParams.height = 136;
                overlayParams.gravity = Gravity.TOP | Gravity.START;
                overlayParams.x = 100;
                overlayParams.y = 200;
                windowManager.updateViewLayout(overlayLayout, overlayParams);
                
                // Прячем кнопки управления внутри оверлея
                closeButton.setVisibility(View.GONE);
                minimizeButton.setVisibility(View.GONE);
            }
        });
        
        overlayLayout.addView(webView);
        overlayLayout.addView(closeButton);
        overlayLayout.addView(minimizeButton);
        
        overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        overlayParams.gravity = Gravity.CENTER;
        
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager != null) {
            windowManager.addView(overlayLayout, overlayParams);
            isOverlayCreated = true;
            isOverlayVisible = true;
        }
    }
    
    private Drawable createCloseIcon() {
        Bitmap bitmap = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(8);
        paint.setStyle(Paint.Style.STROKE);
        
        float centerX = 30;
        float centerY = 30;
        float offset = 15;
        
        canvas.drawLine(centerX - offset, centerY - offset, centerX + offset, centerY + offset, paint);
        canvas.drawLine(centerX + offset, centerY - offset, centerX - offset, centerY + offset, paint);
        
        return new android.graphics.drawable.BitmapDrawable(getResources(), bitmap);
    }
    
    private Drawable createMinimizeIcon() {
        Bitmap bitmap = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(8);
        paint.setStyle(Paint.Style.STROKE);
        
        float centerX = 30;
        float centerY = 30;
        float width = 25;
        float height = 15;
        
        canvas.drawRect(centerX - width/2, centerY - height/2, centerX + width/2, centerY + height/2, paint);
        
        return new android.graphics.drawable.BitmapDrawable(getResources(), bitmap);
    }
    
    private Drawable createCircleButtonBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        drawable.setStroke(4, Color.WHITE);
        return drawable;
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // При возвращении в приложение разворачиваем оверлей на весь экран
        if (overlayLayout != null && overlayParams != null && windowManager != null) {
            overlayParams.width = WindowManager.LayoutParams.MATCH_PARENT;
            overlayParams.height = WindowManager.LayoutParams.MATCH_PARENT;
            overlayParams.gravity = Gravity.CENTER;
            windowManager.updateViewLayout(overlayLayout, overlayParams);
            
            // Показываем кнопки управления снова
            if (overlayLayout.getChildCount() >= 2) {
                View closeBtn = overlayLayout.getChildAt(1);
                View minBtn = overlayLayout.getChildAt(2);
                if (closeBtn != null) closeBtn.setVisibility(View.VISIBLE);
                if (minBtn != null) minBtn.setVisibility(View.VISIBLE);
            }
        }
        // Удаляем кружок, если он был
        if (floatingCircle != null && windowManager != null) {
            windowManager.removeView(floatingCircle);
            floatingCircle = null;
        }
    }
    
    @Override
    public void onPause() {
        super.onPause();
        // При сворачивании приложения создаём кружок и меняем размер оверлея
        if (overlayLayout != null && overlayParams != null && windowManager != null) {
            // Меняем размер на кружок
            overlayParams.width = 136;
            overlayParams.height = 136;
            overlayParams.gravity = Gravity.TOP | Gravity.START;
            overlayParams.x = 100;
            overlayParams.y = 200;
            windowManager.updateViewLayout(overlayLayout, overlayParams);
            
            // Прячем кнопки
            if (overlayLayout.getChildCount() >= 2) {
                View closeBtn = overlayLayout.getChildAt(1);
                View minBtn = overlayLayout.getChildAt(2);
                if (closeBtn != null) closeBtn.setVisibility(View.GONE);
                if (minBtn != null) minBtn.setVisibility(View.GONE);
            }
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
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.destroy();
        }
        if (overlayLayout != null && windowManager != null) {
            windowManager.removeView(overlayLayout);
        }
        if (floatingCircle != null && windowManager != null) {
            windowManager.removeView(floatingCircle);
        }
    }
            }
