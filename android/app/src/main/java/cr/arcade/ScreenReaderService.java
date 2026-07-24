package com.cr.arcade;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScreenReaderService extends AccessibilityService {

    private static final String TAG = "ScreenReaderService";
    private static ScreenReaderService instance;
    private WindowManager windowManager;
    private Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    
    // OCR
    private TextRecognizer textRecognizer;
    
    // Теги
    private Map<String, View> activeTags = new HashMap<>();
    private Map<String, Rect> tagPositions = new HashMap<>();
    private List<FriendData> friends = new ArrayList<>();
    private boolean friendsLoaded = false;
    private boolean isScanning = false;
    private boolean isCapturing = false;
    
    // Захват экрана
    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private int screenWidth, screenHeight;
    private int screenDensity;
    
    // Колбэк для обновления лога
    private OnScanUpdateListener scanListener;

    public interface OnScanUpdateListener {
        void onScanUpdate(Map<String, Boolean> detected);
    }
    private OnScanUpdateListener scanListenerInstance;

    private static class FriendData {
        String name;
        String tag;
        
        FriendData(String name, String tag) {
            this.name = name;
            this.tag = tag;
        }
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.notificationTimeout = 100;
        setServiceInfo(info);
        
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        
        // Инициализируем OCR
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        
        // Получаем размеры экрана
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;
        
        Log.d(TAG, "✅ Service Connected!");
    }

    public void startScanning(MediaProjection projection) {
        if (projection == null) {
            Log.e(TAG, "Projection is null!");
            return;
        }
        
        if (!friendsLoaded) {
            loadFriends();
        }
        if (friends.isEmpty()) {
            Log.d(TAG, "Нет друзей для сканирования");
            return;
        }
        
        this.mediaProjection = projection;
        isScanning = true;
        isCapturing = true;
        
        // Создаем захват экрана
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, 
                PixelFormat.RGBA_8888, 2);
        
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenCapture",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null, null
        );
        
        // Запускаем сканирование
        executor.execute(new ScanRunnable());
        
        Log.d(TAG, "🔍 Сканирование запущено, друзей: " + friends.size());
    }

    public void stopScanning() {
        isScanning = false;
        isCapturing = false;
        
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        
        removeAllTags();
        Log.d(TAG, "⏹ Сканирование остановлено");
    }

    private class ScanRunnable implements Runnable {
        @Override
        public void run() {
            while (isCapturing && isScanning) {
                try {
                    Image image = imageReader.acquireLatestImage();
                    if (image != null) {
                        processImage(image);
                        image.close();
                    }
                    Thread.sleep(200); // 200мс для баланса скорости и нагрузки
                } catch (Exception e) {
                    Log.e(TAG, "Ошибка сканирования: " + e.getMessage());
                }
            }
        }
    }

    private void processImage(Image image) {
        try {
            // Конвертируем Image в Bitmap
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            
            Bitmap bitmap = Bitmap.createBitmap(
                    screenWidth, screenHeight, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
            
            // Распознаем текст через ML Kit
            InputImage inputImage = InputImage.fromBitmap(bitmap, 0);
            
            textRecognizer.process(inputImage)
                .addOnSuccessListener(visionText -> {
                    // Очищаем старые позиции
                    tagPositions.clear();
                    
                    // Ищем ники в распознанном тексте
                    for (Text.TextBlock block : visionText.getTextBlocks()) {
                        for (Text.Line line : block.getLines()) {
                            String text = line.getText().trim();
                            Rect bounds = line.getBoundingBox();
                            
                            if (bounds != null && !text.isEmpty()) {
                                // Проверяем каждый ник
                                for (FriendData friend : friends) {
                                    if (text.contains(friend.name) || friend.name.contains(text)) {
                                        tagPositions.put(friend.name, bounds);
                                        Log.d(TAG, "✅ Найден ник: " + friend.name + " на позиции: " + bounds.left + "," + bounds.top);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    
                    // Обновляем теги и лог
                    handler.post(() -> {
                        updateTags();
                        notifyScanUpdate();
                    });
                    
                    bitmap.recycle();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "OCR ошибка: " + e.getMessage());
                    bitmap.recycle();
                });
            
        } catch (Exception e) {
            Log.e(TAG, "Ошибка обработки: " + e.getMessage());
        }
    }

    private void updateTags() {
        try {
            // Удаляем теги которых нет на экране
            List<String> toRemove = new ArrayList<>();
            for (String name : activeTags.keySet()) {
                if (!tagPositions.containsKey(name)) {
                    toRemove.add(name);
                }
            }
            for (String name : toRemove) {
                removeTag(name);
            }

            // Создаем/обновляем теги
            for (Map.Entry<String, Rect> entry : tagPositions.entrySet()) {
                String name = entry.getKey();
                Rect bounds = entry.getValue();
                
                FriendData friend = null;
                for (FriendData f : friends) {
                    if (f.name.equals(name)) {
                        friend = f;
                        break;
                    }
                }
                if (friend == null) continue;

                if (activeTags.containsKey(name)) {
                    updateTagPosition(name, bounds);
                } else {
                    createTag(name, friend, bounds);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Ошибка обновления тегов: " + e.getMessage());
        }
    }

    private void createTag(String name, FriendData friend, Rect bounds) {
        try {
            if (windowManager == null) return;

            View tagView = createTagView(friend);
            if (tagView == null) return;
            
            tagView.setTag(name);
            
            int flag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                    WindowManager.LayoutParams.TYPE_PHONE;
            
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    flag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );
            
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = bounds.left;
            params.y = bounds.top - 50;
            
            windowManager.addView(tagView, params);
            activeTags.put(name, tagView);
            
            Log.d(TAG, "✅ Создан тег: " + name);
            
        } catch (Exception e) {
            Log.e(TAG, "Ошибка создания тега: " + e.getMessage());
        }
    }

    private void updateTagPosition(String name, Rect bounds) {
        try {
            View tagView = activeTags.get(name);
            if (tagView == null) return;
            
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) tagView.getLayoutParams();
            params.x = bounds.left;
            params.y = bounds.top - 50;
            
            if (windowManager != null) {
                windowManager.updateViewLayout(tagView, params);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Ошибка обновления позиции: " + e.getMessage());
        }
    }

    private void removeTag(String name) {
        try {
            View tagView = activeTags.get(name);
            if (tagView != null && windowManager != null) {
                windowManager.removeView(tagView);
                activeTags.remove(name);
                Log.d(TAG, "❌ Удален тег: " + name);
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка удаления тега: " + e.getMessage());
        }
    }

    private void removeAllTags() {
        try {
            for (String name : new ArrayList<>(activeTags.keySet())) {
                removeTag(name);
            }
            activeTags.clear();
            tagPositions.clear();
        } catch (Exception e) {
            Log.e(TAG, "Ошибка удаления всех тегов: " + e.getMessage());
        }
    }

    private View createTagView(FriendData friend) {
        try {
            LinearLayout tagLayout = new LinearLayout(this);
            tagLayout.setOrientation(LinearLayout.HORIZONTAL);
            tagLayout.setGravity(Gravity.CENTER);
            tagLayout.setPadding(12, 6, 12, 6);
            
            String color = friend.tag.equals("Враг") ? "#F44336" : "#4CAF50";
            
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(20);
            bg.setColor(android.graphics.Color.parseColor("#DD000000"));
            bg.setStroke(3, android.graphics.Color.parseColor(color));
            tagLayout.setBackground(bg);
            
            TextView nameText = new TextView(this);
            nameText.setText(friend.name);
            nameText.setTextColor(android.graphics.Color.WHITE);
            nameText.setTextSize(14);
            nameText.setTypeface(null, android.graphics.Typeface.BOLD);
            nameText.setPadding(8, 0, 4, 0);
            tagLayout.addView(nameText);
            
            TextView tagText = new TextView(this);
            tagText.setText(friend.tag);
            tagText.setTextColor(android.graphics.Color.parseColor(color));
            tagText.setTextSize(12);
            tagText.setTypeface(null, android.graphics.Typeface.BOLD);
            tagText.setPadding(4, 2, 8, 2);
            tagLayout.addView(tagText);
            
            TextView icon = new TextView(this);
            icon.setText(friend.tag.equals("Враг") ? "⚔️" : "🤝");
            icon.setTextSize(16);
            tagLayout.addView(icon);
            
            tagLayout.setClickable(false);
            tagLayout.setFocusable(false);
            
            return tagLayout;
            
        } catch (Exception e) {
            Log.e(TAG, "Ошибка создания тега: " + e.getMessage());
            return null;
        }
    }

    private void notifyScanUpdate() {
        if (scanListenerInstance != null) {
            Map<String, Boolean> detected = new HashMap<>();
            for (FriendData friend : friends) {
                detected.put(friend.name, tagPositions.containsKey(friend.name));
            }
            scanListenerInstance.onScanUpdate(detected);
        }
    }

    public void setScanListener(OnScanUpdateListener listener) {
        this.scanListenerInstance = listener;
    }

    public void loadFriends() {
        try {
            friends.clear();
            SharedPreferences prefs = getSharedPreferences("friends_data", MODE_PRIVATE);
            String json = prefs.getString("friends_list", "");
            if (!json.isEmpty()) {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    friends.add(new FriendData(obj.getString("name"), obj.getString("tag")));
                }
            }
            friendsLoaded = true;
            Log.d(TAG, "Загружено друзей: " + friends.size());
        } catch (Exception e) {
            Log.e(TAG, "Ошибка загрузки друзей: " + e.getMessage());
        }
    }

    public void refreshFriends() {
        friends.clear();
        friendsLoaded = false;
        loadFriends();
        if (isScanning) {
            removeAllTags();
        }
    }

    public boolean isScanning() {
        return isScanning;
    }

    public List<String> getFriendNames() {
        List<String> names = new ArrayList<>();
        for (FriendData f : friends) {
            names.add(f.name);
        }
        return names;
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Service interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        stopScanning();
        removeAllTags();
        if (textRecognizer != null) {
            textRecognizer.close();
        }
    }

    public static ScreenReaderService getInstance() {
        return instance;
    }
}
