package com.voice.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
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
    private ImageButton floatingButton;
    private WindowManager.LayoutParams floatingParams;
    
    private float initialTouchX;
    private float initialTouchY;
    private int initialWindowX;
    private int initialWindowY;

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
                addFloatingButton();
            }
        } else {
            addFloatingButton();
        }
    }

    private void addFloatingButton() {
        floatingButton = new ImageButton(this);
        floatingButton.setImageResource(android.R.drawable.ic_menu_camera);
        floatingButton.setBackgroundColor(0x88000000);
        floatingButton.setPadding(0, 0, 0, 0);
        floatingButton.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        
        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }
        
        floatingParams = new WindowManager.LayoutParams(
                100, 100, layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        
        floatingParams.gravity = Gravity.TOP | Gravity.START;
        floatingParams.x = 100;
        floatingParams.y = 200;
        
        floatingButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            if (floatingButton != null) {
                floatingButton.setVisibility(View.GONE);
            }
        });
        
        floatingButton.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    initialWindowX = floatingParams.x;
                    initialWindowY = floatingParams.y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    floatingParams.x = initialWindowX + (int) (event.getRawX() - initialTouchX);
                    floatingParams.y = initialWindowY + (int) (event.getRawY() - initialTouchY);
                    windowManager.updateViewLayout(floatingButton, floatingParams);
                    return true;
            }
            return false;
        });
        
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        windowManager.addView(floatingButton, floatingParams);
        Toast.makeText(this, "🔘 Круглая плавающая кнопка готова", Toast.LENGTH_SHORT).show();
    }

    // ИСПРАВЛЕНО: public вместо protected
    @Override
    public void onResume() {
        super.onResume();
        if (floatingButton != null) {
            floatingButton.setVisibility(View.GONE);
        }
    }

    // ИСПРАВЛЕНО: public вместо protected
    @Override
    public void onPause() {
        super.onPause();
        if (floatingButton != null) {
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

    // ИСПРАВЛЕНО: public вместо protected
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingButton != null && windowManager != null) {
            windowManager.removeView(floatingButton);
        }
    }
}
