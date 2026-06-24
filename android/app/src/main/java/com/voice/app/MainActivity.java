package com.voice.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
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
    private static final int REQUEST_SCREEN_RECORD = 103;
    private static final int REQUEST_ACCESSIBILITY = 105;

    private WindowManager windowManager;
    public static ImageButton mainCircle;
    private WindowManager.LayoutParams mainCircleParams;
    private FrameLayout mainOverlay;
    private WebView webView;
    private WindowManager.LayoutParams mainOverlayParams;
    private boolean isMainOverlayVisible = false;
    private Bundle webViewState = null;

    private float startX, startY;
    private int initialX, initialY;
    private boolean isDragging = false;

    // ========== АВТОКЛИКЕР ==========
    private ImageButton autoClickerCircle;
    private WindowManager.LayoutParams autoClickerParams;
    private boolean isAutoClickerCircleVisible = false;
    private boolean isAutoClickerMenuOpen = false;
    public static ArrayList<ClickPoint> clickPoints = new ArrayList<>();
    private Timer autoClickTimer;
    private int clickInterval = 1000;
    private int pointCounter = 1;
    private boolean isAutoClickerActive = false;

    // ========== ЗАПИСЬ ЭКРАНА ==========
    private ImageButton recordCircle;
    private WindowManager.LayoutParams recordParams;
    private boolean isRecordCircleVisible = false;
    private boolean isRecordMenuOpen = false;
    private MediaProjectionManager projectionManager;
    private Intent screenRecordIntent;

    // ===== ВНУТРЕННИЙ КЛАСС =====
    public static class ClickPoint {
        public float x, y;
        public int id;
        public View view;
        public ClickPoint(float x, float y, int id) {
            this.x = x;
            this.y = y;
            this.id = id;
            this.view = null;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Разрешения
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

        // Оверлей
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

        // Запуск сервиса для голоса
        Intent serviceIntent = new Intent(this, VoiceForegroundService.class);
        ContextCompat.startForegroundService(this, serviceIntent);

        // Для записи экрана
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        // WebView
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

    // ===== ПРОВЕРКА ACCESSIBILITY (БЕЗОПАСНАЯ) =====
    private boolean isAccessibilityServiceEnabled() {
        String service = getPackageName() + "/" + ClickAccessibilityService.class.getCanonicalName();
        try {
            String enabledServices = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return enabledServices != null && enabledServices.contains(service);
        } catch (Exception e) {
            return false;
        }
    }

    private void requestAccessibilityPermission() {
        new AlertDialog.Builder(this)
            .setTitle("Включите автокликер")
            .setMessage("Для работы автокликера нужно включить его в настройках специальных возможностей.\n\nПосле включения вернитесь в приложение.")
            .setPositiveButton("Перейти в настройки", (dialog, which) -> {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivityForResult(intent, REQUEST_ACCESSIBILITY);
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    // ===== ГЛАВНЫЙ КРУЖОК =====
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
                        toggleMainOverlay();
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

    // ===== ОВЕРЛЕЙ =====
    private void toggleMainOverlay() {
        if (isMainOverlayVisible) {
            hideMainOverlay();
            if (mainCircle != null) mainCircle.setVisibility(View.VISIBLE);
        } else {
            mainCircle.setVisibility(View.GONE);
            showMainOverlay();
        }
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

        // Кнопки управления
        ImageButton closeBtn = createCircleButton(createCloseIcon(), "#DD2C00");
        FrameLayout.LayoutParams closeP = new FrameLayout.LayoutParams(70, 70, Gravity.TOP | Gravity.START);
        closeP.setMargins(20, 40, 0, 0);
        closeBtn.setLayoutParams(closeP);
        closeBtn.setOnClickListener(v -> {
            hideMainOverlay();
            if (mainCircle != null) mainCircle.setVisibility(View.VISIBLE);
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
            if (mainCircle != null) mainCircle.setVisibility(View.VISIBLE);
        });

        // Три кнопки внизу
        LinearLayout bottomButtons = new LinearLayout(this);
        bottomButtons.setOrientation(LinearLayout.HORIZONTAL);
        bottomButtons.setGravity(Gravity.CENTER);
        bottomButtons.setPadding(20, 10, 20, 30);

        FrameLayout.LayoutParams bottomP = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        bottomP.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        bottomButtons.setLayoutParams(bottomP);

        ImageButton autoBtn = createCircleButton(createAutoClickerIcon(), "#3F51B5");
        LinearLayout.LayoutParams btnP = new LinearLayout.LayoutParams(100, 100);
        btnP.setMargins(20, 0, 20, 0);
        autoBtn.setLayoutParams(btnP);
        autoBtn.setOnClickListener(v -> {
            if (!isAccessibilityServiceEnabled()) {
                requestAccessibilityPermission();
            } else {
                showAutoClickerCircle();
            }
        });

        ImageButton recordBtn = createCircleButton(createRecordIcon(), "#E53935");
        recordBtn.setLayoutParams(btnP);
        recordBtn.setOnClickListener(v -> showRecordCircle());

        ImageButton trashBtn = createCircleButton(createTrashIcon(), "#880E4F");
        trashBtn.setLayoutParams(btnP);
        trashBtn.setOnClickListener(v -> {
            hideMainOverlay();
            hideAutoClickerCircle();
            hideRecordCircle();
            if (mainCircle != null && windowManager != null) {
                windowManager.removeView(mainCircle);
                mainCircle = null;
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

    // ===== АВТОКЛИКЕР =====
    private void showAutoClickerCircle() {
        if (isAutoClickerCircleVisible) return;
        int flag = getOverlayFlag();

        autoClickerCircle = new ImageButton(this);
        autoClickerCircle.setImageBitmap(createAutoClickerBitmap());

        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(Color.parseColor("#3F51B5"));
        d.setStroke(4, Color.WHITE);
        autoClickerCircle.setBackground(d);
        autoClickerCircle.setPadding(25, 25, 25, 25);
        autoClickerCircle.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);

        autoClickerParams = new WindowManager.LayoutParams(120, 120, flag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        autoClickerParams.gravity = Gravity.TOP | Gravity.START;
        autoClickerParams.x = 250;
        autoClickerParams.y = 300;

        autoClickerCircle.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = event.getRawX();
                    startY = event.getRawY();
                    initialX = autoClickerParams.x;
                    initialY = autoClickerParams.y;
                    isDragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - startX;
                    float dy = event.getRawY() - startY;
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isDragging = true;
                    autoClickerParams.x = initialX + (int) dx;
                    autoClickerParams.y = initialY + (int) dy;
                    if (windowManager != null) {
                        windowManager.updateViewLayout(autoClickerCircle, autoClickerParams);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!isDragging) {
                        showAutoClickerMenu();
                    }
                    return true;
            }
            return false;
        });

        windowManager.addView(autoClickerCircle, autoClickerParams);
        isAutoClickerCircleVisible = true;
        Toast.makeText(this, "🖱️ Кружок автокликера создан", Toast.LENGTH_SHORT).show();
    }

    private void hideAutoClickerCircle() {
        if (autoClickerCircle != null && windowManager != null && isAutoClickerCircleVisible) {
            windowManager.removeView(autoClickerCircle);
            autoClickerCircle = null;
            isAutoClickerCircleVisible = false;
        }
        for (ClickPoint point : clickPoints) {
            if (point.view != null && windowManager != null) {
                windowManager.removeView(point.view);
            }
        }
        clickPoints.clear();
        pointCounter = 1;
    }

    private Bitmap createAutoClickerBitmap() {
        int size = 80;
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.WHITE);
        p.setStrokeWidth(6);
        p.setStyle(Paint.Style.STROKE);
        c.drawCircle(25, 40, 15, p);
        c.drawCircle(55, 40, 15, p);
        c.drawLine(25, 55, 55, 55, p);
        c.drawLine(40, 20, 40, 35, p);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(40, 20, 6, p);
        return b;
    }

    private void showAutoClickerMenu() {
        if (isAutoClickerMenuOpen) return;
        isAutoClickerMenuOpen = true;

        // Проверяем, включен ли AccessibilityService
        if (!isAccessibilityServiceEnabled()) {
            requestAccessibilityPermission();
            isAutoClickerMenuOpen = false;
            return;
        }

        PopupWindow popup = new PopupWindow(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#1E1E1E"));
        layout.setPadding(30, 20, 30, 20);

        TextView title = new TextView(this);
        title.setText("🖱️ Автокликер");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        layout.addView(title);

        Button addBtn = new Button(this);
        addBtn.setText("➕ Добавить точку");
        addBtn.setTextColor(Color.WHITE);
        addBtn.setBackgroundColor(Color.parseColor("#3F51B5"));
        addBtn.setOnClickListener(v -> {
            addClickPoint();
            popup.dismiss();
            isAutoClickerMenuOpen = false;
        });
        layout.addView(addBtn);

        LinearLayout intLayout = new LinearLayout(this);
        intLayout.setOrientation(LinearLayout.HORIZONTAL);
        EditText intervalInput = new EditText(this);
        intervalInput.setHint("Интервал (мс)");
        intervalInput.setTextColor(Color.WHITE);
        intervalInput.setHintTextColor(Color.GRAY);
        intervalInput.setBackgroundColor(Color.parseColor("#333333"));
        intervalInput.setPadding(15, 10, 15, 10);
        intervalInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        intervalInput.setText(String.valueOf(clickInterval));
        intLayout.addView(intervalInput);

        Button saveIntervalBtn = new Button(this);
        saveIntervalBtn.setText("✅");
        saveIntervalBtn.setTextColor(Color.WHITE);
        saveIntervalBtn.setBackgroundColor(Color.parseColor("#555555"));
        saveIntervalBtn.setOnClickListener(v -> {
            try {
                clickInterval = Integer.parseInt(intervalInput.getText().toString());
                Toast.makeText(this, "Интервал: " + clickInterval + " мс", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Введите число", Toast.LENGTH_SHORT).show();
            }
        });
        intLayout.addView(saveIntervalBtn);
        layout.addView(intLayout);

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
                if (clickPoints.isEmpty()) {
                    Toast.makeText(this, "Добавьте хотя бы одну точку", Toast.LENGTH_SHORT).show();
                    return;
                }
                startAutoClicker();
                startBtn.setText("⏹ Остановить");
                Toast.makeText(this, "Автокликер запущен", Toast.LENGTH_SHORT).show();
            }
            popup.dismiss();
            isAutoClickerMenuOpen = false;
        });
        layout.addView(startBtn);

        Button clearBtn = new Button(this);
        clearBtn.setText("🗑 Очистить точки");
        clearBtn.setTextColor(Color.WHITE);
        clearBtn.setBackgroundColor(Color.parseColor("#DD2C00"));
        clearBtn.setOnClickListener(v -> {
            for (ClickPoint point : clickPoints) {
                if (point.view != null && windowManager != null) {
                    windowManager.removeView(point.view);
                }
            }
            clickPoints.clear();
            pointCounter = 1;
            Toast.makeText(this, "Все точки удалены", Toast.LENGTH_SHORT).show();
            popup.dismiss();
            isAutoClickerMenuOpen = false;
        });
        layout.addView(clearBtn);

        Button closeBtn = new Button(this);
        closeBtn.setText("✕ Закрыть");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setBackgroundColor(Color.parseColor("#555555"));
        closeBtn.setOnClickListener(v -> {
            popup.dismiss();
            isAutoClickerMenuOpen = false;
        });
        layout.addView(closeBtn);

        popup.setContentView(layout);
        popup.setWidth(450);
        popup.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        popup.setBackgroundDrawable(new GradientDrawable());
        popup.setOutsideTouchable(true);
        popup.showAtLocation(mainCircle != null ? mainCircle : mainOverlay, Gravity.CENTER, 0, 0);
        popup.setOnDismissListener(() -> isAutoClickerMenuOpen = false);
    }

    private void addClickPoint() {
        int flag = getOverlayFlag();
        final int pointId = pointCounter++;

        FrameLayout pointView = new FrameLayout(this);
        pointView.setBackground(createCircleBackground("#FF5722"));
        pointView.setPadding(10, 10, 10, 10);

        TextView numText = new TextView(this);
        numText.setText(String.valueOf(pointId));
        numText.setTextColor(Color.WHITE);
        numText.setTextSize(18);
        numText.setPadding(8, 4, 8, 4);
        pointView.addView(numText);

        WindowManager.LayoutParams pointParams = new WindowManager.LayoutParams(
                70, 70, flag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        pointParams.gravity = Gravity.TOP | Gravity.START;
        pointParams.x = 300 + pointId * 20;
        pointParams.y = 400 + pointId * 15;

        final float[] pStartX = new float[1];
        final float[] pStartY = new float[1];
        final int[] pInitX = new int[1];
        final int[] pInitY = new int[1];
        final boolean[] pIsDragging = new boolean[1];

        pointView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    pStartX[0] = event.getRawX();
                    pStartY[0] = event.getRawY();
                    pInitX[0] = pointParams.x;
                    pInitY[0] = pointParams.y;
                    pIsDragging[0] = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - pStartX[0];
                    float dy = event.getRawY() - pStartY[0];
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) pIsDragging[0] = true;
                    pointParams.x = pInitX[0] + (int) dx;
                    pointParams.y = pInitY[0] + (int) dy;
                    if (windowManager != null) {
                        windowManager.updateViewLayout(pointView, pointParams);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!pIsDragging[0]) {
                        removeClickPoint(pointId, pointView);
                    }
                    return true;
            }
            return false;
        });

        ClickPoint newPoint = new ClickPoint(pointParams.x, pointParams.y, pointId);
        newPoint.view = pointView;
        clickPoints.add(newPoint);

        if (windowManager != null) {
            windowManager.addView(pointView, pointParams);
        }
        Toast.makeText(this, "Точка " + pointId + " добавлена", Toast.LENGTH_SHORT).show();
    }

    private void removeClickPoint(int id, View view) {
        if (windowManager != null && view != null) {
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

    private void startAutoClicker() {
        if (clickPoints.isEmpty()) {
            Toast.makeText(this, "Нет точек для кликов", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isAccessibilityServiceEnabled()) {
            requestAccessibilityPermission();
            return;
        }
        isAutoClickerActive = true;
        autoClickTimer = new Timer();
        autoClickTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!isAutoClickerActive) return;
                runOnUiThread(() -> {
                    ClickAccessibilityService.performClick(clickPoints);
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

    // ===== ЗАПИСЬ ЭКРАНА =====
    private void showRecordCircle() {
        if (isRecordCircleVisible) return;
        int flag = getOverlayFlag();

        recordCircle = new ImageButton(this);
        recordCircle.setImageBitmap(createRecordBitmap());

        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(Color.parseColor("#E53935"));
        d.setStroke(4, Color.WHITE);
        recordCircle.setBackground(d);
        recordCircle.setPadding(25, 25, 25, 25);
        recordCircle.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);

        recordParams = new WindowManager.LayoutParams(120, 120, flag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        recordParams.gravity = Gravity.TOP | Gravity.START;
        recordParams.x = 400;
        recordParams.y = 300;

        recordCircle.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = event.getRawX();
                    startY = event.getRawY();
                    initialX = recordParams.x;
                    initialY = recordParams.y;
                    isDragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - startX;
                    float dy = event.getRawY() - startY;
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isDragging = true;
                    recordParams.x = initialX + (int) dx;
                    recordParams.y = initialY + (int) dy;
                    if (windowManager != null) {
                        windowManager.updateViewLayout(recordCircle, recordParams);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!isDragging) {
                        showRecordMenu();
                    }
                    return true;
            }
            return false;
        });

        windowManager.addView(recordCircle, recordParams);
        isRecordCircleVisible = true;
        Toast.makeText(this, "🎥 Кружок записи экрана создан", Toast.LENGTH_SHORT).show();
    }

    private void hideRecordCircle() {
        if (recordCircle != null && windowManager != null && isRecordCircleVisible) {
            windowManager.removeView(recordCircle);
            recordCircle = null;
            isRecordCircleVisible = false;
        }
    }

    private Bitmap createRecordBitmap() {
        int size = 80;
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.RED);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(40, 40, 28, p);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4);
        c.drawCircle(40, 40, 30, p);
        return b;
    }

    private void showRecordMenu() {
        if (isRecordMenuOpen) return;
        isRecordMenuOpen = true;

        PopupWindow popup = new PopupWindow(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#1E1E1E"));
        layout.setPadding(30, 20, 30, 20);

        TextView title = new TextView(this);
        title.setText("🎥 Запись экрана");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        layout.addView(title);

        TextView timerText = new TextView(this);
        timerText.setText("00:00");
        timerText.setTextColor(Color.WHITE);
        timerText.setTextSize(30);
        timerText.setGravity(Gravity.CENTER);
        timerText.setPadding(0, 10, 0, 10);
        layout.addView(timerText);

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
                if (!ScreenRecordService.isRunning) {
                    screenRecordIntent = projectionManager.createScreenCaptureIntent();
                    startActivityForResult(screenRecordIntent, REQUEST_SCREEN_RECORD);
                    ScreenRecordService.delaySeconds = d;
                }
                popup.dismiss();
                isRecordMenuOpen = false;
            });
            delayLayout.addView(db);
        }
        layout.addView(delayLayout);

        if (ScreenRecordService.isRunning) {
            Button stopBtn = new Button(this);
            stopBtn.setText("⏹ Остановить");
            stopBtn.setTextColor(Color.WHITE);
            stopBtn.setBackgroundColor(Color.parseColor("#DD2C00"));
            stopBtn.setOnClickListener(v -> {
                stopScreenRecording();
                popup.dismiss();
                isRecordMenuOpen = false;
            });
            layout.addView(stopBtn);
        }

        Button closeBtn = new Button(this);
        closeBtn.setText("✕ Закрыть");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setBackgroundColor(Color.parseColor("#555555"));
        closeBtn.setOnClickListener(v -> {
            popup.dismiss();
            isRecordMenuOpen = false;
        });
        layout.addView(closeBtn);

        popup.setContentView(layout);
        popup.setWidth(450);
        popup.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        popup.setBackgroundDrawable(new GradientDrawable());
        popup.setOutsideTouchable(true);
        popup.showAtLocation(mainCircle != null ? mainCircle : mainOverlay, Gravity.CENTER, 0, 0);
        popup.setOnDismissListener(() -> isRecordMenuOpen = false);
    }

    private void stopScreenRecording() {
        Intent stopIntent = new Intent(this, ScreenRecordService.class);
        stopIntent.setAction("STOP");
        ContextCompat.startForegroundService(this, stopIntent);
    }

    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====
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

    private Drawable createCircleBackground(String color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(Color.parseColor(color));
        d.setStroke(4, Color.WHITE);
        return d;
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

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SCREEN_RECORD) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                Intent serviceIntent = new Intent(this, ScreenRecordService.class);
                serviceIntent.setAction("START");
                serviceIntent.putExtra("resultCode", resultCode);
                serviceIntent.putExtra("data", data);
                ContextCompat.startForegroundService(this, serviceIntent);
                Toast.makeText(this, "Запись экрана начата", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Запись экрана не разрешена", Toast.LENGTH_SHORT).show();
            }
        }
        if (requestCode == REQUEST_ACCESSIBILITY) {
            if (isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "✅ Автокликер включен!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mainCircle != null && !isMainOverlayVisible) {
            mainCircle.setVisibility(View.GONE);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mainCircle != null && !isMainOverlayVisible) {
            mainCircle.setVisibility(View.VISIBLE);
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
        if (mainCircle != null && windowManager != null) {
            windowManager.removeView(mainCircle);
        }
        if (mainOverlay != null && windowManager != null && isMainOverlayVisible) {
            windowManager.removeView(mainOverlay);
        }
        hideAutoClickerCircle();
        hideRecordCircle();
    }
    }
