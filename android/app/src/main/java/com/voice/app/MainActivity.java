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
    private static final int REQUEST_SCREEN_RECORD = 103;
    
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

    // Кружки управления
    private ImageButton autoClickerCircle;
    private ImageButton recordCircle;
    private ImageButton trashCircle;
    private WindowManager.LayoutParams autoClickerParams;
    private WindowManager.LayoutParams recordParams;
    private WindowManager.LayoutParams trashParams;
    private boolean areControlCirclesVisible = false;

    // Автокликер
    private boolean isAutoClickerActive = false;
    private ArrayList<float[]> clickPoints = new ArrayList<>();
    private Timer autoClickTimer;
    private int clickInterval = 1000;
    private String intervalUnit = "ms";
    private int pointCounter = 1;

    // Запись экрана
    private boolean isRecording = false;
    private boolean isPaused = false;
    private Timer recordTimer;
    private int recordSeconds = 0;
    private TextView recordTimerTextView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Запросы разрешений
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
                            showOverlayAndControlCircles();
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

    // ========== ОВЕРЛЕЙ + УПРАВЛЯЮЩИЕ КРУЖКИ ==========
    private void showOverlayAndControlCircles() {
        showOverlay();
        showControlCircles();
    }

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
        
        // Кнопка ЗАКРЫТЬ (красная, слева вверху)
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
            hideOverlayAndControlCircles();
            if (floatingCircle != null && windowManager != null) {
                windowManager.removeView(floatingCircle);
                floatingCircle = null;
            }
            finishAffinity();
        });
        
        // Кнопка СВЕРНУТЬ (зелёная, справа вверху)
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
            hideOverlayAndControlCircles();
            if (floatingCircle != null) {
                floatingCircle.setVisibility(View.VISIBLE);
            }
        });
        
        overlayLayout.addView(webView);
        overlayLayout.addView(closeButton);
        overlayLayout.addView(minimizeButton);
        
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

    // ========== УПРАВЛЯЮЩИЕ КРУЖКИ (внизу) ==========
    private void showControlCircles() {
        if (areControlCirclesVisible) return;
        
        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }
        
        // 1. Автокликер (синий)
        autoClickerCircle = new ImageButton(this);
        autoClickerCircle.setImageDrawable(createAutoClickerIcon());
        autoClickerCircle.setBackground(createCircleButtonBackground(Color.parseColor("#3F51B5")));
        autoClickerCircle.setPadding(20, 20, 20, 20);
        autoClickerCircle.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        autoClickerParams = new WindowManager.LayoutParams(90, 90, layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        autoClickerParams.gravity = Gravity.BOTTOM | Gravity.START;
        autoClickerParams.x = 30;
        autoClickerParams.y = 30;
        autoClickerCircle.setOnClickListener(v -> showAutoClickerMenu());
        
        // 2. Запись экрана (красный)
        recordCircle = new ImageButton(this);
        recordCircle.setImageDrawable(createRecordIcon());
        recordCircle.setBackground(createCircleButtonBackground(Color.parseColor("#E53935")));
        recordCircle.setPadding(20, 20, 20, 20);
        recordCircle.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        recordParams = new WindowManager.LayoutParams(90, 90, layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        recordParams.gravity = Gravity.BOTTOM | Gravity.CENTER;
        recordParams.y = 30;
        recordCircle.setOnClickListener(v -> showRecordMenu());
        
        // 3. Корзина (тёмно-красный)
        trashCircle = new ImageButton(this);
        trashCircle.setImageDrawable(createTrashIcon());
        trashCircle.setBackground(createCircleButtonBackground(Color.parseColor("#880E4F")));
        trashCircle.setPadding(20, 20, 20, 20);
        trashCircle.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        trashParams = new WindowManager.LayoutParams(90, 90, layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        trashParams.gravity = Gravity.BOTTOM | Gravity.END;
        trashParams.x = 30;
        trashParams.y = 30;
        trashCircle.setOnClickListener(v -> {
            hideOverlayAndControlCircles();
            if (floatingCircle != null && windowManager != null) {
                windowManager.removeView(floatingCircle);
                floatingCircle = null;
            }
            finishAffinity();
        });
        
        windowManager.addView(autoClickerCircle, autoClickerParams);
        windowManager.addView(recordCircle, recordParams);
        windowManager.addView(trashCircle, trashParams);
        
        areControlCirclesVisible = true;
    }

    private void hideControlCircles() {
        if (!areControlCirclesVisible) return;
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
        areControlCirclesVisible = false;
    }

    private void hideOverlayAndControlCircles() {
        if (overlayLayout != null && windowManager != null && isOverlayVisible) {
            windowManager.removeView(overlayLayout);
            overlayLayout = null;
            isOverlayVisible = false;
        }
        hideControlCircles();
    }

    // ========== ИКОНКИ ДЛЯ КРУЖКОВ ==========
    private Drawable createAutoClickerIcon() {
        Bitmap bitmap = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(5);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(15, 20, 45, 20, paint);
        canvas.drawLine(15, 40, 45, 40, paint);
        canvas.drawLine(30, 20, 30, 40, paint);
        paint.setStrokeWidth(3);
        canvas.drawCircle(20, 30, 8, paint);
        canvas.drawCircle(40, 30, 8, paint);
        return new android.graphics.drawable.BitmapDrawable(getResources(), bitmap);
    }

    private Drawable createRecordIcon() {
        Bitmap bitmap = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(30, 30, 20, paint);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
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
        paint.setStrokeWidth(5);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(18, 22, 42, 48, paint);
        canvas.drawLine(22, 16, 38, 16, paint);
        canvas.drawLine(22, 16, 25, 22, paint);
        canvas.drawLine(38, 16, 35, 22, paint);
        canvas.drawLine(16, 22, 44, 22, paint);
        canvas.drawLine(24, 28, 24, 42, paint);
        canvas.drawLine(30, 28, 30, 42, paint);
        canvas.drawLine(36, 28, 36, 42, paint);
        return new android.graphics.drawable.BitmapDrawable(getResources(), bitmap);
    }

    private Drawable createCloseIcon() {
        Bitmap bitmap = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(8);
        paint.setStyle(Paint.Style.STROKE);
        float centerX = 30, centerY = 30, offset = 15;
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
        float centerX = 30, centerY = 30, width = 25, height = 15;
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

    // ========== АВТОКЛИКЕР ==========
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
        
        Button addPointBtn = new Button(this);
        addPointBtn.setText("➕ Добавить точку");
        addPointBtn.setTextColor(Color.WHITE);
        addPointBtn.setBackgroundColor(Color.parseColor("#3F51B5"));
        addPointBtn.setOnClickListener(v -> {
            Toast.makeText(this, "Точка " + pointCounter + " добавлена (симуляция)", Toast.LENGTH_SHORT).show();
            clickPoints.add(new float[]{200 + pointCounter * 50, 300 + pointCounter * 30});
            pointCounter++;
            popup.dismiss();
        });
        layout.addView(addPointBtn);
        
        LinearLayout intervalLayout = new LinearLayout(this);
        intervalLayout.setOrientation(LinearLayout.HORIZONTAL);
        EditText intervalInput = new EditText(this);
        intervalInput.setHint("Интервал");
        intervalInput.setTextColor(Color.WHITE);
        intervalInput.setHintTextColor(Color.GRAY);
        intervalInput.setBackgroundColor(Color.parseColor("#333333"));
        intervalInput.setPadding(15, 10, 15, 10);
        intervalInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        intervalInput.setText("1000");
        intervalLayout.addView(intervalInput);
        
        String[] units = {"мс", "сек", "мин"};
        for (String unit : units) {
            Button unitBtn = new Button(this);
            unitBtn.setText(unit);
            unitBtn.setTextColor(Color.WHITE);
            unitBtn.setBackgroundColor(Color.parseColor("#555555"));
            unitBtn.setOnClickListener(v -> {
                intervalUnit = unit;
                Toast.makeText(this, "Единица: " + unit, Toast.LENGTH_SHORT).show();
            });
            intervalLayout.addView(unitBtn);
        }
        layout.addView(intervalLayout);
        
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
                try {
                    int val = Integer.parseInt(intervalInput.getText().toString());
                    switch (intervalUnit) {
                        case "мс": clickInterval = val; break;
                        case "сек": clickInterval = val * 1000; break;
                        case "мин": clickInterval = val * 60 * 1000; break;
                    }
                } catch (Exception e) {}
                startAutoClicker();
                startStopBtn.setText("⏹ Остановить");
                Toast.makeText(this, "Автокликер запущен", Toast.LENGTH_SHORT).show();
            }
            popup.dismiss();
        });
        layout.addView(startStopBtn);
        
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
        popup.showAtLocation(overlayLayout != null ? overlayLayout : floatingCircle, Gravity.CENTER, 0, 0);
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
                    for (float[] point : clickPoints) {
                        Toast.makeText(MainActivity.this, "Клик в точке (" + (int)point[0] + ", " + (int)point[1] + ")", Toast.LENGTH_SHORT).show();
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
        
        recordTimerTextView = new TextView(this);
        recordTimerTextView.setText("00:00");
        recordTimerTextView.setTextColor(Color.WHITE);
        recordTimerTextView.setTextSize(24);
        recordTimerTextView.setGravity(Gravity.CENTER);
        recordTimerTextView.setPadding(0, 0, 0, 20);
        layout.addView(recordTimerTextView);
        
        LinearLayout delayLayout = new LinearLayout(this);
        delayLayout.setOrientation(LinearLayout.HORIZONTAL);
        int[] delays = {0, 5, 10, 15, 30};
        String[] delayLabels = {"Сразу", "5с", "10с", "15с", "30с"};
        for (int i = 0; i < delays.length; i++) {
            Button delayBtn = new Button(this);
            delayBtn.setText(delayLabels[i]);
            delayBtn.setTextColor(Color.WHITE);
            delayBtn.setBackgroundColor(Color.parseColor("#444444"));
            int finalDelay = delays[i];
            delayBtn.setOnClickListener(v -> {
                if (!isRecording) {
                    startScreenRecording(finalDelay);
                    popup.dismiss();
                }
            });
            delayLayout.addView(delayBtn);
        }
        layout.addView(delayLayout);
        
        if (isRecording) {
            Button pauseBtn = new Button(this);
            pauseBtn.setText(isPaused ? "▶ Возобновить" : "⏸ Пауза");
            pauseBtn.setTextColor(Color.WHITE);
            pauseBtn.setBackgroundColor(Color.parseColor("#FF9800"));
            pauseBtn.setOnClickListener(v -> {
                isPaused = !isPaused;
                Toast.makeText(this, isPaused ? "Запись на паузе" : "Запись возобновлена", Toast.LENGTH_SHORT).show();
                popup.dismiss();
            });
            layout.addView(pauseBtn);
            
            Button stopBtn = new Button(this);
            stopBtn.setText("⏹ Остановить");
            stopBtn.setTextColor(Color.WHITE);
            stopBtn.setBackgroundColor(Color.parseColor("#DD2C00"));
            stopBtn.setOnClickListener(v -> {
                stopScreenRecording();
                popup.dismiss();
            });
            layout.addView(stopBtn);
        }
        
        popup.setContentView(layout);
        popup.setWidth(400);
        popup.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        popup.setBackgroundDrawable(new GradientDrawable());
        popup.setOutsideTouchable(true);
        popup.showAtLocation(overlayLayout != null ? overlayLayout : floatingCircle, Gravity.CENTER, 0, 0);
    }

    private void startScreenRecording(int delay) {
        if (delay > 0) {
            Toast.makeText(this, "Запись через " + delay + " секунд", Toast.LENGTH_SHORT).show();
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                startRecording();
            }, delay * 1000);
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        isRecording = true;
        isPaused = false;
        recordSeconds = 0;
        if (recordTimer != null) recordTimer.cancel();
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
        hideControlCircles();
    }
                                  }
