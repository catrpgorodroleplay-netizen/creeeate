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

public class MainActivity extends BridgeActivity {

    private static final int REQUEST_MICROPHONE = 100;
    private static final int REQUEST_CAMERA = 102;
    private static final int REQUEST_OVERLAY_PERMISSION = 101;
    private static final int REQUEST_GALLERY = 103;
    private static final int REQUEST_STORAGE = 104;
    private static final int REQUEST_NOTIFICATION = 106;
    private static final int REQUEST_ACCESSIBILITY = 107;

    private static final String URL_HOME = "https://wyikhedfghhopyewfvjkurrhncswehipkhf.vercel.app/";

    private WindowManager windowManager;
    private FrameLayout mainCircleContainer;
    private WindowManager.LayoutParams mainCircleParams;
    
    private float startX, startY;
    private int initialX, initialY;
    private boolean isDragging = false;
    
    private WebView webView;
    private boolean isAppInForeground = true;

    private FrameLayout wheelMenuContainer;
    private boolean isWheelMenuVisible = false;

    private ArrayList<CharacterData> characters = new ArrayList<>();
    private SharedPreferences prefs;
    private String tempCharacterName = "";

    private int overlayAlpha = 255;
    private int overlaySize = 80;

    // Макросы
    private ArrayList<MacroConfig> macroConfigs = new ArrayList<>();
    private String currentMacroName = "Макрос 1";
    private boolean isMacroRecording = false;
    private boolean isMacroRunning = false;
    private Handler macroHandler = new Handler();
    private int currentMacroIndex = 0;
    private int macroRepeatCount = 1;
    private int currentRepeat = 0;
    private FrameLayout captureOverlay;
    
    // Быстрые кнопки макросов
    private HashMap<String, QuickMacroButton> quickButtons = new HashMap<>();

    // Диалог с клавиатурой
    private FrameLayout keyboardDialog;
    private TextView keyboardDisplay;
    private String keyboardValue = "";
    private int keyboardMaxLength = 10;
    private Runnable keyboardCallback;
    private String keyboardTitle = "";

    private static class MacroConfig {
        String name;
        ArrayList<MacroPoint> points;
        int color;
        int repeatCount;
        long totalDelay;
        
        MacroConfig(String name) {
            this.name = name;
            this.points = new ArrayList<>();
            this.color = 0xFFFF0000;
            this.repeatCount = 1;
            this.totalDelay = 0;
        }
        
        MacroConfig(JSONObject json) throws Exception {
            this.name = json.getString("name");
            this.color = json.optInt("color", 0xFFFF0000);
            this.repeatCount = json.optInt("repeatCount", 1);
            this.totalDelay = json.optLong("totalDelay", 0);
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
            json.put("repeatCount", repeatCount);
            json.put("totalDelay", totalDelay);
            JSONArray pointsArray = new JSONArray();
            for (MacroPoint p : points) {
                pointsArray.put(p.toJSON());
            }
            json.put("points", pointsArray);
            return json;
        }
    }

    private static class MacroPoint {
        int x, y;
        long delayMs;
        String delayDisplay;
        
        MacroPoint(int x, int y) {
            this.x = x;
            this.y = y;
            this.delayMs = 1000;
            this.delayDisplay = "1с";
        }
        
        MacroPoint(int x, int y, long delayMs) {
            this.x = x;
            this.y = y;
            this.delayMs = delayMs;
            this.delayDisplay = formatDelay(delayMs);
        }
        
        MacroPoint(JSONObject json) throws Exception {
            this.x = json.getInt("x");
            this.y = json.getInt("y");
            this.delayMs = json.optLong("delayMs", 1000);
            this.delayDisplay = formatDelay(delayMs);
        }
        
        JSONObject toJSON() throws Exception {
            JSONObject json = new JSONObject();
            json.put("x", x);
            json.put("y", y);
            json.put("delayMs", delayMs);
            return json;
        }
        
        static String formatDelay(long ms) {
            if (ms >= 3600000) return (ms / 3600000) + "ч";
            if (ms >= 60000) return (ms / 60000) + "м";
            if (ms >= 1000) return (ms / 1000) + "с";
            return ms + "мс";
        }
    }

    private static class QuickMacroButton {
        String macroName;
        FrameLayout container;
        WindowManager.LayoutParams params;
        boolean isFixed;
        int size;
        float lastTouchX, lastTouchY;
        int startX, startY;
        boolean isDragging;
        
        QuickMacroButton(String macroName) {
            this.macroName = macroName;
            this.size = 70;
            this.isFixed = false;
            this.isDragging = false;
        }
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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            prefs = getSharedPreferences("arcade_data", MODE_PRIVATE);
            overlayAlpha = prefs.getInt("overlay_alpha", 255);
            overlaySize = prefs.getInt("overlay_size", 80);
            loadCharacters();
            loadMacroConfigs();
            loadQuickButtons();
        } catch (Exception e) {
            e.printStackTrace();
        }

        requestPermissionsIfNeeded();
        requestAccessibilityPermission();

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

