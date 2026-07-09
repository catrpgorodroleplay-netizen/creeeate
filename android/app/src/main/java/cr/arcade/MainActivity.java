package com.cr.arcade;

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

    private WindowManager windowManager;
    public static FrameLayout mainCircleContainer;
    private WindowManager.LayoutParams mainCircleParams;
    
    private float startX, startY;
    private int initialX, initialY;
    private boolean isDragging = false;
    
    private FrameLayout mainOverlay;
    private WindowManager.LayoutParams mainOverlayParams;
    private boolean isMainOverlayVisible = false;
    private float overlayAlpha = 0.85f;

    // Компоненты персонажей
    private FrameLayout characterContainer;
    private ImageView characterView;
    private WindowManager.LayoutParams characterParams;
    private Bitmap currentCharacterBitmap;
    private boolean isCharacterFixed = false;
    private boolean isCharacterModeActive = false;
    
    private float lastTouchX, lastTouchY;
    private float initialPinchDistance = 0;
    
    private FrameLayout menuContainer;
    private FrameLayout characterListContainer;
    
    // Система персонажей
    private ArrayList<CharacterData> characters = new ArrayList<>();
    private SharedPreferences prefs;
    
    private boolean isCharacterListOpen = false;
    
    // Режимы изменения размера
    private boolean useSeekBarMode = true;
    
    private View.OnTouchListener circleTouchListener;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            prefs = getSharedPreferences("characters", MODE_PRIVATE);
            loadCharacters();
        } catch (Exception e) {
            e.printStackTrace();
        }

        requestPermissionsIfNeeded();

        try {
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
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            if (bridge != null && bridge.getWebView() != null) {
                bridge.getWebView().setWebChromeClient(new WebChromeClient() {
                    @Override
                    public void onPermissionRequest(PermissionRequest request) {
                        try {
                            request.grant(new String[]{
                                    PermissionRequest.RESOURCE_AUDIO_CAPTURE,
                                    PermissionRequest.RESOURCE_VIDEO_CAPTURE
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== КЛАСС ДАННЫХ ПЕРСОНАЖА ====================
    
    private static class CharacterData {
        String name;
        String path;
        long timestamp;
        int width;
        int height;
        
        CharacterData(String name, String path) {
            this.name = name;
            this.path = path;
            this.timestamp = System.currentTimeMillis();
            this.width = 300;
            this.height = 300;
        }
        
        CharacterData(JSONObject json) throws Exception {
            this.name = json.getString("name");
            this.path = json.getString("path");
            this.timestamp = json.optLong("timestamp", System.currentTimeMillis());
            this.width = json.optInt("width", 300);
            this.height = json.optInt("height", 300);
        }
        
        JSONObject toJSON() throws Exception {
            JSONObject json = new JSONObject();
            json.put("name", name);
            json.put("path", path);
            json.put("timestamp", timestamp);
            json.put("width", width);
            json.put("height", height);
            return json;
        }
    }

    // ==================== СОХРАНЕНИЕ ПЕРСОНАЖЕЙ ====================
    
    private void loadCharacters() {
        try {
            characters.clear();
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

    private void requestPermissionsIfNeeded() {
        try {
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== ГЛАВНЫЙ КРУЖОК ====================

    private void createMainCircle() {
        try {
            int flag = getOverlayFlag();
            
            mainCircleContainer = new FrameLayout(this);
            mainCircleContainer.setBackgroundColor(Color.TRANSPARENT);
            
            ImageButton iconButton = new ImageButton(this);
            iconButton.setImageBitmap(createGamepadBitmap());
            iconButton.setBackgroundColor(Color.TRANSPARENT);
            iconButton.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
            iconButton.setPadding(30, 30, 30, 30);
            iconButton.setClickable(false);
            iconButton.setFocusable(false);
            
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(Color.parseColor("#CC0000"));
            d.setStroke(8, Color.parseColor("#FF4444"));
            mainCircleContainer.setBackground(d);
            
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            );
            mainCircleContainer.addView(iconButton, iconParams);
            
            mainCircleParams = new WindowManager.LayoutParams(160, 160, flag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
            mainCircleParams.gravity = Gravity.TOP | Gravity.START;
            mainCircleParams.x = 100;
            mainCircleParams.y = 200;

            circleTouchListener = new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    try {
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
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return false;
                }
            };
            mainCircleContainer.setOnTouchListener(circleTouchListener);

            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (windowManager != null) {
                windowManager.addView(mainCircleContainer, mainCircleParams);
                Toast.makeText(this, "CR Arcade готов", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Bitmap createGamepadBitmap() {
        try {
            int size = 100;
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint();
            paint.setAntiAlias(true);
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(8);

            float cx = size / 2f, cy = size / 2f;
            canvas.drawRoundRect(cx - 38, cy - 26, cx + 38, cy + 26, 20, 20, paint);
            canvas.drawCircle(cx - 30, cy, 16, paint);
            canvas.drawCircle(cx + 30, cy, 16, paint);
            paint.setStrokeWidth(6);
            canvas.drawLine(cx - 22, cy - 10, cx - 22, cy + 10, paint);
            canvas.drawLine(cx - 26, cy, cx - 18, cy, paint);
            canvas.drawCircle(cx + 22, cy - 8, 7, paint);
            canvas.drawCircle(cx + 22, cy + 8, 7, paint);
            canvas.drawCircle(cx + 32, cy, 7, paint);
            canvas.drawCircle(cx + 12, cy, 7, paint);
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ==================== СОЗДАНИЕ ИКОНОК ====================

    private Drawable createHomeIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(8);
            
            float cx = 50, cy = 50;
            c.drawLine(cx - 36, cy + 12, cx, cy - 28, p);
            c.drawLine(cx + 36, cy + 12, cx, cy - 28, p);
            c.drawLine(cx - 36, cy + 12, cx - 36, cy + 38, p);
            c.drawLine(cx + 36, cy + 12, cx + 36, cy + 38, p);
            c.drawLine(cx - 36, cy + 38, cx + 36, cy + 38, p);
            c.drawLine(cx - 12, cy + 38, cx - 12, cy + 16, p);
            c.drawLine(cx + 12, cy + 38, cx + 12, cy + 16, p);
            c.drawLine(cx - 12, cy + 16, cx + 12, cy + 16, p);
            c.drawLine(cx + 8, cy - 22, cx + 8, cy - 36, p);
            c.drawLine(cx + 8, cy - 36, cx + 22, cy - 36, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Drawable createCloseIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(8);
            
            float cx = 50, cy = 50;
            c.drawLine(cx - 28, cy - 28, cx + 28, cy + 28, p);
            c.drawLine(cx + 28, cy - 28, cx - 28, cy + 28, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Drawable createCharacterIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(7);
            
            float cx = 50, cy = 50;
            c.drawCircle(cx, cy - 10, 18, p);
            c.drawLine(cx, cy + 8, cx, cy + 36, p);
            c.drawLine(cx, cy + 16, cx - 24, cy + 6, p);
            c.drawLine(cx, cy + 16, cx + 24, cy + 6, p);
            c.drawLine(cx, cy + 36, cx - 18, cy + 48, p);
            c.drawLine(cx, cy + 36, cx + 18, cy + 48, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Drawable createSettingsIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(7);
            
            float cx = 50, cy = 50;
            c.drawCircle(cx, cy, 18, p);
            c.drawLine(cx - 10, cy - 28, cx - 10, cy - 18, p);
            c.drawLine(cx + 10, cy - 28, cx + 10, cy - 18, p);
            c.drawLine(cx - 10, cy + 18, cx - 10, cy + 28, p);
            c.drawLine(cx + 10, cy + 18, cx + 10, cy + 28, p);
            c.drawLine(cx - 28, cy - 10, cx - 18, cy - 10, p);
            c.drawLine(cx - 28, cy + 10, cx - 18, cy + 10, p);
            c.drawLine(cx + 18, cy - 10, cx + 28, cy - 10, p);
            c.drawLine(cx + 18, cy + 10, cx + 28, cy + 10, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Drawable createExitIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(8);
            
            float cx = 50, cy = 50;
            c.drawRect(cx - 26, cy - 32, cx + 26, cy + 32, p);
            c.drawCircle(cx + 10, cy, 6, p);
            c.drawLine(cx + 26, cy - 10, cx + 40, cy - 10, p);
            c.drawLine(cx + 26, cy + 10, cx + 40, cy + 10, p);
            c.drawLine(cx + 40, cy - 10, cx + 40, cy + 10, p);
            c.drawLine(cx - 26, cy, cx - 12, cy, p);
            c.drawLine(cx - 16, cy - 8, cx - 12, cy, p);
            c.drawLine(cx - 16, cy + 8, cx - 12, cy, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Drawable createHideIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(7);
            
            float cx = 50, cy = 50;
            c.drawOval(cx - 28, cy - 18, cx + 28, cy + 18, p);
            c.drawCircle(cx, cy, 8, p);
            p.setStrokeWidth(8);
            c.drawLine(cx - 22, cy - 12, cx + 22, cy + 12, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Drawable createAddIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(7);
            
            float cx = 40, cy = 40;
            c.drawLine(cx - 18, cy, cx + 18, cy, p);
            c.drawLine(cx, cy - 18, cx, cy + 18, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Drawable createDeleteIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(7);
            
            float cx = 40, cy = 40;
            c.drawLine(cx - 22, cy - 18, cx + 22, cy - 18, p);
            c.drawLine(cx - 12, cy - 26, cx + 12, cy - 26, p);
            c.drawLine(cx - 22, cy - 18, cx - 22, cy + 18, p);
            c.drawLine(cx + 22, cy - 18, cx + 22, cy + 18, p);
            c.drawArc(cx - 16, cy - 32, cx + 16, cy - 12, 0, 180, false, p);
            p.setStrokeWidth(5);
            c.drawLine(cx - 10, cy, cx + 10, cy, p);
            c.drawLine(cx, cy - 10, cx, cy + 10, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Drawable createScreenIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(6);
            
            float cx = 40, cy = 40;
            c.drawRect(cx - 28, cy - 20, cx + 28, cy + 20, p);
            c.drawLine(cx - 16, cy + 20, cx + 16, cy + 20, p);
            c.drawLine(cx - 8, cy + 20, cx - 8, cy + 28, p);
            c.drawLine(cx + 8, cy + 20, cx + 8, cy + 28, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Drawable createFloatIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(6);
            
            float cx = 40, cy = 40;
            c.drawCircle(cx, cy, 26, p);
            c.drawCircle(cx, cy, 8, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Drawable createAlphaIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(6);
            
            float cx = 40, cy = 40;
            c.drawCircle(cx, cy, 26, p);
            c.drawText("α", cx - 12, cy + 12, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ==================== ОВЕРЛЕЙ С КРУГЛЫМИ КНОПКАМИ СЛЕВА ====================

    private void showMainOverlay() {
        try {
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
            innerContainer.setAlpha(overlayAlpha);

            LinearLayout mainLayout = new LinearLayout(this);
            mainLayout.setOrientation(LinearLayout.HORIZONTAL);
            mainLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
            ));

            // ============ ЛЕВАЯ ПАНЕЛЬ С КРУГЛЫМИ КНОПКАМИ ============
            LinearLayout leftBar = new LinearLayout(this);
            leftBar.setOrientation(LinearLayout.VERTICAL);
            leftBar.setGravity(Gravity.CENTER);
            leftBar.setPadding(8, 0, 20, 0);
            
            ImageButton homeBtn = createRoundButton(createHomeIcon(), "#CC0000");
            homeBtn.setOnClickListener(v -> {
                try {
                    hideMainOverlay();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            leftBar.addView(homeBtn);
            
            ImageButton charsBtn = createRoundButton(createCharacterIcon(), "#CC0000");
            charsBtn.setOnClickListener(v -> {
                try {
                    hideMainOverlay();
                    showCharacterListFullscreen();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            leftBar.addView(charsBtn);
            
            ImageButton settingsBtn = createRoundButton(createSettingsIcon(), "#CC0000");
            settingsBtn.setOnClickListener(v -> {
                try {
                    // Настройки оверлея
                    showOverlaySettings();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            leftBar.addView(settingsBtn);
            
            ImageButton exitBtn = createRoundButton(createExitIcon(), "#8B0000");
            exitBtn.setOnClickListener(v -> {
                try {
                    hideMainOverlay();
                    if (mainCircleContainer != null && windowManager != null) {
                        try {
                            windowManager.removeView(mainCircleContainer);
                            mainCircleContainer = null;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    moveTaskToBack(true);
                    System.exit(0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            leftBar.addView(exitBtn);
            
            ImageButton hideBtn = createRoundButton(createHideIcon(), "#8B0000");
            hideBtn.setOnClickListener(v -> {
                try {
                    hideMainOverlay();
                    if (mainCircleContainer != null) {
                        mainCircleContainer.setVisibility(View.VISIBLE);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            leftBar.addView(hideBtn);

            LinearLayout.LayoutParams leftBarParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
            );
            leftBar.setLayoutParams(leftBarParams);
            mainLayout.addView(leftBar);

            // ============ РАЗДЕЛИТЕЛЬ ============
            View divider = new View(this);
            divider.setBackgroundColor(Color.parseColor("#8B0000"));
            divider.setLayoutParams(new LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT));
            mainLayout.addView(divider);

            // ============ ПУСТОЕ ПРОСТРАНСТВО ============
            View spacer = new View(this);
            spacer.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
            mainLayout.addView(spacer);

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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private ImageButton createRoundButton(Drawable icon, String color) {
        try {
            ImageButton btn = new ImageButton(this);
            btn.setImageDrawable(icon);
            btn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            btn.setPadding(30, 30, 30, 30);
            
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(Color.parseColor(color));
            bg.setStroke(6, Color.parseColor("#FF4444"));
            btn.setBackground(bg);
            
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(140, 140);
            params.setMargins(0, 12, 0, 12);
            btn.setLayoutParams(params);
            
            return btn;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void showOverlaySettings() {
        try {
            FrameLayout settingsContainer = new FrameLayout(this);
            settingsContainer.setBackgroundColor(Color.parseColor("#CC000000"));
            
            LinearLayout settingsLayout = new LinearLayout(this);
            settingsLayout.setOrientation(LinearLayout.VERTICAL);
            settingsLayout.setGravity(Gravity.CENTER);
            settingsLayout.setPadding(40, 40, 40, 40);
            
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(28);
            bg.setColor(Color.parseColor("#1A0A0A"));
            bg.setStroke(2, Color.parseColor("#8B0000"));
            settingsLayout.setBackground(bg);
            
            TextView title = new TextView(this);
            title.setText("⚙ НАСТРОЙКИ ОВЕРЛЕЯ");
            title.setTextColor(Color.parseColor("#CC0000"));
            title.setTextSize(22);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, 0, 0, 24);
            settingsLayout.addView(title);
            
            // Прозрачность
            TextView alphaLabel = new TextView(this);
            alphaLabel.setText("ПРОЗРАЧНОСТЬ");
            alphaLabel.setTextColor(Color.parseColor("#888888"));
            alphaLabel.setTextSize(14);
            alphaLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            alphaLabel.setPadding(0, 0, 0, 8);
            settingsLayout.addView(alphaLabel);
            
            SeekBar alphaSeekBar = new SeekBar(this);
            alphaSeekBar.setMax(100);
            alphaSeekBar.setProgress((int)(overlayAlpha * 100));
            alphaSeekBar.setPadding(0, 0, 0, 16);
            
            TextView alphaValue = new TextView(this);
            alphaValue.setText((int)(overlayAlpha * 100) + "%");
            alphaValue.setTextColor(Color.WHITE);
            alphaValue.setTextSize(16);
            alphaValue.setGravity(Gravity.CENTER);
            alphaValue.setPadding(0, 0, 0, 16);
            
            alphaSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        float alpha = progress / 100f;
                        overlayAlpha = alpha;
                        alphaValue.setText(progress + "%");
                        if (mainOverlay != null && mainOverlay.getChildAt(0) != null) {
                            mainOverlay.getChildAt(0).setAlpha(alpha);
                        }
                    }
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            
            settingsLayout.addView(alphaSeekBar);
            settingsLayout.addView(alphaValue);
            
            Button closeBtn = new Button(this);
            closeBtn.setText("ЗАКРЫТЬ");
            closeBtn.setTextColor(Color.WHITE);
            closeBtn.setTextSize(16);
            closeBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable closeBg = new GradientDrawable();
            closeBg.setCornerRadius(20);
            closeBg.setColor(Color.parseColor("#CC0000"));
            closeBg.setStroke(2, Color.parseColor("#8B0000"));
            closeBtn.setBackground(closeBg);
            closeBtn.setPadding(32, 18, 32, 18);
            closeBtn.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            closeBtn.setOnClickListener(v -> {
                try {
                    windowManager.removeView(settingsContainer);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            settingsLayout.addView(closeBtn);
            
            FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
            );
            containerParams.gravity = Gravity.CENTER;
            settingsContainer.addView(settingsLayout, containerParams);
            
            WindowManager.LayoutParams windowParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            
            if (windowManager != null) {
                windowManager.addView(settingsContainer, windowParams);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void hideMainOverlay() {
        try {
            if (mainOverlay != null && windowManager != null && isMainOverlayVisible) {
                try {
                    windowManager.removeView(mainOverlay);
                    mainOverlay = null;
                    isMainOverlayVisible = false;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== СПИСОК ПЕРСОНАЖЕЙ ====================

    private void showCharacterListFullscreen() {
        try {
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
            bg.setStroke(3, Color.parseColor("#CC0000"));
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
            backBtn.setPadding(16, 16, 16, 16);
            backBtn.setOnClickListener(v -> {
                try {
                    removeCharacterList();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            headerLayout.addView(backBtn);
            
            TextView title = new TextView(this);
            title.setText("✦ ПЕРСОНАЖИ ✦");
            title.setTextColor(Color.parseColor("#CC0000"));
            title.setTextSize(24);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setGravity(Gravity.CENTER);
            title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            headerLayout.addView(title);
            
            ImageButton addBtn = new ImageButton(this);
            addBtn.setImageDrawable(createAddIcon());
            GradientDrawable addBg = new GradientDrawable();
            addBg.setShape(GradientDrawable.OVAL);
            addBg.setColor(Color.parseColor("#CC0000"));
            addBg.setStroke(2, Color.parseColor("#FF4444"));
            addBtn.setBackground(addBg);
            addBtn.setPadding(16, 16, 16, 16);
            addBtn.setOnClickListener(v -> openGalleryForCharacter("Персонаж " + (characters.size() + 1)));
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
                emptyText.setTextSize(18);
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
            closeBtn.setTextSize(16);
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
                try {
                    removeCharacterList();
                } catch (Exception e) {
                    e.printStackTrace();
                }
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private LinearLayout createCharacterCard(CharacterData data, int index) {
        try {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(20, 20, 20, 20);
            
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setCornerRadius(20);
            cardBg.setColor(Color.parseColor("#0A0000"));
            cardBg.setStroke(2, Color.parseColor("#8B0000"));
            card.setBackground(cardBg);
            
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cardParams.setMargins(0, 12, 0, 12);
            card.setLayoutParams(cardParams);
            
            FrameLayout previewContainer = new FrameLayout(this);
            previewContainer.setLayoutParams(new LinearLayout.LayoutParams(100, 100));
            
            GradientDrawable previewBg = new GradientDrawable();
            previewBg.setShape(GradientDrawable.OVAL);
            previewBg.setColor(Color.parseColor("#1A1A1A"));
            previewBg.setStroke(3, Color.parseColor("#CC0000"));
            previewContainer.setBackground(previewBg);
            
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), 
                        Uri.fromFile(new File(data.path)));
                Bitmap processed = removeGreenScreen(bitmap, 40);
                ImageView thumbView = new ImageView(this);
                thumbView.setImageBitmap(processed);
                thumbView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                thumbView.setPadding(4, 4, 4, 4);
                previewContainer.addView(thumbView, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                ));
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            card.addView(previewContainer);
            
            LinearLayout infoLayout = new LinearLayout(this);
            infoLayout.setOrientation(LinearLayout.VERTICAL);
            infoLayout.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            infoLayout.setPadding(20, 0, 20, 0);
            
            String displayName = data.name.trim().isEmpty() ? "Персонаж " + (index + 1) : data.name;
            TextView nameText = new TextView(this);
            nameText.setText(displayName);
            nameText.setTextColor(Color.WHITE);
            nameText.setTextSize(20);
            nameText.setTypeface(null, android.graphics.Typeface.BOLD);
            infoLayout.addView(nameText);
            
            card.addView(infoLayout);
            
            LinearLayout actionLayout = new LinearLayout(this);
            actionLayout.setOrientation(LinearLayout.HORIZONTAL);
            actionLayout.setGravity(Gravity.CENTER);
            
            Button deleteBtn = createActionButton(createDeleteIcon(), "#CC0000");
            deleteBtn.setOnClickListener(v -> {
                try {
                    characters.remove(index);
                    saveCharacters();
                    removeCharacterList();
                    showCharacterListFullscreen();
                    Toast.makeText(this, "Персонаж удалён", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            
            Button screenBtn = createActionButton(createScreenIcon(), "#FF9800");
            screenBtn.setOnClickListener(v -> {
                try {
                    loadCharacterToScreen(data);
                    removeCharacterList();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            
            Button circleBtn = createActionButton(createFloatIcon(), "#2196F3");
            circleBtn.setOnClickListener(v -> {
                try {
                    loadCharacterToFloat(data);
                    removeCharacterList();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(70, 70);
            btnParams.setMargins(6, 0, 6, 0);
            actionLayout.addView(deleteBtn, btnParams);
            actionLayout.addView(screenBtn, btnParams);
            actionLayout.addView(circleBtn, btnParams);
            
            card.addView(actionLayout);
            
            return card;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Button createActionButton(Drawable icon, String color) {
        try {
            Button btn = new Button(this);
            btn.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
            btn.setBackground(null);
            btn.setPadding(8, 8, 8, 8);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(Color.parseColor(color));
            bg.setStroke(3, Color.parseColor("#CC0000"));
            btn.setBackground(bg);
            return btn;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void removeCharacterList() {
        try {
            if (characterListContainer != null && windowManager != null) {
                try {
                    windowManager.removeView(characterListContainer);
                    characterListContainer = null;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            isCharacterListOpen = false;
            // Показываем оверлей снова
            showMainOverlay();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== ДОБАВЛЕНИЕ ПЕРСОНАЖА ====================

    private void openGalleryForCharacter(String name) {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_GALLERY);
    }

    // ==================== ЗАГРУЗКА ПЕРСОНАЖА ====================

    private void loadCharacterToFloat(CharacterData data) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), 
                    Uri.fromFile(new File(data.path)));
            Bitmap processed = removeGreenScreen(bitmap, 40);
            
            if (mainCircleContainer != null && windowManager != null) {
                try {
                    windowManager.removeView(mainCircleContainer);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            
            mainCircleContainer = new FrameLayout(this);
            mainCircleContainer.setBackgroundColor(Color.TRANSPARENT);
            
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(Color.TRANSPARENT);
            d.setStroke(4, Color.WHITE);
            mainCircleContainer.setBackground(d);
            
            ImageButton imageButton = new ImageButton(this);
            imageButton.setImageBitmap(processed);
            imageButton.setBackgroundColor(Color.TRANSPARENT);
            imageButton.setPadding(8, 8, 8, 8);
            imageButton.setScaleType(ImageButton.ScaleType.FIT_CENTER);
            imageButton.setClickable(false);
            imageButton.setFocusable(false);
            
            FrameLayout.LayoutParams imgParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            );
            mainCircleContainer.addView(imageButton, imgParams);
            
            mainCircleParams = new WindowManager.LayoutParams(220, 220, getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
            mainCircleParams.gravity = Gravity.TOP | Gravity.START;
            mainCircleParams.x = 100;
            mainCircleParams.y = 200;
            
            mainCircleContainer.setOnTouchListener(circleTouchListener);
            
            if (windowManager != null) {
                windowManager.addView(mainCircleContainer, mainCircleParams);
                Toast.makeText(this, "Персонаж загружен в круг", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка загрузки", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadCharacterToScreen(CharacterData data) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), 
                    Uri.fromFile(new File(data.path)));
            showCharacterOnScreen(bitmap);
            Toast.makeText(this, "Персонаж отображён", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка загрузки", Toast.LENGTH_SHORT).show();
        }
    }

    private void showCharacterOnScreen(Bitmap bitmap) {
        try {
            if (windowManager == null) return;
            
            removeCharacter();
            
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
            
            if (windowManager != null) {
                windowManager.addView(characterContainer, characterParams);
                isCharacterModeActive = true;
                isCharacterFixed = false;
                Toast.makeText(this, "Персонаж на экране", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== УПРАВЛЕНИЕ ПЕРСОНАЖЕМ ====================

    private void addCharacterControls(FrameLayout container) {
        try {
            ImageButton fixButton = new ImageButton(this);
            fixButton.setImageDrawable(createLockIcon(false));
            GradientDrawable fixBg = new GradientDrawable();
            fixBg.setShape(GradientDrawable.OVAL);
            fixBg.setColor(Color.parseColor("#FF6B00"));
            fixButton.setBackground(fixBg);
            fixButton.setPadding(20, 20, 20, 20);
            
            FrameLayout.LayoutParams fixParams = new FrameLayout.LayoutParams(80, 80, Gravity.TOP | Gravity.END);
            fixParams.setMargins(0, 32, 32, 0);
            fixButton.setLayoutParams(fixParams);
            
            fixButton.setOnClickListener(v -> toggleCharacterFix(fixButton));
            
            ImageButton deleteButton = new ImageButton(this);
            deleteButton.setImageDrawable(createDeleteIcon());
            GradientDrawable deleteBg = new GradientDrawable();
            deleteBg.setShape(GradientDrawable.OVAL);
            deleteBg.setColor(Color.RED);
            deleteButton.setBackground(deleteBg);
            deleteButton.setPadding(20, 20, 20, 20);
            
            FrameLayout.LayoutParams deleteParams = new FrameLayout.LayoutParams(80, 80, Gravity.TOP | Gravity.START);
            deleteParams.setMargins(32, 32, 0, 0);
            deleteButton.setLayoutParams(deleteParams);
            
            deleteButton.setOnClickListener(v -> {
                try {
                    removeCharacter();
                    if (mainCircleContainer != null) {
                        mainCircleContainer.setVisibility(View.VISIBLE);
                    }
                    Toast.makeText(this, "Персонаж удалён", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            
            ImageButton backButton = new ImageButton(this);
            backButton.setImageDrawable(createCloseIcon());
            GradientDrawable backBg = new GradientDrawable();
            backBg.setShape(GradientDrawable.OVAL);
            backBg.setColor(Color.parseColor("#9C27B0"));
            backButton.setBackground(backBg);
            backButton.setPadding(20, 20, 20, 20);
            
            FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(80, 80, Gravity.BOTTOM | Gravity.CENTER);
            backParams.setMargins(0, 0, 0, 32);
            backButton.setLayoutParams(backParams);
            
            backButton.setOnClickListener(v -> {
                try {
                    removeCharacter();
                    if (mainCircleContainer != null) {
                        mainCircleContainer.setVisibility(View.VISIBLE);
                    }
                    Toast.makeText(this, "Возврат в меню", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            
            // Панель управления размером
            LinearLayout controlsLayout = new LinearLayout(this);
            controlsLayout.setOrientation(LinearLayout.VERTICAL);
            controlsLayout.setGravity(Gravity.CENTER);
            controlsLayout.setBackgroundColor(Color.parseColor("#AA000000"));
            controlsLayout.setPadding(20, 16, 20, 16);
            
            // Кнопка переключения режима
            LinearLayout modeLayout = new LinearLayout(this);
            modeLayout.setOrientation(LinearLayout.HORIZONTAL);
            modeLayout.setGravity(Gravity.CENTER);
            modeLayout.setPadding(0, 0, 0, 12);
            
            TextView modeLabel = new TextView(this);
            modeLabel.setText("Режим: ");
            modeLabel.setTextColor(Color.WHITE);
            modeLabel.setTextSize(14);
            
            Button modeToggle = new Button(this);
            modeToggle.setText("ПОЛЗУНКИ");
            modeToggle.setTextColor(Color.WHITE);
            modeToggle.setTextSize(12);
            modeToggle.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable modeBg = new GradientDrawable();
            modeBg.setCornerRadius(12);
            modeBg.setColor(Color.parseColor("#CC0000"));
            modeToggle.setBackground(modeBg);
            modeToggle.setPadding(20, 8, 20, 8);
            
            modeToggle.setOnClickListener(v -> {
                useSeekBarMode = !useSeekBarMode;
                modeToggle.setText(useSeekBarMode ? "ПОЛЗУНКИ" : "ЦИФРЫ");
                Toast.makeText(this, useSeekBarMode ? "Режим ползунков" : "Режим ввода цифр", Toast.LENGTH_SHORT).show();
            });
            
            modeLayout.addView(modeLabel);
            modeLayout.addView(modeToggle);
            controlsLayout.addView(modeLayout);
            
            // X контрол
            LinearLayout xLayout = new LinearLayout(this);
            xLayout.setOrientation(LinearLayout.HORIZONTAL);
            xLayout.setGravity(Gravity.CENTER);
            
            TextView sizeXLabel = new TextView(this);
            sizeXLabel.setText("Ширина:");
            sizeXLabel.setTextColor(Color.WHITE);
            sizeXLabel.setPadding(0, 0, 12, 0);
            
            EditText sizeXInput = new EditText(this);
            sizeXInput.setText("500");
            sizeXInput.setInputType(InputType.TYPE_CLASS_NUMBER);
            sizeXInput.setTextColor(Color.WHITE);
            sizeXInput.setBackgroundColor(Color.parseColor("#333333"));
            sizeXInput.setPadding(12, 8, 12, 8);
            sizeXInput.setWidth(80);
            sizeXInput.setVisibility(useSeekBarMode ? View.GONE : View.VISIBLE);
            
            SeekBar xSeekBar = new SeekBar(this);
            xSeekBar.setMax(1200);
            xSeekBar.setProgress(500);
            xSeekBar.setMinWidth(120);
            xSeekBar.setVisibility(useSeekBarMode ? View.VISIBLE : View.GONE);
            
            xLayout.addView(sizeXLabel);
            xLayout.addView(sizeXInput);
            xLayout.addView(xSeekBar);
            controlsLayout.addView(xLayout);
            
            // Y контрол
            LinearLayout yLayout = new LinearLayout(this);
            yLayout.setOrientation(LinearLayout.HORIZONTAL);
            yLayout.setGravity(Gravity.CENTER);
            
            TextView sizeYLabel = new TextView(this);
            sizeYLabel.setText("Высота:");
            sizeYLabel.setTextColor(Color.WHITE);
            sizeYLabel.setPadding(0, 0, 12, 0);
            
            EditText sizeYInput = new EditText(this);
            sizeYInput.setText("500");
            sizeYInput.setInputType(InputType.TYPE_CLASS_NUMBER);
            sizeYInput.setTextColor(Color.WHITE);
            sizeYInput.setBackgroundColor(Color.parseColor("#333333"));
            sizeYInput.setPadding(12, 8, 12, 8);
            sizeYInput.setWidth(80);
            sizeYInput.setVisibility(useSeekBarMode ? View.GONE : View.VISIBLE);
            
            SeekBar ySeekBar = new SeekBar(this);
            ySeekBar.setMax(1200);
            ySeekBar.setProgress(500);
            ySeekBar.setMinWidth(120);
            ySeekBar.setVisibility(useSeekBarMode ? View.VISIBLE : View.GONE);
            
            yLayout.addView(sizeYLabel);
            yLayout.addView(sizeYInput);
            yLayout.addView(ySeekBar);
            controlsLayout.addView(yLayout);
            
            // Настройка слушателей
            xSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && characterParams != null) {
                        int val = Math.max(50, progress);
                        characterParams.width = val;
                        sizeXInput.setText(String.valueOf(val));
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
                        val = Math.max(50, Math.min(1200, val));
                        characterParams.width = val;
                        xSeekBar.setProgress(val);
                        updateCharacterSize();
                    } catch (Exception e) { e.printStackTrace(); }
                }
            });
            
            ySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && characterParams != null) {
                        int val = Math.max(50, progress);
                        characterParams.height = val;
                        sizeYInput.setText(String.valueOf(val));
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
                        val = Math.max(50, Math.min(1200, val));
                        characterParams.height = val;
                        ySeekBar.setProgress(val);
                        updateCharacterSize();
                    } catch (Exception e) { e.printStackTrace(); }
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
                    if (isCharacterFixed) return false;
                    return handleCharacterTouch(event);
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private boolean handleCharacterTouch(MotionEvent event) {
        try {
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
                        if (windowManager != null) {
                            windowManager.updateViewLayout(characterContainer, characterParams);
                        }
                    }
                    return true;
                    
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    initialPinchDistance = 0;
                    return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void toggleCharacterFix(ImageButton fixButton) {
        try {
            isCharacterFixed = !isCharacterFixed;
            
            if (isCharacterFixed) {
                Toast.makeText(this, "🔒 Персонаж закреплён", Toast.LENGTH_SHORT).show();
                fixButton.setImageDrawable(createLockIcon(true));
                
                if (characterParams != null && windowManager != null) {
                    characterParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    windowManager.updateViewLayout(characterContainer, characterParams);
                }
            } else {
                Toast.makeText(this, "🔓 Персонаж разблокирован", Toast.LENGTH_SHORT).show();
                fixButton.setImageDrawable(createLockIcon(false));
                
                if (characterParams != null && windowManager != null) {
                    characterParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    windowManager.updateViewLayout(characterContainer, characterParams);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void updateCharacterSize() {
        try {
            if (windowManager != null && characterContainer != null && characterParams != null) {
                windowManager.updateViewLayout(characterContainer, characterParams);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void removeCharacter() {
        try {
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
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== CHROMAKEY ====================

    private Bitmap removeGreenScreen(Bitmap source, int tolerance) {
        try {
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
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ==================== ИКОНКИ ====================

    private Drawable createLockIcon(boolean locked) {
        try {
            Bitmap b = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(7);
            
            float cx = 40, cy = 40;
            c.drawArc(cx - 24, cy - 34, cx + 24, cy - 6, 0, 180, false, p);
            c.drawRect(cx - 20, cy - 10, cx + 20, cy + 24, p);
            p.setStyle(Paint.Style.FILL);
            p.setStrokeWidth(0);
            c.drawCircle(cx, cy + 8, 6, p);
            
            if (locked) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(8);
                p.setColor(Color.YELLOW);
                c.drawLine(cx - 30, cy - 20, cx + 30, cy + 30, p);
                c.drawLine(cx + 30, cy - 20, cx - 30, cy + 30, p);
            }
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    private int getOverlayFlag() {
        try {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                    WindowManager.LayoutParams.TYPE_PHONE;
        } catch (Exception e) {
            e.printStackTrace();
            return WindowManager.LayoutParams.TYPE_PHONE;
        }
    }

    private float getDistance(MotionEvent event) {
        try {
            float x = event.getX(0) - event.getX(1);
            float y = event.getY(0) - event.getY(1);
            return (float) Math.sqrt(x * x + y * y);
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    // ==================== ЖИЗНЕННЫЙ ЦИКЛ ====================

    @Override
    public void onResume() {
        super.onResume();
        try {
            // Показываем оверлей при возврате в приложение
            if (!isMainOverlayVisible) {
                showMainOverlay();
            }
            if (mainCircleContainer != null) {
                mainCircleContainer.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            // Скрываем оверлей когда выходим из приложения
            if (isMainOverlayVisible) {
                hideMainOverlay();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
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
            if (characterListContainer != null && windowManager != null) {
                try {
                    windowManager.removeView(characterListContainer);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        try {
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        try {
            if (requestCode == REQUEST_GALLERY && resultCode == RESULT_OK && data != null) {
                Uri imageUri = data.getData();
                try {
                    Bitmap original = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                    String path = saveImageToStorage(original);
                    if (path != null) {
                        String name = "Персонаж " + (characters.size() + 1);
                        characters.add(new CharacterData(name, path));
                        saveCharacters();
                        Toast.makeText(this, "Персонаж сохранён", Toast.LENGTH_SHORT).show();
                    }
                    if (isCharacterListOpen) {
                        removeCharacterList();
                        showCharacterListFullscreen();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Ошибка загрузки", Toast.LENGTH_SHORT).show();
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
              }
