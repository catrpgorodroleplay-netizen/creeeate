package com.cr.arcade;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScreenReaderService extends AccessibilityService {

    private static final String TAG = "ScreenReaderService";
    private static ScreenReaderService instance;
    private WindowManager windowManager;
    private Handler handler = new Handler(Looper.getMainLooper());
    
    // Активные теги на экране
    private Map<String, View> activeTags = new HashMap<>();
    private Map<String, Rect> tagPositions = new HashMap<>();
    
    // Список друзей
    private List<FriendData> friends = new ArrayList<>();
    private boolean friendsLoaded = false;
    private boolean isScanning = false;
    
    // Обновление тегов
    private Runnable updateTagsRunnable = new Runnable() {
        @Override
        public void run() {
            if (isScanning) {
                scanScreen();
                handler.postDelayed(this, 100); // Обновляем каждые 100мс для плавности
            }
        }
    };

    private static class FriendData {
        String name;
        String tag;
        
        FriendData(String name, String tag) {
            this.name = name;
            this.tag = tag;
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Не используем событийный метод, сканируем по таймеру
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
        
        Log.d(TAG, "✅ Accessibility Service Connected!");
    }

    public void startScanning() {
        if (!friendsLoaded) {
            loadFriends();
        }
        if (friends.isEmpty()) {
            Log.d(TAG, "Нет друзей для сканирования");
            return;
        }
        isScanning = true;
        handler.post(updateTagsRunnable);
        Log.d(TAG, "🔍 Сканирование запущено, друзей: " + friends.size());
    }

    public void stopScanning() {
        isScanning = false;
        handler.removeCallbacks(updateTagsRunnable);
        removeAllTags();
        Log.d(TAG, "⏹ Сканирование остановлено");
    }

    private void scanScreen() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                // Если ничего не видно - удаляем все теги
                if (!activeTags.isEmpty()) {
                    removeAllTags();
                }
                return;
            }

            // Очищаем старые позиции
            tagPositions.clear();
            
            // Сканируем все текстовые узлы
            scanNode(root);
            root.recycle();

            // Обновляем теги
            updateTags();

        } catch (Exception e) {
            Log.e(TAG, "Ошибка сканирования: " + e.getMessage());
        }
    }

    private void scanNode(AccessibilityNodeInfo node) {
        if (node == null) return;

        // Проверяем текст узла
        CharSequence text = node.getText();
        if (text != null && !TextUtils.isEmpty(text)) {
            String textStr = text.toString().trim();
            
            // Проверяем каждый ник
            for (FriendData friend : friends) {
                if (textStr.contains(friend.name) || friend.name.contains(textStr)) {
                    // Нашли ник!
                    Rect bounds = new Rect();
                    node.getBoundsInScreen(bounds);
                    
                    // Проверяем что ник видим на экране
                    if (bounds.width() > 0 && bounds.height() > 0) {
                        // Сохраняем позицию
                        tagPositions.put(friend.name, bounds);
                        Log.d(TAG, "Найден ник: " + friend.name + " на позиции: " + bounds.left + "," + bounds.top);
                        break;
                    }
                }
            }
        }

        // Рекурсивно проходим по всем дочерним узлам
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                scanNode(child);
                child.recycle();
            }
        }
    }

    private void updateTags() {
        try {
            // Удаляем теги, которых больше нет на экране
            List<String> toRemove = new ArrayList<>();
            for (String name : activeTags.keySet()) {
                if (!tagPositions.containsKey(name)) {
                    toRemove.add(name);
                }
            }
            for (String name : toRemove) {
                removeTag(name);
            }

            // Обновляем или создаем теги для найденных ников
            for (Map.Entry<String, Rect> entry : tagPositions.entrySet()) {
                String name = entry.getKey();
                Rect bounds = entry.getValue();
                
                // Находим данные друга
                FriendData friend = null;
                for (FriendData f : friends) {
                    if (f.name.equals(name)) {
                        friend = f;
                        break;
                    }
                }
                if (friend == null) continue;

                // Обновляем существующий тег или создаем новый
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

            // Создаем тег
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
            
            // Ставим тег ровно над ником
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = bounds.left;
            params.y = bounds.top - 50; // Немного выше ника
            
            windowManager.addView(tagView, params);
            activeTags.put(name, tagView);
            
            Log.d(TAG, "✅ Создан тег для: " + name + " на позиции: " + params.x + "," + params.y);
            
        } catch (Exception e) {
            Log.e(TAG, "Ошибка создания тега: " + e.getMessage());
        }
    }

    private void updateTagPosition(String name, Rect bounds) {
        try {
            View tagView = activeTags.get(name);
            if (tagView == null) return;
            
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) tagView.getLayoutParams();
            
            // Обновляем позицию ровно над ником
            params.x = bounds.left;
            params.y = bounds.top - 50;
            
            if (windowManager != null) {
                windowManager.updateViewLayout(tagView, params);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Ошибка обновления позиции тега: " + e.getMessage());
        }
    }

    private void removeTag(String name) {
        try {
            View tagView = activeTags.get(name);
            if (tagView != null && windowManager != null) {
                windowManager.removeView(tagView);
                activeTags.remove(name);
                Log.d(TAG, "❌ Удален тег для: " + name);
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
            
            // Имя
            TextView nameText = new TextView(this);
            nameText.setText(friend.name);
            nameText.setTextColor(android.graphics.Color.WHITE);
            nameText.setTextSize(14);
            nameText.setTypeface(null, android.graphics.Typeface.BOLD);
            nameText.setPadding(8, 0, 4, 0);
            tagLayout.addView(nameText);
            
            // Тег
            TextView tagText = new TextView(this);
            tagText.setText(friend.tag);
            tagText.setTextColor(android.graphics.Color.parseColor(color));
            tagText.setTextSize(12);
            tagText.setTypeface(null, android.graphics.Typeface.BOLD);
            tagText.setPadding(4, 2, 8, 2);
            tagLayout.addView(tagText);
            
            // Иконка
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
    }

    public static ScreenReaderService getInstance() {
        return instance;
    }
            }
