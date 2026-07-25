package com.cr.arcade;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
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
    
    // Для записи макросов
    private RecordingListener recordingListener;
    private boolean isRecording = false;
    private long lastActionTime = 0;
    private FrameLayout recordOverlay;
    private boolean isOverlayShown = false;
    private FrameLayout touchOverlay; // Для перехвата касаний без блокировки

    // Интерфейс для передачи записанных действий
    public interface RecordingListener {
        void onActionRecorded(int x, int y, long delay);
        void onRecordingStopped();
    }

    @Override
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {
        // Не используем
    }

    @Override
    public void onInterrupt() {
        // Не используем
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        hideRecordOverlay();
        hideTouchOverlay();
        instance = null;
    }

    public static MacroService getInstance() {
        return instance;
    }

    // ==================== ВЫПОЛНЕНИЕ КЛИКА ====================

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void performClick(int x, int y) {
        try {
            Path clickPath = new Path();
            clickPath.moveTo(x, y);
            
            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(clickPath, 0, 1));
            
            dispatchGesture(gestureBuilder.build(), null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== ЗАПИСЬ МАКРОСА ====================

    public void startRecording(RecordingListener listener) {
        if (isRecording) return;
        
        this.recordingListener = listener;
        this.isRecording = true;
        this.lastActionTime = System.currentTimeMillis();
        
        // Показываем оверлей для записи
        showRecordOverlay();
        showTouchOverlay();
        
        Toast.makeText(this, "🔴 Запись начата! Кликайте в игре", Toast.LENGTH_SHORT).show();
    }

    public void stopRecording() {
        if (!isRecording) return;
        
        this.isRecording = false;
        
        // Скрываем оверлеи
        hideRecordOverlay();
        hideTouchOverlay();
        
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

    // ==================== ОВЕРЛЕЙ ДЛЯ ЗАПИСИ (ТОЛЬКО ИНДИКАТОР) ====================

    private void showRecordOverlay() {
        if (isOverlayShown || windowManager == null) return;
        
        try {
            recordOverlay = new FrameLayout(this);
            recordOverlay.setBackgroundColor(0x00000000); // Прозрачный
            
            // Индикатор записи
            FrameLayout indicatorContainer = new FrameLayout(this);
            
            TextView recordText = new TextView(this);
            recordText.setText("🔴 ЗАПИСЬ");
            recordText.setTextColor(0xFFFF0000);
            recordText.setTextSize(18);
            recordText.setTypeface(null, android.graphics.Typeface.BOLD);
            recordText.setGravity(Gravity.CENTER);
            recordText.setPadding(16, 8, 16, 8);
            
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(12);
            bg.setColor(0xCC000000);
            bg.setStroke(2, 0xFFFF0000);
            recordText.setBackground(bg);
            
            FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            textParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            textParams.setMargins(0, 30, 0, 0);
            recordText.setLayoutParams(textParams);
            
            // Анимация мигания
            final TextView blinkText = recordText;
            startBlinking(blinkText);
            
            recordOverlay.addView(recordText);
            
            // Кнопка СТОП
            Button stopBtn = new Button(this);
            stopBtn.setText("⏹ СТОП");
            stopBtn.setTextColor(0xFFFFFFFF);
            stopBtn.setTextSize(14);
            stopBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            
            android.graphics.drawable.GradientDrawable stopBg = new android.graphics.drawable.GradientDrawable();
            stopBg.setCornerRadius(20);
            stopBg.setColor(0xFFFF0000);
            stopBtn.setBackground(stopBg);
            stopBtn.setPadding(24, 10, 24, 10);
            
            FrameLayout.LayoutParams stopParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            stopParams.gravity = Gravity.TOP | Gravity.END;
            stopParams.setMargins(0, 30, 30, 0);
            stopBtn.setLayoutParams(stopParams);
            
            stopBtn.setOnClickListener(v -> {
                // Останавливаем запись через MainActivity
                if (instance != null && recordingListener != null) {
                    recordingListener.onRecordingStopped();
                    stopRecording();
                }
            });
            
            recordOverlay.addView(stopBtn);
            
            int flag = getOverlayFlag();
            
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    flag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP | Gravity.START;
            
            windowManager.addView(recordOverlay, params);
            isOverlayShown = true;
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void hideRecordOverlay() {
        try {
            if (recordOverlay != null && windowManager != null && isOverlayShown) {
                windowManager.removeView(recordOverlay);
                recordOverlay = null;
                isOverlayShown = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== ТАЧ-ОВЕРЛЕЙ (ПРОПУСКАЕТ КЛИКИ) ====================

    private void showTouchOverlay() {
        if (windowManager == null) return;
        
        try {
            touchOverlay = new FrameLayout(this);
            touchOverlay.setBackgroundColor(0x00000000); // Полностью прозрачный
            
            // Устанавливаем слушатель касаний
            touchOverlay.setOnTouchListener((v, event) -> {
                if (isRecording && event.getAction() == MotionEvent.ACTION_DOWN) {
                    // Получаем координаты клика
                    int x = (int) event.getRawX();
                    int y = (int) event.getRawY();
                    
                    // Записываем клик
                    long currentTime = System.currentTimeMillis();
                    long delay = currentTime - lastActionTime;
                    lastActionTime = currentTime;
                    
                    if (recordingListener != null) {
                        mainHandler.post(() -> {
                            if (recordingListener != null) {
                                recordingListener.onActionRecorded(x, y, delay);
                            }
                        });
                    }
                }
                // Возвращаем false, чтобы событие прошло дальше
                return false;
            });
            
            int flag = getOverlayFlag();
            
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    flag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP | Gravity.START;
            
            windowManager.addView(touchOverlay, params);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void hideTouchOverlay() {
        try {
            if (touchOverlay != null && windowManager != null) {
                windowManager.removeView(touchOverlay);
                touchOverlay = null;
            }
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
            if (view != null) {
                view.animate().alpha(1f).setDuration(500).withEndAction(() -> {
                    if (view != null && isRecording) {
                        startBlinking(view);
                    }
                }).start();
            }
        }).start();
    }
    }
