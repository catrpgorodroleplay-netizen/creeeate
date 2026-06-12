package com.voice.app;

import android.Manifest;
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
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    private static final int REQUEST_MICROPHONE = 100;
    private static final int REQUEST_OVERLAY_PERMISSION = 101;
    
    private WindowManager windowManager;
    private ImageButton floatingButton;
    private FrameLayout floatingWindow;
    private WindowManager.LayoutParams buttonParams;
    private WindowManager.LayoutParams windowParams;
    
    private float initialTouchX;
    private float initialTouchY;
    private int initialWindowX;
    private int initialWindowY;
    private boolean isWindowVisible = false;

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
                createFloatingButton();
            }
        } else {
            createFloatingButton();
        }
        
        // Настройка WebView для микрофона в основном приложении
        if (bridge != null && bridge.getWebView() != null) {
            bridge.getWebView().setWebChromeClient(new WebChromeClient() {
                @Override
                public void onPermissionRequest(android.webkit.PermissionRequest request) {
                    request.grant(new String[]{android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                }
            });
        }
    }

    private void createFloatingButton() {
        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }
        
        floatingButton = new ImageButton(this);
        floatingButton.setImageResource(android.R.drawable.ic_menu_camera);
        
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(Color.parseColor("#4CAF50"));
        drawable.setStroke(3, Color.WHITE);
        floatingButton.setBackground(drawable);
        floatingButton.setPadding(20, 20, 20, 20);
        floatingButton.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        
        buttonParams = new WindowManager.LayoutParams(
                80, 80, layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        buttonParams.gravity = Gravity.TOP | Gravity.START;
        buttonParams.x = 100;
        buttonParams.y = 200;
        
        floatingButton.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    initialWindowX = buttonParams.x;
                    initialWindowY = buttonParams.y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    buttonParams.x = initialWindowX + (int) (event.getRawX() - initialTouchX);
                    buttonParams.y = initialWindowY + (int) (event.getRawY() - initialTouchY);
                    if (windowManager != null && floatingButton != null) {
                        windowManager.updateViewLayout(floatingButton, buttonParams);
                    }
                    return true;
            }
            return false;
        });
        
        floatingButton.setOnClickListener(v -> {
            showFloatingWindow();
            floatingButton.setVisibility(View.GONE);
        });
        
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager != null && floatingButton != null) {
            windowManager.addView(floatingButton, buttonParams);
            Toast.makeText(this, "🔘 Плавающая кнопка создана", Toast.LENGTH_SHORT).show();
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
        
        GradientDrawable border = new GradientDrawable();
        border.setColor(Color.parseColor("#2C2C2C"));
        border.setStroke(3, Color.parseColor("#4CAF50"));
        border.setCornerRadius(20);
        floatingWindow.setBackground(border);
        
        // WebView для сайта
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
        
        // Кнопка закрытия
        Button closeButton = new Button(this);
        closeButton.setText("✕ ЗАКРЫТЬ");
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
        
        if (windowManager != null && floatingWindow != null) {
            windowManager.addView(floatingWindow, windowParams);
            isWindowVisible = true;
            Toast.makeText(this, "📱 Окно открыто", Toast.LENGTH_SHORT).show();
        }
    }

    private void hideFloatingWindow() {
        if (floatingWindow != null && windowManager != null) {
            windowManager.removeView(floatingWindow);
            floatingWindow = null;
            isWindowVisible = false;
            Toast.makeText(this, "🔘 Окно закрыто, кнопка возвращена", Toast.LENGTH_SHORT).show();
        }
        if (floatingButton != null) {
            floatingButton.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (floatingButton != null) {
            floatingButton.setVisibility(View.GONE);
        }
        if (floatingWindow != null && windowManager != null) {
            windowManager.removeView(floatingWindow);
            floatingWindow = null;
            isWindowVisible = false;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (floatingButton != null && !isWindowVisible) {
            floatingButton.setVisibility(View.VISIBLE);
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
        if (floatingButton != null && windowManager != null) {
            windowManager.removeView(floatingButton);
        }
        if (floatingWindow != null && windowManager != null) {
            windowManager.removeView(floatingWindow);
        }
    }
}
