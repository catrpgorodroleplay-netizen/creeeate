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
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
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

import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;

public class MainActivity extends BridgeActivity {

    private static final int REQUEST_MICROPHONE = 100;
    private static final int REQUEST_CAMERA = 102;
    private static final int REQUEST_OVERLAY_PERMISSION = 101;
    private static final int REQUEST_GALLERY = 103;
    private static final int REQUEST_STORAGE = 104;
    private static final int REQUEST_NOTIFICATION = 106;
    private static final int REQUEST_ACCESSIBILITY = 107;
    private static final int REQUEST_MEDIA_PROJECTION = 108;

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

    // Компоненты персонажей
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
    
    private ArrayList<CharacterData> characters = new ArrayList<>();
    private SharedPreferences prefs;
    private String tempCharacterName = "";
    
    private boolean isCharacterListOpen = false;
    private EditText nameInput;
    
    private boolean isWebViewMode = true;
    private FrameLayout contentContainer;
    private LinearLayout charactersGridLayout;
    
    private int overlayAlpha = 255;
    private int overlaySize = 136;

    // ==================== СИСТЕМА МАКРОСОВ ====================
    private boolean isMacroRunning = false;
    private Handler macroHandler = new Handler();
    private int currentMacroIndex = 0;

    // ==================== НОВАЯ СИСТЕМА ЗАПИСИ МАКРОСОВ (MediaProjection) ====================
    private boolean isRecordingMacro = false;
    private ArrayList<RecordedAction> recordedActions = new ArrayList<>();
    private long lastActionTime = 0;
    private MacroService macroService;
    
    // MediaProjection для захвата экрана
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection = null;
    private ScreenCaptureService screenCaptureService;
    private boolean isScreenCaptureStarted = false;
    
    // Кнопки макросов на экране
    private HashMap<String, MacroButton> macroButtons = new HashMap<>();
    private ArrayList<MacroButtonData> macroButtonsData = new ArrayList<>();

    // ==================== ОКНА ====================
    private HashMap<String, FloatingWindow> windows = new HashMap<>();
    private boolean useNewMenu = true;

    // ==================== НАСТРОЙКИ ТЕМ ====================
    private String currentTheme = "dark_red";
    private int primaryColor = 0xFFFF0000;
    private int secondaryColor = 0xFFCC0000;
    private int accentColor = 0xFFFF4444;
    private boolean rainbowMode = false;
    private float rainbowHue = 0;

    // ==================== МАКРОСЫ КОНФИГИ ====================
    private ArrayList<MacroConfig> macroConfigs = new ArrayList<>();
    private String currentMacroName = "Макрос 1";

    // ==================== ВНУТРЕННИЕ КЛАССЫ ====================

    private static class RecordedAction {
        int type;
        int x1, y1, x2, y2;
        long delay;
        
        RecordedAction(int type, int x1, int y1, int x2, int y2, long delay) {
            this.type = type;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.delay = delay;
        }
        
        JSONObject toJSON() throws Exception {
            JSONObject json = new JSONObject();
            json.put("type", type);
            json.put("x1", x1);
            json.put("y1", y1);
            json.put("x2", x2);
            json.put("y2", y2);
            json.put("delay", delay);
            return json;
        }
        
        RecordedAction(JSONObject json) throws Exception {
            type = json.getInt("type");
            x1 = json.getInt("x1");
            y1 = json.getInt("y1");
            x2 = json.optInt("x2", x1);
            y2 = json.optInt("y2", y1);
            delay = json.optLong("delay", 1000);
        }
    }

    private static class MacroConfig {
        String name;
        ArrayList<RecordedAction> recordedActions;
        int color;
        String buttonName;
        int buttonColor;
        int buttonSize;
        int buttonX;
        int buttonY;
        boolean isPinned;
        
        MacroConfig(String name) {
            this.name = name;
            this.recordedActions = new ArrayList<>();
            this.color = 0xFFFF0000;
            this.buttonName = "+c";
            this.buttonColor = 0xFFFF0000;
            this.buttonSize = 80;
            this.buttonX = 100;
            this.buttonY = 200;
            this.isPinned = false;
        }
        
        MacroConfig(JSONObject json) throws Exception {
            this.name = json.getString("name");
            this.color = json.optInt("color", 0xFFFF0000);
            this.buttonName = json.optString("button_name", "+c");
            this.buttonColor = json.optInt("button_color", 0xFFFF0000);
            this.buttonSize = json.optInt("button_size", 80);
            this.buttonX = json.optInt("button_x", 100);
            this.buttonY = json.optInt("button_y", 200);
            this.isPinned = json.optBoolean("is_pinned", false);
            this.recordedActions = new ArrayList<>();
            JSONArray actionsArray = json.optJSONArray("recorded_actions");
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
            json.put("button_name", buttonName);
            json.put("button_color", buttonColor);
            json.put("button_size", buttonSize);
            json.put("button_x", buttonX);
            json.put("button_y", buttonY);
            json.put("is_pinned", isPinned);
            JSONArray actionsArray = new JSONArray();
            for (RecordedAction a : recordedActions) {
                actionsArray.put(a.toJSON());
            }
            json.put("recorded_actions", actionsArray);
            return json;
        }
    }

    private static class MacroButtonData {
        String macroName;
        int x, y;
        int size;
        int color;
        String text;
        
