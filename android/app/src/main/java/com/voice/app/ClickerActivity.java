package com.voice.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import com.voice.app.ClickAccessibilityService.ClickPoint;

public class ClickerActivity extends AppCompatActivity {

    private WindowManager windowManager;
    private ArrayList<ClickPoint> clickPoints = new ArrayList<>();
    private boolean isActive = false;
    private Timer clickTimer;
    private int interval = 1000;

    private TextView tvPointsCount, tvPointsList;
    private EditText etInterval;
    private Spinner spinnerUnit;
    private Button btnAddPoint, btnClearPoints, btnStartStop, btnClose;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_clicker);

        initViews();
        setupListeners();
        checkAccessibility();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    private void initViews() {
        tvPointsCount = findViewById(R.id.tvPointsCount);
        tvPointsList = findViewById(R.id.tvPointsList);
        etInterval = findViewById(R.id.etInterval);
        spinnerUnit = findViewById(R.id.spinnerUnit);
        btnAddPoint = findViewById(R.id.btnAddPoint);
        btnClearPoints = findViewById(R.id.btnClearPoints);
        btnStartStop = findViewById(R.id.btnStartStop);
        btnClose = findViewById(R.id.btnClose);

        String[] units = {"Миллисекунды", "Секунды", "Минуты"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, units);
        spinnerUnit.setAdapter(adapter);
    }

    private void setupListeners() {
        btnAddPoint.setOnClickListener(v -> addClickPoint());
        btnClearPoints.setOnClickListener(v -> clearAllPoints());
        btnStartStop.setOnClickListener(v -> toggleClicker());
        btnClose.setOnClickListener(v -> finish());
    }

    private void checkAccessibility() {
        if (!isAccessibilityServiceEnabled()) {
            new AlertDialog.Builder(this)
                .setTitle("Включите автокликер")
                .setMessage("Для работы нужно включить автокликер в настройках специальных возможностей.")
                .setPositiveButton("Перейти", (d, w) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .setNegativeButton("Отмена", null)
                .show();
        }
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
        final ClickPoint newPoint = new ClickPoint(500, 500);
        clickPoints.add(newPoint);

        View pointView = new View(this);
        pointView.setBackgroundColor(0xFFFF0000);
        
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                60, 60,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 500;
        params.y = 500;

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
        Toast.makeText(this, "Точка " + clickPoints.size() + " добавлена", Toast.LENGTH_SHORT).show();
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
        StringBuilder sb = new StringBuilder("Точки:\n");
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
            @Override
            public void run() {
                runOnUiThread(() -> {
                    if (!isActive) return;
                    // Исправлено: передаём ArrayList<ClickPoint>
                    ClickAccessibilityService.performClick(clickPoints);
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
    }
                }
