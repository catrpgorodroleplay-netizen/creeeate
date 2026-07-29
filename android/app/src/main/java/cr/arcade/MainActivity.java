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
import android.graphics.Typeface;
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
    private static final int REQUEST_IMPORT_CONFIG = 999;

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
    private String currentMacroName = "Скрипт 1";
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

    // GPS координаты
    private FrameLayout gpsOverlay;
    private TextView gpsCoordsText;
    private boolean isGpsMode = false;
    private int gpsTargetLine = -1;
    private String gpsTargetMacro = "";

    // Редактор скриптов
    private LinearLayout scriptEditorContainer;
    private EditText scriptInput;
    private TextView scriptPreview;
    private int editingLineIndex = -1;

    // Перетаскивание окон
    private boolean isWindowDragging = false;
    private float windowDragStartX, windowDragStartY;
    private int windowDragStartParamsX, windowDragStartParamsY;
    private View currentDraggingWindow;

    private enum ActionType {
        CLICK,
        SWIPE,
        DELAY
    }

    private static class MacroCommand {
        String command;
        int delayMs;
        int x, y;
        int endX, endY;
        int swipeDuration;
        
        MacroCommand(String command) {
            this.command = command;
            this.delayMs = 1000;
            this.x = 0;
            this.y = 0;
            this.endX = 0;
            this.endY = 0;
            this.swipeDuration = 300;
        }
        
        MacroCommand(JSONObject json) throws Exception {
            this.command = json.getString("command");
            this.delayMs = json.optInt("delayMs", 1000);
            this.x = json.optInt("x", 0);
            this.y = json.optInt("y", 0);
            this.endX = json.optInt("endX", 0);
            this.endY = json.optInt("endY", 0);
            this.swipeDuration = json.optInt("swipeDuration", 300);
        }
        
        JSONObject toJSON() throws Exception {
            JSONObject json = new JSONObject();
            json.put("command", command);
            json.put("delayMs", delayMs);
            json.put("x", x);
            json.put("y", y);
            json.put("endX", endX);
            json.put("endY", endY);
            json.put("swipeDuration", swipeDuration);
            return json;
        }
        
        String toScriptLine() {
            if (command.equals("DELAY")) {
                return "DELAY " + delayMs;
            } else if (command.equals("CLICK")) {
                return "CLICK " + x + "," + y + " DELAY " + delayMs;
            } else if (command.equals("SWIPE")) {
                return "SWIPE " + x + "," + y + " TO " + endX + "," + endY + " DURATION " + swipeDuration + " DELAY " + delayMs;
            }
            return "";
        }
        
        static MacroCommand fromScriptLine(String line) {
            line = line.trim();
            if (line.isEmpty()) return null;
            
            String[] parts = line.split("\\s+");
            if (parts.length < 2) return null;
            
            MacroCommand cmd = new MacroCommand(parts[0]);
            
            if (parts[0].equals("DELAY")) {
                try {
                    cmd.delayMs = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {}
                return cmd;
            } else if (parts[0].equals("CLICK")) {
                String[] coords = parts[1].split(",");
                if (coords.length >= 2) {
                    try {
                        cmd.x = Integer.parseInt(coords[0]);
                        cmd.y = Integer.parseInt(coords[1]);
                    } catch (NumberFormatException e) {}
                }
                for (int i = 2; i < parts.length; i++) {
                    if (parts[i].equals("DELAY") && i + 1 < parts.length) {
                        try {
                            cmd.delayMs = Integer.parseInt(parts[i + 1]);
                        } catch (NumberFormatException e) {}
                        break;
                    }
                }
                return cmd;
            } else if (parts[0].equals("SWIPE")) {
                String[] fromCoords = parts[1].split(",");
                if (fromCoords.length >= 2) {
                    try {
                        cmd.x = Integer.parseInt(fromCoords[0]);
                        cmd.y = Integer.parseInt(fromCoords[1]);
                    } catch (NumberFormatException e) {}
                }
                for (int i = 2; i < parts.length; i++) {
                    if (parts[i].equals("TO") && i + 1 < parts.length) {
                        String[] toCoords = parts[i + 1].split(",");
                        if (toCoords.length >= 2) {
                            try {
                                cmd.endX = Integer.parseInt(toCoords[0]);
                                cmd.endY = Integer.parseInt(toCoords[1]);
                            } catch (NumberFormatException e) {}
                        }
                    }
                    if (parts[i].equals("DURATION") && i + 1 < parts.length) {
                        try {
                            cmd.swipeDuration = Integer.parseInt(parts[i + 1]);
                        } catch (NumberFormatException e) {}
                    }
                    if (parts[i].equals("DELAY") && i + 1 < parts.length) {
                        try {
                            cmd.delayMs = Integer.parseInt(parts[i + 1]);
                        } catch (NumberFormatException e) {}
                    }
                }
                return cmd;
            }
            return null;
        }
    }

    private static class MacroConfig {
        String name;
        ArrayList<MacroCommand> commands;
        int repeatCount;
        String scriptText;
        
        MacroConfig(String name) {
            this.name = name;
            this.commands = new ArrayList<>();
            this.repeatCount = 1;
            this.scriptText = "";
        }
        
        MacroConfig(JSONObject json) throws Exception {
            this.name = json.getString("name");
            this.repeatCount = json.optInt("repeatCount", 1);
            this.scriptText = json.optString("scriptText", "");
            this.commands = new ArrayList<>();
            JSONArray commandsArray = json.getJSONArray("commands");
            for (int i = 0; i < commandsArray.length(); i++) {
                this.commands.add(new MacroCommand(commandsArray.getJSONObject(i)));
            }
            if (this.commands.isEmpty() && !this.scriptText.isEmpty()) {
                parseScript();
            }
        }
        
        JSONObject toJSON() throws Exception {
            JSONObject json = new JSONObject();
            json.put("name", name);
            json.put("repeatCount", repeatCount);
            json.put("scriptText", scriptText);
            JSONArray commandsArray = new JSONArray();
            for (MacroCommand cmd : commands) {
                commandsArray.put(cmd.toJSON());
            }
            json.put("commands", commandsArray);
            return json;
        }
        
        void parseScript() {
            commands.clear();
            String[] lines = scriptText.split("\n");
            for (String line : lines) {
                MacroCommand cmd = MacroCommand.fromScriptLine(line);
                if (cmd != null) {
                    commands.add(cmd);
                }
            }
        }
        
        String buildScript() {
            StringBuilder sb = new StringBuilder();
            for (MacroCommand cmd : commands) {
                sb.append(cmd.toScriptLine()).append("\n");
            }
            scriptText = sb.toString().trim();
            return scriptText;
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
        String shape;
        int color1, color2;
        boolean useGradient;
        String text;
        float alpha;
        int borderColor;
        int borderWidth;
        int textColor;
        float textSize;
        
        QuickMacroButton(String macroName) {
            this.macroName = macroName;
            this.size = 80;
            this.isFixed = false;
            this.isDragging = false;
            this.shape = "square";
            this.color1 = 0xFF00AA00;
            this.color2 = 0xFF008800;
            this.useGradient = true;
            this.text = "▶";
            this.alpha = 1.0f;
            this.borderColor = 0xFF00FF00;
            this.borderWidth = 3;
            this.textColor = 0xFFFFFFFF;
            this.textSize = 24;
        }
        
        QuickMacroButton(JSONObject json) throws Exception {
            this.macroName = json.getString("macroName");
            this.isFixed = json.optBoolean("isFixed", false);
            this.size = json.optInt("size", 80);
            this.shape = json.optString("shape", "square");
            this.color1 = json.optInt("color1", 0xFF00AA00);
            this.color2 = json.optInt("color2", 0xFF008800);
            this.useGradient = json.optBoolean("useGradient", true);
            this.text = json.optString("text", "▶");
            this.alpha = (float) json.optDouble("alpha", 1.0);
            this.borderColor = json.optInt("borderColor", 0xFF00FF00);
            this.borderWidth = json.optInt("borderWidth", 3);
            this.textColor = json.optInt("textColor", 0xFFFFFFFF);
            this.textSize = (float) json.optDouble("textSize", 24);
        }
        
        JSONObject toJSON() throws Exception {
            JSONObject json = new JSONObject();
            json.put("macroName", macroName);
            json.put("isFixed", isFixed);
            json.put("size", size);
            json.put("shape", shape);
            json.put("color1", color1);
            json.put("color2", color2);
            json.put("useGradient", useGradient);
            json.put("text", text);
            json.put("alpha", alpha);
            json.put("borderColor", borderColor);
            json.put("borderWidth", borderWidth);
            json.put("textColor", textColor);
            json.put("textSize", textSize);
            json.put("x", params != null ? params.x : 100);
            json.put("y", params != null ? params.y : 200);
            return json;
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
                macroConfigs.add(new MacroConfig("Скрипт 1"));
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
                    QuickMacroButton btn = new QuickMacroButton(obj);
                    btn.params = new WindowManager.LayoutParams(
                            btn.size + 10, btn.size + 10,
                            getOverlayFlag(),
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                            PixelFormat.TRANSLUCENT
                    );
                    btn.params.gravity = Gravity.TOP | Gravity.START;
                    btn.params.x = obj.optInt("x", 100);
                    btn.params.y = obj.optInt("y", 200);
                    quickButtons.put(name, btn);
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
                JSONObject obj = btn.toJSON();
                if (btn.params != null) {
                    obj.put("x", btn.params.x);
                    obj.put("y", btn.params.y);
                }
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
            String[] labels = {"Web", "Скрипт", "Перс", "Настр", "Закр"};

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

    // ==================== КЛАВИАТУРА ДЛЯ РЕДАКТОРА ====================

    private void showScriptKeyboard(String title, String initialValue, int maxLength, Runnable callback) {
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
            mainLayout.setPadding(20, 20, 20, 20);

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(24);
            bg.setColor(0xFF1A1A1A);
            bg.setStroke(3, 0xFFFF0000);
            mainLayout.setBackground(bg);

            TextView titleView = new TextView(this);
            titleView.setText(title);
            titleView.setTextColor(0xFFFF0000);
            titleView.setTextSize(18);
            titleView.setTypeface(null, android.graphics.Typeface.BOLD);
            titleView.setGravity(Gravity.CENTER);
            titleView.setPadding(0, 0, 0, 12);
            mainLayout.addView(titleView);

            keyboardDisplay = new TextView(this);
            keyboardDisplay.setText(initialValue.isEmpty() ? "" : initialValue);
            keyboardDisplay.setTextColor(Color.WHITE);
            keyboardDisplay.setTextSize(24);
            keyboardDisplay.setTypeface(Typeface.MONOSPACE);
            keyboardDisplay.setGravity(Gravity.CENTER);
            keyboardDisplay.setPadding(8, 16, 8, 16);
            keyboardDisplay.setMinHeight(80);
            
            GradientDrawable displayBg = new GradientDrawable();
            displayBg.setCornerRadius(12);
            displayBg.setColor(0x44000000);
            displayBg.setStroke(2, 0xFFFF4444);
            keyboardDisplay.setBackground(displayBg);
            
            FrameLayout.LayoutParams displayParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            displayParams.setMargins(0, 0, 0, 12);
            keyboardDisplay.setLayoutParams(displayParams);
            mainLayout.addView(keyboardDisplay);

            // Клавиатура для скриптов
            LinearLayout keyboardGrid = new LinearLayout(this);
            keyboardGrid.setOrientation(LinearLayout.VERTICAL);
            keyboardGrid.setGravity(Gravity.CENTER);

            // Строка 1: Цифры и команды
            String[] row1 = {"1", "2", "3", "4", "5", "DELAY", "CLICK"};
            keyboardGrid.addView(createScriptKeyboardRow(row1));
            
            // Строка 2: Цифры и команды
            String[] row2 = {"6", "7", "8", "9", "0", "SWIPE", "TO"};
            keyboardGrid.addView(createScriptKeyboardRow(row2));
            
            // Строка 3: Управление
            String[] row3 = {",", " ", "(", ")", "DURATION", "⌫", "✓"};
            keyboardGrid.addView(createScriptKeyboardRow(row3));

            mainLayout.addView(keyboardGrid);

            // Подсказка
            TextView hint = new TextView(this);
            hint.setText("Пример: CLICK 100,200 DELAY 500");
            hint.setTextColor(0xFF888888);
            hint.setTextSize(12);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(0, 8, 0, 8);
            mainLayout.addView(hint);

            Button cancelBtn = new Button(this);
            cancelBtn.setText("✕ ОТМЕНА");
            cancelBtn.setTextColor(Color.WHITE);
            cancelBtn.setTextSize(14);
            cancelBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable cancelBg2 = new GradientDrawable();
            cancelBg2.setCornerRadius(16);
            cancelBg2.setColor(0xFF444444);
            cancelBtn.setBackground(cancelBg2);
            cancelBtn.setPadding(20, 12, 20, 12);
            cancelBtn.setOnClickListener(v -> closeKeyboard());
            
            LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            cancelParams.setMargins(0, 8, 0, 0);
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

    private LinearLayout createScriptKeyboardRow(String[] keys) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, 2, 0, 2);
        
        for (String key : keys) {
            Button keyBtn = new Button(this);
            keyBtn.setText(key);
            keyBtn.setTextColor(Color.WHITE);
            keyBtn.setTextSize(key.length() > 3 ? 14 : 20);
            keyBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            
            GradientDrawable keyBg = new GradientDrawable();
            keyBg.setCornerRadius(8);
            if (key.equals(" ")) {
                keyBg.setColor(0xFF444444);
                keyBtn.setWidth(120);
            } else if (key.equals("⌫")) {
                keyBg.setColor(0xFFFF4444);
            } else if (key.equals("✓")) {
                keyBg.setColor(0xFF00AA00);
            } else if (key.equals("DELAY") || key.equals("CLICK") || key.equals("SWIPE") || key.equals("TO") || key.equals("DURATION")) {
                keyBg.setColor(0xFF0066CC);
                keyBg.setStroke(1, 0xFF4488FF);
            } else {
                keyBg.setColor(0xFF333333);
                keyBg.setStroke(1, 0xFFFF4444);
            }
            keyBtn.setBackground(keyBg);
            
            LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(
                    key.length() > 3 ? 80 : 55, 55);
            keyParams.setMargins(3, 3, 3, 3);
            keyBtn.setLayoutParams(keyParams);

            keyBtn.setOnClickListener(v -> handleScriptKeyboardKey(key));
            row.addView(keyBtn);
        }
        
        return row;
    }

    private void handleScriptKeyboardKey(String key) {
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
            } else if (key.equals(" ")) {
                keyboardValue += " ";
            } else {
                if (keyboardValue.length() < keyboardMaxLength) {
                    keyboardValue += key;
                }
            }
            
            if (keyboardDisplay != null) {
                keyboardDisplay.setText(keyboardValue.isEmpty() ? "" : keyboardValue);
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

    // ==================== GPS КООРДИНАТЫ ====================

    private void startGpsMode(String macroName, int lineIndex) {
        if (windowManager == null) return;
        
        isGpsMode = true;
        gpsTargetMacro = macroName;
        gpsTargetLine = lineIndex;
        
        Toast.makeText(this, "🟣 Нажмите на кнопку для получения координат", Toast.LENGTH_LONG).show();
        
        gpsOverlay = new FrameLayout(this);
        gpsOverlay.setBackgroundColor(0x44AA00FF);
        
        // Фиолетовая иконка GPS
        FrameLayout gpsIcon = new FrameLayout(this);
        TextView gpsText = new TextView(this);
        gpsText.setText("📍");
        gpsText.setTextSize(48);
        gpsText.setTextColor(0xFFFF00FF);
        gpsText.setGravity(Gravity.CENTER);
        gpsIcon.addView(gpsText);
        
        gpsOverlay.addView(gpsIcon, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // Отображение координат
        gpsCoordsText = new TextView(this);
        gpsCoordsText.setText("Нажмите на экран");
        gpsCoordsText.setTextColor(Color.WHITE);
        gpsCoordsText.setTextSize(18);
        gpsCoordsText.setTypeface(null, android.graphics.Typeface.BOLD);
        gpsCoordsText.setGravity(Gravity.CENTER);
        gpsCoordsText.setBackgroundColor(0x88000000);
        gpsCoordsText.setPadding(20, 12, 20, 12);
        
        FrameLayout.LayoutParams coordsParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        coordsParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        coordsParams.setMargins(0, 0, 0, 80);
        gpsOverlay.addView(gpsCoordsText, coordsParams);

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
        
        gpsOverlay.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP && isGpsMode) {
                int x = (int) event.getRawX();
                int y = (int) event.getRawY();
                
                if (gpsCoordsText != null) {
                    gpsCoordsText.setText("Координаты: " + x + ", " + y);
                }
                
                showGpsCoordsDialog(x, y);
                return true;
            }
            return false;
        });
        
        windowManager.addView(gpsOverlay, captureParams);
    }

    private void showGpsCoordsDialog(final int x, final int y) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("📍 GPS КООРДИНАТЫ");
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);
        
        TextView coordsView = new TextView(this);
        coordsView.setText("X: " + x + "\nY: " + y);
        coordsView.setTextColor(Color.WHITE);
        coordsView.setTextSize(20);
        coordsView.setTypeface(null, android.graphics.Typeface.BOLD);
        coordsView.setGravity(Gravity.CENTER);
        coordsView.setPadding(0, 20, 0, 20);
        layout.addView(coordsView);
        
        builder.setView(layout);
        builder.setPositiveButton("ВСТАВИТЬ", (d, w) -> {
            insertGpsCoords(x, y);
        });
        builder.setNegativeButton("ОТМЕНА", (d, w) -> {
            if (gpsOverlay != null && windowManager != null) {
                windowManager.removeView(gpsOverlay);
                gpsOverlay = null;
                isGpsMode = false;
            }
        });
        builder.setNeutralButton("ПОВТОРНО", (d, w) -> {
            if (gpsCoordsText != null) {
                gpsCoordsText.setText("Нажмите на экран");
            }
        });
        builder.show();
    }

    private void insertGpsCoords(int x, int y) {
        if (gpsTargetMacro != null && !gpsTargetMacro.isEmpty()) {
            MacroConfig config = getMacroConfig(gpsTargetMacro);
            if (config != null) {
                String[] lines = config.scriptText.split("\n");
                if (gpsTargetLine >= 0 && gpsTargetLine < lines.length) {
                    String line = lines[gpsTargetLine];
                    if (line.contains("CLICK") && !line.contains(",")) {
                        lines[gpsTargetLine] = "CLICK " + x + "," + y + " " + 
                            line.substring(line.indexOf("CLICK") + 5).trim();
                    } else if (line.contains("SWIPE") && !line.contains(",")) {
                        lines[gpsTargetLine] = "SWIPE " + x + "," + y + " " + 
                            line.substring(line.indexOf("SWIPE") + 5).trim();
                    } else {
                        String newLine = "CLICK " + x + "," + y + " DELAY 1000";
                        String[] newLines = new String[lines.length + 1];
                        for (int i = 0; i < lines.length; i++) {
                            if (i == gpsTargetLine) {
                                newLines[i] = lines[i];
                                newLines[i + 1] = newLine;
                            } else if (i > gpsTargetLine) {
                                newLines[i + 1] = lines[i];
                            } else {
                                newLines[i] = lines[i];
                            }
                        }
                        lines = newLines;
                    }
                    config.scriptText = String.join("\n", lines);
                    config.parseScript();
                    saveMacroConfigs();
                    Toast.makeText(this, "✅ Координаты вставлены: " + x + "," + y, Toast.LENGTH_SHORT).show();
                }
            }
        }
        
        if (gpsOverlay != null && windowManager != null) {
            windowManager.removeView(gpsOverlay);
            gpsOverlay = null;
            isGpsMode = false;
        }
        
        if (scriptEditorContainer != null && scriptEditorContainer.getVisibility() == View.VISIBLE) {
            updateScriptEditor();
        }
    }

    private MacroConfig getMacroConfig(String name) {
        for (MacroConfig c : macroConfigs) {
            if (c.name.equals(name)) {
                return c;
            }
        }
        return null;
    }

    // ==================== ПЕРЕТАСКИВАНИЕ ОКОН ====================

    private void setupWindowDragging(final View view, final WindowManager.LayoutParams params) {
        view.setOnTouchListener(new View.OnTouchListener() {
            float touchStartX, touchStartY;
            int paramStartX, paramStartY;
            boolean isDraggingWindow = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        touchStartX = event.getRawX();
                        touchStartY = event.getRawY();
                        paramStartX = params.x;
                        paramStartY = params.y;
                        isDraggingWindow = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - touchStartX;
                        float dy = event.getRawY() - touchStartY;
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isDraggingWindow = true;
                        }
                        if (isDraggingWindow) {
                            params.x = paramStartX + (int) dx;
                            params.y = paramStartY + (int) dy;
                            if (windowManager != null) {
                                windowManager.updateViewLayout(view, params);
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        return isDraggingWindow;
                }
                return false;
            }
        });
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

            LinearLayout titleBar = new LinearLayout(this);
            titleBar.setOrientation(LinearLayout.HORIZONTAL);
            titleBar.setGravity(Gravity.CENTER_VERTICAL);
            titleBar.setPadding(16, 8, 16, 8);
            titleBar.setBackgroundColor(0x66000000);
            
            TextView titleText = new TextView(this);
            titleText.setText("🌐 WebView");
            titleText.setTextColor(0xFFFF0000);
            titleText.setTextSize(16);
            titleText.setTypeface(null, android.graphics.Typeface.BOLD);
            titleText.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            titleBar.addView(titleText);
            
            ImageButton closeBtn = new ImageButton(this);
            closeBtn.setImageDrawable(createCloseIcon());
            closeBtn.setBackgroundColor(Color.TRANSPARENT);
            closeBtn.setPadding(8, 4, 8, 4);
            closeBtn.setOnClickListener(v -> {
                if (windowManager != null) windowManager.removeView(container);
            });
            titleBar.addView(closeBtn);
            
            container.addView(titleBar, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT));

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
            setupWindowDragging(container, params);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Drawable createCloseIcon() {
        Bitmap b = Bitmap.createBitmap(30, 30, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3);
        c.drawLine(8, 8, 22, 22, p);
        c.drawLine(22, 8, 8, 22, p);
        return new android.graphics.drawable.BitmapDrawable(getResources(), b);
    }

    // ==================== ПЕРСОНАЖИ ОКНО ====================

    private void showCharactersWindow() {
        try {
            if (windowManager == null) return;

            FrameLayout wrapper = new FrameLayout(this);
            
            LinearLayout mainLayout = new LinearLayout(this);
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setPadding(16, 16, 16, 16);
            mainLayout.setBackgroundColor(0xDD0D0D0D);

            GradientDrawable border = new GradientDrawable();
            border.setCornerRadius(16);
            border.setColor(0xDD0D0D0D);
            border.setStroke(3, 0xFFFF0000);
            mainLayout.setBackground(border);

            LinearLayout titleBar = new LinearLayout(this);
            titleBar.setOrientation(LinearLayout.HORIZONTAL);
            titleBar.setGravity(Gravity.CENTER_VERTICAL);
            titleBar.setPadding(8, 8, 8, 8);
            
            TextView title = new TextView(this);
            title.setText("👤 ПЕРСОНАЖИ");
            title.setTextColor(0xFFFF0000);
            title.setTextSize(18);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            titleBar.addView(title);
            
            ImageButton closeBtn = new ImageButton(this);
            closeBtn.setImageDrawable(createCloseIcon());
            closeBtn.setBackgroundColor(Color.TRANSPARENT);
            closeBtn.setPadding(8, 4, 8, 4);
            closeBtn.setOnClickListener(v -> {
                if (windowManager != null) windowManager.removeView(wrapper);
            });
            titleBar.addView(closeBtn);
            
            mainLayout.addView(titleBar);

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

            mainLayout.addView(btnLayout);

            updateCharactersUI(listContainer);

            wrapper.addView(mainLayout);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    400, 500,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.CENTER;

            windowManager.addView(wrapper, params);
            setupWindowDragging(wrapper, params);

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

    // ==================== РЕДАКТОР СКРИПТОВ ====================

    private void showMacrosWindow() {
        try {
            if (windowManager == null) return;

            FrameLayout wrapper = new FrameLayout(this);
            
            LinearLayout mainLayout = new LinearLayout(this);
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setPadding(16, 16, 16, 16);
            mainLayout.setBackgroundColor(0xDD0D0D0D);

            GradientDrawable border = new GradientDrawable();
            border.setCornerRadius(16);
            border.setColor(0xDD0D0D0D);
            border.setStroke(3, 0xFFFF0000);
            mainLayout.setBackground(border);

            // Заголовок
            LinearLayout titleBar = new LinearLayout(this);
            titleBar.setOrientation(LinearLayout.HORIZONTAL);
            titleBar.setGravity(Gravity.CENTER_VERTICAL);
            titleBar.setPadding(8, 8, 8, 8);
            
            TextView title = new TextView(this);
            title.setText("📝 РЕДАКТОР СКРИПТОВ");
            title.setTextColor(0xFFFF0000);
            title.setTextSize(16);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            titleBar.addView(title);
            
            ImageButton closeBtn = new ImageButton(this);
            closeBtn.setImageDrawable(createCloseIcon());
            closeBtn.setBackgroundColor(Color.TRANSPARENT);
            closeBtn.setPadding(8, 4, 8, 4);
            closeBtn.setOnClickListener(v -> {
                if (windowManager != null) windowManager.removeView(wrapper);
            });
            titleBar.addView(closeBtn);
            
            mainLayout.addView(titleBar);

            // Выбор скрипта
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
                    updateScriptEditor();
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
                    updateScriptEditor();
                }
            });
            selectLayout.addView(nextBtn);

            Button newMacroBtn = new Button(this);
            newMacroBtn.setText("+");
            newMacroBtn.setTextColor(Color.WHITE);
            newMacroBtn.setTextSize(18);
            newMacroBtn.setBackgroundColor(0xFFFF0000);
            newMacroBtn.setPadding(12, 4, 12, 4);
            newMacroBtn.setOnClickListener(v -> showNewMacroDialog());
            selectLayout.addView(newMacroBtn);

            mainLayout.addView(selectLayout);

            // Редактор
            scriptEditorContainer = new LinearLayout(this);
            scriptEditorContainer.setOrientation(LinearLayout.VERTICAL);
            scriptEditorContainer.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

            // Подсказка
            TextView hint = new TextView(this);
            hint.setText("Команды: DELAY, CLICK, SWIPE\nПример: CLICK 100,200 DELAY 500");
            hint.setTextColor(0xFF888888);
            hint.setTextSize(11);
            hint.setPadding(0, 4, 0, 4);
            scriptEditorContainer.addView(hint);

            // Текстовое поле для скрипта
            scriptInput = new EditText(this);
            scriptInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            scriptInput.setTextColor(Color.WHITE);
            scriptInput.setTextSize(14);
            scriptInput.setTypeface(Typeface.MONOSPACE);
            scriptInput.setBackgroundColor(0x22000000);
            scriptInput.setPadding(12, 12, 12, 12);
            scriptInput.setMinHeight(200);
            
            ScrollView scrollView = new ScrollView(this);
            scrollView.addView(scriptInput);
            scriptEditorContainer.addView(scrollView);

            // Кнопки для редактирования
            LinearLayout editLayout = new LinearLayout(this);
            editLayout.setOrientation(LinearLayout.HORIZONTAL);
            editLayout.setGravity(Gravity.CENTER);
            editLayout.setPadding(0, 8, 0, 8);

            Button gpsBtn = new Button(this);
            gpsBtn.setText("📍 GPS");
            gpsBtn.setTextColor(Color.WHITE);
            gpsBtn.setTextSize(12);
            gpsBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable gpsBg = new GradientDrawable();
            gpsBg.setCornerRadius(12);
            gpsBg.setColor(0xFFAA00FF);
            gpsBtn.setBackground(gpsBg);
            gpsBtn.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            gpsBtn.setOnClickListener(v -> {
                int line = getCurrentLine();
                if (line >= 0) {
                    startGpsMode(currentMacroName, line);
                } else {
                    Toast.makeText(this, "Установите курсор на строку", Toast.LENGTH_SHORT).show();
                }
            });
            editLayout.addView(gpsBtn);

            Button runBtn = new Button(this);
            runBtn.setText("▶ ЗАПУСТИТЬ");
            runBtn.setTextColor(Color.WHITE);
            runBtn.setTextSize(12);
            runBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable runBg = new GradientDrawable();
            runBg.setCornerRadius(12);
            runBg.setColor(0xFF00AA00);
            runBtn.setBackground(runBg);
            runBtn.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            runBtn.setOnClickListener(v -> {
                saveScript();
                startMacroExecution(currentMacroName);
            });
            editLayout.addView(runBtn);

            Button stopBtn = new Button(this);
            stopBtn.setText("■ СТОП");
            stopBtn.setTextColor(Color.WHITE);
            stopBtn.setTextSize(12);
            stopBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable stopBg = new GradientDrawable();
            stopBg.setCornerRadius(12);
            stopBg.setColor(0xFFFF0000);
            stopBtn.setBackground(stopBg);
            stopBtn.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            stopBtn.setOnClickListener(v -> stopMacroExecution());
            editLayout.addView(stopBtn);

            mainLayout.addView(editLayout);

            // Цикл
            LinearLayout cycleLayout = new LinearLayout(this);
            cycleLayout.setOrientation(LinearLayout.HORIZONTAL);
            cycleLayout.setGravity(Gravity.CENTER);
            cycleLayout.setPadding(0, 4, 0, 4);

            TextView cycleLabel = new TextView(this);
            cycleLabel.setText("🔄 ЦИКЛ:");
            cycleLabel.setTextColor(Color.WHITE);
            cycleLabel.setTextSize(14);
            cycleLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            cycleLayout.addView(cycleLabel);

            final TextView cycleDisplay = new TextView(this);
            MacroConfig config = getCurrentMacro();
            cycleDisplay.setText(String.valueOf(config != null ? config.repeatCount : 1));
            cycleDisplay.setTextColor(0xFFFF0000);
            cycleDisplay.setTextSize(20);
            cycleDisplay.setTypeface(null, android.graphics.Typeface.BOLD);
            cycleDisplay.setPadding(12, 0, 12, 0);
            cycleDisplay.setBackgroundColor(0x33000000);
            cycleDisplay.setOnClickListener(v -> {
                showScriptKeyboard("🔄 КОЛИЧЕСТВО ПОВТОРОВ", 
                    cycleDisplay.getText().toString(), 3,
                    () -> {
                        try {
                            int count = Integer.parseInt(keyboardValue);
                            MacroConfig cfg = getCurrentMacro();
                            if (cfg != null) {
                                cfg.repeatCount = Math.max(1, count);
                                saveMacroConfigs();
                                cycleDisplay.setText(String.valueOf(cfg.repeatCount));
                                Toast.makeText(this, "Цикл: " + cfg.repeatCount + " раз", Toast.LENGTH_SHORT).show();
                            }
                        } catch (NumberFormatException e) {}
                    });
            });
            cycleLayout.addView(cycleDisplay);

            mainLayout.addView(cycleLayout);

            // Кнопка сохранения
            Button saveBtn = new Button(this);
            saveBtn.setText("💾 СОХРАНИТЬ");
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
                saveScript();
                Toast.makeText(this, "Скрипт сохранён!", Toast.LENGTH_SHORT).show();
            });
            mainLayout.addView(saveBtn);

            // Кнопка быстрого запуска
            Button quickBtn = new Button(this);
            quickBtn.setText("🚀 СОЗДАТЬ КНОПКУ");
            quickBtn.setTextColor(Color.WHITE);
            quickBtn.setTextSize(14);
            quickBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable quickBg = new GradientDrawable();
            quickBg.setCornerRadius(12);
            quickBg.setColor(0xFF00AA00);
            quickBg.setAlpha(200);
            quickBtn.setBackground(quickBg);
            quickBtn.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            quickBtn.setOnClickListener(v -> {
                saveScript();
                createQuickButton(currentMacroName);
            });
            mainLayout.addView(quickBtn);

            wrapper.addView(mainLayout);

            updateScriptEditor();

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    480, 650,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.CENTER;

            windowManager.addView(wrapper, params);
            setupWindowDragging(wrapper, params);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateScriptEditor() {
        MacroConfig config = getCurrentMacro();
        if (config == null || scriptInput == null) return;

        String script = config.buildScript();
        scriptInput.setText(script);
        
        for (View v : ((ViewGroup)scriptInput.getParent()).getRootView().getTouchables()) {
            if (v instanceof TextView && ((TextView) v).getText().toString().equals(currentMacroName)) {
                ((TextView) v).setText(currentMacroName);
                break;
            }
        }
    }

    private void saveScript() {
        MacroConfig config = getCurrentMacro();
        if (config == null || scriptInput == null) return;

        String text = scriptInput.getText().toString().trim();
        config.scriptText = text;
        config.parseScript();
        saveMacroConfigs();
    }

    private int getCurrentLine() {
        if (scriptInput == null) return -1;
        int selectionStart = scriptInput.getSelectionStart();
        String text = scriptInput.getText().toString();
        if (selectionStart < 0) return 0;
        
        int lineCount = 0;
        for (int i = 0; i < text.length(); i++) {
            if (i >= selectionStart) {
                return lineCount;
            }
            if (text.charAt(i) == '\n') {
                lineCount++;
            }
        }
        return lineCount;
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
        MacroConfig newC = new MacroConfig("Скрипт 1");
        macroConfigs.add(newC);
        currentMacroName = newC.name;
        return newC;
    }

    private void showNewMacroDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Новый скрипт");
        final EditText input = new EditText(this);
        input.setHint("Имя скрипта");
        input.setText("Скрипт " + (macroConfigs.size() + 1));
        builder.setView(input);
        builder.setPositiveButton("Создать", (d, w) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) name = "Скрипт " + (macroConfigs.size() + 1);
            MacroConfig config = new MacroConfig(name);
            macroConfigs.add(config);
            currentMacroName = name;
            saveMacroConfigs();
            updateScriptEditor();
            Toast.makeText(this, "Скрипт создан", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    // ==================== ВЫПОЛНЕНИЕ СКРИПТА ====================

    private void startMacroExecution(String macroName) {
        MacroConfig config = null;
        for (MacroConfig c : macroConfigs) {
            if (c.name.equals(macroName)) {
                config = c;
                break;
            }
        }
        if (config == null || config.commands.isEmpty()) {
            Toast.makeText(this, "Нет команд для выполнения", Toast.LENGTH_SHORT).show();
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
        Toast.makeText(this, "🚀 Скрипт запущен! Циклов: " + macroRepeatCount, Toast.LENGTH_SHORT).show();
        executeNextCommand(config);
    }

    private void executeNextCommand(MacroConfig config) {
        if (!isMacroRunning) {
            stopMacroExecution();
            return;
        }

        if (currentMacroIndex >= config.commands.size()) {
            currentRepeat++;
            if (currentRepeat < macroRepeatCount) {
                currentMacroIndex = 0;
                Toast.makeText(this, "🔄 Цикл " + (currentRepeat + 1) + "/" + macroRepeatCount, Toast.LENGTH_SHORT).show();
                executeNextCommand(config);
                return;
            } else {
                stopMacroExecution();
                Toast.makeText(this, "✅ Скрипт завершён! Циклов: " + macroRepeatCount, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        MacroCommand cmd = config.commands.get(currentMacroIndex);
        
        if (cmd.command.equals("DELAY")) {
            macroHandler.postDelayed(() -> {
                currentMacroIndex++;
                executeNextCommand(config);
            }, cmd.delayMs);
        } else if (cmd.command.equals("CLICK")) {
            performClick(cmd.x, cmd.y);
            macroHandler.postDelayed(() -> {
                currentMacroIndex++;
                executeNextCommand(config);
            }, cmd.delayMs);
        } else if (cmd.command.equals("SWIPE")) {
            performSwipe(cmd.x, cmd.y, cmd.endX, cmd.endY, cmd.swipeDuration);
            macroHandler.postDelayed(() -> {
                currentMacroIndex++;
                executeNextCommand(config);
            }, cmd.delayMs);
        } else {
            currentMacroIndex++;
            executeNextCommand(config);
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

    private void performSwipe(int startX, int startY, int endX, int endY, long duration) {
        try {
            MacroService service = MacroService.getInstance();
            if (service != null) {
                service.performSwipe(startX, startY, endX, endY, duration);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopMacroExecution() {
        isMacroRunning = false;
        macroHandler.removeCallbacksAndMessages(null);
        Toast.makeText(this, "⏹ Скрипт остановлен", Toast.LENGTH_SHORT).show();
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

    // ==================== БЫСТРЫЕ КНОПКИ ====================

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

            if (quickButtons.containsKey(macroName)) {
                QuickMacroButton existing = quickButtons.get(macroName);
                if (existing.container != null && existing.container.getParent() != null) {
                    Toast.makeText(this, "Кнопка уже существует", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            QuickMacroButton btn = quickButtons.containsKey(macroName) ? 
                quickButtons.get(macroName) : new QuickMacroButton(macroName);
            
            btn.container = new FrameLayout(this);
            btn.container.setBackgroundColor(Color.TRANSPARENT);

            FrameLayout buttonFrame = createSquareButtonFrame(btn);
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
            
            if (btn.params.x == 0 && btn.params.y == 0) {
                btn.params.x = 100 + (int)(Math.random() * 200);
                btn.params.y = 100 + (int)(Math.random() * 300);
            }

            btn.container.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (btn.isFixed) {
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
            Toast.makeText(this, "✅ Кнопка создана для: " + macroName, Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка создания кнопки", Toast.LENGTH_SHORT).show();
        }
    }

    private FrameLayout createSquareButtonFrame(QuickMacroButton btn) {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.TRANSPARENT);

        View buttonView = new View(this);
        
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        
        if (btn.shape.equals("rounded")) {
            drawable.setCornerRadius(btn.size / 5f);
        } else if (btn.shape.equals("circle")) {
            drawable.setShape(GradientDrawable.OVAL);
        } else {
            drawable.setCornerRadius(0);
        }
        
        if (btn.useGradient) {
            drawable.setColors(new int[]{btn.color1, btn.color2});
            drawable.setGradientType(GradientDrawable.LINEAR_GRADIENT);
            drawable.setOrientation(GradientDrawable.Orientation.TL_BR);
        } else {
            drawable.setColor(btn.color1);
        }
        
        drawable.setStroke(btn.borderWidth, btn.borderColor);
        drawable.setAlpha((int)(btn.alpha * 255));
        buttonView.setBackground(drawable);

        FrameLayout.LayoutParams viewParams = new FrameLayout.LayoutParams(
                btn.size, btn.size);
        buttonView.setLayoutParams(viewParams);
        frame.addView(buttonView);

        TextView textView = new TextView(this);
        textView.setText(btn.text);
        textView.setTextColor(btn.textColor);
        textView.setTextSize(btn.textSize);
        textView.setGravity(Gravity.CENTER);
        textView.setPadding(4, 4, 4, 4);
        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
                btn.size, btn.size);
        textView.setLayoutParams(textParams);
        frame.addView(textView);

        if (btn.isFixed) {
            TextView lockIcon = new TextView(this);
            lockIcon.setText("🔒");
            lockIcon.setTextColor(Color.WHITE);
            lockIcon.setTextSize(14);
            lockIcon.setGravity(Gravity.TOP | Gravity.END);
            FrameLayout.LayoutParams lockParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            lockParams.gravity = Gravity.TOP | Gravity.END;
            lockIcon.setLayoutParams(lockParams);
            frame.addView(lockIcon);
        }

        return frame;
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

        Button exportBtn = new Button(this);
        exportBtn.setText("💾 ЭКСПОРТ КОНФИГА");
        exportBtn.setTextColor(Color.WHITE);
        exportBtn.setBackgroundColor(0xFFFF8800);
        exportBtn.setPadding(20, 12, 20, 12);
        exportBtn.setOnClickListener(v -> exportConfig());
        layout.addView(exportBtn);

        Button importBtn = new Button(this);
        importBtn.setText("📂 ИМПОРТ КОНФИГА");
        importBtn.setTextColor(Color.WHITE);
        importBtn.setBackgroundColor(0xFF0088FF);
        importBtn.setPadding(20, 12, 20, 12);
        importBtn.setOnClickListener(v -> importConfig());
        layout.addView(importBtn);

        builder.setView(layout);
        builder.setPositiveButton("✕ Закрыть", null);
        builder.show();
    }

    private void exportConfig() {
        try {
            JSONObject config = new JSONObject();
            
            JSONArray macrosArray = new JSONArray();
            for (MacroConfig c : macroConfigs) {
                macrosArray.put(c.toJSON());
            }
            config.put("macros", macrosArray);
            
            JSONArray buttonsArray = new JSONArray();
            for (String name : quickButtons.keySet()) {
                QuickMacroButton btn = quickButtons.get(name);
                JSONObject obj = btn.toJSON();
                if (btn.params != null) {
                    obj.put("x", btn.params.x);
                    obj.put("y", btn.params.y);
                }
                buttonsArray.put(obj);
            }
            config.put("quick_buttons", buttonsArray);
            
            config.put("overlay_alpha", overlayAlpha);
            config.put("overlay_size", overlaySize);
            
            String jsonString = config.toString(2);
            
            File dir = new File(getExternalFilesDir(null), "configs");
            if (!dir.exists()) dir.mkdirs();
            
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File file = new File(dir, "arcade_config_" + timeStamp + ".json");
            
            FileOutputStream out = new FileOutputStream(file);
            out.write(jsonString.getBytes());
            out.close();
            
            Toast.makeText(this, "✅ Конфиг сохранён: " + file.getName(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "❌ Ошибка экспорта", Toast.LENGTH_SHORT).show();
        }
    }

    private void importConfig() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Выберите файл конфига"), REQUEST_IMPORT_CONFIG);
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
            if (gpsOverlay != null && windowManager != null) {
                windowManager.removeView(gpsOverlay);
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
            
            if (requestCode == REQUEST_IMPORT_CONFIG && resultCode == RESULT_OK && data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    String json = readTextFromUri(uri);
                    if (json != null) {
                        importConfigFromJson(json);
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

    private String readTextFromUri(Uri uri) throws IOException {
        java.io.InputStream inputStream = getContentResolver().openInputStream(uri);
        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(inputStream));
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            stringBuilder.append(line);
        }
        reader.close();
        inputStream.close();
        return stringBuilder.toString();
    }

    private void importConfigFromJson(String json) {
        try {
            JSONObject config = new JSONObject(json);
            
            if (config.has("macros")) {
                JSONArray macrosArray = config.getJSONArray("macros");
                macroConfigs.clear();
                for (int i = 0; i < macrosArray.length(); i++) {
                    macroConfigs.add(new MacroConfig(macrosArray.getJSONObject(i)));
                }
                if (!macroConfigs.isEmpty()) {
                    currentMacroName = macroConfigs.get(0).name;
                }
                saveMacroConfigs();
            }
            
            if (config.has("quick_buttons")) {
                for (QuickMacroButton btn : quickButtons.values()) {
                    if (btn.container != null && windowManager != null) {
                        windowManager.removeView(btn.container);
                    }
                }
                quickButtons.clear();
                
                JSONArray buttonsArray = config.getJSONArray("quick_buttons");
                for (int i = 0; i < buttonsArray.length(); i++) {
                    JSONObject obj = buttonsArray.getJSONObject(i);
                    String name = obj.getString("macroName");
                    QuickMacroButton btn = new QuickMacroButton(obj);
                    btn.params = new WindowManager.LayoutParams(
                            btn.size + 10, btn.size + 10,
                            getOverlayFlag(),
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                            PixelFormat.TRANSLUCENT
                    );
                    btn.params.gravity = Gravity.TOP | Gravity.START;
                    btn.params.x = obj.optInt("x", 100);
                    btn.params.y = obj.optInt("y", 200);
                    quickButtons.put(name, btn);
                    createQuickButtonUI(name);
                }
                saveQuickButtons();
            }
            
            if (config.has("overlay_alpha")) {
                overlayAlpha = config.getInt("overlay_alpha");
                prefs.edit().putInt("overlay_alpha", overlayAlpha).apply();
            }
            if (config.has("overlay_size")) {
                overlaySize = config.getInt("overlay_size");
                prefs.edit().putInt("overlay_size", overlaySize).apply();
            }
            
            Toast.makeText(this, "✅ Конфиг импортирован успешно!", Toast.LENGTH_LONG).show();
            
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "❌ Ошибка импорта конфига", Toast.LENGTH_SHORT).show();
        }
    }
                }
