package com.cr.arcade;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ScreenReaderService extends AccessibilityService {

    private static final String TAG = "ScreenReaderService";
    private static ScreenReaderService instance;
    private WindowManager windowManager;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Set<String> detectedNames = new HashSet<>();
    private List<View> tagViews = new ArrayList<>();
    private boolean isScanning = false;

    // Список друзей (синхронизируется с MainActivity)
    private ArrayList<FriendData> friends = new ArrayList<>();
    private boolean friendsLoaded = false;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!isScanning) return;
        
        // Загружаем друзей из SharedPreferences при первом событии
        if (!friendsLoaded) {
            loadFriends();
            friendsLoaded = true;
        }

        if (friends.isEmpty()) return;

        // Получаем корневой узел текущего окна
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        // Сканируем все текстовые узлы на экране
        scanForNames(root);
        root.recycle();
    }

    private void scanForNames(AccessibilityNodeInfo node) {
        if (node == null) return;

        // Проверяем текст узла
        CharSequence text = node.getText();
        if (text != null && !TextUtils.isEmpty(text)) {
            String textStr = text.toString().trim();
            // Проверяем, есть ли этот текст в списке друзей
            for (FriendData friend : friends) {
                if (textStr.contains(friend.name) || friend.name.contains(textStr)) {
                    // Нашли ник на экране!
                    Rect bounds = new Rect();
                    node.getBoundsInScreen(bounds);
                    
                    // Показываем тег в этом месте
                    showTagOnScreen(friend, bounds);
                    break;
                }
            }
        }

        // Рекурсивно проходим по всем дочерним узлам
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                scanForNames(child);
                child.recycle();
            }
        }
    }

    private void showTagOnScreen(FriendData friend, Rect bounds) {
        try {
            if (windowManager == null) {
                windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            }

            // Проверяем, есть ли уже тег для этого друга
            for (View view : tagViews) {
                if (view.getTag() != null && view.getTag().equals(friend.name)) {
                    // Обновляем позицию
                    updateTagPosition(view, bounds);
                    return;
                }
            }

            // Создаем новый тег
            View tagView = createTagView(friend);
            tagView.setTag(friend.name);
            
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                            WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );
            
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = bounds.left;
            params.y = bounds.top - 30; // Немного выше текста
            
            windowManager.addView(tagView, params);
            tagViews.add(tagView);
            
            // Удаляем тег через 3 секунды, если он не обновился
            handler.postDelayed(() -> {
                removeTag(friend.name);
            }, 3000);

        } catch (Exception e) {
            Log.e(TAG, "Error showing tag", e);
        }
    }

    private void updateTagPosition(View tagView, Rect bounds) {
        try {
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) tagView.getLayoutParams();
            params.x = bounds.left;
            params.y = bounds.top - 30;
            if (windowManager != null) {
                windowManager.updateViewLayout(tagView, params);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating tag position", e);
        }
    }

    private View createTagView(FriendData friend) {
        try {
            LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
            LinearLayout tagLayout = new LinearLayout(this);
            tagLayout.setOrientation(LinearLayout.HORIZONTAL);
            tagLayout.setGravity(Gravity.CENTER);
            tagLayout.setPadding(12, 6, 12, 6);
            
            // Цвет зависит от тега
            String color = friend.tag.equals("Враг") ? "#F44336" : "#4CAF50";
            
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(20);
            bg.setColor(android.graphics.Color.parseColor("#CC000000"));
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
            
            // Делаем клики проходящими сквозь
            tagLayout.setClickable(false);
            tagLayout.setFocusable(false);
            
            return tagLayout;
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating tag view", e);
            return null;
        }
    }

    private void removeTag(String name) {
        try {
            for (int i = tagViews.size() - 1; i >= 0; i--) {
                View view = tagViews.get(i);
                if (view.getTag() != null && view.getTag().equals(name)) {
                    if (windowManager != null) {
                        windowManager.removeView(view);
                    }
                    tagViews.remove(i);
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error removing tag", e);
        }
    }

    public void setScanning(boolean enabled) {
        this.isScanning = enabled;
        if (!enabled) {
            // Удаляем все теги
            for (View view : tagViews) {
                try {
                    if (windowManager != null) {
                        windowManager.removeView(view);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error removing view", e);
                }
            }
            tagViews.clear();
        }
    }

    public void loadFriends() {
        try {
            friends.clear();
            android.content.SharedPreferences prefs = getSharedPreferences("friends_data", MODE_PRIVATE);
            String json = prefs.getString("friends_list", "");
            if (!json.isEmpty()) {
                org.json.JSONArray array = new org.json.JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    org.json.JSONObject obj = array.getJSONObject(i);
                    String name = obj.getString("name");
                    String tag = obj.getString("tag");
                    friends.add(new FriendData(name, tag));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading friends", e);
        }
    }

    public void refreshFriends() {
        friends.clear();
        loadFriends();
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Service interrupted");
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_DEFAULT |
                     AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS |
                     AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        info.notificationTimeout = 100;
        setServiceInfo(info);
        
        Log.d(TAG, "Accessibility Service Connected!");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        if (windowManager != null) {
            for (View view : tagViews) {
                try {
                    windowManager.removeView(view);
                } catch (Exception e) {}
            }
        }
    }

    public static ScreenReaderService getInstance() {
        return instance;
    }

    // Внутренний класс для данных друга
    private static class FriendData {
        String name;
        String tag;
        
        FriendData(String name, String tag) {
            this.name = name;
            this.tag = tag;
        }
    }
              }
