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
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
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
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.RotateAnimation;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
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
import java.util.UUID;

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

    // МАКРОСЫ
    private HashMap<String, MacroConfig> allMacros = new HashMap<>();
    private ArrayList<String> macroIds = new ArrayList<>();
    private String currentMacroId = "";
    
    private boolean isMacroRecording = false;
    private boolean isMacroRunning = false;
    private Handler macroHandler = new Handler();
    private int currentMacroIndex = 0;
    private int macroRepeatCount = 1;
    private int currentRepeat = 0;
    private FrameLayout captureOverlay;
    
    // Быстрые кнопки макросов на экране (серые)
    private HashMap<String, QuickMacroButton> quickButtons = new HashMap<>();
    private int quickButtonIdCounter = 1;
    private int buttonNumberCounter = 1;

    // GPS координаты
    private FrameLayout gpsOverlay;
    private TextView gpsCoordsText;
    private boolean isGpsMode = false;
    private int gpsTargetLine = -1;
    private String gpsTargetMacroId = "";

    // Клавиатура
    private FrameLayout keyboardDialog;
    private TextView keyboardDisplay;
    private String keyboardValue = "";
    private int keyboardMaxLength = 10;
    private Runnable keyboardCallback;

    private boolean isRecordingSwipe = false;
    private float swipeStartX = 0, swipeStartY = 0;
    private float swipeEndX = 0, swipeEndY = 0;

    // Звуки
    private MediaPlayer clickSound;
    private MediaPlayer deleteSound;
    private MediaPlayer saveSound;

    // Ссылки на UI элементы макросов
    private LinearLayout pointsContainer;
    private TextView macroNameText;
    private TextView repeatDisplay;
    private LinearLayout macrosMainLayout;
    private FrameLayout macrosWrapper;
    
    // Окно логов
    private FrameLayout logsWrapper;
    private LinearLayout logsContainer;
    private TextView logCurrentIndex;
    private Button logPrevBtn, logNextBtn, logCloseBtn;
    private int currentLogIndex = 0;
    private ArrayList<String> logEntries = new ArrayList<>();

    private enum ActionType {
        CLICK, SWIPE
    }

    private enum MacroMode {
        NORMAL, HOLD, TOGGLE
    }

    private static class MacroPoint {
        int x, y;
        long delayMs;
        String delayDisplay;
        ActionType type;
        int endX, endY;
        long swipeDuration;
        
        MacroPoint(int x, int y) {
            this.x = x;
            this.y = y;
            this.delayMs = 1000;
            this.delayDisplay = "1000мс";
            this.type = ActionType.CLICK;
            this.endX = x;
            this.endY = y;
            this.swipeDuration = 300;
        }
        
        MacroPoint(int x, int y, int endX, int endY, long swipeDuration) {
            this.x = x;
            this.y = y;
            this.endX = endX;
            this.endY = endY;
            this.swipeDuration = swipeDuration;
            this.delayMs = 1000;
            this.delayDisplay = "1000мс";
            this.type = ActionType.SWIPE;
        }
        
        MacroPoint(JSONObject json) throws Exception {
            this.x = json.getInt("x");
            this.y = json.getInt("y");
            this.delayMs = json.optLong("delayMs", 1000);
            this.delayDisplay = formatDelay(delayMs);
            this.type = ActionType.valueOf(json.optString("type", "CLICK"));
            this.endX = json.optInt("endX", x);
            this.endY = json.optInt("endY", y);
            this.swipeDuration = json.optLong("swipeDuration", 300);
        }
        
        JSONObject toJSON() throws Exception {
            JSONObject json = new JSONObject();
            json.put("x", x);
            json.put("y", y);
            json.put("delayMs", delayMs);
            json.put("type", type.name());
            json.put("endX", endX);
            json.put("endY", endY);
            json.put("swipeDuration", swipeDuration);
            return json;
        }
        
        static String formatDelay(long ms) {
            return ms + "мс";
        }
        
        String getDisplayText() {
            if (type == ActionType.SWIPE) {
                return "↗ (" + x + "," + y + ") → (" + endX + "," + endY + ")";
            }
            return "(" + x + "," + y + ")";
        }
        
        String getTypeIcon() {
            return type == ActionType.SWIPE ? "↗" : "•";
        }
    }

    private static class MacroConfig {
        String id;
        String name;
        ArrayList<MacroPoint> points;
        int color;
        int repeatCount;
        long totalDelay;
        MacroMode mode;
        boolean isToggled;
        boolean isSavedAsButton;
        
        MacroConfig(String id, String name) {
            this.id = id;
            this.name = name;
            this.points = new ArrayList<>();
            this.color = 0xFFFF0000;
            this.repeatCount = 1;
            this.totalDelay = 0;
            this.mode = MacroMode.NORMAL;
            this.isToggled = false;
            this.isSavedAsButton = false;
        }
        
        MacroConfig(JSONObject json) throws Exception {
            this.id = json.getString("id");
            this.name = json.getString("name");
            this.color = json.optInt("color", 0xFFFF0000);
            this.repeatCount = json.optInt("repeatCount", 1);
            this.totalDelay = json.optLong("totalDelay", 0);
            this.mode = MacroMode.valueOf(json.optString("mode", "NORMAL"));
            this.isToggled = json.optBoolean("isToggled", false);
            this.isSavedAsButton = json.optBoolean("isSavedAsButton", false);
            this.points = new ArrayList<>();
            JSONArray pointsArray = json.getJSONArray("points");
            for (int i = 0; i < pointsArray.length(); i++) {
                this.points.add(new MacroPoint(pointsArray.getJSONObject(i)));
            }
        }
        
        JSONObject toJSON() throws Exception {
            JSONObject json = new JSONObject();
            json.put("id", id);
            json.put("name", name);
            json.put("color", color);
            json.put("repeatCount", repeatCount);
            json.put("totalDelay", totalDelay);
            json.put("mode", mode.name());
            json.put("isToggled", isToggled);
            json.put("isSavedAsButton", isSavedAsButton);
            JSONArray pointsArray = new JSONArray();
            for (MacroPoint p : points) {
                pointsArray.put(p.toJSON());
            }
            json.put("points", pointsArray);
            return json;
        }
    }

    private static class QuickMacroButton {
        String macroId;
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
        boolean rainbowEffect;
        String displayName;
        int id;
        int number;
        boolean isToggled;
        MacroMode mode;
        
        QuickMacroButton(String macroId, int id, int number) {
            this.macroId = macroId;
            this.id = id;
            this.number = number;
            this.size = 140;
            this.isFixed = false;
            this.isDragging = false;
            this.shape = "rounded";
            this.color1 = 0xFF333333;
            this.color2 = 0xFF1A1A1A;
            this.useGradient = true;
            this.text = String.valueOf(number);
            this.alpha = 1.0f;
            this.borderColor = 0xFF555555;
            this.borderWidth = 3;
            this.textColor = 0xFFFFFFFF;
            this.textSize = 36;
            this.rainbowEffect = false;
            this.displayName = String.valueOf(number);
            this.isToggled = false;
            this.mode = MacroMode.NORMAL;
        }
        
        QuickMacroButton(JSONObject json) throws Exception {
            this.macroId = json.getString("macroId");
            this.id = json.optInt("id", 1);
            this.number = json.optInt("number", 1);
            this.isFixed = json.optBoolean("isFixed", false);
            this.size = json.optInt("size", 140);
            this.shape = json.optString("shape", "rounded");
            this.color1 = json.optInt("color1", 0xFF333333);
            this.color2 = json.optInt("color2", 0xFF1A1A1A);
            this.useGradient = json.optBoolean("useGradient", true);
            this.text = json.optString("text", String.valueOf(number));
            this.alpha = (float) json.optDouble("alpha", 1.0);
            this.borderColor = json.optInt("borderColor", 0xFF555555);
            this.borderWidth = json.optInt("borderWidth", 3);
            this.textColor = json.optInt("textColor", 0xFFFFFFFF);
            this.textSize = (float) json.optDouble("textSize", 36);
            this.rainbowEffect = false;
            this.displayName = json.optString("displayName", String.valueOf(number));
            this.isToggled = json.optBoolean("isToggled", false);
            this.mode = MacroMode.valueOf(json.optString("mode", "NORMAL"));
        }
        
        JSONObject toJSON() throws Exception {
            JSONObject json = new JSONObject();
            json.put("macroId", macroId);
            json.put("id", id);
            json.put("number", number);
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
            json.put("rainbowEffect", rainbowEffect);
            json.put("displayName", displayName);
            json.put("isToggled", isToggled);
            json.put("mode", mode.name());
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
            quickButtonIdCounter = prefs.getInt("quick_button_id_counter", 1);
            buttonNumberCounter = prefs.getInt("button_number_counter", 1);
            loadCharacters();
            loadMacroConfigs();
            loadQuickButtons();
            initSounds();
            
            // Восстанавливаем кнопки
            for (String id : macroIds) {
                MacroConfig config = allMacros.get(id);
                if (config != null && config.isSavedAsButton) {
                    createQuickButtonUI(id);
                }
            }
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

    private void initSounds() {
        try {
            clickSound = MediaPlayer.create(this, Settings.System.DEFAULT_NOTIFICATION_URI);
            deleteSound = MediaPlayer.create(this, Settings.System.DEFAULT_NOTIFICATION_URI);
            saveSound = MediaPlayer.create(this, Settings.System.DEFAULT_NOTIFICATION_URI);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void playClickSound() {
        try { if (clickSound != null) clickSound.start(); } catch (Exception e) {}
    }

    private void playDeleteSound() {
        try { if (deleteSound != null) deleteSound.start(); } catch (Exception e) {}
    }

    private void playSaveSound() {
        try { if (saveSound != null) saveSound.start(); } catch (Exception e) {}
    }

    private void vibrate() {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(50);
                }
            }
        } catch (Exception e) {}
    }

    private Animation createPopAnimation() {
        AnimationSet set = new AnimationSet(true);
        ScaleAnimation scale = new ScaleAnimation(0.8f, 1f, 0.8f, 1f, 
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        scale.setDuration(200);
        AlphaAnimation alpha = new AlphaAnimation(0.5f, 1f);
        alpha.setDuration(200);
        set.addAnimation(scale);
        set.addAnimation(alpha);
        return set;
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
            allMacros.clear();
            macroIds.clear();
            String json = prefs.getString("all_macros", "");
            if (!json.isEmpty()) {
                JSONObject macrosObj = new JSONObject(json);
                JSONArray idsArray = macrosObj.getJSONArray("ids");
                JSONObject dataObj = macrosObj.getJSONObject("data");
                
                for (int i = 0; i < idsArray.length(); i++) {
                    String id = idsArray.getString(i);
                    if (dataObj.has(id)) {
                        MacroConfig config = new MacroConfig(dataObj.getJSONObject(id));
                        allMacros.put(id, config);
                        macroIds.add(id);
                    }
                }
            }
            
            if (macroIds.isEmpty()) {
                createDefaultMacro();
            } else {
                boolean found = false;
                for (String id : macroIds) {
                    MacroConfig config = allMacros.get(id);
                    if (config != null && !config.isSavedAsButton) {
                        currentMacroId = id;
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    createDefaultMacro();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createDefaultMacro() {
        String id = UUID.randomUUID().toString();
        MacroConfig config = new MacroConfig(id, "Макрос 1");
        allMacros.put(id, config);
        macroIds.add(id);
        currentMacroId = id;
        saveMacroConfigs();
    }

    private void saveMacroConfigs() {
        try {
            JSONObject macrosObj = new JSONObject();
            JSONArray idsArray = new JSONArray();
            JSONObject dataObj = new JSONObject();
            
            for (String id : macroIds) {
                idsArray.put(id);
                dataObj.put(id, allMacros.get(id).toJSON());
            }
            
            macrosObj.put("ids", idsArray);
            macrosObj.put("data", dataObj);
            
            prefs.edit().putString("all_macros", macrosObj.toString()).apply();
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
                    QuickMacroButton btn = new QuickMacroButton(obj);
                    btn.params = new WindowManager.LayoutParams(
                            btn.size + 20, btn.size + 20,
                            getOverlayFlag(),
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                            PixelFormat.TRANSLUCENT
                    );
                    btn.params.gravity = Gravity.TOP | Gravity.START;
                    btn.params.x = obj.optInt("x", 100);
                    btn.params.y = obj.optInt("y", 200);
                    quickButtons.put(btn.macroId, btn);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveQuickButtons() {
        try {
            JSONArray array = new JSONArray();
            for (String macroId : quickButtons.keySet()) {
                QuickMacroButton btn = quickButtons.get(macroId);
                JSONObject obj = btn.toJSON();
                if (btn.params != null) {
                    obj.put("x", btn.params.x);
                    obj.put("y", btn.params.y);
                }
                array.put(obj);
            }
            prefs.edit().putString("quick_buttons", array.toString()).apply();
            prefs.edit().putInt("quick_button_id_counter", quickButtonIdCounter).apply();
            prefs.edit().putInt("button_number_counter", buttonNumberCounter).apply();
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
            bg.setColor(0xFF1A1A1A);
            bg.setStroke(3, 0xFFFF0000);
            dialogLayout.setBackground(bg);
            
            TextView title = new TextView(this);
            title.setText("РАЗРЕШЕНИЕ ДЛЯ МАКРОСОВ");
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

    // ==================== ГЛАВНЫЙ КРУЖОК С АВАРИЙНОЙ ОСТАНОВКОЙ ====================

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
            d.setColor(0xFF333333);
            d.setStroke(3, 0xFFFF0000);
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
                private int tapCount = 0;
                private Handler delayHandler = new Handler();
                private Runnable menuRunnable;

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
                                    // Отменяем таймер меню если начали перетаскивание
                                    delayHandler.removeCallbacks(menuRunnable);
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
                                    
                                    // Проверяем двойной тап
                                    if (currentTime - lastTapTime < 400) {
                                        tapCount++;
                                        if (tapCount >= 2) {
                                            // ДВОЙНОЙ ТАП - АВАРИЙНАЯ ОСТАНОВКА
                                            delayHandler.removeCallbacks(menuRunnable);
                                            emergencyStopMacros();
                                            tapCount = 0;
                                            return true;
                                        }
                                    } else {
                                        tapCount = 1;
                                    }
                                    lastTapTime = currentTime;
                                    
                                    // Одинарный тап - запуск/остановка макроса
                                    if (isMacroRunning) {
                                        stopMacroExecution();
                                    } else if (!macroIds.isEmpty()) {
                                        for (String id : macroIds) {
                                            MacroConfig config = allMacros.get(id);
                                            if (config != null && !config.isSavedAsButton) {
                                                startMacroExecution(id);
                                                break;
                                            }
                                        }
                                    }
                                    
                                    // Задержка для показа меню (долгое нажатие)
                                    menuRunnable = () -> {
                                        if (!isDragging) {
                                            showWheelMenu();
                                        }
                                    };
                                    delayHandler.postDelayed(menuRunnable, 600);
                                    return true;
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

    // ==================== АВАРИЙНАЯ ОСТАНОВКА МАКРОСОВ ====================

    private void emergencyStopMacros() {
        // Останавливаем выполнение макроса
        isMacroRunning = false;
        macroHandler.removeCallbacksAndMessages(null);
        
        // Отменяем все жесты в сервисе
        MacroService service = MacroService.getInstance();
        if (service != null) {
            service.cancelAllGestures();
        }
        
        // Сбрасываем состояние TOGGLE
        for (String id : macroIds) {
            MacroConfig config = allMacros.get(id);
            if (config != null && config.mode == MacroMode.TOGGLE) {
                config.isToggled = false;
            }
        }
        saveMacroConfigs();
        
        // Обновляем UI кнопок
        for (String id : quickButtons.keySet()) {
            QuickMacroButton btn = quickButtons.get(id);
            if (btn != null && btn.container != null) {
                btn.isToggled = false;
                updateQuickButtonUI(id);
            }
        }
        
        Toast.makeText(this, "⏹ Аварийная остановка макросов", Toast.LENGTH_SHORT).show();
        playDeleteSound();
        vibrate();
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
            wheelBg.setColor(0xFF222222);
            wheelBg.setStroke(3, 0xFFFF0000);
            itemsLayout.setBackground(wheelBg);

            String[] items = {"W", "M", "C", "S", "X"};
            String[] labels = {"Web", "Макросы", "Персоны", "Настройки", "Закрыть"};

            for (int i = 0; i < items.length; i++) {
                final int index = i;
                LinearLayout itemLayout = new LinearLayout(this);
                itemLayout.setOrientation(LinearLayout.VERTICAL);
                itemLayout.setGravity(Gravity.CENTER);
                itemLayout.setPadding(8, 8, 8, 8);

                TextView iconView = new TextView(this);
                iconView.setText(items[i]);
                iconView.setTextSize(24);
                iconView.setTextColor(0xFFFF0000);
                iconView.setTypeface(null, android.graphics.Typeface.BOLD);
                iconView.setGravity(Gravity.CENTER);
                
                TextView labelView = new TextView(this);
                labelView.setText(labels[i]);
                labelView.setTextSize(10);
                labelView.setTextColor(0xFF888888);
                labelView.setGravity(Gravity.CENTER);

                itemLayout.addView(iconView);
                itemLayout.addView(labelView);

                GradientDrawable itemBg = new GradientDrawable();
                itemBg.setShape(GradientDrawable.OVAL);
                itemBg.setColor(0xFF333333);
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

    // ==================== КЛАВИАТУРА ====================

    private void showKeyboardDialog(String title, String initialValue, int maxLength, Runnable callback) {
        try {
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

            TextView titleView = new TextView(this);
            titleView.setText(title);
            titleView.setTextColor(0xFFFF0000);
            titleView.setTextSize(20);
            titleView.setTypeface(null, android.graphics.Typeface.BOLD);
            titleView.setGravity(Gravity.CENTER);
            titleView.setPadding(0, 0, 0, 16);
            mainLayout.addView(titleView);

            keyboardDisplay = new TextView(this);
            keyboardDisplay.setText(initialValue.isEmpty() ? "0" : initialValue);
            keyboardDisplay.setTextColor(Color.WHITE);
            keyboardDisplay.setTextSize(36);
            keyboardDisplay.setTypeface(Typeface.MONOSPACE);
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

            LinearLayout keyboardGrid = new LinearLayout(this);
            keyboardGrid.setOrientation(LinearLayout.VERTICAL);
            keyboardGrid.setGravity(Gravity.CENTER);

            LinearLayout row1 = createNumberRow(new String[]{"1", "2", "3", "4", "5"});
            keyboardGrid.addView(row1);
            
            LinearLayout row2 = createNumberRow(new String[]{"6", "7", "8", "9", "0"});
            keyboardGrid.addView(row2);
            
            LinearLayout row3 = new LinearLayout(this);
            row3.setOrientation(LinearLayout.HORIZONTAL);
            row3.setGravity(Gravity.CENTER);
            row3.setPadding(0, 4, 0, 4);
            
            String[] row3Keys = {"-", "ОЧИСТИТЬ", "✓"};
            for (String key : row3Keys) {
                Button keyBtn = new Button(this);
                keyBtn.setText(key);
                keyBtn.setTextColor(Color.WHITE);
                keyBtn.setTextSize(key.equals("ОЧИСТИТЬ") ? 16 : 22);
                keyBtn.setTypeface(null, android.graphics.Typeface.BOLD);
                
                GradientDrawable keyBg = new GradientDrawable();
                keyBg.setCornerRadius(12);
                if (key.equals("-")) {
                    keyBg.setColor(0xFF444444);
                    keyBg.setStroke(2, 0xFFFF4444);
                } else if (key.equals("ОЧИСТИТЬ")) {
                    keyBg.setColor(0xFFFF4444);
                    keyBtn.setWidth(160);
                } else if (key.equals("✓")) {
                    keyBg.setColor(0xFF00AA00);
                }
                keyBtn.setBackground(keyBg);
                
                LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(
                        key.equals("ОЧИСТИТЬ") ? 160 : 80, 70);
                keyParams.setMargins(6, 6, 6, 6);
                keyBtn.setLayoutParams(keyParams);

                keyBtn.setOnClickListener(v -> handleKeyboardKey(key));
                row3.addView(keyBtn);
            }
            keyboardGrid.addView(row3);

            mainLayout.addView(keyboardGrid);

            TextView hint = new TextView(this);
            hint.setText("Введите значение");
            hint.setTextColor(0xFF888888);
            hint.setTextSize(12);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(0, 8, 0, 8);
            mainLayout.addView(hint);

            Button cancelBtn = new Button(this);
            cancelBtn.setText("ОТМЕНА");
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

            if (mainLayout != null) {
                mainLayout.startAnimation(createPopAnimation());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private LinearLayout createNumberRow(String[] keys) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, 4, 0, 4);
        
        for (String key : keys) {
            Button keyBtn = new Button(this);
            keyBtn.setText(key);
            keyBtn.setTextColor(Color.WHITE);
            keyBtn.setTextSize(24);
            keyBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            
            GradientDrawable keyBg = new GradientDrawable();
            keyBg.setCornerRadius(12);
            keyBg.setColor(0xFF333333);
            keyBg.setStroke(2, 0xFFFF4444);
            keyBtn.setBackground(keyBg);
            
            LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(
                    60, 70);
            keyParams.setMargins(6, 6, 6, 6);
            keyBtn.setLayoutParams(keyParams);

            keyBtn.setOnClickListener(v -> handleKeyboardKey(key));
            row.addView(keyBtn);
        }
        
        return row;
    }

    private void handleKeyboardKey(String key) {
        try {
            if (key.equals("ОЧИСТИТЬ")) {
                keyboardValue = "";
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
            
            vibrate();
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

    private void startGpsMode(String macroId, int pointIndex) {
        if (windowManager == null) return;
        
        isGpsMode = true;
        gpsTargetMacroId = macroId;
        gpsTargetLine = pointIndex;
        
        Toast.makeText(this, "Нажмите на экран для получения координат", Toast.LENGTH_LONG).show();
        
        gpsOverlay = new FrameLayout(this);
        gpsOverlay.setBackgroundColor(0x44AA00FF);
        
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
                
                insertGpsCoords(x, y);
                return true;
            }
            return false;
        });
        
        windowManager.addView(gpsOverlay, captureParams);
    }

    private void insertGpsCoords(int x, int y) {
        if (gpsTargetMacroId != null && !gpsTargetMacroId.isEmpty()) {
            MacroConfig config = allMacros.get(gpsTargetMacroId);
            if (config != null && gpsTargetLine >= 0 && gpsTargetLine < config.points.size()) {
                MacroPoint point = config.points.get(gpsTargetLine);
                point.x = x;
                point.y = y;
                saveMacroConfigs();
                Toast.makeText(this, "Координаты обновлены: " + x + "," + y, Toast.LENGTH_SHORT).show();
                playSaveSound();
            }
        }
        
        if (gpsOverlay != null && windowManager != null) {
            windowManager.removeView(gpsOverlay);
            gpsOverlay = null;
            isGpsMode = false;
        }
        
        updateMacroUI();
    }

    // ==================== ОКНА С ВОЗМОЖНОСТЬЮ РАСШИРЕНИЯ ====================

    private void setupResizableWindow(final View view, final WindowManager.LayoutParams params) {
        view.setOnTouchListener(new View.OnTouchListener() {
            float touchStartX, touchStartY;
            int paramStartX, paramStartY;
            int paramStartWidth, paramStartHeight;
            boolean isDragging = false;
            boolean isResizing = false;
            int resizeZone = 40;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                float x = event.getRawX();
                float y = event.getRawY();
                
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        touchStartX = x;
                        touchStartY = y;
                        paramStartX = params.x;
                        paramStartY = params.y;
                        paramStartWidth = params.width;
                        paramStartHeight = params.height;
                        
                        int viewRight = params.x + params.width;
                        int viewBottom = params.y + params.height;
                        if (x > viewRight - resizeZone && y > viewBottom - resizeZone) {
                            isResizing = true;
                            isDragging = false;
                        } else {
                            isDragging = true;
                            isResizing = false;
                        }
                        return true;
                        
                    case MotionEvent.ACTION_MOVE:
                        float dx = x - touchStartX;
                        float dy = y - touchStartY;
                        
                        if (isResizing) {
                            int newWidth = (int) Math.max(200, paramStartWidth + dx);
                            int newHeight = (int) Math.max(150, paramStartHeight + dy);
                            
                            int maxWidth = getScreenWidth() - 20;
                            int maxHeight = getScreenHeight() - 20;
                            newWidth = Math.min(newWidth, maxWidth);
                            newHeight = Math.min(newHeight, maxHeight);
                            
                            params.width = newWidth;
                            params.height = newHeight;
                            
                            if (windowManager != null) {
                                windowManager.updateViewLayout(view, params);
                            }
                        } else if (isDragging) {
                            params.x = paramStartX + (int) dx;
                            params.y = paramStartY + (int) dy;
                            if (windowManager != null) {
                                windowManager.updateViewLayout(view, params);
                            }
                        }
                        return true;
                        
                    case MotionEvent.ACTION_UP:
                        isDragging = false;
                        isResizing = false;
                        return true;
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
            titleText.setText("WebView");
            titleText.setTextColor(0xFFFF0000);
            titleText.setTextSize(16);
            titleText.setTypeface(null, android.graphics.Typeface.BOLD);
            titleText.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            titleBar.addView(titleText);
            
            ImageButton expandBtn = new ImageButton(this);
            expandBtn.setImageBitmap(createExpandIcon());
            expandBtn.setBackgroundColor(Color.TRANSPARENT);
            expandBtn.setPadding(8, 4, 8, 4);
            expandBtn.setOnClickListener(v -> {
                WindowManager.LayoutParams lp = (WindowManager.LayoutParams) container.getLayoutParams();
                if (lp.width < getScreenWidth() - 50) {
                    lp.width = getScreenWidth() - 20;
                    lp.height = getScreenHeight() - 20;
                } else {
                    lp.width = (int)(getScreenWidth() * 0.85);
                    lp.height = (int)(getScreenHeight() * 0.7);
                }
                if (windowManager != null) {
                    windowManager.updateViewLayout(container, lp);
                }
            });
            titleBar.addView(expandBtn);
            
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

            View resizeHandle = new View(this);
            resizeHandle.setBackgroundColor(0x44FFFFFF);
            FrameLayout.LayoutParams resizeParams = new FrameLayout.LayoutParams(
                    40, 40);
            resizeParams.gravity = Gravity.BOTTOM | Gravity.END;
            container.addView(resizeHandle, resizeParams);

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
            setupResizableWindow(container, params);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Bitmap createExpandIcon() {
        Bitmap b = Bitmap.createBitmap(30, 30, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2);
        c.drawRect(6, 6, 24, 24, p);
        p.setStrokeWidth(3);
        c.drawLine(24, 6, 24, 12, p);
        c.drawLine(24, 6, 18, 6, p);
        c.drawLine(6, 24, 12, 24, p);
        c.drawLine(6, 24, 6, 18, p);
        return b;
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
            mainLayout.setBackgroundColor(0xDD1A1A1A);

            GradientDrawable border = new GradientDrawable();
            border.setCornerRadius(20);
            border.setColor(0xDD1A1A1A);
            border.setStroke(3, 0xFFFF0000);
            mainLayout.setBackground(border);

            LinearLayout titleBar = new LinearLayout(this);
            titleBar.setOrientation(LinearLayout.HORIZONTAL);
            titleBar.setGravity(Gravity.CENTER_VERTICAL);
            titleBar.setPadding(8, 8, 8, 8);
            
            TextView title = new TextView(this);
            title.setText("ПЕРСОНАЖИ");
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
            addBtn.setText("+ ДОБАВИТЬ");
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

            View resizeHandle = new View(this);
            resizeHandle.setBackgroundColor(0x44FFFFFF);
            FrameLayout.LayoutParams resizeParams = new FrameLayout.LayoutParams(
                    30, 30);
            resizeParams.gravity = Gravity.BOTTOM | Gravity.END;
            wrapper.addView(resizeHandle, resizeParams);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    400, 500,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.CENTER;

            windowManager.addView(wrapper, params);
            setupResizableWindow(wrapper, params);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateCharactersUI(LinearLayout container) {
        container.removeAllViews();

        loadCharacters();

        if (characters.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Нет персонажей");
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
        c.drawText("P", 25, 35, p);
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

    // ==================== ОКНО МАКРОСОВ ====================

    private void showMacrosWindow() {
        try {
            if (windowManager == null) return;

            macrosWrapper = new FrameLayout(this);
            
            macrosMainLayout = new LinearLayout(this);
            macrosMainLayout.setOrientation(LinearLayout.VERTICAL);
            macrosMainLayout.setPadding(12, 12, 12, 12);
            macrosMainLayout.setBackgroundColor(0xDD1A1A1A);

            GradientDrawable border = new GradientDrawable();
            border.setCornerRadius(16);
            border.setColor(0xDD1A1A1A);
            border.setStroke(2, 0xFFFF0000);
            macrosMainLayout.setBackground(border);

            // Заголовок
            LinearLayout titleBar = new LinearLayout(this);
            titleBar.setOrientation(LinearLayout.HORIZONTAL);
            titleBar.setGravity(Gravity.CENTER_VERTICAL);
            titleBar.setPadding(8, 8, 8, 8);
            
            TextView title = new TextView(this);
            title.setText("МАКРОСЫ");
            title.setTextColor(0xFFFF0000);
            title.setTextSize(16);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            titleBar.addView(title);
            
            // Кнопка логов
            ImageButton logsBtn = new ImageButton(this);
            logsBtn.setImageBitmap(createLogsIcon());
            logsBtn.setBackgroundColor(Color.TRANSPARENT);
            logsBtn.setPadding(4, 4, 4, 4);
            logsBtn.setOnClickListener(v -> showLogsWindow());
            titleBar.addView(logsBtn);
            
            // Кнопка аварийной остановки
            ImageButton stopAllBtn = new ImageButton(this);
            stopAllBtn.setImageBitmap(createStopIcon());
            stopAllBtn.setBackgroundColor(Color.TRANSPARENT);
            stopAllBtn.setPadding(4, 4, 4, 4);
            stopAllBtn.setOnClickListener(v -> emergencyStopMacros());
            titleBar.addView(stopAllBtn);
            
            ImageButton closeBtn = new ImageButton(this);
            closeBtn.setImageDrawable(createCloseIcon());
            closeBtn.setBackgroundColor(Color.TRANSPARENT);
            closeBtn.setPadding(4, 4, 4, 4);
            closeBtn.setOnClickListener(v -> {
                if (windowManager != null && macrosWrapper != null) {
                    windowManager.removeView(macrosWrapper);
                    macrosWrapper = null;
                }
            });
            titleBar.addView(closeBtn);
            
            macrosMainLayout.addView(titleBar);

            // Выбор макроса
            LinearLayout selectLayout = new LinearLayout(this);
            selectLayout.setOrientation(LinearLayout.HORIZONTAL);
            selectLayout.setGravity(Gravity.CENTER);
            selectLayout.setPadding(0, 4, 0, 4);

            ArrayList<String> activeMacros = getActiveMacroIds();
            
            Button prevMacroBtn = createSmallButton("◀");
            prevMacroBtn.setOnClickListener(v -> {
                int idx = getMacroIndex(currentMacroId);
                if (idx > 0) {
                    currentMacroId = activeMacros.get(idx - 1);
                    updateMacroUI();
                }
            });
            selectLayout.addView(prevMacroBtn);

            macroNameText = new TextView(this);
            MacroConfig currentConfig = allMacros.get(currentMacroId);
            macroNameText.setText(currentConfig != null ? currentConfig.name : "Нет макроса");
            macroNameText.setTextColor(0xFFFF0000);
            macroNameText.setTextSize(14);
            macroNameText.setTypeface(null, android.graphics.Typeface.BOLD);
            macroNameText.setPadding(8, 0, 8, 0);
            macroNameText.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            selectLayout.addView(macroNameText);

            Button nextMacroBtn = createSmallButton("▶");
            nextMacroBtn.setOnClickListener(v -> {
                int idx = getMacroIndex(currentMacroId);
                if (idx < activeMacros.size() - 1) {
                    currentMacroId = activeMacros.get(idx + 1);
                    updateMacroUI();
                }
            });
            selectLayout.addView(nextMacroBtn);

            Button newMacroBtn = createSmallButton("+");
            newMacroBtn.setOnClickListener(v -> showNewMacroDialog());
            selectLayout.addView(newMacroBtn);

            macrosMainLayout.addView(selectLayout);

            // Точки макроса
            FrameLayout pointsFrame = new FrameLayout(this);
            pointsFrame.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
            
            pointsContainer = new LinearLayout(this);
            pointsContainer.setOrientation(LinearLayout.VERTICAL);
            pointsContainer.setPadding(0, 4, 0, 4);
            
            ScrollView scrollView = new ScrollView(this);
            scrollView.addView(pointsContainer);
            pointsFrame.addView(scrollView);
            macrosMainLayout.addView(pointsFrame);

            // Кнопки управления
            LinearLayout controlLayout = new LinearLayout(this);
            controlLayout.setOrientation(LinearLayout.HORIZONTAL);
            controlLayout.setGravity(Gravity.CENTER);
            controlLayout.setPadding(0, 4, 0, 4);

            Button addPointBtn = createSmallButton("Клик");
            addPointBtn.setBackgroundColor(0xFFFF0000);
            addPointBtn.setOnClickListener(v -> startMacroRecording(ActionType.CLICK));
            controlLayout.addView(addPointBtn);
            
            Button addSwipeBtn = createSmallButton("Свайп");
            addSwipeBtn.setBackgroundColor(0xFF0066CC);
            addSwipeBtn.setOnClickListener(v -> startMacroRecording(ActionType.SWIPE));
            controlLayout.addView(addSwipeBtn);

            Button startBtn = createSmallButton("Старт");
            startBtn.setBackgroundColor(0xFF00AA00);
            startBtn.setOnClickListener(v -> startMacroExecution(currentMacroId));
            controlLayout.addView(startBtn);

            Button stopBtn = createSmallButton("Стоп");
            stopBtn.setBackgroundColor(0xFFFF0000);
            stopBtn.setOnClickListener(v -> stopMacroExecution());
            controlLayout.addView(stopBtn);

            Button clearBtn = createSmallButton("Очистить");
            clearBtn.setBackgroundColor(0xFFFF8800);
            clearBtn.setOnClickListener(v -> clearMacroPoints());
            controlLayout.addView(clearBtn);

            macrosMainLayout.addView(controlLayout);

            // Настройки (только цикл)
            LinearLayout settingsLayout = new LinearLayout(this);
            settingsLayout.setOrientation(LinearLayout.HORIZONTAL);
            settingsLayout.setGravity(Gravity.CENTER);
            settingsLayout.setPadding(0, 4, 0, 4);

            TextView repeatLabel = new TextView(this);
            repeatLabel.setText("Цикл:");
            repeatLabel.setTextColor(0xFF888888);
            repeatLabel.setTextSize(12);
            settingsLayout.addView(repeatLabel);

            repeatDisplay = new TextView(this);
            MacroConfig config = allMacros.get(currentMacroId);
            repeatDisplay.setText(String.valueOf(config != null ? config.repeatCount : 1));
            repeatDisplay.setTextColor(0xFFFF0000);
            repeatDisplay.setTextSize(16);
            repeatDisplay.setTypeface(null, android.graphics.Typeface.BOLD);
            repeatDisplay.setPadding(8, 0, 8, 0);
            repeatDisplay.setBackgroundColor(0x33000000);
            repeatDisplay.setOnClickListener(v -> {
                showKeyboardDialog("КОЛИЧЕСТВО ПОВТОРОВ", 
                    repeatDisplay.getText().toString(), 3,
                    () -> {
                        try {
                            int count = Integer.parseInt(keyboardValue);
                            MacroConfig mc = allMacros.get(currentMacroId);
                            if (mc != null) {
                                mc.repeatCount = Math.max(1, count);
                                saveMacroConfigs();
                                repeatDisplay.setText(String.valueOf(mc.repeatCount));
                                updateMacroUI();
                            }
                        } catch (NumberFormatException e) {}
                    });
            });
            settingsLayout.addView(repeatDisplay);

            macrosMainLayout.addView(settingsLayout);

            // Кнопка сохранения в кнопку с выбором режима
            Button saveToScreenBtn = new Button(this);
            saveToScreenBtn.setText("СОХРАНИТЬ В КНОПКУ");
            saveToScreenBtn.setTextColor(Color.WHITE);
            saveToScreenBtn.setTextSize(13);
            saveToScreenBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable saveScreenBg = new GradientDrawable();
            saveScreenBg.setCornerRadius(10);
            saveScreenBg.setColors(new int[]{0xFFFF4400, 0xFFFF0000});
            saveScreenBg.setGradientType(GradientDrawable.LINEAR_GRADIENT);
            saveScreenBg.setOrientation(GradientDrawable.Orientation.TL_BR);
            saveScreenBg.setAlpha(220);
            saveToScreenBtn.setBackground(saveScreenBg);
            saveToScreenBtn.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            saveToScreenBtn.setOnClickListener(v -> showModeSelectionDialog());
            macrosMainLayout.addView(saveToScreenBtn);

            // Информация о кнопках
            TextView buttonsInfo = new TextView(this);
            buttonsInfo.setGravity(Gravity.CENTER);
            buttonsInfo.setTextColor(0xFF666666);
            buttonsInfo.setTextSize(11);
            buttonsInfo.setPadding(0, 4, 0, 4);
            updateButtonsInfo(buttonsInfo);
            macrosMainLayout.addView(buttonsInfo);

            macrosWrapper.addView(macrosMainLayout);

            updateMacroUI();

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    380, 600,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.CENTER;

            windowManager.addView(macrosWrapper, params);
            setupResizableWindow(macrosWrapper, params);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Bitmap createStopIcon() {
        Bitmap b = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(0xFFFF0000);
        p.setStyle(Paint.Style.FILL);
        c.drawRect(6, 6, 18, 18, p);
        return b;
    }

    private Button createSmallButton(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(11);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(6);
        bg.setColor(0xFF444444);
        btn.setBackground(bg);
        btn.setPadding(8, 4, 8, 4);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(2, 0, 2, 0);
        btn.setLayoutParams(lp);
        return btn;
    }

    // ==================== ВЫБОР РЕЖИМА ПРИ СОЗДАНИИ КНОПКИ ====================

    private void showModeSelectionDialog() {
        MacroConfig config = allMacros.get(currentMacroId);
        if (config == null) {
            Toast.makeText(this, "Макрос не найден", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (config.points.isEmpty()) {
            Toast.makeText(this, "В макросе нет точек!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Нет разрешения на оверлей!", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("ВЫБЕРИТЕ РЕЖИМ ДЛЯ КНОПКИ");

        String[] modes = {
            "NORMAL - Обычный (один раз/цикл)", 
            "HOLD - Зажим (удерживать)", 
            "TOGGLE - Переключатель (вкл/выкл)"
        };
        
        builder.setItems(modes, (dialog, which) -> {
            MacroMode selectedMode;
            switch (which) {
                case 0: selectedMode = MacroMode.NORMAL; break;
                case 1: selectedMode = MacroMode.HOLD; break;
                case 2: selectedMode = MacroMode.TOGGLE; break;
                default: selectedMode = MacroMode.NORMAL; break;
            }
            config.mode = selectedMode;
            saveMacroConfigs();
            saveMacroToScreen(selectedMode);
        });
        
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void saveMacroToScreen(MacroMode mode) {
        MacroConfig config = allMacros.get(currentMacroId);
        if (config == null) {
            Toast.makeText(this, "Макрос не найден", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Нет разрешения на оверлей!", Toast.LENGTH_LONG).show();
            return;
        }
        
        config.isSavedAsButton = true;
        config.mode = mode;
        saveMacroConfigs();
        
        createQuickButton(currentMacroId, mode);
        
        String modeText = mode == MacroMode.NORMAL ? "Обычный" : mode == MacroMode.HOLD ? "Зажим" : "Переключатель";
        Toast.makeText(this, "Сохранено в кнопку #" + buttonNumberCounter + " (" + modeText + ")", Toast.LENGTH_LONG).show();
        updateMacroUI();
        
        for (int i = 0; i < macrosMainLayout.getChildCount(); i++) {
            View v = macrosMainLayout.getChildAt(i);
            if (v instanceof TextView && ((TextView) v).getText().toString().contains("Кнопок на экране:")) {
                updateButtonsInfo((TextView) v);
                break;
            }
        }
        
        playSaveSound();
    }

    private void updateButtonsInfo(TextView infoText) {
        infoText.setText("Кнопок на экране: " + quickButtons.size());
    }

    private ArrayList<String> getActiveMacroIds() {
        ArrayList<String> active = new ArrayList<>();
        for (String id : macroIds) {
            MacroConfig config = allMacros.get(id);
            if (config != null && !config.isSavedAsButton) {
                active.add(id);
            }
        }
        if (active.isEmpty()) {
            createDefaultMacro();
            for (String id : macroIds) {
                MacroConfig config = allMacros.get(id);
                if (config != null && !config.isSavedAsButton) {
                    active.add(id);
                    currentMacroId = id;
                    break;
                }
            }
        }
        return active;
    }

    private int getMacroIndex(String macroId) {
        ArrayList<String> activeMacros = getActiveMacroIds();
        for (int i = 0; i < activeMacros.size(); i++) {
            if (activeMacros.get(i).equals(macroId)) return i;
        }
        return 0;
    }

    private void showNewMacroDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("СОЗДАТЬ МАКРОС");
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);
        
        final EditText input = new EditText(this);
        input.setHint("Имя макроса");
        String defaultName = "Макрос " + (getActiveMacroIds().size() + 1);
        input.setText(defaultName);
        layout.addView(input);
        
        builder.setView(layout);
        
        builder.setPositiveButton("СОЗДАТЬ", (d, w) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                name = "Макрос " + (getActiveMacroIds().size() + 1);
            }
            
            for (String id : getActiveMacroIds()) {
                MacroConfig existing = allMacros.get(id);
                if (existing != null && existing.name.equals(name)) {
                    Toast.makeText(this, "Макрос с таким именем уже существует!", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            
            String newId = UUID.randomUUID().toString();
            MacroConfig newMacro = new MacroConfig(newId, name);
            allMacros.put(newId, newMacro);
            macroIds.add(newId);
            currentMacroId = newId;
            saveMacroConfigs();
            updateMacroUI();
            Toast.makeText(this, "Макрос '" + name + "' создан", Toast.LENGTH_SHORT).show();
            playSaveSound();
        });
        
        builder.setNegativeButton("ОТМЕНА", null);
        builder.show();
    }

    // ==================== ОКНО ЛОГОВ ====================

    private void showLogsWindow() {
        try {
            if (windowManager == null) return;

            logEntries.clear();
            MacroConfig config = allMacros.get(currentMacroId);
            if (config != null) {
                for (int i = 0; i < config.points.size(); i++) {
                    MacroPoint p = config.points.get(i);
                    String type = p.type == ActionType.CLICK ? "Клик" : "Свайп";
                    String coords = p.type == ActionType.CLICK ? 
                        "(" + p.x + ", " + p.y + ")" : 
                        "(" + p.x + ", " + p.y + ") → (" + p.endX + ", " + p.endY + ")";
                    logEntries.add("#" + (i+1) + " " + type + " " + coords + " | " + p.delayMs + "мс");
                }
            }

            if (logEntries.isEmpty()) {
                logEntries.add("Нет точек в макросе");
            }

            logsWrapper = new FrameLayout(this);
            logsWrapper.setBackgroundColor(0xDD1A1A1A);

            LinearLayout mainLayout = new LinearLayout(this);
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setPadding(16, 16, 16, 16);
            mainLayout.setBackgroundColor(0xDD1A1A1A);

            GradientDrawable border = new GradientDrawable();
            border.setCornerRadius(16);
            border.setColor(0xDD1A1A1A);
            border.setStroke(2, 0xFFFF0000);
            mainLayout.setBackground(border);

            LinearLayout titleBar = new LinearLayout(this);
            titleBar.setOrientation(LinearLayout.HORIZONTAL);
            titleBar.setGravity(Gravity.CENTER_VERTICAL);
            titleBar.setPadding(4, 4, 4, 4);
            
            TextView title = new TextView(this);
            title.setText("ЛОГИ МАКРОСА");
            title.setTextColor(0xFFFF0000);
            title.setTextSize(16);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            titleBar.addView(title);
            
            ImageButton closeBtn = new ImageButton(this);
            closeBtn.setImageDrawable(createCloseIcon());
            closeBtn.setBackgroundColor(Color.TRANSPARENT);
            closeBtn.setPadding(4, 4, 4, 4);
            closeBtn.setOnClickListener(v -> {
                if (windowManager != null && logsWrapper != null) {
                    windowManager.removeView(logsWrapper);
                    logsWrapper = null;
                }
            });
            titleBar.addView(closeBtn);
            
            mainLayout.addView(titleBar);

            FrameLayout logFrame = new FrameLayout(this);
            logFrame.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
            logFrame.setBackgroundColor(0xFF0A0A0A);
            logFrame.setPadding(8, 8, 8, 8);
            
            GradientDrawable logBg = new GradientDrawable();
            logBg.setCornerRadius(8);
            logBg.setColor(0xFF0A0A0A);
            logBg.setStroke(1, 0xFF333333);
            logFrame.setBackground(logBg);

            logsContainer = new LinearLayout(this);
            logsContainer.setOrientation(LinearLayout.VERTICAL);
            logsContainer.setPadding(4, 4, 4, 4);
            
            ScrollView logScroll = new ScrollView(this);
            logScroll.addView(logsContainer);
            logFrame.addView(logScroll);
            mainLayout.addView(logFrame);

            LinearLayout navLayout = new LinearLayout(this);
            navLayout.setOrientation(LinearLayout.HORIZONTAL);
            navLayout.setGravity(Gravity.CENTER);
            navLayout.setPadding(0, 8, 0, 4);

            logPrevBtn = createSmallButton("◀");
            logPrevBtn.setOnClickListener(v -> {
                if (currentLogIndex > 0) {
                    currentLogIndex--;
                    updateLogDisplay();
                }
            });
            navLayout.addView(logPrevBtn);

            logCurrentIndex = new TextView(this);
            logCurrentIndex.setText("1/" + logEntries.size());
            logCurrentIndex.setTextColor(0xFFFF8800);
            logCurrentIndex.setTextSize(14);
            logCurrentIndex.setTypeface(null, android.graphics.Typeface.BOLD);
            logCurrentIndex.setPadding(12, 0, 12, 0);
            navLayout.addView(logCurrentIndex);

            logNextBtn = createSmallButton("▶");
            logNextBtn.setOnClickListener(v -> {
                if (currentLogIndex < logEntries.size() - 1) {
                    currentLogIndex++;
                    updateLogDisplay();
                }
            });
            navLayout.addView(logNextBtn);

            mainLayout.addView(navLayout);

            updateLogDisplay();

            logsWrapper.addView(mainLayout);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    350, 400,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.CENTER;

            windowManager.addView(logsWrapper, params);
            setupResizableWindow(logsWrapper, params);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateLogDisplay() {
        if (logsContainer == null) return;
        logsContainer.removeAllViews();

        if (logEntries.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Нет записей");
            empty.setTextColor(0xFF666666);
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, 30, 0, 30);
            logsContainer.addView(empty);
            return;
        }

        if (currentLogIndex >= logEntries.size()) {
            currentLogIndex = logEntries.size() - 1;
        }

        String entry = logEntries.get(currentLogIndex);
        TextView logText = new TextView(this);
        logText.setText(entry);
        logText.setTextColor(0xFFFFFFFF);
        logText.setTextSize(16);
        logText.setTypeface(Typeface.MONOSPACE);
        logText.setPadding(8, 12, 8, 12);
        
        GradientDrawable entryBg = new GradientDrawable();
        entryBg.setCornerRadius(6);
        entryBg.setColor(0x22000000);
        entryBg.setStroke(1, 0xFFFF4400);
        logText.setBackground(entryBg);
        
        logsContainer.addView(logText);

        logCurrentIndex.setText((currentLogIndex + 1) + "/" + logEntries.size());

        logPrevBtn.setEnabled(currentLogIndex > 0);
        logNextBtn.setEnabled(currentLogIndex < logEntries.size() - 1);
        logPrevBtn.setAlpha(currentLogIndex > 0 ? 1.0f : 0.4f);
        logNextBtn.setAlpha(currentLogIndex < logEntries.size() - 1 ? 1.0f : 0.4f);
    }

    private Bitmap createLogsIcon() {
        Bitmap b = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2);
        c.drawRect(4, 4, 20, 20, p);
        p.setStrokeWidth(1.5f);
        for (int i = 6; i < 20; i += 5) {
            c.drawLine(8, i, 16, i, p);
        }
        return b;
    }

    // ==================== ЗАПИСЬ МАКРОСА ====================

    private void startMacroRecording(ActionType type) {
        if (isMacroRecording) return;
        if (windowManager == null) return;
        
        MacroConfig config = allMacros.get(currentMacroId);
        if (config == null) {
            Toast.makeText(this, "Ошибка: макрос не найден", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (config.isSavedAsButton) {
            Toast.makeText(this, "Этот макрос уже сохранен как кнопка", Toast.LENGTH_LONG).show();
            return;
        }
        
        isMacroRecording = true;
        isRecordingSwipe = (type == ActionType.SWIPE);
        
        String message = isRecordingSwipe ? 
            "Нажмите для начала свайпа, затем для конца" : 
            "Нажмите на экран для добавления клика";
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        
        captureOverlay = new FrameLayout(this);
        captureOverlay.setBackgroundColor(isRecordingSwipe ? 0x3300AAFF : 0x3300FF00);
        
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
                
                if (isRecordingSwipe) {
                    if (swipeStartX == 0 && swipeStartY == 0) {
                        swipeStartX = x;
                        swipeStartY = y;
                        Toast.makeText(this, "Начало: (" + x + "," + y + ")", Toast.LENGTH_SHORT).show();
                        return true;
                    } else {
                        swipeEndX = x;
                        swipeEndY = y;
                        addMacroSwipe((int)swipeStartX, (int)swipeStartY, (int)swipeEndX, (int)swipeEndY);
                        swipeStartX = 0;
                        swipeStartY = 0;
                        return true;
                    }
                } else {
                    addMacroPoint(x, y);
                }
                return true;
            }
            return false;
        });
        
        windowManager.addView(captureOverlay, captureParams);
    }

    private void addMacroPoint(int x, int y) {
        MacroConfig config = allMacros.get(currentMacroId);
        if (config == null) {
            Toast.makeText(this, "Ошибка: макрос не найден", Toast.LENGTH_SHORT).show();
            return;
        }

        MacroPoint point = new MacroPoint(x, y);
        config.points.add(point);
        saveMacroConfigs();

        Toast.makeText(this, "Клик #" + config.points.size(), Toast.LENGTH_SHORT).show();
        playClickSound();

        showDelayDialog(config.points.size() - 1);

        if (captureOverlay != null && windowManager != null) {
            windowManager.removeView(captureOverlay);
            captureOverlay = null;
            isMacroRecording = false;
        }

        updateMacroUI();
    }

    private void addMacroSwipe(int startX, int startY, int endX, int endY) {
        MacroConfig config = allMacros.get(currentMacroId);
        if (config == null) {
            Toast.makeText(this, "Ошибка: макрос не найден", Toast.LENGTH_SHORT).show();
            return;
        }

        MacroPoint point = new MacroPoint(startX, startY, endX, endY, 300);
        config.points.add(point);
        saveMacroConfigs();

        Toast.makeText(this, "Свайп #" + config.points.size(), Toast.LENGTH_SHORT).show();
        playClickSound();

        showDelayDialog(config.points.size() - 1);

        if (captureOverlay != null && windowManager != null) {
            windowManager.removeView(captureOverlay);
            captureOverlay = null;
            isMacroRecording = false;
        }

        updateMacroUI();
    }

    private void showDelayDialog(final int index) {
        MacroConfig config = allMacros.get(currentMacroId);
        if (config == null || index >= config.points.size()) return;

        long currentDelay = config.points.get(index).delayMs;

        showKeyboardDialog("ЗАДЕРЖКА (мс)", String.valueOf(currentDelay), 6, () -> {
            try {
                long value = Long.parseLong(keyboardValue);
                if (value < 10) value = 10;
                MacroConfig cfg = allMacros.get(currentMacroId);
                if (cfg != null && index < cfg.points.size()) {
                    cfg.points.get(index).delayMs = value;
                    cfg.points.get(index).delayDisplay = value + "мс";
                    saveMacroConfigs();
                    updateMacroUI();
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Ошибка", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void clearMacroPoints() {
        MacroConfig config = allMacros.get(currentMacroId);
        if (config != null && !config.points.isEmpty()) {
            config.points.clear();
            saveMacroConfigs();
            updateMacroUI();
            Toast.makeText(this, "Все точки удалены", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateMacroUI() {
        if (macrosMainLayout == null || pointsContainer == null) return;
        
        MacroConfig config = allMacros.get(currentMacroId);
        if (config == null) {
            ArrayList<String> active = getActiveMacroIds();
            if (!active.isEmpty()) {
                currentMacroId = active.get(0);
                config = allMacros.get(currentMacroId);
            } else {
                createDefaultMacro();
                config = allMacros.get(currentMacroId);
            }
        }
        
        if (macroNameText != null) {
            macroNameText.setText(config != null ? config.name : "Нет макроса");
        }
        
        if (repeatDisplay != null) {
            repeatDisplay.setText(String.valueOf(config != null ? config.repeatCount : 1));
        }

        pointsContainer.removeAllViews();

        if (config == null) {
            TextView empty = new TextView(this);
            empty.setText("Нет активных макросов");
            empty.setTextColor(0xFF888888);
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, 20, 0, 20);
            pointsContainer.addView(empty);
            return;
        }

        if (config.isSavedAsButton) {
            TextView saved = new TextView(this);
            saved.setText("Сохранен как кнопка #" + getButtonNumber(config.id));
            saved.setTextColor(0xFFFF8800);
            saved.setTextSize(13);
            saved.setGravity(Gravity.CENTER);
            saved.setPadding(0, 20, 0, 20);
            pointsContainer.addView(saved);
            return;
        }

        if (config.points.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Нет точек.\nКликните на экран для записи");
            empty.setTextColor(0xFF666666);
            empty.setTextSize(13);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, 20, 0, 20);
            pointsContainer.addView(empty);
        } else {
            int start = Math.max(0, config.points.size() - 5);
            for (int i = start; i < config.points.size(); i++) {
                MacroPoint p = config.points.get(i);
                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.HORIZONTAL);
                item.setGravity(Gravity.CENTER_VERTICAL);
                item.setPadding(4, 3, 4, 3);
                GradientDrawable itemBg = new GradientDrawable();
                itemBg.setCornerRadius(6);
                itemBg.setColor(p.type == ActionType.SWIPE ? 0x2200AAFF : 0x22FF0000);
                itemBg.setStroke(1, p.type == ActionType.SWIPE ? 0xFF00AAFF : 0xFFFF0000);
                item.setBackground(itemBg);

                TextView info = new TextView(this);
                String typeIcon = p.getTypeIcon();
                String delayText = p.delayMs + "мс";
                String posText = p.getDisplayText();
                info.setText(" " + typeIcon + " #" + (i+1) + " " + posText + " " + delayText);
                info.setTextColor(Color.WHITE);
                info.setTextSize(10);
                info.setLayoutParams(new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                item.addView(info);

                Button delayBtn = new Button(this);
                delayBtn.setText("⏱");
                delayBtn.setTextColor(0xFFFFAA00);
                delayBtn.setTextSize(12);
                delayBtn.setBackgroundColor(0x33FFAA00);
                delayBtn.setPadding(4, 2, 4, 2);
                final int idx = i;
                delayBtn.setOnClickListener(v -> showDelayDialog(idx));
                item.addView(delayBtn);

                Button gpsBtn = new Button(this);
                gpsBtn.setText("📍");
                gpsBtn.setTextColor(0xFFAA00FF);
                gpsBtn.setTextSize(12);
                gpsBtn.setBackgroundColor(0x33AA00FF);
                gpsBtn.setPadding(4, 2, 4, 2);
                gpsBtn.setOnClickListener(v -> {
                    startGpsMode(currentMacroId, idx);
                });
                item.addView(gpsBtn);

                Button delBtn = new Button(this);
                delBtn.setText("✕");
                delBtn.setTextColor(0xFFFF0000);
                delBtn.setTextSize(10);
                delBtn.setBackgroundColor(0x33FF0000);
                delBtn.setPadding(4, 2, 4, 2);
                final int pointIndex = i;
                final MacroConfig finalConfig = config;
                delBtn.setOnClickListener(v -> {
                    finalConfig.points.remove(pointIndex);
                    saveMacroConfigs();
                    updateMacroUI();
                });
                item.addView(delBtn);

                pointsContainer.addView(item);
            }
            
            if (config.points.size() > 5) {
                TextView more = new TextView(this);
                more.setText("... +" + (config.points.size() - 5) + " точек (см. логи)");
                more.setTextColor(0xFF666666);
                more.setTextSize(11);
                more.setGravity(Gravity.CENTER);
                more.setPadding(0, 4, 0, 4);
                pointsContainer.addView(more);
            }
        }
    }

    private int getButtonNumber(String macroId) {
        QuickMacroButton btn = quickButtons.get(macroId);
        return btn != null ? btn.number : 0;
    }

    // ==================== ВЫПОЛНЕНИЕ МАКРОСА (ИСПРАВЛЕННОЕ) ====================

    private void startMacroExecution(String macroId) {
        MacroConfig config = allMacros.get(macroId);
        if (config == null) {
            Toast.makeText(this, "Макрос не найден", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (config.points.isEmpty()) {
            Toast.makeText(this, "В макросе нет точек", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Включите специальные возможности", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }

        if (config.mode == MacroMode.TOGGLE) {
            config.isToggled = !config.isToggled;
            saveMacroConfigs();
            
            QuickMacroButton btn = quickButtons.get(macroId);
            if (btn != null && btn.container != null) {
                btn.isToggled = config.isToggled;
                updateQuickButtonUI(macroId);
            }
            
            if (config.isToggled) {
                Toast.makeText(this, "Макрос ВКЛЮЧЕН", Toast.LENGTH_SHORT).show();
                isMacroRunning = true;
                currentMacroIndex = 0;
                currentRepeat = 0;
                macroRepeatCount = -1;
                executeNextPointToggle(config);
            } else {
                Toast.makeText(this, "Макрос ВЫКЛЮЧЕН", Toast.LENGTH_SHORT).show();
                stopMacroExecution();
            }
            return;
        }

        if (config.mode == MacroMode.HOLD) {
            isMacroRunning = true;
            currentMacroIndex = 0;
            currentRepeat = 0;
            macroRepeatCount = config.repeatCount;
            Toast.makeText(this, "Режим HOLD: удерживайте", Toast.LENGTH_SHORT).show();
            executeNextPointHold(config);
            return;
        }

        isMacroRunning = true;
        currentMacroIndex = 0;
        currentRepeat = 0;
        macroRepeatCount = config.repeatCount;
        Toast.makeText(this, "Макрос запущен! Циклов: " + macroRepeatCount, Toast.LENGTH_SHORT).show();
        executeNextPoint(config);
    }

    private void executeNextPointHold(MacroConfig config) {
        if (!isMacroRunning) {
            stopMacroExecution();
            return;
        }

        if (currentMacroIndex >= config.points.size()) {
            currentRepeat++;
            if (currentRepeat < macroRepeatCount) {
                currentMacroIndex = 0;
                executeNextPointHold(config);
                return;
            } else {
                stopMacroExecution();
                Toast.makeText(this, "Макрос завершён", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        MacroPoint point = config.points.get(currentMacroIndex);
        
        if (point.type == ActionType.SWIPE) {
            performSwipe(point.x, point.y, point.endX, point.endY, point.swipeDuration);
        } else {
            performClick(point.x, point.y);
        }

        currentMacroIndex++;
        
        macroHandler.postDelayed(() -> {
            executeNextPointHold(config);
        }, point.delayMs);
    }

    private void executeNextPointToggle(MacroConfig config) {
        if (!isMacroRunning || !config.isToggled) {
            stopMacroExecution();
            return;
        }

        if (currentMacroIndex >= config.points.size()) {
            currentMacroIndex = 0;
            macroHandler.postDelayed(() -> {
                executeNextPointToggle(config);
            }, 500);
            return;
        }

        MacroPoint point = config.points.get(currentMacroIndex);
        
        if (point.type == ActionType.SWIPE) {
            performSwipe(point.x, point.y, point.endX, point.endY, point.swipeDuration);
        } else {
            performClick(point.x, point.y);
        }

        currentMacroIndex++;
        
        macroHandler.postDelayed(() -> {
            executeNextPointToggle(config);
        }, point.delayMs);
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
                Toast.makeText(this, "Цикл " + (currentRepeat + 1) + "/" + macroRepeatCount, Toast.LENGTH_SHORT).show();
                executeNextPoint(config);
                return;
            } else {
                stopMacroExecution();
                Toast.makeText(this, "Макрос завершён! Циклов: " + macroRepeatCount, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        MacroPoint point = config.points.get(currentMacroIndex);
        
        if (point.type == ActionType.SWIPE) {
            performSwipe(point.x, point.y, point.endX, point.endY, point.swipeDuration);
        } else {
            performClick(point.x, point.y);
        }

        currentMacroIndex++;
        
        macroHandler.postDelayed(() -> {
            executeNextPoint(config);
        }, point.delayMs);
    }

    public void stopMacroOnHoldRelease() {
        if (isMacroRunning) {
            MacroConfig config = allMacros.get(currentMacroId);
            if (config != null && config.mode == MacroMode.HOLD) {
                stopMacroExecution();
                Toast.makeText(this, "Режим HOLD остановлен", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ==================== ВЫПОЛНЕНИЕ КЛИКОВ И СВАЙПОВ (ИСПРАВЛЕННОЕ) ====================

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
        
        MacroConfig config = allMacros.get(currentMacroId);
        if (config != null && config.mode == MacroMode.TOGGLE) {
            config.isToggled = false;
            saveMacroConfigs();
            QuickMacroButton btn = quickButtons.get(currentMacroId);
            if (btn != null && btn.container != null) {
                btn.isToggled = false;
                updateQuickButtonUI(currentMacroId);
            }
        }
        
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

    // ==================== БЫСТРЫЕ КНОПКИ (СЕРЫЕ) ====================

    private void createQuickButtonUI(String macroId) {
        try {
            if (windowManager == null) return;
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                return;
            }
            
            if (quickButtons.containsKey(macroId)) {
                QuickMacroButton btn = quickButtons.get(macroId);
                if (btn.container != null && btn.container.getParent() != null) {
                    return;
                }
                createQuickButton(macroId, btn.mode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createQuickButton(String macroId, MacroMode mode) {
        try {
            if (windowManager == null) {
                windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
                if (windowManager == null) {
                    Toast.makeText(this, "Ошибка: WindowManager недоступен", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Нет разрешения на оверлей!", Toast.LENGTH_LONG).show();
                return;
            }

            MacroConfig config = allMacros.get(macroId);
            if (config == null) {
                Toast.makeText(this, "Макрос не найден", Toast.LENGTH_SHORT).show();
                return;
            }

            if (quickButtons.containsKey(macroId)) {
                QuickMacroButton existing = quickButtons.get(macroId);
                if (existing.container != null && existing.container.getParent() != null) {
                    Toast.makeText(this, "Кнопка уже существует", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            int newId = quickButtonIdCounter++;
            int number = buttonNumberCounter++;
            QuickMacroButton btn = new QuickMacroButton(macroId, newId, number);
            btn.mode = mode;
            
            btn.size = 140;
            btn.textSize = 36;
            btn.borderWidth = 3;
            btn.rainbowEffect = false;
            btn.displayName = String.valueOf(number);
            btn.text = String.valueOf(number);
            btn.color1 = 0xFF333333;
            btn.color2 = 0xFF1A1A1A;
            btn.borderColor = 0xFF555555;
            
            btn.container = new FrameLayout(this);
            btn.container.setBackgroundColor(Color.TRANSPARENT);

            FrameLayout buttonFrame = createButtonFrame(btn);
            btn.container.addView(buttonFrame, new FrameLayout.LayoutParams(
                    btn.size, btn.size));

            btn.params = new WindowManager.LayoutParams(
                    btn.size + 20, btn.size + 20,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            btn.params.gravity = Gravity.TOP | Gravity.START;
            
            int buttonIndex = quickButtons.size();
            int columns = 3;
            int spacing = 170;
            int startX = 20;
            int startY = 80;
            
            int col = buttonIndex % columns;
            int row = buttonIndex / columns;
            
            btn.params.x = startX + (col * spacing);
            btn.params.y = startY + (row * spacing);

            btn.container.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (btn.isFixed) {
                        if (event.getAction() == MotionEvent.ACTION_UP) {
                            handleButtonPress(btn.macroId);
                            vibrate();
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
                            if (btn.mode == MacroMode.HOLD) {
                                if (!isMacroRunning) {
                                    startMacroExecution(btn.macroId);
                                }
                            }
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
                            if (btn.mode == MacroMode.HOLD) {
                                stopMacroOnHoldRelease();
                            }
                            if (!btn.isDragging && !btn.isFixed) {
                                handleButtonPress(btn.macroId);
                                vibrate();
                            }
                            return true;
                    }
                    return false;
                }
            });

            windowManager.addView(btn.container, btn.params);
            quickButtons.put(macroId, btn);
            saveQuickButtons();
            
            String modeText = mode == MacroMode.NORMAL ? "Обычный" : mode == MacroMode.HOLD ? "Зажим" : "Переключатель";
            Toast.makeText(this, "Кнопка #" + number + " создана (" + modeText + ")", Toast.LENGTH_SHORT).show();
            playSaveSound();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка создания кнопки: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void handleButtonPress(String macroId) {
        startMacroExecution(macroId);
    }

    private FrameLayout createButtonFrame(QuickMacroButton btn) {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.TRANSPARENT);

        View buttonView = new View(this);
        
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        
        if (btn.shape.equals("rounded")) {
            drawable.setCornerRadius(btn.size / 5f);
        } else if (btn.shape.equals("circle")) {
            drawable.setShape(GradientDrawable.OVAL);
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
        
        if (btn.isToggled) {
            drawable.setStroke(btn.borderWidth + 4, 0xFF00FF00);
        }
        
        buttonView.setBackground(drawable);

        FrameLayout.LayoutParams viewParams = new FrameLayout.LayoutParams(
                btn.size, btn.size);
        buttonView.setLayoutParams(viewParams);
        frame.addView(buttonView);

        TextView textView = new TextView(this);
        textView.setText(btn.displayName);
        textView.setTextColor(btn.textColor);
        textView.setTextSize(btn.textSize);
        textView.setGravity(Gravity.CENTER);
        textView.setPadding(4, 4, 4, 4);
        textView.setTypeface(null, android.graphics.Typeface.BOLD);
        textView.setShadowLayer(4, 2, 2, Color.BLACK);
        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
                btn.size, btn.size);
        textView.setLayoutParams(textParams);
        frame.addView(textView);

        MacroConfig config = allMacros.get(btn.macroId);
        String macroName = config != null ? config.name : "";
        if (macroName.length() > 4) {
            macroName = macroName.substring(0, 4);
        }
        TextView subText = new TextView(this);
        subText.setText(macroName);
        subText.setTextColor(0x88FFFFFF);
        subText.setTextSize(10);
        subText.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        subText.setShadowLayer(2, 1, 1, Color.BLACK);
        FrameLayout.LayoutParams subParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        subParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        subParams.setMargins(0, 0, 0, 4);
        subText.setLayoutParams(subParams);
        frame.addView(subText);

        TextView modeIndicator = new TextView(this);
        String modeSymbol = btn.mode == MacroMode.HOLD ? "H" : 
                           btn.mode == MacroMode.TOGGLE ? "T" : "N";
        modeIndicator.setText(modeSymbol);
        modeIndicator.setTextColor(0x88FFFFFF);
        modeIndicator.setTextSize(12);
        modeIndicator.setTypeface(null, android.graphics.Typeface.BOLD);
        modeIndicator.setGravity(Gravity.TOP | Gravity.START);
        modeIndicator.setShadowLayer(2, 1, 1, Color.BLACK);
        FrameLayout.LayoutParams modeParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        modeParams.gravity = Gravity.TOP | Gravity.START;
        modeParams.setMargins(4, 4, 0, 0);
        modeIndicator.setLayoutParams(modeParams);
        frame.addView(modeIndicator);

        if (btn.isFixed) {
            TextView lockIcon = new TextView(this);
            lockIcon.setText("🔒");
            lockIcon.setTextColor(Color.WHITE);
            lockIcon.setTextSize(14);
            lockIcon.setGravity(Gravity.TOP | Gravity.START);
            lockIcon.setShadowLayer(2, 1, 1, Color.BLACK);
            FrameLayout.LayoutParams lockParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            lockParams.gravity = Gravity.TOP | Gravity.START;
            lockParams.setMargins(4, 4, 0, 0);
            lockIcon.setLayoutParams(lockParams);
            frame.addView(lockIcon);
        }

        return frame;
    }

    private void updateQuickButtonUI(String macroId) {
        QuickMacroButton btn = quickButtons.get(macroId);
        if (btn == null || btn.container == null) return;

        btn.container.removeAllViews();
        FrameLayout buttonFrame = createButtonFrame(btn);
        btn.container.addView(buttonFrame, new FrameLayout.LayoutParams(
                btn.size, btn.size));

        if (windowManager != null) {
            btn.params.width = btn.size + 20;
            btn.params.height = btn.size + 20;
            windowManager.updateViewLayout(btn.container, btn.params);
        }
    }

    private void showButtonEditorList() {
        if (quickButtons.isEmpty()) {
            Toast.makeText(this, "Нет кнопок на экране", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Редактор кнопок");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);
        layout.setBackgroundColor(0xFF1A1A1A);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);

        for (String macroId : quickButtons.keySet()) {
            QuickMacroButton btn = quickButtons.get(macroId);
            MacroConfig config = allMacros.get(macroId);
            String name = config != null ? config.name : "";
            String modeText = btn.mode == MacroMode.NORMAL ? "N" : btn.mode == MacroMode.HOLD ? "H" : "T";
            
            LinearLayout itemLayout = new LinearLayout(this);
            itemLayout.setOrientation(LinearLayout.HORIZONTAL);
            itemLayout.setGravity(Gravity.CENTER_VERTICAL);
            itemLayout.setPadding(8, 8, 8, 8);
            
            GradientDrawable itemBg = new GradientDrawable();
            itemBg.setCornerRadius(8);
            itemBg.setColor(0x22FFFFFF);
            itemBg.setStroke(2, 0xFFFF8800);
            itemLayout.setBackground(itemBg);

            TextView infoText = new TextView(this);
            infoText.setText("#" + btn.number + " [" + modeText + "] " + name);
            infoText.setTextColor(Color.WHITE);
            infoText.setTextSize(14);
            infoText.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            itemLayout.addView(infoText);

            Button editBtn = new Button(this);
            editBtn.setText("✎");
            editBtn.setTextColor(0xFFFF8800);
            editBtn.setTextSize(18);
            editBtn.setBackgroundColor(0x33FF8800);
            editBtn.setPadding(8, 4, 8, 4);
            editBtn.setOnClickListener(v -> showButtonEditor(macroId));
            itemLayout.addView(editBtn);

            listLayout.addView(itemLayout);
        }

        scrollView.addView(listLayout);
        layout.addView(scrollView);

        builder.setView(layout);
        builder.setPositiveButton("Закрыть", null);
        builder.show();
    }

    private void showButtonEditor(final String macroId) {
        final QuickMacroButton btn = quickButtons.get(macroId);
        if (btn == null || btn.container == null) {
            Toast.makeText(this, "Кнопка не найдена", Toast.LENGTH_SHORT).show();
            return;
        }
        
        MacroConfig config = allMacros.get(macroId);
        String macroName = config != null ? config.name : "";

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Редактор: Кнопка #" + btn.number);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);
        layout.setBackgroundColor(0xFF1A1A1A);

        LinearLayout sizeLayout = new LinearLayout(this);
        sizeLayout.setOrientation(LinearLayout.HORIZONTAL);
        sizeLayout.setGravity(Gravity.CENTER);
        sizeLayout.setPadding(0, 8, 0, 0);

        TextView sizeLabel = new TextView(this);
        sizeLabel.setText("Размер: " + btn.size + "px");
        sizeLabel.setTextColor(Color.WHITE);
        sizeLabel.setPadding(0, 0, 8, 0);
        sizeLayout.addView(sizeLabel);

        SeekBar sizeSeek = new SeekBar(this);
        sizeSeek.setMax(260);
        sizeSeek.setMin(80);
        sizeSeek.setProgress(btn.size);
        sizeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                sizeLabel.setText("Размер: " + progress + "px");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        sizeLayout.addView(sizeSeek);
        layout.addView(sizeLayout);

        TextView modeInfo = new TextView(this);
        String modeText = btn.mode == MacroMode.NORMAL ? "Обычный" : 
                         btn.mode == MacroMode.HOLD ? "Зажим" : "Переключатель";
        modeInfo.setText("Режим: " + modeText);
        modeInfo.setTextColor(0xFFFF8800);
        modeInfo.setTextSize(14);
        modeInfo.setTypeface(null, android.graphics.Typeface.BOLD);
        modeInfo.setPadding(0, 8, 0, 8);
        layout.addView(modeInfo);

        Button fixBtn = new Button(this);
        fixBtn.setText(btn.isFixed ? "РАЗБЛОКИРОВАТЬ" : "ЗАКРЕПИТЬ");
        fixBtn.setTextColor(Color.WHITE);
        fixBtn.setBackgroundColor(btn.isFixed ? 0xFFFF8800 : 0xFF00AA00);
        fixBtn.setPadding(20, 12, 20, 12);
        fixBtn.setOnClickListener(v -> {
            btn.isFixed = !btn.isFixed;
            fixBtn.setText(btn.isFixed ? "РАЗБЛОКИРОВАТЬ" : "ЗАКРЕПИТЬ");
            fixBtn.setBackgroundColor(btn.isFixed ? 0xFFFF8800 : 0xFF00AA00);
            updateQuickButtonUI(macroId);
            saveQuickButtons();
            playClickSound();
            Toast.makeText(this, btn.isFixed ? "Закреплено" : "Разблокировано", Toast.LENGTH_SHORT).show();
        });
        layout.addView(fixBtn);

        Button applyBtn = new Button(this);
        applyBtn.setText("ПРИМЕНИТЬ");
        applyBtn.setTextColor(Color.WHITE);
        applyBtn.setBackgroundColor(0xFF00AA00);
        applyBtn.setPadding(20, 12, 20, 12);
        applyBtn.setOnClickListener(v -> {
            btn.size = sizeSeek.getProgress();
            btn.params.width = btn.size + 20;
            btn.params.height = btn.size + 20;
            updateQuickButtonUI(macroId);
            saveQuickButtons();
            playSaveSound();
            Toast.makeText(this, "Настройки применены!", Toast.LENGTH_SHORT).show();
        });
        layout.addView(applyBtn);

        Button delBtn = new Button(this);
        delBtn.setText("УДАЛИТЬ КНОПКУ");
        delBtn.setTextColor(Color.WHITE);
        delBtn.setBackgroundColor(0xFFFF0000);
        delBtn.setPadding(20, 12, 20, 12);
        delBtn.setOnClickListener(v -> {
            removeQuickButton(macroId);
            Toast.makeText(this, "Кнопка удалена", Toast.LENGTH_SHORT).show();
        });
        layout.addView(delBtn);

        builder.setView(layout);
        builder.setPositiveButton("Закрыть", null);
        builder.show();
    }

    private void showRemoveQuickButtonDialog() {
        if (quickButtons.isEmpty()) {
            Toast.makeText(this, "Нет кнопок на экране", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] names = new String[quickButtons.size()];
        int i = 0;
        for (String macroId : quickButtons.keySet()) {
            QuickMacroButton btn = quickButtons.get(macroId);
            MacroConfig config = allMacros.get(macroId);
            names[i++] = "Кнопка #" + btn.number + " - " + (config != null ? config.name : "");
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("УДАЛИТЬ КНОПКУ");
        builder.setItems(names, (dialog, which) -> {
            String macroId = (String) quickButtons.keySet().toArray()[which];
            removeQuickButton(macroId);
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void removeQuickButton(String macroId) {
        try {
            QuickMacroButton btn = quickButtons.remove(macroId);
            if (btn != null && btn.container != null && windowManager != null) {
                windowManager.removeView(btn.container);
                
                MacroConfig config = allMacros.get(macroId);
                if (config != null) {
                    macroIds.remove(macroId);
                    allMacros.remove(macroId);
                    
                    if (currentMacroId.equals(macroId)) {
                        ArrayList<String> active = getActiveMacroIds();
                        if (!active.isEmpty()) {
                            currentMacroId = active.get(0);
                        } else {
                            createDefaultMacro();
                        }
                    }
                    saveMacroConfigs();
                }
                
                saveQuickButtons();
                playDeleteSound();
                Toast.makeText(this, "Кнопка #" + btn.number + " и макрос удалены", Toast.LENGTH_SHORT).show();
                
                updateMacroUI();
                
                if (macrosMainLayout != null) {
                    for (int i = 0; i < macrosMainLayout.getChildCount(); i++) {
                        View v = macrosMainLayout.getChildAt(i);
                        if (v instanceof TextView && ((TextView) v).getText().toString().contains("Кнопок на экране:")) {
                            updateButtonsInfo((TextView) v);
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== НАСТРОЙКИ ====================

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("НАСТРОЙКИ");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        TextView sizeLabel = new TextView(this);
        int currentSize = prefs.getInt("overlay_size", 80);
        sizeLabel.setText("Размер оверлея: " + currentSize + "px");
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
                sizeLabel.setText("Размер оверлея: " + progress + "px");
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
                overlayAlpha = progress;
                alphaLabel.setText("Прозрачность: " + (progress * 100 / 255) + "%");
                if (!isAppInForeground) {
                    removeMainCircle();
                    createMainCircle();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        layout.addView(alphaSeek);

        TextView infoText = new TextView(this);
        infoText.setText("Макросов: " + allMacros.size() + " | Кнопок: " + quickButtons.size());
        infoText.setTextColor(0xFFFF8800);
        infoText.setTextSize(14);
        infoText.setGravity(Gravity.CENTER);
        infoText.setPadding(0, 16, 0, 8);
        layout.addView(infoText);

        Button exportBtn = new Button(this);
        exportBtn.setText("ЭКСПОРТ КОНФИГА");
        exportBtn.setTextColor(Color.WHITE);
        GradientDrawable exportBg = new GradientDrawable();
        exportBg.setCornerRadius(12);
        exportBg.setColors(new int[]{0xFFFF8800, 0xFFFF4400});
        exportBg.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        exportBg.setOrientation(GradientDrawable.Orientation.TL_BR);
        exportBtn.setBackground(exportBg);
        exportBtn.setPadding(20, 12, 20, 12);
        exportBtn.setOnClickListener(v -> exportConfig());
        layout.addView(exportBtn);

        Button importBtn = new Button(this);
        importBtn.setText("ИМПОРТ КОНФИГА");
        importBtn.setTextColor(Color.WHITE);
        GradientDrawable importBg = new GradientDrawable();
        importBg.setCornerRadius(12);
        importBg.setColors(new int[]{0xFF0088FF, 0xFF0044FF});
        importBg.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        importBg.setOrientation(GradientDrawable.Orientation.TL_BR);
        importBtn.setBackground(importBg);
        importBtn.setPadding(20, 12, 20, 12);
        importBtn.setOnClickListener(v -> importConfig());
        layout.addView(importBtn);

        builder.setView(layout);
        builder.setPositiveButton("Закрыть", null);
        builder.show();
    }

    private void exportConfig() {
        try {
            JSONObject config = new JSONObject();
            
            JSONObject macrosObj = new JSONObject();
            JSONArray idsArray = new JSONArray();
            JSONObject dataObj = new JSONObject();
            
            for (String id : macroIds) {
                idsArray.put(id);
                dataObj.put(id, allMacros.get(id).toJSON());
            }
            
            macrosObj.put("ids", idsArray);
            macrosObj.put("data", dataObj);
            config.put("all_macros", macrosObj);
            
            JSONArray buttonsArray = new JSONArray();
            for (String macroId : quickButtons.keySet()) {
                QuickMacroButton btn = quickButtons.get(macroId);
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
            config.put("quick_button_id_counter", quickButtonIdCounter);
            config.put("button_number_counter", buttonNumberCounter);
            
            String jsonString = config.toString(2);
            
            File dir = new File(getExternalFilesDir(null), "configs");
            if (!dir.exists()) dir.mkdirs();
            
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File file = new File(dir, "arcade_config_" + timeStamp + ".json");
            
            FileOutputStream out = new FileOutputStream(file);
            out.write(jsonString.getBytes());
            out.close();
            
            Toast.makeText(this, "Конфиг сохранён: " + file.getName(), Toast.LENGTH_LONG).show();
            playSaveSound();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка экспорта", Toast.LENGTH_SHORT).show();
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
            if (macrosWrapper != null && windowManager != null) {
                windowManager.removeView(macrosWrapper);
                macrosWrapper = null;
            }
            if (logsWrapper != null && windowManager != null) {
                windowManager.removeView(logsWrapper);
                logsWrapper = null;
            }
            macroHandler.removeCallbacksAndMessages(null);
            closeKeyboard();
            
            if (clickSound != null) clickSound.release();
            if (deleteSound != null) deleteSound.release();
            if (saveSound != null) saveSound.release();
            
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
                    Toast.makeText(this, "Специальные возможности включены", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Включите специальные возможности для работы макросов", Toast.LENGTH_LONG).show();
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
            
            if (config.has("all_macros")) {
                JSONObject macrosObj = config.getJSONObject("all_macros");
                JSONArray idsArray = macrosObj.getJSONArray("ids");
                JSONObject dataObj = macrosObj.getJSONObject("data");
                
                allMacros.clear();
                macroIds.clear();
                
                for (int i = 0; i < idsArray.length(); i++) {
                    String id = idsArray.getString(i);
                    if (dataObj.has(id)) {
                        MacroConfig macro = new MacroConfig(dataObj.getJSONObject(id));
                        allMacros.put(id, macro);
                        macroIds.add(id);
                    }
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
                    String macroId = obj.getString("macroId");
                    if (allMacros.containsKey(macroId)) {
                        QuickMacroButton btn = new QuickMacroButton(obj);
                        btn.params = new WindowManager.LayoutParams(
                                btn.size + 20, btn.size + 20,
                                getOverlayFlag(),
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                                PixelFormat.TRANSLUCENT
                        );
                        btn.params.gravity = Gravity.TOP | Gravity.START;
                        btn.params.x = obj.optInt("x", 100);
                        btn.params.y = obj.optInt("y", 200);
                        quickButtons.put(macroId, btn);
                        createQuickButtonUI(macroId);
                    }
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
            if (config.has("quick_button_id_counter")) {
                quickButtonIdCounter = config.getInt("quick_button_id_counter");
                prefs.edit().putInt("quick_button_id_counter", quickButtonIdCounter).apply();
            }
            if (config.has("button_number_counter")) {
                buttonNumberCounter = config.getInt("button_number_counter");
                prefs.edit().putInt("button_number_counter", buttonNumberCounter).apply();
            }
            
            ArrayList<String> active = getActiveMacroIds();
            if (!active.isEmpty()) {
                currentMacroId = active.get(0);
            } else {
                createDefaultMacro();
            }
            
            updateMacroUI();
            
            Toast.makeText(this, "Конфиг импортирован успешно!", Toast.LENGTH_LONG).show();
            playSaveSound();
            
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка импорта конфига", Toast.LENGTH_SHORT).show();
        }
    }
      }
