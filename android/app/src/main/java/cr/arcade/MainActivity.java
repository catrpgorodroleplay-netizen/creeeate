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
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
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
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends BridgeActivity {

    private static final int REQUEST_OVERLAY_PERMISSION = 101;
    private static final int REQUEST_GALLERY = 103;
    private static final int REQUEST_VIDEO = 105;
    private static final int REQUEST_GENERAL_PERMISSIONS = 108;

    private static final String URL_HOME = "https://wyikhedfghhopyewfvjkurrhncswehipkhf.vercel.app/";
    private static final String URL_SETTINGS = "https://whuokhgrdcbnmkloplureecvjiqoendu.vercel.app/";
    private static final String URL_WEB_OVERLAY = "https://acojnehucijwbyqofufhwnwkrofucyksmnx.vercel.app/";
    private static final String TAG = "MainActivity";

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

    // Компоненты для хромакей видео с OpenGL
    private FrameLayout chromaContainer;
    private GLSurfaceView chromaGLSurfaceView;
    private ChromaRenderer chromaRenderer;
    private MediaPlayer chromaMediaPlayer;
    private Surface chromaSurface;
    private WindowManager.LayoutParams chromaParams;
    private boolean isChromaActive = false;
    private boolean isChromaFixed = false;
    private String currentChromaVideoPath = null;
    private int chromaTolerance = 40;
    
    // Контролы
    private LinearLayout chromaControls;
    private ImageButton chromaFixButton;
    private ImageButton chromaDeleteButton;
    private ImageButton chromaBackButton;
    private EditText chromaSizeXInput, chromaSizeYInput, chromaSizeZInput;
    private TextView chromaSizeXLabel, chromaSizeYLabel, chromaSizeZLabel;
    private SeekBar chromaXSeekBar, chromaYSeekBar, chromaZSeekBar;
    private SeekBar chromaToleranceSeekBar;
    private TextView chromaToleranceLabel;
    
    // WebView оверлей
    private FrameLayout webOverlayContainer;
    private WebView webOverlayView;
    private WindowManager.LayoutParams webOverlayParams;
    private boolean isWebOverlayActive = false;
    private boolean isWebOverlayFixed = false;
    
    private float lastTouchX, lastTouchY;
    private float initialPinchDistance = 0;
    
    // Система персонажей
    private ArrayList<CharacterData> characters = new ArrayList<>();
    private SharedPreferences prefs;
    private String tempCharacterName = "";
    private EditText nameInput;
    private boolean isVideoMode = false;
    
    // Переключение режимов
    private boolean isWebViewMode = true;
    private FrameLayout contentContainer;
    private LinearLayout charactersGridLayout;
    
    // Настройки оверлея
    private int overlayAlpha = 255;
    private int overlaySize = 136;

    // Сохранение состояния WebView
    private String currentWebViewUrl = URL_HOME;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            prefs = getSharedPreferences("characters", MODE_PRIVATE);
            overlayAlpha = prefs.getInt("overlay_alpha", 255);
            overlaySize = prefs.getInt("overlay_size", 136);
            chromaTolerance = prefs.getInt("chroma_tolerance", 40);
            loadCharacters();
        } catch (Exception e) {
            Log.e(TAG, "onCreate error", e);
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
            Log.e(TAG, "Overlay permission error", e);
        }

        if (windowManager == null) {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        }
    }

    // ==================== КЛАСС ДАННЫХ ПЕРСОНАЖА ====================
    
    private static class CharacterData {
        String name;
        String path;
        long timestamp;
        boolean isVideo;
        int width;
        int height;
        
        CharacterData(String name, String path, boolean isVideo) {
            this.name = name;
            this.path = path;
            this.isVideo = isVideo;
            this.timestamp = System.currentTimeMillis();
            this.width = 400;
            this.height = 400;
        }
        
        CharacterData(JSONObject json) throws Exception {
            this.name = json.getString("name");
            this.path = json.getString("path");
            this.isVideo = json.optBoolean("isVideo", false);
            this.timestamp = json.optLong("timestamp", System.currentTimeMillis());
            this.width = json.optInt("width", 400);
            this.height = json.optInt("height", 400);
        }
        
        JSONObject toJSON() throws Exception {
            JSONObject json = new JSONObject();
            json.put("name", name);
            json.put("path", path);
            json.put("isVideo", isVideo);
            json.put("timestamp", timestamp);
            json.put("width", width);
            json.put("height", height);
            return json;
        }
    }

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
            Log.e(TAG, "loadCharacters error", e);
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
                .putInt("overlay_alpha", overlayAlpha)
                .putInt("overlay_size", overlaySize)
                .putInt("chroma_tolerance", chromaTolerance)
                .putString("webview_url", currentWebViewUrl)
                .apply();
        } catch (Exception e) {
            Log.e(TAG, "saveCharacters error", e);
        }
    }
    
    private String saveVideoToStorage(Uri videoUri) {
        try {
            File dir = new File(getExternalFilesDir(null), "characters/videos");
            if (!dir.exists()) dir.mkdirs();
            
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File file = new File(dir, "VIDEO_" + timeStamp + ".mp4");
            
            InputStream in = getContentResolver().openInputStream(videoUri);
            FileOutputStream out = new FileOutputStream(file);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            out.close();
            
            return file.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, "saveVideoToStorage error", e);
            return null;
        }
    }

    private void requestPermissionsIfNeeded() {
        try {
            List<String> permissionsNeeded = new ArrayList<>();
            
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.CAMERA);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                        != PackageManager.PERMISSION_GRANTED) {
                    permissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES);
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                        != PackageManager.PERMISSION_GRANTED) {
                    permissionsNeeded.add(Manifest.permission.READ_MEDIA_VIDEO);
                }
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
                }
            }
            
            if (!permissionsNeeded.isEmpty()) {
                ActivityCompat.requestPermissions(this,
                        permissionsNeeded.toArray(new String[0]), 
                        REQUEST_GENERAL_PERMISSIONS);
            }
        } catch (Exception e) {
            Log.e(TAG, "requestPermissionsIfNeeded error", e);
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
            
            if (mainCircleContainer != null) {
                Log.d(TAG, "mainCircleContainer уже существует");
                return;
            }
            
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
                        Log.e(TAG, "Touch error", e);
                    }
                    return false;
                }
            });

            if (windowManager != null) {
                windowManager.addView(mainCircleContainer, mainCircleParams);
                Log.d(TAG, "mainCircleContainer создан");
            }
        } catch (Exception e) {
            Log.e(TAG, "createMainCircle error", e);
        }
    }

    private void removeMainCircle() {
        try {
            if (mainCircleContainer != null && windowManager != null) {
                windowManager.removeView(mainCircleContainer);
                mainCircleContainer = null;
                Log.d(TAG, "mainCircleContainer удален");
            }
        } catch (Exception e) {
            Log.e(TAG, "removeMainCircle error", e);
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
            Log.e(TAG, "createGamepadBitmap error", e);
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
            Log.e(TAG, "createHomeIcon error", e);
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
            Log.e(TAG, "createCloseIcon error", e);
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
            Log.e(TAG, "createCharacterIcon error", e);
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
            Log.e(TAG, "createSettingsIcon error", e);
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
            Log.e(TAG, "createExitIcon error", e);
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
            Log.e(TAG, "createHideIcon error", e);
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
            Log.e(TAG, "createDeleteIcon error", e);
            return null;
        }
    }

    private Drawable createWebOverlayIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(6);
            
            float cx = 60, cy = 60;
            c.drawCircle(cx, cy, 28, p);
            c.drawLine(cx - 28, cy, cx + 28, cy, p);
            c.drawLine(cx, cy - 28, cx, cy + 28, p);
            c.drawOval(cx - 28, cy - 20, cx + 28, cy + 20, p);
            c.drawLine(cx, cy + 12, cx, cy - 18, p);
            c.drawLine(cx - 10, cy - 8, cx, cy - 18, p);
            c.drawLine(cx + 10, cy - 8, cx, cy - 18, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            Log.e(TAG, "createWebOverlayIcon error", e);
            return null;
        }
    }

    private Drawable createLockIcon(boolean locked) {
        try {
            Bitmap b = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(5);
            
            float cx = 30, cy = 30;
            c.drawArc(cx - 18, cy - 26, cx + 18, cy - 4, 0, 180, false, p);
            c.drawRect(cx - 15, cy - 8, cx + 15, cy + 18, p);
            p.setStyle(Paint.Style.FILL);
            p.setStrokeWidth(0);
            c.drawCircle(cx, cy + 6, 4, p);
            
            if (locked) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(6);
                p.setColor(Color.YELLOW);
                c.drawLine(cx - 22, cy - 15, cx + 22, cy + 22, p);
                c.drawLine(cx + 22, cy - 15, cx - 22, cy + 22, p);
            }
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            Log.e(TAG, "createLockIcon error", e);
            return null;
        }
    }

    private Drawable createChromaIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(6);
            
            float cx = 60, cy = 60;
            // Иконка видео с хромакеем - квадрат с зеленым углом
            c.drawRect(cx - 30, cy - 22, cx + 30, cy + 22, p);
            // Треугольник воспроизведения
            c.drawLine(cx - 10, cy - 14, cx + 12, cy, p);
            c.drawLine(cx + 12, cy, cx - 10, cy + 14, p);
            c.drawLine(cx - 10, cy + 14, cx - 10, cy - 14, p);
            // Зеленый уголок (символ хромакея)
            p.setColor(Color.GREEN);
            p.setStyle(Paint.Style.FILL);
            // Зеленый фон в левом нижнем углу
            c.drawRect(cx - 30, cy + 6, cx - 8, cy + 22, p);
            // Текст "KEY"
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.FILL);
            p.setTextSize(12);
            p.setStrokeWidth(0);
            c.drawText("KEY", cx - 28, cy + 19, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            Log.e(TAG, "createChromaIcon error", e);
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

            LinearLayout leftButtons = new LinearLayout(this);
            leftButtons.setOrientation(LinearLayout.HORIZONTAL);
            leftButtons.setGravity(Gravity.CENTER);
            
            ImageButton homeBtn = createLargeRoundButton(createHomeIcon(), "#1A1A1A", 4, "#CC0000");
            homeBtn.setOnClickListener(v -> {
                isWebViewMode = true;
                if (webView != null) {
                    webView.loadUrl(URL_HOME);
                    currentWebViewUrl = URL_HOME;
                    saveCharacters();
                }
                updateContent();
            });
            leftButtons.addView(homeBtn);
            
            ImageButton charsBtn = createLargeRoundButton(createCharacterIcon(), "#1A1A1A", 4, "#CC0000");
            charsBtn.setOnClickListener(v -> {
                isWebViewMode = false;
                updateContent();
            });
            leftButtons.addView(charsBtn);
            
            // Новая кнопка для хромакей видео
            ImageButton chromaBtn = createLargeRoundButton(createChromaIcon(), "#1A1A1A", 4, "#00CC00");
            chromaBtn.setOnClickListener(v -> {
                showAddChromaVideoDialog();
            });
            leftButtons.addView(chromaBtn);
            
            ImageButton webOverlayBtn = createLargeRoundButton(createWebOverlayIcon(), "#1A1A1A", 4, "#00CCFF");
            webOverlayBtn.setOnClickListener(v -> {
                hideMainOverlay();
                createMainCircle();
                showWebOverlay();
            });
            leftButtons.addView(webOverlayBtn);
            
            ImageButton settingsBtn = createLargeRoundButton(createSettingsIcon(), "#1A1A1A", 4, "#CC0000");
            settingsBtn.setOnClickListener(v -> {
                isWebViewMode = true;
                if (webView != null) {
                    webView.loadUrl(URL_SETTINGS);
                    currentWebViewUrl = URL_SETTINGS;
                    saveCharacters();
                }
                updateContent();
            });
            leftButtons.addView(settingsBtn);
            
            topBar.addView(leftButtons);

            LinearLayout rightButtons = new LinearLayout(this);
            rightButtons.setOrientation(LinearLayout.HORIZONTAL);
            rightButtons.setGravity(Gravity.CENTER);
            
            ImageButton hideBtn = createLargeRoundButton(createHideIcon(), "#1A0000", 4, "#8B0000");
            hideBtn.setOnClickListener(v -> {
                hideMainOverlay();
                createMainCircle();
            });
            rightButtons.addView(hideBtn);
            
            ImageButton exitBtn = createLargeRoundButton(createExitIcon(), "#1A0000", 4, "#8B0000");
            exitBtn.setOnClickListener(v -> showDeleteOverlayConfirmation());
            rightButtons.addView(exitBtn);
            
            topBar.addView(rightButtons);

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
            Log.e(TAG, "showMainOverlay error", e);
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
                    removeChromaVideo();
                    removeWebOverlay();
                    removeMainCircle();
                    finishAffinity();
                    System.exit(0);
                } catch (Exception e) {
                    Log.e(TAG, "Confirm delete error", e);
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
            Log.e(TAG, "showDeleteOverlayConfirmation error", e);
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
            Log.e(TAG, "createLargeRoundButton error", e);
            return null;
        }
    }

    private Button createSmallActionButton(String text, String color) {
        try {
            Button btn = new Button(this);
            btn.setText(text);
            btn.setTextColor(Color.WHITE);
            btn.setTextSize(14);
            btn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(Color.parseColor(color));
            bg.setStroke(2, Color.parseColor("#CC0000"));
            btn.setBackground(bg);
            btn.setPadding(0, 0, 0, 0);
            return btn;
        } catch (Exception e) {
            Log.e(TAG, "createSmallActionButton error", e);
            return null;
        }
    }

    // ==================== СОЗДАНИЕ WEBVIEW ====================

    private void createWebView() {
        try {
            if (webView != null) {
                return;
            }
            
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
            ws.setCacheMode(WebSettings.LOAD_DEFAULT);

            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onPermissionRequest(PermissionRequest request) {
                    try {
                        request.grant(new String[]{
                                PermissionRequest.RESOURCE_AUDIO_CAPTURE,
                                PermissionRequest.RESOURCE_VIDEO_CAPTURE
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "WebView permission error", e);
                    }
                }
            });
            
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    currentWebViewUrl = url;
                    saveCharacters();
                }
            });

            String savedUrl = prefs.getString("webview_url", URL_HOME);
            currentWebViewUrl = savedUrl;
            webView.loadUrl(savedUrl);

            webView.setBackgroundColor(Color.parseColor("#0A0A0A"));
            webView.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                    
            Log.d(TAG, "WebView создан с URL: " + currentWebViewUrl);
        } catch (Exception e) {
            Log.e(TAG, "createWebView error", e);
        }
    }

    // ==================== ГРИД ПЕРСОНАЖЕЙ ====================

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
            Log.e(TAG, "createCharactersGrid error", e);
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
            
            Button addBtn = new Button(this);
            addBtn.setText("+");
            addBtn.setTextColor(Color.WHITE);
            addBtn.setTextSize(24);
            addBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable addBg = new GradientDrawable();
            addBg.setShape(GradientDrawable.OVAL);
            addBg.setColor(Color.parseColor("#CC0000"));
            addBtn.setBackground(addBg);
            addBtn.setPadding(8, 4, 8, 4);
            LinearLayout.LayoutParams addBtnParams = new LinearLayout.LayoutParams(60, 60);
            addBtn.setLayoutParams(addBtnParams);
            addBtn.setOnClickListener(v -> showAddCharacterDialog(false));
            headerRow.addView(addBtn);
            
            Button addVideoBtn = new Button(this);
            addVideoBtn.setText("🎬");
            addVideoBtn.setTextColor(Color.WHITE);
            addVideoBtn.setTextSize(20);
            addVideoBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable addVideoBg = new GradientDrawable();
            addVideoBg.setShape(GradientDrawable.OVAL);
            addVideoBg.setColor(Color.parseColor("#2196F3"));
            addVideoBtn.setBackground(addVideoBg);
            addVideoBtn.setPadding(8, 4, 8, 4);
            LinearLayout.LayoutParams addVideoParams = new LinearLayout.LayoutParams(60, 60);
            addVideoParams.setMargins(8, 0, 0, 0);
            addVideoBtn.setLayoutParams(addVideoParams);
            addVideoBtn.setOnClickListener(v -> showAddCharacterDialog(true));
            headerRow.addView(addVideoBtn);
            
            charactersGridLayout.addView(headerRow);
            
            if (characters.isEmpty()) {
                TextView emptyText = new TextView(this);
                emptyText.setText("📭 Нет сохранённых персонажей\n\nНажмите + или 🎬 чтобы добавить");
                emptyText.setTextColor(Color.parseColor("#555555"));
                emptyText.setTextSize(16);
                emptyText.setTypeface(null, android.graphics.Typeface.BOLD);
                emptyText.setGravity(Gravity.CENTER);
                emptyText.setPadding(0, 60, 0, 60);
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
                rowLayout.setPadding(0, 4, 0, 4);
                
                for (int i = r * itemsPerRow; i < Math.min((r + 1) * itemsPerRow, totalItems); i++) {
                    CharacterData data = characters.get(i);
                    LinearLayout card = createSmallCharacterCard(data, i);
                    LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                    cardParams.setMargins(4, 0, 4, 0);
                    card.setLayoutParams(cardParams);
                    rowLayout.addView(card);
                }
                
                gridContainer.addView(rowLayout);
            }
            
            scrollView.addView(gridContainer);
            charactersGridLayout.addView(scrollView);
        } catch (Exception e) {
            Log.e(TAG, "updateCharactersGrid error", e);
        }
    }

    private LinearLayout createSmallCharacterCard(CharacterData data, int index) {
        try {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(12, 12, 12, 12);
            
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setCornerRadius(16);
            cardBg.setColor(Color.parseColor("#1A0000"));
            cardBg.setStroke(1, Color.parseColor("#8B0000"));
            card.setBackground(cardBg);
            
            FrameLayout previewContainer = new FrameLayout(this);
            previewContainer.setLayoutParams(new LinearLayout.LayoutParams(80, 80));
            
            GradientDrawable previewBg = new GradientDrawable();
            previewBg.setShape(GradientDrawable.OVAL);
            previewBg.setColor(Color.parseColor("#1A1A1A"));
            previewBg.setStroke(2, Color.parseColor("#8B0000"));
            previewContainer.setBackground(previewBg);
            
            if (!data.isVideo) {
                try {
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), 
                            Uri.fromFile(new File(data.path)));
                    ImageView thumbView = new ImageView(this);
                    thumbView.setImageBitmap(bitmap);
                    thumbView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    thumbView.setPadding(2, 2, 2, 2);
                    previewContainer.addView(thumbView, new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                    ));
                } catch (IOException e) {
                    Log.e(TAG, "createSmallCharacterCard image error", e);
                }
            } else {
                TextView videoIcon = new TextView(this);
                videoIcon.setText("🎬");
                videoIcon.setTextSize(32);
                videoIcon.setGravity(Gravity.CENTER);
                previewContainer.addView(videoIcon);
            }
            
            card.addView(previewContainer);
            
            String displayName = data.name.trim().isEmpty() ? "Без имени" : data.name;
            if (displayName.length() > 12) displayName = displayName.substring(0, 10) + "...";
            
            TextView nameText = new TextView(this);
            nameText.setText(displayName + (data.isVideo ? " 🎬" : ""));
            nameText.setTextColor(Color.WHITE);
            nameText.setTextSize(11);
            nameText.setTypeface(null, android.graphics.Typeface.BOLD);
            nameText.setPadding(0, 6, 0, 0);
            nameText.setGravity(Gravity.CENTER);
            card.addView(nameText);
            
            LinearLayout actionLayout = new LinearLayout(this);
            actionLayout.setOrientation(LinearLayout.HORIZONTAL);
            actionLayout.setGravity(Gravity.CENTER);
            actionLayout.setPadding(0, 4, 0, 0);
            
            Button circleBtn = createSmallActionButton("⭕", "#2196F3");
            circleBtn.setOnClickListener(v -> {
                loadCharacterToFloat(data);
                hideMainOverlay();
                createMainCircle();
            });
            
            Button screenBtn = createSmallActionButton("🖥", "#FF9800");
            screenBtn.setOnClickListener(v -> {
                if (data.isVideo) {
                    // Загружаем видео с хромакеем
                    loadChromaVideo(data);
                } else {
                    Toast.makeText(this, "Это не видео", Toast.LENGTH_SHORT).show();
                }
                hideMainOverlay();
                createMainCircle();
            });
            
            Button deleteBtn = createSmallActionButton("🗑", "#CC0000");
            deleteBtn.setOnClickListener(v -> {
                characters.remove(index);
                saveCharacters();
                if (isMainOverlayVisible) updateCharactersGrid();
            });
            
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(36, 36);
            btnParams.setMargins(2, 0, 2, 0);
            actionLayout.addView(circleBtn, btnParams);
            actionLayout.addView(screenBtn, btnParams);
            actionLayout.addView(deleteBtn, btnParams);
            
            card.addView(actionLayout);
            return card;
        } catch (Exception e) {
            Log.e(TAG, "createSmallCharacterCard error", e);
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
            Log.e(TAG, "updateContent error", e);
        }
    }

    private void hideMainOverlay() {
        try {
            if (mainOverlay != null && windowManager != null && isMainOverlayVisible) {
                if (webView != null) {
                    currentWebViewUrl = webView.getUrl();
                    saveCharacters();
                }
                windowManager.removeView(mainOverlay);
                mainOverlay = null;
                isMainOverlayVisible = false;
            }
        } catch (Exception e) {
            Log.e(TAG, "hideMainOverlay error", e);
        }
    }

    // ==================== WEBVIEW ОВЕРЛЕЙ ====================

    private void showWebOverlay() {
        try {
            if (windowManager == null) {
                windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
                if (windowManager == null) return;
            }
            
            if (webOverlayContainer != null) {
                Toast.makeText(this, "WebView оверлей уже активен", Toast.LENGTH_SHORT).show();
                return;
            }
            
            webOverlayContainer = new FrameLayout(this);
            webOverlayContainer.setBackgroundColor(Color.TRANSPARENT);
            
            webOverlayView = new WebView(this);
            WebSettings ws = webOverlayView.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setMediaPlaybackRequiresUserGesture(false);
            ws.setDomStorageEnabled(true);
            ws.setAllowFileAccess(true);
            ws.setAllowContentAccess(true);
            ws.setUseWideViewPort(true);
            ws.setLoadWithOverviewMode(true);
            ws.setJavaScriptCanOpenWindowsAutomatically(true);
            ws.setCacheMode(WebSettings.LOAD_DEFAULT);
            
            webOverlayView.setClickable(true);
            webOverlayView.setFocusable(true);
            webOverlayView.setFocusableInTouchMode(true);
            webOverlayView.setBackgroundColor(Color.TRANSPARENT);
            
            webOverlayView.loadUrl(URL_WEB_OVERLAY);
            
            FrameLayout.LayoutParams webParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT);
            webOverlayContainer.addView(webOverlayView, webParams);
            
            webOverlayParams = new WindowManager.LayoutParams(
                    600, 500,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            webOverlayParams.gravity = Gravity.CENTER;
            
            windowManager.addView(webOverlayContainer, webOverlayParams);
            isWebOverlayActive = true;
        } catch (Exception e) {
            Log.e(TAG, "showWebOverlay error", e);
        }
    }

    private void removeWebOverlay() {
        try {
            if (webOverlayView != null) {
                webOverlayView.loadUrl("about:blank");
                webOverlayView.clearHistory();
                webOverlayView.clearCache(true);
                webOverlayView.destroy();
                webOverlayView = null;
            }
            
            if (webOverlayContainer != null && windowManager != null) {
                windowManager.removeView(webOverlayContainer);
                webOverlayContainer = null;
            }
            
            isWebOverlayActive = false;
        } catch (Exception e) {
            Log.e(TAG, "removeWebOverlay error", e);
        }
    }

    // ==================== ХРОМАКЕЙ ВИДЕО С OPENGL ====================

    private void showAddChromaVideoDialog() {
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
            bg.setStroke(3, Color.parseColor("#00CC00"));
            dialogLayout.setBackground(bg);
            
            TextView title = new TextView(this);
            title.setText("🎬 ХРОМАКЕЙ ВИДЕО");
            title.setTextColor(Color.parseColor("#00CC00"));
            title.setTextSize(22);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, 0, 0, 20);
            dialogLayout.addView(title);
            
            TextView desc = new TextView(this);
            desc.setText("Загрузите видео с зеленым фоном\n(зеленый фон будет удален в реальном времени)");
            desc.setTextColor(Color.parseColor("#888888"));
            desc.setTextSize(14);
            desc.setGravity(Gravity.CENTER);
            desc.setPadding(0, 0, 0, 20);
            dialogLayout.addView(desc);
            
            TextView nameLabel = new TextView(this);
            nameLabel.setText("ИМЯ ПЕРСОНАЖА");
            nameLabel.setTextColor(Color.parseColor("#888888"));
            nameLabel.setTextSize(12);
            nameLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            nameLabel.setPadding(0, 0, 0, 8);
            dialogLayout.addView(nameLabel);
            
            nameInput = new EditText(this);
            nameInput.setHint("Введите имя персонажа");
            nameInput.setHintTextColor(Color.parseColor("#555555"));
            nameInput.setTextColor(Color.WHITE);
            nameInput.setTextSize(16);
            nameInput.setBackgroundColor(Color.parseColor("#0A0000"));
            nameInput.setPadding(20, 16, 20, 16);
            
            GradientDrawable inputBg = new GradientDrawable();
            inputBg.setCornerRadius(14);
            inputBg.setColor(Color.parseColor("#0A0000"));
            inputBg.setStroke(2, Color.parseColor("#00CC00"));
            nameInput.setBackground(inputBg);
            nameInput.setInputType(InputType.TYPE_CLASS_TEXT);
            nameInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
            
            LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            inputParams.setMargins(0, 0, 0, 20);
            nameInput.setLayoutParams(inputParams);
            dialogLayout.addView(nameInput);
            
            // Ползунок чувствительности
            LinearLayout toleranceLayout = new LinearLayout(this);
            toleranceLayout.setOrientation(LinearLayout.HORIZONTAL);
            toleranceLayout.setGravity(Gravity.CENTER);
            toleranceLayout.setPadding(0, 0, 0, 20);
            
            TextView toleranceLabel = new TextView(this);
            toleranceLabel.setText("Чувствительность: ");
            toleranceLabel.setTextColor(Color.WHITE);
            toleranceLabel.setTextSize(14);
            toleranceLayout.addView(toleranceLabel);
            
            final SeekBar toleranceSeekBar = new SeekBar(this);
            toleranceSeekBar.setMax(100);
            toleranceSeekBar.setProgress(chromaTolerance);
            toleranceSeekBar.setMinWidth(150);
            toleranceLayout.addView(toleranceSeekBar);
            
            final TextView toleranceValue = new TextView(this);
            toleranceValue.setText(String.valueOf(chromaTolerance));
            toleranceValue.setTextColor(Color.WHITE);
            toleranceValue.setTextSize(14);
            toleranceValue.setPadding(8, 0, 0, 0);
            toleranceLayout.addView(toleranceValue);
            
            toleranceSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    toleranceValue.setText(String.valueOf(progress));
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            
            dialogLayout.addView(toleranceLayout);
            
            Button addBtn = new Button(this);
            addBtn.setText("📹 ВЫБРАТЬ ВИДЕО");
            addBtn.setTextColor(Color.WHITE);
            addBtn.setTextSize(14);
            addBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable addBg2 = new GradientDrawable();
            addBg2.setCornerRadius(20);
            addBg2.setColor(Color.parseColor("#00CC00"));
            addBtn.setBackground(addBg2);
            addBtn.setPadding(32, 18, 32, 18);
            addBtn.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            addBtn.setOnClickListener(v -> {
                String name = nameInput.getText().toString().trim();
                tempCharacterName = name.isEmpty() ? "Хромакей" : name;
                chromaTolerance = toleranceSeekBar.getProgress();
                saveCharacters();
                if (windowManager != null) windowManager.removeView(dialogOverlay);
                openVideoPickerForChroma();
            });
            dialogLayout.addView(addBtn);
            
            Button cancelBtn = new Button(this);
            cancelBtn.setText("ОТМЕНА");
            cancelBtn.setTextColor(Color.WHITE);
            cancelBtn.setTextSize(14);
            cancelBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable cancelBg = new GradientDrawable();
            cancelBg.setCornerRadius(20);
            cancelBg.setColor(Color.parseColor("#2A0000"));
            cancelBg.setStroke(2, Color.parseColor("#00CC00"));
            cancelBtn.setBackground(cancelBg);
            cancelBtn.setPadding(32, 18, 32, 18);
            cancelBtn.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            cancelBtn.setOnClickListener(v -> {
                if (windowManager != null) windowManager.removeView(dialogOverlay);
            });
            
            LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cancelParams.setMargins(0, 8, 0, 0);
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
            Log.e(TAG, "showAddChromaVideoDialog error", e);
        }
    }

    private void openVideoPickerForChroma() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_VIDEO);
    }

    // Загрузка видео с хромакеем с использованием OpenGL
    private void loadChromaVideo(CharacterData data) {
        try {
            if (windowManager == null) return;
            
            // Удаляем предыдущее видео
            removeChromaVideo();
            
            currentChromaVideoPath = data.path;
            
            // Создаем контейнер
            chromaContainer = new FrameLayout(this);
            chromaContainer.setBackgroundColor(Color.TRANSPARENT);
            
            // Создаем GLSurfaceView для рендеринга с хромакеем
            chromaGLSurfaceView = new GLSurfaceView(this);
            chromaGLSurfaceView.setEGLContextClientVersion(2);
            chromaGLSurfaceView.setZOrderOnTop(true);
            chromaGLSurfaceView.getHolder().setFormat(PixelFormat.TRANSPARENT);
            
            // Создаем рендерер
            chromaRenderer = new ChromaRenderer();
            chromaRenderer.setTolerance(chromaTolerance);
            chromaGLSurfaceView.setRenderer(chromaRenderer);
            chromaGLSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
            
            FrameLayout.LayoutParams glParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT);
            chromaContainer.addView(chromaGLSurfaceView, glParams);
            
            // Добавляем контролы
            addChromaControls(chromaContainer);
            
            // Параметры окна
            chromaParams = new WindowManager.LayoutParams(
                    500, 500,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            chromaParams.gravity = Gravity.CENTER;
            
            windowManager.addView(chromaContainer, chromaParams);
            isChromaActive = true;
            isChromaFixed = false;
            
            // Запускаем воспроизведение видео
            startChromaVideoPlayback(data.path);
            
            Toast.makeText(this, "🎬 Хромакей видео загружено", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "loadChromaVideo error", e);
            Toast.makeText(this, "Ошибка загрузки: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void startChromaVideoPlayback(String path) {
        try {
            if (chromaMediaPlayer != null) {
                chromaMediaPlayer.release();
                chromaMediaPlayer = null;
            }
            
            chromaMediaPlayer = new MediaPlayer();
            chromaMediaPlayer.setDataSource(path);
            chromaMediaPlayer.setLooping(true);
            chromaMediaPlayer.setVolume(0, 0);
            
            // Создаем Surface для рендеринга
            if (chromaRenderer != null) {
                SurfaceTexture surfaceTexture = chromaRenderer.getSurfaceTexture();
                if (surfaceTexture != null) {
                    chromaSurface = new Surface(surfaceTexture);
                    chromaMediaPlayer.setSurface(chromaSurface);
                }
            }
            
            chromaMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.start();
                    Log.d(TAG, "Видео воспроизводится с хромакеем");
                }
            });
            
            chromaMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
                    return false;
                }
            });
            
            chromaMediaPlayer.prepareAsync();
            
        } catch (Exception e) {
            Log.e(TAG, "startChromaVideoPlayback error", e);
        }
    }

    // ==================== OPENGL РЕНДЕРЕР ДЛЯ ХРОМАКЕЯ ====================

    private static class ChromaRenderer implements GLSurfaceView.Renderer {
        
        private SurfaceTexture surfaceTexture;
        private int textureId = -1;
        private float tolerance = 40.0f;
        private final float[] mMVPMatrix = new float[16];
        private final float[] mSTMatrix = new float[16];
        
        // Координаты вершин (полный экран)
        private final float[] squareVertices = {
            -1.0f, -1.0f,  // 0 - левый нижний
             1.0f, -1.0f,  // 1 - правый нижний
            -1.0f,  1.0f,  // 2 - левый верхний
             1.0f,  1.0f   // 3 - правый верхний
        };
        
        // Координаты текстуры
        private final float[] textureVertices = {
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f
        };
        
        private int programHandle;
        private int positionHandle;
        private int textureCoordHandle;
        private int textureHandle;
        private int stMatrixHandle;
        private int toleranceHandle;
        private int mvpmMatrixHandle;
        
        private static final String VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;\n" +
            "uniform mat4 uSTMatrix;\n" +
            "attribute vec4 aPosition;\n" +
            "attribute vec4 aTextureCoord;\n" +
            "varying vec2 vTextureCoord;\n" +
            "void main() {\n" +
            "  gl_Position = uMVPMatrix * aPosition;\n" +
            "  vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" +
            "}\n";
        
        private static final String FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "uniform float uTolerance;\n" +
            "varying vec2 vTextureCoord;\n" +
            "void main() {\n" +
            "  vec4 color = texture2D(sTexture, vTextureCoord);\n" +
            "  // Хромакей - удаляем зеленый фон\n" +
            "  float greenThreshold = 0.2 + (uTolerance / 200.0);\n" +
            "  float redBlueThreshold = 0.2 + (uTolerance / 300.0);\n" +
            "  float r = color.r;\n" +
            "  float g = color.g;\n" +
            "  float b = color.b;\n" +
            "  // Проверяем зеленый цвет\n" +
            "  if (g > greenThreshold && g > r + redBlueThreshold && g > b + redBlueThreshold) {\n" +
            "    gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);\n" +
            "  } else {\n" +
            "    gl_FragColor = color;\n" +
            "  }\n" +
            "}\n";
        
        public SurfaceTexture getSurfaceTexture() {
            return surfaceTexture;
        }
        
        public void setTolerance(float tolerance) {
            this.tolerance = tolerance;
        }
        
        @Override
        public void onSurfaceCreated(GL10 unused, EGLConfig config) {
            Matrix.setIdentityM(mSTMatrix, 0);
            
            // Создаем текстуру для видео
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            textureId = textures[0];
            
            surfaceTexture = new SurfaceTexture(textureId);
            surfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
                @Override
                public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                    // Кадр готов к рендерингу
                }
            });
            
            // Создаем шейдеры
            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
            int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
            
            programHandle = GLES20.glCreateProgram();
            GLES20.glAttachShader(programHandle, vertexShader);
            GLES20.glAttachShader(programHandle, fragmentShader);
            GLES20.glLinkProgram(programHandle);
            
            // Получаем ссылки на атрибуты
            positionHandle = GLES20.glGetAttribLocation(programHandle, "aPosition");
            textureCoordHandle = GLES20.glGetAttribLocation(programHandle, "aTextureCoord");
            textureHandle = GLES20.glGetUniformLocation(programHandle, "sTexture");
            stMatrixHandle = GLES20.glGetUniformLocation(programHandle, "uSTMatrix");
            toleranceHandle = GLES20.glGetUniformLocation(programHandle, "uTolerance");
            mvpmMatrixHandle = GLES20.glGetUniformLocation(programHandle, "uMVPMatrix");
            
            GLES20.glUseProgram(programHandle);
            GLES20.glUniform1i(textureHandle, 0);
            GLES20.glUniformMatrix4fv(stMatrixHandle, 1, false, mSTMatrix, 0);
            GLES20.glUniform1f(toleranceHandle, tolerance);
            
            // Матрица проекции (простая ортогональная)
            Matrix.orthoM(mMVPMatrix, 0, -1, 1, -1, 1, -1, 1);
            GLES20.glUniformMatrix4fv(mvpmMatrixHandle, 1, false, mMVPMatrix, 0);
        }
        
        @Override
        public void onSurfaceChanged(GL10 unused, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
        }
        
        @Override
        public void onDrawFrame(GL10 unused) {
            // Обновляем текстуру
            if (surfaceTexture != null) {
                surfaceTexture.updateTexImage();
                surfaceTexture.getTransformMatrix(mSTMatrix);
            }
            
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            
            GLES20.glUseProgram(programHandle);
            
            // Вершины
            FloatBuffer vertexBuffer = createFloatBuffer(squareVertices);
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
            GLES20.glEnableVertexAttribArray(positionHandle);
            
            // Координаты текстуры
            FloatBuffer textureBuffer = createFloatBuffer(textureVertices);
            GLES20.glVertexAttribPointer(textureCoordHandle, 2, GLES20.GL_FLOAT, false, 0, textureBuffer);
            GLES20.glEnableVertexAttribArray(textureCoordHandle);
            
            // Передаем матрицу текстуры
            GLES20.glUniformMatrix4fv(stMatrixHandle, 1, false, mSTMatrix, 0);
            
            // Передаем чувствительность
            GLES20.glUniform1f(toleranceHandle, tolerance);
            
            // Рисуем
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        }
        
        private int loadShader(int shaderType, String source) {
            int shader = GLES20.glCreateShader(shaderType);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            return shader;
        }
        
        private FloatBuffer createFloatBuffer(float[] arr) {
            ByteBuffer bb = ByteBuffer.allocateDirect(arr.length * 4);
            bb.order(ByteOrder.nativeOrder());
            FloatBuffer fb = bb.asFloatBuffer();
            fb.put(arr);
            fb.position(0);
            return fb;
        }
    }

    // ==================== КОНТРОЛЫ ХРОМАКЕЙ ВИДЕО ====================

    private void addChromaControls(FrameLayout container) {
        try {
            chromaFixButton = new ImageButton(this);
            chromaFixButton.setImageDrawable(createLockIcon(false));
            GradientDrawable fixBg = new GradientDrawable();
            fixBg.setShape(GradientDrawable.OVAL);
            fixBg.setColor(Color.parseColor("#00CC00"));
            fixBg.setStroke(2, Color.WHITE);
            chromaFixButton.setBackground(fixBg);
            chromaFixButton.setPadding(16, 16, 16, 16);
            
            FrameLayout.LayoutParams fixParams = new FrameLayout.LayoutParams(60, 60, Gravity.TOP | Gravity.END);
            fixParams.setMargins(0, 24, 24, 0);
            chromaFixButton.setLayoutParams(fixParams);
            chromaFixButton.setOnClickListener(v -> toggleChromaFix());
            
            chromaDeleteButton = new ImageButton(this);
            chromaDeleteButton.setImageDrawable(createDeleteIcon());
            GradientDrawable deleteBg = new GradientDrawable();
            deleteBg.setShape(GradientDrawable.OVAL);
            deleteBg.setColor(Color.RED);
            deleteBg.setStroke(2, Color.WHITE);
            chromaDeleteButton.setBackground(deleteBg);
            chromaDeleteButton.setPadding(16, 16, 16, 16);
            
            FrameLayout.LayoutParams deleteParams = new FrameLayout.LayoutParams(60, 60, Gravity.TOP | Gravity.START);
            deleteParams.setMargins(24, 24, 0, 0);
            chromaDeleteButton.setLayoutParams(deleteParams);
            chromaDeleteButton.setOnClickListener(v -> {
                removeChromaVideo();
                Toast.makeText(this, "Хромакей видео удалено", Toast.LENGTH_SHORT).show();
            });
            
            chromaBackButton = new ImageButton(this);
            chromaBackButton.setImageDrawable(createCloseIcon());
            GradientDrawable backBg = new GradientDrawable();
            backBg.setShape(GradientDrawable.OVAL);
            backBg.setColor(Color.parseColor("#9C27B0"));
            backBg.setStroke(2, Color.WHITE);
            chromaBackButton.setBackground(backBg);
            chromaBackButton.setPadding(16, 16, 16, 16);
            
            FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(60, 60, Gravity.BOTTOM | Gravity.CENTER);
            backParams.setMargins(0, 0, 0, 24);
            chromaBackButton.setLayoutParams(backParams);
            chromaBackButton.setOnClickListener(v -> {
                removeChromaVideo();
                Toast.makeText(this, "Хромакей видео закрыто", Toast.LENGTH_SHORT).show();
            });
            
            // Панель управления
            chromaControls = new LinearLayout(this);
            chromaControls.setOrientation(LinearLayout.VERTICAL);
            chromaControls.setGravity(Gravity.CENTER);
            chromaControls.setBackgroundColor(Color.parseColor("#AA000000"));
            chromaControls.setPadding(16, 12, 16, 12);
            
            TextView titleText = new TextView(this);
            titleText.setText("🎬 ХРОМАКЕЙ");
            titleText.setTextColor(Color.parseColor("#00CC00"));
            titleText.setGravity(Gravity.CENTER);
            titleText.setTextSize(14);
            titleText.setTypeface(null, android.graphics.Typeface.BOLD);
            titleText.setPadding(0, 0, 0, 8);
            chromaControls.addView(titleText);
            
            // X контрол
            LinearLayout xLayout = new LinearLayout(this);
            xLayout.setOrientation(LinearLayout.HORIZONTAL);
            xLayout.setGravity(Gravity.CENTER);
            
            chromaSizeXLabel = new TextView(this);
            chromaSizeXLabel.setText("Ш");
            chromaSizeXLabel.setTextColor(Color.WHITE);
            chromaSizeXLabel.setPadding(0, 0, 8, 0);
            
            chromaSizeXInput = new EditText(this);
            chromaSizeXInput.setText("500");
            chromaSizeXInput.setInputType(InputType.TYPE_CLASS_NUMBER);
            chromaSizeXInput.setTextColor(Color.WHITE);
            chromaSizeXInput.setBackgroundColor(Color.parseColor("#333333"));
            chromaSizeXInput.setPadding(8, 4, 8, 4);
            chromaSizeXInput.setWidth(60);
            
            chromaXSeekBar = new SeekBar(this);
            chromaXSeekBar.setMax(1000);
            chromaXSeekBar.setProgress(500);
            
            xLayout.addView(chromaSizeXLabel);
            xLayout.addView(chromaSizeXInput);
            xLayout.addView(chromaXSeekBar);
            chromaControls.addView(xLayout);
            
            // Y контрол
            LinearLayout yLayout = new LinearLayout(this);
            yLayout.setOrientation(LinearLayout.HORIZONTAL);
            yLayout.setGravity(Gravity.CENTER);
            
            chromaSizeYLabel = new TextView(this);
            chromaSizeYLabel.setText("В");
            chromaSizeYLabel.setTextColor(Color.WHITE);
            chromaSizeYLabel.setPadding(0, 0, 8, 0);
            
            chromaSizeYInput = new EditText(this);
            chromaSizeYInput.setText("500");
            chromaSizeYInput.setInputType(InputType.TYPE_CLASS_NUMBER);
            chromaSizeYInput.setTextColor(Color.WHITE);
            chromaSizeYInput.setBackgroundColor(Color.parseColor("#333333"));
            chromaSizeYInput.setPadding(8, 4, 8, 4);
            chromaSizeYInput.setWidth(60);
            
            chromaYSeekBar = new SeekBar(this);
            chromaYSeekBar.setMax(1000);
            chromaYSeekBar.setProgress(500);
            
            yLayout.addView(chromaSizeYLabel);
            yLayout.addView(chromaSizeYInput);
            yLayout.addView(chromaYSeekBar);
            chromaControls.addView(yLayout);
            
            // Z контрол (прозрачность)
            LinearLayout zLayout = new LinearLayout(this);
            zLayout.setOrientation(LinearLayout.HORIZONTAL);
            zLayout.setGravity(Gravity.CENTER);
            
            chromaSizeZLabel = new TextView(this);
            chromaSizeZLabel.setText("α");
            chromaSizeZLabel.setTextColor(Color.WHITE);
            chromaSizeZLabel.setPadding(0, 0, 8, 0);
            
            chromaSizeZInput = new EditText(this);
            chromaSizeZInput.setText("100");
            chromaSizeZInput.setInputType(InputType.TYPE_CLASS_NUMBER);
            chromaSizeZInput.setTextColor(Color.WHITE);
            chromaSizeZInput.setBackgroundColor(Color.parseColor("#333333"));
            chromaSizeZInput.setPadding(8, 4, 8, 4);
            chromaSizeZInput.setWidth(60);
            
            chromaZSeekBar = new SeekBar(this);
            chromaZSeekBar.setMax(100);
            chromaZSeekBar.setProgress(100);
            
            zLayout.addView(chromaSizeZLabel);
            zLayout.addView(chromaSizeZInput);
            zLayout.addView(chromaZSeekBar);
            chromaControls.addView(zLayout);
            
            // Чувствительность хромакея
            LinearLayout toleranceLayout = new LinearLayout(this);
            toleranceLayout.setOrientation(LinearLayout.HORIZONTAL);
            toleranceLayout.setGravity(Gravity.CENTER);
            toleranceLayout.setPadding(0, 4, 0, 0);
            
            chromaToleranceLabel = new TextView(this);
            chromaToleranceLabel.setText("🔍 ");
            chromaToleranceLabel.setTextColor(Color.WHITE);
            chromaToleranceLabel.setTextSize(14);
            toleranceLayout.addView(chromaToleranceLabel);
            
            chromaToleranceSeekBar = new SeekBar(this);
            chromaToleranceSeekBar.setMax(100);
            chromaToleranceSeekBar.setProgress(chromaTolerance);
            toleranceLayout.addView(chromaToleranceSeekBar);
            
            final TextView toleranceValue = new TextView(this);
            toleranceValue.setText(String.valueOf(chromaTolerance));
            toleranceValue.setTextColor(Color.WHITE);
            toleranceValue.setTextSize(12);
            toleranceValue.setPadding(8, 0, 0, 0);
            toleranceLayout.addView(toleranceValue);
            
            chromaToleranceSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    toleranceValue.setText(String.valueOf(progress));
                    chromaTolerance = progress;
                    if (chromaRenderer != null) {
                        chromaRenderer.setTolerance(progress);
                    }
                    saveCharacters();
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            
            chromaControls.addView(toleranceLayout);
            
            // Слушатели для размеров
            chromaXSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && chromaParams != null) {
                        chromaParams.width = progress + 50;
                        chromaSizeXInput.setText(String.valueOf(progress + 50));
                        updateChromaSize();
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            
            chromaYSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && chromaParams != null) {
                        chromaParams.height = progress + 50;
                        chromaSizeYInput.setText(String.valueOf(progress + 50));
                        updateChromaSize();
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            
            chromaZSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        chromaSizeZInput.setText(String.valueOf(progress));
                        float alpha = progress / 100f;
                        if (chromaContainer != null) chromaContainer.setAlpha(alpha);
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            
            FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
            );
            controlsParams.setMargins(0, 0, 0, 100);
            chromaControls.setLayoutParams(controlsParams);
            
            container.addView(chromaFixButton);
            container.addView(chromaDeleteButton);
            container.addView(chromaBackButton);
            container.addView(chromaControls);
            
            // Обработка касаний для перемещения
            if (chromaGLSurfaceView != null) {
                chromaGLSurfaceView.setOnTouchListener((v, event) -> {
                    if (isChromaFixed) return false;
                    return handleChromaTouch(event);
                });
            }
            
        } catch (Exception e) {
            Log.e(TAG, "addChromaControls error", e);
        }
    }

    private boolean handleChromaTouch(MotionEvent event) {
        try {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastTouchX = event.getRawX();
                    lastTouchY = event.getRawY();
                    initialX = chromaParams.x;
                    initialY = chromaParams.y;
                    initialPinchDistance = 0;
                    return true;
                    
                case MotionEvent.ACTION_MOVE:
                    if (event.getPointerCount() == 2) {
                        float distance = getDistance(event);
                        if (initialPinchDistance == 0) {
                            initialPinchDistance = distance;
                        } else {
                            float scale = distance / initialPinchDistance;
                            int newWidth = (int)(chromaParams.width * scale);
                            int newHeight = (int)(chromaParams.height * scale);
                            if (newWidth > 100 && newHeight > 100 && newWidth < 1500 && newHeight < 1500) {
                                chromaParams.width = newWidth;
                                chromaParams.height = newHeight;
                                chromaSizeXInput.setText(String.valueOf(newWidth));
                                chromaSizeYInput.setText(String.valueOf(newHeight));
                                chromaXSeekBar.setProgress(Math.min(newWidth - 50, 1000));
                                chromaYSeekBar.setProgress(Math.min(newHeight - 50, 1000));
                                updateChromaSize();
                            }
                        }
                    } else {
                        float dx = event.getRawX() - lastTouchX;
                        float dy = event.getRawY() - lastTouchY;
                        chromaParams.x += (int) dx;
                        chromaParams.y += (int) dy;
                        lastTouchX = event.getRawX();
                        lastTouchY = event.getRawY();
                        if (windowManager != null) {
                            windowManager.updateViewLayout(chromaContainer, chromaParams);
                        }
                    }
                    return true;
                    
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    initialPinchDistance = 0;
                    return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "handleChromaTouch error", e);
        }
        return false;
    }

    private void toggleChromaFix() {
        try {
            isChromaFixed = !isChromaFixed;
            
            if (isChromaFixed) {
                Toast.makeText(this, "🔒 Хромакей закреплён", Toast.LENGTH_SHORT).show();
                chromaFixButton.setImageDrawable(createLockIcon(true));
                hideChromaControls();
                
                if (chromaParams != null && windowManager != null) {
                    chromaParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    windowManager.updateViewLayout(chromaContainer, chromaParams);
                }
            } else {
                Toast.makeText(this, "🔓 Хромакей разблокирован", Toast.LENGTH_SHORT).show();
                chromaFixButton.setImageDrawable(createLockIcon(false));
                showChromaControls();
                
                if (chromaParams != null && windowManager != null) {
                    chromaParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    windowManager.updateViewLayout(chromaContainer, chromaParams);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "toggleChromaFix error", e);
        }
    }

    private void hideChromaControls() {
        try {
            if (chromaControls != null) chromaControls.setVisibility(View.GONE);
            if (chromaDeleteButton != null) chromaDeleteButton.setVisibility(View.GONE);
            if (chromaBackButton != null) chromaBackButton.setVisibility(View.GONE);
            if (chromaFixButton != null) chromaFixButton.setVisibility(View.GONE);
        } catch (Exception e) {
            Log.e(TAG, "hideChromaControls error", e);
        }
    }

    private void showChromaControls() {
        try {
            if (chromaControls != null) chromaControls.setVisibility(View.VISIBLE);
            if (chromaDeleteButton != null) chromaDeleteButton.setVisibility(View.VISIBLE);
            if (chromaBackButton != null) chromaBackButton.setVisibility(View.VISIBLE);
            if (chromaFixButton != null) chromaFixButton.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            Log.e(TAG, "showChromaControls error", e);
        }
    }

    private void updateChromaSize() {
        try {
            if (windowManager != null && chromaContainer != null && chromaParams != null) {
                windowManager.updateViewLayout(chromaContainer, chromaParams);
            }
        } catch (Exception e) {
            Log.e(TAG, "updateChromaSize error", e);
        }
    }

    private void removeChromaVideo() {
        try {
            if (chromaMediaPlayer != null) {
                chromaMediaPlayer.stop();
                chromaMediaPlayer.release();
                chromaMediaPlayer = null;
            }
            
            if (chromaSurface != null) {
                chromaSurface.release();
                chromaSurface = null;
            }
            
            if (chromaRenderer != null) {
                chromaRenderer = null;
            }
            
            if (chromaGLSurfaceView != null) {
                chromaGLSurfaceView.onPause();
                chromaGLSurfaceView = null;
            }
            
            if (chromaContainer != null && windowManager != null) {
                windowManager.removeView(chromaContainer);
                chromaContainer = null;
            }
            
            isChromaActive = false;
            isChromaFixed = false;
            currentChromaVideoPath = null;
            
            Log.d(TAG, "Хромакей видео удалено");
        } catch (Exception e) {
            Log.e(TAG, "removeChromaVideo error", e);
        }
    }

    // ==================== ДОБАВЛЕНИЕ ПЕРСОНАЖА ====================

    private void showAddCharacterDialog(boolean isVideo) {
        try {
            this.isVideoMode = isVideo;
            
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
            title.setText(isVideo ? "🎬 НОВОЕ ВИДЕО" : "🖼 НОВЫЙ ПЕРСОНАЖ");
            title.setTextColor(Color.parseColor("#CC0000"));
            title.setTextSize(22);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, 0, 0, 20);
            dialogLayout.addView(title);
            
            TextView nameLabel = new TextView(this);
            nameLabel.setText("ИМЯ");
            nameLabel.setTextColor(Color.parseColor("#888888"));
            nameLabel.setTextSize(12);
            nameLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            nameLabel.setPadding(0, 0, 0, 8);
            dialogLayout.addView(nameLabel);
            
            nameInput = new EditText(this);
            nameInput.setHint(isVideo ? "Введите имя видео" : "Введите имя персонажа");
            nameInput.setHintTextColor(Color.parseColor("#555555"));
            nameInput.setTextColor(Color.WHITE);
            nameInput.setTextSize(16);
            nameInput.setBackgroundColor(Color.parseColor("#0A0000"));
            nameInput.setPadding(20, 16, 20, 16);
            
            GradientDrawable inputBg = new GradientDrawable();
            inputBg.setCornerRadius(14);
            inputBg.setColor(Color.parseColor("#0A0000"));
            inputBg.setStroke(2, Color.parseColor("#8B0000"));
            nameInput.setBackground(inputBg);
            nameInput.setInputType(InputType.TYPE_CLASS_TEXT);
            nameInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
            
            LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            inputParams.setMargins(0, 0, 0, 20);
            nameInput.setLayoutParams(inputParams);
            dialogLayout.addView(nameInput);
            
            Button addBtn = new Button(this);
            addBtn.setText(isVideo ? "🎬 ВЫБРАТЬ ВИДЕО" : "📷 ВЫБРАТЬ ИЗОБРАЖЕНИЕ");
            addBtn.setTextColor(Color.WHITE);
            addBtn.setTextSize(14);
            addBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable addBg2 = new GradientDrawable();
            addBg2.setCornerRadius(20);
            addBg2.setColor(Color.parseColor("#CC0000"));
            addBtn.setBackground(addBg2);
            addBtn.setPadding(32, 18, 32, 18);
            addBtn.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            addBtn.setOnClickListener(v -> {
                String name = nameInput.getText().toString().trim();
                tempCharacterName = name.isEmpty() ? "Без имени" : name;
                if (windowManager != null) windowManager.removeView(dialogOverlay);
                if (isVideo) {
                    openVideoPickerForCharacter();
                } else {
                    openGalleryForCharacter();
                }
            });
            dialogLayout.addView(addBtn);
            
            Button cancelBtn = new Button(this);
            cancelBtn.setText("ОТМЕНА");
            cancelBtn.setTextColor(Color.WHITE);
            cancelBtn.setTextSize(14);
            cancelBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable cancelBg = new GradientDrawable();
            cancelBg.setCornerRadius(20);
            cancelBg.setColor(Color.parseColor("#2A0000"));
            cancelBg.setStroke(2, Color.parseColor("#8B0000"));
            cancelBtn.setBackground(cancelBg);
            cancelBtn.setPadding(32, 18, 32, 18);
            cancelBtn.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            cancelBtn.setOnClickListener(v -> {
                if (windowManager != null) windowManager.removeView(dialogOverlay);
            });
            
            LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cancelParams.setMargins(0, 8, 0, 0);
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
            Log.e(TAG, "showAddCharacterDialog error", e);
        }
    }

    private void openGalleryForCharacter() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_GALLERY);
    }

    private void openVideoPickerForCharacter() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_VIDEO);
    }

    // ==================== ЗАГРУЗКА ПЕРСОНАЖА В КРУЖОК ====================

    private void loadCharacterToFloat(CharacterData data) {
        try {
            if (data.isVideo) {
                Toast.makeText(this, "Видео в кружке пока не поддерживается", Toast.LENGTH_SHORT).show();
                return;
            }
            
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
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), 
                        Uri.fromFile(new File(data.path)));
                imageButton.setImageBitmap(bitmap);
            } catch (Exception e) {
                Log.e(TAG, "loadCharacterToFloat error", e);
            }
            imageButton.setBackgroundColor(Color.TRANSPARENT);
            imageButton.setPadding(5, 5, 5, 5);
            imageButton.setScaleType(ImageButton.ScaleType.FIT_CENTER);
            imageButton.setClickable(false);
            imageButton.setFocusable(false);
            
            mainCircleContainer.addView(imageButton, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            
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
            
            mainCircleContainer.setOnTouchListener(createTouchListener());
            
            if (windowManager == null) {
                windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            }
            
            if (windowManager != null) {
                windowManager.addView(mainCircleContainer, mainCircleParams);
                Toast.makeText(this, "Персонаж загружен в оверлей", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "loadCharacterToFloat error", e);
            Toast.makeText(this, "Ошибка загрузки", Toast.LENGTH_SHORT).show();
        }
    }

    private View.OnTouchListener createTouchListener() {
        return (v, event) -> {
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
                Log.e(TAG, "Touch error", e);
            }
            return false;
        };
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    private int getOverlayFlag() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;
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
        isAppInForeground = true;
        if (mainCircleContainer != null && !isMainOverlayVisible) {
            mainCircleContainer.setVisibility(View.VISIBLE);
        }
        if (isChromaActive && chromaGLSurfaceView != null) {
            chromaGLSurfaceView.onResume();
        }
        if (isMainOverlayVisible) {
            updateContent();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        isAppInForeground = false;
        hideMainOverlay();
        if (isChromaActive && chromaGLSurfaceView != null) {
            chromaGLSurfaceView.onPause();
        }
        createMainCircle();
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
            removeChromaVideo();
            removeWebOverlay();
            removeMainCircle();
            hideMainOverlay();
            
            if (webView != null) {
                webView.loadUrl("about:blank");
                webView.clearHistory();
                webView.clearCache(true);
                webView.clearFormData();
                webView.destroy();
                webView = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "onDestroy error", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            switch (code) {
                case REQUEST_GENERAL_PERMISSIONS: Toast.makeText(this, "Разрешения получены", Toast.LENGTH_SHORT).show(); break;
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
                    String path = saveImageToStorage(imageUri);
                    if (path != null) {
                        characters.add(new CharacterData(tempCharacterName, path, false));
                        saveCharacters();
                        Toast.makeText(this, "Персонаж сохранён", Toast.LENGTH_SHORT).show();
                        if (isMainOverlayVisible) updateContent();
                    }
                }
            }
            
            if (requestCode == REQUEST_VIDEO && resultCode == RESULT_OK && data != null) {
                Uri videoUri = data.getData();
                if (videoUri != null) {
                    String path = saveVideoToStorage(videoUri);
                    if (path != null) {
                        characters.add(new CharacterData(tempCharacterName, path, true));
                        saveCharacters();
                        Toast.makeText(this, "Видео сохранено", Toast.LENGTH_SHORT).show();
                        if (isMainOverlayVisible) updateContent();
                        
                        // Сразу загружаем видео с хромакеем
                        CharacterData newData = characters.get(characters.size() - 1);
                        loadChromaVideo(newData);
                        hideMainOverlay();
                        createMainCircle();
                    }
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
            Log.e(TAG, "onActivityResult error", e);
            Toast.makeText(this, "Ошибка загрузки: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String saveImageToStorage(Uri imageUri) {
        try {
            File dir = new File(getExternalFilesDir(null), "characters");
            if (!dir.exists()) dir.mkdirs();
            
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File file = new File(dir, "CHAR_" + timeStamp + ".png");
            
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
            FileOutputStream out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.close();
            
            return file.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, "saveImageToStorage error", e);
            return null;
        }
    }
  }
