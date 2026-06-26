package com.voice.app;

import android.Manifest;
import android.content.Intent;
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
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.BridgeActivity;

import java.io.IOException;

public class MainActivity extends BridgeActivity {

    private static final int REQUEST_MICROPHONE = 100;
    private static final int REQUEST_CAMERA = 102;
    private static final int REQUEST_OVERLAY_PERMISSION = 101;
    private static final int REQUEST_GALLERY = 103;
    private static final int REQUEST_STORAGE = 104;

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
    private WindowManager.LayoutParams characterParams;
    private Bitmap currentCharacterBitmap;
    private boolean isCharacterFixed = false;
    private boolean isCharacterModeActive = false;
    
    private float lastTouchX, lastTouchY;
    private float initialPinchDistance = 0;
    
    private FrameLayout menuContainer;
    
    private LinearLayout controlsLayout;
    private ImageButton fixButton;
    private ImageButton deleteButton;
    private ImageButton backButton;
    private SeekBar sizeSeekBar;
    private EditText sizeXInput, sizeYInput, sizeZInput;
    private TextView sizeXLabel, sizeYLabel, sizeZLabel;
    
    // Флаг что персонаж в режиме удаления
    private boolean isDeleteMode = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
                        showCharacterMenu();
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

    private void showCharacterMenu() {
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
        
        Button loadBtn = new Button(this);
        loadBtn.setText("📁 Загрузить персонажа");
        loadBtn.setTextColor(Color.WHITE);
        loadBtn.setBackgroundColor(Color.parseColor("#2196F3"));
        loadBtn.setPadding(30, 20, 30, 20);
        loadBtn.setOnClickListener(v -> {
            removeCharacterMenu();
            openGallery();
        });
        
        Button exampleBtn = new Button(this);
        exampleBtn.setText("🎨 Использовать пример");
        exampleBtn.setTextColor(Color.WHITE);
        exampleBtn.setBackgroundColor(Color.parseColor("#4CAF50"));
        exampleBtn.setPadding(30, 20, 30, 20);
        exampleBtn.setOnClickListener(v -> {
            removeCharacterMenu();
            loadExampleCharacter();
        });
        
        Button webBtn = new Button(this);
        webBtn.setText("🌐 Открыть WebView");
        webBtn.setTextColor(Color.WHITE);
        webBtn.setBackgroundColor(Color.parseColor("#FF9800"));
        webBtn.setPadding(30, 20, 30, 20);
        webBtn.setOnClickListener(v -> {
            removeCharacterMenu();
            showMainOverlay();
        });
        
        // Кнопка удаления персонажа из оверлея
        Button deleteCharacterBtn = new Button(this);
        deleteCharacterBtn.setText("🗑️ Удалить персонажа");
        deleteCharacterBtn.setTextColor(Color.WHITE);
        deleteCharacterBtn.setBackgroundColor(Color.parseColor("#F44336"));
        deleteCharacterBtn.setPadding(30, 20, 30, 20);
        deleteCharacterBtn.setOnClickListener(v -> {
            removeCharacterMenu();
            removeCharacterFromOverlay();
        });
        
        Button closeBtn = new Button(this);
        closeBtn.setText("❌ Закрыть");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setBackgroundColor(Color.parseColor("#666666"));
        closeBtn.setPadding(30, 20, 30, 20);
        closeBtn.setOnClickListener(v -> removeCharacterMenu());
        
        int margin = 20;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, margin, 0, margin);
        
        menuLayout.addView(loadBtn, params);
        menuLayout.addView(exampleBtn, params);
        menuLayout.addView(webBtn, params);
        menuLayout.addView(deleteCharacterBtn, params);
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

