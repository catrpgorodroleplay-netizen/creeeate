package com.cr.arcade;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.graphics.Rect;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;

public class MacroService extends AccessibilityService {
    private static MacroService instance;
    private WindowManager windowManager;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private RecordingListener recordingListener;
    private boolean isRecording = false;
    private long lastActionTime = 0;
    private int actionCount = 0;
    
    // ТОЛЬКО ИНДИКАТОР (НЕ БЛОКИРУЕТ!)
    private FrameLayout indicatorOverlay;
    private boolean isIndicatorShown = false;

    public interface RecordingListener {
        void onActionRecorded(int x, int y, long delay);
        void onRecordingStopped();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (isRecording && recordingListener != null) {
            int eventType = event.getEventType();
            
            // Ловим ВСЕ типы событий
            if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED ||
                eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
                eventType == AccessibilityEvent.TYPE_VIEW_SELECTED) {
                
                AccessibilityNodeInfo source = event.getSource();
                if (source != null) {
                    Rect rect = new Rect();
                    source.getBoundsInScreen(rect);
                    
                    int x = rect.centerX();
                    int y = rect.centerY();
                    
                    if (x > 0 && y > 0) {
                        long currentTime = System.currentTimeMillis();
                        long delay = currentTime - lastActionTime;
                        if (delay < 50) delay = 50;
                        lastActionTime = currentTime;
                        actionCount++;
                        
                        recordingListener.onActionRecorded(x, y, delay);
                    }
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
    public void onServiceConnected() {
        super.onServiceConnected();
        Toast.makeText(this, "✅ Макрос сервис готов", Toast.LENGTH_SHORT).show();
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

    // ===== ВЫПОЛНЕНИЕ КЛИКА =====
    public void performClick(int x, int y) {
        try {
            Path clickPath = new Path();
            clickPath.moveTo(x, y);
            
            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(clickPath, 0, 50));
            
            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    // Клик выполнен
                }
                
                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    // Пробуем альтернативный метод
                    performAlternativeClick(x, y);
                }
            }, null);
        } catch (Exception e) {
            e.printStackTrace();
            performAlternativeClick(x, y);
        }
    }

    private void performAlternativeClick(int x, int y) {
        try {
            Path clickPath = new Path();
            clickPath.moveTo(x - 5, y - 5);
            clickPath.lineTo(x + 5, y + 5);
            
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(clickPath, 0, 80));
            
            dispatchGesture(builder.build(), null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ===== ЗАПИСЬ =====
    public void startRecording(RecordingListener listener) {
        if (isRecording) return;
        
        this.recordingListener = listener;
        this.isRecording = true;
        this.lastActionTime = System.currentTimeMillis();
        this.actionCount = 0;
        
        showIndicatorOverlay();
        
        Toast.makeText(this, "🔴 Запись начата! Кликайте в любом приложении", Toast.LENGTH_SHORT).show();
    }

    public void stopRecording() {
        if (!isRecording) return;
        
        this.isRecording = false;
        hideIndicatorOverlay();
        
        if (recordingListener != null) {
            recordingListener.onRecordingStopped();
            recordingListener = null;
        }
        
        Toast.makeText(this, "⏹ Запись остановлена. Записано: " + actionCount + " действий", Toast.LENGTH_SHORT).show();
    }

    public boolean isRecording() {
        return isRecording;
    }

    public void stopRecordingFromActivity() {
        if (isRecording) {
            stopRecording();
        }
    }

    // ===== ИНДИКАТОР (НЕ БЛОКИРУЕТ!) =====
    private void showIndicatorOverlay() {
        if (windowManager == null || isIndicatorShown) return;
        
        try {
            indicatorOverlay = new FrameLayout(this);
            indicatorOverlay.setBackgroundColor(0x00000000);
            
            FrameLayout container = new FrameLayout(this);
            container.setBackgroundColor(0x00000000);
            
            // Текст "ЗАПИСЬ"
            TextView recordText = new TextView(this);
            recordText.setText("🔴 ЗАПИСЬ");
            recordText.setTextColor(0xFFFF0000);
            recordText.setTextSize(20);
            recordText.setTypeface(null, android.graphics.Typeface.BOLD);
            recordText.setGravity(Gravity.CENTER);
            recordText.setPadding(24, 12, 24, 12);
            
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(20);
            bg.setColor(0xCC000000);
            bg.setStroke(3, 0xFFFF0000);
            recordText.setBackground(bg);
            
            FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            textParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            textParams.setMargins(0, 40, 0, 0);
            recordText.setLayoutParams(textParams);
            container.addView(recordText);
            
            // Кнопка СТОП
            Button stopBtn = new Button(this);
            stopBtn.setText("⏹ СТОП");
            stopBtn.setTextColor(0xFFFFFFFF);
            stopBtn.setTextSize(16);
            stopBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            
            android.graphics.drawable.GradientDrawable stopBg = new android.graphics.drawable.GradientDrawable();
            stopBg.setCornerRadius(24);
            stopBg.setColor(0xFFFF0000);
            stopBtn.setBackground(stopBg);
            stopBtn.setPadding(28, 12, 28, 12);
            
            FrameLayout.LayoutParams stopParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            stopParams.gravity = Gravity.TOP | Gravity.END;
            stopParams.setMargins(0, 40, 40, 0);
            stopBtn.setLayoutParams(stopParams);
            
            stopBtn.setOnClickListener(v -> {
                if (isRecording) {
                    stopRecording();
                }
            });
            container.addView(stopBtn);
            
            indicatorOverlay.addView(container);
            
            // КЛЮЧЕВОЙ ФЛАГ: НЕ БЛОКИРУЕМ КЛИКИ
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | // ← КЛИКИ ПРОХОДЯТ!
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

    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====
    private int getOverlayFlag() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;
    }
            }
