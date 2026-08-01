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

    // ========== НОВАЯ СИСТЕМА СТРАНИЦ МАКРОСОВ ==========
    private ArrayList<MacroPage> macroPages = new ArrayList<>();
    private int currentPageIndex = 0;
    private int maxPages = 20;
    
    // Макросы на текущей странице
    private ArrayList<MacroConfig> currentPageMacros = new ArrayList<>();
    private String currentMacroName = "Макрос 1";
    
    private boolean isMacroRecording = false;
    private boolean isMacroRunning = false;
    private Handler macroHandler = new Handler();
    private int currentMacroIndex = 0;
    private int macroRepeatCount = 1;
    private int currentRepeat = 0;
    private FrameLayout captureOverlay;
    
    // Быстрые кнопки на текущей странице
    private HashMap<String, QuickMacroButton> quickButtons = new HashMap<>();
    private int buttonCounter = 1;

    // GPS координаты
    private FrameLayout gpsOverlay;
    private TextView gpsCoordsText;
    private boolean isGpsMode = false;
    private int gpsTargetLine = -1;
    private String gpsTargetMacro = "";

    // Клавиатура
    private FrameLayout keyboardDialog;
    private TextView keyboardDisplay;
    private String keyboardValue = "";
    private int keyboardMaxLength = 10;
    private Runnable keyboardCallback;
    private String keyboardTitle = "";

    private boolean isRecordingSwipe = false;
    private float swipeStartX = 0, swipeStartY = 0;
    private float swipeEndX = 0, swipeEndY = 0;

    // Звуки
    private MediaPlayer clickSound;
    private MediaPlayer deleteSound;
    private MediaPlayer saveSound;

    // ========== НОВЫЙ КЛАСС СТРАНИЦЫ МАКРОСОВ ==========
    private static class MacroPage {
        int pageNumber;
        ArrayList<MacroConfig> macros;
        HashMap<String, QuickMacroButton> quickButtons;
        int buttonCounter;
        String currentMacroName;
        
        MacroPage(int pageNumber) {
            this.pageNumber = pageNumber;
            this.macros = new ArrayList<>();
            this.quickButtons = new HashMap<>();
            this.buttonCounter = 1;
            this.currentMacroName = "Макрос 1";
            // Создаем дефолтный макрос
            MacroConfig defaultMacro = new MacroConfig("Макрос 1");
            defaultMacro.color = getRainbowColor(pageNumber);
            this.macros.add(defaultMacro);
        }
        
        MacroPage(JSONObject json) throws Exception {
            this.pageNumber = json.getInt("pageNumber");
            this.buttonCounter = json.optInt("buttonCounter", 1);
            this.currentMacroName = json.optString("currentMacroName", "Макрос 1");
            
            this.macros = new ArrayList<>();
            JSONArray macrosArray = json.getJSONArray("macros");
            for (int i = 0; i < macrosArray.length(); i++) {
                this.macros.add(new MacroConfig(macrosArray.getJSONObject(i)));
            }
            
            this.quickButtons = new HashMap<>();
            JSONArray buttonsArray = json.optJSONArray("quickButtons");
            if (buttonsArray != null) {
                for (int i = 0; i < buttonsArray.length(); i++) {
                    JSONObject obj = buttonsArray.getJSONObject(i);
                    String name = obj.getString("macroName");
                    QuickMacroButton btn = new QuickMacroButton(obj);
                    this.quickButtons.put(name, btn);
                }
            }
        }
        
        JSONObject toJSON() throws Exception {
            JSONObject json = new JSONObject();
            json.put("pageNumber", pageNumber);
            json.put("buttonCounter", buttonCounter);
            json.put("currentMacroName", currentMacroName);
            
            JSONArray macrosArray = new JSONArray();
            for (MacroConfig c : macros) {
                macrosArray.put(c.toJSON());
            }
            json.put("macros", macrosArray);
            
            JSONArray buttonsArray = new JSONArray();
            for (String name : quickButtons.keySet()) {
                QuickMacroButton btn = quickButtons.get(name);
                buttonsArray.put(btn.toJSON());
            }
            json.put("quickButtons", buttonsArray);
            
            return json;
        }
        
        MacroConfig getCurrentMacro() {
            for (MacroConfig c : macros) {
                if (c.name.equals(currentMacroName)) {
                    return c;
                }
            }
            if (!macros.isEmpty()) {
                currentMacroName = macros.get(0).name;
                return macros.get(0);
            }
            MacroConfig newC = new MacroConfig("Макрос 1");
            newC.color = getRainbowColor(pageNumber);
            macros.add(newC);
            currentMacroName = newC.name;
            return newC;
        }
        
        int getMacroIndex(String name) {
            for (int i = 0; i < macros.size(); i++) {
                if (macros.get(i).name.equals(name)) return i;
            }
            return 0;
        }
        
        MacroConfig getMacroConfig(String name) {
            for (MacroConfig c : macros) {
                if (c.name.equals(name)) {
                    return c;
                }
            }
            return null;
        }
        
        private static int getRainbowColor(int page) {
            float hue = (page * 36) % 360;
            return Color.HSVToColor(255, new float[]{hue, 0.9f, 0.9f});
        }
    }

    private enum ActionType {
        CLICK,
        SWIPE
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
            return type == ActionType.SWIPE ? "↗" : "●";
        }
    }

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
        int pageNumber;
        
        QuickMacroButton(String macroName) {
            this.macroName = macroName;
            this.size = 160;
            this.isFixed = false;
            this.isDragging = false;
            this.shape = "rounded";
            this.color1 = 0xFF00AA00;
            this.color2 = 0xFF008800;
            this.useGradient = true;
            this.text = "▶";
            this.alpha = 1.0f;
            this.borderColor = 0xFF00FF00;
            this.borderWidth = 4;
            this.textColor = 0xFFFFFFFF;
            this.textSize = 32;
            this.pageNumber = 1;
        }
        
        QuickMacroButton(JSONObject json) throws Exception {
            this.macroName = json.getString("macroName");
            this.isFixed = json.optBoolean("isFixed", false);
            this.size = json.optInt("size", 160);
            this.shape = json.optString("shape", "rounded");
            this.color1 = json.optInt("color1", 0xFF00AA00);
            this.color2 = json.optInt("color2", 0xFF008800);
            this.useGradient = json.optBoolean("useGradient", true);
            this.text = json.optString("text", "▶");
            this.alpha = (float) json.optDouble("alpha", 1.0);
            this.borderColor = json.optInt("borderColor", 0xFF00FF00);
            this.borderWidth = json.optInt("borderWidth", 4);
            this.textColor = json.optInt("textColor", 0xFFFFFFFF);
            this.textSize = (float) json.optDouble("textSize", 32);
            this.pageNumber = json.optInt("pageNumber", 1);
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
            json.put("pageNumber", pageNumber);
            json.put("x", params != null ? params.x : 100);
            json.put("y", params != null ? params.y : 200);
            return json;
        }
        
        String getButtonDisplayName() {
            return "M" + pageNumber + "-" + macroName;
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

    // ========== РАДУЖНЫЙ ГРАДИЕНТ ДЛЯ КНОПОК ==========
    private static int[] getRainbowColors(int index) {
        float hue = (index * 36) % 360;
        int color1 = Color.HSVToColor(255, new float[]{hue, 0.9f, 0.9f});
        int color2 = Color.HSVToColor(255, new float[]{(hue + 40) % 360, 0.9f, 0.9f});
        return new int[]{color1, color2};
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            prefs = getSharedPreferences("arcade_data", MODE_PRIVATE);
            overlayAlpha = prefs.getInt("overlay_alpha", 255);
            overlaySize = prefs.getInt("overlay_size", 80);
            buttonCounter = prefs.getInt("button_counter", 1);
            loadCharacters();
            loadMacroPages();
            loadQuickButtonsFromAllPages();
            initSounds();
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
        try {
            if (clickSound != null) {
                clickSound.start();
            }
        } catch (Exception e) {}
    }

    private void playDeleteSound() {
        try {
            if (deleteSound != null) {
                deleteSound.start();
            }
        } catch (Exception e) {}
    }

    private void playSaveSound() {
        try {
            if (saveSound != null) {
                saveSound.start();
            }
        } catch (Exception e) {}
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

    // ==================== ЗАГРУЗКА/СОХРАНЕНИЕ СТРАНИЦ ====================

    private void loadMacroPages() {
        try {
            macroPages.clear();
            String json = prefs.getString("macro_pages", "");
            if (!json.isEmpty()) {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    macroPages.add(new MacroPage(array.getJSONObject(i)));
                }
            }
            // Если страниц нет, создаем первую
            if (macroPages.isEmpty()) {
                macroPages.add(new MacroPage(1));
                currentPageIndex = 0;
            }
            // Обновляем текущую страницу
            updateCurrentPageData();
        } catch (Exception e) {
            e.printStackTrace();
            // Восстанавливаем дефолт
            macroPages.clear();
            macroPages.add(new MacroPage(1));
            currentPageIndex = 0;
            updateCurrentPageData();
        }
    }

    private void saveMacroPages() {
        try {
            JSONArray array = new JSONArray();
            for (MacroPage page : macroPages) {
                array.put(page.toJSON());
            }
            prefs.edit().putString("macro_pages", array.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateCurrentPageData() {
        if (macroPages.isEmpty()) {
            macroPages.add(new MacroPage(1));
            currentPageIndex = 0;
        }
        if (currentPageIndex >= macroPages.size()) {
            currentPageIndex = macroPages.size() - 1;
        }
        MacroPage page = macroPages.get(currentPageIndex);
        currentPageMacros = page.macros;
        currentMacroName = page.currentMacroName;
        buttonCounter = page.buttonCounter;
        quickButtons = page.quickButtons;
    }

    private void loadQuickButtonsFromAllPages() {
        // Загружаем все кнопки со всех страниц
        for (MacroPage page : macroPages) {
            for (String name : page.quickButtons.keySet()) {
                QuickMacroButton btn = page.quickButtons.get(name);
                // Восстанавливаем UI только для текущей страницы
                if (page == macroPages.get(currentPageIndex)) {
                    createQuickButtonUI(name, btn);
                }
            }
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
            
            // Радужный градиент для кружка
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            int[] rainbowColors = getRainbowColors((int)(System.currentTimeMillis() / 1000) % 360);
            d.setColors(rainbowColors);
            d.setStroke(4, 0xFFFFFFFF);
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
                                        } else if (!currentPageMacros.isEmpty()) {
                                            startMacroExecution(currentPageMacros.get(0).name);
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
            int[] rainbowColors = getRainbowColors((int)(System.currentTimeMillis() / 500) % 360);
            wheelBg.setColors(rainbowColors);
            wheelBg.setStroke(3, 0xFFFFFFFF);
            itemsLayout.setBackground(wheelBg);

            String[] items = {"🌐", "🎯", "👤", "⚙️", "✕"};
            String[] labels = {"Web", "Макросы", "Перс", "Настр", "Закр"};

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

            // Ряд 1: 1 2 3 4 5
            LinearLayout row1 = createNumberRow(new String[]{"1", "2", "3", "4", "5"});
            keyboardGrid.addView(row1);
            
            // Ряд 2: 6 7 8 9 0
            LinearLayout row2 = createNumberRow(new String[]{"6", "7", "8", "9", "0"});
            keyboardGrid.addView(row2);
            
            // Ряд 3: - очистить ✓
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
            hint.setText("⏱ Введите задержку в миллисекундах");
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

    private void startGpsMode(String macroName, int pointIndex) {
        if (windowManager == null) return;
        
        isGpsMode = true;
        gpsTargetMacro = macroName;
        gpsTargetLine = pointIndex;
        
        Toast.makeText(this, "🟣 Нажмите на кнопку для получения координат", Toast.LENGTH_LONG).show();
        
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
        if (gpsTargetMacro != null && !gpsTargetMacro.isEmpty()) {
            MacroPage page = macroPages.get(currentPageIndex);
            MacroConfig config = page.getMacroConfig(gpsTargetMacro);
            if (config != null && gpsTargetLine >= 0 && gpsTargetLine < config.points.size()) {
                MacroPoint point = config.points.get(gpsTargetLine);
                point.x = x;
                point.y = y;
                saveMacroPages();
                Toast.makeText(this, "✅ Координаты обновлены: " + x + "," + y, Toast.LENGTH_SHORT).show();
                playSaveSound();
            }
        }
        
        if (gpsOverlay != null && windowManager != null) {
            windowManager.removeView(gpsOverlay);
            gpsOverlay = null;
            isGpsMode = false;
        }
        
        // Обновляем UI макросов если открыто
        View macrosView = findMacrosView();
        if (macrosView != null && macrosView instanceof LinearLayout) {
            updateMacroUI((LinearLayout) macrosView);
        }
    }

    private View findMacrosView() {
        try {
            for (QuickMacroButton btn : quickButtons.values()) {
                if (btn.container != null && btn.container.getParent() != null) {
                    View parent = (View) btn.container.getParent();
                    if (parent != null && parent instanceof FrameLayout) {
                        FrameLayout frame = (FrameLayout) parent;
                        for (int i = 0; i < frame.getChildCount(); i++) {
                            View child = frame.getChildAt(i);
                            if (child instanceof LinearLayout) {
                                LinearLayout ll = (LinearLayout) child;
                                for (int j = 0; j < ll.getChildCount(); j++) {
                                    View v = ll.getChildAt(j);
                                    if (v instanceof LinearLayout) {
                                        LinearLayout titleBar = (LinearLayout) v;
                                        for (int k = 0; k < titleBar.getChildCount(); k++) {
                                            View tv = titleBar.getChildAt(k);
                                            if (tv instanceof TextView) {
                                                String text = ((TextView) tv).getText().toString();
                                                if (text.contains("МАКРОСЫ") || text.contains("🎯")) {
                                                    return ll;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
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
            border.setCornerRadius(20);
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

    // ==================== НОВОЕ ОКНО МАКРОСОВ С ПАГИНАЦИЕЙ ====================

    private void showMacrosWindow() {
        try {
            if (windowManager == null) return;

            FrameLayout wrapper = new FrameLayout(this);
            
            LinearLayout mainLayout = new LinearLayout(this);
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setPadding(16, 16, 16, 16);
            mainLayout.setBackgroundColor(0xDD0D0D0D);

            GradientDrawable border = new GradientDrawable();
            border.setCornerRadius(20);
            int[] rainbowColors = getRainbowColors((int)(System.currentTimeMillis() / 1000) % 360);
            border.setColors(rainbowColors);
            border.setStroke(3, 0xFFFFFFFF);
            mainLayout.setBackground(border);

            // ===== ЗАГОЛОВОК С НАВИГАЦИЕЙ ПО СТРАНИЦАМ =====
            LinearLayout titleBar = new LinearLayout(this);
            titleBar.setOrientation(LinearLayout.HORIZONTAL);
            titleBar.setGravity(Gravity.CENTER_VERTICAL);
            titleBar.setPadding(8, 8, 8, 8);
            
            TextView title = new TextView(this);
            title.setText("🎯 МАКРОСЫ");
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

            // ===== НАВИГАЦИЯ ПО СТРАНИЦАМ =====
            LinearLayout pageNavLayout = new LinearLayout(this);
            pageNavLayout.setOrientation(LinearLayout.HORIZONTAL);
            pageNavLayout.setGravity(Gravity.CENTER);
            pageNavLayout.setPadding(0, 8, 0, 8);

            // Кнопка "◄" - переход на предыдущую страницу
            Button prevPageBtn = new Button(this);
            prevPageBtn.setText("◄");
            prevPageBtn.setTextColor(Color.WHITE);
            prevPageBtn.setTextSize(22);
            prevPageBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable prevBg = new GradientDrawable();
            prevBg.setCornerRadius(16);
            int[] prevColors = getRainbowColors(0);
            prevBg.setColors(prevColors);
            prevPageBtn.setBackground(prevBg);
            prevPageBtn.setPadding(20, 12, 20, 12);
            prevPageBtn.setOnClickListener(v -> {
                if (currentPageIndex > 0) {
                    currentPageIndex--;
                    updateCurrentPageData();
                    updateMacroUI(mainLayout);
                    // Обновляем отображение номера страницы
                    updatePageDisplay(mainLayout);
                    playClickSound();
                } else {
                    Toast.makeText(this, "📄 Это первая страница", Toast.LENGTH_SHORT).show();
                }
            });
            pageNavLayout.addView(prevPageBtn);

            // Отображение номера страницы
            final TextView pageDisplay = new TextView(this);
            pageDisplay.setText("Страница " + (currentPageIndex + 1) + "/" + macroPages.size());
            pageDisplay.setTextColor(0xFFFF0000);
            pageDisplay.setTextSize(18);
            pageDisplay.setTypeface(null, android.graphics.Typeface.BOLD);
            pageDisplay.setPadding(16, 0, 16, 0);
            pageDisplay.setGravity(Gravity.CENTER);
            pageNavLayout.addView(pageDisplay);

            // Кнопка "►" - переход на следующую страницу
            Button nextPageBtn = new Button(this);
            nextPageBtn.setText("►");
            nextPageBtn.setTextColor(Color.WHITE);
            nextPageBtn.setTextSize(22);
            nextPageBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable nextBg = new GradientDrawable();
            nextBg.setCornerRadius(16);
            int[] nextColors = getRainbowColors(180);
            nextBg.setColors(nextColors);
            nextPageBtn.setBackground(nextBg);
            nextPageBtn.setPadding(20, 12, 20, 12);
            nextPageBtn.setOnClickListener(v -> {
                if (currentPageIndex < macroPages.size() - 1) {
                    currentPageIndex++;
                    updateCurrentPageData();
                    updateMacroUI(mainLayout);
                    updatePageDisplay(mainLayout);
                    playClickSound();
                } else {
                    // Если достигнут максимум страниц
                    if (macroPages.size() >= maxPages) {
                        Toast.makeText(this, "⚠️ Достигнут лимит страниц (" + maxPages + ")", Toast.LENGTH_SHORT).show();
                    } else {
                        // Создаем новую страницу
                        createNewPage(mainLayout);
                    }
                }
            });
            pageNavLayout.addView(nextPageBtn);

            // Кнопка "➕" - создать новую страницу
            Button addPageBtn = new Button(this);
            addPageBtn.setText("➕");
            addPageBtn.setTextColor(Color.WHITE);
            addPageBtn.setTextSize(22);
            addPageBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable addBg = new GradientDrawable();
            addBg.setCornerRadius(16);
            int[] addColors = getRainbowColors(270);
            addBg.setColors(addColors);
            addPageBtn.setBackground(addBg);
            addPageBtn.setPadding(16, 12, 16, 12);
            addPageBtn.setOnClickListener(v -> {
                if (macroPages.size() >= maxPages) {
                    Toast.makeText(this, "⚠️ Достигнут лимит страниц (" + maxPages + ")", Toast.LENGTH_SHORT).show();
                } else {
                    createNewPage(mainLayout);
                }
            });
            pageNavLayout.addView(addPageBtn);

            mainLayout.addView(pageNavLayout);

            // ===== ВЫБОР МАКРОСА НА ТЕКУЩЕЙ СТРАНИЦЕ =====
            LinearLayout selectLayout = new LinearLayout(this);
            selectLayout.setOrientation(LinearLayout.HORIZONTAL);
            selectLayout.setGravity(Gravity.CENTER);
            selectLayout.setPadding(0, 8, 0, 8);

            Button prevMacroBtn = new Button(this);
            prevMacroBtn.setText("◄");
            prevMacroBtn.setTextColor(Color.WHITE);
            prevMacroBtn.setTextSize(18);
            GradientDrawable prevMacroBg = new GradientDrawable();
            prevMacroBg.setCornerRadius(12);
            prevMacroBg.setColor(0x33FF0000);
            prevMacroBtn.setBackground(prevMacroBg);
            prevMacroBtn.setPadding(12, 4, 12, 4);
            prevMacroBtn.setOnClickListener(v -> {
                int idx = getCurrentPageMacroIndex(currentMacroName);
                if (idx > 0) {
                    currentMacroName = currentPageMacros.get(idx - 1).name;
                    updateMacroUI(mainLayout);
                } else {
                    Toast.makeText(this, "📌 Это первый макрос", Toast.LENGTH_SHORT).show();
                }
            });
            selectLayout.addView(prevMacroBtn);

            final TextView macroNameText = new TextView(this);
            macroNameText.setText(currentMacroName);
            macroNameText.setTextColor(0xFFFF0000);
            macroNameText.setTextSize(16);
            macroNameText.setTypeface(null, android.graphics.Typeface.BOLD);
            macroNameText.setPadding(12, 0, 12, 0);
            selectLayout.addView(macroNameText);

            Button nextMacroBtn = new Button(this);
            nextMacroBtn.setText("►");
            nextMacroBtn.setTextColor(Color.WHITE);
            nextMacroBtn.setTextSize(18);
            GradientDrawable nextMacroBg = new GradientDrawable();
            nextMacroBg.setCornerRadius(12);
            nextMacroBg.setColor(0x33FF0000);
            nextMacroBtn.setBackground(nextMacroBg);
            nextMacroBtn.setPadding(12, 4, 12, 4);
            nextMacroBtn.setOnClickListener(v -> {
                int idx = getCurrentPageMacroIndex(currentMacroName);
                if (idx < currentPageMacros.size() - 1) {
                    currentMacroName = currentPageMacros.get(idx + 1).name;
                    updateMacroUI(mainLayout);
                } else {
                    // Создаем новый макрос на текущей странице
                    createNewMacroOnPage(mainLayout);
                }
            });
            selectLayout.addView(nextMacroBtn);

            Button newMacroBtn = new Button(this);
            newMacroBtn.setText("➕");
            newMacroBtn.setTextColor(Color.WHITE);
            newMacroBtn.setTextSize(18);
            newMacroBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable newMacroBg = new GradientDrawable();
            newMacroBg.setCornerRadius(12);
            int[] newColors = getRainbowColors((int)(System.currentTimeMillis() / 500) % 360);
            newMacroBg.setColors(newColors);
            newMacroBtn.setBackground(newMacroBg);
            newMacroBtn.setPadding(12, 4, 12, 4);
            newMacroBtn.setOnClickListener(v -> createNewMacroOnPage(mainLayout));
            selectLayout.addView(newMacroBtn);

            mainLayout.addView(selectLayout);

            // ===== ТОЧКИ МАКРОСА =====
            final LinearLayout pointsContainer = new LinearLayout(this);
            pointsContainer.setOrientation(LinearLayout.VERTICAL);
            pointsContainer.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
            
            ScrollView scrollView = new ScrollView(this);
            scrollView.addView(pointsContainer);
            mainLayout.addView(scrollView);

            // ===== КНОПКИ УПРАВЛЕНИЯ МАКРОСОМ =====
            LinearLayout controlLayout = new LinearLayout(this);
            controlLayout.setOrientation(LinearLayout.HORIZONTAL);
            controlLayout.setGravity(Gravity.CENTER);
            controlLayout.setPadding(0, 8, 0, 8);

            Button addPointBtn = createStyledButton("● КЛИК", 0xFFFF0000);
            addPointBtn.setOnClickListener(v -> startMacroRecording(mainLayout, ActionType.CLICK));
            controlLayout.addView(addPointBtn);
            
            Button addSwipeBtn = createStyledButton("↗ СВАЙП", 0xFF00AAFF);
            addSwipeBtn.setOnClickListener(v -> startMacroRecording(mainLayout, ActionType.SWIPE));
            controlLayout.addView(addSwipeBtn);

            Button startBtn = createStyledButton("▶ ЗАПУСК", 0xFF00AA00);
            startBtn.setOnClickListener(v -> startMacroExecution(currentMacroName));
            controlLayout.addView(startBtn);

            Button stopBtn = createStyledButton("■ СТОП", 0xFFFF0000);
            stopBtn.setOnClickListener(v -> stopMacroExecution());
            controlLayout.addView(stopBtn);

            Button clearBtn = createStyledButton("✕ ОЧИСТИТЬ", 0xFFFF8800);
            clearBtn.setOnClickListener(v -> clearMacroPoints(mainLayout));
            controlLayout.addView(clearBtn);

            mainLayout.addView(controlLayout);

            // ===== НАСТРОЙКА ПОВТОРОВ =====
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
            MacroConfig config = getCurrentPageMacro();
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
                            MacroConfig cfg = getCurrentPageMacro();
                            if (cfg != null) {
                                cfg.repeatCount = Math.max(1, count);
                                saveMacroPages();
                                repeatDisplay.setText(String.valueOf(cfg.repeatCount));
                                updateMacroUI(mainLayout);
                                Toast.makeText(this, "Цикл: " + cfg.repeatCount + " раз", Toast.LENGTH_SHORT).show();
                            }
                        } catch (NumberFormatException e) {}
                    });
            });
            settingsLayout.addView(repeatDisplay);

            mainLayout.addView(settingsLayout);

            // ===== БЫСТРЫЕ КНОПКИ =====
            LinearLayout quickLayout = new LinearLayout(this);
            quickLayout.setOrientation(LinearLayout.HORIZONTAL);
            quickLayout.setGravity(Gravity.CENTER);
            quickLayout.setPadding(0, 8, 0, 8);

            Button quickBtn = new Button(this);
            quickBtn.setText("🚀 СОЗДАТЬ КНОПКУ");
            quickBtn.setTextColor(Color.WHITE);
            quickBtn.setTextSize(14);
            quickBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable quickBg = new GradientDrawable();
            quickBg.setCornerRadius(12);
            int[] qColors = getRainbowColors((int)(System.currentTimeMillis() / 300) % 360);
            quickBg.setColors(qColors);
            quickBtn.setBackground(quickBg);
            quickBtn.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            quickBtn.setOnClickListener(v -> createQuickButton(currentMacroName));
            quickLayout.addView(quickBtn);

            Button editQuickBtn = new Button(this);
            editQuickBtn.setText("✏️ РЕДАКТОР");
            editQuickBtn.setTextColor(Color.WHITE);
            editQuickBtn.setTextSize(14);
            editQuickBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable editBg = new GradientDrawable();
            editBg.setCornerRadius(12);
            int[] eColors = getRainbowColors(120);
            editBg.setColors(eColors);
            editQuickBtn.setBackground(editBg);
            editQuickBtn.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            editQuickBtn.setOnClickListener(v -> showButtonEditor(currentMacroName));
            quickLayout.addView(editQuickBtn);

            Button removeQuickBtn = new Button(this);
            removeQuickBtn.setText("🗑 УДАЛИТЬ");
            removeQuickBtn.setTextColor(Color.WHITE);
            removeQuickBtn.setTextSize(14);
            removeQuickBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable removeBg = new GradientDrawable();
            removeBg.setCornerRadius(12);
            int[] rColors = getRainbowColors(240);
            removeBg.setColors(rColors);
            removeQuickBtn.setBackground(removeBg);
            removeQuickBtn.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            removeQuickBtn.setOnClickListener(v -> showRemoveQuickButtonDialog());
            quickLayout.addView(removeQuickBtn);

            mainLayout.addView(quickLayout);

            // ===== КНОПКА СОХРАНЕНИЯ =====
            Button saveBtn = new Button(this);
            saveBtn.setText("💾 СОХРАНИТЬ ВСЁ");
            saveBtn.setTextColor(Color.WHITE);
            saveBtn.setTextSize(14);
            saveBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable saveBg = new GradientDrawable();
            saveBg.setCornerRadius(12);
            int[] sColors = getRainbowColors(60);
            saveBg.setColors(sColors);
            saveBtn.setBackground(saveBg);
            saveBtn.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            saveBtn.setOnClickListener(v -> {
                saveMacroPages();
                Toast.makeText(this, "✅ Всё сохранено!", Toast.LENGTH_SHORT).show();
                playSaveSound();
            });
            mainLayout.addView(saveBtn);

            wrapper.addView(mainLayout);

            // Обновляем UI
            updateMacroUI(mainLayout);
            updatePageDisplay(mainLayout);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    480, 700,
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

    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ДЛЯ СТРАНИЦ =====

    private void updatePageDisplay(LinearLayout mainLayout) {
        // Находим TextView с номером страницы
        for (int i = 0; i < mainLayout.getChildCount(); i++) {
            View child = mainLayout.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout ll = (LinearLayout) child;
                for (int j = 0; j < ll.getChildCount(); j++) {
                    View v = ll.getChildAt(j);
                    if (v instanceof TextView) {
                        String text = ((TextView) v).getText().toString();
                        if (text.startsWith("Страница")) {
                            ((TextView) v).setText("Страница " + (currentPageIndex + 1) + "/" + macroPages.size());
                            return;
                        }
                    }
                }
            }
        }
    }

    private void createNewPage(LinearLayout mainLayout) {
        if (macroPages.size() >= maxPages) {
            Toast.makeText(this, "⚠️ Достигнут лимит страниц (" + maxPages + ")", Toast.LENGTH_SHORT).show();
            return;
        }
        
        int newPageNumber = macroPages.size() + 1;
        MacroPage newPage = new MacroPage(newPageNumber);
        macroPages.add(newPage);
        currentPageIndex = macroPages.size() - 1;
        updateCurrentPageData();
        updateMacroUI(mainLayout);
        updatePageDisplay(mainLayout);
        Toast.makeText(this, "✅ Создана страница " + newPageNumber, Toast.LENGTH_SHORT).show();
        playSaveSound();
        saveMacroPages();
    }

    private void createNewMacroOnPage(LinearLayout mainLayout) {
        String newName = "Макрос " + (currentPageMacros.size() + 1);
        // Проверяем уникальность имени
        boolean exists = true;
        int counter = 1;
        while (exists) {
            exists = false;
            for (MacroConfig c : currentPageMacros) {
                if (c.name.equals(newName)) {
                    exists = true;
                    counter++;
                    newName = "Макрос " + counter;
                    break;
                }
            }
        }
        
        MacroConfig newMacro = new MacroConfig(newName);
        int[] colors = getRainbowColors(currentPageMacros.size() * 36);
        newMacro.color = colors[0];
        currentPageMacros.add(newMacro);
        currentMacroName = newName;
        
        // Обновляем счетчик кнопок
        MacroPage page = macroPages.get(currentPageIndex);
        page.buttonCounter = currentPageMacros.size() + 1;
        
        saveMacroPages();
        updateMacroUI(mainLayout);
        Toast.makeText(this, "✅ Создан макрос: " + newName, Toast.LENGTH_SHORT).show();
        playSaveSound();
    }

    private MacroConfig getCurrentPageMacro() {
        MacroPage page = macroPages.get(currentPageIndex);
        return page.getCurrentMacro();
    }

    private int getCurrentPageMacroIndex(String name) {
        MacroPage page = macroPages.get(currentPageIndex);
        return page.getMacroIndex(name);
    }

    private void updateMacroUI(LinearLayout mainLayout) {
        MacroPage page = macroPages.get(currentPageIndex);
        currentPageMacros = page.macros;
        currentMacroName = page.currentMacroName;
        quickButtons = page.quickButtons;
        buttonCounter = page.buttonCounter;
        
        MacroConfig config = getCurrentPageMacro();
        if (config == null) return;

        // Находим контейнер для точек
        LinearLayout pointsContainer = null;
        for (int i = 0; i < mainLayout.getChildCount(); i++) {
            View v = mainLayout.getChildAt(i);
            if (v instanceof LinearLayout) {
                LinearLayout ll = (LinearLayout) v;
                for (int j = 0; j < ll.getChildCount(); j++) {
                    View child = ll.getChildAt(j);
                    if (child instanceof ScrollView) {
                        pointsContainer = (LinearLayout) ((ScrollView) child).getChildAt(0);
                        break;
                    }
                }
            }
        }

        if (pointsContainer == null) return;
        pointsContainer.removeAllViews();

        // Обновляем имя макроса в заголовке
        for (int i = 0; i < mainLayout.getChildCount(); i++) {
            View v = mainLayout.getChildAt(i);
            if (v instanceof LinearLayout) {
                LinearLayout ll = (LinearLayout) v;
                for (int j = 0; j < ll.getChildCount(); j++) {
                    View child = ll.getChildAt(j);
                    if (child instanceof TextView) {
                        TextView tv = (TextView) child;
                        if (tv.getText().toString().equals(currentMacroName) || 
                            currentMacroName.contains(tv.getText().toString())) {
                            tv.setText(currentMacroName);
                            break;
                        }
                    }
                }
            }
        }

        // Заголовок с информацией о макросе
        TextView header = new TextView(this);
        String pageInfo = "📄 Стр." + (currentPageIndex + 1) + " | ";
        header.setText(pageInfo + "📌 " + config.name + " | Точек: " + config.points.size() + " | 🔄 Цикл: " + config.repeatCount);
        header.setTextColor(0xFFFFAA00);
        header.setTextSize(13);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setPadding(0, 0, 0, 8);
        pointsContainer.addView(header);

        if (config.points.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Нет точек.\n● - клик  ↗ - свайп\n\nНажмите ◄ или ► для выбора макроса\nили ➕ для создания нового");
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
                if (p.type == ActionType.SWIPE) {
                    int[] colors = getRainbowColors(i * 30 + 120);
                    itemBg.setColors(colors);
                } else {
                    int[] colors = getRainbowColors(i * 30);
                    itemBg.setColors(colors);
                }
                itemBg.setAlpha(100);
                item.setBackground(itemBg);

                TextView info = new TextView(this);
                String typeIcon = p.getTypeIcon();
                String delayText = p.delayMs + "мс";
                String posText = p.getDisplayText();
                info.setText(" " + typeIcon + " #" + (i+1) + " " + posText + " " + delayText);
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
                delayBtn.setOnClickListener(v -> showDelayDialog(idx, mainLayout));
                item.addView(delayBtn);

                Button gpsBtn = new Button(this);
                gpsBtn.setText("📍");
                gpsBtn.setTextColor(0xFFAA00FF);
                gpsBtn.setTextSize(14);
                gpsBtn.setBackgroundColor(0x33AA00FF);
                gpsBtn.setPadding(8, 4, 8, 4);
                gpsBtn.setOnClickListener(v -> {
                    startGpsMode(currentMacroName, idx);
                });
                item.addView(gpsBtn);

                Button delBtn = new Button(this);
                delBtn.setText("✕");
                delBtn.setTextColor(0xFFFF0000);
                delBtn.setTextSize(12);
                delBtn.setBackgroundColor(0x33FF0000);
                delBtn.setPadding(8, 4, 8, 4);
                delBtn.setOnClickListener(v -> {
                    config.points.remove(idx);
                    saveMacroPages();
                    updateMacroUI(mainLayout);
                });
                item.addView(delBtn);

                pointsContainer.addView(item);
            }
        }
    }

    private Button createStyledButton(String text, int color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(12);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(12);
        int[] colors = getRainbowColors((int)(Math.random() * 360));
        bg.setColors(colors);
        bg.setAlpha(200);
        btn.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(3, 0, 3, 0);
        btn.setLayoutParams(lp);

        return btn;
    }

    private void showDelayDialog(final int index, final LinearLayout mainLayout) {
        MacroConfig config = getCurrentPageMacro();
        if (config == null || index >= config.points.size()) return;

        long currentDelay = config.points.get(index).delayMs;
        String displayValue = String.valueOf(currentDelay);

        showKeyboardDialog("⏱ ЗАДЕРЖКА (мс)", displayValue, 6, () -> {
            try {
                long value = Long.parseLong(keyboardValue);
                if (value < 10) value = 10;
                MacroConfig cfg = getCurrentPageMacro();
                if (cfg != null && index < cfg.points.size()) {
                    cfg.points.get(index).delayMs = value;
                    cfg.points.get(index).delayDisplay = value + "мс";
                    saveMacroPages();
                    updateMacroUI(mainLayout);
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Ошибка", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void clearMacroPoints(LinearLayout mainLayout) {
        MacroConfig config = getCurrentPageMacro();
        if (config != null && !config.points.isEmpty()) {
            config.points.clear();
            saveMacroPages();
            updateMacroUI(mainLayout);
            Toast.makeText(this, "Все точки удалены", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Нет точек для удаления", Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== ЗАПИСЬ МАКРОСА ====================

    private void startMacroRecording(final LinearLayout mainLayout, ActionType type) {
        if (isMacroRecording) return;
        if (windowManager == null) return;
        
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
                        Toast.makeText(this, "Начало свайпа: (" + x + "," + y + ")", Toast.LENGTH_SHORT).show();
                        return true;
                    } else {
                        swipeEndX = x;
                        swipeEndY = y;
                        addMacroSwipe((int)swipeStartX, (int)swipeStartY, (int)swipeEndX, (int)swipeEndY, mainLayout);
                        swipeStartX = 0;
                        swipeStartY = 0;
                        return true;
                    }
                } else {
                    addMacroPoint(x, y, mainLayout);
                }
                return true;
            }
            return false;
        });
        
        windowManager.addView(captureOverlay, captureParams);
    }

    private void addMacroPoint(int x, int y, LinearLayout mainLayout) {
        MacroConfig config = getCurrentPageMacro();
        if (config == null) return;

        MacroPoint point = new MacroPoint(x, y);
        config.points.add(point);
        saveMacroPages();

        Toast.makeText(this, "Клик #" + config.points.size() + ": (" + x + "," + y + ")", Toast.LENGTH_SHORT).show();
        playClickSound();

        showDelayDialog(config.points.size() - 1, mainLayout);

        if (captureOverlay != null && windowManager != null) {
            windowManager.removeView(captureOverlay);
            captureOverlay = null;
            isMacroRecording = false;
        }

        updateMacroUI(mainLayout);
    }

    private void addMacroSwipe(int startX, int startY, int endX, int endY, LinearLayout mainLayout) {
        MacroConfig config = getCurrentPageMacro();
        if (config == null) return;

        MacroPoint point = new MacroPoint(startX, startY, endX, endY, 300);
        config.points.add(point);
        saveMacroPages();

        Toast.makeText(this, "Свайп #" + config.points.size() + ": (" + startX + "," + startY + ") → (" + endX + "," + endY + ")", Toast.LENGTH_SHORT).show();
        playClickSound();

        showDelayDialog(config.points.size() - 1, mainLayout);

        if (captureOverlay != null && windowManager != null) {
            windowManager.removeView(captureOverlay);
            captureOverlay = null;
            isMacroRecording = false;
        }

        updateMacroUI(mainLayout);
    }

    // ==================== ВЫПОЛНЕНИЕ МАКРОСА ====================

    private void startMacroExecution(String macroName) {
        MacroPage page = macroPages.get(currentPageIndex);
        MacroConfig config = page.getMacroConfig(macroName);
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
        Toast.makeText(this, "🚀 Макрос '" + macroName + "' запущен! Циклов: " + macroRepeatCount, Toast.LENGTH_SHORT).show();
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

    // ==================== БЫСТРЫЕ КНОПКИ ====================

    private void createQuickButton(String macroName) {
        try {
            if (windowManager == null) return;

            MacroPage page = macroPages.get(currentPageIndex);
            
            // Проверяем, существует ли уже кнопка для этого макроса на текущей странице
            if (page.quickButtons.containsKey(macroName)) {
                QuickMacroButton existing = page.quickButtons.get(macroName);
                if (existing.container != null && existing.container.getParent() != null) {
                    Toast.makeText(this, "Кнопка уже существует для макроса: " + macroName, Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            QuickMacroButton btn = page.quickButtons.containsKey(macroName) ? 
                page.quickButtons.get(macroName) : new QuickMacroButton(macroName);
            
            btn.size = 160;
            btn.textSize = 32;
            btn.borderWidth = 4;
            btn.pageNumber = currentPageIndex + 1;
            
            // Радужные цвета для кнопки
            int[] colors = getRainbowColors((int)(System.currentTimeMillis() / 200) % 360);
            btn.color1 = colors[0];
            btn.color2 = colors[1];
            
            // Имя кнопки: M{номер_страницы}-{номер_макроса}
            String buttonDisplayName = "M" + (currentPageIndex + 1) + "-" + macroName;
            btn.text = buttonDisplayName;
            
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
            
            // Автоматическое размещение кнопок с сеткой
            int buttonIndex = page.quickButtons.size();
            int columns = 3;
            int spacing = 190;
            int startX = 30;
            int startY = 100 + (currentPageIndex * 30); // Смещение для разных страниц
            
            int col = buttonIndex % columns;
            int row = buttonIndex / columns;
            
            btn.params.x = startX + (col * spacing);
            btn.params.y = startY + (row * spacing);

            btn.container.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (btn.isFixed) {
                        if (event.getAction() == MotionEvent.ACTION_UP) {
                            startMacroExecution(btn.macroName);
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
                                vibrate();
                            }
                            return true;
                    }
                    return false;
                }
            });

            windowManager.addView(btn.container, btn.params);
            page.quickButtons.put(macroName, btn);
            quickButtons = page.quickButtons;
            saveMacroPages();
            Toast.makeText(this, "✅ Кнопка " + buttonDisplayName + " создана!", Toast.LENGTH_SHORT).show();
            playSaveSound();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка создания кнопки", Toast.LENGTH_SHORT).show();
        }
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
        } else {
            drawable.setCornerRadius(0);
        }
        
        if (btn.useGradient) {
            int[] colors = getRainbowColors((int)(System.currentTimeMillis() / 300) % 360);
            drawable.setColors(colors);
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

        // Текст на кнопке
        TextView textView = new TextView(this);
        String displayName = btn.text;
        if (displayName.length() > 10) {
            displayName = displayName.substring(0, 9) + "…";
        }
        textView.setText(displayName);
        textView.setTextColor(btn.textColor);
        textView.setTextSize(btn.textSize);
        textView.setGravity(Gravity.CENTER);
        textView.setPadding(4, 4, 4, 4);
        textView.setTypeface(null, android.graphics.Typeface.BOLD);
        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
                btn.size, btn.size);
        textView.setLayoutParams(textParams);
        frame.addView(textView);

        if (btn.isFixed) {
            TextView lockIcon = new TextView(this);
            lockIcon.setText("🔒");
            lockIcon.setTextColor(Color.WHITE);
            lockIcon.setTextSize(20);
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

    private void createQuickButtonUI(String macroName, QuickMacroButton btn) {
        try {
            if (windowManager == null) return;
            
            if (btn.container != null && btn.container.getParent() != null) {
                return;
            }
            
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

            btn.container.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (btn.isFixed) {
                        if (event.getAction() == MotionEvent.ACTION_UP) {
                            startMacroExecution(btn.macroName);
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
                                vibrate();
                            }
                            return true;
                    }
                    return false;
                }
            });

            windowManager.addView(btn.container, btn.params);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateQuickButtonUI(String macroName) {
        MacroPage page = macroPages.get(currentPageIndex);
        QuickMacroButton btn = page.quickButtons.get(macroName);
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

    private void showButtonEditor(final String macroName) {
        MacroPage page = macroPages.get(currentPageIndex);
        final QuickMacroButton btn = page.quickButtons.get(macroName);
        if (btn == null || btn.container == null) {
            Toast.makeText(this, "Кнопка не найдена для макроса: " + macroName, Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("✏️ РЕДАКТОР: " + btn.text);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);
        layout.setBackgroundColor(0xFF1A1A1A);

        // Размер
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
        sizeSeek.setMin(100);
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

        // Закрепить
        Button fixBtn = new Button(this);
        fixBtn.setText(btn.isFixed ? "🔓 РАЗБЛОКИРОВАТЬ" : "🔒 ЗАКРЕПИТЬ");
        fixBtn.setTextColor(Color.WHITE);
        GradientDrawable fixBg = new GradientDrawable();
        fixBg.setCornerRadius(12);
        int[] colors = getRainbowColors(btn.isFixed ? 60 : 240);
        fixBg.setColors(colors);
        fixBtn.setBackground(fixBg);
        fixBtn.setPadding(20, 12, 20, 12);
        fixBtn.setOnClickListener(v -> {
            btn.isFixed = !btn.isFixed;
            fixBtn.setText(btn.isFixed ? "🔓 РАЗБЛОКИРОВАТЬ" : "🔒 ЗАКРЕПИТЬ");
            updateQuickButtonUI(macroName);
            saveMacroPages();
            playClickSound();
            Toast.makeText(this, btn.isFixed ? "🔒 Кнопка закреплена" : "🔓 Кнопка разблокирована", Toast.LENGTH_SHORT).show();
        });
        layout.addView(fixBtn);

        // Применить
        Button applyBtn = new Button(this);
        applyBtn.setText("✅ ПРИМЕНИТЬ");
        applyBtn.setTextColor(Color.WHITE);
        GradientDrawable applyBg = new GradientDrawable();
        applyBg.setCornerRadius(12);
        int[] applyColors = getRainbowColors(120);
        applyBg.setColors(applyColors);
        applyBtn.setBackground(applyBg);
        applyBtn.setPadding(20, 12, 20, 12);
        applyBtn.setOnClickListener(v -> {
            btn.size = sizeSeek.getProgress();
            btn.params.width = btn.size + 20;
            btn.params.height = btn.size + 20;
            updateQuickButtonUI(macroName);
            saveMacroPages();
            playSaveSound();
            Toast.makeText(this, "✅ Настройки применены!", Toast.LENGTH_SHORT).show();
        });
        layout.addView(applyBtn);

        // Удалить кнопку
        Button delBtn = new Button(this);
        delBtn.setText("🗑 УДАЛИТЬ КНОПКУ");
        delBtn.setTextColor(Color.WHITE);
        GradientDrawable delBg = new GradientDrawable();
        delBg.setCornerRadius(12);
        int[] delColors = getRainbowColors(0);
        delBg.setColors(delColors);
        delBtn.setBackground(delBg);
        delBtn.setPadding(20, 12, 20, 12);
        delBtn.setOnClickListener(v -> {
            removeQuickButton(macroName);
            Toast.makeText(this, "🗑 Кнопка удалена", Toast.LENGTH_SHORT).show();
        });
        layout.addView(delBtn);

        builder.setView(layout);
        builder.setPositiveButton("✕ Закрыть", null);
        builder.show();
    }

    private void showRemoveQuickButtonDialog() {
        MacroPage page = macroPages.get(currentPageIndex);
        if (page.quickButtons.isEmpty()) {
            Toast.makeText(this, "Нет быстрых кнопок на этой странице", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] names = page.quickButtons.keySet().toArray(new String[0]);
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
            MacroPage page = macroPages.get(currentPageIndex);
            QuickMacroButton btn = page.quickButtons.remove(macroName);
            if (btn != null && btn.container != null && windowManager != null) {
                windowManager.removeView(btn.container);
                saveMacroPages();
                playDeleteSound();
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

        // Информация о страницах
        TextView infoText = new TextView(this);
        infoText.setText("📄 Страниц: " + macroPages.size() + " / " + maxPages);
        infoText.setTextColor(0xFFFFAA00);
        infoText.setTextSize(14);
        infoText.setGravity(Gravity.CENTER);
        infoText.setPadding(0, 20, 0, 10);
        layout.addView(infoText);

        Button exportBtn = new Button(this);
        exportBtn.setText("💾 ЭКСПОРТ КОНФИГА");
        exportBtn.setTextColor(Color.WHITE);
        GradientDrawable exportBg = new GradientDrawable();
        exportBg.setCornerRadius(12);
        int[] expColors = getRainbowColors(60);
        exportBg.setColors(expColors);
        exportBtn.setBackground(exportBg);
        exportBtn.setPadding(20, 12, 20, 12);
        exportBtn.setOnClickListener(v -> exportConfig());
        layout.addView(exportBtn);

        Button importBtn = new Button(this);
        importBtn.setText("📂 ИМПОРТ КОНФИГА");
        importBtn.setTextColor(Color.WHITE);
        GradientDrawable importBg = new GradientDrawable();
        importBg.setCornerRadius(12);
        int[] impColors = getRainbowColors(180);
        importBg.setColors(impColors);
        importBtn.setBackground(importBg);
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
            
            // Экспорт страниц макросов
            JSONArray pagesArray = new JSONArray();
            for (MacroPage page : macroPages) {
                pagesArray.put(page.toJSON());
            }
            config.put("macro_pages", pagesArray);
            
            config.put("overlay_alpha", overlayAlpha);
            config.put("overlay_size", overlaySize);
            config.put("current_page", currentPageIndex);
            
            String jsonString = config.toString(2);
            
            File dir = new File(getExternalFilesDir(null), "configs");
            if (!dir.exists()) dir.mkdirs();
            
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File file = new File(dir, "arcade_config_" + timeStamp + ".json");
            
            FileOutputStream out = new FileOutputStream(file);
            out.write(jsonString.getBytes());
            out.close();
            
            Toast.makeText(this, "✅ Конфиг сохранён: " + file.getName(), Toast.LENGTH_LONG).show();
            playSaveSound();
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

    private void importConfigFromJson(String json) {
        try {
            JSONObject config = new JSONObject(json);
            
            if (config.has("macro_pages")) {
                JSONArray pagesArray = config.getJSONArray("macro_pages");
                macroPages.clear();
                for (int i = 0; i < pagesArray.length(); i++) {
                    macroPages.add(new MacroPage(pagesArray.getJSONObject(i)));
                }
                if (!macroPages.isEmpty()) {
                    currentPageIndex = config.optInt("current_page", 0);
                    if (currentPageIndex >= macroPages.size()) {
                        currentPageIndex = macroPages.size() - 1;
                    }
                    updateCurrentPageData();
                }
                saveMacroPages();
            }
            
            if (config.has("overlay_alpha")) {
                overlayAlpha = config.getInt("overlay_alpha");
                prefs.edit().putInt("overlay_alpha", overlayAlpha).apply();
            }
            if (config.has("overlay_size")) {
                overlaySize = config.getInt("overlay_size");
                prefs.edit().putInt("overlay_size", overlaySize).apply();
            }
            
            // Пересоздаем быстрые кнопки для текущей страницы
            MacroPage page = macroPages.get(currentPageIndex);
            for (String name : page.quickButtons.keySet()) {
                QuickMacroButton btn = page.quickButtons.get(name);
                createQuickButtonUI(name, btn);
            }
            
            Toast.makeText(this, "✅ Конфиг импортирован успешно! Страниц: " + macroPages.size(), Toast.LENGTH_LONG).show();
            playSaveSound();
            
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "❌ Ошибка импорта конфига", Toast.LENGTH_SHORT).show();
        }
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
            macroHandler.removeCallbacksAndMessages(null);
            closeKeyboard();
            
            if (clickSound != null) clickSound.release();
            if (deleteSound != null) deleteSound.release();
            if (saveSound != null) saveSound.release();
            
            // Удаляем все быстрые кнопки
            for (MacroPage page : macroPages) {
                for (QuickMacroButton btn : page.quickButtons.values()) {
                    if (btn.container != null && windowManager != null) {
                        windowManager.removeView(btn.container);
                    }
                }
            }
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
              }
