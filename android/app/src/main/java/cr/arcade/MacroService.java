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
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;

public class MacroService extends AccessibilityService {
    private static MacroService instance;
    private WindowManager windowManager;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Handler clickHandler = new Handler();
    
    private RecordingListener recordingListener;
    private boolean isRecording = false;
    private long lastActionTime = 0;
    private int actionCount = 0;
    
    // ОВЕРЛЕЙ
    private FrameLayout clickOverlay;
    private boolean isOverlayShown = false;
    
    // ИНДИКАТОР
    private FrameLayout indicatorOverlay;
    private boolean isIndicatorShown = false;
    
    private boolean isProcessingClick = false;
    
    // ДЛЯ ОТСЛЕЖИВАНИЯ ЖЕСТОВ
    private float touchStartX, touchStartY;
    private float touchEndX, touchEndY;
    private long touchStartTime;
    private boolean isSwiping = false;
    private static final int SWIPE_THRESHOLD = 30;
    private static final int CLICK_MAX_TIME = 300;

    public interface RecordingListener {
        void onActionRecorded(int x, int y, long delay);
        void onSwipeRecorded(int x1, int y1, int x2, int y2, long delay);
        void onRecordingStopped();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

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
        instance = this;
        Toast.makeText(this, "✅ Макрос сервис готов", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        hideClickOverlay();
        hideIndicatorOverlay();
        instance = null;
    }

    public static MacroService getInstance() {
        return instance;
    }

    // ===== ВЫПОЛНЕНИЕ КЛИКА =====
    public void doClick(int x, int y) {
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

    // ===== ВЫПОЛНЕНИЕ СВАЙПА =====
    public void doSwipe(int x1, int y1, int x2, int y2, long duration) {
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

    // ===== ЗАПИСЬ =====
    public void startRecording(RecordingListener listener) {
        if (isRecording) return;
        
        this.recordingListener = listener;
        this.isRecording = true;
        this.lastActionTime = System.currentTimeMillis();
        this.actionCount = 0;
        
        showIndicatorOverlay();
        showClickOverlay();
        
        Toast.makeText(this, "🔴 Запись начата! Клики и свайпы записываются", Toast.LENGTH_SHORT).show();
    }

    public void stopRecording() {
        if (!isRecording) return;
        
        this.isRecording = false;
        hideClickOverlay();
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

    // ===== УМНЫЙ ОВЕРЛЕЙ (КЛИКИ + СВАЙПЫ) =====
    private void showClickOverlay() {
        if (windowManager == null || isOverlayShown) return;
        
        try {
            clickOverlay = new FrameLayout(this) {
                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    if (!isRecording || isProcessingClick) return false;
                    
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            touchStartX = event.getRawX();
                            touchStartY = event.getRawY();
                            touchEndX = touchStartX;
                            touchEndY = touchStartY;
                            touchStartTime = System.currentTimeMillis();
                            isSwiping = false;
                            return true;
                            
                        case MotionEvent.ACTION_MOVE:
                            float dx = event.getRawX() - touchStartX;
                            float dy = event.getRawY() - touchStartY;
                            if (Math.abs(dx) > SWIPE_THRESHOLD || Math.abs(dy) > SWIPE_THRESHOLD) {
                                isSwiping = true;
                                touchEndX = event.getRawX();
                                touchEndY = event.getRawY();
                            }
                            return true;
                            
                        case MotionEvent.ACTION_UP:
                            long duration = System.currentTimeMillis() - touchStartTime;
                            float dx2 = event.getRawX() - touchStartX;
                            float dy2 = event.getRawY() - touchStartY;
                            
                            // Если свайп
                            if (isSwiping || Math.abs(dx2) > SWIPE_THRESHOLD || Math.abs(dy2) > SWIPE_THRESHOLD) {
                                // Записываем свайп
                                final int x1 = (int) touchStartX;
                                final int y1 = (int) touchStartY;
                                final int x2 = (int) event.getRawX();
                                final int y2 = (int) event.getRawY();
                                final long swipeDuration = Math.max(duration, 100);
                                
                                long currentTime = System.currentTimeMillis();
                                long delay = currentTime - lastActionTime;
                                if (delay < 50) delay = 50;
                                lastActionTime = currentTime;
                                actionCount++;
                                
                                if (recordingListener != null) {
                                    recordingListener.onSwipeRecorded(x1, y1, x2, y2, delay);
                                }
                                
                                // Выполняем свайп (чтобы игра получила действие)
                                isProcessingClick = true;
                                hideClickOverlay();
                                
                                clickHandler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        doSwipe(x1, y1, x2, y2, swipeDuration);
                                        
                                        clickHandler.postDelayed(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (isRecording) {
                                                    showClickOverlay();
                                                }
                                                isProcessingClick = false;
                                            }
                                        }, 150);
                                    }
                                }, 50);
                                
                                return true;
                            }
                            
                            // Иначе это клик
                            if (duration < CLICK_MAX_TIME) {
                                final int x = (int) event.getRawX();
                                final int y = (int) event.getRawY();
                                
                                long currentTime = System.currentTimeMillis();
                                long delay = currentTime - lastActionTime;
                                if (delay < 50) delay = 50;
                                lastActionTime = currentTime;
                                actionCount++;
                                
                                if (recordingListener != null) {
                                    recordingListener.onActionRecorded(x, y, delay);
                                }
                                
                                isProcessingClick = true;
                                hideClickOverlay();
                                
                                clickHandler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        doClick(x, y);
                                        
                                        clickHandler.postDelayed(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (isRecording) {
                                                    showClickOverlay();
                                                }
                                                isProcessingClick = false;
                                            }
                                        }, 100);
                                    }
                                }, 50);
                                
                                return true;
                            }
                            
                            return false;
                    }
                    return false;
                }
            };
            
            clickOverlay.setBackgroundColor(0x00000000);
            clickOverlay.setClickable(true);
            clickOverlay.setFocusable(true);
            
            int flag = getOverlayFlag();
            
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    flag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = 0;
            params.y = 0;
            
            windowManager.addView(clickOverlay, params);
            isOverlayShown = true;
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void hideClickOverlay() {
        try {
            if (clickOverlay != null && windowManager != null && isOverlayShown) {
                windowManager.removeView(clickOverlay);
                clickOverlay = null;
                isOverlayShown = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ===== ИНДИКАТОР =====
    private void showIndicatorOverlay() {
        if (windowManager == null || isIndicatorShown) return;
        
        try {
            indicatorOverlay = new FrameLayout(this);
            indicatorOverlay.setBackgroundColor(0x00000000);
            
            FrameLayout container = new FrameLayout(this);
            container.setBackgroundColor(0x00000000);
            
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

    private int getOverlayFlag() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;
    }
                                }
