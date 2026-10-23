package com.cr.arcade;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.view.accessibility.AccessibilityEvent;

/** Выполняет клики/свайпы. Не блокирует ваши собственные касания. */
public class MacroService extends AccessibilityService {
    public static volatile MacroService instance;

    @Override protected void onServiceConnected() { instance = this; }
    @Override public boolean onUnbind(android.content.Intent i) { instance = null; return super.onUnbind(i); }
    @Override public void onDestroy() { instance = null; super.onDestroy(); }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {}
    @Override public void onInterrupt() {}

    /** tap: x1==x2 && y1==y2. durMs - длительность касания. */
    public boolean gesture(int x1, int y1, int x2, int y2, long durMs) {
        try {
            Path p = new Path();
            p.moveTo(Math.max(0, x1), Math.max(0, y1));
            if (x1 != x2 || y1 != y2) p.lineTo(Math.max(0, x2), Math.max(0, y2));
            GestureDescription.StrokeDescription sd =
                    new GestureDescription.StrokeDescription(p, 0, Math.max(1, durMs));
            return dispatchGesture(new GestureDescription.Builder().addStroke(sd).build(), null, null);
        } catch (Exception e) {
            return false;
        }
    }
}
