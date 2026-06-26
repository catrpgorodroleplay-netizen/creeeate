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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
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
import androidx.core.content.FileProvider;

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

    private FrameLayout characterContainer;
    private ImageView characterView;
    private VideoView characterVideoView;
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
    private int selectedCharacterIndex = 0;
    private boolean isCharacterListOpen = false;

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
        d.setStroke(6, Color.parseColor("#FF6666"));
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
            Toast.makeText(this, "🎮 Главный кружок создан", Toast.LENGTH_SHORT).show();
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
        menuContainer = new FrameLayout(this);
        menuContainer.setBackgroundColor(Color.parseColor("#CC000000"));
        
        LinearLayout menuLayout = new LinearLayout(this);
        menuLayout.setOrientation(LinearLayout.VERTICAL);
        menuLayout.setGravity(Gravity.CENTER);
        menuLayout.setPadding(50, 50, 50, 50);
        
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(30);
        bg.setColor(Color.parseColor("#DD1E1E1E"));
        bg.setStroke(4, Color.WHITE);
        menuLayout.setBackground(bg);
        
        // Заголовок
        TextView title = new TextView(this);
        title.setText("🎮 Меню");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 30);
        menuLayout.addView(title);
        
        // Кнопка персонажей
        Button charactersBtn = new Button(this);
        charactersBtn.setText("👥 Персонажи");
        charactersBtn.setTextColor(Color.WHITE);
        charactersBtn.setBackgroundColor(Color.parseColor("#2196F3"));
        charactersBtn.setPadding(30, 20, 30, 20);
        charactersBtn.setOnClickListener(v -> {
            removeMenu();
            showCharacterList();
        });
        
        // Кнопка WebView
        Button webBtn = new Button(this);
        webBtn.setText("🌐 Открыть WebView");
        webBtn.setTextColor(Color.WHITE);
        webBtn.setBackgroundColor(Color.parseColor("#FF9800"));
        webBtn.setPadding(30, 20, 30, 20);
        webBtn.setOnClickListener(v -> {
            removeMenu();
            showMainOverlay();
        });
        
        // Кнопка закрыть
        Button closeBtn = new Button(this);
        closeBtn.setText("❌ Закрыть");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setBackgroundColor(Color.parseColor("#F44336"));
        closeBtn.setPadding(30, 20, 30, 20);
        closeBtn.setOnClickListener(v -> removeMenu());
        
        int margin = 20;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, margin, 0, margin);
        
        menuLayout.addView(charactersBtn, params);
        menuLayout.addView(webBtn, params);
        menuLayout.addView(closeBtn, params);
        
        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
                400,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
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

    // ==================== СПИСОК ПЕРСОНАЖЕЙ ====================

    private void showCharacterList() {
        isCharacterListOpen = true;
        characterListContainer = new FrameLayout(this);
        characterListContainer.setBackgroundColor(Color.parseColor("#CC000000"));
        
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(50, 50, 50, 50);
        
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(30);
        bg.setColor(Color.parseColor("#DD1E1E1E"));
        bg.setStroke(4, Color.WHITE);
        mainLayout.setBackground(bg);
        
        // Заголовок
        LinearLayout headerLayout = new LinearLayout(this);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(Gravity.CENTER_VERTICAL);
        
        TextView title = new TextView(this);
        title.setText("👥 Мои персонажи");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        headerLayout.addView(title);
        
        // Кнопка добавления
        Button addBtn = new Button(this);
        addBtn.setText("➕");
        addBtn.setTextColor(Color.WHITE);
        addBtn.setBackgroundColor(Color.parseColor("#4CAF50"));
        addBtn.setPadding(20, 10, 20, 10);
        addBtn.setOnClickListener(v -> showAddCharacterDialog());
        headerLayout.addView(addBtn);
        
        mainLayout.addView(headerLayout);
        
        // Разделитель
        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#666666"));
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2));
        divider.setPadding(0, 20, 0, 20);
        mainLayout.addView(divider);
        
        // Список персонажей
        ScrollView scrollView = new ScrollView(this);
        LinearLayout listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        
        if (characters.isEmpty()) {
            TextView emptyText = new TextView(this);
            emptyText.setText("😕 Нет персонажей\nНажмите ➕ чтобы добавить");
            emptyText.setTextColor(Color.parseColor("#AAAAAA"));
            emptyText.setTextSize(18);
            emptyText.setGravity(Gravity.CENTER);
            emptyText.setPadding(0, 100, 0, 100);
            listLayout.addView(emptyText);
        } else {
            for (int i = 0; i < characters.size(); i++) {
                CharacterData data = characters.get(i);
                LinearLayout itemLayout = createCharacterItem(data, i);
                listLayout.addView(itemLayout);
            }
        }
        
        scrollView.addView(listLayout);
        mainLayout.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 500));
        
        // Кнопка закрыть
        Button closeBtn = new Button(this);
        closeBtn.setText("❌ Закрыть");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setBackgroundColor(Color.parseColor("#F44336"));
        closeBtn.setPadding(30, 20, 30, 20);
        closeBtn.setOnClickListener(v -> {
            removeCharacterList();
            showMainMenu();
        });
        
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        closeParams.setMargins(0, 20, 0, 0);
        mainLayout.addView(closeBtn, closeParams);
        
        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
                500,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
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

    private LinearLayout createCharacterItem(CharacterData data, int index) {
        LinearLayout itemLayout = new LinearLayout(this);
        itemLayout.setOrientation(LinearLayout.HORIZONTAL);
        itemLayout.setGravity(Gravity.CENTER_VERTICAL);
        itemLayout.setBackgroundColor(Color.parseColor("#333333"));
        itemLayout.setPadding(20, 20, 20, 20);
        
        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        itemParams.setMargins(0, 10, 0, 10);
        itemLayout.setLayoutParams(itemParams);
        
        GradientDrawable itemBg = new GradientDrawable();
        itemBg.setCornerRadius(15);
        itemBg.setColor(Color.parseColor("#333333"));
        itemLayout.setBackground(itemBg);
        
        // Иконка типа
        TextView typeIcon = new TextView(this);
        typeIcon.setText(data.isVideo ? "🎬" : "🖼️");
        typeIcon.setTextSize(28);
        typeIcon.setPadding(10, 0, 20, 0);
        itemLayout.addView(typeIcon);
        
        // Имя
        LinearLayout nameLayout = new LinearLayout(this);
        nameLayout.setOrientation(LinearLayout.VERTICAL);
        nameLayout.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        
        TextView nameText = new TextView(this);
        nameText.setText(data.name);
        nameText.setTextColor(Color.WHITE);
        nameText.setTextSize(18);
        nameLayout.addView(nameText);
        
        TextView typeText = new TextView(this);
        typeText.setText(data.isVideo ? "Видео" : "Изображение");
        typeText.setTextColor(Color.parseColor("#AAAAAA"));
        typeText.setTextSize(14);
        nameLayout.addView(typeText);
        
        itemLayout.addView(nameLayout);
        
        // Кнопки действий
        // На плавающее окно
        Button floatBtn = new Button(this);
        floatBtn.setText("🔄 Окно");
        floatBtn.setTextColor(Color.WHITE);
        floatBtn.setBackgroundColor(Color.parseColor("#2196F3"));
        floatBtn.setTextSize(12);
        floatBtn.setPadding(15, 10, 15, 10);
        floatBtn.setOnClickListener(v -> {
            loadCharacterToFloat(data);
            removeCharacterList();
        });
        itemLayout.addView(floatBtn);
        
        // На экран
        Button screenBtn = new Button(this);
        screenBtn.setText("📺 Экран");
        screenBtn.setTextColor(Color.WHITE);
        screenBtn.setBackgroundColor(Color.parseColor("#FF9800"));
        screenBtn.setTextSize(12);
        screenBtn.setPadding(15, 10, 15, 10);
        screenBtn.setOnClickListener(v -> {
            loadCharacterToScreen(data);
            removeCharacterList();
        });
        itemLayout.addView(screenBtn);
        
        // Удалить
        Button deleteBtn = new Button(this);
        deleteBtn.setText("🗑️");
        deleteBtn.setTextColor(Color.WHITE);
        deleteBtn.setBackgroundColor(Color.parseColor("#F44336"));
        deleteBtn.setTextSize(16);
        deleteBtn.setPadding(15, 10, 15, 10);
        deleteBtn.setOnClickListener(v -> {
            characters.remove(index);
            saveCharacters();
            removeCharacterList();
            showCharacterList();
        });
        itemLayout.addView(deleteBtn);
        
        return itemLayout;
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

    // ==================== ДОБАВЛЕНИЕ ПЕРСОНАЖА ====================

    private void showAddCharacterDialog() {
        removeCharacterList();
        
        menuContainer = new FrameLayout(this);
        menuContainer.setBackgroundColor(Color.parseColor("#CC000000"));
        
        LinearLayout menuLayout = new LinearLayout(this);
        menuLayout.setOrientation(LinearLayout.VERTICAL);
        menuLayout.setGravity(Gravity.CENTER);
        menuLayout.setPadding(50, 50, 50, 50);
        
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(30);
        bg.setColor(Color.parseColor("#DD1E1E1E"));
        bg.setStroke(4, Color.WHITE);
        menuLayout.setBackground(bg);
        
        TextView title = new TextView(this);
        title.setText("➕ Добавить персонажа");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 20);
        menuLayout.addView(title);
        
        // Поле ввода имени
        EditText nameInput = new EditText(this);
        nameInput.setHint("Введите имя персонажа");
        nameInput.setHintTextColor(Color.parseColor("#888888"));
        nameInput.setTextColor(Color.WHITE);
        nameInput.setBackgroundColor(Color.parseColor("#333333"));
        nameInput.setPadding(20, 15, 20, 15);
        nameInput.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        menuLayout.addView(nameInput);
        
        LinearLayout buttonLayout = new LinearLayout(this);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setGravity(Gravity.CENTER);
        buttonLayout.setPadding(0, 20, 0, 0);
        
        Button imageBtn = new Button(this);
        imageBtn.setText("🖼️ Изображение");
        imageBtn.setTextColor(Color.WHITE);
        imageBtn.setBackgroundColor(Color.parseColor("#2196F3"));
        imageBtn.setPadding(20, 15, 20, 15);
        imageBtn.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Введите имя персонажа", Toast.LENGTH_SHORT).show();
                return;
            }
            removeMenu();
            openGalleryForCharacter(name, false);
        });
        
        Button videoBtn = new Button(this);
        videoBtn.setText("🎬 Видео");
        videoBtn.setTextColor(Color.WHITE);
        videoBtn.setBackgroundColor(Color.parseColor("#FF9800"));
        videoBtn.setPadding(20, 15, 20, 15);
        videoBtn.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Введите имя персонажа", Toast.LENGTH_SHORT).show();
                return;
            }
            removeMenu();
            openVideoGalleryForCharacter(name);
        });
        
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        btnParams.setMargins(5, 0, 5, 0);
        buttonLayout.addView(imageBtn, btnParams);
        buttonLayout.addView(videoBtn, btnParams);
        menuLayout.addView(buttonLayout);
        
        Button cancelBtn = new Button(this);
        cancelBtn.setText("❌ Отмена");
        cancelBtn.setTextColor(Color.WHITE);
        cancelBtn.setBackgroundColor(Color.parseColor("#F44336"));
        cancelBtn.setPadding(30, 15, 30, 15);
        cancelBtn.setOnClickListener(v -> {
            removeMenu();
            showCharacterList();
        });
        
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cancelParams.setMargins(0, 20, 0, 0);
        menuLayout.addView(cancelBtn, cancelParams);
        
        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
                400,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
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

    private String tempCharacterName = "";

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

    // ==================== ЗАГРУЗКА ПЕРСОНАЖА ====================

    private void loadCharacterToFloat(CharacterData data) {
        // Загружаем персонажа в плавающее окно (заменяет главный кружок)
        if (data.isVideo) {
            // Видео пока не поддерживаем для плавающего окна
            Toast.makeText(this, "Видео пока не поддерживается для плавающего окна", Toast.LENGTH_LONG).show();
            return;
        }
        
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), 
                    Uri.fromFile(new File(data.path)));
            Bitmap processed = removeGreenScreen(bitmap, 40);
            
            // Создаем новый кружок с персонажем
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
            Toast.makeText(this, "✅ Персонаж загружен в плавающее окно!", Toast.LENGTH_SHORT).show();
            
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "❌ Ошибка загрузки персонажа", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadCharacterToScreen(CharacterData data) {
        if (data.isVideo) {
            // Загрузка видео
            showVideoCharacter(data.path);
        } else {
            // Загрузка изображения
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), 
                        Uri.fromFile(new File(data.path)));
                showCharacterOnScreen(bitmap);
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "❌ Ошибка загрузки персонажа", Toast.LENGTH_SHORT).show();
            }
        }
        Toast.makeText(this, "✅ Персонаж загружен на экран!", Toast.LENGTH_SHORT).show();
    }

    // ==================== ВИДЕО ПЕРСОНАЖ ====================

    private void showVideoCharacter(String videoPath) {
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
        
        characterVideoView = new VideoView(this);
        characterVideoView.setVideoPath(videoPath);
        characterVideoView.setZOrderOnTop(true);
        characterVideoView.setZOrderMediaOverlay(true);
        
        // Запускаем видео с зацикливанием
        characterVideoView.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            characterVideoView.start();
        });
        
        FrameLayout.LayoutParams videoParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        characterContainer.addView(characterVideoView, videoParams);
        
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
        
        // Запускаем видео
        characterVideoView.start();
        
        Toast.makeText(this, "🎬 Видео персонаж загружен!", Toast.LENGTH_SHORT).show();
    }

    // ==================== ПОКАЗ ПЕРСОНАЖА НА ЭКРАНЕ ====================

    private void showCharacterOnScreen(Bitmap bitmap) {
        if (windowManager == null) return;
        
        if (characterContainer != null && windowManager != null) {
            try {
                windowManager.removeView(characterContainer);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
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
        
        Toast.makeText(this, "✅ Персонаж загружен!", Toast.LENGTH_SHORT).show();
    }

    // ==================== УПРАВЛЕНИЕ ПЕРСОНАЖЕМ ====================

    private void addCharacterControls(FrameLayout container) {
        // Кнопка фиксации
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
        
        fixButton.setOnClickListener(v -> {
            isCharacterFixed = !isCharacterFixed;
            if (isCharacterFixed) {
                Toast.makeText(this, "🔒 Персонаж зафиксирован!", Toast.LENGTH_SHORT).show();
                fixButton.setImageDrawable(createLockIcon(true));
                hideAllControls();
                setCharacterTouchThrough(true);
            } else {
                Toast.makeText(this, "🔓 Персонаж разблокирован", Toast.LENGTH_SHORT).show();
                fixButton.setImageDrawable(createLockIcon(false));
                showAllControls();
                setCharacterTouchThrough(false);
            }
        });
        
        // Кнопка удаления
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
            Toast.makeText(this, "🗑️ Персонаж удален", Toast.LENGTH_SHORT).show();
        });
        
        // Кнопка назад
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
            Toast.makeText(this, "⬅️ Возврат к меню", Toast.LENGTH_SHORT).show();
        });
        
        // Управление размером
        controlsLayout = new LinearLayout(this);
        controlsLayout.setOrientation(LinearLayout.VERTICAL);
        controlsLayout.setGravity(Gravity.CENTER);
        controlsLayout.setBackgroundColor(Color.parseColor("#AA000000"));
        controlsLayout.setPadding(20, 15, 20, 15);
        
        TextView titleText = new TextView(this);
        titleText.setText("📐 Размеры XYZ");
        titleText.setTextColor(Color.WHITE);
        titleText.setGravity(Gravity.CENTER);
        titleText.setTextSize(16);
        titleText.setPadding(0, 0, 0, 10);
        controlsLayout.addView(titleText);
        
        // X
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
        
        // Y
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
        
        // Z
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
        
        // Слушатели
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
        
        // Настройка перемещения
        if (characterView != null) {
            characterView.setOnTouchListener((v, event) -> {
                if (isCharacterFixed) {
                    return false;
                }
                
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
                                if (newWidth > 10 && newHeight > 10) {
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
            });
        }
    }

    // ==================== ПРОПУСК КАСАНИЙ ====================
    
    private void setCharacterTouchThrough(boolean enabled) {
        if (characterContainer == null) return;
        
        if (enabled) {
            characterContainer.setAlpha(0.7f);
            characterContainer.setClickable(false);
            characterContainer.setFocusable(false);
            characterContainer.setEnabled(false);
            if (characterView != null) {
                characterView.setClickable(false);
                characterView.setFocusable(false);
                characterView.setEnabled(false);
            }
            if (characterVideoView != null) {
                characterVideoView.setClickable(false);
                characterVideoView.setFocusable(false);
                characterVideoView.setEnabled(false);
            }
        } else {
            characterContainer.setAlpha(1.0f);
            characterContainer.setClickable(true);
            characterContainer.setFocusable(true);
            characterContainer.setEnabled(true);
            if (characterView != null) {
                characterView.setClickable(true);
                characterView.setFocusable(true);
                characterView.setEnabled(true);
            }
            if (characterVideoView != null) {
                characterVideoView.setClickable(true);
                characterVideoView.setFocusable(true);
                characterVideoView.setEnabled(true);
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
            characterVideoView = null;
            currentCharacterBitmap = null;
            isCharacterModeActive = false;
            isCharacterFixed = false;
            isCharacterVideo = false;
        }
    }

    // ==================== CHROMAKEY ====================

    private Bitmap removeGreenScreen(Bitmap source, int tolerance) {
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
        mainOverlay.setBackgroundColor(Color.parseColor("#DD1E1E1E"));
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

        // Кнопка закрыть
        ImageButton closeBtn = createCircleButton(createCloseIcon(), "#DD2C00");
        FrameLayout.LayoutParams closeP = new FrameLayout.LayoutParams(70, 70, Gravity.TOP | Gravity.START);
        closeP.setMargins(20, 40, 0, 0);
        closeBtn.setLayoutParams(closeP);
        closeBtn.setOnClickListener(v -> {
            hideMainOverlay();
            if (mainCircle != null) {
                mainCircle.setVisibility(View.VISIBLE);
            }
        });

        // Кнопка свернуть
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

        // Кнопка удаления персонажа из оверлея
        ImageButton deleteCharacterBtn = createCircleButton(createDeleteIcon(), "#FF0000");
        FrameLayout.LayoutParams deleteCharP = new FrameLayout.LayoutParams(70, 70, Gravity.BOTTOM | Gravity.END);
        deleteCharP.setMargins(0, 0, 30, 100);
        deleteCharacterBtn.setLayoutParams(deleteCharP);
        deleteCharacterBtn.setOnClickListener(v -> {
            removeCharacter();
            Toast.makeText(this, "🗑️ Персонаж удален", Toast.LENGTH_SHORT).show();
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
        // Возобновляем видео если оно было
        if (characterVideoView != null && isCharacterModeActive) {
            characterVideoView.resume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Паузим видео если оно есть
        if (characterVideoView != null && isCharacterModeActive) {
            characterVideoView.pause();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
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
                Toast.makeText(this, "🎤 Микрофон разрешён", Toast.LENGTH_SHORT).show();
            }
        }
        if (code == REQUEST_CAMERA && results.length > 0) {
            if (results[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "📷 Камера разрешена", Toast.LENGTH_SHORT).show();
            }
        }
        if (code == REQUEST_STORAGE && results.length > 0) {
            if (results[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "📁 Доступ к хранилищу разрешен", Toast.LENGTH_SHORT).show();
            }
        }
        if (code == REQUEST_VIDEO_GALLERY && results.length > 0) {
            if (results[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "🎬 Доступ к видео разрешен", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(this, "✅ Персонаж сохранен!", Toast.LENGTH_SHORT).show();
                }
                // Показываем список персонажей
                showCharacterList();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "❌ Ошибка загрузки изображения", Toast.LENGTH_SHORT).show();
            }
        }
        
        if (requestCode == REQUEST_VIDEO_GALLERY && resultCode == RESULT_OK && data != null) {
            Uri videoUri = data.getData();
            String path = saveVideoToStorage(videoUri);
            if (path != null) {
                characters.add(new CharacterData(tempCharacterName, path, true));
                saveCharacters();
                Toast.makeText(this, "✅ Видео персонаж сохранен!", Toast.LENGTH_SHORT).show();
            }
            showCharacterList();
        }
        
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    createMainCircle();
                } else {
                    Toast.makeText(this, "❌ Нужно разрешение на поверхность!", Toast.LENGTH_LONG).show();
                }
            }
        }
    }
            }
