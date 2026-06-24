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
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
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
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.BridgeActivity;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends BridgeActivity {

    private static final int REQUEST_MICROPHONE = 100;
    private static final int REQUEST_CAMERA = 102;
    private static final int REQUEST_OVERLAY_PERMISSION = 101;
    private static final int REQUEST_SCREEN_RECORD = 103;
    private static final int REQUEST_STORAGE = 104;
    
    private WindowManager windowManager;
    public static ImageButton floatingCircle;
    private FrameLayout overlayLayout;
    private WebView webView;
    private WindowManager.LayoutParams circleParams;
    private WindowManager.LayoutParams overlayParams;
    private boolean isOverlayVisible = false;
    private Bundle webViewState = null;
    
    private float startX, startY;
    private int initialX, initialY;
    private boolean isDragging = false;

    // === ПЕРЕМЕННЫЕ ДЛЯ ЗАПИСИ ЭКРАНА ===
    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private MediaRecorder mediaRecorder;
    private boolean isRecording = false;
    private boolean isPaused = false;
    private int screenWidth = 1080;
    private int screenHeight = 1920;
    private int screenDensity = 320;
    private String recordFilePath = "";
    private Timer recordTimer;
    private int recordSeconds = 0;
    private TextView recordTimerTextView;
    private ImageButton recordButton;
    private boolean isRecordMenuVisible = false;

    // === ПЕРЕМЕННЫЕ ДЛЯ АВТОКЛИКЕРА ===
    private boolean isAutoClickerActive = false;
    private ArrayList<AutoClickPoint> clickPoints = new ArrayList<>();
    private Timer autoClickTimer;
    private int clickInterval = 1000; // по умолчанию 1 сек
    private String intervalUnit = "ms"; // ms, sec, min, hour
    private boolean isAutoClickerMenuVisible = false;
    private FrameLayout clickerOverlay;
    private int clickPointCounter = 1;

    // === КЛАСС ДЛЯ ТОЧЕК КЛИКЕРА ===
    private class AutoClickPoint {
        float x, y;
        int id;
        View view;
        AutoClickPoint(float x, float y, int id) {
            this.x = x;
            this.y = y;
            this.id = id;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Запрос микрофона
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MICROPHONE);
        }
        
        // Запрос камеры
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
        }

        // Запрос разрешения на плавающее окно
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
            } else {
                createFloatingCircle();
            }
        } else {
            createFloatingCircle();
        }
        
        // Запуск Foreground Service
        Intent serviceIntent = new Intent(this, VoiceForegroundService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
        
        // Настройка WebView
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

        // Инициализация записи экрана
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
    }

    // ========== КРУЖОК ==========
    private void createFloatingCircle() {
        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }
        
        floatingCircle = new ImageButton(this);
        floatingCircle.setImageBitmap(createXboxGamepadBitmap());
        
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(Color.parseColor("#CC0000"));
        drawable.setStroke(6, Color.parseColor("#FF6666"));
        floatingCircle.setBackground(drawable);
        floatingCircle.setPadding(25, 25, 25, 25);
        floatingCircle.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        
        circleParams = new WindowManager.LayoutParams(
                136, 136, layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        circleParams.gravity = Gravity.TOP | Gravity.START;
        circleParams.x = 100;
        circleParams.y = 200;
        
        floatingCircle.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getRawX();
                        startY = event.getRawY();
                        initialX = circleParams.x;
                        initialY = circleParams.y;
                        isDragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - startX;
                        float deltaY = event.getRawY() - startY;
                        if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                            isDragging = true;
                        }
                        circleParams.x = initialX + (int) deltaX;
                        circleParams.y = initialY + (int) deltaY;
                        if (windowManager != null) {
                            windowManager.updateViewLayout(floatingCircle, circleParams);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!isDragging) {
                            floatingCircle.setVisibility(View.GONE);
                            showOverlay();
                        }
                        return true;
                }
                return false;
            }
        });
        
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager != null) {
            windowManager.addView(floatingCircle, circleParams);
            Toast.makeText(this, "🎮 Красный кружок создан", Toast.LENGTH_SHORT).show();
        }
    }
    
    private Bitmap createXboxGamepadBitmap() {
        int size = 90;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(6);
        
        float centerX = size / 2f;
        float centerY = size / 2f;
        
        float rectWidth = 65;
        float rectHeight = 45;
        float left = centerX - rectWidth / 2;
        float top = centerY - rectHeight / 2;
        float right = centerX + rectWidth / 2;
        float bottom = centerY + rectHeight / 2;
        canvas.drawRoundRect(left, top, right, bottom, 18, 18, paint);
        
        canvas.drawCircle(centerX - 25, centerY, 12, paint);
        canvas.drawCircle(centerX + 25, centerY, 12, paint);
        
        paint.setStrokeWidth(5);
        canvas.drawLine(centerX - 18, centerY - 8, centerX - 18, centerY + 8, paint);
        canvas.drawLine(centerX - 22, centerY, centerX - 14, centerY, paint);
        
        canvas.drawCircle(centerX + 18, centerY - 6, 5, paint);
        canvas.drawCircle(centerX + 18, centerY + 6, 5, paint);
        canvas.drawCircle(centerX + 26, centerY, 5, paint);
        canvas.drawCircle(centerX + 10, centerY, 5, paint);
        
        return bitmap;
    }

    // ========== ОВЕРЛЕЙ ==========
    private void showOverlay() {
        if (isOverlayVisible) return;
        
        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }
        
        overlayLayout = new FrameLayout(this);
        overlayLayout.setBackgroundColor(Color.parseColor("#DD1E1E1E"));
        overlayLayout.setPadding(15, 15, 15, 15);
        
        // ===== WEBVIEW =====
        webView = new WebView(this);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        
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
        
        // ===== ВЕРХНИЕ КНОПКИ (закрыть и свернуть) =====
        ImageButton closeButton = new ImageButton(this);
        closeButton.setImageDrawable(createCloseIcon());
        closeButton.setBackground(createCircleButtonBackground(Color.parseColor("#DD2C00")));
        closeButton.setPadding(20, 20, 20, 20);
        closeButton.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(
                70, 70,
                Gravity.TOP | Gravity.START);
        closeParams.setMargins(20, 40, 0, 0);
        closeButton.setLayoutParams(closeParams);
        closeButton.setOnClickListener(v -> {
            hideOverlay();
            if (floatingCircle != null && windowManager != null) {
                windowManager.removeView(floatingCircle);
                floatingCircle = null;
            }
            finishAffinity();
        });
        
        ImageButton minimizeButton = new ImageButton(this);
        minimizeButton.setImageDrawable(createMinimizeIcon());
        minimizeButton.setBackground(createCircleButtonBackground(Color.parseColor("#4CAF50")));
        minimizeButton.setPadding(20, 20, 20, 20);
        minimizeButton.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        
        FrameLayout.LayoutParams minParams = new FrameLayout.LayoutParams(
                70, 70,
                Gravity.TOP | Gravity.END);
        minParams.setMargins(0, 40, 20, 0);
        minimizeButton.setLayoutParams(minParams);
        minimizeButton.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            webView.saveState(bundle);
            webViewState = bundle;
            hideOverlay();
            if (floatingCircle != null) {
                floatingCircle.setVisibility(View.VISIBLE);
            }
        });
        
        // ===== НИЖНИЕ КНОПКИ (автокликер, запись, корзина) =====
        LinearLayout bottomButtons = new LinearLayout(this);
        bottomButtons.setOrientation(LinearLayout.HORIZONTAL);
        bottomButtons.setGravity(Gravity.CENTER);
        bottomButtons.setPadding(20, 10, 20, 20);
        
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        bottomParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        bottomButtons.setLayoutParams(bottomParams);
        
        // 1. Автокликер
        ImageButton autoClickerBtn = new ImageButton(this);
        autoClickerBtn.setImageDrawable(createAutoClickerIcon());
        autoClickerBtn.setBackground(createCircleButtonBackground(Color.parseColor("#3F51B5")));
        autoClickerBtn.setPadding(18, 18, 18, 18);
        autoClickerBtn.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(70, 70);
        btnParams.setMargins(10, 0, 10, 0);
        autoClickerBtn.setLayoutParams(btnParams);
        autoClickerBtn.setOnClickListener(v -> showAutoClickerMenu());
        
        // 2. Запись экрана
        recordButton = new ImageButton(this);
        recordButton.setImageDrawable(createRecordIcon());
        recordButton.setBackground(createCircleButtonBackground(Color.parseColor("#E53935")));
        recordButton.setPadding(18, 18, 18, 18);
        recordButton.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        recordButton.setLayoutParams(btnParams);
        recordButton.setOnClickListener(v -> showRecordMenu());
        
        // 3. Корзина (закрыть оверлей)
        ImageButton trashButton = new ImageButton(this);
        trashButton.setImageDrawable(createTrashIcon());
        trashButton.setBackground(createCircleButtonBackground(Color.parseColor("#880E4F")));
        trashButton.setPadding(18, 18, 18, 18);
        trashButton.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        trashButton.setLayoutParams(btnParams);
        trashButton.setOnClickListener(v -> {
            hideOverlay();
            if (floatingCircle != null && windowManager != null) {
                windowManager.removeView(floatingCircle);
                floatingCircle = null;
            }
            finishAffinity();
        });
        
        bottomButtons.addView(autoClickerBtn);
        bottomButtons.addView(recordButton);
        bottomButtons.addView(trashButton);
        
        // ===== СБОРКА =====
        overlayLayout.addView(webView);
        overlayLayout.addView(closeButton);
        overlayLayout.addView(minimizeButton);
        overlayLayout.addView(bottomButtons);
        
        overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        overlayParams.gravity = Gravity.CENTER;
        
        if (windowManager != null) {
            windowManager.addView(overlayLayout, overlayParams);
            isOverlayVisible = true;
        }
    }

    // ========== ИКОНКИ ДЛЯ КНОПОК ==========
    private Drawable createAutoClickerIcon() {
        Bitmap bitmap = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(6);
        paint.setStyle(Paint.Style.STROKE);
        
        // Рисуем указатель мыши
        float[] points = {20, 15, 20, 50, 35, 38, 25, 35, 45, 25, 30, 30, 40, 18};
        for (int i = 0; i < points.length; i += 2) {
            canvas.drawCircle(points[i], points[i+1], 4, paint);
        }
        // Линия указателя
        canvas.drawLine(20, 15, 45, 25, paint);
        paint.setStrokeWidth(3);
        canvas.drawLine(20, 15, 35, 38, paint);
        
        return new android.graphics.drawable.BitmapDrawable(getResources(), bitmap);
    }

    private Drawable createRecordIcon() {
        Bitmap bitmap = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(6);
        paint.setStyle(Paint.Style.FILL);
        
        // Красный кружок (как кнопка записи)
        paint.setColor(Color.RED);
        canvas.drawCircle(30, 30, 18, paint);
        
        // Белый контур
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(3);
        canvas.drawCircle(30, 30, 22, paint);
        
        return new android.graphics.drawable.BitmapDrawable(getResources(), bitmap);
    }

    private Drawable createTrashIcon() {
        Bitmap bitmap = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(6);
        paint.setStyle(Paint.Style.STROKE);
        
        // Корзина
        float left = 15, top = 18, right = 45, bottom = 48;
        canvas.drawRect(left, top, right, bottom, paint);
        // Ручка
        canvas.drawLine(22, 12, 38, 12, paint);
        canvas.drawLine(22, 12, 25, 18, paint);
        canvas.drawLine(38, 12, 35, 18, paint);
        // Крышка
        canvas.drawLine(18, 18, 42, 18, paint);
        // Полосы
        canvas.drawLine(22, 25, 22, 40, paint);
        canvas.drawLine(30, 25, 30, 40, paint);
        canvas.drawLine(38, 25, 38, 40, paint);
        
        return new android.graphics.drawable.BitmapDrawable(getResources(), bitmap);
    }

    // ========== АВТОКЛИКЕР ==========
    private void showAutoClickerMenu() {
        if (isAutoClickerMenuVisible) return;
        isAutoClickerMenuVisible = true;
        
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        View popupView = inflater.inflate(android.R.layout.simple_list_item_1, null);
        
        PopupWindow popupWindow = new PopupWindow(popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setBackgroundDrawable(new GradientDrawable());
        popupWindow.setOutsideTouchable(true);
        
        // Создаём меню вручную
        LinearLayout menuLayout = new LinearLayout(this);
        menuLayout.setOrientation(LinearLayout.VERTICAL);
        menuLayout.setBackgroundColor(Color.parseColor("#1E1E1E"));
        menuLayout.setPadding(30, 20, 30, 20);
        
        TextView title = new TextView(this);
        title.setText("🖱️ Автокликер");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setPadding(0, 0, 0, 20);
        menuLayout.addView(title);
        
        // Кнопка "Добавить точку"
        Button addPointBtn = new Button(this);
        addPointBtn.setText("➕ Добавить точку");
        addPointBtn.setTextColor(Color.WHITE);
        addPointBtn.setBackgroundColor(Color.parseColor("#3F51B5"));
        addPointBtn.setOnClickListener(v -> {
            addClickPoint();
            popupWindow.dismiss();
            isAutoClickerMenuVisible = false;
        });
        menuLayout.addView(addPointBtn);
        
        // Интервал
        LinearLayout intervalLayout = new LinearLayout(this);
        intervalLayout.setOrientation(LinearLayout.HORIZONTAL);
        intervalLayout.setPadding(0, 20, 0, 0);
        
        EditText intervalInput = new EditText(this);
        intervalInput.setHint("Интервал");
        intervalInput.setTextColor(Color.WHITE);
        intervalInput.setHintTextColor(Color.GRAY);
        intervalInput.setBackgroundColor(Color.parseColor("#333333"));
        intervalInput.setPadding(15, 10, 15, 10);
        intervalInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        intervalInput.setText(String.valueOf(clickInterval));
        intervalLayout.addView(intervalInput);
        
        Button[] unitButtons = new Button[4];
        String[] units = {"мс", "сек", "мин", "час"};
        for (int i = 0; i < 4; i++) {
            unitButtons[i] = new Button(this);
            unitButtons[i].setText(units[i]);
            unitButtons[i].setTextColor(Color.WHITE);
            unitButtons[i].setBackgroundColor(Color.parseColor("#555555"));
            int finalI = i;
            unitButtons[i].setOnClickListener(v -> {
                intervalUnit = units[finalI];
                Toast.makeText(this, "Единица: " + intervalUnit, Toast.LENGTH_SHORT).show();
            });
            intervalLayout.addView(unitButtons[i]);
        }
        menuLayout.addView(intervalLayout);
        
        // Кнопка "Запустить/Остановить"
        Button startStopBtn = new Button(this);
        startStopBtn.setText(isAutoClickerActive ? "⏹ Остановить" : "▶ Запустить");
        startStopBtn.setTextColor(Color.WHITE);
        startStopBtn.setBackgroundColor(Color.parseColor("#4CAF50"));
        startStopBtn.setOnClickListener(v -> {
            if (isAutoClickerActive) {
                stopAutoClicker();
                startStopBtn.setText("▶ Запустить");
                Toast.makeText(this, "Автокликер остановлен", Toast.LENGTH_SHORT).show();
            } else {
                // Получаем интервал
                try {
                    int val = Integer.parseInt(intervalInput.getText().toString());
                    switch (intervalUnit) {
                        case "мс": clickInterval = val; break;
                        case "сек": clickInterval = val * 1000; break;
                        case "мин": clickInterval = val * 60 * 1000; break;
                        case "час": clickInterval = val * 60 * 60 * 1000; break;
                    }
                } catch (Exception e) {}
                startAutoClicker();
                startStopBtn.setText("⏹ Остановить");
                Toast.makeText(this, "Автокликер запущен", Toast.LENGTH_SHORT).show();
            }
        });
        menuLayout.addView(startStopBtn);
        
        // Кнопка "Очистить точки"
        Button clearBtn = new Button(this);
        clearBtn.setText("🗑 Очистить точки");
        clearBtn.setTextColor(Color.WHITE);
        clearBtn.setBackgroundColor(Color.parseColor("#DD2C00"));
        clearBtn.setOnClickListener(v -> {
            clearClickPoints();
            popupWindow.dismiss();
            isAutoClickerMenuVisible = false;
        });
        menuLayout.addView(clearBtn);
        
        popupWindow.setContentView(menuLayout);
        popupWindow.setWidth(400);
        popupWindow.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        
        View anchor = overlayLayout != null ? overlayLayout : floatingCircle;
        if (anchor != null) {
            popupWindow.showAtLocation(anchor, Gravity.CENTER, 0, 0);
        }
        
        popupWindow.setOnDismissListener(() -> isAutoClickerMenuVisible = false);
    }

    private void addClickPoint() {
        // Создаём прозрачный кружок с номером
        final int pointId = clickPointCounter++;
        FrameLayout pointView = new FrameLayout(this);
        pointView.setBackground(createCircleButtonBackground(Color.parseColor("#00FFFFFF")));
        pointView.setPadding(20, 20, 20, 20);
        
        TextView numText = new TextView(this);
        numText.setText(String.valueOf(pointId));
        numText.setTextColor(Color.WHITE);
        numText.setTextSize(16);
        numText.setBackgroundColor(Color.parseColor("#88000000"));
        numText.setPadding(10, 5, 10, 5);
        pointView.addView(numText);
        
        WindowManager.LayoutParams pointParams = new WindowManager.LayoutParams(
                60, 60,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        pointParams.gravity = Gravity.TOP | Gravity.START;
        pointParams.x = 200 + pointId * 50;
        pointParams.y = 300 + pointId * 30;
        
        // Drag для перемещения точки
        final float[] startX = new float[1];
        final float[] startY = new float[1];
        final int[] initX = new int[1];
        final int[] initY = new int[1];
        final boolean[] isDraggingPoint = new boolean[1];
        
        pointView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX[0] = event.getRawX();
                    startY[0] = event.getRawY();
                    initX[0] = pointParams.x;
                    initY[0] = pointParams.y;
                    isDraggingPoint[0] = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - startX[0];
                    float dy = event.getRawY() - startY[0];
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isDraggingPoint[0] = true;
                    }
                    pointParams.x = initX[0] + (int) dx;
                    pointParams.y = initY[0] + (int) dy;
                    if (windowManager != null) {
                        windowManager.updateViewLayout(pointView, pointParams);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!isDraggingPoint[0]) {
                        // Удаляем точку по нажатию
                        removeClickPoint(pointView, pointId);
                    }
                    return true;
            }
            return false;
        });
        
        // Добавляем точку в список и на экран
        AutoClickPoint point = new AutoClickPoint(pointParams.x, pointParams.y, pointId);
        point.view = pointView;
        clickPoints.add(point);
        
        if (windowManager != null) {
            windowManager.addView(pointView, pointParams);
        }
        
        Toast.makeText(this, "Точка " + pointId + " добавлена", Toast.LENGTH_SHORT).show();
    }

    private void removeClickPoint(View view, int id) {
        if (windowManager != null) {
            windowManager.removeView(view);
        }
        for (int i = 0; i < clickPoints.size(); i++) {
            if (clickPoints.get(i).id == id) {
                clickPoints.remove(i);
                break;
            }
        }
        Toast.makeText(this, "Точка " + id + " удалена", Toast.LENGTH_SHORT).show();
    }

    private void clearClickPoints() {
        for (AutoClickPoint point : clickPoints) {
            if (point.view != null && windowManager != null) {
                try {
                    windowManager.removeView(point.view);
                } catch (Exception e) {}
            }
        }
        clickPoints.clear();
        clickPointCounter = 1;
        Toast.makeText(this, "Все точки удалены", Toast.LENGTH_SHORT).show();
    }

    private void startAutoClicker() {
        if (clickPoints.isEmpty()) {
            Toast.makeText(this, "Нет точек для кликов", Toast.LENGTH_SHORT).show();
            return;
        }
        isAutoClickerActive = true;
        autoClickTimer = new Timer();
        autoClickTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> {
                    if (!isAutoClickerActive) return;
                    // Эмуляция клика в каждой точке
                    for (AutoClickPoint point : clickPoints) {
                        // Имитация клика
                        performClick(point.x, point.y);
                    }
                });
            }
        }, 0, clickInterval);
    }

    private void stopAutoClicker() {
        isAutoClickerActive = false;
        if (autoClickTimer != null) {
            autoClickTimer.cancel();
            autoClickTimer = null;
        }
    }

    private void performClick(float x, float y) {
        // В реальном приложении здесь была бы эмуляция касания через AccessibilityService
        // Для этого нужно расширить приложение и использовать AccessibilityService
        // Пока просто логируем
        android.util.Log.d("AutoClicker", "Клик в точке: " + x + ", " + y);
    }

    // ========== ЗАПИСЬ ЭКРАНА ==========
    private void showRecordMenu() {
        if (isRecordMenuVisible) return;
        isRecordMenuVisible = true;
        
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        View popupView = inflater.inflate(android.R.layout.simple_list_item_1, null);
        
        PopupWindow popupWindow = new PopupWindow(popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setBackgroundDrawable(new GradientDrawable());
        popupWindow.setOutsideTouchable(true);
        
        LinearLayout menuLayout = new LinearLayout(this);
        menuLayout.setOrientation(LinearLayout.VERTICAL);
        menuLayout.setBackgroundColor(Color.parseColor("#1E1E1E"));
        menuLayout.setPadding(30, 20, 30, 20);
        
        TextView title = new TextView(this);
        title.setText("🎥 Запись экрана");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setPadding(0, 0, 0, 20);
        menuLayout.addView(title);
        
        // Таймер записи
        recordTimerTextView = new TextView(this);
        recordTimerTextView.setText("00:00");
        recordTimerTextView.setTextColor(Color.WHITE);
        recordTimerTextView.setTextSize(24);
        recordTimerTextView.setGravity(Gravity.CENTER);
        recordTimerTextView.setPadding(0, 0, 0, 20);
        menuLayout.addView(recordTimerTextView);
        
        // Кнопки задержки
        LinearLayout delayLayout = new LinearLayout(this);
        delayLayout.setOrientation(LinearLayout.HORIZONTAL);
        delayLayout.setPadding(0, 0, 0, 20);
        
        int[] delays = {0, 5, 10, 15, 30, 60};
        String[] delayLabels = {"Сразу", "5с", "10с", "15с", "30с", "60с"};
        for (int i = 0; i < delays.length; i++) {
            Button delayBtn = new Button(this);
            delayBtn.setText(delayLabels[i]);
            delayBtn.setTextColor(Color.WHITE);
            delayBtn.setBackgroundColor(Color.parseColor("#444444"));
            delayBtn.setPadding(10, 5, 10, 5);
            int finalDelay = delays[i];
            delayBtn.setOnClickListener(v -> {
                if (!isRecording) {
                    startScreenRecording(finalDelay);
                    popupWindow.dismiss();
                    isRecordMenuVisible = false;
                }
            });
            delayLayout.addView(delayBtn);
        }
        menuLayout.addView(delayLayout);
        
        // Кнопки управления
        if (isRecording) {
            Button pauseBtn = new Button(this);
            pauseBtn.setText(isPaused ? "▶ Возобновить" : "⏸ Пауза");
            pauseBtn.setTextColor(Color.WHITE);
            pauseBtn.setBackgroundColor(Color.parseColor("#FF9800"));
            pauseBtn.setOnClickListener(v -> {
                toggleRecordPause();
                popupWindow.dismiss();
                isRecordMenuVisible = false;
            });
            menuLayout.addView(pauseBtn);
            
            Button stopBtn = new Button(this);
            stopBtn.setText("⏹ Остановить");
            stopBtn.setTextColor(Color.WHITE);
            stopBtn.setBackgroundColor(Color.parseColor("#DD2C00"));
            stopBtn.setOnClickListener(v -> {
                stopScreenRecording();
                popupWindow.dismiss();
                isRecordMenuVisible = false;
            });
            menuLayout.addView(stopBtn);
        }
        
        popupWindow.setContentView(menuLayout);
        popupWindow.setWidth(400);
        popupWindow.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        
        View anchor = overlayLayout != null ? overlayLayout : floatingCircle;
        if (anchor != null) {
            popupWindow.showAtLocation(anchor, Gravity.CENTER, 0, 0);
        }
        popupWindow.setOnDismissListener(() -> isRecordMenuVisible = false);
    }

    private void startScreenRecording(int delaySeconds) {
        // Запрос разрешения на запись экрана
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Intent intent = projectionManager.createScreenCaptureIntent();
            startActivityForResult(intent, REQUEST_SCREEN_RECORD);
        }
        
        if (delaySeconds > 0) {
            Toast.makeText(this, "Запись начнётся через " + delaySeconds + " секунд", Toast.LENGTH_SHORT).show();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                startRecording();
            }, delaySeconds * 1000);
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaProjection == null) {
            Intent intent = projectionManager.createScreenCaptureIntent();
            startActivityForResult(intent, REQUEST_SCREEN_RECORD);
            return;
        }
        // TODO: Реализовать полноценную запись экрана через MediaProjection
        // Это сложный компонент, требует отдельной реализации
        isRecording = true;
        isPaused = false;
        recordSeconds = 0;
        updateRecordTimer();
        Toast.makeText(this, "Запись экрана начата", Toast.LENGTH_SHORT).show();
    }

    private void stopScreenRecording() {
        isRecording = false;
        isPaused = false;
        if (recordTimer != null) {
            recordTimer.cancel();
            recordTimer = null;
        }
        if (recordTimerTextView != null) {
            recordTimerTextView.setText("00:00");
        }
        Toast.makeText(this, "Запись остановлена", Toast.LENGTH_SHORT).show();
    }

    private void toggleRecordPause() {
        isPaused = !isPaused;
        Toast.makeText(this, isPaused ? "Запись на паузе" : "Запись возобновлена", Toast.LENGTH_SHORT).show();
    }

    private void updateRecordTimer() {
        if (recordTimer != null) {
            recordTimer.cancel();
        }
        recordTimer = new Timer();
        recordTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!isRecording || isPaused) return;
                runOnUiThread(() -> {
                    recordSeconds++;
                    int minutes = recordSeconds / 60;
                    int seconds = recordSeconds % 60;
                    if (recordTimerTextView != null) {
                        recordTimerTextView.setText(String.format("%02d:%02d", minutes, seconds));
                    }
                });
            }
        }, 1000, 1000);
    }

    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========
    private Drawable createCloseIcon() {
        Bitmap bitmap = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(8);
        paint.setStyle(Paint.Style.STROKE);
        
        float centerX = 30;
        float centerY = 30;
        float offset = 15;
        
        canvas.drawLine(centerX - offset, centerY - offset, centerX + offset, centerY + offset, paint);
        canvas.drawLine(centerX + offset, centerY - offset, centerX - offset, centerY + offset, paint);
        
        return new android.graphics.drawable.BitmapDrawable(getResources(), bitmap);
    }
    
    private Drawable createMinimizeIcon() {
        Bitmap bitmap = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(8);
        paint.setStyle(Paint.Style.STROKE);
        
        float centerX = 30;
        float centerY = 30;
        float width = 25;
        float height = 15;
        
        canvas.drawRect(centerX - width/2, centerY - height/2, centerX + width/2, centerY + height/2, paint);
        
        return new android.graphics.drawable.BitmapDrawable(getResources(), bitmap);
    }
    
    private Drawable createCircleButtonBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        drawable.setStroke(4, Color.WHITE);
        return drawable;
    }

    private void hideOverlay() {
        if (overlayLayout != null && windowManager != null && isOverlayVisible) {
            windowManager.removeView(overlayLayout);
            isOverlayVisible = false;
        }
        // Останавливаем автокликер при закрытии
        stopAutoClicker();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (floatingCircle != null && !isOverlayVisible) {
            floatingCircle.setVisibility(View.GONE);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (floatingCircle != null && !isOverlayVisible) {
            floatingCircle.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SCREEN_RECORD) {
            if (resultCode == RESULT_OK && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mediaProjection = projectionManager.getMediaProjection(resultCode, data);
                Toast.makeText(this, "Разрешение на запись экрана получено", Toast.LENGTH_SHORT).show();
                // Здесь можно начать запись через MediaRecorder
            } else {
                Toast.makeText(this, "Запись экрана отклонена", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_MICROPHONE && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "🎤 Микрофон разрешён", Toast.LENGTH_SHORT).show();
            }
        }
        if (requestCode == REQUEST_CAMERA && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "📷 Камера разрешена", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopAutoClicker();
        stopScreenRecording();
        if (floatingCircle != null && windowManager != null) {
            windowManager.removeView(floatingCircle);
        }
        if (overlayLayout != null && windowManager != null && isOverlayVisible) {
            windowManager.removeView(overlayLayout);
        }
    }
                            }
