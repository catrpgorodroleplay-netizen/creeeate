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
    
    private FrameLayout recordingOverlay;
    private boolean isOverlayShown = false;
    
    // Координаты для записи
    private float touchStartX, touchStartY;
    private boolean isSwiping = false;

    public interface RecordingListener {
        void onActionRecorded(int x, int y, long delay);
        void onSwipeRecorded(int x1, int y1, int x2, int y2, long delay);
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
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Toast.makeText(this, "✅ Макрос сервис готов", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        hideRecordingOverlay();
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

    // ===== ЗАПИСЬ МАКРОСА =====
    public void startRecording(RecordingListener listener) {
        if (isRecording) return;
        
        this.recordingListener = listener;
        this.isRecording = true;
        this.lastActionTime = System.currentTimeMillis();
        this.actionCount = 0;
        
        showRecordingOverlay();
        
        Toast.makeText(this, "🔴 Запись начата! Кликайте на зеленом экране", Toast.LENGTH_SHORT).show();
    }

    public void stopRecording() {
        if (!isRecording) return;
        
        this.isRecording = false;
        hideRecordingOverlay();
        
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

    // ===== ЗЕЛЕНЫЙ ОВЕРЛЕЙ ДЛЯ ЗАПИСИ =====
    private void showRecordingOverlay() {
        if (windowManager == null || isOverlayShown) return;
        
        try {
            recordingOverlay = new FrameLayout(this) {
                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    if (!isRecording || recordingListener == null) return false;
                    
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            touchStartX = event.getRawX();
                            touchStartY = event.getRawY();
                            isSwiping = false;
                            return true;
                            
                        case MotionEvent.ACTION_MOVE:
                            float dx = event.getRawX() - touchStartX;
                            float dy = event.getRawY() - touchStartY;
                            if (Math.abs(dx) > 30 || Math.abs(dy) > 30) {
                                isSwiping = true;
                            }
                            return true;
                            
                        case MotionEvent.ACTION_UP:
                            float dx2 = event.getRawX() - touchStartX;
                            float dy2 = event.getRawY() - touchStartY;
                            
                            if (isSwiping || Math.abs(dx2) > 30 || Math.abs(dy2) > 30) {
                                // Свайп
                                int x1 = (int) touchStartX;
                                int y1 = (int) touchStartY;
                                int x2 = (int) event.getRawX();
                                int y2 = (int) event.getRawY();
                                long duration = System.currentTimeMillis() - lastActionTime;
                                
                                long currentTime = System.currentTimeMillis();
                                long delay = currentTime - lastActionTime;
                                if (delay < 50) delay = 50;
                                lastActionTime = currentTime;
                                actionCount++;
                                
                                recordingListener.onSwipeRecorded(x1, y1, x2, y2, delay);
                            } else {
                                // Клик
                                int x = (int) event.getRawX();
                                int y = (int) event.getRawY();
                                
                                long currentTime = System.currentTimeMillis();
                                long delay = currentTime - lastActionTime;
                                if (delay < 50) delay = 50;
                                lastActionTime = currentTime;
                                actionCount++;
                                
                                recordingListener.onActionRecorded(x, y, delay);
                            }
                            
                            // Вибрация
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrate(15);
                            }
                            
                            return true;
                    }
                    return false;
                }
            };
            
            // ЗЕЛЕНЫЙ ФОН С ПРОЗРАЧНОСТЬЮ
            recordingOverlay.setBackgroundColor(0x8800FF00); // Зеленый с прозрачностью 50%
            
            // Кнопка СТОП (в правом верхнем углу, НЕ перекрывает зеленый фон)
            Button stopBtn = new Button(this);
            stopBtn.setText("⏹ СТОП");
            stopBtn.setTextColor(0xFFFFFFFF);
            stopBtn.setTextSize(18);
            stopBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            stopBtn.setPadding(30, 16, 30, 16);
            
            android.graphics.drawable.GradientDrawable stopBg = new android.graphics.drawable.GradientDrawable();
            stopBg.setCornerRadius(30);
            stopBg.setColor(0xFFFF0000);
            stopBtn.setBackground(stopBg);
            
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
            
            recordingOverlay.addView(stopBtn);
            
            // Текст "ЗАПИСЬ"
            TextView recordText = new TextView(this);
            recordText.setText("🔴 ЗАПИСЬ");
            recordText.setTextColor(0xFFFF0000);
            recordText.setTextSize(22);
            recordText.setTypeface(null, android.graphics.Typeface.BOLD);
            recordText.setGravity(Gravity.CENTER);
            recordText.setPadding(24, 12, 24, 12);
            
            android.graphics.drawable.GradientDrawable textBg = new android.graphics.drawable.GradientDrawable();
            textBg.setCornerRadius(20);
            textBg.setColor(0xCC000000);
            textBg.setStroke(3, 0xFFFF0000);
            recordText.setBackground(textBg);
            
            FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            textParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            textParams.setMargins(0, 40, 0, 0);
            recordText.setLayoutParams(textParams);
            
            recordingOverlay.addView(recordText);
            
            // Счетчик действий
            final TextView counterText = new TextView(this);
            counterText.setText("0");
            counterText.setTextColor(0xFFFFFFFF);
            counterText.setTextSize(32);
            counterText.setTypeface(null, android.graphics.Typeface.BOLD);
            counterText.setGravity(Gravity.CENTER);
            
            android.graphics.drawable.GradientDrawable counterBg = new android.graphics.drawable.GradientDrawable();
            counterBg.setCornerRadius(40);
            counterBg.setColor(0xCC000000);
            counterBg.setStroke(2, 0xFFFFFFFF);
            counterText.setBackground(counterBg);
            counterText.setPadding(30, 20, 30, 20);
            
            FrameLayout.LayoutParams counterParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            counterParams.gravity = Gravity.CENTER;
            counterText.setLayoutParams(counterParams);
            
            recordingOverlay.addView(counterText);
            
            // Обновляем счетчик
            final Handler counterHandler = new Handler();
            counterHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (isRecording) {
                        counterText.setText(String.valueOf(actionCount));
                        counterHandler.postDelayed(this, 200);
                    }
                }
            });
            
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP | Gravity.START;
            
            windowManager.addView(recordingOverlay, params);
            isOverlayShown = true;
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void hideRecordingOverlay() {
        try {
            if (recordingOverlay != null && windowManager != null && isOverlayShown) {
                windowManager.removeView(recordingOverlay);
                recordingOverlay = null;
                isOverlayShown = false;
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