        if (windowManager == null) {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        }
    }

    // ==================== ЗАГРУЗКА/СОХРАНЕНИЕ ====================

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
            prefs.edit().putString("characters_list", array.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
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

    private void loadQuickButtons() {
        try {
            quickButtons.clear();
            String json = prefs.getString("quick_buttons", "");
            if (!json.isEmpty()) {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    String name = obj.getString("macroName");
                    QuickMacroButton btn = new QuickMacroButton(name);
                    btn.isFixed = obj.optBoolean("isFixed", false);
                    btn.size = obj.optInt("size", 70);
                    btn.params = new WindowManager.LayoutParams(
                            btn.size, btn.size,
                            getOverlayFlag(),
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                            PixelFormat.TRANSLUCENT
                    );
                    btn.params.gravity = Gravity.TOP | Gravity.START;
                    btn.params.x = obj.optInt("x", 100);
                    btn.params.y = obj.optInt("y", 200);
                    quickButtons.put(name, btn);
                    // Создаем UI после загрузки
                    createQuickButtonUI(name);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveQuickButtons() {
        try {
            JSONArray array = new JSONArray();
            for (String name : quickButtons.keySet()) {
                QuickMacroButton btn = quickButtons.get(name);
                JSONObject obj = new JSONObject();
                obj.put("macroName", name);
                obj.put("isFixed", btn.isFixed);
                obj.put("size", btn.size);
                obj.put("x", btn.params.x);
                obj.put("y", btn.params.y);
                array.put(obj);
            }
            prefs.edit().putString("quick_buttons", array.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
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
            iconButton.setPadding(20, 20, 20, 20);
            iconButton.setClickable(false);
            iconButton.setFocusable(false);
            
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(0xFFFF0000);
            d.setStroke(4, Color.parseColor("#FF4444"));
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
                                        } else if (!macroConfigs.isEmpty()) {
                                            startMacroExecution(macroConfigs.get(0).name);
                                        }
                                        return true;
                                    }
                                    lastTapTime = currentTime;
                                    showWheelMenu();
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
            int size = 100;
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint();
            paint.setAntiAlias(true);
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(6);

            float cx = size / 2f, cy = size / 2f;
            canvas.drawRoundRect(cx - 32, cy - 22, cx + 32, cy + 22, 18, 18, paint);
            canvas.drawCircle(cx - 26, cy, 14, paint);
            canvas.drawCircle(cx + 26, cy, 14, paint);
            paint.setStrokeWidth(5);
            canvas.drawLine(cx - 20, cy - 8, cx - 20, cy + 8, paint);
            canvas.drawLine(cx - 24, cy, cx - 16, cy, paint);
            canvas.drawCircle(cx + 18, cy - 6, 6, paint);
            canvas.drawCircle(cx + 18, cy + 6, 6, paint);
            canvas.drawCircle(cx + 28, cy, 6, paint);
            canvas.drawCircle(cx + 8, cy, 6, paint);
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ==================== КОЛЕСИКО МЕНЮ ====================

    private void showWheelMenu() {
        try {
            if (windowManager == null) return;
            
            if (isWheelMenuVisible && wheelMenuContainer != null) {
                windowManager.removeView(wheelMenuContainer);
                wheelMenuContainer = null;
                isWheelMenuVisible = false;
                return;
            }

            wheelMenuContainer = new FrameLayout(this);
            wheelMenuContainer.setBackgroundColor(Color.TRANSPARENT);

            LinearLayout itemsLayout = new LinearLayout(this);
            itemsLayout.setOrientation(LinearLayout.HORIZONTAL);
            itemsLayout.setGravity(Gravity.CENTER);
            itemsLayout.setPadding(8, 8, 8, 8);
            
            GradientDrawable wheelBg = new GradientDrawable();
            wheelBg.setShape(GradientDrawable.OVAL);
            wheelBg.setColor(0xDD000000);
            wheelBg.setStroke(3, 0xFFFF0000);
            itemsLayout.setBackground(wheelBg);

            String[] items = {"🌐", "🎯", "👤", "⚙️", "✕"};
            String[] labels = {"Web", "Макрос", "Перс", "Настр", "Закр"};

            for (int i = 0; i < items.length; i++) {
                final int index = i;
                LinearLayout itemLayout = new LinearLayout(this);
                itemLayout.setOrientation(LinearLayout.VERTICAL);
                itemLayout.setGravity(Gravity.CENTER);
                itemLayout.setPadding(8, 8, 8, 8);

                TextView iconView = new TextView(this);
                iconView.setText(items[i]);
                iconView.setTextSize(24);
                iconView.setTextColor(Color.WHITE);
                iconView.setGravity(Gravity.CENTER);
                
                TextView labelView = new TextView(this);
                labelView.setText(labels[i]);
                labelView.setTextSize(10);
                labelView.setTextColor(0xFFAAAAAA);
                labelView.setGravity(Gravity.CENTER);

                itemLayout.addView(iconView);
                itemLayout.addView(labelView);

                GradientDrawable itemBg = new GradientDrawable();
                itemBg.setShape(GradientDrawable.OVAL);
                itemBg.setColor(0x44FF0000);
                itemBg.setStroke(2, 0xFFFF0000);
                itemLayout.setBackground(itemBg);
                itemLayout.setPadding(12, 8, 12, 8);

                itemLayout.setOnClickListener(v -> handleWheelClick(index));
                itemsLayout.addView(itemLayout);
            }

            RotateAnimation rotate = new RotateAnimation(
                    0, 360,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f
            );
            rotate.setDuration(300);
            rotate.setRepeatCount(0);
            itemsLayout.startAnimation(rotate);

            wheelMenuContainer.addView(itemsLayout);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.CENTER;

            windowManager.addView(wheelMenuContainer, params);
            isWheelMenuVisible = true;

            new Handler().postDelayed(() -> {
                if (isWheelMenuVisible && wheelMenuContainer != null) {
                    try {
                        windowManager.removeView(wheelMenuContainer);
                        wheelMenuContainer = null;
                        isWheelMenuVisible = false;
                    } catch (Exception e) {}
                }
            }, 5000);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleWheelClick(int index) {
        try {
            if (wheelMenuContainer != null && windowManager != null) {
                windowManager.removeView(wheelMenuContainer);
                wheelMenuContainer = null;
                isWheelMenuVisible = false;
            }

            switch (index) {
                case 0: showWebViewWindow(); break;
                case 1: showMacrosWindow(); break;
                case 2: showCharactersWindow(); break;
                case 3: showSettingsDialog(); break;
                case 4: removeMainCircle(); break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== КЛАВИАТУРА С ЦИФРАМИ ====================

    private void showKeyboardDialog(String title, String initialValue, int maxLength, Runnable callback) {
        try {
            keyboardTitle = title;
            keyboardValue = initialValue;
            keyboardMaxLength = maxLength;
            keyboardCallback = callback;

            if (keyboardDialog != null && windowManager != null) {
                windowManager.removeView(keyboardDialog);
                keyboardDialog = null;
            }

            keyboardDialog = new FrameLayout(this);
            keyboardDialog.setBackgroundColor(0xCC000000);

            LinearLayout mainLayout = new LinearLayout(this);
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setGravity(Gravity.CENTER);
            mainLayout.setPadding(30, 30, 30, 30);

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(24);
            bg.setColor(0xFF1A1A1A);
            bg.setStroke(3, 0xFFFF0000);
            mainLayout.setBackground(bg);

            // Заголовок
            TextView titleView = new TextView(this);
            titleView.setText(title);
            titleView.setTextColor(0xFFFF0000);
            titleView.setTextSize(20);
            titleView.setTypeface(null, android.graphics.Typeface.BOLD);
            titleView.setGravity(Gravity.CENTER);
            titleView.setPadding(0, 0, 0, 16);
            mainLayout.addView(titleView);

            // Дисплей с подсветкой
            keyboardDisplay = new TextView(this);
            keyboardDisplay.setText(initialValue.isEmpty() ? "0" : initialValue);
            keyboardDisplay.setTextColor(Color.WHITE);
            keyboardDisplay.setTextSize(36);
            keyboardDisplay.setTypeface(null, android.graphics.Typeface.BOLD);
            keyboardDisplay.setGravity(Gravity.CENTER);
            keyboardDisplay.setPadding(0, 20, 0, 20);
            
            GradientDrawable displayBg = new GradientDrawable();
            displayBg.setCornerRadius(12);
            displayBg.setColor(0x44000000);
            displayBg.setStroke(2, 0xFFFF4444);
            keyboardDisplay.setBackground(displayBg);
            
            FrameLayout.LayoutParams displayParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            displayParams.setMargins(0, 0, 0, 16);
            keyboardDisplay.setLayoutParams(displayParams);
            mainLayout.addView(keyboardDisplay);

            // Клавиатура с цифрами
            LinearLayout keyboardGrid = new LinearLayout(this);
            keyboardGrid.setOrientation(LinearLayout.VERTICAL);
            keyboardGrid.setGravity(Gravity.CENTER);

            // Ряд 1: 7 8 9
            LinearLayout row1 = createKeyboardRow(new String[]{"7", "8", "9"});
            keyboardGrid.addView(row1);
            
            // Ряд 2: 4 5 6
            LinearLayout row2 = createKeyboardRow(new String[]{"4", "5", "6"});
            keyboardGrid.addView(row2);
            
            // Ряд 3: 1 2 3
            LinearLayout row3 = createKeyboardRow(new String[]{"1", "2", "3"});
            keyboardGrid.addView(row3);
            
            // Ряд 4: 0 ⌫ ✓
            LinearLayout row4 = new LinearLayout(this);
            row4.setOrientation(LinearLayout.HORIZONTAL);
            row4.setGravity(Gravity.CENTER);
            row4.setPadding(0, 4, 0, 4);
            
            String[] row4Keys = {"0", "⌫", "✓"};
            for (String key : row4Keys) {
                Button keyBtn = new Button(this);
                keyBtn.setText(key);
                keyBtn.setTextColor(Color.WHITE);
                keyBtn.setTextSize(22);
                keyBtn.setTypeface(null, android.graphics.Typeface.BOLD);
                
                GradientDrawable keyBg = new GradientDrawable();
                keyBg.setCornerRadius(12);
                if (key.equals("⌫")) {
                    keyBg.setColor(0xFFFF4444);
                } else if (key.equals("✓")) {
                    keyBg.setColor(0xFF00AA00);
                } else {
                    keyBg.setColor(0xFF333333);
                    keyBg.setStroke(2, 0xFFFF4444);
                }
                keyBtn.setBackground(keyBg);
                
                LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(
                        80, 80);
                keyParams.setMargins(6, 6, 6, 6);
                keyBtn.setLayoutParams(keyParams);

                keyBtn.setOnClickListener(v -> handleKeyboardKey(key));
                row4.addView(keyBtn);
            }
            keyboardGrid.addView(row4);

            mainLayout.addView(keyboardGrid);

            // Кнопка отмены
            Button cancelBtn = new Button(this);
            cancelBtn.setText("✕ ОТМЕНА");
            cancelBtn.setTextColor(Color.WHITE);
            cancelBtn.setTextSize(14);
            cancelBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable cancelBg = new GradientDrawable();
            cancelBg.setCornerRadius(16);
            cancelBg.setColor(0xFF444444);
            cancelBtn.setBackground(cancelBg);
            cancelBtn.setPadding(20, 12, 20, 12);
            cancelBtn.setOnClickListener(v -> closeKeyboard());
            
            LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            cancelParams.setMargins(0, 12, 0, 0);
            cancelBtn.setLayoutParams(cancelParams);
            mainLayout.addView(cancelBtn);

            FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            containerParams.gravity = Gravity.CENTER;
            keyboardDialog.addView(mainLayout, containerParams);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.CENTER;

            windowManager.addView(keyboardDialog, params);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private LinearLayout createKeyboardRow(String[] keys) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, 4, 0, 4);
        
        for (String key : keys) {
            Button keyBtn = new Button(this);
            keyBtn.setText(key);
            keyBtn.setTextColor(Color.WHITE);
            keyBtn.setTextSize(22);
            keyBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            
            GradientDrawable keyBg = new GradientDrawable();
            keyBg.setCornerRadius(12);
            keyBg.setColor(0xFF333333);
            keyBg.setStroke(2, 0xFFFF4444);
            keyBtn.setBackground(keyBg);
            
            LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(
                    80, 80);
            keyParams.setMargins(6, 6, 6, 6);
            keyBtn.setLayoutParams(keyParams);

            keyBtn.setOnClickListener(v -> handleKeyboardKey(key));
            row.addView(keyBtn);
        }
        
        return row;
    }

    private void handleKeyboardKey(String key) {
        try {
            if (key.equals("⌫")) {
                if (!keyboardValue.isEmpty()) {
                    keyboardValue = keyboardValue.substring(0, keyboardValue.length() - 1);
                }
            } else if (key.equals("✓")) {
                if (keyboardCallback != null) {
                    keyboardCallback.run();
                }
                closeKeyboard();
                return;
            } else {
                if (keyboardValue.length() < keyboardMaxLength) {
                    keyboardValue += key;
                }
            }
            
            if (keyboardDisplay != null) {
                keyboardDisplay.setText(keyboardValue.isEmpty() ? "0" : keyboardValue);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void closeKeyboard() {
        try {
            if (keyboardDialog != null && windowManager != null) {
                windowManager.removeView(keyboardDialog);
                keyboardDialog = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== WEBVIEW ОКНО ====================

    private void showWebViewWindow() {
        try {
            if (windowManager == null) return;

            FrameLayout container = new FrameLayout(this);
            container.setBackgroundColor(0xFF0A0A0A);

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
            container.addView(webView);

            ImageButton closeBtn = new ImageButton(this);
            closeBtn.setImageDrawable(createCloseIcon());
            closeBtn.setBackgroundColor(0xCC000000);
            closeBtn.setPadding(12, 12, 12, 12);
            closeBtn.setOnClickListener(v -> {
                if (windowManager != null) windowManager.removeView(container);
            });

            FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(50, 50);
            closeParams.gravity = Gravity.TOP | Gravity.END;
            closeParams.setMargins(0, 16, 16, 0);
            container.addView(closeBtn, closeParams);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    (int)(getScreenWidth() * 0.85),
                    (int)(getScreenHeight() * 0.7),
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.CENTER;

            windowManager.addView(container, params);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Drawable createCloseIcon() {
        Bitmap b = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4);
        c.drawLine(10, 10, 30, 30, p);
        c.drawLine(30, 10, 10, 30, p);
        return new android.graphics.drawable.BitmapDrawable(getResources(), b);
    }

    // ==================== ПЕРСОНАЖИ ОКНО ====================

    private void showCharactersWindow() {
        try {
            if (windowManager == null) return;

            LinearLayout mainLayout = new LinearLayout(this);
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setPadding(16, 16, 16, 16);
            mainLayout.setBackgroundColor(0xDD0D0D0D);

            GradientDrawable border = new GradientDrawable();
            border.setCornerRadius(16);
            border.setColor(0xDD0D0D0D);
            border.setStroke(3, 0xFFFF0000);
            mainLayout.setBackground(border);

            TextView title = new TextView(this);
            title.setText("👤 ПЕРСОНАЖИ");
            title.setTextColor(0xFFFF0000);
            title.setTextSize(20);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, 0, 0, 16);
            mainLayout.addView(title);

            final LinearLayout listContainer = new LinearLayout(this);
            listContainer.setOrientation(LinearLayout.VERTICAL);
            listContainer.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
            
            ScrollView scrollView = new ScrollView(this);
            scrollView.addView(listContainer);
            mainLayout.addView(scrollView);

            LinearLayout btnLayout = new LinearLayout(this);
            btnLayout.setOrientation(LinearLayout.HORIZONTAL);
            btnLayout.setGravity(Gravity.CENTER);
            btnLayout.setPadding(0, 12, 0, 0);

            Button addBtn = new Button(this);
            addBtn.setText("➕ ДОБАВИТЬ");
            addBtn.setTextColor(Color.WHITE);
            addBtn.setTextSize(14);
            GradientDrawable addBg = new GradientDrawable();
            addBg.setCornerRadius(16);
            addBg.setColor(0xFFFF0000);
            addBtn.setBackground(addBg);
            addBtn.setPadding(20, 12, 20, 12);
            addBtn.setOnClickListener(v -> showAddCharacterDialog(listContainer));
            btnLayout.addView(addBtn);

            Button closeBtn = new Button(this);
            closeBtn.setText("✕ ЗАКРЫТЬ");
            closeBtn.setTextColor(Color.WHITE);
            closeBtn.setTextSize(14);
            GradientDrawable closeBg = new GradientDrawable();
            closeBg.setCornerRadius(16);
            closeBg.setColor(0xFF333333);
            closeBtn.setBackground(closeBg);
            closeBtn.setPadding(20, 12, 20, 12);
            closeBtn.setOnClickListener(v -> {
                if (windowManager != null) windowManager.removeView(mainLayout);
            });
            btnLayout.addView(closeBtn);

            mainLayout.addView(btnLayout);

            updateCharactersUI(listContainer);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    400, 500,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.CENTER;

            windowManager.addView(mainLayout, params);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateCharactersUI(LinearLayout container) {
        container.removeAllViews();

        loadCharacters();

        if (characters.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("📭 Нет персонажей");
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
            itemBg.setStroke(2, 0xFFFF0000);
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
        Bitmap b = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.GRAY);
        c.drawCircle(25, 25, 20, p);
        p.setColor(Color.WHITE);
        p.setTextSize(25);
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

    // ==================== МАКРОСЫ ОКНО ====================

    private void showMacrosWindow() {
        try {
            if (windowManager == null) return;

            LinearLayout mainLayout = new LinearLayout(this);
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setPadding(16, 16, 16, 16);
            mainLayout.setBackgroundColor(0xDD0D0D0D);

            GradientDrawable border = new GradientDrawable();
            border.setCornerRadius(16);
            border.setColor(0xDD0D0D0D);
            border.setStroke(3, 0xFFFF0000);
            mainLayout.setBackground(border);

            TextView title = new TextView(this);
            title.setText("🎯 МАКРОСЫ");
            title.setTextColor(0xFFFF0000);
            title.setTextSize(20);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, 0, 0, 12);
            mainLayout.addView(title);

            // Выбор макроса
            LinearLayout selectLayout = new LinearLayout(this);
            selectLayout.setOrientation(LinearLayout.HORIZONTAL);
            selectLayout.setGravity(Gravity.CENTER);
            selectLayout.setPadding(0, 8, 0, 8);

            Button prevBtn = new Button(this);
            prevBtn.setText("◄");
            prevBtn.setTextColor(Color.WHITE);
            prevBtn.setTextSize(18);
            prevBtn.setBackgroundColor(0x33FF0000);
            prevBtn.setPadding(12, 4, 12, 4);
            prevBtn.setOnClickListener(v -> {
                int idx = getMacroIndex(currentMacroName);
                if (idx > 0) {
                    currentMacroName = macroConfigs.get(idx - 1).name;
                    updateMacroUI(mainLayout);
                }
            });
            selectLayout.addView(prevBtn);

            final TextView macroNameText = new TextView(this);
            macroNameText.setText(currentMacroName);
            macroNameText.setTextColor(0xFFFF0000);
            macroNameText.setTextSize(16);
            macroNameText.setTypeface(null, android.graphics.Typeface.BOLD);
            macroNameText.setPadding(12, 0, 12, 0);
            selectLayout.addView(macroNameText);

            Button nextBtn = new Button(this);
            nextBtn.setText("►");
            nextBtn.setTextColor(Color.WHITE);
            nextBtn.setTextSize(18);
            nextBtn.setBackgroundColor(0x33FF0000);
            nextBtn.setPadding(12, 4, 12, 4);
            nextBtn.setOnClickListener(v -> {
                int idx = getMacroIndex(currentMacroName);
                if (idx < macroConfigs.size() - 1) {
                    currentMacroName = macroConfigs.get(idx + 1).name;
                    updateMacroUI(mainLayout);
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
            pointsContainer.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
            
            ScrollView scrollView = new ScrollView(this);
            scrollView.addView(pointsContainer);
            mainLayout.addView(scrollView);

            // Кнопки управления
            LinearLayout controlLayout = new LinearLayout(this);
            controlLayout.setOrientation(LinearLayout.HORIZONTAL);
            controlLayout.setGravity(Gravity.CENTER);
            controlLayout.setPadding(0, 8, 0, 8);

            Button addPointBtn = createStyledButton("➕", 0xFFFF0000);
            addPointBtn.setOnClickListener(v -> startMacroRecording(mainLayout));
            controlLayout.addView(addPointBtn);

            Button startBtn = createStyledButton("▶", 0xFF00AA00);
            startBtn.setOnClickListener(v -> startMacroExecution(currentMacroName));
            controlLayout.addView(startBtn);

            Button stopBtn = createStyledButton("■", 0xFFFF0000);
            stopBtn.setOnClickListener(v -> stopMacroExecution());
            controlLayout.addView(stopBtn);

            Button clearBtn = createStyledButton("✕", 0xFFFF8800);
            clearBtn.setOnClickListener(v -> clearMacroPoints(mainLayout));
            controlLayout.addView(clearBtn);

            mainLayout.addView(controlLayout);

            // Настройка повторов с клавиатурой
            LinearLayout settingsLayout = new LinearLayout(this);
            settingsLayout.setOrientation(LinearLayout.HORIZONTAL);
            settingsLayout.setGravity(Gravity.CENTER);
            settingsLayout.setPadding(0, 4, 0, 4);

            TextView repeatLabel = new TextView(this);
            repeatLabel.setText("🔄 ЦИКЛ:");
            repeatLabel.setTextColor(Color.WHITE);
            repeatLabel.setTextSize(14);
            repeatLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            settingsLayout.addView(repeatLabel);

            final TextView repeatDisplay = new TextView(this);
            MacroConfig config = getCurrentMacro();
            repeatDisplay.setText(String.valueOf(config != null ? config.repeatCount : 1));
            repeatDisplay.setTextColor(0xFFFF0000);
            repeatDisplay.setTextSize(20);
            repeatDisplay.setTypeface(null, android.graphics.Typeface.BOLD);
            repeatDisplay.setPadding(12, 0, 12, 0);
            repeatDisplay.setBackgroundColor(0x33000000);
            repeatDisplay.setOnClickListener(v -> {
                showKeyboardDialog("🔄 КОЛИЧЕСТВО ПОВТОРОВ", 
                    repeatDisplay.getText().toString(), 3,
                    () -> {
                        try {
                            int count = Integer.parseInt(keyboardValue);
                            MacroConfig cfg = getCurrentMacro();
                            if (cfg != null) {
                                cfg.repeatCount = Math.max(1, count);
                                saveMacroConfigs();
                                repeatDisplay.setText(String.valueOf(cfg.repeatCount));
                                updateMacroUI(mainLayout);
                                Toast.makeText(this, "Цикл: " + cfg.repeatCount + " раз", Toast.LENGTH_SHORT).show();
                            }
                        } catch (NumberFormatException e) {}
                    });
            });
            settingsLayout.addView(repeatDisplay);

            mainLayout.addView(settingsLayout);

            // Кнопки быстрого запуска
            LinearLayout quickLayout = new LinearLayout(this);
            quickLayout.setOrientation(LinearLayout.HORIZONTAL);
            quickLayout.setGravity(Gravity.CENTER);
            quickLayout.setPadding(0, 8, 0, 8);

            Button quickBtn = new Button(this);
            quickBtn.setText("🚀 СОЗДАТЬ КНОПКУ");
            quickBtn.setTextColor(Color.WHITE);
            quickBtn.setTextSize(12);
            quickBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable quickBg = new GradientDrawable();
            quickBg.setCornerRadius(12);
            quickBg.setColor(0xFF00AA00);
            quickBg.setAlpha(200);
            quickBtn.setBackground(quickBg);
            quickBtn.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            quickBtn.setOnClickListener(v -> createQuickButton(currentMacroName));
            quickLayout.addView(quickBtn);

            Button editQuickBtn = new Button(this);
            editQuickBtn.setText("✏️ РЕДАКТИРОВАТЬ");
            editQuickBtn.setTextColor(Color.WHITE);
            editQuickBtn.setTextSize(12);
            editQuickBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable editBg = new GradientDrawable();
            editBg.setCornerRadius(12);
            editBg.setColor(0xFFFF8800);
            editBg.setAlpha(200);
            editQuickBtn.setBackground(editBg);
            editQuickBtn.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            editQuickBtn.setOnClickListener(v -> showEditQuickButtonDialog());
            quickLayout.addView(editQuickBtn);

            Button removeQuickBtn = new Button(this);
            removeQuickBtn.setText("🗑 УДАЛИТЬ");
            removeQuickBtn.setTextColor(Color.WHITE);
            removeQuickBtn.setTextSize(12);
            removeQuickBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable removeBg = new GradientDrawable();
            removeBg.setCornerRadius(12);
            removeBg.setColor(0xFFFF0000);
            removeBg.setAlpha(200);
            removeQuickBtn.setBackground(removeBg);
            removeQuickBtn.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            removeQuickBtn.setOnClickListener(v -> showRemoveQuickButtonDialog());
            quickLayout.addView(removeQuickBtn);

            mainLayout.addView(quickLayout);

            // Кнопка сохранения
            Button saveBtn = new Button(this);
            saveBtn.setText("💾 СОХРАНИТЬ ВСЁ");
            saveBtn.setTextColor(Color.WHITE);
            saveBtn.setTextSize(14);
            saveBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable saveBg = new GradientDrawable();
            saveBg.setCornerRadius(12);
            saveBg.setColor(0xFFFFAA00);
            saveBg.setAlpha(200);
            saveBtn.setBackground(saveBg);
            saveBtn.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            saveBtn.setOnClickListener(v -> {
                saveMacroConfigs();
                saveQuickButtons();
                Toast.makeText(this, "Всё сохранено!", Toast.LENGTH_SHORT).show();
            });
            mainLayout.addView(saveBtn);

            // Кнопка закрытия
            Button closeBtn = new Button(this);
            closeBtn.setText("✕ ЗАКРЫТЬ");
            closeBtn.setTextColor(Color.WHITE);
            closeBtn.setTextSize(14);
            closeBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable closeBg = new GradientDrawable();
            closeBg.setCornerRadius(12);
            closeBg.setColor(0xFF333333);
            closeBtn.setBackground(closeBg);
            closeBtn.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            closeBtn.setOnClickListener(v -> {
                if (windowManager != null) windowManager.removeView(mainLayout);
            });
            mainLayout.addView(closeBtn);

            updateMacroUI(mainLayout);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    420, 600,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.CENTER;

            windowManager.addView(mainLayout, params);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateMacroUI(LinearLayout mainLayout) {
        MacroConfig config = getCurrentMacro();
        if (config == null) return;

        LinearLayout pointsContainer = null;
        for (int i = 0; i < mainLayout.getChildCount(); i++) {
            View v = mainLayout.getChildAt(i);
            if (v instanceof LinearLayout && ((LinearLayout) v).getChildCount() > 0) {
                LinearLayout ll = (LinearLayout) v;
                if (ll.getChildAt(0) instanceof ScrollView) {
                    pointsContainer = (LinearLayout) ((ScrollView) ll.getChildAt(0)).getChildAt(0);
                    break;
                }
            }
        }

        if (pointsContainer == null) return;
        pointsContainer.removeAllViews();

        // Обновляем имя макроса
        for (int i = 0; i < mainLayout.getChildCount(); i++) {
            View v = mainLayout.getChildAt(i);
            if (v instanceof LinearLayout) {
                LinearLayout ll = (LinearLayout) v;
                for (int j = 0; j < ll.getChildCount(); j++) {
                    View child = ll.getChildAt(j);
                    if (child instanceof TextView && ((TextView) child).getText().toString().equals(currentMacroName)) {
                        ((TextView) child).setText(currentMacroName);
                        break;
                    }
                }
            }
        }

        TextView header = new TextView(this);
        header.setText("📌 Точки: " + config.points.size() + " | 🔄 Цикл: " + config.repeatCount);
        header.setTextColor(Color.WHITE);
        header.setTextSize(13);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setPadding(0, 0, 0, 8);
        pointsContainer.addView(header);

        if (config.points.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Нет точек. Нажмите ➕ для записи");
            empty.setTextColor(0xFF888888);
            empty.setTextSize(12);
            empty.setPadding(0, 20, 0, 20);
            empty.setGravity(Gravity.CENTER);
            pointsContainer.addView(empty);
        } else {
            for (int i = 0; i < config.points.size(); i++) {
                MacroPoint p = config.points.get(i);
                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.HORIZONTAL);
                item.setGravity(Gravity.CENTER_VERTICAL);
                item.setPadding(4, 4, 4, 4);
                GradientDrawable itemBg = new GradientDrawable();
                itemBg.setCornerRadius(8);
                itemBg.setColor(0x22FF0000);
                itemBg.setStroke(1, 0xFFFF0000);
                item.setBackground(itemBg);

                TextView info = new TextView(this);
                String delayText = MacroPoint.formatDelay(p.delayMs);
                info.setText(" #" + (i+1) + " (" + p.x + "," + p.y + ") " + delayText);
                info.setTextColor(Color.WHITE);
                info.setTextSize(11);
                info.setLayoutParams(new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                item.addView(info);

                Button delayBtn = new Button(this);
                delayBtn.setText("⏱");
                delayBtn.setTextColor(0xFFFFAA00);
                delayBtn.setTextSize(14);
                delayBtn.setBackgroundColor(0x33FFAA00);
                delayBtn.setPadding(8, 4, 8, 4);
                final int idx = i;
                delayBtn.setOnClickListener(v -> showDelayDialogWithKeyboard(idx, mainLayout));
                item.addView(delayBtn);

                Button delBtn = new Button(this);
                delBtn.setText("✕");
                delBtn.setTextColor(0xFFFF0000);
                delBtn.setTextSize(12);
                delBtn.setBackgroundColor(0x33FF0000);
                delBtn.setPadding(8, 4, 8, 4);
                delBtn.setOnClickListener(v -> {
                    config.points.remove(idx);
                    saveMacroConfigs();
                    updateMacroUI(mainLayout);
                });
                item.addView(delBtn);

                pointsContainer.addView(item);
            }
        }
    }

    private int getMacroIndex(String name) {
        for (int i = 0; i < macroConfigs.size(); i++) {
            if (macroConfigs.get(i).name.equals(name)) return i;
        }
        return 0;
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

    private Button createStyledButton(String text, int color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(14);
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
            macroConfigs.add(config);
            currentMacroName = name;
            saveMacroConfigs();
            updateMacroUI(mainLayout);
            Toast.makeText(this, "Макрос создан", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    // ==================== ЗАПИСЬ МАКРОСА ====================

    private void startMacroRecording(final LinearLayout mainLayout) {
        if (isMacroRecording) return;
        if (windowManager == null) return;
        
        isMacroRecording = true;
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
                addMacroPoint(x, y, mainLayout);
                return true;
            }
            return false;
        });
        
        windowManager.addView(captureOverlay, captureParams);
    }

    private void addMacroPoint(int x, int y, LinearLayout mainLayout) {
        MacroConfig config = getCurrentMacro();
        if (config == null) return;

        MacroPoint point = new MacroPoint(x, y);
        config.points.add(point);
        saveMacroConfigs();

        Toast.makeText(this, "Точка " + config.points.size() + ": " + x + ", " + y, Toast.LENGTH_SHORT).show();

        showDelayDialogWithKeyboard(config.points.size() - 1, mainLayout);

        if (captureOverlay != null && windowManager != null) {
            windowManager.removeView(captureOverlay);
            captureOverlay = null;
            isMacroRecording = false;
        }

        updateMacroUI(mainLayout);
    }

    private void showDelayDialogWithKeyboard(final int index, final LinearLayout mainLayout) {
        MacroConfig config = getCurrentMacro();
        if (config == null || index >= config.points.size()) return;

        long currentDelay = config.points.get(index).delayMs;
        String displayValue = String.valueOf(currentDelay / 1000);
        if (currentDelay < 1000) displayValue = String.valueOf(currentDelay);

        showKeyboardDialog("⏱ ЗАДЕРЖКА (сек)", displayValue, 5, () -> {
            try {
                long value = Long.parseLong(keyboardValue);
                long delayMs = value * 1000;
                if (delayMs < 100) delayMs = 100;
                MacroConfig cfg = getCurrentMacro();
                if (cfg != null && index < cfg.points.size()) {
                    cfg.points.get(index).delayMs = delayMs;
                    cfg.points.get(index).delayDisplay = MacroPoint.formatDelay(delayMs);
                    saveMacroConfigs();
                    updateMacroUI(mainLayout);
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Ошибка", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void clearMacroPoints(LinearLayout mainLayout) {
        MacroConfig config = getCurrentMacro();
        if (config != null && !config.points.isEmpty()) {
            config.points.clear();
            saveMacroConfigs();
            updateMacroUI(mainLayout);
            Toast.makeText(this, "Все точки удалены", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Нет точек для удаления", Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== ВЫПОЛНЕНИЕ МАКРОСА ====================

    private void startMacroExecution(String macroName) {
        MacroConfig config = null;
        for (MacroConfig c : macroConfigs) {
            if (c.name.equals(macroName)) {
                config = c;
                break;
            }
        }
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
        currentRepeat = 0;
        macroRepeatCount = config.repeatCount;
        Toast.makeText(this, "🚀 Макрос запущен! Циклов: " + macroRepeatCount, Toast.LENGTH_SHORT).show();
        executeNextPoint(config);
    }

    private void executeNextPoint(MacroConfig config) {
        if (!isMacroRunning) {
            stopMacroExecution();
            return;
        }

        if (currentMacroIndex >= config.points.size()) {
            currentRepeat++;
            if (currentRepeat < macroRepeatCount) {
                currentMacroIndex = 0;
                Toast.makeText(this, "🔄 Цикл " + (currentRepeat + 1) + "/" + macroRepeatCount, Toast.LENGTH_SHORT).show();
                executeNextPoint(config);
                return;
            } else {
                stopMacroExecution();
                Toast.makeText(this, "✅ Макрос завершён! Циклов: " + macroRepeatCount, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        MacroPoint point = config.points.get(currentMacroIndex);
        performClick(point.x, point.y);

        currentMacroIndex++;
        
        macroHandler.postDelayed(() -> {
            executeNextPoint(config);
        }, point.delayMs);
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
        Toast.makeText(this, "⏹ Макрос остановлен", Toast.LENGTH_SHORT).show();
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

    // ==================== БЫСТРЫЕ КНОПКИ МАКРОСОВ ====================

    private void createQuickButtonUI(String macroName) {
        try {
            if (windowManager == null) return;
            
            if (quickButtons.containsKey(macroName)) {
                QuickMacroButton btn = quickButtons.get(macroName);
                if (btn.container != null && btn.container.getParent() != null) {
                    return;
                }
                createQuickButton(macroName);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createQuickButton(String macroName) {
        try {
            if (windowManager == null) return;

            // Проверяем, существует ли уже кнопка
            if (quickButtons.containsKey(macroName)) {
                QuickMacroButton existing = quickButtons.get(macroName);
                if (existing.container != null && existing.container.getParent() != null) {
                    Toast.makeText(this, "Кнопка уже существует", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            QuickMacroButton btn = new QuickMacroButton(macroName);
            
            btn.container = new FrameLayout(this);
            btn.container.setBackgroundColor(Color.TRANSPARENT);

            // Основная кнопка - квадратная
            FrameLayout buttonFrame = new FrameLayout(this);
            buttonFrame.setBackgroundColor(Color.TRANSPARENT);

            // Иконка
            TextView iconView = new TextView(this);
            iconView.setText("▶");
            iconView.setTextColor(Color.WHITE);
            iconView.setTextSize(28);
            iconView.setGravity(Gravity.CENTER);

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(0xFF00AA00);
            bg.setStroke(3, 0xFF00FF00);
            buttonFrame.setBackground(bg);
            buttonFrame.addView(iconView);

            // Если закреплена - показываем замок
            if (btn.isFixed) {
                TextView lockIcon = new TextView(this);
                lockIcon.setText("🔒");
                lockIcon.setTextColor(Color.WHITE);
                lockIcon.setTextSize(12);
                lockIcon.setGravity(Gravity.TOP | Gravity.END);
                FrameLayout.LayoutParams lockParams = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT);
                lockParams.gravity = Gravity.TOP | Gravity.END;
                lockIcon.setLayoutParams(lockParams);
                buttonFrame.addView(lockIcon);
            }

            btn.container.addView(buttonFrame, new FrameLayout.LayoutParams(
                    btn.size, btn.size));

            btn.params = new WindowManager.LayoutParams(
                    btn.size + 10, btn.size + 10,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            btn.params.gravity = Gravity.TOP | Gravity.START;
            btn.params.x = 100 + (int)(Math.random() * 200);
            btn.params.y = 100 + (int)(Math.random() * 300);

            btn.container.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (btn.isFixed) {
                        // Если закреплена - только запуск макроса
                        if (event.getAction() == MotionEvent.ACTION_UP) {
                            startMacroExecution(btn.macroName);
                        }
                        return true;
                    }

                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            btn.lastTouchX = event.getRawX();
                            btn.lastTouchY = event.getRawY();
                            btn.startX = btn.params.x;
                            btn.startY = btn.params.y;
                            btn.isDragging = false;
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            float dx = event.getRawX() - btn.lastTouchX;
                            float dy = event.getRawY() - btn.lastTouchY;
                            if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                                btn.isDragging = true;
                            }
                            if (btn.isDragging && !btn.isFixed) {
                                btn.params.x = btn.startX + (int) dx;
                                btn.params.y = btn.startY + (int) dy;
                                if (windowManager != null) {
                                    windowManager.updateViewLayout(btn.container, btn.params);
                                }
                            }
                            return true;
                        case MotionEvent.ACTION_UP:
                            if (!btn.isDragging && !btn.isFixed) {
                                startMacroExecution(btn.macroName);
                            }
                            return true;
                    }
                    return false;
                }
            });

            windowManager.addView(btn.container, btn.params);
            quickButtons.put(macroName, btn);
            saveQuickButtons();
            Toast.makeText(this, "✅ Быстрая кнопка создана для: " + macroName, Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка создания кнопки", Toast.LENGTH_SHORT).show();
        }
    }

    private void showEditQuickButtonDialog() {
        if (quickButtons.isEmpty()) {
            Toast.makeText(this, "Нет быстрых кнопок", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] names = quickButtons.keySet().toArray(new String[0]);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("✏️ РЕДАКТИРОВАНИЕ КНОПКИ");
        builder.setItems(names, (dialog, which) -> {
            String name = names[which];
            showEditQuickButtonOptions(name);
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void showEditQuickButtonOptions(final String macroName) {
        final QuickMacroButton btn = quickButtons.get(macroName);
        if (btn == null || btn.container == null) {
            Toast.makeText(this, "Кнопка не найдена", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("✏️ " + macroName);

        String[] options;
        if (btn.isFixed) {
            options = new String[]{"🔓 Разблокировать", "📐 Размер (" + btn.size + "px)", "🗑 Удалить"};
        } else {
            options = new String[]{"🔒 Закрепить", "📐 Размер (" + btn.size + "px)", "🗑 Удалить"};
        }

        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    btn.isFixed = !btn.isFixed;
                    // Обновляем UI кнопки
                    if (btn.container != null) {
                        btn.container.removeAllViews();
                        FrameLayout buttonFrame = new FrameLayout(this);
                        TextView iconView = new TextView(this);
                        iconView.setText("▶");
                        iconView.setTextColor(Color.WHITE);
                        iconView.setTextSize(28);
                        iconView.setGravity(Gravity.CENTER);
                        GradientDrawable bg = new GradientDrawable();
                        bg.setShape(GradientDrawable.OVAL);
                        bg.setColor(btn.isFixed ? 0xFFFF8800 : 0xFF00AA00);
                        bg.setStroke(3, btn.isFixed ? 0xFFFFAA00 : 0xFF00FF00);
                        buttonFrame.setBackground(bg);
                        buttonFrame.addView(iconView);
                        
                        if (btn.isFixed) {
                            TextView lockIcon = new TextView(this);
                            lockIcon.setText("🔒");
                            lockIcon.setTextColor(Color.WHITE);
                            lockIcon.setTextSize(12);
                            lockIcon.setGravity(Gravity.TOP | Gravity.END);
                            FrameLayout.LayoutParams lockParams = new FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.WRAP_CONTENT,
                                    FrameLayout.LayoutParams.WRAP_CONTENT);
                            lockParams.gravity = Gravity.TOP | Gravity.END;
                            lockIcon.setLayoutParams(lockParams);
                            buttonFrame.addView(lockIcon);
                        }
                        
                        btn.container.addView(buttonFrame, new FrameLayout.LayoutParams(
                                btn.size, btn.size));
                    }
                    Toast.makeText(this, btn.isFixed ? "🔒 Кнопка закреплена" : "🔓 Кнопка разблокирована", Toast.LENGTH_SHORT).show();
                    saveQuickButtons();
                    break;
                case 1:
                    showKeyboardDialog("📐 РАЗМЕР КНОПКИ", String.valueOf(btn.size), 3, () -> {
                        try {
                            int newSize = Integer.parseInt(keyboardValue);
                            newSize = Math.max(40, Math.min(120, newSize));
                            btn.size = newSize;
                            btn.params.width = newSize + 10;
                            btn.params.height = newSize + 10;
                            
                            // Обновляем UI
                            if (btn.container != null) {
                                btn.container.removeAllViews();
                                FrameLayout buttonFrame = new FrameLayout(this);
                                TextView iconView = new TextView(this);
                                iconView.setText("▶");
                                iconView.setTextColor(Color.WHITE);
                                iconView.setTextSize(newSize > 60 ? 28 : 20);
                                iconView.setGravity(Gravity.CENTER);
                                GradientDrawable bg = new GradientDrawable();
                                bg.setShape(GradientDrawable.OVAL);
                                bg.setColor(btn.isFixed ? 0xFFFF8800 : 0xFF00AA00);
                                bg.setStroke(3, btn.isFixed ? 0xFFFFAA00 : 0xFF00FF00);
                                buttonFrame.setBackground(bg);
                                buttonFrame.addView(iconView);
                                
                                if (btn.isFixed) {
                                    TextView lockIcon = new TextView(this);
                                    lockIcon.setText("🔒");
                                    lockIcon.setTextColor(Color.WHITE);
                                    lockIcon.setTextSize(12);
                                    lockIcon.setGravity(Gravity.TOP | Gravity.END);
                                    FrameLayout.LayoutParams lockParams = new FrameLayout.LayoutParams(
                                            FrameLayout.LayoutParams.WRAP_CONTENT,
                                            FrameLayout.LayoutParams.WRAP_CONTENT);
                                    lockParams.gravity = Gravity.TOP | Gravity.END;
                                    lockIcon.setLayoutParams(lockParams);
                                    buttonFrame.addView(lockIcon);
                                }
                                
                                btn.container.addView(buttonFrame, new FrameLayout.LayoutParams(
                                        btn.size, btn.size));
                            }
                            
                            if (windowManager != null && btn.container != null) {
                                windowManager.updateViewLayout(btn.container, btn.params);
                            }
                            saveQuickButtons();
                            Toast.makeText(this, "📐 Размер: " + newSize + "px", Toast.LENGTH_SHORT).show();
                        } catch (NumberFormatException e) {}
                    });
                    break;
                case 2:
                    removeQuickButton(macroName);
                    break;
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void showRemoveQuickButtonDialog() {
        if (quickButtons.isEmpty()) {
            Toast.makeText(this, "Нет быстрых кнопок", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] names = quickButtons.keySet().toArray(new String[0]);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🗑 УДАЛИТЬ КНОПКУ");
        builder.setItems(names, (dialog, which) -> {
            String name = names[which];
            removeQuickButton(name);
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void removeQuickButton(String macroName) {
        try {
            QuickMacroButton btn = quickButtons.remove(macroName);
            if (btn != null && btn.container != null && windowManager != null) {
                windowManager.removeView(btn.container);
                saveQuickButtons();
                Toast.makeText(this, "🗑 Кнопка удалена", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== НАСТРОЙКИ ====================

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⚙️ НАСТРОЙКИ");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        TextView sizeLabel = new TextView(this);
        int currentSize = prefs.getInt("overlay_size", 80);
        sizeLabel.setText("📐 Размер оверлея: " + currentSize + "px");
        sizeLabel.setTextColor(Color.WHITE);
        layout.addView(sizeLabel);

        SeekBar sizeSeek = new SeekBar(this);
        sizeSeek.setMax(160);
        sizeSeek.setMin(40);
        sizeSeek.setProgress(currentSize);
        sizeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                prefs.edit().putInt("overlay_size", progress).apply();
                overlaySize = progress;
                sizeLabel.setText("📐 Размер оверлея: " + progress + "px");
                if (!isAppInForeground) {
                    removeMainCircle();
                    createMainCircle();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        layout.addView(sizeSeek);

        TextView alphaLabel = new TextView(this);
        int currentAlpha = prefs.getInt("overlay_alpha", 255);
        alphaLabel.setText("🔆 Прозрачность: " + (currentAlpha * 100 / 255) + "%");
        alphaLabel.setTextColor(Color.WHITE);
        layout.addView(alphaLabel);

        SeekBar alphaSeek = new SeekBar(this);
        alphaSeek.setMax(255);
        alphaSeek.setProgress(currentAlpha);
        alphaSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                prefs.edit().putInt("overlay_alpha", progress).apply();
                overlayAlpha = progress;
                alphaLabel.setText("🔆 Прозрачность: " + (progress * 100 / 255) + "%");
                if (!isAppInForeground) {
                    removeMainCircle();
                    createMainCircle();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        layout.addView(alphaSeek);

        builder.setView(layout);
        builder.setPositiveButton("✕ Закрыть", null);
        builder.show();
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    private int getOverlayFlag() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;
    }

    private int getScreenWidth() {
        DisplayMetrics metrics = new DisplayMetrics();
        if (windowManager != null) {
            windowManager.getDefaultDisplay().getMetrics(metrics);
            return metrics.widthPixels;
        }
        return 1080;
    }

    private int getScreenHeight() {
        DisplayMetrics metrics = new DisplayMetrics();
        if (windowManager != null) {
            windowManager.getDefaultDisplay().getMetrics(metrics);
            return metrics.heightPixels;
        }
        return 1920;
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
            removeMainCircle();
            if (captureOverlay != null && windowManager != null) {
                windowManager.removeView(captureOverlay);
            }
            macroHandler.removeCallbacksAndMessages(null);
            closeKeyboard();
            
            for (QuickMacroButton btn : quickButtons.values()) {
                if (btn.container != null && windowManager != null) {
                    windowManager.removeView(btn.container);
                }
            }
            quickButtons.clear();
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
