package com.voice.app;

import android.Manifest;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.BridgeActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.UUID;

public class MainActivity extends BridgeActivity {

    // ==================== КОНСТАНТЫ ====================
    private static final int REQUEST_OVERLAY_PERMISSION = 101;
    private static final int REQUEST_GALLERY = 103;

    // ==================== ПЕРЕМЕННЫЕ ====================
    private WindowManager windowManager;
    private FrameLayout mainCircleContainer;
    private WindowManager.LayoutParams mainCircleParams;
    private float startX, startY;
    private int initialX, initialY;
    private boolean isDragging = false;

    // Главное меню
    private FrameLayout mainOverlay;
    private boolean isMainOverlayVisible = false;

    // ====== NINTENDO MODE (ПОЛНОСТЬЮ РАБОЧИЙ) ======
    private boolean isNintendoModeActive = false;
    private FrameLayout nintendoContainer;
    private WindowManager.LayoutParams nintendoParams;

    // ====== КНОПКИ И СТИКИ ======
    private ArrayList<ProgrammableButton> buttons = new ArrayList<>();
    private ArrayList<ProgrammableStick> sticks = new ArrayList<>();
    private SharedPreferences prefs;
    private boolean isEditModeActive = false;

    // ==================== КЛАССЫ ДАННЫХ ====================

    private static class ProgrammableButton {
        String id;
        String name;
        int x, y;
        int size = 80;
        String action; // "CLICK", "HOLD", "SWIPE"
        int holdDuration = 500;
        ArrayList<Point> macroPoints = new ArrayList<>();

