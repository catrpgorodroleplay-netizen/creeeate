package com.voice.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class RecordOverlayActivity extends AppCompatActivity {

    private static final int REQUEST_SCREEN_RECORD = 103;
    private WindowManager windowManager;
    private FrameLayout overlayLayout;
    private WindowManager.LayoutParams overlayParams;
    private View minimizeCircle;
    private boolean isMinimized = false;

    private TextView tvStatus, tvTimer;
    private Button btnStart, btnStop, btnMinimize, btnClose;
    private MediaProjectionManager projectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        createOverlay();
    }

    private void createOverlay() {
        int flag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;

        overlayLayout = new FrameLayout(this);
        overlayLayout.setBackgroundColor(Color.parseColor("#DD1A1A2E"));
        overlayLayout.setPadding(20, 20, 20, 20);

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(Color.parseColor("#1A1A2E"));
        mainLayout.setPadding(30, 30, 30, 30);

        TextView title = new TextView(this);
        title.setText("🎥 ЗАПИСЬ ЭКРАНА");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 20);
        mainLayout.addView(title);

        tvStatus = new TextView(this);
        tvStatus.setText("⏸ Готов к записи");
        tvStatus.setTextColor(Color.LTGRAY);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setPadding(0, 0, 0, 10);
        mainLayout.addView(tvStatus);

        tvTimer = new TextView(this);
        tvTimer.setText("00:00");
        tvTimer.setTextColor(Color.parseColor("#4CAF50"));
        tvTimer.setTextSize(48);
        tvTimer.setGravity(Gravity.CENTER);
        tvTimer.setPadding(0, 0, 0, 20);
        mainLayout.addView(tvTimer);

        GridLayout gridLayout = new GridLayout(this);
        gridLayout.setColumnCount(2);
        gridLayout.setPadding(0, 0, 0, 20);

        btnStart = createButton("▶ НАЧАТЬ", "#4CAF50");
        btnStart.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Intent intent = projectionManager.createScreenCaptureIntent();
                startActivityForResult(intent, REQUEST_SCREEN_RECORD);
            }
        });
        gridLayout.addView(btnStart);

        btnStop = createButton("⏹ ОСТАНОВИТЬ", "#E53935");
        btnStop.setEnabled(false);
        btnStop.setOnClickListener(v -> {
            if (ScreenRecordService.isRunning) {
                Intent stopIntent = new Intent(this, ScreenRecordService.class);
                stopIntent.setAction("STOP");
                ContextCompat.startForegroundService(this, stopIntent);
                btnStart.setEnabled(true);
                btnStop.setEnabled(false);
                tvStatus.setText("⏹ Запись остановлена");
                tvTimer.setText("00:00");
            }
        });
        gridLayout.addView(btnStop);

        btnMinimize = createButton("⏺ СВЕРНУТЬ", "#FF9800");
        btnMinimize.setOnClickListener(v -> minimizeToCircle());
        gridLayout.addView(btnMinimize);

        mainLayout.addView(gridLayout);

        btnClose = createButton("✕ ЗАКРЫТЬ", "#555555");
        btnClose.setOnClickListener(v -> {
            if (windowManager != null && overlayLayout != null) {
                windowManager.removeView(overlayLayout);
            }
            finish();
        });
        mainLayout.addView(btnClose);

        overlayLayout.addView(mainLayout);

        overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                flag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        overlayParams.gravity = Gravity.CENTER;

        if (windowManager != null) {
            windowManager.addView(overlayLayout, overlayParams);
        }
    }

    private Button createButton(String text, String color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.parseColor(color));
        btn.setPadding(15, 15, 15, 15);
        btn.setLayoutParams(new GridLayout.LayoutParams(
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)));
        return btn;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SCREEN_RECORD) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                Intent serviceIntent = new Intent(this, ScreenRecordService.class);
                serviceIntent.setAction("START");
                serviceIntent.putExtra("resultCode", resultCode);
                serviceIntent.putExtra("data", data);
                ContextCompat.startForegroundService(this, serviceIntent);
                btnStart.setEnabled(false);
                btnStop.setEnabled(true);
                tvStatus.setText("⏺ Идёт запись...");
                Toast.makeText(this, "Запись экрана начата", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Запись не разрешена", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void minimizeToCircle() {
        if (isMinimized) return;
        isMinimized = true;

        minimizeCircle = new View(this);
        minimizeCircle.setBackgroundColor(0xFFE53935);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                70, 70,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 200;

        minimizeCircle.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_MOVE:
                    params.x = (int) event.getRawX() - 35;
                    params.y = (int) event.getRawY() - 35;
                    windowManager.updateViewLayout(minimizeCircle, params);
                    break;
                case MotionEvent.ACTION_UP:
                    showFullScreen();
                    break;
            }
            return true;
        });

        windowManager.addView(minimizeCircle, params);

        if (windowManager != null && overlayLayout != null) {
            windowManager.removeView(overlayLayout);
        }
        Toast.makeText(this, "Свернуто в кружок", Toast.LENGTH_SHORT).show();
    }

    private void showFullScreen() {
        if (minimizeCircle != null && windowManager != null) {
            try {
                windowManager.removeView(minimizeCircle);
            } catch (Exception e) {}
            minimizeCircle = null;
        }
        isMinimized = false;

        if (windowManager != null && overlayLayout != null) {
            windowManager.addView(overlayLayout, overlayParams);
        }
        Toast.makeText(this, "Развернуто", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ScreenRecordService.isRunning) {
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
            tvStatus.setText("⏺ Идёт запись...");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (minimizeCircle != null) {
            try {
                windowManager.removeView(minimizeCircle);
            } catch (Exception e) {}
        }
        if (overlayLayout != null && windowManager != null) {
            try {
                windowManager.removeView(overlayLayout);
            } catch (Exception e) {}
        }
    }
          }
