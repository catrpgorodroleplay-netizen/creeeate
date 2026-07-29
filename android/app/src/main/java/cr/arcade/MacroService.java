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
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

public class MacroService extends AccessibilityService {

    private static MacroService instance;
    private WindowManager windowManager;
    private FrameLayout overlayView;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isShowingOverlay = false;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Не используется
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
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        showOverlay("✅ Макросы готовы к работе");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        hideOverlay();
    }

    public static MacroService getInstance() {
        return instance;
    }

    /**
     * Улучшенный клик с плавной анимацией
     */
    public void performClick(int x, int y) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Используем GestureDescription для плавного клика
            Path clickPath = new Path();
            clickPath.moveTo(x, y);
            
            // Добавляем небольшое случайное смещение для реалистичности
            float offsetX = (float) (Math.random() * 4 - 2);
            float offsetY = (float) (Math.random() * 4 - 2);
            
            // Создаем жест с задержкой для имитации реального нажатия
            GestureDescription.StrokeDescription stroke = 
                new GestureDescription.StrokeDescription(clickPath, 0, 100);
            
            GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();
            
            dispatchGesture(gesture, new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    // Показываем визуальный фидбек
                    showClickFeedback(x, y);
                }
                
                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                }
            });
        } else {
            // Для старых версий используем обычный клик
            performLegacyClick(x, y);
        }
    }

    /**
     * Клик с дополнительным фидбеком
     */
    public void performClickWithFeedback(int x, int y) {
        showClickFeedback(x, y);
        performClick(x, y);
    }

    /**
     * Визуальный фидбек клика (круговая анимация)
     */
    private void showClickFeedback(final int x, final int y) {
        mainHandler.post(() -> {
            if (windowManager == null) return;

            // Создаем круговой эффект
            FrameLayout feedbackView = new FrameLayout(getApplicationContext());
            feedbackView.setBackgroundColor(0x44FF0000);
            
            // Создаем концентрические круги
            FrameLayout innerCircle = new FrameLayout(getApplicationContext());
            innerCircle.setBackgroundColor(0x88FF0000);
            
            FrameLayout.LayoutParams innerParams = new FrameLayout.LayoutParams(20, 20);
            innerParams.gravity = Gravity.CENTER;
            innerCircle.setLayoutParams(innerParams);
            feedbackView.addView(innerCircle);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    50, 50,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = x - 25;
            params.y = y - 25;

            windowManager.addView(feedbackView, params);

            // Анимируем исчезновение
            feedbackView.postDelayed(() -> {
                try {
                    if (windowManager != null) {
                        windowManager.removeView(feedbackView);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 300);
        });
    }

    /**
     * Обычный клик для старых версий
     */
    private void performLegacyClick(int x, int y) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            Path clickPath = new Path();
            clickPath.moveTo(x, y);
            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(clickPath, 0, 1));
            dispatchGesture(gestureBuilder.build(), null);
        }
    }

    /**
     * Клик с задержкой (для последовательных действий)
     */
    public void performClickDelayed(final int x, final int y, long delayMs) {
        mainHandler.postDelayed(() -> performClick(x, y), delayMs);
    }

    /**
     * Показать оверлей с сообщением
     */
    private void showOverlay(String message) {
        mainHandler.post(() -> {
            if (windowManager == null) return;

            if (overlayView != null) {
                hideOverlay();
            }

            overlayView = new FrameLayout(getApplicationContext());
            overlayView.setBackgroundColor(0xAA000000);

            // Создаем текст
            TextView textView = new TextView(getApplicationContext());
            textView.setText(message);
            textView.setTextColor(0xFFFFFFFF);
            textView.setTextSize(18);
            textView.setGravity(Gravity.CENTER);
            textView.setPadding(30, 20, 30, 20);

            FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            textParams.gravity = Gravity.CENTER;
            overlayView.addView(textView, textParams);

            // Кнопка закрытия
            Button closeBtn = new Button(getApplicationContext());
            closeBtn.setText("✕");
            closeBtn.setTextColor(0xFFFFFFFF);
            closeBtn.setTextSize(20);
            closeBtn.setBackgroundColor(0x33FF0000);
            closeBtn.setOnClickListener(v -> hideOverlay());

            FrameLayout.LayoutParams btnParams = new FrameLayout.LayoutParams(60, 60);
            btnParams.gravity = Gravity.TOP | Gravity.END;
            btnParams.setMargins(0, 20, 20, 0);
            overlayView.addView(closeBtn, btnParams);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP;
            params.y = 80;

            windowManager.addView(overlayView, params);
            isShowingOverlay = true;

            // Автоматически скрываем через 3 секунды
            overlayView.postDelayed(this::hideOverlay, 3000);
        });
    }

    /**
     * Скрыть оверлей
     */
    private void hideOverlay() {
        mainHandler.post(() -> {
            if (overlayView != null && windowManager != null) {
                try {
                    windowManager.removeView(overlayView);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                overlayView = null;
                isShowingOverlay = false;
            }
        });
    }

    /**
     * Проверка, активна ли служба
     */
    public boolean isServiceActive() {
        return instance != null;
    }

    /**
     * Получение координат центра экрана
     */
    public int[] getScreenCenter() {
        if (windowManager == null) return new int[]{0, 0};
        android.graphics.Point size = new android.graphics.Point();
        windowManager.getDefaultDisplay().getSize(size);
        return new int[]{size.x / 2, size.y / 2};
    }

    /**
     * Выполнение свайпа
     */
    public void performSwipe(int startX, int startY, int endX, int endY, long duration) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Path swipePath = new Path();
            swipePath.moveTo(startX, startY);
            swipePath.lineTo(endX, endY);
            
            GestureDescription.StrokeDescription stroke = 
                new GestureDescription.StrokeDescription(swipePath, 0, duration);
            
            GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();
            
            dispatchGesture(gesture, null);
        }
    }

    /**
     * Нажатие на элемент по тексту
     */
    public boolean clickElementByText(String text) {
        try {
            AccessibilityNodeInfoCompat root = AccessibilityNodeInfoCompat.wrap(getRootInActiveWindow());
            if (root == null) return false;
            
            // Ищем элемент с нужным текстом
            boolean found = findAndClickNode(root, text);
            root.recycle();
            return found;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean findAndClickNode(AccessibilityNodeInfoCompat node, String text) {
        if (node == null) return false;
        
        // Проверяем текущий узел
        if (node.getText() != null && node.getText().toString().toLowerCase().contains(text.toLowerCase())) {
            if (node.isClickable()) {
                node.performAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
                return true;
            }
        }
        
        // Рекурсивно ищем в детях
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfoCompat child = node.getChild(i);
            if (child != null) {
                boolean found = findAndClickNode(child, text);
                child.recycle();
                if (found) return true;
            }
        }
        
        return false;
    }
}
