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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
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
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends BridgeActivity {

    private static final int REQUEST_MICROPHONE = 100;
    private static final int REQUEST_CAMERA = 102;
    private static final int REQUEST_OVERLAY_PERMISSION = 101;
    private static final int REQUEST_GALLERY = 103;
    private static final int REQUEST_STORAGE = 104;
    private static final int REQUEST_NOTIFICATION = 106;
    private static final int REQUEST_ACCESSIBILITY = 107;

    private static final String URL_HOME = "https://wyikhedfghhopyewfvjkurrhncswehipkhf.vercel.app/";
    private static final String URL_SETTINGS = "https://whuokhgrdcbnmkloplureecvjiqoendu.vercel.app/";

    private WindowManager windowManager;
    private FrameLayout mainCircleContainer;
    private WindowManager.LayoutParams mainCircleParams;
    
    private float startX, startY;
    private int initialX, initialY;
    private boolean isDragging = false;
    
    private FrameLayout mainOverlay;
    private WebView webView;
    private WindowManager.LayoutParams mainOverlayParams;
    private boolean isMainOverlayVisible = false;
    private boolean isAppInForeground = true;

    // Компоненты персонажей (старые)
    private FrameLayout characterContainer;
    private ImageView characterView;
    private WindowManager.LayoutParams characterParams;
    private Bitmap currentCharacterBitmap;
    private boolean isCharacterFixed = false;
    private boolean isCharacterModeActive = false;
    
    private float lastTouchX, lastTouchY;
    private float initialPinchDistance = 0;
    
    private FrameLayout menuContainer;
    private FrameLayout characterListContainer;
    
    private LinearLayout controlsLayout;
    private ImageButton fixButton;
    private ImageButton deleteButton;
    private ImageButton backButton;
    private EditText sizeXInput, sizeYInput, sizeZInput;
    private TextView sizeXLabel, sizeYLabel, sizeZLabel;
    
    // Система персонажей (старая)
    private ArrayList<CharacterData> characters = new ArrayList<>();
    private SharedPreferences prefs;
    private String tempCharacterName = "";
    
    private boolean isCharacterListOpen = false;
    private EditText nameInput;
    
    // Переключение режимов
    private boolean isWebViewMode = true;
    private FrameLayout contentContainer;
    private LinearLayout charactersGridLayout;
    
    // Настройки оверлея
    private int overlayAlpha = 255;
    private int overlaySize = 136;

    // ==================== НОВАЯ СИСТЕМА МАКРОСОВ ====================
    private FrameLayout macroControlContainer;
    private WindowManager.LayoutParams macroParams;
    private boolean isMacroModeActive = false;
    private ArrayList<MacroPoint> macroPoints = new ArrayList<>();
    private boolean isMacroRecording = false;
    private boolean isMacroRunning = false;
    private android.os.Handler macroHandler = new android.os.Handler();
    private Runnable macroRunnable;
    private int currentMacroIndex = 0;
    private FrameLayout captureOverlay;
    private LinearLayout macroPointsListLayout;
    private TextView macroStatusText;
    private TextView macroPointCountText;
    private int selectedMacroPointIndex = -1;

    // ==================== НОВАЯ СИСТЕМА ЗАПИСИ МАКРОСОВ ====================
    private boolean isRecordingMode = false;
    private ArrayList<RecordedAction> recordedActions = new ArrayList<>();
    private long recordingStartTime = 0;
    private TextView recordingStatusText;
    private ImageButton recordButton;

    private static class RecordedAction {
        String type;
        int x1, y1, x2, y2;
        long delay;
        long timestamp;
        int duration;
        long timeFromStart;
        
        RecordedAction(String type, int x, int y, long timeFromStart) {
            this.type = type;
            this.x1 = x;
            this.y1 = y;
            this.x2 = x;
            this.y2 = y;
            this.timestamp = System.currentTimeMillis();
            this.timeFromStart = timeFromStart;
            this.duration = 0;
        }
        
        RecordedAction(String type, int x1, int y1, int x2, int y2, int duration, long timeFromStart) {
            this.type = type;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.duration = duration;
            this.timestamp = System.currentTimeMillis();
            this.timeFromStart = timeFromStart;
        }
        
        JSONObject toJSON() throws Exception {
            JSONObject json = new JSONObject();
            json.put("type", type);
            json.put("x1", x1);
            json.put("y1", y1);
            json.put("x2", x2);
            json.put("y2", y2);
            json.put("duration", duration);
            json.put("timestamp", timestamp);
            json.put("timeFromStart", timeFromStart);
            return json;
        }
        
        RecordedAction(JSONObject json) throws Exception {
            this.type = json.getString("type");
            this.x1 = json.getInt("x1");
            this.y1 = json.getInt("y1");
            this.x2 = json.optInt("x2", x1);
            this.y2 = json.optInt("y2", y1);
            this.duration = json.optInt("duration", 0);
            this.timestamp = json.getLong("timestamp");
            this.timeFromStart = json.optLong("timeFromStart", 0);
        }
    }

    // ==================== НОВЫЕ ОКНА ====================
    private HashMap<String, FloatingWindow> windows = new HashMap<>();
    private boolean useModernMenu = true;

    private static class FloatingWindow {
        FrameLayout container;
        WindowManager.LayoutParams params;
        View contentView;
        String type;
        boolean isMinimized = false;
        boolean isResizing = false;
        int minWidth = 200;
        int minHeight = 180;
        int lastTouchX, lastTouchY;
        int startWidth, startHeight;
        String title = "Окно";
        ImageButton closeBtn;
        ImageButton minimizeBtn;
        ImageButton maximizeBtn;
        LinearLayout titleBar;
        View resizeHandle;
    }

    // ==================== НОВЫЕ НАСТРОЙКИ ТЕМ ====================
    private String currentTheme = "dark_red";
    private int primaryColor = 0xFFFF0000;
    private int secondaryColor = 0xFFCC0000;
    private int accentColor = 0xFFFF4444;
    private boolean rainbowMode = false;
    private float rainbowHue = 0;

    // ==================== НОВЫЕ МАКРОСЫ КОНФИГИ ====================
    private ArrayList<MacroConfig> macroConfigs = new ArrayList<>();
    private String currentMacroName = "Макрос 1";

    private static class MacroConfig {
        String name;
        ArrayList<MacroPoint> points;
        ArrayList<RecordedAction> recordedActions;
        int color;
        boolean isRecorded = false;
        
        MacroConfig(String name) {
            this.name = name;
            this.points = new ArrayList<>();
            this.recordedActions = new ArrayList<>();
            this.color = 0xFFFF0000;
            this.isRecorded = false;
        }
        
        MacroConfig(JSONObject json) throws Exception {
            this.name = json.getString("name");
            this.color = json.optInt("color", 0xFFFF0000);
            this.isRecorded = json.optBoolean("isRecorded", false);
            this.points = new ArrayList<>();
            this.recordedActions = new ArrayList<>();
            
            JSONArray pointsArray = json.optJSONArray("points");
            if (pointsArray != null) {
                for (int i = 0; i < pointsArray.length(); i++) {
                    this.points.add(new MacroPoint(pointsArray.getJSONObject(i)));
                }
            }
            
            JSONArray actionsArray = json.optJSONArray("recordedActions");
            if (actionsArray != null) {
                for (int i = 0; i < actionsArray.length(); i++) {
                    this.recordedActions.add(new RecordedAction(actionsArray.getJSONObject(i)));
                }
            }
        }
        
        JSONObject toJSON() throws Exception {
            JSONObject json = new JSONObject();
            json.put("name", name);
            json.put("color", color);
            json.put("isRecorded", isRecorded);
            
            JSONArray pointsArray = new JSONArray();
            for (MacroPoint p : points) {
                pointsArray.put(p.toJSON());
            }
            json.put("points", pointsArray);
            
            JSONArray actionsArray = new JSONArray();
            for (RecordedAction a : recordedActions) {
                actionsArray.put(a.toJSON());
            }
            json.put("recordedActions", actionsArray);
            
            return json;
        }
    }

    private static class MacroPoint {
        int x, y, delay;
        
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
        }
        
        JSONObject toJSON() throws Exception {
            JSONObject json = new JSONObject();
            json.put("x", x);
            json.put("y", y);
            json.put("delay", delay);
            return json;
        }
    }

    // ==================== КЛАСС ДАННЫХ ПЕРСОНАЖА ====================
    
    private static class CharacterData {
        String name;
        String path;
        long timestamp;
        int width;
        int height;
        
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
        }
        
        JSONObject toJSON() throws Exception {
            JSONObject json = new JSONObject();
            json.put("name", name);
            json.put("path", path);
            json.put("timestamp", timestamp);
            json.put("width", width);
            json.put("height", height);
            return json;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            prefs = getSharedPreferences("characters", MODE_PRIVATE);
            overlayAlpha = prefs.getInt("overlay_alpha", 255);
            overlaySize = prefs.getInt("overlay_size", 136);
            useModernMenu = prefs.getBoolean("use_modern_menu", true);
            loadCharacters();
            loadMacroPoints();
            loadMacroConfigs();
            loadSettings();
        } catch (Exception e) {
            e.printStackTrace();
        }

        requestPermissionsIfNeeded();
        requestAccessibilityPermission();

        createWebView();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            if (bridge != null && bridge.getWebView() != null) {
                bridge.getWebView().setWebChromeClient(new WebChromeClient() {
                    @Override
                    public void onPermissionRequest(PermissionRequest request) {
                        try {
                            request.grant(new String[]{
                                    PermissionRequest.RESOURCE_AUDIO_CAPTURE,
                                    PermissionRequest.RESOURCE_VIDEO_CAPTURE
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (windowManager == null) {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        }
    }

    // ==================== ЗАГРУЗКА НАСТРОЕК ====================

    private void loadSettings() {
        currentTheme = prefs.getString("theme", "dark_red");
        rainbowMode = prefs.getBoolean("rainbow_mode", false);
        primaryColor = prefs.getInt("primary_color", 0xFFFF0000);
        secondaryColor = prefs.getInt("secondary_color", 0xFFCC0000);
        accentColor = prefs.getInt("accent_color", 0xFFFF4444);
        useModernMenu = prefs.getBoolean("use_modern_menu", true);
    }

    private void saveSettings() {
        prefs.edit()
            .putString("theme", currentTheme)
            .putBoolean("rainbow_mode", rainbowMode)
            .putInt("primary_color", primaryColor)
            .putInt("secondary_color", secondaryColor)
            .putInt("accent_color", accentColor)
            .putBoolean("use_modern_menu", useModernMenu)
            .apply();
    }

    // ==================== МАКРОСЫ - СОХРАНЕНИЕ ====================

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
            e.printStackTrace();
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
            e.printStackTrace();
        }
    }

    // ==================== СТАРЫЕ МЕТОДЫ СОХРАНЕНИЯ ====================
    
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
            e.printStackTrace();
        }
    }
    
    private void saveCharacters() {
        try {
            JSONArray array = new JSONArray();
            for (CharacterData data : characters) {
                array.put(data.toJSON());
            }
            prefs.edit()
                .putString("characters_list", array.toString())
                .putInt("overlay_alpha", overlayAlpha)
                .putInt("overlay_size", overlaySize)
                .apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadMacroPoints() {
        try {
            macroPoints.clear();
            String json = prefs.getString("macro_points", "");
            if (!json.isEmpty()) {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    macroPoints.add(new MacroPoint(array.getJSONObject(i)));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveMacroPoints() {
        try {
            JSONArray array = new JSONArray();
            for (MacroPoint point : macroPoints) {
                array.put(point.toJSON());
            }
            prefs.edit().putString("macro_points", array.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
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
            e.printStackTrace();
            return null;
        }
    }

    // ==================== ЗАПРОС РАЗРЕШЕНИЙ ====================

    private void requestPermissionsIfNeeded() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MICROPHONE);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION);
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.READ_MEDIA_IMAGES}, REQUEST_STORAGE);
                }
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_STORAGE);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
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
            e.printStackTrace();
        }
    }

    private void showAccessibilityDialog() {
        try {
            FrameLayout dialogOverlay = new FrameLayout(this);
            dialogOverlay.setBackgroundColor(0xCC000000);
            
            LinearLayout dialogLayout = new LinearLayout(this);
            dialogLayout.setOrientation(LinearLayout.VERTICAL);
            dialogLayout.setGravity(Gravity.CENTER);
            dialogLayout.setPadding(40, 40, 40, 40);
            
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(30);
            bg.setColor(0xFF0D0D0D);
            bg.setStroke(3, 0xFFFF0000);
            dialogLayout.setBackground(bg);
            
            TextView title = new TextView(this);
            title.setText("🔧 РАЗРЕШЕНИЕ ДЛЯ МАКРОСОВ");
            title.setTextColor(0xFFFF0000);
            title.setTextSize(22);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, 0, 0, 20);
            dialogLayout.addView(title);
            
            TextView message = new TextView(this);
            message.setText("Для работы макросов необходимо включить\nспециальные возможности.\n\nЭто позволит приложению выполнять\nклики в играх и других приложениях.");
            message.setTextColor(0xFFAAAAAA);
            message.setTextSize(16);
            message.setGravity(Gravity.CENTER);
            message.setPadding(0, 0, 0, 30);
            dialogLayout.addView(message);
            
            LinearLayout buttonLayout = new LinearLayout(this);
            buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
            buttonLayout.setGravity(Gravity.CENTER);
            
            Button enableBtn = new Button(this);
            enableBtn.setText("ВКЛЮЧИТЬ");
            enableBtn.setTextColor(0xFFFFFFFF);
            enableBtn.setTextSize(14);
            enableBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable enableBg = new GradientDrawable();
            enableBg.setCornerRadius(25);
            enableBg.setColor(0xFFFF0000);
            enableBtn.setBackground(enableBg);
            enableBtn.setPadding(30, 16, 30, 16);
            enableBtn.setOnClickListener(v -> {
                if (windowManager != null) windowManager.removeView(dialogOverlay);
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivityForResult(intent, REQUEST_ACCESSIBILITY);
            });
            
            Button cancelBtn = new Button(this);
            cancelBtn.setText("ПОЗЖЕ");
            cancelBtn.setTextColor(0xFFFFFFFF);
            cancelBtn.setTextSize(14);
            cancelBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable cancelBg = new GradientDrawable();
            cancelBg.setCornerRadius(25);
            cancelBg.setColor(0xFF2A0000);
            cancelBg.setStroke(2, 0xFF8B0000);
            cancelBtn.setBackground(cancelBg);
            cancelBtn.setPadding(30, 16, 30, 16);
            cancelBtn.setOnClickListener(v -> {
                if (windowManager != null) windowManager.removeView(dialogOverlay);
            });
            
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            btnParams.setMargins(8, 0, 8, 0);
            buttonLayout.addView(enableBtn, btnParams);
            buttonLayout.addView(cancelBtn, btnParams);
            
            dialogLayout.addView(buttonLayout);
            
            FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            containerParams.gravity = Gravity.CENTER;
            containerParams.setMargins(40, 0, 40, 0);
            dialogOverlay.addView(dialogLayout, containerParams);
            
            WindowManager.LayoutParams windowParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT);
            
            if (windowManager != null) windowManager.addView(dialogOverlay, windowParams);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== СОЗДАНИЕ WEBVIEW ====================

    private void createWebView() {
        try {
            if (webView != null) return;
            
            webView = new WebView(this);
            WebSettings ws = webView.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setMediaPlaybackRequiresUserGesture(false);
            ws.setDomStorageEnabled(true);
            ws.setAllowFileAccess(true);
            ws.setAllowContentAccess(true);
            ws.setUseWideViewPort(true);
            ws.setLoadWithOverviewMode(true);
            ws.setJavaScriptCanOpenWindowsAutomatically(true);
            ws.setCacheMode(WebSettings.LOAD_DEFAULT);
            ws.setDatabaseEnabled(true);
            
            ws.setMediaPlaybackRequiresUserGesture(false);
            
            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onPermissionRequest(PermissionRequest request) {
                    try {
                        request.grant(new String[]{
                                PermissionRequest.RESOURCE_AUDIO_CAPTURE,
                                PermissionRequest.RESOURCE_VIDEO_CAPTURE
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    view.loadUrl(url);
                    return true;
                }
            });

            webView.loadUrl(URL_HOME);

            webView.setBackgroundColor(Color.parseColor("#0A0A0A"));
            webView.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== ГЛАВНЫЙ КРУЖОК (ОВЕРЛЕЙ) ====================

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
            
            int flag = getOverlayFlag();
            
            mainCircleContainer = new FrameLayout(this);
            mainCircleContainer.setBackgroundColor(Color.TRANSPARENT);
            
            ImageButton iconButton = new ImageButton(this);
            iconButton.setImageBitmap(createGamepadBitmap());
            iconButton.setBackgroundColor(Color.TRANSPARENT);
            iconButton.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
            iconButton.setPadding(25, 25, 25, 25);
            iconButton.setClickable(false);
            iconButton.setFocusable(false);
            
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(getThemeColor());
            d.setStroke(6, Color.parseColor("#FF4444"));
            mainCircleContainer.setBackground(d);
            mainCircleContainer.setAlpha(overlayAlpha / 255f);
            
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            );
            mainCircleContainer.addView(iconButton, iconParams);
            
            mainCircleParams = new WindowManager.LayoutParams(
                    overlaySize, overlaySize,
                    flag,
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
                                        if (isMacroRunning) {
                                            stopMacroExecution();
                                        } else {
                                            startMacroExecution();
                                        }
                                        return true;
                                    }
                                    lastTapTime = currentTime;
                                    hideMainOverlay();
                                    if (useModernMenu) {
                                        showModernMenu();
                                    } else {
                                        showOldMenu();
                                    }
                                }
                                return true;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return false;
                }
            });

            if (windowManager != null) {
                windowManager.addView(mainCircleContainer, mainCircleParams);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void removeMainCircle() {
        try {
            if (mainCircleContainer != null && windowManager != null) {
                windowManager.removeView(mainCircleContainer);
                mainCircleContainer = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateOverlayAppearance() {
        try {
            if (mainCircleContainer != null && windowManager != null) {
                GradientDrawable d = new GradientDrawable();
                d.setShape(GradientDrawable.OVAL);
                d.setColor(getThemeColor());
                d.setStroke(6, Color.parseColor("#FF4444"));
                mainCircleContainer.setBackground(d);
                mainCircleContainer.setAlpha(overlayAlpha / 255f);
                mainCircleParams.width = overlaySize;
                mainCircleParams.height = overlaySize;
                windowManager.updateViewLayout(mainCircleContainer, mainCircleParams);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Bitmap createGamepadBitmap() {
        try {
            int size = 120;
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint();
            paint.setAntiAlias(true);
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(8);

            float cx = size / 2f, cy = size / 2f;
            canvas.drawRoundRect(cx - 40, cy - 28, cx + 40, cy + 28, 22, 22, paint);
            canvas.drawCircle(cx - 32, cy, 16, paint);
            canvas.drawCircle(cx + 32, cy, 16, paint);
            paint.setStrokeWidth(6);
            canvas.drawLine(cx - 24, cy - 10, cx - 24, cy + 10, paint);
            canvas.drawLine(cx - 30, cy, cx - 18, cy, paint);
            canvas.drawCircle(cx + 22, cy - 8, 7, paint);
            canvas.drawCircle(cx + 22, cy + 8, 7, paint);
            canvas.drawCircle(cx + 34, cy, 7, paint);
            canvas.drawCircle(cx + 10, cy, 7, paint);
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ==================== СТАРОЕ МЕНЮ ====================

    private void showOldMenu() {
        try {
            if (windowManager == null) return;
            
            if (windows.containsKey("menu")) {
                windows.get("menu").container.setVisibility(View.VISIBLE);
                return;
            }

            FloatingWindow menuWin = new FloatingWindow();
            menuWin.type = "menu";
            menuWin.title = "⚡ CR ARCADE";

            LinearLayout mainLayout = new LinearLayout(this);
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setPadding(20, 20, 20, 20);

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(24);
            bg.setColor(0xDD0D0D0D);
            bg.setStroke(3, getThemeColor());
            mainLayout.setBackground(bg);

            TextView title = new TextView(this);
            title.setText("⚡ CR ARCADE");
            title.setTextColor(getThemeColor());
            title.setTextSize(24);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, 0, 0, 16);
            mainLayout.addView(title);

            String[][] items = {
                {"🌐 WebView", "Открыть браузер"},
                {"🎯 Макросы", "Управление макросами"},
                {"👤 Персонажи", "Мои персонажи"},
                {"🎨 Тема", "Настройки внешнего вида"},
                {"⚙️ Настройки", "Дополнительные настройки"},
                {"🔄 Сменить меню", "На новое/старое"}
            };

            for (String[] item : items) {
                Button btn = new Button(this);
                btn.setText(item[0] + "\n" + item[1]);
                btn.setTextColor(Color.WHITE);
                btn.setTextSize(14);
                btn.setTypeface(null, android.graphics.Typeface.BOLD);
                GradientDrawable btnBg = new GradientDrawable();
                btnBg.setCornerRadius(16);
                btnBg.setColor(0x44FF0000);
                btnBg.setStroke(2, getThemeColor());
                btn.setBackground(btnBg);
                btn.setPadding(16, 12, 16, 12);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 6, 0, 6);
                btn.setLayoutParams(lp);

                btn.setOnClickListener(v -> {
                    switch (item[0]) {
                        case "🌐 WebView":
                            showWebViewWindow();
                            break;
                        case "🎯 Макросы":
                            showMacrosWindow();
                            break;
                        case "👤 Персонажи":
                            showCharactersWindow();
                            break;
                        case "🎨 Тема":
                            showThemeSettings();
                            break;
                        case "⚙️ Настройки":
                            showSettingsDialog();
                            break;
                        case "🔄 Сменить меню":
                            toggleMenuStyle();
                            break;
                    }
                });
                mainLayout.addView(btn);
            }

            Button closeBtn = new Button(this);
            closeBtn.setText("✕ ЗАКРЫТЬ");
            closeBtn.setTextColor(Color.WHITE);
            closeBtn.setTextSize(14);
            GradientDrawable closeBg = new GradientDrawable();
            closeBg.setCornerRadius(16);
            closeBg.setColor(0x44FF0000);
            closeBg.setStroke(2, 0xFFFF0000);
            closeBtn.setBackground(closeBg);
            closeBtn.setPadding(16, 12, 16, 12);
            LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            closeLp.setMargins(0, 12, 0, 0);
            closeBtn.setLayoutParams(closeLp);
            closeBtn.setOnClickListener(v -> {
                if (windows.containsKey("menu")) {
                    removeWindow("menu");
                }
            });
            mainLayout.addView(closeBtn);

            menuWin.contentView = mainLayout;
            menuWin.container = new FrameLayout(this);
            menuWin.container.addView(mainLayout);

            menuWin.params = new WindowManager.LayoutParams(
                    350, WindowManager.LayoutParams.WRAP_CONTENT,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            menuWin.params.gravity = Gravity.CENTER;

            setupWindowDragging(menuWin);

            windowManager.addView(menuWin.container, menuWin.params);
            windows.put("menu", menuWin);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== НОВОЕ МЕНЮ - КРУЖОЧКИ ====================

    private void showModernMenu() {
        try {
            if (windowManager == null) return;
            
            if (windows.containsKey("menu_modern")) {
                windows.get("menu_modern").container.setVisibility(View.VISIBLE);
                return;
            }

            FloatingWindow menuWin = new FloatingWindow();
            menuWin.type = "menu_modern";
            menuWin.title = "Меню";

            LinearLayout mainLayout = new LinearLayout(this);
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setPadding(12, 12, 12, 12);

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(20);
            bg.setColor(0xDD0D0D0D);
            bg.setStroke(2, getThemeColor());
            mainLayout.setBackground(bg);

            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            
            TextView title = new TextView(this);
            title.setText("⚡");
            title.setTextColor(getThemeColor());
            title.setTextSize(20);
            title.setPadding(0, 0, 8, 0);
            header.addView(title);
            
            TextView titleText = new TextView(this);
            titleText.setText("CR ARCADE");
            titleText.setTextColor(Color.WHITE);
            titleText.setTextSize(14);
            titleText.setTypeface(null, android.graphics.Typeface.BOLD);
            titleText.setLayoutParams(new LinearLayout.LayoutParams(0, 
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            header.addView(titleText);
            
            Button closeBtn = new Button(this);
            closeBtn.setText("✕");
            closeBtn.setTextColor(Color.WHITE);
            closeBtn.setTextSize(14);
            closeBtn.setBackgroundColor(Color.TRANSPARENT);
            closeBtn.setPadding(8, 4, 8, 4);
            closeBtn.setOnClickListener(v -> removeWindow("menu_modern"));
            header.addView(closeBtn);
            
            mainLayout.addView(header);

            LinearLayout menuGrid = new LinearLayout(this);
            menuGrid.setOrientation(LinearLayout.VERTICAL);
            menuGrid.setGravity(Gravity.CENTER);
            menuGrid.setPadding(0, 8, 0, 0);

            String[][] icons = {
                {"🌐", "WebView"},
                {"🎯", "Макросы"},
                {"👤", "Персонажи"},
                {"🎨", "Тема"},
                {"⚙️", "Настройки"},
                {"🔄", "Сменить"}
            };

            int cols = 3;
            for (int i = 0; i < icons.length; i += cols) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER);
                
                for (int j = i; j < Math.min(i + cols, icons.length); j++) {
                    final String icon = icons[j][0];
                    final String label = icons[j][1];
                    
                    LinearLayout item = new LinearLayout(this);
                    item.setOrientation(LinearLayout.VERTICAL);
                    item.setGravity(Gravity.CENTER);
                    item.setPadding(4, 4, 4, 4);
                    
                    Button circleBtn = new Button(this);
                    circleBtn.setText(icon);
                    circleBtn.setTextSize(24);
                    circleBtn.setTextColor(Color.WHITE);
                    
                    GradientDrawable circleBg = new GradientDrawable();
                    circleBg.setShape(GradientDrawable.OVAL);
                    circleBg.setColor(getThemeColor());
                    circleBg.setAlpha(180);
                    circleBg.setStroke(2, Color.parseColor("#FF4444"));
                    circleBtn.setBackground(circleBg);
                    
                    int size = 60;
                    LinearLayout.LayoutParams circleParams = new LinearLayout.LayoutParams(size, size);
                    circleParams.setMargins(3, 3, 3, 3);
                    circleBtn.setLayoutParams(circleParams);
                    
                    circleBtn.setOnClickListener(v -> {
                        switch (label) {
                            case "WebView":
                                showWebViewWindow();
                                break;
                            case "Макросы":
                                showMacrosWindow();
                                break;
                            case "Персонажи":
                                showCharactersWindow();
                                break;
                            case "Тема":
                                showThemeSettings();
                                break;
                            case "Настройки":
                                showSettingsDialog();
                                break;
                            case "Сменить":
                                toggleMenuStyle();
                                break;
                        }
                    });
                    
                    item.addView(circleBtn);
                    
                    TextView labelText = new TextView(this);
                    labelText.setText(label);
                    labelText.setTextColor(0xFF888888);
                    labelText.setTextSize(10);
                    labelText.setGravity(Gravity.CENTER);
                    item.addView(labelText);
                    
                    LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                    item.setLayoutParams(itemParams);
                    row.addView(item);
                }
                
                menuGrid.addView(row);
            }

            mainLayout.addView(menuGrid);

            LinearLayout switchLayout = new LinearLayout(this);
            switchLayout.setOrientation(LinearLayout.HORIZONTAL);
            switchLayout.setGravity(Gravity.CENTER);
            switchLayout.setPadding(0, 8, 0, 0);
            
            TextView switchLabel = new TextView(this);
            switchLabel.setText("Новое меню");
            switchLabel.setTextColor(Color.WHITE);
            switchLabel.setTextSize(12);
            switchLabel.setPadding(0, 0, 8, 0);
            switchLayout.addView(switchLabel);
            
            Button switchBtn = new Button(this);
            switchBtn.setText(useModernMenu ? "✅" : "⬜");
            switchBtn.setTextSize(16);
            switchBtn.setBackgroundColor(Color.TRANSPARENT);
            switchBtn.setOnClickListener(v -> toggleMenuStyle());
            switchLayout.addView(switchBtn);
            
            mainLayout.addView(switchLayout);

            menuWin.contentView = mainLayout;
            menuWin.container = new FrameLayout(this);
            menuWin.container.addView(mainLayout);

            menuWin.params = new WindowManager.LayoutParams(
                    220, WindowManager.LayoutParams.WRAP_CONTENT,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            menuWin.params.gravity = Gravity.CENTER;

            setupWindowDragging(menuWin);

            windowManager.addView(menuWin.container, menuWin.params);
            windows.put("menu_modern", menuWin);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void toggleMenuStyle() {
        useModernMenu = !useModernMenu;
        saveSettings();
        removeWindow("menu");
        removeWindow("menu_modern");
        Toast.makeText(this, useModernMenu ? "Новое меню" : "Старое меню", Toast.LENGTH_SHORT).show();
        if (useModernMenu) {
            showModernMenu();
        } else {
            showOldMenu();
        }
    }

    // ==================== WEBVIEW ОКНО ====================

    private void showWebViewWindow() {
        try {
            if (windows.containsKey("webview")) {
                windows.get("webview").container.setVisibility(View.VISIBLE);
                return;
            }

            FloatingWindow win = new FloatingWindow();
            win.type = "webview";
            win.title = "🌐 WebView";

            LinearLayout container = new LinearLayout(this);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setBackgroundColor(0xFF0A0A0A);

            LinearLayout titleBar = createTitleBar(win);
            container.addView(titleBar);

            WebView webView = new WebView(this);
            WebSettings ws = webView.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setDomStorageEnabled(true);
            ws.setMediaPlaybackRequiresUserGesture(false);
            ws.setUseWideViewPort(true);
            ws.setLoadWithOverviewMode(true);
            ws.setAllowFileAccess(true);
            ws.setAllowContentAccess(true);

            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onPermissionRequest(PermissionRequest request) {
                    request.grant(request.getResources());
                }
            });
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    view.loadUrl(url);
                    return true;
                }
            });

            webView.loadUrl(URL_HOME);
            webView.setBackgroundColor(0xFF0A0A0A);

            FrameLayout webContainer = new FrameLayout(this);
            webContainer.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
            webContainer.addView(webView);
            container.addView(webContainer);

            win.contentView = container;
            win.container = new FrameLayout(this);
            win.container.addView(container);

            GradientDrawable border = new GradientDrawable();
            border.setCornerRadius(12);
            border.setColor(0xFF0A0A0A);
            border.setStroke(3, getThemeColor());
            win.container.setBackground(border);

            win.params = new WindowManager.LayoutParams(
                    500, 400,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            win.params.gravity = Gravity.CENTER;

            setupWindowResizing(win);
            setupWindowDragging(win);

            windowManager.addView(win.container, win.params);
            windows.put("webview", win);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== МАКРОСЫ ОКНО С ЗАПИСЬЮ ====================

    private void showMacrosWindow() {
        try {
            if (windows.containsKey("macros")) {
                windows.get("macros").container.setVisibility(View.VISIBLE);
                return;
            }

            FloatingWindow win = new FloatingWindow();
            win.type = "macros";
            win.title = "🎯 МАКРОСЫ";

            LinearLayout mainLayout = new LinearLayout(this);
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setPadding(12, 12, 12, 12);
            mainLayout.setBackgroundColor(0xDD0D0D0D);

            LinearLayout titleBar = createTitleBar(win);
            mainLayout.addView(titleBar);

            // Выбор макроса
            LinearLayout selectLayout = new LinearLayout(this);
            selectLayout.setOrientation(LinearLayout.HORIZONTAL);
            selectLayout.setGravity(Gravity.CENTER);
            selectLayout.setPadding(0, 8, 0, 8);

            Button prevBtn = new Button(this);
            prevBtn.setText("◀");
            prevBtn.setTextColor(Color.WHITE);
            prevBtn.setTextSize(16);
            prevBtn.setBackgroundColor(0x33FF0000);
            prevBtn.setPadding(8, 4, 8, 4);
            prevBtn.setOnClickListener(v -> {
                int idx = -1;
                for (int i = 0; i < macroConfigs.size(); i++) {
                    if (macroConfigs.get(i).name.equals(currentMacroName)) {
                        idx = i;
                        break;
                    }
                }
                if (idx > 0) {
                    currentMacroName = macroConfigs.get(idx - 1).name;
                    updateMacroUI(mainLayout);
                } else {
                    Toast.makeText(this, "Это первый макрос", Toast.LENGTH_SHORT).show();
                }
            });
            selectLayout.addView(prevBtn);

            final TextView macroNameText = new TextView(this);
            macroNameText.setText(currentMacroName);
            macroNameText.setTextColor(getThemeColor());
            macroNameText.setTextSize(14);
            macroNameText.setTypeface(null, android.graphics.Typeface.BOLD);
            macroNameText.setPadding(8, 0, 8, 0);
            macroNameText.setLayoutParams(new LinearLayout.LayoutParams(0, 
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            macroNameText.setGravity(Gravity.CENTER);
            selectLayout.addView(macroNameText);

            Button nextBtn = new Button(this);
            nextBtn.setText("▶");
            nextBtn.setTextColor(Color.WHITE);
            nextBtn.setTextSize(16);
            nextBtn.setBackgroundColor(0x33FF0000);
            nextBtn.setPadding(8, 4, 8, 4);
            nextBtn.setOnClickListener(v -> {
                int idx = -1;
                for (int i = 0; i < macroConfigs.size(); i++) {
                    if (macroConfigs.get(i).name.equals(currentMacroName)) {
                        idx = i;
                        break;
                    }
                }
                if (idx < macroConfigs.size() - 1) {
                    currentMacroName = macroConfigs.get(idx + 1).name;
                    updateMacroUI(mainLayout);
                } else {
                    Toast.makeText(this, "Это последний макрос", Toast.LENGTH_SHORT).show();
                }
            });
            selectLayout.addView(nextBtn);

            Button newMacroBtn = new Button(this);
            newMacroBtn.setText("+");
            newMacroBtn.setTextColor(Color.WHITE);
            newMacroBtn.setTextSize(16);
            newMacroBtn.setBackgroundColor(0xFFFF0000);
            newMacroBtn.setPadding(12, 4, 12, 4);
            newMacroBtn.setOnClickListener(v -> showNewMacroDialog(mainLayout));
            selectLayout.addView(newMacroBtn);

            mainLayout.addView(selectLayout);

            // Статус записи
            recordingStatusText = new TextView(this);
            recordingStatusText.setText("⏸ Готов");
            recordingStatusText.setTextColor(Color.WHITE);
            recordingStatusText.setTextSize(12);
            recordingStatusText.setGravity(Gravity.CENTER);
            recordingStatusText.setPadding(0, 4, 0, 4);
            mainLayout.addView(recordingStatusText);

            // Точки макроса
            final LinearLayout pointsContainer = new LinearLayout(this);
            pointsContainer.setOrientation(LinearLayout.VERTICAL);
            pointsContainer.setPadding(0, 4, 0, 0);
            mainLayout.addView(pointsContainer);

            // Кнопки управления
            LinearLayout controlLayout = new LinearLayout(this);
            controlLayout.setOrientation(LinearLayout.HORIZONTAL);
            controlLayout.setGravity(Gravity.CENTER);
            controlLayout.setPadding(0, 4, 0, 4);

            // Кнопка записи
            recordButton = new ImageButton(this);
            recordButton.setImageDrawable(createRecordIcon(false));
            recordButton.setBackgroundColor(Color.TRANSPARENT);
            recordButton.setPadding(8, 8, 8, 8);
            recordButton.setOnClickListener(v -> toggleRecording());
            controlLayout.addView(recordButton);

            Button startBtn = createStyledIconButton("▶ СТАРТ", 0xFF00AA00);
            startBtn.setOnClickListener(v -> startMacroExecution());
            controlLayout.addView(startBtn);

            Button stopBtn = createStyledIconButton("■ СТОП", 0xFFFF0000);
            stopBtn.setOnClickListener(v -> stopMacroExecution());
            controlLayout.addView(stopBtn);

            Button clearBtn = createStyledIconButton("✕ ОЧ", 0xFFFF8800);
            clearBtn.setOnClickListener(v -> clearMacroPoints());
            controlLayout.addView(clearBtn);

            mainLayout.addView(controlLayout);

            // Кнопка сохранения
            Button saveBtn = createStyledButton("💾 СОХРАНИТЬ", 0xFFFFAA00);
            saveBtn.setOnClickListener(v -> {
                saveMacroConfigs();
                Toast.makeText(this, "Макрос сохранён!", Toast.LENGTH_SHORT).show();
            });
            mainLayout.addView(saveBtn);

            win.contentView = mainLayout;
            win.container = new FrameLayout(this);
            win.container.addView(mainLayout);

            GradientDrawable border = new GradientDrawable();
            border.setCornerRadius(16);
            border.setColor(0xDD0D0D0D);
            border.setStroke(3, getThemeColor());
            win.container.setBackground(border);

            win.params = new WindowManager.LayoutParams(
                    400, 480,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            win.params.gravity = Gravity.CENTER;

            setupWindowResizing(win);
            setupWindowDragging(win);

            windowManager.addView(win.container, win.params);
            windows.put("macros", win);

            updateMacroUI(mainLayout);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== ЗАПИСЬ МАКРОСОВ (ПРОХОДИТ ЧЕРЕЗ ПРИЛОЖЕНИЕ) ====================

    private void toggleRecording() {
        if (isRecordingMode) {
            stopRecording();
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Включите специальные возможности", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }

        isRecordingMode = true;
        recordedActions.clear();
        recordingStartTime = System.currentTimeMillis();
        
        if (recordingStatusText != null) {
            recordingStatusText.setText("🔴 ЗАПИСЬ...");
            recordingStatusText.setTextColor(Color.RED);
        }
        if (recordButton != null) {
            recordButton.setImageDrawable(createRecordIcon(true));
        }
        
        Toast.makeText(this, "Запись начата! Нажимайте на экран", Toast.LENGTH_SHORT).show();

        // Создаем прозрачный оверлей который пропускает касания
        captureOverlay = new FrameLayout(this);
        captureOverlay.setBackgroundColor(0x00000000);
        
        int flag = getOverlayFlag();
        WindowManager.LayoutParams captureParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                flag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
        );
        captureParams.gravity = Gravity.TOP | Gravity.START;
        captureParams.x = 0;
        captureParams.y = 0;
        
        captureOverlay.setOnTouchListener((v, event) -> {
            if (isRecordingMode && event.getAction() == MotionEvent.ACTION_UP) {
                int x = (int) event.getRawX();
                int y = (int) event.getRawY();
                long timeFromStart = System.currentTimeMillis() - recordingStartTime;
                addRecordedAction("click", x, y, timeFromStart);
                return false;
            }
            return false;
        });
        
        windowManager.addView(captureOverlay, captureParams);
    }

    private void stopRecording() {
        isRecordingMode = false;
        
        if (captureOverlay != null && windowManager != null) {
            windowManager.removeView(captureOverlay);
            captureOverlay = null;
        }
        
        if (recordingStatusText != null) {
            recordingStatusText.setText("⏸ Запись остановлена");
            recordingStatusText.setTextColor(Color.WHITE);
        }
        if (recordButton != null) {
            recordButton.setImageDrawable(createRecordIcon(false));
        }
        
        if (!recordedActions.isEmpty()) {
            MacroConfig config = getCurrentMacro();
            if (config != null) {
                config.recordedActions = new ArrayList<>(recordedActions);
                config.isRecorded = true;
                saveMacroConfigs();
                
                Toast.makeText(this, "Запись сохранена! " + recordedActions.size() + " действий", Toast.LENGTH_SHORT).show();
                
                if (windows.containsKey("macros")) {
                    updateMacroUI((LinearLayout) windows.get("macros").contentView);
                }
            }
        } else {
            Toast.makeText(this, "Нет записанных действий", Toast.LENGTH_SHORT).show();
        }
    }

    private void addRecordedAction(String type, int x, int y, long timeFromStart) {
        if (!isRecordingMode) return;
        
        RecordedAction action = new RecordedAction(type, x, y, timeFromStart);
        recordedActions.add(action);
        
        if (recordingStatusText != null) {
            recordingStatusText.setText("🔴 " + recordedActions.size() + " действий");
        }
    }

    private void updateMacroUI(LinearLayout mainLayout) {
        MacroConfig config = getCurrentMacro();
        if (config == null) return;

        LinearLayout pointsContainer = null;
        for (int i = 0; i < mainLayout.getChildCount(); i++) {
            View v = mainLayout.getChildAt(i);
            if (v instanceof LinearLayout) {
                LinearLayout ll = (LinearLayout) v;
                if (ll.getChildCount() > 0 && ll.getChildAt(0) instanceof TextView) {
                    TextView tv = (TextView) ll.getChildAt(0);
                    if (tv.getText().toString().contains("Точки:") || tv.getText().toString().contains("Действий:")) {
                        pointsContainer = ll;
                        break;
                    }
                }
            }
        }

        // Обновляем имя макроса
        for (int i = 0; i < mainLayout.getChildCount(); i++) {
            View v = mainLayout.getChildAt(i);
            if (v instanceof LinearLayout) {
                LinearLayout ll = (LinearLayout) v;
                for (int j = 0; j < ll.getChildCount(); j++) {
                    View child = ll.getChildAt(j);
                    if (child instanceof TextView) {
                        TextView tv = (TextView) child;
                        if (tv.getText().toString().equals(currentMacroName) ||
                            tv.getText().toString().matches(".*" + currentMacroName + ".*")) {
                            tv.setText(currentMacroName);
                            tv.setTextColor(getThemeColor());
                            break;
                        }
                    }
                }
            }
        }

        if (pointsContainer == null) {
            pointsContainer = new LinearLayout(this);
            pointsContainer.setOrientation(LinearLayout.VERTICAL);
            pointsContainer.setPadding(0, 4, 0, 0);
            int insertIndex = mainLayout.getChildCount() - 2;
            mainLayout.addView(pointsContainer, insertIndex);
        }

        pointsContainer.removeAllViews();

        int totalActions = config.points.size() + config.recordedActions.size();
        TextView header = new TextView(this);
        if (config.isRecorded && !config.recordedActions.isEmpty()) {
            header.setText("Действий: " + config.recordedActions.size() + " (запись)");
        } else {
            header.setText("Точки: " + config.points.size());
        }
        header.setTextColor(Color.WHITE);
        header.setTextSize(12);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setPadding(0, 0, 0, 4);
        pointsContainer.addView(header);

        if (config.isRecorded && !config.recordedActions.isEmpty()) {
            ScrollView scroll = new ScrollView(this);
            LinearLayout list = new LinearLayout(this);
            list.setOrientation(LinearLayout.VERTICAL);

            for (int i = 0; i < config.recordedActions.size(); i++) {
                RecordedAction a = config.recordedActions.get(i);
                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.HORIZONTAL);
                item.setGravity(Gravity.CENTER_VERTICAL);
                item.setPadding(4, 4, 4, 4);
                GradientDrawable itemBg = new GradientDrawable();
                itemBg.setCornerRadius(6);
                itemBg.setColor(0x22FF8800);
                itemBg.setStroke(1, getThemeColor());
                item.setBackground(itemBg);

                String actionText = a.type.equals("click") ? "👆" : "🖐";
                String timeText = (a.timeFromStart / 1000) + "с";
                TextView info = new TextView(this);
                info.setText(actionText + " #" + (i+1) + " (" + a.x1 + "," + a.y1 + ") " + timeText);
                info.setTextColor(Color.WHITE);
                info.setTextSize(11);
                info.setLayoutParams(new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                item.addView(info);

                Button delBtn = new Button(this);
                delBtn.setText("✕");
                delBtn.setTextColor(0xFFFF0000);
                delBtn.setTextSize(11);
                delBtn.setBackgroundColor(0x33FF0000);
                delBtn.setPadding(6, 2, 6, 2);
                final int idx = i;
                delBtn.setOnClickListener(v -> {
                    config.recordedActions.remove(idx);
                    saveMacroConfigs();
                    updateMacroUI(mainLayout);
                });
                item.addView(delBtn);

                list.addView(item);
            }

            scroll.addView(list);
            pointsContainer.addView(scroll);
        } else if (!config.points.isEmpty()) {
            ScrollView scroll = new ScrollView(this);
            LinearLayout list = new LinearLayout(this);
            list.setOrientation(LinearLayout.VERTICAL);

            for (int i = 0; i < config.points.size(); i++) {
                MacroPoint p = config.points.get(i);
                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.HORIZONTAL);
                item.setGravity(Gravity.CENTER_VERTICAL);
                item.setPadding(4, 4, 4, 4);
                GradientDrawable itemBg = new GradientDrawable();
                itemBg.setCornerRadius(6);
                itemBg.setColor(0x22FF0000);
                itemBg.setStroke(1, getThemeColor());
                item.setBackground(itemBg);

                String delayText = p.delay >= 1000 ? (p.delay/1000) + "с" : p.delay + "мс";
                TextView info = new TextView(this);
                info.setText(" #" + (i+1) + " (" + p.x + "," + p.y + ") " + delayText);
                info.setTextColor(Color.WHITE);
                info.setTextSize(11);
                info.setLayoutParams(new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                item.addView(info);

                Button delBtn = new Button(this);
                delBtn.setText("✕");
                delBtn.setTextColor(0xFFFF0000);
                delBtn.setTextSize(11);
                delBtn.setBackgroundColor(0x33FF0000);
                delBtn.setPadding(6, 2, 6, 2);
                final int idx = i;
                delBtn.setOnClickListener(v -> {
                    config.points.remove(idx);
                    saveMacroConfigs();
                    updateMacroUI(mainLayout);
                });
                item.addView(delBtn);

                list.addView(item);
            }

            scroll.addView(list);
            pointsContainer.addView(scroll);
        } else {
            TextView empty = new TextView(this);
            empty.setText("Нет действий. Нажмите 🔴 ЗАПИСЬ");
            empty.setTextColor(0xFF888888);
            empty.setTextSize(11);
            empty.setPadding(0, 4, 0, 4);
            pointsContainer.addView(empty);
        }
    }

    private Drawable createRecordIcon(boolean recording) {
        Bitmap b = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        
        float cx = 25, cy = 25;
        
        if (recording) {
            p.setColor(Color.RED);
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(cx, cy, 12, p);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2);
            c.drawCircle(cx, cy, 12, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.WHITE);
            c.drawRect(cx - 6, cy - 6, cx + 6, cy + 6, p);
        } else {
            p.setColor(Color.RED);
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(cx, cy, 14, p);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2);
            c.drawCircle(cx, cy, 14, p);
        }
        
        return new android.graphics.drawable.BitmapDrawable(getResources(), b);
    }

    // ==================== МАКРОСЫ - ВЫПОЛНЕНИЕ ====================

    private void startMacroExecution() {
        MacroConfig config = getCurrentMacro();
        if (config == null) return;
        
        int totalActions = config.points.size() + config.recordedActions.size();
        if (totalActions == 0) {
            Toast.makeText(this, "Нет действий для выполнения", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Включите специальные возможности", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }

        isMacroRunning = true;
        currentMacroIndex = 0;
        Toast.makeText(this, "Макрос запущен! (" + totalActions + " действий)", Toast.LENGTH_SHORT).show();
        
        if (config.isRecorded && !config.recordedActions.isEmpty()) {
            executeRecordedActions(config);
        } else {
            executePoints(config);
        }
    }

    private void executePoints(MacroConfig config) {
        if (!isMacroRunning || currentMacroIndex >= config.points.size()) {
            stopMacroExecution();
            return;
        }

        MacroPoint point = config.points.get(currentMacroIndex);
        performClick(point.x, point.y);

        currentMacroIndex++;
        
        macroHandler.postDelayed(() -> {
            executePoints(config);
        }, point.delay);
    }

    private void executeRecordedActions(MacroConfig config) {
        if (!isMacroRunning || currentMacroIndex >= config.recordedActions.size()) {
            stopMacroExecution();
            return;
        }

        RecordedAction action = config.recordedActions.get(currentMacroIndex);
        
        if (action.type.equals("click")) {
            performClick(action.x1, action.y1);
        } else if (action.type.equals("swipe")) {
            performSwipe(action.x1, action.y1, action.x2, action.y2, action.duration);
        }
        
        currentMacroIndex++;
        
        macroHandler.postDelayed(() -> {
            executeRecordedActions(config);
        }, 200);
    }

    private void performSwipe(int startX, int startY, int endX, int endY, int duration) {
        try {
            MacroService service = MacroService.getInstance();
            if (service != null) {
                service.performSwipe(startX, startY, endX, endY, duration);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void performClick(int x, int y) {
        try {
            MacroService service = MacroService.getInstance();
            if (service != null) {
                service.performClick(x, y);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopMacroExecution() {
        isMacroRunning = false;
        macroHandler.removeCallbacksAndMessages(null);
        Toast.makeText(this, "Макрос остановлен", Toast.LENGTH_SHORT).show();
    }

    private boolean isAccessibilityServiceEnabled() {
        try {
            String service = getPackageName() + "/" + MacroService.class.getCanonicalName();
            String enabledServices = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return enabledServices != null && enabledServices.contains(service);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
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

    private void showNewMacroDialog(final LinearLayout mainLayout) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Новый макрос");
        final EditText input = new EditText(this);
        input.setHint("Имя макроса");
        input.setText("Макрос " + (macroConfigs.size() + 1));
        builder.setView(input);
        builder.setPositiveButton("Создать", (d, w) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) name = "Макрос " + (macroConfigs.size() + 1);
            MacroConfig config = new MacroConfig(name);
            config.color = getThemeColor();
            macroConfigs.add(config);
            currentMacroName = name;
            saveMacroConfigs();
            updateMacroUI(mainLayout);
            Toast.makeText(this, "Макрос создан", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void clearMacroPoints() {
        MacroConfig config = getCurrentMacro();
        if (config != null) {
            config.points.clear();
            config.recordedActions.clear();
            config.isRecorded = false;
            saveMacroConfigs();
            if (windows.containsKey("macros")) {
                updateMacroUI((LinearLayout) windows.get("macros").contentView);
            }
            Toast.makeText(this, "Все действия удалены", Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== ПЕРСОНАЖИ КАК ОКНО ====================

    private void showCharactersWindow() {
        try {
            if (windows.containsKey("characters")) {
                windows.get("characters").container.setVisibility(View.VISIBLE);
                return;
            }

            FloatingWindow win = new FloatingWindow();
            win.type = "characters";
            win.title = "👤 ПЕРСОНАЖИ";

            LinearLayout mainLayout = new LinearLayout(this);
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setPadding(12, 12, 12, 12);
            mainLayout.setBackgroundColor(0xDD0D0D0D);

            LinearLayout titleBar = createTitleBar(win);
            mainLayout.addView(titleBar);

            final LinearLayout listContainer = new LinearLayout(this);
            listContainer.setOrientation(LinearLayout.VERTICAL);
            mainLayout.addView(listContainer);

            Button addBtn = createStyledButton("ДОБАВИТЬ ПЕРСОНАЖА", 0xFFFF0000);
            addBtn.setOnClickListener(v -> showAddCharacterDialog(listContainer));
            mainLayout.addView(addBtn);

            win.contentView = mainLayout;
            win.container = new FrameLayout(this);
            win.container.addView(mainLayout);

            GradientDrawable border = new GradientDrawable();
            border.setCornerRadius(16);
            border.setColor(0xDD0D0D0D);
            border.setStroke(3, getThemeColor());
            win.container.setBackground(border);

            win.params = new WindowManager.LayoutParams(
                    380, 420,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            win.params.gravity = Gravity.CENTER;

            setupWindowResizing(win);
            setupWindowDragging(win);

            windowManager.addView(win.container, win.params);
            windows.put("characters", win);

            updateCharactersUI(listContainer);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateCharactersUI(LinearLayout container) {
        container.removeAllViews();
        loadCharacters();

        if (characters.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Нет персонажей\nНажмите + чтобы добавить");
            empty.setTextColor(0xFF888888);
            empty.setTextSize(12);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, 30, 0, 30);
            container.addView(empty);
            return;
        }

        for (int i = 0; i < characters.size(); i++) {
            CharacterData data = characters.get(i);
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(6, 6, 6, 6);
            GradientDrawable itemBg = new GradientDrawable();
            itemBg.setCornerRadius(10);
            itemBg.setColor(0x22FFFFFF);
            itemBg.setStroke(2, getThemeColor());
            item.setBackground(itemBg);

            ImageView icon = new ImageView(this);
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(),
                        Uri.fromFile(new File(data.path)));
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 50, 50, true);
                icon.setImageBitmap(scaled);
            } catch (Exception e) {
                icon.setImageBitmap(createPlaceholderIcon());
            }
            icon.setPadding(4, 4, 4, 4);
            item.addView(icon);

            TextView nameText = new TextView(this);
            nameText.setText(data.name);
            nameText.setTextColor(Color.WHITE);
            nameText.setTextSize(12);
            nameText.setPadding(8, 0, 0, 0);
            nameText.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            item.addView(nameText);

            Button delBtn = new Button(this);
            delBtn.setText("✕");
            delBtn.setTextColor(0xFFFF0000);
            delBtn.setTextSize(14);
            delBtn.setBackgroundColor(0x33FF0000);
            delBtn.setPadding(6, 2, 6, 2);
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
        Bitmap b = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.GRAY);
        c.drawCircle(25, 25, 20, p);
        p.setColor(Color.WHITE);
        p.setTextSize(24);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText("👤", 25, 35, p);
        return b;
    }

    private void showAddCharacterDialog(final LinearLayout container) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Новый персонаж");
        final EditText input = new EditText(this);
        input.setHint("Имя персонажа");
        builder.setView(input);
        builder.setPositiveButton("Выбрать фото", (d, w) -> {
            tempCharacterName = input.getText().toString().trim();
            if (tempCharacterName.isEmpty()) tempCharacterName = "Персонаж " + (characters.size() + 1);
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, REQUEST_GALLERY);
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    // ==================== ТЕМЫ И НАСТРОЙКИ ====================

    private void showThemeSettings() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🎨 Настройка темы");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(16, 16, 16, 16);

        LinearLayout themeButtons = new LinearLayout(this);
        themeButtons.setOrientation(LinearLayout.HORIZONTAL);
        themeButtons.setGravity(Gravity.CENTER);

        int[] colors = {0xFFFF0000, 0xFFCC0000, 0xFFFF8800, 0xFFFF00FF, 0xFFFF0000};
        String[] names = {"Красный", "Тёмный", "Оранж", "Розовый", "Радуга"};

        for (int i = 0; i < names.length; i++) {
            Button btn = new Button(this);
            btn.setText(names[i]);
            btn.setTextColor(Color.WHITE);
            btn.setTextSize(11);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(10);
            bg.setColor(colors[i]);
            btn.setBackground(bg);
            btn.setPadding(10, 6, 10, 6);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            lp.setMargins(3, 0, 3, 0);
            btn.setLayoutParams(lp);

            final int color = colors[i];
            final boolean isRainbow = (i == 4);
            btn.setOnClickListener(v -> {
                primaryColor = color;
                secondaryColor = color;
                accentColor = color;
                rainbowMode = isRainbow;
                saveSettings();
                applyTheme();
                Toast.makeText(this, "Тема применена!", Toast.LENGTH_SHORT).show();
            });
            themeButtons.addView(btn);
        }
        layout.addView(themeButtons);

        builder.setView(layout);
        builder.setPositiveButton("Закрыть", null);
        builder.show();
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⚙️ Настройки");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(16, 16, 16, 16);

        LinearLayout menuStyleLayout = new LinearLayout(this);
        menuStyleLayout.setOrientation(LinearLayout.HORIZONTAL);
        menuStyleLayout.setGravity(Gravity.CENTER_VERTICAL);
        menuStyleLayout.setPadding(0, 8, 0, 8);
        
        TextView menuLabel = new TextView(this);
        menuLabel.setText("Стиль меню: ");
        menuLabel.setTextColor(Color.WHITE);
        menuLabel.setTextSize(14);
        menuStyleLayout.addView(menuLabel);
        
        Button menuToggle = new Button(this);
        menuToggle.setText(useModernMenu ? "Новое (кружочки)" : "Старое (список)");
        menuToggle.setTextColor(Color.WHITE);
        menuToggle.setTextSize(12);
        menuToggle.setBackgroundColor(0xFF444444);
        menuToggle.setPadding(12, 6, 12, 6);
        menuToggle.setOnClickListener(v -> {
            toggleMenuStyle();
            menuToggle.setText(useModernMenu ? "Новое (кружочки)" : "Старое (список)");
        });
        menuStyleLayout.addView(menuToggle);
        
        layout.addView(menuStyleLayout);

        TextView sizeLabel = new TextView(this);
        int currentSize = prefs.getInt("overlay_size", 80);
        sizeLabel.setText("Размер оверлея: " + currentSize + "px");
        sizeLabel.setTextColor(Color.WHITE);
        sizeLabel.setPadding(0, 8, 0, 0);
        layout.addView(sizeLabel);

        SeekBar sizeSeek = new SeekBar(this);
        sizeSeek.setMax(200);
        sizeSeek.setMin(40);
        sizeSeek.setProgress(currentSize);
        sizeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                prefs.edit().putInt("overlay_size", progress).apply();
                sizeLabel.setText("Размер оверлея: " + progress + "px");
                createMainCircle();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        layout.addView(sizeSeek);

        TextView alphaLabel = new TextView(this);
        int currentAlpha = prefs.getInt("overlay_alpha", 200);
        alphaLabel.setText("Прозрачность: " + (currentAlpha * 100 / 255) + "%");
        alphaLabel.setTextColor(Color.WHITE);
        alphaLabel.setPadding(0, 8, 0, 0);
        layout.addView(alphaLabel);

        SeekBar alphaSeek = new SeekBar(this);
        alphaSeek.setMax(255);
        alphaSeek.setProgress(currentAlpha);
        alphaSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                prefs.edit().putInt("overlay_alpha", progress).apply();
                alphaLabel.setText("Прозрачность: " + (progress * 100 / 255) + "%");
                createMainCircle();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        layout.addView(alphaSeek);

        builder.setView(layout);
        builder.setPositiveButton("Закрыть", null);
        builder.show();
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    private int getThemeColor() {
        if (rainbowMode) {
            rainbowHue += 0.01f;
            if (rainbowHue > 1f) rainbowHue = 0f;
            return Color.HSVToColor(new float[]{rainbowHue * 360f, 0.9f, 1f});
        }
        return primaryColor;
    }

    private void applyTheme() {
        updateOverlayAppearance();
        for (FloatingWindow win : windows.values()) {
            if (win.container != null) {
                GradientDrawable border = new GradientDrawable();
                border.setCornerRadius(16);
                border.setColor(0xDD0D0D0D);
                border.setStroke(3, getThemeColor());
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
    }

    private int getOverlayFlag() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;
    }

    // ==================== СОЗДАНИЕ ЗАГОЛОВКА ОКНА ====================

    private LinearLayout createTitleBar(final FloatingWindow win) {
        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(10, 6, 10, 6);
        titleBar.setBackgroundColor(0x44000000);

        TextView titleText = new TextView(this);
        titleText.setText(win.title);
        titleText.setTextColor(getThemeColor());
        titleText.setTextSize(14);
        titleText.setTypeface(null, android.graphics.Typeface.BOLD);
        titleText.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        titleBar.addView(titleText);

        ImageButton minBtn = new ImageButton(this);
        minBtn.setImageDrawable(createMinimizeIcon());
        minBtn.setBackgroundColor(Color.TRANSPARENT);
        minBtn.setPadding(6, 4, 6, 4);
        minBtn.setOnClickListener(v -> {
            win.container.setVisibility(View.GONE);
        });
        titleBar.addView(minBtn);

        ImageButton closeBtn = new ImageButton(this);
        closeBtn.setImageDrawable(createCloseIcon());
        closeBtn.setBackgroundColor(Color.TRANSPARENT);
        closeBtn.setPadding(6, 4, 6, 4);
        closeBtn.setOnClickListener(v -> {
            removeWindow(win.type);
        });
        titleBar.addView(closeBtn);

        win.titleBar = titleBar;
        return titleBar;
    }

    // ==================== ПЕРЕТАСКИВАНИЕ ОКНА ====================

    private void setupWindowDragging(final FloatingWindow win) {
        if (win.container == null) return;

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

    // ==================== РЕСАЙЗ ОКНА ====================

    private void setupWindowResizing(final FloatingWindow win) {
        View resizeHandle = new View(this);
        resizeHandle.setBackgroundColor(getThemeColor());
        resizeHandle.setAlpha(0.5f);

        FrameLayout.LayoutParams handleParams = new FrameLayout.LayoutParams(24, 24);
        handleParams.gravity = Gravity.BOTTOM | Gravity.END;
        resizeHandle.setLayoutParams(handleParams);

        win.container.addView(resizeHandle);

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
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== СТИЛИЗОВАННЫЕ КНОПКИ ====================

    private Button createStyledButton(String text, int color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(12);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(12);
        bg.setColor(color);
        bg.setAlpha(200);
        btn.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 3, 0, 3);
        btn.setLayoutParams(lp);

        return btn;
    }

    private Button createStyledIconButton(String text, int color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(11);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(10);
        bg.setColor(color);
        bg.setAlpha(180);
        btn.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(2, 0, 2, 0);
        btn.setLayoutParams(lp);

        return btn;
    }

    // ==================== ИКОНКИ ====================

    private Drawable createCloseIcon() {
        Bitmap b = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(5);
        
        float cx = 20, cy = 20;
        c.drawLine(cx - 12, cy - 12, cx + 12, cy + 12, p);
        c.drawLine(cx + 12, cy - 12, cx - 12, cy + 12, p);
        
        return new android.graphics.drawable.BitmapDrawable(getResources(), b);
    }

    private Drawable createMinimizeIcon() {
        Bitmap b = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(5);
        
        float cx = 20, cy = 20;
        c.drawLine(cx - 12, cy, cx + 12, cy, p);
        
        return new android.graphics.drawable.BitmapDrawable(getResources(), b);
    }

    // ==================== СТАРЫЕ МЕТОДЫ ДЛЯ СОВМЕСТИМОСТИ ====================

    private void hideMainOverlay() {
        try {
            if (mainOverlay != null && windowManager != null && isMainOverlayVisible) {
                windowManager.removeView(mainOverlay);
                mainOverlay = null;
                isMainOverlayVisible = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void hideMacroControl() {
        try {
            if (macroControlContainer != null && windowManager != null) {
                windowManager.removeView(macroControlContainer);
                macroControlContainer = null;
                isMacroModeActive = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void removeCharacter() {
        try {
            if (characterContainer != null && windowManager != null) {
                windowManager.removeView(characterContainer);
                characterContainer = null;
                characterView = null;
                currentCharacterBitmap = null;
                isCharacterModeActive = false;
                isCharacterFixed = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== ЖИЗНЕННЫЙ ЦИКЛ ====================

    @Override
    public void onResume() {
        super.onResume();
        isAppInForeground = true;
        removeMainCircle();
    }

    @Override
    public void onPause() {
        super.onPause();
        isAppInForeground = false;
        hideMainOverlay();
        createMainCircle();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (!isAppInForeground) {
            createMainCircle();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            removeCharacter();
            removeMainCircle();
            hideMainOverlay();
            hideMacroControl();
            for (String key : windows.keySet()) {
                removeWindow(key);
            }
            if (webView != null) {
                webView.loadUrl("about:blank");
                webView.clearHistory();
                webView.clearCache(true);
                webView.removeAllViews();
                webView.destroy();
                webView = null;
            }
            macroHandler.removeCallbacksAndMessages(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            switch (code) {
                case REQUEST_MICROPHONE: Toast.makeText(this, "Микрофон разрешён", Toast.LENGTH_SHORT).show(); break;
                case REQUEST_CAMERA: Toast.makeText(this, "Камера разрешена", Toast.LENGTH_SHORT).show(); break;
                case REQUEST_STORAGE: Toast.makeText(this, "Хранилище разрешено", Toast.LENGTH_SHORT).show(); break;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        try {
            if (requestCode == REQUEST_GALLERY && resultCode == RESULT_OK && data != null) {
                Uri imageUri = data.getData();
                if (imageUri != null) {
                    Bitmap original = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                    String path = saveImageToStorage(original);
                    if (path != null) {
                        characters.add(new CharacterData(tempCharacterName, path));
                        saveCharacters();
                        Toast.makeText(this, "Персонаж сохранён", Toast.LENGTH_SHORT).show();
                        if (windows.containsKey("characters")) {
                            LinearLayout container = null;
                            LinearLayout mainLayout = (LinearLayout) windows.get("characters").contentView;
                            for (int i = 0; i < mainLayout.getChildCount(); i++) {
                                View v = mainLayout.getChildAt(i);
                                if (v instanceof LinearLayout) {
                                    LinearLayout ll = (LinearLayout) v;
                                    if (ll.getChildCount() > 0 && ll.getChildAt(0) instanceof TextView) {
                                        TextView tv = (TextView) ll.getChildAt(0);
                                        if (tv.getText().toString().contains("ПЕРСОНАЖИ")) {
                                            continue;
                                        }
                                    }
                                    if (ll.getChildCount() > 0 && ll.getChildAt(0) instanceof Button) {
                                        continue;
                                    }
                                    container = ll;
                                    break;
                                }
                            }
                            if (container != null) {
                                updateCharactersUI(container);
                            }
                        }
                    }
                }
            }
            
            if (requestCode == REQUEST_OVERLAY_PERMISSION) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (Settings.canDrawOverlays(this)) {
                        if (!isAppInForeground) {
                            createMainCircle();
                        }
                    } else {
                        Toast.makeText(this, "Разрешение на оверлей требуется!", Toast.LENGTH_LONG).show();
                    }
                }
            }
            
            if (requestCode == REQUEST_ACCESSIBILITY) {
                if (isAccessibilityServiceEnabled()) {
                    Toast.makeText(this, "✅ Специальные возможности включены", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "⚠️ Включите специальные возможности для работы макросов", Toast.LENGTH_LONG).show();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка загрузки", Toast.LENGTH_SHORT).show();
        }
    }
              }
