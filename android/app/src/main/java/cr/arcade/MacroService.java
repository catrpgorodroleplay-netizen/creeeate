package com.cr.arcade;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MacroService extends AccessibilityService {

    private static final String TAG = "MacroService";
    private static MacroService instance;
    private static Context appContext;
    private static Handler mainHandler;

    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private WindowManager windowManager;
    private FrameLayout overlayView;
    private boolean isOverlayVisible = false;
    private boolean isClickThroughEnabled = false;
    
    // Статистика
    private int totalClicks = 0;
    private int totalSwipes = 0;
    private long totalExecutionTime = 0;
    private long lastActionTime = 0;
    
    // Менеджер доступности
    private AccessibilityManager accessibilityManager;
    
    // Кеш узлов для быстрого доступа
    private HashMap<String, AccessibilityNodeInfoCompat> nodeCache = new HashMap<>();
    
    // Очередь действий
    private ArrayList<Runnable> actionQueue = new ArrayList<>();
    private boolean isProcessingQueue = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "MacroService created");
        
        instance = this;
        appContext = getApplicationContext();
        mainHandler = new Handler(Looper.getMainLooper());
        accessibilityManager = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        
        initWindowManager();
        createOverlay();
        showOverlay();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Обработка событий доступности
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";
            Log.d(TAG, "Window changed: " + packageName);
            
            // Очищаем кеш при смене окна
            nodeCache.clear();
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "MacroService interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MacroService destroyed");
        
        instance = null;
        hideOverlay();
        
        if (executor != null) {
            executor.shutdownNow();
        }
        
        if (windowManager != null && overlayView != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception e) {
                Log.e(TAG, "Error removing overlay", e);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        return START_STICKY;
    }

    // ==================== ИНИЦИАЛИЗАЦИЯ ====================

    private void initWindowManager() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    private void createOverlay() {
        if (overlayView != null) return;
        
        overlayView = new FrameLayout(this);
        overlayView.setBackgroundColor(0x3300FF00);
        overlayView.setClickable(true);
        overlayView.setFocusable(true);
        
        // Создаем панель управления
        FrameLayout controlPanel = new FrameLayout(this);
        controlPanel.setBackgroundColor(0xDD000000);
        
        Button closeBtn = new Button(this);
        closeBtn.setText("✕");
        closeBtn.setTextColor(0xFFFF0000);
        closeBtn.setTextSize(24);
        closeBtn.setBackgroundColor(0x33000000);
        closeBtn.setPadding(20, 10, 20, 10);
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        closeParams.gravity = Gravity.TOP | Gravity.END;
        closeParams.setMargins(0, 20, 20, 0);
        closeBtn.setLayoutParams(closeParams);
        closeBtn.setOnClickListener(v -> hideOverlay());
        controlPanel.addView(closeBtn);
        
        TextView statusText = new TextView(this);
        statusText.setText("🟢 Macro Service Active");
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(18);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(20, 20, 20, 20);
        controlPanel.addView(statusText);
        
        overlayView.addView(controlPanel);
        
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP;
        params.x = 0;
        params.y = 0;
        
        overlayView.setLayoutParams(params);
    }

    private void showOverlay() {
        if (overlayView == null || isOverlayVisible) return;
        
        try {
            windowManager.addView(overlayView, overlayView.getLayoutParams());
            isOverlayVisible = true;
            Log.d(TAG, "Overlay shown");
        } catch (Exception e) {
            Log.e(TAG, "Error showing overlay", e);
        }
    }

    private void hideOverlay() {
        if (overlayView == null || !isOverlayVisible) return;
        
        try {
            windowManager.removeView(overlayView);
            isOverlayVisible = false;
            Log.d(TAG, "Overlay hidden");
        } catch (Exception e) {
            Log.e(TAG, "Error hiding overlay", e);
        }
    }

    // ==================== ПУБЛИЧНЫЕ МЕТОДЫ ====================

    public static MacroService getInstance() {
        return instance;
    }

    public static void setContext(Context context) {
        appContext = context;
    }

    public static void setMainHandler(Handler handler) {
        mainHandler = handler;
    }

    public static boolean isServiceEnabled() {
        return instance != null;
    }

    public void performClick(int x, int y) {
        if (!isClickThroughEnabled) {
            Log.d(TAG, "Click-through is disabled");
            return;
        }
        
        Log.d(TAG, "Performing click at: " + x + ", " + y);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Path clickPath = new Path();
            clickPath.moveTo(x, y);
            
            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(clickPath, 0, 100));
            
            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    totalClicks++;
                    lastActionTime = System.currentTimeMillis();
                    Log.d(TAG, "Click completed at: " + x + ", " + y);
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    Log.d(TAG, "Click cancelled at: " + x + ", " + y);
                }
            }, null);
        } else {
            // Для старых версий Android используем альтернативный метод
            performClickLegacy(x, y);
        }
    }

    public void performSwipe(int x1, int y1, int x2, int y2, int duration) {
        if (!isClickThroughEnabled) {
            Log.d(TAG, "Click-through is disabled");
            return;
        }
        
        Log.d(TAG, "Performing swipe from (" + x1 + "," + y1 + ") to (" + x2 + "," + y2 + ")");
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Path swipePath = new Path();
            swipePath.moveTo(x1, y1);
            swipePath.lineTo(x2, y2);
            
            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, duration));
            
            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    totalSwipes++;
                    lastActionTime = System.currentTimeMillis();
                    Log.d(TAG, "Swipe completed");
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    Log.d(TAG, "Swipe cancelled");
                }
            }, null);
        } else {
            performSwipeLegacy(x1, y1, x2, y2);
        }
    }

    public void performLongClick(int x, int y, int duration) {
        if (!isClickThroughEnabled) {
            Log.d(TAG, "Click-through is disabled");
            return;
        }
        
        Log.d(TAG, "Performing long click at: " + x + ", " + y + " duration: " + duration);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Path clickPath = new Path();
            clickPath.moveTo(x, y);
            
            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(clickPath, 0, duration));
            
            dispatchGesture(gestureBuilder.build(), null, null);
        }
    }

    public void performDoubleClick(int x, int y, int delay) {
        if (!isClickThroughEnabled) {
            Log.d(TAG, "Click-through is disabled");
            return;
        }
        
        Log.d(TAG, "Performing double click at: " + x + ", " + y);
        
        performClick(x, y);
        mainHandler.postDelayed(() -> performClick(x, y), delay);
    }

    public void performScroll(int x, int y, int scrollX, int scrollY) {
        if (!isClickThroughEnabled) {
            Log.d(TAG, "Click-through is disabled");
            return;
        }
        
        Log.d(TAG, "Performing scroll at: " + x + ", " + y + " scroll: " + scrollX + ", " + scrollY);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Path scrollPath = new Path();
            int startX = x - scrollX / 2;
            int startY = y - scrollY / 2;
            int endX = x + scrollX / 2;
            int endY = y + scrollY / 2;
            
            scrollPath.moveTo(startX, startY);
            scrollPath.lineTo(endX, endY);
            
            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(scrollPath, 0, 300));
            
            dispatchGesture(gestureBuilder.build(), null, null);
        }
    }

    public void performCustomGesture(ArrayList<Point> points, int duration) {
        if (!isClickThroughEnabled) {
            Log.d(TAG, "Click-through is disabled");
            return;
        }
        
        if (points == null || points.size() < 2) {
            Log.d(TAG, "Invalid points for gesture");
            return;
        }
        
        Log.d(TAG, "Performing custom gesture with " + points.size() + " points");
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Path gesturePath = new Path();
            gesturePath.moveTo(points.get(0).x, points.get(0).y);
            
            for (int i = 1; i < points.size(); i++) {
                gesturePath.lineTo(points.get(i).x, points.get(i).y);
            }
            
            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(gesturePath, 0, duration));
            
            dispatchGesture(gestureBuilder.build(), null, null);
        }
    }

    // ==================== НАХОЖДЕНИЕ ЭЛЕМЕНТОВ ====================

    public AccessibilityNodeInfoCompat findNodeByText(String text) {
        return findNodeByText(text, false);
    }

    public AccessibilityNodeInfoCompat findNodeByText(String text, boolean partialMatch) {
        if (getRootInActiveWindow() == null) return null;
        
        AccessibilityNodeInfoCompat root = AccessibilityNodeInfoCompat.wrap(getRootInActiveWindow());
        if (root == null) return null;
        
        return findNodeByTextRecursive(root, text, partialMatch);
    }

    private AccessibilityNodeInfoCompat findNodeByTextRecursive(AccessibilityNodeInfoCompat node, String text, boolean partialMatch) {
        if (node == null) return null;
        
        CharSequence nodeText = node.getText();
        if (nodeText != null) {
            String nodeTextStr = nodeText.toString();
            if (partialMatch) {
                if (nodeTextStr.toLowerCase().contains(text.toLowerCase())) {
                    return node;
                }
            } else {
                if (nodeTextStr.equals(text)) {
                    return node;
                }
            }
        }
        
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfoCompat child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfoCompat result = findNodeByTextRecursive(child, text, partialMatch);
                if (result != null) {
                    return result;
                }
            }
        }
        
        return null;
    }

    public AccessibilityNodeInfoCompat findNodeById(String id) {
        if (getRootInActiveWindow() == null) return null;
        
        AccessibilityNodeInfoCompat root = AccessibilityNodeInfoCompat.wrap(getRootInActiveWindow());
        if (root == null) return null;
        
        return findNodeByIdRecursive(root, id);
    }

    private AccessibilityNodeInfoCompat findNodeByIdRecursive(AccessibilityNodeInfoCompat node, String id) {
        if (node == null) return null;
        
        if (node.getViewIdResourceName() != null && node.getViewIdResourceName().equals(id)) {
            return node;
        }
        
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfoCompat child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfoCompat result = findNodeByIdRecursive(child, id);
                if (result != null) {
                    return result;
                }
            }
        }
        
        return null;
    }

    public boolean performClickOnNode(AccessibilityNodeInfoCompat node) {
        if (node == null) return false;
        
        return node.performAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
    }

    public boolean performLongClickOnNode(AccessibilityNodeInfoCompat node) {
        if (node == null) return false;
        
        return node.performAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK);
    }

    public boolean performFocusOnNode(AccessibilityNodeInfoCompat node) {
        if (node == null) return false;
        
        return node.performAction(AccessibilityNodeInfoCompat.ACTION_FOCUS);
    }

    // ==================== МЕТОДЫ ДЛЯ СТАРЫХ ВЕРСИЙ ANDROID ====================

    private void performClickLegacy(int x, int y) {
        // Для API < 24 используем альтернативный метод через события
        // В реальном приложении здесь была бы реализация через AccessibilityService
        Log.d(TAG, "Legacy click at: " + x + ", " + y);
        
        // Имитация клика через системные события
        long downTime = System.currentTimeMillis();
        MotionEvent downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent upEvent = MotionEvent.obtain(downTime, downTime + 100, MotionEvent.ACTION_UP, x, y, 0);
        
        // Здесь должен быть код для отправки событий
        // В реальном приложении это делается через AccessibilityService
        
        downEvent.recycle();
        upEvent.recycle();
        
        totalClicks++;
        lastActionTime = System.currentTimeMillis();
    }

    private void performSwipeLegacy(int x1, int y1, int x2, int y2) {
        Log.d(TAG, "Legacy swipe from (" + x1 + "," + y1 + ") to (" + x2 + "," + y2 + ")");
        
        totalSwipes++;
        lastActionTime = System.currentTimeMillis();
    }

    // ==================== УПРАВЛЕНИЕ ОЧЕРЕДЬЮ ====================

    public void addActionToQueue(Runnable action) {
        actionQueue.add(action);
        if (!isProcessingQueue) {
            processQueue();
        }
    }

    private void processQueue() {
        if (actionQueue.isEmpty()) {
            isProcessingQueue = false;
            return;
        }
        
        isProcessingQueue = true;
        Runnable action = actionQueue.remove(0);
        executor.execute(() -> {
            try {
                action.run();
            } catch (Exception e) {
                Log.e(TAG, "Error processing action", e);
            } finally {
                mainHandler.post(this::processQueue);
            }
        });
    }

    public void clearQueue() {
        actionQueue.clear();
        isProcessingQueue = false;
    }

    public int getQueueSize() {
        return actionQueue.size();
    }

    // ==================== СТАТИСТИКА ====================

    public String getStatistics() {
        return "Кликов: " + totalClicks + 
               "\nСвайпов: " + totalSwipes +
               "\nВремя выполнения: " + totalExecutionTime + "ms";
    }

    public void resetStatistics() {
        totalClicks = 0;
        totalSwipes = 0;
        totalExecutionTime = 0;
    }

    public int getTotalClicks() {
        return totalClicks;
    }

    public int getTotalSwipes() {
        return totalSwipes;
    }

    public long getLastActionTime() {
        return lastActionTime;
    }

    // ==================== НАСТРОЙКИ ====================

    public void setClickThroughEnabled(boolean enabled) {
        this.isClickThroughEnabled = enabled;
        Log.d(TAG, "Click-through: " + (enabled ? "enabled" : "disabled"));
        
        if (enabled) {
            showOverlay();
        } else {
            hideOverlay();
        }
    }

    public boolean isClickThroughEnabled() {
        return isClickThroughEnabled;
    }

    public boolean isAccessibilityServiceEnabled() {
        String service = getPackageName() + "/" + MacroService.class.getCanonicalName();
        String enabledServices = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabledServices != null && enabledServices.contains(service);
    }

    public void checkAndStartService() {
        if (!isAccessibilityServiceEnabled()) {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ КЛАССЫ ====================

    public static class Point {
        public int x, y;
        
        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    // ==================== КОНСТАНТЫ ЦВЕТОВ ====================

    private static class Color {
        static final int WHITE = 0xFFFFFFFF;
        static final int BLACK = 0xFF000000;
        static final int RED = 0xFFFF0000;
        static final int GREEN = 0xFF00FF00;
        static final int BLUE = 0xFF0000FF;
        static final int YELLOW = 0xFFFFFF00;
        static final int CYAN = 0xFF00FFFF;
        static final int MAGENTA = 0xFFFF00FF;
    }

    // ==================== ТЕСТОВЫЕ МЕТОДЫ ====================

    public void testClick() {
        if (!isClickThroughEnabled) {
            Toast.makeText(this, "Click-through is disabled", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Тестовый клик в центре экрана
        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager != null) {
            android.view.Display display = windowManager.getDefaultDisplay();
            android.graphics.Point size = new android.graphics.Point();
            display.getSize(size);
            
            int x = size.x / 2;
            int y = size.y / 2;
            
            performClick(x, y);
            Toast.makeText(this, "Test click at: " + x + ", " + y, Toast.LENGTH_SHORT).show();
        }
    }

    public void testSwipe() {
        if (!isClickThroughEnabled) {
            Toast.makeText(this, "Click-through is disabled", Toast.LENGTH_SHORT).show();
            return;
        }
        
        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager != null) {
            android.view.Display display = windowManager.getDefaultDisplay();
            android.graphics.Point size = new android.graphics.Point();
            display.getSize(size);
            
            int x1 = size.x / 3;
            int y1 = size.y / 2;
            int x2 = size.x * 2 / 3;
            int y2 = size.y / 2;
            
            performSwipe(x1, y1, x2, y2, 500);
            Toast.makeText(this, "Test swipe", Toast.LENGTH_SHORT).show();
        }
    }
                }
