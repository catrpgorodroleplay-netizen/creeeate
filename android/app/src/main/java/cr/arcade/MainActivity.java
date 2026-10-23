package com.cr.arcade;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * ARCADE — Capacitor-обёртка с оверлеем.
 * При выходе из приложения WebView переезжает в оверлей и продолжает жить (микрофон, звук, таймеры).
 * Все окна — оверлейные, поэтому работают поверх любых игр.
 */
public class MainActivity extends BridgeActivity {

    private static final String TAG = "ArcadeOverlay";
    private static final int RC_OVERLAY = 101, RC_GALLERY = 103, RC_AUDIO = 105, RC_ACCESS = 107, RC_PERMS = 110, RC_IMPORT = 999;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int FL_NF = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
    private static final int FL_SCREEN = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
    private static final int DF_IME = 1, DF_SCROLL = 2;

    // ===== Палитра =====
    private static final int C_BG = 0xFF111114, C_CARD = 0xFF1B1B20, C_CARD2 = 0xFF26262D, C_STROKE = 0xFF383840;
    private static final int C_RED = 0xFFE5252A, C_TEXT = 0xFFF1F1F4, C_MUTED = 0xFF8F8F9B;
    private static final int C_GREEN = 0xFF2ECC71, C_BLUE = 0xFF3D8BFF, C_AMBER = 0xFFFFB020;
    private static final int[] ACCENTS = {0xFFE5252A, 0xFF3D8BFF, 0xFF2ECC71, 0xFFFFB020, 0xFFB05CFF, 0xFFEEEEEE};

    private interface IntCb { void on(int v); }
    private interface StrCb { void on(String s); }
    private interface Check { boolean run(); }

    private enum ActionType { CLICK, SWIPE, LONG, WAIT }
    private enum MacroMode { NORMAL, HOLD, TOGGLE }

    private static MacroMode parseMode(String s) { try { return MacroMode.valueOf(s); } catch (Exception e) { return MacroMode.NORMAL; } }
    private static ActionType parseType(String s) { try { return ActionType.valueOf(s); } catch (Exception e) { return ActionType.CLICK; } }

    // ===== Состояние =====
    private WindowManager wm;
    private Context appCtx;
    private SharedPreferences prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();

    private boolean overlayActive, suppressOverlay, platformClosed, overlayAsked, accAsked;

    // bubble
    private FrameLayout bubbleRoot;
    private GamepadView gamepad;
    private WindowManager.LayoutParams bubbleLp;
    private ValueAnimator pulseAnim;
    private View menuRoot;

    // web
    private FrameLayout webRoot, webHolder;
    private WindowManager.LayoutParams webLp;
    private boolean webVisible;
    private WebView movedWeb;
    private ViewGroup origParent;
    private int origIndex;
    private ViewGroup.LayoutParams origLp;

    // panels / dialogs
    private final HashMap<String, Panel> panels = new HashMap<>();
    private final ArrayList<View> dialogs = new ArrayList<>();
    private ScrollView macroScroll;
    private String animatedMacroId = "";

    // toast pill
    private FrameLayout toastView;
    private TextView toastText;
    private WindowManager.LayoutParams toastLp;
    private final Runnable toastHide = this::hideToastPill;

    // macros
    private final HashMap<String, MacroConfig> allMacros = new HashMap<>();
    private final ArrayList<String> macroIds = new ArrayList<>();
    private String currentMacroId = "";
    private final HashMap<String, MacroRunner> runners = new HashMap<>();
    private final HashMap<String, QuickMacroButton> quickButtons = new HashMap<>();
    private int quickButtonIdCounter = 1;
    private PointsView pointsView;
    private FrameLayout captureRoot;
    private TextView captureInfo;
    private int captureCount;
    private PowerManager.WakeLock wakeLock;

    // sounds / characters
    private final HashMap<String, SoundData> soundLibrary = new HashMap<>();
    private final ArrayList<String> soundIds = new ArrayList<>();
    private final HashMap<String, MediaPlayer> players = new HashMap<>();
    private String pendingSoundName = "";
    private final ArrayList<CharacterData> characters = new ArrayList<>();
    private String tempCharacterName = "";
    private final HashMap<String, View> charViews = new HashMap<>();

    // ==================== Модели ====================

    private static class MacroPoint {
        int x, y, endX, endY;
        long delayMs = 500, swipeDuration = 300;
        ActionType type = ActionType.CLICK;

        MacroPoint(int x, int y) { this.x = x; this.y = y; this.endX = x; this.endY = y; }

        MacroPoint(int x, int y, int ex, int ey, long dur) {
            this.x = x; this.y = y; this.endX = ex; this.endY = ey; this.swipeDuration = dur; this.type = ActionType.SWIPE;
        }

        MacroPoint(JSONObject j) throws Exception {
            x = j.getInt("x"); y = j.getInt("y");
            delayMs = j.optLong("delayMs", 500);
            type = parseType(j.optString("type", "CLICK"));
            endX = j.optInt("endX", x); endY = j.optInt("endY", y);
            swipeDuration = j.optLong("swipeDuration", 300);
        }

        JSONObject toJSON() throws Exception {
            JSONObject j = new JSONObject();
            j.put("x", x); j.put("y", y); j.put("delayMs", delayMs); j.put("type", type.name());
            j.put("endX", endX); j.put("endY", endY); j.put("swipeDuration", swipeDuration);
            return j;
        }

        String title() {
            switch (type) {
                case SWIPE: return "Свайп";
                case LONG: return "Долгое нажатие";
                case WAIT: return "Пауза";
                default: return "Клик";
            }
        }

        String sub() {
            String d = "⏱ " + delayMs + " мс";
            switch (type) {
                case SWIPE: return "(" + x + "," + y + ") → (" + endX + "," + endY + ") • " + d;
                case LONG: return "(" + x + "," + y + ") • держать " + swipeDuration + " мс • " + d;
                case WAIT: return d;
                default: return "(" + x + "," + y + ") • " + d;
            }
        }
    }

    private static class MacroConfig {
        String id, name;
        ArrayList<MacroPoint> points = new ArrayList<>();
        int repeatCount = 1;      // 0 = бесконечно
        MacroMode mode = MacroMode.NORMAL;
        boolean isSavedAsButton = false;
        int jitter = 0;           // px разброса координат
        int variation = 0;        // % разброса задержек
        int startDelay = 0;       // мс до старта

        MacroConfig(String id, String name) { this.id = id; this.name = name; }

        MacroConfig(JSONObject j) throws Exception {
            id = j.getString("id"); name = j.getString("name");
            repeatCount = j.optInt("repeatCount", 1);
            mode = parseMode(j.optString("mode", "NORMAL"));
            isSavedAsButton = j.optBoolean("isSavedAsButton", false);
            jitter = j.optInt("jitter", 0); variation = j.optInt("variation", 0); startDelay = j.optInt("startDelay", 0);
            JSONArray arr = j.getJSONArray("points");
            for (int i = 0; i < arr.length(); i++) points.add(new MacroPoint(arr.getJSONObject(i)));
        }

        JSONObject toJSON() throws Exception {
            JSONObject j = new JSONObject();
            j.put("id", id); j.put("name", name); j.put("repeatCount", repeatCount); j.put("mode", mode.name());
            j.put("isSavedAsButton", isSavedAsButton); j.put("jitter", jitter); j.put("variation", variation); j.put("startDelay", startDelay);
            JSONArray arr = new JSONArray();
            for (MacroPoint p : points) arr.put(p.toJSON());
            j.put("points", arr);
            return j;
        }
    }

    private static class QuickMacroButton {
        String macroId, shape = "circle", text;
        int id, sizeDp = 56, color = ACCENTS[0], x = 60, y = 300;
        float alpha = 0.95f;
        boolean isFixed = false;
        QuickView view;
        WindowManager.LayoutParams lp;

        QuickMacroButton(String macroId, int id) { this.macroId = macroId; this.id = id; this.text = String.valueOf(id); }

        QuickMacroButton(JSONObject j) throws Exception {
            macroId = j.getString("macroId");
            id = j.optInt("id", 1);
            isFixed = j.optBoolean("isFixed", false);
            sizeDp = j.optInt("sizeDp", 56);
            shape = j.optString("shape", "circle");
            color = j.optInt("color", ACCENTS[0]);
            text = j.optString("text", String.valueOf(id));
            alpha = (float) j.optDouble("alpha", 0.95);
            x = j.optInt("x", 60); y = j.optInt("y", 300);
        }

        JSONObject toJSON() throws Exception {
            JSONObject j = new JSONObject();
            j.put("macroId", macroId); j.put("id", id); j.put("isFixed", isFixed); j.put("sizeDp", sizeDp);
            j.put("shape", shape); j.put("color", color); j.put("text", text); j.put("alpha", alpha);
            j.put("x", x); j.put("y", y);
            return j;
        }
    }

    private static class SoundData {
        String id, name, path;
        boolean isLooping = false;
        float volume = 1f;

        SoundData(String id, String name, String path) { this.id = id; this.name = name; this.path = path; }

        SoundData(JSONObject j) throws Exception {
            id = j.getString("id"); name = j.getString("name"); path = j.getString("path");
            isLooping = j.optBoolean("isLooping", false); volume = (float) j.optDouble("volume", 1.0);
        }

        JSONObject toJSON() throws Exception {
            JSONObject j = new JSONObject();
            j.put("id", id); j.put("name", name); j.put("path", path); j.put("isLooping", isLooping); j.put("volume", volume);
            return j;
        }
    }

    private static class CharacterData {
        String name, path;

        CharacterData(String name, String path) { this.name = name; this.path = path; }

        CharacterData(JSONObject j) throws Exception { name = j.getString("name"); path = j.getString("path"); }

        JSONObject toJSON() throws Exception {
            JSONObject j = new JSONObject();
            j.put("name", name); j.put("path", path);
            return j;
        }
    }

    private static class Panel {
        String key;
        FrameLayout root;
        LinearLayout body;
        WindowManager.LayoutParams lp;
        Runnable refresh;
    }

    // ==================== Кастомные View ====================

    /** Красный геймпад с чёрными кнопками на сером фоне. */
    private static class GamepadView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean running = false;

        GamepadView(Context c) { super(c); }

        void setRunning(boolean r) { running = r; invalidate(); }

