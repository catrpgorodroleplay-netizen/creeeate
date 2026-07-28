package com.cr.arcade;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.RequiresApi;

import java.util.Random;

public class MacroService extends AccessibilityService {
    private static MacroService instance;
    private Random random = new Random();

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
        super.onDestroy();
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
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(clickPath, 0, 1));
            dispatchGesture(builder.build(), null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void performClickWithOffset(int x, int y, int offset) {
        int ox = random.nextInt(offset * 2) - offset;
        int oy = random.nextInt(offset * 2) - offset;
        performClick(x + ox, y + oy);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void performSwipe(int startX, int startY, int endX, int endY, long duration) {
        try {
            Path path = new Path();
            path.moveTo(startX, startY);
            path.lineTo(endX, endY);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, duration));
            dispatchGesture(builder.build(), null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void performLongPress(int x, int y, long duration) {
        try {
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, duration));
            dispatchGesture(builder.build(), null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
