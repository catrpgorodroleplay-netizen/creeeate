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
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
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
import android.widget.MediaController;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.BridgeActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
    private static final int REQUEST_VIDEO_GALLERY = 105;

    private WindowManager windowManager;
    public static ImageButton mainCircle;
    private WindowManager.LayoutParams mainCircleParams;
    
    private float startX, startY;
    private int initialX, initialY;
    private boolean isDragging = false;
    
    private FrameLayout mainOverlay;
    private WebView webView;
    private WindowManager.LayoutParams mainOverlayParams;
    private boolean isMainOverlayVisible = false;
    private Bundle webViewState = null;

    // Компоненты персонажа
    private FrameLayout characterContainer;
    private ImageView characterView;
    private SurfaceView characterSurfaceView;
    private MediaPlayer characterMediaPlayer;
    private WindowManager.LayoutParams characterParams;
    private Bitmap currentCharacterBitmap;
    private boolean isCharacterFixed = false;
    private boolean isCharacterModeActive = false;
    private boolean isCharacterVideo = false;
    private String currentCharacterPath = "";
    
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

        Intent serviceIntent = new Intent(this, VoiceForegroundService.class);
        ContextCompat.startForegroundService(this, serviceIntent);

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
        boolean isVideo;
        long timestamp;
        
        CharacterData(String name, String path, boolean isVideo) {
            this.name = name;
            this.path = path;
            this.isVideo = isVideo;
            this.timestamp = System.currentTimeMillis();
        }
        
        CharacterData(JSONObject json) throws Exception {
            this.name = json.getString("name");
            this.path = json.getString("path");
            this.isVideo = json.getBoolean("isVideo");
            this.timestamp = json.getLong("timestamp");
        }
        
        JSONObject toJSON() throws Exception {
            JSONObject json = new JSONObject();
            json.put("name", name);
            json.put("path", path);
            json.put("isVideo", isVideo);
            json.put("timestamp", timestamp);
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
            File dir = new File(getExternalFilesDir(null), "characters");
            if (!dir.exists()) dir.mkdirs();
            
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File file = new File(dir, "CHAR_VIDEO_" + timeStamp + ".mp4");
            
            InputStream in = getContentResolver().openInputStream(videoUri);
            FileOutputStream out = new FileOutputStream(file);
            
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
                        new String[]{Manifest.permission.READ_MEDIA_VIDEO}, REQUEST_VIDEO_GALLERY);
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
        mainCircle = new ImageButton(this);
        mainCircle.setImageBitmap(createGamepadBitmap());

        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(Color.parseColor("#CC0000"));
        d.setStroke(6, Color.parseColor("#FF4444"));
        mainCircle.setBackground(d);
        mainCircle.setPadding(25, 25, 25, 25);
        mainCircle.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);

        mainCircleParams = new WindowManager.LayoutParams(136, 136, flag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        mainCircleParams.gravity = Gravity.TOP | Gravity.START;
        mainCircleParams.x = 100;
        mainCircleParams.y = 200;

        mainCircle.setOnTouchListener((v, event) -> {
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
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isDragging = true;
                    mainCircleParams.x = initialX + (int) dx;
                    mainCircleParams.y = initialY + (int) dy;
                    if (windowManager != null) {
                        windowManager.updateViewLayout(mainCircle, mainCircleParams);
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
            windowManager.addView(mainCircle, mainCircleParams);
            Toast.makeText(this, "Controller ready", Toast.LENGTH_SHORT).show();
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

    // ==================== ГЛАВНОЕ МЕНЮ (СОВРЕМЕННОЕ) ====================

    private void showMainMenu() {
        if (menuContainer != null) return;
        
        menuContainer = new FrameLayout(this);
        menuContainer.setBackgroundColor(Color.parseColor("#CC000000"));
        
        // Основной контейнер
        LinearLayout menuLayout = new LinearLayout(this);
        menuLayout.setOrientation(LinearLayout.VERTICAL);
        menuLayout.setGravity(Gravity.CENTER);
        menuLayout.setPadding(48, 48, 48, 48);
        
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(32);
        bg.setColor(Color.parseColor("#1A0A0A"));
        bg.setStroke(2, Color.parseColor("#8B0000"));
        menuLayout.setBackground(bg);
        
        // Заголовок
        TextView title = new TextView(this);
        title.setText("CHARACTER STUDIO");
        title.setTextColor(Color.parseColor("#CC0000"));
        title.setTextSize(24);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 32);
        menuLayout.addView(title);
        
        // Кнопка персонажей
        Button charactersBtn = createModernButton("MANAGE CHARACTERS", "#8B0000");
        charactersBtn.setOnClickListener(v -> {
            removeMenu();
            showCharacterListFullscreen();
        });
        
        // Кнопка WebView
        Button webBtn = createModernButton("OPEN WEB VIEW", "#CC0000");
        webBtn.setOnClickListener(v -> {
            removeMenu();
            showMainOverlay();
        });
        
        // Кнопка закрытия
        Button closeBtn = createModernButton("CLOSE", "#2A0000");
        closeBtn.setOnClickListener(v -> removeMenu());
        
        int margin = 12;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, margin, 0, margin);
        
        menuLayout.addView(charactersBtn, params);
        menuLayout.addView(webBtn, params);
        menuLayout.addView(closeBtn, params);
        
        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        containerParams.gravity = Gravity.CENTER;
        menuContainer.addView(menuLayout, containerParams);
        
        WindowManager.LayoutParams menuWindowParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                getOverlayFlag(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );
        
        if (windowManager != null) {
            windowManager.addView(menuContainer, menuWindowParams);
        }
    }

    private Button createModernButton(String text, String color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(16);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);
        btn.setPadding(32, 24, 32, 24);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(24);
        bg.setColor(Color.parseColor(color));
        bg.setStroke(2, Color.parseColor("#CC0000"));
        btn.setBackground(bg);
        btn.setAllCaps(false);
        return btn;
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

    // ==================== СПИСОК ПЕРСОНАЖЕЙ (СОВРЕМЕННЫЙ) ====================

    private void showCharacterListFullscreen() {
        isCharacterListOpen = true;
        characterListContainer = new FrameLayout(this);
        characterListContainer.setBackgroundColor(Color.parseColor("#DD000000"));
        
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(32, 48, 32, 48);
        
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(32);
        bg.setColor(Color.parseColor("#1A0A0A"));
        bg.setStroke(2, Color.parseColor("#8B0000"));
        mainLayout.setBackground(bg);
        
        // Заголовок
        LinearLayout headerLayout = new LinearLayout(this);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(Gravity.CENTER_VERTICAL);
        headerLayout.setPadding(0, 0, 0, 24);
        
        TextView title = new TextView(this);
        title.setText("CHARACTERS");
        title.setTextColor(Color.parseColor("#CC0000"));
        title.setTextSize(22);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        headerLayout.addView(title);
        
        // Кнопка добавления
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
        divider.setBackgroundColor(Color.parseColor("#8B0000"));
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2));
        divider.setPadding(0, 0, 0, 24);
        mainLayout.addView(divider);
        
        ScrollView scrollView = new ScrollView(this);
        LinearLayout gridLayout = new LinearLayout(this);
        gridLayout.setOrientation(LinearLayout.VERTICAL);
        gridLayout.setPadding(0, 8, 0, 8);
        
        if (characters.isEmpty()) {
            TextView emptyText = new TextView(this);
            emptyText.setText("No characters yet\nPress + to add");
            emptyText.setTextColor(Color.parseColor("#555555"));
            emptyText.setTextSize(18);
            emptyText.setGravity(Gravity.CENTER);
            emptyText.setPadding(0, 80, 0, 80);
            gridLayout.addView(emptyText);
        } else {
            for (int i = 0; i < characters.size(); i++) {
                CharacterData data = characters.get(i);
                LinearLayout cardView = createModernCharacterCard(data, i);
                gridLayout.addView(cardView);
            }
        }
        
        scrollView.addView(gridLayout);
        mainLayout.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.MATCH_PARENT));
        
        Button closeBtn = new Button(this);
        closeBtn.setText("CLOSE");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setTextSize(16);
        closeBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setCornerRadius(24);
        closeBg.setColor(Color.parseColor("#2A0000"));
        closeBg.setStroke(2, Color.parseColor("#8B0000"));
        closeBtn.setBackground(closeBg);
        closeBtn.setPadding(32, 20, 32, 20);
        closeBtn.setOnClickListener(v -> {
            removeCharacterList();
            showMainMenu();
        });
        
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        closeParams.setMargins(0, 16, 0, 0);
        mainLayout.addView(closeBtn, closeParams);
        
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

    private LinearLayout createModernCharacterCard(CharacterData data, int index) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(20, 20, 20, 20);
        
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
        
        // Иконка типа
        TextView icon = new TextView(this);
        icon.setText(data.isVideo ? "▶" : "▣");
        icon.setTextSize(32);
        icon.setTextColor(Color.parseColor("#CC0000"));
        icon.setPadding(0, 0, 16, 0);
        card.addView(icon);
        
        // Информация
        LinearLayout infoLayout = new LinearLayout(this);
        infoLayout.setOrientation(LinearLayout.VERTICAL);
        infoLayout.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        
        String displayName = data.name.trim().isEmpty() ? "Unnamed" : data.name;
        TextView nameText = new TextView(this);
        nameText.setText(displayName);
        nameText.setTextColor(Color.WHITE);
        nameText.setTextSize(18);
        nameText.setTypeface(null, android.graphics.Typeface.BOLD);
        infoLayout.addView(nameText);
        
        TextView typeText = new TextView(this);
        typeText.setText(data.isVideo ? "Video" : "Image");
        typeText.setTextColor(Color.parseColor("#888888"));
        typeText.setTextSize(14);
        infoLayout.addView(typeText);
        
        card.addView(infoLayout);
        
        // Действия
        LinearLayout actionLayout = new LinearLayout(this);
        actionLayout.setOrientation(LinearLayout.HORIZONTAL);
        actionLayout.setGravity(Gravity.CENTER);
        
        Button floatBtn = createSmallActionButton("●", "#2196F3");
        floatBtn.setOnClickListener(v -> {
            loadCharacterToFloat(data);
            removeCharacterList();
        });
        
        Button screenBtn = createSmallActionButton("◉", "#FF9800");
        screenBtn.setOnClickListener(v -> {
            loadCharacterToScreen(data);
            removeCharacterList();
        });
        
        Button deleteBtn = createSmallActionButton("×", "#CC0000");
        deleteBtn.setOnClickListener(v -> {
            characters.remove(index);
            saveCharacters();
            removeCharacterList();
            showCharacterListFullscreen();
        });
        
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                56, 56
        );
        btnParams.setMargins(4, 0, 4, 0);
        actionLayout.addView(floatBtn, btnParams);
        actionLayout.addView(screenBtn, btnParams);
        actionLayout.addView(deleteBtn, btnParams);
        
        card.addView(actionLayout);
        
        return card;
    }

    private Button createSmallActionButton(String text, String color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(18);
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

    // ==================== ДОБАВЛЕНИЕ ПЕРСОНАЖА (С РАБОЧЕЙ КЛАВИАТУРОЙ) ====================

    private void showAddCharacterDialog() {
        removeCharacterList();
        
        menuContainer = new FrameLayout(this);
        menuContainer.setBackgroundColor(Color.parseColor("#CC000000"));
        
        LinearLayout menuLayout = new LinearLayout(this);
        menuLayout.setOrientation(LinearLayout.VERTICAL);
        menuLayout.setGravity(Gravity.CENTER);
        menuLayout.setPadding(48, 48, 48, 48);
        
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(32);
        bg.setColor(Color.parseColor("#1A0A0A"));
        bg.setStroke(2, Color.parseColor("#8B0000"));
        menuLayout.setBackground(bg);
        
        TextView title = new TextView(this);
        title.setText("NEW CHARACTER");
        title.setTextColor(Color.parseColor("#CC0000"));
        title.setTextSize(22);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 24);
        menuLayout.addView(title);
        
        // Поле ввода имени с работающей клавиатурой
        TextView nameLabel = new TextView(this);
        nameLabel.setText("NAME");
        nameLabel.setTextColor(Color.parseColor("#888888"));
        nameLabel.setTextSize(14);
        nameLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        nameLabel.setPadding(0, 0, 0, 8);
        menuLayout.addView(nameLabel);
        
        nameInput = new EditText(this);
        nameInput.setHint("Enter character name");
        nameInput.setHintTextColor(Color.parseColor("#555555"));
        nameInput.setTextColor(Color.WHITE);
        nameInput.setTextSize(18);
        nameInput.setBackgroundColor(Color.parseColor("#0A0000"));
        nameInput.setPadding(24, 20, 24, 20);
        
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setCornerRadius(16);
        inputBg.setColor(Color.parseColor("#0A0000"));
        inputBg.setStroke(2, Color.parseColor("#8B0000"));
        nameInput.setBackground(inputBg);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT);
        nameInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        nameInput.setFocusable(true);
        nameInput.setFocusableInTouchMode(true);
        nameInput.requestFocus();
        
        // Обработка Enter на клавиатуре
        nameInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || 
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                // Переход к выбору типа
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
        
        // Кнопки выбора типа
        TextView typeLabel = new TextView(this);
        typeLabel.setText("SELECT TYPE");
        typeLabel.setTextColor(Color.parseColor("#888888"));
        typeLabel.setTextSize(14);
        typeLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        typeLabel.setPadding(0, 0, 0, 8);
        menuLayout.addView(typeLabel);
        
        LinearLayout buttonLayout = new LinearLayout(this);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setGravity(Gravity.CENTER);
        buttonLayout.setPadding(0, 0, 0, 16);
        
        Button imageBtn = createModernButton("IMAGE", "#8B0000");
        imageBtn.setTextSize(14);
        imageBtn.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            tempCharacterName = name.isEmpty() ? "Unnamed" : name;
            removeMenu();
            openGalleryForCharacter(tempCharacterName, false);
        });
        
        Button videoBtn = createModernButton("VIDEO", "#CC0000");
        videoBtn.setTextSize(14);
        videoBtn.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            tempCharacterName = name.isEmpty() ? "Unnamed" : name;
            removeMenu();
            openVideoGalleryForCharacter(tempCharacterName);
        });
        
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        btnParams.setMargins(4, 0, 4, 0);
        buttonLayout.addView(imageBtn, btnParams);
        buttonLayout.addView(videoBtn, btnParams);
        menuLayout.addView(buttonLayout);
        
        Button cancelBtn = createModernButton("CANCEL", "#2A0000");
        cancelBtn.setTextSize(14);
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
        
        // Показываем клавиатуру
        nameInput.postDelayed(() -> {
            nameInput.requestFocus();
            android.view.inputmethod.InputMethodManager imm = 
                (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(nameInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }, 300);
    }

    private void openGalleryForCharacter(String name, boolean isVideo) {
        tempCharacterName = name;
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_GALLERY);
    }

    private void openVideoGalleryForCharacter(String name) {
        tempCharacterName = name;
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_VIDEO_GALLERY);
    }

    // ==================== ЗАГРУЗКА ПЕРСОНАЖА В ПЛАВАЮЩИЙ КРУЖОК ====================

    private void loadCharacterToFloat(CharacterData data) {
        if (data.isVideo) {
            loadVideoToFloatAnimated(data.path);
            return;
        }
        
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), 
                    Uri.fromFile(new File(data.path)));
            Bitmap processed = removeGreenScreen(bitmap, 40);
            
            if (mainCircle != null && windowManager != null) {
                windowManager.removeView(mainCircle);
            }
            
            mainCircle = new ImageButton(this);
            mainCircle.setImageBitmap(processed);
            
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(Color.TRANSPARENT);
            d.setStroke(3, Color.WHITE);
            mainCircle.setBackground(d);
            mainCircle.setPadding(5, 5, 5, 5);
            mainCircle.setScaleType(ImageButton.ScaleType.FIT_CENTER);
            
            mainCircleParams = new WindowManager.LayoutParams(200, 200, getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
            mainCircleParams.gravity = Gravity.TOP | Gravity.START;
            mainCircleParams.x = 100;
            mainCircleParams.y = 200;
            
            mainCircle.setOnTouchListener((v, event) -> {
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
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isDragging = true;
                        mainCircleParams.x = initialX + (int) dx;
                        mainCircleParams.y = initialY + (int) dy;
                        if (windowManager != null) {
                            windowManager.updateViewLayout(mainCircle, mainCircleParams);
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
            
            windowManager.addView(mainCircle, mainCircleParams);
            Toast.makeText(this, "Character loaded", Toast.LENGTH_SHORT).show();
            
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error loading character", Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== АНИМИРОВАННОЕ ВИДЕО В ПЛАВАЮЩЕМ КРУЖКЕ ====================
    
    private void loadVideoToFloatAnimated(String videoPath) {
        try {
            if (mainCircle != null && windowManager != null) {
                windowManager.removeView(mainCircle);
            }
            
            // Создаем ImageButton с прозрачным фоном
            mainCircle = new ImageButton(this);
            mainCircle.setBackgroundColor(Color.TRANSPARENT);
            mainCircle.setImageBitmap(null);
            
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(Color.TRANSPARENT);
            d.setStroke(3, Color.WHITE);
            mainCircle.setBackground(d);
            mainCircle.setPadding(5, 5, 5, 5);
            
            mainCircleParams = new WindowManager.LayoutParams(200, 200, getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
            mainCircleParams.gravity = Gravity.TOP | Gravity.START;
            mainCircleParams.x = 100;
            mainCircleParams.y = 200;
            
            // Создаем SurfaceView для видео
            final SurfaceView videoSurface = new SurfaceView(this);
            videoSurface.setZOrderOnTop(true);
            videoSurface.getHolder().setFormat(PixelFormat.TRANSLUCENT);
            
            // Добавляем SurfaceView в mainCircle
            FrameLayout.LayoutParams videoParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            );
            mainCircle.addView(videoSurface, videoParams);
            
            // Создаем MediaPlayer
            final MediaPlayer player = new MediaPlayer();
            player.setDataSource(videoPath);
            player.setLooping(true);
            player.setVolume(0, 0);
            
            videoSurface.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    player.setDisplay(holder);
                    player.prepareAsync();
                }
                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    if (player != null) {
                        player.stop();
                        player.release();
                    }
                }
            });
            
            player.setOnPreparedListener(mp -> {
                mp.start();
            });
            
            player.setOnErrorListener((mp, what, extra) -> {
                Toast.makeText(MainActivity.this, "Video error", Toast.LENGTH_SHORT).show();
                return false;
            });
            
            // Сохраняем MediaPlayer для жизненного цикла
            if (characterMediaPlayer != null) {
                characterMediaPlayer.release();
            }
            characterMediaPlayer = player;
            
            mainCircle.setOnTouchListener((v, event) -> {
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
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isDragging = true;
                        mainCircleParams.x = initialX + (int) dx;
                        mainCircleParams.y = initialY + (int) dy;
                        if (windowManager != null) {
                            windowManager.updateViewLayout(mainCircle, mainCircleParams);
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
            
            windowManager.addView(mainCircle, mainCircleParams);
            Toast.makeText(this, "Animated character loaded", Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error loading animated character", Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== ЗАГРУЗКА ПЕРСОНАЖА НА ЭКРАН ====================

    private void loadCharacterToScreen(CharacterData data) {
        if (data.isVideo) {
            showVideoCharacterWithChromaKey(data.path);
        } else {
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), 
                        Uri.fromFile(new File(data.path)));
                showCharacterOnScreen(bitmap);
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Error loading character", Toast.LENGTH_SHORT).show();
            }
        }
        Toast.makeText(this, "Character displayed", Toast.LENGTH_SHORT).show();
    }

    // ==================== ВИДЕО С CHROMAKEY (ОБРЕЗКА ЗЕЛЕНОГО ФОНА) ====================
    
    private void showVideoCharacterWithChromaKey(String videoPath) {
        if (windowManager == null) return;
        
        if (characterContainer != null && windowManager != null) {
            try {
                windowManager.removeView(characterContainer);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        characterContainer = new FrameLayout(this);
        characterContainer.setBackgroundColor(Color.TRANSPARENT);
        
        // Создаем SurfaceView для видео с ChromaKey
        characterSurfaceView = new SurfaceView(this);
        characterSurfaceView.setZOrderOnTop(true);
        characterSurfaceView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        
        FrameLayout.LayoutParams surfaceParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        characterSurfaceView.setLayoutParams(surfaceParams);
        characterContainer.addView(characterSurfaceView);
        
        characterMediaPlayer = new MediaPlayer();
        try {
            characterMediaPlayer.setDataSource(videoPath);
            characterMediaPlayer.setLooping(true);
            characterMediaPlayer.setVolume(0, 0);
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        characterSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                characterMediaPlayer.setDisplay(holder);
                characterMediaPlayer.prepareAsync();
            }
            
            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
            
            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                if (characterMediaPlayer != null) {
                    characterMediaPlayer.stop();
                    characterMediaPlayer.release();
                }
            }
        });
        
        characterMediaPlayer.setOnPreparedListener(mp -> {
            // Видео загружено, запускаем с ChromaKey обработкой
            mp.start();
        });
        
        characterMediaPlayer.setOnErrorListener((mp, what, extra) -> {
            Toast.makeText(this, "Video error", Toast.LENGTH_SHORT).show();
            return false;
        });
        
        addCharacterControls(characterContainer);
        
        characterParams = new WindowManager.LayoutParams(
                500, 500,
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
        isCharacterVideo = true;
        currentCharacterPath = videoPath;
        
        Toast.makeText(this, "Video character loaded", Toast.LENGTH_SHORT).show();
    }

    private void showCharacterOnScreen(Bitmap bitmap) {
        if (windowManager == null) return;
        
        if (characterContainer != null && windowManager != null) {
            try {
                windowManager.removeView(characterContainer);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        if (characterMediaPlayer != null) {
            try {
                characterMediaPlayer.stop();
                characterMediaPlayer.release();
            } catch (Exception e) {}
            characterMediaPlayer = null;
        }
        
        // Обработка ChromaKey для изображения
        currentCharacterBitmap = removeGreenScreen(bitmap, 40);
        isCharacterVideo = false;
        
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
                500, 500,
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
        
        Toast.makeText(this, "Character displayed", Toast.LENGTH_SHORT).show();
    }

    // ==================== УПРАВЛЕНИЕ ПЕРСОНАЖЕМ ====================

    private void addCharacterControls(FrameLayout container) {
        fixButton = new ImageButton(this);
        fixButton.setImageDrawable(createLockIcon(false));
        GradientDrawable fixBg = new GradientDrawable();
        fixBg.setShape(GradientDrawable.OVAL);
        fixBg.setColor(Color.parseColor("#FF6B00"));
        fixButton.setBackground(fixBg);
        fixButton.setPadding(20, 20, 20, 20);
        
        FrameLayout.LayoutParams fixParams = new FrameLayout.LayoutParams(70, 70, Gravity.TOP | Gravity.END);
        fixParams.setMargins(0, 30, 30, 0);
        fixButton.setLayoutParams(fixParams);
        
        fixButton.setOnClickListener(v -> toggleCharacterFix());
        
        deleteButton = new ImageButton(this);
        deleteButton.setImageDrawable(createDeleteIcon());
        GradientDrawable deleteBg = new GradientDrawable();
        deleteBg.setShape(GradientDrawable.OVAL);
        deleteBg.setColor(Color.RED);
        deleteButton.setBackground(deleteBg);
        deleteButton.setPadding(20, 20, 20, 20);
        
        FrameLayout.LayoutParams deleteParams = new FrameLayout.LayoutParams(70, 70, Gravity.TOP | Gravity.START);
        deleteParams.setMargins(30, 30, 0, 0);
        deleteButton.setLayoutParams(deleteParams);
        
        deleteButton.setOnClickListener(v -> {
            removeCharacter();
            if (mainCircle != null) {
                mainCircle.setVisibility(View.VISIBLE);
            }
            Toast.makeText(this, "Character removed", Toast.LENGTH_SHORT).show();
        });
        
        backButton = new ImageButton(this);
        backButton.setImageDrawable(createBackIcon());
        GradientDrawable backBg = new GradientDrawable();
        backBg.setShape(GradientDrawable.OVAL);
        backBg.setColor(Color.parseColor("#9C27B0"));
        backButton.setBackground(backBg);
        backButton.setPadding(20, 20, 20, 20);
        
        FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(70, 70, Gravity.BOTTOM | Gravity.CENTER);
        backParams.setMargins(0, 0, 0, 30);
        backButton.setLayoutParams(backParams);
        
        backButton.setOnClickListener(v -> {
            removeCharacter();
            if (mainCircle != null) {
                mainCircle.setVisibility(View.VISIBLE);
            }
            Toast.makeText(this, "Return to menu", Toast.LENGTH_SHORT).show();
        });
        
        controlsLayout = new LinearLayout(this);
        controlsLayout.setOrientation(LinearLayout.VERTICAL);
        controlsLayout.setGravity(Gravity.CENTER);
        controlsLayout.setBackgroundColor(Color.parseColor("#AA000000"));
        controlsLayout.setPadding(20, 15, 20, 15);
        
        TextView titleText = new TextView(this);
        titleText.setText("SIZE XYZ");
        titleText.setTextColor(Color.WHITE);
        titleText.setGravity(Gravity.CENTER);
        titleText.setTextSize(16);
        titleText.setTypeface(null, android.graphics.Typeface.BOLD);
        titleText.setPadding(0, 0, 0, 10);
        controlsLayout.addView(titleText);
        
        LinearLayout xLayout = new LinearLayout(this);
        xLayout.setOrientation(LinearLayout.HORIZONTAL);
        xLayout.setGravity(Gravity.CENTER);
        
        sizeXLabel = new TextView(this);
        sizeXLabel.setText("X:");
        sizeXLabel.setTextColor(Color.WHITE);
        sizeXLabel.setPadding(0, 0, 10, 0);
        
        sizeXInput = new EditText(this);
        sizeXInput.setText("500");
        sizeXInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        sizeXInput.setTextColor(Color.WHITE);
        sizeXInput.setBackgroundColor(Color.parseColor("#333333"));
        sizeXInput.setPadding(10, 5, 10, 5);
        sizeXInput.setWidth(80);
        
        SeekBar xSeekBar = new SeekBar(this);
        xSeekBar.setMax(1000);
        xSeekBar.setProgress(500);
        xSeekBar.setMinWidth(100);
        
        xLayout.addView(sizeXLabel);
        xLayout.addView(sizeXInput);
        xLayout.addView(xSeekBar);
        controlsLayout.addView(xLayout);
        
        LinearLayout yLayout = new LinearLayout(this);
        yLayout.setOrientation(LinearLayout.HORIZONTAL);
        yLayout.setGravity(Gravity.CENTER);
        
        sizeYLabel = new TextView(this);
        sizeYLabel.setText("Y:");
        sizeYLabel.setTextColor(Color.WHITE);
        sizeYLabel.setPadding(0, 0, 10, 0);
        
        sizeYInput = new EditText(this);
        sizeYInput.setText("500");
        sizeYInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        sizeYInput.setTextColor(Color.WHITE);
        sizeYInput.setBackgroundColor(Color.parseColor("#333333"));
        sizeYInput.setPadding(10, 5, 10, 5);
        sizeYInput.setWidth(80);
        
        SeekBar ySeekBar = new SeekBar(this);
        ySeekBar.setMax(1000);
        ySeekBar.setProgress(500);
        ySeekBar.setMinWidth(100);
        
        yLayout.addView(sizeYLabel);
        yLayout.addView(sizeYInput);
        yLayout.addView(ySeekBar);
        controlsLayout.addView(yLayout);
        
        LinearLayout zLayout = new LinearLayout(this);
        zLayout.setOrientation(LinearLayout.HORIZONTAL);
        zLayout.setGravity(Gravity.CENTER);
        
        sizeZLabel = new TextView(this);
        sizeZLabel.setText("Z:");
        sizeZLabel.setTextColor(Color.WHITE);
        sizeZLabel.setPadding(0, 0, 10, 0);
        
        sizeZInput = new EditText(this);
        sizeZInput.setText("500");
        sizeZInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        sizeZInput.setTextColor(Color.WHITE);
        sizeZInput.setBackgroundColor(Color.parseColor("#333333"));
        sizeZInput.setPadding(10, 5, 10, 5);
        sizeZInput.setWidth(80);
        
        SeekBar zSeekBar = new SeekBar(this);
        zSeekBar.setMax(1000);
        zSeekBar.setProgress(500);
        zSeekBar.setMinWidth(100);
        
        zLayout.addView(sizeZLabel);
        zLayout.addView(sizeZInput);
        zLayout.addView(zSeekBar);
        controlsLayout.addView(zLayout);
        
        xSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && characterParams != null) {
                    characterParams.width = progress;
                    sizeXInput.setText(String.valueOf(progress));
                    updateCharacterSize();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        sizeXInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                try {
                    int val = Integer.parseInt(sizeXInput.getText().toString());
                    if (val > 0) {
                        characterParams.width = val;
                        xSeekBar.setProgress(Math.min(val, 1000));
                        updateCharacterSize();
                    }
                } catch (NumberFormatException e) {}
            }
        });
        
        ySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && characterParams != null) {
                    characterParams.height = progress;
                    sizeYInput.setText(String.valueOf(progress));
                    updateCharacterSize();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        sizeYInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                try {
                    int val = Integer.parseInt(sizeYInput.getText().toString());
                    if (val > 0) {
                        characterParams.height = val;
                        ySeekBar.setProgress(Math.min(val, 1000));
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
                    float alpha = 0.3f + (progress / 1000f) * 0.7f;
                    if (characterView != null) characterView.setAlpha(alpha);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        sizeZInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                try {
                    int val = Integer.parseInt(sizeZInput.getText().toString());
                    if (val >= 0 && val <= 1000) {
                        zSeekBar.setProgress(val);
                        float alpha = 0.3f + (val / 1000f) * 0.7f;
                        if (characterView != null) characterView.setAlpha(alpha);
                    }
                } catch (NumberFormatException e) {}
            }
        });
        
        FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
        );
        controlsParams.setMargins(0, 0, 0, 120);
        controlsLayout.setLayoutParams(controlsParams);
        
        container.addView(fixButton);
        container.addView(deleteButton);
        container.addView(backButton);
        container.addView(controlsLayout);
        
        if (characterView != null) {
            characterView.setOnTouchListener((v, event) -> {
                if (isCharacterFixed) {
                    return false;
                }
                return handleCharacterTouch(event);
            });
        }
        
        if (characterSurfaceView != null) {
            characterSurfaceView.setOnTouchListener((v, event) -> {
                if (isCharacterFixed) {
                    return false;
                }
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
                        if (newWidth > 50 && newHeight > 50) {
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
            Toast.makeText(this, "Character fixed", Toast.LENGTH_SHORT).show();
            fixButton.setImageDrawable(createLockIcon(true));
            hideAllControls();
            
            if (characterParams != null && windowManager != null) {
                characterParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                windowManager.updateViewLayout(characterContainer, characterParams);
            }
            
            if (characterContainer != null) {
                characterContainer.setAlpha(0.7f);
                characterContainer.setClickable(false);
                characterContainer.setFocusable(false);
            }
            if (characterView != null) {
                characterView.setClickable(false);
                characterView.setFocusable(false);
            }
            if (characterSurfaceView != null) {
                characterSurfaceView.setClickable(false);
                characterSurfaceView.setFocusable(false);
            }
            
        } else {
            Toast.makeText(this, "Character unlocked", Toast.LENGTH_SHORT).show();
            fixButton.setImageDrawable(createLockIcon(false));
            showAllControls();
            
            if (characterParams != null && windowManager != null) {
                characterParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                windowManager.updateViewLayout(characterContainer, characterParams);
            }
            
            if (characterContainer != null) {
                characterContainer.setAlpha(1.0f);
                characterContainer.setClickable(true);
                characterContainer.setFocusable(true);
            }
            if (characterView != null) {
                characterView.setClickable(true);
                characterView.setFocusable(true);
            }
            if (characterSurfaceView != null) {
                characterSurfaceView.setClickable(true);
                characterSurfaceView.setFocusable(true);
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
            characterSurfaceView = null;
            if (characterMediaPlayer != null) {
                try {
                    characterMediaPlayer.stop();
                    characterMediaPlayer.release();
                } catch (Exception e) {}
                characterMediaPlayer = null;
            }
            currentCharacterBitmap = null;
            isCharacterModeActive = false;
            isCharacterFixed = false;
            isCharacterVideo = false;
        }
    }

    // ==================== CHROMAKEY (ОБРЕЗКА ЗЕЛЕНОГО ФОНА) ====================

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
                
                // Обрезка зеленого фона
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
        Bitmap b = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(6);
        
        float cx = 40, cy = 40;
        c.drawArc(cx - 25, cy - 35, cx + 25, cy - 5, 0, 180, false, p);
        c.drawRect(cx - 20, cy - 10, cx + 20, cy + 25, p);
        p.setStyle(Paint.Style.FILL);
        p.setStrokeWidth(0);
        c.drawCircle(cx, cy + 8, 5, p);
        
        if (locked) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(8);
            p.setColor(Color.YELLOW);
            c.drawLine(cx - 30, cy - 20, cx + 30, cy + 30, p);
            c.drawLine(cx + 30, cy - 20, cx - 30, cy + 30, p);
        }
        
        return new android.graphics.drawable.BitmapDrawable(getResources(), b);
    }

    private Drawable createDeleteIcon() {
        Bitmap b = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(8);
        
        float cx = 40, cy = 40;
        c.drawLine(cx - 25, cy - 20, cx + 25, cy - 20, p);
        c.drawLine(cx - 15, cy - 30, cx + 15, cy - 30, p);
        c.drawLine(cx - 25, cy - 20, cx - 25, cy + 20, p);
        c.drawLine(cx + 25, cy - 20, cx + 25, cy + 20, p);
        c.drawArc(cx - 20, cy - 35, cx + 20, cy - 15, 0, 180, false, p);
        p.setStrokeWidth(6);
        c.drawLine(cx - 12, cy, cx + 12, cy, p);
        c.drawLine(cx, cy - 12, cx, cy + 12, p);
        
        return new android.graphics.drawable.BitmapDrawable(getResources(), b);
    }

    private Drawable createBackIcon() {
        Bitmap b = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(8);
        
        float cx = 40, cy = 40;
        c.drawLine(cx + 20, cy - 20, cx - 20, cy, p);
        c.drawLine(cx + 20, cy + 20, cx - 20, cy, p);
        c.drawLine(cx + 20, cy - 20, cx + 20, cy + 20, p);
        
        return new android.graphics.drawable.BitmapDrawable(getResources(), b);
    }

    // ==================== WEBVIEW ОВЕРЛЕЙ ====================

    private void showMainOverlay() {
        if (isMainOverlayVisible) return;
        int flag = getOverlayFlag();

        mainOverlay = new FrameLayout(this);
        mainOverlay.setBackgroundColor(Color.parseColor("#DD000000"));
        mainOverlay.setPadding(15, 15, 15, 15);

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

        webView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        ImageButton closeBtn = createCircleButton(createCloseIcon(), "#CC0000");
        FrameLayout.LayoutParams closeP = new FrameLayout.LayoutParams(70, 70, Gravity.TOP | Gravity.START);
        closeP.setMargins(20, 40, 0, 0);
        closeBtn.setLayoutParams(closeP);
        closeBtn.setOnClickListener(v -> {
            hideMainOverlay();
            if (mainCircle != null) {
                mainCircle.setVisibility(View.VISIBLE);
            }
        });

        ImageButton minimizeBtn = createCircleButton(createMinimizeIcon(), "#4CAF50");
        FrameLayout.LayoutParams minP = new FrameLayout.LayoutParams(70, 70, Gravity.TOP | Gravity.END);
        minP.setMargins(0, 40, 20, 0);
        minimizeBtn.setLayoutParams(minP);
        minimizeBtn.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            webView.saveState(bundle);
            webViewState = bundle;
            hideMainOverlay();
            if (mainCircle != null) {
                mainCircle.setVisibility(View.VISIBLE);
            }
        });

        ImageButton deleteCharacterBtn = createCircleButton(createDeleteIcon(), "#FF0000");
        FrameLayout.LayoutParams deleteCharP = new FrameLayout.LayoutParams(70, 70, Gravity.BOTTOM | Gravity.END);
        deleteCharP.setMargins(0, 0, 30, 100);
        deleteCharacterBtn.setLayoutParams(deleteCharP);
        deleteCharacterBtn.setOnClickListener(v -> {
            removeCharacter();
            Toast.makeText(this, "Character removed", Toast.LENGTH_SHORT).show();
        });

        mainOverlay.addView(webView);
        mainOverlay.addView(closeBtn);
        mainOverlay.addView(minimizeBtn);
        mainOverlay.addView(deleteCharacterBtn);

        mainOverlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                flag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        mainOverlayParams.gravity = Gravity.CENTER;

        if (windowManager != null) {
            windowManager.addView(mainOverlay, mainOverlayParams);
            isMainOverlayVisible = true;
        }
    }

    private void hideMainOverlay() {
        if (mainOverlay != null && windowManager != null && isMainOverlayVisible) {
            windowManager.removeView(mainOverlay);
            mainOverlay = null;
            isMainOverlayVisible = false;
        }
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    private int getOverlayFlag() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;
    }

    private ImageButton createCircleButton(Drawable icon, String color) {
        ImageButton btn = new ImageButton(this);
        btn.setImageDrawable(icon);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(Color.parseColor(color));
        d.setStroke(4, Color.WHITE);
        btn.setBackground(d);
        btn.setPadding(20, 20, 20, 20);
        btn.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        return btn;
    }

    private Drawable createCloseIcon() {
        Bitmap b = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.WHITE);
        p.setStrokeWidth(8);
        p.setStyle(Paint.Style.STROKE);
        float cx = 30, cy = 30, o = 15;
        c.drawLine(cx - o, cy - o, cx + o, cy + o, p);
        c.drawLine(cx + o, cy - o, cx - o, cy + o, p);
        return new android.graphics.drawable.BitmapDrawable(getResources(), b);
    }

    private Drawable createMinimizeIcon() {
        Bitmap b = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.WHITE);
        p.setStrokeWidth(8);
        p.setStyle(Paint.Style.STROKE);
        float cx = 30, cy = 30, w = 25, h = 15;
        c.drawRect(cx - w/2, cy - h/2, cx + w/2, cy + h/2, p);
        return new android.graphics.drawable.BitmapDrawable(getResources(), b);
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
        if (mainCircle != null && !isMainOverlayVisible) {
            mainCircle.setVisibility(View.VISIBLE);
        }
        if (characterMediaPlayer != null && isCharacterModeActive && isCharacterVideo) {
            characterMediaPlayer.start();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (characterMediaPlayer != null && isCharacterModeActive && isCharacterVideo) {
            try {
                characterMediaPlayer.pause();
            } catch (Exception e) {}
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        if (characterMediaPlayer != null) {
            try {
                characterMediaPlayer.stop();
                characterMediaPlayer.release();
            } catch (Exception e) {}
            characterMediaPlayer = null;
        }
        
        if (mainCircle != null && windowManager != null) {
            try {
                windowManager.removeView(mainCircle);
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
                Toast.makeText(this, "Microphone granted", Toast.LENGTH_SHORT).show();
            }
        }
        if (code == REQUEST_CAMERA && results.length > 0) {
            if (results[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera granted", Toast.LENGTH_SHORT).show();
            }
        }
        if (code == REQUEST_STORAGE && results.length > 0) {
            if (results[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Storage granted", Toast.LENGTH_SHORT).show();
            }
        }
        if (code == REQUEST_VIDEO_GALLERY && results.length > 0) {
            if (results[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Video access granted", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(this, "Character saved", Toast.LENGTH_SHORT).show();
                }
                showCharacterListFullscreen();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
            }
        }
        
        if (requestCode == REQUEST_VIDEO_GALLERY && resultCode == RESULT_OK && data != null) {
            Uri videoUri = data.getData();
            String path = saveVideoToStorage(videoUri);
            if (path != null) {
                characters.add(new CharacterData(tempCharacterName, path, true));
                saveCharacters();
                Toast.makeText(this, "Video character saved", Toast.LENGTH_SHORT).show();
            }
            showCharacterListFullscreen();
        }
        
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
