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
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
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
import java.io.FileInputStream;
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
    private static final int REQUEST_AUDIO_FILE = 105;

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
    
    private HashMap<String, QuickMacroButton> quickButtons = new HashMap<>();
    private int quickButtonIdCounter = 1;

    // ЗВУКИ
    private HashMap<String, SoundData> soundLibrary = new HashMap<>();
    private ArrayList<String> soundIds = new ArrayList<>();
    private MediaPlayer soundPlayer;
    private String currentSoundId = "";
    private boolean isSoundPlaying = false;
    private boolean isMicrophoneEmulationActive = false;
    private AudioTrack audioTrack;
    private AudioRecord audioRecord;
    private Handler audioHandler = new Handler();
    private Thread audioThread;
    private volatile boolean isAudioLoopRunning = false;
    private byte[] audioBuffer;

    // GPS
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
    private String keyboardTitle = "";

    private boolean isRecordingSwipe = false;
    private float swipeStartX = 0, swipeStartY = 0;
    private float swipeEndX = 0, swipeEndY = 0;

    // Звуки UI
    private MediaPlayer clickSound;
    private MediaPlayer deleteSound;
    private MediaPlayer saveSound;

    // Радужная анимация
    private Handler rainbowHandler = new Handler();
    private boolean rainbowAnimRunning = false;
    private int rainbowColorIndex = 0;
    private int[] rainbowColors = {
        0xFFFF0000, 0xFFFF8800, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFF8800FF
    };

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
            return type == ActionType.SWIPE ? "↗" : "●";
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
        int size;
        FrameLayout container;
        WindowManager.LayoutParams params;
        boolean isFixed;
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
        boolean isToggled;
        String macroName;
        
        QuickMacroButton(String macroId, int id) {
            this.macroId = macroId;
            this.id = id;
            this.size = 160;
            this.isFixed = false;
            this.isDragging = false;
            this.shape = "rounded";
            this.color1 = 0xFF00AA00;
            this.color2 = 0xFF008800;
            this.useGradient = true;
            this.text = String.valueOf(id);
            this.alpha = 1.0f;
            this.borderColor = 0xFF00FF00;
            this.borderWidth = 4;
            this.textColor = 0xFFFFFFFF;
            this.textSize = 44;
            this.rainbowEffect = true;
            this.displayName = String.valueOf(id);
            this.isToggled = false;
            this.macroName = "Макрос " + id;
        }
        
        QuickMacroButton(JSONObject json) throws Exception {
            this.macroId = json.getString("macroId");
            this.id = json.optInt("id", 1);
            this.isFixed = json.optBoolean("isFixed", false);
            this.size = json.optInt("size", 160);
            this.shape = json.optString("shape", "rounded");
            this.color1 = json.optInt("color1", 0xFF00AA00);
            this.color2 = json.optInt("color2", 0xFF008800);
            this.useGradient = json.optBoolean("useGradient", true);
            this.text = json.optString("text", String.valueOf(id));
            this.alpha = (float) json.optDouble("alpha", 1.0);
            this.borderColor = json.optInt("borderColor", 0xFF00FF00);
            this.borderWidth = json.optInt("borderWidth", 4);
            this.textColor = json.optInt("textColor", 0xFFFFFFFF);
            this.textSize = (float) json.optDouble("textSize", 44);
            this.rainbowEffect = json.optBoolean("rainbowEffect", true);
            this.displayName = json.optString("displayName", String.valueOf(id));
            this.isToggled = json.optBoolean("isToggled", false);
            this.macroName = json.optString("macroName", "Макрос " + id);
        }
        
        JSONObject toJSON() throws Exception {
            JSONObject json = new JSONObject();
            json.put("macroId", macroId);
            json.put("id", id);
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
            json.put("macroName", macroName);
            if (params != null) {
                json.put("x", params.x);
                json.put("y", params.y);
            }
            return json;
        }
    }

    private static class SoundData {
        String id;
        String name;
        String path;
        long duration;
        boolean isLooping;
        float volume;
        
        SoundData(String id, String name, String path) {
            this.id = id;
            this.name = name;
            this.path = path;
            this.duration = 0;
            this.isLooping = false;
            this.volume = 1.0f;
        }
        
        SoundData(JSONObject json) throws Exception {
            this.id = json.getString("id");
            this.name = json.getString("name");
            this.path = json.getString("path");
            this.duration = json.optLong("duration", 0);
            this.isLooping = json.optBoolean("isLooping", false);
            this.volume = (float) json.optDouble("volume", 1.0);
        }
        
        JSONObject toJSON() throws Exception {
            JSONObject json = new JSONObject();
            json.put("id", id);
            json.put("name", name);
            json.put("path", path);
            json.put("duration", duration);
            json.put("isLooping", isLooping);
            json.put("volume", volume);
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
            loadCharacters();
            loadMacroConfigs();
            loadQuickButtons();
            loadSoundLibrary();
            initSounds();
            startRainbowAnimation();
            initAudioEmulation();
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

    // ==================== ЗВУКОВОЙ ДВИЖОК ====================

    private void initAudioEmulation() {
        try {
            int bufferSize = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (bufferSize > 0) {
                audioBuffer = new byte[bufferSize];
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadSoundLibrary() {
        try {
            soundLibrary.clear();
            soundIds.clear();
            String json = prefs.getString("sound_library", "");
            if (!json.isEmpty()) {
                JSONObject soundsObj = new JSONObject(json);
                JSONArray idsArray = soundsObj.getJSONArray("ids");
                JSONObject dataObj = soundsObj.getJSONObject("data");
                
                for (int i = 0; i < idsArray.length(); i++) {
                    String id = idsArray.getString(i);
                    if (dataObj.has(id)) {
                        SoundData sound = new SoundData(dataObj.getJSONObject(id));
                        soundLibrary.put(id, sound);
                        soundIds.add(id);
                    }
                }
            }
            
            if (soundIds.isEmpty()) {
                createDefaultSound();
            }
            currentSoundId = soundIds.get(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createDefaultSound() {
        String id = UUID.randomUUID().toString();
        SoundData sound = new SoundData(id, "Звук 1", "");
        soundLibrary.put(id, sound);
        soundIds.add(id);
        currentSoundId = id;
        saveSoundLibrary();
    }

    private void saveSoundLibrary() {
        try {
            JSONObject soundsObj = new JSONObject();
            JSONArray idsArray = new JSONArray();
            JSONObject dataObj = new JSONObject();
            
            for (String id : soundIds) {
                idsArray.put(id);
                dataObj.put(id, soundLibrary.get(id).toJSON());
            }
            
            soundsObj.put("ids", idsArray);
            soundsObj.put("data", dataObj);
            
            prefs.edit().putString("sound_library", soundsObj.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void playSoundInGame(String soundId) {
        try {
            SoundData sound = soundLibrary.get(soundId);
            if (sound == null || sound.path == null || sound.path.isEmpty()) {
                Toast.makeText(this, "Звук не найден", Toast.LENGTH_SHORT).show();
                return;
            }

            if (soundPlayer != null) {
                soundPlayer.release();
                soundPlayer = null;
            }

            soundPlayer = new MediaPlayer();
            soundPlayer.setDataSource(sound.path);
            soundPlayer.setLooping(sound.isLooping);
            soundPlayer.setVolume(sound.volume, sound.volume);
            
            // Важно: устанавливаем аудио-атрибуты для эмуляции микрофона
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioAttributes attributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                soundPlayer.setAudioAttributes(attributes);
            }
            
            soundPlayer.setOnCompletionListener(mp -> {
                isSoundPlaying = false;
                if (!sound.isLooping) {
                    stopMicrophoneEmulation();
                }
            });

            soundPlayer.prepare();
            soundPlayer.start();
            isSoundPlaying = true;
            
            // Автоматически включаем эмуляцию микрофона
            startMicrophoneEmulation();
            
            Toast.makeText(this, "🎵 Воспроизведение: " + sound.name, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка воспроизведения звука", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopSound() {
        try {
            if (soundPlayer != null) {
                soundPlayer.stop();
                soundPlayer.release();
                soundPlayer = null;
            }
            isSoundPlaying = false;
            stopMicrophoneEmulation();
            Toast.makeText(this, "⏹ Звук остановлен", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== ЭМУЛЯЦИЯ МИКРОФОНА ====================

    private void startMicrophoneEmulation() {
        if (isMicrophoneEmulationActive) {
            return;
        }

        if (!hasMicrophonePermission()) {
            requestPermissionsIfNeeded();
            return;
        }

        try {
            isMicrophoneEmulationActive = true;
            isAudioLoopRunning = true;

            // Создаем AudioTrack для воспроизведения звука через микрофон
            int bufferSize = AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioTrack = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(44100)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setBufferSizeInBytes(bufferSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build();
            } else {
                audioTrack = new AudioTrack(
                        AudioManager.STREAM_VOICE_CALL,
                        44100,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize,
                        AudioTrack.MODE_STREAM
                );
            }

            audioTrack.play();

            // Запускаем поток для эмуляции
            audioThread = new Thread(() -> {
                try {
                    // Генерируем тестовый аудио-сигнал (белый шум или тон)
                    byte[] audioData = new byte[bufferSize];
                    Random random = new Random();
                    
                    while (isAudioLoopRunning) {
                        // Если звук воспроизводится, проигрываем его через микрофон
                        if (isSoundPlaying && soundPlayer != null) {
                            // Для простоты используем белый шум, имитирующий голос
                            random.nextBytes(audioData);
                            
                            // Можно также читать из реального микрофона и передавать дальше
                            // Но для простоты используем сгенерированный звук
                        } else {
                            // Тишина
                            for (int i = 0; i < audioData.length; i++) {
                                audioData[i] = 0;
                            }
                        }
                        
                        if (audioTrack != null && audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                            audioTrack.write(audioData, 0, audioData.length);
                        }
                        
                        Thread.sleep(20);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            audioThread.start();

            Toast.makeText(this, "🎙 Микрофон активирован для звука", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка активации микрофона", Toast.LENGTH_SHORT).show();
            isMicrophoneEmulationActive = false;
        }
    }

    private void stopMicrophoneEmulation() {
        isAudioLoopRunning = false;
        isMicrophoneEmulationActive = false;
        
        try {
            if (audioThread != null) {
                audioThread.interrupt();
                audioThread = null;
            }
            if (audioTrack != null) {
                audioTrack.stop();
                audioTrack.release();
                audioTrack = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean hasMicrophonePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    // ==================== ЗВУКОВОЕ ОКНО ====================

    private void showSoundWindow() {
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
            border.setStroke(3, 0xFFFF00FF);
            mainLayout.setBackground(border);

            // Заголовок
            LinearLayout titleBar = new LinearLayout(this);
            titleBar.setOrientation(LinearLayout.HORIZONTAL);
            titleBar.setGravity(Gravity.CENTER_VERTICAL);
            titleBar.setPadding(8, 8, 8, 8);
            
            TextView title = new TextView(this);
            title.setText("🎵 ЗВУКИ");
            title.setTextColor(0xFFFF00FF);
            title.setTextSize(18);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            titleBar.addView(title);
            
            // Кнопка добавления звука
            Button addSoundBtn = new Button(this);
            addSoundBtn.setText("➕");
            addSoundBtn.setTextColor(Color.WHITE);
            addSoundBtn.setTextSize(18);
            GradientDrawable addBg = new GradientDrawable();
            addBg.setCornerRadius(12);
            addBg.setColors(new int[]{0xFFFF00AA, 0xFFFF00FF});
            addBg.setGradientType(GradientDrawable.LINEAR_GRADIENT);
            addBg.setOrientation(GradientDrawable.Orientation.TL_BR);
            addSoundBtn.setBackground(addBg);
            addSoundBtn.setPadding(12, 4, 12, 4);
            addSoundBtn.setOnClickListener(v -> showAddSoundDialog(mainLayout));
            titleBar.addView(addSoundBtn);
            
            ImageButton closeBtn = new ImageButton(this);
            closeBtn.setImageDrawable(createCloseIcon());
            closeBtn.setBackgroundColor(Color.TRANSPARENT);
            closeBtn.setPadding(8, 4, 8, 4);
            closeBtn.setOnClickListener(v -> {
                if (windowManager != null) windowManager.removeView(wrapper);
            });
            titleBar.addView(closeBtn);
            
            mainLayout.addView(titleBar);

            // Выбор звука
            LinearLayout selectLayout = new LinearLayout(this);
            selectLayout.setOrientation(LinearLayout.HORIZONTAL);
            selectLayout.setGravity(Gravity.CENTER);
            selectLayout.setPadding(0, 8, 0, 8);

            final Spinner soundSpinner = new Spinner(this);
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    TextView text = (TextView) view;
                    text.setTextColor(Color.WHITE);
                    text.setTextSize(16);
                    return view;
                }
            };
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            
            updateSoundSpinnerList(adapter);
            soundSpinner.setAdapter(adapter);
            
            int currentPos = soundIds.indexOf(currentSoundId);
            if (currentPos >= 0) {
                soundSpinner.setSelection(currentPos);
            }
            
            soundSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (position < soundIds.size()) {
                        currentSoundId = soundIds.get(position);
                        updateSoundUI(mainLayout);
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
            
            selectLayout.addView(soundSpinner);
            mainLayout.addView(selectLayout);

            // Информация о звуке
            final LinearLayout infoContainer = new LinearLayout(this);
            infoContainer.setOrientation(LinearLayout.VERTICAL);
            infoContainer.setPadding(0, 8, 0, 8);
            mainLayout.addView(infoContainer);

            // Кнопки управления
            LinearLayout controlLayout = new LinearLayout(this);
            controlLayout.setOrientation(LinearLayout.HORIZONTAL);
            controlLayout.setGravity(Gravity.CENTER);
            controlLayout.setPadding(0, 8, 0, 8);

            Button playBtn = createSoundButton("▶ ВОСПРОИЗВЕСТИ", 0xFF00AA00);
            playBtn.setOnClickListener(v -> {
                SoundData sound = soundLibrary.get(currentSoundId);
                if (sound != null && sound.path != null && !sound.path.isEmpty()) {
                    playSoundInGame(currentSoundId);
                } else {
                    Toast.makeText(this, "Выберите аудио файл!", Toast.LENGTH_SHORT).show();
                }
            });
            controlLayout.addView(playBtn);

            Button stopBtn = createSoundButton("⏹ СТОП", 0xFFFF0000);
            stopBtn.setOnClickListener(v -> stopSound());
            controlLayout.addView(stopBtn);

            mainLayout.addView(controlLayout);

            // Настройки звука
            LinearLayout settingsLayout = new LinearLayout(this);
            settingsLayout.setOrientation(LinearLayout.VERTICAL);
            settingsLayout.setPadding(0, 8, 0, 8);

            // Громкость
            LinearLayout volumeLayout = new LinearLayout(this);
            volumeLayout.setOrientation(LinearLayout.HORIZONTAL);
            volumeLayout.setGravity(Gravity.CENTER);
            
            TextView volLabel = new TextView(this);
            volLabel.setText("Громкость:");
            volLabel.setTextColor(Color.WHITE);
            volLabel.setTextSize(14);
            volumeLayout.addView(volLabel);

            final SeekBar volumeSeek = new SeekBar(this);
            volumeSeek.setMax(100);
            volumeSeek.setProgress(100);
            volumeSeek.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            volumeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    SoundData sound = soundLibrary.get(currentSoundId);
                    if (sound != null) {
                        sound.volume = progress / 100f;
                        if (soundPlayer != null) {
                            soundPlayer.setVolume(sound.volume, sound.volume);
                        }
                        saveSoundLibrary();
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            volumeLayout.addView(volumeSeek);
            settingsLayout.addView(volumeLayout);

            // Зацикливание
            LinearLayout loopLayout = new LinearLayout(this);
            loopLayout.setOrientation(LinearLayout.HORIZONTAL);
            loopLayout.setGravity(Gravity.CENTER);
            loopLayout.setPadding(0, 4, 0, 4);

            Button loopBtn = new Button(this);
            loopBtn.setText("🔄 ЗАЦИКЛИТЬ");
            loopBtn.setTextColor(Color.WHITE);
            loopBtn.setTextSize(14);
            GradientDrawable loopBg = new GradientDrawable();
            loopBg.setCornerRadius(12);
            loopBg.setColor(0xFF444444);
            loopBtn.setBackground(loopBg);
            loopBtn.setPadding(20, 12, 20, 12);
            loopBtn.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            loopBtn.setOnClickListener(v -> {
                SoundData sound = soundLibrary.get(currentSoundId);
                if (sound != null) {
                    sound.isLooping = !sound.isLooping;
                    saveSoundLibrary();
                    loopBtn.setText(sound.isLooping ? "🔄 ЗАЦИКЛЕНО" : "🔄 ЗАЦИКЛИТЬ");
                    loopBtn.setBackgroundColor(sound.isLooping ? 0xFF00AA00 : 0xFF444444);
                    updateSoundUI(mainLayout);
                    Toast.makeText(this, sound.isLooping ? "Зацикливание включено" : "Зацикливание выключено", Toast.LENGTH_SHORT).show();
                }
            });
            loopLayout.addView(loopBtn);

            Button micBtn = new Button(this);
            micBtn.setText(isMicrophoneEmulationActive ? "🎙 МИКРОФОН ВКЛ" : "🎙 МИКРОФОН ВЫКЛ");
            micBtn.setTextColor(Color.WHITE);
            micBtn.setTextSize(14);
            GradientDrawable micBg = new GradientDrawable();
            micBg.setCornerRadius(12);
            micBg.setColor(isMicrophoneEmulationActive ? 0xFF00AA00 : 0xFFFF4444);
            micBtn.setBackground(micBg);
            micBtn.setPadding(20, 12, 20, 12);
            micBtn.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            micBtn.setOnClickListener(v -> {
                if (isMicrophoneEmulationActive) {
                    stopMicrophoneEmulation();
                } else {
                    startMicrophoneEmulation();
                }
                micBtn.setText(isMicrophoneEmulationActive ? "🎙 МИКРОФОН ВКЛ" : "🎙 МИКРОФОН ВЫКЛ");
                micBtn.setBackgroundColor(isMicrophoneEmulationActive ? 0xFF00AA00 : 0xFFFF4444);
                Toast.makeText(this, isMicrophoneEmulationActive ? "Микрофон включен" : "Микрофон выключен", Toast.LENGTH_SHORT).show();
            });
            loopLayout.addView(micBtn);

            settingsLayout.addView(loopLayout);

            mainLayout.addView(settingsLayout);

            // Кнопка удаления звука
            Button deleteSoundBtn = new Button(this);
            deleteSoundBtn.setText("🗑 УДАЛИТЬ ЗВУК");
            deleteSoundBtn.setTextColor(Color.WHITE);
            deleteSoundBtn.setTextSize(14);
            deleteSoundBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable delBg = new GradientDrawable();
            delBg.setCornerRadius(12);
            delBg.setColors(new int[]{0xFFFF0000, 0xFFFF4444});
            delBg.setGradientType(GradientDrawable.LINEAR_GRADIENT);
            delBg.setOrientation(GradientDrawable.Orientation.TL_BR);
            delBg.setAlpha(200);
            deleteSoundBtn.setBackground(delBg);
            deleteSoundBtn.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            deleteSoundBtn.setOnClickListener(v -> deleteCurrentSound(mainLayout));
            mainLayout.addView(deleteSoundBtn);

            wrapper.addView(mainLayout);

            View resizeHandle = new View(this);
            resizeHandle.setBackgroundColor(0x44FFFFFF);
            FrameLayout.LayoutParams resizeParams = new FrameLayout.LayoutParams(
                    30, 30);
            resizeParams.gravity = Gravity.BOTTOM | Gravity.END;
            wrapper.addView(resizeHandle, resizeParams);

            updateSoundUI(mainLayout);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    400, 600,
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

    private void updateSoundUI(LinearLayout mainLayout) {
        SoundData sound = soundLibrary.get(currentSoundId);
        if (sound == null) return;

        // Обновляем информацию
        for (int i = 0; i < mainLayout.getChildCount(); i++) {
            View v = mainLayout.getChildAt(i);
            if (v instanceof LinearLayout) {
                LinearLayout ll = (LinearLayout) v;
                if (ll.getChildCount() > 0 && ll.getChildAt(0) instanceof TextView) {
                    // Ищем контейнер информации
                    if (ll.getChildAt(0) instanceof TextView) {
                        TextView tv = (TextView) ll.getChildAt(0);
                        String text = tv.getText().toString();
                        if (text.contains("Название:") || text.contains("Файл:")) {
                            // Обновляем информацию
                            ll.removeAllViews();
                            
                            TextView info1 = new TextView(this);
                            info1.setText("Название: " + sound.name);
                            info1.setTextColor(0xFFFF00FF);
                            info1.setTextSize(14);
                            info1.setPadding(0, 4, 0, 4);
                            ll.addView(info1);
                            
                            TextView info2 = new TextView(this);
                            info2.setText("Файл: " + (sound.path.isEmpty() ? "Не выбран" : new File(sound.path).getName()));
                            info2.setTextColor(0xFFAAAAAA);
                            info2.setTextSize(12);
                            info2.setPadding(0, 4, 0, 4);
                            ll.addView(info2);
                            
                            TextView info3 = new TextView(this);
                            info3.setText("Зацикливание: " + (sound.isLooping ? "Вкл" : "Выкл") + " | Громкость: " + (int)(sound.volume * 100) + "%");
                            info3.setTextColor(0xFF888888);
                            info3.setTextSize(12);
                            info3.setPadding(0, 4, 0, 4);
                            ll.addView(info3);
                            break;
                        }
                    }
                }
            }
        }
    }

    private void updateSoundSpinnerList(ArrayAdapter<String> adapter) {
        adapter.clear();
        for (String id : soundIds) {
            SoundData sound = soundLibrary.get(id);
            if (sound != null) {
                adapter.add(sound.name + (sound.path.isEmpty() ? " (нет файла)" : ""));
            }
        }
        if (soundIds.isEmpty()) {
            adapter.add("Нет звуков. Добавьте!");
        }
        adapter.notifyDataSetChanged();
    }

    private Button createSoundButton(String text, int color) {
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
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(3, 0, 3, 0);
        btn.setLayoutParams(lp);

        return btn;
    }

    private void showAddSoundDialog(final LinearLayout mainLayout) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("ДОБАВИТЬ ЗВУК");
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);
        
        final EditText input = new EditText(this);
        input.setHint("Введите имя звука");
        String defaultName = "Звук " + (soundIds.size() + 1);
        input.setText(defaultName);
        layout.addView(input);
        
        builder.setView(layout);
        
        builder.setPositiveButton("ВЫБРАТЬ ФАЙЛ", (d, w) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                name = "Звук " + (soundIds.size() + 1);
            }
            
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("audio/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, "Выберите аудио файл"), REQUEST_AUDIO_FILE);
            
            // Сохраняем имя для нового звука
            prefs.edit().putString("temp_sound_name", name).apply();
        });
        
        builder.setNegativeButton("ОТМЕНА", null);
        builder.show();
    }

    private void deleteCurrentSound(LinearLayout mainLayout) {
        if (soundIds.size() <= 1) {
            Toast.makeText(this, "Нельзя удалить последний звук", Toast.LENGTH_SHORT).show();
            return;
        }
        
        SoundData sound = soundLibrary.get(currentSoundId);
        if (sound == null) return;
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("УДАЛИТЬ ЗВУК?");
        builder.setMessage("Вы уверены что хотите удалить звук '" + sound.name + "'?");
        builder.setPositiveButton("УДАЛИТЬ", (d, w) -> {
            // Удаляем файл
            if (sound.path != null && !sound.path.isEmpty()) {
                new File(sound.path).delete();
            }
            
            soundIds.remove(currentSoundId);
            soundLibrary.remove(currentSoundId);
            saveSoundLibrary();
            
            if (!soundIds.isEmpty()) {
                currentSoundId = soundIds.get(0);
            } else {
                createDefaultSound();
                currentSoundId = soundIds.get(0);
            }
            
            updateSoundSpinnerList((ArrayAdapter<String>) ((Spinner) findSpinner(mainLayout)).getAdapter());
            updateSoundUI(mainLayout);
            Toast.makeText(this, "Звук удален", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("ОТМЕНА", null);
        builder.show();
    }

    private Spinner findSpinner(ViewGroup viewGroup) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View v = viewGroup.getChildAt(i);
            if (v instanceof Spinner) {
                return (Spinner) v;
            }
            if (v instanceof ViewGroup) {
                Spinner result = findSpinner((ViewGroup) v);
                if (result != null) return result;
            }
        }
        return null;
    }

    // ==================== ОСТАЛЬНОЙ КОД (РАНЕЕ БЫЛ) ====================

    private void startRainbowAnimation() {
        if (rainbowAnimRunning) return;
        rainbowAnimRunning = true;
        rainbowHandler.post(rainbowRunnable);
    }

    private void stopRainbowAnimation() {
        rainbowAnimRunning = false;
        rainbowHandler.removeCallbacks(rainbowRunnable);
    }

    private Runnable rainbowRunnable = new Runnable() {
        @Override
        public void run() {
            if (!rainbowAnimRunning) return;
            
            for (QuickMacroButton btn : quickButtons.values()) {
                if (btn.rainbowEffect && btn.container != null) {
                    int color = rainbowColors[rainbowColorIndex % rainbowColors.length];
                    int nextColor = rainbowColors[(rainbowColorIndex + 1) % rainbowColors.length];
                    
                    GradientDrawable drawable = new GradientDrawable();
                    drawable.setShape(GradientDrawable.RECTANGLE);
                    if (btn.shape.equals("rounded")) {
                        drawable.setCornerRadius(btn.size / 5f);
                    } else if (btn.shape.equals("circle")) {
                        drawable.setShape(GradientDrawable.OVAL);
                    }
                    drawable.setColors(new int[]{color, nextColor});
                    drawable.setGradientType(GradientDrawable.LINEAR_GRADIENT);
                    drawable.setOrientation(GradientDrawable.Orientation.TL_BR);
                    drawable.setStroke(btn.borderWidth, btn.borderColor);
                    drawable.setAlpha((int)(btn.alpha * 255));
                    
                    View buttonView = btn.container.getChildAt(0);
                    if (buttonView != null) {
                        buttonView.setBackground(drawable);
                    }
                }
            }
            
            rainbowColorIndex = (rainbowColorIndex + 1) % rainbowColors.length;
            rainbowHandler.postDelayed(this, 150);
        }
    };

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
            }
            
            for (String id : macroIds) {
                MacroConfig config = allMacros.get(id);
                if (config != null && !config.isSavedAsButton) {
                    currentMacroId = id;
                    break;
                }
            }
            if (currentMacroId.isEmpty() && !macroIds.isEmpty()) {
                currentMacroId = macroIds.get(0);
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
                    }
                }
            }
            
            int maxId = 0;
            for (QuickMacroButton btn : quickButtons.values()) {
                if (btn.id > maxId) maxId = btn.id;
            }
            if (maxId >= quickButtonIdCounter) {
                quickButtonIdCounter = maxId + 1;
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

    private String saveAudioToStorage(Uri audioUri) {
        try {
            File dir = new File(getExternalFilesDir(null), "sounds");
            if (!dir.exists()) dir.mkdirs();
            
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File file = new File(dir, "SOUND_" + timeStamp + ".mp3");
            
            java.io.InputStream inputStream = getContentResolver().openInputStream(audioUri);
            FileOutputStream outputStream = new FileOutputStream(file);
            
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.close();
            inputStream.close();
            
            return file.getAbsolutePath();
        } catch (Exception e) {
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
            
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColors(new int[]{0xFFFF0000, 0xFFFF8800, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFF8800FF});
            d.setGradientType(GradientDrawable.LINEAR_GRADIENT);
            d.setOrientation(GradientDrawable.Orientation.TL_BR);
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
                                        } else if (!macroIds.isEmpty()) {
                                            startMacroExecution(currentMacroId);
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
            wheelBg.setColors(new int[]{0xFFFF0000, 0xFFFF8800, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFF8800FF});
            wheelBg.setGradientType(GradientDrawable.LINEAR_GRADIENT);
            wheelBg.setOrientation(GradientDrawable.Orientation.TL_BR);
            wheelBg.setStroke(3, 0xFFFF0000);
            itemsLayout.setBackground(wheelBg);

            String[] items = {"🌐", "🎯", "👤", "🎵", "⚙️", "✕"};
            String[] labels = {"Web", "Макросы", "Персонажи", "Звуки", "Настройки", "Закрыть"};

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
                int color = rainbowColors[i % rainbowColors.length];
                itemBg.setColor(color | 0x44000000);
                itemBg.setStroke(2, color);
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
                case 3: showSoundWindow(); break;
                case 4: showSettingsDialog(); break;
                case 5: removeMainCircle(); break;
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
            titleText.setText("🌐 WebView");
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
            title.setText("🎯 МАКРОСЫ");
            title.setTextColor(0xFFFF0000);
            title.setTextSize(18);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            titleBar.addView(title);
            
            Button newMacroBtn = new Button(this);
            newMacroBtn.setText("➕");
            newMacroBtn.setTextColor(Color.WHITE);
            newMacroBtn.setTextSize(18);
            GradientDrawable newBg = new GradientDrawable();
            newBg.setCornerRadius(12);
            newBg.setColors(new int[]{0xFF00AA00, 0xFF00FF00});
            newBg.setGradientType(GradientDrawable.LINEAR_GRADIENT);
            newBg.setOrientation(GradientDrawable.Orientation.TL_BR);
            newMacroBtn.setBackground(newBg);
            newMacroBtn.setPadding(12, 4, 12, 4);
            newMacroBtn.setOnClickListener(v -> showNewMacroDialog(mainLayout));
            titleBar.addView(newMacroBtn);
            
            ImageButton closeBtn = new ImageButton(this);
            closeBtn.setImageDrawable(createCloseIcon());
            closeBtn.setBackgroundColor(Color.TRANSPARENT);
            closeBtn.setPadding(8, 4, 8, 4);
            closeBtn.setOnClickListener(v -> {
                if (windowManager != null) windowManager.removeView(wrapper);
            });
            titleBar.addView(closeBtn);
            
            mainLayout.addView(titleBar);

            // ВЫБОР МАКРОСА
            LinearLayout selectLayout = new LinearLayout(this);
            selectLayout.setOrientation(LinearLayout.HORIZONTAL);
            selectLayout.setGravity(Gravity.CENTER);
            selectLayout.setPadding(0, 8, 0, 8);

            final Spinner macroSpinner = new Spinner(this);
            final ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    TextView text = (TextView) view;
                    text.setTextColor(Color.WHITE);
                    text.setTextSize(16);
                    return view;
                }
            };
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            
            updateSpinnerList(adapter);
            macroSpinner.setAdapter(adapter);
            
            int currentPos = 0;
            ArrayList<String> availableMacros = getAvailableMacros();
            for (int i = 0; i < availableMacros.size(); i++) {
                String id = availableMacros.get(i);
                if (id.equals(currentMacroId)) {
                    currentPos = i;
                    break;
                }
            }
            if (currentPos < availableMacros.size()) {
                macroSpinner.setSelection(currentPos);
            }
            
            macroSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    ArrayList<String> available = getAvailableMacros();
                    if (position < available.size()) {
                        currentMacroId = available.get(position);
                        updateMacroUI(mainLayout);
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
            
            selectLayout.addView(macroSpinner);
            mainLayout.addView(selectLayout);

            // ИНФО
            LinearLayout infoLayout = new LinearLayout(this);
            infoLayout.setOrientation(LinearLayout.HORIZONTAL);
            infoLayout.setGravity(Gravity.CENTER);
            infoLayout.setPadding(0, 4, 0, 4);
            
            final TextView modeDisplay = new TextView(this);
            MacroConfig cfg = allMacros.get(currentMacroId);
            String modeText = cfg != null ? cfg.mode.name() : "NORMAL";
            modeDisplay.setText("Режим: " + modeText);
            modeDisplay.setTextColor(0xFFFF8800);
            modeDisplay.setTextSize(14);
            modeDisplay.setTypeface(null, android.graphics.Typeface.BOLD);
            modeDisplay.setPadding(12, 4, 12, 4);
            modeDisplay.setBackgroundColor(0x33000000);
            modeDisplay.setOnClickListener(v -> showModeSelector(mainLayout));
            infoLayout.addView(modeDisplay);

            final TextView repeatDisplay = new TextView(this);
            MacroConfig config = allMacros.get(currentMacroId);
            repeatDisplay.setText("Цикл: " + (config != null ? config.repeatCount : 1));
            repeatDisplay.setTextColor(0xFFFF0000);
            repeatDisplay.setTextSize(14);
            repeatDisplay.setTypeface(null, android.graphics.Typeface.BOLD);
            repeatDisplay.setPadding(12, 4, 12, 4);
            repeatDisplay.setBackgroundColor(0x33000000);
            repeatDisplay.setOnClickListener(v -> {
                showKeyboardDialog("🔄 КОЛИЧЕСТВО ПОВТОРОВ", 
                    repeatDisplay.getText().toString().replace("Цикл: ", ""), 3,
                    () -> {
                        try {
                            int count = Integer.parseInt(keyboardValue);
                            MacroConfig mc = allMacros.get(currentMacroId);
                            if (mc != null) {
                                mc.repeatCount = Math.max(1, count);
                                saveMacroConfigs();
                                repeatDisplay.setText("Цикл: " + mc.repeatCount);
                                updateMacroUI(mainLayout);
                                Toast.makeText(this, "Цикл: " + mc.repeatCount + " раз", Toast.LENGTH_SHORT).show();
                            }
                        } catch (NumberFormatException e) {}
                    });
            });
            infoLayout.addView(repeatDisplay);
            
            mainLayout.addView(infoLayout);

            // Точки
            final LinearLayout pointsContainer = new LinearLayout(this);
            pointsContainer.setOrientation(LinearLayout.VERTICAL);
            pointsContainer.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
            
            ScrollView scrollView = new ScrollView(this);
            scrollView.addView(pointsContainer);
            mainLayout.addView(scrollView);

            // КНОПКИ
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

            Button startBtn = createStyledButton("▶ СТАРТ", 0xFF00AA00);
            startBtn.setOnClickListener(v -> startMacroExecution(currentMacroId));
            controlLayout.addView(startBtn);

            Button stopBtn = createStyledButton("■ СТОП", 0xFFFF0000);
            stopBtn.setOnClickListener(v -> stopMacroExecution());
            controlLayout.addView(stopBtn);

            Button clearBtn = createStyledButton("✕ ОЧИСТИТЬ", 0xFFFF8800);
            clearBtn.setOnClickListener(v -> clearMacroPoints(mainLayout));
            controlLayout.addView(clearBtn);

            mainLayout.addView(controlLayout);

            Button saveToButtonBtn = new Button(this);
            saveToButtonBtn.setText("💾 СОХРАНИТЬ В КНОПКУ");
            saveToButtonBtn.setTextColor(Color.WHITE);
            saveToButtonBtn.setTextSize(14);
            saveToButtonBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable saveBtnBg = new GradientDrawable();
            saveBtnBg.setCornerRadius(12);
            saveBtnBg.setColors(new int[]{0xFFFF8800, 0xFFFFAA00, 0xFFFF4400});
            saveBtnBg.setGradientType(GradientDrawable.LINEAR_GRADIENT);
            saveBtnBg.setOrientation(GradientDrawable.Orientation.TL_BR);
            saveBtnBg.setAlpha(200);
            saveToButtonBtn.setBackground(saveBtnBg);
            saveToButtonBtn.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            saveToButtonBtn.setOnClickListener(v -> saveMacroToQuickButton(mainLayout));
            mainLayout.addView(saveToButtonBtn);

            wrapper.addView(mainLayout);

            View resizeHandle = new View(this);
            resizeHandle.setBackgroundColor(0x44FFFFFF);
            FrameLayout.LayoutParams resizeParams = new FrameLayout.LayoutParams(
                    30, 30);
            resizeParams.gravity = Gravity.BOTTOM | Gravity.END;
            wrapper.addView(resizeHandle, resizeParams);

            updateMacroUI(mainLayout);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    450, 750,
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

    private ArrayList<String> getAvailableMacros() {
        ArrayList<String> result = new ArrayList<>();
        for (String id : macroIds) {
            MacroConfig config = allMacros.get(id);
            if (config != null && !config.isSavedAsButton) {
                result.add(id);
            }
        }
        return result;
    }

    private void updateSpinnerList(ArrayAdapter<String> adapter) {
        adapter.clear();
        ArrayList<String> available = getAvailableMacros();
        for (String id : available) {
            MacroConfig config = allMacros.get(id);
            if (config != null) {
                adapter.add(config.name + " (" + config.points.size() + " точек)");
            }
        }
        if (available.isEmpty()) {
            adapter.add("Нет макросов. Создайте новый!");
        }
        adapter.notifyDataSetChanged();
    }

    private void showModeSelector(final LinearLayout mainLayout) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Выберите режим макроса");

        String[] modes = {"NORMAL - Обычный", "HOLD - Зажим", "TOGGLE - Переключатель"};
        builder.setItems(modes, (dialog, which) -> {
            MacroConfig config = allMacros.get(currentMacroId);
            if (config != null) {
                switch (which) {
                    case 0: config.mode = MacroMode.NORMAL; break;
                    case 1: config.mode = MacroMode.HOLD; break;
                    case 2: config.mode = MacroMode.TOGGLE; break;
                }
                saveMacroConfigs();
                updateMacroUI(mainLayout);
                Toast.makeText(this, "Режим: " + config.mode.name(), Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void updateMacroUI(LinearLayout mainLayout) {
        MacroConfig config = allMacros.get(currentMacroId);
        if (config == null || config.isSavedAsButton) return;

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

        for (int i = 0; i < mainLayout.getChildCount(); i++) {
            View v = mainLayout.getChildAt(i);
            if (v instanceof LinearLayout) {
                LinearLayout ll = (LinearLayout) v;
                for (int j = 0; j < ll.getChildCount(); j++) {
                    View child = ll.getChildAt(j);
                    if (child instanceof TextView) {
                        TextView tv = (TextView) child;
                        String text = tv.getText().toString();
                        if (text.startsWith("Режим: ")) {
                            tv.setText("Режим: " + config.mode.name());
                        }
                        if (text.startsWith("Цикл: ")) {
                            tv.setText("Цикл: " + config.repeatCount);
                        }
                    }
                }
            }
        }

        TextView header = new TextView(this);
        header.setText("📌 Точек: " + config.points.size() + " | 💾 Название: " + config.name);
        header.setTextColor(Color.WHITE);
        header.setTextSize(13);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setPadding(0, 0, 0, 8);
        pointsContainer.addView(header);

        if (config.points.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Нет точек.\n● КЛИК  ↗ СВАЙП");
            empty.setTextColor(0xFF888888);
            empty.setTextSize(14);
            empty.setPadding(0, 30, 0, 30);
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
                itemBg.setColor(p.type == ActionType.SWIPE ? 0x2200AAFF : 0x22FF0000);
                itemBg.setStroke(1, p.type == ActionType.SWIPE ? 0xFF00AAFF : 0xFFFF0000);
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
                    startGpsMode(currentMacroId, idx);
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
                    saveMacroConfigs();
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
        bg.setColor(color);
        bg.setAlpha(200);
        btn.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(3, 0, 3, 0);
        btn.setLayoutParams(lp);

        return btn;
    }

    private void showNewMacroDialog(final LinearLayout mainLayout) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("СОЗДАТЬ НОВЫЙ МАКРОС");
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);
        
        final EditText input = new EditText(this);
        input.setHint("Введите имя макроса");
        String defaultName = "Макрос " + (macroIds.size() + 1);
        input.setText(defaultName);
        layout.addView(input);
        
        builder.setView(layout);
        
        builder.setPositiveButton("СОЗДАТЬ", (d, w) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                name = "Макрос " + (macroIds.size() + 1);
            }
            
            String newId = UUID.randomUUID().toString();
            MacroConfig newMacro = new MacroConfig(newId, name);
            allMacros.put(newId, newMacro);
            macroIds.add(newId);
            currentMacroId = newId;
            saveMacroConfigs();
            updateMacroUI(mainLayout);
            
            View macrosView = findMacrosView();
            if (macrosView != null) {
                for (int i = 0; i < ((ViewGroup) macrosView).getChildCount(); i++) {
                    View child = ((ViewGroup) macrosView).getChildAt(i);
                    if (child instanceof LinearLayout) {
                        LinearLayout ll = (LinearLayout) child;
                        for (int j = 0; j < ll.getChildCount(); j++) {
                            View view = ll.getChildAt(j);
                            if (view instanceof Spinner) {
                                Spinner spinner = (Spinner) view;
                                ArrayAdapter<String> adapter = (ArrayAdapter<String>) spinner.getAdapter();
                                updateSpinnerList(adapter);
                                ArrayList<String> available = getAvailableMacros();
                                for (int k = 0; k < available.size(); k++) {
                                    if (available.get(k).equals(newId)) {
                                        spinner.setSelection(k);
                                        break;
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
            }
            
            Toast.makeText(this, "Макрос '" + name + "' создан!", Toast.LENGTH_SHORT).show();
            playSaveSound();
        });
        
        builder.setNegativeButton("ОТМЕНА", null);
        builder.show();
    }

    private void saveMacroToQuickButton(LinearLayout mainLayout) {
        MacroConfig config = allMacros.get(currentMacroId);
        if (config == null) {
            Toast.makeText(this, "Макрос не найден", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (config.points.isEmpty()) {
            Toast.makeText(this, "В макросе нет точек! Добавьте клики или свайпы.", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (config.isSavedAsButton) {
            Toast.makeText(this, "Этот макрос уже сохранен как кнопка!", Toast.LENGTH_SHORT).show();
            return;
        }
        
        int newId = quickButtonIdCounter++;
        QuickMacroButton btn = new QuickMacroButton(currentMacroId, newId);
        btn.macroName = config.name;
        btn.displayName = String.valueOf(newId);
        
        int buttonIndex = quickButtons.size();
        int columns = 3;
        int spacing = 190;
        int startX = 30;
        int startY = 100;
        int col = buttonIndex % columns;
        int row = buttonIndex / columns;
        
        btn.params = new WindowManager.LayoutParams(
                btn.size + 20, btn.size + 20,
                getOverlayFlag(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );
        btn.params.gravity = Gravity.TOP | Gravity.START;
        btn.params.x = startX + (col * spacing);
        btn.params.y = startY + (row * spacing);
        
        config.isSavedAsButton = true;
        saveMacroConfigs();
        
        createQuickButtonUI(currentMacroId);
        saveQuickButtons();
        
        updateMacroUI(mainLayout);
        
        View macrosView = findMacrosView();
        if (macrosView != null) {
            for (int i = 0; i < ((ViewGroup) macrosView).getChildCount(); i++) {
                View child = ((ViewGroup) macrosView).getChildAt(i);
                if (child instanceof LinearLayout) {
                    LinearLayout ll = (LinearLayout) child;
                    for (int j = 0; j < ll.getChildCount(); j++) {
                        View view = ll.getChildAt(j);
                        if (view instanceof Spinner) {
                            Spinner spinner = (Spinner) view;
                            ArrayAdapter<String> adapter = (ArrayAdapter<String>) spinner.getAdapter();
                            updateSpinnerList(adapter);
                            ArrayList<String> available = getAvailableMacros();
                            if (!available.isEmpty()) {
                                currentMacroId = available.get(0);
                                spinner.setSelection(0);
                                updateMacroUI(mainLayout);
                            } else {
                                String newId2 = UUID.randomUUID().toString();
                                MacroConfig newMacro = new MacroConfig(newId2, "Макрос 1");
                                allMacros.put(newId2, newMacro);
                                macroIds.add(newId2);
                                currentMacroId = newId2;
                                saveMacroConfigs();
                                updateSpinnerList(adapter);
                                spinner.setSelection(0);
                                updateMacroUI(mainLayout);
                            }
                            break;
                        }
                    }
                }
            }
        }
        
        Toast.makeText(this, "✅ Макрос '" + config.name + "' сохранен как кнопка #" + newId, Toast.LENGTH_LONG).show();
        playSaveSound();
    }

    // ==================== БЫСТРЫЕ КНОПКИ ====================

    private void createQuickButtonUI(String macroId) {
        try {
            if (windowManager == null) return;
            
            if (quickButtons.containsKey(macroId)) {
                QuickMacroButton btn = quickButtons.get(macroId);
                if (btn.container != null && btn.container.getParent() != null) {
                    return;
                }
                
                btn.container = new FrameLayout(this);
                btn.container.setBackgroundColor(Color.TRANSPARENT);

                FrameLayout buttonFrame = createButtonFrame(btn);
                btn.container.addView(buttonFrame, new FrameLayout.LayoutParams(
                        btn.size, btn.size));

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
                                MacroConfig cfg = allMacros.get(btn.macroId);
                                if (cfg != null && cfg.mode == MacroMode.HOLD) {
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
                                MacroConfig cfg2 = allMacros.get(btn.macroId);
                                if (cfg2 != null && cfg2.mode == MacroMode.HOLD) {
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
                saveQuickButtons();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleButtonPress(String macroId) {
        MacroConfig config = allMacros.get(macroId);
        if (config == null) {
            Toast.makeText(this, "Макрос не найден", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (config.points.isEmpty()) {
            Toast.makeText(this, "В макросе нет точек", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (config.mode == MacroMode.TOGGLE) {
            startMacroExecution(macroId);
            return;
        }
        
        if (config.mode == MacroMode.NORMAL) {
            if (!isMacroRunning) {
                startMacroExecution(macroId);
            } else {
                Toast.makeText(this, "Макрос уже выполняется", Toast.LENGTH_SHORT).show();
            }
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
        
        if (btn.rainbowEffect) {
            int color = rainbowColors[rainbowColorIndex % rainbowColors.length];
            int nextColor = rainbowColors[(rainbowColorIndex + 1) % rainbowColors.length];
            drawable.setColors(new int[]{color, nextColor});
            drawable.setGradientType(GradientDrawable.LINEAR_GRADIENT);
            drawable.setOrientation(GradientDrawable.Orientation.TL_BR);
        } else if (btn.useGradient) {
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
        textView.setShadowLayer(6, 3, 3, Color.BLACK);
        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
                btn.size, btn.size);
        textView.setLayoutParams(textParams);
        frame.addView(textView);

        MacroConfig config = allMacros.get(btn.macroId);
        String macroName = config != null ? config.name : btn.macroName;
        String shortName = macroName;
        if (shortName.length() > 6) {
            shortName = shortName.substring(0, 5) + "…";
        }
        TextView subText = new TextView(this);
        subText.setText(shortName);
        subText.setTextColor(0xCCFFFFFF);
        subText.setTextSize(11);
        subText.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        subText.setShadowLayer(3, 1, 1, Color.BLACK);
        FrameLayout.LayoutParams subParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        subParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        subParams.setMargins(0, 0, 0, 6);
        subText.setLayoutParams(subParams);
        frame.addView(subText);

        if (config != null) {
            TextView modeIndicator = new TextView(this);
            String modeSymbol = config.mode == MacroMode.HOLD ? "🖐" : 
                               config.mode == MacroMode.TOGGLE ? "🔄" : "▶";
            modeIndicator.setText(modeSymbol);
            modeIndicator.setTextColor(0xCCFFFFFF);
            modeIndicator.setTextSize(16);
            modeIndicator.setGravity(Gravity.BOTTOM | Gravity.START);
            modeIndicator.setShadowLayer(3, 1, 1, Color.BLACK);
            FrameLayout.LayoutParams modeParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            modeParams.gravity = Gravity.BOTTOM | Gravity.START;
            modeParams.setMargins(4, 0, 0, 4);
            modeIndicator.setLayoutParams(modeParams);
            frame.addView(modeIndicator);
        }

        if (btn.isFixed) {
            TextView lockIcon = new TextView(this);
            lockIcon.setText("🔒");
            lockIcon.setTextColor(Color.WHITE);
            lockIcon.setTextSize(16);
            lockIcon.setGravity(Gravity.TOP | Gravity.START);
            lockIcon.setShadowLayer(3, 1, 1, Color.BLACK);
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

    // ==================== ЗАПИСЬ МАКРОСА ====================

    private void startMacroRecording(final LinearLayout mainLayout, ActionType type) {
        if (isMacroRecording) return;
        if (windowManager == null) return;
        
        MacroConfig config = allMacros.get(currentMacroId);
        if (config == null) {
            Toast.makeText(this, "Ошибка: макрос не найден", Toast.LENGTH_SHORT).show();
            return;
        }
        
        isMacroRecording = true;
        isRecordingSwipe = (type == ActionType.SWIPE);
        
        String message = isRecordingSwipe ? 
            "Нажмите для начала свайпа, затем для конца" : 
            "Нажмите на экран для добавления клика в макрос: " + config.name;
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
        MacroConfig config = allMacros.get(currentMacroId);
        if (config == null) {
            Toast.makeText(this, "Ошибка: макрос не найден", Toast.LENGTH_SHORT).show();
            return;
        }

        MacroPoint point = new MacroPoint(x, y);
        config.points.add(point);
        saveMacroConfigs();

        Toast.makeText(this, "Клик #" + config.points.size() + " в макрос '" + config.name + "': (" + x + "," + y + ")", Toast.LENGTH_SHORT).show();
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
        MacroConfig config = allMacros.get(currentMacroId);
        if (config == null) {
            Toast.makeText(this, "Ошибка: макрос не найден", Toast.LENGTH_SHORT).show();
            return;
        }

        MacroPoint point = new MacroPoint(startX, startY, endX, endY, 300);
        config.points.add(point);
        saveMacroConfigs();

        Toast.makeText(this, "Свайп #" + config.points.size() + " в макрос '" + config.name + "': (" + startX + "," + startY + ") → (" + endX + "," + endY + ")", Toast.LENGTH_SHORT).show();
        playClickSound();

        showDelayDialog(config.points.size() - 1, mainLayout);

        if (captureOverlay != null && windowManager != null) {
            windowManager.removeView(captureOverlay);
            captureOverlay = null;
            isMacroRecording = false;
        }

        updateMacroUI(mainLayout);
    }

    private void showDelayDialog(final int index, final LinearLayout mainLayout) {
        MacroConfig config = allMacros.get(currentMacroId);
        if (config == null || index >= config.points.size()) return;

        long currentDelay = config.points.get(index).delayMs;
        String displayValue = String.valueOf(currentDelay);

        showKeyboardDialog("⏱ ЗАДЕРЖКА (мс) для макроса '" + config.name + "'", displayValue, 6, () -> {
            try {
                long value = Long.parseLong(keyboardValue);
                if (value < 10) value = 10;
                MacroConfig cfg = allMacros.get(currentMacroId);
                if (cfg != null && index < cfg.points.size()) {
                    cfg.points.get(index).delayMs = value;
                    cfg.points.get(index).delayDisplay = value + "мс";
                    saveMacroConfigs();
                    updateMacroUI(mainLayout);
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Ошибка", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void clearMacroPoints(LinearLayout mainLayout) {
        MacroConfig config = allMacros.get(currentMacroId);
        if (config != null && !config.points.isEmpty()) {
            config.points.clear();
            saveMacroConfigs();
            updateMacroUI(mainLayout);
            Toast.makeText(this, "Все точки удалены из макроса '" + config.name + "'", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Нет точек для удаления", Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== ВЫПОЛНЕНИЕ МАКРОСА ====================

    private void startMacroExecution(String macroId) {
        MacroConfig config = allMacros.get(macroId);
        if (config == null) {
            Toast.makeText(this, "Макрос не найден", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (config.points.isEmpty()) {
            Toast.makeText(this, "В макросе '" + config.name + "' нет точек", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(this, "🟢 Макрос '" + config.name + "' ВКЛЮЧЕН", Toast.LENGTH_SHORT).show();
                isMacroRunning = true;
                currentMacroIndex = 0;
                currentRepeat = 0;
                macroRepeatCount = -1;
                executeNextPointToggle(config);
            } else {
                Toast.makeText(this, "🔴 Макрос '" + config.name + "' ВЫКЛЮЧЕН", Toast.LENGTH_SHORT).show();
                stopMacroExecution();
            }
            return;
        }

        if (config.mode == MacroMode.HOLD) {
            isMacroRunning = true;
            currentMacroIndex = 0;
            currentRepeat = 0;
            macroRepeatCount = config.repeatCount;
            Toast.makeText(this, "🔄 Режим HOLD: удерживайте кнопку", Toast.LENGTH_SHORT).show();
            executeNextPointHold(config);
            return;
        }

        isMacroRunning = true;
        currentMacroIndex = 0;
        currentRepeat = 0;
        macroRepeatCount = config.repeatCount;
        Toast.makeText(this, "🚀 Макрос '" + config.name + "' запущен! Циклов: " + macroRepeatCount, Toast.LENGTH_SHORT).show();
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
                Toast.makeText(this, "🔄 Цикл " + (currentRepeat + 1) + "/" + macroRepeatCount, Toast.LENGTH_SHORT).show();
                executeNextPointHold(config);
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
            Toast.makeText(this, "🔄 Бесконечный цикл", Toast.LENGTH_SHORT).show();
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

    public void stopMacroOnHoldRelease() {
        if (isMacroRunning) {
            MacroConfig config = allMacros.get(currentMacroId);
            if (config != null && config.mode == MacroMode.HOLD) {
                stopMacroExecution();
                Toast.makeText(this, "⏹ Режим HOLD остановлен", Toast.LENGTH_SHORT).show();
            }
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

    // ==================== НАСТРОЙКИ ====================

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⚙️ НАСТРОЙКИ");

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

        TextView info = new TextView(this);
        info.setText("Макросов: " + allMacros.size() + " | Кнопок: " + quickButtons.size() + " | Звуков: " + soundLibrary.size());
        info.setTextColor(0xFFFF8800);
        info.setTextSize(16);
        info.setTypeface(null, android.graphics.Typeface.BOLD);
        info.setGravity(Gravity.CENTER);
        info.setPadding(0, 16, 0, 8);
        layout.addView(info);

        Button exportBtn = new Button(this);
        exportBtn.setText("💾 ЭКСПОРТ КОНФИГА");
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
        importBtn.setText("📂 ИМПОРТ КОНФИГА");
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
        builder.setPositiveButton("✕ Закрыть", null);
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
            
            JSONObject soundsObj = new JSONObject();
            JSONArray soundIdsArray = new JSONArray();
            JSONObject soundDataObj = new JSONObject();
            for (String id : soundIds) {
                soundIdsArray.put(id);
                soundDataObj.put(id, soundLibrary.get(id).toJSON());
            }
            soundsObj.put("ids", soundIdsArray);
            soundsObj.put("data", soundDataObj);
            config.put("sound_library", soundsObj);
            
            config.put("overlay_alpha", overlayAlpha);
            config.put("overlay_size", overlaySize);
            config.put("quick_button_id_counter", quickButtonIdCounter);
            
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
        startRainbowAnimation();
    }

    @Override
    public void onPause() {
        super.onPause();
        isAppInForeground = false;
        createMainCircle();
        stopRainbowAnimation();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (!isAppInForeground) {
            createMainCircle();
        }
        stopRainbowAnimation();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            stopRainbowAnimation();
            removeMainCircle();
            if (captureOverlay != null && windowManager != null) {
                windowManager.removeView(captureOverlay);
            }
            if (gpsOverlay != null && windowManager != null) {
                windowManager.removeView(gpsOverlay);
            }
            macroHandler.removeCallbacksAndMessages(null);
            closeKeyboard();
            stopMicrophoneEmulation();
            stopSound();
            
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
                case REQUEST_MICROPHONE: 
                    Toast.makeText(this, "Микрофон разрешён", Toast.LENGTH_SHORT).show();
                    break;
                case REQUEST_CAMERA: 
                    Toast.makeText(this, "Камера разрешена", Toast.LENGTH_SHORT).show(); 
                    break;
                case REQUEST_STORAGE: 
                    Toast.makeText(this, "Хранилище разрешено", Toast.LENGTH_SHORT).show(); 
                    break;
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
            
            if (requestCode == REQUEST_AUDIO_FILE && resultCode == RESULT_OK && data != null) {
                Uri audioUri = data.getData();
                if (audioUri != null) {
                    String path = saveAudioToStorage(audioUri);
                    if (path != null) {
                        String name = prefs.getString("temp_sound_name", "Звук " + (soundIds.size() + 1));
                        String id = UUID.randomUUID().toString();
                        SoundData sound = new SoundData(id, name, path);
                        soundLibrary.put(id, sound);
                        soundIds.add(id);
                        currentSoundId = id;
                        saveSoundLibrary();
                        
                        Toast.makeText(this, "Звук '" + name + "' добавлен!", Toast.LENGTH_LONG).show();
                        playSaveSound();
                        
                        // Обновляем UI звуков
                        View soundView = findSoundView();
                        if (soundView != null && soundView instanceof LinearLayout) {
                            updateSoundUI((LinearLayout) soundView);
                        }
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

    private View findSoundView() {
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
                                                if (text.contains("ЗВУКИ") || text.contains("🎵")) {
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
            
            if (config.has("sound_library")) {
                JSONObject soundsObj = config.getJSONObject("sound_library");
                JSONArray idsArray = soundsObj.getJSONArray("ids");
                JSONObject dataObj = soundsObj.getJSONObject("data");
                
                soundLibrary.clear();
                soundIds.clear();
                
                for (int i = 0; i < idsArray.length(); i++) {
                    String id = idsArray.getString(i);
                    if (dataObj.has(id)) {
                        SoundData sound = new SoundData(dataObj.getJSONObject(id));
                        soundLibrary.put(id, sound);
                        soundIds.add(id);
                    }
                }
                if (!soundIds.isEmpty()) {
                    currentSoundId = soundIds.get(0);
                } else {
                    createDefaultSound();
                }
                saveSoundLibrary();
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
            
            Toast.makeText(this, "Конфиг импортирован успешно!", Toast.LENGTH_LONG).show();
            playSaveSound();
            
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка импорта конфига", Toast.LENGTH_SHORT).show();
        }
    }
}
