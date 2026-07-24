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

    // ==================== НОВЫЕ ОКНА ====================
    private HashMap<String, FloatingWindow> windows = new HashMap<>();

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
        int color;
        
        MacroConfig(String name) {
            this.name = name;
            this.points = new ArrayList<>();
            this.color = 0xFFFF0000;
        }
        
        MacroConfig(JSONObject json) throws Exception {
            this.name = json.getString("name");
            this.color = json.optInt("color", 0xFFFF0000);
            this.points = new ArrayList<>();
            JSONArray pointsArray = json.getJSONArray("points");
            for (int i = 0; i < pointsArray.length(); i++) {
                this.points.add(new MacroPoint(pointsArray.getJSONObject(i)));
            }
        }
        
        JSONObject toJSON() throws Exception {
            JSONObject json = new JSONObject();
            json.put("name", name);
            json.put("color", color);
            JSONArray pointsArray = new JSONArray();
            for (MacroPoint p : points) {
                pointsArray.put(p.toJSON());
            }
            json.put("points", pointsArray);
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

    // ==================== КЛАСС ДАННЫХ ПЕРСОНАЖА (СТАРЫЙ) ====================
    
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
            loadCharacters();
            loadMacroPoints();
            loadMacroConfigs();
            loadSettings();
        } catch (Exception e) {
            e.printStackTrace();
        }

        requestPermissionsIfNeeded();
        requestAccessibilityPermission();

        // Создаем WebView сразу при запуске
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
    }

    private void saveSettings() {
        prefs.edit()
            .putString("theme", currentTheme)
            .putBoolean("rainbow_mode", rainbowMode)
            .putInt("primary_color", primaryColor)
            .putInt("secondary_color", secondaryColor)
            .putInt("accent_color", accentColor)
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

    // ==================== СОЗДАНИЕ WEBVIEW (СТАРЫЙ) ====================

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

    // ==================== ИКОНКИ (СТАРЫЕ) ====================

    private Drawable createHomeIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(8);
            
            float cx = 60, cy = 60;
            c.drawLine(cx - 40, cy + 10, cx, cy - 30, p);
            c.drawLine(cx + 40, cy + 10, cx, cy - 30, p);
            c.drawLine(cx - 40, cy + 10, cx - 40, cy + 40, p);
            c.drawLine(cx + 40, cy + 10, cx + 40, cy + 40, p);
            c.drawLine(cx - 40, cy + 40, cx + 40, cy + 40, p);
            c.drawLine(cx - 15, cy + 40, cx - 15, cy + 15, p);
            c.drawLine(cx + 15, cy + 40, cx + 15, cy + 15, p);
            c.drawLine(cx - 15, cy + 15, cx + 15, cy + 15, p);
            c.drawLine(cx + 8, cy - 24, cx + 8, cy - 40, p);
            c.drawLine(cx + 8, cy - 40, cx + 24, cy - 40, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // СТАРЫЙ createCloseIcon - ОСТАВЛЯЕМ ТОЛЬКО ЭТОТ
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

    private Drawable createCharacterIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(7);
            
            float cx = 60, cy = 60;
            c.drawCircle(cx, cy - 15, 18, p);
            c.drawLine(cx, cy + 5, cx, cy + 35, p);
            c.drawLine(cx, cy + 12, cx - 25, cy + 0, p);
            c.drawLine(cx, cy + 12, cx + 25, cy + 0, p);
            c.drawLine(cx, cy + 35, cx - 20, cy + 50, p);
            c.drawLine(cx, cy + 35, cx + 20, cy + 50, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Drawable createSettingsIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(7);
            
            float cx = 60, cy = 60;
            c.drawCircle(cx, cy, 20, p);
            c.drawLine(cx - 12, cy - 30, cx - 12, cy - 20, p);
            c.drawLine(cx + 12, cy - 30, cx + 12, cy - 20, p);
            c.drawLine(cx - 12, cy + 20, cx - 12, cy + 30, p);
            c.drawLine(cx + 12, cy + 20, cx + 12, cy + 30, p);
            c.drawLine(cx - 30, cy - 12, cx - 20, cy - 12, p);
            c.drawLine(cx - 30, cy + 12, cx - 20, cy + 12, p);
            c.drawLine(cx + 20, cy - 12, cx + 30, cy - 12, p);
            c.drawLine(cx + 20, cy + 12, cx + 30, cy + 12, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Drawable createExitIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(8);
            
            float cx = 60, cy = 60;
            c.drawRect(cx - 25, cy - 30, cx + 25, cy + 30, p);
            c.drawCircle(cx + 10, cy, 6, p);
            c.drawLine(cx + 25, cy - 12, cx + 42, cy - 12, p);
            c.drawLine(cx + 25, cy + 12, cx + 42, cy + 12, p);
            c.drawLine(cx + 42, cy - 12, cx + 42, cy + 12, p);
            c.drawLine(cx - 25, cy, cx - 10, cy, p);
            c.drawLine(cx - 16, cy - 8, cx - 10, cy, p);
            c.drawLine(cx - 16, cy + 8, cx - 10, cy, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Drawable createHideIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(7);
            
            float cx = 60, cy = 60;
            c.drawOval(cx - 30, cy - 20, cx + 30, cy + 20, p);
            c.drawCircle(cx, cy, 10, p);
            p.setStrokeWidth(8);
            c.drawLine(cx - 25, cy - 15, cx + 25, cy + 15, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Drawable createDeleteIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(6);
            
            float cx = 30, cy = 30;
            c.drawLine(cx - 18, cy - 15, cx + 18, cy - 15, p);
            c.drawLine(cx - 10, cy - 22, cx + 10, cy - 22, p);
            c.drawLine(cx - 18, cy - 15, cx - 18, cy + 15, p);
            c.drawLine(cx + 18, cy - 15, cx + 18, cy + 15, p);
            c.drawArc(cx - 14, cy - 26, cx + 14, cy - 10, 0, 180, false, p);
            p.setStrokeWidth(4);
            c.drawLine(cx - 8, cy, cx + 8, cy, p);
            c.drawLine(cx, cy - 8, cx, cy + 8, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Drawable createScreenIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(6);
            
            float cx = 30, cy = 30;
            c.drawRect(cx - 20, cy - 15, cx + 20, cy + 12, p);
            c.drawLine(cx - 20, cy + 18, cx + 20, cy + 18, p);
            c.drawLine(cx, cy + 18, cx, cy + 25, p);
            c.drawLine(cx - 8, cy + 25, cx + 8, cy + 25, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Drawable createFloatIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(7);
            
            float cx = 30, cy = 30;
            c.drawOval(cx - 15, cy - 15, cx + 15, cy + 15, p);
            c.drawCircle(cx + 6, cy - 6, 4, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Drawable createAddIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(5);
            
            float cx = 30, cy = 30;
            c.drawLine(cx - 15, cy, cx + 15, cy, p);
            c.drawLine(cx, cy - 15, cx, cy + 15, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Drawable createLockIcon(boolean locked) {
        try {
            Bitmap b = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(5);
            
            float cx = 30, cy = 30;
            c.drawArc(cx - 18, cy - 26, cx + 18, cy - 4, 0, 180, false, p);
            c.drawRect(cx - 15, cy - 8, cx + 15, cy + 18, p);
            p.setStyle(Paint.Style.FILL);
            p.setStrokeWidth(0);
            c.drawCircle(cx, cy + 6, 4, p);
            
            if (locked) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(6);
                p.setColor(Color.YELLOW);
                c.drawLine(cx - 22, cy - 15, cx + 22, cy + 22, p);
                c.drawLine(cx + 22, cy - 15, cx - 22, cy + 22, p);
            }
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ==================== ИКОНКА МАКРОСОВ (НОВАЯ - ВЕКТОРНАЯ) ====================
    
    private Drawable createMacroIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(6);
            
            float cx = 60, cy = 60;
            
            // Основной круг
            c.drawCircle(cx, cy, 35, p);
            
            // Три точки внутри
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(cx - 20, cy - 15, 6, p);
            c.drawCircle(cx + 15, cy - 15, 6, p);
            c.drawCircle(cx, cy + 20, 6, p);
            
            // Соединительные линии
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(4);
            c.drawLine(cx - 14, cy - 12, cx + 9, cy - 12, p);
            c.drawLine(cx - 6, cy + 14, cx + 9, cy - 12, p);
            c.drawLine(cx - 14, cy - 12, cx - 6, cy + 14, p);
            
            // Внешние стрелки
            p.setStrokeWidth(5);
            // Стрелка вверх
            c.drawLine(cx - 45, cy - 25, cx - 35, cy - 25, p);
            c.drawLine(cx - 40, cy - 32, cx - 35, cy - 25, p);
            c.drawLine(cx - 40, cy - 18, cx - 35, cy - 25, p);
            
            // Стрелка вниз
            c.drawLine(cx + 35, cy + 30, cx + 45, cy + 30, p);
            c.drawLine(cx + 38, cy + 23, cx + 45, cy + 30, p);
            c.drawLine(cx + 38, cy + 37, cx + 45, cy + 30, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ==================== НОРМАЛЬНЫЕ ИКОНКИ ДЛЯ МАКРОСОВ ====================
    
    private Drawable createAddPointIcon() {
        Bitmap b = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(5);
        
        float cx = 30, cy = 30;
        c.drawCircle(cx, cy, 20, p);
        c.drawLine(cx - 10, cy, cx + 10, cy, p);
        c.drawLine(cx, cy - 10, cx, cy + 10, p);
        
        return new android.graphics.drawable.BitmapDrawable(getResources(), b);
    }
    
    private Drawable createPlayIcon() {
        Bitmap b = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.FILL);
        
        float cx = 30, cy = 30;
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(cx - 10, cy - 15);
        path.lineTo(cx - 10, cy + 15);
        path.lineTo(cx + 15, cy);
        path.close();
        c.drawPath(path, p);
        
        return new android.graphics.drawable.BitmapDrawable(getResources(), b);
    }
    
    private Drawable createStopIcon() {
        Bitmap b = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.FILL);
        
        float cx = 30, cy = 30;
        c.drawRect(cx - 15, cy - 15, cx + 15, cy + 15, p);
        
        return new android.graphics.drawable.BitmapDrawable(getResources(), b);
    }
    
    private Drawable createClearIcon() {
        Bitmap b = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(5);
        
        float cx = 30, cy = 30;
        c.drawLine(cx - 15, cy - 15, cx + 15, cy + 15, p);
        c.drawLine(cx + 15, cy - 15, cx - 15, cy + 15, p);
        
        return new android.graphics.drawable.BitmapDrawable(getResources(), b);
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

    // ==================== ГЛАВНОЕ МЕНЮ (ОКНО) ====================

    private void showMainMenu() {
        try {
            if (windowManager == null) return;
            
            // Если уже есть, показываем
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

            // Заголовок
            TextView title = new TextView(this);
            title.setText("⚡ CR ARCADE");
            title.setTextColor(getThemeColor());
            title.setTextSize(24);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, 0, 0, 16);
            mainLayout.addView(title);

            // Кнопки меню
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

            // Кнопка закрытия
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

            // Заголовок
            LinearLayout titleBar = createTitleBar(win);
            container.addView(titleBar);

            // Создаем WebView
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
            webContainer.addView(webView);
            webContainer.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
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
                    600, 500,
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
            mainLayout.setPadding(16, 16, 16, 16);
            mainLayout.setBackgroundColor(0xDD0D0D0D);

            // Заголовок
            LinearLayout titleBar = createTitleBar(win);
            mainLayout.addView(titleBar);

            // Список персонажей
            final LinearLayout listContainer = new LinearLayout(this);
            listContainer.setOrientation(LinearLayout.VERTICAL);
            mainLayout.addView(listContainer);

            // Кнопка добавления
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

            // Иконка
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

            // Кнопка удаления
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

    // ==================== МАКРОСЫ ОКНО ====================

    private void showMacrosWindow() {
        try {
            if (windows.containsKey("macros")) {
                windows.get("macros").container.setVisibility(View.VISIBLE);
                return;
            }

            FloatingWindow win = new FloatingWindow();
            win.type = "macros";
            win.title = "МАКРОСЫ";

            LinearLayout mainLayout = new LinearLayout(this);
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setPadding(16, 16, 16, 16);
            mainLayout.setBackgroundColor(0xDD0D0D0D);

            // Заголовок
            LinearLayout titleBar = createTitleBar(win);
            mainLayout.addView(titleBar);

            // Выбор макроса
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
            prevBtn.setText("<");
            prevBtn.setTextColor(Color.WHITE);
            prevBtn.setTextSize(18);
            prevBtn.setBackgroundColor(0x33FF0000);
            prevBtn.setPadding(12, 4, 12, 4);
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
            macroNameText.setTextSize(16);
            macroNameText.setTypeface(null, android.graphics.Typeface.BOLD);
            macroNameText.setPadding(12, 0, 12, 0);
            selectLayout.addView(macroNameText);

            Button nextBtn = new Button(this);
            nextBtn.setText(">");
            nextBtn.setTextColor(Color.WHITE);
            nextBtn.setTextSize(18);
            nextBtn.setBackgroundColor(0x33FF0000);
            nextBtn.setPadding(12, 4, 12, 4);
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
            newMacroBtn.setTextSize(18);
            newMacroBtn.setBackgroundColor(0xFFFF0000);
            newMacroBtn.setPadding(12, 4, 12, 4);
            newMacroBtn.setOnClickListener(v -> showNewMacroDialog(mainLayout));
            selectLayout.addView(newMacroBtn);

            mainLayout.addView(selectLayout);

            // Точки макроса
            final LinearLayout pointsContainer = new LinearLayout(this);
            pointsContainer.setOrientation(LinearLayout.VERTICAL);
            pointsContainer.setPadding(0, 12, 0, 0);
            mainLayout.addView(pointsContainer);

            // Кнопки управления с нормальными иконками
            LinearLayout controlLayout = new LinearLayout(this);
            controlLayout.setOrientation(LinearLayout.HORIZONTAL);
            controlLayout.setGravity(Gravity.CENTER);
            controlLayout.setPadding(0, 12, 0, 0);

            Button addPointBtn = createStyledIconButton("ДОБАВИТЬ", 0xFFFF0000);
            addPointBtn.setOnClickListener(v -> startMacroRecording());
            controlLayout.addView(addPointBtn);

            Button startBtn = createStyledIconButton("▶ СТАРТ", 0xFF00AA00);
            startBtn.setOnClickListener(v -> startMacroExecution());
            controlLayout.addView(startBtn);

            Button stopBtn = createStyledIconButton("■ СТОП", 0xFFFF0000);
            stopBtn.setOnClickListener(v -> stopMacroExecution());
            controlLayout.addView(stopBtn);

            Button clearBtn = createStyledIconButton("✕ ОЧИСТИТЬ", 0xFFFF8800);
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
                    480, 550,
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

    private void updateMacroUI(LinearLayout mainLayout) {
        MacroConfig config = getCurrentMacro();
        if (config == null) return;

        // Находим pointsContainer
        LinearLayout pointsContainer = null;
        for (int i = 0; i < mainLayout.getChildCount(); i++) {
            View v = mainLayout.getChildAt(i);
            if (v instanceof LinearLayout) {
                LinearLayout ll = (LinearLayout) v;
                if (ll.getChildCount() > 0 && ll.getChildAt(0) instanceof TextView) {
                    TextView tv = (TextView) ll.getChildAt(0);
                    if (tv.getText().toString().contains("Точки:")) {
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
            pointsContainer.setPadding(0, 12, 0, 0);
            mainLayout.addView(pointsContainer, mainLayout.getChildCount() - 2);
        }

        pointsContainer.removeAllViews();

        TextView header = new TextView(this);
        header.setText("Точки: " + config.points.size());
        header.setTextColor(Color.WHITE);
        header.setTextSize(14);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setPadding(0, 0, 0, 8);
        pointsContainer.addView(header);

        if (config.points.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Нет точек. Нажмите ДОБАВИТЬ");
            empty.setTextColor(0xFF888888);
            empty.setTextSize(12);
            empty.setPadding(0, 8, 0, 8);
            pointsContainer.addView(empty);
        } else {
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
                itemBg.setCornerRadius(8);
                itemBg.setColor(0x22FF0000);
                itemBg.setStroke(1, getThemeColor());
                item.setBackground(itemBg);

                TextView info = new TextView(this);
                String delayText = p.delay >= 1000 ? (p.delay/1000) + "с" : p.delay + "мс";
                info.setText(" #" + (i+1) + " (" + p.x + "," + p.y + ") " + delayText);
                info.setTextColor(Color.WHITE);
                info.setTextSize(12);
                info.setLayoutParams(new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                item.addView(info);

                // Кнопка удаления
                Button delBtn = new Button(this);
                delBtn.setText("✕");
                delBtn.setTextColor(0xFFFF0000);
                delBtn.setTextSize(12);
                delBtn.setBackgroundColor(0x33FF0000);
                delBtn.setPadding(8, 4, 8, 4);
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

    // ==================== МАКРОСЫ - ЗАПИСЬ И ВЫПОЛНЕНИЕ ====================

    private void startMacroRecording() {
        if (isMacroRecording) return;
        if (windowManager == null) return;
        
        isMacroRecording = true;
        macroStatusText.setText("🔴 Нажмите на экран");
        Toast.makeText(this, "Нажмите на экран для добавления точки", Toast.LENGTH_SHORT).show();
        
        captureOverlay = new FrameLayout(this);
        captureOverlay.setBackgroundColor(0x3300FF00);
        
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
        captureParams.gravity = Gravity.TOP | Gravity.START;
        captureParams.x = 0;
        captureParams.y = 0;
        
        captureOverlay.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP && isMacroRecording) {
                int x = (int) event.getRawX();
                int y = (int) event.getRawY();
                addMacroPointToConfig(x, y);
                return true;
            }
            return false;
        });
        
        windowManager.addView(captureOverlay, captureParams);
    }

    private void addMacroPointToConfig(int x, int y) {
        MacroConfig config = getCurrentMacro();
        if (config == null) return;

        MacroPoint point = new MacroPoint(x, y);
        config.points.add(point);
        saveMacroConfigs();

        Toast.makeText(this, "Точка " + config.points.size() + ": " + x + ", " + y, Toast.LENGTH_SHORT).show();

        showDelayDialog(config.points.size() - 1);

        // Обновляем UI макросов
        if (windows.containsKey("macros")) {
            updateMacroUI((LinearLayout) windows.get("macros").contentView);
        }
    }

    private void showDelayDialog(final int index) {
        try {
            if (captureOverlay != null && windowManager != null) {
                windowManager.removeView(captureOverlay);
                captureOverlay = null;
                isMacroRecording = false;
                if (macroStatusText != null) {
                    macroStatusText.setText("⏸ Готов");
                }
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("⏱ ЗАДЕРЖКА");
            final EditText input = new EditText(this);
            input.setHint("Задержка в секундах");
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            input.setText("1");
            builder.setView(input);

            builder.setPositiveButton("OK", (d, w) -> {
                try {
                    int delay = Integer.parseInt(input.getText().toString());
                    MacroConfig config = getCurrentMacro();
                    if (config != null && index < config.points.size()) {
                        config.points.get(index).delay = delay * 1000;
                        saveMacroConfigs();
                        if (windows.containsKey("macros")) {
                            updateMacroUI((LinearLayout) windows.get("macros").contentView);
                        }
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Ошибка", Toast.LENGTH_SHORT).show();
                }
            });
            builder.setNegativeButton("Пропустить", null);
            builder.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void clearMacroPoints() {
        MacroConfig config = getCurrentMacro();
        if (config != null && !config.points.isEmpty()) {
            config.points.clear();
            saveMacroConfigs();
            if (windows.containsKey("macros")) {
                updateMacroUI((LinearLayout) windows.get("macros").contentView);
            }
            Toast.makeText(this, "Все точки удалены", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Нет точек для удаления", Toast.LENGTH_SHORT).show();
        }
    }

    private void startMacroExecution() {
        MacroConfig config = getCurrentMacro();
        if (config == null || config.points.isEmpty()) {
            Toast.makeText(this, "Нет точек для выполнения", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Включите специальные возможности", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }

        isMacroRunning = true;
        currentMacroIndex = 0;
        Toast.makeText(this, "Макрос запущен!", Toast.LENGTH_SHORT).show();
        executeNextPoint(config);
    }

    private void executeNextPoint(MacroConfig config) {
        if (!isMacroRunning || currentMacroIndex >= config.points.size()) {
            stopMacroExecution();
            return;
        }

        MacroPoint point = config.points.get(currentMacroIndex);
        performClick(point.x, point.y);

        currentMacroIndex++;
        macroHandler.postDelayed(() -> {
            executeNextPoint(config);
        }, point.delay);
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
        if (macroStatusText != null) {
            macroStatusText.setText("⏸ Остановлен");
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
            btn.setOnClickListener(v -> {
                primaryColor = color;
                secondaryColor = color;
                accentColor = color;
                rainbowMode = (i == 4);
                saveSettings();
                applyTheme();
                Toast.makeText(this, "Тема применена!", Toast.LENGTH_SHORT).show();
            });
            themeButtons.addView(btn);
        }
        layout.addView(themeButtons);

        // Custom цвет
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

        // Размер оверлея
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

        // Прозрачность
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
        // Обновляем все окна
        for (FloatingWindow win : windows.values()) {
            if (win.container != null) {
                GradientDrawable border = new GradientDrawable();
                border.setCornerRadius(16);
                border.setColor(0xDD0D0D0D);
                border.setStroke(3, getThemeColor());
                win.container.setBackground(border);
                
                // Обновляем заголовки
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

    private Button createStyledIconButton(String text, int color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(13);
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

    // ==================== СТАРЫЕ МЕТОДЫ ДЛЯ ПЕРСОНАЖЕЙ ====================

    private void showDeleteOverlayConfirmation() {
        try {
            FrameLayout confirmOverlay = new FrameLayout(this);
            confirmOverlay.setBackgroundColor(Color.parseColor("#E6000000"));
            
            LinearLayout confirmLayout = new LinearLayout(this);
            confirmLayout.setOrientation(LinearLayout.VERTICAL);
            confirmLayout.setGravity(Gravity.CENTER);
            confirmLayout.setPadding(40, 40, 40, 40);
            
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(40);
            bg.setColor(Color.parseColor("#0D0D0D"));
            bg.setStroke(3, Color.parseColor("#CC0000"));
            confirmLayout.setBackground(bg);
            
            TextView warnIcon = new TextView(this);
            warnIcon.setText("⚠");
            warnIcon.setTextSize(50);
            warnIcon.setGravity(Gravity.CENTER);
            warnIcon.setPadding(0, 0, 0, 20);
            confirmLayout.addView(warnIcon);
            
            TextView title = new TextView(this);
            title.setText("УДАЛЕНИЕ ОВЕРЛЕЯ");
            title.setTextColor(Color.parseColor("#CC0000"));
            title.setTextSize(24);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, 0, 0, 12);
            confirmLayout.addView(title);
            
            TextView message = new TextView(this);
            message.setText("Вы уверены, что хотите удалить\nоверлей CR Arcade?\n\nЭто действие нельзя отменить.");
            message.setTextColor(Color.parseColor("#AAAAAA"));
            message.setTextSize(16);
            message.setGravity(Gravity.CENTER);
            message.setPadding(0, 0, 0, 30);
            confirmLayout.addView(message);
            
            LinearLayout buttonLayout = new LinearLayout(this);
            buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
            buttonLayout.setGravity(Gravity.CENTER);
            
            Button confirmBtn = new Button(this);
            confirmBtn.setText("УДАЛИТЬ");
            confirmBtn.setTextColor(Color.WHITE);
            confirmBtn.setTextSize(16);
            confirmBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable confirmBg = new GradientDrawable();
            confirmBg.setCornerRadius(25);
            confirmBg.setColor(Color.parseColor("#CC0000"));
            confirmBtn.setBackground(confirmBg);
            confirmBtn.setPadding(30, 16, 30, 16);
            confirmBtn.setOnClickListener(v -> {
                try {
                    if (windowManager != null) {
                        windowManager.removeView(confirmOverlay);
                    }
                    hideMainOverlay();
                    removeCharacter();
                    removeMainCircle();
                    hideMacroControl();
                    finishAffinity();
                    System.exit(0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            
            Button cancelBtn = new Button(this);
            cancelBtn.setText("ОТМЕНА");
            cancelBtn.setTextColor(Color.WHITE);
            cancelBtn.setTextSize(16);
            cancelBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable cancelBg = new GradientDrawable();
            cancelBg.setCornerRadius(25);
            cancelBg.setColor(Color.parseColor("#2A2A2A"));
            cancelBg.setStroke(2, Color.parseColor("#8B0000"));
            cancelBtn.setBackground(cancelBg);
            cancelBtn.setPadding(30, 16, 30, 16);
            cancelBtn.setOnClickListener(v -> {
                if (windowManager != null) windowManager.removeView(confirmOverlay);
            });
            
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            btnParams.setMargins(8, 0, 8, 0);
            buttonLayout.addView(confirmBtn, btnParams);
            buttonLayout.addView(cancelBtn, btnParams);
            
            confirmLayout.addView(buttonLayout);
            
            FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            containerParams.gravity = Gravity.CENTER;
            containerParams.setMargins(40, 0, 40, 0);
            confirmOverlay.addView(confirmLayout, containerParams);
            
            WindowManager.LayoutParams windowParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT);
            
            if (windowManager != null) windowManager.addView(confirmOverlay, windowParams);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private ImageButton createLargeRoundButton(Drawable icon, String bgColor, int strokeWidth, String strokeColor) {
        try {
            ImageButton btn = new ImageButton(this);
            btn.setImageDrawable(icon);
            btn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            btn.setPadding(30, 30, 30, 30);
            
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(Color.parseColor(bgColor));
            bg.setStroke(strokeWidth, Color.parseColor(strokeColor));
            btn.setBackground(bg);
            
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(130, 130);
            params.setMargins(12, 0, 12, 0);
            btn.setLayoutParams(params);
            
            return btn;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private ImageButton createSmallRoundButton(Drawable icon, String color) {
        try {
            ImageButton btn = new ImageButton(this);
            btn.setImageDrawable(icon);
            btn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            btn.setPadding(10, 10, 10, 10);
            
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(Color.parseColor(color));
            bg.setStroke(2, Color.parseColor("#FF4444"));
            btn.setBackground(bg);
            return btn;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Button createSmallActionButton(String text, String color) {
        try {
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
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ==================== ГРИД ПЕРСОНАЖЕЙ (СТАРЫЙ) ====================

    private void createCharactersGrid() {
        try {
            charactersGridLayout = new LinearLayout(this);
            charactersGridLayout.setOrientation(LinearLayout.VERTICAL);
            charactersGridLayout.setPadding(8, 8, 8, 8);
            charactersGridLayout.setGravity(Gravity.CENTER);
            charactersGridLayout.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            updateCharactersGrid();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateCharactersGrid() {
        try {
            if (charactersGridLayout == null) return;
            charactersGridLayout.removeAllViews();
            
            LinearLayout headerRow = new LinearLayout(this);
            headerRow.setOrientation(LinearLayout.HORIZONTAL);
            headerRow.setGravity(Gravity.CENTER_VERTICAL);
            headerRow.setPadding(0, 0, 0, 16);
            
            TextView headerTitle = new TextView(this);
            headerTitle.setText("МОИ ПЕРСОНАЖИ");
            headerTitle.setTextColor(Color.parseColor("#CC0000"));
            headerTitle.setTextSize(20);
            headerTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            headerTitle.setLayoutParams(titleParams);
            headerRow.addView(headerTitle);
            
            Button addBtn = new Button(this);
            addBtn.setText("+");
            addBtn.setTextColor(Color.WHITE);
            addBtn.setTextSize(24);
            addBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable addBg = new GradientDrawable();
            addBg.setShape(GradientDrawable.OVAL);
            addBg.setColor(Color.parseColor("#CC0000"));
            addBtn.setBackground(addBg);
            addBtn.setPadding(8, 4, 8, 4);
            LinearLayout.LayoutParams addBtnParams = new LinearLayout.LayoutParams(60, 60);
            addBtn.setLayoutParams(addBtnParams);
            addBtn.setOnClickListener(v -> showAddCharacterDialog());
            headerRow.addView(addBtn);
            charactersGridLayout.addView(headerRow);
            
            if (characters.isEmpty()) {
                TextView emptyText = new TextView(this);
                emptyText.setText("📭 Нет сохранённых персонажей\n\nНажмите + чтобы добавить");
                emptyText.setTextColor(Color.parseColor("#555555"));
                emptyText.setTextSize(16);
                emptyText.setTypeface(null, android.graphics.Typeface.BOLD);
                emptyText.setGravity(Gravity.CENTER);
                emptyText.setPadding(0, 60, 0, 60);
                charactersGridLayout.addView(emptyText);
                return;
            }
            
            ScrollView scrollView = new ScrollView(this);
            LinearLayout gridContainer = new LinearLayout(this);
            gridContainer.setOrientation(LinearLayout.VERTICAL);
            
            int itemsPerRow = 2;
            int totalItems = characters.size();
            int rows = (int) Math.ceil((double) totalItems / itemsPerRow);
            
            for (int r = 0; r < rows; r++) {
                LinearLayout rowLayout = new LinearLayout(this);
                rowLayout.setOrientation(LinearLayout.HORIZONTAL);
                rowLayout.setGravity(Gravity.CENTER);
                rowLayout.setPadding(0, 4, 0, 4);
                
                for (int i = r * itemsPerRow; i < Math.min((r + 1) * itemsPerRow, totalItems); i++) {
                    CharacterData data = characters.get(i);
                    LinearLayout card = createSmallCharacterCard(data, i);
                    LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                    cardParams.setMargins(4, 0, 4, 0);
                    card.setLayoutParams(cardParams);
                    rowLayout.addView(card);
                }
                
                gridContainer.addView(rowLayout);
            }
            
            scrollView.addView(gridContainer);
            charactersGridLayout.addView(scrollView);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private LinearLayout createSmallCharacterCard(CharacterData data, int index) {
        try {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(12, 12, 12, 12);
            
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setCornerRadius(16);
            cardBg.setColor(Color.parseColor("#1A0000"));
            cardBg.setStroke(1, Color.parseColor("#8B0000"));
            card.setBackground(cardBg);
            
            FrameLayout previewContainer = new FrameLayout(this);
            previewContainer.setLayoutParams(new LinearLayout.LayoutParams(80, 80));
            
            GradientDrawable previewBg = new GradientDrawable();
            previewBg.setShape(GradientDrawable.OVAL);
            previewBg.setColor(Color.parseColor("#1A1A1A"));
            previewBg.setStroke(2, Color.parseColor("#8B0000"));
            previewContainer.setBackground(previewBg);
            
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), 
                        Uri.fromFile(new File(data.path)));
                Bitmap processed = removeGreenScreen(bitmap, 40);
                ImageView thumbView = new ImageView(this);
                thumbView.setImageBitmap(processed);
                thumbView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                thumbView.setPadding(2, 2, 2, 2);
                previewContainer.addView(thumbView, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                ));
            } catch (IOException e) {
                e.printStackTrace();
            }
            
            card.addView(previewContainer);
            
            String displayName = data.name.trim().isEmpty() ? "Без имени" : data.name;
            if (displayName.length() > 12) displayName = displayName.substring(0, 10) + "...";
            
            TextView nameText = new TextView(this);
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
            
            Button circleBtn = createSmallActionButton("⭕", "#2196F3");
            circleBtn.setOnClickListener(v -> {
                loadCharacterToFloat(data);
                hideMainOverlay();
                createMainCircle();
            });
            
            Button screenBtn = createSmallActionButton("🖥", "#FF9800");
            screenBtn.setOnClickListener(v -> {
                loadCharacterToScreen(data);
                hideMainOverlay();
                createMainCircle();
            });
            
            Button deleteBtn = createSmallActionButton("🗑", "#CC0000");
            deleteBtn.setOnClickListener(v -> {
                characters.remove(index);
                saveCharacters();
                if (isMainOverlayVisible) updateCharactersGrid();
            });
            
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(36, 36);
            btnParams.setMargins(2, 0, 2, 0);
            actionLayout.addView(circleBtn, btnParams);
            actionLayout.addView(screenBtn, btnParams);
            actionLayout.addView(deleteBtn, btnParams);
            
            card.addView(actionLayout);
            return card;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void updateContent() {
        try {
            if (contentContainer == null) return;
            contentContainer.removeAllViews();
            
            if (isWebViewMode) {
                if (webView != null) {
                    contentContainer.addView(webView);
                } else {
                    createWebView();
                    if (webView != null) contentContainer.addView(webView);
                }
            } else {
                if (charactersGridLayout != null) {
                    updateCharactersGrid();
                    contentContainer.addView(charactersGridLayout);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

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

    private void showAddCharacterDialog() {
        // Старый метод - используем новый
    }

    // ==================== ЗАГРУЗКА ПЕРСОНАЖА (СТАРЫЙ) ====================

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
            
            mainCircleContainer.setOnTouchListener(createTouchListener());
            
            if (windowManager == null) {
                windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            }
            
            if (windowManager != null) {
                windowManager.addView(mainCircleContainer, mainCircleParams);
                Toast.makeText(this, "Персонаж загружен в оверлей", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка загрузки", Toast.LENGTH_SHORT).show();
        }
    }

    private View.OnTouchListener createTouchListener() {
        return (v, event) -> {
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
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isDragging = true;
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
                            hideMainOverlay();
                            showMainMenu();
                        }
                        return true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        };
    }

    // ==================== ЗАГРУЗКА НА ЭКРАН (СТАРЫЙ) ====================

    private void loadCharacterToScreen(CharacterData data) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), 
                    Uri.fromFile(new File(data.path)));
            showCharacterOnScreen(bitmap);
            Toast.makeText(this, "Персонаж на экране", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка загрузки", Toast.LENGTH_SHORT).show();
        }
    }

    private void showCharacterOnScreen(Bitmap bitmap) {
        try {
            if (windowManager == null) return;
            
            removeCharacter();
            
            currentCharacterBitmap = removeGreenScreen(bitmap, 40);
            
            characterContainer = new FrameLayout(this);
            characterContainer.setBackgroundColor(Color.TRANSPARENT);
            
            characterView = new ImageView(this);
            characterView.setImageBitmap(currentCharacterBitmap);
            characterView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            characterView.setClickable(true);
            characterView.setFocusable(true);
            
            FrameLayout.LayoutParams charParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT);
            characterContainer.addView(characterView, charParams);
            
            addCharacterControls(characterContainer);
            
            characterParams = new WindowManager.LayoutParams(
                    400, 400,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT);
            characterParams.gravity = Gravity.CENTER;
            
            windowManager.addView(characterContainer, characterParams);
            isCharacterModeActive = true;
            isCharacterFixed = false;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== УПРАВЛЕНИЕ ПЕРСОНАЖЕМ (СТАРЫЙ) ====================

    private void addCharacterControls(FrameLayout container) {
        try {
            fixButton = new ImageButton(this);
            fixButton.setImageDrawable(createLockIcon(false));
            GradientDrawable fixBg = new GradientDrawable();
            fixBg.setShape(GradientDrawable.OVAL);
            fixBg.setColor(Color.parseColor("#FF6B00"));
            fixBg.setStroke(2, Color.WHITE);
            fixButton.setBackground(fixBg);
            fixButton.setPadding(16, 16, 16, 16);
            
            FrameLayout.LayoutParams fixParams = new FrameLayout.LayoutParams(60, 60, Gravity.TOP | Gravity.END);
            fixParams.setMargins(0, 24, 24, 0);
            fixButton.setLayoutParams(fixParams);
            fixButton.setOnClickListener(v -> toggleCharacterFix());
            
            deleteButton = new ImageButton(this);
            deleteButton.setImageDrawable(createDeleteIcon());
            GradientDrawable deleteBg = new GradientDrawable();
            deleteBg.setShape(GradientDrawable.OVAL);
            deleteBg.setColor(Color.RED);
            deleteBg.setStroke(2, Color.WHITE);
            deleteButton.setBackground(deleteBg);
            deleteButton.setPadding(16, 16, 16, 16);
            
            FrameLayout.LayoutParams deleteParams = new FrameLayout.LayoutParams(60, 60, Gravity.TOP | Gravity.START);
            deleteParams.setMargins(24, 24, 0, 0);
            deleteButton.setLayoutParams(deleteParams);
            deleteButton.setOnClickListener(v -> {
                removeCharacter();
                Toast.makeText(this, "Персонаж удалён", Toast.LENGTH_SHORT).show();
            });
            
            backButton = new ImageButton(this);
            backButton.setImageDrawable(createCloseIcon());
            GradientDrawable backBg = new GradientDrawable();
            backBg.setShape(GradientDrawable.OVAL);
            backBg.setColor(Color.parseColor("#9C27B0"));
            backBg.setStroke(2, Color.WHITE);
            backButton.setBackground(backBg);
            backButton.setPadding(16, 16, 16, 16);
            
            FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(60, 60, Gravity.BOTTOM | Gravity.CENTER);
            backParams.setMargins(0, 0, 0, 24);
            backButton.setLayoutParams(backParams);
            backButton.setOnClickListener(v -> {
                removeCharacter();
                Toast.makeText(this, "Персонаж закрыт", Toast.LENGTH_SHORT).show();
            });
            
            controlsLayout = new LinearLayout(this);
            controlsLayout.setOrientation(LinearLayout.VERTICAL);
            controlsLayout.setGravity(Gravity.CENTER);
            controlsLayout.setBackgroundColor(Color.parseColor("#AA000000"));
            controlsLayout.setPadding(16, 12, 16, 12);
            controlsLayout.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
            ));
            
            TextView titleText = new TextView(this);
            titleText.setText("РАЗМЕР");
            titleText.setTextColor(Color.WHITE);
            titleText.setGravity(Gravity.CENTER);
            titleText.setTextSize(14);
            titleText.setTypeface(null, android.graphics.Typeface.BOLD);
            titleText.setPadding(0, 0, 0, 8);
            controlsLayout.addView(titleText);
            
            LinearLayout xLayout = new LinearLayout(this);
            xLayout.setOrientation(LinearLayout.HORIZONTAL);
            xLayout.setGravity(Gravity.CENTER);
            
            sizeXLabel = new TextView(this);
            sizeXLabel.setText("Ш");
            sizeXLabel.setTextColor(Color.WHITE);
            sizeXLabel.setPadding(0, 0, 8, 0);
            
            sizeXInput = new EditText(this);
            sizeXInput.setText("400");
            sizeXInput.setInputType(InputType.TYPE_CLASS_NUMBER);
            sizeXInput.setTextColor(Color.WHITE);
            sizeXInput.setBackgroundColor(Color.parseColor("#333333"));
            sizeXInput.setPadding(8, 4, 8, 4);
            sizeXInput.setWidth(60);
            
            SeekBar xSeekBar = new SeekBar(this);
            xSeekBar.setMax(800);
            xSeekBar.setProgress(400);
            xSeekBar.setMinWidth(80);
            
            xLayout.addView(sizeXLabel);
            xLayout.addView(sizeXInput);
            xLayout.addView(xSeekBar);
            controlsLayout.addView(xLayout);
            
            LinearLayout yLayout = new LinearLayout(this);
            yLayout.setOrientation(LinearLayout.HORIZONTAL);
            yLayout.setGravity(Gravity.CENTER);
            
            sizeYLabel = new TextView(this);
            sizeYLabel.setText("В");
            sizeYLabel.setTextColor(Color.WHITE);
            sizeYLabel.setPadding(0, 0, 8, 0);
            
            sizeYInput = new EditText(this);
            sizeYInput.setText("400");
            sizeYInput.setInputType(InputType.TYPE_CLASS_NUMBER);
            sizeYInput.setTextColor(Color.WHITE);
            sizeYInput.setBackgroundColor(Color.parseColor("#333333"));
            sizeYInput.setPadding(8, 4, 8, 4);
            sizeYInput.setWidth(60);
            
            SeekBar ySeekBar = new SeekBar(this);
            ySeekBar.setMax(800);
            ySeekBar.setProgress(400);
            ySeekBar.setMinWidth(80);
            
            yLayout.addView(sizeYLabel);
            yLayout.addView(sizeYInput);
            yLayout.addView(ySeekBar);
            controlsLayout.addView(yLayout);
            
            LinearLayout zLayout = new LinearLayout(this);
            zLayout.setOrientation(LinearLayout.HORIZONTAL);
            zLayout.setGravity(Gravity.CENTER);
            
            sizeZLabel = new TextView(this);
            sizeZLabel.setText("α");
            sizeZLabel.setTextColor(Color.WHITE);
            sizeZLabel.setPadding(0, 0, 8, 0);
            
            sizeZInput = new EditText(this);
            sizeZInput.setText("100");
            sizeZInput.setInputType(InputType.TYPE_CLASS_NUMBER);
            sizeZInput.setTextColor(Color.WHITE);
            sizeZInput.setBackgroundColor(Color.parseColor("#333333"));
            sizeZInput.setPadding(8, 4, 8, 4);
            sizeZInput.setWidth(60);
            
            SeekBar zSeekBar = new SeekBar(this);
            zSeekBar.setMax(100);
            zSeekBar.setProgress(100);
            zSeekBar.setMinWidth(80);
            
            zLayout.addView(sizeZLabel);
            zLayout.addView(sizeZInput);
            zLayout.addView(zSeekBar);
            controlsLayout.addView(zLayout);
            
            xSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && characterParams != null) {
                        characterParams.width = progress + 50;
                        sizeXInput.setText(String.valueOf(progress + 50));
                        updateCharacterSize();
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            
            sizeXInput.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    try {
                        int val = Integer.parseInt(sizeXInput.getText().toString());
                        if (val > 50) {
                            characterParams.width = val;
                            xSeekBar.setProgress(Math.min(val - 50, 800));
                            updateCharacterSize();
                        }
                    } catch (NumberFormatException e) {}
                }
            });
            
            ySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && characterParams != null) {
                        characterParams.height = progress + 50;
                        sizeYInput.setText(String.valueOf(progress + 50));
                        updateCharacterSize();
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            
            sizeYInput.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    try {
                        int val = Integer.parseInt(sizeYInput.getText().toString());
                        if (val > 50) {
                            characterParams.height = val;
                            ySeekBar.setProgress(Math.min(val - 50, 800));
                            updateCharacterSize();
                        }
                    } catch (NumberFormatException e) {}
                }
            });
            
            zSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        sizeZInput.setText(String.valueOf(progress));
                        float alpha = progress / 100f;
                        if (characterContainer != null) characterContainer.setAlpha(alpha);
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            
            sizeZInput.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    try {
                        int val = Integer.parseInt(sizeZInput.getText().toString());
                        if (val >= 0 && val <= 100) {
                            zSeekBar.setProgress(val);
                            float alpha = val / 100f;
                            if (characterContainer != null) characterContainer.setAlpha(alpha);
                        }
                    } catch (NumberFormatException e) {}
                }
            });
            
            FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
            );
            controlsParams.setMargins(0, 0, 0, 100);
            controlsLayout.setLayoutParams(controlsParams);
            
            container.addView(fixButton);
            container.addView(deleteButton);
            container.addView(backButton);
            container.addView(controlsLayout);
            
            if (characterView != null) {
                characterView.setOnTouchListener((v, event) -> {
                    if (isCharacterFixed) return false;
                    return handleCharacterTouch(event);
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private boolean handleCharacterTouch(MotionEvent event) {
        try {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastTouchX = event.getRawX();
                    lastTouchY = event.getRawY();
                    initialX = characterParams.x;
                    initialY = characterParams.y;
                    initialPinchDistance = 0;
                    return true;
                    
                case MotionEvent.ACTION_MOVE:
                    if (event.getPointerCount() == 2) {
                        float distance = getDistance(event);
                        if (initialPinchDistance == 0) {
                            initialPinchDistance = distance;
                        } else {
                            float scale = distance / initialPinchDistance;
                            int newWidth = (int)(characterParams.width * scale);
                            int newHeight = (int)(characterParams.height * scale);
                            if (newWidth > 50 && newHeight > 50 && newWidth < 1200 && newHeight < 1200) {
                                characterParams.width = newWidth;
                                characterParams.height = newHeight;
                                sizeXInput.setText(String.valueOf(newWidth));
                                sizeYInput.setText(String.valueOf(newHeight));
                                updateCharacterSize();
                            }
                        }
                    } else {
                        float dx = event.getRawX() - lastTouchX;
                        float dy = event.getRawY() - lastTouchY;
                        characterParams.x += (int) dx;
                        characterParams.y += (int) dy;
                        lastTouchX = event.getRawX();
                        lastTouchY = event.getRawY();
                        if (windowManager != null) {
                            windowManager.updateViewLayout(characterContainer, characterParams);
                        }
                    }
                    return true;
                    
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    initialPinchDistance = 0;
                    return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void toggleCharacterFix() {
        try {
            isCharacterFixed = !isCharacterFixed;
            
            if (isCharacterFixed) {
                Toast.makeText(this, "🔒 Персонаж закреплён", Toast.LENGTH_SHORT).show();
                fixButton.setImageDrawable(createLockIcon(true));
                hideAllControls();
                
                if (characterParams != null && windowManager != null) {
                    characterParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    windowManager.updateViewLayout(characterContainer, characterParams);
                }
                
                if (characterContainer != null) {
                    characterContainer.setClickable(false);
                    characterContainer.setFocusable(false);
                }
                if (characterView != null) {
                    characterView.setClickable(false);
                    characterView.setFocusable(false);
                }
            } else {
                Toast.makeText(this, "🔓 Персонаж разблокирован", Toast.LENGTH_SHORT).show();
                fixButton.setImageDrawable(createLockIcon(false));
                showAllControls();
                
                if (characterParams != null && windowManager != null) {
                    characterParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    windowManager.updateViewLayout(characterContainer, characterParams);
                }
                
                if (characterContainer != null) {
                    characterContainer.setClickable(true);
                    characterContainer.setFocusable(true);
                }
                if (characterView != null) {
                    characterView.setClickable(true);
                    characterView.setFocusable(true);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void hideAllControls() {
        try {
            if (controlsLayout != null) controlsLayout.setVisibility(View.GONE);
            if (deleteButton != null) deleteButton.setVisibility(View.GONE);
            if (backButton != null) backButton.setVisibility(View.GONE);
            if (fixButton != null) fixButton.setVisibility(View.GONE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showAllControls() {
        try {
            if (controlsLayout != null) controlsLayout.setVisibility(View.VISIBLE);
            if (deleteButton != null) deleteButton.setVisibility(View.VISIBLE);
            if (backButton != null) backButton.setVisibility(View.VISIBLE);
            if (fixButton != null) fixButton.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void updateCharacterSize() {
        try {
            if (windowManager != null && characterContainer != null && characterParams != null) {
                windowManager.updateViewLayout(characterContainer, characterParams);
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

    // ==================== CHROMAKEY (СТАРЫЙ) ====================

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
            e.printStackTrace();
            return null;
        }
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЙ МЕТОД ====================

    private float getDistance(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    // ==================== ЖИЗНЕННЫЙ ЦИКЛ ====================

    @Override
    public void onResume() {
        super.onResume();
        isAppInForeground = true;
        removeMainCircle();
        
        if (isMainOverlayVisible) {
            updateContent();
        }
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
            if (webView != null) {
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
                        if (isMainOverlayVisible) updateContent();
                        // Обновляем окно персонажей если открыто
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
