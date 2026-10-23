package com.cr.arcade;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.PointF;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * Служба специальных возможностей для выполнения кликов и свайпов.
 * Должна быть объявлена в AndroidManifest.xml и включена пользователем
 * в настройках «Специальные возможности».
 *
 * MainActivity ожидает:
 *   - статический getInstance(), возвращающий null, пока сервис не подключён;
 *   - performClick(int x, int y);
 *   - performSwipe(int x1, int y1, int x2, int y2, long durationMs).
 *
 * Координаты — абсолютные пиксели экрана (как их отдаёт MotionEvent.getRawX/Y).
 */
public class MacroService extends AccessibilityService {

    private static final String TAG = "MacroService";

    private static volatile MacroService instance;

    private final Handler ui = new Handler(Looper.getMainLooper());

    /** Возвращает активный экземпляр или null, если служба выключена. */
    public static MacroService getInstance() {
        return instance;
    }

    /** Удобная проверка из других мест. */
    public static boolean isReady() {
        return instance != null;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.i(TAG, "MacroService подключён");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Нам не нужны события — только возможность отправлять жесты.
    }

    @Override
    public void onInterrupt() {
        // Ничего.
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (instance == this) instance = null;
        Log.i(TAG, "MacroService отключён");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    // ==================== Публичное API ====================

    /**
     * Одиночный клик в точку (x, y).
     * @return true, если жест поставлен в очередь диспетчера.
     */
    public boolean performClick(final int x, final int y) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "performClick требует API 24+");
            return false;
        }
        final Path path = new Path();
        path.moveTo(x, y);
        final GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 40); // 40 мс — короткий тап
        return dispatchGestureSafe(new GestureDescription.Builder().addStroke(stroke).build());
    }

    /**
     * Свайп / перетаскивание из (x1, y1) в (x2, y2) за durationMs.
     * Для «долгого нажатия» MainActivity вызывает performSwipe(x, y, x+1, y, holdMs).
     */
    public boolean performSwipe(final int x1, final int y1,
                                final int x2, final int y2,
                                final long durationMs) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "performSwipe требует API 24+");
            return false;
        }
        final long dur = Math.max(1, durationMs);
        final Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        final GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, dur);
        return dispatchGestureSafe(new GestureDescription.Builder().addStroke(stroke).build());
    }

    /**
     * Долгое нажатие как отдельный метод (на случай прямого вызова).
     */
    public boolean performLongPress(final int x, final int y, final long holdMs) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false;
        final Path path = new Path();
        path.moveTo(x, y);
        // Минимальное смещение, чтобы система восприняла это как stroke,
        // но визуально это точка. Длительность — это и есть «удержание».
        path.lineTo(x + 1, y);
        final GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, Math.max(1, holdMs));
        return dispatchGestureSafe(new GestureDescription.Builder().addStroke(stroke).build());
    }

    // ==================== Внутреннее ====================

    private boolean dispatchGestureSafe(GestureDescription gesture) {
        try {
            // dispatchGesture можно вызывать из любого потока, но удобнее с main.
            final boolean[] ok = {false};
            if (Looper.myLooper() == Looper.getMainLooper()) {
                ok[0] = dispatchGesture(gesture, null, null);
                return ok[0];
            }
            final Object lock = new Object();
            ui.post(() -> {
                synchronized (lock) {
                    try {
                        ok[0] = dispatchGesture(gesture, null, null);
                    } catch (Exception e) {
                        Log.e(TAG, "dispatchGesture", e);
                    }
                    lock.notifyAll();
                }
            });
            synchronized (lock) {
                // Небольшое ожидание, чтобы MainActivity успел получить результат.
                // В обычной работе dispatchGesture быстрый; таймаут — страховка.
                try { lock.wait(50); } catch (InterruptedException ignored) { }
            }
            return ok[0];
        } catch (Exception e) {
            Log.e(TAG, "dispatchGestureSafe", e);
            return false;
        }
    }
}
