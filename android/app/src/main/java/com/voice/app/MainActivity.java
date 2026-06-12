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
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    private static final int REQUEST_MICROPHONE = 100;
    private static final int REQUEST_OVERLAY_PERMISSION = 101;
    
    private WindowManager windowManager;
    private ImageButton floatingCircle;
    private WindowManager.LayoutParams circleParams;
    private Button minimizeButton;
    
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
                addMinimizeButton();
            }
        } else {
            addMinimizeButton();
        }
        
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
    
    private void addMinimizeButton() {
        minimizeButton = new Button(this);
        minimizeButton.setText("🔘 СВЕРНУТЬ В КРУЖОК");
        minimizeButton.setTextColor(Color.WHITE);
        minimizeButton.setBackgroundColor(Color.parseColor("#4CAF50"));
        minimizeButton.setAllCaps(false);
        minimizeButton.setPadding(40, 25, 40, 25);
        minimizeButton.setTextSize(18);
        
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.bottomMargin = 80;
        
        View rootView = findViewById(android.R.id.content);
        if (rootView instanceof FrameLayout) {
            ((FrameLayout) rootView).addView(minimizeButton, params);
        }
        
        minimizeButton.setOnClickListener(v -> {
            // 1. Показываем кружок
            showFloatingCircle();
            // 2. Сворачиваем приложение (имитируем нажатие Home)
            moveTaskToBack(true);
            Toast.makeText(this, "🔘 Кружок создан. Нажмите на него, чтобы вернуться.", Toast.LENGTH_LONG).show();
        });
    }
    
    private void showFloatingCircle() {
        if (windowManager == null) {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        }
        
        // Удаляем старый кружок, если есть
        if (floatingCircle != null) {
            try {
                windowManager.removeView(floatingCircle);
            } catch (Exception ignored) {}
        }
        
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
        drawable.setStroke(4, Color.WHITE);
        floatingCircle.setBackground(drawable);
        floatingCircle.setPadding(25, 25, 25, 25);
        floatingCircle.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        
        circleParams = new WindowManager.LayoutParams(
                85, 85, layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        circleParams.gravity = Gravity.TOP | Gravity.START;
        circleParams.x = 100;
        circleParams.y = 200;
        
        // Перетаскивание
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
                    windowManager.updateViewLayout(floatingCircle, circleParams);
                    return true;
            }
            return false;
        });
        
        // Нажатие на кружок -> возврат в приложение
        floatingCircle.setOnClickListener(v -> {
            // Убираем кружок
            try {
                windowManager.removeView(floatingCircle);
                floatingCircle = null;
            } catch (Exception ignored) {}
            
            // Возвращаем приложение
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        });
        
        windowManager.addView(floatingCircle, circleParams);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Убираем кружок при возврате в приложение
        if (floatingCircle != null && windowManager != null) {
            try {
                windowManager.removeView(floatingCircle);
                floatingCircle = null;
            } catch (Exception ignored) {}
        }
        if (minimizeButton != null) {
            minimizeButton.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Не удаляем кружок! Он должен висеть.
        // Но кнопку сворачивания прячем
        if (minimizeButton != null) {
            minimizeButton.setVisibility(View.GONE);
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
        if (floatingCircle != null && windowManager != null) {
            try {
                windowManager.removeView(floatingCircle);
            } catch (Exception ignored) {}
        }
    }
}
