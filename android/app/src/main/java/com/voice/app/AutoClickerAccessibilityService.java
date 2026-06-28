package com.voice.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.RequiresApi;

import java.util.ArrayList;

public class AutoClickerAccessibilityService extends AccessibilityService {

    private static AutoClickerAccessibilityService instance;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Здесь можно обрабатывать события экрана, если нужно
    }

    @Override
    public void onInterrupt() {
        // Сервис был прерван
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    // ==================== МЕТОДЫ ДЛЯ КЛИКОВ ИЗ MAINACTIVITY ====================

    /**
     * Получить экземпляр сервиса для вызова методов из MainActivity
     */
    public static AutoClickerAccessibilityService getInstance() {
        return instance;
    }

    /**
     * Проверка, запущен ли сервис
     */
    public static boolean isServiceRunning() {
        return instance != null;
    }

    /**
     * Выполнить обычный клик (Tap) по координатам
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void performTap(int x, int y) {
        Path clickPath = new Path();
        clickPath.moveTo(x, y);
        
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(clickPath, 0, 100));
        
        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
            }
        }, null);
    }

    /**
     * Выполнить зажатие (Hold) на определенное время
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void performHold(int x, int y, int durationMs) {
        Path holdPath = new Path();
        holdPath.moveTo(x, y);
        
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        // Время удержания в миллисекундах (например, 500)
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(holdPath, 0, durationMs));
        
        dispatchGesture(gestureBuilder.build(), null, null);
    }

    /**
     * Выполнить свайп (проведение пальцем) по точкам
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void performSwipe(ArrayList<Point> points, int durationMs) {
        if (points == null || points.size() < 2) return;
        
        Path swipePath = new Path();
        swipePath.moveTo(points.get(0).x, points.get(0).y);
        for (int i = 1; i < points.size(); i++) {
            swipePath.lineTo(points.get(i).x, points.get(i).y);
        }
        
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        // Время выполнения свайпа (например, 300мс)
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, durationMs));
        
        dispatchGesture(gestureBuilder.build(), null, null);
    }

    /**
     * Выполнить макрос (серию кликов с задержками)
     * В этом примере просто выполняет клики по очереди
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void performMacro(ArrayList<Point> points, int delayBetweenClicksMs) {
        if (points == null || points.isEmpty()) return;
        
        // В реальности здесь нужно использовать Handler или корутины для задержек
        // Для простоты выполним все клики подряд с небольшой задержкой в самом жесте
        long startTime = 0;
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        
        for (Point p : points) {
            Path path = new Path();
            path.moveTo(p.x, p.y);
            // Задержка между кликами
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, startTime, 50));
            startTime += delayBetweenClicksMs;
        }
        
        dispatchGesture(gestureBuilder.build(), null, null);
    }

    // Вспомогательный класс для хранения координат
    public static class Point {
        public int x, y;
        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
