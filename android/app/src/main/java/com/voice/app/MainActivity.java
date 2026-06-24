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
    private FrameLayout overlayLayout;
    private WebView webView;
    private WindowManager.LayoutParams circleParams;
    private WindowManager.LayoutParams overlayParams;
    private boolean isOverlayVisible = false;
    private Bundle webViewState = null;
    
    // Три управляющих кружка
    private ImageButton autoClickerCircle;
    private ImageButton recordCircle;
    private ImageButton trashCircle;
    private WindowManager.LayoutParams autoParams;
    private WindowManager.LayoutParams recordParams;
    private WindowManager.LayoutParams trashParams;
    private boolean circlesVisible = false;

    // Автокликер
    private boolean isAutoClickerActive = false;
    private ArrayList<ClickPoint> clickPoints = new ArrayList<>();
    private Timer autoClickTimer;
    private int clickInterval = 1000;
    private String intervalUnit = "ms";
    private int pointCounter = 1;

    // Запись
    private boolean isRecording = false;
    private Timer recordTimer;
    private int recordSeconds = 0;
    private TextView recordTimerText;

    private float startX, startY;
    private int initialX, initialY;
    private boolean isDragging = false;

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

    // ========== ГЛАВНЫЙ КРУЖОК ==========
    private void createMainCircle() {
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
                        toggleOverlayAndCircles();
                    }
                    return true;
            }
            return false;
        });
        
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager != null) {
            windowManager.addView(floatingCircle, circleParams);
            Toast.makeText(this, "🎮 Кружок создан", Toast.LENGTH_SHORT).show();
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

    // ========== ОВЕРЛЕЙ С САЙТОМ ==========
    private void showOverlay() {
        if (isOverlayVisible) return;
        
        int flag = getOverlayFlag();
        
        overlayLayout = new FrameLayout(this);
        overlayLayout.setBackgroundColor(Color.parseColor("#DD1E1E1E"));
        overlayLayout.setPadding(15, 15, 15, 15);
        
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
        
        // Кнопки в оверлее
        ImageButton closeBtn = createCircleButton(createCloseIcon(), "#DD2C00");
        FrameLayout.LayoutParams closeP = new FrameLayout.LayoutParams(70, 70, Gravity.TOP | Gravity.START);
        closeP.setMargins(20, 40, 0, 0);
        closeBtn.setLayoutParams(closeP);
        closeBtn.setOnClickListener(v -> {
            hideAll();
            finishAffinity();
        });
        
        ImageButton minimizeBtn = createCircleButton(createMinimizeIcon(), "#4CAF50");
        FrameLayout.LayoutParams minP = new FrameLayout.LayoutParams(70, 70, Gravity.TOP | Gravity.END);
        minP.setMargins(0, 40, 20, 0);
        minimizeBtn.setLayoutParams(minP);
        minimizeBtn.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            webView.saveState(bundle);
            webViewState = bundle;
            hideAll();
            if (floatingCircle != null) {
                floatingCircle.setVisibility(View.VISIBLE);
            }
        });
        
        overlayLayout.addView(webView);
        overlayLayout.addView(closeBtn);
        overlayLayout.addView(minimizeBtn);
        
        overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                flag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        overlayParams.gravity = Gravity.CENTER;
        
        if (windowManager != null) {
            windowManager.addView(overlayLayout, overlayParams);
            isOverlayVisible = true;
        }
    }

    private void hideOverlay() {
        if (overlayLayout != null && windowManager != null && isOverlayVisible) {
            windowManager.removeView(overlayLayout);
            overlayLayout = null;
            isOverlayVisible = false;
        }
    }

    // ========== ТРИ УПРАВЛЯЮЩИХ КРУЖКА (ПОВЕРХ ВСЕГО) ==========
    private void showControlCircles() {
        if (circlesVisible) return;
        
        int flag = getOverlayFlag();
        int size = 80;
        int margin = 40;
        
        // 1. Автокликер (синий, слева)
        autoClickerCircle = new ImageButton(this);
        autoClickerCircle.setImageDrawable(createAutoClickerIcon());
        autoClickerCircle.setBackground(createCircleBackground("#3F51B5"));
        autoClickerCircle.setPadding(20, 20, 20, 20);
        autoClickerCircle.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        autoParams = new WindowManager.LayoutParams(size, size, flag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        autoParams.gravity = Gravity.BOTTOM | Gravity.START;
        autoParams.x = margin;
        autoParams.y = margin;
        autoClickerCircle.setOnClickListener(v -> showAutoClickerMenu());
        makeDraggable(autoClickerCircle, autoParams);
        
        // 2. Запись (красный, центр)
        recordCircle = new ImageButton(this);
        recordCircle.setImageDrawable(createRecordIcon());
        recordCircle.setBackground(createCircleBackground("#E53935"));
        recordCircle.setPadding(20, 20, 20, 20);
        recordCircle.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        recordParams = new WindowManager.LayoutParams(size, size, flag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        recordParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        recordParams.y = margin;
        recordCircle.setOnClickListener(v -> showRecordMenu());
        makeDraggable(recordCircle, recordParams);
        
        // 3. Корзина (тёмно-красный, справа)
        trashCircle = new ImageButton(this);
        trashCircle.setImageDrawable(createTrashIcon());
        trashCircle.setBackground(createCircleBackground("#880E4F"));
        trashCircle.setPadding(20, 20, 20, 20);
        trashCircle.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        trashParams = new WindowManager.LayoutParams(size, size, flag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        trashParams.gravity = Gravity.BOTTOM | Gravity.END;
        trashParams.x = margin;
        trashParams.y = margin;
        trashCircle.setOnClickListener(v -> {
            hideAll();
            if (floatingCircle != null && windowManager != null) {
                windowManager.removeView(floatingCircle);
                floatingCircle = null;
            }
            finishAffinity();
        });
        makeDraggable(trashCircle, trashParams);
        
        windowManager.addView(autoClickerCircle, autoParams);
        windowManager.addView(recordCircle, recordParams);
        windowManager.addView(trashCircle, trashParams);
        
        circlesVisible = true;
    }

    private void hideControlCircles() {
        if (!circlesVisible) return;
        try {
            if (autoClickerCircle != null && windowManager != null) {
                windowManager.removeView(autoClickerCircle);
                autoClickerCircle = null;
            }
            if (recordCircle != null && windowManager != null) {
                windowManager.removeView(recordCircle);
                recordCircle = null;
            }
            if (trashCircle != null && windowManager != null) {
                windowManager.removeView(trashCircle);
                trashCircle = null;
            }
        } catch (Exception e) {}
        circlesVisible = false;
    }

    private void toggleOverlayAndCircles() {
        if (isOverlayVisible) {
            hideAll();
            if (floatingCircle != null) {
                floatingCircle.setVisibility(View.VISIBLE);
            }
        } else {
            floatingCircle.setVisibility(View.GONE);
            showOverlay();
            showControlCircles();
        }
    }

    private void hideAll() {
        hideOverlay();
        hideControlCircles();
    }

    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========
    private int getOverlayFlag() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;
    }

    private ImageButton createCircleButton(Drawable icon, String color) {
        ImageButton btn = new ImageButton(this);
        btn.setImageDrawable(icon);
        btn.setBackground(createCircleBackground(color));
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

    private void makeDraggable(View view, WindowManager.LayoutParams params) {
        final float[] startX = new float[1];
        final float[] startY = new float[1];
        final int[] initX = new int[1];
        final int[] initY = new int[1];
        
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX[0] = event.getRawX();
                    startY[0] = event.getRawY();
                    initX[0] = params.x;
                    initY[0] = params.y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    params.x = initX[0] + (int) (event.getRawX() - startX[0]);
                    params.y = initY[0] + (int) (event.getRawY() - startY[0]);
                    if (windowManager != null) {
                        windowManager.updateViewLayout(view, params);
                    }
                    return true;
            }
            return false;
        });
    }

    // ========== ИКОНКИ ==========
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

    // ========== АВТОКЛИКЕР ==========
    private class ClickPoint {
        float x, y;
        ClickPoint(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    private void showAutoClickerMenu() {
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
            clickPoints.add(new ClickPoint(200 + pointCounter * 50, 300 + pointCounter * 30));
            Toast.makeText(this, "Точка " + pointCounter + " добавлена", Toast.LENGTH_SHORT).show();
            pointCounter++;
            popup.dismiss();
        });
        layout.addView(addBtn);
        
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
            popup.dismiss();
        });
        layout.addView(startBtn);
        
        Button clearBtn = new Button(this);
        clearBtn.setText("🗑 Очистить точки");
        clearBtn.setTextColor(Color.WHITE);
        clearBtn.setBackgroundColor(Color.parseColor("#DD2C00"));
        clearBtn.setOnClickListener(v -> {
            clickPoints.clear();
            pointCounter = 1;
            Toast.makeText(this, "Все точки удалены", Toast.LENGTH_SHORT).show();
            popup.dismiss();
        });
        layout.addView(clearBtn);
        
        popup.setContentView(layout);
        popup.setWidth(400);
        popup.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        popup.setBackgroundDrawable(new GradientDrawable());
        popup.setOutsideTouchable(true);
        popup.showAtLocation(floatingCircle, Gravity.CENTER, 0, 0);
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
                if (!isAutoClickerActive) return;
                runOnUiThread(() -> {
                    for (ClickPoint p : clickPoints) {
                        Toast.makeText(MainActivity.this, "Клик в (" + (int)p.x + ", " + (int)p.y + ")", Toast.LENGTH_SHORT).show();
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

    // ========== ЗАПИСЬ ЭКРАНА ==========
    private void showRecordMenu() {
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
        
        recordTimerText = new TextView(this);
        recordTimerText.setText("00:00");
        recordTimerText.setTextColor(Color.WHITE);
        recordTimerText.setTextSize(24);
        recordTimerText.setGravity(Gravity.CENTER);
        recordTimerText.setPadding(0, 0, 0, 20);
        layout.addView(recordTimerText);
        
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
                    popup.dismiss();
                }
            });
            delayLayout.addView(db);
        }
        layout.addView(delayLayout);
        
        if (isRecording) {
            Button pauseBtn = new Button(this);
            pauseBtn.setText("⏸ Пауза");
            pauseBtn.setTextColor(Color.WHITE);
            pauseBtn.setBackgroundColor(Color.parseColor("#FF9800"));
            pauseBtn.setOnClickListener(v -> {
                Toast.makeText(this, "Пауза", Toast.LENGTH_SHORT).show();
                popup.dismiss();
            });
            layout.addView(pauseBtn);
            
            Button stopBtn = new Button(this);
            stopBtn.setText("⏹ Остановить");
            stopBtn.setTextColor(Color.WHITE);
            stopBtn.setBackgroundColor(Color.parseColor("#DD2C00"));
            stopBtn.setOnClickListener(v -> {
                stopRecording();
                popup.dismiss();
            });
            layout.addView(stopBtn);
        }
        
        popup.setContentView(layout);
        popup.setWidth(400);
        popup.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        popup.setBackgroundDrawable(new GradientDrawable());
        popup.setOutsideTouchable(true);
        popup.showAtLocation(floatingCircle, Gravity.CENTER, 0, 0);
    }

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

    // ========== ЖИЗНЕННЫЙ ЦИКЛ ==========
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
        if (floatingCircle != null && windowManager != null) {
            windowManager.removeView(floatingCircle);
        }
        if (overlayLayout != null && windowManager != null && isOverlayVisible) {
            windowManager.removeView(overlayLayout);
        }
        hideControlCircles();
    }
    }
