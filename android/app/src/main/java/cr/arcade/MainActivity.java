package com.cr.arcade;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.BridgeActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends BridgeActivity {

    // ==================== КОНСТАНТЫ ====================
    private static final int REQUEST_OVERLAY_PERMISSION = 101;
    private static final int REQUEST_GALLERY = 103;
    private static final int REQUEST_ACCESSIBILITY = 107;
    private static final int REQUEST_IMPORT_CONFIG = 108;

    private static final String PREFS_NAME = "arcade_data";
    private static final String TAG = "CRArcade";

    // ==================== ОСНОВНЫЕ ПЕРЕМЕННЫЕ ====================
    private WindowManager windowManager;
    private SharedPreferences prefs;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private long backPressedTime = 0;
    private boolean isAppInForeground = true;

    // ==================== ПЕРЕМЕННЫЕ ДЛЯ ПЕРЕТАСКИВАНИЯ ====================
    private float startX, startY;
    private int initialX, initialY;
    private boolean isDragging = false;

    // ==================== НАСТРОЙКИ ====================
    private int primaryColor = 0xFFFF0000;
    private int overlayAlpha = 200;
    private int overlaySize = 80;
    private boolean buttonsVisible = true;
    private boolean rainbowMode = false;
    private float rainbowHue = 0;
    private boolean recordPassThrough = true;

    // ==================== ГЛАВНЫЙ ОВЕРЛЕЙ ====================
    private FrameLayout mainOverlay;
    private WindowManager.LayoutParams mainOverlayParams;
    private FrameLayout mainCircleContainer;
    private WindowManager.LayoutParams mainCircleParams;

    // ==================== ОКНА В ОВЕРЛЕЕ ====================
    private HashMap<String, FloatingWindow> windows = new HashMap<>();
    private ArrayList<String> windowOrder = new ArrayList<>();
    private boolean isMainMenuOpen = false;

    // ==================== МАКРОСЫ ====================
    private ArrayList<MacroConfig> macroConfigs = new ArrayList<>();
    private String currentMacroName = "Макрос 1";
    private MacroConfig runningConfig = null;
    private boolean isMacroRunning = false;
    private boolean isMacroPaused = false;
    private boolean stopRequested = false;
    private int currentPointIndex = 0;
    private int currentRepeatCount = 0;
    private long macroStartTime = 0;

    // ==================== ЗАПИСЬ МАКРОСА ====================
    private FrameLayout recordingOverlay;
    private FrameLayout recordingTouchOverlay;
    private TextView recordingStatusText;
    private TextView recordingTimeText;
    private Button stopRecordBtn;
    private Button pauseRecordBtn;
    private boolean isRecordingMode = false;
    private boolean isRecordingPaused = false;
    private ArrayList<RecordedAction> recordedActions = new ArrayList<>();
    private long recordingStartTime = 0;
    private long recordingPauseTime = 0;
    private long lastActionTime = 0;
    private float lastRawX = 0;
    private float lastRawY = 0;
    private boolean isSwiping = false;
    private int recordedClickCount = 0;
    private int recordedSwipeCount = 0;

    // ==================== АВТО КЛИКЕР ====================
    private boolean isAutoClickerRunning = false;
    private boolean isAutoClickerPaused = false;
    private FrameLayout autoClickerOverlay;
    private boolean isAutoClickerRecording = false;

    // ==================== ПЛАВАЮЩИЕ КНОПКИ ====================
    private HashMap<String, FloatingButton> floatingButtons = new HashMap<>();
    private HashMap<String, MacroConfig> buttonMacroMap = new HashMap<>();

    // ==================== ПЕРСОНАЖИ ====================
    private ArrayList<CharacterData> characters = new ArrayList<>();
    private String tempCharacterName = "";

    // ==================== ВСПОМОГАТЕЛЬНЫЕ КЛАССЫ ====================

    private static class MacroConfig {
        String name;
        String type;
        ArrayList<MacroPoint> points;
        ArrayList<RecordedAction> actions;
        int color;
        boolean isLoop = false;
        int loopCount = 1;
        String buttonName = "";
        int buttonColor = 0xFFFF0000;
        int buttonSize = 80;
        int buttonX = 200;
        int buttonY = 300;
        boolean buttonFixed = false;
        long createdTime;
        String description = "";
        boolean isEnabled = true;
        
        MacroConfig(String name) {
            this.name = name;
            this.type = "recorded";
            this.points = new ArrayList<>();
            this.actions = new ArrayList<>();
            this.color = 0xFFFF0000;
            this.createdTime = System.currentTimeMillis();
        }
        
        MacroConfig(JSONObject json) throws Exception {
            this.name = json.getString("name");
            this.type = json.optString("type", "recorded");
            this.color = json.optInt("color", 0xFFFF0000);
            this.isLoop = json.optBoolean("isLoop", false);
            this.loopCount = json.optInt("loopCount", 1);
            this.buttonName = json.optString("buttonName", "");
            this.buttonColor = json.optInt("buttonColor", 0xFFFF0000);
            this.buttonSize = json.optInt("buttonSize", 80);
            this.buttonX = json.optInt("buttonX", 200);
            this.buttonY = json.optInt("buttonY", 300);
            this.buttonFixed = json.optBoolean("buttonFixed", false);
            this.createdTime = json.optLong("createdTime", System.currentTimeMillis());
            this.description = json.optString("description", "");
            this.isEnabled = json.optBoolean("isEnabled", true);
            
            this.points = new ArrayList<>();
            this.actions = new ArrayList<>();
            
            JSONArray pointsArray = json.optJSONArray("points");
            if (pointsArray != null) {
                for (int i = 0; i < pointsArray.length(); i++) {
                    this.points.add(new MacroPoint(pointsArray.getJSONObject(i)));
                }
            }
            
            JSONArray actionsArray = json.optJSONArray("actions");
            if (actionsArray != null) {
                for (int i = 0; i < actionsArray.length(); i++) {
                    this.actions.add(new RecordedAction(actionsArray.getJSONObject(i)));
                }
            }
        }
        
        JSONObject toJSON() throws Exception {
            JSONObject json = new JSONObject();
            json.put("name", name);
            json.put("type", type);
            json.put("color", color);
            json.put("isLoop", isLoop);
            json.put("loopCount", loopCount);
            json.put("buttonName", buttonName);
            json.put("buttonColor", buttonColor);
            json.put("buttonSize", buttonSize);
            json.put("buttonX", buttonX);
            json.put("buttonY", buttonY);
            json.put("buttonFixed", buttonFixed);
            json.put("createdTime", createdTime);
            json.put("description", description);
            json.put("isEnabled", isEnabled);
            
            JSONArray pointsArray = new JSONArray();
            for (MacroPoint p : points) pointsArray.put(p.toJSON());
            json.put("points", pointsArray);
            
            JSONArray actionsArray = new JSONArray();
            for (RecordedAction a : actions) actionsArray.put(a.toJSON());
            json.put("actions", actionsArray);
            
            return json;
        }
    }

    private static class MacroPoint {
        int x, y, delay;
        int repeatCount = 1;
        boolean randomOffset = false;
        int offsetRange = 10;
        String actionType = "click";
        int x2 = 0, y2 = 0;
        int swipeDuration = 500;
        String comment = "";
        
        MacroPoint(int x, int y) {
            this.x = x;
            this.y = y;
            this.delay = 1000;
        }
        
        MacroPoint(int x, int y, int delay) {
            this.x = x;
            this.y = y;
            this.delay = delay;
        }
        
        MacroPoint(JSONObject json) throws Exception {
            this.x = json.getInt("x");
            this.y = json.getInt("y");
            this.delay = json.optInt("delay", 1000);
            this.repeatCount = json.optInt("repeatCount", 1);
            this.randomOffset = json.optBoolean("randomOffset", false);
            this.offsetRange = json.optInt("offsetRange", 10);
            this.actionType = json.optString("actionType", "click");
            this.x2 = json.optInt("x2", x);
            this.y2 = json.optInt("y2", y);
            this.swipeDuration = json.optInt("swipeDuration", 500);
            this.comment = json.optString("comment", "");
        }
        
        JSONObject toJSON() throws Exception {
            JSONObject json = new JSONObject();
            json.put("x", x);
            json.put("y", y);
            json.put("delay", delay);
            json.put("repeatCount", repeatCount);
            json.put("randomOffset", randomOffset);
            json.put("offsetRange", offsetRange);
            json.put("actionType", actionType);
            json.put("x2", x2);
            json.put("y2", y2);
            json.put("swipeDuration", swipeDuration);
            json.put("comment", comment);
            return json;
        }
    }

    private static class RecordedAction {
        String type;
        int x1, y1, x2, y2;
        long delay;
        long duration;
        long timestamp;
        
        RecordedAction(String type, int x1, int y1, long delay) {
            this.type = type;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x1;
            this.y2 = y1;
            this.delay = delay;
            this.duration = 0;
            this.timestamp = System.currentTimeMillis();
        }
        
        RecordedAction(String type, int x1, int y1, int x2, int y2, long delay, long duration) {
            this.type = type;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.delay = delay;
            this.duration = duration;
            this.timestamp = System.currentTimeMillis();
        }
        
        JSONObject toJSON() throws Exception {
            JSONObject json = new JSONObject();
            json.put("type", type);
            json.put("x1", x1);
            json.put("y1", y1);
            json.put("x2", x2);
            json.put("y2", y2);
            json.put("delay", delay);
            json.put("duration", duration);
            json.put("timestamp", timestamp);
            return json;
        }
        
        RecordedAction(JSONObject json) throws Exception {
            type = json.getString("type");
            x1 = json.getInt("x1");
            y1 = json.getInt("y1");
            x2 = json.optInt("x2", x1);
            y2 = json.optInt("y2", y1);
            delay = json.getLong("delay");
            duration = json.optLong("duration", 0);
            timestamp = json.optLong("timestamp", System.currentTimeMillis());
        }
    }

    private static class FloatingButton {
        FrameLayout container;
        WindowManager.LayoutParams params;
        String macroName;
        int color;
        boolean isFixed = false;
        TextView label;
        String buttonId;
        boolean isVisible = true;
        int size = 80;
        boolean isSquare = false;
        int cornerRadius = 50;
    }

    private static class FloatingWindow {
        FrameLayout container;
        WindowManager.LayoutParams params;
        View contentView;
        String type;
        String title = "Окно";
        boolean isMinimized = false;
        boolean isResizing = false;
        int minWidth = 300;
        int minHeight = 250;
        int lastTouchX, lastTouchY;
        int startWidth, startHeight;
        LinearLayout titleBar;
        View resizeHandle;
        boolean isDraggable = true;
        boolean isResizable = true;
        boolean isClosable = true;
        boolean isMinimizable = true;
        int cornerRadius = 16;
        int borderColor = 0xFFFF0000;
        int borderWidth = 3;
        int backgroundColor = 0xDD0D0D0D;
        int savedWidth, savedHeight;
        String icon = "📦";
        float alpha = 1.0f;
    }

    private static class CharacterData {
        String name;
        String path;
        long timestamp;
        int width;
        int height;
        boolean isFavorite = false;
        String category = "default";
        String description = "";
        
        CharacterData(String name, String path) {
            this.name = name;
            this.path = path;
            this.timestamp = System.currentTimeMillis();
            this.width = 300;
            this.height = 300;
        }
        
        CharacterData(JSONObject json) throws Exception {
            this.name = json.getString("name");
            this.path = json.getString("path");
            this.timestamp = json.optLong("timestamp", System.currentTimeMillis());
            this.width = json.optInt("width", 300);
            this.height = json.optInt("height", 300);
            this.isFavorite = json.optBoolean("isFavorite", false);
            this.category = json.optString("category", "default");
            this.description = json.optString("description", "");
        }
        
        JSONObject toJSON() throws Exception {
            JSONObject json = new JSONObject();
            json.put("name", name);
            json.put("path", path);
            json.put("timestamp", timestamp);
            json.put("width", width);
            json.put("height", height);
            json.put("isFavorite", isFavorite);
            json.put("category", category);
            json.put("description", description);
            return json;
        }
    }

    // ==================== ЖИЗНЕННЫЙ ЦИКЛ ====================

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        try {
            prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            
            loadSettings();
            loadMacroConfigs();
            loadCharacters();
            checkPermissions();
            
            createMainOverlay();
            createMainCircle();
            restoreButtons();
            
        } catch (Exception e) {
            Log.e(TAG, "onCreate error", e);
            Toast.makeText(this, "Ошибка инициализации", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        isAppInForeground = true;
        removeMainCircle();
        showMainOverlay();
        showFloatingButtons();
    }

    @Override
    public void onPause() {
        super.onPause();
        isAppInForeground = false;
        hideMainOverlay();
        hideFloatingButtons();
        createMainCircle();
        saveAllData();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            cleanupResources();
        } catch (Exception e) {
            Log.e(TAG, "onDestroy error", e);
        }
    }

    private void cleanupResources() {
        if (mainOverlay != null && windowManager != null) {
            try { windowManager.removeView(mainOverlay); } catch (Exception e) {}
        }
        removeMainCircle();
        
        for (FloatingButton btn : floatingButtons.values()) {
            if (btn.container != null && windowManager != null) {
                try { windowManager.removeView(btn.container); } catch (Exception e) {}
            }
        }
        
        for (FloatingWindow win : windows.values()) {
            if (win.container != null && windowManager != null) {
                try { windowManager.removeView(win.container); } catch (Exception e) {}
            }
        }
        
        if (recordingOverlay != null && windowManager != null) {
            try { windowManager.removeView(recordingOverlay); } catch (Exception e) {}
        }
        if (recordingTouchOverlay != null && windowManager != null) {
            try { windowManager.removeView(recordingTouchOverlay); } catch (Exception e) {}
        }
        if (autoClickerOverlay != null && windowManager != null) {
            try { windowManager.removeView(autoClickerOverlay); } catch (Exception e) {}
        }
        
        mainHandler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
    }

    private void saveAllData() {
        saveMacroConfigs();
        saveCharacters();
        saveButtonPositions();
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, "onBackPressed");
        
        if (windows.size() > 0) {
            String[] keys = windows.keySet().toArray(new String[0]);
            if (keys.length > 0) {
                removeWindow(keys[keys.length - 1]);
                return;
            }
        }
        
        if (isRecordingMode) {
            stopRecordingMode();
            Toast.makeText(this, "Запись остановлена", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (isAutoClickerRunning) {
            stopAutoClicker();
            Toast.makeText(this, "Авто кликер остановлен", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (isMacroRunning) {
            stopMacroExecution();
            Toast.makeText(this, "Макрос остановлен", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (isMainMenuOpen) {
            closeMainMenu();
            return;
        }
        
        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            cleanupResources();
            finishAffinity();
            System.exit(0);
        } else {
            Toast.makeText(this, "Нажмите еще раз для выхода", Toast.LENGTH_SHORT).show();
            backPressedTime = System.currentTimeMillis();
        }
    }

    // ==================== ГЛАВНЫЙ ОВЕРЛЕЙ ====================

    private void createMainOverlay() {
        if (windowManager == null) return;
        if (mainOverlay != null) return;
        
        mainOverlay = new FrameLayout(this);
        mainOverlay.setBackgroundColor(0xCC000000);
        mainOverlay.setVisibility(View.GONE);
        
        mainOverlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                getOverlayFlag(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        
        windowManager.addView(mainOverlay, mainOverlayParams);
    }

    private void showMainOverlay() {
        if (mainOverlay != null) {
            mainOverlay.setVisibility(View.VISIBLE);
            for (FloatingWindow win : windows.values()) {
                if (win.container != null) {
                    win.container.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    private void hideMainOverlay() {
        if (mainOverlay != null) {
            mainOverlay.setVisibility(View.GONE);
        }
    }

    // ==================== ГЛАВНАЯ КНОПКА ====================

    private void createMainCircle() {
        try {
            if (isAppInForeground) {
                removeMainCircle();
                return;
            }
            
            if (windowManager == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                return;
            }
            if (mainCircleContainer != null) return;
            
            mainCircleContainer = new FrameLayout(this);
            mainCircleContainer.setBackgroundColor(Color.TRANSPARENT);
            
            FrameLayout circleButton = new FrameLayout(this);
            GradientDrawable circleBg = new GradientDrawable();
            circleBg.setShape(GradientDrawable.OVAL);
            circleBg.setColor(getThemeColor());
            circleBg.setStroke(6, Color.parseColor("#FF4444"));
            circleButton.setBackground(circleBg);
            
            TextView iconText = new TextView(this);
            iconText.setText("▶");
            iconText.setTextColor(Color.WHITE);
            iconText.setTextSize(28);
            iconText.setGravity(Gravity.CENTER);
            circleButton.addView(iconText);
            
            mainCircleContainer.addView(circleButton, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));
            
            mainCircleContainer.setAlpha(overlayAlpha / 255f);
            
            mainCircleParams = new WindowManager.LayoutParams(
                    overlaySize, overlaySize,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            mainCircleParams.gravity = Gravity.TOP | Gravity.START;
            
            int savedX = prefs.getInt("overlay_x", 100);
            int savedY = prefs.getInt("overlay_y", 200);
            mainCircleParams.x = savedX;
            mainCircleParams.y = savedY;

            mainCircleContainer.setOnTouchListener(new View.OnTouchListener() {
                private long lastTapTime = 0;
                private boolean isDragging = false;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    try {
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                startX = event.getRawX();
                                startY = event.getRawY();
                                initialX = mainCircleParams.x;
                                initialY = mainCircleParams.y;
                                isDragging = false;
                                return true;
                                
                            case MotionEvent.ACTION_MOVE:
                                float dx = event.getRawX() - startX;
                                float dy = event.getRawY() - startY;
                                if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                                    isDragging = true;
                                }
                                if (isDragging) {
                                    mainCircleParams.x = initialX + (int) dx;
                                    mainCircleParams.y = initialY + (int) dy;
                                    if (windowManager != null) {
                                        windowManager.updateViewLayout(mainCircleContainer, mainCircleParams);
                                        prefs.edit()
                                            .putInt("overlay_x", mainCircleParams.x)
                                            .putInt("overlay_y", mainCircleParams.y)
                                            .apply();
                                    }
                                }
                                return true;
                                
                            case MotionEvent.ACTION_UP:
                                if (!isDragging) {
                                    long currentTime = System.currentTimeMillis();
                                    if (currentTime - lastTapTime < 300) {
                                        showMainOverlay();
                                        showMainMenu();
                                    } else {
                                        showMainOverlay();
                                        showMainMenu();
                                    }
                                    lastTapTime = currentTime;
                                }
                                return true;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Touch error", e);
                    }
                    return false;
                }
            });

            if (windowManager != null) {
                windowManager.addView(mainCircleContainer, mainCircleParams);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "createMainCircle error", e);
        }
    }

    private void removeMainCircle() {
        try {
            if (mainCircleContainer != null && windowManager != null) {
                windowManager.removeView(mainCircleContainer);
                mainCircleContainer = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "removeMainCircle error", e);
        }
    }

    // ==================== ГЛАВНОЕ МЕНЮ ====================

    private void showMainMenu() {
        try {
            if (isMainMenuOpen) {
                closeMainMenu();
                return;
            }
            
            isMainMenuOpen = true;
            
            FloatingWindow menuWin = new FloatingWindow();
            menuWin.type = "main_menu";
            menuWin.title = "CR ARCADE";
            menuWin.isClosable = true;
            menuWin.isResizable = false;
            menuWin.isDraggable = true;
            menuWin.minWidth = 350;
            menuWin.minHeight = 400;
            menuWin.icon = "⚡";
            
            LinearLayout menuLayout = new LinearLayout(this);
            menuLayout.setOrientation(LinearLayout.VERTICAL);
            menuLayout.setPadding(20, 20, 20, 20);
            menuLayout.setBackgroundColor(0xDD0D0D0D);
            
            TextView titleText = new TextView(this);
            titleText.setText("⚡ CR ARCADE");
            titleText.setTextColor(getThemeColor());
            titleText.setTextSize(28);
            titleText.setTypeface(null, android.graphics.Typeface.BOLD);
            titleText.setGravity(Gravity.CENTER);
            titleText.setPadding(0, 0, 0, 20);
            menuLayout.addView(titleText);
            
            String[][] menuItems = {
                {"🎮", "Макросы"},
                {"🔄", "Авто кликер"},
                {"👤", "Персонажи"},
                {"⚙️", "Настройки"},
                {"📁", "Конфиги"},
                {"❌", "Закрыть"}
            };
            
            for (String[] item : menuItems) {
                Button btn = new Button(this);
                btn.setText(item[0] + " " + item[1]);
                btn.setTextColor(Color.WHITE);
                btn.setTextSize(16);
                btn.setTypeface(null, android.graphics.Typeface.BOLD);
                
                GradientDrawable btnBg = new GradientDrawable();
                btnBg.setCornerRadius(16);
                btnBg.setColor(0x33FFFFFF);
                btnBg.setStroke(2, getThemeColor());
                btn.setBackground(btnBg);
                
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                params.setMargins(0, 8, 0, 8);
                btn.setLayoutParams(params);
                
                btn.setOnClickListener(v -> {
                    handleMenuClick(item[1]);
                });
                
                menuLayout.addView(btn);
            }
            
            menuWin.contentView = menuLayout;
            menuWin.container = new FrameLayout(this);
            menuWin.container.addView(menuLayout);
            
            setupWindowAppearance(menuWin);
            setupWindowDragging(menuWin);
            
            menuWin.params = new WindowManager.LayoutParams(
                    400, 500,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            menuWin.params.gravity = Gravity.CENTER;
            
            if (windowManager != null) {
                windowManager.addView(menuWin.container, menuWin.params);
            }
            
            windows.put("main_menu", menuWin);
            windowOrder.add("main_menu");
            
        } catch (Exception e) {
            Log.e(TAG, "showMainMenu error", e);
        }
    }

    private void closeMainMenu() {
        if (windows.containsKey("main_menu")) {
            removeWindow("main_menu");
        }
        isMainMenuOpen = false;
    }

    private void handleMenuClick(String item) {
        closeMainMenu();
        
        switch (item) {
            case "Макросы":
                showMacrosWindow();
                break;
            case "Авто кликер":
                showAutoClickerWindow();
                break;
            case "Персонажи":
                showCharactersWindow();
                break;
            case "Настройки":
                showSettingsWindow();
                break;
            case "Конфиги":
                showConfigWindow();
                break;
            case "Закрыть":
                hideMainOverlay();
                break;
        }
    }

    // ==================== ОКНО МАКРОСОВ ====================

    private void showMacrosWindow() {
        try {
            if (windows.containsKey("macros")) {
                bringWindowToFront("macros");
                return;
            }

            FloatingWindow win = new FloatingWindow();
            win.type = "macros";
            win.title = "Макросы";
            win.isClosable = true;
            win.isResizable = true;
            win.isDraggable = true;
            win.isMinimizable = true;
            win.minWidth = 400;
            win.minHeight = 450;
            win.icon = "🎮";

            LinearLayout mainLayout = new LinearLayout(this);
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setPadding(16, 16, 16, 16);
            mainLayout.setBackgroundColor(0xDD0D0D0D);

            LinearLayout titleBar = createWindowTitleBar(win);
            mainLayout.addView(titleBar);

            LinearLayout selectLayout = createMacroSelector(mainLayout);
            mainLayout.addView(selectLayout);

            final LinearLayout pointsContainer = new LinearLayout(this);
            pointsContainer.setOrientation(LinearLayout.VERTICAL);
            pointsContainer.setPadding(0, 12, 0, 0);
            mainLayout.addView(pointsContainer);

            LinearLayout controlLayout = new LinearLayout(this);
            controlLayout.setOrientation(LinearLayout.HORIZONTAL);
            controlLayout.setGravity(Gravity.CENTER);
            controlLayout.setPadding(0, 12, 0, 0);

            Button recordBtn = createStyledButton("Запись", 0xFFFF0000);
            recordBtn.setOnClickListener(v -> startRecordingMode());
            controlLayout.addView(recordBtn);

            Button startBtn = createStyledButton("▶ Старт", 0xFF00AA00);
            startBtn.setOnClickListener(v -> startMacroExecution());
            controlLayout.addView(startBtn);

            Button pauseBtn = createStyledButton("⏸ Пауза", 0xFFFF8800);
            pauseBtn.setOnClickListener(v -> toggleMacroPause());
            controlLayout.addView(pauseBtn);

            Button stopBtn = createStyledButton("■ Стоп", 0xFFFF0000);
            stopBtn.setOnClickListener(v -> stopMacroExecution());
            controlLayout.addView(stopBtn);

            Button clearBtn = createStyledButton("✕ Очистить", 0xFFFF8800);
            clearBtn.setOnClickListener(v -> clearMacroPoints());
            controlLayout.addView(clearBtn);

            mainLayout.addView(controlLayout);

            Button createBtnBtn = createStyledButton("🔘 Создать кнопку", 0xFF0066FF);
            createBtnBtn.setOnClickListener(v -> showCreateButtonDialog());
            mainLayout.addView(createBtnBtn);

            Button saveBtn = createStyledButton("💾 Сохранить макрос", 0xFFFFAA00);
            saveBtn.setOnClickListener(v -> {
                saveMacroConfigs();
                Toast.makeText(this, "Макрос сохранён!", Toast.LENGTH_SHORT).show();
            });
            mainLayout.addView(saveBtn);

            win.contentView = mainLayout;
            win.container = new FrameLayout(this);
            win.container.addView(mainLayout);

            setupWindowAppearance(win);
            setupWindowDragging(win);
            setupWindowResizing(win);

            win.params = new WindowManager.LayoutParams(
                    480, 550,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            win.params.gravity = Gravity.CENTER;

            if (windowManager != null) {
                windowManager.addView(win.container, win.params);
            }

            windows.put("macros", win);
            windowOrder.add("macros");

            updateMacroUI(pointsContainer);

        } catch (Exception e) {
            Log.e(TAG, "showMacrosWindow error", e);
        }
    }

    // ==================== ОКНО АВТО КЛИКЕРА ====================

    private void showAutoClickerWindow() {
        try {
            if (windows.containsKey("autoclicker")) {
                bringWindowToFront("autoclicker");
                return;
            }

            FloatingWindow win = new FloatingWindow();
            win.type = "autoclicker";
            win.title = "Авто кликер";
            win.isClosable = true;
            win.isResizable = true;
            win.isDraggable = true;
            win.isMinimizable = true;
            win.minWidth = 400;
            win.minHeight = 400;
            win.icon = "🔄";

            LinearLayout mainLayout = new LinearLayout(this);
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setPadding(16, 16, 16, 16);
            mainLayout.setBackgroundColor(0xDD0D0D0D);

            LinearLayout titleBar = createWindowTitleBar(win);
            mainLayout.addView(titleBar);

            LinearLayout selectLayout = createMacroSelector(mainLayout);
            mainLayout.addView(selectLayout);

            final LinearLayout pointsContainer = new LinearLayout(this);
            pointsContainer.setOrientation(LinearLayout.VERTICAL);
            pointsContainer.setPadding(0, 12, 0, 0);
            mainLayout.addView(pointsContainer);

            LinearLayout controlLayout = new LinearLayout(this);
            controlLayout.setOrientation(LinearLayout.HORIZONTAL);
            controlLayout.setGravity(Gravity.CENTER);
            controlLayout.setPadding(0, 12, 0, 0);

            Button addBtn = createStyledButton("➕ Добавить", 0xFFFF8800);
            addBtn.setOnClickListener(v -> startAutoClickerRecording());
            controlLayout.addView(addBtn);

            Button startBtn = createStyledButton("▶ Старт", 0xFF00AA00);
            startBtn.setOnClickListener(v -> startAutoClicker());
            controlLayout.addView(startBtn);

            Button stopBtn = createStyledButton("■ Стоп", 0xFFFF0000);
            stopBtn.setOnClickListener(v -> stopAutoClicker());
            controlLayout.addView(stopBtn);

            mainLayout.addView(controlLayout);

            LinearLayout settingsLayout = createAutoClickerSettings();
            mainLayout.addView(settingsLayout);

            win.contentView = mainLayout;
            win.container = new FrameLayout(this);
            win.container.addView(mainLayout);

            setupWindowAppearance(win);
            setupWindowDragging(win);
            setupWindowResizing(win);

            win.params = new WindowManager.LayoutParams(
                    450, 500,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            win.params.gravity = Gravity.CENTER;

            if (windowManager != null) {
                windowManager.addView(win.container, win.params);
            }

            windows.put("autoclicker", win);
            windowOrder.add("autoclicker");

            updateAutoClickerUI(pointsContainer);

        } catch (Exception e) {
            Log.e(TAG, "showAutoClickerWindow error", e);
        }
    }

    // ==================== ОКНО ПЕРСОНАЖЕЙ ====================

    private void showCharactersWindow() {
        try {
            if (windows.containsKey("characters")) {
                bringWindowToFront("characters");
                return;
            }

            FloatingWindow win = new FloatingWindow();
            win.type = "characters";
            win.title = "Персонажи";
            win.isClosable = true;
            win.isResizable = true;
            win.isDraggable = true;
            win.isMinimizable = true;
            win.minWidth = 350;
            win.minHeight = 400;
            win.icon = "👤";

            LinearLayout mainLayout = new LinearLayout(this);
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setPadding(16, 16, 16, 16);
            mainLayout.setBackgroundColor(0xDD0D0D0D);

            LinearLayout titleBar = createWindowTitleBar(win);
            mainLayout.addView(titleBar);

            final LinearLayout listContainer = new LinearLayout(this);
            listContainer.setOrientation(LinearLayout.VERTICAL);
            listContainer.setPadding(0, 12, 0, 0);
            mainLayout.addView(listContainer);

            Button addBtn = createStyledButton("➕ Добавить персонажа", 0xFFFF0000);
            addBtn.setOnClickListener(v -> showAddCharacterDialog(listContainer));
            mainLayout.addView(addBtn);

            win.contentView = mainLayout;
            win.container = new FrameLayout(this);
            win.container.addView(mainLayout);

            setupWindowAppearance(win);
            setupWindowDragging(win);
            setupWindowResizing(win);

            win.params = new WindowManager.LayoutParams(
                    400, 450,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            win.params.gravity = Gravity.CENTER;

            if (windowManager != null) {
                windowManager.addView(win.container, win.params);
            }

            windows.put("characters", win);
            windowOrder.add("characters");

            updateCharactersUI(listContainer);

        } catch (Exception e) {
            Log.e(TAG, "showCharactersWindow error", e);
        }
    }

    // ==================== ОКНО НАСТРОЕК ====================

    private void showSettingsWindow() {
        try {
            if (windows.containsKey("settings")) {
                bringWindowToFront("settings");
                return;
            }

            FloatingWindow win = new FloatingWindow();
            win.type = "settings";
            win.title = "Настройки";
            win.isClosable = true;
            win.isResizable = true;
            win.isDraggable = true;
            win.isMinimizable = true;
            win.minWidth = 350;
            win.minHeight = 400;
            win.icon = "⚙️";

            LinearLayout mainLayout = new LinearLayout(this);
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setPadding(16, 16, 16, 16);
            mainLayout.setBackgroundColor(0xDD0D0D0D);

            LinearLayout titleBar = createWindowTitleBar(win);
            mainLayout.addView(titleBar);

            TextView sizeLabel = new TextView(this);
            sizeLabel.setText("Размер оверлея: " + overlaySize + "px");
            sizeLabel.setTextColor(Color.WHITE);
            mainLayout.addView(sizeLabel);

            SeekBar sizeSeek = new SeekBar(this);
            sizeSeek.setMax(200);
            sizeSeek.setMin(40);
            sizeSeek.setProgress(overlaySize);
            sizeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    overlaySize = progress;
                    prefs.edit().putInt("overlay_size", progress).apply();
                    sizeLabel.setText("Размер оверлея: " + progress + "px");
                    createMainCircle();
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            mainLayout.addView(sizeSeek);

            TextView alphaLabel = new TextView(this);
            alphaLabel.setText("Прозрачность: " + (overlayAlpha * 100 / 255) + "%");
            alphaLabel.setTextColor(Color.WHITE);
            mainLayout.addView(alphaLabel);

            SeekBar alphaSeek = new SeekBar(this);
            alphaSeek.setMax(255);
            alphaSeek.setProgress(overlayAlpha);
            alphaSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    overlayAlpha = progress;
                    prefs.edit().putInt("overlay_alpha", progress).apply();
                    alphaLabel.setText("Прозрачность: " + (progress * 100 / 255) + "%");
                    createMainCircle();
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            mainLayout.addView(alphaSeek);

            Button toggleBtn = createStyledButton(
                buttonsVisible ? "🙈 Скрыть кнопки" : "👀 Показать кнопки",
                0xFF0066FF
            );
            toggleBtn.setOnClickListener(v -> {
                toggleButtonsVisibility();
                toggleBtn.setText(buttonsVisible ? "🙈 Скрыть кнопки" : "👀 Показать кнопки");
            });
            mainLayout.addView(toggleBtn);

            win.contentView = mainLayout;
            win.container = new FrameLayout(this);
            win.container.addView(mainLayout);

            setupWindowAppearance(win);
            setupWindowDragging(win);
            setupWindowResizing(win);

            win.params = new WindowManager.LayoutParams(
                    380, 450,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            win.params.gravity = Gravity.CENTER;

            if (windowManager != null) {
                windowManager.addView(win.container, win.params);
            }

            windows.put("settings", win);
            windowOrder.add("settings");

        } catch (Exception e) {
            Log.e(TAG, "showSettingsWindow error", e);
        }
    }

    // ==================== ОКНО КОНФИГОВ ====================

    private void showConfigWindow() {
        try {
            if (windows.containsKey("configs")) {
                bringWindowToFront("configs");
                return;
            }

            FloatingWindow win = new FloatingWindow();
            win.type = "configs";
            win.title = "Конфиги";
            win.isClosable = true;
            win.isResizable = true;
            win.isDraggable = true;
            win.isMinimizable = true;
            win.minWidth = 350;
            win.minHeight = 300;
            win.icon = "📁";

            LinearLayout mainLayout = new LinearLayout(this);
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setPadding(16, 16, 16, 16);
            mainLayout.setBackgroundColor(0xDD0D0D0D);

            LinearLayout titleBar = createWindowTitleBar(win);
            mainLayout.addView(titleBar);

            Button exportBtn = createStyledButton("📤 Экспорт конфига", 0xFF00AA00);
            exportBtn.setOnClickListener(v -> exportConfig());
            mainLayout.addView(exportBtn);

            Button importBtn = createStyledButton("📥 Импорт конфига", 0xFFFF8800);
            importBtn.setOnClickListener(v -> importConfig());
            mainLayout.addView(importBtn);

            Button deleteAllBtn = createStyledButton("🗑 Удалить все", 0xFFFF0000);
            deleteAllBtn.setOnClickListener(v -> {
                AlertDialog.Builder confirm = new AlertDialog.Builder(MainActivity.this);
                confirm.setTitle("Удалить все?");
                confirm.setMessage("Все макросы и настройки будут удалены");
                confirm.setPositiveButton("Удалить", (d, w) -> {
                    macroConfigs.clear();
                    saveMacroConfigs();
                    Toast.makeText(MainActivity.this, "Все макросы удалены", Toast.LENGTH_SHORT).show();
                });
                confirm.setNegativeButton("Отмена", null);
                confirm.show();
            });
            mainLayout.addView(deleteAllBtn);

            win.contentView = mainLayout;
            win.container = new FrameLayout(this);
            win.container.addView(mainLayout);

            setupWindowAppearance(win);
            setupWindowDragging(win);
            setupWindowResizing(win);

            win.params = new WindowManager.LayoutParams(
                    380, 350,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            win.params.gravity = Gravity.CENTER;

            if (windowManager != null) {
                windowManager.addView(win.container, win.params);
            }

            windows.put("configs", win);
            windowOrder.add("configs");

        } catch (Exception e) {
            Log.e(TAG, "showConfigWindow error", e);
        }
    }

    // ==================== УПРАВЛЕНИЕ ОКНАМИ ====================

    private void bringWindowToFront(String type) {
        if (windows.containsKey(type)) {
            FloatingWindow win = windows.get(type);
            if (windowManager != null && win.container != null) {
                windowManager.updateViewLayout(win.container, win.params);
            }
            windowOrder.remove(type);
            windowOrder.add(type);
        }
    }

    private void removeWindow(String type) {
        try {
            if (windows.containsKey(type)) {
                FloatingWindow win = windows.get(type);
                if (win.container != null && windowManager != null) {
                    windowManager.removeView(win.container);
                }
                windows.remove(type);
                windowOrder.remove(type);
            }
        } catch (Exception e) {
            Log.e(TAG, "removeWindow error", e);
        }
    }

    private void minimizeWindow(String type) {
        if (windows.containsKey(type)) {
            FloatingWindow win = windows.get(type);
            if (win.isMinimized) {
                win.isMinimized = false;
                win.params.width = win.savedWidth;
                win.params.height = win.savedHeight;
                if (win.titleBar != null) win.titleBar.setVisibility(View.VISIBLE);
                if (win.resizeHandle != null) win.resizeHandle.setVisibility(View.VISIBLE);
                if (windowManager != null) {
                    windowManager.updateViewLayout(win.container, win.params);
                }
            } else {
                win.isMinimized = true;
                win.savedWidth = win.params.width;
                win.savedHeight = win.params.height;
                win.params.width = 200;
                win.params.height = 50;
                if (win.titleBar != null) win.titleBar.setVisibility(View.GONE);
                if (win.resizeHandle != null) win.resizeHandle.setVisibility(View.GONE);
                if (windowManager != null) {
                    windowManager.updateViewLayout(win.container, win.params);
                }
            }
        }
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ДЛЯ ОКОН ====================

    private LinearLayout createWindowTitleBar(final FloatingWindow win) {
        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(12, 8, 12, 8);
        titleBar.setBackgroundColor(0x44000000);

        TextView iconText = new TextView(this);
        iconText.setText(win.icon);
        iconText.setTextSize(18);
        iconText.setPadding(0, 0, 8, 0);
        titleBar.addView(iconText);

        TextView titleText = new TextView(this);
        titleText.setText(win.title);
        titleText.setTextColor(getThemeColor());
        titleText.setTextSize(16);
        titleText.setTypeface(null, android.graphics.Typeface.BOLD);
        titleText.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        titleBar.addView(titleText);

        if (win.isMinimizable) {
            ImageButton minBtn = new ImageButton(this);
            minBtn.setImageDrawable(createMinimizeIcon());
            minBtn.setBackgroundColor(Color.TRANSPARENT);
            minBtn.setPadding(8, 4, 8, 4);
            minBtn.setOnClickListener(v -> minimizeWindow(win.type));
            titleBar.addView(minBtn);
        }

        if (win.isClosable) {
            ImageButton closeBtn = new ImageButton(this);
            closeBtn.setImageDrawable(createCloseIcon());
            closeBtn.setBackgroundColor(Color.TRANSPARENT);
            closeBtn.setPadding(8, 4, 8, 4);
            closeBtn.setOnClickListener(v -> removeWindow(win.type));
            titleBar.addView(closeBtn);
        }

        win.titleBar = titleBar;
        return titleBar;
    }

    private void setupWindowAppearance(FloatingWindow win) {
        GradientDrawable border = new GradientDrawable();
        border.setCornerRadius(win.cornerRadius);
        border.setColor(win.backgroundColor);
        border.setStroke(win.borderWidth, getThemeColor());
        win.container.setBackground(border);
        win.container.setAlpha(win.alpha);
    }

    private void setupWindowDragging(final FloatingWindow win) {
        if (win.container == null || !win.isDraggable) return;

        win.container.setOnTouchListener(new View.OnTouchListener() {
            float startX, startY;
            int initX, initY;
            boolean dragging;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (win.isResizing) return false;

                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = e.getRawX();
                        startY = e.getRawY();
                        initX = win.params.x;
                        initY = win.params.y;
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - startX;
                        float dy = e.getRawY() - startY;
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) dragging = true;
                        if (dragging && win.params != null) {
                            win.params.x = initX + (int) dx;
                            win.params.y = initY + (int) dy;
                            if (windowManager != null) {
                                windowManager.updateViewLayout(win.container, win.params);
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        return dragging;
                }
                return false;
            }
        });
    }

    private void setupWindowResizing(final FloatingWindow win) {
        if (!win.isResizable) return;
        
        View resizeHandle = new View(this);
        resizeHandle.setBackgroundColor(getThemeColor());
        resizeHandle.setAlpha(0.5f);

        FrameLayout.LayoutParams handleParams = new FrameLayout.LayoutParams(30, 30);
        handleParams.gravity = Gravity.BOTTOM | Gravity.END;
        resizeHandle.setLayoutParams(handleParams);

        win.container.addView(resizeHandle);
        win.resizeHandle = resizeHandle;

        resizeHandle.setOnTouchListener(new View.OnTouchListener() {
            float startX, startY;
            int startWidth, startHeight;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        win.isResizing = true;
                        startX = e.getRawX();
                        startY = e.getRawY();
                        startWidth = win.params.width;
                        startHeight = win.params.height;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - startX;
                        float dy = e.getRawY() - startY;
                        int newWidth = Math.max(win.minWidth, startWidth + (int) dx);
                        int newHeight = Math.max(win.minHeight, startHeight + (int) dy);
                        win.params.width = newWidth;
                        win.params.height = newHeight;
                        if (windowManager != null) {
                            windowManager.updateViewLayout(win.container, win.params);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        win.isResizing = false;
                        return true;
                }
                return false;
            }
        });
    }

    private Drawable createCloseIcon() {
        Bitmap b = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(5);
        float cx = 25, cy = 25;
        c.drawLine(cx - 15, cy - 15, cx + 15, cy + 15, p);
        c.drawLine(cx + 15, cy - 15, cx - 15, cy + 15, p);
        return new android.graphics.drawable.BitmapDrawable(getResources(), b);
    }

    private Drawable createMinimizeIcon() {
        Bitmap b = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(5);
        float cx = 25, cy = 25;
        c.drawLine(cx - 12, cy, cx + 12, cy, p);
        return new android.graphics.drawable.BitmapDrawable(getResources(), b);
    }

    // ==================== МАКРОСЫ ====================

    private LinearLayout createMacroSelector(final LinearLayout parent) {
        LinearLayout selectLayout = new LinearLayout(this);
        selectLayout.setOrientation(LinearLayout.HORIZONTAL);
        selectLayout.setGravity(Gravity.CENTER);
        selectLayout.setPadding(0, 8, 0, 8);

        TextView label = new TextView(this);
        label.setText("Макрос:");
        label.setTextColor(Color.WHITE);
        label.setTextSize(14);
        label.setPadding(0, 0, 12, 0);
        selectLayout.addView(label);

        Button prevBtn = new Button(this);
        prevBtn.setText("◄");
        prevBtn.setTextColor(Color.WHITE);
        prevBtn.setTextSize(18);
        prevBtn.setBackgroundColor(0x33FF0000);
        prevBtn.setPadding(12, 4, 12, 4);
        prevBtn.setOnClickListener(v -> {
            int idx = getCurrentMacroIndex();
            if (idx > 0) {
                currentMacroName = macroConfigs.get(idx - 1).name;
                updateMacroUI(parent);
            } else {
                Toast.makeText(this, "Это первый макрос", Toast.LENGTH_SHORT).show();
            }
        });
        selectLayout.addView(prevBtn);

        final TextView nameText = new TextView(this);
        nameText.setText(currentMacroName);
        nameText.setTextColor(getThemeColor());
        nameText.setTextSize(16);
        nameText.setTypeface(null, android.graphics.Typeface.BOLD);
        nameText.setPadding(12, 0, 12, 0);
        nameText.setOnClickListener(v -> showMacroListDialog(parent));
        selectLayout.addView(nameText);

        Button nextBtn = new Button(this);
        nextBtn.setText("►");
        nextBtn.setTextColor(Color.WHITE);
        nextBtn.setTextSize(18);
        nextBtn.setBackgroundColor(0x33FF0000);
        nextBtn.setPadding(12, 4, 12, 4);
        nextBtn.setOnClickListener(v -> {
            int idx = getCurrentMacroIndex();
            if (idx < macroConfigs.size() - 1) {
                currentMacroName = macroConfigs.get(idx + 1).name;
                updateMacroUI(parent);
            } else {
                Toast.makeText(this, "Это последний макрос", Toast.LENGTH_SHORT).show();
            }
        });
        selectLayout.addView(nextBtn);

        Button newBtn = new Button(this);
        newBtn.setText("+");
        newBtn.setTextColor(Color.WHITE);
        newBtn.setTextSize(18);
        newBtn.setBackgroundColor(0xFFFF0000);
        newBtn.setPadding(12, 4, 12, 4);
        newBtn.setOnClickListener(v -> showNewMacroDialog(parent));
        selectLayout.addView(newBtn);

        return selectLayout;
    }

    private void updateMacroUI(LinearLayout container) {
        container.removeAllViews();

        MacroConfig config = getCurrentMacro();
        if (config == null) return;

        TextView info = new TextView(this);
        info.setText("📊 Точек: " + config.points.size() + " | Циклов: " + 
                    (config.isLoop ? "∞" : config.loopCount));
        info.setTextColor(Color.WHITE);
        info.setTextSize(12);
        info.setPadding(0, 0, 0, 8);
        container.addView(info);

        if (config.points.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("📭 Нет точек\nНажмите 'Запись' для добавления");
            empty.setTextColor(0xFF888888);
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, 20, 0, 20);
            container.addView(empty);
            return;
        }

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < config.points.size(); i++) {
            MacroPoint p = config.points.get(i);
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(4, 4, 4, 4);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(8);
            bg.setColor(0x22FF0000);
            bg.setStroke(1, getThemeColor());
            item.setBackground(bg);

            String action = p.actionType.equals("swipe") ? "🔄 Свайп" : "👆 Клик";
            TextView text = new TextView(this);
            text.setText(" #" + (i+1) + " " + action + " (" + p.x + "," + p.y + ")");
            text.setTextColor(Color.WHITE);
            text.setTextSize(12);
            text.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            item.addView(text);

            Button editBtn = new Button(this);
            editBtn.setText("⚙");
            editBtn.setTextColor(0xFFFFFF00);
            editBtn.setTextSize(12);
            editBtn.setBackgroundColor(0x33000000);
            editBtn.setPadding(6, 2, 6, 2);
            final int idx = i;
            editBtn.setOnClickListener(v -> showPointEditor(idx, config));
            item.addView(editBtn);

            Button delBtn = new Button(this);
            delBtn.setText("✕");
            delBtn.setTextColor(0xFFFF0000);
            delBtn.setTextSize(12);
            delBtn.setBackgroundColor(0x33FF0000);
            delBtn.setPadding(6, 2, 6, 2);
            delBtn.setOnClickListener(v -> {
                config.points.remove(idx);
                saveMacroConfigs();
                updateMacroUI(container);
            });
            item.addView(delBtn);

            list.addView(item);
        }

        scroll.addView(list);
        container.addView(scroll);

        // Настройки циклов
        LinearLayout loopLayout = new LinearLayout(this);
        loopLayout.setOrientation(LinearLayout.HORIZONTAL);
        loopLayout.setGravity(Gravity.CENTER);
        loopLayout.setPadding(0, 8, 0, 0);

        TextView loopLabel = new TextView(this);
        loopLabel.setText("Циклы:");
        loopLabel.setTextColor(Color.WHITE);
        loopLabel.setTextSize(12);
        loopLabel.setPadding(0, 0, 8, 0);
        loopLayout.addView(loopLabel);

        final EditText loopInput = new EditText(this);
        loopInput.setText(config.isLoop ? "∞" : String.valueOf(config.loopCount));
        loopInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        loopInput.setTextColor(Color.WHITE);
        loopInput.setBackgroundColor(0x22FFFFFF);
        loopInput.setPadding(8, 4, 8, 4);
        loopInput.setWidth(80);
        loopInput.setOnEditorActionListener((v, actionId, event) -> {
            try {
                String text = loopInput.getText().toString().trim();
                if (text.equals("∞") || text.isEmpty()) {
                    config.isLoop = true;
                    config.loopCount = 1;
                } else {
                    config.isLoop = false;
                    config.loopCount = Integer.parseInt(text);
                }
                saveMacroConfigs();
                updateMacroUI(container);
            } catch (Exception e) {
                Toast.makeText(this, "Ошибка", Toast.LENGTH_SHORT).show();
            }
            return false;
        });
        loopLayout.addView(loopInput);

        Switch infiniteSwitch = new Switch(this);
        infiniteSwitch.setChecked(config.isLoop);
        infiniteSwitch.setText("∞");
        infiniteSwitch.setTextColor(Color.WHITE);
        infiniteSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            config.isLoop = isChecked;
            if (isChecked) {
                loopInput.setText("∞");
                loopInput.setEnabled(false);
            } else {
                loopInput.setText("1");
                loopInput.setEnabled(true);
            }
            saveMacroConfigs();
            updateMacroUI(container);
        });
        loopLayout.addView(infiniteSwitch);

        container.addView(loopLayout);
    }

    private void updateAutoClickerUI(LinearLayout container) {
        container.removeAllViews();

        MacroConfig config = getCurrentMacro();
        if (config == null || !config.type.equals("autoclicker")) {
            TextView empty = new TextView(this);
            empty.setText("Переключите макрос на Авто кликер");
            empty.setTextColor(0xFF888888);
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, 20, 0, 20);
            container.addView(empty);
            return;
        }

        if (config.points.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("📭 Нет точек\nНажмите 'Добавить' для создания");
            empty.setTextColor(0xFF888888);
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, 20, 0, 20);
            container.addView(empty);
            return;
        }

        for (int i = 0; i < config.points.size(); i++) {
            MacroPoint p = config.points.get(i);
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(4, 4, 4, 4);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(8);
            bg.setColor(0x22FF0000);
            bg.setStroke(1, getThemeColor());
            item.setBackground(bg);

            TextView text = new TextView(this);
            text.setText(" #" + (i+1) + " (" + p.x + "," + p.y + ") " + p.delay + "мс");
            text.setTextColor(Color.WHITE);
            text.setTextSize(12);
            text.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            item.addView(text);

            Button editBtn = new Button(this);
            editBtn.setText("⚙");
            editBtn.setTextColor(0xFFFFFF00);
            editBtn.setTextSize(12);
            editBtn.setBackgroundColor(0x33000000);
            editBtn.setPadding(6, 2, 6, 2);
            final int idx = i;
            editBtn.setOnClickListener(v -> showPointEditor(idx, config));
            item.addView(editBtn);

            Button delBtn = new Button(this);
            delBtn.setText("✕");
            delBtn.setTextColor(0xFFFF0000);
            delBtn.setTextSize(12);
            delBtn.setBackgroundColor(0x33FF0000);
            delBtn.setPadding(6, 2, 6, 2);
            delBtn.setOnClickListener(v -> {
                config.points.remove(idx);
                saveMacroConfigs();
                updateAutoClickerUI(container);
            });
            item.addView(delBtn);

            container.addView(item);
        }
    }

    private LinearLayout createAutoClickerSettings() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(0, 12, 0, 0);

        LinearLayout loopLayout = new LinearLayout(this);
        loopLayout.setOrientation(LinearLayout.HORIZONTAL);
        loopLayout.setGravity(Gravity.CENTER_VERTICAL);
        loopLayout.setPadding(0, 4, 0, 4);

        TextView loopLabel = new TextView(this);
        loopLabel.setText("Циклы: ∞");
        loopLabel.setTextColor(Color.WHITE);
        loopLabel.setTextSize(14);
        loopLabel.setPadding(0, 0, 12, 0);
        loopLayout.addView(loopLabel);

        final EditText loopInput = new EditText(this);
        loopInput.setText("∞");
        loopInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        loopInput.setTextColor(Color.WHITE);
        loopInput.setBackgroundColor(0x22FFFFFF);
        loopInput.setPadding(8, 4, 8, 4);
        loopInput.setWidth(80);
        loopLayout.addView(loopInput);

        Switch infiniteSwitch = new Switch(this);
        infiniteSwitch.setChecked(true);
        infiniteSwitch.setText("∞");
        infiniteSwitch.setTextColor(Color.WHITE);
        infiniteSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                loopInput.setText("∞");
                loopInput.setEnabled(false);
                loopLabel.setText("Циклы: ∞");
            } else {
                loopInput.setText("1");
                loopInput.setEnabled(true);
                loopLabel.setText("Циклы: 1");
            }
        });
        loopLayout.addView(infiniteSwitch);

        layout.addView(loopLayout);

        return layout;
    }

    private void showPointEditor(int index, MacroConfig config) {
        if (index < 0 || index >= config.points.size()) return;
        MacroPoint point = config.points.get(index);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Редактирование точки #" + (index + 1));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        Spinner typeSpinner = new Spinner(this);
        String[] types = {"Клик", "Свайп", "Ожидание"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, types);
        typeSpinner.setAdapter(adapter);
        int pos = point.actionType.equals("swipe") ? 1 : point.actionType.equals("wait") ? 2 : 0;
        typeSpinner.setSelection(pos);
        layout.addView(typeSpinner);

        LinearLayout coordLayout = new LinearLayout(this);
        coordLayout.setOrientation(LinearLayout.HORIZONTAL);
        coordLayout.setPadding(0, 8, 0, 8);

        final EditText xInput = new EditText(this);
        xInput.setHint("X");
        xInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        xInput.setText(String.valueOf(point.x));
        xInput.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        coordLayout.addView(xInput);

        final EditText yInput = new EditText(this);
        yInput.setHint("Y");
        yInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        yInput.setText(String.valueOf(point.y));
        yInput.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        coordLayout.addView(yInput);

        layout.addView(coordLayout);

        LinearLayout delayLayout = new LinearLayout(this);
        delayLayout.setOrientation(LinearLayout.HORIZONTAL);
        delayLayout.setPadding(0, 8, 0, 8);

        TextView delayLabel = new TextView(this);
        delayLabel.setText("Задержка (мс):");
        delayLabel.setTextColor(Color.WHITE);
        delayLayout.addView(delayLabel);

        final EditText delayInput = new EditText(this);
        delayInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        delayInput.setText(String.valueOf(point.delay));
        delayInput.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        delayLayout.addView(delayInput);

        layout.addView(delayLayout);

        LinearLayout repeatLayout = new LinearLayout(this);
        repeatLayout.setOrientation(LinearLayout.HORIZONTAL);
        repeatLayout.setPadding(0, 8, 0, 8);

        TextView repeatLabel = new TextView(this);
        repeatLabel.setText("Повторы:");
        repeatLabel.setTextColor(Color.WHITE);
        repeatLayout.addView(repeatLabel);

        final EditText repeatInput = new EditText(this);
        repeatInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        repeatInput.setText(String.valueOf(point.repeatCount));
        repeatInput.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        repeatLayout.addView(repeatInput);

        layout.addView(repeatLayout);

        CheckBox randomCheck = new CheckBox(this);
        randomCheck.setText("Случайное смещение");
        randomCheck.setTextColor(Color.WHITE);
        randomCheck.setChecked(point.randomOffset);
        layout.addView(randomCheck);

        final LinearLayout swipeLayout = new LinearLayout(this);
        swipeLayout.setOrientation(LinearLayout.VERTICAL);
        swipeLayout.setVisibility(View.GONE);

        LinearLayout coord2Layout = new LinearLayout(this);
        coord2Layout.setOrientation(LinearLayout.HORIZONTAL);
        coord2Layout.setPadding(0, 4, 0, 4);

        final EditText x2Input = new EditText(this);
        x2Input.setHint("X2");
        x2Input.setInputType(InputType.TYPE_CLASS_NUMBER);
        x2Input.setText(String.valueOf(point.x2));
        x2Input.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        coord2Layout.addView(x2Input);

        final EditText y2Input = new EditText(this);
        y2Input.setHint("Y2");
        y2Input.setInputType(InputType.TYPE_CLASS_NUMBER);
        y2Input.setText(String.valueOf(point.y2));
        y2Input.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        coord2Layout.addView(y2Input);

        swipeLayout.addView(coord2Layout);

        LinearLayout durationLayout = new LinearLayout(this);
        durationLayout.setOrientation(LinearLayout.HORIZONTAL);
        durationLayout.setPadding(0, 4, 0, 4);

        TextView durationLabel = new TextView(this);
        durationLabel.setText("Длительность (мс):");
        durationLabel.setTextColor(Color.WHITE);
        durationLayout.addView(durationLabel);

        final EditText durationInput = new EditText(this);
        durationInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        durationInput.setText(String.valueOf(point.swipeDuration));
        durationInput.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        durationLayout.addView(durationInput);

        swipeLayout.addView(durationLayout);
        layout.addView(swipeLayout);

        typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                swipeLayout.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        builder.setView(layout);
        builder.setPositiveButton("Сохранить", (d, w) -> {
            try {
                point.x = Integer.parseInt(xInput.getText().toString());
                point.y = Integer.parseInt(yInput.getText().toString());
                point.delay = Integer.parseInt(delayInput.getText().toString());
                point.repeatCount = Integer.parseInt(repeatInput.getText().toString());
                point.randomOffset = randomCheck.isChecked();
                
                int typePos = typeSpinner.getSelectedItemPosition();
                if (typePos == 1) {
                    point.actionType = "swipe";
                    point.x2 = Integer.parseInt(x2Input.getText().toString());
                    point.y2 = Integer.parseInt(y2Input.getText().toString());
                    point.swipeDuration = Integer.parseInt(durationInput.getText().toString());
                } else if (typePos == 2) {
                    point.actionType = "wait";
                } else {
                    point.actionType = "click";
                }
                
                saveMacroConfigs();
                
                if (windows.containsKey("macros")) {
                    LinearLayout container = null;
                    LinearLayout mainLayout = (LinearLayout) windows.get("macros").contentView;
                    for (int i = 0; i < mainLayout.getChildCount(); i++) {
                        View v = mainLayout.getChildAt(i);
                        if (v instanceof LinearLayout) {
                            LinearLayout ll = (LinearLayout) v;
                            if (ll.getChildCount() > 0 && ll.getChildAt(0) instanceof TextView) {
                                TextView tv = (TextView) ll.getChildAt(0);
                                if (tv.getText().toString().contains("Точек:")) {
                                    container = ll;
                                    break;
                                }
                            }
                        }
                    }
                    if (container != null) {
                        updateMacroUI(container);
                    }
                }
                
                if (windows.containsKey("autoclicker")) {
                    LinearLayout container = null;
                    LinearLayout mainLayout = (LinearLayout) windows.get("autoclicker").contentView;
                    for (int i = 0; i < mainLayout.getChildCount(); i++) {
                        View v = mainLayout.getChildAt(i);
                        if (v instanceof LinearLayout) {
                            LinearLayout ll = (LinearLayout) v;
                            if (ll.getChildCount() > 0 && ll.getChildAt(0) instanceof TextView) {
                                TextView tv = (TextView) ll.getChildAt(0);
                                if (tv.getText().toString().contains("Точек:")) {
                                    container = ll;
                                    break;
                                }
                            }
                        }
                    }
                    if (container != null) {
                        updateAutoClickerUI(container);
                    }
                }
                
                Toast.makeText(this, "Точка обновлена", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "showPointEditor error", e);
                Toast.makeText(this, "Ошибка", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void showMacroListDialog(final LinearLayout parent) {
        if (macroConfigs.isEmpty()) {
            Toast.makeText(this, "Нет макросов", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] names = new String[macroConfigs.size()];
        for (int i = 0; i < macroConfigs.size(); i++) {
            MacroConfig config = macroConfigs.get(i);
            names[i] = config.name + " (" + config.points.size() + " точек)";
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Выбор макроса");
        builder.setItems(names, (d, which) -> {
            currentMacroName = macroConfigs.get(which).name;
            updateMacroUI(parent);
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void showNewMacroDialog(final LinearLayout parent) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Новый макрос");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        final EditText nameInput = new EditText(this);
        nameInput.setHint("Имя макроса");
        nameInput.setText("Макрос " + (macroConfigs.size() + 1));
        layout.addView(nameInput);

        final Spinner typeSpinner = new Spinner(this);
        String[] types = {"Запись", "Авто кликер"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, types);
        typeSpinner.setAdapter(adapter);
        layout.addView(typeSpinner);

        final EditText descInput = new EditText(this);
        descInput.setHint("Описание");
        layout.addView(descInput);

        builder.setView(layout);
        builder.setPositiveButton("Создать", (d, w) -> {
            try {
                String name = nameInput.getText().toString().trim();
                if (name.isEmpty()) name = "Макрос " + (macroConfigs.size() + 1);
                
                MacroConfig config = new MacroConfig(name);
                config.type = typeSpinner.getSelectedItemPosition() == 1 ? "autoclicker" : "recorded";
                config.color = getThemeColor();
                config.description = descInput.getText().toString().trim();
                macroConfigs.add(config);
                currentMacroName = name;
                saveMacroConfigs();
                updateMacroUI(parent);
                Toast.makeText(this, "Макрос создан", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "showNewMacroDialog error", e);
                Toast.makeText(this, "Ошибка", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void showCreateButtonDialog() {
        MacroConfig config = getCurrentMacro();
        if (config == null || config.points.isEmpty()) {
            Toast.makeText(this, "Макрос пуст", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Создать кнопку");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        final EditText nameInput = new EditText(this);
        nameInput.setHint("Название кнопки");
        nameInput.setText(config.name);
        layout.addView(nameInput);

        final EditText colorInput = new EditText(this);
        colorInput.setHint("Цвет (HEX)");
        colorInput.setText("#FF0000");
        layout.addView(colorInput);

        final EditText sizeInput = new EditText(this);
        sizeInput.setHint("Размер");
        sizeInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        sizeInput.setText("80");
        layout.addView(sizeInput);

        builder.setView(layout);
        builder.setPositiveButton("Создать", (d, w) -> {
            try {
                String name = nameInput.getText().toString().trim();
                if (name.isEmpty()) name = config.name;
                
                int color = Color.parseColor(colorInput.getText().toString().trim());
                int size = Integer.parseInt(sizeInput.getText().toString());
                
                config.buttonName = name;
                config.buttonColor = color;
                config.buttonSize = size;
                config.buttonX = 200;
                config.buttonY = 300;
                
                createFloatingButton(config, name, color);
                Toast.makeText(this, "Кнопка создана", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "showCreateButtonDialog error", e);
                Toast.makeText(this, "Ошибка", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    // ==================== ЗАПИСЬ МАКРОСА ====================

    private void startRecordingMode() {
        if (isRecordingMode) return;
        if (windowManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Требуется разрешение на оверлей", Toast.LENGTH_SHORT).show();
            return;
        }

        MacroConfig config = getCurrentMacro();
        if (config == null) {
            Toast.makeText(this, "Нет макроса", Toast.LENGTH_SHORT).show();
            return;
        }

        isRecordingMode = true;
        isRecordingPaused = false;
        recordedActions.clear();
        recordedClickCount = 0;
        recordedSwipeCount = 0;
        recordingStartTime = System.currentTimeMillis();
        lastActionTime = recordingStartTime;
        isSwiping = false;

        recordingOverlay = new FrameLayout(this);
        recordingOverlay.setBackgroundColor(0x3300FF00);
        recordingOverlay.setClickable(false);
        recordingOverlay.setFocusable(false);

        LinearLayout controlPanel = new LinearLayout(this);
        controlPanel.setOrientation(LinearLayout.HORIZONTAL);
        controlPanel.setGravity(Gravity.CENTER_VERTICAL);
        controlPanel.setPadding(16, 10, 16, 10);

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setCornerRadius(20);
        panelBg.setColor(0xDD000000);
        panelBg.setStroke(2, 0xFFFF0000);
        controlPanel.setBackground(panelBg);

        TextView recIcon = new TextView(this);
        recIcon.setText("●");
        recIcon.setTextColor(0xFFFF0000);
        recIcon.setTextSize(24);
        recIcon.setPadding(0, 0, 12, 0);
        controlPanel.addView(recIcon);

        recordingStatusText = new TextView(this);
        recordingStatusText.setText("0");
        recordingStatusText.setTextColor(Color.WHITE);
        recordingStatusText.setTextSize(16);
        recordingStatusText.setTypeface(null, android.graphics.Typeface.BOLD);
        recordingStatusText.setPadding(0, 0, 12, 0);
        controlPanel.addView(recordingStatusText);

        recordingTimeText = new TextView(this);
        recordingTimeText.setText("00:00");
        recordingTimeText.setTextColor(0xFF888888);
        recordingTimeText.setTextSize(14);
        recordingTimeText.setPadding(0, 0, 12, 0);
        controlPanel.addView(recordingTimeText);

        pauseRecordBtn = new Button(this);
        pauseRecordBtn.setText("⏸");
        pauseRecordBtn.setTextColor(Color.WHITE);
        pauseRecordBtn.setTextSize(18);
        pauseRecordBtn.setBackgroundColor(0x33FFFFFF);
        pauseRecordBtn.setPadding(12, 8, 12, 8);
        pauseRecordBtn.setOnClickListener(v -> toggleRecordingPause());
        controlPanel.addView(pauseRecordBtn);

        stopRecordBtn = new Button(this);
        stopRecordBtn.setText("■ СТОП");
        stopRecordBtn.setTextColor(Color.WHITE);
        stopRecordBtn.setTextSize(16);
        stopRecordBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable stopBg = new GradientDrawable();
        stopBg.setCornerRadius(12);
        stopBg.setColor(0xFFFF0000);
        stopRecordBtn.setBackground(stopBg);
        stopRecordBtn.setPadding(24, 12, 24, 12);
        stopRecordBtn.setClickable(true);
        stopRecordBtn.setFocusable(true);
        stopRecordBtn.setOnClickListener(v -> stopRecordingMode());
        controlPanel.addView(stopRecordBtn);

        recordingOverlay.addView(controlPanel);

        int flag = getOverlayFlag();
        WindowManager.LayoutParams overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                flag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        overlayParams.gravity = Gravity.TOP;
        overlayParams.y = 40;

        windowManager.addView(recordingOverlay, overlayParams);

        recordingTouchOverlay = new FrameLayout(this);
        recordingTouchOverlay.setBackgroundColor(0x00000000);
        recordingTouchOverlay.setClickable(false);
        recordingTouchOverlay.setFocusable(false);
        recordingTouchOverlay.setFocusableInTouchMode(false);

        WindowManager.LayoutParams captureParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                flag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );

        final Rect stopBtnRect = new Rect();
        recordingTouchOverlay.setOnTouchListener((v, e) -> {
            if (!isRecordingMode || isRecordingPaused) return false;

            int[] location = new int[2];
            if (stopRecordBtn != null) {
                stopRecordBtn.getLocationOnScreen(location);
                stopBtnRect.set(location[0], location[1],
                        location[0] + stopRecordBtn.getWidth(),
                        location[1] + stopRecordBtn.getHeight());
                if (stopBtnRect.contains((int)e.getRawX(), (int)e.getRawY())) {
                    return false;
                }
            }
            
            if (pauseRecordBtn != null) {
                pauseRecordBtn.getLocationOnScreen(location);
                Rect pauseRect = new Rect(location[0], location[1],
                        location[0] + pauseRecordBtn.getWidth(),
                        location[1] + pauseRecordBtn.getHeight());
                if (pauseRect.contains((int)e.getRawX(), (int)e.getRawY())) {
                    return false;
                }
            }
            
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastRawX = e.getRawX();
                    lastRawY = e.getRawY();
                    isSwiping = false;
                    long currentTime = System.currentTimeMillis();
                    long delay = currentTime - lastActionTime;
                    
                    if (recordPassThrough) {
                        MacroService service = MacroService.getInstance();
                        if (service != null) {
                            service.performClick((int)e.getRawX(), (int)e.getRawY());
                        }
                    }
                    
                    RecordedAction action = new RecordedAction(
                        "click",
                        (int)e.getRawX(), (int)e.getRawY(),
                        delay
                    );
                    recordedActions.add(action);
                    recordedClickCount++;
                    lastActionTime = currentTime;
                    updateRecordingUI();
                    return true;
                    
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - lastRawX;
                    float dy = e.getRawY() - lastRawY;
                    if (Math.abs(dx) > 20 || Math.abs(dy) > 20) {
                        isSwiping = true;
                    }
                    return true;
                    
                case MotionEvent.ACTION_UP:
                    if (isSwiping) {
                        long currentTime2 = System.currentTimeMillis();
                        long delay2 = currentTime2 - lastActionTime;
                        
                        if (recordPassThrough) {
                            MacroService service = MacroService.getInstance();
                            if (service != null) {
                                service.performClick((int)e.getRawX(), (int)e.getRawY());
                            }
                        }
                        
                        RecordedAction swipeAction = new RecordedAction(
                            "swipe",
                            (int)lastRawX, (int)lastRawY,
                            (int)e.getRawX(), (int)e.getRawY(),
                            delay2, currentTime2 - lastActionTime
                        );
                        recordedActions.add(swipeAction);
                        recordedSwipeCount++;
                        lastActionTime = currentTime2;
                        updateRecordingUI();
                    }
                    return true;
            }
            return false;
        });

        windowManager.addView(recordingTouchOverlay, captureParams);

        startRecordingTimer();

        Toast.makeText(this, "🔴 ЗАПИСЬ НАЧАТА", Toast.LENGTH_SHORT).show();
    }

    private void toggleRecordingPause() {
        if (isRecordingMode) {
            isRecordingPaused = !isRecordingPaused;
            if (isRecordingPaused) {
                recordingPauseTime = System.currentTimeMillis();
                pauseRecordBtn.setText("▶");
                Toast.makeText(this, "⏸ Запись на паузе", Toast.LENGTH_SHORT).show();
            } else {
                long pauseDuration = System.currentTimeMillis() - recordingPauseTime;
                recordingStartTime += pauseDuration;
                pauseRecordBtn.setText("⏸");
                Toast.makeText(this, "▶ Запись продолжена", Toast.LENGTH_SHORT).show();
            }
            updateRecordingUI();
        }
    }

    private void startRecordingTimer() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!isRecordingMode) return;
                if (!isRecordingPaused) {
                    long elapsed = System.currentTimeMillis() - recordingStartTime;
                    long minutes = elapsed / 60000;
                    long seconds = (elapsed % 60000) / 1000;
                    if (recordingTimeText != null) {
                        recordingTimeText.setText(String.format("%02d:%02d", minutes, seconds));
                    }
                }
                mainHandler.postDelayed(this, 1000);
            }
        });
    }

    private void updateRecordingUI() {
        if (recordingStatusText != null) {
            recordingStatusText.setText(String.valueOf(recordedActions.size()));
        }
    }

    private void stopRecordingMode() {
        if (!isRecordingMode) return;

        isRecordingMode = false;
        isRecordingPaused = false;
        mainHandler.removeCallbacksAndMessages(null);

        if (recordingOverlay != null && windowManager != null) {
            try { windowManager.removeView(recordingOverlay); } catch (Exception e) {}
            recordingOverlay = null;
        }
        if (recordingTouchOverlay != null && windowManager != null) {
            try { windowManager.removeView(recordingTouchOverlay); } catch (Exception e) {}
            recordingTouchOverlay = null;
        }

        if (recordedActions.isEmpty()) {
            Toast.makeText(this, "Ничего не записано", Toast.LENGTH_SHORT).show();
            return;
        }

        MacroConfig config = getCurrentMacro();
        if (config == null) return;

        config.points.clear();
        config.actions = new ArrayList<>(recordedActions);
        
        for (RecordedAction action : recordedActions) {
            MacroPoint point;
            if (action.type.equals("swipe")) {
                point = new MacroPoint(action.x1, action.y1, (int)action.delay);
                point.actionType = "swipe";
                point.x2 = action.x2;
                point.y2 = action.y2;
                point.swipeDuration = (int)action.duration;
                config.points.add(point);
            } else {
                point = new MacroPoint(action.x1, action.y1, (int)action.delay);
                point.actionType = "click";
                config.points.add(point);
            }
        }

        saveMacroConfigs();
        
        String stats = "📊 Кликов: " + recordedClickCount + ", Свайпов: " + recordedSwipeCount;
        Toast.makeText(this, "✅ Сохранено: " + stats, Toast.LENGTH_LONG).show();
        
        if (windows.containsKey("macros")) {
            LinearLayout container = null;
            LinearLayout mainLayout = (LinearLayout) windows.get("macros").contentView;
            for (int i = 0; i < mainLayout.getChildCount(); i++) {
                View v = mainLayout.getChildAt(i);
                if (v instanceof LinearLayout) {
                    LinearLayout ll = (LinearLayout) v;
                    if (ll.getChildCount() > 0 && ll.getChildAt(0) instanceof TextView) {
                        TextView tv = (TextView) ll.getChildAt(0);
                        if (tv.getText().toString().contains("Точек:")) {
                            container = ll;
                            break;
                        }
                    }
                }
            }
            if (container != null) {
                updateMacroUI(container);
            }
        }
    }

    // ==================== ВЫПОЛНЕНИЕ МАКРОСА ====================

    private void startMacroExecution() {
        MacroConfig config = getCurrentMacro();
        if (config == null || config.points.isEmpty()) {
            Toast.makeText(this, "Нет точек для выполнения", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Включите специальные возможности", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }

        isMacroRunning = true;
        isMacroPaused = false;
        stopRequested = false;
        runningConfig = config;
        currentPointIndex = 0;
        currentRepeatCount = 0;
        macroStartTime = System.currentTimeMillis();
        
        config.lastUsed = System.currentTimeMillis();
        config.useCount++;
        saveMacroConfigs();
        
        Toast.makeText(this, "▶ Запуск: " + config.name, Toast.LENGTH_SHORT).show();
        executeNextPoint();
    }

    private void toggleMacroPause() {
        if (isMacroRunning) {
            isMacroPaused = !isMacroPaused;
            if (isMacroPaused) {
                Toast.makeText(this, "⏸ Макрос на паузе", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "▶ Макрос продолжен", Toast.LENGTH_SHORT).show();
                executeNextPoint();
            }
        }
    }

    private void executeNextPoint() {
        if (stopRequested || !isMacroRunning || runningConfig == null) {
            stopMacroExecution();
            return;
        }

        if (isMacroPaused) {
            mainHandler.postDelayed(() -> executeNextPoint(), 100);
            return;
        }

        if (currentPointIndex >= runningConfig.points.size()) {
            if (runningConfig.isLoop) {
                currentPointIndex = 0;
                currentRepeatCount++;
                mainHandler.postDelayed(() -> executeNextPoint(), 500);
            } else if (currentRepeatCount < runningConfig.loopCount - 1) {
                currentRepeatCount++;
                currentPointIndex = 0;
                mainHandler.postDelayed(() -> executeNextPoint(), 500);
            } else {
                stopMacroExecution();
            }
            return;
        }

        MacroPoint point = runningConfig.points.get(currentPointIndex);
        
        executor.execute(() -> {
            try {
                int repeatCount = point.repeatCount > 0 ? point.repeatCount : 1;
                for (int i = 0; i < repeatCount && !stopRequested; i++) {
                    int x = point.x;
                    int y = point.y;
                    if (point.randomOffset) {
                        Random rand = new Random();
                        x += rand.nextInt(point.offsetRange * 2) - point.offsetRange;
                        y += rand.nextInt(point.offsetRange * 2) - point.offsetRange;
                    }
                    
                    if (point.actionType.equals("swipe")) {
                        performSwipe(point);
                    } else if (point.actionType.equals("wait")) {
                        try { Thread.sleep(point.delay); } catch (InterruptedException e) {}
                    } else {
                        performClick(x, y);
                    }
                    
                    if (stopRequested) break;
                }
            } catch (Exception e) {
                Log.e(TAG, "executeNextPoint error", e);
            }
        });

        currentPointIndex++;
        mainHandler.postDelayed(() -> executeNextPoint(), point.delay);
    }

    private void performClick(int x, int y) {
        try {
            MacroService service = MacroService.getInstance();
            if (service != null) {
                service.performClick(x, y);
            }
        } catch (Exception e) {
            Log.e(TAG, "performClick error", e);
        }
    }

    private void performSwipe(MacroPoint point) {
        try {
            MacroService service = MacroService.getInstance();
            if (service != null) {
                service.performSwipe(point.x, point.y, point.x2, point.y2, point.swipeDuration);
            }
        } catch (Exception e) {
            Log.e(TAG, "performSwipe error", e);
        }
    }

    private void stopMacroExecution() {
        isMacroRunning = false;
        isMacroPaused = false;
        stopRequested = true;
        runningConfig = null;
        mainHandler.removeCallbacksAndMessages(null);
        Toast.makeText(this, "■ Макрос остановлен", Toast.LENGTH_SHORT).show();
    }

    // ==================== АВТО КЛИКЕР ====================

    private void startAutoClickerRecording() {
        if (isAutoClickerRecording) return;
        if (windowManager == null) return;
        
        MacroConfig config = getCurrentMacro();
        if (config == null || !config.type.equals("autoclicker")) {
            Toast.makeText(this, "Переключите макрос на Авто кликер", Toast.LENGTH_SHORT).show();
            return;
        }
        
        isAutoClickerRecording = true;
        Toast.makeText(this, "Нажмите на экран для добавления точки", Toast.LENGTH_SHORT).show();
        
        autoClickerOverlay = new FrameLayout(this);
        autoClickerOverlay.setBackgroundColor(0x3300FF00);
        autoClickerOverlay.setClickable(false);
        
        int flag = getOverlayFlag();
        WindowManager.LayoutParams captureParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                flag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        
        autoClickerOverlay.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP && isAutoClickerRecording) {
                int x = (int) event.getRawX();
                int y = (int) event.getRawY();
                addAutoClickerPoint(x, y);
                return true;
            }
            return false;
        });
        
        windowManager.addView(autoClickerOverlay, captureParams);
    }

    private void addAutoClickerPoint(int x, int y) {
        MacroConfig config = getCurrentMacro();
        if (config == null) return;

        MacroPoint point = new MacroPoint(x, y);
        point.actionType = "click";
        config.points.add(point);
        saveMacroConfigs();

        Toast.makeText(this, "Точка " + config.points.size() + ": " + x + ", " + y, Toast.LENGTH_SHORT).show();
        showPointDelayDialog(config.points.size() - 1);

        if (windows.containsKey("autoclicker")) {
            LinearLayout container = null;
            LinearLayout mainLayout = (LinearLayout) windows.get("autoclicker").contentView;
            for (int i = 0; i < mainLayout.getChildCount(); i++) {
                View v = mainLayout.getChildAt(i);
                if (v instanceof LinearLayout) {
                    LinearLayout ll = (LinearLayout) v;
                    if (ll.getChildCount() > 0 && ll.getChildAt(0) instanceof TextView) {
                        TextView tv = (TextView) ll.getChildAt(0);
                        if (tv.getText().toString().contains("Точек:")) {
                            container = ll;
                            break;
                        }
                    }
                }
            }
            if (container != null) {
                updateAutoClickerUI(container);
            }
        }

        removeAutoClickerOverlay();
    }

    private void removeAutoClickerOverlay() {
        try {
            if (autoClickerOverlay != null && windowManager != null) {
                windowManager.removeView(autoClickerOverlay);
                autoClickerOverlay = null;
                isAutoClickerRecording = false;
            }
        } catch (Exception e) {
            Log.e(TAG, "removeAutoClickerOverlay error", e);
        }
    }

    private void showPointDelayDialog(final int index) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Настройки точки");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        final EditText delayInput = new EditText(this);
        delayInput.setHint("Задержка (мс)");
        delayInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        delayInput.setText("1000");
        layout.addView(delayInput);

        final EditText repeatInput = new EditText(this);
        repeatInput.setHint("Повторов");
        repeatInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        repeatInput.setText("1");
        layout.addView(repeatInput);

        final CheckBox randomCheck = new CheckBox(this);
        randomCheck.setText("Случайное смещение");
        randomCheck.setTextColor(Color.WHITE);
        layout.addView(randomCheck);

        builder.setView(layout);
        builder.setPositiveButton("OK", (d, w) -> {
            try {
                MacroConfig config = getCurrentMacro();
                if (config != null && index < config.points.size()) {
                    MacroPoint point = config.points.get(index);
                    point.delay = Integer.parseInt(delayInput.getText().toString());
                    point.repeatCount = Integer.parseInt(repeatInput.getText().toString());
                    point.randomOffset = randomCheck.isChecked();
                    point.offsetRange = 10;
                    saveMacroConfigs();
                    
                    if (windows.containsKey("autoclicker")) {
                        LinearLayout container = null;
                        LinearLayout mainLayout = (LinearLayout) windows.get("autoclicker").contentView;
                        for (int i = 0; i < mainLayout.getChildCount(); i++) {
                            View v = mainLayout.getChildAt(i);
                            if (v instanceof LinearLayout) {
                                LinearLayout ll = (LinearLayout) v;
                                if (ll.getChildCount() > 0 && ll.getChildAt(0) instanceof TextView) {
                                    TextView tv = (TextView) ll.getChildAt(0);
                                    if (tv.getText().toString().contains("Точек:")) {
                                        container = ll;
                                        break;
                                    }
                                }
                            }
                        }
                        if (container != null) {
                            updateAutoClickerUI(container);
                        }
                    }
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Ошибка", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void startAutoClicker() {
        MacroConfig config = getCurrentMacro();
        if (config == null || !config.type.equals("autoclicker") || config.points.isEmpty()) {
            Toast.makeText(this, "Нет точек для авто кликера", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Включите специальные возможности", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }

        isAutoClickerRunning = true;
        isAutoClickerPaused = false;
        stopRequested = false;
        runningConfig = config;
        currentPointIndex = 0;
        currentRepeatCount = 0;
        
        Toast.makeText(this, "🔄 Авто кликер запущен", Toast.LENGTH_SHORT).show();
        executeAutoClickerPoint();
    }

    private void executeAutoClickerPoint() {
        if (stopRequested || !isAutoClickerRunning || runningConfig == null) {
            stopAutoClicker();
            return;
        }

        if (isAutoClickerPaused) {
            mainHandler.postDelayed(() -> executeAutoClickerPoint(), 100);
            return;
        }

        if (currentPointIndex >= runningConfig.points.size()) {
            if (runningConfig.isLoop) {
                currentPointIndex = 0;
                currentRepeatCount++;
                mainHandler.postDelayed(() -> executeAutoClickerPoint(), 500);
            } else if (currentRepeatCount < runningConfig.loopCount - 1) {
                currentRepeatCount++;
                currentPointIndex = 0;
                mainHandler.postDelayed(() -> executeAutoClickerPoint(), 500);
            } else {
                stopAutoClicker();
            }
            return;
        }

        MacroPoint point = runningConfig.points.get(currentPointIndex);
        
        int repeatCount = point.repeatCount > 0 ? point.repeatCount : 1;
        for (int i = 0; i < repeatCount && !stopRequested; i++) {
            int x = point.x;
            int y = point.y;
            if (point.randomOffset) {
                Random rand = new Random();
                x += rand.nextInt(point.offsetRange * 2) - point.offsetRange;
                y += rand.nextInt(point.offsetRange * 2) - point.offsetRange;
            }
            performClick(x, y);
            try { Thread.sleep(50); } catch (InterruptedException e) {}
        }

        currentPointIndex++;
        mainHandler.postDelayed(() -> executeAutoClickerPoint(), point.delay);
    }

    private void stopAutoClicker() {
        isAutoClickerRunning = false;
        isAutoClickerPaused = false;
        stopRequested = true;
        runningConfig = null;
        mainHandler.removeCallbacksAndMessages(null);
        Toast.makeText(this, "■ Авто кликер остановлен", Toast.LENGTH_SHORT).show();
    }

    // ==================== ПЛАВАЮЩИЕ КНОПКИ ====================

    private void createFloatingButton(MacroConfig config, String name, int color) {
        try {
            if (windowManager == null) return;
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Требуется разрешение на оверлей", Toast.LENGTH_SHORT).show();
                return;
            }

            if (floatingButtons.containsKey(name)) {
                removeFloatingButton(name);
            }

            FloatingButton btn = new FloatingButton();
            btn.macroName = name;
            btn.color = color;
            btn.buttonId = name;
            btn.isVisible = true;
            btn.size = config.buttonSize > 0 ? config.buttonSize : 80;
            btn.isFixed = config.buttonFixed;

            btn.container = new FrameLayout(this);
            btn.container.setBackgroundColor(Color.TRANSPARENT);

            FrameLayout circle = new FrameLayout(this);
            GradientDrawable circleBg = new GradientDrawable();
            if (btn.isSquare) {
                circleBg.setShape(GradientDrawable.RECTANGLE);
                circleBg.setCornerRadius(btn.cornerRadius);
            } else {
                circleBg.setShape(GradientDrawable.OVAL);
            }
            circleBg.setColor(color);
            circleBg.setStroke(3, Color.WHITE);
            circle.setBackground(circleBg);

            btn.label = new TextView(this);
            String labelText = name.length() > 4 ? name.substring(0, 4) : name;
            btn.label.setText(labelText);
            btn.label.setTextColor(Color.WHITE);
            btn.label.setTextSize(14);
            btn.label.setTypeface(null, android.graphics.Typeface.BOLD);
            btn.label.setGravity(Gravity.CENTER);
            btn.label.setPadding(4, 4, 4, 4);
            circle.addView(btn.label);

            TextView icon = new TextView(this);
            icon.setText("▶");
            icon.setTextColor(Color.WHITE);
            icon.setTextSize(10);
            icon.setGravity(Gravity.TOP | Gravity.END);
            icon.setPadding(0, 2, 4, 0);
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
            );
            iconParams.gravity = Gravity.TOP | Gravity.END;
            circle.addView(icon, iconParams);

            btn.container.addView(circle, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));

            btn.params = new WindowManager.LayoutParams(
                    btn.size,
                    btn.size,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            btn.params.gravity = Gravity.TOP | Gravity.START;
            btn.params.x = config.buttonX > 0 ? config.buttonX : 200;
            btn.params.y = config.buttonY > 0 ? config.buttonY : 300;

            if (btn.isFixed) {
                btn.params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            }

            btn.container.setOnTouchListener(new View.OnTouchListener() {
                private boolean isDragging = false;
                private long lastTapTime = 0;

                @Override
                public boolean onTouch(View v, MotionEvent e) {
                    if (btn.isFixed) return false;

                    switch (e.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            startX = e.getRawX();
                            startY = e.getRawY();
                            initialX = btn.params.x;
                            initialY = btn.params.y;
                            isDragging = false;
                            return true;
                            
                        case MotionEvent.ACTION_MOVE:
                            float dx = e.getRawX() - startX;
                            float dy = e.getRawY() - startY;
                            if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                                isDragging = true;
                            }
                            if (isDragging) {
                                btn.params.x = initialX + (int) dx;
                                btn.params.y = initialY + (int) dy;
                                if (windowManager != null) {
                                    windowManager.updateViewLayout(btn.container, btn.params);
                                    saveButtonPosition(btn);
                                }
                            }
                            return true;
                            
                        case MotionEvent.ACTION_UP:
                            if (!isDragging) {
                                long currentTime = System.currentTimeMillis();
                                if (currentTime - lastTapTime < 300) {
                                    showButtonMenu(btn);
                                } else {
                                    runMacroByName(btn.macroName);
                                }
                                lastTapTime = currentTime;
                            }
                            return true;
                    }
                    return false;
                }
            });

            btn.container.setOnLongClickListener(v -> {
                showButtonMenu(btn);
                return true;
            });

            windowManager.addView(btn.container, btn.params);
            floatingButtons.put(name, btn);
            buttonMacroMap.put(name, config);

            config.buttonName = name;
            config.buttonColor = color;
            config.buttonX = btn.params.x;
            config.buttonY = btn.params.y;
            config.buttonSize = btn.size;
            config.buttonFixed = btn.isFixed;
            saveMacroConfigs();

            Toast.makeText(this, "🔘 Кнопка создана: " + name, Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Log.e(TAG, "createFloatingButton error", e);
            Toast.makeText(this, "Ошибка создания кнопки", Toast.LENGTH_SHORT).show();
        }
    }

    private void removeFloatingButton(String name) {
        try {
            if (floatingButtons.containsKey(name)) {
                FloatingButton btn = floatingButtons.get(name);
                if (btn.container != null && windowManager != null) {
                    windowManager.removeView(btn.container);
                }
                floatingButtons.remove(name);
                buttonMacroMap.remove(name);
            }
        } catch (Exception e) {
            Log.e(TAG, "removeFloatingButton error", e);
        }
    }

    private void showFloatingButtons() {
        for (FloatingButton btn : floatingButtons.values()) {
            if (btn.isVisible && btn.container != null) {
                btn.container.setVisibility(View.VISIBLE);
            }
        }
        buttonsVisible = true;
    }

    private void hideFloatingButtons() {
        for (FloatingButton btn : floatingButtons.values()) {
            if (btn.container != null) {
                btn.container.setVisibility(View.GONE);
            }
        }
        buttonsVisible = false;
    }

    private void toggleButtonsVisibility() {
        if (buttonsVisible) {
            hideFloatingButtons();
            Toast.makeText(this, "🙈 Кнопки скрыты", Toast.LENGTH_SHORT).show();
        } else {
            showFloatingButtons();
            Toast.makeText(this, "👀 Кнопки показаны", Toast.LENGTH_SHORT).show();
        }
    }

    private void showButtonMenu(final FloatingButton btn) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🔘 " + btn.macroName);

        String[] items = {
            "▶ Выполнить",
            "🎨 Изменить цвет",
            "📏 Изменить размер",
            "✏️ Изменить название",
            btn.isFixed ? "🔓 Разблокировать" : "🔒 Закрепить",
            "🔲 Квадратная/Круглая",
            "🎯 Сбросить позицию",
            "🗑 Удалить"
        };

        builder.setItems(items, (d, which) -> {
            switch (which) {
                case 0:
                    runMacroByName(btn.macroName);
                    break;
                case 1:
                    showButtonColorPicker(btn);
                    break;
                case 2:
                    showButtonSizePicker(btn);
                    break;
                case 3:
                    showButtonRenameDialog(btn);
                    break;
                case 4:
                    toggleButtonFixed(btn);
                    break;
                case 5:
                    toggleButtonShape(btn);
                    break;
                case 6:
                    resetButtonPosition(btn);
                    break;
                case 7:
                    removeFloatingButton(btn.macroName);
                    Toast.makeText(this, "Кнопка удалена", Toast.LENGTH_SHORT).show();
                    break;
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void showButtonColorPicker(final FloatingButton btn) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Выберите цвет");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        final EditText colorInput = new EditText(this);
        colorInput.setHint("HEX код #FF0000");
        colorInput.setText("#" + Integer.toHexString(btn.color).substring(2));
        layout.addView(colorInput);

        LinearLayout presetLayout = new LinearLayout(this);
        presetLayout.setOrientation(LinearLayout.HORIZONTAL);
        presetLayout.setGravity(Gravity.CENTER);
        presetLayout.setPadding(0, 8, 0, 8);

        int[] colors = {
            0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFF00,
            0xFFFF00FF, 0xFF00FFFF, 0xFFFFFFFF, 0xFFFF8800,
            0xFFFF44AA, 0xFF44FF44, 0xFF4444FF, 0xFFFFAA44
        };
        
        for (int color : colors) {
            Button colorBtn = new Button(this);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(color);
            colorBtn.setBackground(bg);
            colorBtn.setPadding(12, 12, 12, 12);
            colorBtn.setOnClickListener(v -> {
                btn.color = color;
                updateButtonAppearance(btn);
                saveButtonPosition(btn);
                colorInput.setText("#" + Integer.toHexString(color).substring(2));
            });
            presetLayout.addView(colorBtn);
        }
        layout.addView(presetLayout);

        builder.setView(layout);
        builder.setPositiveButton("Применить", (d, w) -> {
            try {
                String hex = colorInput.getText().toString().trim();
                if (hex.startsWith("#")) hex = hex.substring(1);
                btn.color = Color.parseColor("#" + hex);
                updateButtonAppearance(btn);
                saveButtonPosition(btn);
            } catch (Exception e) {
                Toast.makeText(this, "Ошибка", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void showButtonSizePicker(final FloatingButton btn) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Размер кнопки");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        final SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(200);
        seekBar.setMin(30);
        seekBar.setProgress(btn.size);
        layout.addView(seekBar);

        final TextView sizeText = new TextView(this);
        sizeText.setText("Размер: " + btn.size + "px");
        sizeText.setTextColor(Color.WHITE);
        sizeText.setGravity(Gravity.CENTER);
        sizeText.setTextSize(16);
        layout.addView(sizeText);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    btn.size = progress;
                    btn.params.width = progress;
                    btn.params.height = progress;
                    if (windowManager != null) {
                        windowManager.updateViewLayout(btn.container, btn.params);
                    }
                    sizeText.setText("Размер: " + progress + "px");
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        builder.setView(layout);
        builder.setPositiveButton("OK", (d, w) -> {
            saveButtonPosition(btn);
            MacroConfig config = buttonMacroMap.get(btn.macroName);
            if (config != null) {
                config.buttonSize = btn.size;
                saveMacroConfigs();
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void showButtonRenameDialog(final FloatingButton btn) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Новое название");

        final EditText input = new EditText(this);
        input.setHint("Название");
        input.setText(btn.macroName);
        input.setSelectAllOnFocus(true);
        builder.setView(input);

        builder.setPositiveButton("OK", (d, w) -> {
            String newName = input.getText().toString().trim();
            if (newName.isEmpty()) {
                Toast.makeText(this, "Название не может быть пустым", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newName.equals(btn.macroName) && floatingButtons.containsKey(newName)) {
                Toast.makeText(this, "Кнопка с таким именем уже существует", Toast.LENGTH_SHORT).show();
                return;
            }

            String oldName = btn.macroName;
            btn.macroName = newName;
            btn.label.setText(newName.length() > 4 ? newName.substring(0, 4) : newName);

            MacroConfig config = buttonMacroMap.get(oldName);
            if (config != null) {
                config.buttonName = newName;
                buttonMacroMap.remove(oldName);
                buttonMacroMap.put(newName, config);
                saveMacroConfigs();
            }

            floatingButtons.remove(oldName);
            floatingButtons.put(newName, btn);

            Toast.makeText(this, "Название изменено", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void toggleButtonFixed(FloatingButton btn) {
        btn.isFixed = !btn.isFixed;
        if (btn.isFixed) {
            btn.params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            Toast.makeText(this, "🔒 Кнопка закреплена", Toast.LENGTH_SHORT).show();
        } else {
            btn.params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            Toast.makeText(this, "🔓 Кнопка разблокирована", Toast.LENGTH_SHORT).show();
        }
        if (windowManager != null) {
            windowManager.updateViewLayout(btn.container, btn.params);
        }
        saveButtonPosition(btn);
        
        MacroConfig config = buttonMacroMap.get(btn.macroName);
        if (config != null) {
            config.buttonFixed = btn.isFixed;
            saveMacroConfigs();
        }
    }

    private void toggleButtonShape(FloatingButton btn) {
        btn.isSquare = !btn.isSquare;
        View circle = ((ViewGroup) btn.container.getChildAt(0));
        GradientDrawable bg = (GradientDrawable) circle.getBackground();
        if (btn.isSquare) {
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(btn.cornerRadius);
        } else {
            bg.setShape(GradientDrawable.OVAL);
        }
        Toast.makeText(this, btn.isSquare ? "🔲 Квадратная" : "⭕ Круглая", Toast.LENGTH_SHORT).show();
    }

    private void resetButtonPosition(FloatingButton btn) {
        btn.params.x = 200;
        btn.params.y = 300;
        if (windowManager != null) {
            windowManager.updateViewLayout(btn.container, btn.params);
        }
        saveButtonPosition(btn);
        Toast.makeText(this, "Позиция сброшена", Toast.LENGTH_SHORT).show();
    }

    private void updateButtonAppearance(FloatingButton btn) {
        View circle = ((ViewGroup) btn.container.getChildAt(0));
        GradientDrawable bg = (GradientDrawable) circle.getBackground();
        bg.setColor(btn.color);
    }

    private void saveButtonPosition(FloatingButton btn) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("name", btn.macroName);
            obj.put("color", btn.color);
            obj.put("x", btn.params.x);
            obj.put("y", btn.params.y);
            obj.put("size", btn.params.width);
            obj.put("fixed", btn.isFixed);
            obj.put("square", btn.isSquare);
            prefs.edit().putString("button_" + btn.macroName, obj.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "saveButtonPosition error", e);
        }
    }

    private void restoreButtons() {
        try {
            for (MacroConfig config : macroConfigs) {
                if (config.buttonName != null && !config.buttonName.isEmpty()) {
                    String json = prefs.getString("button_" + config.buttonName, "");
                    if (!json.isEmpty()) {
                        JSONObject obj = new JSONObject(json);
                        config.buttonColor = obj.optInt("color", config.buttonColor);
                        config.buttonX = obj.optInt("x", config.buttonX);
                        config.buttonY = obj.optInt("y", config.buttonY);
                        config.buttonSize = obj.optInt("size", config.buttonSize);
                        config.buttonFixed = obj.optBoolean("fixed", config.buttonFixed);
                        boolean isSquare = obj.optBoolean("square", false);
                        createFloatingButton(config, config.buttonName, config.buttonColor);
                        if (floatingButtons.containsKey(config.buttonName)) {
                            FloatingButton btn = floatingButtons.get(config.buttonName);
                            btn.isSquare = isSquare;
                            btn.params.x = config.buttonX;
                            btn.params.y = config.buttonY;
                            btn.size = config.buttonSize;
                            btn.params.width = btn.size;
                            btn.params.height = btn.size;
                            btn.isFixed = config.buttonFixed;
                            if (btn.isFixed) {
                                btn.params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                            }
                            if (windowManager != null) {
                                windowManager.updateViewLayout(btn.container, btn.params);
                            }
                            updateButtonAppearance(btn);
                        }
                    } else {
                        createFloatingButton(config, config.buttonName, config.buttonColor);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "restoreButtons error", e);
        }
    }

    private void saveButtonPositions() {
        for (FloatingButton btn : floatingButtons.values()) {
            saveButtonPosition(btn);
        }
    }

    private void runMacroByName(String name) {
        MacroConfig config = buttonMacroMap.get(name);
        if (config != null) {
            String oldName = currentMacroName;
            currentMacroName = config.name;
            startMacroExecution();
            currentMacroName = oldName;
        } else {
            Toast.makeText(this, "Макрос не найден", Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== ПЕРСОНАЖИ ====================

    private void updateCharactersUI(LinearLayout container) {
        container.removeAllViews();
        loadCharacters();

        if (characters.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("📭 Нет персонажей\nНажмите + чтобы добавить");
            empty.setTextColor(0xFF888888);
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, 40, 0, 40);
            container.addView(empty);
            return;
        }

        for (int i = 0; i < characters.size(); i++) {
            CharacterData data = characters.get(i);
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(8, 8, 8, 8);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(12);
            bg.setColor(0x22FFFFFF);
            bg.setStroke(2, getThemeColor());
            item.setBackground(bg);

            ImageView icon = new ImageView(this);
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(),
                        Uri.fromFile(new File(data.path)));
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 60, 60, true);
                icon.setImageBitmap(scaled);
            } catch (Exception e) {
                icon.setImageBitmap(createPlaceholderIcon());
            }
            icon.setPadding(4, 4, 4, 4);
            item.addView(icon);

            TextView nameText = new TextView(this);
            nameText.setText(data.name);
            nameText.setTextColor(Color.WHITE);
            nameText.setTextSize(14);
            nameText.setPadding(12, 0, 0, 0);
            nameText.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            item.addView(nameText);

            Button delBtn = new Button(this);
            delBtn.setText("✕");
            delBtn.setTextColor(0xFFFF0000);
            delBtn.setTextSize(16);
            delBtn.setBackgroundColor(0x33FF0000);
            delBtn.setPadding(8, 4, 8, 4);
            final int idx = i;
            delBtn.setOnClickListener(v -> {
                characters.remove(idx);
                saveCharacters();
                updateCharactersUI(container);
                Toast.makeText(this, "Персонаж удалён", Toast.LENGTH_SHORT).show();
            });
            item.addView(delBtn);

            container.addView(item);
        }
    }

    private Bitmap createPlaceholderIcon() {
        Bitmap b = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.GRAY);
        c.drawCircle(30, 30, 25, p);
        p.setColor(Color.WHITE);
        p.setTextSize(30);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText("P", 30, 42, p);
        return b;
    }

    private void showAddCharacterDialog(final LinearLayout container) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Новый персонаж");
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        final EditText input = new EditText(this);
        input.setHint("Имя персонажа");
        input.setText("Персонаж " + (characters.size() + 1));
        layout.addView(input);

        builder.setView(layout);
        builder.setPositiveButton("Выбрать фото", (d, w) -> {
            tempCharacterName = input.getText().toString().trim();
            if (tempCharacterName.isEmpty()) tempCharacterName = "Персонаж " + (characters.size() + 1);
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, REQUEST_GALLERY);
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void loadCharacters() {
        try {
            characters.clear();
            String json = prefs.getString("characters_list", "");
            if (!json.isEmpty()) {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    characters.add(new CharacterData(array.getJSONObject(i)));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "loadCharacters error", e);
        }
    }

    private void saveCharacters() {
        try {
            JSONArray array = new JSONArray();
            for (CharacterData data : characters) {
                array.put(data.toJSON());
            }
            prefs.edit().putString("characters_list", array.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "saveCharacters error", e);
        }
    }

    private String saveImageToStorage(Bitmap bitmap) {
        try {
            File dir = new File(getExternalFilesDir(null), "characters");
            if (!dir.exists()) dir.mkdirs();
            
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File file = new File(dir, "CHAR_" + timeStamp + ".png");
            
            FileOutputStream out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.close();
            
            return file.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, "saveImageToStorage error", e);
            return null;
        }
    }

    // ==================== КОНФИГИ ====================

    private void importConfig() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Выберите JSON"), REQUEST_IMPORT_CONFIG);
    }

    private void exportConfig() {
        try {
            JSONArray array = new JSONArray();
            for (MacroConfig config : macroConfigs) {
                array.put(config.toJSON());
            }
            String json = array.toString(2);

            File dir = new File(getExternalFilesDir(null), "configs");
            if (!dir.exists()) dir.mkdirs();
            
            String fileName = "cr_arcade_config_" + 
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + 
                ".json";
            File file = new File(dir, fileName);
            FileOutputStream out = new FileOutputStream(file);
            out.write(json.getBytes());
            out.close();

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/json");
            shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
            startActivity(Intent.createChooser(shareIntent, "Поделиться конфигом"));

            Toast.makeText(this, "✅ Конфиг экспортирован", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Log.e(TAG, "exportConfig error", e);
            Toast.makeText(this, "Ошибка экспорта", Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    private Button createStyledButton(String text, int color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(14);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(16);
        bg.setColor(color);
        bg.setAlpha(200);
        btn.setBackground(bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 6, 0, 6);
        btn.setLayoutParams(params);

        return btn;
    }

    private void clearMacroPoints() {
        MacroConfig config = getCurrentMacro();
        if (config != null && !config.points.isEmpty()) {
            config.points.clear();
            saveMacroConfigs();
            if (windows.containsKey("macros")) {
                LinearLayout container = null;
                LinearLayout mainLayout = (LinearLayout) windows.get("macros").contentView;
                for (int i = 0; i < mainLayout.getChildCount(); i++) {
                    View v = mainLayout.getChildAt(i);
                    if (v instanceof LinearLayout) {
                        LinearLayout ll = (LinearLayout) v;
                        if (ll.getChildCount() > 0 && ll.getChildAt(0) instanceof TextView) {
                            TextView tv = (TextView) ll.getChildAt(0);
                            if (tv.getText().toString().contains("Точек:")) {
                                container = ll;
                                break;
                            }
                        }
                    }
                }
                if (container != null) {
                    updateMacroUI(container);
                }
            }
            Toast.makeText(this, "Все точки удалены", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Нет точек для удаления", Toast.LENGTH_SHORT).show();
        }
    }

    private MacroConfig getCurrentMacro() {
        for (MacroConfig c : macroConfigs) {
            if (c.name.equals(currentMacroName)) {
                return c;
            }
        }
        if (!macroConfigs.isEmpty()) {
            currentMacroName = macroConfigs.get(0).name;
            return macroConfigs.get(0);
        }
        MacroConfig newC = new MacroConfig("Макрос 1");
        macroConfigs.add(newC);
        currentMacroName = newC.name;
        return newC;
    }

    private int getCurrentMacroIndex() {
        for (int i = 0; i < macroConfigs.size(); i++) {
            if (macroConfigs.get(i).name.equals(currentMacroName)) {
                return i;
            }
        }
        return 0;
    }

    private void loadMacroConfigs() {
        try {
            macroConfigs.clear();
            String json = prefs.getString("macro_configs", "");
            if (!json.isEmpty()) {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    macroConfigs.add(new MacroConfig(array.getJSONObject(i)));
                }
            }
            if (macroConfigs.isEmpty()) {
                macroConfigs.add(new MacroConfig("Макрос 1"));
            }
            currentMacroName = macroConfigs.get(0).name;
        } catch (Exception e) {
            Log.e(TAG, "loadMacroConfigs error", e);
        }
    }

    private void saveMacroConfigs() {
        try {
            JSONArray array = new JSONArray();
            for (MacroConfig config : macroConfigs) {
                array.put(config.toJSON());
            }
            prefs.edit().putString("macro_configs", array.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "saveMacroConfigs error", e);
        }
    }

    private void loadSettings() {
        overlayAlpha = prefs.getInt("overlay_alpha", 200);
        overlaySize = prefs.getInt("overlay_size", 80);
        buttonsVisible = prefs.getBoolean("buttons_visible", true);
        primaryColor = prefs.getInt("primary_color", 0xFFFF0000);
        rainbowMode = prefs.getBoolean("rainbow_mode", false);
        recordPassThrough = prefs.getBoolean("record_pass_through", true);
    }

    private int getThemeColor() {
        if (rainbowMode) {
            rainbowHue += 0.01f;
            if (rainbowHue > 1f) rainbowHue = 0f;
            return Color.HSVToColor(new float[]{rainbowHue * 360f, 0.9f, 1f});
        }
        return primaryColor;
    }

    private boolean isAccessibilityServiceEnabled() {
        try {
            String service = getPackageName() + "/" + MacroService.class.getCanonicalName();
            String enabledServices = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return enabledServices != null && enabledServices.contains(service);
        } catch (Exception e) {
            return false;
        }
    }

    private int getOverlayFlag() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;
    }

    private void checkPermissions() {
        checkOverlayPermission();
        requestPermissionsIfNeeded();
        requestAccessibilityPermission();
    }

    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
            }
        }
    }

    private void requestPermissionsIfNeeded() {
        try {
            ArrayList<String> permissions = new ArrayList<>();
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.POST_NOTIFICATIONS);
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                        != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
                }
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
                }
            }
            
            if (!permissions.isEmpty()) {
                ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), 100);
            }
        } catch (Exception e) {
            Log.e(TAG, "requestPermissionsIfNeeded error", e);
        }
    }

    private void requestAccessibilityPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                String service = getPackageName() + "/" + MacroService.class.getCanonicalName();
                String enabledServices = Settings.Secure.getString(getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                if (enabledServices == null || !enabledServices.contains(service)) {
                    showAccessibilityDialog();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "requestAccessibilityPermission error", e);
        }
    }

    private void showAccessibilityDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⚙️ Специальные возможности");
        builder.setMessage("Для работы макросов необходимо включить специальные возможности.\n\n" +
                          "Это позволит приложению выполнять клики и свайпы в других приложениях.");
        builder.setPositiveButton("Включить", (d, w) -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivityForResult(intent, REQUEST_ACCESSIBILITY);
        });
        builder.setNegativeButton("Позже", null);
        builder.show();
    }

    // ==================== ОБРАБОТЧИКИ РЕЗУЛЬТАТОВ ====================

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    if (!isAppInForeground) {
                        createMainCircle();
                    }
                    Toast.makeText(this, "Разрешение на оверлей получено", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Разрешение на оверлей требуется!", Toast.LENGTH_LONG).show();
                }
            }
        }
        
        if (requestCode == REQUEST_ACCESSIBILITY) {
            if (isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "Специальные возможности включены", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Включите специальные возможности для работы макросов", Toast.LENGTH_LONG).show();
            }
        }
        
        if (requestCode == REQUEST_GALLERY && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                try {
                    Bitmap original = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                    String path = saveImageToStorage(original);
                    if (path != null) {
                        characters.add(new CharacterData(tempCharacterName, path));
                        saveCharacters();
                        Toast.makeText(this, "✅ Персонаж сохранён", Toast.LENGTH_SHORT).show();
                        
                        if (windows.containsKey("characters")) {
                            LinearLayout container = null;
                            LinearLayout mainLayout = (LinearLayout) windows.get("characters").contentView;
                            for (int i = 0; i < mainLayout.getChildCount(); i++) {
                                View v = mainLayout.getChildAt(i);
                                if (v instanceof LinearLayout) {
                                    LinearLayout ll = (LinearLayout) v;
                                    if (ll.getChildCount() > 0 && ll.getChildAt(0) instanceof TextView) {
                                        TextView tv = (TextView) ll.getChildAt(0);
                                        if (tv.getText().toString().contains("Нет персонажей")) {
                                            container = ll;
                                            break;
                                        }
                                    }
                                }
                            }
                            if (container != null) {
                                updateCharactersUI(container);
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Gallery error", e);
                    Toast.makeText(this, "Ошибка загрузки", Toast.LENGTH_SHORT).show();
                }
            }
        }
        
        if (requestCode == REQUEST_IMPORT_CONFIG && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    FileInputStream fis = (FileInputStream) getContentResolver().openInputStream(uri);
                    byte[] buffer = new byte[fis.available()];
                    fis.read(buffer);
                    fis.close();
                    String json = new String(buffer);
                    JSONArray array = new JSONArray(json);
                    for (int i = 0; i < array.length(); i++) {
                        macroConfigs.add(new MacroConfig(array.getJSONObject(i)));
                    }
                    saveMacroConfigs();
                    Toast.makeText(this, "✅ Импортировано " + array.length() + " макросов", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e(TAG, "Import error", e);
                    Toast.makeText(this, "❌ Ошибка импорта", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            switch (code) {
                case 100:
                    Toast.makeText(this, "Разрешения получены", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    }
  }
