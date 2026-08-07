package com.cr.arcade;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class MacroService extends AccessibilityService {

    private static MacroService instance;
    private Handler handler = new Handler(Looper.getMainLooper());

    public static MacroService getInstance() {
        return instance;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    // ==================== КЛИК БЕЗ ЗАЖАТИЯ ====================
    
    public void performClick(int x, int y) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        
        try {
            // Способ 1: Используем GestureDescription (стандартный)
            Path clickPath = new Path();
            clickPath.moveTo(x, y);
            
            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            // Длительность 1мс - мгновенное нажатие и отпускание
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(clickPath, 0, 1));
            
            GestureDescription gesture = gestureBuilder.build();
            
            dispatchGesture(gesture, new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    Log.d("MacroService", "Клик выполнен: " + x + ", " + y);
                }
                
                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    Log.d("MacroService", "Клик отменен");
                }
            }, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== СВАЙП БЕЗ ЗАЖАТИЯ ====================
    
    public void performSwipe(int startX, int startY, int endX, int endY, long duration) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        
        try {
            Path swipePath = new Path();
            swipePath.moveTo(startX, startY);
            swipePath.lineTo(endX, endY);
            
            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, duration));
            
            GestureDescription gesture = gestureBuilder.build();
            
            dispatchGesture(gesture, new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    Log.d("MacroService", "Свайп выполнен");
                }
                
                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    Log.d("MacroService", "Свайп отменен");
                }
            }, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== СБРОС ВСЕХ ЖЕСТОВ ====================
    
    public void cancelAllGestures() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Path emptyPath = new Path();
                emptyPath.moveTo(0, 0);
                GestureDescription.Builder builder = new GestureDescription.Builder();
                builder.addStroke(new GestureDescription.StrokeDescription(emptyPath, 0, 1));
                dispatchGesture(builder.build(), null, null);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
