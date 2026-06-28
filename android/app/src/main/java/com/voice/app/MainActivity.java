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
import java.util.UUID;

public class MainActivity extends BridgeActivity {

    // ==================== КОНСТАНТЫ ====================
    private static final int REQUEST_MICROPHONE = 100;
    private static final int REQUEST_CAMERA = 102;
    private static final int REQUEST_OVERLAY_PERMISSION = 101;
    private static final int REQUEST_GALLERY = 103;
    private static final int REQUEST_STORAGE = 104;

    // ==================== ПЕРЕМЕННЫЕ ====================
    private WindowManager windowManager;
    private FrameLayout mainCircleContainer;
    private WindowManager.LayoutParams mainCircleParams;
    private float startX, startY;
    private int initialX, initialY;
    private boolean isDragging = false;

    private FrameLayout mainOverlay;
    private WindowManager.LayoutParams mainOverlayParams;
    private boolean isMainOverlayVisible = false;
    private WebView webView;

    // ==================== СИСТЕМА ПЕРСОНАЖЕЙ ====================
    private ArrayList<CharacterData> characters = new ArrayList<>();
    private SharedPreferences prefs;
    private String tempCharacterName = "";
    private FrameLayout characterListContainer;
    private FrameLayout menuContainer;
    private boolean isCharacterListOpen = false;
    private EditText nameInput;

    // ==================== АВТОКЛИКЕР ====================
    private ArrayList<ClickButton> clickButtons = new ArrayList<>();
    private boolean isLocked = false;

    // ==================== КЛАССЫ ДАННЫХ ====================

    private static class CharacterData {
        String name;
        String path;
        long timestamp;

        CharacterData(String name, String path) {
            this.name = name;
            this.path = path;
            this.timestamp = System.currentTimeMillis();
        }

        CharacterData(JSONObject json) throws Exception {
            this.name = json.getString("name");
            this.path = json.getString("path");
            this.timestamp = json.getLong("timestamp");
        }

        JSONObject toJSON() throws Exception {
            JSONObject json = new JSONObject();
            json.put("name", name);
            json.put("path", path);
            json.put("timestamp", timestamp);
            return json;
        }
    }

    private static class ClickButton {
        String id;
        String name;
        int x, y;
        int size = 80;
        String actionType; // CLICK, SWIPE, HOLD, MACRO
        int holdDuration = 500;
        ArrayList<ActionPoint> sequence = new ArrayList<>();

        ClickButton(String id, String name, int x, int y) {
            this.id = id;
            this.name = name;
            this.x = x;
            this.y = y;
            this.actionType = "CLICK";
        }
    }

    private static class ActionPoint {
        int x, y;
        long delayMs;
        String type;

        ActionPoint(int x, int y, long delayMs, String type) {
            this.x = x;
            this.y = y;
            this.delayMs = delayMs;
            this.type = type;
        }
    }

    // ==================== ЖИЗНЕННЫЙ ЦИКЛ ====================

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("app_config", MODE_PRIVATE);
        loadCharacters();
        loadClickButtons();

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

    // ==================== СОХРАНЕНИЕ ====================

