package com.cr.arcade;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
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
    private Handler clickHandler = new Handler();
    
    private RecordingListener recordingListener;
    private boolean isRecording = false;
    private long lastActionTime = 0;
    
    // ИНДИКАТОР ЗАПИСИ
    private FrameLayout indicatorOverlay;
    private boolean isIndicatorShown = false;
    
    // Для записи через touch-перехват (альтернативный метод)
    private FrameLayout touchInterceptor;
    private boolean isTouchInterceptorShown = false;

    public interface RecordingListener {
        void onActionRecorded(int x, int y, long delay);
        void onRecordingStopped();
    }

    @Override
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {
        // МЕТОД 1: ЗАПИСЬ ЧЕРЕЗ СИСТЕМНЫЕ СОБЫТИЯ
        if (isRecording && recordingListener != null) {
            int eventType = event.getEventType();
            
            if (eventType == android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED ||
                eventType == android.view.accessibility.AccessibilityEvent.TYPE_VIEW_LONG_CLICKED ||
                eventType == android.view.accessibility.AccessibilityEvent.TYPE_VIEW_TOUCH_EXPLORATION_GESTURE_END ||
                eventType == android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                
                android.view.accessibility.AccessibilityNodeInfo source = event.getSource();
                if (source != null) {
                    android.graphics.Rect rect = new android.graphics.Rect();
                    source.getBoundsInScreen(rect);
                    
                    int x = rect.centerX();
                    int y = rect.centerY();
                    
                    if (x > 0 && y > 0) {
                        long currentTime = System.currentTimeMillis();
                        long delay = currentTime - lastActionTime;
                        if (delay < 50) delay = 50; // Минимальная задержка
                        lastActionTime = currentTime;
                        
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
    public void onDestroy() {
        super.onDestroy();
        hideIndicatorOverlay();
        hideTouchInterceptor();
        instance = null;
    }

    public static MacroService getInstance() {
        return instance;
    }

    // ==================== УНИВЕРСАЛЬНЫЙ КЛИК ====================
    
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
                    // Клик выполнен
                }
                
                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    // Если клик отменен - пробуем альтернативный метод
                    performAlternativeClick(x, y);
                }
            }, null);
        } catch (Exception e) {
            performAlternativeClick(x, y);
        }
    }

    // ===== АЛЬТЕРНАТИВНЫЙ МЕТОД КЛИКА =====
    private void performAlternativeClick(int x, int y) {
        try {
            // Используем другой паттерн для клика
            Path clickPath = new Path();
            clickPath.moveTo(x - 5, y - 5);
            clickPath.lineTo(x + 5, y + 5);
            
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(clickPath, 0, 100));
            
            dispatchGesture(builder.build(), null, null);
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

    // ===== ДВОЙНОЙ КЛИК =====
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void performDoubleClick(int x, int y) {
        performClick(x, y);
        clickHandler.postDelayed(() -> performClick(x, y), 100);
    }

    // ==================== ЗАПИСЬ МАКРОСОВ ====================

    public void startRecording(RecordingListener listener) {
        if (isRecording) return;
        
        this.recordingListener = listener;
        this.isRecording = true;
        this.lastActionTime = System.currentTimeMillis();
        
        // Показываем индикатор
        showIndicatorOverlay();
        
        // Включаем перехват касаний (для приложений, где не работают Accessibility события)
        showTouchInterceptor();
        
        Toast.makeText(this, "🔴 Запись начата", Toast.LENGTH_SHORT).show();
    }

    public void stopRecording() {
        if (!isRecording) return;
        
        this.isRecording = false;
        hideIndicatorOverlay();
        hideTouchInterceptor();
        
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

    // ==================== ИНДИКАТОР ЗАПИСИ ====================

    private void showIndicatorOverlay() {
        if (windowManager == null || isIndicatorShown) return;
        
        try {
            indicatorOverlay = new FrameLayout(this);
            indicatorOverlay.setBackgroundColor(0x00000000);
            indicatorOverlay.setClickable(false);
            indicatorOverlay.setFocusable(false);
            
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
            indicatorOverlay.addView(recordText);
            
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
            indicatorOverlay.addView(stopBtn);
            
            // КЛЮЧЕВОЙ ФЛАГ: НЕ БЛОКИРУЕМ КЛИКИ
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP | Gravity.START;
            
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

    // ==================== ПЕРЕХВАТ КАСАНИЙ (альтернативная запись) ====================

    private void showTouchInterceptor() {
        if (windowManager == null || isTouchInterceptorShown) return;
        
        try {
            touchInterceptor = new FrameLayout(this) {
                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    if (isRecording && recordingListener != null) {
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {
                            int x = (int) event.getRawX();
                            int y = (int) event.getRawY();
                            
                            long currentTime = System.currentTimeMillis();
                            long delay = currentTime - lastActionTime;
                            if (delay < 50) delay = 50;
                            lastActionTime = currentTime;
                            
                            recordingListener.onActionRecorded(x, y, delay);
                            
                            // ВИБРАЦИЯ для обратной связи
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrate(20);
                            }
                        }
                    }
                    // ВОЗВРАЩАЕМ false - клик проходит дальше!
                    return false;
                }
            };
            
            touchInterceptor.setBackgroundColor(0x00000000);
            
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP | Gravity.START;
            
            windowManager.addView(touchInterceptor, params);
            isTouchInterceptorShown = true;
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void hideTouchInterceptor() {
        try {
            if (touchInterceptor != null && windowManager != null && isTouchInterceptorShown) {
                windowManager.removeView(touchInterceptor);
                touchInterceptor = null;
                isTouchInterceptorShown = false;
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

    private void vibrate(long ms) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                android.os.VibrationEffect effect = android.os.VibrationEffect.createOneShot(ms, 50);
                android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (vibrator != null) {
                    vibrator.vibrate(effect);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
