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

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends BridgeActivity {

    private static final int REQUEST_MICROPHONE = 100;
    private static final int REQUEST_CAMERA = 102;
    private static final int REQUEST_OVERLAY_PERMISSION = 101;
    
    private WindowManager windowManager;
    public static ImageButton floatingCircle;
    private FrameLayout mainOverlay;
    private WebView webView;
    private WindowManager.LayoutParams circleParams;
    private WindowManager.LayoutParams overlayParams;
    private boolean isMainOverlayVisible = false;
    private Bundle webViewState = null;
    
    private float startX, startY;
    private int initialX, initialY;
    private boolean isDragging = false;

    // Отдельные оверлеи для фишек
    private FrameLayout autoClickerOverlay;
    private FrameLayout recordOverlay;
    private WindowManager.LayoutParams autoOverlayParams;
    private WindowManager.LayoutParams recordOverlayParams;
    private boolean isAutoOverlayVisible = false;
    private boolean isRecordOverlayVisible = false;

    // Автокликер
    private boolean isAutoClickerActive = false;
    private ArrayList<float[]> clickPoints = new ArrayList<>();
    private Timer autoClickTimer;
    private int clickInterval = 1000;
    private String intervalUnit = "ms";
    private int pointCounter = 1;

    // Запись экрана
    private boolean isRecording = false;
    private Timer recordTimer;
    private int recordSeconds = 0;
    private TextView recordTimerText;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

    // ========== ГЛАВНЫЙ КРУЖОК ==========
    private void createFloatingCircle() {
        int flag = getOverlayFlag();
        
        floatingCircle = new ImageButton(this);
        floatingCircle.setImageBitmap(createGamepadBitmap());
        
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(Color.parseColor("#CC0000"));
        drawable.setStroke(6, Color.parseColor("#FF6666"));
        floatingCircle.setBackground(drawable);
        floatingCircle.setPadding(25, 25, 25, 25);
        floatingCircle.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        
        circleParams = new WindowManager.LayoutParams(136, 136, flag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        circleParams.gravity = Gravity.TOP | Gravity.START;
        circleParams.x = 100;
        circleParams.y = 200;
        
        floatingCircle.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = event.getRawX();
                    startY = event.getRawY();
                    initialX = circleParams.x;
                    initialY = circleParams.y;
                    isDragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - startX;
                    float dy = event.getRawY() - startY;
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isDragging = true;
                    circleParams.x = initialX + (int) dx;
                    circleParams.y = initialY + (int) dy;
                    if (windowManager != null) {
                        windowManager.updateViewLayout(floatingCircle, circleParams);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!isDragging) {
                        toggleMainOverlay();
                    }
                    return true;
            }
            return false;
        });
        
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager != null) {
            windowManager.addView(floatingCircle, circleParams);
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

    private int getOverlayFlag() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;
    }

    // ========== ГЛАВНЫЙ ОВЕРЛЕЙ (САЙТ + 3 КНОПКИ) ==========
    private void toggleMainOverlay() {
        if (isMainOverlayVisible) {
            hideMainOverlay();
            if (floatingCircle != null) {
                floatingCircle.setVisibility(View.VISIBLE);
            }
        } else {
            floatingCircle.setVisibility(View.GONE);
            showMainOverlay();
        }
    }

    private void showMainOverlay() {
        if (isMainOverlayVisible) return;
        
        int flag = getOverlayFlag();
        
        mainOverlay = new FrameLayout(this);
        mainOverlay.setBackgroundColor(Color.parseColor("#DD1E1E1E"));
        mainOverlay.setPadding(15, 15, 15, 15);
        
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
        
        if (webViewState != null) {
            webView.restoreState(webViewState);
        } else {
            webView.loadUrl("https://crconferensimessenger.vercel.app/");
        }
        
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        
        // Кнопки управления вверху
        ImageButton closeBtn = createCircleButton(createCloseIcon(), "#DD2C00");
        FrameLayout.LayoutParams closeP = new FrameLayout.LayoutParams(70, 70, Gravity.TOP | Gravity.START);
        closeP.setMargins(20, 40, 0, 0);
        closeBtn.setLayoutParams(closeP);
        closeBtn.setOnClickListener(v -> {
            hideMainOverlay();
            if (floatingCircle != null) {
                floatingCircle.setVisibility(View.VISIBLE);
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
            if (floatingCircle != null) {
                floatingCircle.setVisibility(View.VISIBLE);
            }
        });
        
        // ===== ТРИ БОЛЬШИЕ КНОПКИ ВНИЗУ =====
        LinearLayout bottomButtons = new LinearLayout(this);
        bottomButtons.setOrientation(LinearLayout.HORIZONTAL);
        bottomButtons.setGravity(Gravity.CENTER);
        bottomButtons.setPadding(20, 10, 20, 30);
        
        FrameLayout.LayoutParams bottomP = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        bottomP.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        bottomButtons.setLayoutParams(bottomP);
        
        // 1. Автокликер (синий)
        ImageButton autoBtn = createCircleButton(createAutoClickerIcon(), "#3F51B5");
        LinearLayout.LayoutParams btnP = new LinearLayout.LayoutParams(100, 100);
        btnP.setMargins(20, 0, 20, 0);
        autoBtn.setLayoutParams(btnP);
        autoBtn.setOnClickListener(v -> showAutoClickerOverlay());
        
        // 2. Запись экрана (красный)
        ImageButton recordBtn = createCircleButton(createRecordIcon(), "#E53935");
        recordBtn.setLayoutParams(btnP);
        recordBtn.setOnClickListener(v -> showRecordOverlay());
        
        // 3. Корзина (тёмно-красный)
        ImageButton trashBtn = createCircleButton(createTrashIcon(), "#880E4F");
        trashBtn.setLayoutParams(btnP);
        trashBtn.setOnClickListener(v -> {
            hideMainOverlay();
            if (floatingCircle != null && windowManager != null) {
                windowManager.removeView(floatingCircle);
                floatingCircle = null;
            }
            finishAffinity();
        });
        
        bottomButtons.addView(autoBtn);
        bottomButtons.addView(recordBtn);
        bottomButtons.addView(trashBtn);
        
        mainOverlay.addView(webView);
        mainOverlay.addView(closeBtn);
        mainOverlay.addView(minimizeBtn);
        mainOverlay.addView(bottomButtons);
        
        overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                flag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        overlayParams.gravity = Gravity.CENTER;
        
        if (windowManager != null) {
            windowManager.addView(mainOverlay, overlayParams);
            isMainOverlayVisible = true;
        }
    }

    private void hideMainOverlay() {
        if (mainOverlay != null && windowManager != null && isMainOverlayVisible) {
            windowManager.removeView(mainOverlay);
            mainOverlay = null;
            isMainOverlayVisible = false;
        }
        // Закрываем дочерние оверлеи
        hideAutoClickerOverlay();
        hideRecordOverlay();
    }

    // ========== ОВЕРЛЕЙ АВТОКЛИКЕРА ==========
    private void showAutoClickerOverlay() {
        if (isAutoOverlayVisible) return;
        
        int flag = getOverlayFlag();
        
        autoClickerOverlay = new FrameLayout(this);
        autoClickerOverlay.setBackgroundColor(Color.parseColor("#DD222222"));
        autoClickerOverlay.setPadding(20, 20, 20, 20);
        
        // Контент
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#1E1E1E"));
        layout.setPadding(30, 30, 30, 30);
        
        // Заголовок
        TextView title = new TextView(this);
        title.setText("🖱️ Автокликер");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        layout.addView(title);
        
        // Кнопка "Добавить точку"
        Button addBtn = new Button(this);
        addBtn.setText("➕ Добавить точку");
        addBtn.setTextColor(Color.WHITE);
        addBtn.setBackgroundColor(Color.parseColor("#3F51B5"));
        addBtn.setOnClickListener(v -> {
            clickPoints.add(new float[]{200 + pointCounter * 50, 300 + pointCounter * 30});
            Toast.makeText(this, "Точка " + pointCounter + " добавлена", Toast.LENGTH_SHORT).show();
            pointCounter++;
        });
        layout.addView(addBtn);
        
        // Интервал
        LinearLayout intLayout = new LinearLayout(this);
        intLayout.setOrientation(LinearLayout.HORIZONTAL);
        EditText intervalInput = new EditText(this);
        intervalInput.setHint("Интервал");
        intervalInput.setTextColor(Color.WHITE);
        intervalInput.setHintTextColor(Color.GRAY);
        intervalInput.setBackgroundColor(Color.parseColor("#333333"));
        intervalInput.setPadding(15, 10, 15, 10);
        intervalInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        intervalInput.setText("1000");
        intLayout.addView(intervalInput);
        
        String[] units = {"мс", "сек", "мин"};
        for (String unit : units) {
            Button ub = new Button(this);
            ub.setText(unit);
            ub.setTextColor(Color.WHITE);
            ub.setBackgroundColor(Color.parseColor("#555555"));
            ub.setOnClickListener(v -> {
                intervalUnit = unit;
                Toast.makeText(this, "Единица: " + unit, Toast.LENGTH_SHORT).show();
            });
            intLayout.addView(ub);
        }
        layout.addView(intLayout);
        
        // Кнопка запуска/остановки
        Button startBtn = new Button(this);
        startBtn.setText(isAutoClickerActive ? "⏹ Остановить" : "▶ Запустить");
        startBtn.setTextColor(Color.WHITE);
        startBtn.setBackgroundColor(Color.parseColor("#4CAF50"));
        startBtn.setOnClickListener(v -> {
            if (isAutoClickerActive) {
                stopAutoClicker();
                startBtn.setText("▶ Запустить");
                Toast.makeText(this, "Автокликер остановлен", Toast.LENGTH_SHORT).show();
            } else {
                try {
                    int val = Integer.parseInt(intervalInput.getText().toString());
                    switch (intervalUnit) {
                        case "мс": clickInterval = val; break;
                        case "сек": clickInterval = val * 1000; break;
                        case "мин": clickInterval = val * 60 * 1000; break;
                    }
                } catch (Exception e) {}
                startAutoClicker();
                startBtn.setText("⏹ Остановить");
                Toast.makeText(this, "Автокликер запущен", Toast.LENGTH_SHORT).show();
            }
        });
        layout.addView(startBtn);
        
        // Кнопка очистки точек
        Button clearBtn = new Button(this);
        clearBtn.setText("🗑 Очистить точки");
        clearBtn.setTextColor(Color.WHITE);
        clearBtn.setBackgroundColor(Color.parseColor("#DD2C00"));
        clearBtn.setOnClickListener(v -> {
            clickPoints.clear();
            pointCounter = 1;
            Toast.makeText(this, "Все точки удалены", Toast.LENGTH_SHORT).show();
        });
        layout.addView(clearBtn);
        
        // Кнопка закрытия оверлея
        Button closeOverlayBtn = new Button(this);
        closeOverlayBtn.setText("✕ Закрыть");
        closeOverlayBtn.setTextColor(Color.WHITE);
        closeOverlayBtn.setBackgroundColor(Color.parseColor("#555555"));
        closeOverlayBtn.setOnClickListener(v -> hideAutoClickerOverlay());
        layout.addView(closeOverlayBtn);
        
        autoClickerOverlay.addView(layout);
        
        autoOverlayParams = new WindowManager.LayoutParams(
                500, ViewGroup.LayoutParams.WRAP_CONTENT,
                flag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        autoOverlayParams.gravity = Gravity.CENTER;
        
        if (windowManager != null) {
            windowManager.addView(autoClickerOverlay, autoOverlayParams);
            isAutoOverlayVisible = true;
        }
    }

    private void hideAutoClickerOverlay() {
        if (autoClickerOverlay != null && windowManager != null && isAutoOverlayVisible) {
            windowManager.removeView(autoClickerOverlay);
            autoClickerOverlay = null;
            isAutoOverlayVisible = false;
        }
    }

    // ========== ОВЕРЛЕЙ ЗАПИСИ ЭКРАНА ==========
    private void showRecordOverlay() {
        if (isRecordOverlayVisible) return;
        
        int flag = getOverlayFlag();
        
        recordOverlay = new FrameLayout(this);
        recordOverlay.setBackgroundColor(Color.parseColor("#DD222222"));
        recordOverlay.setPadding(20, 20, 20, 20);
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#1E1E1E"));
        layout.setPadding(30, 30, 30, 30);
        
        // Заголовок
        TextView title = new TextView(this);
        title.setText("🎥 Запись экрана");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        layout.addView(title);
        
        // Таймер
        recordTimerText = new TextView(this);
        recordTimerText.setText("00:00");
        recordTimerText.setTextColor(Color.WHITE);
        recordTimerText.setTextSize(28);
        recordTimerText.setGravity(Gravity.CENTER);
        recordTimerText.setPadding(0, 20, 0, 20);
        layout.addView(recordTimerText);
        
        // Кнопки задержки
        LinearLayout delayLayout = new LinearLayout(this);
        delayLayout.setOrientation(LinearLayout.HORIZONTAL);
        int[] delays = {0, 5, 10, 15, 30};
        String[] labels = {"Сразу", "5с", "10с", "15с", "30с"};
        for (int i = 0; i < delays.length; i++) {
            Button db = new Button(this);
            db.setText(labels[i]);
            db.setTextColor(Color.WHITE);
            db.setBackgroundColor(Color.parseColor("#444444"));
            int d = delays[i];
            db.setOnClickListener(v -> {
                if (!isRecording) {
                    startRecording(d);
                }
            });
            delayLayout.addView(db);
        }
        layout.addView(delayLayout);
        
        // Кнопки управления записью
        if (isRecording) {
            Button pauseBtn = new Button(this);
            pauseBtn.setText("⏸ Пауза");
            pauseBtn.setTextColor(Color.WHITE);
            pauseBtn.setBackgroundColor(Color.parseColor("#FF9800"));
            pauseBtn.setOnClickListener(v -> {
                Toast.makeText(this, "Пауза", Toast.LENGTH_SHORT).show();
            });
            layout.addView(pauseBtn);
            
            Button stopBtn = new Button(this);
            stopBtn.setText("⏹ Остановить");
            stopBtn.setTextColor(Color.WHITE);
            stopBtn.setBackgroundColor(Color.parseColor("#DD2C00"));
            stopBtn.setOnClickListener(v -> stopRecording());
            layout.addView(stopBtn);
        }
        
        // Кнопка закрытия оверлея
        Button closeOverlayBtn = new Button(this);
        closeOverlayBtn.setText("✕ Закрыть");
        closeOverlayBtn.setTextColor(Color.WHITE);
        closeOverlayBtn.setBackgroundColor(Color.parseColor("#555555"));
        closeOverlayBtn.setOnClickListener(v -> hideRecordOverlay());
        layout.addView(closeOverlayBtn);
        
        recordOverlay.addView(layout);
        
        recordOverlayParams = new WindowManager.LayoutParams(
                500, ViewGroup.LayoutParams.WRAP_CONTENT,
                flag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        recordOverlayParams.gravity = Gravity.CENTER;
        
        if (windowManager != null) {
            windowManager.addView(recordOverlay, recordOverlayParams);
            isRecordOverlayVisible = true;
        }
    }

    private void hideRecordOverlay() {
        if (recordOverlay != null && windowManager != null && isRecordOverlayVisible) {
            windowManager.removeView(recordOverlay);
            recordOverlay = null;
            isRecordOverlayVisible = false;
        }
    }

    // ========== ЛОГИКА АВТОКЛИКЕРА ==========
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
                if (!isAutoClickerActive) return;
                runOnUiThread(() -> {
                    for (float[] p : clickPoints) {
                        Toast.makeText(MainActivity.this, "Клик в (" + (int)p[0] + ", " + (int)p[1] + ")", Toast.LENGTH_SHORT).show();
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

    // ========== ЛОГИКА ЗАПИСИ ==========
    private void startRecording(int delay) {
        if (delay > 0) {
            Toast.makeText(this, "Запись через " + delay + " сек", Toast.LENGTH_SHORT).show();
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::startRecordingNow, delay * 1000);
        } else {
            startRecordingNow();
        }
    }

    private void startRecordingNow() {
        isRecording = true;
        recordSeconds = 0;
        if (recordTimer != null) recordTimer.cancel();
        recordTimer = new Timer();
        recordTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!isRecording) return;
                runOnUiThread(() -> {
                    recordSeconds++;
                    int min = recordSeconds / 60;
                    int sec = recordSeconds % 60;
                    if (recordTimerText != null) {
                        recordTimerText.setText(String.format("%02d:%02d", min, sec));
                    }
                });
            }
        }, 1000, 1000);
        Toast.makeText(this, "Запись начата", Toast.LENGTH_SHORT).show();
    }

    private void stopRecording() {
        isRecording = false;
        if (recordTimer != null) {
            recordTimer.cancel();
            recordTimer = null;
        }
        if (recordTimerText != null) {
            recordTimerText.setText("00:00");
        }
        Toast.makeText(this, "Запись остановлена", Toast.LENGTH_SHORT).show();
    }

    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========
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

    private Drawable createAutoClickerIcon() {
        Bitmap b = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.WHITE);
        p.setStrokeWidth(5);
        p.setStyle(Paint.Style.STROKE);
        c.drawCircle(20, 30, 10, p);
        c.drawCircle(40, 30, 10, p);
        c.drawLine(20, 40, 40, 40, p);
        c.drawLine(30, 15, 30, 25, p);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(30, 15, 3, p);
        return new android.graphics.drawable.BitmapDrawable(getResources(), b);
    }

    private Drawable createRecordIcon() {
        Bitmap b = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.RED);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(30, 30, 20, p);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3);
        c.drawCircle(30, 30, 22, p);
        return new android.graphics.drawable.BitmapDrawable(getResources(), b);
    }

    private Drawable createTrashIcon() {
        Bitmap b = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.WHITE);
        p.setStrokeWidth(5);
        p.setStyle(Paint.Style.STROKE);
        c.drawRect(18, 22, 42, 48, p);
        c.drawLine(22, 16, 38, 16, p);
        c.drawLine(22, 16, 25, 22, p);
        c.drawLine(38, 16, 35, 22, p);
        c.drawLine(16, 22, 44, 22, p);
        c.drawLine(24, 28, 24, 42, p);
        c.drawLine(30, 28, 30, 42, p);
        c.drawLine(36, 28, 36, 42, p);
        return new android.graphics.drawable.BitmapDrawable(getResources(), b);
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

    // ========== ЖИЗНЕННЫЙ ЦИКЛ ==========
    @Override
    public void onResume() {
        super.onResume();
        if (floatingCircle != null && !isMainOverlayVisible) {
            floatingCircle.setVisibility(View.GONE);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (floatingCircle != null && !isMainOverlayVisible) {
            floatingCircle.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQUEST_MICROPHONE && results.length > 0) {
            Toast.makeText(this, results[0] == PackageManager.PERMISSION_GRANTED ?
                    "🎤 Микрофон разрешён" : "🎤 Микрофон НЕ разрешён", Toast.LENGTH_SHORT).show();
        }
        if (code == REQUEST_CAMERA && results.length > 0) {
            Toast.makeText(this, results[0] == PackageManager.PERMISSION_GRANTED ?
                    "📷 Камера разрешена" : "📷 Камера НЕ разрешена", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopAutoClicker();
        stopRecording();
        hideMainOverlay();
        hideAutoClickerOverlay();
        hideRecordOverlay();
        if (floatingCircle != null && windowManager != null) {
            windowManager.removeView(floatingCircle);
        }
    }
            }