    private void loadCharacters() {
        characters.clear();
        try {
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

    private void loadClickButtons() {
        clickButtons.clear();
        try {
            String json = prefs.getString("click_buttons_list", "");
            if (!json.isEmpty()) {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    ClickButton btn = new ClickButton(
                            obj.getString("id"),
                            obj.getString("name"),
                            obj.getInt("x"),
                            obj.getInt("y")
                    );
                    btn.size = obj.optInt("size", 80);
                    btn.actionType = obj.getString("actionType");
                    btn.holdDuration = obj.optInt("holdDuration", 500);
                    
                    JSONArray seqArr = obj.optJSONArray("sequence");
                    if (seqArr != null) {
                        for (int j = 0; j < seqArr.length(); j++) {
                            JSONObject pObj = seqArr.getJSONObject(j);
                            btn.sequence.add(new ActionPoint(
                                pObj.getInt("x"),
                                pObj.getInt("y"),
                                pObj.getLong("delayMs"),
                                pObj.getString("type")
                            ));
                        }
                    }
                    clickButtons.add(btn);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveClickButtons() {
        try {
            JSONArray array = new JSONArray();
            for (ClickButton btn : clickButtons) {
                JSONObject obj = new JSONObject();
                obj.put("id", btn.id);
                obj.put("name", btn.name);
                obj.put("x", btn.x);
                obj.put("y", btn.y);
                obj.put("size", btn.size);
                obj.put("actionType", btn.actionType);
                obj.put("holdDuration", btn.holdDuration);
                
                JSONArray seqArr = new JSONArray();
                for (ActionPoint p : btn.sequence) {
                    JSONObject pObj = new JSONObject();
                    pObj.put("x", p.x);
                    pObj.put("y", p.y);
                    pObj.put("delayMs", p.delayMs);
                    pObj.put("type", p.type);
                    seqArr.put(pObj);
                }
                obj.put("sequence", seqArr);
                array.put(obj);
            }
            prefs.edit().putString("click_buttons_list", array.toString()).apply();
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
        iconView.setImageBitmap(createMainIconBitmap());
        iconView.setBackgroundColor(Color.TRANSPARENT);
        iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iconView.setPadding(20, 20, 20, 20);

        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(Color.parseColor("#2196F3"));
        d.setStroke(6, Color.parseColor("#00BCD4"));
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
            Toast.makeText(this, "Ready!", Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap createMainIconBitmap() {
        int size = 90;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(6);

        float cx = size / 2f, cy = size / 2f;
        canvas.drawCircle(cx, cy, 20, paint);
        canvas.drawLine(cx - 15, cy, cx + 15, cy, paint);
        canvas.drawLine(cx, cy - 15, cx, cy + 15, paint);
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
        innerBg.setStroke(2, Color.parseColor("#00BCD4"));
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
        title.setText("AUTO CLICKER & CHARACTERS");
        title.setTextColor(Color.parseColor("#00BCD4"));
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        topBar.addView(title);

        LinearLayout rightButtons = new LinearLayout(this);
        rightButtons.setOrientation(LinearLayout.HORIZONTAL);
        rightButtons.setGravity(Gravity.CENTER);

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
        divider.setBackgroundColor(Color.parseColor("#00BCD4"));
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2
        ));
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

        String[] actions = {"CHARACTERS", "AUTO CLICKER", "HIDE"};
        String[] colors = {"#8B0000", "#2196F3", "#2A0000"};

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
            btnBg.setStroke(2, Color.parseColor("#00BCD4"));
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
                        hideMainMenu();
                        showCharacterListFullscreen();
                        break;
                    case 1:
                        hideMainMenu();
                        showAutoClickerMenu();
                        break;
                    case 2:
                        hideMainMenu();
                        if (mainCircleContainer != null) {
                            mainCircleContainer.setVisibility(View.VISIBLE);
                        }
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
        bg.setStroke(2, Color.parseColor("#00BCD4"));
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

    // ==================== СИСТЕМА ПЕРСОНАЖЕЙ ====================

    private void showCharacterListFullscreen() {
        isCharacterListOpen = true;
        characterListContainer = new FrameLayout(this);
        characterListContainer.setBackgroundColor(Color.parseColor("#E6000000"));

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(24, 40, 24, 40);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(32);
        bg.setColor(Color.parseColor("#1A0A0A"));
        bg.setStroke(2, Color.parseColor("#00BCD4"));
        mainLayout.setBackground(bg);

        LinearLayout headerLayout = new LinearLayout(this);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(Gravity.CENTER_VERTICAL);
        headerLayout.setPadding(0, 0, 0, 20);

        Button backBtn = new Button(this);
        backBtn.setText("←");
        backBtn.setTextColor(Color.WHITE);
        backBtn.setTextSize(24);
        GradientDrawable backBg = new GradientDrawable();
        backBg.setShape(GradientDrawable.OVAL);
        backBg.setColor(Color.parseColor("#2A0000"));
        backBg.setStroke(2, Color.parseColor("#00BCD4"));
        backBtn.setBackground(backBg);
        backBtn.setPadding(20, 10, 20, 10);
        backBtn.setOnClickListener(v -> {
            removeCharacterList();
            showMainMenu();
        });
        headerLayout.addView(backBtn);

        TextView title = new TextView(this);
        title.setText("CHARACTERS");
        title.setTextColor(Color.parseColor("#CC0000"));
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        headerLayout.addView(title);

        Button addBtn = new Button(this);
        addBtn.setText("+");
        addBtn.setTextColor(Color.WHITE);
        addBtn.setTextSize(28);
        GradientDrawable addBg = new GradientDrawable();
        addBg.setShape(GradientDrawable.OVAL);
        addBg.setColor(Color.parseColor("#CC0000"));
        addBtn.setBackground(addBg);
        addBtn.setPadding(24, 12, 24, 12);
        addBtn.setOnClickListener(v -> showAddCharacterDialog());
        headerLayout.addView(addBtn);

        mainLayout.addView(headerLayout);

        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#00BCD4"));
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2));
        mainLayout.addView(divider);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout gridLayout = new LinearLayout(this);
        gridLayout.setOrientation(LinearLayout.VERTICAL);
        gridLayout.setPadding(0, 8, 0, 8);

        if (characters.isEmpty()) {
            TextView emptyText = new TextView(this);
            emptyText.setText("No characters saved");
            emptyText.setTextColor(Color.parseColor("#555555"));
            emptyText.setTextSize(16);
            emptyText.setTypeface(null, android.graphics.Typeface.BOLD);
            emptyText.setGravity(Gravity.CENTER);
            emptyText.setPadding(0, 60, 0, 60);
            gridLayout.addView(emptyText);
        } else {
            for (int i = 0; i < characters.size(); i++) {
                CharacterData data = characters.get(i);
                LinearLayout cardView = createCharacterCard(data, i);
                gridLayout.addView(cardView);
            }
        }

        scrollView.addView(gridLayout);
        mainLayout.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1));

        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER);
        bottomBar.setPadding(0, 16, 0, 0);

        Button closeBtn = new Button(this);
        closeBtn.setText("CLOSE");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setTextSize(14);
        closeBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setCornerRadius(20);
        closeBg.setColor(Color.parseColor("#2A0000"));
        closeBg.setStroke(2, Color.parseColor("#00BCD4"));
        closeBtn.setBackground(closeBg);
        closeBtn.setPadding(32, 16, 32, 16);
        closeBtn.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        closeBtn.setOnClickListener(v -> {
            removeCharacterList();
            showMainMenu();
        });
        bottomBar.addView(closeBtn);

        mainLayout.addView(bottomBar);

        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        characterListContainer.addView(mainLayout, containerParams);

        WindowManager.LayoutParams windowParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                getOverlayFlag(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );

        if (windowManager != null) {
            windowManager.addView(characterListContainer, windowParams);
        }
    }

    private LinearLayout createCharacterCard(CharacterData data, int index) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(16, 16, 16, 16);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(16);
        cardBg.setColor(Color.parseColor("#0A0000"));
        cardBg.setStroke(1, Color.parseColor("#00BCD4"));
        card.setBackground(cardBg);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 8, 0, 8);
        card.setLayoutParams(cardParams);

        FrameLayout previewContainer = new FrameLayout(this);
        previewContainer.setLayoutParams(new LinearLayout.LayoutParams(56, 56));

        GradientDrawable previewBg = new GradientDrawable();
        previewBg.setShape(GradientDrawable.OVAL);
        previewBg.setColor(Color.parseColor("#1A1A1A"));
        previewBg.setStroke(2, Color.parseColor("#00BCD4"));
        previewContainer.setBackground(previewBg);

        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(),
                    Uri.fromFile(new File(data.path)));
            ImageView thumbView = new ImageView(this);
            thumbView.setImageBitmap(bitmap);
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

