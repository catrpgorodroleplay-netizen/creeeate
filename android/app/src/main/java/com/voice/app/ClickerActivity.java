package com.voice.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class ClickerActivity extends AppCompatActivity {

    private WindowManager windowManager;
    private ArrayList<ClickPoint> clickPoints = new ArrayList<>();
    private int pointCounter = 1;
    private boolean isActive = false;
    private Timer clickTimer;
    private int interval = 1000;

    private FrameLayout overlayView;
    private TextView statusText;
    private Button addPointBtn, startBtn, clearBtn;

    private class ClickPoint {
        float x, y;
        View view;
        ClickPoint(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_clicker); // Тебе нужно создать этот layout или использовать код для UI

        // Проверка Accessibility
        if (!isAccessibilityServiceEnabled()) {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            Toast.makeText(this, "Включите автокликер в настройках", Toast.LENGTH_LONG).show();
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        initUI();
    }

    private void initUI() {
        // Здесь создай свой интерфейс (можно через XML или кодом)
        // Я создам простой пример через код, чтобы не было лишних файлов
        FrameLayout layout = new FrameLayout(this);
        layout.setBackgroundColor(0xCC000000);

        // Кнопка добавления точки
        addPointBtn = new Button(this);
        addPointBtn.setText("➕ Добавить точку");
        addPointBtn.setOnClickListener(v -> addClickPoint());
        layout.addView(addPointBtn);

        // Кнопка запуска
        startBtn = new Button(this);
        startBtn.setText("▶ Запустить");
        startBtn.setOnClickListener(v -> toggleClicker());
        layout.addView(startBtn);

        // Кнопка очистки
        clearBtn = new Button(this);
        clearBtn.setText("🗑 Очистить");
        clearBtn.setOnClickListener(v -> clearPoints());
        layout.addView(clearBtn);

        // Статус
        statusText = new TextView(this);
        statusText.setText("Точек: 0");
        layout.addView(statusText);

        setContentView(layout);
    }

    private void addClickPoint() {
        // Создаём плавающую точку
        final ClickPoint newPoint = new ClickPoint(500, 500);
        clickPoints.add(newPoint);

        // Визуализация точки
        View pointView = new View(this);
        pointView.setBackgroundColor(0xFFFF0000);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                50, 50,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 500;
        params.y = 500;

        // Перетаскивание точки
        pointView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_MOVE:
                    params.x = (int) event.getRawX() - 25;
                    params.y = (int) event.getRawY() - 25;
                    newPoint.x = event.getRawX();
                    newPoint.y = event.getRawY();
                    windowManager.updateViewLayout(pointView, params);
                    break;
                case MotionEvent.ACTION_UP:
                    Toast.makeText(this, "Точка перемещена", Toast.LENGTH_SHORT).show();
                    break;
            }
            return true;
        });

        windowManager.addView(pointView, params);
        newPoint.view = pointView;
        pointCounter++;
        statusText.setText("Точек: " + clickPoints.size());
    }

    private void toggleClicker() {
        if (isActive) {
            stopClicker();
        } else {
            startClicker();