        @Override
        protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight());
            float x0 = (getWidth() - s) / 2f, y0 = (getHeight() - s) / 2f;
            float cx = x0 + s / 2f, cy = y0 + s / 2f, r = s / 2f - s * 0.03f;

            p.setStyle(Paint.Style.FILL);
            p.setShader(new RadialGradient(cx, cy - s * 0.15f, s * 0.7f, 0xFF4F4F56, 0xFF2A2A2F, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s * 0.04f);
            p.setColor(running ? 0xFF2ECC71 : 0xFF63636B);
            c.drawCircle(cx, cy, r, p);

            // красный корпус
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFE5252A);
            c.drawRoundRect(new RectF(x0 + s * 0.19f, y0 + s * 0.32f, x0 + s * 0.81f, y0 + s * 0.62f), s * 0.13f, s * 0.13f, p);
            c.drawCircle(x0 + s * 0.29f, y0 + s * 0.62f, s * 0.115f, p);
            c.drawCircle(x0 + s * 0.71f, y0 + s * 0.62f, s * 0.115f, p);
            c.drawRoundRect(new RectF(x0 + s * 0.24f, y0 + s * 0.26f, x0 + s * 0.40f, y0 + s * 0.34f), s * 0.03f, s * 0.03f, p);
            c.drawRoundRect(new RectF(x0 + s * 0.60f, y0 + s * 0.26f, x0 + s * 0.76f, y0 + s * 0.34f), s * 0.03f, s * 0.03f, p);

            // чёрные кнопки
            p.setColor(0xFF0B0B0D);
            float dx = x0 + s * 0.33f, dy = y0 + s * 0.47f, arm = s * 0.075f, th = s * 0.028f;
            c.drawRoundRect(new RectF(dx - arm, dy - th, dx + arm, dy + th), th, th, p);
            c.drawRoundRect(new RectF(dx - th, dy - arm, dx + th, dy + arm), th, th, p);
            float bx = x0 + s * 0.67f, by = y0 + s * 0.47f, o = s * 0.058f, br = s * 0.032f;
            c.drawCircle(bx, by - o, br, p);
            c.drawCircle(bx, by + o, br, p);
            c.drawCircle(bx - o, by, br, p);
            c.drawCircle(bx + o, by, br, p);
            c.drawRoundRect(new RectF(x0 + s * 0.43f, y0 + s * 0.46f, x0 + s * 0.485f, y0 + s * 0.485f), s * 0.01f, s * 0.01f, p);
            c.drawRoundRect(new RectF(x0 + s * 0.515f, y0 + s * 0.46f, x0 + s * 0.57f, y0 + s * 0.485f), s * 0.01f, s * 0.01f, p);
        }
    }

    /** Маркеры точек макроса поверх экрана. */
    private static class PointsView extends View {
        final ArrayList<int[]> pts = new ArrayList<>();
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int flashIdx = -1;
        float flashT = 1f;
        ValueAnimator fa;

        PointsView(Context c) { super(c); }

        void setPoints(MacroConfig cfg) {
            pts.clear();
            for (MacroPoint m : cfg.points) pts.add(new int[]{m.x, m.y, m.endX, m.endY, m.type.ordinal()});
            invalidate();
        }

        void flash(int idx) {
            flashIdx = idx;
            if (fa != null) fa.cancel();
            fa = ValueAnimator.ofFloat(0f, 1f);
            fa.setDuration(350);
            fa.addUpdateListener(a -> { flashT = (float) a.getAnimatedValue(); invalidate(); });
            fa.start();
        }

        @Override
        protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density;
            int[] loc = new int[2];
            getLocationOnScreen(loc);
            c.translate(-loc[0], -loc[1]);
            for (int i = 0; i < pts.size(); i++) {
                int[] a = pts.get(i);
                int t = a[4];
                if (t == 3) continue;
                int col = t == 1 ? 0xFF3D8BFF : t == 2 ? 0xFFFFB020 : 0xFFE5252A;
                if (t == 1) {
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3 * d); p.setColor((col & 0x00FFFFFF) | 0xAA000000);
                    c.drawLine(a[0], a[1], a[2], a[3], p);
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(a[2], a[3], 7 * d, p);
                }
                p.setStyle(Paint.Style.FILL); p.setColor((col & 0x00FFFFFF) | 0xAA000000);
                c.drawCircle(a[0], a[1], 15 * d, p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2 * d); p.setColor(0xFFFFFFFF);
                c.drawCircle(a[0], a[1], 15 * d, p);
                p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(12 * d); p.setFakeBoldText(true);
                c.drawText(String.valueOf(i + 1), a[0], a[1] - (p.descent() + p.ascent()) / 2f, p);
                p.setFakeBoldText(false);
                if (i == flashIdx && flashT < 1f) {
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3 * d);
                    p.setColor(((int) ((1f - flashT) * 255) << 24) | 0x00FFFFFF);
                    c.drawCircle(a[0], a[1], (15 + flashT * 32) * d, p);
                }
            }
        }
    }

    /** Быстрая кнопка макроса. */
    private static class QuickView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        String label = "1", name = "";
        int accent = 0xFFE5252A, mode = 0;
        boolean circle = true, running = false, locked = false;
        float phase = 0f;
        ValueAnimator anim;

        QuickView(Context c) { super(c); }

        void setRunning(boolean r) {
            if (r == running && (anim != null) == r) return;
            running = r;
            if (anim != null) { anim.cancel(); anim = null; }
            if (r) {
                anim = ValueAnimator.ofFloat(0f, 360f);
                anim.setDuration(1100);
                anim.setRepeatCount(ValueAnimator.INFINITE);
                anim.setInterpolator(new android.view.animation.LinearInterpolator());
                anim.addUpdateListener(a -> { phase = (float) a.getAnimatedValue(); invalidate(); });
                anim.start();
            }
            invalidate();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (anim != null) { anim.cancel(); anim = null; }
        }

        @Override
        protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density;
            float pad = 8 * d;
            RectF r = new RectF(pad, pad, getWidth() - pad, getHeight() - pad);
            float rad = r.width() * 0.24f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xF01B1B20);
            if (circle) c.drawOval(r, p); else c.drawRoundRect(r, rad, rad, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3 * d);
            p.setColor(running ? 0xFF2ECC71 : accent);
            if (circle) c.drawOval(r, p); else c.drawRoundRect(r, rad, rad, p);
            if (running) {
                p.setStrokeWidth(5 * d);
                p.setColor(0xFFFFFFFF);
                RectF rr = new RectF(r.left - 3 * d, r.top - 3 * d, r.right + 3 * d, r.bottom + 3 * d);
                c.drawArc(rr, phase, 70, false, p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(0xFFFFFFFF);
            p.setFakeBoldText(true);
            p.setTextSize(r.width() * 0.36f);
            c.drawText(label, r.centerX(), r.centerY() - (p.descent() + p.ascent()) / 2f - r.height() * 0.06f, p);
            p.setFakeBoldText(false);
            p.setTextSize(Math.max(8 * d, r.width() * 0.15f));
            p.setColor(0xFF9A9AA5);
            String n = name.length() > 7 ? name.substring(0, 6) + "…" : name;
            c.drawText(n, r.centerX(), r.bottom - r.height() * 0.13f, p);
            p.setTextSize(r.width() * 0.17f);
            p.setColor(0xFFCCCCCC);
            p.setTextAlign(Paint.Align.LEFT);
            c.drawText(mode == 1 ? "◉" : mode == 2 ? "⇄" : "▶", r.left + r.width() * 0.16f, r.top + r.height() * 0.26f, p);
            if (locked) {
                p.setTextAlign(Paint.Align.RIGHT);
                c.drawText("🔒", r.right - r.width() * 0.12f, r.top + r.height() * 0.26f, p);
            }
        }
    }

    // ==================== Жизненный цикл ====================

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appCtx = getApplicationContext();
        wm = (WindowManager) appCtx.getSystemService(Context.WINDOW_SERVICE);
        prefs = getSharedPreferences("arcade_data", MODE_PRIVATE);
        quickButtonIdCounter = prefs.getInt("quick_button_id_counter", 1);
        try {
            loadMacroConfigs();
            loadQuickButtons();
            loadSounds();
            loadCharacters();
        } catch (Exception e) { Log.e(TAG, "load", e); }
        tweakBridgeWebView();
        requestRuntimePermissions();
        // startKeepAlive(); // Временно отключено для предотвращения краша при запросе разрешений
    }

    @Override
    public void onStart() {
        super.onStart();
        suppressOverlay = false;
        exitOverlayMode();
    }

    @Override
    public void onResume() {
        super.onResume();
        suppressOverlay = false;
        checkSetup();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (!platformClosed) keepWebAlive(getBridgeWebView());
    }

    @Override
    public void onStop() {
        super.onStop();
        if (!platformClosed && !suppressOverlay && !isFinishing() && !isChangingConfigurations()) enterOverlayMode();
        if (!platformClosed) keepWebAlive(movedWeb != null ? movedWeb : getBridgeWebView());
    }

    @Override
    public void onDestroy() {
        try {
            cleanupAll();
        } catch (Exception e) { Log.e(TAG, "destroy", e); }
        super.onDestroy();
    }

    private void cleanupAll() {
        stopAllMacros();
        stopAllSounds();
        exitOverlayMode();
        for (View v : new ArrayList<>(charViews.values())) removeWin(v);
        charViews.clear();
        releaseWakeLock();
        // stopKeepAlive(); // Временно отключено
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == RC_PERMS) {
            // startKeepAlive(); // Временно отключено
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        try {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
            Uri uri = data.getData();
            if (requestCode == RC_GALLERY) {
                Bitmap bmp = decodeUri(uri, 512);
                if (bmp != null) {
                    String path = saveBitmap(bmp);
                    if (path != null) {
                        characters.add(new CharacterData(tempCharacterName, path));
                        saveCharacters();
                        toast("Персонаж сохранён");
                    }
                }
            } else if (requestCode == RC_AUDIO) {
                String path = saveAudio(uri);
                if (path != null) {
                    String id = UUID.randomUUID().toString();
                    soundLibrary.put(id, new SoundData(id, pendingSoundName, path));
                    soundIds.add(id);
                    saveSounds();
                    toast("Звук «" + pendingSoundName + "» добавлен");
                }
            } else if (requestCode == RC_IMPORT) {
                String json = readUri(uri);
                if (json != null) importConfig(json, false);
            }
        } catch (Exception e) {
            Log.e(TAG, "result", e);
            toast("Ошибка загрузки");
        }
    }

    // ==================== Разрешения / сервис ====================

    private boolean canOverlay() { return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this); }

    private void requestRuntimePermissions() {
        try {
            ArrayList<String> need = new ArrayList<>();
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.RECORD_AUDIO);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.CAMERA);
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                need.add(Manifest.permission.POST_NOTIFICATIONS);
            if (!need.isEmpty()) ActivityCompat.requestPermissions(this, need.toArray(new String[0]), RC_PERMS);
        } catch (Exception e) { Log.e(TAG, "perm", e); }
    }

    private void checkSetup() {
        if (platformClosed) return;
        if (!canOverlay() && !overlayAsked) {
            overlayAsked = true;
            new AlertDialog.Builder(this)
                    .setTitle("Нужен оверлей")
                    .setMessage("Разрешите показ поверх других приложений — без этого сайт и меню не смогут работать вне приложения.")
                    .setCancelable(false)
                    .setPositiveButton("Открыть настройки", (d, w) -> {
                        suppressOverlay = true;
                        startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())), RC_OVERLAY);
                    })
                    .setNegativeButton("Позже", null).show();
            return;
        }
        if (!isAccessibilityEnabled() && !accAsked) {
            accAsked = true;
            new AlertDialog.Builder(this)
                    .setTitle("Специальные возможности")
                    .setMessage("Для кликов и свайпов макросов включите службу этого приложения в «Специальных возможностях».")
                    .setPositiveButton("Включить", (d, w) -> {
                        suppressOverlay = true;
                        startActivityForResult(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), RC_ACCESS);
                    })
                    .setNegativeButton("Позже", null).show();
        }
    }

    private boolean isAccessibilityEnabled() {
        try {
            if (MacroService.getInstance() != null) return true;
            String en = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return en != null && en.contains(getPackageName()) && en.contains("MacroService");
        } catch (Exception e) { return false; }
    }

    private void startKeepAlive() {
        try {
            Intent i = new Intent().setClassName(getPackageName(), "com.cr.arcade.KeepAliveService");
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        } catch (Exception e) { Log.w(TAG, "keepalive start: " + e); }
    }

    private void stopKeepAlive() {
        try {
            stopService(new Intent().setClassName(getPackageName(), "com.cr.arcade.KeepAliveService"));
        } catch (Exception ignored) { }
    }

    // ==================== Режим оверлея ====================

    private void enterOverlayMode() {
        if (platformClosed || overlayActive || !canOverlay()) return;
        overlayActive = true;
        try {
            moveWebViewToOverlay();
            showBubble();
            for (QuickMacroButton b : quickButtons.values()) attachQuick(b);
            applyKeepScreen();
            ui.removeCallbacks(webTick);
            ui.postDelayed(webTick, 3000);
        } catch (Exception e) { Log.e(TAG, "enter", e); }
    }

    private void exitOverlayMode() {
        if (!overlayActive) return;
        stopAllMacros();
        overlayActive = false;
        ui.removeCallbacks(webTick);
        closeCapture(false);
        closeMenuNow();
        for (String k : new ArrayList<>(panels.keySet())) { Panel p = panels.remove(k); if (p != null) removeWin(p.root); }
        for (View d : new ArrayList<>(dialogs)) removeWin(d);
        dialogs.clear();
        stopPulse();
        removeWin(bubbleRoot); bubbleRoot = null; gamepad = null;
        for (QuickMacroButton b : quickButtons.values()) detachQuick(b);
        removeWin(pointsView); pointsView = null;
        if (toastView != null) { removeWin(toastView); toastView = null; }
        restoreWebViewToActivity();
        releaseWakeLock();
    }

    private final Runnable webTick = new Runnable() {
        @Override public void run() {
            if (!overlayActive) return;
            keepWebAlive(movedWeb);
            ui.postDelayed(this, 3000);
        }
    };

    private void closePlatform() {
        if (platformClosed) return;
        platformClosed = true;
        try {
            stopAllMacros();
            stopAllSounds();
            exitOverlayMode();
            stopKeepAlive();
            WebView w = getBridgeWebView();
            if (w != null) { w.loadUrl("about:blank"); w.onPause(); w.pauseTimers(); }
        } catch (Exception e) { Log.e(TAG, "close", e); }
        Toast.makeText(appCtx, "Платформа выключена", Toast.LENGTH_SHORT).show();
        try { finishAndRemoveTask(); } catch (Exception e) { finish(); }
        ui.postDelayed(() -> android.os.Process.killProcess(android.os.Process.myPid()), 600);
    }

    // ==================== WebView в оверлее ====================

    private WebView getBridgeWebView() {
        try {
            Bridge b = getBridge();
            return b == null ? null : b.getWebView();
        } catch (Exception e) { return null; }
    }

    private void tweakBridgeWebView() {
        try {
            WebView w = getBridgeWebView();
            if (w != null) {
                WebSettings s = w.getSettings();
                s.setMediaPlaybackRequiresUserGesture(false);
                s.setDomStorageEnabled(true);
            }
        } catch (Exception ignored) { }
    }

    private void keepWebAlive(WebView w) {
        if (w == null) return;
        try { w.onResume(); w.resumeTimers(); } catch (Exception ignored) { }
    }

    private void moveWebViewToOverlay() {
        WebView w = getBridgeWebView();
        if (w == null) return;
        buildWebPanel();
        if (webHolder == null) return;
        ViewParent pp = w.getParent();
        if (pp instanceof ViewGroup && pp != webHolder) {
            ViewGroup parent = (ViewGroup) pp;
            origParent = parent;
            origIndex = parent.indexOfChild(w);
            origLp = w.getLayoutParams();
            parent.removeView(w);
        }
        if (w.getParent() == null) webHolder.addView(w, new FrameLayout.LayoutParams(MATCH, MATCH));
        movedWeb = w;
        keepWebAlive(w);
    }

    private void restoreWebViewToActivity() {
        WebView w = movedWeb;
        movedWeb = null;
        if (w != null) {
            ViewParent pp = w.getParent();
            if (pp instanceof ViewGroup) ((ViewGroup) pp).removeView(w);
        }
        removeWin(webRoot);
        webRoot = null; webHolder = null; webVisible = false;
        if (w != null && origParent != null) {
            try {
                int idx = Math.min(origIndex, origParent.getChildCount());
                if (origLp != null) origParent.addView(w, idx, origLp); else origParent.addView(w, idx);
            } catch (Exception e) { Log.e(TAG, "restore web", e); }
            keepWebAlive(w);
            w.requestLayout();
        }
    }

    private void buildWebPanel() {
        if (webRoot != null) return;
        DisplayMetrics m = realMetrics();
        int pw = Math.round(m.widthPixels * 0.94f), ph = Math.round(m.heightPixels * 0.78f);
        webLp = baseLp(pw, ph, FL_NF | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        webLp.x = (m.widthPixels - pw) / 2;
        webLp.y = Math.round(m.heightPixels * 0.08f);
        webLp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

        webRoot = new FrameLayout(this) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    if (e.getAction() == KeyEvent.ACTION_UP) {
                        if (movedWeb != null && movedWeb.canGoBack()) movedWeb.goBack(); else showWeb(false);
                    }
                    return true;
                }
                return super.dispatchKeyEvent(e);
            }
        };
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackground(rect(C_BG, 16, C_RED, 1.5f));
        col.setClipToOutline(true);

        LinearLayout hdr = new LinearLayout(this);
        hdr.setOrientation(LinearLayout.HORIZONTAL);
        hdr.setGravity(Gravity.CENTER_VERTICAL);
        hdr.setPadding(dp(12), dp(4), dp(6), dp(4));
        hdr.setBackgroundColor(C_CARD);
        TextView t = tv("🌐  ПЛАТФОРМА", 13, C_RED, true);
        t.setLetterSpacing(0.1f);
        hdr.addView(t, lin(0, WRAP, 1f, 0, 0, 0, 0));
        hdr.addView(iconBtn("◀", C_TEXT, C_CARD2, 34, v -> { if (movedWeb != null && movedWeb.canGoBack()) movedWeb.goBack(); }), lin(dp(34), dp(34), 0, 0, 0, dp(4), 0));
        hdr.addView(iconBtn("⟳", C_TEXT, C_CARD2, 34, v -> { if (movedWeb != null) movedWeb.reload(); }), lin(dp(34), dp(34), 0, 0, 0, dp(4), 0));
        hdr.addView(iconBtn("⤢", C_TEXT, C_CARD2, 34, v -> toggleWebSize()), lin(dp(34), dp(34), 0, 0, 0, dp(4), 0));
        hdr.addView(iconBtn("—", C_TEXT, C_RED, 34, v -> showWeb(false)), lin(dp(34), dp(34), 0, 0, 0, 0, 0));
        col.addView(hdr, lin(MATCH, WRAP, 0, 0, 0, 0, 0));
        webHolder = new FrameLayout(this);
        webHolder.setBackgroundColor(0xFF000000);
        col.addView(webHolder, lin(MATCH, 0, 1f, 0, 0, 0, 0));
        webRoot.addView(col, new FrameLayout.LayoutParams(MATCH, MATCH));

        TextView grip = tv("◢", 14, C_TEXT, true);
        grip.setGravity(Gravity.CENTER);
        grip.setBackground(rect(0xAA000000, 8, 0, 0));
        webRoot.addView(grip, new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.BOTTOM | Gravity.END));
        makeDraggable(hdr, webRoot, webLp);
        makeResizable(grip, webRoot, webLp, dp(220), dp(240));

        webRoot.setAlpha(0f);
        if (!addWin(webRoot, webLp)) { webRoot = null; webHolder = null; }
    }

    private void showWeb(boolean show) {
        if (webRoot == null) { if (show) toast("Сайт недоступен"); return; }
        webVisible = show;
        webRoot.animate().cancel();
        if (show) {
            webLp.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            updWin(webRoot, webLp);
            webRoot.setScaleX(0.95f); webRoot.setScaleY(0.95f);
            webRoot.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(new DecelerateInterpolator()).start();
        } else {
            try {
                InputMethodManager imm = (InputMethodManager) appCtx.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(webRoot.getWindowToken(), 0);
            } catch (Exception ignored) { }
            webRoot.animate().alpha(0f).setDuration(160).withEndAction(() -> {
                if (!webVisible && webRoot != null) {
                    webLp.flags = FL_NF | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    updWin(webRoot, webLp);
                }
            }).start();
        }
    }

    private void toggleWebSize() {
        if (webRoot == null) return;
        DisplayMetrics m = realMetrics();
        if (webLp.width < m.widthPixels * 0.97f) {
            webLp.width = m.widthPixels; webLp.height = Math.round(m.heightPixels * 0.94f); webLp.x = 0; webLp.y = dp(8);
        } else {
            webLp.width = Math.round(m.widthPixels * 0.94f); webLp.height = Math.round(m.heightPixels * 0.78f);
            webLp.x = (m.widthPixels - webLp.width) / 2; webLp.y = Math.round(m.heightPixels * 0.08f);
        }
        updWin(webRoot, webLp);
    }

    // ==================== Утилиты UI ====================

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private DisplayMetrics realMetrics() {
        DisplayMetrics m = new DisplayMetrics();
        try { wm.getDefaultDisplay().getRealMetrics(m); } catch (Exception e) { m = getResources().getDisplayMetrics(); }
        return m;
    }

    private int screenW() { return realMetrics().widthPixels; }
    private int screenH() { return realMetrics().heightPixels; }

    private GradientDrawable rect(int fill, float rDp, int stroke, float strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(rDp));
        if (strokeDp > 0) g.setStroke(Math.max(1, dp(strokeDp)), stroke);
        return g;
    }

    private void pressFx(View v) {
        v.setOnTouchListener((x, e) -> {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) x.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).start();
            else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) x.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
            return false;
        });
    }

    private TextView tv(String text, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView btn(String text, int fill, float sp, final View.OnClickListener l) {
        TextView t = tv(text, sp, C_TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10), dp(9), dp(10), dp(9));
        t.setBackground(rect(fill, 10, 0, 0));
        t.setOnClickListener(v -> { buzz(); l.onClick(v); });
        pressFx(t);
        return t;
    }

    private TextView chip(String text, int fill, final View.OnClickListener l) {
        TextView t = tv(text, 12, C_TEXT, true);
        t.setSingleLine(true);
        t.setPadding(dp(11), dp(6), dp(11), dp(6));
        t.setBackground(rect(fill, 14, 0, 0));
        t.setOnClickListener(v -> { buzz(); l.onClick(v); });
        pressFx(t);
        return t;
    }

    private TextView iconBtn(String g, int textColor, int bg, int sizeDp, final View.OnClickListener l) {
        TextView t = tv(g, 13, textColor, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(rect(bg, 9, 0, 0));
        t.setOnClickListener(v -> { buzz(); l.onClick(v); });
        pressFx(t);
        return t;
    }

    private LinearLayout.LayoutParams lin(int w, int h, float weight, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h, weight);
        p.setMargins(l, t, r, b);
        return p;
    }

    private TextView sectionLabel(String s) {
        TextView t = tv(s, 11, C_RED, true);
        t.setLetterSpacing(0.12f);
        t.setPadding(0, dp(12), 0, dp(4));
        return t;
    }

    private EditText styledEdit(String hint, String initial, int inputType) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setText(initial);
        et.setInputType(inputType);
        et.setTextColor(C_TEXT);
        et.setHintTextColor(C_MUTED);
        et.setTextSize(14);
        et.setSingleLine((inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0);
        et.setBackground(rect(C_CARD2, 10, C_STROKE, 1));
        et.setPadding(dp(12), dp(10), dp(12), dp(10));
        et.setSelectAllOnFocus(true);
        return et;
    }

    private LinearLayout sliderRow(final String label, final int min, int max, int value, final String unit, final IntCb live, final IntCb commit) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(0, dp(4), 0, dp(4));
        final TextView t = tv(label + ": " + value + unit, 12, C_MUTED, false);
        SeekBar s = new SeekBar(this);
        s.setMax(max - min);
        s.setProgress(value - min);
        try {
            s.getProgressDrawable().setColorFilter(C_RED, PorterDuff.Mode.SRC_IN);
            s.getThumb().setColorFilter(C_RED, PorterDuff.Mode.SRC_IN);
        } catch (Exception ignored) { }
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int pr, boolean user) {
                int v = pr + min;
                t.setText(label + ": " + v + unit);
                if (user && live != null) live.on(v);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { if (commit != null) commit.on(sb.getProgress() + min); }
        });
        r.addView(t);
        r.addView(s, new LinearLayout.LayoutParams(MATCH, WRAP));
        return r;
    }

    private View toggleRow(String label, final String key, boolean def, final Runnable onChange) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(5), 0, dp(5));
        r.addView(tv(label, 13, C_TEXT, false), lin(0, WRAP, 1f, 0, 0, 0, 0));
        final TextView pill = tv("", 11, C_TEXT, true);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(12), dp(5), dp(12), dp(5));
        final boolean[] st = {prefs.getBoolean(key, def)};
        Runnable paint = () -> {
            pill.setText(st[0] ? "ВКЛ" : "ВЫКЛ");
            pill.setBackground(rect(st[0] ? C_GREEN : C_CARD2, 12, 0, 0));
            pill.setTextColor(st[0] ? 0xFF06210F : C_MUTED);
        };
        paint.run();
        pill.setOnClickListener(v -> {
            st[0] = !st[0];
            prefs.edit().putBoolean(key, st[0]).apply();
            paint.run();
            buzz();
            if (onChange != null) onChange.run();
        });
        pressFx(pill);
        r.addView(pill);
        return r;
    }

    private void buzz() {
        if (!prefs.getBoolean("haptic", true)) return;
        try {
            Vibrator v = (Vibrator) appCtx.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null) return;
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(18);
        } catch (Exception ignored) { }
    }

    private void animateIn(View v) {
        v.setAlpha(0f); v.setScaleX(0.93f); v.setScaleY(0.93f);
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(new DecelerateInterpolator()).start();
    }

    private void animateOut(View v, Runnable end) {
        v.animate().alpha(0f).scaleX(0.93f).scaleY(0.93f).setDuration(140).withEndAction(end).start();
    }

    // ----- toast pill -----
    private void toast(String msg) {
        if (!overlayActive) { Toast.makeText(appCtx, msg, Toast.LENGTH_SHORT).show(); return; }
        if (toastView == null) {
            toastText = tv(msg, 13, C_TEXT, true);
            toastText.setPadding(dp(16), dp(10), dp(16), dp(10));
            toastText.setBackground(rect(0xF01B1B20, 20, C_RED, 1));
            toastView = new FrameLayout(this);
            toastView.addView(toastText);
            toastLp = baseLp(WRAP, WRAP, FL_NF | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            toastLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            toastLp.y = dp(90);
            toastView.setAlpha(0f);
            if (addWin(toastView, toastLp)) toastView.animate().alpha(1f).setDuration(160).start();
        } else {
            toastText.setText(msg);
            toastView.animate().cancel();
            toastView.setAlpha(1f);
        }
        ui.removeCallbacks(toastHide);
        ui.postDelayed(toastHide, 1900);
    }

    private void hideToastPill() {
        if (toastView == null) return;
        final View v = toastView;
        toastView = null;
        v.animate().alpha(0f).setDuration(200).withEndAction(() -> removeWin(v)).start();
    }

    // ----- окна -----
    private int overlayType() {
        return Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private WindowManager.LayoutParams baseLp(int w, int h, int flags) {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(w, h, overlayType(), flags, PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        return p;
    }

    private WindowManager.LayoutParams fullLp(int flags) {
        WindowManager.LayoutParams p = baseLp(MATCH, MATCH, flags | FL_SCREEN);
        if (Build.VERSION.SDK_INT >= 28) p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        return p;
    }

    private boolean addWin(View v, WindowManager.LayoutParams p) {
        try { wm.addView(v, p); return true; } catch (Exception e) { Log.e(TAG, "addView", e); return false; }
    }

    private void removeWin(View v) {
        if (v == null) return;
        try { wm.removeViewImmediate(v); } catch (Exception ignored) { }
    }

    private void updWin(View v, WindowManager.LayoutParams p) {
        if (v == null) return;
        try { wm.updateViewLayout(v, p); } catch (Exception ignored) { }
    }

    private void makeDraggable(View handle, final View win, final WindowManager.LayoutParams lp) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            float tx, ty; int ox, oy;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: tx = e.getRawX(); ty = e.getRawY(); ox = lp.x; oy = lp.y; return true;
                    case MotionEvent.ACTION_MOVE:
                        lp.x = clamp(ox + (int) (e.getRawX() - tx), -lp.width + dp(70), screenW() - dp(70));
                        lp.y = clamp(oy + (int) (e.getRawY() - ty), 0, screenH() - dp(50));
                        updWin(win, lp);
                        return true;
                    case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: return true;
                }
                return false;
            }
        });
    }

    private void makeResizable(View grip, final View win, final WindowManager.LayoutParams lp, final int minW, final int minH) {
        grip.setOnTouchListener(new View.OnTouchListener() {
            float tx, ty; int ow, oh;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: tx = e.getRawX(); ty = e.getRawY(); ow = lp.width; oh = lp.height; return true;
                    case MotionEvent.ACTION_MOVE:
                        lp.width = clamp(ow + (int) (e.getRawX() - tx), minW, screenW());
                        lp.height = clamp(oh + (int) (e.getRawY() - ty), minH, screenH());
                        updWin(win, lp);
                        return true;
                    case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: return true;
                }
                return false;
            }
        });
    }

    // ----- панели -----
    private Panel openPanel(String key, String icon, String title, int wDp, int hDp) {
        Panel ex = panels.get(key);
        if (ex != null) {
            removeWin(ex.root);
            addWin(ex.root, ex.lp);
            animateIn(ex.root);
            return ex;
        }
        final Panel p = new Panel();
        p.key = key;
        DisplayMetrics m = realMetrics();
        int w = Math.min(dp(wDp), m.widthPixels - dp(16)), h = Math.min(dp(hDp), m.heightPixels - dp(60));
        p.lp = baseLp(w, h, FL_NF);
        p.lp.x = (m.widthPixels - w) / 2;
        p.lp.y = Math.max(dp(24), (m.heightPixels - h) / 3);
        p.root = new FrameLayout(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackground(rect(C_BG, 16, C_STROKE, 1));
        col.setClipToOutline(true);

        LinearLayout hdr = new LinearLayout(this);
        hdr.setOrientation(LinearLayout.HORIZONTAL);
        hdr.setGravity(Gravity.CENTER_VERTICAL);
        hdr.setPadding(dp(12), dp(4), dp(6), dp(4));
        hdr.setBackgroundColor(C_CARD);
        TextView t = tv(icon + "  " + title, 13, C_RED, true);
        t.setLetterSpacing(0.1f);
        hdr.addView(t, lin(0, WRAP, 1f, 0, 0, 0, 0));
        hdr.addView(iconBtn("✕", C_TEXT, C_CARD2, 34, v -> closePanel(key)), lin(dp(34), dp(34), 0, 0, 0, 0, 0));
        col.addView(hdr, lin(MATCH, WRAP, 0, 0, 0, 0, 0));

        p.body = new LinearLayout(this);
        p.body.setOrientation(LinearLayout.VERTICAL);
        p.body.setPadding(dp(10), dp(8), dp(10), dp(8));
        col.addView(p.body, lin(MATCH, 0, 1f, 0, 0, 0, 0));
        p.root.addView(col, new FrameLayout.LayoutParams(MATCH, MATCH));

        TextView grip = tv("◢", 14, C_MUTED, true);
        grip.setGravity(Gravity.CENTER);
        p.root.addView(grip, new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.BOTTOM | Gravity.END));
        makeDraggable(hdr, p.root, p.lp);
        makeResizable(grip, p.root, p.lp, dp(240), dp(240));

        if (addWin(p.root, p.lp)) {
            animateIn(p.root);
            panels.put(key, p);
        }
        return p;
    }

    private void closePanel(String key) {
        final Panel p = panels.remove(key);
        if (p == null) return;
        if ("macros".equals(key) && captureRoot != null) closeCapture(false);
        animateOut(p.root, () -> removeWin(p.root));
    }

    private void refreshPanel(String key) {
        Panel p = panels.get(key);
        if (p != null && p.refresh != null) p.refresh.run();
    }

    // ----- диалоги -----
    private View showDialog(String title, View content, String okText, final Check ok, String cancelText, int fl) {
        final FrameLayout scrim = new FrameLayout(this) {
            @Override public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    if (e.getAction() == KeyEvent.ACTION_UP) dismissDialog(this);
                    return true;
                }
                return super.dispatchKeyEvent(e);
            }
        };
        scrim.setBackgroundColor(0xB3000000);
        scrim.setOnClickListener(v -> dismissDialog(scrim));
        final LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(rect(C_CARD, 16, C_RED, 1.2f));
        card.setClickable(true);

        TextView tt = tv(title, 15, C_TEXT, true);
        tt.setLetterSpacing(0.04f);
        card.addView(tt);
        View line = new View(this);
        line.setBackgroundColor(C_RED);
        card.addView(line, lin(dp(36), dp(2), 0, 0, dp(6), 0, dp(10)));

        if ((fl & DF_SCROLL) != 0) {
            ScrollView sv = new ScrollView(this);
            sv.addView(content);
            card.addView(sv, lin(MATCH, Math.min(Math.round(screenH() * 0.5f), dp(420)), 0, 0, 0, 0, dp(8)));
        } else {
            card.addView(content, lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, 0);
        if (cancelText != null)
            row.addView(btn(cancelText, C_CARD2, 13, v -> dismissDialog(scrim)), lin(0, WRAP, 1f, 0, 0, okText != null ? dp(8) : 0, 0));
        if (okText != null)
            row.addView(btn(okText, C_RED, 13, v -> { if (ok == null || ok.run()) dismissDialog(scrim); }), lin(0, WRAP, 1f, 0, 0, 0, 0));
        card.addView(row);

        scrim.addView(card, new FrameLayout.LayoutParams(Math.min(dp(320), screenW() - dp(32)), WRAP, Gravity.CENTER));
        WindowManager.LayoutParams lp = fullLp(0);
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | ((fl & DF_IME) != 0 ? WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE : 0);
        if (addWin(scrim, lp)) {
            dialogs.add(scrim);
            animateIn(card);
            if ((fl & DF_IME) != 0) content.requestFocus();
        }
        return scrim;
    }

    private void dismissDialog(final View v) {
        if (v == null) return;
        dialogs.remove(v);
        try {
            InputMethodManager imm = (InputMethodManager) appCtx.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        } catch (Exception ignored) { }
        v.animate().alpha(0f).setDuration(120).withEndAction(() -> removeWin(v)).start();
    }

    private void promptText(String title, String hint, String initial, int inputType, final StrCb cb) {
        final EditText et = styledEdit(hint, initial, inputType);
        showDialog(title, et, "ОК", () -> { cb.on(et.getText().toString().trim()); return true; }, "Отмена", DF_IME);
    }

    private void confirm(String title, String msg, String okText, final Runnable ok) {
        TextView t = tv(msg, 13, C_MUTED, false);
        showDialog(title, t, okText, () -> { ok.run(); return true; }, "Отмена", 0);
    }

    private void choose(String title, String[] items, final IntCb cb) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final View[] h = new View[1];
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            box.addView(btn(items[i], C_CARD2, 13, v -> { dismissDialog(h[0]); ui.postDelayed(() -> cb.on(idx), 150); }), lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        }
        h[0] = showDialog(title, box, null, null, "Отмена", items.length > 6 ? DF_SCROLL : 0);
    }

    // ==================== Пузырь (геймпад) ====================

    private boolean keepScreenNow() {
        int mode = prefs.getInt("keep_mode", 1);
        return overlayActive && (mode == 2 || (mode == 1 && !runners.isEmpty()));
    }

    private int bubbleFlags() { return FL_NF | (keepScreenNow() ? WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON : 0); }

    private void showBubble() {
        if (bubbleRoot != null) return;
        int size = dp(prefs.getInt("bubble_dp", 56));
        int win = size + dp(12);
        gamepad = new GamepadView(this);
        gamepad.setRunning(!runners.isEmpty());
        bubbleRoot = new FrameLayout(this);
        bubbleRoot.addView(gamepad, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        bubbleRoot.setAlpha(prefs.getInt("bubble_alpha", 100) / 100f);
        bubbleLp = baseLp(win, win, bubbleFlags());
        bubbleLp.x = prefs.getInt("bubble_x", screenW() - win - dp(2));
        bubbleLp.y = prefs.getInt("bubble_y", screenH() / 3);

        bubbleRoot.setOnTouchListener(new View.OnTouchListener() {
            float tx, ty; int ox, oy; boolean drag, longDone;
            final Runnable lr = () -> {
                if (drag) return;
                longDone = true;
                buzz();
                if (!runners.isEmpty()) { stopAllMacros(); toast("⏹ Все макросы остановлены"); }
                else toast("Долгое нажатие — стоп всех макросов");
            };

            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        tx = e.getRawX(); ty = e.getRawY(); ox = bubbleLp.x; oy = bubbleLp.y; drag = false; longDone = false;
                        bubbleRoot.animate().scaleX(0.9f).scaleY(0.9f).setDuration(90).start();
                        ui.postDelayed(lr, 700);
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float dx = e.getRawX() - tx, dy = e.getRawY() - ty;
                        if (!drag && (Math.abs(dx) > dp(8) || Math.abs(dy) > dp(8))) { drag = true; ui.removeCallbacks(lr); }
                        if (drag) {
                            bubbleLp.x = ox + (int) dx;
                            bubbleLp.y = clamp(oy + (int) dy, 0, screenH() - bubbleLp.height);
                            updWin(bubbleRoot, bubbleLp);
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        ui.removeCallbacks(lr);
                        bubbleRoot.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
                        if (drag) snapBubble();
                        else if (e.getActionMasked() == MotionEvent.ACTION_UP && !longDone) toggleMenu();
                        return true;
                }
                return false;
            }
        });

        if (addWin(bubbleRoot, bubbleLp)) {
            bubbleRoot.setScaleX(0.3f); bubbleRoot.setScaleY(0.3f);
            bubbleRoot.animate().scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(new OvershootInterpolator(1.4f)).start();
            updatePulse(!runners.isEmpty());
        } else {
            bubbleRoot = null; gamepad = null;
        }
    }

    private void rebuildBubble() {
        stopPulse();
        removeWin(bubbleRoot);
        bubbleRoot = null; gamepad = null;
        if (overlayActive) showBubble();
    }

    private void snapBubble() {
        if (bubbleRoot == null) return;
        int sw = screenW();
        final int target = (bubbleLp.x + bubbleLp.width / 2 < sw / 2) ? dp(2) : sw - bubbleLp.width - dp(2);
        ValueAnimator va = ValueAnimator.ofInt(bubbleLp.x, target);
        va.setDuration(240);
        va.setInterpolator(new OvershootInterpolator(0.8f));
        va.addUpdateListener(a -> {
            if (bubbleRoot == null) return;
            bubbleLp.x = (int) a.getAnimatedValue();
            updWin(bubbleRoot, bubbleLp);
        });
        va.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (bubbleLp != null) prefs.edit().putInt("bubble_x", bubbleLp.x).putInt("bubble_y", bubbleLp.y).apply();
            }
        });
        va.start();
    }

    private void updatePulse(boolean run) {
        stopPulse();
        if (!run || gamepad == null) return;
        pulseAnim = ValueAnimator.ofFloat(1f, 1.12f);
        pulseAnim.setDuration(700);
        pulseAnim.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnim.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnim.addUpdateListener(a -> {
            if (gamepad == null) return;
            float f = (float) a.getAnimatedValue();
            gamepad.setScaleX(f); gamepad.setScaleY(f);
        });
        pulseAnim.start();
    }

    private void stopPulse() {
        if (pulseAnim != null) { pulseAnim.cancel(); pulseAnim = null; }
        if (gamepad != null) { gamepad.setScaleX(1f); gamepad.setScaleY(1f); }
    }

    // ==================== Главное меню ====================

    private void toggleMenu() { if (menuRoot != null) closeMenu(); else openMenu(); }

    private void openMenu() {
        if (menuRoot != null || !overlayActive) return;
        final FrameLayout scrim = new FrameLayout(this);
        scrim.setBackgroundColor(0x99000000);
        scrim.setOnClickListener(v -> closeMenu());
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(14));
        card.setBackground(rect(C_BG, 20, C_RED, 1.5f));
        card.setClickable(true);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = tv("ARCADE", 17, C_RED, true);
        t.setLetterSpacing(0.18f);
        head.addView(t, lin(0, WRAP, 1f, 0, 0, 0, 0));
        head.addView(tv(runners.isEmpty() ? "готов" : "макросов: " + runners.size(), 12, runners.isEmpty() ? C_MUTED : C_GREEN, true));
        card.addView(head, lin(MATCH, WRAP, 0, 0, 0, 0, dp(10)));

        String[][] tiles = {{"🌐", "Сайт"}, {"🎯", "Макросы"}, {"🔊", "Звуки"}, {"👤", "Персонажи"}, {"⚙️", "Настройки"}, {"⏻", "Выключить"}};
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(this);
            for (int c = 0; c < 3; c++) {
                final int idx = r * 3 + c;
                LinearLayout tile = new LinearLayout(this);
                tile.setOrientation(LinearLayout.VERTICAL);
                tile.setGravity(Gravity.CENTER);
                tile.setPadding(0, dp(12), 0, dp(10));
                tile.setBackground(rect(idx == 5 ? 0xFF3A1214 : C_CARD2, 14, idx == 5 ? C_RED : 0, 1));
                tile.addView(tv(tiles[idx][0], 24, C_TEXT, false));
                TextView lb = tv(tiles[idx][1], 11, idx == 5 ? C_RED : C_MUTED, true);
                lb.setPadding(0, dp(4), 0, 0);
                tile.addView(lb);
                tile.setOnClickListener(v -> { buzz(); closeMenu(); ui.postDelayed(() -> handleMenu(idx), 140); });
                pressFx(tile);
                row.addView(tile, lin(0, WRAP, 1f, dp(3), dp(3), dp(3), dp(3)));
            }
            card.addView(row, lin(MATCH, WRAP, 0, 0, 0, 0, 0));
        }
        if (!runners.isEmpty())
            card.addView(btn("■  СТОП ВСЕХ МАКРОСОВ", C_RED, 13, v -> { stopAllMacros(); closeMenu(); }), lin(MATCH, WRAP, 0, dp(3), dp(8), dp(3), 0));

        scrim.addView(card, new FrameLayout.LayoutParams(Math.min(dp(310), screenW() - dp(32)), WRAP, Gravity.CENTER));
        WindowManager.LayoutParams lp = fullLp(FL_NF);
        if (addWin(scrim, lp)) {
            menuRoot = scrim;
            scrim.setAlpha(0f);
            scrim.animate().alpha(1f).setDuration(140).start();
            card.setScaleX(0.85f); card.setScaleY(0.85f);
            card.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new OvershootInterpolator(1.2f)).start();
        }
    }

    private void closeMenu() {
        if (menuRoot == null) return;
        final View v = menuRoot;
        menuRoot = null;
        v.animate().alpha(0f).setDuration(120).withEndAction(() -> removeWin(v)).start();
    }

    private void closeMenuNow() {
        if (menuRoot != null) { removeWin(menuRoot); menuRoot = null; }
    }

    private void handleMenu(int idx) {
        if (!overlayActive) return;
        switch (idx) {
            case 0: showWeb(true); break;
            case 1: openMacrosPanel(); break;
            case 2: openSoundsPanel(); break;
            case 3: openCharsPanel(); break;
            case 4: openSettingsPanel(); break;
            case 5: confirmClose(); break;
        }
    }

    private void confirmClose() {
        confirm("ВЫКЛЮЧИТЬ ПЛАТФОРМУ?", "Оверлей, фоновый режим, макросы и звуки будут остановлены, приложение закроется.", "ВЫКЛЮЧИТЬ", this::closePlatform);
    }

    // ==================== Макросы: данные ====================

    private void loadMacroConfigs() {
        allMacros.clear(); macroIds.clear();
        try {
            String json = prefs.getString("all_macros", "");
            if (!json.isEmpty()) {
                JSONObject o = new JSONObject(json);
                JSONArray ids = o.getJSONArray("ids");
                JSONObject data = o.getJSONObject("data");
                for (int i = 0; i < ids.length(); i++) {
                    String id = ids.getString(i);
                    if (data.has(id)) { allMacros.put(id, new MacroConfig(data.getJSONObject(id))); macroIds.add(id); }
                }
            }
        } catch (Exception e) { Log.e(TAG, "macros", e); }
        if (macroIds.isEmpty()) createMacro("Макрос 1");
        String cur = prefs.getString("cur_macro", "");
        currentMacroId = allMacros.containsKey(cur) ? cur : macroIds.get(0);
    }

    private String createMacro(String name) {
        String id = UUID.randomUUID().toString();
        allMacros.put(id, new MacroConfig(id, name));
        macroIds.add(id);
        saveMacroConfigs();
        return id;
    }

    private void saveMacroConfigs() {
        try {
            JSONObject o = new JSONObject();
            JSONArray ids = new JSONArray();
            JSONObject data = new JSONObject();
            for (String id : macroIds) { ids.put(id); data.put(id, allMacros.get(id).toJSON()); }
            o.put("ids", ids); o.put("data", data);
            prefs.edit().putString("all_macros", o.toString()).apply();
        } catch (Exception e) { Log.e(TAG, "saveMacros", e); }
    }

    private void loadQuickButtons() {
        quickButtons.clear();
        try {
            String json = prefs.getString("quick_buttons", "");
            if (!json.isEmpty()) {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    QuickMacroButton b = new QuickMacroButton(arr.getJSONObject(i));
                    if (allMacros.containsKey(b.macroId)) quickButtons.put(b.macroId, b);
                }
            }
            for (QuickMacroButton b : quickButtons.values()) if (b.id >= quickButtonIdCounter) quickButtonIdCounter = b.id + 1;
        } catch (Exception e) { Log.e(TAG, "quick", e); }
    }

    private void saveQuickButtons() {
        try {
            JSONArray arr = new JSONArray();
            for (QuickMacroButton b : quickButtons.values()) arr.put(b.toJSON());
            prefs.edit().putString("quick_buttons", arr.toString()).putInt("quick_button_id_counter", quickButtonIdCounter).apply();
        } catch (Exception e) { Log.e(TAG, "saveQuick", e); }
    }

    // ==================== Макросы: выполнение ====================

    private class MacroRunner implements Runnable {
        final String macroId;
        final int limit;
        int index = 0, cycle = 0;
        boolean active = true;

        MacroRunner(String id, int limit) { this.macroId = id; this.limit = limit; }

        @Override public void run() {
            if (!active) return;
            MacroConfig c = allMacros.get(macroId);
            if (c == null || c.points.isEmpty()) { stopMacro(macroId, true); return; }
            if (index >= c.points.size()) {
                cycle++;
                index = 0;
                if (limit > 0 && cycle >= limit) { toast("✅ Макрос «" + c.name + "» завершён"); stopMacro(macroId, true); return; }
            }
            MacroPoint pt = c.points.get(index);
            doPoint(pt, c);
            if (pointsView != null) pointsView.flash(index);
            long d = Math.max(10, pt.delayMs);
            if (pt.type == ActionType.SWIPE || pt.type == ActionType.LONG) d = Math.max(d, pt.swipeDuration + 30);
            if (c.variation > 0) {
                double f = 1 + (rnd.nextDouble() * 2 - 1) * c.variation / 100.0;
                d = Math.max(10, (long) (d * f));
            }
            index++;
            ui.postDelayed(this, d);
        }
    }

    private int jit(int v, int j) { return j <= 0 ? v : v + rnd.nextInt(j * 2 + 1) - j; }

    private void doPoint(MacroPoint p, MacroConfig c) {
        try {
            MacroService s = MacroService.getInstance();
            if (s == null) return;
            int x = jit(p.x, c.jitter), y = jit(p.y, c.jitter);
            switch (p.type) {
                case CLICK: s.performClick(x, y); break;
                case SWIPE: s.performSwipe(x, y, jit(p.endX, c.jitter), jit(p.endY, c.jitter), p.swipeDuration); break;
                case LONG: s.performSwipe(x, y, x + 1, y, Math.max(300, p.swipeDuration)); break;
                default: break;
            }
        } catch (Exception e) { Log.e(TAG, "doPoint", e); }
    }

    private boolean ensureAccessibility() {
        if (isAccessibilityEnabled()) return true;
        toast("Включите «Специальные возможности» для макросов");
        try {
            appCtx.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) { }
        return false;
    }

    private void startMacro(String id, boolean infinite) {
        MacroConfig c = allMacros.get(id);
        if (c == null) return;
        if (c.points.isEmpty()) { toast("В макросе нет точек"); return; }
        if (!ensureAccessibility()) return;
        stopMacro(id, false);
        MacroRunner r = new MacroRunner(id, infinite ? 0 : c.repeatCount);
        runners.put(id, r);
        ui.postDelayed(r, Math.max(0, c.startDelay));
        onRunnersChanged();
    }

    private void stopMacro(String id, boolean notify) {
        MacroRunner r = runners.remove(id);
        if (r != null) { r.active = false; ui.removeCallbacks(r); }
        if (notify) onRunnersChanged();
    }

    private void stopAllMacros() {
        if (runners.isEmpty()) return;
        for (MacroRunner r : runners.values()) { r.active = false; ui.removeCallbacks(r); }
        runners.clear();
        onRunnersChanged();
    }

    private void onRunnersChanged() {
        boolean running = !runners.isEmpty();
        if (gamepad != null) gamepad.setRunning(running);
        updatePulse(running);
        for (QuickMacroButton b : quickButtons.values()) if (b.view != null) b.view.setRunning(runners.containsKey(b.macroId));
        applyKeepScreen();
        refreshPointsView();
        refreshPanel("macros");
    }

    private void applyKeepScreen() {
        if (bubbleRoot != null && bubbleLp != null) {
            bubbleLp.flags = bubbleFlags();
            updWin(bubbleRoot, bubbleLp);
        }
        boolean need = overlayActive && (!runners.isEmpty() || prefs.getInt("keep_mode", 1) == 2);
        if (need) acquireWakeLock(); else releaseWakeLock();
    }

    private void acquireWakeLock() {
        try {
            if (wakeLock == null) {
                PowerManager pm = (PowerManager) appCtx.getSystemService(Context.POWER_SERVICE);
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "arcade:macro");
            }
            if (!wakeLock.isHeld()) wakeLock.acquire(6 * 60 * 60 * 1000L);
        } catch (Exception ignored) { }
    }

    private void releaseWakeLock() {
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignored) { }
    }

    private void refreshPointsView() {
        String showId = null;
        if (captureRoot != null) showId = currentMacroId;
        else if (overlayActive && prefs.getBoolean("show_points", true) && !runners.isEmpty()) showId = runners.keySet().iterator().next();
        MacroConfig c = showId == null ? null : allMacros.get(showId);
        if (c == null) { removeWin(pointsView); pointsView = null; return; }
        if (pointsView == null) {
            pointsView = new PointsView(this);
            if (!addWin(pointsView, fullLp(FL_NF | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE))) { pointsView = null; return; }
        }
        pointsView.setPoints(c);
    }

    // ==================== Макросы: быстрые кнопки ====================

    private void attachQuick(final QuickMacroButton b) {
        if (!overlayActive || b.view != null) return;
        b.view = new QuickView(this);
        int win = dp(b.sizeDp + 16);
        b.lp = baseLp(win, win, FL_NF);
        b.lp.x = b.x; b.lp.y = b.y;
        applyQuickVisual(b);
        b.view.setOnTouchListener(new View.OnTouchListener() {
            float tx, ty; int ox, oy; boolean drag, longDone;
            final Runnable lr = () -> {
                if (drag) return;
                longDone = true;
                buzz();
                MacroConfig c = allMacros.get(b.macroId);
                if (c != null && c.mode == MacroMode.HOLD) stopMacro(b.macroId, true);
                showQuickSettings(b);
            };

            @Override public boolean onTouch(View v, MotionEvent e) {
                MacroConfig c = allMacros.get(b.macroId);
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        tx = e.getRawX(); ty = e.getRawY(); ox = b.lp.x; oy = b.lp.y; drag = false; longDone = false;
                        v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).start();
                        ui.postDelayed(lr, 700);
                        if (c != null && c.mode == MacroMode.HOLD && !runners.containsKey(b.macroId)) startMacro(b.macroId, true);
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float dx = e.getRawX() - tx, dy = e.getRawY() - ty;
                        if (!drag && (Math.abs(dx) > dp(8) || Math.abs(dy) > dp(8))) {
                            if (!b.isFixed) {
                                drag = true;
                                ui.removeCallbacks(lr);
                                if (c != null && c.mode == MacroMode.HOLD) stopMacro(b.macroId, true);
                            }
                        }
                        if (drag) {
                            b.lp.x = clamp(ox + (int) dx, 0, screenW() - b.lp.width);
                            b.lp.y = clamp(oy + (int) dy, 0, screenH() - b.lp.height);
                            updWin(b.view, b.lp);
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        ui.removeCallbacks(lr);
                        v.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
                        if (drag) {
                            b.x = b.lp.x; b.y = b.lp.y; saveQuickButtons();
                        } else if (c != null && c.mode == MacroMode.HOLD) {
                            stopMacro(b.macroId, true);
                        } else if (e.getActionMasked() == MotionEvent.ACTION_UP && !longDone) {
                            quickTap(b);
                        }
                        return true;
                }
                return false;
            }
        });
        if (!addWin(b.view, b.lp)) b.view = null;
    }

    private void quickTap(QuickMacroButton b) {
        MacroConfig c = allMacros.get(b.macroId);
        if (c == null) return;
        buzz();
        if (runners.containsKey(b.macroId)) { stopMacro(b.macroId, true); return; }
        startMacro(b.macroId, c.mode == MacroMode.TOGGLE);
    }

    private void detachQuick(QuickMacroButton b) {
        if (b.view != null) { removeWin(b.view); b.view = null; }
    }

    private void applyQuickVisual(QuickMacroButton b) {
        if (b.view == null) return;
        MacroConfig c = allMacros.get(b.macroId);
        b.view.label = b.text;
        b.view.name = c != null ? c.name : "";
        b.view.accent = b.color;
        b.view.circle = "circle".equals(b.shape);
        b.view.locked = b.isFixed;
        b.view.mode = c == null ? 0 : c.mode.ordinal();
        b.view.setAlpha(b.alpha);
        b.view.setRunning(runners.containsKey(b.macroId));
        b.view.invalidate();
    }

    private void applyQuick(QuickMacroButton b) {
        applyQuickVisual(b);
        if (b.view != null && b.lp != null) {
            b.lp.width = dp(b.sizeDp + 16);
            b.lp.height = dp(b.sizeDp + 16);
            updWin(b.view, b.lp);
        }
    }

    private void createQuickFor(String macroId) {
        MacroConfig c = allMacros.get(macroId);
        if (c == null) return;
        if (c.points.isEmpty()) { toast("Сначала добавьте точки"); return; }
        if (quickButtons.containsKey(macroId)) { toast("Кнопка для этого макроса уже есть"); return; }
        QuickMacroButton b = new QuickMacroButton(macroId, quickButtonIdCounter++);
        int n = quickButtons.size();
        b.x = dp(16 + (n % 4) * 72);
        b.y = dp(130 + (n / 4) * 72);
        quickButtons.put(macroId, b);
        c.isSavedAsButton = true;
        saveMacroConfigs();
        saveQuickButtons();
        attachQuick(b);
        toast("🔘 Кнопка #" + b.id + " создана — зажмите её для настроек");
        refreshPanel("macros");
    }

    private void showQuickSettings(final QuickMacroButton b) {
        final MacroConfig c = allMacros.get(b.macroId);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(tv("Макрос: " + (c != null ? c.name : "?"), 13, C_MUTED, false));
        box.addView(sliderRow("Размер", 36, 110, b.sizeDp, " dp", v -> { b.sizeDp = v; applyQuick(b); }, v -> saveQuickButtons()));
        box.addView(sliderRow("Прозрачность", 25, 100, Math.round(b.alpha * 100), "%", v -> { b.alpha = v / 100f; applyQuick(b); }, v -> saveQuickButtons()));

        LinearLayout colors = new LinearLayout(this);
        colors.setPadding(0, dp(6), 0, dp(6));
        for (final int col : ACCENTS) {
            View sw = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(col);
            g.setStroke(dp(2), col == b.color ? 0xFFFFFFFF : 0x00000000);
            sw.setBackground(g);
            sw.setOnClickListener(v -> { b.color = col; applyQuick(b); saveQuickButtons(); toast("Цвет изменён"); });
            colors.addView(sw, lin(dp(30), dp(30), 0, 0, 0, dp(8), 0));
        }
        box.addView(colors);

        final TextView fixed = btn(b.isFixed ? "🔒 Позиция зафиксирована" : "🔓 Можно двигать", C_CARD2, 12, v -> { });
        fixed.setOnClickListener(v -> {
            b.isFixed = !b.isFixed;
            fixed.setText(b.isFixed ? "🔒 Позиция зафиксирована" : "🔓 Можно двигать");
            applyQuick(b); saveQuickButtons();
        });
        box.addView(fixed, lin(MATCH, WRAP, 0, 0, dp(4), 0, dp(6)));
        final TextView shape = btn("circle".equals(b.shape) ? "⬤ Форма: круг" : "▢ Форма: квадрат", C_CARD2, 12, v -> { });
        shape.setOnClickListener(v -> {
            b.shape = "circle".equals(b.shape) ? "rounded" : "circle";
            shape.setText("circle".equals(b.shape) ? "⬤ Форма: круг" : "▢ Форма: квадрат");
            applyQuick(b); saveQuickButtons();
        });
        box.addView(shape, lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        box.addView(btn("✏️ Подпись на кнопке", C_CARD2, 12, v ->
                promptText("ПОДПИСЬ", "Текст", b.text, InputType.TYPE_CLASS_TEXT, s -> {
                    b.text = s.isEmpty() ? String.valueOf(b.id) : (s.length() > 4 ? s.substring(0, 4) : s);
                    applyQuick(b); saveQuickButtons();
                })), lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        box.addView(btn("🎯 Открыть этот макрос", C_BLUE, 12, v -> {
            currentMacroId = b.macroId;
            prefs.edit().putString("cur_macro", currentMacroId).apply();
            openMacrosPanel();
        }), lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        box.addView(btn("🗑 Удалить кнопку", 0xFF5A1518, 12, v ->
                confirm("УДАЛИТЬ КНОПКУ?", "Макрос останется в списке.", "УДАЛИТЬ", () -> {
                    stopMacro(b.macroId, true);
                    detachQuick(b);
                    quickButtons.remove(b.macroId);
                    if (c != null) c.isSavedAsButton = false;
                    saveMacroConfigs(); saveQuickButtons();
                    refreshPanel("macros");
                })), lin(MATCH, WRAP, 0, 0, 0, 0, 0));
        showDialog("КНОПКА #" + b.id, box, "ГОТОВО", null, null, DF_SCROLL);
    }

    // ==================== Макросы: панель ====================

    private void openMacrosPanel() {
        final Panel p = openPanel("macros", "🎯", "МАКРОСЫ", 350, 540);
        p.refresh = () -> buildMacrosBody(p);
        p.refresh.run();
    }

    private String modeRu(MacroMode m) { return m == MacroMode.HOLD ? "Зажим" : m == MacroMode.TOGGLE ? "Переключатель" : "Обычный"; }

    private void buildMacrosBody(final Panel p) {
        final int sy = macroScroll != null ? macroScroll.getScrollY() : 0;
        p.body.removeAllViews();
        final MacroConfig c = allMacros.get(currentMacroId);
        if (c == null) return;

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        for (final String id : macroIds) {
            MacroConfig m = allMacros.get(id);
            if (m == null) continue;
            String label = (quickButtons.containsKey(id) ? "🔘 " : "") + m.name + (runners.containsKey(id) ? " ●" : "");
            chips.addView(chip(label, id.equals(currentMacroId) ? C_RED : C_CARD2, v -> {
                currentMacroId = id;
                prefs.edit().putString("cur_macro", id).apply();
                p.refresh.run();
            }), lin(WRAP, WRAP, 0, 0, 0, dp(6), 0));
        }
        chips.addView(chip("＋", C_CARD2, v -> promptText("НОВЫЙ МАКРОС", "Название", "Макрос " + (macroIds.size() + 1), InputType.TYPE_CLASS_TEXT, s -> {
            currentMacroId = createMacro(s.isEmpty() ? "Макрос " + (macroIds.size() + 1) : s);
            prefs.edit().putString("cur_macro", currentMacroId).apply();
            refreshPanel("macros");
        })));
        hs.addView(chips);
        p.body.addView(hs, lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));

        LinearLayout info = new LinearLayout(this);
        info.addView(chip("РЕЖИМ: " + modeRu(c.mode), C_CARD2, v ->
                choose("РЕЖИМ МАКРОСА", new String[]{"Обычный — запуск на N циклов", "Зажим — работает пока держишь кнопку", "Переключатель — вкл/выкл, бесконечно"}, i -> {
                    c.mode = i == 1 ? MacroMode.HOLD : i == 2 ? MacroMode.TOGGLE : MacroMode.NORMAL;
                    saveMacroConfigs();
                    for (QuickMacroButton b : quickButtons.values()) applyQuickVisual(b);
                    refreshPanel("macros");
                })), lin(WRAP, WRAP, 0, 0, 0, dp(6), 0));
        info.addView(chip("ЦИКЛЫ: " + (c.repeatCount == 0 ? "∞" : String.valueOf(c.repeatCount)), C_CARD2, v ->
                promptText("КОЛИЧЕСТВО ЦИКЛОВ (0 = бесконечно)", "Число", String.valueOf(c.repeatCount), InputType.TYPE_CLASS_NUMBER, s -> {
                    try { c.repeatCount = Math.max(0, Math.min(99999, Integer.parseInt(s))); } catch (Exception ignored) { }
                    saveMacroConfigs(); refreshPanel("macros");
                })), lin(WRAP, WRAP, 0, 0, 0, dp(6), 0));
        info.addView(chip("⋮", C_CARD2, v -> macroMenu(c)));
        p.body.addView(info, lin(MATCH, WRAP, 0, 0, 0, 0, dp(4)));

        TextView hint = tv(c.mode == MacroMode.HOLD ? "Кнопка работает, пока вы её удерживаете" :
                c.mode == MacroMode.TOGGLE ? "Нажатие на кнопку включает и выключает бесконечный цикл" :
                        "Нажатие на кнопку запускает макрос, повторное — останавливает", 11, C_MUTED, false);
        p.body.addView(hint, lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        macroScroll = sv;
        p.body.addView(sv, lin(MATCH, 0, 1f, 0, 0, 0, dp(6)));

        boolean animate = !currentMacroId.equals(animatedMacroId);
        animatedMacroId = currentMacroId;
        if (c.points.isEmpty()) {
            TextView e = tv("Действий пока нет.\nДобавьте клик, свайп, долгое нажатие или паузу ниже.", 13, C_MUTED, false);
            e.setGravity(Gravity.CENTER);
            e.setPadding(0, dp(30), 0, dp(30));
            list.addView(e);
        } else {
            for (int i = 0; i < c.points.size(); i++) {
                View row = pointRow(c, i, p);
                list.addView(row, lin(MATCH, WRAP, 0, 0, 0, 0, dp(5)));
                if (animate) {
                    row.setAlpha(0f); row.setTranslationY(dp(10));
                    row.animate().alpha(1f).translationY(0).setStartDelay(Math.min(i * 25L, 250L)).setDuration(200).start();
                }
            }
        }
        sv.post(() -> sv.scrollTo(0, sy));

        LinearLayout add = new LinearLayout(this);
        add.addView(btn("● КЛИК", C_RED, 11, v -> startCapture(ActionType.CLICK, -1)), lin(0, WRAP, 1f, 0, 0, dp(4), 0));
        add.addView(btn("↗ СВАЙП", C_BLUE, 11, v -> startCapture(ActionType.SWIPE, -1)), lin(0, WRAP, 1f, 0, 0, dp(4), 0));
        add.addView(btn("⏺ ДОЛГО", 0xFFB27800, 11, v -> startCapture(ActionType.LONG, -1)), lin(0, WRAP, 1f, 0, 0, dp(4), 0));
        add.addView(btn("⏱ ПАУЗА", 0xFF4A4A55, 11, v -> addWait(c)), lin(0, WRAP, 1f, 0, 0, 0, 0));
        p.body.addView(add, lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));

        boolean running = runners.containsKey(c.id);
        LinearLayout ctl = new LinearLayout(this);
        ctl.addView(btn(running ? "■ СТОП" : "▶ СТАРТ", running ? C_RED : 0xFF1E8E4E, 12, v -> {
            if (runners.containsKey(c.id)) stopMacro(c.id, true); else startMacro(c.id, false);
        }), lin(0, WRAP, 1.2f, 0, 0, dp(4), 0));
        ctl.addView(btn("🔘 В КНОПКУ", 0xFFB27800, 12, v -> createQuickFor(c.id)), lin(0, WRAP, 1.4f, 0, 0, dp(4), 0));
        ctl.addView(btn("🗑", C_CARD2, 12, v -> confirm("ОЧИСТИТЬ МАКРОС?", "Все действия будут удалены.", "ОЧИСТИТЬ", () -> {
            c.points.clear(); saveMacroConfigs(); refreshPointsView(); refreshPanel("macros");
        })), lin(0, WRAP, 0.6f, 0, 0, 0, 0));
        p.body.addView(ctl);
    }

    private View pointRow(final MacroConfig c, final int i, final Panel p) {
        final MacroPoint pt = c.points.get(i);
        int accent = pt.type == ActionType.SWIPE ? C_BLUE : pt.type == ActionType.LONG ? C_AMBER : pt.type == ActionType.WAIT ? C_MUTED : C_RED;
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), dp(6), dp(6), dp(6));
        row.setBackground(rect(C_CARD, 10, (accent & 0x00FFFFFF) | 0x66000000, 1));

        TextView badge = tv(String.valueOf(i + 1), 11, 0xFF0B0B0D, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(rect(accent, 12, 0, 0));
        row.addView(badge, lin(dp(24), dp(24), 0, 0, 0, dp(8), 0));

        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.addView(tv(pt.title(), 12, C_TEXT, true));
        txt.addView(tv(pt.sub(), 10, C_MUTED, false));
        txt.setOnClickListener(v -> editPointDialog(i));
        row.addView(txt, lin(0, WRAP, 1f, 0, 0, dp(4), 0));

        if (i > 0) row.addView(iconBtn("▲", C_TEXT, C_CARD2, 28, v -> { Collections.swap(c.points, i, i - 1); saveMacroConfigs(); refreshPanel("macros"); }), lin(dp(28), dp(28), 0, 0, 0, dp(3), 0));
        if (i < c.points.size() - 1) row.addView(iconBtn("▼", C_TEXT, C_CARD2, 28, v -> { Collections.swap(c.points, i, i + 1); saveMacroConfigs(); refreshPanel("macros"); }), lin(dp(28), dp(28), 0, 0, 0, dp(3), 0));
        if (pt.type != ActionType.WAIT) row.addView(iconBtn("📍", C_TEXT, C_CARD2, 28, v -> startCapture(pt.type, i)), lin(dp(28), dp(28), 0, 0, 0, dp(3), 0));
        row.addView(iconBtn("✕", C_RED, C_CARD2, 28, v -> { c.points.remove(i); saveMacroConfigs(); refreshPointsView(); refreshPanel("macros"); }), lin(dp(28), dp(28), 0, 0, 0, 0, 0));
        return row;
    }

    private void addWait(MacroConfig c) {
        MacroPoint pt = new MacroPoint(0, 0);
        pt.type = ActionType.WAIT;
        pt.delayMs = prefs.getLong("last_delay", 500);
        c.points.add(pt);
        saveMacroConfigs();
        refreshPanel("macros");
        editPointDialog(c.points.size() - 1);
    }

    private long parseL(EditText e, long def) {
        try { return Long.parseLong(e.getText().toString().trim()); } catch (Exception ex) { return def; }
    }

    private void editPointDialog(final int idx) {
        final MacroConfig c = allMacros.get(currentMacroId);
        if (c == null || idx >= c.points.size()) return;
        final MacroPoint pt = c.points.get(idx);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final EditText delay = styledEdit("мс", String.valueOf(pt.delayMs), InputType.TYPE_CLASS_NUMBER);
        box.addView(tv("Задержка после действия, мс", 12, C_MUTED, false));
        box.addView(delay, lin(MATCH, WRAP, 0, 0, dp(2), 0, dp(8)));
        final EditText dur = (pt.type == ActionType.SWIPE || pt.type == ActionType.LONG) ? styledEdit("мс", String.valueOf(pt.swipeDuration), InputType.TYPE_CLASS_NUMBER) : null;
        if (dur != null) {
            box.addView(tv(pt.type == ActionType.SWIPE ? "Длительность свайпа, мс" : "Время удержания, мс", 12, C_MUTED, false));
            box.addView(dur, lin(MATCH, WRAP, 0, 0, dp(2), 0, dp(8)));
        }
        final EditText ex = pt.type == ActionType.WAIT ? null : styledEdit("X", String.valueOf(pt.x), InputType.TYPE_CLASS_NUMBER);
        final EditText ey = pt.type == ActionType.WAIT ? null : styledEdit("Y", String.valueOf(pt.y), InputType.TYPE_CLASS_NUMBER);
        final EditText ex2 = pt.type == ActionType.SWIPE ? styledEdit("X2", String.valueOf(pt.endX), InputType.TYPE_CLASS_NUMBER) : null;
        final EditText ey2 = pt.type == ActionType.SWIPE ? styledEdit("Y2", String.valueOf(pt.endY), InputType.TYPE_CLASS_NUMBER) : null;
        if (ex != null) {
            box.addView(tv("Координаты X / Y", 12, C_MUTED, false));
            LinearLayout r = new LinearLayout(this);
            r.addView(ex, lin(0, WRAP, 1f, 0, dp(2), dp(6), dp(8)));
            r.addView(ey, lin(0, WRAP, 1f, 0, dp(2), 0, dp(8)));
            box.addView(r);
        }
        if (ex2 != null) {
            box.addView(tv("Конец свайпа X2 / Y2", 12, C_MUTED, false));
            LinearLayout r = new LinearLayout(this);
            r.addView(ex2, lin(0, WRAP, 1f, 0, dp(2), dp(6), 0));
            r.addView(ey2, lin(0, WRAP, 1f, 0, dp(2), 0, 0));
            box.addView(r);
        }
        showDialog("ДЕЙСТВИЕ #" + (idx + 1) + " — " + pt.title().toUpperCase(), box, "СОХРАНИТЬ", () -> {
            pt.delayMs = Math.max(10, parseL(delay, pt.delayMs));
            prefs.edit().putLong("last_delay", pt.delayMs).apply();
            if (dur != null) pt.swipeDuration = Math.max(50, parseL(dur, pt.swipeDuration));
            if (ex != null) { pt.x = (int) parseL(ex, pt.x); pt.y = (int) parseL(ey, pt.y); }
            if (ex2 != null) { pt.endX = (int) parseL(ex2, pt.endX); pt.endY = (int) parseL(ey2, pt.endY); }
            saveMacroConfigs(); refreshPointsView(); refreshPanel("macros");
            return true;
        }, "Отмена", DF_IME | DF_SCROLL);
    }

    private void macroMenu(final MacroConfig c) {
        choose("МАКРОС «" + c.name + "»", new String[]{"✏️ Переименовать", "📄 Дублировать", "🎲 Рандом и задержка старта", "🗑 Удалить макрос"}, i -> {
            if (i == 0) {
                promptText("ПЕРЕИМЕНОВАТЬ", "Название", c.name, InputType.TYPE_CLASS_TEXT, s -> {
                    if (!s.isEmpty()) c.name = s;
                    saveMacroConfigs();
                    QuickMacroButton b = quickButtons.get(c.id);
                    if (b != null) applyQuickVisual(b);
                    refreshPanel("macros");
                });
            } else if (i == 1) {
                try {
                    MacroConfig d = new MacroConfig(c.toJSON());
                    d.id = UUID.randomUUID().toString();
                    d.name = c.name + " копия";
                    d.isSavedAsButton = false;
                    allMacros.put(d.id, d); macroIds.add(d.id);
                    currentMacroId = d.id;
                    saveMacroConfigs();
                    refreshPanel("macros");
                } catch (Exception e) { toast("Не удалось дублировать"); }
            } else if (i == 2) {
                macroParamsDialog(c);
            } else {
                confirm("УДАЛИТЬ МАКРОС?", "«" + c.name + "» и его кнопка будут удалены.", "УДАЛИТЬ", () -> {
                    stopMacro(c.id, true);
                    QuickMacroButton b = quickButtons.remove(c.id);
                    if (b != null) detachQuick(b);
                    allMacros.remove(c.id); macroIds.remove(c.id);
                    if (macroIds.isEmpty()) createMacro("Макрос 1");
                    currentMacroId = macroIds.get(0);
                    saveMacroConfigs(); saveQuickButtons();
                    refreshPanel("macros");
                });
            }
        });
    }

    private void macroParamsDialog(final MacroConfig c) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(sliderRow("Разброс координат", 0, 40, c.jitter, " px", v -> c.jitter = v, v -> saveMacroConfigs()));
        box.addView(sliderRow("Разброс задержек", 0, 60, c.variation, " %", v -> c.variation = v, v -> saveMacroConfigs()));
        box.addView(sliderRow("Задержка перед стартом", 0, 5000, c.startDelay, " мс", v -> c.startDelay = v, v -> saveMacroConfigs()));
        showDialog("ПАРАМЕТРЫ МАКРОСА", box, "ГОТОВО", () -> { saveMacroConfigs(); return true; }, null, 0);
    }

    // ----- запись точек -----
    private void startCapture(final ActionType type, final int replaceIdx) {
        if (captureRoot != null || !overlayActive) return;
        final MacroConfig cfg = allMacros.get(currentMacroId);
        if (cfg == null) return;
        final int accent = type == ActionType.SWIPE ? C_BLUE : type == ActionType.LONG ? C_AMBER : C_RED;
        Panel mp = panels.get("macros");
        if (mp != null) mp.root.setVisibility(View.INVISIBLE);
        captureCount = 0;

        final FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x33000000);
        captureRoot = root;
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(8), dp(8), dp(8));
        bar.setBackground(rect(0xF0111114, 14, accent, 1.5f));
        bar.setClickable(true);
        captureInfo = tv("", 12, C_TEXT, true);
        bar.addView(captureInfo, lin(0, WRAP, 1f, 0, 0, dp(8), 0));
        bar.addView(btn(replaceIdx >= 0 ? "ОТМЕНА" : "ГОТОВО", C_RED, 12, v -> closeCapture(true)), lin(WRAP, WRAP, 0, 0, 0, 0, 0));
        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP);
        barLp.setMargins(dp(12), dp(40), dp(12), 0);
        root.addView(bar, barLp);

        final View dot = new View(this);
        GradientDrawable dg = new GradientDrawable();
        dg.setShape(GradientDrawable.OVAL);
        dg.setColor((accent & 0x00FFFFFF) | 0x88000000);
        dg.setStroke(dp(2), 0xFFFFFFFF);
        dot.setBackground(dg);
        dot.setVisibility(View.GONE);
        root.addView(dot, new FrameLayout.LayoutParams(dp(36), dp(36)));
        updateCaptureInfo(type);

        root.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy; long dt;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dx = e.getRawX(); dy = e.getRawY(); dt = SystemClock.uptimeMillis();
                        dot.setVisibility(View.VISIBLE);
                        dot.setX(e.getX() - dp(18)); dot.setY(e.getY() - dp(18));
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        dot.setX(e.getX() - dp(18)); dot.setY(e.getY() - dp(18));
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        dot.setVisibility(View.GONE);
                        return true;
                    case MotionEvent.ACTION_UP: {
                        dot.setVisibility(View.GONE);
                        int sx = (int) dx, sy = (int) dy, ex = (int) e.getRawX(), ey = (int) e.getRawY();
                        long dur = SystemClock.uptimeMillis() - dt;
                        MacroPoint np = null;
                        if (type == ActionType.SWIPE) {
                            double dist = Math.hypot(ex - sx, ey - sy);
                            if (dist > dp(24)) np = new MacroPoint(sx, sy, ex, ey, clamp((int) dur, 150, 2000));
                            else toast("Проведите пальцем от начала к концу");
                        } else if (type == ActionType.LONG) {
                            np = new MacroPoint(sx, sy);
                            np.type = ActionType.LONG;
                            np.swipeDuration = clamp((int) dur, 400, 10000);
                        } else {
                            np = new MacroPoint(ex, ey);
                        }
                        if (np == null) return true;
                        buzz();
                        if (replaceIdx >= 0 && replaceIdx < cfg.points.size()) {
                            MacroPoint old = cfg.points.get(replaceIdx);
                            old.x = np.x; old.y = np.y; old.endX = np.endX; old.endY = np.endY;
                            if (type != ActionType.CLICK) old.swipeDuration = np.swipeDuration;
                            saveMacroConfigs();
                            closeCapture(true);
                        } else {
                            np.delayMs = prefs.getLong("last_delay", 500);
                            cfg.points.add(np);
                            captureCount++;
                            saveMacroConfigs();
                            updateCaptureInfo(type);
                            if (pointsView != null) { pointsView.setPoints(cfg); pointsView.flash(cfg.points.size() - 1); }
                        }
                        return true;
                    }
                }
                return false;
            }
        });

        refreshPointsView();
        if (!addWin(root, fullLp(FL_NF))) { captureRoot = null; if (mp != null) mp.root.setVisibility(View.VISIBLE); return; }
        animateIn(bar);
    }

    private void updateCaptureInfo(ActionType t) {
        if (captureInfo == null) return;
        String s = t == ActionType.SWIPE ? "↗ СВАЙП — проведите пальцем" : t == ActionType.LONG ? "⏺ ДОЛГО — удерживайте палец" : "● КЛИК — нажимайте точки";
        captureInfo.setText(s + "\nдобавлено: " + captureCount);
    }

    private void closeCapture(boolean restorePanel) {
        if (captureRoot == null) return;
        removeWin(captureRoot);
        captureRoot = null; captureInfo = null;
        Panel mp = panels.get("macros");
        if (mp != null) mp.root.setVisibility(View.VISIBLE);
        refreshPointsView();
        if (restorePanel) refreshPanel("macros");
    }

    // ==================== Звуки ====================

    private void loadSounds() {
        soundLibrary.clear(); soundIds.clear();
        try {
            String json = prefs.getString("sound_library", "");
            if (!json.isEmpty()) {
                JSONObject o = new JSONObject(json);
                JSONArray ids = o.getJSONArray("ids");
                JSONObject data = o.getJSONObject("data");
                for (int i = 0; i < ids.length(); i++) {
                    String id = ids.getString(i);
                    if (!data.has(id)) continue;
                    SoundData s = new SoundData(data.getJSONObject(id));
                    if (s.path == null || s.path.isEmpty()) continue;
                    soundLibrary.put(id, s); soundIds.add(id);
                }
            }
        } catch (Exception e) { Log.e(TAG, "sounds", e); }
    }

    private void saveSounds() {
        try {
            JSONObject o = new JSONObject();
            JSONArray ids = new JSONArray();
            JSONObject data = new JSONObject();
            for (String id : soundIds) { ids.put(id); data.put(id, soundLibrary.get(id).toJSON()); }
            o.put("ids", ids); o.put("data", data);
            prefs.edit().putString("sound_library", o.toString()).apply();
        } catch (Exception e) { Log.e(TAG, "saveSounds", e); }
    }

    private void playSound(final String id) {
        SoundData s = soundLibrary.get(id);
        if (s == null || s.path.isEmpty() || !new File(s.path).exists()) { toast("Файл звука не найден"); return; }
        stopSound(id);
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setDataSource(s.path);
            mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            mp.setLooping(s.isLooping);
            mp.setVolume(s.volume, s.volume);
            mp.setOnCompletionListener(m -> { stopSound(id); refreshPanel("sounds"); });
            mp.prepare();
            mp.start();
            players.put(id, mp);
        } catch (Exception e) { toast("Ошибка воспроизведения"); }
        refreshPanel("sounds");
    }

    private void stopSound(String id) {
        MediaPlayer mp = players.remove(id);
        if (mp != null) {
            try { mp.stop(); } catch (Exception ignored) { }
            mp.release();
        }
    }

    private void stopAllSounds() {
        for (String id : new ArrayList<>(players.keySet())) stopSound(id);
        refreshPanel("sounds");
    }

    private void openSoundsPanel() {
        final Panel p = openPanel("sounds", "🔊", "ЗВУКИ", 340, 460);
        p.refresh = () -> buildSoundsBody(p);
        p.refresh.run();
    }

    private void buildSoundsBody(Panel p) {
        p.body.removeAllViews();
        p.body.addView(btn("＋ ДОБАВИТЬ ЗВУК", C_RED, 13, v ->
                promptText("НАЗВАНИЕ ЗВУКА", "Название", "Звук " + (soundIds.size() + 1), InputType.TYPE_CLASS_TEXT, s -> {
                    pendingSoundName = s.isEmpty() ? "Звук " + (soundIds.size() + 1) : s;
                    suppressOverlay = true;
                    try {
                        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                        i.setType("audio/*");
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        startActivityForResult(Intent.createChooser(i, "Выберите аудио"), RC_AUDIO);
                    } catch (Exception e) { toast("Не удалось открыть выбор файла"); }
                })), lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        p.body.addView(sv, lin(MATCH, 0, 1f, 0, 0, 0, 0));
        if (soundIds.isEmpty()) {
            TextView e = tv("Звуков нет.\nДобавьте аудиофайл — он сохранится в приложении.", 13, C_MUTED, false);
            e.setGravity(Gravity.CENTER);
            e.setPadding(0, dp(30), 0, dp(30));
            list.addView(e);
        }
        for (final String id : new ArrayList<>(soundIds)) {
            final SoundData s = soundLibrary.get(id);
            if (s == null) continue;
            final boolean playing = players.containsKey(id);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(8), dp(8), dp(8), dp(4));
            card.setBackground(rect(C_CARD, 12, playing ? C_GREEN : C_STROKE, 1));
            LinearLayout top = new LinearLayout(this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(iconBtn(playing ? "■" : "▶", C_TEXT, playing ? C_RED : 0xFF1E8E4E, 38, v -> { if (playing) { stopSound(id); refreshPanel("sounds"); } else playSound(id); }), lin(dp(38), dp(38), 0, 0, 0, dp(8), 0));
            LinearLayout nm = new LinearLayout(this);
            nm.setOrientation(LinearLayout.VERTICAL);
            nm.addView(tv(s.name, 13, C_TEXT, true));
            nm.addView(tv(new File(s.path).getName(), 10, C_MUTED, false));
            top.addView(nm, lin(0, WRAP, 1f, 0, 0, dp(4), 0));
            top.addView(iconBtn("🔁", C_TEXT, s.isLooping ? C_GREEN : C_CARD2, 32, v -> {
                s.isLooping = !s.isLooping;
                MediaPlayer mp = players.get(id);
                if (mp != null) mp.setLooping(s.isLooping);
                saveSounds(); refreshPanel("sounds");
            }), lin(dp(32), dp(32), 0, 0, 0, dp(4), 0));
            top.addView(iconBtn("✕", C_RED, C_CARD2, 32, v -> confirm("УДАЛИТЬ ЗВУК?", "«" + s.name + "» будет удалён.", "УДАЛИТЬ", () -> {
                stopSound(id);
                try { new File(s.path).delete(); } catch (Exception ignored) { }
                soundIds.remove(id); soundLibrary.remove(id);
                saveSounds(); refreshPanel("sounds");
            })), lin(dp(32), dp(32), 0, 0, 0, 0, 0));
            card.addView(top);
            card.addView(sliderRow("Громкость", 0, 100, Math.round(s.volume * 100), "%", v -> {
                s.volume = v / 100f;
                MediaPlayer mp = players.get(id);
                if (mp != null) mp.setVolume(s.volume, s.volume);
            }, v -> saveSounds()));
            list.addView(card, lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        }
    }

    // ==================== Персонажи ====================

    private void loadCharacters() {
        characters.clear();
        try {
            String json = prefs.getString("characters_list", "");
            if (!json.isEmpty()) {
                JSONArray a = new JSONArray(json);
                for (int i = 0; i < a.length(); i++) characters.add(new CharacterData(a.getJSONObject(i)));
            }
        } catch (Exception e) { Log.e(TAG, "chars", e); }
    }

    private void saveCharacters() {
        try {
            JSONArray a = new JSONArray();
            for (CharacterData c : characters) a.put(c.toJSON());
            prefs.edit().putString("characters_list", a.toString()).apply();
        } catch (Exception e) { Log.e(TAG, "saveChars", e); }
    }

    private void openCharsPanel() {
        final Panel p = openPanel("chars", "👤", "ПЕРСОНАЖИ", 330, 440);
        p.refresh = () -> buildCharsBody(p);
        p.refresh.run();
    }

    private void buildCharsBody(Panel p) {
        p.body.removeAllViews();
        p.body.addView(btn("＋ ДОБАВИТЬ ПЕРСОНАЖА", C_RED, 13, v ->
                promptText("ИМЯ ПЕРСОНАЖА", "Имя", "Персонаж " + (characters.size() + 1), InputType.TYPE_CLASS_TEXT, s -> {
                    tempCharacterName = s.isEmpty() ? "Персонаж " + (characters.size() + 1) : s;
                    suppressOverlay = true;
                    try { startActivityForResult(new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI), RC_GALLERY); }
                    catch (Exception e) { toast("Не удалось открыть галерею"); }
                })), lin(MATCH, WRAP, 0, 0, 0, 0, dp(8)));
        TextView hint = tv("Нажмите 👁, чтобы вывести картинку поверх экрана (перетаскивайте, тап — размер, ✕ — убрать).", 11, C_MUTED, false);
        p.body.addView(hint, lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        p.body.addView(sv, lin(MATCH, 0, 1f, 0, 0, 0, 0));
        if (characters.isEmpty()) {
            TextView e = tv("Персонажей нет", 13, C_MUTED, false);
            e.setGravity(Gravity.CENTER);
            e.setPadding(0, dp(30), 0, dp(30));
            list.addView(e);
        }
        for (final CharacterData d : new ArrayList<>(characters)) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(8), dp(8), dp(8));
            row.setBackground(rect(C_CARD, 12, C_STROKE, 1));
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Bitmap bm = decodeFile(d.path, 96);
            if (bm != null) iv.setImageBitmap(bm);
            iv.setBackground(rect(C_CARD2, 10, 0, 0));
            iv.setClipToOutline(true);
            row.addView(iv, lin(dp(46), dp(46), 0, 0, 0, dp(10), 0));
            row.addView(tv(d.name, 14, C_TEXT, true), lin(0, WRAP, 1f, 0, 0, dp(4), 0));
            row.addView(iconBtn("👁", C_TEXT, charViews.containsKey(d.path) ? C_GREEN : C_CARD2, 34, v -> { toggleCharOverlay(d); refreshPanel("chars"); }), lin(dp(34), dp(34), 0, 0, 0, dp(4), 0));
            row.addView(iconBtn("✕", C_RED, C_CARD2, 34, v -> confirm("УДАЛИТЬ ПЕРСОНАЖА?", "«" + d.name + "»", "УДАЛИТЬ", () -> {
                View ov = charViews.remove(d.path);
                removeWin(ov);
                try { new File(d.path).delete(); } catch (Exception ignored) { }
                characters.remove(d);
                saveCharacters(); refreshPanel("chars");
            })), lin(dp(34), dp(34), 0, 0, 0, 0, 0));
            list.addView(row, lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        }
    }

    private void toggleCharOverlay(final CharacterData d) {
        View ex = charViews.remove(d.path);
        if (ex != null) { removeWin(ex); return; }
        Bitmap bm = decodeFile(d.path, 640);
        if (bm == null) { toast("Не удалось загрузить картинку"); return; }
        final FrameLayout box = new FrameLayout(this);
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(bm);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        box.addView(iv, new FrameLayout.LayoutParams(MATCH, MATCH));
        TextView x = tv("✕", 12, C_TEXT, true);
        x.setGravity(Gravity.CENTER);
        x.setBackground(rect(0xCC000000, 12, 0, 0));
        box.addView(x, new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.TOP | Gravity.END));
        x.setOnClickListener(v -> { removeWin(charViews.remove(d.path)); refreshPanel("chars"); });
        final int[] sizes = {140, 220, 320};
        final int[] si = {0};
        final WindowManager.LayoutParams lp = baseLp(dp(sizes[0]), dp(sizes[0]), FL_NF);
        lp.x = dp(40); lp.y = dp(160);
        box.setOnTouchListener(new View.OnTouchListener() {
            float tx, ty; int ox, oy; boolean drag;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: tx = e.getRawX(); ty = e.getRawY(); ox = lp.x; oy = lp.y; drag = false; return true;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(e.getRawX() - tx) > dp(8) || Math.abs(e.getRawY() - ty) > dp(8)) drag = true;
                        if (drag) { lp.x = ox + (int) (e.getRawX() - tx); lp.y = oy + (int) (e.getRawY() - ty); updWin(box, lp); }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!drag) {
                            si[0] = (si[0] + 1) % sizes.length;
                            lp.width = dp(sizes[si[0]]); lp.height = dp(sizes[si[0]]);
                            updWin(box, lp);
                        }
                        return true;
                }
                return false;
            }
        });
        if (addWin(box, lp)) { charViews.put(d.path, box); animateIn(box); }
    }

    // ==================== Настройки / конфиги ====================

    private void openSettingsPanel() {
        final Panel p = openPanel("settings", "⚙️", "НАСТРОЙКИ", 340, 520);
        p.refresh = () -> buildSettingsBody(p);
        p.refresh.run();
    }

    private void buildSettingsBody(final Panel p) {
        p.body.removeAllViews();
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col);
        p.body.addView(sv, lin(MATCH, MATCH, 0, 0, 0, 0, 0));

        col.addView(sectionLabel("ОВЕРЛЕЙ"));
        col.addView(sliderRow("Размер геймпада", 36, 96, prefs.getInt("bubble_dp", 56), " dp", null, v -> { prefs.edit().putInt("bubble_dp", v).apply(); rebuildBubble(); }));
        col.addView(sliderRow("Прозрачность", 30, 100, prefs.getInt("bubble_alpha", 100), "%", v -> {
            prefs.edit().putInt("bubble_alpha", v).apply();
            if (bubbleRoot != null) bubbleRoot.setAlpha(v / 100f);
        }, null));
        String[] km = {"никогда", "пока работает макрос", "всегда в оверлее"};
        col.addView(btn("🔆 Не гасить экран: " + km[prefs.getInt("keep_mode", 1)], C_CARD2, 12, v ->
                choose("НЕ ГАСИТЬ ЭКРАН", km, i -> { prefs.edit().putInt("keep_mode", i).apply(); applyKeepScreen(); refreshPanel("settings"); })), lin(MATCH, WRAP, 0, 0, dp(4), 0, dp(4)));
        col.addView(toggleRow("Показывать точки при работе макроса", "show_points", true, this::refreshPointsView));
        col.addView(toggleRow("Вибрация", "haptic", true, null));

        col.addView(sectionLabel("КОНФИГ"));
        col.addView(btn("💾 Экспорт в файл", C_CARD2, 12, v -> exportConfig()), lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        col.addView(btn("📂 Импорт конфига", C_CARD2, 12, v -> importDialog()), lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));
        col.addView(btn("📋 Скопировать конфиг (JSON)", C_CARD2, 12, v -> {
            try {
                ClipboardManager cm = (ClipboardManager) appCtx.getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("arcade_config", buildConfigJson()));
                toast("Конфиг скопирован в буфер");
            } catch (Exception e) { toast("Ошибка копирования"); }
        }), lin(MATCH, WRAP, 0, 0, 0, 0, dp(6)));

        col.addView(sectionLabel("ИНФО"));
        col.addView(tv("Макросов: " + allMacros.size() + "  •  Кнопок: " + quickButtons.size() + "  •  Звуков: " + soundIds.size() + "  •  Персонажей: " + characters.size(), 12, C_MUTED, false));
        col.addView(sectionLabel("ПЛАТФОРМА"));
        col.addView(btn("⏻  ВЫКЛЮЧИТЬ ПЛАТФОРМУ", 0xFF5A1518, 13, v -> confirmClose()), lin(MATCH, WRAP, 0, 0, 0, 0, dp(10)));
    }

    private File appDir(String sub) {
        File base = getExternalFilesDir(null);
        if (base == null) base = getFilesDir();
        File d = new File(base, sub);
        d.mkdirs();
        return d;
    }

    private String buildConfigJson() throws Exception {
        JSONObject cfg = new JSONObject();
        cfg.put("version", 2);
        JSONObject mo = new JSONObject();
        JSONArray ids = new JSONArray();
        JSONObject data = new JSONObject();
        for (String id : macroIds) { ids.put(id); data.put(id, allMacros.get(id).toJSON()); }
        mo.put("ids", ids); mo.put("data", data);
        cfg.put("all_macros", mo);
        JSONArray qb = new JSONArray();
        for (QuickMacroButton b : quickButtons.values()) qb.put(b.toJSON());
        cfg.put("quick_buttons", qb);
        JSONObject so = new JSONObject();
        JSONArray sids = new JSONArray();
        JSONObject sdata = new JSONObject();
        for (String id : soundIds) { sids.put(id); sdata.put(id, soundLibrary.get(id).toJSON()); }
        so.put("ids", sids); so.put("data", sdata);
        cfg.put("sound_library", so);
        JSONObject st = new JSONObject();
        st.put("bubble_dp", prefs.getInt("bubble_dp", 56));
        st.put("bubble_alpha", prefs.getInt("bubble_alpha", 100));
        st.put("keep_mode", prefs.getInt("keep_mode", 1));
        cfg.put("settings", st);
        cfg.put("quick_button_id_counter", quickButtonIdCounter);
        return cfg.toString(2);
    }

    private void exportConfig() {
        try {
            String js = buildConfigJson();
            String name = "arcade_config_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".json";
            FileOutputStream out = new FileOutputStream(new File(appDir("configs"), name));
            out.write(js.getBytes(StandardCharsets.UTF_8));
            out.close();
            String pub = saveToDownloads(name, js);
            toast(pub != null ? "Сохранено: " + pub : "Сохранено в памяти приложения: " + name);
        } catch (Exception e) {
            Log.e(TAG, "export", e);
            toast("Ошибка экспорта");
        }
    }

    private String saveToDownloads(String name, String content) {
        if (Build.VERSION.SDK_INT < 29) return null;
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ArcadeConfigs");
            Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (u == null) return null;
            OutputStream os = getContentResolver().openOutputStream(u);
            if (os == null) return null;
            os.write(content.getBytes(StandardCharsets.UTF_8));
            os.close();
            return "Download/ArcadeConfigs/" + name;
        } catch (Exception e) { return null; }
    }

    private ArrayList<File> listConfigs() {
        ArrayList<File> res = new ArrayList<>();
        File[] fs = appDir("configs").listFiles((d, n) -> n.endsWith(".json"));
        if (fs != null) {
            Arrays.sort(fs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (int i = 0; i < fs.length && i < 8; i++) res.add(fs[i]);
        }
        return res;
    }

    private String readFile(File f) {
        try {
            return readStream(new FileInputStream(f));
        } catch (Exception e) { return null; }
    }

    private String readUri(Uri uri) {
        try { return readStream(getContentResolver().openInputStream(uri)); } catch (Exception e) { return null; }
    }

    private String readStream(InputStream is) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        r.close();
        return sb.toString();
    }

    private void importDialog() {
        final ArrayList<File> files = listConfigs();
        ArrayList<String> items = new ArrayList<>();
        for (File f : files) items.add("📄 " + f.getName());
        items.add("📋 Вставить JSON из текста");
        items.add("📂 Выбрать файл…");
        choose("ИМПОРТ КОНФИГА", items.toArray(new String[0]), i -> {
            if (i < files.size()) {
                String js = readFile(files.get(i));
                if (js == null) toast("Не удалось прочитать файл"); else askImportMode(js);
            } else if (i == files.size()) {
                final EditText et = styledEdit("Вставьте JSON сюда", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                et.setMinLines(5);
                et.setGravity(Gravity.TOP);
                showDialog("ВСТАВИТЬ КОНФИГ", et, "ДАЛЕЕ", () -> {
                    String js = et.getText().toString().trim();
                    if (js.isEmpty()) { toast("Пусто"); return false; }
                    ui.postDelayed(() -> askImportMode(js), 150);
                    return true;
                }, "Отмена", DF_IME);
            } else {
                suppressOverlay = true;
                try {
                    Intent it = new Intent(Intent.ACTION_GET_CONTENT);
                    it.setType("*/*");
                    it.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(Intent.createChooser(it, "Выберите файл конфига"), RC_IMPORT);
                } catch (Exception e) { toast("Не удалось открыть выбор файла"); }
            }
        });
    }

    private void askImportMode(final String json) {
        choose("КАК ИМПОРТИРОВАТЬ?", new String[]{"Заменить всё", "Добавить к существующим"}, i -> importConfig(json, i == 0));
    }

    private boolean importConfig(String json, boolean replace) {
        try {
            JSONObject cfg = new JSONObject(json.trim());
            stopAllMacros();
            HashMap<String, String> idMap = new HashMap<>();
            if (cfg.has("all_macros")) {
                JSONObject mo = cfg.getJSONObject("all_macros");
                JSONArray ids = mo.getJSONArray("ids");
                JSONObject data = mo.getJSONObject("data");
                if (replace) {
                    for (QuickMacroButton b : quickButtons.values()) detachQuick(b);
                    quickButtons.clear(); allMacros.clear(); macroIds.clear();
                }
                for (int i = 0; i < ids.length(); i++) {
                    String id = ids.getString(i);
                    if (!data.has(id)) continue;
                    MacroConfig m = new MacroConfig(data.getJSONObject(id));
                    String nid = id;
                    if (!replace || allMacros.containsKey(id)) { nid = UUID.randomUUID().toString(); m.id = nid; }
                    m.isSavedAsButton = false;
                    idMap.put(id, nid);
                    allMacros.put(nid, m); macroIds.add(nid);
                }
            }
            if (cfg.has("quick_buttons")) {
                JSONArray qb = cfg.getJSONArray("quick_buttons");
                for (int i = 0; i < qb.length(); i++) {
                    QuickMacroButton b = new QuickMacroButton(qb.getJSONObject(i));
                    String nid = idMap.containsKey(b.macroId) ? idMap.get(b.macroId) : b.macroId;
                    MacroConfig m = allMacros.get(nid);
                    if (m == null || quickButtons.containsKey(nid)) continue;
                    b.macroId = nid;
                    b.id = quickButtonIdCounter++;
                    if (b.text == null || b.text.isEmpty()) b.text = String.valueOf(b.id);
                    m.isSavedAsButton = true;
                    quickButtons.put(nid, b);
                }
            }
            if (cfg.has("sound_library")) {
                JSONObject so = cfg.getJSONObject("sound_library");
                JSONArray ids = so.getJSONArray("ids");
                JSONObject data = so.getJSONObject("data");
                if (replace) { stopAllSounds(); soundLibrary.clear(); soundIds.clear(); }
                for (int i = 0; i < ids.length(); i++) {
                    String id = ids.getString(i);
                    if (!data.has(id)) continue;
                    SoundData s = new SoundData(data.getJSONObject(id));
                    if (s.path.isEmpty() || !new File(s.path).exists() || soundLibrary.containsKey(s.id)) continue;
                    soundLibrary.put(s.id, s); soundIds.add(s.id);
                }
            }
            if (replace && cfg.has("settings")) {
                JSONObject st = cfg.getJSONObject("settings");
                SharedPreferences.Editor ed = prefs.edit();
                if (st.has("bubble_dp")) ed.putInt("bubble_dp", st.getInt("bubble_dp"));
                if (st.has("bubble_alpha")) ed.putInt("bubble_alpha", st.getInt("bubble_alpha"));
                if (st.has("keep_mode")) ed.putInt("keep_mode", st.getInt("keep_mode"));
                ed.apply();
            }
            if (macroIds.isEmpty()) createMacro("Макрос 1");
            if (!allMacros.containsKey(currentMacroId)) currentMacroId = macroIds.get(0);
            saveMacroConfigs(); saveQuickButtons(); saveSounds();
            for (QuickMacroButton b : quickButtons.values()) attachQuick(b);
            if (overlayActive) rebuildBubble();
            refreshPanel("macros"); refreshPanel("sounds"); refreshPanel("settings");
            toast("✅ Конфиг импортирован");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "import", e);
            toast("Ошибка импорта: неверный конфиг");
            return false;
        }
    }

    // ==================== Файлы / картинки ====================

    private Bitmap decodeUri(Uri uri, int max) throws IOException {
        InputStream in = getContentResolver().openInputStream(uri);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(in, null, o);
        if (in != null) in.close();
        int s = 1;
        while (o.outWidth / s > max * 2 || o.outHeight / s > max * 2) s *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = s;
        in = getContentResolver().openInputStream(uri);
        Bitmap b = BitmapFactory.decodeStream(in, null, o2);
        if (in != null) in.close();
        return b;
    }

    private Bitmap decodeFile(String path, int max) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            int s = 1;
            while (o.outWidth / s > max * 2 || o.outHeight / s > max * 2) s *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = s;
            return BitmapFactory.decodeFile(path, o2);
        } catch (Exception e) { return null; }
    }

    private String saveBitmap(Bitmap bmp) {
        try {
            File f = new File(appDir("characters"), "CHAR_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".png");
            FileOutputStream out = new FileOutputStream(f);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.close();
            return f.getAbsolutePath();
        } catch (IOException e) { return null; }
    }

    private String saveAudio(Uri uri) {
        try {
            File f = new File(appDir("sounds"), "SOUND_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".mp3");
            InputStream in = getContentResolver().openInputStream(uri);
            FileOutputStream out = new FileOutputStream(f);
            byte[] buf = new byte[8192];
            int n;
            while (in != null && (n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.close();
            if (in != null) in.close();
            return f.getAbsolutePath();
        } catch (Exception e) { return null; }
    }
                                                       }
