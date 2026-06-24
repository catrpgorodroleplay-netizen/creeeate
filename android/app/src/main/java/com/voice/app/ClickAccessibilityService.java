package com.voice.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;
import java.util.ArrayList;

public class ClickAccessibilityService extends AccessibilityService {

    private static ClickAccessibilityService instance;

    public static boolean isRunning() { return instance != null; }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    public void onCreate() { super.onCreate(); instance = this; }

    @Override
    public void onDestroy() { instance = null; super.onDestroy(); }

    public static void performClick(ArrayList<MainActivity.ClickPoint> points) {
        if (instance == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        for (MainActivity.ClickPoint point : points) {
            Path clickPath = new Path();
            clickPath.moveTo(point.x, point.y);
            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(clickPath, 0, 1));
            instance.dispatchGesture(gestureBuilder.build(), null, null);
        }
    }
}
