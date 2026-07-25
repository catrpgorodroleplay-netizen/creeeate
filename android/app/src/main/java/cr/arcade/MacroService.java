package com.cr.arcade;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

public class MacroService extends AccessibilityService {
    private static MacroService instance;
    private WindowManager windowManager;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private RecordingListener recordingListener;
    private boolean isRecording = false;
    private long lastActionTime = 0;
    private TextView recordIndicator;
    private Button stopButton;
    private boolean isProcessingClick = false;

    public interface RecordingListener {
        void onActionRecorded(int x, int y, long delay);
        void onRecordingStopped();
    }

    @Override
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        hideRecordIndicator();
        instance = null;
    }

    public static MacroService getInstance() {
        return instance;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void performClick(int x, int y) {
        try {
            Path clickPath = new Path();
            clickPath.moveTo(x, y);
            
            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(clickPath, 0, 50));
            
            dispatchGesture(gestureBuilder.build(), null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== ЗАПИСЬ МАКРОСОВ ====================

    public void startRecording(RecordingListener listener) {
        if (isRecording) return;
        
        this.recordingListener = listener;
        this.isRecording = true;
        this.lastActionTime = System.currentTimeMillis();
        this.isProcessingClick = false;
        
        showRecordIndicator();
        
        Toast.makeText(this, "🔴 Запись начата! Кликайте в игре", Toast.LENGTH_SHORT).show();
    }

    public void stopRecording() {
        if (!isRecording) return;
        
        this.isRecording = false;
        this.isProcessingClick = false;
        hideRecordIndicator();
        
        if (recordingListener != null) {
            recordingListener.onRecordingStopped();
            recordingListener = null;
        }
        
        Toast.makeText(this, "⏹ Запись остановлена", Toast.LENGTH_SHORT).show();
    }

    public boolean isRecording() {
        return isRecording;
    }

    public void stopRecordingFromActivity() {
        if (isRecording) {
            stopRecording();
        }
    }

    // ==================== МЕТОД ДЛЯ ЗАПИСИ КЛИКА ИЗ АКТИВНОСТИ ====================

    public void recordClick(int x, int y) {
        if (!isRecording || recordingListener == null || isProcessingClick) return;
        
        isProcessingClick = true;
        
        long currentTime = System.currentTimeMillis();
        long delay = currentTime - lastActionTime;
        lastActionTime = currentTime;
        
        mainHandler.post(() -> {
            if (recordingListener != null) {
                recordingListener.onActionRecorded(x, y, delay);
            }
            isProcessingClick = false;
        });
    }

    // ==================== ИНДИКАТОР ЗАПИСИ (БЕЗ ПЕРЕХВАТА КЛИКОВ) ====================

    private void showRecordIndicator() {
        if (windowManager == null) return;
        
        try {
            // Создаем контейнер для индикатора
            FrameLayout container = new FrameLayout(this);
            container.setBackgroundColor(0x00000000);
            
            // Индикатор записи
            recordIndicator = new TextView(this);
            recordIndicator.setText("🔴 ЗАПИСЬ");
            recordIndicator.setTextColor(0xFFFF0000);
            recordIndicator.setTextSize(16);
            recordIndicator.setTypeface(null, android.graphics.Typeface.BOLD);
            recordIndicator.setGravity(Gravity.CENTER);
            recordIndicator.setPadding(16, 8, 16, 8);
            
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(12);
            bg.setColor(0xCC000000);
            bg.setStroke(2, 0xFFFF0000);
            recordIndicator.setBackground(bg);
            
            FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            textParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            textParams.setMargins(0, 30, 0, 0);
            recordIndicator.setLayoutParams(textParams);
            
            container.addView(recordIndicator);
            
            // Кнопка СТОП
            stopButton = new Button(this);
            stopButton.setText("⏹ СТОП");
            stopButton.setTextColor(0xFFFFFFFF);
            stopButton.setTextSize(14);
            stopButton.setTypeface(null, android.graphics.Typeface.BOLD);
            
            android.graphics.drawable.GradientDrawable stopBg = new android.graphics.drawable.GradientDrawable();
            stopBg.setCornerRadius(20);
            stopBg.setColor(0xFFFF0000);
            stopButton.setBackground(stopBg);
            stopButton.setPadding(24, 10, 24, 10);
            
            FrameLayout.LayoutParams stopParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            stopParams.gravity = Gravity.TOP | Gravity.END;
            stopParams.setMargins(0, 30, 30, 0);
            stopButton.setLayoutParams(stopParams);
            
            stopButton.setOnClickListener(v -> {
                if (recordingListener != null) {
                    recordingListener.onRecordingStopped();
                }
                stopRecording();
            });
            
            container.addView(stopButton);
            
            int flag = getOverlayFlag();
            
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    flag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | // КЛЮЧЕВОЙ ФЛАГ!
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = 0;
            params.y = 0;
            
            windowManager.addView(container, params);
            
            // Сохраняем контейнер для удаления
            final FrameLayout finalContainer = container;
            
            // Анимация мигания
            startBlinking(recordIndicator);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void hideRecordIndicator() {
        try {
            // Удаляем все окна
            if (recordIndicator != null && recordIndicator.getParent() != null) {
                View parent = (View) recordIndicator.getParent();
                if (parent != null && windowManager != null) {
                    windowManager.removeView(parent);
                }
            }
            recordIndicator = null;
            stopButton = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    private int getOverlayFlag() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;
    }

    private void startBlinking(final TextView view) {
        if (view == null) return;
        
        view.animate().alpha(0.3f).setDuration(500).withEndAction(() -> {
            if (view != null && isRecording) {
                view.animate().alpha(1f).setDuration(500).withEndAction(() -> {
                    if (view != null && isRecording) {
                        startBlinking(view);
                    }
                }).start();
            }
        }).start();
    }
}