        ProgrammableButton(String id, String name, int x, int y) {
            this.id = id;
            this.name = name;
            this.x = x;
            this.y = y;
            this.action = "CLICK";
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

    private static class Point {
        int x, y;
        Point(int x, int y) { this.x = x; this.y = y; }
    }

    // ==================== ЖИЗНЕННЫЙ ЦИКЛ ====================

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("nintendo_config", MODE_PRIVATE);
        loadButtons();
        loadSticks();

        // Запрашиваем разрешение на оверлей
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

        // Настройка WebView в Bridge
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
                    btn.action = obj.getString("action");
                    btn.holdDuration = obj.optInt("holdDuration", 500);
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
                obj.put("action", btn.action);
                obj.put("holdDuration", btn.holdDuration);
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

        ImageView iconView = new ImageView(this);
        iconView.setImageBitmap(createGamepadBitmap());
        iconView.setBackgroundColor(Color.TRANSPARENT);
        iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iconView.setPadding(20, 20, 20, 20);

        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(Color.parseColor("#CC0000"));
        d.setStroke(6, Color.parseColor("#FF4444"));
        mainCircleContainer.setBackground(d);

        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        mainCircleContainer.addView(iconView, iconParams);

        mainCircleParams = new WindowManager.LayoutParams(136, 136, flag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        mainCircleParams.gravity = Gravity.TOP | Gravity.START;
        mainCircleParams.x = 100;
        mainCircleParams.y = 200;

        mainCircleContainer.setOnTouchListener((v, event) -> {
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
        });

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager != null) {
            windowManager.addView(mainCircleContainer, mainCircleParams);
            Toast.makeText(this, "Controller Ready!", Toast.LENGTH_SHORT).show();
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

        Button closeBtn = new Button(this);
        closeBtn.setText("X");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setTextSize(16);
        closeBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        closeBtn.setPadding(16, 8, 16, 8);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setShape(GradientDrawable.OVAL);
        closeBg.setColor(Color.parseColor("#2A0000"));
        closeBg.setStroke(2, Color.parseColor("#00E5FF"));
        closeBtn.setBackground(closeBg);
        closeBtn.setLayoutParams(new LinearLayout.LayoutParams(48, 48));
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
        mainLayout.addView(divider);

        // Нижняя панель с кнопками действий
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER);
        bottomBar.setPadding(0, 16, 0, 8);

        String[] actions = {"TOGGLE NINTENDO", "ADD BUTTON", "ADD STICK"};
        for (int i = 0; i < actions.length; i++) {
            Button btn = new Button(this);
            btn.setText(actions[i]);
            btn.setTextColor(Color.WHITE);
            btn.setTextSize(12);
            btn.setTypeface(null, android.graphics.Typeface.BOLD);
            btn.setPadding(24, 14, 24, 14);

            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setCornerRadius(20);
            btnBg.setColor(Color.parseColor(i == 0 ? "#00E5FF" : "#8B0000"));
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

    // ==================== NINTENDO MODE (ПОЛНОСТЬЮ РАБОЧИЙ) ====================

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

        // Создаем панели
        LinearLayout leftPanel = createControllerPanel(true);
        LinearLayout rightPanel = createControllerPanel(false);

        FrameLayout.LayoutParams leftParams = new FrameLayout.LayoutParams(
                150, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START
        );
        FrameLayout.LayoutParams rightParams = new FrameLayout.LayoutParams(
                150, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.END
        );
        nintendoContainer.addView(leftPanel, leftParams);
        nintendoContainer.addView(rightPanel, rightParams);

        // Добавляем кнопки и стики на панели
        addButtonsToPanel(leftPanel, true);
        addButtonsToPanel(rightPanel, false);
        addSticksToPanel(leftPanel, true);
        addSticksToPanel(rightPanel, false);

        // Кнопка закрытия режима (прямо на панели справа)
        Button closeModeBtn = new Button(this);
        closeModeBtn.setText("X");
        closeModeBtn.setTextColor(Color.WHITE);
        closeModeBtn.setTextSize(20);
        closeModeBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setShape(GradientDrawable.OVAL);
        closeBg.setColor(Color.parseColor("#FF0000"));
        closeBg.setStroke(2, Color.parseColor("#FFFFFF"));
        closeModeBtn.setBackground(closeBg);
        closeModeBtn.setPadding(16, 8, 16, 8);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(60, 60);
        closeParams.setMargins(0, 20, 0, 0);
        closeModeBtn.setLayoutParams(closeParams);
        closeModeBtn.setOnClickListener(v -> hideNintendoMode());
        rightPanel.addView(closeModeBtn);

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
                isNintendoModeActive = false;
                Toast.makeText(this, "Nintendo Mode OFF", Toast.LENGTH_SHORT).show();
                // Показываем главный круг обратно
                if (mainCircleContainer != null) {
                    mainCircleContainer.setVisibility(View.VISIBLE);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private LinearLayout createControllerPanel(boolean isLeft) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        panel.setBackgroundColor(Color.parseColor("#E61A0A0A"));
        panel.setPadding(10, 40, 10, 20);

        TextView title = new TextView(this);
        title.setText(isLeft ? "L" : "R");
        title.setTextColor(Color.parseColor("#00E5FF"));
        title.setTextSize(24);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 20);
        panel.addView(title);

        return panel;
    }

    // ==================== ДОБАВЛЕНИЕ ЭЛЕМЕНТОВ НА ПАНЕЛИ ====================

    private void addButtonsToPanel(LinearLayout panel, boolean isLeft) {
        for (ProgrammableButton btn : buttons) {
            // Фильтруем: левые кнопки на левую панель, правые на правую
            if ((isLeft && btn.x < 400) || (!isLeft && btn.x >= 400)) {
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

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(btn.size, btn.size);
                params.setMargins(0, 10, 0, 10);
                view.setLayoutParams(params);

                view.setOnTouchListener((v, event) -> {
                    if (isEditModeActive) {
                        // В режиме редактирования можно перемещать кнопку (для упрощения просто меняем координаты)
                        if (event.getAction() == MotionEvent.ACTION_MOVE) {
                            btn.x = (int) event.getRawX();
                            btn.y = (int) event.getRawY();
                            Toast.makeText(MainActivity.this, "Move: " + btn.x + "," + btn.y, Toast.LENGTH_SHORT).show();
                            return true;
                        } else if (event.getAction() == MotionEvent.ACTION_UP) {
                            saveButtons();
                            return true;
                        }
                    } else {
                        // Реальный клик
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {
                            executeAction(btn);
                            return true;
                        }
                    }
                    return false;
                });

                panel.addView(view);
            }
        }
    }

    private void addSticksToPanel(LinearLayout panel, boolean isLeft) {
        for (ProgrammableStick stick : sticks) {
            if ((isLeft && stick.x < 400) || (!isLeft && stick.x >= 400)) {
                FrameLayout stickContainer = new FrameLayout(this);
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.OVAL);
                bg.setColor(Color.parseColor("#1A0A0A"));
                bg.setStroke(3, Color.parseColor("#00E5FF"));
                stickContainer.setBackground(bg);

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(stick.size, stick.size);
                params.setMargins(0, 10, 0, 10);
                stickContainer.setLayoutParams(params);

                ImageView knob = new ImageView(this);
                knob.setImageBitmap(createKnobBitmap());
                knob.setLayoutParams(new FrameLayout.LayoutParams(stick.size / 2, stick.size / 2, Gravity.CENTER));
                stickContainer.addView(knob);

                stickContainer.setOnTouchListener((v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_MOVE) {
                        float dx = event.getX() - stick.size / 2;
                        float dy = event.getY() - stick.size / 2;
                        float distance = (float) Math.sqrt(dx * dx + dy * dy);
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
                    return false;
                });

                panel.addView(stickContainer);
            }
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
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2, paint);
        return bitmap;
    }