    private void removeCharacterMenu() {
        if (menuContainer != null && windowManager != null) {
            try {
                windowManager.removeView(menuContainer);
                menuContainer = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (mainCircle != null) {
            mainCircle.setVisibility(View.VISIBLE);
        }
    }

    private void removeCharacterFromOverlay() {
        // Удаляем персонажа с экрана
        if (characterContainer != null && windowManager != null) {
            try {
                windowManager.removeView(characterContainer);
            } catch (Exception e) {
                e.printStackTrace();
            }
            characterContainer = null;
            characterView = null;
            currentCharacterBitmap = null;
            isCharacterModeActive = false;
            isCharacterFixed = false;
            Toast.makeText(this, "🗑️ Персонаж удален", Toast.LENGTH_SHORT).show();
        }
        if (mainCircle != null) {
            mainCircle.setVisibility(View.VISIBLE);
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_GALLERY);
    }

    private void loadExampleCharacter() {
        Bitmap testCharacter = createTestCharacter();
        showCharacterOnScreen(testCharacter);
    }

    private Bitmap createTestCharacter() {
        int size = 400;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        
        paint.setColor(Color.parseColor("#00FF00"));
        canvas.drawRect(0, 0, size, size, paint);
        
        paint.setColor(Color.RED);
        canvas.drawCircle(size/2, size/3, 80, paint);
        
        paint.setColor(Color.WHITE);
        canvas.drawCircle(size/2 - 35, size/3 - 20, 25, paint);
        canvas.drawCircle(size/2 + 35, size/3 - 20, 25, paint);
        paint.setColor(Color.BLACK);
        canvas.drawCircle(size/2 - 35, size/3 - 15, 12, paint);
        canvas.drawCircle(size/2 + 35, size/3 - 15, 12, paint);
        
        paint.setColor(Color.BLACK);
        paint.setStrokeWidth(5);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawArc(size/2 - 50, size/3, size/2 + 50, size/3 + 60, 0, 180, false, paint);
        
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.BLUE);
        canvas.drawRect(size/2 - 50, size/3 + 90, size/2 - 20, size/3 + 160, paint);
        canvas.drawRect(size/2 + 20, size/3 + 90, size/2 + 50, size/3 + 160, paint);
        
        return bitmap;
    }

    private void showCharacterOnScreen(Bitmap bitmap) {
        if (windowManager == null) return;
        
        // НЕ удаляем основной оверлей, а только персонажа если есть
        if (characterContainer != null && windowManager != null) {
            try {
                windowManager.removeView(characterContainer);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        currentCharacterBitmap = removeGreenScreen(bitmap, 40);
        
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
        
        // НЕ скрываем главный кружок
        if (mainCircle != null) {
            mainCircle.setVisibility(View.VISIBLE);
        }
        
        Toast.makeText(this, "✅ Персонаж загружен!", Toast.LENGTH_SHORT).show();
    }

    private void addCharacterControls(FrameLayout container) {
        // Кнопка фиксации - показываем всегда
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
                // Скрываем ВСЕ кнопки управления при фиксации
                hideAllControls();
                // Делаем персонажа полностью прозрачным для тачей
                characterView.setClickable(false);
                characterView.setFocusable(false);
                characterView.setAlpha(0.3f);
            } else {
                Toast.makeText(this, "🔓 Персонаж разблокирован", Toast.LENGTH_SHORT).show();
                fixButton.setImageDrawable(createLockIcon(false));
                // Показываем кнопки
                showAllControls();
                characterView.setClickable(true);
                characterView.setFocusable(true);
                characterView.setAlpha(1.0f);
            }
        });
        
        // Кнопка удаления - скрываем при фиксации
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
            // Удаляем персонажа через основной оверлей
            removeCharacterFromOverlay();
        });
        
        // Кнопка назад - скрываем при фиксации
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
            removeCharacterFromOverlay();
        });
        
        // Управление размером XYZ - скрываем при фиксации
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
        
        // Строка X
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
        
        // Строка Y
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
        
        // Строка Z (глубина)
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
        
        // Слушатели для X
        xSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && characterParams != null) {
                    characterParams.width = Math.max(10, progress);
                    sizeXInput.setText(String.valueOf(characterParams.width));
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
        
        // Слушатели для Y
        ySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && characterParams != null) {
                    characterParams.height = Math.max(10, progress);
                    sizeYInput.setText(String.valueOf(characterParams.height));
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
        
        // Слушатели для Z
        zSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    sizeZInput.setText(String.valueOf(progress));
                    float alpha = 0.2f + (progress / 1000f) * 0.8f;
                    characterView.setAlpha(alpha);
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
                        float alpha = 0.2f + (val / 1000f) * 0.8f;
                        characterView.setAlpha(alpha);
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
        
        // Настройка тач-событий для перемещения
        characterView.setOnTouchListener((v, event) -> {
            if (isCharacterFixed) {
                // Пропускаем событие дальше - кнопки под персонажем будут работать
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
    
    private void hideAllControls() {
        if (controlsLayout != null) controlsLayout.setVisibility(View.GONE);
        if (deleteButton != null) deleteButton.setVisibility(View.GONE);
        if (backButton != null) backButton.setVisibility(View.GONE);
        // Кнопка фиксации остается видимой
    }
    
    private void showAllControls() {
        if (controlsLayout != null) controlsLayout.setVisibility(View.VISIBLE);
        if (deleteButton != null) deleteButton.setVisibility(View.VISIBLE);
        if (backButton != null) backButton.setVisibility(View.VISIBLE);
    }
    
    private void updateCharacterSize() {
        if (windowManager != null && characterContainer != null && characterParams != null) {
            windowManager.updateViewLayout(characterContainer, characterParams);
        }
    }

    private void removeCharacterFromOverlay() {
        if (characterContainer != null && windowManager != null) {
            try {
                windowManager.removeView(characterContainer);
            } catch (Exception e) {
                e.printStackTrace();
            }
            characterContainer = null;
            characterView = null;
            currentCharacterBitmap = null;
            isCharacterModeActive = false;
            isCharacterFixed = false;
            Toast.makeText(this, "🗑️ Персонаж удален", Toast.LENGTH_SHORT).show();
        }
        if (mainCircle != null) {
            mainCircle.setVisibility(View.VISIBLE);
        }
    }

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

        ImageButton closeBtn = createCircleButton(createCloseIcon(), "#DD2C00");
        FrameLayout.LayoutParams closeP = new FrameLayout.LayoutParams(70, 70, Gravity.TOP | Gravity.START);
        closeP.setMargins(20, 40, 0, 0);
        closeBtn.setLayoutParams(closeP);
        closeBtn.setOnClickListener(v -> {
            hideMainOverlay();
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
        });

        mainOverlay.addView(webView);
        mainOverlay.addView(closeBtn);
        mainOverlay.addView(minimizeBtn);

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

    @Override
    public void onResume() {
        super.onResume();
        if (mainCircle != null) {
            mainCircle.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Не прячем ничего
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
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_GALLERY && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            try {
                Bitmap original = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                showCharacterOnScreen(original);
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "❌ Ошибка загрузки изображения", Toast.LENGTH_SHORT).show();
            }
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
