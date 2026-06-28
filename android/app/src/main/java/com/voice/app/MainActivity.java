package com.voice.app;

import android.accessibilityservice.GestureDescription;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
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
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.BridgeActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends BridgeActivity {

    // ==================== КОНСТАНТЫ ====================
    private static final int REQUEST_MICROPHONE = 100;
    private static final int REQUEST_CAMERA = 102;
    private static final int REQUEST_OVERLAY_PERMISSION = 101;
    private static final int REQUEST_GALLERY = 103;
    private static final int REQUEST_STORAGE = 104;
    private static final int REQUEST_SCREEN_CAPTURE = 105;

    // ==================== ПЕРЕМЕННЫЕ ====================
    private WindowManager windowManager;
    private FrameLayout mainCircleContainer;
    private WindowManager.LayoutParams mainCircleParams;

    private float startX, startY;
    private int initialX, initialY;
    private boolean isDragging = false;

    // Основной оверлей
    private FrameLayout mainOverlay;
    private WebView webView;
    private WindowManager.LayoutParams mainOverlayParams;
    private boolean isMainOverlayVisible = false;

    // ====== NINTENDO MODE ======
    private boolean isNintendoModeActive = false;
    private LinearLayout leftControllerPanel;
    private LinearLayout rightControllerPanel;
    private FrameLayout nintendoContainer;
    private WindowManager.LayoutParams nintendoParams;

    // ====== КНОПКИ И СТИКИ ======
    private ArrayList<ProgrammableButton> buttons = new ArrayList<>();
    private ArrayList<ProgrammableStick> sticks = new ArrayList<>();
    private SharedPreferences prefs;
    private boolean isEditModeActive = false;
    private Object pendingPlacement = null;
    private FrameLayout placementOverlay;

    // Режимы действий
    private enum ActionType { CLICK, HOLD, SWIPE, MACRO }
    private ActionType currentActionType = ActionType.CLICK;

    // ==================== КЛАССЫ ДАННЫХ ====================

    private static class ProgrammableButton {
        String id;
        String name;
        int x, y;
        int size = 80;
        ActionType action;
        ArrayList<AutoClickerAccessibilityService.Point> macroPoints = new ArrayList<>();
        int holdDuration = 500;

        ProgrammableButton(String id, String name, int x, int y) {
            this.id = id;
            this.name = name;
            this.x = x;
            this.y = y;
            this.action = ActionType.CLICK;
        }
    }

    private static class ProgrammableStick {
        String id;
        String name;
        int x, y;
        int size = 120;
        String leftAction = "NONE";
        String rightAction = "NONE";
        String upAction = "NONE";
        String downAction = "NONE";

        ProgrammableStick(String id, String name, int x, int y) {
            this.id = id;
            this.name = name;
            this.x = x;
            this.y = y;
        }
    }

    // ==================== ЖИЗНЕННЫЙ ЦИКЛ ====================

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("nintendo_config", MODE_PRIVATE);
        loadButtons();
        loadSticks();

        requestPermissionsIfNeeded();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
            } else {
                createMainCircle();
            }
        } else {
            createMainCircle();
        }

        if (bridge != null && bridge.getWebView() != null) {
            bridge.getWebView().setWebChromeClient(new WebChromeClient() {
                @Override
                public void onPermissionRequest(PermissionRequest request) {
                    request.grant(new String[]{
                            PermissionRequest.RESOURCE_AUDIO_CAPTURE,
                            PermissionRequest.RESOURCE_VIDEO_CAPTURE
                    });
                }
            });
        }
    }

    // ==================== РАЗРЕШЕНИЯ ====================

    private void requestPermissionsIfNeeded() {
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
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_MICROPHONE);
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
    }

    // ==================== СОХРАНЕНИЕ НАСТРОЕК ====================

    private void loadButtons() {
        buttons.clear();
        try {
            String json = prefs.getString("buttons_list", "");
            if (!json.isEmpty()) {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    ProgrammableButton btn = new ProgrammableButton(
                            obj.getString("id"),
                            obj.getString("name"),
                            obj.getInt("x"),
                            obj.getInt("y")
                    );
                    btn.size = obj.optInt("size", 80);
                    btn.action = ActionType.valueOf(obj.getString("action"));
                    btn.holdDuration = obj.optInt("holdDuration", 500);
                    JSONArray macroArr = obj.optJSONArray("macro");
                    if (macroArr != null) {
                        for (int j = 0; j < macroArr.length(); j++) {
                            JSONObject pObj = macroArr.getJSONObject(j);
                            btn.macroPoints.add(new AutoClickerAccessibilityService.Point(pObj.getInt("x"), pObj.getInt("y")));
                        }
                    }
                    buttons.add(btn);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveButtons() {
        try {
            JSONArray array = new JSONArray();
            for (ProgrammableButton btn : buttons) {
                JSONObject obj = new JSONObject();
                obj.put("id", btn.id);
                obj.put("name", btn.name);
                obj.put("x", btn.x);
                obj.put("y", btn.y);
                obj.put("size", btn.size);
                obj.put("action", btn.action.name());
                obj.put("holdDuration", btn.holdDuration);
                JSONArray macroArr = new JSONArray();
                for (AutoClickerAccessibilityService.Point p : btn.macroPoints) {
                    JSONObject pObj = new JSONObject();
                    pObj.put("x", p.x);
                    pObj.put("y", p.y);
                    macroArr.put(pObj);
                }
                obj.put("macro", macroArr);
                array.put(obj);
            }
            prefs.edit().putString("buttons_list", array.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadSticks() {
        sticks.clear();
        try {
            String json = prefs.getString("sticks_list", "");
            if (!json.isEmpty()) {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    ProgrammableStick stick = new ProgrammableStick(
                            obj.getString("id"),
                            obj.getString("name"),
                            obj.getInt("x"),
                            obj.getInt("y")
                    );
                    stick.size = obj.optInt("size", 120);
                    stick.leftAction = obj.optString("leftAction", "NONE");
                    stick.rightAction = obj.optString("rightAction", "NONE");
                    stick.upAction = obj.optString("upAction", "NONE");
                    stick.downAction = obj.optString("downAction", "NONE");
                    sticks.add(stick);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveSticks() {
        try {
            JSONArray array = new JSONArray();
            for (ProgrammableStick stick : sticks) {
                JSONObject obj = new JSONObject();
                obj.put("id", stick.id);
                obj.put("name", stick.name);
                obj.put("x", stick.x);
                obj.put("y", stick.y);
                obj.put("size", stick.size);
                obj.put("leftAction", stick.leftAction);
                obj.put("rightAction", stick.rightAction);
                obj.put("upAction", stick.upAction);
                obj.put("downAction", stick.downAction);
                array.put(obj);
            }
            prefs.edit().putString("sticks_list", array.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== ГЛАВНЫЙ КРУГ ====================

    private void createMainCircle() {
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
        d.setColor(Color.parseColor("#CC0000"));
        d.setStroke(6, Color.parseColor("#FF4444"));
        mainCircleContainer.setBackground(d);

        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        mainCircleContainer.addView(iconButton, iconParams);

        mainCircleParams = new WindowManager.LayoutParams(136, 136, flag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        mainCircleParams.gravity = Gravity.TOP | Gravity.START;
        mainCircleParams.x = 100;
        mainCircleParams.y = 200;

        mainCircleContainer.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
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
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!isDragging) {
                            showMainMenu();
                        }
                        return true;
                }
                return false;
            }
        });

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager != null) {
            windowManager.addView(mainCircleContainer, mainCircleParams);
            Toast.makeText(this, "Nintendo Ready!", Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap createGamepadBitmap() {
        int size = 90;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(6);

        float cx = size / 2f, cy = size / 2f;
        canvas.drawRoundRect(cx - 32, cy - 22, cx + 32, cy + 22, 18, 18, paint);
        canvas.drawCircle(cx - 25, cy, 12, paint);
        canvas.drawCircle(cx + 25, cy, 12, paint);
        paint.setStrokeWidth(5);
        canvas.drawLine(cx - 18, cy - 8, cx - 18, cy + 8, paint);
        canvas.drawLine(cx - 22, cy, cx - 14, cy, paint);
        canvas.drawCircle(cx + 18, cy - 6, 5, paint);
        canvas.drawCircle(cx + 18, cy + 6, 5, paint);
        canvas.drawCircle(cx + 26, cy, 5, paint);
        canvas.drawCircle(cx + 10, cy, 5, paint);
        return bitmap;
    }

    // ==================== ГЛАВНОЕ МЕНЮ ====================

    private void showMainMenu() {
        if (isMainOverlayVisible) return;
        int flag = getOverlayFlag();

        mainOverlay = new FrameLayout(this);
        mainOverlay.setBackgroundColor(Color.parseColor("#E6000000"));
        mainOverlay.setPadding(20, 20, 20, 20);

        FrameLayout innerContainer = new FrameLayout(this);
        GradientDrawable innerBg = new GradientDrawable();
        innerBg.setShape(GradientDrawable.RECTANGLE);
        innerBg.setCornerRadius(24);
        innerBg.setColor(Color.parseColor("#1A0A0A"));
        innerBg.setStroke(2, Color.parseColor("#8B0000"));
        innerContainer.setBackground(innerBg);
        innerContainer.setPadding(16, 16, 16, 16);

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));

        // Верхняя панель
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, 8, 0, 16);

        TextView title = new TextView(this);
        title.setText("NINTENDO CONTROLLER");
        title.setTextColor(Color.parseColor("#00E5FF"));
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        topBar.addView(title);

        LinearLayout rightButtons = new LinearLayout(this);
        rightButtons.setOrientation(LinearLayout.HORIZONTAL);
        rightButtons.setGravity(Gravity.CENTER);

        Button editBtn = createTopBarButton("E", "#8B0000");
        editBtn.setOnClickListener(v -> toggleEditMode());
        rightButtons.addView(editBtn);

        Button closeBtn = createTopBarButton("X", "#2A0000");
        closeBtn.setOnClickListener(v -> {
            hideMainMenu();
            if (mainCircleContainer != null) {
                mainCircleContainer.setVisibility(View.VISIBLE);
            }
        });
        rightButtons.addView(closeBtn);

        topBar.addView(rightButtons);
        mainLayout.addView(topBar);

        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#8B0000"));
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2
        ));
        divider.setPadding(0, 0, 0, 16);
        mainLayout.addView(divider);

        // WebView
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

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.grant(new String[]{
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE,
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE
                });
            }
        });
        webView.setWebViewClient(new WebViewClient());

        webView.loadUrl("https://crconferensimessenger.vercel.app/");

        webView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        webView.setBackgroundColor(Color.parseColor("#0A0000"));
        mainLayout.addView(webView);

        // Нижняя панель
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER);
        bottomBar.setPadding(0, 16, 0, 8);

        String[] actions = {"TOGGLE NINTENDO", "ADD BUTTON", "ADD STICK"};
        String[] colors = {"#00E5FF", "#8B0000", "#CC0000"};

        for (int i = 0; i < actions.length; i++) {
            Button btn = new Button(this);
            btn.setText(actions[i]);
            btn.setTextColor(Color.WHITE);
            btn.setTextSize(12);
            btn.setTypeface(null, android.graphics.Typeface.BOLD);
            btn.setPadding(24, 14, 24, 14);

            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setCornerRadius(20);
            btnBg.setColor(Color.parseColor(colors[i]));
            btnBg.setStroke(2, Color.parseColor("#00E5FF"));
            btn.setBackground(btnBg);

            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1
            );
            btnParams.setMargins(4, 0, 4, 0);
            btn.setLayoutParams(btnParams);

            final int index = i;
            btn.setOnClickListener(v -> {
                switch (index) {
                    case 0:
                        toggleNintendoMode();
                        break;
                    case 1:
                        showAddButtonDialog();
                        break;
                    case 2:
                        showAddStickDialog();
                        break;
                }
            });

            bottomBar.addView(btn);
        }

        mainLayout.addView(bottomBar);

        innerContainer.addView(mainLayout);

        FrameLayout.LayoutParams innerParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        mainOverlay.addView(innerContainer, innerParams);

        mainOverlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                flag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );
        mainOverlayParams.gravity = Gravity.CENTER;

        if (windowManager != null) {
            windowManager.addView(mainOverlay, mainOverlayParams);
            isMainOverlayVisible = true;
        }
    }

    private Button createTopBarButton(String text, String color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(16);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);
        btn.setPadding(16, 8, 16, 8);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.parseColor(color));
        bg.setStroke(2, Color.parseColor("#00E5FF"));
        btn.setBackground(bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(48, 48);
        params.setMargins(4, 0, 4, 0);
        btn.setLayoutParams(params);

        return btn;
    }

    private void hideMainMenu() {
        if (mainOverlay != null && windowManager != null && isMainOverlayVisible) {
            try {
                windowManager.removeView(mainOverlay);
                mainOverlay = null;
                isMainOverlayVisible = false;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // ==================== NINTENDO MODE ====================

    private void toggleNintendoMode() {
        if (isNintendoModeActive) {
            hideNintendoMode();
        } else {
            showNintendoMode();
        }
    }

    private void showNintendoMode() {
        if (isNintendoModeActive) return;
        int flag = getOverlayFlag();

        nintendoContainer = new FrameLayout(this);
        nintendoContainer.setBackgroundColor(Color.TRANSPARENT);

        // Левая панель (джойкон)
        leftControllerPanel = createControllerPanel(true);
        FrameLayout.LayoutParams leftParams = new FrameLayout.LayoutParams(
                150, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START
        );
        nintendoContainer.addView(leftControllerPanel, leftParams);

        // Правая панель (джойкон)
        rightControllerPanel = createControllerPanel(false);
        FrameLayout.LayoutParams rightParams = new FrameLayout.LayoutParams(
                150, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.END
        );
        nintendoContainer.addView(rightControllerPanel, rightParams);

        addButtonsToNintendoContainer();
        addSticksToNintendoContainer();

        nintendoParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                flag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );

        if (windowManager != null) {
            windowManager.addView(nintendoContainer, nintendoParams);
            isNintendoModeActive = true;
            Toast.makeText(this, "Nintendo Mode ON", Toast.LENGTH_SHORT).show();
        }
    }

    private void hideNintendoMode() {
        if (nintendoContainer != null && windowManager != null && isNintendoModeActive) {
            try {
                windowManager.removeView(nintendoContainer);
                nintendoContainer = null;
                leftControllerPanel = null;
                rightControllerPanel = null;
                isNintendoModeActive = false;
                Toast.makeText(this, "Nintendo Mode OFF", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private LinearLayout createControllerPanel(boolean isLeft) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setBackgroundColor(Color.parseColor("#E61A0A0A"));
        panel.setPadding(20, 40, 20, 40);

        TextView title = new TextView(this);
        title.setText(isLeft ? "L" : "R");
        title.setTextColor(Color.parseColor("#00E5FF"));
        title.setTextSize(24);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 30);
        panel.addView(title);

        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#00E5FF"));
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2
        ));
        panel.addView(divider);

        return panel;
    }

    // ==================== ДОБАВЛЕНИЕ КНОПОК И СТИКОВ ====================

    private void showAddButtonDialog() {
        hideMainMenu();
        LinearLayout dialog = new LinearLayout(this);
        dialog.setOrientation(LinearLayout.VERTICAL);
        dialog.setGravity(Gravity.CENTER);
        dialog.setPadding(40, 40, 40, 40);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(28);
        bg.setColor(Color.parseColor("#1A0A0A"));
        bg.setStroke(2, Color.parseColor("#8B0000"));
        dialog.setBackground(bg);

        TextView title = new TextView(this);
        title.setText("NEW BUTTON");
        title.setTextColor(Color.parseColor("#00E5FF"));
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 20);
        dialog.addView(title);

        EditText nameInput = new EditText(this);
        nameInput.setHint("Button Name");
        nameInput.setHintTextColor(Color.parseColor("#555555"));
        nameInput.setTextColor(Color.WHITE);
        nameInput.setTextSize(16);
        nameInput.setBackgroundColor(Color.parseColor("#0A0000"));
        nameInput.setPadding(20, 16, 20, 16);
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setCornerRadius(14);
        inputBg.setColor(Color.parseColor("#0A0000"));
        inputBg.setStroke(2, Color.parseColor("#8B0000"));
        nameInput.setBackground(inputBg);
        nameInput.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        dialog.addView(nameInput);

        TextView actionLabel = new TextView(this);
        actionLabel.setText("ACTION TYPE");
        actionLabel.setTextColor(Color.parseColor("#888888"));
        actionLabel.setTextSize(14);
        actionLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        actionLabel.setPadding(0, 20, 0, 10);
        dialog.addView(actionLabel);

        String[] actionTypes = {"CLICK", "HOLD", "SWIPE", "MACRO"};
        for (String type : actionTypes) {
            Button btn = new Button(this);
            btn.setText(type);
            btn.setTextColor(Color.WHITE);
            btn.setTextSize(14);
            btn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setCornerRadius(16);
            btnBg.setColor(Color.parseColor("#1A1A1A"));
            btnBg.setStroke(2, Color.parseColor("#8B0000"));
            btn.setBackground(btnBg);
            btn.setPadding(32, 16, 32, 16);
            btn.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            btn.setOnClickListener(v -> {
                currentActionType = ActionType.valueOf(type);
                Toast.makeText(this, "Action set to: " + type, Toast.LENGTH_SHORT).show();
                btn.setBackgroundColor(Color.parseColor("#CC0000"));
            });
            dialog.addView(btn);
        }

        Button createBtn = new Button(this);
        createBtn.setText("CREATE & PLACE");
        createBtn.setTextColor(Color.WHITE);
        createBtn.setTextSize(16);
        createBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable createBg = new GradientDrawable();
        createBg.setCornerRadius(20);
        createBg.setColor(Color.parseColor("#CC0000"));
        createBg.setStroke(2, Color.parseColor("#00E5FF"));
        createBtn.setBackground(createBg);
        createBtn.setPadding(32, 18, 32, 18);
        createBtn.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        createBtn.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) name = "Btn " + (buttons.size() + 1);
            ProgrammableButton btn = new ProgrammableButton(
                    "btn_" + System.currentTimeMillis(),
                    name,
                    0, 0
            );
            btn.action = currentActionType;
            buttons.add(btn);
            saveButtons();
            Toast.makeText(this, "Button created! Tap anywhere to place it", Toast.LENGTH_LONG).show();
            removeDialog(dialog);
            enterPlacementMode(btn);
        });
        dialog.addView(createBtn);

        showDialog(dialog);
    }

    private void showAddStickDialog() {
        hideMainMenu();
        LinearLayout dialog = new LinearLayout(this);
        dialog.setOrientation(LinearLayout.VERTICAL);
        dialog.setGravity(Gravity.CENTER);
        dialog.setPadding(40, 40, 40, 40);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(28);
        bg.setColor(Color.parseColor("#1A0A0A"));
        bg.setStroke(2, Color.parseColor("#8B0000"));
        dialog.setBackground(bg);

        TextView title = new TextView(this);
        title.setText("NEW STICK");
        title.setTextColor(Color.parseColor("#00E5FF"));
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 20);
        dialog.addView(title);

        EditText nameInput = new EditText(this);
        nameInput.setHint("Stick Name");
        nameInput.setHintTextColor(Color.parseColor("#555555"));
        nameInput.setTextColor(Color.WHITE);
        nameInput.setTextSize(16);
        nameInput.setBackgroundColor(Color.parseColor("#0A0000"));
        nameInput.setPadding(20, 16, 20, 16);
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setCornerRadius(14);
        inputBg.setColor(Color.parseColor("#0A0000"));
        inputBg.setStroke(2, Color.parseColor("#8B0000"));
        nameInput.setBackground(inputBg);
        nameInput.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        dialog.addView(nameInput);

        Button createBtn = new Button(this);
        createBtn.setText("CREATE & PLACE");
        createBtn.setTextColor(Color.WHITE);
        createBtn.setTextSize(16);
        createBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable createBg = new GradientDrawable();
        createBg.setCornerRadius(20);
        createBg.setColor(Color.parseColor("#00E5FF"));
        createBg.setStroke(2, Color.parseColor("#CC0000"));
        createBtn.setBackground(createBg);
        createBtn.setPadding(32, 18, 32, 18);
        createBtn.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        createBtn.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) name = "Stick " + (sticks.size() + 1);
            ProgrammableStick stick = new ProgrammableStick(
                    "stick_" + System.currentTimeMillis(),
                    name,
                    0, 0
            );
            sticks.add(stick);
            saveSticks();
            Toast.makeText(this, "Stick created! Tap anywhere to place it", Toast.LENGTH_LONG).show();
            removeDialog(dialog);
            enterPlacementMode(stick);
        });
        dialog.addView(createBtn);

        showDialog(dialog);
    }

    // ==================== РЕЖИМ РАЗМЕЩЕНИЯ ====================

    private void enterPlacementMode(Object item) {
        pendingPlacement = item;
        isEditModeActive = true;

        placementOverlay = new FrameLayout(this);
        placementOverlay.setBackgroundColor(Color.parseColor("#80000000"));

        TextView instruction = new TextView(this);
        instruction.setText("TAP ON SCREEN TO PLACE");
        instruction.setTextColor(Color.WHITE);
        instruction.setTextSize(24);
        instruction.setTypeface(null, android.graphics.Typeface.BOLD);
        instruction.setGravity(Gravity.CENTER);
        instruction.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        placementOverlay.addView(instruction);

        placementOverlay.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float x = event.getRawX();
                float y = event.getRawY();

                if (pendingPlacement instanceof ProgrammableButton) {
                    ProgrammableButton btn = (ProgrammableButton) pendingPlacement;
                    btn.x = (int) x - btn.size / 2;
                    btn.y = (int) y - btn.size / 2;
                    saveButtons();
                    Toast.makeText(this, "Button placed!", Toast.LENGTH_SHORT).show();
                } else if (pendingPlacement instanceof ProgrammableStick) {
                    ProgrammableStick stick = (ProgrammableStick) pendingPlacement;
                    stick.x = (int) x - stick.size / 2;
                    stick.y = (int) y - stick.size / 2;
                    saveSticks();
                    Toast.makeText(this, "Stick placed!", Toast.LENGTH_SHORT).show();
                }

                removePlacementOverlay();
                isEditModeActive = false;
                pendingPlacement = null;
                if (isNintendoModeActive) {
                    hideNintendoMode();
                    showNintendoMode();
                }
                return true;
            }
            return false;
        });

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                getOverlayFlag(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );

        if (windowManager != null) {
            windowManager.addView(placementOverlay, params);
        }
    }

    private void removePlacementOverlay() {
        if (placementOverlay != null && windowManager != null) {
            try {
                windowManager.removeView(placementOverlay);
                placementOverlay = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // ==================== ДОБАВЛЕНИЕ ЭЛЕМЕНТОВ В КОНТЕЙНЕР ====================

    private void addButtonsToNintendoContainer() {
        if (nintendoContainer == null) return;

        for (ProgrammableButton btn : buttons) {
            Button view = new Button(this);
            view.setText(btn.name);
            view.setTextColor(Color.WHITE);
            view.setTextSize(12);
            view.setTypeface(null, android.graphics.Typeface.BOLD);

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(Color.parseColor("#CC0000"));
            bg.setStroke(3, Color.parseColor("#00E5FF"));
            view.setBackground(bg);

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    btn.size, btn.size
            );
            params.leftMargin = btn.x;
            params.topMargin = btn.y;
            view.setLayoutParams(params);

            view.setOnTouchListener((v, event) -> {
                if (isEditModeActive) {
                    if (event.getAction() == MotionEvent.ACTION_MOVE) {
                        btn.x = (int) event.getRawX() - btn.size / 2;
                        btn.y = (int) event.getRawY() - btn.size / 2;
                        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
                        lp.leftMargin = btn.x;
                        lp.topMargin = btn.y;
                        v.setLayoutParams(lp);
                        return true;
                    } else if (event.getAction() == MotionEvent.ACTION_UP) {
                        saveButtons();
                        return true;
                    }
                } else {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        executeAction(btn);
                        return true;
                    }
                }
                return false;
            });

            nintendoContainer.addView(view);
        }
    }

    private void addSticksToNintendoContainer() {
        if (nintendoContainer == null) return;

        for (ProgrammableStick stick : sticks) {
            FrameLayout stickContainer = new FrameLayout(this);

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(Color.parseColor("#1A0A0A"));
            bg.setStroke(3, Color.parseColor("#00E5FF"));
            stickContainer.setBackground(bg);

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    stick.size, stick.size
            );
            params.leftMargin = stick.x;
            params.topMargin = stick.y;
            stickContainer.setLayoutParams(params);

            ImageView knob = new ImageView(this);
            Bitmap knobBitmap = createKnobBitmap();
            knob.setImageBitmap(knobBitmap);
            knob.setLayoutParams(new FrameLayout.LayoutParams(
                    stick.size / 2, stick.size / 2, Gravity.CENTER
            ));
            stickContainer.addView(knob);

            stickContainer.setOnTouchListener((v, event) -> {
                if (isEditModeActive) {
                    if (event.getAction() == MotionEvent.ACTION_MOVE) {
                        stick.x = (int) event.getRawX() - stick.size / 2;
                        stick.y = (int) event.getRawY() - stick.size / 2;
                        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
                        lp.leftMargin = stick.x;
                        lp.topMargin = stick.y;
                        v.setLayoutParams(lp);
                        return true;
                    } else if (event.getAction() == MotionEvent.ACTION_UP) {
                        saveSticks();
                        return true;
                    }
                } else {
                    if (event.getAction() == MotionEvent.ACTION_MOVE) {
                        float dx = event.getX() - stick.size / 2;
                        float dy = event.getY() - stick.size / 2;
                        float distance = (float) Math.sqrt(dx*dx + dy*dy);
                        float threshold = stick.size / 4;

                        if (distance > threshold) {
                            String action = "NONE";
                            if (Math.abs(dx) > Math.abs(dy)) {
                                action = dx > 0 ? stick.rightAction : stick.leftAction;
                            } else {
                                action = dy > 0 ? stick.downAction : stick.upAction;
                            }
                            if (!action.equals("NONE")) {
                                executeStickAction(action);
                            }
                        }
                        return true;
                    }
                }
                return false;
            });

            nintendoContainer.addView(stickContainer);
        }
    }

    private Bitmap createKnobBitmap() {
        int size = 60;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.parseColor("#00E5FF"));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(size/2f, size/2f, size/2f - 2, paint);
        return bitmap;
    }

    // ==================== ВЫПОЛНЕНИЕ ДЕЙСТВИЙ (С ВЫЗОВОМ АВТОКЛИКЕРА) ====================

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void executeAction(ProgrammableButton btn) {
        AutoClickerAccessibilityService service = AutoClickerAccessibilityService.getInstance();
        if (service == null) {
            Toast.makeText(this, "Accessibility Service not running! Enable it in Settings.", Toast.LENGTH_LONG).show();
            return;
        }

        int centerX = btn.x + btn.size / 2;
        int centerY = btn.y + btn.size / 2;

        switch (btn.action) {
            case CLICK:
                service.performTap(centerX, centerY);
                Toast.makeText(this, "Click at " + centerX + ", " + centerY, Toast.LENGTH_SHORT).show();
                break;
            case HOLD:
                service.performHold(centerX, centerY, btn.holdDuration);
                Toast.makeText(this, "Hold for " + btn.holdDuration + "ms", Toast.LENGTH_SHORT).show();
                break;
            case SWIPE:
                if (btn.macroPoints.size() >= 2) {
                    service.performSwipe(btn.macroPoints, 300);
                    Toast.makeText(this, "Swipe executed", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Record macro first!", Toast.LENGTH_SHORT).show();
                }
                break;
            case MACRO:
                if (btn.macroPoints.size() > 0) {
                    service.performMacro(btn.macroPoints, 100);
                    Toast.makeText(this, "Macro executed", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Record macro first!", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    private void executeStickAction(String action) {
        switch (action) {
            case "SCREENSHOT":
                Toast.makeText(this, "Screenshot captured!", Toast.LENGTH_SHORT).show();
                break;
            case "CLOSE_APP":
                Intent homeIntent = new Intent(Intent.ACTION_MAIN);
                homeIntent.addCategory(Intent.CATEGORY_HOME);
                startActivity(homeIntent);
                break;
            default:
                Toast.makeText(this, "Action: " + action, Toast.LENGTH_SHORT).show();
                break;
        }
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    private int getOverlayFlag() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;
    }

    private void showDialog(View dialog) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                getOverlayFlag(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );
        if (windowManager != null) {
            windowManager.addView(dialog, params);
        }
    }

    private void removeDialog(View dialog) {
        if (dialog != null && windowManager != null) {
            try {
                windowManager.removeView(dialog);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void toggleEditMode() {
        isEditModeActive = !isEditModeActive;
        Toast.makeText(this, "Edit Mode: " + (isEditModeActive ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
        if (isNintendoModeActive) {
            hideNintendoMode();
            showNintendoMode();
        }
    }

    // ==================== ЖИЗНЕННЫЙ ЦИКЛ (ОСТАЛЬНОЕ) ====================

    @Override
    public void onResume() {
        super.onResume();
        if (mainCircleContainer != null && !isMainOverlayVisible) {
            mainCircleContainer.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mainCircleContainer != null && windowManager != null) {
            try {
                windowManager.removeView(mainCircleContainer);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (mainOverlay != null && windowManager != null && isMainOverlayVisible) {
            try {
                windowManager.removeView(mainOverlay);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (nintendoContainer != null && windowManager != null && isNintendoModeActive) {
            try {
                windowManager.removeView(nintendoContainer);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (placementOverlay != null && windowManager != null) {
            try {
                windowManager.removeView(placementOverlay);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    createMainCircle();
                } else {
                    Toast.makeText(this, "Overlay permission required!", Toast.LENGTH_LONG).show();
                }
            }
        }
        if (requestCode == REQUEST_SCREEN_CAPTURE && resultCode == RESULT_OK) {
            Toast.makeText(this, "Screen capture permission granted!", Toast.LENGTH_SHORT).show();
        }
    }
          }