    // ==================== ВЫПОЛНЕНИЕ ДЕЙСТВИЙ (РЕАЛЬНЫЕ КЛИКИ) ====================

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
            case "CLICK":
                service.performTap(centerX, centerY);
                Toast.makeText(this, "Click!", Toast.LENGTH_SHORT).show();
                break;
            case "HOLD":
                service.performHold(centerX, centerY, btn.holdDuration);
                Toast.makeText(this, "Hold!", Toast.LENGTH_SHORT).show();
                break;
            case "SWIPE":
                if (btn.macroPoints.size() >= 2) {
                    service.performSwipe(btn.macroPoints, 300);
                    Toast.makeText(this, "Swipe!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Record macro first!", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    private void executeStickAction(String action) {
        switch (action) {
            case "SCREENSHOT":
                // Захват экрана (требует MediaProjection)
                Toast.makeText(this, "Screenshot!", Toast.LENGTH_SHORT).show();
                break;
            case "CLOSE_APP":
                Intent homeIntent = new Intent(Intent.ACTION_MAIN);
                homeIntent.addCategory(Intent.CATEGORY_HOME);
                startActivity(homeIntent);
                Toast.makeText(this, "App minimized!", Toast.LENGTH_SHORT).show();
                break;
            default:
                Toast.makeText(this, "Action: " + action, Toast.LENGTH_SHORT).show();
                break;
        }
    }

    // ==================== ДИАЛОГИ СОЗДАНИЯ ====================

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

        Button createBtn = new Button(this);
        createBtn.setText("CREATE");
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
                    UUID.randomUUID().toString(),
                    name,
                    200, 200 // По умолчанию в центр
            );
            buttons.add(btn);
            saveButtons();
            Toast.makeText(this, "Button created!", Toast.LENGTH_SHORT).show();
            removeDialog(dialog);
            // Перезапускаем режим Nintendo, чтобы обновить кнопки
            if (isNintendoModeActive) {
                hideNintendoMode();
                showNintendoMode();
            }
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
        createBtn.setText("CREATE");
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
                    UUID.randomUUID().toString(),
                    name,
                    200, 200
            );
            sticks.add(stick);
            saveSticks();
            Toast.makeText(this, "Stick created!", Toast.LENGTH_SHORT).show();
            removeDialog(dialog);
            if (isNintendoModeActive) {
                hideNintendoMode();
                showNintendoMode();
            }
        });
        dialog.addView(createBtn);

        showDialog(dialog);
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

    // ==================== ЖИЗНЕННЫЙ ЦИКЛ ====================

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
    }
              }
