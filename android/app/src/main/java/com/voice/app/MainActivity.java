package com.voice.app;

import android.Manifest;
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
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
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

    private static final int REQUEST_MICROPHONE = 100;
    private static final int REQUEST_CAMERA = 102;
    private static final int REQUEST_OVERLAY_PERMISSION = 101;
    private static final int REQUEST_GALLERY = 103;
    private static final int REQUEST_STORAGE = 104;
    private static final int REQUEST_VIDEO = 105;

    private WindowManager windowManager;
    public static FrameLayout mainCircleContainer;
    private WindowManager.LayoutParams mainCircleParams;
    
    private float startX, startY;
    private int initialX, initialY;
    private boolean isDragging = false;
    
    private FrameLayout mainOverlay;
    private WebView webView;
    private WindowManager.LayoutParams mainOverlayParams;
    private boolean isMainOverlayVisible = false;
    private Bundle webViewState = null;

    // Компоненты персонажей и видео
    private FrameLayout characterContainer;
    private ImageView characterView;
    private VideoView videoView;
    private WindowManager.LayoutParams characterParams;
    private Bitmap currentCharacterBitmap;
    private boolean isCharacterFixed = false;
    private boolean isCharacterModeActive = false;
    private String currentVideoPath = null;
    private MediaPlayer currentMediaPlayer = null;
    private boolean isVideoPlaying = false;
    
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
    
    // Система персонажей
    private ArrayList<CharacterData> characters = new ArrayList<>();
    private SharedPreferences prefs;
    private String tempCharacterName = "";
    
    private boolean isCharacterListOpen = false;
    private EditText nameInput;
    private boolean isVideoMode = false;
    
    // Переключение режимов
    private boolean isWebViewMode = true;
    private FrameLayout contentContainer;
    private LinearLayout charactersGridLayout;
    
    // Флаг - показывать ли оверлей только вне приложения
    private boolean isAppInForeground = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("characters", MODE_PRIVATE);
        loadCharacters();

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

    // ==================== КЛАСС ДАННЫХ ПЕРСОНАЖА ====================
    
    private static class CharacterData {
        String name;
        String path;
        long timestamp;
        boolean isVideo;
        int width;
        int height;
        
        CharacterData(String name, String path, boolean isVideo) {
            this.name = name;
            this.path = path;
            this.isVideo = isVideo;
            this.timestamp = System.currentTimeMillis();
            this.width = 300;
            this.height = 300;
        }
        
        CharacterData(JSONObject json) throws Exception {
            this.name = json.getString("name");
            this.path = json.getString("path");
            this.isVideo = json.optBoolean("isVideo", false);
            this.timestamp = json.optLong("timestamp", System.currentTimeMillis());
            this.width = json.optInt("width", 300);
            this.height = json.optInt("height", 300);
        }
        
        JSONObject toJSON() throws Exception {
            JSONObject json = new JSONObject();
            json.put("name", name);
            json.put("path", path);
            json.put("isVideo", isVideo);
            json.put("timestamp", timestamp);
            json.put("width", width);
            json.put("height", height);
            return json;
        }
    }

    // ==================== СОХРАНЕНИЕ ПЕРСОНАЖЕЙ ====================
    
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

    private String saveVideoToStorage(Uri videoUri) {
        try {
            File dir = new File(getExternalFilesDir(null), "characters/videos");
            if (!dir.exists()) dir.mkdirs();
            
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File file = new File(dir, "VIDEO_" + timeStamp + ".mp4");
            
            java.io.InputStream in = getContentResolver().openInputStream(videoUri);
            java.io.FileOutputStream out = new java.io.FileOutputStream(file);
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            out.close();
            
            return file.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

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
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_MEDIA_VIDEO}, REQUEST_VIDEO);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_STORAGE);
            }
        }
    }

    // ==================== ГЛАВНЫЙ КРУЖОК ====================

    private void createMainCircle() {
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
                            showMainOverlay();
                        }
                        return true;
                }
                return false;
            }
        });

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager != null) {
            windowManager.addView(mainCircleContainer, mainCircleParams);
            Toast.makeText(this, "CR Arcade готов", Toast.LENGTH_SHORT).show();
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

    // ==================== СОЗДАНИЕ ИКОНОК ====================

    private Drawable createHomeIcon() {
        Bitmap b = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(6);
        
        float cx = 40, cy = 40;
        // Домик
        c.drawLine(cx - 30, cy + 10, cx, cy - 22, p);
        c.drawLine(cx + 30, cy + 10, cx, cy - 22, p);
        c.drawLine(cx - 30, cy + 10, cx - 30, cy + 30, p);
        c.drawLine(cx + 30, cy + 10, cx + 30, cy + 30, p);
        c.drawLine(cx - 30, cy + 30, cx + 30, cy + 30, p);
        // Дверь
        c.drawLine(cx - 10, cy + 30, cx - 10, cy + 12, p);
        c.drawLine(cx + 10, cy + 30, cx + 10, cy + 12, p);
        c.drawLine(cx - 10, cy + 12, cx + 10, cy + 12, p);
        // Дымоход
        c.drawLine(cx + 6, cy - 18, cx + 6, cy - 30, p);
        c.drawLine(cx + 6, cy - 30, cx + 18, cy - 30, p);
        
        return new android.graphics.drawable.BitmapDrawable(getResources(), b);
    }

    private Drawable createCloseIcon() {
        Bitmap b = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(6);
        
        float cx = 40, cy = 40;
        c.drawLine(cx - 22, cy - 22, cx + 22, cy + 22, p);
        c.drawLine(cx + 22, cy - 22, cx - 22, cy + 22, p);
        
        return new android.graphics.drawable.BitmapDrawable(getResources(), b);
    }

    private Drawable createCharacterIcon() {
        Bitmap b = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(5);
        
        float cx = 40, cy = 40;
        c.drawCircle(cx, cy - 8, 14, p);
        c.drawLine(cx, cy + 6, cx, cy + 28, p);
        c.drawLine(cx, cy + 12, cx - 18, cy + 4, p);
        c.drawLine(cx, cy + 12, cx + 18, cy + 4, p);
        c.drawLine(cx, cy + 28, cx - 14, cy + 38, p);
        c.drawLine(cx, cy + 28, cx + 14, cy + 38, p);
        
        return new android.graphics.drawable.BitmapDrawable(getResources(), b);
    }

    private Drawable createSettingsIcon() {
        Bitmap b = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(5);
        
        float cx = 40, cy = 40;
        // Шестеренка
        c.drawCircle(cx, cy, 14, p);
        c.drawLine(cx - 8, cy - 22, cx - 8, cy - 14, p);
        c.drawLine(cx + 8, cy - 22, cx + 8, cy - 14, p);
        c.drawLine(cx - 8, cy + 14, cx - 8, cy + 22, p);
        c.drawLine(cx + 8, cy + 14, cx + 8, cy + 22, p);
        c.drawLine(cx - 22, cy - 8, cx - 14, cy - 8, p);
        c.drawLine(cx - 22, cy + 8, cx - 14, cy + 8, p);
        c.drawLine(cx + 14, cy - 8, cx + 22, cy - 8, p);
        c.drawLine(cx + 14, cy + 8, cx + 22, cy + 8, p);
        
        return new android.graphics.drawable.BitmapDrawable(getResources(), b);
    }

    private Drawable createExitIcon() {
        Bitmap b = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(6);
        
        float cx = 40, cy = 40;
        // Дверь с выходом
        c.drawRect(cx - 20, cy - 25, cx + 20, cy + 25, p);
        c.drawCircle(cx + 8, cy, 4, p);
        c.drawLine(cx + 20, cy - 8, cx + 32, cy - 8, p);
        c.drawLine(cx + 20, cy + 8, cx + 32, cy + 8, p);
        c.drawLine(cx + 32, cy - 8, cx + 32, cy + 8, p);
        // Стрелка выхода
        c.drawLine(cx - 20, cy, cx - 8, cy, p);
        c.drawLine(cx - 12, cy - 6, cx - 8, cy, p);
        c.drawLine(cx - 12, cy + 6, cx - 8, cy, p);
        
        return new android.graphics.drawable.BitmapDrawable(getResources(), b);
    }

    private Drawable createHideIcon() {
        Bitmap b = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(5);
        
        float cx = 40, cy = 40;
        // Глаз с перечеркиванием
        c.drawOval(cx - 22, cy - 14, cx + 22, cy + 14, p);
        c.drawCircle(cx, cy, 6, p);
        p.setStrokeWidth(6);
        c.drawLine(cx - 18, cy - 10, cx + 18, cy + 10, p);
        
        return new android.graphics.drawable.BitmapDrawable(getResources(), b);
    }

    // ==================== ОВЕРЛЕЙ С КРУГЛЫМИ КНОПКАМИ СВЕРХУ ====================

    private void showMainOverlay() {
        if (isMainOverlayVisible) return;
        int flag = getOverlayFlag();

        mainOverlay = new FrameLayout(this);
        mainOverlay.setBackgroundColor(Color.parseColor("#D0000000"));
        mainOverlay.setPadding(20, 20, 20, 20);

        FrameLayout innerContainer = new FrameLayout(this);
        GradientDrawable innerBg = new GradientDrawable();
        innerBg.setShape(GradientDrawable.RECTANGLE);
        innerBg.setCornerRadius(40);
        innerBg.setColor(Color.parseColor("#1A0A0A"));
        innerBg.setStroke(3, Color.parseColor("#CC0000"));
        innerBg.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        innerBg.setGradientRadius(500);
        innerBg.setColors(new int[]{Color.parseColor("#1A0A0A"), Color.parseColor("#2A0000")});
        innerContainer.setBackground(innerBg);
        innerContainer.setPadding(20, 20, 20, 20);

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));

        // ============ ВЕРХНЯЯ ПАНЕЛЬ С КРУГЛЫМИ КНОПКАМИ (одна строка) ============
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, 8, 0, 16);
        
        // 1. Кнопка ДОМИК (Home) - возвращает WebView
        ImageButton homeBtn = createRoundButton(createHomeIcon(), "#CC0000", "🏠");
        homeBtn.setOnClickListener(v -> {
            isWebViewMode = true;
            updateContent();
        });
        topBar.addView(homeBtn);
        
        // 2. Кнопка ПЕРСОНАЖИ (Characters)
        ImageButton charsBtn = createRoundButton(createCharacterIcon(), "#CC0000", "👤");
        charsBtn.setOnClickListener(v -> {
            isWebViewMode = false;
            updateContent();
        });
        topBar.addView(charsBtn);
        
        // 3. Кнопка ДОПОЛНИТЕЛЬНЫЕ ФУНКЦИИ (Settings)
        ImageButton settingsBtn = createRoundButton(createSettingsIcon(), "#CC0000", "⚙️");
        settingsBtn.setOnClickListener(v -> {
            // Открываем сайт в WebView
            isWebViewMode = true;
            webView.loadUrl("https://whuokhgrdcbnmkloplureecvjiqoendu.vercel.app/");
            updateContent();
        });
        topBar.addView(settingsBtn);
        
        // 4. Кнопка УДАЛИТЬ ОВЕРЛЕЙ (Exit)
        ImageButton exitBtn = createRoundButton(createExitIcon(), "#8B0000", "🚪");
        exitBtn.setOnClickListener(v -> {
            // Полностью удаляем оверлей и убиваем приложение с фона
            hideMainOverlay();
            if (mainCircleContainer != null && windowManager != null) {
                try {
                    windowManager.removeView(mainCircleContainer);
                    mainCircleContainer = null;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            // Закрываем приложение
            moveTaskToBack(true);
            System.exit(0);
        });
        topBar.addView(exitBtn);
        
        // 5. Кнопка ЗАКРЫТЬ МЕНЮ (скрывает меню, но оверлей остаётся)
        ImageButton hideBtn = createRoundButton(createHideIcon(), "#8B0000", "👁");
        hideBtn.setOnClickListener(v -> {
            hideMainOverlay();
            if (mainCircleContainer != null) {
                mainCircleContainer.setVisibility(View.VISIBLE);
            }
        });
        topBar.addView(hideBtn);

        topBar.setGravity(Gravity.CENTER);
        mainLayout.addView(topBar);

        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#8B0000"));
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2
        ));
        divider.setPadding(0, 0, 0, 16);
        mainLayout.addView(divider);

        // ============ КОНТЕЙНЕР ДЛЯ КОНТЕНТА (WebView / Персонажи) ============
        contentContainer = new FrameLayout(this);
        contentContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        contentContainer.setBackgroundColor(Color.parseColor("#0A0000"));
        contentContainer.setPadding(0, 0, 0, 0);
        
        // Создаем WebView
        createWebView();
        // Создаем грид персонажей
        createCharactersGrid();
        
        // Показываем WebView по умолчанию
        isWebViewMode = true;
        updateContent();
        
        mainLayout.addView(contentContainer);

        // ============ НИЖНЯЯ ПАНЕЛЬ (добавление персонажей) ============
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER);
        bottomBar.setPadding(0, 16, 0, 8);

        String[] actions = {"📷", "🎬", "➕"};
        String[] colors = {"#8B0000", "#CC0000", "#4CAF50"};
        String[] labels = {"ДОБАВИТЬ", "ВИДЕО", "ПЕРСОНАЖА"};
        
        for (int i = 0; i < actions.length; i++) {
            LinearLayout btnWrapper = new LinearLayout(this);
            btnWrapper.setOrientation(LinearLayout.VERTICAL);
            btnWrapper.setGravity(Gravity.CENTER);
            
            Button btn = new Button(this);
            btn.setText(actions[i]);
            btn.setTextColor(Color.WHITE);
            btn.setTextSize(20);
            btn.setTypeface(null, android.graphics.Typeface.BOLD);
            btn.setPadding(20, 14, 20, 14);
            
            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setShape(GradientDrawable.OVAL);
            btnBg.setColor(Color.parseColor(colors[i]));
            btnBg.setStroke(2, Color.parseColor("#CC0000"));
            btn.setBackground(btnBg);
            
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    60, 60
            );
            btnParams.setMargins(6, 0, 6, 0);
            btn.setLayoutParams(btnParams);
            
            TextView label = new TextView(this);
            label.setText(labels[i]);
            label.setTextColor(Color.parseColor("#888888"));
            label.setTextSize(9);
            label.setTypeface(null, android.graphics.Typeface.BOLD);
            label.setPadding(0, 4, 0, 0);
            
            final int index = i;
            btn.setOnClickListener(v -> {
                switch (index) {
                    case 0:
                        showAddCharacterDialog(false);
                        break;
                    case 1:
                        showAddCharacterDialog(true);
                        break;
                    case 2:
                        hideMainOverlay();
                        showCharacterListFullscreen();
                        break;
                }
            });
            
            btnWrapper.addView(btn);
            btnWrapper.addView(label);
            bottomBar.addView(btnWrapper);
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

    // ============ КРУГЛАЯ КНОПКА (увеличенная, красная) ============
    private ImageButton createRoundButton(Drawable icon, String color, String label) {
        ImageButton btn = new ImageButton(this);
        btn.setImageDrawable(icon);
        btn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        btn.setPadding(22, 22, 22, 22);
        
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.parseColor(color));
        bg.setStroke(4, Color.parseColor("#FF4444"));
        btn.setBackground(bg);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(85, 85);
        params.setMargins(8, 0, 8, 0);
        btn.setLayoutParams(params);
        
        return btn;
    }

    // ============ СОЗДАНИЕ WEBVIEW ============
    private void createWebView() {
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

        if (webViewState != null) {
            webView.restoreState(webViewState);
        } else {
            webView.loadUrl("https://crconferensimessenger.vercel.app/");
        }

        webView.setBackgroundColor(Color.parseColor("#0A0000"));
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
    }

    // ============ СОЗДАНИЕ ГРИДА ПЕРСОНАЖЕЙ ============
    private void createCharactersGrid() {
        charactersGridLayout = new LinearLayout(this);
        charactersGridLayout.setOrientation(LinearLayout.VERTICAL);
        charactersGridLayout.setPadding(8, 8, 8, 8);
        charactersGridLayout.setGravity(Gravity.CENTER);
        charactersGridLayout.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        
        updateCharactersGrid();
    }

    private void updateCharactersGrid() {
        if (charactersGridLayout == null) return;
        
        charactersGridLayout.removeAllViews();
        
        if (characters.isEmpty()) {
            TextView emptyText = new TextView(this);
            emptyText.setText("📭 Нет сохранённых персонажей\n\nНажмите + чтобы добавить");
            emptyText.setTextColor(Color.parseColor("#555555"));
            emptyText.setTextSize(16);
            emptyText.setTypeface(null, android.graphics.Typeface.BOLD);
            emptyText.setGravity(Gravity.CENTER);
            emptyText.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            emptyText.setPadding(0, 60, 0, 60);
            charactersGridLayout.addView(emptyText);
            return;
        }
        
        // Создаем строки по 2 персонажа
        int itemsPerRow = 2;
        int totalItems = characters.size();
        int rows = (int) Math.ceil((double) totalItems / itemsPerRow);
        
        for (int r = 0; r < rows; r++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(Gravity.CENTER);
            rowLayout.setPadding(0, 4, 0, 4);
            rowLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            
            int startIndex = r * itemsPerRow;
            int endIndex = Math.min(startIndex + itemsPerRow, totalItems);
            
            for (int i = startIndex; i < endIndex; i++) {
                CharacterData data = characters.get(i);
                LinearLayout card = createSmallCharacterCard(data, i);
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                );
                cardParams.setMargins(4, 0, 4, 0);
                card.setLayoutParams(cardParams);
                rowLayout.addView(card);
            }
            
            // Заполняем пустые места
            for (int i = endIndex; i < startIndex + itemsPerRow; i++) {
                View spacer = new View(this);
                spacer.setLayoutParams(new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1
                ));
                rowLayout.addView(spacer);
            }
            
            charactersGridLayout.addView(rowLayout);
        }
    }

    private LinearLayout createSmallCharacterCard(CharacterData data, int index) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(12, 12, 12, 12);
        
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(16);
        cardBg.setColor(Color.parseColor("#1A0000"));
        cardBg.setStroke(1, Color.parseColor("#8B0000"));
        card.setBackground(cardBg);
        
        // Превью
        FrameLayout previewContainer = new FrameLayout(this);
        previewContainer.setLayoutParams(new LinearLayout.LayoutParams(80, 80));
        
        GradientDrawable previewBg = new GradientDrawable();
        previewBg.setShape(GradientDrawable.OVAL);
        previewBg.setColor(Color.parseColor("#1A1A1A"));
        previewBg.setStroke(2, Color.parseColor("#8B0000"));
        previewContainer.setBackground(previewBg);
        
        if (!data.isVideo) {
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
        } else {
            TextView videoIcon = new TextView(this);
            videoIcon.setText("🎬");
            videoIcon.setTextSize(32);
            videoIcon.setGravity(Gravity.CENTER);
            previewContainer.addView(videoIcon);
        }
        
        card.addView(previewContainer);
        
        // Имя
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
        
        // Кнопки действий (УДАЛИТЬ, НА ЭКРАН, НА ОВЕРЛЕЙ)
        LinearLayout actionLayout = new LinearLayout(this);
        actionLayout.setOrientation(LinearLayout.HORIZONTAL);
        actionLayout.setGravity(Gravity.CENTER);
        actionLayout.setPadding(0, 4, 0, 0);
        
        // Кнопка "Удалить"
        Button deleteBtn = createSmallActionButton("🗑", "#CC0000");
        deleteBtn.setOnClickListener(v -> {
            characters.remove(index);
            saveCharacters();
            updateCharactersGrid();
            Toast.makeText(this, "Персонаж удалён", Toast.LENGTH_SHORT).show();
        });
        
        // Кнопка "На экран"
        Button screenBtn = createSmallActionButton("🖥", "#FF9800");
        screenBtn.setOnClickListener(v -> {
            loadCharacterToScreen(data);
            hideMainOverlay();
        });
        
        // Кнопка "На оверлей" (круг)
        Button circleBtn = createSmallActionButton("⭕", "#2196F3");
        circleBtn.setOnClickListener(v -> {
            loadCharacterToFloat(data);
            hideMainOverlay();
        });
        
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(36, 36);
        btnParams.setMargins(2, 0, 2, 0);
        actionLayout.addView(deleteBtn, btnParams);
        actionLayout.addView(screenBtn, btnParams);
        actionLayout.addView(circleBtn, btnParams);
        
        card.addView(actionLayout);
        
        return card;
    }

    // ============ ОБНОВЛЕНИЕ КОНТЕНТА ============
    private void updateContent() {
        if (contentContainer == null) return;
        
        contentContainer.removeAllViews();
        
        if (isWebViewMode) {
            if (webView != null) {
                contentContainer.addView(webView);
            }
        } else {
            if (charactersGridLayout != null) {
                updateCharactersGrid();
                contentContainer.addView(charactersGridLayout);
            }
        }
    }

    private void hideMainOverlay() {
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

    // ==================== СПИСОК ПЕРСОНАЖЕЙ (полноэкранный) ====================

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
        bg.setStroke(2, Color.parseColor("#8B0000"));
        mainLayout.setBackground(bg);
        
        LinearLayout headerLayout = new LinearLayout(this);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(Gravity.CENTER_VERTICAL);
        headerLayout.setPadding(0, 0, 0, 20);
        
        ImageButton backBtn = new ImageButton(this);
        backBtn.setImageDrawable(createCloseIcon());
        GradientDrawable backBg = new GradientDrawable();
        backBg.setShape(GradientDrawable.OVAL);
        backBg.setColor(Color.parseColor("#2A0000"));
        backBg.setStroke(2, Color.parseColor("#8B0000"));
        backBtn.setBackground(backBg);
        backBtn.setPadding(12, 12, 12, 12);
        backBtn.setOnClickListener(v -> {
            removeCharacterList();
            showMainOverlay();
        });
        headerLayout.addView(backBtn);
        
        TextView title = new TextView(this);
        title.setText("✦ ПЕРСОНАЖИ ✦");
        title.setTextColor(Color.parseColor("#CC0000"));
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        headerLayout.addView(title);
        
        ImageButton addBtn = new ImageButton(this);
        addBtn.setImageDrawable(createAddIcon());
        GradientDrawable addBg = new GradientDrawable();
        addBg.setShape(GradientDrawable.OVAL);
        addBg.setColor(Color.parseColor("#CC0000"));
        addBtn.setBackground(addBg);
        addBtn.setPadding(12, 12, 12, 12);
        addBtn.setOnClickListener(v -> showAddCharacterDialog(false));
        headerLayout.addView(addBtn);
        
        mainLayout.addView(headerLayout);
        
        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#8B0000"));
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2));
        divider.setPadding(0, 0, 0, 20);
        mainLayout.addView(divider);
        
        ScrollView scrollView = new ScrollView(this);
        LinearLayout gridLayout = new LinearLayout(this);
        gridLayout.setOrientation(LinearLayout.VERTICAL);
        gridLayout.setPadding(0, 8, 0, 8);
        
        if (characters.isEmpty()) {
            TextView emptyText = new TextView(this);
            emptyText.setText("📭 Нет сохранённых персонажей\n\nНажмите + чтобы добавить");
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
        closeBtn.setText("✕ ЗАКРЫТЬ");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setTextSize(14);
        closeBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setCornerRadius(20);
        closeBg.setColor(Color.parseColor("#2A0000"));
        closeBg.setStroke(2, Color.parseColor("#8B0000"));
        closeBtn.setBackground(closeBg);
        closeBtn.setPadding(32, 16, 32, 16);
        closeBtn.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        closeBtn.setOnClickListener(v -> {
            removeCharacterList();
            showMainOverlay();
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
        cardBg.setStroke(1, Color.parseColor("#8B0000"));
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
        previewBg.setStroke(2, Color.parseColor("#8B0000"));
        previewContainer.setBackground(previewBg);
        
        if (!data.isVideo) {
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
        } else {
            TextView videoIcon = new TextView(this);
            videoIcon.setText("🎬");
            videoIcon.setTextSize(28);
            videoIcon.setGravity(Gravity.CENTER);
            previewContainer.addView(videoIcon);
        }
        
        card.addView(previewContainer);
        
        LinearLayout infoLayout = new LinearLayout(this);
        infoLayout.setOrientation(LinearLayout.VERTICAL);
        infoLayout.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        infoLayout.setPadding(16, 0, 16, 0);
        
        String displayName = data.name.trim().isEmpty() ? "Без имени" : data.name;
        TextView nameText = new TextView(this);
        nameText.setText(displayName);
        nameText.setTextColor(Color.WHITE);
        nameText.setTextSize(16);
        nameText.setTypeface(null, android.graphics.Typeface.BOLD);
        infoLayout.addView(nameText);
        
        TextView typeText = new TextView(this);
        typeText.setText(data.isVideo ? "🎬 ВИДЕО" : "🖼 ИЗОБРАЖЕНИЕ");
        typeText.setTextColor(data.isVideo ? Color.parseColor("#FF9800") : Color.parseColor("#888888"));
        typeText.setTextSize(11);
        typeText.setTypeface(null, android.graphics.Typeface.BOLD);
        infoLayout.addView(typeText);
        
        card.addView(infoLayout);
        
        LinearLayout actionLayout = new LinearLayout(this);
        actionLayout.setOrientation(LinearLayout.HORIZONTAL);
        actionLayout.setGravity(Gravity.CENTER);
        
        // Удалить
        Button deleteBtn = createSmallActionButton("🗑", "#CC0000");
        deleteBtn.setOnClickListener(v -> {
            characters.remove(index);
            saveCharacters();
            removeCharacterList();
            showCharacterListFullscreen();
        });
        
        // На экран
        Button screenBtn = createSmallActionButton("🖥", "#FF9800");
        screenBtn.setOnClickListener(v -> {
            loadCharacterToScreen(data);
            removeCharacterList();
        });
        
        // На оверлей
        Button circleBtn = createSmallActionButton("⭕", "#2196F3");
        circleBtn.setOnClickListener(v -> {
            loadCharacterToFloat(data);
            removeCharacterList();
        });
        
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(44, 44);
        btnParams.setMargins(4, 0, 4, 0);
        actionLayout.addView(deleteBtn, btnParams);
        actionLayout.addView(screenBtn, btnParams);
        actionLayout.addView(circleBtn, btnParams);
        
        card.addView(actionLayout);
        
        return card;
    }

    private Button createSmallActionButton(String text, String color) {
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

    private Drawable createAddIcon() {
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
    }

    // ==================== ДОБАВЛЕНИЕ ПЕРСОНАЖА ====================

    private void showAddCharacterDialog(boolean isVideo) {
        removeCharacterList();
        this.isVideoMode = isVideo;
        
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
        bg.setStroke(2, Color.parseColor("#8B0000"));
        menuLayout.setBackground(bg);
        
        TextView title = new TextView(this);
        title.setText(isVideo ? "🎬 НОВОЕ ВИДЕО" : "🖼 НОВЫЙ ПЕРСОНАЖ");
        title.setTextColor(Color.parseColor("#CC0000"));
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 20);
        menuLayout.addView(title);
        
        TextView nameLabel = new TextView(this);
        nameLabel.setText("ИМЯ");
        nameLabel.setTextColor(Color.parseColor("#888888"));
        nameLabel.setTextSize(12);
        nameLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        nameLabel.setPadding(0, 0, 0, 8);
        menuLayout.addView(nameLabel);
        
        nameInput = new EditText(this);
        nameInput.setHint(isVideo ? "Введите имя видео" : "Введите имя персонажа");
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
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT);
        nameInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        nameInput.setFocusable(true);
        nameInput.setFocusableInTouchMode(true);
        nameInput.requestFocus();
        
        nameInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                android.view.inputmethod.InputMethodManager imm = 
                    (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(nameInput.getWindowToken(), 0);
                return true;
            }
            return false;
        });
        
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        inputParams.setMargins(0, 0, 0, 20);
        nameInput.setLayoutParams(inputParams);
        menuLayout.addView(nameInput);
        
        Button addBtn = new Button(this);
        addBtn.setText(isVideo ? "🎬 ВЫБРАТЬ ВИДЕО" : "📷 ВЫБРАТЬ ИЗОБРАЖЕНИЕ");
        addBtn.setTextColor(Color.WHITE);
        addBtn.setTextSize(14);
        addBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable addBg = new GradientDrawable();
        addBg.setCornerRadius(20);
        addBg.setColor(Color.parseColor("#CC0000"));
        addBg.setStroke(2, Color.parseColor("#8B0000"));
        addBtn.setBackground(addBg);
        addBtn.setPadding(32, 18, 32, 18);
        addBtn.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        addBtn.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            tempCharacterName = name.isEmpty() ? "Без имени" : name;
            removeMenu();
            if (isVideo) {
                openVideoPicker(tempCharacterName);
            } else {
                openGalleryForCharacter(tempCharacterName);
            }
        });
        menuLayout.addView(addBtn);
        
        Button cancelBtn = new Button(this);
        cancelBtn.setText("ОТМЕНА");
        cancelBtn.setTextColor(Color.WHITE);
        cancelBtn.setTextSize(14);
        cancelBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable cancelBg = new GradientDrawable();
        cancelBg.setCornerRadius(20);
        cancelBg.setColor(Color.parseColor("#2A0000"));
        cancelBg.setStroke(2, Color.parseColor("#8B0000"));
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
        
        nameInput.postDelayed(() -> {
            nameInput.requestFocus();
            android.view.inputmethod.InputMethodManager imm = 
                (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(nameInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }, 300);
    }

    private void openGalleryForCharacter(String name) {
        tempCharacterName = name;
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_GALLERY);
    }

    private void openVideoPicker(String name) {
        tempCharacterName = name;
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_VIDEO);
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

    // ==================== ЗАГРУЗКА ПЕРСОНАЖА ====================

    private void loadCharacterToFloat(CharacterData data) {
        try {
            if (data.isVideo) {
                loadVideoToFloat(data);
                return;
            }
            
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), 
                    Uri.fromFile(new File(data.path)));
            Bitmap processed = removeGreenScreen(bitmap, 40);
            
            if (mainCircleContainer != null && windowManager != null) {
                windowManager.removeView(mainCircleContainer);
            }
            
            mainCircleContainer = new FrameLayout(this);
            mainCircleContainer.setBackgroundColor(Color.TRANSPARENT);
            
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(Color.TRANSPARENT);
            d.setStroke(3, Color.WHITE);
            mainCircleContainer.setBackground(d);
            
            ImageButton imageButton = new ImageButton(this);
            imageButton.setImageBitmap(processed);
            imageButton.setBackgroundColor(Color.TRANSPARENT);
            imageButton.setPadding(5, 5, 5, 5);
            imageButton.setScaleType(ImageButton.ScaleType.FIT_CENTER);
            imageButton.setClickable(false);
            imageButton.setFocusable(false);
            
            FrameLayout.LayoutParams imgParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            );
            mainCircleContainer.addView(imageButton, imgParams);
            
            mainCircleParams = new WindowManager.LayoutParams(200, 200, getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
            mainCircleParams.gravity = Gravity.TOP | Gravity.START;
            mainCircleParams.x = 100;
            mainCircleParams.y = 200;
            
            mainCircleContainer.setOnTouchListener(createTouchListener());
            
            windowManager.addView(mainCircleContainer, mainCircleParams);
            Toast.makeText(this, "Персонаж загружен в круг", Toast.LENGTH_SHORT).show();
            
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка загрузки", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadVideoToFloat(CharacterData data) {
        try {
            if (mainCircleContainer != null && windowManager != null) {
                windowManager.removeView(mainCircleContainer);
            }
            
            mainCircleContainer = new FrameLayout(this);
            mainCircleContainer.setBackgroundColor(Color.TRANSPARENT);
            
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(Color.TRANSPARENT);
            d.setStroke(3, Color.WHITE);
            mainCircleContainer.setBackground(d);
            
            VideoView circleVideoView = new VideoView(this);
            circleVideoView.setVideoPath(data.path);
            circleVideoView.setPadding(5, 5, 5, 5);
            circleVideoView.setClipToOutline(true);
            
            FrameLayout.LayoutParams vidParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            );
            mainCircleContainer.addView(circleVideoView, vidParams);
            
            circleVideoView.setOnPreparedListener(mp -> {
                mp.setLooping(true);
                circleVideoView.start();
            });
            
            circleVideoView.start();
            
            mainCircleParams = new WindowManager.LayoutParams(200, 200, getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
            mainCircleParams.gravity = Gravity.TOP | Gravity.START;
            mainCircleParams.x = 100;
            mainCircleParams.y = 200;
            
            mainCircleContainer.setOnTouchListener(createTouchListener());
            
            windowManager.addView(mainCircleContainer, mainCircleParams);
            Toast.makeText(this, "Видео загружено в круг", Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка загрузки видео", Toast.LENGTH_SHORT).show();
        }
    }

    private View.OnTouchListener createTouchListener() {
        return new View.OnTouchListener() {
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
                            showMainOverlay();
                        }
                        return true;
                }
                return false;
            }
        };
    }

    // ==================== ЗАГРУЗКА НА ЭКРАН ====================

    private void loadCharacterToScreen(CharacterData data) {
        try {
            if (data.isVideo) {
                showVideoOnScreen(data);
                return;
            }
            
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), 
                    Uri.fromFile(new File(data.path)));
            showCharacterOnScreen(bitmap);
            Toast.makeText(this, "Персонаж отображён", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка загрузки", Toast.LENGTH_SHORT).show();
        }
    }

    private void showCharacterOnScreen(Bitmap bitmap) {
        if (windowManager == null) return;
        
        removeCharacter();
        
        currentCharacterBitmap = removeGreenScreen(bitmap, 40);
        isVideoMode = false;
        
        characterContainer = new FrameLayout(this);
        characterContainer.setBackgroundColor(Color.TRANSPARENT);
        
        characterView = new ImageView(this);
        characterView.setImageBitmap(currentCharacterBitmap);
        characterView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        characterView.setClickable(true);
        characterView.setFocusable(true);
        
        FrameLayout.LayoutParams charParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        characterContainer.addView(characterView, charParams);
        
        addCharacterControls(characterContainer);
        
        characterParams = new WindowManager.LayoutParams(
                400, 400,
                getOverlayFlag(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );
        characterParams.gravity = Gravity.CENTER;
        characterParams.x = 0;
        characterParams.y = 0;
        
        windowManager.addView(characterContainer, characterParams);
        isCharacterModeActive = true;
        isCharacterFixed = false;
        
        Toast.makeText(this, "Персонаж на экране", Toast.LENGTH_SHORT).show();
    }

    private void showVideoOnScreen(CharacterData data) {
        if (windowManager == null) return;
        
        removeCharacter();
        
        isVideoMode = true;
        currentVideoPath = data.path;
        
        characterContainer = new FrameLayout(this);
        characterContainer.setBackgroundColor(Color.TRANSPARENT);
        
        videoView = new VideoView(this);
        videoView.setVideoPath(data.path);
        videoView.setClickable(true);
        videoView.setFocusable(true);
        
        int videoWidth = 400;
        int videoHeight = 400;
        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(data.path);
            String width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            if (width != null && height != null) {
                videoWidth = Integer.parseInt(width);
                videoHeight = Integer.parseInt(height);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (retriever != null) {
                try {
                    retriever.release();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        
        videoView.setOnPreparedListener(mp -> {
            currentMediaPlayer = mp;
            mp.setLooping(true);
            videoView.start();
            isVideoPlaying = true;
        });
        
        videoView.setOnErrorListener((mp, what, extra) -> {
            Toast.makeText(this, "Ошибка видео", Toast.LENGTH_SHORT).show();
            return true;
        });
        
        FrameLayout.LayoutParams vidParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        characterContainer.addView(videoView, vidParams);
        
        addCharacterControls(characterContainer);
        
        int baseSize = 400;
        int finalWidth, finalHeight;
        if (videoWidth > videoHeight) {
            finalWidth = baseSize;
            finalHeight = baseSize * videoHeight / videoWidth;
        } else {
            finalHeight = baseSize;
            finalWidth = baseSize * videoWidth / videoHeight;
        }
        
        characterParams = new WindowManager.LayoutParams(
                finalWidth, finalHeight,
                getOverlayFlag(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );
        characterParams.gravity = Gravity.CENTER;
        characterParams.x = 0;
        characterParams.y = 0;
        
        windowManager.addView(characterContainer, characterParams);
        isCharacterModeActive = true;
        isCharacterFixed = false;
        
        videoView.start();
        Toast.makeText(this, "Видео воспроизводится", Toast.LENGTH_SHORT).show();
    }

    // ==================== УПРАВЛЕНИЕ ПЕРСОНАЖЕМ ====================

    private void addCharacterControls(FrameLayout container) {
        fixButton = new ImageButton(this);
        fixButton.setImageDrawable(createLockIcon(false));
        GradientDrawable fixBg = new GradientDrawable();
        fixBg.setShape(GradientDrawable.OVAL);
        fixBg.setColor(Color.parseColor("#FF6B00"));
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
        deleteButton.setBackground(deleteBg);
        deleteButton.setPadding(16, 16, 16, 16);
        
        FrameLayout.LayoutParams deleteParams = new FrameLayout.LayoutParams(60, 60, Gravity.TOP | Gravity.START);
        deleteParams.setMargins(24, 24, 0, 0);
        deleteButton.setLayoutParams(deleteParams);
        
        deleteButton.setOnClickListener(v -> {
            removeCharacter();
            if (mainCircleContainer != null) {
                mainCircleContainer.setVisibility(View.VISIBLE);
            }
            Toast.makeText(this, "Персонаж удалён", Toast.LENGTH_SHORT).show();
        });
        
        backButton = new ImageButton(this);
        backButton.setImageDrawable(createCloseIcon());
        GradientDrawable backBg = new GradientDrawable();
        backBg.setShape(GradientDrawable.OVAL);
        backBg.setColor(Color.parseColor("#9C27B0"));
        backButton.setBackground(backBg);
        backButton.setPadding(16, 16, 16, 16);
        
        FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(60, 60, Gravity.BOTTOM | Gravity.CENTER);
        backParams.setMargins(0, 0, 0, 24);
        backButton.setLayoutParams(backParams);
        
        backButton.setOnClickListener(v -> {
            removeCharacter();
            if (mainCircleContainer != null) {
                mainCircleContainer.setVisibility(View.VISIBLE);
            }
            Toast.makeText(this, "Возврат в меню", Toast.LENGTH_SHORT).show();
        });
        
        controlsLayout = new LinearLayout(this);
        controlsLayout.setOrientation(LinearLayout.VERTICAL);
        controlsLayout.setGravity(Gravity.CENTER);
        controlsLayout.setBackgroundColor(Color.parseColor("#AA000000"));
        controlsLayout.setPadding(16, 12, 16, 12);
        
        TextView titleText = new TextView(this);
        titleText.setText(isVideoMode ? "🎬 ВИДЕО" : "РАЗМЕР");
        titleText.setTextColor(Color.WHITE);
        titleText.setGravity(Gravity.CENTER);
        titleText.setTextSize(14);
        titleText.setTypeface(null, android.graphics.Typeface.BOLD);
        titleText.setPadding(0, 0, 0, 8);
        controlsLayout.addView(titleText);
        
        // X контрол
        LinearLayout xLayout = new LinearLayout(this);
        xLayout.setOrientation(LinearLayout.HORIZONTAL);
        xLayout.setGravity(Gravity.CENTER);
        
        sizeXLabel = new TextView(this);
        sizeXLabel.setText("Ш");
        sizeXLabel.setTextColor(Color.WHITE);
        sizeXLabel.setPadding(0, 0, 8, 0);
        
        sizeXInput = new EditText(this);
        sizeXInput.setText("400");
        sizeXInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
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
        
        // Y контрол
        LinearLayout yLayout = new LinearLayout(this);
        yLayout.setOrientation(LinearLayout.HORIZONTAL);
        yLayout.setGravity(Gravity.CENTER);
        
        sizeYLabel = new TextView(this);
        sizeYLabel.setText("В");
        sizeYLabel.setTextColor(Color.WHITE);
        sizeYLabel.setPadding(0, 0, 8, 0);
        
        sizeYInput = new EditText(this);
        sizeYInput.setText("400");
        sizeYInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
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
        
        // Z контрол (прозрачность)
        LinearLayout zLayout = new LinearLayout(this);
        zLayout.setOrientation(LinearLayout.HORIZONTAL);
        zLayout.setGravity(Gravity.CENTER);
        
        sizeZLabel = new TextView(this);
        sizeZLabel.setText("α");
        sizeZLabel.setTextColor(Color.WHITE);
        sizeZLabel.setPadding(0, 0, 8, 0);
        
        sizeZInput = new EditText(this);
        sizeZInput.setText("100");
        sizeZInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
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
        
        // Настройка слушателей
        xSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && characterParams != null) {
                    characterParams.width = progress + 50;
                    sizeXInput.setText(String.valueOf(progress + 50));
                    updateCharacterSize();
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
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
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
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
                    if (videoView != null) videoView.setAlpha(alpha);
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        sizeZInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                try {
                    int val = Integer.parseInt(sizeZInput.getText().toString());
                    if (val >= 0 && val <= 100) {
                        zSeekBar.setProgress(val);
                        float alpha = val / 100f;
                        if (characterContainer != null) characterContainer.setAlpha(alpha);
                        if (videoView != null) videoView.setAlpha(alpha);
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
        
        if (videoView != null) {
            videoView.setOnTouchListener((v, event) -> {
                if (isCharacterFixed) return false;
                return handleCharacterTouch(event);
            });
        }
    }
    
    private boolean handleCharacterTouch(MotionEvent event) {
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
                    windowManager.updateViewLayout(characterContainer, characterParams);
                }
                return true;
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                initialPinchDistance = 0;
                return true;
        }
        return false;
    }

    private void toggleCharacterFix() {
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
            if (videoView != null) {
                videoView.setClickable(false);
                videoView.setFocusable(false);
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
            if (videoView != null) {
                videoView.setClickable(true);
                videoView.setFocusable(true);
            }
        }
    }

    private void hideAllControls() {
        if (controlsLayout != null) controlsLayout.setVisibility(View.GONE);
        if (deleteButton != null) deleteButton.setVisibility(View.GONE);
        if (backButton != null) backButton.setVisibility(View.GONE);
        if (fixButton != null) fixButton.setVisibility(View.GONE);
    }

    private void showAllControls() {
        if (controlsLayout != null) controlsLayout.setVisibility(View.VISIBLE);
        if (deleteButton != null) deleteButton.setVisibility(View.VISIBLE);
        if (backButton != null) backButton.setVisibility(View.VISIBLE);
        if (fixButton != null) fixButton.setVisibility(View.VISIBLE);
    }
    
    private void updateCharacterSize() {
        if (windowManager != null && characterContainer != null && characterParams != null) {
            windowManager.updateViewLayout(characterContainer, characterParams);
        }
    }

    private void removeCharacter() {
        if (characterContainer != null && windowManager != null) {
            try {
                windowManager.removeView(characterContainer);
            } catch (Exception e) {
                e.printStackTrace();
            }
            characterContainer = null;
            characterView = null;
            videoView = null;
            currentCharacterBitmap = null;
            currentMediaPlayer = null;
            isCharacterModeActive = false;
            isCharacterFixed = false;
            isVideoMode = false;
            isVideoPlaying = false;
        }
    }

    // ==================== CHROMAKEY ====================

    private Bitmap removeGreenScreen(Bitmap source, int tolerance) {
        if (source == null) return null;
        
        Bitmap result = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int pixel = source.getPixel(x, y);
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);
                
                if (g > r + tolerance && g > b + tolerance) {
                    paint.setColor(Color.TRANSPARENT);
                    canvas.drawPoint(x, y, paint);
                } else {
                    paint.setColor(pixel);
                    canvas.drawPoint(x, y, paint);
                }
            }
        }
        return result;
    }

    // ==================== ИКОНКИ ====================

    private Drawable createLockIcon(boolean locked) {
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
    }

    private Drawable createDeleteIcon() {
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
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    private int getOverlayFlag() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;
    }

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
        // Не показываем оверлей, если приложение в фореграунде
        if (mainOverlay != null && isMainOverlayVisible) {
            hideMainOverlay();
        }
        if (mainCircleContainer != null) {
            mainCircleContainer.setVisibility(View.VISIBLE);
        }
        if (videoView != null && isVideoPlaying) {
            videoView.start();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        isAppInForeground = false;
        if (videoView != null && isVideoPlaying) {
            videoView.pause();
        }
        // Если приложение ушло в фон, показываем оверлей
        if (!isAppInForeground && mainCircleContainer != null) {
            showMainOverlay();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        if (videoView != null) {
            videoView.stopPlayback();
        }
        
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
        if (characterContainer != null && windowManager != null) {
            try {
                windowManager.removeView(characterContainer);
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
        if (characterListContainer != null && windowManager != null) {
            try {
                windowManager.removeView(characterListContainer);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQUEST_MICROPHONE && results.length > 0) {
            if (results[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Микрофон разрешён", Toast.LENGTH_SHORT).show();
            }
        }
        if (code == REQUEST_CAMERA && results.length > 0) {
            if (results[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Камера разрешена", Toast.LENGTH_SHORT).show();
            }
        }
        if (code == REQUEST_STORAGE && results.length > 0) {
            if (results[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Хранилище разрешено", Toast.LENGTH_SHORT).show();
            }
        }
        if (code == REQUEST_VIDEO && results.length > 0) {
            if (results[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Доступ к видео разрешён", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_GALLERY && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            try {
                Bitmap original = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                String path = saveImageToStorage(original);
                if (path != null) {
                    characters.add(new CharacterData(tempCharacterName, path, false));
                    saveCharacters();
                    Toast.makeText(this, "Персонаж сохранён", Toast.LENGTH_SHORT).show();
                }
                if (isMainOverlayVisible) {
                    updateContent();
                } else {
                    showCharacterListFullscreen();
                }
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Ошибка загрузки", Toast.LENGTH_SHORT).show();
            }
        }
        
        if (requestCode == REQUEST_VIDEO && resultCode == RESULT_OK && data != null) {
            Uri videoUri = data.getData();
            String path = saveVideoToStorage(videoUri);
            if (path != null) {
                characters.add(new CharacterData(tempCharacterName, path, true));
                saveCharacters();
                Toast.makeText(this, "Видео сохранено", Toast.LENGTH_SHORT).show();
            }
            if (isMainOverlayVisible) {
                updateContent();
            } else {
                showCharacterListFullscreen();
            }
        }
        
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    createMainCircle();
                } else {
                    Toast.makeText(this, "Разрешение на оверлей требуется!", Toast.LENGTH_LONG).show();
                }
            }
        }
    }
      }
