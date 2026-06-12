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
    private FrameLayout floatingWindow;
    private WindowManager.LayoutParams windowParams;
    private boolean isWindowVisible = false;
    private Button nativeMinimizeButton;

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
            }
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
        
        // Добавляем нативную кнопку в интерфейс приложения
        addNativeButton();
    }
    
    private void addNativeButton() {
        // Создаём кнопку
        nativeMinimizeButton = new Button(this);
        nativeMinimizeButton.setText("🔘 СВЕРНУТЬ В КРУЖОК");
        nativeMinimizeButton.setTextColor(Color.WHITE);
        nativeMinimizeButton.setBackgroundColor(Color.parseColor("#4CAF50"));
        nativeMinimizeButton.setAllCaps(false);
        nativeMinimizeButton.setPadding(30, 20, 30, 20);
        
        // Параметры кнопки
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.bottomMargin = 100;
        
        // Добавляем кнопку в корневой вид
        View rootView = findViewById(android.R.id.content);
        if (rootView instanceof FrameLayout) {
            ((FrameLayout) rootView).addView(nativeMinimizeButton, params);
        }
        
        // Обработчик нажатия
        nativeMinimizeButton.setOnClickListener(v -> {
            // Показываем плавающий кружок и сворачиваем приложение
            showFloatingCircle();
            moveTaskToBack(true);
            Toast.makeText(this, "🔘 Приложение свернуто, микрофон работает", Toast.LENGTH_SHORT).show();
        });
    }
    
    private void showFloatingCircle() {
        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }
        
        // Создаём плавающий кружок
        ImageButton floatingCircle = new ImageButton(this);
        floatingCircle.setImageResource(android.R.drawable.ic_menu_camera);
        
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(Color.parseColor("#4CAF50"));
        drawable.setStroke(3, Color.WHITE);
        floatingCircle.setBackground(drawable);
        floatingCircle.setPadding(20, 20, 20, 20);
        floatingCircle.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        
        WindowManager.LayoutParams circleParams = new WindowManager.LayoutParams(
                70, 70, layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        circleParams.gravity = Gravity.TOP | Gravity.START;
        circleParams.x = 100;
        circleParams.y = 200;
        
        // Перетаскивание кружка
        final float[] initialTouchX = {0};
        final float[] initialTouchY = {0};
        final int[] initialWindowX = {0};
        final int[] initialWindowY = {0};
        
        floatingCircle.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialTouchX[0] = event.getRawX();
                    initialTouchY[0] = event.getRawY();
                    initialWindowX[0] = circleParams.x;
                    initialWindowY[0] = circleParams.y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    circleParams.x = initialWindowX[0] + (int) (event.getRawX() - initialTouchX[0]);
                    circleParams.y = initialWindowY[0] + (int) (event.getRawY() - initialTouchY[0]);
                    if (windowManager != null) {
                        windowManager.updateViewLayout(floatingCircle, circleParams);
                    }
                    return true;
            }
            return false;
        });
        
        // Нажатие на кружок — возвращаем приложение
        floatingCircle.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            if (windowManager != null && floatingCircle != null) {
                windowManager.removeView(floatingCircle);
            }
        });
        
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager != null) {
            windowManager.addView(floatingCircle, circleParams);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // При возвращении в приложение показываем кнопку
        if (nativeMinimizeButton != null) {
            nativeMinimizeButton.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // При сворачивании скрываем кнопку (она не нужна, есть кружок)
        if (nativeMinimizeButton != null) {
            nativeMinimizeButton.setVisibility(View.GONE);
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
        if (windowManager != null) {
            // Очистка (кружок удалится сам при перезапуске)
        }
    }
}
