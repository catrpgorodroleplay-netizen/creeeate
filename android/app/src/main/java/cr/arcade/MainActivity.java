package com.cr.arcade;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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
import java.io.OutputStream;
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
    private static final int REQUEST_NOTIFICATION = 105;

    private static final String URL_HOME = "https://wyikhedfghhopyewfvjkurrhncswehipkhf.vercel.app/";
    private static final String URL_SETTINGS = "https://whuokhgrdcbnmkloplureecvjiqoendu.vercel.app/";

    private WindowManager windowManager;
    public static FrameLayout mainCircleContainer;
    private WindowManager.LayoutParams mainCircleParams;
    
    private float startX, startY;
    private int initialX, initialY;
    private boolean isDragging = false;
    
    private FrameLayout mainOverlay;
    private WebView webView;
    private WindowManager.LayoutParams mainOverlayParams;
    private boolean isMainOverlayVisible = false;
    private boolean isAppInForeground = true;

    // Система персонажей на экране
    private ArrayList<ScreenCharacter> screenCharacters = new ArrayList<>();
    private SharedPreferences prefs;
    private int characterCounter = 0;
    
    // Настройки оверлея
    private int overlayAlpha = 255;
    private int overlaySize = 136;
    
    // Режимы
    private boolean isWebViewMode = true;
    private FrameLayout contentContainer;
    private LinearLayout charactersGridLayout;
    
    // Сохранение сцены
    private boolean autoSaveEnabled = false;
    
    // Редактирование персонажа
    private FrameLayout editContainer;
    private ImageView editImageView;
    private WindowManager.LayoutParams editParams;
    private Bitmap editBitmap;
    private int selectedCharacterIndex = -1;
    private float rotationAngle = 0;
    private float scaleX = 1.0f;
    private float scaleY = 1.0f;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            prefs = getSharedPreferences("characters", MODE_PRIVATE);
            characterCounter = prefs.getInt("character_counter", 0);
            overlayAlpha = prefs.getInt("overlay_alpha", 255);
            overlaySize = prefs.getInt("overlay_size", 136);
            autoSaveEnabled = prefs.getBoolean("auto_save_enabled", false);
            loadCharacters();
            loadScene();
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
                }
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

        if (windowManager == null) {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        }
    }

    // ==================== КЛАССЫ ДАННЫХ ====================
    
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
    
    private static class ScreenCharacter {
        String characterPath;
        int x, y;
        int width, height;
        float rotation;
        float scaleX, scaleY;
        FrameLayout container;
        WindowManager.LayoutParams params;
        
        ScreenCharacter(String path, int x, int y, int width, int height) {
            this.characterPath = path;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.rotation = 0;
            this.scaleX = 1.0f;
            this.scaleY = 1.0f;
        }
        
        ScreenCharacter(JSONObject json) throws Exception {
            this.characterPath = json.getString("path");
            this.x = json.getInt("x");
            this.y = json.getInt("y");
            this.width = json.getInt("width");
            this.height = json.getInt("height");
            this.rotation = (float) json.optDouble("rotation", 0);
            this.scaleX = (float) json.optDouble("scaleX", 1.0);
            this.scaleY = (float) json.optDouble("scaleY", 1.0);
        }
        
        JSONObject toJSON() throws Exception {
            JSONObject json = new JSONObject();
            json.put("path", characterPath);
            json.put("x", x);
            json.put("y", y);
            json.put("width", width);
            json.put("height", height);
            json.put("rotation", rotation);
            json.put("scaleX", scaleX);
            json.put("scaleY", scaleY);
            return json;
        }
    }

    private ArrayList<CharacterData> characters = new ArrayList<>();

    // ==================== СОХРАНЕНИЕ ====================
    
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
            prefs.edit()
                .putString("characters_list", array.toString())
                .putInt("character_counter", characterCounter)
                .putInt("overlay_alpha", overlayAlpha)
                .putInt("overlay_size", overlaySize)
                .putBoolean("auto_save_enabled", autoSaveEnabled)
                .apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void saveScene() {
        try {
            JSONArray array = new JSONArray();
            for (ScreenCharacter sc : screenCharacters) {
                array.put(sc.toJSON());
            }
            prefs.edit().putString("scene_data", array.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void loadScene() {
        try {
            String json = prefs.getString("scene_data", "");
            if (!json.isEmpty()) {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    ScreenCharacter sc = new ScreenCharacter(array.getJSONObject(i));
                    screenCharacters.add(sc);
                    if (isAppInForeground) {
                        showCharacterOnScreen(sc);
                    }
                }
            }
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
                            new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION);
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
            if (windowManager == null) {
                windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            }
            
            if (windowManager == null) return;
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                return;
            }
            
            if (mainCircleContainer != null) return;
            
            int flag = getOverlayFlag();
            
            mainCircleContainer = new FrameLayout(this);
            mainCircleContainer.setBackgroundColor(Color.TRANSPARENT);
            
            ImageButton iconButton = new ImageButton(this);
            iconButton.setImageBitmap(createGamepadBitmap());
            iconButton.setBackgroundColor(Color.TRANSPARENT);
            iconButton.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
            iconButton.setPadding(25, 25, 25, 25);
            iconButton.setClickable(false);
            iconButton.setFocusable(false);
            
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(Color.parseColor("#CC0000"));
            d.setStroke(6, Color.parseColor("#FF4444"));
            mainCircleContainer.setBackground(d);
            mainCircleContainer.setAlpha(overlayAlpha / 255f);
            
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            );
            mainCircleContainer.addView(iconButton, iconParams);
            
            mainCircleParams = new WindowManager.LayoutParams(
                    overlaySize, overlaySize,
                    flag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            mainCircleParams.gravity = Gravity.TOP | Gravity.START;
            
            int savedX = prefs.getInt("overlay_x", 100);
            int savedY = prefs.getInt("overlay_y", 200);
            mainCircleParams.x = savedX;
            mainCircleParams.y = savedY;

            mainCircleContainer.setOnTouchListener(new View.OnTouchListener() {
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
                                        prefs.edit()
                                            .putInt("overlay_x", mainCircleParams.x)
                                            .putInt("overlay_y", mainCircleParams.y)
                                            .apply();
                                    }
                                }
                                return true;
                                
                            case MotionEvent.ACTION_UP:
                                if (!isDragging) {
                                    hideMainOverlay();
                                    showMainOverlay();
                                }
                                return true;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return false;
                }
            });

            if (windowManager != null) {
                windowManager.addView(mainCircleContainer, mainCircleParams);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void removeMainCircle() {
        try {
            if (mainCircleContainer != null && windowManager != null) {
                windowManager.removeView(mainCircleContainer);
                mainCircleContainer = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateOverlayAppearance() {
        try {
            if (mainCircleContainer != null && windowManager != null) {
                GradientDrawable d = new GradientDrawable();
                d.setShape(GradientDrawable.OVAL);
                d.setColor(Color.parseColor("#CC0000"));
                d.setStroke(6, Color.parseColor("#FF4444"));
                mainCircleContainer.setBackground(d);
                mainCircleContainer.setAlpha(overlayAlpha / 255f);
                mainCircleParams.width = overlaySize;
                mainCircleParams.height = overlaySize;
                windowManager.updateViewLayout(mainCircleContainer, mainCircleParams);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Bitmap createGamepadBitmap() {
        try {
            int size = 120;
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint();
            paint.setAntiAlias(true);
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(8);

            float cx = size / 2f, cy = size / 2f;
            canvas.drawRoundRect(cx - 40, cy - 28, cx + 40, cy + 28, 22, 22, paint);
            canvas.drawCircle(cx - 32, cy, 16, paint);
            canvas.drawCircle(cx + 32, cy, 16, paint);
            paint.setStrokeWidth(6);
            canvas.drawLine(cx - 24, cy - 10, cx - 24, cy + 10, paint);
            canvas.drawLine(cx - 30, cy, cx - 18, cy, paint);
            canvas.drawCircle(cx + 22, cy - 8, 7, paint);
            canvas.drawCircle(cx + 22, cy + 8, 7, paint);
            canvas.drawCircle(cx + 34, cy, 7, paint);
            canvas.drawCircle(cx + 10, cy, 7, paint);
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ==================== ИКОНКИ ====================

    private Drawable createHomeIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(8);
            
            float cx = 60, cy = 60;
            c.drawLine(cx - 40, cy + 10, cx, cy - 30, p);
            c.drawLine(cx + 40, cy + 10, cx, cy - 30, p);
            c.drawLine(cx - 40, cy + 10, cx - 40, cy + 40, p);
            c.drawLine(cx + 40, cy + 10, cx + 40, cy + 40, p);
            c.drawLine(cx - 40, cy + 40, cx + 40, cy + 40, p);
            c.drawLine(cx - 15, cy + 40, cx - 15, cy + 15, p);
            c.drawLine(cx + 15, cy + 40, cx + 15, cy + 15, p);
            c.drawLine(cx - 15, cy + 15, cx + 15, cy + 15, p);
            c.drawLine(cx + 8, cy - 24, cx + 8, cy - 40, p);
            c.drawLine(cx + 8, cy - 40, cx + 24, cy - 40, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Drawable createCloseIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(8);
            
            float cx = 40, cy = 40;
            c.drawLine(cx - 25, cy - 25, cx + 25, cy + 25, p);
            c.drawLine(cx + 25, cy - 25, cx - 25, cy + 25, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Drawable createCharacterIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(7);
            
            float cx = 60, cy = 60;
            c.drawCircle(cx, cy - 15, 18, p);
            c.drawLine(cx, cy + 5, cx, cy + 35, p);
            c.drawLine(cx, cy + 12, cx - 25, cy + 0, p);
            c.drawLine(cx, cy + 12, cx + 25, cy + 0, p);
            c.drawLine(cx, cy + 35, cx - 20, cy + 50, p);
            c.drawLine(cx, cy + 35, cx + 20, cy + 50, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Drawable createSettingsIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(7);
            
            float cx = 60, cy = 60;
            c.drawCircle(cx, cy, 20, p);
            c.drawLine(cx - 12, cy - 30, cx - 12, cy - 20, p);
            c.drawLine(cx + 12, cy - 30, cx + 12, cy - 20, p);
            c.drawLine(cx - 12, cy + 20, cx - 12, cy + 30, p);
            c.drawLine(cx + 12, cy + 20, cx + 12, cy + 30, p);
            c.drawLine(cx - 30, cy - 12, cx - 20, cy - 12, p);
            c.drawLine(cx - 30, cy + 12, cx - 20, cy + 12, p);
            c.drawLine(cx + 20, cy - 12, cx + 30, cy - 12, p);
            c.drawLine(cx + 20, cy + 12, cx + 30, cy + 12, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Drawable createExitIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(8);
            
            float cx = 60, cy = 60;
            c.drawRect(cx - 25, cy - 30, cx + 25, cy + 30, p);
            c.drawCircle(cx + 10, cy, 6, p);
            c.drawLine(cx + 25, cy - 12, cx + 42, cy - 12, p);
            c.drawLine(cx + 25, cy + 12, cx + 42, cy + 12, p);
            c.drawLine(cx + 42, cy - 12, cx + 42, cy + 12, p);
            c.drawLine(cx - 25, cy, cx - 10, cy, p);
            c.drawLine(cx - 16, cy - 8, cx - 10, cy, p);
            c.drawLine(cx - 16, cy + 8, cx - 10, cy, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Drawable createHideIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(7);
            
            float cx = 60, cy = 60;
            c.drawOval(cx - 30, cy - 20, cx + 30, cy + 20, p);
            c.drawCircle(cx, cy, 10, p);
            p.setStrokeWidth(8);
            c.drawLine(cx - 25, cy - 15, cx + 25, cy + 15, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Drawable createDeleteIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(6);
            
            float cx = 30, cy = 30;
            c.drawLine(cx - 18, cy - 15, cx + 18, cy - 15, p);
            c.drawLine(cx - 10, cy - 22, cx + 10, cy - 22, p);
            c.drawLine(cx - 18, cy - 15, cx - 18, cy + 15, p);
            c.drawLine(cx + 18, cy - 15, cx + 18, cy + 15, p);
            c.drawArc(cx - 14, cy - 26, cx + 14, cy - 10, 0, 180, false, p);
            p.setStrokeWidth(4);
            c.drawLine(cx - 8, cy, cx + 8, cy, p);
            c.drawLine(cx, cy - 8, cx, cy + 8, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Drawable createScreenIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(6);
            
            float cx = 30, cy = 30;
            c.drawRect(cx - 20, cy - 15, cx + 20, cy + 12, p);
            c.drawLine(cx - 20, cy + 18, cx + 20, cy + 18, p);
            c.drawLine(cx, cy + 18, cx, cy + 25, p);
            c.drawLine(cx - 8, cy + 25, cx + 8, cy + 25, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Drawable createFloatIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(7);
            
            float cx = 30, cy = 30;
            c.drawOval(cx - 15, cy - 15, cx + 15, cy + 15, p);
            c.drawCircle(cx + 6, cy - 6, 4, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Drawable createEditIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(6);
            
            float cx = 30, cy = 30;
            c.drawLine(cx - 18, cy + 18, cx + 18, cy - 18, p);
            c.drawLine(cx - 18, cy + 18, cx - 10, cy + 22, p);
            c.drawLine(cx - 10, cy + 22, cx - 14, cy + 18, p);
            c.drawLine(cx + 18, cy - 18, cx + 22, cy - 10, p);
            c.drawLine(cx + 22, cy - 10, cx + 18, cy - 14, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Drawable createSaveIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(6);
            
            float cx = 30, cy = 30;
            c.drawRect(cx - 20, cy - 15, cx + 20, cy + 20, p);
            c.drawLine(cx - 12, cy - 5, cx, cy + 10, p);
            c.drawLine(cx + 12, cy - 5, cx, cy + 10, p);
            c.drawLine(cx - 8, cy + 20, cx + 8, cy + 20, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Drawable createLoadIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(6);
            
            float cx = 30, cy = 30;
            c.drawArc(cx - 20, cy - 15, cx + 20, cy + 15, 45, 270, false, p);
            c.drawLine(cx + 10, cy - 8, cx + 22, cy - 8, p);
            c.drawLine(cx + 22, cy - 8, cx + 22, cy - 20, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Drawable createAutoSaveIcon(boolean enabled) {
        try {
            Bitmap b = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(enabled ? Color.parseColor("#00FF00") : Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(6);
            
            float cx = 30, cy = 30;
            c.drawArc(cx - 18, cy - 12, cx + 18, cy + 12, 0, 360, false, p);
            p.setStrokeWidth(4);
            c.drawLine(cx - 6, cy, cx + 6, cy, p);
            c.drawLine(cx, cy - 6, cx, cy + 6, p);
            
            if (enabled) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.parseColor("#00FF00"));
                c.drawCircle(cx + 20, cy - 18, 8, p);
            }
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ==================== ОВЕРЛЕЙ С МЕНЮ ====================

    private void showMainOverlay() {
        try {
            if (isMainOverlayVisible || windowManager == null) return;
            
            removeMainCircle();
            
            int flag = getOverlayFlag();

            mainOverlay = new FrameLayout(this);
            mainOverlay.setBackgroundColor(Color.parseColor("#D0000000"));
            mainOverlay.setPadding(20, 20, 20, 20);

            FrameLayout innerContainer = new FrameLayout(this);
            GradientDrawable innerBg = new GradientDrawable();
            innerBg.setShape(GradientDrawable.RECTANGLE);
            innerBg.setCornerRadius(50);
            innerBg.setColor(Color.parseColor("#0D0D0D"));
            innerBg.setStroke(4, Color.parseColor("#CC0000"));
            innerContainer.setBackground(innerBg);
            innerContainer.setPadding(20, 20, 20, 20);

            LinearLayout mainLayout = new LinearLayout(this);
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
            ));

            LinearLayout topBar = new LinearLayout(this);
            topBar.setOrientation(LinearLayout.HORIZONTAL);
            topBar.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            topBar.setPadding(0, 8, 0, 16);
            
            ImageButton homeBtn = createLargeRoundButton(createHomeIcon(), "#1A1A1A", 4, "#CC0000");
            homeBtn.setOnClickListener(v -> {
                isWebViewMode = true;
                if (webView != null) webView.loadUrl(URL_HOME);
                updateContent();
            });
            topBar.addView(homeBtn);
            
            ImageButton charsBtn = createLargeRoundButton(createCharacterIcon(), "#1A1A1A", 4, "#CC0000");
            charsBtn.setOnClickListener(v -> {
                isWebViewMode = false;
                updateContent();
            });
            topBar.addView(charsBtn);
            
            ImageButton settingsBtn = createLargeRoundButton(createSettingsIcon(), "#1A1A1A", 4, "#CC0000");
            settingsBtn.setOnClickListener(v -> {
                isWebViewMode = true;
                if (webView != null) webView.loadUrl(URL_SETTINGS);
                updateContent();
            });
            topBar.addView(settingsBtn);
            
            ImageButton exitBtn = createLargeRoundButton(createExitIcon(), "#1A0000", 4, "#8B0000");
            exitBtn.setOnClickListener(v -> showDeleteOverlayConfirmation());
            topBar.addView(exitBtn);
            
            ImageButton hideBtn = createLargeRoundButton(createHideIcon(), "#1A0000", 4, "#8B0000");
            hideBtn.setOnClickListener(v -> {
                hideMainOverlay();
                createMainCircle();
            });
            topBar.addView(hideBtn);

            mainLayout.addView(topBar);

            View divider = new View(this);
            divider.setBackgroundColor(Color.parseColor("#CC0000"));
            divider.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 3
            ));
            mainLayout.addView(divider);

            contentContainer = new FrameLayout(this);
            contentContainer.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0, 1
            ));
            contentContainer.setBackgroundColor(Color.parseColor("#0A0A0A"));
            contentContainer.setPadding(0, 16, 0, 0);
            
            createWebView();
            createCharactersGrid();
            
            isWebViewMode = true;
            updateContent();
            
            mainLayout.addView(contentContainer);
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

            windowManager.addView(mainOverlay, mainOverlayParams);
            isMainOverlayVisible = true;
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showDeleteOverlayConfirmation() {
        try {
            FrameLayout confirmOverlay = new FrameLayout(this);
            confirmOverlay.setBackgroundColor(Color.parseColor("#E6000000"));
            
            LinearLayout confirmLayout = new LinearLayout(this);
            confirmLayout.setOrientation(LinearLayout.VERTICAL);
            confirmLayout.setGravity(Gravity.CENTER);
            confirmLayout.setPadding(40, 40, 40, 40);
            
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(40);
            bg.setColor(Color.parseColor("#0D0D0D"));
            bg.setStroke(3, Color.parseColor("#CC0000"));
            confirmLayout.setBackground(bg);
            
            TextView warnIcon = new TextView(this);
            warnIcon.setText("⚠");
            warnIcon.setTextSize(50);
            warnIcon.setGravity(Gravity.CENTER);
            warnIcon.setPadding(0, 0, 0, 20);
            confirmLayout.addView(warnIcon);
            
            TextView title = new TextView(this);
            title.setText("УДАЛЕНИЕ ОВЕРЛЕЯ");
            title.setTextColor(Color.parseColor("#CC0000"));
            title.setTextSize(24);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, 0, 0, 12);
            confirmLayout.addView(title);
            
            TextView message = new TextView(this);
            message.setText("Вы уверены, что хотите удалить\nоверлей CR Arcade?\n\nЭто действие нельзя отменить.");
            message.setTextColor(Color.parseColor("#AAAAAA"));
            message.setTextSize(16);
            message.setGravity(Gravity.CENTER);
            message.setPadding(0, 0, 0, 30);
            confirmLayout.addView(message);
            
            LinearLayout buttonLayout = new LinearLayout(this);
            buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
            buttonLayout.setGravity(Gravity.CENTER);
            
            Button confirmBtn = new Button(this);
            confirmBtn.setText("УДАЛИТЬ");
            confirmBtn.setTextColor(Color.WHITE);
            confirmBtn.setTextSize(16);
            confirmBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable confirmBg = new GradientDrawable();
            confirmBg.setCornerRadius(25);
            confirmBg.setColor(Color.parseColor("#CC0000"));
            confirmBtn.setBackground(confirmBg);
            confirmBtn.setPadding(30, 16, 30, 16);
            confirmBtn.setOnClickListener(v -> {
                try {
                    if (windowManager != null) {
                        windowManager.removeView(confirmOverlay);
                    }
                    hideMainOverlay();
                    clearAllScreenCharacters();
                    removeMainCircle();
                    finishAffinity();
                    System.exit(0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            
            Button cancelBtn = new Button(this);
            cancelBtn.setText("ОТМЕНА");
            cancelBtn.setTextColor(Color.WHITE);
            cancelBtn.setTextSize(16);
            cancelBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable cancelBg = new GradientDrawable();
            cancelBg.setCornerRadius(25);
            cancelBg.setColor(Color.parseColor("#2A2A2A"));
            cancelBg.setStroke(2, Color.parseColor("#8B0000"));
            cancelBtn.setBackground(cancelBg);
            cancelBtn.setPadding(30, 16, 30, 16);
            cancelBtn.setOnClickListener(v -> {
                if (windowManager != null) windowManager.removeView(confirmOverlay);
            });
            
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            btnParams.setMargins(8, 0, 8, 0);
            buttonLayout.addView(confirmBtn, btnParams);
            buttonLayout.addView(cancelBtn, btnParams);
            
            confirmLayout.addView(buttonLayout);
            
            FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            containerParams.gravity = Gravity.CENTER;
            containerParams.setMargins(40, 0, 40, 0);
            confirmOverlay.addView(confirmLayout, containerParams);
            
            WindowManager.LayoutParams windowParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT);
            
            if (windowManager != null) windowManager.addView(confirmOverlay, windowParams);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private ImageButton createLargeRoundButton(Drawable icon, String bgColor, int strokeWidth, String strokeColor) {
        try {
            ImageButton btn = new ImageButton(this);
            btn.setImageDrawable(icon);
            btn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            btn.setPadding(30, 30, 30, 30);
            
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(Color.parseColor(bgColor));
            bg.setStroke(strokeWidth, Color.parseColor(strokeColor));
            btn.setBackground(bg);
            
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(130, 130);
            params.setMargins(12, 0, 12, 0);
            btn.setLayoutParams(params);
            
            return btn;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void createWebView() {
        try {
            if (webView != null) return;
            
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
            webView.setWebViewClient(new WebViewClient());

            webView.loadUrl(URL_HOME);

            webView.setBackgroundColor(Color.parseColor("#0A0A0A"));
            webView.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createCharactersGrid() {
        try {
            charactersGridLayout = new LinearLayout(this);
            charactersGridLayout.setOrientation(LinearLayout.VERTICAL);
            charactersGridLayout.setPadding(8, 8, 8, 8);
            charactersGridLayout.setGravity(Gravity.CENTER);
            charactersGridLayout.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            updateCharactersGrid();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateCharactersGrid() {
        try {
            if (charactersGridLayout == null) return;
            charactersGridLayout.removeAllViews();
            
            LinearLayout headerRow = new LinearLayout(this);
            headerRow.setOrientation(LinearLayout.HORIZONTAL);
            headerRow.setGravity(Gravity.CENTER_VERTICAL);
            headerRow.setPadding(0, 0, 0, 16);
            
            TextView headerTitle = new TextView(this);
            headerTitle.setText("МОИ ПЕРСОНАЖИ");
            headerTitle.setTextColor(Color.parseColor("#CC0000"));
            headerTitle.setTextSize(20);
            headerTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            headerTitle.setLayoutParams(titleParams);
            headerRow.addView(headerTitle);
            
            ImageButton autoSaveBtn = new ImageButton(this);
            autoSaveBtn.setImageDrawable(createAutoSaveIcon(autoSaveEnabled));
            GradientDrawable autoBg = new GradientDrawable();
            autoBg.setShape(GradientDrawable.OVAL);
            autoBg.setColor(autoSaveEnabled ? Color.parseColor("#004D00") : Color.parseColor("#1A1A1A"));
            autoBg.setStroke(2, autoSaveEnabled ? Color.parseColor("#00FF00") : Color.parseColor("#555555"));
            autoSaveBtn.setBackground(autoBg);
            autoSaveBtn.setPadding(8, 8, 8, 8);
            LinearLayout.LayoutParams autoParams = new LinearLayout.LayoutParams(60, 60);
            autoParams.setMargins(0, 0, 8, 0);
            autoSaveBtn.setLayoutParams(autoParams);
            autoSaveBtn.setOnClickListener(v -> {
                autoSaveEnabled = !autoSaveEnabled;
                prefs.edit().putBoolean("auto_save_enabled", autoSaveEnabled).apply();
                autoSaveBtn.setImageDrawable(createAutoSaveIcon(autoSaveEnabled));
                GradientDrawable newBg = new GradientDrawable();
                newBg.setShape(GradientDrawable.OVAL);
                newBg.setColor(autoSaveEnabled ? Color.parseColor("#004D00") : Color.parseColor("#1A1A1A"));
                newBg.setStroke(2, autoSaveEnabled ? Color.parseColor("#00FF00") : Color.parseColor("#555555"));
                autoSaveBtn.setBackground(newBg);
                Toast.makeText(this, autoSaveEnabled ? "Автосохранение включено" : "Автосохранение выключено", Toast.LENGTH_SHORT).show();
            });
            headerRow.addView(autoSaveBtn);
            
            ImageButton saveSceneBtn = new ImageButton(this);
            saveSceneBtn.setImageDrawable(createSaveIcon());
            GradientDrawable saveBg = new GradientDrawable();
            saveBg.setShape(GradientDrawable.OVAL);
            saveBg.setColor(Color.parseColor("#004D00"));
            saveBg.setStroke(2, Color.parseColor("#00FF00"));
            saveSceneBtn.setBackground(saveBg);
            saveSceneBtn.setPadding(8, 8, 8, 8);
            LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(60, 60);
            saveParams.setMargins(0, 0, 8, 0);
            saveSceneBtn.setLayoutParams(saveParams);
            saveSceneBtn.setOnClickListener(v -> {
                saveScene();
                Toast.makeText(this, "Сцена сохранена!", Toast.LENGTH_SHORT).show();
            });
            headerRow.addView(saveSceneBtn);
            
            ImageButton loadSceneBtn = new ImageButton(this);
            loadSceneBtn.setImageDrawable(createLoadIcon());
            GradientDrawable loadBg = new GradientDrawable();
            loadBg.setShape(GradientDrawable.OVAL);
            loadBg.setColor(Color.parseColor("#1A1A1A"));
            loadBg.setStroke(2, Color.parseColor("#FF8C00"));
            loadSceneBtn.setBackground(loadBg);
            loadSceneBtn.setPadding(8, 8, 8, 8);
            LinearLayout.LayoutParams loadParams = new LinearLayout.LayoutParams(60, 60);
            loadParams.setMargins(0, 0, 8, 0);
            loadSceneBtn.setLayoutParams(loadParams);
            loadSceneBtn.setOnClickListener(v -> {
                showLoadSceneConfirmation();
            });
            headerRow.addView(loadSceneBtn);
            
            Button addBtn = new Button(this);
            addBtn.setText("+");
            addBtn.setTextColor(Color.WHITE);
            addBtn.setTextSize(24);
            addBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable addBg2 = new GradientDrawable();
            addBg2.setShape(GradientDrawable.OVAL);
            addBg2.setColor(Color.parseColor("#CC0000"));
            addBtn.setBackground(addBg2);
            addBtn.setPadding(8, 4, 8, 4);
            LinearLayout.LayoutParams addBtnParams = new LinearLayout.LayoutParams(60, 60);
            addBtn.setLayoutParams(addBtnParams);
            addBtn.setOnClickListener(v -> showAddCharacterDialog());
            headerRow.addView(addBtn);
            charactersGridLayout.addView(headerRow);
            
            if (characters.isEmpty()) {
                TextView emptyText = new TextView(this);
                emptyText.setText("Нет сохранённых персонажей\n\nНажмите + чтобы добавить");
                emptyText.setTextColor(Color.parseColor("#555555"));
                emptyText.setTextSize(16);
                emptyText.setTypeface(null, android.graphics.Typeface.BOLD);
                emptyText.setGravity(Gravity.CENTER);
                emptyText.setPadding(0, 80, 0, 80);
                charactersGridLayout.addView(emptyText);
                return;
            }
            
            ScrollView scrollView = new ScrollView(this);
            LinearLayout gridContainer = new LinearLayout(this);
            gridContainer.setOrientation(LinearLayout.VERTICAL);
            
            int itemsPerRow = 2;
            int totalItems = characters.size();
            int rows = (int) Math.ceil((double) totalItems / itemsPerRow);
            
            for (int r = 0; r < rows; r++) {
                LinearLayout rowLayout = new LinearLayout(this);
                rowLayout.setOrientation(LinearLayout.HORIZONTAL);
                rowLayout.setGravity(Gravity.CENTER);
                rowLayout.setPadding(0, 8, 0, 8);
                
                for (int i = r * itemsPerRow; i < Math.min((r + 1) * itemsPerRow, totalItems); i++) {
                    CharacterData data = characters.get(i);
                    LinearLayout card = createLargeCharacterCard(data, i);
                    LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                    cardParams.setMargins(8, 0, 8, 0);
                    card.setLayoutParams(cardParams);
                    rowLayout.addView(card);
                }
                
                gridContainer.addView(rowLayout);
            }
            
            scrollView.addView(gridContainer);
            charactersGridLayout.addView(scrollView);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private LinearLayout createLargeCharacterCard(CharacterData data, int index) {
        try {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(16, 16, 16, 16);
            
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setCornerRadius(24);
            cardBg.setColor(Color.parseColor("#1A1A1A"));
            cardBg.setStroke(2, Color.parseColor("#CC0000"));
            card.setBackground(cardBg);
            
            FrameLayout previewContainer = new FrameLayout(this);
            previewContainer.setLayoutParams(new LinearLayout.LayoutParams(180, 180));
            
            GradientDrawable previewBg = new GradientDrawable();
            previewBg.setShape(GradientDrawable.OVAL);
            previewBg.setColor(Color.parseColor("#2A2A2A"));
            previewBg.setStroke(3, Color.parseColor("#CC0000"));
            previewContainer.setBackground(previewBg);
            
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), Uri.fromFile(new File(data.path)));
                Bitmap processed = removeGreenScreen(bitmap, 40);
                ImageView thumbView = new ImageView(this);
                thumbView.setImageBitmap(processed);
                thumbView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                previewContainer.addView(thumbView, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            } catch (Exception e) {
                TextView errorText = new TextView(this);
                errorText.setText("?");
                errorText.setTextColor(Color.parseColor("#555555"));
                errorText.setTextSize(40);
                errorText.setGravity(Gravity.CENTER);
                previewContainer.addView(errorText);
            }
            
            card.addView(previewContainer);
            
            String displayName = data.name;
            if (displayName.length() > 18) displayName = displayName.substring(0, 16) + "...";
            
            TextView nameText = new TextView(this);
            nameText.setText(displayName);
            nameText.setTextColor(Color.WHITE);
            nameText.setTextSize(16);
            nameText.setTypeface(null, android.graphics.Typeface.BOLD);
            nameText.setPadding(0, 12, 0, 12);
            nameText.setGravity(Gravity.CENTER);
            card.addView(nameText);
            
            LinearLayout actionLayout = new LinearLayout(this);
            actionLayout.setOrientation(LinearLayout.HORIZONTAL);
            actionLayout.setGravity(Gravity.CENTER);
            actionLayout.setPadding(0, 4, 0, 0);
            
            ImageButton deleteBtn = createSmallRoundButton(createDeleteIcon(), "#CC0000");
            deleteBtn.setOnClickListener(v -> {
                characters.remove(index);
                saveCharacters();
                updateCharactersGrid();
                Toast.makeText(this, "Персонаж удалён", Toast.LENGTH_SHORT).show();
            });
            
            ImageButton screenBtn = createSmallRoundButton(createScreenIcon(), "#FF8C00");
            screenBtn.setOnClickListener(v -> {
                loadCharacterToScreen(data);
                hideMainOverlay();
                createMainCircle();
            });
            
            ImageButton circleBtn = createSmallRoundButton(createFloatIcon(), "#0080FF");
            circleBtn.setOnClickListener(v -> {
                loadCharacterToFloat(data);
                hideMainOverlay();
                createMainCircle();
            });
            
            ImageButton editBtn = createSmallRoundButton(createEditIcon(), "#9C27B0");
            editBtn.setOnClickListener(v -> {
                showCharacterEditor(data, index);
            });
            
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(50, 50);
            btnParams.setMargins(4, 0, 4, 0);
            actionLayout.addView(deleteBtn, btnParams);
            actionLayout.addView(screenBtn, btnParams);
            actionLayout.addView(circleBtn, btnParams);
            actionLayout.addView(editBtn, btnParams);
            
            card.addView(actionLayout);
            return card;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private ImageButton createSmallRoundButton(Drawable icon, String color) {
        try {
            ImageButton btn = new ImageButton(this);
            btn.setImageDrawable(icon);
            btn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            btn.setPadding(10, 10, 10, 10);
            
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(Color.parseColor(color));
            bg.setStroke(2, Color.parseColor("#FF4444"));
            btn.setBackground(bg);
            return btn;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void updateContent() {
        try {
            if (contentContainer == null) return;
            contentContainer.removeAllViews();
            
            if (isWebViewMode) {
                if (webView != null) {
                    contentContainer.addView(webView);
                } else {
                    createWebView();
                    if (webView != null) contentContainer.addView(webView);
                }
            } else {
                if (charactersGridLayout != null) {
                    updateCharactersGrid();
                    contentContainer.addView(charactersGridLayout);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void hideMainOverlay() {
        try {
            if (mainOverlay != null && windowManager != null && isMainOverlayVisible) {
                windowManager.removeView(mainOverlay);
                mainOverlay = null;
                isMainOverlayVisible = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== ДОБАВЛЕНИЕ ПЕРСОНАЖА ====================

    private void showAddCharacterDialog() {
        try {
            FrameLayout dialogOverlay = new FrameLayout(this);
            dialogOverlay.setBackgroundColor(Color.parseColor("#CC000000"));
            
            LinearLayout dialogLayout = new LinearLayout(this);
            dialogLayout.setOrientation(LinearLayout.VERTICAL);
            dialogLayout.setGravity(Gravity.CENTER);
            dialogLayout.setPadding(40, 40, 40, 40);
            
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(30);
            bg.setColor(Color.parseColor("#0D0D0D"));
            bg.setStroke(3, Color.parseColor("#CC0000"));
            dialogLayout.setBackground(bg);
            
            TextView title = new TextView(this);
            title.setText("ДОБАВИТЬ ПЕРСОНАЖА");
            title.setTextColor(Color.parseColor("#CC0000"));
            title.setTextSize(22);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, 0, 0, 30);
            dialogLayout.addView(title);
            
            Button addBtn = new Button(this);
            addBtn.setText("ВЫБРАТЬ ИЗОБРАЖЕНИЕ");
            addBtn.setTextColor(Color.WHITE);
            addBtn.setTextSize(16);
            addBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable addBg = new GradientDrawable();
            addBg.setCornerRadius(25);
            addBg.setColor(Color.parseColor("#CC0000"));
            addBtn.setBackground(addBg);
            addBtn.setPadding(40, 20, 40, 20);
            addBtn.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            addBtn.setOnClickListener(v -> {
                if (windowManager != null) windowManager.removeView(dialogOverlay);
                openGalleryForCharacter();
            });
            dialogLayout.addView(addBtn);
            
            Button cancelBtn = new Button(this);
            cancelBtn.setText("ОТМЕНА");
            cancelBtn.setTextColor(Color.WHITE);
            cancelBtn.setTextSize(16);
            cancelBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable cancelBg = new GradientDrawable();
            cancelBg.setCornerRadius(25);
            cancelBg.setColor(Color.parseColor("#2A2A2A"));
            cancelBg.setStroke(2, Color.parseColor("#8B0000"));
            cancelBtn.setBackground(cancelBg);
            cancelBtn.setPadding(40, 20, 40, 20);
            cancelBtn.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            cancelBtn.setOnClickListener(v -> {
                if (windowManager != null) windowManager.removeView(dialogOverlay);
            });
            
            LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cancelParams.setMargins(0, 12, 0, 0);
            dialogLayout.addView(cancelBtn, cancelParams);
            
            FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            containerParams.gravity = Gravity.CENTER;
            containerParams.setMargins(40, 0, 40, 0);
            dialogOverlay.addView(dialogLayout, containerParams);
            
            WindowManager.LayoutParams windowParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT);
            
            if (windowManager != null) windowManager.addView(dialogOverlay, windowParams);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void openGalleryForCharacter() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_GALLERY);
    }

    // ==================== ЗАГРУЗКА ПЕРСОНАЖА ====================

    private void loadCharacterToFloat(CharacterData data) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), Uri.fromFile(new File(data.path)));
            Bitmap processed = removeGreenScreen(bitmap, 40);
            
            removeMainCircle();
            
            mainCircleContainer = new FrameLayout(this);
            mainCircleContainer.setBackgroundColor(Color.TRANSPARENT);
            
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(Color.TRANSPARENT);
            d.setStroke(3, Color.WHITE);
            mainCircleContainer.setBackground(d);
            mainCircleContainer.setAlpha(overlayAlpha / 255f);
            
            ImageButton imageButton = new ImageButton(this);
            imageButton.setImageBitmap(processed);
            imageButton.setBackgroundColor(Color.TRANSPARENT);
            imageButton.setPadding(5, 5, 5, 5);
            imageButton.setScaleType(ImageButton.ScaleType.FIT_CENTER);
            imageButton.setClickable(false);
            imageButton.setFocusable(false);
            
            mainCircleContainer.addView(imageButton, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            
            mainCircleParams = new WindowManager.LayoutParams(
                    overlaySize, overlaySize,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT);
            mainCircleParams.gravity = Gravity.TOP | Gravity.START;
            
            int savedX = prefs.getInt("overlay_x", 100);
            int savedY = prefs.getInt("overlay_y", 200);
            mainCircleParams.x = savedX;
            mainCircleParams.y = savedY;
            
            mainCircleContainer.setOnTouchListener(new View.OnTouchListener() {
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
                                if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isDragging = true;
                                if (isDragging) {
                                    mainCircleParams.x = initialX + (int) dx;
                                    mainCircleParams.y = initialY + (int) dy;
                                    if (windowManager != null) {
                                        windowManager.updateViewLayout(mainCircleContainer, mainCircleParams);
                                        prefs.edit()
                                            .putInt("overlay_x", mainCircleParams.x)
                                            .putInt("overlay_y", mainCircleParams.y)
                                            .apply();
                                    }
                                }
                                return true;
                                
                            case MotionEvent.ACTION_UP:
                                if (!isDragging) {
                                    hideMainOverlay();
                                    showMainOverlay();
                                }
                                return true;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return false;
                }
            });
            
            if (windowManager == null) {
                windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            }
            
            if (windowManager != null) {
                windowManager.addView(mainCircleContainer, mainCircleParams);
                Toast.makeText(this, "Персонаж загружен в оверлей", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка загрузки", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadCharacterToScreen(CharacterData data) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), Uri.fromFile(new File(data.path)));
            Bitmap processed = removeGreenScreen(bitmap, 40);
            
            ScreenCharacter sc = new ScreenCharacter(
                data.path,
                100 + (screenCharacters.size() * 50),
                200 + (screenCharacters.size() * 30),
                400, 400
            );
            screenCharacters.add(sc);
            showCharacterOnScreen(sc);
            
            if (autoSaveEnabled) {
                saveScene();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка загрузки", Toast.LENGTH_SHORT).show();
        }
    }

    private void showCharacterOnScreen(ScreenCharacter sc) {
        try {
            if (windowManager == null) {
                windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            }
            if (windowManager == null) return;
            
            Bitmap bitmap = BitmapFactory.decodeFile(sc.characterPath);
            if (bitmap == null) return;
            
            Bitmap processed = removeGreenScreen(bitmap, 40);
            
            FrameLayout container = new FrameLayout(this);
            container.setBackgroundColor(Color.TRANSPARENT);
            
            ImageView imageView = new ImageView(this);
            imageView.setImageBitmap(processed);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            
            if (sc.rotation != 0 || sc.scaleX != 1.0f || sc.scaleY != 1.0f) {
                Matrix matrix = new Matrix();
                matrix.setScale(sc.scaleX, sc.scaleY);
                matrix.postRotate(sc.rotation, processed.getWidth() / 2f, processed.getHeight() / 2f);
                Bitmap transformed = Bitmap.createBitmap(processed, 0, 0, processed.getWidth(), processed.getHeight(), matrix, true);
                imageView.setImageBitmap(transformed);
            }
            
            container.addView(imageView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            
            addCharacterControls(container, sc);
            
            sc.container = container;
            sc.params = new WindowManager.LayoutParams(
                    sc.width, sc.height,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | 
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT);
            sc.params.gravity = Gravity.TOP | Gravity.START;
            sc.params.x = sc.x;
            sc.params.y = sc.y;
            
            windowManager.addView(container, sc.params);
            
            Toast.makeText(this, "Персонаж на экране", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== УПРАВЛЕНИЕ ПЕРСОНАЖЕМ ====================

    private void addCharacterControls(FrameLayout container, ScreenCharacter sc) {
        try {
            ImageButton closeBtn = new ImageButton(this);
            closeBtn.setImageDrawable(createCloseIcon());
            GradientDrawable closeBg = new GradientDrawable();
            closeBg.setShape(GradientDrawable.OVAL);
            closeBg.setColor(Color.parseColor("#CC0000"));
            closeBg.setStroke(2, Color.WHITE);
            closeBtn.setBackground(closeBg);
            closeBtn.setPadding(12, 12, 12, 12);
            
            FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(60, 60, Gravity.TOP | Gravity.END);
            closeParams.setMargins(0, 16, 16, 0);
            closeBtn.setLayoutParams(closeParams);
            closeBtn.setOnClickListener(v -> {
                removeScreenCharacter(sc);
                Toast.makeText(MainActivity.this, "Персонаж убран", Toast.LENGTH_SHORT).show();
            });
            
            ImageButton editBtn = new ImageButton(this);
            editBtn.setImageDrawable(createEditIcon());
            GradientDrawable editBg = new GradientDrawable();
            editBg.setShape(GradientDrawable.OVAL);
            editBg.setColor(Color.parseColor("#9C27B0"));
            editBg.setStroke(2, Color.WHITE);
            editBtn.setBackground(editBg);
            editBtn.setPadding(12, 12, 12, 12);
            
            FrameLayout.LayoutParams editParams = new FrameLayout.LayoutParams(60, 60, Gravity.BOTTOM | Gravity.END);
            editParams.setMargins(0, 0, 16, 80);
            editBtn.setLayoutParams(editParams);
            editBtn.setOnClickListener(v -> {
                int index = screenCharacters.indexOf(sc);
                if (index >= 0 && index < characters.size()) {
                    showCharacterEditor(characters.get(index), index);
                }
            });
            
            container.addView(closeBtn);
            container.addView(editBtn);
            
            container.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        lastTouchX = event.getRawX();
                        lastTouchY = event.getRawY();
                        return true;
                        
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - lastTouchX;
                        float dy = event.getRawY() - lastTouchY;
                        sc.params.x += (int) dx;
                        sc.params.y += (int) dy;
                        sc.x = sc.params.x;
                        sc.y = sc.params.y;
                        lastTouchX = event.getRawX();
                        lastTouchY = event.getRawY();
                        if (windowManager != null) {
                            windowManager.updateViewLayout(sc.container, sc.params);
                            if (autoSaveEnabled) saveScene();
                        }
                        return true;
                }
                return false;
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void removeScreenCharacter(ScreenCharacter sc) {
        try {
            if (sc.container != null && windowManager != null) {
                windowManager.removeView(sc.container);
            }
            screenCharacters.remove(sc);
            if (autoSaveEnabled) saveScene();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void clearAllScreenCharacters() {
        try {
            for (ScreenCharacter sc : new ArrayList<>(screenCharacters)) {
                removeScreenCharacter(sc);
            }
            screenCharacters.clear();
            if (autoSaveEnabled) saveScene();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== РЕДАКТОР ПЕРСОНАЖА ====================

    private void showCharacterEditor(CharacterData data, int index) {
        try {
            if (windowManager == null) return;
            
            selectedCharacterIndex = index;
            
            editContainer = new FrameLayout(this);
            editContainer.setBackgroundColor(Color.parseColor("#E6000000"));
            
            LinearLayout editLayout = new LinearLayout(this);
            editLayout.setOrientation(LinearLayout.VERTICAL);
            editLayout.setGravity(Gravity.CENTER);
            editLayout.setPadding(30, 30, 30, 30);
            
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(40);
            bg.setColor(Color.parseColor("#0D0D0D"));
            bg.setStroke(3, Color.parseColor("#CC0000"));
            editLayout.setBackground(bg);
            
            TextView title = new TextView(this);
            title.setText("РЕДАКТОР ПЕРСОНАЖА");
            title.setTextColor(Color.parseColor("#CC0000"));
            title.setTextSize(22);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, 0, 0, 20);
            editLayout.addView(title);
            
            FrameLayout previewFrame = new FrameLayout(this);
            previewFrame.setLayoutParams(new LinearLayout.LayoutParams(200, 200));
            GradientDrawable previewBg = new GradientDrawable();
            previewBg.setShape(GradientDrawable.OVAL);
            previewBg.setColor(Color.parseColor("#1A1A1A"));
            previewBg.setStroke(2, Color.parseColor("#CC0000"));
            previewFrame.setBackground(previewBg);
            
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), Uri.fromFile(new File(data.path)));
                editBitmap = removeGreenScreen(bitmap, 40);
                editImageView = new ImageView(this);
                editImageView.setImageBitmap(editBitmap);
                editImageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                previewFrame.addView(editImageView, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            } catch (Exception e) {
                e.printStackTrace();
            }
            editLayout.addView(previewFrame);
            
            LinearLayout controlPanel = new LinearLayout(this);
            controlPanel.setOrientation(LinearLayout.VERTICAL);
            controlPanel.setPadding(0, 20, 0, 0);
            
            TextView sizeLabel = new TextView(this);
            sizeLabel.setText("РАЗМЕР: 400px");
            sizeLabel.setTextColor(Color.WHITE);
            sizeLabel.setTextSize(14);
            sizeLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            controlPanel.addView(sizeLabel);
            
            SeekBar sizeSeek = new SeekBar(this);
            sizeSeek.setMax(800);
            sizeSeek.setMin(50);
            sizeSeek.setProgress(400);
            sizeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    sizeLabel.setText("РАЗМЕР: " + progress + "px");
                    if (editImageView != null) {
                        editImageView.getLayoutParams().width = progress;
                        editImageView.getLayoutParams().height = progress;
                        editImageView.requestLayout();
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            controlPanel.addView(sizeSeek);
            
            TextView rotLabel = new TextView(this);
            rotLabel.setText("ПОВОРОТ: 0°");
            rotLabel.setTextColor(Color.WHITE);
            rotLabel.setTextSize(14);
            rotLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            rotLabel.setPadding(0, 12, 0, 0);
            controlPanel.addView(rotLabel);
            
            SeekBar rotSeek = new SeekBar(this);
            rotSeek.setMax(360);
            rotSeek.setProgress(0);
            rotSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    rotLabel.setText("ПОВОРОТ: " + progress + "°");
                    rotationAngle = progress;
                    updateEditPreview();
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            controlPanel.addView(rotSeek);
            
            TextView scaleXLabel = new TextView(this);
            scaleXLabel.setText("МАСШТАБ X: 1.0");
            scaleXLabel.setTextColor(Color.WHITE);
            scaleXLabel.setTextSize(14);
            scaleXLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            scaleXLabel.setPadding(0, 12, 0, 0);
            controlPanel.addView(scaleXLabel);
            
            SeekBar scaleXSeek = new SeekBar(this);
            scaleXSeek.setMax(300);
            scaleXSeek.setProgress(100);
            scaleXSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    scaleX = progress / 100f;
                    scaleXLabel.setText("МАСШТАБ X: " + String.format("%.1f", scaleX));
                    updateEditPreview();
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            controlPanel.addView(scaleXSeek);
            
            TextView scaleYLabel = new TextView(this);
            scaleYLabel.setText("МАСШТАБ Y: 1.0");
            scaleYLabel.setTextColor(Color.WHITE);
            scaleYLabel.setTextSize(14);
            scaleYLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            scaleYLabel.setPadding(0, 12, 0, 0);
            controlPanel.addView(scaleYLabel);
            
            SeekBar scaleYSeek = new SeekBar(this);
            scaleYSeek.setMax(300);
            scaleYSeek.setProgress(100);
            scaleYSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    scaleY = progress / 100f;
                    scaleYLabel.setText("МАСШТАБ Y: " + String.format("%.1f", scaleY));
                    updateEditPreview();
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            controlPanel.addView(scaleYSeek);
            
            editLayout.addView(controlPanel);
            
            LinearLayout buttonRow = new LinearLayout(this);
            buttonRow.setOrientation(LinearLayout.HORIZONTAL);
            buttonRow.setGravity(Gravity.CENTER);
            buttonRow.setPadding(0, 20, 0, 0);
            
            Button applyBtn = new Button(this);
            applyBtn.setText("ПРИМЕНИТЬ");
            applyBtn.setTextColor(Color.WHITE);
            applyBtn.setTextSize(14);
            applyBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable applyBg = new GradientDrawable();
            applyBg.setCornerRadius(20);
            applyBg.setColor(Color.parseColor("#CC0000"));
            applyBtn.setBackground(applyBg);
            applyBtn.setPadding(20, 12, 20, 12);
            LinearLayout.LayoutParams applyParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            applyParams.setMargins(0, 0, 8, 0);
            applyBtn.setLayoutParams(applyParams);
            applyBtn.setOnClickListener(v -> {
                applyCharacterEdits(data, index);
                if (windowManager != null) windowManager.removeView(editContainer);
                Toast.makeText(MainActivity.this, "Изменения применены", Toast.LENGTH_SHORT).show();
            });
            
            Button cancelBtn2 = new Button(this);
            cancelBtn2.setText("ОТМЕНА");
            cancelBtn2.setTextColor(Color.WHITE);
            cancelBtn2.setTextSize(14);
            cancelBtn2.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable cancelBg2 = new GradientDrawable();
            cancelBg2.setCornerRadius(20);
            cancelBg2.setColor(Color.parseColor("#2A2A2A"));
            cancelBg2.setStroke(2, Color.parseColor("#8B0000"));
            cancelBtn2.setBackground(cancelBg2);
            cancelBtn2.setPadding(20, 12, 20, 12);
            LinearLayout.LayoutParams cancelParams2 = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            cancelParams2.setMargins(8, 0, 0, 0);
            cancelBtn2.setLayoutParams(cancelParams2);
            cancelBtn2.setOnClickListener(v -> {
                if (windowManager != null) windowManager.removeView(editContainer);
            });
            
            buttonRow.addView(applyBtn);
            buttonRow.addView(cancelBtn2);
            editLayout.addView(buttonRow);
            
            // Кнопка сохранения в галерею (упрощенная версия без Environment)
            Button saveGalleryBtn = new Button(this);
            saveGalleryBtn.setText("СОХРАНИТЬ В ГАЛЕРЕЮ");
            saveGalleryBtn.setTextColor(Color.WHITE);
            saveGalleryBtn.setTextSize(14);
            saveGalleryBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable saveGalleryBg = new GradientDrawable();
            saveGalleryBg.setCornerRadius(20);
            saveGalleryBg.setColor(Color.parseColor("#0080FF"));
            saveGalleryBtn.setBackground(saveGalleryBg);
            saveGalleryBtn.setPadding(20, 12, 20, 12);
            saveGalleryBtn.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            saveGalleryBtn.setOnClickListener(v -> {
                saveEditedCharacterToGallery();
            });
            LinearLayout.LayoutParams saveGalleryParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            saveGalleryParams.setMargins(0, 12, 0, 0);
            saveGalleryBtn.setLayoutParams(saveGalleryParams);
            editLayout.addView(saveGalleryBtn);
            
            FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            containerParams.gravity = Gravity.CENTER;
            containerParams.setMargins(20, 40, 20, 40);
            editContainer.addView(editLayout, containerParams);
            
            editParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT);
            
            windowManager.addView(editContainer, editParams);
            
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка открытия редактора", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateEditPreview() {
        try {
            if (editImageView == null || editBitmap == null) return;
            
            Matrix matrix = new Matrix();
            matrix.setScale(scaleX, scaleY);
            matrix.postRotate(rotationAngle, editBitmap.getWidth() / 2f, editBitmap.getHeight() / 2f);
            
            Bitmap transformed = Bitmap.createBitmap(editBitmap, 0, 0, editBitmap.getWidth(), editBitmap.getHeight(), matrix, true);
            editImageView.setImageBitmap(transformed);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void applyCharacterEdits(CharacterData data, int index) {
        try {
            for (ScreenCharacter sc : screenCharacters) {
                if (sc.characterPath.equals(data.path)) {
                    sc.rotation = rotationAngle;
                    sc.scaleX = scaleX;
                    sc.scaleY = scaleY;
                    if (sc.container != null && windowManager != null) {
                        windowManager.removeView(sc.container);
                        showCharacterOnScreen(sc);
                    }
                    if (autoSaveEnabled) saveScene();
                    break;
                }
            }
            
            if (editImageView != null) {
                int size = editImageView.getLayoutParams().width;
                data.width = size;
                data.height = size;
                saveCharacters();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Упрощенная версия сохранения в галерею без Environment
    private void saveEditedCharacterToGallery() {
        try {
            if (editBitmap == null) return;
            
            Matrix matrix = new Matrix();
            matrix.setScale(scaleX, scaleY);
            matrix.postRotate(rotationAngle, editBitmap.getWidth() / 2f, editBitmap.getHeight() / 2f);
            
            Bitmap transformed = Bitmap.createBitmap(editBitmap, 0, 0, editBitmap.getWidth(), editBitmap.getHeight(), matrix, true);
            
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "EDITED_" + timeStamp + ".png";
            
            // Используем MediaStore для Android 10+
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CRArcade");
            }
            
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                OutputStream out = getContentResolver().openOutputStream(uri);
                if (out != null) {
                    transformed.compress(Bitmap.CompressFormat.PNG, 100, out);
                    out.close();
                    Toast.makeText(this, "Сохранено в галерею", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка сохранения: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== ЗАГРУЗКА СЦЕНЫ ====================

    private void showLoadSceneConfirmation() {
        try {
            FrameLayout confirmOverlay = new FrameLayout(this);
            confirmOverlay.setBackgroundColor(Color.parseColor("#CC000000"));
            
            LinearLayout confirmLayout = new LinearLayout(this);
            confirmLayout.setOrientation(LinearLayout.VERTICAL);
            confirmLayout.setGravity(Gravity.CENTER);
            confirmLayout.setPadding(40, 40, 40, 40);
            
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(30);
            bg.setColor(Color.parseColor("#0D0D0D"));
            bg.setStroke(3, Color.parseColor("#CC0000"));
            confirmLayout.setBackground(bg);
            
            TextView title = new TextView(this);
            title.setText("ЗАГРУЗИТЬ СЦЕНУ?");
            title.setTextColor(Color.parseColor("#CC0000"));
            title.setTextSize(22);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, 0, 0, 20);
            confirmLayout.addView(title);
            
            TextView message = new TextView(this);
            message.setText("Текущие персонажи на экране\nбудут удалены.");
            message.setTextColor(Color.parseColor("#AAAAAA"));
            message.setTextSize(16);
            message.setGravity(Gravity.CENTER);
            message.setPadding(0, 0, 0, 30);
            confirmLayout.addView(message);
            
            LinearLayout buttonLayout = new LinearLayout(this);
            buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
            buttonLayout.setGravity(Gravity.CENTER);
            
            Button loadBtn = new Button(this);
            loadBtn.setText("ЗАГРУЗИТЬ");
            loadBtn.setTextColor(Color.WHITE);
            loadBtn.setTextSize(16);
            loadBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable loadBg = new GradientDrawable();
            loadBg.setCornerRadius(25);
            loadBg.setColor(Color.parseColor("#FF8C00"));
            loadBtn.setBackground(loadBg);
            loadBtn.setPadding(30, 16, 30, 16);
            loadBtn.setOnClickListener(v -> {
                try {
                    if (windowManager != null) windowManager.removeView(confirmOverlay);
                    clearAllScreenCharacters();
                    loadScene();
                    Toast.makeText(MainActivity.this, "Сцена загружена!", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            
            Button cancelBtn = new Button(this);
            cancelBtn.setText("ОТМЕНА");
            cancelBtn.setTextColor(Color.WHITE);
            cancelBtn.setTextSize(16);
            cancelBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable cancelBg = new GradientDrawable();
            cancelBg.setCornerRadius(25);
            cancelBg.setColor(Color.parseColor("#2A2A2A"));
            cancelBg.setStroke(2, Color.parseColor("#8B0000"));
            cancelBtn.setBackground(cancelBg);
            cancelBtn.setPadding(30, 16, 30, 16);
            cancelBtn.setOnClickListener(v -> {
                if (windowManager != null) windowManager.removeView(confirmOverlay);
            });
            
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            btnParams.setMargins(8, 0, 8, 0);
            buttonLayout.addView(loadBtn, btnParams);
            buttonLayout.addView(cancelBtn, btnParams);
            
            confirmLayout.addView(buttonLayout);
            
            FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            containerParams.gravity = Gravity.CENTER;
            containerParams.setMargins(40, 0, 40, 0);
            confirmOverlay.addView(confirmLayout, containerParams);
            
            WindowManager.LayoutParams windowParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT);
            
            if (windowManager != null) windowManager.addView(confirmOverlay, windowParams);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== CHROMAKEY ====================

    private Bitmap removeGreenScreen(Bitmap source, int tolerance) {
        try {
            if (source == null) return null;
            
            int width = source.getWidth();
            int height = source.getHeight();
            Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            
            int[] pixels = new int[width * height];
            source.getPixels(pixels, 0, width, 0, 0, width, height);
            
            for (int i = 0; i < pixels.length; i++) {
                int pixel = pixels[i];
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);
                
                if (g > r + tolerance && g > b + tolerance) {
                    pixels[i] = Color.TRANSPARENT;
                }
            }
            
            result.setPixels(pixels, 0, width, 0, 0, width, height);
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    private int getOverlayFlag() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;
    }

    // ==================== ЖИЗНЕННЫЙ ЦИКЛ ====================

    @Override
    public void onResume() {
        super.onResume();
        isAppInForeground = true;
        removeMainCircle();
        hideMainOverlay();
        for (ScreenCharacter sc : screenCharacters) {
            if (sc.container == null) {
                showCharacterOnScreen(sc);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        isAppInForeground = false;
        hideMainOverlay();
        for (ScreenCharacter sc : screenCharacters) {
            try {
                if (sc.container != null && windowManager != null) {
                    windowManager.removeView(sc.container);
                    sc.container = null;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        createMainCircle();
        if (autoSaveEnabled) saveScene();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (!isAppInForeground) {
            createMainCircle();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            clearAllScreenCharacters();
            removeMainCircle();
            hideMainOverlay();
            if (editContainer != null && windowManager != null) {
                windowManager.removeView(editContainer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            switch (code) {
                case REQUEST_MICROPHONE: Toast.makeText(this, "Микрофон разрешён", Toast.LENGTH_SHORT).show(); break;
                case REQUEST_CAMERA: Toast.makeText(this, "Камера разрешена", Toast.LENGTH_SHORT).show(); break;
                case REQUEST_STORAGE: Toast.makeText(this, "Хранилище разрешено", Toast.LENGTH_SHORT).show(); break;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        try {
            if (requestCode == REQUEST_GALLERY && resultCode == RESULT_OK && data != null) {
                Uri imageUri = data.getData();
                if (imageUri != null) {
                    Bitmap original = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                    String path = saveImageToStorage(original);
                    if (path != null) {
                        characterCounter++;
                        String charName = "Персонаж " + characterCounter;
                        characters.add(new CharacterData(charName, path));
                        saveCharacters();
                        Toast.makeText(this, charName + " сохранён", Toast.LENGTH_SHORT).show();
                        if (isMainOverlayVisible) updateContent();
                    }
                }
            }
            
            if (requestCode == REQUEST_OVERLAY_PERMISSION) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                    if (!isAppInForeground) {
                        createMainCircle();
                    }
                } else {
                    Toast.makeText(this, "Разрешение на оверлей требуется!", Toast.LENGTH_LONG).show();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка загрузки", Toast.LENGTH_SHORT).show();
        }
    }
        }
