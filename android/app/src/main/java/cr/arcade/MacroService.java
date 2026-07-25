package com.cr.arcade;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import java.util.ArrayList;

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

    // Интерфейс для передачи записанных действий в MainActivity
    public interface RecordingListener {
        void onActionRecorded(int x, int y, long delay);
    }

    @Override
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {
        // Обрабатываем события касаний для записи
        if (isRecording && event != null) {
            // При клике на экран
            if (event.getEventType() == android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED) {
                // Получаем координаты клика из события
                // Это пример - в реальности координаты нужно получать по-другому
            }
        }
    }

    @Override
    public void onInterrupt() {
        // Не используется
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
            
            boolean result = dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    // Клик выполнен
                }
                
                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    // Клик отменен
                }
            }, null);
            
            if (!result) {
                Toast.makeText(this, "Ошибка выполнения клика", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== ЗАПИСЬ МАКРОСА ====================

    /**
     * Начать запись макроса
     * @param listener слушатель для получения записанных действий
     */
    public void startRecording(RecordingListener listener) {
        if (isRecording) return;
        
        this.recordingListener = listener;
        this.isRecording = true;
        this.lastActionTime = System.currentTimeMillis();
        
        // Показываем оверлей для записи
        showRecordOverlay();
        
        Toast.makeText(this, "🔴 Запись начата! Кликайте в игре", Toast.LENGTH_SHORT).show();
    }

    /**
     * Остановить запись макроса
     */
    public void stopRecording() {
        if (!isRecording) return;
        
        this.isRecording = false;
        this.recordingListener = null;
        
        // Скрываем оверлей
        hideRecordOverlay();
        
        Toast.makeText(this, "⏹ Запись остановлена", Toast.LENGTH_SHORT).show();
    }

    /**
     * Проверить, идет ли запись
     */
    public boolean isRecording() {
        return isRecording;
    }

    /**
     * Записать клик в текущий макрос
     * Этот метод вызывается из MainActivity при кликах
     */
    public void recordClick(int x, int y) {
        if (!isRecording || recordingListener == null) return;
        
        long currentTime = System.currentTimeMillis();
        long delay = currentTime - lastActionTime;
        lastActionTime = currentTime;
        
        // Передаем записанное действие в MainActivity
        mainHandler.post(() -> {
            if (recordingListener != null) {
                recordingListener.onActionRecorded(x, y, delay);
            }
        });
    }

    // ==================== ОВЕРЛЕЙ ДЛЯ ЗАПИСИ ====================

    /**
     * Показать оверлей с индикатором записи
     */
    private void showRecordOverlay() {
        if (isOverlayShown || windowManager == null) return;
        
        try {
            // Создаем оверлей
            recordOverlay = new FrameLayout(this);
            recordOverlay.setBackgroundColor(0x2200FF00); // Полупрозрачный зеленый
            
            // Индикатор записи
            FrameLayout indicatorContainer = new FrameLayout(this);
            
            TextView recordText = new TextView(this);
            recordText.setText("🔴 ЗАПИСЬ");
            recordText.setTextColor(0xFFFF0000);
            recordText.setTextSize(20);
            recordText.setTypeface(null, android.graphics.Typeface.BOLD);
            recordText.setGravity(Gravity.CENTER);
            recordText.setPadding(20, 10, 20, 10);
            
            // Фон для текста
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(16);
            bg.setColor(0xCC000000);
            bg.setStroke(2, 0xFFFF0000);
            recordText.setBackground(bg);
            
            FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            textParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            textParams.setMargins(0, 40, 0, 0);
            recordText.setLayoutParams(textParams);
            
            // Мигающий индикатор
            final TextView blinkText = recordText;
            mainHandler.post(() -> {
                if (blinkText != null) {
                    blinkText.animate().alpha(0.3f).setDuration(500).withEndAction(() -> {
                        if (blinkText != null) {
                            blinkText.animate().alpha(1f).setDuration(500).start();
                        }
                    }).start();
                }
            });
            
            recordOverlay.addView(recordText);
            
            // Кнопка "СТОП" для остановки записи
            Button stopBtn = new Button(this);
            stopBtn.setText("⏹ СТОП");
            stopBtn.setTextColor(0xFFFFFFFF);
            stopBtn.setTextSize(16);
            stopBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            
            android.graphics.drawable.GradientDrawable stopBg = new android.graphics.drawable.GradientDrawable();
            stopBg.setCornerRadius(25);
            stopBg.setColor(0xFFFF0000);
            stopBtn.setBackground(stopBg);
            stopBtn.setPadding(30, 12, 30, 12);
            
            FrameLayout.LayoutParams stopParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            stopParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            stopParams.setMargins(0, 0, 0, 60);
            stopBtn.setLayoutParams(stopParams);
            
            stopBtn.setOnClickListener(v -> {
                // Останавливаем запись через MainActivity
                if (instance != null) {
                    // Уведомляем MainActivity через статический метод или broadcast
                }
                stopRecording();
                // Уведомляем MainActivity
                if (recordingListener != null) {
                    // Это вызовет stopRecording в MainActivity
                }
            });
            
            recordOverlay.addView(stopBtn);
            
            // Настройки оверлея
            int flag;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                flag = WindowManager.LayoutParams.TYPE_PHONE;
            }
            
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
            
            windowManager.addView(recordOverlay, params);
            isOverlayShown = true;
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Скрыть оверлей записи
     */
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

    // ==================== МЕТОДЫ ДЛЯ ОСТАНОВКИ ИЗ ВНЕ ====================

    /**
     * Остановить запись из MainActivity
     */
    public void stopRecordingFromActivity() {
        if (isRecording) {
            stopRecording();
        }
    }
}
