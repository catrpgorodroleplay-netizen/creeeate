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
    
    // Оверлей для перехвата касаний (прозрачный)
    private FrameLayout touchOverlay;
    private boolean isTouchOverlayShown = false;

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
        hideTouchOverlay();
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
        
        // Показываем оверлей для перехвата касаний
        showTouchOverlay();
        
        Toast.makeText(this, "🔴 Запись начата! Кликайте в игре", Toast.LENGTH_SHORT).show();
    }

    public void stopRecording() {
        if (!isRecording) return;
        
        this.isRecording = false;
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

    // ==================== ОВЕРЛЕЙ ДЛЯ ПЕРЕХВАТА КАСАНИЙ ====================

    private void showTouchOverlay() {
        if (windowManager == null || isTouchOverlayShown) return;
        
        try {
            touchOverlay = new FrameLayout(this) {
                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    // Перехватываем и записываем клик
                    if (isRecording && event.getAction() == MotionEvent.ACTION_DOWN) {
                        int x = (int) event.getRawX();
                        int y = (int) event.getRawY();
                        
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
                    // ВОЗВРАЩАЕМ false - КЛИК ПРОХОДИТ В ИГРУ!
                    return false;
                }
            };
            
            // ПОЛНОСТЬЮ ПРОЗРАЧНЫЙ
            touchOverlay.setBackgroundColor(0x00000000);
            
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
            params.x = 0;
            params.y = 0;
            
            windowManager.addView(touchOverlay, params);
            isTouchOverlayShown = true;
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void hideTouchOverlay() {
        try {
            if (touchOverlay != null && windowManager != null && isTouchOverlayShown) {
                windowManager.removeView(touchOverlay);
                touchOverlay = null;
                isTouchOverlayShown = false;
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
