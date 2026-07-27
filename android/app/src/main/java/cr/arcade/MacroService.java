package com.cr.arcade;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.RequiresApi;

public class MacroService extends AccessibilityService {
    
    private static MacroService instance;
    private RecordingListener listener;
    private boolean isRecording = false;
    private long lastActionTime = 0;
    
    public interface RecordingListener {
        void onActionRecorded(int x, int y, long delay);
        void onRecordingStopped();
    }
    
    public static MacroService getInstance() {
        return instance;
    }
    
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Не используем для записи
    }
    
    @Override
    public void onInterrupt() {
        Log.d("MacroService", "Сервис прерван");
    }
    
    @Override
    public void onServiceConnected() {
        instance = this;
        Log.d("MacroService", "✅ Сервис подключен!");
    }
    
    public void startRecording(RecordingListener listener) {
        this.listener = listener;
        this.isRecording = true;
        this.lastActionTime = System.currentTimeMillis();
        Log.d("MacroService", "🔴 Запись начата");
    }
    
    public void stopRecording() {
        this.isRecording = false;
        if (listener != null) {
            listener.onRecordingStopped();
        }
        Log.d("MacroService", "⏹ Запись остановлена");
    }
    
    public void stopRecordingFromActivity() {
        this.isRecording = false;
        Log.d("MacroService", "Запись остановлена из Activity");
    }
    
    public void recordClick(int x, int y) {
        if (isRecording && listener != null) {
            long currentTime = System.currentTimeMillis();
            long delay = currentTime - lastActionTime;
            lastActionTime = currentTime;
            listener.onActionRecorded(x, y, delay);
            Log.d("MacroService", "📝 Записан клик: (" + x + ", " + y + ")");
        }
    }
    
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void performClick(int x, int y) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.e("MacroService", "❌ API 24+ требуется для кликов");
            return;
        }
        
        try {
            Path clickPath = new Path();
            clickPath.moveTo(x, y);
            
            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(clickPath, 0, 30));
            
            boolean result = dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    Log.d("MacroService", "✅ Клик выполнен: (" + x + ", " + y + ")");
                }
                
                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    Log.e("MacroService", "❌ Клик отменен: (" + x + ", " + y + ")");
                }
            }, null);
            
            if (!result) {
                Log.e("MacroService", "❌ Не удалось выполнить клик");
            }
            
        } catch (Exception e) {
            Log.e("MacroService", "❌ Ошибка клика: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void performSwipe(int x1, int y1, int x2, int y2, long duration) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }
        
        try {
            Path swipePath = new Path();
            swipePath.moveTo(x1, y1);
            swipePath.lineTo(x2, y2);
            
            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, duration));
            
            dispatchGesture(gestureBuilder.build(), null, null);
            Log.d("MacroService", "✅ Свайп выполнен");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public boolean isRecording() {
        return isRecording;
    }
}