        LinearLayout infoLayout = new LinearLayout(this);
        infoLayout.setOrientation(LinearLayout.VERTICAL);
        infoLayout.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        infoLayout.setPadding(16, 0, 16, 0);

        String displayName = data.name.trim().isEmpty() ? "Unnamed" : data.name;
        TextView nameText = new TextView(this);
        nameText.setText(displayName);
        nameText.setTextColor(Color.WHITE);
        nameText.setTextSize(16);
        nameText.setTypeface(null, android.graphics.Typeface.BOLD);
        infoLayout.addView(nameText);

        TextView typeText = new TextView(this);
        typeText.setText("IMAGE");
        typeText.setTextColor(Color.parseColor("#888888"));
        typeText.setTextSize(11);
        typeText.setTypeface(null, android.graphics.Typeface.BOLD);
        infoLayout.addView(typeText);

        card.addView(infoLayout);

        LinearLayout actionLayout = new LinearLayout(this);
        actionLayout.setOrientation(LinearLayout.HORIZONTAL);
        actionLayout.setGravity(Gravity.CENTER);

        Button circleBtn = createSmallActionButton("C", "#2196F3");
        circleBtn.setOnClickListener(v -> {
            Toast.makeText(this, "Character loaded!", Toast.LENGTH_SHORT).show();
            removeCharacterList();
        });

