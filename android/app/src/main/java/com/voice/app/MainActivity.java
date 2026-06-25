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
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    private static final int REQUEST_MICROPHONE = 100;
    private static final int REQUEST_CAMERA = 102;
    private static final int REQUEST_OVERLAY_PERMISSION = 101;
    private static final int REQUEST_PICK_IMAGE = 999;

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

    private OverlayCharacterManager characterManager;

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

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        characterManager = new OverlayCharacterManager(this, windowManager);
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
        }
    }

    private void pickCharacterImage() {
        if (characterManager == null) {
            Toast.makeText(this, "Ошибка: менеджер персонажа не инициализирован", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "Выберите изображение персонажа"), REQUEST_PICK_IMAGE);
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

        // ===== КНОПКА ЗАКРЫТЬ (крестик) =====
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
        mainOverlay.addView(closeBtn);

        // ===== КНОПКА СВЕРНУТЬ (зелёная) =====
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
        mainOverlay.addView(minimizeBtn);

        // ===== КНОПКА ВЫБОРА ПЕРСОНАЖА (оранжевая) =====
        ImageButton pickCharacterBtn = createCircleButton(createCharacterIcon(), "#FF9800");
        FrameLayout.LayoutParams pickP = new FrameLayout.LayoutParams(70, 70, Gravity.BOTTOM | Gravity.END);
        pickP.setMargins(0, 0, 100, 40);
        pickCharacterBtn.setLayoutParams(pickP);
        pickCharacterBtn.setOnClickListener(v -> pickCharacterImage());
        mainOverlay.addView(pickCharacterBtn);

        // ===== КНОПКА ЗАКРЕПЛЕНИЯ (зелёная) =====
        ImageButton fixBtn = createCircleButton(createFixIcon(), "#4CAF50");
        FrameLayout.LayoutParams fixP = new FrameLayout.LayoutParams(70, 70, Gravity.BOTTOM | Gravity.END);
        fixP.setMargins(0, 0, 20, 40);
        fixBtn.setLayoutParams(fixP);
        fixBtn.setOnClickListener(v -> {
            if (characterManager != null && characterManager.isCharacterLoaded()) {
                characterManager.fixCharacter();
                Toast.makeText(this, "🔒 Персонаж зафиксирован", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Сначала загрузи персонажа", Toast.LENGTH_SHORT).show();
            }
        });
        mainOverlay.addView(fixBtn);

        // ===== КНОПКА УДАЛЕНИЯ (красная) =====
        ImageButton removeBtn = createCircleButton(createRemoveIcon(), "#E53935");
        FrameLayout.LayoutParams removeP = new FrameLayout.LayoutParams(70, 70, Gravity.BOTTOM | Gravity.START);
        removeP.setMargins(20, 0, 0, 40);
        removeBtn.setLayoutParams(removeP);
        removeBtn.setOnClickListener(v -> {
            if (characterManager != null) {
                characterManager.removeCharacter();
                Toast.makeText(this, "🗑 Персонаж удалён", Toast.LENGTH_SHORT).show();
            }
        });
        mainOverlay.addView(removeBtn);

        // ===== WebView (сайт) =====
        mainOverlay.addView(webView);

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

    // ===== ИКОНКИ =====
    private Drawable createCharacterIcon() {
        Bitmap b = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.WHITE);
        p.setStrokeWidth(6);
        p.setStyle(Paint.Style.STROKE);
        c.drawCircle(30, 20, 12, p);
        c.drawLine(30, 32, 30, 48, p);
        c.drawLine(30, 36, 18, 26, p);
        c.drawLine(30, 36, 42, 26, p);
        c.drawLine(30, 48, 20, 58, p);
        c.drawLine(30, 48, 40, 58, p);
        return new android.graphics.drawable.BitmapDrawable(getResources(), b);
    }

    private Drawable createFixIcon() {
        Bitmap b = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.WHITE);
        p.setStrokeWidth(6);
        p.setStyle(Paint.Style.STROKE);
        float cx = 30, cy = 30;
        c.drawRect(cx - 10, cy + 5, cx + 10, cy + 25, p);
        c.drawLine(cx - 6, cy + 5, cx - 6, cy - 5, p);
        c.drawLine(cx + 6, cy + 5, cx + 6, cy - 5, p);
        c.drawArc(cx - 10, cy - 15, cx + 10, cy - 5, 0, 180, false, p);
        return new android.graphics.drawable.BitmapDrawable(getResources(), b);
    }

    private Drawable createRemoveIcon() {
        Bitmap b = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(Color.WHITE);
        p.setStrokeWidth(8);
        p.setStyle(Paint.Style.STROKE);
        float cx = 30, cy = 30, o = 18;
        c.drawLine(cx - o, cy - o, cx + o, cy + o, p);
        c.drawLine(cx + o, cy - o, cx - o, cy + o, p);
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

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            if (characterManager != null && imageUri != null) {
                characterManager.loadCharacterFromUri(imageUri);
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
    public void onDestroy() {
        super.onDestroy();
        if (mainCircle != null && windowManager != null) {
            windowManager.removeView(mainCircle);
        }
        if (mainOverlay != null && windowManager != null && isMainOverlayVisible) {
            windowManager.removeView(mainOverlay);
        }
        if (characterManager != null) {
            characterManager.removeCharacter();
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
    }
                        }
