package com.voice.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.voice.app.ClickAccessibilityService.ClickPoint;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class ClickerOverlayActivity extends AppCompatActivity {

    private WindowManager windowManager;
    private FrameLayout overlayLayout;
    private WindowManager.LayoutParams overlayParams;
    private View minimizeCircle;
    private boolean isMinimized = false;

    private ArrayList<ClickPoint> clickPoints = new ArrayList<>();
    private boolean isActive = false;
    private Timer clickTimer;
    private int interval = 1000;

    private TextView tvPointsCount, tvPointsList;
    private EditText etInterval;
    private Spinner spinnerUnit;
    private Button btnAddPoint, btnClearPoints, btnStartStop, btnMinimize, btnClose;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!isAccessibilityServiceEnabled()) {
            new AlertDialog.Builder(this)
                .setTitle("Включите автокликер")
                .setMessage("Для работы нужно включить автокликер в настройках специальных возможностей.")
                .setPositiveButton("Перейти", (d, w) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .setNegativeButton("Отмена", null)
                .show();
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
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
        title.setText("🖱️ АВТОКЛИКЕР");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 20);
        mainLayout.addView(title);

        tvPointsCount = new TextView(this);
        tvPointsCount.setText("Точек: 0");
        tvPointsCount.setTextColor(Color.LTGRAY);
        tvPointsCount.setGravity(Gravity.CENTER);
        tvPointsCount.setPadding(0, 0, 0, 10);
        mainLayout.addView(tvPointsCount);

        LinearLayout intervalLayout = new LinearLayout(this);
        intervalLayout.setOrientation(LinearLayout.HORIZONTAL);
        intervalLayout.setPadding(0, 0, 0, 20);

        etInterval = new EditText(this);
        etInterval.setHint("Интервал");
        etInterval.setText("1000");
        etInterval.setTextColor(Color.WHITE);
        etInterval.setHintTextColor(Color.GRAY);
        etInterval.setBackgroundColor(Color.parseColor("#333344"));
        etInterval.setPadding(15, 10, 15, 10);
        etInterval.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        intervalLayout.addView(etInterval);

        spinnerUnit = new Spinner(this);
        String[] units = {"Миллисекунды", "Секунды", "Минуты"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, units);
        spinnerUnit.setAdapter(adapter);
        spinnerUnit.setBackgroundColor(Color.parseColor("#333344"));
        intervalLayout.addView(spinnerUnit);

        mainLayout.addView(intervalLayout);

        GridLayout gridLayout = new GridLayout(this);
        gridLayout.setColumnCount(2);
        gridLayout.setPadding(0, 0, 0, 20);

        btnAddPoint = createButton("➕ ДОБАВИТЬ", "#3F51B5");
        btnAddPoint.setOnClickListener(v -> addClickPoint());
        gridLayout.addView(btnAddPoint);

        btnClearPoints = createButton("🗑 ОЧИСТИТЬ", "#E53935");
        btnClearPoints.setOnClickListener(v -> clearAllPoints());
        gridLayout.addView(btnClearPoints);

        btnStartStop = createButton("▶ ЗАПУСТИТЬ", "#4CAF50");
        btnStartStop.setOnClickListener(v -> toggleClicker());
        gridLayout.addView(btnStartStop);

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

        tvPointsList = new TextView(this);
        tvPointsList.setText("Точки (по порядку):");
        tvPointsList.setTextColor(Color.LTGRAY);
        tvPointsList.setPadding(0, 10, 0, 0);
        mainLayout.addView(tvPointsList);

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

    private boolean isAccessibilityServiceEnabled() {
        String service = getPackageName() + "/" + ClickAccessibilityService.class.getCanonicalName();
        try {
            String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return enabled != null && enabled.contains(service);
        } catch (Exception e) {
            return false;
        }
    }

    private void addClickPoint() {
        final int pointId = clickPoints.size() + 1;
        final ClickPoint newPoint = new ClickPoint(500 + (pointId * 30), 400 + (pointId * 20));
        clickPoints.add(newPoint);

        FrameLayout pointView = new FrameLayout(this);
        pointView.setBackground(ContextCompat.getDrawable(this, R.drawable.circle_point));
        pointView.setPadding(10, 10, 10, 10);

        TextView numText = new TextView(this);
        numText.setText(String.valueOf(pointId));
        numText.setTextColor(Color.WHITE);
        numText.setTextSize(16);
        numText.setPadding(8, 4, 8, 4);
        pointView.addView(numText);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                60, 60,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 500 + (pointId * 30);
        params.y = 400 + (pointId * 20);

        pointView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_MOVE:
                    params.x = (int) event.getRawX() - 30;
                    params.y = (int) event.getRawY() - 30;
                    newPoint.x = event.getRawX();
                    newPoint.y = event.getRawY();
                    windowManager.updateViewLayout(pointView, params);
                    break;
            }
            return true;
        });

        windowManager.addView(pointView, params);
        newPoint.view = pointView;
        updateUI();
        Toast.makeText(this, "Точка " + pointId + " добавлена", Toast.LENGTH_SHORT).show();
    }

    private void clearAllPoints() {
        for (ClickPoint point : clickPoints) {
            if (point.view != null) {
                windowManager.removeView(point.view);
            }
        }
        clickPoints.clear();
        updateUI();
        Toast.makeText(this, "Все точки удалены", Toast.LENGTH_SHORT).show();
    }

    private void updateUI() {
        tvPointsCount.setText("Точек: " + clickPoints.size());
        StringBuilder sb = new StringBuilder("Точки (по порядку):\n");
        for (int i = 0; i < clickPoints.size(); i++) {
            sb.append((i+1)).append(") X:").append((int)clickPoints.get(i).x)
              .append(" Y:").append((int)clickPoints.get(i).y).append("\n");
        }
        tvPointsList.setText(sb.toString());
    }

    private void toggleClicker() {
        if (isActive) {
            stopClicker();
        } else {
            startClicker();
        }
    }

    private void startClicker() {
        if (clickPoints.isEmpty()) {
            Toast.makeText(this, "Добавьте хотя бы одну точку", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Включите автокликер в настройках", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            int val = Integer.parseInt(etInterval.getText().toString());
            int pos = spinnerUnit.getSelectedItemPosition();
            switch (pos) {
                case 0: interval = val; break;
                case 1: interval = val * 1000; break;
                case 2: interval = val * 60 * 1000; break;
            }
        } catch (Exception e) {
            interval = 1000;
        }

        isActive = true;
        btnStartStop.setText("⏹ ОСТАНОВИТЬ");
        btnStartStop.setBackgroundColor(0xFFE53935);
        Toast.makeText(this, "Автокликер запущен", Toast.LENGTH_SHORT).show();

        clickTimer = new Timer();
        clickTimer.schedule(new TimerTask() {
            int index = 0;
            @Override
            public void run() {
                runOnUiThread(() -> {
                    if (!isActive || clickPoints.isEmpty()) return;
                    ClickPoint point = clickPoints.get(index);
                    ArrayList<ClickPoint> singlePoint = new ArrayList<>();
                    singlePoint.add(point);
                    ClickAccessibilityService.performClick(singlePoint);
                    index = (index + 1) % clickPoints.size();
                });
            }
        }, 0, interval);
    }

    private void stopClicker() {
        isActive = false;
        if (clickTimer != null) {
            clickTimer.cancel();
            clickTimer = null;
        }
        btnStartStop.setText("▶ ЗАПУСТИТЬ");
        btnStartStop.setBackgroundColor(0xFF4CAF50);
        Toast.makeText(this, "Автокликер остановлен", Toast.LENGTH_SHORT).show();
    }

    private void minimizeToCircle() {
        if (isMinimized) return;
        isMinimized = true;

        minimizeCircle = new View(this);
        minimizeCircle.setBackgroundColor(0xFF3F51B5);

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
        Toast.makeText(this, "Автокликер развернут", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopClicker();
        for (ClickPoint point : clickPoints) {
            if (point.view != null) {
                try {
                    windowManager.removeView(point.view);
                } catch (Exception e) {}
            }
        }
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
