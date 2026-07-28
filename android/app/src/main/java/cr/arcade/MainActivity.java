package com.cr.arcade;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;

import com.getcapacitor.BridgeActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainActivity extends BridgeActivity {

    // ==================== КОНСТАНТЫ ====================
    private static final int REQUEST_MICROPHONE = 100;
    private static final int REQUEST_CAMERA = 102;
    private static final int REQUEST_OVERLAY_PERMISSION = 101;
    private static final int REQUEST_GALLERY = 103;
    private static final int REQUEST_STORAGE = 104;
    private static final int REQUEST_NOTIFICATION = 106;
    private static final int REQUEST_ACCESSIBILITY = 107;
    private static final int REQUEST_IMPORT_CONFIG = 108;
    private static final int REQUEST_EXPORT_CONFIG = 109;
    private static final int REQUEST_PICK_IMAGE = 110;

    private static final String URL_HOME = "https://wyikhedfghhopyewfvjkurrhncswehipkhf.vercel.app/";
    private static final String URL_SETTINGS = "https://whuokhgrdcbnmkloplureecvjiqoendu.vercel.app/";
    private static final String PREFS_NAME = "arcade_data";
    private static final String TAG = "CRArcade";

    // ==================== ОСНОВНЫЕ ПЕРЕМЕННЫЕ ====================
    private WindowManager windowManager;
    private FrameLayout mainCircleContainer;
    private WindowManager.LayoutParams mainCircleParams;
    private SharedPreferences prefs;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private long backPressedTime = 0;
    
    private float startX, startY;
    private int initialX, initialY;
    private boolean isDragging = false;
    private boolean isAppInForeground = true;

    // ==================== НАСТРОЙКИ ТЕМ ====================
    private String currentTheme = "dark_red";
    private int primaryColor = 0xFFFF0000;
    private int secondaryColor = 0xFFCC0000;
    private int accentColor = 0xFFFF4444;
    private boolean rainbowMode = false;
    private float rainbowHue = 0;
    private int overlayAlpha = 200;
    private int overlaySize = 80;
    private boolean buttonsVisible = true;
    private boolean isDarkMode = true;

    // ==================== УПРАВЛЕНИЕ МАКРОСАМИ ====================
    private ArrayList<MacroConfig> macroConfigs = new ArrayList<>();
    private String currentMacroName = "Макрос 1";
    private MacroConfig currentMacroConfig;
    private boolean isMacroRunning = false;
    private boolean isMacroPaused = false;
    private boolean isMacroRecording = false;
    private boolean isAutoClickerRunningGlobal = false;
    private boolean isAutoClickerPaused = false;
    private Handler macroHandler = new Handler(Looper.getMainLooper());
    private Runnable macroRunnable;
    private int currentPointIndex = 0;
    private int currentRepeatCount = 0;
    private boolean stopRequested = false;
    private long macroStartTime = 0;
    private long macroExecutionTime = 0;
    private MacroConfig runningConfig = null;

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
    private float lastRawX = 0, lastRawY = 0;
    private boolean isSwiping = false;
    private boolean recordPassThrough = true;
    private int recordedClickCount = 0;
    private int recordedSwipeCount = 0;

    // ==================== АВТО КЛИКЕР ====================
    private FrameLayout autoClickerOverlay;
    private LinearLayout autoClickerPointsLayout;
    private int selectedPointIndex = -1;
    private boolean isAutoClickerRecording = false;
    private AutoClickerConfig autoClickerConfig = new AutoClickerConfig();
    
    private static class AutoClickerConfig {
        ArrayList<AutoClickerPoint> points = new ArrayList<>();
        boolean loop = true;
        int loopCount = 1;
        int clickDelay = 100;
        int totalClicks = 0;
        int maxClicks = 0;
        boolean stopAfterMaxClicks = false;
        String stopCondition = "never";
        int stopAfterSeconds = 0;
        long startTime = 0;
        boolean isRunning = false;
        boolean isPaused = false;
        int currentPointIndex = 0;
        int currentRepeatCount = 0;
        int clicksDone = 0;
    }
    
    private static class AutoClickerPoint {
        int x, y;
        int delay = 1000;
        int repeatCount = 1;
        boolean randomOffset = false;
        int offsetRange = 10;
        String actionType = "click";
        int x2 = 0, y2 = 0;
        int swipeDuration = 500;
        
        AutoClickerPoint(int x, int y) {
            this.x = x;
            this.y = y;
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
            return json;
        }
        
        AutoClickerPoint(JSONObject json) throws Exception {
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
        }
    }

    // ==================== МАКРО КОНФИГ ====================
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
        boolean recordPassThrough = true;
        long createdTime;
        String description = "";
        boolean isEnabled = true;
        String icon = "🎮";
        int priority = 0;
        String category = "default";
        long lastUsed = 0;
        int useCount = 0;
        
        MacroConfig(String name) {
            this.name = name;
            this.type = "recorded";
            this.points = new ArrayList<>();
            this.actions = new ArrayList<>();
            this.color = 0xFFFF0000;
            this.createdTime = System.currentTimeMillis();
            this.lastUsed = this.createdTime;
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
            this.recordPassThrough = json.optBoolean("recordPassThrough", true);
            this.createdTime = json.optLong("createdTime", System.currentTimeMillis());
            this.description = json.optString("description", "");
            this.isEnabled = json.optBoolean("isEnabled", true);
            this.icon = json.optString("icon", "🎮");
            this.priority = json.optInt("priority", 0);
            this.category = json.optString("category", "default");
            this.lastUsed = json.optLong("lastUsed", this.createdTime);
            this.useCount = json.optInt("useCount", 0);
            
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
            json.put("recordPassThrough", recordPassThrough);
            json.put("createdTime", createdTime);
            json.put("description", description);
            json.put("isEnabled", isEnabled);
            json.put("icon", icon);
            json.put("priority", priority);
            json.put("category", category);
            json.put("lastUsed", lastUsed);
            json.put("useCount", useCount);
            
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
        boolean isKeyPress = false;
        int keyCode = 0;
        String keyName = "";
        
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
            json.put("isKeyPress", isKeyPress);
            json.put("keyCode", keyCode);
            json.put("keyName", keyName);
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
            isKeyPress = json.optBoolean("isKeyPress", false);
            keyCode = json.optInt("keyCode", 0);
            keyName = json.optString("keyName", "");
        }
    }

    // ==================== ПЛАВАЮЩИЕ КНОПКИ ====================
    private HashMap<String, FloatingButton> floatingButtons = new HashMap<>();
    private HashMap<String, MacroConfig> buttonMacroMap = new HashMap<>();
    private ArrayList<FloatingButton> buttonList = new ArrayList<>();

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
        int cornerRadius = 50;
        boolean isSquare = false;
        boolean showBorder = true;
        int borderColor = 0xFFFFFFFF;
        int borderWidth = 3;
        boolean showLabel = true;
        String icon = "▶";
        boolean isGlowing = false;
        long lastClickTime = 0;
        int clickCount = 0;
        View glowView;
        Handler glowHandler = new Handler(Looper.getMainLooper());
        Runnable glowRunnable;
    }

    // ==================== ОКНА ====================
    private HashMap<String, FloatingWindow> windows = new HashMap<>();
    private FrameLayout mainMenuContainer;
    private boolean isMainMenuOpen = false;
    private ArrayList<String> windowStack = new ArrayList<>();

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
        boolean isModal = false;
        boolean isDraggable = true;
        boolean isResizable = true;
        boolean isClosable = true;
        boolean isMinimizable = true;
        boolean isMaximized = false;
        int savedWidth, savedHeight, savedX, savedY;
        String icon = "📦";
        int cornerRadius = 16;
        int borderColor = 0xFFFF0000;
        int borderWidth = 3;
        float alpha = 1.0f;
        boolean isTransparent = false;
        int backgroundColor = 0xDD0D0D0D;
        boolean keepOnTop = false;
        boolean fullScreen = false;
        boolean showTitleBar = true;
        boolean showCloseButton = true;
        boolean showMinimizeButton = true;
        boolean showResizeHandle = true;
        View customTitleBar;
        View customContent;
    }

    // ==================== ПЕРСОНАЖИ ====================
    private ArrayList<CharacterData> characters = new ArrayList<>();
    private String tempCharacterName = "";
    private Bitmap currentCharacterBitmap;
    private FrameLayout characterContainer;
    private ImageView characterView;
    private WindowManager.LayoutParams characterParams;
    private boolean isCharacterFixed = false;
    private boolean isCharacterModeActive = false;

    private static class CharacterData {
        String name;
        String path;
        long timestamp;
        int width;
        int height;
        boolean isFavorite = false;
        String category = "default";
        String description = "";
        int x = 0, y = 0;
        float scale = 1.0f;
        float rotation = 0;
        int alpha = 255;
        
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
            this.x = json.optInt("x", 0);
            this.y = json.optInt("y", 0);
            this.scale = (float) json.optDouble("scale", 1.0);
            this.rotation = (float) json.optDouble("rotation", 0);
            this.alpha = json.optInt("alpha", 255);
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
            json.put("x", x);
            json.put("y", y);
            json.put("scale", scale);
            json.put("rotation", rotation);
            json.put("alpha", alpha);
            return json;
        }
    }

    // ==================== ЖИЗНЕННЫЙ ЦИКЛ ====================

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        try {
            initializeApp();
            createMainCircle();
            createMainMenu();
            loadPersistentData();
        } catch (Exception e) {
            Log.e(TAG, "onCreate error", e);
            Toast.makeText(this, "Ошибка инициализации", Toast.LENGTH_SHORT).show();
        }
    }

    private void initializeApp() {
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        loadSettings();
        loadMacroConfigs();
        loadCharacters();
        loadButtonConfigs();
        checkPermissions();
        setupServices();
    }

    private void checkPermissions() {
        checkOverlayPermission();
        requestPermissionsIfNeeded();
        requestAccessibilityPermission();
    }

    private void setupServices() {
        MacroService.setContext(this);
        MacroService.setMainHandler(mainHandler);
    }

    private void loadPersistentData() {
        restoreButtons();
        restoreWindows();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        isAppInForeground = true;
        removeMainCircle();
        
        if (buttonsVisible) {
            showFloatingButtons();
        }
        
        updateUI();
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        isAppInForeground = false;
        
        if (!isRecordingMode && !isAutoClickerRunningGlobal) {
            createMainCircle();
        }
        
        saveAllData();
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
        if (!isAppInForeground && !isRecordingMode) {
            createMainCircle();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        
        try {
            cleanupResources();
        } catch (Exception e) {
            Log.e(TAG, "onDestroy error", e);
        }
    }

    private void cleanupResources() {
        stopAllMacros();
        stopAutoClicker();
        stopMacroExecution();
        
        removeMainCircle();
        removeRecordingOverlay();
        removeAutoClickerOverlay();
        
        hideAllWindows();
        hideMainMenu();
        
        macroHandler.removeCallbacksAndMessages(null);
        mainHandler.removeCallbacksAndMessages(null);
        
        executor.shutdownNow();
        
        if (currentCharacterBitmap != null) {
            currentCharacterBitmap.recycle();
            currentCharacterBitmap = null;
        }
    }

    private void saveAllData() {
        saveSettings();
        saveMacroConfigs();
        saveCharacters();
        saveButtonConfigs();
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
        
        if (isAutoClickerRunningGlobal) {
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
            hideMainMenu();
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

    // ==================== ГЛАВНОЕ МЕНЮ ====================

    private void createMainMenu() {
        try {
            if (mainMenuContainer != null) return;
            
            mainMenuContainer = new FrameLayout(this);
            mainMenuContainer.setBackgroundColor(0xCC000000);
            mainMenuContainer.setOnTouchListener((v, e) -> {
                if (e.getAction() == MotionEvent.ACTION_OUTSIDE) {
                    hideMainMenu();
                }
                return false;
            });
            
            LinearLayout menuLayout = new LinearLayout(this);
            menuLayout.setOrientation(LinearLayout.VERTICAL);
            menuLayout.setGravity(Gravity.CENTER);
            menuLayout.setPadding(30, 30, 30, 30);
            
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(30);
            bg.setColor(0xDD0D0D0D);
            bg.setStroke(3, primaryColor);
            menuLayout.setBackground(bg);
            
            TextView title = new TextView(this);
            title.setText("⚡ CR ARCADE");
            title.setTextColor(primaryColor);
            title.setTextSize(32);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, 0, 0, 30);
            menuLayout.addView(title);
            
            LinearLayout statusLayout = new LinearLayout(this);
            statusLayout.setOrientation(LinearLayout.HORIZONTAL);
            statusLayout.setGravity(Gravity.CENTER);
            statusLayout.setPadding(0, 0, 0, 20);
            
            TextView statusText = new TextView(this);
            statusText.setText("🟢 Активен");
            statusText.setTextColor(Color.GREEN);
            statusText.setTextSize(14);
            statusLayout.addView(statusText);
            
            TextView versionText = new TextView(this);
            versionText.setText(" v2.0");
            versionText.setTextColor(0xFF888888);
            versionText.setTextSize(12);
            statusLayout.addView(versionText);
            
            menuLayout.addView(statusLayout);
            
            String[][] menuItems = {
                {"🎮", "Макросы", "Управление макросами"},
                {"🔄", "Авто кликер", "Настройка кликов"},
                {"👤", "Персонажи", "Управление персонажами"},
                {"⚙️", "Настройки", "Настройки приложения"},
                {"📁", "Конфиги", "Экспорт/импорт"},
                {"🎨", "Темы", "Оформление"},
                {"📊", "Статистика", "Просмотр статистики"},
                {"❌", "Выход", "Закрыть приложение"}
            };
            
            for (String[] item : menuItems) {
                LinearLayout itemLayout = new LinearLayout(this);
                itemLayout.setOrientation(LinearLayout.HORIZONTAL);
                itemLayout.setGravity(Gravity.CENTER_VERTICAL);
                itemLayout.setPadding(20, 12, 20, 12);
                
                GradientDrawable itemBg = new GradientDrawable();
                itemBg.setCornerRadius(16);
                itemBg.setColor(0x33FFFFFF);
                itemBg.setStroke(2, primaryColor);
                itemLayout.setBackground(itemBg);
                
                LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                itemParams.setMargins(0, 6, 0, 6);
                itemLayout.setLayoutParams(itemParams);
                
                TextView iconText = new TextView(this);
                iconText.setText(item[0]);
                iconText.setTextSize(24);
                iconText.setPadding(0, 0, 16, 0);
                itemLayout.addView(iconText);
                
                LinearLayout textLayout = new LinearLayout(this);
                textLayout.setOrientation(LinearLayout.VERTICAL);
                textLayout.setLayoutParams(new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                
                TextView titleText = new TextView(this);
                titleText.setText(item[1]);
                titleText.setTextColor(Color.WHITE);
                titleText.setTextSize(18);
                titleText.setTypeface(null, android.graphics.Typeface.BOLD);
                textLayout.addView(titleText);
                
                TextView descText = new TextView(this);
                descText.setText(item[2]);
                descText.setTextColor(0xFF888888);
                descText.setTextSize(12);
                textLayout.addView(descText);
                
                itemLayout.addView(textLayout);
                
                TextView arrowText = new TextView(this);
                arrowText.setText("›");
                arrowText.setTextColor(primaryColor);
                arrowText.setTextSize(28);
                arrowText.setPadding(16, 0, 0, 0);
                itemLayout.addView(arrowText);
                
                final String action = item[1];
                itemLayout.setOnClickListener(v -> {
                    hideMainMenu();
                    handleMenuItemClick(action);
                });
                
                menuLayout.addView(itemLayout);
            }
            
            mainMenuContainer.addView(menuLayout);
            
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );
            
            if (windowManager != null) {
                windowManager.addView(mainMenuContainer, params);
                isMainMenuOpen = true;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "createMainMenu error", e);
        }
    }

    private void hideMainMenu() {
        try {
            if (mainMenuContainer != null && windowManager != null) {
                windowManager.removeView(mainMenuContainer);
                mainMenuContainer = null;
                isMainMenuOpen = false;
            }
        } catch (Exception e) {
            Log.e(TAG, "hideMainMenu error", e);
        }
    }

    private void handleMenuItemClick(String item) {
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
                showSettingsDialog();
                break;
            case "Конфиги":
                showConfigDialog();
                break;
            case "Темы":
                showThemeDialog();
                break;
            case "Статистика":
                showStatisticsDialog();
                break;
            case "Выход":
                confirmExit();
                break;
            default:
                Toast.makeText(this, "Функция в разработке", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmExit() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Выход");
        builder.setMessage("Вы уверены, что хотите выйти?");
        builder.setPositiveButton("Выйти", (d, w) -> {
            cleanupResources();
            finishAffinity();
            System.exit(0);
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    // ==================== ГЛАВНАЯ КНОПКА (ОВЕРЛЕЙ) ====================

    private void createMainCircle() {
        try {
            if (isAppInForeground) {
                removeMainCircle();
                return;
            }
            
            if (windowManager == null) {
                windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
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
            iconText.setPadding(0, 0, 0, 0);
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

            setupMainCircleTouchListener();
            
            if (windowManager != null) {
                windowManager.addView(mainCircleContainer, mainCircleParams);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "createMainCircle error", e);
        }
    }

    private void setupMainCircleTouchListener() {
        if (mainCircleContainer == null) return;
        
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
                                    createMainMenu();
                                } else {
                                    showMacrosWindow();
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

    private void updateOverlayAppearance() {
        try {
            if (mainCircleContainer != null && windowManager != null) {
                View circleView = ((ViewGroup) mainCircleContainer).getChildAt(0);
                if (circleView != null) {
                    GradientDrawable bg = (GradientDrawable) circleView.getBackground();
                    if (bg != null) {
                        bg.setColor(getThemeColor());
                    }
                }
                
                mainCircleParams.width = overlaySize;
                mainCircleParams.height = overlaySize;
                mainCircleContainer.setAlpha(overlayAlpha / 255f);
                windowManager.updateViewLayout(mainCircleContainer, mainCircleParams);
            }
        } catch (Exception e) {
            Log.e(TAG, "updateOverlayAppearance error", e);
        }
    }

    // ==================== УПРАВЛЕНИЕ ОКНАМИ ====================

    private void showMacrosWindow() {
        try {
            if (windows.containsKey("macros")) {
                windows.get("macros").container.setVisibility(View.VISIBLE);
                bringWindowToFront("macros");
                return;
            }

            FloatingWindow win = createWindow("macros", "Макросы", "🎮", 500, 600);
            
            LinearLayout mainLayout = new LinearLayout(this);
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setPadding(16, 16, 16, 16);
            mainLayout.setBackgroundColor(0xDD0D0D0D);
            
            LinearLayout titleBar = createTitleBar(win);
            mainLayout.addView(titleBar);
            
            LinearLayout selectLayout = createMacroSelector(mainLayout);
            mainLayout.addView(selectLayout);
            
            final LinearLayout contentContainer = new LinearLayout(this);
            contentContainer.setOrientation(LinearLayout.VERTICAL);
            contentContainer.setPadding(0, 12, 0, 0);
            mainLayout.addView(contentContainer);
            
            LinearLayout controlLayout = createMacroControls(contentContainer);
            mainLayout.addView(controlLayout);
            
            LinearLayout infoLayout = createMacroInfo();
            mainLayout.addView(infoLayout);
            
            win.contentView = mainLayout;
            win.container.addView(mainLayout);
            
            setupWindowAppearance(win);
            
            windowManager.addView(win.container, win.params);
            windows.put("macros", win);
            windowStack.add("macros");
            
            updateMacroUI(contentContainer);
            
        } catch (Exception e) {
            Log.e(TAG, "showMacrosWindow error", e);
            Toast.makeText(this, "Ошибка открытия окна", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAutoClickerWindow() {
        try {
            if (windows.containsKey("autoclicker")) {
                windows.get("autoclicker").container.setVisibility(View.VISIBLE);
                bringWindowToFront("autoclicker");
                return;
            }

            FloatingWindow win = createWindow("autoclicker", "Авто кликер", "🔄", 480, 550);
            
            LinearLayout mainLayout = new LinearLayout(this);
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setPadding(16, 16, 16, 16);
            mainLayout.setBackgroundColor(0xDD0D0D0D);
            
            LinearLayout titleBar = createTitleBar(win);
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
            
            Button addBtn = createStyledIconButton("➕ Добавить", 0xFFFF8800);
            addBtn.setOnClickListener(v -> startAutoClickerRecording());
            controlLayout.addView(addBtn);
            
            Button startBtn = createStyledIconButton("▶ Старт", 0xFF00AA00);
            startBtn.setOnClickListener(v -> startAutoClicker());
            controlLayout.addView(startBtn);
            
            Button stopBtn = createStyledIconButton("■ Стоп", 0xFFFF0000);
            stopBtn.setOnClickListener(v -> stopAutoClicker());
            controlLayout.addView(stopBtn);
            
            Button clearBtn = createStyledIconButton("✕ Очистить", 0xFFFF8800);
            clearBtn.setOnClickListener(v -> clearAutoClickerPoints());
            controlLayout.addView(clearBtn);
            
            mainLayout.addView(controlLayout);
            
            LinearLayout settingsLayout = createAutoClickerSettings();
            mainLayout.addView(settingsLayout);
            
            win.contentView = mainLayout;
            win.container.addView(mainLayout);
            
            setupWindowAppearance(win);
            
            windowManager.addView(win.container, win.params);
            windows.put("autoclicker", win);
            windowStack.add("autoclicker");
            
            updateAutoClickerUI(pointsContainer);
            
        } catch (Exception e) {
            Log.e(TAG, "showAutoClickerWindow error", e);
            Toast.makeText(this, "Ошибка открытия окна", Toast.LENGTH_SHORT).show();
        }
    }

    private void showCharactersWindow() {
        try {
            if (windows.containsKey("characters")) {
                windows.get("characters").container.setVisibility(View.VISIBLE);
                bringWindowToFront("characters");
                return;
            }

            FloatingWindow win = createWindow("characters", "Персонажи", "👤", 400, 500);
            
            LinearLayout mainLayout = new LinearLayout(this);
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setPadding(16, 16, 16, 16);
            mainLayout.setBackgroundColor(0xDD0D0D0D);
            
            LinearLayout titleBar = createTitleBar(win);
            mainLayout.addView(titleBar);
            
            final LinearLayout listContainer = new LinearLayout(this);
            listContainer.setOrientation(LinearLayout.VERTICAL);
            listContainer.setPadding(0, 12, 0, 0);
            mainLayout.addView(listContainer);
            
            Button addBtn = createStyledButton("➕ Добавить персонажа", 0xFFFF0000);
            addBtn.setOnClickListener(v -> showAddCharacterDialog(listContainer));
            mainLayout.addView(addBtn);
            
            win.contentView = mainLayout;
            win.container.addView(mainLayout);
            
            setupWindowAppearance(win);
            
            windowManager.addView(win.container, win.params);
            windows.put("characters", win);
            windowStack.add("characters");
            
            updateCharactersUI(listContainer);
            
        } catch (Exception e) {
            Log.e(TAG, "showCharactersWindow error", e);
            Toast.makeText(this, "Ошибка открытия окна", Toast.LENGTH_SHORT).show();
        }
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⚙️ Настройки");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        addSizeControl(layout);
        addAlphaControl(layout);
        addButtonVisibilityControl(layout);
        addAutoClickerControl(layout);
        addRecordingModeControl(layout);

        builder.setView(layout);
        builder.setPositiveButton("Закрыть", null);
        builder.setNegativeButton("Сбросить", (d, w) -> resetSettings());
        builder.show();
    }

    private void addSizeControl(LinearLayout layout) {
        TextView sizeLabel = new TextView(this);
        int currentSize = prefs.getInt("overlay_size", 80);
        sizeLabel.setText("Размер оверлея: " + currentSize + "px");
        sizeLabel.setTextColor(Color.WHITE);
        layout.addView(sizeLabel);

        SeekBar sizeSeek = new SeekBar(this);
        sizeSeek.setMax(200);
        sizeSeek.setMin(40);
        sizeSeek.setProgress(currentSize);
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
        layout.addView(sizeSeek);
    }

    private void addAlphaControl(LinearLayout layout) {
        TextView alphaLabel = new TextView(this);
        int currentAlpha = prefs.getInt("overlay_alpha", 200);
        alphaLabel.setText("Прозрачность: " + (currentAlpha * 100 / 255) + "%");
        alphaLabel.setTextColor(Color.WHITE);
        layout.addView(alphaLabel);

        SeekBar alphaSeek = new SeekBar(this);
        alphaSeek.setMax(255);
        alphaSeek.setProgress(currentAlpha);
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
        layout.addView(alphaSeek);
    }

    private void addButtonVisibilityControl(LinearLayout layout) {
        Button toggleButtonsBtn = createStyledButton(
            buttonsVisible ? "🙈 Скрыть кнопки" : "👀 Показать кнопки", 
            0xFF0066FF
        );
        toggleButtonsBtn.setOnClickListener(v -> {
            toggleButtonsVisibility();
            toggleButtonsBtn.setText(buttonsVisible ? "🙈 Скрыть кнопки" : "👀 Показать кнопки");
        });
        layout.addView(toggleButtonsBtn);
    }

    private void addAutoClickerControl(LinearLayout layout) {
        Button autoClickerBtn = createStyledButton("🔄 Открыть авто кликер", 0xFFFF8800);
        autoClickerBtn.setOnClickListener(v -> showAutoClickerWindow());
        layout.addView(autoClickerBtn);
    }

    private void addRecordingModeControl(LinearLayout layout) {
        LinearLayout recordLayout = new LinearLayout(this);
        recordLayout.setOrientation(LinearLayout.HORIZONTAL);
        recordLayout.setGravity(Gravity.CENTER_VERTICAL);
        recordLayout.setPadding(0, 8, 0, 8);

        TextView recordLabel = new TextView(this);
        recordLabel.setText("Пропуск кликов при записи:");
        recordLabel.setTextColor(Color.WHITE);
        recordLabel.setTextSize(14);
        recordLayout.addView(recordLabel);

        Switch recordSwitch = new Switch(this);
        recordSwitch.setChecked(prefs.getBoolean("record_pass_through", true));
        recordSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("record_pass_through", isChecked).apply();
            recordPassThrough = isChecked;
        });
        recordLayout.addView(recordSwitch);

        layout.addView(recordLayout);
    }

    private void resetSettings() {
        prefs.edit()
            .putInt("overlay_size", 80)
            .putInt("overlay_alpha", 200)
            .putBoolean("record_pass_through", true)
            .apply();
        overlaySize = 80;
        overlayAlpha = 200;
        recordPassThrough = true;
        createMainCircle();
        Toast.makeText(this, "Настройки сброшены", Toast.LENGTH_SHORT).show();
    }

    private void showThemeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🎨 Выбор темы");

        String[] themes = {
            "Красная", "Синяя", "Зеленая", "Фиолетовая",
            "Оранжевая", "Розовая", "Белая", "Черная",
            "Радужная", "Неоновая", "Киберпанк", "Ретро"
        };
        
        int[] themeColors = {
            0xFFFF0000, 0xFF0000FF, 0xFF00FF00, 0xFFFF00FF,
            0xFFFF8800, 0xFFFF44AA, 0xFFFFFFFF, 0xFF000000,
            0xFFFF00FF, 0xFF00FFFF, 0xFFFF0088, 0xFFFFAA00
        };

        builder.setItems(themes, (d, which) -> {
            primaryColor = themeColors[which];
            rainbowMode = which == 8;
            saveSettings();
            applyTheme();
            Toast.makeText(this, "Тема применена", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void showStatisticsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("📊 Статистика");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        int totalMacros = macroConfigs.size();
        int totalActions = 0;
        int totalClicks = 0;
        int totalSwipes = 0;
        
        for (MacroConfig config : macroConfigs) {
            totalActions += config.points.size();
            for (MacroPoint point : config.points) {
                if (point.actionType.equals("click")) totalClicks++;
                else if (point.actionType.equals("swipe")) totalSwipes++;
            }
        }

        addStatItem(layout, "📦 Всего макросов", String.valueOf(totalMacros));
        addStatItem(layout, "🎯 Всего действий", String.valueOf(totalActions));
        addStatItem(layout, "👆 Кликов", String.valueOf(totalClicks));
        addStatItem(layout, "🔄 Свайпов", String.valueOf(totalSwipes));
        addStatItem(layout, "🔘 Кнопок", String.valueOf(floatingButtons.size()));
        addStatItem(layout, "👤 Персонажей", String.valueOf(characters.size()));

        builder.setView(layout);
        builder.setPositiveButton("Закрыть", null);
        builder.show();
    }

    private void addStatItem(LinearLayout layout, String label, String value) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setPadding(0, 8, 0, 8);
        
        TextView labelText = new TextView(this);
        labelText.setText(label + ":");
        labelText.setTextColor(Color.WHITE);
        labelText.setTextSize(16);
        labelText.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        item.addView(labelText);
        
        TextView valueText = new TextView(this);
        valueText.setText(value);
        valueText.setTextColor(primaryColor);
        valueText.setTextSize(16);
        valueText.setTypeface(null, android.graphics.Typeface.BOLD);
        item.addView(valueText);
        
        layout.addView(item);
    }

    private void showConfigDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("📁 Управление конфигами");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        Button exportBtn = createStyledButton("📤 Экспорт конфига", 0xFF00AA00);
        exportBtn.setOnClickListener(v -> exportConfig());
        layout.addView(exportBtn);

        Button importBtn = createStyledButton("📥 Импорт конфига", 0xFFFF8800);
        importBtn.setOnClickListener(v -> importConfig());
        layout.addView(importBtn);

        Button backupBtn = createStyledButton("💾 Создать резервную копию", 0xFF0066FF);
        backupBtn.setOnClickListener(v -> createBackup());
        layout.addView(backupBtn);

        Button restoreBtn = createStyledButton("🔄 Восстановить из резерва", 0xFFFF8800);
        restoreBtn.setOnClickListener(v -> restoreBackup());
        layout.addView(restoreBtn);

        Button deleteAllBtn = createStyledButton("🗑 Удалить все данные", 0xFFFF0000);
        deleteAllBtn.setOnClickListener(v -> {
            AlertDialog.Builder confirm = new AlertDialog.Builder(this);
            confirm.setTitle("Удалить все?");
            confirm.setMessage("Все макросы, кнопки и настройки будут удалены");
            confirm.setPositiveButton("Удалить", (d, w) -> {
                clearAllData();
                Toast.makeText(this, "Все данные удалены", Toast.LENGTH_SHORT).show();
            });
            confirm.setNegativeButton("Отмена", null);
            confirm.show();
        });
        layout.addView(deleteAllBtn);

        builder.setView(layout);
        builder.setNegativeButton("Закрыть", null);
        builder.show();
    }

    private void createBackup() {
        try {
            String backupName = "backup_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".json";
            File backupDir = new File(getExternalFilesDir(null), "backups");
            if (!backupDir.exists()) backupDir.mkdirs();
            
            File backupFile = new File(backupDir, backupName);
            
            JSONObject data = new JSONObject();
            data.put("version", "2.0");
            data.put("timestamp", System.currentTimeMillis());
            
            JSONArray macrosArray = new JSONArray();
            for (MacroConfig config : macroConfigs) {
                macrosArray.put(config.toJSON());
            }
            data.put("macros", macrosArray);
            
            JSONArray buttonsArray = new JSONArray();
            for (FloatingButton btn : floatingButtons.values()) {
                JSONObject btnObj = new JSONObject();
                btnObj.put("name", btn.macroName);
                btnObj.put("color", btn.color);
                btnObj.put("x", btn.params.x);
                btnObj.put("y", btn.params.y);
                btnObj.put("size", btn.params.width);
                btnObj.put("fixed", btn.isFixed);
                buttonsArray.put(btnObj);
            }
            data.put("buttons", buttonsArray);
            
            JSONObject settings = new JSONObject();
            settings.put("overlayAlpha", overlayAlpha);
            settings.put("overlaySize", overlaySize);
            settings.put("buttonsVisible", buttonsVisible);
            settings.put("primaryColor", primaryColor);
            settings.put("rainbowMode", rainbowMode);
            data.put("settings", settings);
            
            FileOutputStream out = new FileOutputStream(backupFile);
            out.write(data.toString(2).getBytes());
            out.close();
            
            Toast.makeText(this, "Резервная копия создана: " + backupName, Toast.LENGTH_LONG).show();
            
        } catch (Exception e) {
            Log.e(TAG, "createBackup error", e);
            Toast.makeText(this, "Ошибка создания резерва", Toast.LENGTH_SHORT).show();
        }
    }

    private void restoreBackup() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Выберите резерв"), REQUEST_EXPORT_CONFIG);
    }

    private void clearAllData() {
        macroConfigs.clear();
        floatingButtons.clear();
        buttonMacroMap.clear();
        characters.clear();
        prefs.edit().clear().apply();
        saveMacroConfigs();
        saveCharacters();
        removeAllButtons();
        Toast.makeText(this, "Все данные удалены", Toast.LENGTH_SHORT).show();
    }

    // ==================== МЕТОДЫ ДЛЯ РАБОТЫ С МАКРОСАМИ ====================

    private LinearLayout createMacroSelector(final LinearLayout parent) {
        LinearLayout selectLayout = new LinearLayout(this);
        selectLayout.setOrientation(LinearLayout.HORIZONTAL);
        selectLayout.setGravity(Gravity.CENTER);
        selectLayout.setPadding(0, 8, 0, 8);

        TextView selectLabel = new TextView(this);
        selectLabel.setText("Макрос:");
        selectLabel.setTextColor(Color.WHITE);
        selectLabel.setTextSize(14);
        selectLabel.setPadding(0, 0, 12, 0);
        selectLayout.addView(selectLabel);

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

        final TextView macroNameText = new TextView(this);
        macroNameText.setText(currentMacroName);
        macroNameText.setTextColor(getThemeColor());
        macroNameText.setTextSize(16);
        macroNameText.setTypeface(null, android.graphics.Typeface.BOLD);
        macroNameText.setPadding(12, 0, 12, 0);
        macroNameText.setOnClickListener(v -> showMacroListDialog(parent));
        selectLayout.addView(macroNameText);

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

        Button newMacroBtn = new Button(this);
        newMacroBtn.setText("+");
        newMacroBtn.setTextColor(Color.WHITE);
        newMacroBtn.setTextSize(18);
        newMacroBtn.setBackgroundColor(0xFFFF0000);
        newMacroBtn.setPadding(12, 4, 12, 4);
        newMacroBtn.setOnClickListener(v -> showNewMacroDialog(parent));
        selectLayout.addView(newMacroBtn);

        return selectLayout;
    }

    private LinearLayout createMacroControls(final LinearLayout contentContainer) {
        LinearLayout controlLayout = new LinearLayout(this);
        controlLayout.setOrientation(LinearLayout.HORIZONTAL);
        controlLayout.setGravity(Gravity.CENTER);
        controlLayout.setPadding(0, 12, 0, 0);
        controlLayout.setWeightSum(5);

        Button recordBtn = createStyledIconButton("Запись", 0xFFFF0000);
        recordBtn.setOnClickListener(v -> startRecordingMode());
        controlLayout.addView(recordBtn);

        Button startBtn = createStyledIconButton("▶ Старт", 0xFF00AA00);
        startBtn.setOnClickListener(v -> startMacroExecution());
        controlLayout.addView(startBtn);

        Button pauseBtn = createStyledIconButton("⏸ Пауза", 0xFFFF8800);
        pauseBtn.setOnClickListener(v -> toggleMacroPause());
        controlLayout.addView(pauseBtn);

        Button stopBtn = createStyledIconButton("■ Стоп", 0xFFFF0000);
        stopBtn.setOnClickListener(v -> stopMacroExecution());
        controlLayout.addView(stopBtn);

        Button clearBtn = createStyledIconButton("✕ Очистить", 0xFFFF8800);
        clearBtn.setOnClickListener(v -> clearMacroPoints());
        controlLayout.addView(clearBtn);

        return controlLayout;
    }

    private LinearLayout createMacroInfo() {
        LinearLayout infoLayout = new LinearLayout(this);
        infoLayout.setOrientation(LinearLayout.HORIZONTAL);
        infoLayout.setGravity(Gravity.CENTER);
        infoLayout.setPadding(0, 8, 0, 0);

        TextView infoText = new TextView(this);
        infoText.setText("Статус: Готов");
        infoText.setTextColor(0xFF888888);
        infoText.setTextSize(12);
        infoLayout.addView(infoText);

        return infoLayout;
    }

    private LinearLayout createAutoClickerSettings() {
        LinearLayout settingsLayout = new LinearLayout(this);
        settingsLayout.setOrientation(LinearLayout.VERTICAL);
        settingsLayout.setPadding(0, 12, 0, 0);

        LinearLayout loopLayout = new LinearLayout(this);
        loopLayout.setOrientation(LinearLayout.HORIZONTAL);
        loopLayout.setGravity(Gravity.CENTER_VERTICAL);
        loopLayout.setPadding(0, 4, 0, 4);

        TextView loopLabel = new TextView(this);
        loopLabel.setText("Циклы:");
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
            } else {
                loopInput.setText("1");
                loopInput.setEnabled(true);
            }
        });
        loopLayout.addView(infiniteSwitch);

        settingsLayout.addView(loopLayout);

        LinearLayout maxClickLayout = new LinearLayout(this);
        maxClickLayout.setOrientation(LinearLayout.HORIZONTAL);
        maxClickLayout.setGravity(Gravity.CENTER_VERTICAL);
        maxClickLayout.setPadding(0, 4, 0, 4);

        TextView maxClickLabel = new TextView(this);
        maxClickLabel.setText("Макс кликов:");
        maxClickLabel.setTextColor(Color.WHITE);
        maxClickLabel.setTextSize(14);
        maxClickLabel.setPadding(0, 0, 12, 0);
        maxClickLayout.addView(maxClickLabel);

        final EditText maxClickInput = new EditText(this);
        maxClickInput.setText("0");
        maxClickInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        maxClickInput.setTextColor(Color.WHITE);
        maxClickInput.setBackgroundColor(0x22FFFFFF);
        maxClickInput.setPadding(8, 4, 8, 4);
        maxClickInput.setWidth(80);
        maxClickLayout.addView(maxClickInput);

        settingsLayout.addView(maxClickLayout);

        Button saveSettingsBtn = createStyledButton("💾 Сохранить настройки", 0xFFFFAA00);
        saveSettingsBtn.setOnClickListener(v -> {
            Toast.makeText(this, "Настройки сохранены", Toast.LENGTH_SHORT).show();
        });
        settingsLayout.addView(saveSettingsBtn);

        return settingsLayout;
    }

    private void updateMacroUI(LinearLayout contentContainer) {
        contentContainer.removeAllViews();

        MacroConfig config = getCurrentMacro();
        if (config == null) return;

        TextView infoText = new TextView(this);
        int totalActions = config.points.size();
        infoText.setText("📊 Тип: " + (config.type.equals("recorded") ? "Запись" : "Авто кликер") + 
                        " | Действий: " + totalActions +
                        " | Циклов: " + (config.isLoop ? "∞" : config.loopCount));
        infoText.setTextColor(Color.WHITE);
        infoText.setTextSize(12);
        infoText.setPadding(0, 0, 0, 8);
        contentContainer.addView(infoText);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);

        if (config.points.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("📭 Нет точек\nНажмите 'Запись' для добавления");
            empty.setTextColor(0xFF888888);
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, 40, 0, 40);
            listLayout.addView(empty);
        } else {
            for (int i = 0; i < config.points.size(); i++) {
                MacroPoint p = config.points.get(i);
                LinearLayout item = createPointItem(p, i, config);
                listLayout.addView(item);
            }
        }

        scrollView.addView(listLayout);
        contentContainer.addView(scrollView);

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
                updateMacroUI(contentContainer);
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
            updateMacroUI(contentContainer);
        });
        loopLayout.addView(infiniteSwitch);

        contentContainer.addView(loopLayout);

        Button saveBtn = createStyledButton("💾 СОХРАНИТЬ МАКРОС", 0xFFFFAA00);
        saveBtn.setOnClickListener(v -> {
            saveMacroConfigs();
            Toast.makeText(this, "Макрос сохранён!", Toast.LENGTH_SHORT).show();
        });
        contentContainer.addView(saveBtn);

        Button createBtnBtn = createStyledButton("🔘 СОЗДАТЬ КНОПКУ", 0xFF0066FF);
        createBtnBtn.setOnClickListener(v -> showCreateButtonDialog());
        contentContainer.addView(createBtnBtn);
    }

    private LinearLayout createPointItem(MacroPoint p, int index, MacroConfig config) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(4, 4, 4, 4);
        GradientDrawable itemBg = new GradientDrawable();
        itemBg.setCornerRadius(8);
        itemBg.setColor(0x22FF0000);
        itemBg.setStroke(1, getThemeColor());
        item.setBackground(itemBg);

        String actionText = "";
        String icon = "";
        if (p.actionType.equals("swipe")) {
            icon = "🔄";
            actionText = "Свайп (" + p.x + "," + p.y + " → " + p.x2 + "," + p.y2 + ")";
        } else if (p.actionType.equals("wait")) {
            icon = "⏱";
            actionText = "Ожидание (" + p.delay + "мс)";
        } else {
            icon = "👆";
            actionText = "Клик (" + p.x + "," + p.y + ")";
        }
        
        String delayText = p.delay >= 1000 ? (p.delay/1000) + "с" : p.delay + "мс";
        String repeatText = p.repeatCount > 1 ? " x" + p.repeatCount : "";
        String randomText = p.randomOffset ? " 🎲" : "";

        TextView info = new TextView(this);
        info.setText(icon + " #" + (index+1) + " " + actionText + " " + delayText + repeatText + randomText);
        info.setTextColor(Color.WHITE);
        info.setTextSize(11);
        info.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        item.addView(info);

        Button editBtn = new Button(this);
        editBtn.setText("⚙");
        editBtn.setTextColor(0xFFFFFF00);
        editBtn.setTextSize(12);
        editBtn.setBackgroundColor(0x33000000);
        editBtn.setPadding(6, 2, 6, 2);
        editBtn.setOnClickListener(v -> showPointEditor(index, config));
        item.addView(editBtn);

        Button delBtn = new Button(this);
        delBtn.setText("✕");
        delBtn.setTextColor(0xFFFF0000);
        delBtn.setTextSize(12);
        delBtn.setBackgroundColor(0x33FF0000);
        delBtn.setPadding(6, 2, 6, 2);
        delBtn.setOnClickListener(v -> {
            config.points.remove(index);
            saveMacroConfigs();
            if (windows.containsKey("macros")) {
                LinearLayout content = null;
                LinearLayout mainLayout = (LinearLayout) windows.get("macros").contentView;
                for (int i = 0; i < mainLayout.getChildCount(); i++) {
                    View vw = mainLayout.getChildAt(i);
                    if (vw instanceof LinearLayout) {
                        LinearLayout ll = (LinearLayout) vw;
                        if (ll.getChildCount() > 0 && ll.getChildAt(0) instanceof TextView) {
                            TextView tv = (TextView) ll.getChildAt(0);
                            if (tv.getText().toString().contains("Действий:")) {
                                content = ll;
                                break;
                            }
                        }
                    }
                }
                if (content != null) {
                    updateMacroUI(content);
                }
            }
        });
        item.addView(delBtn);

        return item;
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
            empty.setPadding(0, 40, 0, 40);
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

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setPadding(0, 4, 0, 4);

        for (int i = 0; i < config.points.size(); i++) {
            MacroPoint p = config.points.get(i);
            LinearLayout item = createAutoClickerPointItem(p, i, config);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(4, 4, 4, 4);
            item.setLayoutParams(params);
            grid.addView(item);
        }

        container.addView(grid);
    }

    private LinearLayout createAutoClickerPointItem(MacroPoint p, int index, MacroConfig config) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(8, 8, 8, 8);
        
        GradientDrawable itemBg = new GradientDrawable();
        itemBg.setCornerRadius(12);
        itemBg.setColor(0x22FF0000);
        itemBg.setStroke(2, getThemeColor());
        item.setBackground(itemBg);

        String icon = p.actionType.equals("swipe") ? "🔄" : "👆";
        TextView numberText = new TextView(this);
        numberText.setText(icon + " " + (index + 1));
        numberText.setTextColor(Color.WHITE);
        numberText.setTextSize(16);
        numberText.setTypeface(null, android.graphics.Typeface.BOLD);
        numberText.setGravity(Gravity.CENTER);
        item.addView(numberText);

        TextView coordText = new TextView(this);
        coordText.setText(p.x + "," + p.y);
        coordText.setTextColor(0xFF888888);
        coordText.setTextSize(11);
        coordText.setGravity(Gravity.CENTER);
        item.addView(coordText);

        TextView delayText = new TextView(this);
        delayText.setText((p.delay/1000) + "с");
        delayText.setTextColor(0xFF888888);
        delayText.setTextSize(10);
        delayText.setGravity(Gravity.CENTER);
        item.addView(delayText);

        LinearLayout btnLayout = new LinearLayout(this);
        btnLayout.setOrientation(LinearLayout.HORIZONTAL);
        btnLayout.setGravity(Gravity.CENTER);
        btnLayout.setPadding(0, 4, 0, 0);

        Button editBtn = new Button(this);
        editBtn.setText("⚙");
        editBtn.setTextColor(0xFFFFFF00);
        editBtn.setTextSize(12);
        editBtn.setBackgroundColor(0x33000000);
        editBtn.setPadding(4, 2, 4, 2);
        editBtn.setOnClickListener(v -> showPointEditor(index, config));
        btnLayout.addView(editBtn);

        Button delBtn = new Button(this);
        delBtn.setText("✕");
        delBtn.setTextColor(0xFFFF0000);
        delBtn.setTextSize(12);
        delBtn.setBackgroundColor(0x33FF0000);
        delBtn.setPadding(4, 2, 4, 2);
        delBtn.setOnClickListener(v -> {
            config.points.remove(index);
            saveMacroConfigs();
            if (windows.containsKey("autoclicker")) {
                LinearLayout container = null;
                LinearLayout mainLayout = (LinearLayout) windows.get("autoclicker").contentView;
                for (int i = 0; i < mainLayout.getChildCount(); i++) {
                    View vw = mainLayout.getChildAt(i);
                    if (vw instanceof LinearLayout) {
                        LinearLayout ll = (LinearLayout) vw;
                        if (ll.getChildCount() > 0 && ll.getChildAt(0) instanceof TextView) {
                            TextView tv = (TextView) ll.getChildAt(0);
                            if (tv.getText().toString().contains("Тип:")) {
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
        });
        btnLayout.addView(delBtn);

        item.addView(btnLayout);
        return item;
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
                    LinearLayout content = null;
                    LinearLayout mainLayout = (LinearLayout) windows.get("macros").contentView;
                    for (int i = 0; i < mainLayout.getChildCount(); i++) {
                        View vw = mainLayout.getChildAt(i);
                        if (vw instanceof LinearLayout) {
                            LinearLayout ll = (LinearLayout) vw;
                            if (ll.getChildCount() > 0 && ll.getChildAt(0) instanceof TextView) {
                                TextView tv = (TextView) ll.getChildAt(0);
                                if (tv.getText().toString().contains("Действий:")) {
                                    content = ll;
                                    break;
                                }
                            }
                        }
                    }
                    if (content != null) {
                        updateMacroUI(content);
                    }
                }
                
                if (windows.containsKey("autoclicker")) {
                    LinearLayout container = null;
                    LinearLayout mainLayout = (LinearLayout) windows.get("autoclicker").contentView;
                    for (int i = 0; i < mainLayout.getChildCount(); i++) {
                        View vw = mainLayout.getChildAt(i);
                        if (vw instanceof LinearLayout) {
                            LinearLayout ll = (LinearLayout) vw;
                            if (ll.getChildCount() > 0 && ll.getChildAt(0) instanceof TextView) {
                                TextView tv = (TextView) ll.getChildAt(0);
                                if (tv.getText().toString().contains("Тип:")) {
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

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Выбор макроса");

        String[] names = new String[macroConfigs.size()];
        for (int i = 0; i < macroConfigs.size(); i++) {
            MacroConfig config = macroConfigs.get(i);
            names[i] = config.name + " (" + config.points.size() + " точек)";
        }

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
        stopRecordBtn.setOnClickListener(v -> {
            stopRecordingMode();
        });
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
        overlayParams.gravity = Gravity.TOP | Gravity.START;
        overlayParams.x = 0;
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

        removeRecordingOverlay();

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
            LinearLayout content = null;
            LinearLayout mainLayout = (LinearLayout) windows.get("macros").contentView;
            for (int i = 0; i < mainLayout.getChildCount(); i++) {
                View v = mainLayout.getChildAt(i);
                if (v instanceof LinearLayout) {
                    LinearLayout ll = (LinearLayout) v;
                    if (ll.getChildCount() > 0 && ll.getChildAt(0) instanceof TextView) {
                        TextView tv = (TextView) ll.getChildAt(0);
                        if (tv.getText().toString().contains("Действий:")) {
                            content = ll;
                            break;
                        }
                    }
                }
            }
            if (content != null) {
                updateMacroUI(content);
            }
        }
    }

    private void removeRecordingOverlay() {
        try {
            if (recordingOverlay != null && windowManager != null) {
                windowManager.removeView(recordingOverlay);
                recordingOverlay = null;
            }
            if (recordingTouchOverlay != null && windowManager != null) {
                windowManager.removeView(recordingTouchOverlay);
                recordingTouchOverlay = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "removeRecordingOverlay error", e);
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
            macroHandler.postDelayed(() -> executeNextPoint(), 100);
            return;
        }

        if (currentPointIndex >= runningConfig.points.size()) {
            if (runningConfig.isLoop) {
                currentPointIndex = 0;
                currentRepeatCount++;
                macroHandler.postDelayed(() -> executeNextPoint(), 500);
            } else if (currentRepeatCount < runningConfig.loopCount - 1) {
                currentRepeatCount++;
                currentPointIndex = 0;
                macroHandler.postDelayed(() -> executeNextPoint(), 500);
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
        macroHandler.postDelayed(() -> executeNextPoint(), point.delay);
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
        macroHandler.removeCallbacksAndMessages(null);
        
        if (runningConfig != null) {
            macroExecutionTime = System.currentTimeMillis() - macroStartTime;
            Toast.makeText(this, "■ Макрос остановлен\nВремя: " + (macroExecutionTime/1000) + "с", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "■ Макрос остановлен", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopAllMacros() {
        isMacroRunning = false;
        isMacroPaused = false;
        stopRequested = true;
        runningConfig = null;
        macroHandler.removeCallbacksAndMessages(null);
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
                        if (tv.getText().toString().contains("Тип:")) {
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
                                    if (tv.getText().toString().contains("Тип:")) {
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

        isAutoClickerRunningGlobal = true;
        isAutoClickerPaused = false;
        stopRequested = false;
        runningConfig = config;
        currentPointIndex = 0;
        currentRepeatCount = 0;
        
        autoClickerConfig.isRunning = true;
        autoClickerConfig.isPaused = false;
        autoClickerConfig.currentPointIndex = 0;
        autoClickerConfig.currentRepeatCount = 0;
        autoClickerConfig.clicksDone = 0;
        autoClickerConfig.startTime = System.currentTimeMillis();
        
        Toast.makeText(this, "🔄 Авто кликер запущен", Toast.LENGTH_SHORT).show();
        executeAutoClickerPoint();
    }

    private void executeAutoClickerPoint() {
        if (stopRequested || !isAutoClickerRunningGlobal || runningConfig == null) {
            stopAutoClicker();
            return;
        }

        if (isAutoClickerPaused) {
            macroHandler.postDelayed(() -> executeAutoClickerPoint(), 100);
            return;
        }

        if (autoClickerConfig.stopAfterMaxClicks && 
            autoClickerConfig.clicksDone >= autoClickerConfig.maxClicks) {
            stopAutoClicker();
            Toast.makeText(this, "Достигнуто максимальное количество кликов", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentPointIndex >= runningConfig.points.size()) {
            if (runningConfig.isLoop) {
                currentPointIndex = 0;
                currentRepeatCount++;
                macroHandler.postDelayed(() -> executeAutoClickerPoint(), 500);
            } else if (currentRepeatCount < runningConfig.loopCount - 1) {
                currentRepeatCount++;
                currentPointIndex = 0;
                macroHandler.postDelayed(() -> executeAutoClickerPoint(), 500);
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
            autoClickerConfig.clicksDone++;
            try { Thread.sleep(50); } catch (InterruptedException e) {}
        }

        currentPointIndex++;
        macroHandler.postDelayed(() -> executeAutoClickerPoint(), point.delay);
    }

    private void stopAutoClicker() {
        isAutoClickerRunningGlobal = false;
        isAutoClickerPaused = false;
        stopRequested = true;
        runningConfig = null;
        autoClickerConfig.isRunning = false;
        macroHandler.removeCallbacksAndMessages(null);
        
        long elapsed = System.currentTimeMillis() - autoClickerConfig.startTime;
        Toast.makeText(this, "■ Авто кликер остановлен\nКликов: " + autoClickerConfig.clicksDone + 
                       " Время: " + (elapsed/1000) + "с", Toast.LENGTH_LONG).show();
    }

    private void clearAutoClickerPoints() {
        MacroConfig config = getCurrentMacro();
        if (config != null && !config.points.isEmpty()) {
            config.points.clear();
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
                            if (tv.getText().toString().contains("Тип:")) {
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
            Toast.makeText(this, "Все точки удалены", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Нет точек для удаления", Toast.LENGTH_SHORT).show();
        }
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
            circleBg.setStroke(btn.borderWidth, btn.borderColor);
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

            setupButtonTouchListener(btn);
            setupButtonLongPress(btn);

            windowManager.addView(btn.container, btn.params);
            floatingButtons.put(name, btn);
            buttonMacroMap.put(name, config);
            buttonList.add(btn);

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

    private void setupButtonTouchListener(final FloatingButton btn) {
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
    }

    private void setupButtonLongPress(final FloatingButton btn) {
        btn.container.setOnLongClickListener(v -> {
            showButtonMenu(btn);
            return true;
        });
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
                buttonList.remove(btn);
            }
        } catch (Exception e) {
            Log.e(TAG, "removeFloatingButton error", e);
        }
    }

    private void removeAllButtons() {
        for (String name : floatingButtons.keySet()) {
            removeFloatingButton(name);
        }
        floatingButtons.clear();
        buttonMacroMap.clear();
        buttonList.clear();
    }

    private void showFloatingButtons() {
        for (FloatingButton btn : floatingButtons.values()) {
            if (btn.isVisible && btn.container != null) {
                btn.container.setVisibility(View.VISIBLE);
            }
        }
        buttonsVisible = true;
        saveSettings();
    }

    private void hideFloatingButtons() {
        for (FloatingButton btn : floatingButtons.values()) {
            if (btn.container != null) {
                btn.container.setVisibility(View.GONE);
            }
        }
        buttonsVisible = false;
        saveSettings();
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

        LinearLayout alphaLayout = new LinearLayout(this);
        alphaLayout.setOrientation(LinearLayout.HORIZONTAL);
        alphaLayout.setGravity(Gravity.CENTER_VERTICAL);
        alphaLayout.setPadding(0, 8, 0, 0);

        TextView alphaLabel = new TextView(this);
        alphaLabel.setText("Прозрачность:");
        alphaLabel.setTextColor(Color.WHITE);
        alphaLabel.setPadding(0, 0, 12, 0);
        alphaLayout.addView(alphaLabel);

        final SeekBar alphaSeek = new SeekBar(this);
        alphaSeek.setMax(100);
        alphaSeek.setProgress(100);
        alphaLayout.addView(alphaSeek);

        final TextView alphaValue = new TextView(this);
        alphaValue.setText("100%");
        alphaValue.setTextColor(Color.WHITE);
        alphaValue.setPadding(12, 0, 0, 0);
        alphaLayout.addView(alphaValue);

        alphaSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                alphaValue.setText(progress + "%");
                btn.container.setAlpha(progress / 100f);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        layout.addView(alphaLayout);

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

    private void loadButtonConfigs() {
        // Кнопки восстанавливаются через restoreButtons
    }

    private void saveButtonConfigs() {
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

    // ==================== КОНФИГИ ====================

    private void importConfig() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Выберите JSON"), REQUEST_IMPORT_CONFIG);
    }

    private void exportConfig() {
        try {
            JSONObject exportData = new JSONObject();
            JSONArray configsArray = new JSONArray();
            for (MacroConfig config : macroConfigs) {
                configsArray.put(config.toJSON());
            }
            exportData.put("macros", configsArray);
            exportData.put("version", "2.0");
            exportData.put("exportTime", System.currentTimeMillis());
            exportData.put("appName", "CR Arcade");
            
            JSONObject settings = new JSONObject();
            settings.put("primaryColor", primaryColor);
            settings.put("rainbowMode", rainbowMode);
            settings.put("overlaySize", overlaySize);
            settings.put("overlayAlpha", overlayAlpha);
            exportData.put("settings", settings);

            String json = exportData.toString(2);

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

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setPadding(0, 4, 0, 4);

        for (int i = 0; i < characters.size(); i++) {
            CharacterData data = characters.get(i);
            LinearLayout item = createCharacterCard(data, i);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(4, 4, 4, 4);
            item.setLayoutParams(params);
            grid.addView(item);
        }

        container.addView(grid);
    }

    private LinearLayout createCharacterCard(CharacterData data, int index) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(12, 12, 12, 12);
        
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(16);
        cardBg.setColor(0x1A0000);
        cardBg.setStroke(2, getThemeColor());
        card.setBackground(cardBg);

        FrameLayout previewContainer = new FrameLayout(this);
        previewContainer.setLayoutParams(new LinearLayout.LayoutParams(80, 80));
        
        GradientDrawable previewBg = new GradientDrawable();
        previewBg.setShape(GradientDrawable.OVAL);
        previewBg.setColor(0x1A1A1A);
        previewBg.setStroke(2, getThemeColor());
        previewContainer.setBackground(previewBg);

        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), 
                    Uri.fromFile(new File(data.path)));
            Bitmap processed = removeGreenScreen(bitmap, 40);
            ImageView thumbView = new ImageView(this);
            thumbView.setImageBitmap(processed);
            thumbView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbView.setPadding(2, 2, 2, 2);
            previewContainer.addView(thumbView);
        } catch (IOException e) {
            e.printStackTrace();
        }

        card.addView(previewContainer);

        TextView nameText = new TextView(this);
        String displayName = data.name.trim().isEmpty() ? "Без имени" : data.name;
        if (displayName.length() > 12) displayName = displayName.substring(0, 10) + "...";
        nameText.setText(displayName);
        nameText.setTextColor(Color.WHITE);
        nameText.setTextSize(11);
        nameText.setTypeface(null, android.graphics.Typeface.BOLD);
        nameText.setPadding(0, 6, 0, 0);
        nameText.setGravity(Gravity.CENTER);
        card.addView(nameText);

        LinearLayout actionLayout = new LinearLayout(this);
        actionLayout.setOrientation(LinearLayout.HORIZONTAL);
        actionLayout.setGravity(Gravity.CENTER);
        actionLayout.setPadding(0, 4, 0, 0);

        Button loadBtn = createSmallActionButton("⭕", "#2196F3");
        loadBtn.setOnClickListener(v -> {
            loadCharacterToFloat(data);
            Toast.makeText(this, "Персонаж загружен", Toast.LENGTH_SHORT).show();
        });

        Button deleteBtn = createSmallActionButton("🗑", "#CC0000");
        deleteBtn.setOnClickListener(v -> {
            characters.remove(index);
            saveCharacters();
            if (windows.containsKey("characters")) {
                LinearLayout container = null;
                LinearLayout mainLayout = (LinearLayout) windows.get("characters").contentView;
                for (int i = 0; i < mainLayout.getChildCount(); i++) {
                    View vw = mainLayout.getChildAt(i);
                    if (vw instanceof LinearLayout) {
                        LinearLayout ll = (LinearLayout) vw;
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
        });

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(36, 36);
        btnParams.setMargins(2, 0, 2, 0);
        actionLayout.addView(loadBtn, btnParams);
        actionLayout.addView(deleteBtn, btnParams);

        card.addView(actionLayout);
        return card;
    }

    private Button createSmallActionButton(String text, String color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(14);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.parseColor(color));
        bg.setStroke(2, Color.parseColor("#CC0000"));
        btn.setBackground(bg);
        btn.setPadding(0, 0, 0, 0);
        return btn;
    }

    private void showAddCharacterDialog(final LinearLayout container) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Новый персонаж");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        final EditText nameInput = new EditText(this);
        nameInput.setHint("Имя персонажа");
        nameInput.setText("Персонаж " + (characters.size() + 1));
        layout.addView(nameInput);

        builder.setView(layout);
        builder.setPositiveButton("Выбрать фото", (d, w) -> {
            tempCharacterName = nameInput.getText().toString().trim();
            if (tempCharacterName.isEmpty()) tempCharacterName = "Персонаж " + (characters.size() + 1);
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, REQUEST_GALLERY);
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void loadCharacterToFloat(CharacterData data) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), 
                    Uri.fromFile(new File(data.path)));
            Bitmap processed = removeGreenScreen(bitmap, 40);
            
            removeMainCircle();
            
            mainCircleContainer = new FrameLayout(this);
            mainCircleContainer.setBackgroundColor(Color.TRANSPARENT);
            
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(Color.TRANSPARENT);
            d.setStroke(3, Color.WHITE);
            mainCircleContainer.setBackground(d);
            mainCircleContainer.setAlpha(overlayAlpha / 255f);
            
            ImageButton imageButton = new ImageButton(this);
            imageButton.setImageBitmap(processed);
            imageButton.setBackgroundColor(Color.TRANSPARENT);
            imageButton.setPadding(5, 5, 5, 5);
            imageButton.setScaleType(ImageButton.ScaleType.FIT_CENTER);
            imageButton.setClickable(false);
            imageButton.setFocusable(false);
            
            mainCircleContainer.addView(imageButton, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            
            mainCircleParams = new WindowManager.LayoutParams(
                    overlaySize, overlaySize,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT);
            mainCircleParams.gravity = Gravity.TOP | Gravity.START;
            
            int savedX = prefs.getInt("overlay_x", 100);
            int savedY = prefs.getInt("overlay_y", 200);
            mainCircleParams.x = savedX;
            mainCircleParams.y = savedY;
            
            setupMainCircleTouchListener();
            
            if (windowManager != null) {
                windowManager.addView(mainCircleContainer, mainCircleParams);
                Toast.makeText(this, "👤 Персонаж загружен в оверлей", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "loadCharacterToFloat error", e);
            Toast.makeText(this, "Ошибка загрузки", Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap removeGreenScreen(Bitmap source, int tolerance) {
        try {
            if (source == null) return null;
            
            int width = source.getWidth();
            int height = source.getHeight();
            Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            
            int[] pixels = new int[width * height];
            source.getPixels(pixels, 0, width, 0, 0, width, height);
            
            for (int i = 0; i < pixels.length; i++) {
                int pixel = pixels[i];
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);
                
                if (g > r + tolerance && g > b + tolerance) {
                    pixels[i] = Color.TRANSPARENT;
                }
            }
            
            result.setPixels(pixels, 0, width, 0, 0, width, height);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "removeGreenScreen error", e);
            return null;
        }
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

    // ==================== УПРАВЛЕНИЕ ОКНАМИ ====================

    private FloatingWindow createWindow(String type, String title, String icon, int width, int height) {
        FloatingWindow win = new FloatingWindow();
        win.type = type;
        win.title = title;
        win.icon = icon;
        win.minWidth = 350;
        win.minHeight = 300;
        win.isDraggable = true;
        win.isResizable = true;
        win.isClosable = true;
        win.isMinimizable = true;
        win.showTitleBar = true;
        win.showCloseButton = true;
        win.showMinimizeButton = true;
        win.showResizeHandle = true;
        win.cornerRadius = 16;
        win.borderColor = getThemeColor();
        win.borderWidth = 3;
        win.backgroundColor = 0xDD0D0D0D;

        win.container = new FrameLayout(this);
        win.container.setBackgroundColor(Color.TRANSPARENT);

        win.params = new WindowManager.LayoutParams(
                width, height,
                getOverlayFlag(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );
        win.params.gravity = Gravity.CENTER;
        win.params.x = 0;
        win.params.y = 0;

        return win;
    }

    private void setupWindowAppearance(FloatingWindow win) {
        GradientDrawable border = new GradientDrawable();
        border.setCornerRadius(win.cornerRadius);
        border.setColor(win.backgroundColor);
        border.setStroke(win.borderWidth, win.borderColor);
        win.container.setBackground(border);
        win.container.setAlpha(win.alpha);
    }

    private void bringWindowToFront(String type) {
        if (windows.containsKey(type)) {
            FloatingWindow win = windows.get(type);
            if (windowManager != null && win.container != null) {
                windowManager.updateViewLayout(win.container, win.params);
            }
            windowStack.remove(type);
            windowStack.add(type);
        }
    }

    private void minimizeWindow(String type) {
        if (windows.containsKey(type)) {
            FloatingWindow win = windows.get(type);
            if (win.isMinimized) {
                win.isMinimized = false;
                if (win.savedWidth > 0 && win.savedHeight > 0) {
                    win.params.width = win.savedWidth;
                    win.params.height = win.savedHeight;
                }
                if (windowManager != null) {
                    windowManager.updateViewLayout(win.container, win.params);
                }
                if (win.titleBar != null) {
                    win.titleBar.setVisibility(View.VISIBLE);
                }
                if (win.resizeHandle != null) {
                    win.resizeHandle.setVisibility(View.VISIBLE);
                }
            } else {
                win.isMinimized = true;
                win.savedWidth = win.params.width;
                win.savedHeight = win.params.height;
                win.params.width = 200;
                win.params.height = 60;
                if (windowManager != null) {
                    windowManager.updateViewLayout(win.container, win.params);
                }
                if (win.titleBar != null) {
                    win.titleBar.setVisibility(View.GONE);
                }
                if (win.resizeHandle != null) {
                    win.resizeHandle.setVisibility(View.GONE);
                }
            }
        }
    }

    private void hideWindow(String type) {
        if (windows.containsKey(type)) {
            FloatingWindow win = windows.get(type);
            win.container.setVisibility(View.GONE);
        }
    }

    private void showWindow(String type) {
        if (windows.containsKey(type)) {
            FloatingWindow win = windows.get(type);
            win.container.setVisibility(View.VISIBLE);
            bringWindowToFront(type);
        }
    }

    private void restoreWindows() {
        try {
            String saved = prefs.getString("windows_state", "");
            if (!saved.isEmpty()) {
                JSONArray array = new JSONArray(saved);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    String type = obj.getString("type");
                    boolean isVisible = obj.getBoolean("visible");
                    if (isVisible) {
                        switch (type) {
                            case "macros":
                                showMacrosWindow();
                                break;
                            case "autoclicker":
                                showAutoClickerWindow();
                                break;
                            case "characters":
                                showCharactersWindow();
                                break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "restoreWindows error", e);
        }
    }

    private void saveWindowsState() {
        try {
            JSONArray array = new JSONArray();
            for (String type : windows.keySet()) {
                FloatingWindow win = windows.get(type);
                JSONObject obj = new JSONObject();
                obj.put("type", type);
                obj.put("visible", win.container.getVisibility() == View.VISIBLE);
                array.put(obj);
            }
            prefs.edit().putString("windows_state", array.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "saveWindowsState error", e);
        }
    }

    // ==================== МЕТОДЫ ЗАГРУЗКИ/СОХРАНЕНИЯ ====================

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
        currentTheme = prefs.getString("theme", "dark_red");
        rainbowMode = prefs.getBoolean("rainbow_mode", false);
        primaryColor = prefs.getInt("primary_color", 0xFFFF0000);
        secondaryColor = prefs.getInt("secondary_color", 0xFFCC0000);
        accentColor = prefs.getInt("accent_color", 0xFFFF4444);
        overlayAlpha = prefs.getInt("overlay_alpha", 200);
        overlaySize = prefs.getInt("overlay_size", 80);
        buttonsVisible = prefs.getBoolean("buttons_visible", true);
        recordPassThrough = prefs.getBoolean("record_pass_through", true);
        isDarkMode = prefs.getBoolean("dark_mode", true);
    }

    private void saveSettings() {
        prefs.edit()
            .putString("theme", currentTheme)
            .putBoolean("rainbow_mode", rainbowMode)
            .putInt("primary_color", primaryColor)
            .putInt("secondary_color", secondaryColor)
            .putInt("accent_color", accentColor)
            .putInt("overlay_alpha", overlayAlpha)
            .putInt("overlay_size", overlaySize)
            .putBoolean("buttons_visible", buttonsVisible)
            .putBoolean("record_pass_through", recordPassThrough)
            .putBoolean("dark_mode", isDarkMode)
            .apply();
    }

    private void applyTheme() {
        updateOverlayAppearance();
        if (mainMenuContainer != null) {
            hideMainMenu();
            createMainMenu();
        }
        
        for (FloatingWindow win : windows.values()) {
            if (win.container != null) {
                GradientDrawable border = new GradientDrawable();
                border.setCornerRadius(win.cornerRadius);
                border.setColor(win.backgroundColor);
                border.setStroke(win.borderWidth, getThemeColor());
                win.container.setBackground(border);
                
                if (win.titleBar != null) {
                    for (int i = 0; i < win.titleBar.getChildCount(); i++) {
                        View v = win.titleBar.getChildAt(i);
                        if (v instanceof TextView) {
                            ((TextView) v).setTextColor(getThemeColor());
                        }
                    }
                }
            }
        }
        
        for (FloatingButton btn : floatingButtons.values()) {
            updateButtonAppearance(btn);
        }
    }

    private int getThemeColor() {
        if (rainbowMode) {
            rainbowHue += 0.01f;
            if (rainbowHue > 1f) rainbowHue = 0f;
            return Color.HSVToColor(new float[]{rainbowHue * 360f, 0.9f, 1f});
        }
        return primaryColor;
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

    private void clearMacroPoints() {
        MacroConfig config = getCurrentMacro();
        if (config != null && !config.points.isEmpty()) {
            config.points.clear();
            saveMacroConfigs();
            if (windows.containsKey("macros")) {
                LinearLayout content = null;
                LinearLayout mainLayout = (LinearLayout) windows.get("macros").contentView;
                for (int i = 0; i < mainLayout.getChildCount(); i++) {
                    View v = mainLayout.getChildAt(i);
                    if (v instanceof LinearLayout) {
                        LinearLayout ll = (LinearLayout) v;
                        if (ll.getChildCount() > 0 && ll.getChildAt(0) instanceof TextView) {
                            TextView tv = (TextView) ll.getChildAt(0);
                            if (tv.getText().toString().contains("Действий:")) {
                                content = ll;
                                break;
                            }
                        }
                    }
                }
                if (content != null) {
                    updateMacroUI(content);
                }
            }
            Toast.makeText(this, "Все точки удалены", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Нет точек для удаления", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        try {
            String service = getPackageName() + "/" + MacroService.class.getCanonicalName();
            String enabledServices = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return enabledServices != null && enabledServices.contains(service);
        } catch (Exception e) {
            Log.e(TAG, "isAccessibilityServiceEnabled error", e);
            return false;
        }
    }

    private int getOverlayFlag() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;
    }

    // ==================== UI КОМПОНЕНТЫ ====================

    private LinearLayout createTitleBar(final FloatingWindow win) {
        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(12, 8, 12, 8);
        titleBar.setBackgroundColor(0x44000000);
        titleBar.setClickable(true);

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
            closeBtn.setOnClickListener(v -> {
                removeWindow(win.type);
            });
            titleBar.addView(closeBtn);
        }

        win.titleBar = titleBar;
        return titleBar;
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

    private void removeWindow(String type) {
        try {
            if (windows.containsKey(type)) {
                FloatingWindow win = windows.get(type);
                if (win.container != null && windowManager != null) {
                    windowManager.removeView(win.container);
                }
                windows.remove(type);
                windowStack.remove(type);
                saveWindowsState();
            }
        } catch (Exception e) {
            Log.e(TAG, "removeWindow error", e);
        }
    }

    private void hideAllWindows() {
        for (FloatingWindow win : windows.values()) {
            if (win.container != null && windowManager != null) {
                try { windowManager.removeView(win.container); } catch (Exception e) {}
            }
        }
        windows.clear();
        windowStack.clear();
    }

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

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 6, 0, 6);
        btn.setLayoutParams(lp);

        return btn;
    }

    private Button createStyledIconButton(String text, int color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(12);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(12);
        bg.setColor(color);
        bg.setAlpha(180);
        btn.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(3, 0, 3, 0);
        btn.setLayoutParams(lp);

        return btn;
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

    // ==================== ОБРАБОТЧИКИ РЕЗУЛЬТАТОВ ====================

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            switch (code) {
                case REQUEST_MICROPHONE: 
                    Toast.makeText(this, "Микрофон разрешён", Toast.LENGTH_SHORT).show(); 
                    break;
                case REQUEST_CAMERA: 
                    Toast.makeText(this, "Камера разрешена", Toast.LENGTH_SHORT).show(); 
                    break;
                case REQUEST_STORAGE: 
                    Toast.makeText(this, "Хранилище разрешено", Toast.LENGTH_SHORT).show(); 
                    break;
                case REQUEST_NOTIFICATION: 
                    Toast.makeText(this, "Уведомления разрешены", Toast.LENGTH_SHORT).show(); 
                    break;
            }
        } else {
            Toast.makeText(this, "Некоторые разрешения не получены", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        try {
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
                        JSONObject root = new JSONObject(json);
                        JSONArray array = root.getJSONArray("macros");
                        
                        int importedCount = 0;
                        for (int i = 0; i < array.length(); i++) {
                            MacroConfig config = new MacroConfig(array.getJSONObject(i));
                            boolean exists = false;
                            for (MacroConfig c : macroConfigs) {
                                if (c.name.equals(config.name)) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists) {
                                macroConfigs.add(config);
                                importedCount++;
                            }
                        }
                        
                        if (root.has("settings")) {
                            JSONObject settings = root.getJSONObject("settings");
                            primaryColor = settings.optInt("primaryColor", primaryColor);
                            rainbowMode = settings.optBoolean("rainbowMode", rainbowMode);
                            overlaySize = settings.optInt("overlaySize", overlaySize);
                            overlayAlpha = settings.optInt("overlayAlpha", overlayAlpha);
                            saveSettings();
                            applyTheme();
                        }
                        
                        saveMacroConfigs();
                        Toast.makeText(this, "✅ Импортировано " + importedCount + " макросов", Toast.LENGTH_SHORT).show();
                        
                        for (MacroConfig config : macroConfigs) {
                            if (config.buttonName != null && !config.buttonName.isEmpty()) {
                                createFloatingButton(config, config.buttonName, config.buttonColor);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Import error", e);
                        Toast.makeText(this, "❌ Ошибка импорта", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            if (requestCode == REQUEST_EXPORT_CONFIG && resultCode == RESULT_OK && data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    try {
                        FileInputStream fis = (FileInputStream) getContentResolver().openInputStream(uri);
                        byte[] buffer = new byte[fis.available()];
                        fis.read(buffer);
                        fis.close();
                        String json = new String(buffer);
                        JSONObject root = new JSONObject(json);
                        JSONArray array = root.getJSONArray("macros");
                        
                        macroConfigs.clear();
                        for (int i = 0; i < array.length(); i++) {
                            macroConfigs.add(new MacroConfig(array.getJSONObject(i)));
                        }
                        
                        if (root.has("settings")) {
                            JSONObject settings = root.getJSONObject("settings");
                            primaryColor = settings.optInt("primaryColor", primaryColor);
                            rainbowMode = settings.optBoolean("rainbowMode", rainbowMode);
                            overlaySize = settings.optInt("overlaySize", overlaySize);
                            overlayAlpha = settings.optInt("overlayAlpha", overlayAlpha);
                            saveSettings();
                            applyTheme();
                        }
                        
                        saveMacroConfigs();
                        Toast.makeText(this, "✅ Восстановлено " + array.length() + " макросов", Toast.LENGTH_SHORT).show();
                        
                        removeAllButtons();
                        for (MacroConfig config : macroConfigs) {
                            if (config.buttonName != null && !config.buttonName.isEmpty()) {
                                createFloatingButton(config, config.buttonName, config.buttonColor);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Restore error", e);
                        Toast.makeText(this, "❌ Ошибка восстановления", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "onActivityResult error", e);
            Toast.makeText(this, "Ошибка", Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    private void updateUI() {
        updateOverlayAppearance();
        
        for (FloatingWindow win : windows.values()) {
            if (win.container != null && win.contentView != null) {
                if (win.type.equals("macros")) {
                    LinearLayout content = null;
                    LinearLayout mainLayout = (LinearLayout) win.contentView;
                    for (int i = 0; i < mainLayout.getChildCount(); i++) {
                        View v = mainLayout.getChildAt(i);
                        if (v instanceof LinearLayout) {
                            LinearLayout ll = (LinearLayout) v;
                            if (ll.getChildCount() > 0 && ll.getChildAt(0) instanceof TextView) {
                                TextView tv = (TextView) ll.getChildAt(0);
                                if (tv.getText().toString().contains("Действий:")) {
                                    content = ll;
                                    break;
                                }
                            }
                        }
                    }
                    if (content != null) {
                        updateMacroUI(content);
                    }
                }
            }
        }
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
            
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.RECORD_AUDIO);
            }
            
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.CAMERA);
            }
            
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
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("⚙️ Специальные возможности");
            builder.setMessage("Для работы макросов и авто кликера необходимо включить специальные возможности.\n\n" +
                              "Это позволит приложению выполнять клики и свайпы в других приложениях.");
            builder.setPositiveButton("Включить", (d, w) -> {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivityForResult(intent, REQUEST_ACCESSIBILITY);
            });
            builder.setNegativeButton("Позже", null);
            builder.setCancelable(true);
            builder.show();
        } catch (Exception e) {
            Log.e(TAG, "showAccessibilityDialog error", e);
        }
    }

    // ==================== ДОПОЛНИТЕЛЬНЫЕ МЕТОДЫ ====================

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showToastLong(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void log(String message) {
        Log.d(TAG, message);
    }

    private void logError(String message, Exception e) {
        Log.e(TAG, message, e);
    }

    private void runOnBackground(Runnable runnable) {
        executor.execute(runnable);
    }

    private void delayExecution(Runnable runnable, long delay) {
        mainHandler.postDelayed(runnable, delay);
    }

    private void cancelDelayed() {
        mainHandler.removeCallbacksAndMessages(null);
    }
      }