        Button deleteBtn = createSmallActionButton("D", "#CC0000");
        deleteBtn.setOnClickListener(v -> {
            characters.remove(index);
            saveCharacters();
            removeCharacterList();
            showCharacterListFullscreen();
        });

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(44, 44);
        btnParams.setMargins(4, 0, 4, 0);
        actionLayout.addView(circleBtn, btnParams);
        actionLayout.addView(deleteBtn, btnParams);

        card.addView(actionLayout);

        return card;
    }

    private Button createSmallActionButton(String text, String color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(12);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.parseColor(color));
        bg.setStroke(2, Color.parseColor("#00BCD4"));
        btn.setBackground(bg);
        btn.setPadding(0, 0, 0, 0);
        return btn;
    }

    private void removeCharacterList() {
        if (characterListContainer != null && windowManager != null) {
            try {
                windowManager.removeView(characterListContainer);
                characterListContainer = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        isCharacterListOpen = false;
    }

    private void showAddCharacterDialog() {
        removeCharacterList();

        menuContainer = new FrameLayout(this);
        menuContainer.setBackgroundColor(Color.parseColor("#CC000000"));

        LinearLayout menuLayout = new LinearLayout(this);
        menuLayout.setOrientation(LinearLayout.VERTICAL);
        menuLayout.setGravity(Gravity.CENTER);
        menuLayout.setPadding(40, 40, 40, 40);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(28);
        bg.setColor(Color.parseColor("#1A0A0A"));
        bg.setStroke(2, Color.parseColor("#00BCD4"));
        menuLayout.setBackground(bg);

        TextView title = new TextView(this);
        title.setText("NEW CHARACTER");
        title.setTextColor(Color.parseColor("#CC0000"));
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 20);
        menuLayout.addView(title);

        TextView nameLabel = new TextView(this);
        nameLabel.setText("NAME");
        nameLabel.setTextColor(Color.parseColor("#888888"));
        nameLabel.setTextSize(12);
        nameLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        nameLabel.setPadding(0, 0, 0, 8);
        menuLayout.addView(nameLabel);

        nameInput = new EditText(this);
        nameInput.setHint("Enter character name");
        nameInput.setHintTextColor(Color.parseColor("#555555"));
        nameInput.setTextColor(Color.WHITE);
        nameInput.setTextSize(16);
        nameInput.setBackgroundColor(Color.parseColor("#0A0000"));
        nameInput.setPadding(20, 16, 20, 16);

        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setCornerRadius(14);
        inputBg.setColor(Color.parseColor("#0A0000"));
        inputBg.setStroke(2, Color.parseColor("#00BCD4"));
        nameInput.setBackground(inputBg);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT);
        nameInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        nameInput.setFocusable(true);
        nameInput.setFocusableInTouchMode(true);
        nameInput.requestFocus();

        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        inputParams.setMargins(0, 0, 0, 20);
        nameInput.setLayoutParams(inputParams);
        menuLayout.addView(nameInput);

        Button addBtn = new Button(this);
        addBtn.setText("ADD IMAGE");
        addBtn.setTextColor(Color.WHITE);
        addBtn.setTextSize(14);
        addBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable addBg = new GradientDrawable();
        addBg.setCornerRadius(20);
        addBg.setColor(Color.parseColor("#CC0000"));
        addBg.setStroke(2, Color.parseColor("#00BCD4"));
        addBtn.setBackground(addBg);
        addBtn.setPadding(32, 18, 32, 18);
        addBtn.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        addBtn.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            tempCharacterName = name.isEmpty() ? "Unnamed" : name;
            removeMenu();
            openGalleryForCharacter(tempCharacterName);
        });
        menuLayout.addView(addBtn);

        Button cancelBtn = new Button(this);
        cancelBtn.setText("CANCEL");
        cancelBtn.setTextColor(Color.WHITE);
        cancelBtn.setTextSize(14);
        cancelBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable cancelBg = new GradientDrawable();
        cancelBg.setCornerRadius(20);
        cancelBg.setColor(Color.parseColor("#2A0000"));
        cancelBg.setStroke(2, Color.parseColor("#00BCD4"));
        cancelBtn.setBackground(cancelBg);
        cancelBtn.setPadding(32, 18, 32, 18);
        cancelBtn.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        cancelBtn.setOnClickListener(v -> {
            removeMenu();
            showCharacterListFullscreen();
        });

        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cancelParams.setMargins(0, 8, 0, 0);
        menuLayout.addView(cancelBtn, cancelParams);

        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        containerParams.gravity = Gravity.CENTER;
        menuContainer.addView(menuLayout, containerParams);

        WindowManager.LayoutParams windowParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                getOverlayFlag(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );

        if (windowManager != null) {
            windowManager.addView(menuContainer, windowParams);
        }
    }

    private void openGalleryForCharacter(String name) {
        tempCharacterName = name;
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_GALLERY);
    }

    private void removeMenu() {
        if (menuContainer != null && windowManager != null) {
            try {
                windowManager.removeView(menuContainer);
                menuContainer = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // ==================== АВТОКЛИКЕР ====================

    private void showAutoClickerMenu() {
        isMainOverlayVisible = false;
        FrameLayout autoOverlay = new FrameLayout(this);
        autoOverlay.setBackgroundColor(Color.parseColor("#E6000000"));

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(24, 40, 24, 40);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(32);
        bg.setColor(Color.parseColor("#1A0A0A"));
        bg.setStroke(2, Color.parseColor("#2196F3"));
        mainLayout.setBackground(bg);

        LinearLayout headerLayout = new LinearLayout(this);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(Gravity.CENTER_VERTICAL);
        headerLayout.setPadding(0, 0, 0, 20);

        Button backBtn = new Button(this);
        backBtn.setText("←");
        backBtn.setTextColor(Color.WHITE);
        backBtn.setTextSize(24);
        GradientDrawable backBg = new GradientDrawable();
        backBg.setShape(GradientDrawable.OVAL);
        backBg.setColor(Color.parseColor("#2A0000"));
        backBg.setStroke(2, Color.parseColor("#2196F3"));
        backBtn.setBackground(backBg);
        backBtn.setPadding(20, 10, 20, 10);
        backBtn.setOnClickListener(v -> {
            removeAutoClickerOverlay(autoOverlay);
            showMainMenu();
        });
        headerLayout.addView(backBtn);

        TextView title = new TextView(this);
        title.setText("AUTO CLICKER");
        title.setTextColor(Color.parseColor("#2196F3"));
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        headerLayout.addView(title);

        mainLayout.addView(headerLayout);

        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#2196F3"));
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2));
        mainLayout.addView(divider);

        // Список кнопок автокликера
        LinearLayout buttonList = new LinearLayout(this);
        buttonList.setOrientation(LinearLayout.VERTICAL);
        buttonList.setPadding(0, 16, 0, 16);

        for (ClickButton btn : clickButtons) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(8, 8, 8, 8);

            TextView nameText = new TextView(this);
            nameText.setText(btn.name + " (" + btn.x + "," + btn.y + ")");
            nameText.setTextColor(Color.WHITE);
            nameText.setTextSize(14);
            nameText.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1
            ));
            item.addView(nameText);

            Button editBtn = new Button(this);
            editBtn.setText("E");
            editBtn.setTextColor(Color.WHITE);
            editBtn.setTextSize(12);
            GradientDrawable ebg = new GradientDrawable();
            ebg.setShape(GradientDrawable.OVAL);
            ebg.setColor(Color.parseColor("#2196F3"));
            editBtn.setBackground(ebg);
            editBtn.setPadding(8, 8, 8, 8);
            editBtn.setOnClickListener(v -> showEditClickButtonDialog(btn, autoOverlay));
            item.addView(editBtn);

            Button deleteBtn = new Button(this);
            deleteBtn.setText("X");
            deleteBtn.setTextColor(Color.WHITE);
            deleteBtn.setTextSize(12);
            GradientDrawable dbg = new GradientDrawable();
            dbg.setShape(GradientDrawable.OVAL);
            dbg.setColor(Color.parseColor("#F44336"));
            deleteBtn.setBackground(dbg);
            deleteBtn.setPadding(8, 8, 8, 8);
            deleteBtn.setOnClickListener(v -> {
                clickButtons.remove(btn);
                saveClickButtons();
                removeAutoClickerOverlay(autoOverlay);
                showAutoClickerMenu();
            });
            item.addView(deleteBtn);

            buttonList.addView(item);
        }

        mainLayout.addView(buttonList);

        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER);
        bottomBar.setPadding(0, 16, 0, 0);

        String[] actions = {"ADD BUTTON", "LOCK/UNLOCK", "CLOSE"};
        for (int i = 0; i < actions.length; i++) {
            Button btn = new Button(this);
            btn.setText(actions[i]);
            btn.setTextColor(Color.WHITE);
            btn.setTextSize(12);
            btn.setTypeface(null, android.graphics.Typeface.BOLD);
            btn.setPadding(20, 12, 20, 12);

            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setCornerRadius(20);
            btnBg.setColor(Color.parseColor("#2196F3"));
            btnBg.setStroke(2, Color.parseColor("#00BCD4"));
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
                        showAddClickButtonDialog(autoOverlay);
                        break;
                    case 1:
                        toggleLock();
                        break;
                    case 2:
                        removeAutoClickerOverlay(autoOverlay);
                        showMainMenu();
                        break;
                }
            });
            bottomBar.addView(btn);
        }
        mainLayout.addView(bottomBar);

        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        autoOverlay.addView(mainLayout, containerParams);

        WindowManager.LayoutParams windowParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                getOverlayFlag(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );

        if (windowManager != null) {
            windowManager.addView(autoOverlay, windowParams);
        }
    }

    private void removeAutoClickerOverlay(FrameLayout overlay) {
        if (overlay != null && windowManager != null) {
            try {
                windowManager.removeView(overlay);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void toggleLock() {
        isLocked = !isLocked;
        Toast.makeText(this, isLocked ? "LOCKED" : "UNLOCKED", Toast.LENGTH_SHORT).show();
    }

    private void showAddClickButtonDialog(FrameLayout parentOverlay) {
        LinearLayout dialog = new LinearLayout(this);
        dialog.setOrientation(LinearLayout.VERTICAL);
        dialog.setGravity(Gravity.CENTER);
        dialog.setPadding(40, 40, 40, 40);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(28);
        bg.setColor(Color.parseColor("#1A0A0A"));
        bg.setStroke(2, Color.parseColor("#2196F3"));
        dialog.setBackground(bg);

        TextView title = new TextView(this);
        title.setText("NEW CLICK BUTTON");
        title.setTextColor(Color.parseColor("#2196F3"));
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
        inputBg.setStroke(2, Color.parseColor("#2196F3"));
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
        createBg.setColor(Color.parseColor("#2196F3"));
        createBg.setStroke(2, Color.parseColor("#00BCD4"));
        createBtn.setBackground(createBg);
        createBtn.setPadding(32, 18, 32, 18);
        createBtn.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        createBtn.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) name = "Btn " + (clickButtons.size() + 1);
            ClickButton btn = new ClickButton(
                    UUID.randomUUID().toString(),
                    name,
                    200, 200
            );
            clickButtons.add(btn);
            saveClickButtons();
            Toast.makeText(this, "Button created!", Toast.LENGTH_SHORT).show();
            removeDialog(dialog);
            removeAutoClickerOverlay(parentOverlay);
            showAutoClickerMenu();
        });
        dialog.addView(createBtn);

        showDialog(dialog);
    }

    private void showEditClickButtonDialog(ClickButton btn, FrameLayout parentOverlay) {
        LinearLayout dialog = new LinearLayout(this);
        dialog.setOrientation(LinearLayout.VERTICAL);
        dialog.setGravity(Gravity.CENTER);
        dialog.setPadding(40, 40, 40, 40);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(28);
        bg.setColor(Color.parseColor("#1A0A0A"));
        bg.setStroke(2, Color.parseColor("#2196F3"));
        dialog.setBackground(bg);

        TextView title = new TextView(this);
        title.setText("EDIT: " + btn.name);
        title.setTextColor(Color.parseColor("#2196F3"));
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 20);
        dialog.addView(title);

        Button addPointBtn = new Button(this);
        addPointBtn.setText("ADD POINT (current position)");
        addPointBtn.setTextColor(Color.WHITE);
        addPointBtn.setTextSize(14);
        GradientDrawable addBg = new GradientDrawable();
        addBg.setCornerRadius(20);
        addBg.setColor(Color.parseColor("#2196F3"));
        addBg.setStroke(2, Color.parseColor("#00BCD4"));
        addPointBtn.setBackground(addBg);
        addPointBtn.setPadding(32, 16, 32, 16);
        addPointBtn.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        addPointBtn.setOnClickListener(v -> {
            btn.sequence.add(new ActionPoint(btn.x, btn.y, 500, "CLICK"));
            saveClickButtons();
            Toast.makeText(this, "Point added!", Toast.LENGTH_SHORT).show();
            removeDialog(dialog);
            removeAutoClickerOverlay(parentOverlay);
            showAutoClickerMenu();
        });
        dialog.addView(addPointBtn);

        if (!btn.sequence.isEmpty()) {
            TextView seqTitle = new TextView(this);
            seqTitle.setText("SEQUENCE (" + btn.sequence.size() + " points)");
            seqTitle.setTextColor(Color.parseColor("#888888"));
            seqTitle.setTextSize(14);
            seqTitle.setPadding(0, 20, 0, 10);
            dialog.addView(seqTitle);

            for (int i = 0; i < btn.sequence.size(); i++) {
                ActionPoint p = btn.sequence.get(i);
                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.HORIZONTAL);
                item.setGravity(Gravity.CENTER_VERTICAL);
                item.setPadding(8, 8, 8, 8);

                TextView info = new TextView(this);
                info.setText(i + ": (" + p.x + "," + p.y + ") " + p.type + " " + p.delayMs + "ms");
                info.setTextColor(Color.WHITE);
                info.setTextSize(14);
                info.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1
                ));
                item.addView(info);

                Button removeBtn = new Button(this);
                removeBtn.setText("X");
                removeBtn.setTextColor(Color.WHITE);
                removeBtn.setTextSize(12);
                GradientDrawable rbg = new GradientDrawable();
                rbg.setShape(GradientDrawable.OVAL);
                rbg.setColor(Color.parseColor("#F44336"));
                removeBtn.setBackground(rbg);
                removeBtn.setPadding(8, 8, 8, 8);
                
                final int index = i; // <--- ИСПРАВЛЕНИЕ ОШИБКИ
                removeBtn.setOnClickListener(v -> {
                    btn.sequence.remove(index); // <--- ИСПОЛЬЗУЕМ index
                    saveClickButtons();
                    removeDialog(dialog);
                    removeAutoClickerOverlay(parentOverlay);
                    showAutoClickerMenu();
                });
                item.addView(removeBtn);

                dialog.addView(item);
            }
        }

        Button closeBtn = new Button(this);
        closeBtn.setText("CLOSE");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setTextSize(16);
        closeBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setCornerRadius(20);
        closeBg.setColor(Color.parseColor("#2A0000"));
        closeBg.setStroke(2, Color.parseColor("#2196F3"));
        closeBtn.setBackground(closeBg);
        closeBtn.setPadding(32, 18, 32, 18);
        closeBtn.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        closeBtn.setOnClickListener(v -> {
            removeDialog(dialog);
            removeAutoClickerOverlay(parentOverlay);
            showAutoClickerMenu();
        });
        dialog.addView(closeBtn);

        showDialog(dialog);
    }

    // ==================== ВЫПОЛНЕНИЕ АВТОКЛИКЕРА ====================

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void executeClickAction(ClickButton btn) {
        AutoClickerAccessibilityService service = AutoClickerAccessibilityService.getInstance();
        if (service == null) {
            Toast.makeText(this, "Accessibility Service not running!", Toast.LENGTH_LONG).show();
            return;
        }

        if (btn.sequence.isEmpty()) {
            int centerX = btn.x + btn.size / 2;
            int centerY = btn.y + btn.size / 2;
            service.performTap(centerX, centerY);
            Toast.makeText(this, "Click!", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            for (ActionPoint p : btn.sequence) {
                try {
                    Thread.sleep(p.delayMs);
                    switch (p.type) {
                        case "CLICK":
                            service.performTap(p.x, p.y);
                            break;
                        case "HOLD":
                            service.performHold(p.x, p.y, 500);
                            break;
                        case "SWIPE":
                            ArrayList<AutoClickerAccessibilityService.Point> swipePoints = new ArrayList<>();
                            swipePoints.add(new AutoClickerAccessibilityService.Point(p.x, p.y));
                            swipePoints.add(new AutoClickerAccessibilityService.Point(p.x, p.y + 100));
                            service.performSwipe(swipePoints, 300);
                            break;
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            runOnUiThread(() -> 
                Toast.makeText(MainActivity.this, "Sequence completed!", Toast.LENGTH_SHORT).show()
            );
        }).start();
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
        if (characterListContainer != null && windowManager != null) {
            try {
                windowManager.removeView(characterListContainer);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (menuContainer != null && windowManager != null) {
            try {
                windowManager.removeView(menuContainer);
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
        if (requestCode == REQUEST_GALLERY && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            try {
                Bitmap original = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                String path = saveImageToStorage(original);
                if (path != null) {
                    characters.add(new CharacterData(tempCharacterName, path));
                    saveCharacters();
                    Toast.makeText(this, "Character saved!", Toast.LENGTH_SHORT).show();
                }
                showCharacterListFullscreen();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
            }
        }
    }
            }