        MacroButtonData(String macroName, int x, int y, int size, int color, String text) {
            this.macroName = macroName;
            this.x = x;
            this.y = y;
            this.size = size;
            this.color = color;
            this.text = text;
        }
    }

    private static class MacroButton {
        FrameLayout container;
        Button button;
        WindowManager.LayoutParams params;
        String macroName;
        boolean isPinned;
    }

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

    private static class FloatingWindow {
        FrameLayout container;
        WindowManager.LayoutParams params;
        View contentView;
        String type;
        boolean isMinimized = false;
        boolean isResizing = false;
        int minWidth = 250;
        int minHeight = 200;
        int lastTouchX, lastTouchY;
        int startWidth, startHeight;
        String title = "Окно";
        ImageButton closeBtn;
        ImageButton minimizeBtn;
        ImageButton maximizeBtn;
        LinearLayout titleBar;
        View resizeHandle;
    }

    // ==================== ЖИЗНЕННЫЙ ЦИКЛ ====================

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            prefs = getSharedPreferences("characters", MODE_PRIVATE);
            overlayAlpha = prefs.getInt("overlay_alpha", 255);
            overlaySize = prefs.getInt("overlay_size", 136);
            useNewMenu = prefs.getBoolean("use_new_menu", true);
            loadCharacters();
            loadMacroConfigs();
            loadSettings();
        } catch (Exception e) {
            e.printStackTrace();
        }

        requestPermissionsIfNeeded();
        requestAccessibilityPermission();

        createWebView();
        macroService = MacroService.getInstance();

        // Инициализируем MediaProjectionManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        }

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
        
        // Загружаем кнопки макросов
        loadMacroButtons();
    }

    @Override
    public void onResume() {
        super.onResume();
        isAppInForeground = true;
        removeMainCircle();
        
        if (isMainOverlayVisible) {
            updateContent();
        }
        
        // Показываем кнопки макросов если есть
        showAllMacroButtons();
    }

    @Override
    public void onPause() {
        super.onPause();
        isAppInForeground = false;
        hideMainOverlay();
        createMainCircle();
        hideAllMacroButtons();
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
            removeMainCircle();
            hideMainOverlay();
            if (webView != null) {
                webView.destroy();
                webView = null;
            }
            macroHandler.removeCallbacksAndMessages(null);
            if (isRecordingMacro && macroService != null) {
                macroService.stopRecordingFromActivity();
            }
            stopScreenCapture();
            hideAllMacroButtons();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== ЗАГРУЗКА НАСТРОЕК ====================

    private void loadSettings() {
        currentTheme = prefs.getString("theme", "dark_red");
        rainbowMode = prefs.getBoolean("rainbow_mode", false);
        primaryColor = prefs.getInt("primary_color", 0xFFFF0000);
        secondaryColor = prefs.getInt("secondary_color", 0xFFCC0000);
        accentColor = prefs.getInt("accent_color", 0xFFFF4444);
    }

    private void saveSettings() {
        prefs.edit()
            .putString("theme", currentTheme)
            .putBoolean("rainbow_mode", rainbowMode)
            .putInt("primary_color", primaryColor)
            .putInt("secondary_color", secondaryColor)
            .putInt("accent_color", accentColor)
            .putBoolean("use_new_menu", useNewMenu)
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

    // ==================== СОХРАНЕНИЕ ПЕРСОНАЖЕЙ ====================
    
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

    // ==================== ГЛАВНЫЙ КРУЖОК ====================

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
                                    showMainMenu();
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

    // ==================== ГЛАВНОЕ МЕНЮ ====================

    private void showMainMenu() {
        try {
            if (windowManager == null) return;

            if (useNewMenu) {
                showWheelMenu();
            } else {
                showOldMenu();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== НОВОЕ МЕНЮ ====================

    private void showWheelMenu() {
        try {
            if (windows.containsKey("wheel_menu")) {
                windows.get("wheel_menu").container.setVisibility(View.VISIBLE);
                return;
            }

            FloatingWindow win = new FloatingWindow();
            win.type = "wheel_menu";
            win.title = "⚡ МЕНЮ";

            FrameLayout mainContainer = new FrameLayout(this);
            mainContainer.setBackgroundColor(0x00000000);

            LinearLayout wheelLayout = new LinearLayout(this);
            wheelLayout.setOrientation(LinearLayout.HORIZONTAL);
            wheelLayout.setGravity(Gravity.CENTER);
            wheelLayout.setPadding(16, 16, 16, 16);

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(40);
            bg.setColor(0xAA0D0D0D);
            bg.setStroke(2, getThemeColor());
            wheelLayout.setBackground(bg);

            String[] items = {"🌐", "🎯", "👤", "🎨", "⚙️"};
            String[] labels = {"Web", "Макр", "Перс", "Тема", "Настр"};
            
            for (int i = 0; i < items.length; i++) {
                LinearLayout itemLayout = new LinearLayout(this);
                itemLayout.setOrientation(LinearLayout.VERTICAL);
                itemLayout.setGravity(Gravity.CENTER);
                itemLayout.setPadding(8, 4, 8, 4);

                Button circleBtn = new Button(this);
                circleBtn.setText(items[i]);
                circleBtn.setTextSize(22);
                circleBtn.setTextColor(Color.WHITE);
                
                GradientDrawable circleBg = new GradientDrawable();
                circleBg.setShape(GradientDrawable.OVAL);
                circleBg.setColor(getThemeColor());
                circleBg.setAlpha(180);
                circleBg.setStroke(2, 0xFFFF4444);
                circleBtn.setBackground(circleBg);
                
                LinearLayout.LayoutParams circleParams = new LinearLayout.LayoutParams(70, 70);
                circleParams.setMargins(4, 0, 4, 0);
                circleBtn.setLayoutParams(circleParams);

                TextView label = new TextView(this);
                label.setText(labels[i]);
                label.setTextColor(0xFFAAAAAA);
                label.setTextSize(9);
                label.setGravity(Gravity.CENTER);

                final int index = i;
                circleBtn.setOnClickListener(v -> {
                    switch (index) {
                        case 0: showWebViewWindow(); break;
                        case 1: showMacrosWindow(); break;
                        case 2: showCharactersWindow(); break;
                        case 3: showThemeSettings(); break;
                        case 4: showSettingsDialog(); break;
                    }
                });

                itemLayout.addView(circleBtn);
                itemLayout.addView(label);
                wheelLayout.addView(itemLayout);
            }

            Button closeBtn = new Button(this);
            closeBtn.setText("✕");
            closeBtn.setTextSize(16);
            closeBtn.setTextColor(0xFFFF4444);
            closeBtn.setBackgroundColor(Color.TRANSPARENT);
            closeBtn.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(40, 40);
            closeParams.setMargins(4, 0, 0, 0);
            closeBtn.setLayoutParams(closeParams);
            closeBtn.setOnClickListener(v -> removeWindow("wheel_menu"));
            wheelLayout.addView(closeBtn);

            mainContainer.addView(wheelLayout);

            Button switchBtn = new Button(this);
            switchBtn.setText("↺");
            switchBtn.setTextSize(14);
            switchBtn.setTextColor(0xFF888888);
            switchBtn.setBackgroundColor(Color.TRANSPARENT);
            switchBtn.setPadding(4, 4, 4, 4);
            FrameLayout.LayoutParams switchParams = new FrameLayout.LayoutParams(40, 40);
            switchParams.gravity = Gravity.BOTTOM | Gravity.END;
            switchParams.setMargins(0, 0, 8, 8);
            switchBtn.setLayoutParams(switchParams);
            switchBtn.setOnClickListener(v -> {
                useNewMenu = false;
                saveSettings();
                removeWindow("wheel_menu");
                showMainMenu();
            });
            mainContainer.addView(switchBtn);

            win.contentView = mainContainer;
            win.container = new FrameLayout(this);
            win.container.addView(mainContainer);

            win.params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            win.params.gravity = Gravity.CENTER;

            wheelLayout.setScaleX(0.7f);
            wheelLayout.setScaleY(0.7f);
            wheelLayout.setAlpha(0f);
            wheelLayout.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(200).start();

            setupWindowDragging(win);

            windowManager.addView(win.container, win.params);
            windows.put("wheel_menu", win);

        } catch (Exception e) {
            e.printStackTrace();
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
                {"⚙️ Настройки", "Дополнительные настройки"}
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
                    }
                });
                mainLayout.addView(btn);
            }

            Button switchBtn = new Button(this);
            switchBtn.setText("🔄 НОВОЕ МЕНЮ");
            switchBtn.setTextColor(0xFF8888FF);
            switchBtn.setTextSize(12);
            switchBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable switchBg = new GradientDrawable();
            switchBg.setCornerRadius(12);
            switchBg.setColor(0x334444FF);
            switchBg.setStroke(1, 0xFF4444FF);
            switchBtn.setBackground(switchBg);
            switchBtn.setPadding(16, 8, 16, 8);
            LinearLayout.LayoutParams switchLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            switchLp.setMargins(0, 8, 0, 0);
            switchBtn.setLayoutParams(switchLp);
            switchBtn.setOnClickListener(v -> {
                useNewMenu = true;
                saveSettings();
                removeWindow("menu");
                showMainMenu();
            });
            mainLayout.addView(switchBtn);

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
                    400, WindowManager.LayoutParams.WRAP_CONTENT,
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
            ws.setBuiltInZoomControls(true);
            ws.setDisplayZoomControls(false);

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

            LinearLayout.LayoutParams webParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
            webView.setLayoutParams(webParams);
            container.addView(webView);

            win.contentView = container;
            win.container = new FrameLayout(this);
            win.container.addView(container);

            GradientDrawable border = new GradientDrawable();
            border.setCornerRadius(12);
            border.setColor(0xFF0A0A0A);
            border.setStroke(3, getThemeColor());
            win.container.setBackground(border);

            win.params = new WindowManager.LayoutParams(
                    550, 450,
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

    // ==================== МАКРОСЫ ОКНО ====================

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
            mainLayout.setPadding(16, 16, 16, 16);
            mainLayout.setBackgroundColor(0xDD0D0D0D);

            LinearLayout titleBar = createTitleBar(win);
            mainLayout.addView(titleBar);

            // Список макросов
            final LinearLayout listContainer = new LinearLayout(this);
            listContainer.setOrientation(LinearLayout.VERTICAL);
            mainLayout.addView(listContainer);

            // Кнопка записи макроса
            Button recordBtn = createStyledButton("🟢 ЗАПИСАТЬ МАКРОС", 0xFF00AA00);
            recordBtn.setOnClickListener(v -> startMacroRecording());
            mainLayout.addView(recordBtn);

            // Кнопка создания кнопки макроса
            Button createBtn = createStyledButton("➕ СОЗДАТЬ КНОПКУ", 0xFFFF8800);
            createBtn.setOnClickListener(v -> showCreateMacroButtonDialog());
            mainLayout.addView(createBtn);

            // Кнопка обновить кнопки
            Button refreshBtn = createStyledButton("🔄 ОБНОВИТЬ КНОПКИ", 0xFF4444FF);
            refreshBtn.setOnClickListener(v -> {
                loadMacroConfigs();
                updateMacrosList(listContainer);
                showAllMacroButtons();
                Toast.makeText(this, "Обновлено!", Toast.LENGTH_SHORT).show();
            });
            mainLayout.addView(refreshBtn);

            win.contentView = mainLayout;
            win.container = new FrameLayout(this);
            win.container.addView(mainLayout);

            GradientDrawable border = new GradientDrawable();
            border.setCornerRadius(16);
            border.setColor(0xDD0D0D0D);
            border.setStroke(3, getThemeColor());
            win.container.setBackground(border);

            win.params = new WindowManager.LayoutParams(
                    450, 500,
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

            updateMacrosList(listContainer);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateMacrosList(LinearLayout container) {
        container.removeAllViews();
        loadMacroConfigs();

        if (macroConfigs.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Нет макросов\nНажмите ЗАПИСАТЬ МАКРОС");
            empty.setTextColor(0xFF888888);
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, 20, 0, 20);
            container.addView(empty);
            return;
        }

        for (int i = 0; i < macroConfigs.size(); i++) {
            MacroConfig config = macroConfigs.get(i);
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(8, 8, 8, 8);
            GradientDrawable itemBg = new GradientDrawable();
            itemBg.setCornerRadius(12);
            itemBg.setColor(0x22FFFFFF);
            itemBg.setStroke(2, config.color);
            item.setBackground(itemBg);

            TextView nameText = new TextView(this);
            String info = config.name + " (" + config.recordedActions.size() + " действий)";
            if (config.isPinned) info += " 📌";
            nameText.setText(info);
            nameText.setTextColor(Color.WHITE);
            nameText.setTextSize(13);
            nameText.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            item.addView(nameText);

            Button runBtn = new Button(this);
            runBtn.setText("▶");
            runBtn.setTextColor(0xFF00FF00);
            runBtn.setTextSize(16);
            runBtn.setBackgroundColor(0x3300FF00);
            runBtn.setPadding(8, 4, 8, 4);
            runBtn.setOnClickListener(v -> runMacro(config.name));
            item.addView(runBtn);

            Button delBtn = new Button(this);
            delBtn.setText("✕");
            delBtn.setTextColor(0xFFFF0000);
            delBtn.setTextSize(16);
            delBtn.setBackgroundColor(0x33FF0000);
            delBtn.setPadding(8, 4, 8, 4);
            final int idx = i;
            delBtn.setOnClickListener(v -> {
                macroConfigs.remove(idx);
                saveMacroConfigs();
                updateMacrosList(container);
                hideAllMacroButtons();
                showAllMacroButtons();
                Toast.makeText(this, "Макрос удалён", Toast.LENGTH_SHORT).show();
            });
            item.addView(delBtn);

            container.addView(item);
        }
    }

    // ==================== ЗАПИСЬ МАКРОСА (MediaProjection) ====================

    private void startMacroRecording() {
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Включите специальные возможности", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }

        // Проверяем разрешение на захват экрана
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (mediaProjectionManager != null) {
                try {
                    Intent intent = mediaProjectionManager.createScreenCaptureIntent();
                    startActivityForResult(intent, REQUEST_MEDIA_PROJECTION);
                    return;
                } catch (Exception e) {
                    Log.e("CR_ARCADE", "Ошибка создания intent: " + e.getMessage());
                    Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "MediaProjectionManager не доступен", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "Android 5.0+ требуется для захвата экрана", Toast.LENGTH_LONG).show();
        }
    }

    private void beginMacroRecording() {
        // Создаем новый макрос
        MacroConfig newMacro = new MacroConfig("Макрос " + (macroConfigs.size() + 1));
        macroConfigs.add(newMacro);
        currentMacroName = newMacro.name;
        saveMacroConfigs();

        // Начинаем запись
        isRecordingMacro = true;
        recordedActions.clear();
        lastActionTime = System.currentTimeMillis();

        // Запускаем сервис захвата экрана
        if (mediaProjection != null) {
            try {
                // Запускаем сервис
                Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
                
                // Создаем слушатель
                ScreenCaptureService.ScreenCaptureListener listener = new ScreenCaptureService.ScreenCaptureListener() {
                    @Override
                    public void onScreenCaptured(Bitmap bitmap) {
                        // Не используем битмап напрямую
                    }

                    @Override
                    public void onTouchDetected(int x, int y) {
                        if (isRecordingMacro) {
                            long currentTime = System.currentTimeMillis();
                            long delay = currentTime - lastActionTime;
                            lastActionTime = currentTime;
                            
                            RecordedAction action = new RecordedAction(0, x, y, x, y, delay);
                            recordedActions.add(action);
                            
                            runOnUiThread(() -> {
                                Toast.makeText(MainActivity.this, 
                                    "Клик #" + recordedActions.size() + " (" + x + "," + y + ")", 
                                    Toast.LENGTH_SHORT).show();
                                updateMacroUI();
                            });
                            
                            Log.d("CR_ARCADE", "✅ КЛИК ЗАПИСАН: (" + x + ", " + y + ")");
                        }
                    }
                };
                
                // Используем привязку к сервису
                screenCaptureService = new ScreenCaptureService();
                screenCaptureService.startCapture(mediaProjection, listener);
                
                isScreenCaptureStarted = true;
                Toast.makeText(this, "🔴 Запись начата! Кликайте по экрану", Toast.LENGTH_LONG).show();
                
            } catch (Exception e) {
                Log.e("CR_ARCADE", "Ошибка: " + e.getMessage());
                Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_LONG).show();
                isRecordingMacro = false;
            }
        } else {
            Toast.makeText(this, "Ошибка: mediaProjection == null", Toast.LENGTH_LONG).show();
            isRecordingMacro = false;
        }
    }

    private void updateMacroUI() {
        // Если окно макросов открыто - обновляем список
        if (windows.containsKey("macros")) {
            LinearLayout container = null;
            LinearLayout layout = (LinearLayout) windows.get("macros").contentView;
            for (int i = 0; i < layout.getChildCount(); i++) {
                View v = layout.getChildAt(i);
                if (v instanceof LinearLayout) {
                    LinearLayout ll = (LinearLayout) v;
                    if (ll.getChildCount() > 0 && ll.getChildAt(0) instanceof TextView) {
                        TextView tv = (TextView) ll.getChildAt(0);
                        if (tv.getText().toString().contains("МАКРОСЫ")) continue;
                    }
                    if (ll.getChildCount() > 0 && ll.getChildAt(0) instanceof Button) continue;
                    container = ll;
                    break;
                }
            }
            if (container != null) updateMacrosList(container);
        }
    }

    private void stopScreenCapture() {
        if (isScreenCaptureStarted) {
            try {
                if (screenCaptureService != null) {
                    screenCaptureService.stopCapture();
                    screenCaptureService = null;
                }
                Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
                stopService(serviceIntent);
            } catch (Exception e) {
                e.printStackTrace();
            }
            isScreenCaptureStarted = false;
        }
        
        if (mediaProjection != null) {
            try {
                mediaProjection.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mediaProjection = null;
        }
    }

    private void finishMacroRecording() {
        isRecordingMacro = false;
        stopScreenCapture();
        
        // Сохраняем макрос
        MacroConfig config = getCurrentMacro();
        if (config != null && !recordedActions.isEmpty()) {
            config.recordedActions.addAll(recordedActions);
            saveMacroConfigs();
            Toast.makeText(this, "✅ Записано " + recordedActions.size() + " действий!", Toast.LENGTH_LONG).show();
        } else {
            // Если ничего не записано - удаляем пустой макрос
            if (config != null) {
                macroConfigs.remove(config);
                saveMacroConfigs();
            }
            Toast.makeText(this, "❌ Ничего не записано", Toast.LENGTH_SHORT).show();
        }
        
        recordedActions.clear();
        showAllMacroButtons();
        
        // Обновляем список в окне
        if (windows.containsKey("macros")) {
            LinearLayout container = null;
            LinearLayout mainLayout = (LinearLayout) windows.get("macros").contentView;
            for (int i = 0; i < mainLayout.getChildCount(); i++) {
                View v = mainLayout.getChildAt(i);
                if (v instanceof LinearLayout) {
                    LinearLayout ll = (LinearLayout) v;
                    if (ll.getChildCount() > 0 && ll.getChildAt(0) instanceof TextView) {
                        TextView tv = (TextView) ll.getChildAt(0);
                        if (tv.getText().toString().contains("МАКРОСЫ")) continue;
                    }
                    if (ll.getChildCount() > 0 && ll.getChildAt(0) instanceof Button) continue;
                    container = ll;
                    break;
                }
            }
            if (container != null) updateMacrosList(container);
        }
    }

    // ==================== КНОПКИ МАКРОСОВ НА ЭКРАНЕ ====================

    private void showCreateMacroButtonDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Создать кнопку макроса");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        // Выбор макроса
        final Spinner macroSpinner = new Spinner(this);
        ArrayList<String> macroNames = new ArrayList<>();
        for (MacroConfig config : macroConfigs) {
            if (!config.recordedActions.isEmpty()) {
                macroNames.add(config.name);
            }
        }
        if (macroNames.isEmpty()) {
            Toast.makeText(this, "Нет макросов с действиями", Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, macroNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        macroSpinner.setAdapter(adapter);
        layout.addView(macroSpinner);

        // Название кнопки
        final EditText nameInput = new EditText(this);
        nameInput.setHint("Название кнопки");
        nameInput.setText("+c");
        layout.addView(nameInput);

        // Выбор цвета
        final LinearLayout colorLayout = new LinearLayout(this);
        colorLayout.setOrientation(LinearLayout.HORIZONTAL);
        colorLayout.setGravity(Gravity.CENTER);
        
        TextView colorLabel = new TextView(this);
        colorLabel.setText("Цвет: ");
        colorLabel.setTextColor(Color.WHITE);
        colorLayout.addView(colorLabel);
        
        int[] colors = {0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFF00, 0xFFFF00FF, 0xFF00FFFF, 0xFFFF8800, 0xFFFF44FF};
        final int[] selectedColor = {colors[0]};
        
        for (int c : colors) {
            Button colorBtn = new Button(this);
            colorBtn.setText("  ");
            colorBtn.setBackgroundColor(c);
            colorBtn.setPadding(4, 4, 4, 4);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(40, 40);
            cp.setMargins(4, 0, 4, 0);
            colorBtn.setLayoutParams(cp);
            final int colorVal = c;
            colorBtn.setOnClickListener(v -> {
                selectedColor[0] = colorVal;
                Toast.makeText(this, "Цвет выбран", Toast.LENGTH_SHORT).show();
            });
            colorLayout.addView(colorBtn);
        }
        layout.addView(colorLayout);

        // Размер
        LinearLayout sizeLayout = new LinearLayout(this);
        sizeLayout.setOrientation(LinearLayout.HORIZONTAL);
        sizeLayout.setGravity(Gravity.CENTER);
        
        TextView sizeLabel = new TextView(this);
        sizeLabel.setText("Размер: 80");
        sizeLabel.setTextColor(Color.WHITE);
        sizeLayout.addView(sizeLabel);
        
        SeekBar sizeSeek = new SeekBar(this);
        sizeSeek.setMax(150);
        sizeSeek.setMin(40);
        sizeSeek.setProgress(80);
        sizeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                sizeLabel.setText("Размер: " + progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        sizeLayout.addView(sizeSeek);
        layout.addView(sizeLayout);

        builder.setView(layout);
        builder.setPositiveButton("Создать", (d, w) -> {
            String macroName = macroSpinner.getSelectedItem().toString();
            String buttonName = nameInput.getText().toString().trim();
            if (buttonName.isEmpty()) buttonName = "+c";
            int size = sizeSeek.getProgress();
            
            for (MacroConfig config : macroConfigs) {
                if (config.name.equals(macroName)) {
                    config.buttonName = buttonName;
                    config.buttonColor = selectedColor[0];
                    config.buttonSize = size;
                    config.isPinned = true;
                    config.buttonX = 100 + new Random().nextInt(200);
                    config.buttonY = 200 + new Random().nextInt(300);
                    saveMacroConfigs();
                    showAllMacroButtons();
                    Toast.makeText(this, "Кнопка создана!", Toast.LENGTH_SHORT).show();
                    break;
                }
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void showAllMacroButtons() {
        hideAllMacroButtons();
        
        if (!isAppInForeground) return;
        if (windowManager == null) return;
        if (isRecordingMacro) return;
        
        loadMacroConfigs();
        
        for (MacroConfig config : macroConfigs) {
            if (config.isPinned && !config.recordedActions.isEmpty()) {
                createMacroButton(config);
            }
        }
    }

    private void createMacroButton(MacroConfig config) {
        try {
            if (windowManager == null) return;
            if (macroButtons.containsKey(config.name)) return;
            
            MacroButton mb = new MacroButton();
            mb.macroName = config.name;
            mb.isPinned = config.isPinned;
            
            mb.container = new FrameLayout(this);
            
            mb.button = new Button(this);
            mb.button.setText(config.buttonName);
            mb.button.setTextColor(Color.WHITE);
            mb.button.setTextSize(14);
            mb.button.setTypeface(null, android.graphics.Typeface.BOLD);
            
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(config.buttonColor);
            bg.setStroke(3, Color.WHITE);
            mb.button.setBackground(bg);
            mb.button.setPadding(8, 8, 8, 8);
            
            mb.button.setOnClickListener(v -> {
                runMacro(config.name);
            });
            
            mb.button.setOnLongClickListener(v -> {
                showDeleteMacroButtonDialog(config.name);
                return true;
            });
            
            mb.button.setOnTouchListener(new View.OnTouchListener() {
                float startX, startY;
                int initX, initY;
                boolean dragging;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            startX = event.getRawX();
                            startY = event.getRawY();
                            initX = mb.params.x;
                            initY = mb.params.y;
                            dragging = false;
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            float dx = event.getRawX() - startX;
                            float dy = event.getRawY() - startY;
                            if (Math.abs(dx) > 10 || Math.abs(dy) > 10) dragging = true;
                            if (dragging) {
                                mb.params.x = initX + (int) dx;
                                mb.params.y = initY + (int) dy;
                                if (windowManager != null) {
                                    windowManager.updateViewLayout(mb.container, mb.params);
                                    for (MacroConfig c : macroConfigs) {
                                        if (c.name.equals(config.name)) {
                                            c.buttonX = mb.params.x;
                                            c.buttonY = mb.params.y;
                                            saveMacroConfigs();
                                            break;
                                        }
                                    }
                                }
                            }
                            return true;
                        case MotionEvent.ACTION_UP:
                            return dragging;
                    }
                    return false;
                }
            });
            
            FrameLayout.LayoutParams btnParams = new FrameLayout.LayoutParams(
                    config.buttonSize, config.buttonSize);
            btnParams.gravity = Gravity.CENTER;
            mb.container.addView(mb.button, btnParams);
            
            int flag = getOverlayFlag();
            
            mb.params = new WindowManager.LayoutParams(
                    config.buttonSize + 20,
                    config.buttonSize + 20,
                    flag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            mb.params.gravity = Gravity.TOP | Gravity.START;
            mb.params.x = config.buttonX;
            mb.params.y = config.buttonY;
            
            windowManager.addView(mb.container, mb.params);
            macroButtons.put(config.name, mb);
            
            Log.d("CR_ARCADE", "✅ Кнопка создана: " + config.name);
            
        } catch (Exception e) {
            Log.e("CR_ARCADE", "❌ Ошибка создания кнопки: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showDeleteMacroButtonDialog(final String macroName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Удалить кнопку?");
        builder.setMessage("Удалить кнопку макроса \"" + macroName + "\"?\n(макрос сохранится)");
        builder.setPositiveButton("Удалить", (d, w) -> {
            for (MacroConfig config : macroConfigs) {
                if (config.name.equals(macroName)) {
                    config.isPinned = false;
                    saveMacroConfigs();
                    break;
                }
            }
            hideAllMacroButtons();
            showAllMacroButtons();
            Toast.makeText(this, "Кнопка удалена", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void hideAllMacroButtons() {
        try {
            for (MacroButton mb : macroButtons.values()) {
                if (mb.container != null && windowManager != null) {
                    windowManager.removeView(mb.container);
                }
            }
            macroButtons.clear();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== ЗАПУСК МАКРОСА ====================

    private void runMacro(String macroName) {
        MacroConfig config = null;
        for (MacroConfig c : macroConfigs) {
            if (c.name.equals(macroName)) {
                config = c;
                break;
            }
        }
        
        if (config == null || config.recordedActions.isEmpty()) {
            Toast.makeText(this, "Нет действий в макросе", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Включите специальные возможности", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }

        if (isMacroRunning) {
            stopMacroExecution();
            return;
        }

        isMacroRunning = true;
        currentMacroIndex = 0;
        Toast.makeText(this, "▶ Макрос запущен: " + config.name, Toast.LENGTH_SHORT).show();
        
        executeMacroActions(config);
    }

    private void executeMacroActions(MacroConfig config) {
        if (!isMacroRunning || currentMacroIndex >= config.recordedActions.size()) {
            stopMacroExecution();
            return;
        }

        RecordedAction action = config.recordedActions.get(currentMacroIndex);
        
        if (macroService != null && action.type == 0) {
            macroService.performClick(action.x1, action.y1);
            Log.d("CR_ARCADE", "▶ Клик #" + (currentMacroIndex+1) + ": (" + action.x1 + ", " + action.y1 + ")");
        }
        
        currentMacroIndex++;
        
        macroHandler.postDelayed(() -> {
            executeMacroActions(config);
        }, action.delay > 0 ? action.delay : 100);
    }

    private void stopMacroExecution() {
        isMacroRunning = false;
        macroHandler.removeCallbacksAndMessages(null);
        Toast.makeText(this, "⏹ Макрос остановлен", Toast.LENGTH_SHORT).show();
    }

    // ==================== ПЕРСОНАЖИ ====================

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
            mainLayout.setPadding(16, 16, 16, 16);
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
                    450, 500,
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
            GradientDrawable itemBg = new GradientDrawable();
            itemBg.setCornerRadius(12);
            itemBg.setColor(0x22FFFFFF);
            itemBg.setStroke(2, getThemeColor());
            item.setBackground(itemBg);

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
        c.drawText("👤", 30, 42, p);
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
        layout.setPadding(20, 20, 20, 20);

        LinearLayout themeButtons = new LinearLayout(this);
        themeButtons.setOrientation(LinearLayout.HORIZONTAL);
        themeButtons.setGravity(Gravity.CENTER);

        int[] colors = {0xFFFF0000, 0xFFCC0000, 0xFFFF8800, 0xFFFF00FF, 0xFFFF0000};
        String[] names = {"Красный", "Тёмный", "Оранж", "Розовый", "Радуга"};

        for (int i = 0; i < names.length; i++) {
            Button btn = new Button(this);
            btn.setText(names[i]);
            btn.setTextColor(Color.WHITE);
            btn.setTextSize(12);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(12);
            bg.setColor(colors[i]);
            btn.setBackground(bg);
            btn.setPadding(12, 8, 12, 8);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            lp.setMargins(4, 0, 4, 0);
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

        LinearLayout customLayout = new LinearLayout(this);
        customLayout.setOrientation(LinearLayout.HORIZONTAL);
        customLayout.setGravity(Gravity.CENTER);
        customLayout.setPadding(0, 16, 0, 0);

        TextView customLabel = new TextView(this);
        customLabel.setText("Свой цвет: ");
        customLabel.setTextColor(Color.WHITE);
        customLayout.addView(customLabel);

        Button colorPickerBtn = new Button(this);
        colorPickerBtn.setText("🎨 Выбрать");
        colorPickerBtn.setTextColor(Color.WHITE);
        colorPickerBtn.setBackgroundColor(0xFF444444);
        colorPickerBtn.setPadding(16, 8, 16, 8);
        colorPickerBtn.setOnClickListener(v -> {
            AlertDialog.Builder colorDialog = new AlertDialog.Builder(this);
            colorDialog.setTitle("Выберите цвет");
            final EditText hexInput = new EditText(this);
            hexInput.setHint("HEX код (например #FF0000)");
            hexInput.setText("#" + Integer.toHexString(primaryColor).substring(2));
            colorDialog.setView(hexInput);
            colorDialog.setPositiveButton("Применить", (d, w) -> {
                try {
                    String hex = hexInput.getText().toString().trim();
                    if (hex.startsWith("#")) hex = hex.substring(1);
                    int colorVal = Color.parseColor("#" + hex);
                    primaryColor = colorVal;
                    secondaryColor = colorVal;
                    accentColor = colorVal;
                    rainbowMode = false;
                    saveSettings();
                    applyTheme();
                    Toast.makeText(this, "Цвет применён!", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Ошибка! Введите HEX", Toast.LENGTH_SHORT).show();
                }
            });
            colorDialog.setNegativeButton("Отмена", null);
            colorDialog.show();
        });
        customLayout.addView(colorPickerBtn);

        layout.addView(customLayout);

        builder.setView(layout);
        builder.setPositiveButton("Закрыть", null);
        builder.show();
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⚙️ Настройки");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

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

        Button menuToggle = new Button(this);
        String menuText = useNewMenu ? "🔄 Переключить на СТАРОЕ меню" : "🔄 Переключить на НОВОЕ меню";
        menuToggle.setText(menuText);
        menuToggle.setTextColor(0xFFFF8800);
        menuToggle.setBackgroundColor(0x33444444);
        menuToggle.setPadding(16, 12, 16, 12);
        menuToggle.setOnClickListener(v -> {
            useNewMenu = !useNewMenu;
            saveSettings();
            Toast.makeText(this, useNewMenu ? "Новое меню" : "Старое меню", Toast.LENGTH_SHORT).show();
            for (String key : windows.keySet()) {
                removeWindow(key);
            }
        });
        layout.addView(menuToggle);

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
        if (mainOverlay != null) {
            hideMainOverlay();
            showMainMenu();
        }
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
        titleBar.setPadding(12, 8, 12, 8);
        titleBar.setBackgroundColor(0x44000000);

        TextView titleText = new TextView(this);
        titleText.setText(win.title);
        titleText.setTextColor(getThemeColor());
        titleText.setTextSize(16);
        titleText.setTypeface(null, android.graphics.Typeface.BOLD);
        titleText.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        titleBar.addView(titleText);

        ImageButton minBtn = new ImageButton(this);
        minBtn.setImageDrawable(createMinimizeIcon());
        minBtn.setBackgroundColor(Color.TRANSPARENT);
        minBtn.setPadding(8, 4, 8, 4);
        minBtn.setOnClickListener(v -> {
            win.container.setVisibility(View.GONE);
        });
        titleBar.addView(minBtn);

        ImageButton closeBtn = new ImageButton(this);
        closeBtn.setImageDrawable(createCloseIcon());
        closeBtn.setBackgroundColor(Color.TRANSPARENT);
        closeBtn.setPadding(8, 4, 8, 4);
        closeBtn.setOnClickListener(v -> {
            removeWindow(win.type);
        });
        titleBar.addView(closeBtn);

        win.titleBar = titleBar;
        return titleBar;
    }

    private Drawable createCloseIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(8);
            
            float cx = 40, cy = 40;
            c.drawLine(cx - 25, cy - 25, cx + 25, cy + 25, p);
            c.drawLine(cx + 25, cy - 25, cx - 25, cy + 25, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Drawable createMinimizeIcon() {
        Bitmap b = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(5);
        float cx = 30, cy = 30;
        c.drawRect(cx - 15, cy - 15, cx + 15, cy + 15, p);
        c.drawLine(cx - 10, cy, cx + 10, cy, p);
        return new android.graphics.drawable.BitmapDrawable(getResources(), b);
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

        FrameLayout.LayoutParams handleParams = new FrameLayout.LayoutParams(30, 30);
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
        lp.setMargins(0, 4, 0, 4);
        btn.setLayoutParams(lp);

        return btn;
    }

    // ==================== СТАРЫЕ МЕТОДЫ ====================

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

    private void updateContent() {
        // Заглушка
    }

    private void loadMacroButtons() {
        // Загружаем сохраненные кнопки
        showAllMacroButtons();
    }

    private void startMacroExecution() {
        MacroConfig config = getCurrentMacro();
        if (config != null && !config.recordedActions.isEmpty()) {
            runMacro(config.name);
        } else {
            Toast.makeText(this, "Нет макроса для выполнения", Toast.LENGTH_SHORT).show();
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
            // Обработка разрешения на захват экрана
            if (requestCode == REQUEST_MEDIA_PROJECTION) {
                if (resultCode == RESULT_OK && data != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
                        if (mediaProjection != null) {
                            Toast.makeText(this, "✅ Разрешение на захват экрана получено!", Toast.LENGTH_SHORT).show();
                            beginMacroRecording();
                            return;
                        }
                    }
                } else {
                    Toast.makeText(this, "❌ Разрешение на захват экрана не получено", Toast.LENGTH_LONG).show();
                    isRecordingMacro = false;
                }
                return;
            }
            
            // Обработка выбора фото для персонажа
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
                return;
            }
            
            // Обработка разрешения на оверлей
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
                return;
            }
            
            // Обработка разрешения на специальные возможности
            if (requestCode == REQUEST_ACCESSIBILITY) {
                if (isAccessibilityServiceEnabled()) {
                    Toast.makeText(this, "✅ Специальные возможности включены", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "⚠️ Включите специальные возможности для работы макросов", Toast.LENGTH_LONG).show();
                }
                return;
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
              }
