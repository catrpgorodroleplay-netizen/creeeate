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
    public void onAccessibilityEvent(AccessibilityEvent event) {}
    
    @Override
    public void onInterrupt() {}
    
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
            Log.e("MacroService", "❌ API 24+ требуется");
            return;
        }
        
        try {
            Path clickPath = new Path();
            clickPath.moveTo(x, y);
            
            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(clickPath, 0, 30));
            
            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    Log.d("MacroService", "✅ Клик выполнен: (" + x + ", " + y + ")");
                }
                
                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    Log.e("MacroService", "❌ Клик отменен: (" + x + ", " + y + ")");
                }
            }, null);
            
        } catch (Exception e) {
            Log.e("MacroService", "❌ Ошибка: " + e.getMessage());
        }
    }
    
    public boolean isRecording() {
        return isRecording;
    }
}
