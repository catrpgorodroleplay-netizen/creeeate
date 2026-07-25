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
    
    // Оверлей ДЛЯ ИНДИКАТОРА (НЕ БЛОКИРУЕТ КЛИКИ!)
    private FrameLayout indicatorOverlay;
    private boolean isIndicatorShown = false;

    public interface RecordingListener {
        void onActionRecorded(int x, int y, long delay);
        void onRecordingStopped();
    }

    @Override
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {
        // ===== ВАЖНО: ЗАПИСЫВАЕМ КЛИКИ ИЗ СОБЫТИЙ =====
        if (isRecording && recordingListener != null) {
            if (event.getEventType() == android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED ||
                event.getEventType() == android.view.accessibility.AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
                
                android.view.accessibility.AccessibilityNodeInfo source = event.getSource();
                if (source != null) {
                    android.graphics.Rect rect = new android.graphics.Rect();
                    source.getBoundsInScreen(rect);
                    
                    int x = rect.centerX();
                    int y = rect.centerY();
                    
                    long currentTime = System.currentTimeMillis();
                    long delay = currentTime - lastActionTime;
                    lastActionTime = currentTime;
                    
                    recordingListener.onActionRecorded(x, y, delay);
                }
            }
        }
    }

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
        hideIndicatorOverlay();
        instance = null;
    }

    public static MacroService getInstance() {
        return instance;
    }

    // ===== НАСТОЯЩИЙ КЛИК В ЛЮБОЕ ПРИЛОЖЕНИЕ =====
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void performClick(int x, int y) {
        try {
            Path clickPath = new Path();
            clickPath.moveTo(x, y);
            
            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(clickPath, 0, 50));
            
            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    // Клик выполнен успешно
                }
                
                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    // Клик отменен
                }
            }, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ===== ДОЛГИЙ КЛИК =====
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void performLongClick(int x, int y) {
        try {
            Path clickPath = new Path();
            clickPath.moveTo(x, y);
            
            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(clickPath, 0, 800));
            
            dispatchGesture(gestureBuilder.build(), null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ===== СВАЙП =====
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void performSwipe(int x1, int y1, int x2, int y2, long duration) {
        try {
            Path swipePath = new Path();
            swipePath.moveTo(x1, y1);
            swipePath.lineTo(x2, y2);
            
            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, duration));
            
            dispatchGesture(gestureBuilder.build(), null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== ЗАПИСЬ МАКРОСОВ (БЕЗ БЛОКИРОВКИ) ====================

    public void startRecording(RecordingListener listener) {
        if (isRecording) return;
        
        this.recordingListener = listener;
        this.isRecording = true;
        this.lastActionTime = System.currentTimeMillis();
        
        // Показываем ТОЛЬКО ИНДИКАТОР (не блокирует клики)
        showIndicatorOnly();
        
        Toast.makeText(this, "🔴 Запись начата! Кликайте в игре", Toast.LENGTH_SHORT).show();
    }

    public void stopRecording() {
        if (!isRecording) return;
        
        this.isRecording = false;
        hideIndicatorOverlay();
        
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

    // ==================== ИНДИКАТОР ЗАПИСИ (НЕ БЛОКИРУЕТ КЛИКИ!) ====================

    private void showIndicatorOnly() {
        if (windowManager == null || isIndicatorShown) return;
        
        try {
            indicatorOverlay = new FrameLayout(this);
            indicatorOverlay.setBackgroundColor(0x00000000); // ПОЛНОСТЬЮ ПРОЗРАЧНЫЙ
            
            // Текст "ЗАПИСЬ"
            TextView recordText = new TextView(this);
            recordText.setText("🔴 ЗАПИСЬ");
            recordText.setTextColor(0xFFFF0000);
            recordText.setTextSize(18);
            recordText.setTypeface(null, android.graphics.Typeface.BOLD);
            recordText.setGravity(Gravity.CENTER);
            recordText.setPadding(20, 10, 20, 10);
            
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(16);
            bg.setColor(0xCC000000);
            bg.setStroke(2, 0xFFFF0000);
            recordText.setBackground(bg);
            
            FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            textParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            textParams.setMargins(0, 30, 0, 0);
            recordText.setLayoutParams(textParams);
            
            indicatorOverlay.addView(recordText);
            
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
                if (isRecording) {
                    stopRecording();
                }
            });
            
            indicatorOverlay.addView(stopBtn);
            
            // КЛЮЧЕВОЙ МОМЕНТ: НЕ ПЕРЕХВАТЫВАЕМ КАСАНИЯ!
            indicatorOverlay.setClickable(false);
            indicatorOverlay.setFocusable(false);
            indicatorOverlay.setFocusableInTouchMode(false);
            
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
            
            windowManager.addView(indicatorOverlay, params);
            isIndicatorShown = true;
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void hideIndicatorOverlay() {
        try {
            if (indicatorOverlay != null && windowManager != null && isIndicatorShown) {
                windowManager.removeView(indicatorOverlay);
                indicatorOverlay = null;
                isIndicatorShown = false;
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
                }
