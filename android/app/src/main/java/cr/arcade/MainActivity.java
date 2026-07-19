package com.cr.arcade;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.InputType;
import android.util.Base64;
import android.util.Log;
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
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.BridgeActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends BridgeActivity {

    private static final int REQUEST_MICROPHONE = 100;
    private static final int REQUEST_CAMERA = 102;
    private static final int REQUEST_OVERLAY_PERMISSION = 101;
    private static final int REQUEST_GALLERY = 103;
    private static final int REQUEST_STORAGE = 104;
    private static final int REQUEST_VIDEO = 105;
    private static final int REQUEST_NOTIFICATION = 106;
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

    // Компоненты персонажей и видео
    private FrameLayout characterContainer;
    private ImageView characterView;
    private VideoView videoView;
    private WindowManager.LayoutParams characterParams;
    private Bitmap currentCharacterBitmap;
    private boolean isCharacterFixed = false;
    private boolean isCharacterModeActive = false;
    private String currentVideoPath = null;
    private MediaPlayer currentMediaPlayer = null;
    private boolean isVideoPlaying = false;
    
    // WebView оверлей
    private FrameLayout webOverlayContainer;
    private WebView webOverlayView;
    private WindowManager.LayoutParams webOverlayParams;
    private boolean isWebOverlayActive = false;
    private boolean isWebOverlayFixed = false;
    
    private float lastTouchX, lastTouchY;
    private float initialPinchDistance = 0;
    
    private FrameLayout menuContainer;
    private FrameLayout characterListContainer;
    
    private LinearLayout controlsLayout;
    private ImageButton fixButton;
    private ImageButton deleteButton;
    private ImageButton backButton;
    private EditText sizeXInput, sizeYInput, sizeZInput;
    private TextView sizeXLabel, sizeYLabel, sizeZLabel;
    
    // Контролы для WebView оверлея
    private LinearLayout webOverlayControls;
    private ImageButton webFixButton;
    private ImageButton webDeleteButton;
    private ImageButton webBackButton;
    private EditText webSizeXInput, webSizeYInput, webSizeZInput;
    private TextView webSizeXLabel, webSizeYLabel, webSizeZLabel;
    private SeekBar webXSeekBar, webYSeekBar, webZSeekBar;
    
    // Система персонажей
    private ArrayList<CharacterData> characters = new ArrayList<>();
    private SharedPreferences prefs;
    private String tempCharacterName = "";
    
    private boolean isCharacterListOpen = false;
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
    private boolean isWebViewLoading = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            prefs = getSharedPreferences("characters", MODE_PRIVATE);
            overlayAlpha = prefs.getInt("overlay_alpha", 255);
            overlaySize = prefs.getInt("overlay_size", 136);
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
                            Log.e(TAG, "Permission request error", e);
                        }
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Bridge error", e);
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
            this.width = 300;
            this.height = 300;
        }
        
        CharacterData(JSONObject json) throws Exception {
            this.name = json.getString("name");
            this.path = json.getString("path");
            this.isVideo = json.optBoolean("isVideo", false);
            this.timestamp = json.optLong("timestamp", System.currentTimeMillis());
            this.width = json.optInt("width", 300);
            this.height = json.optInt("height", 300);
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
                .putString("webview_url", currentWebViewUrl)
                .apply();
        } catch (Exception e) {
            Log.e(TAG, "saveCharacters error", e);
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
            Log.e(TAG, "saveImageToStorage error", e);
            return null;
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
            Log.e(TAG, "updateOverlayAppearance error", e);
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
                    removeCharacter();
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
                    isWebViewLoading = false;
                }
                
                @Override
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    super.onPageStarted(view, url, favicon);
                    isWebViewLoading = true;
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
                    Bitmap processed = removeGreenScreen(bitmap, 40);
                    ImageView thumbView = new ImageView(this);
                    thumbView.setImageBitmap(processed);
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
                loadCharacterToScreen(data);
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

    // ==================== WEBVIEW ОВЕРЛЕЙ С РАБОЧИМИ КНОПКАМИ ====================

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
            
            // Создаем основной контейнер
            webOverlayContainer = new FrameLayout(this);
            webOverlayContainer.setBackgroundColor(Color.TRANSPARENT);
            webOverlayContainer.setClickable(true);
            webOverlayContainer.setFocusable(true);
            
            // Создаем WebView с прозрачным фоном
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
            ws.setAllowUniversalAccessFromFileURLs(true);
            ws.setAllowFileAccessFromFileURLs(true);
            ws.setBuiltInZoomControls(true);
            ws.setDisplayZoomControls(false);
            
            // WebView должен быть кликабельным для передачи кликов на сайт
            webOverlayView.setClickable(true);
            webOverlayView.setFocusable(true);
            webOverlayView.setFocusableInTouchMode(true);
            webOverlayView.setEnabled(true);
            webOverlayView.setBackgroundColor(Color.TRANSPARENT);
            webOverlayView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null);
            
            webOverlayView.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onProgressChanged(WebView view, int newProgress) {
                    super.onProgressChanged(view, newProgress);
                    Log.d(TAG, "WebView загрузка: " + newProgress + "%");
                }
            });
            
            webOverlayView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    Log.d(TAG, "WebView оверлей загружен: " + url);
                    injectChromakeyCSS(view);
                }
                
                @Override
                public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                    super.onReceivedError(view, errorCode, description, failingUrl);
                    Log.e(TAG, "WebView ошибка: " + errorCode + " - " + description);
                }
            });
            
            // Загружаем URL
            webOverlayView.loadUrl(URL_WEB_OVERLAY);
            Log.d(TAG, "Загрузка WebView оверлея: " + URL_WEB_OVERLAY);
            
            FrameLayout.LayoutParams webParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT);
            webOverlayContainer.addView(webOverlayView, webParams);
            
            // Добавляем контролы
            addWebOverlayControls(webOverlayContainer);
            
            // Параметры окна - НЕ используем FLAG_NOT_FOCUSABLE чтобы WebView мог получать фокус
            webOverlayParams = new WindowManager.LayoutParams(
                    600, 500,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            webOverlayParams.gravity = Gravity.CENTER;
            
            windowManager.addView(webOverlayContainer, webOverlayParams);
            isWebOverlayActive = true;
            isWebOverlayFixed = false;
            
            Toast.makeText(this, "WebView оверлей открыт", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "showWebOverlay error", e);
            Toast.makeText(this, "Ошибка открытия: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    // Метод для инъекции CSS удаляющего зеленый фон
    private void injectChromakeyCSS(WebView view) {
        String css = 
            "javascript:(function() {" +
            "  var style = document.createElement('style');" +
            "  style.innerHTML = '" +
            "    * { background: transparent !important; }" +
            "    body { background: transparent !important; }" +
            "    html { background: transparent !important; }" +
            "    div, section, article, header, footer, main, nav, aside {" +
            "      background: transparent !important;" +
            "    }" +
            "    img, video, iframe {" +
            "      background: transparent !important;" +
            "    }" +
            "    .green-bg, .bg-green, [style*='background-color: green']," +
            "    [style*='background: green'], [style*='background:#00ff00']," +
            "    [style*='background: #00ff00'], [style*='background:#00FF00'] {" +
            "      background: transparent !important;" +
            "    }" +
            "  ';" +
            "  document.head.appendChild(style);" +
            "})();";
        
        view.loadUrl(css);
        Log.d(TAG, "CSS для удаления зеленого фона инжектирован");
    }

    private void addWebOverlayControls(FrameLayout container) {
        try {
            // Кнопка фиксации
            webFixButton = new ImageButton(this);
            webFixButton.setImageDrawable(createLockIcon(false));
            GradientDrawable fixBg = new GradientDrawable();
            fixBg.setShape(GradientDrawable.OVAL);
            fixBg.setColor(Color.parseColor("#FF6B00"));
            fixBg.setStroke(2, Color.WHITE);
            webFixButton.setBackground(fixBg);
            webFixButton.setPadding(16, 16, 16, 16);
            
            FrameLayout.LayoutParams fixParams = new FrameLayout.LayoutParams(60, 60, Gravity.TOP | Gravity.END);
            fixParams.setMargins(0, 24, 24, 0);
            webFixButton.setLayoutParams(fixParams);
            webFixButton.setOnClickListener(v -> toggleWebOverlayFix());
            
            // Кнопка удаления
            webDeleteButton = new ImageButton(this);
            webDeleteButton.setImageDrawable(createDeleteIcon());
            GradientDrawable deleteBg = new GradientDrawable();
            deleteBg.setShape(GradientDrawable.OVAL);
            deleteBg.setColor(Color.RED);
            deleteBg.setStroke(2, Color.WHITE);
            webDeleteButton.setBackground(deleteBg);
            webDeleteButton.setPadding(16, 16, 16, 16);
            
            FrameLayout.LayoutParams deleteParams = new FrameLayout.LayoutParams(60, 60, Gravity.TOP | Gravity.START);
            deleteParams.setMargins(24, 24, 0, 0);
            webDeleteButton.setLayoutParams(deleteParams);
            webDeleteButton.setOnClickListener(v -> {
                removeWebOverlay();
                Toast.makeText(this, "WebView оверлей удалён", Toast.LENGTH_SHORT).show();
            });
            
            // Кнопка закрытия
            webBackButton = new ImageButton(this);
            webBackButton.setImageDrawable(createCloseIcon());
            GradientDrawable backBg = new GradientDrawable();
            backBg.setShape(GradientDrawable.OVAL);
            backBg.setColor(Color.parseColor("#9C27B0"));
            backBg.setStroke(2, Color.WHITE);
            webBackButton.setBackground(backBg);
            webBackButton.setPadding(16, 16, 16, 16);
            
            FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(60, 60, Gravity.BOTTOM | Gravity.CENTER);
            backParams.setMargins(0, 0, 0, 24);
            webBackButton.setLayoutParams(backParams);
            webBackButton.setOnClickListener(v -> {
                removeWebOverlay();
                Toast.makeText(this, "WebView оверлей закрыт", Toast.LENGTH_SHORT).show();
            });
            
            // Панель управления размером
            webOverlayControls = new LinearLayout(this);
            webOverlayControls.setOrientation(LinearLayout.VERTICAL);
            webOverlayControls.setGravity(Gravity.CENTER);
            webOverlayControls.setBackgroundColor(Color.parseColor("#AA000000"));
            webOverlayControls.setPadding(16, 12, 16, 12);
            webOverlayControls.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
            ));
            
            TextView titleText = new TextView(this);
            titleText.setText("🌐 WEBVIEW ОВЕРЛЕЙ");
            titleText.setTextColor(Color.WHITE);
            titleText.setGravity(Gravity.CENTER);
            titleText.setTextSize(14);
            titleText.setTypeface(null, android.graphics.Typeface.BOLD);
            titleText.setPadding(0, 0, 0, 8);
            webOverlayControls.addView(titleText);
            
            // X контрол
            LinearLayout xLayout = new LinearLayout(this);
            xLayout.setOrientation(LinearLayout.HORIZONTAL);
            xLayout.setGravity(Gravity.CENTER);
            
            webSizeXLabel = new TextView(this);
            webSizeXLabel.setText("Ш");
            webSizeXLabel.setTextColor(Color.WHITE);
            webSizeXLabel.setPadding(0, 0, 8, 0);
            
            webSizeXInput = new EditText(this);
            webSizeXInput.setText("600");
            webSizeXInput.setInputType(InputType.TYPE_CLASS_NUMBER);
            webSizeXInput.setTextColor(Color.WHITE);
            webSizeXInput.setBackgroundColor(Color.parseColor("#333333"));
            webSizeXInput.setPadding(8, 4, 8, 4);
            webSizeXInput.setWidth(60);
            
            webXSeekBar = new SeekBar(this);
            webXSeekBar.setMax(1000);
            webXSeekBar.setProgress(550);
            webXSeekBar.setMinWidth(80);
            
            xLayout.addView(webSizeXLabel);
            xLayout.addView(webSizeXInput);
            xLayout.addView(webXSeekBar);
            webOverlayControls.addView(xLayout);
            
            // Y контрол
            LinearLayout yLayout = new LinearLayout(this);
            yLayout.setOrientation(LinearLayout.HORIZONTAL);
            yLayout.setGravity(Gravity.CENTER);
            
            webSizeYLabel = new TextView(this);
            webSizeYLabel.setText("В");
            webSizeYLabel.setTextColor(Color.WHITE);
            webSizeYLabel.setPadding(0, 0, 8, 0);
            
            webSizeYInput = new EditText(this);
            webSizeYInput.setText("500");
            webSizeYInput.setInputType(InputType.TYPE_CLASS_NUMBER);
            webSizeYInput.setTextColor(Color.WHITE);
            webSizeYInput.setBackgroundColor(Color.parseColor("#333333"));
            webSizeYInput.setPadding(8, 4, 8, 4);
            webSizeYInput.setWidth(60);
            
            webYSeekBar = new SeekBar(this);
            webYSeekBar.setMax(1000);
            webYSeekBar.setProgress(450);
            webYSeekBar.setMinWidth(80);
            
            yLayout.addView(webSizeYLabel);
            yLayout.addView(webSizeYInput);
            yLayout.addView(webYSeekBar);
            webOverlayControls.addView(yLayout);
            
            // Z контрол
            LinearLayout zLayout = new LinearLayout(this);
            zLayout.setOrientation(LinearLayout.HORIZONTAL);
            zLayout.setGravity(Gravity.CENTER);
            
            webSizeZLabel = new TextView(this);
            webSizeZLabel.setText("α");
            webSizeZLabel.setTextColor(Color.WHITE);
            webSizeZLabel.setPadding(0, 0, 8, 0);
            
            webSizeZInput = new EditText(this);
            webSizeZInput.setText("100");
            webSizeZInput.setInputType(InputType.TYPE_CLASS_NUMBER);
            webSizeZInput.setTextColor(Color.WHITE);
            webSizeZInput.setBackgroundColor(Color.parseColor("#333333"));
            webSizeZInput.setPadding(8, 4, 8, 4);
            webSizeZInput.setWidth(60);
            
            webZSeekBar = new SeekBar(this);
            webZSeekBar.setMax(100);
            webZSeekBar.setProgress(100);
            webZSeekBar.setMinWidth(80);
            
            zLayout.addView(webSizeZLabel);
            zLayout.addView(webSizeZInput);
            zLayout.addView(webZSeekBar);
            webOverlayControls.addView(zLayout);
            
            // Слушатели для контролов
            webXSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && webOverlayParams != null) {
                        webOverlayParams.width = progress + 100;
                        webSizeXInput.setText(String.valueOf(progress + 100));
                        updateWebOverlaySize();
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            
            webSizeXInput.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    try {
                        int val = Integer.parseInt(webSizeXInput.getText().toString());
                        if (val > 100) {
                            webOverlayParams.width = val;
                            webXSeekBar.setProgress(Math.min(val - 100, 1000));
                            updateWebOverlaySize();
                        }
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "webSizeXInput error", e);
                    }
                }
            });
            
            webYSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && webOverlayParams != null) {
                        webOverlayParams.height = progress + 100;
                        webSizeYInput.setText(String.valueOf(progress + 100));
                        updateWebOverlaySize();
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            
            webSizeYInput.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    try {
                        int val = Integer.parseInt(webSizeYInput.getText().toString());
                        if (val > 100) {
                            webOverlayParams.height = val;
                            webYSeekBar.setProgress(Math.min(val - 100, 1000));
                            updateWebOverlaySize();
                        }
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "webSizeYInput error", e);
                    }
                }
            });
            
            webZSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        webSizeZInput.setText(String.valueOf(progress));
                        float alpha = progress / 100f;
                        if (webOverlayContainer != null) webOverlayContainer.setAlpha(alpha);
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            
            webSizeZInput.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    try {
                        int val = Integer.parseInt(webSizeZInput.getText().toString());
                        if (val >= 0 && val <= 100) {
                            webZSeekBar.setProgress(val);
                            float alpha = val / 100f;
                            if (webOverlayContainer != null) webOverlayContainer.setAlpha(alpha);
                        }
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "webSizeZInput error", e);
                    }
                }
            });
            
            FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
            );
            controlsParams.setMargins(0, 0, 0, 100);
            webOverlayControls.setLayoutParams(controlsParams);
            
            // Добавляем все контролы в контейнер
            container.addView(webFixButton);
            container.addView(webDeleteButton);
            container.addView(webBackButton);
            container.addView(webOverlayControls);
            
            // Обработка касаний для перемещения
            setupWebOverlayTouchHandler(container);
            
        } catch (Exception e) {
            Log.e(TAG, "addWebOverlayControls error", e);
        }
    }
    
    private void setupWebOverlayTouchHandler(FrameLayout container) {
        // Используем отдельный прозрачный слой для обработки касаний
        // чтобы не мешать кликам по WebView
        final View touchLayer = new View(this);
        touchLayer.setBackgroundColor(Color.TRANSPARENT);
        touchLayer.setClickable(false);
        touchLayer.setFocusable(false);
        
        FrameLayout.LayoutParams touchParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        container.addView(touchLayer, touchParams);
        
        // Обработка касаний на прозрачном слое
        touchLayer.setOnTouchListener((v, event) -> {
            if (isWebOverlayFixed) return false;
            
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastTouchX = event.getRawX();
                    lastTouchY = event.getRawY();
                    initialX = webOverlayParams.x;
                    initialY = webOverlayParams.y;
                    initialPinchDistance = 0;
                    // Проверяем, не клик ли это по контролам
                    if (isTouchOnControls(event)) {
                        return false; // Пропускаем клики по контролам
                    }
                    return true;
                    
                case MotionEvent.ACTION_MOVE:
                    if (event.getPointerCount() == 2) {
                        float distance = getDistance(event);
                        if (initialPinchDistance == 0) {
                            initialPinchDistance = distance;
                        } else {
                            float scale = distance / initialPinchDistance;
                            int newWidth = (int)(webOverlayParams.width * scale);
                            int newHeight = (int)(webOverlayParams.height * scale);
                            if (newWidth > 150 && newHeight > 150 && newWidth < 1200 && newHeight < 1200) {
                                webOverlayParams.width = newWidth;
                                webOverlayParams.height = newHeight;
                                webSizeXInput.setText(String.valueOf(newWidth));
                                webSizeYInput.setText(String.valueOf(newHeight));
                                webXSeekBar.setProgress(Math.min(newWidth - 100, 1000));
                                webYSeekBar.setProgress(Math.min(newHeight - 100, 1000));
                                updateWebOverlaySize();
                            }
                        }
                    } else {
                        float dx = event.getRawX() - lastTouchX;
                        float dy = event.getRawY() - lastTouchY;
                        webOverlayParams.x += (int) dx;
                        webOverlayParams.y += (int) dy;
                        lastTouchX = event.getRawX();
                        lastTouchY = event.getRawY();
                        if (windowManager != null) {
                            windowManager.updateViewLayout(webOverlayContainer, webOverlayParams);
                        }
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
    
    private boolean isTouchOnControls(MotionEvent event) {
        if (webOverlayControls == null) return false;
        float x = event.getRawX();
        float y = event.getRawY();
        // Проверяем попадание в область контролов
        int[] location = new int[2];
        webOverlayControls.getLocationOnScreen(location);
        int left = location[0];
        int top = location[1];
        int right = left + webOverlayControls.getWidth();
        int bottom = top + webOverlayControls.getHeight();
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    private void toggleWebOverlayFix() {
        try {
            isWebOverlayFixed = !isWebOverlayFixed;
            
            if (isWebOverlayFixed) {
                Toast.makeText(this, "🔒 WebView оверлей закреплён", Toast.LENGTH_SHORT).show();
                webFixButton.setImageDrawable(createLockIcon(true));
                hideWebOverlayControls();
                
                // Блокируем только перемещение, но оставляем клики по сайту
                if (webOverlayView != null) {
                    webOverlayView.setClickable(true);
                    webOverlayView.setFocusable(true);
                    webOverlayView.setEnabled(true);
                }
                
                if (webOverlayParams != null && windowManager != null) {
                    // Не блокируем touch полностью, только блокируем перемещение через флаг
                    // но оставляем возможность кликать
                }
            } else {
                Toast.makeText(this, "🔓 WebView оверлей разблокирован", Toast.LENGTH_SHORT).show();
                webFixButton.setImageDrawable(createLockIcon(false));
                showWebOverlayControls();
                
                if (webOverlayView != null) {
                    webOverlayView.setClickable(true);
                    webOverlayView.setFocusable(true);
                    webOverlayView.setEnabled(true);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "toggleWebOverlayFix error", e);
        }
    }

    private void hideWebOverlayControls() {
        try {
            if (webOverlayControls != null) webOverlayControls.setVisibility(View.GONE);
            if (webDeleteButton != null) webDeleteButton.setVisibility(View.GONE);
            if (webBackButton != null) webBackButton.setVisibility(View.GONE);
            if (webFixButton != null) webFixButton.setVisibility(View.GONE);
        } catch (Exception e) {
            Log.e(TAG, "hideWebOverlayControls error", e);
        }
    }

    private void showWebOverlayControls() {
        try {
            if (webOverlayControls != null) webOverlayControls.setVisibility(View.VISIBLE);
            if (webDeleteButton != null) webDeleteButton.setVisibility(View.VISIBLE);
            if (webBackButton != null) webBackButton.setVisibility(View.VISIBLE);
            if (webFixButton != null) webFixButton.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            Log.e(TAG, "showWebOverlayControls error", e);
        }
    }
    
    private void updateWebOverlaySize() {
        try {
            if (windowManager != null && webOverlayContainer != null && webOverlayParams != null) {
                windowManager.updateViewLayout(webOverlayContainer, webOverlayParams);
            }
        } catch (Exception e) {
            Log.e(TAG, "updateWebOverlaySize error", e);
        }
    }

    private void removeWebOverlay() {
        try {
            if (webOverlayView != null) {
                webOverlayView.loadUrl("about:blank");
                webOverlayView.clearHistory();
                webOverlayView.clearCache(true);
                webOverlayView.clearFormData();
                webOverlayView.destroy();
                webOverlayView = null;
            }
            
            if (webOverlayContainer != null && windowManager != null) {
                windowManager.removeView(webOverlayContainer);
                webOverlayContainer = null;
            }
            
            isWebOverlayActive = false;
            isWebOverlayFixed = false;
            
            Log.d(TAG, "WebView оверлей удален");
        } catch (Exception e) {
            Log.e(TAG, "removeWebOverlay error", e);
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
                    openVideoPicker();
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

    private void openVideoPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_VIDEO);
    }

    // ==================== ЗАГРУЗКА ПЕРСОНАЖА ====================

    private void loadCharacterToFloat(CharacterData data) {
        try {
            if (data.isVideo) {
                loadVideoToFloat(data);
                return;
            }
            
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), 
                    Uri.fromFile(new File(data.path)));
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

    private void loadVideoToFloat(CharacterData data) {
        try {
            removeMainCircle();
            
            mainCircleContainer = new FrameLayout(this);
            mainCircleContainer.setBackgroundColor(Color.TRANSPARENT);
            
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(Color.TRANSPARENT);
            d.setStroke(3, Color.WHITE);
            mainCircleContainer.setBackground(d);
            mainCircleContainer.setAlpha(overlayAlpha / 255f);
            
            VideoView circleVideoView = new VideoView(this);
            circleVideoView.setVideoPath(data.path);
            circleVideoView.setPadding(5, 5, 5, 5);
            circleVideoView.setClipToOutline(true);
            
            FrameLayout.LayoutParams vidParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT);
            mainCircleContainer.addView(circleVideoView, vidParams);
            
            circleVideoView.setOnPreparedListener(mp -> {
                mp.setLooping(true);
                circleVideoView.start();
            });
            
            circleVideoView.start();
            
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
                Toast.makeText(this, "Видео загружено в оверлей", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "loadVideoToFloat error", e);
            Toast.makeText(this, "Ошибка загрузки видео", Toast.LENGTH_SHORT).show();
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

    // ==================== ЗАГРУЗКА НА ЭКРАН ====================

    private void loadCharacterToScreen(CharacterData data) {
        try {
            if (data.isVideo) {
                showVideoOnScreen(data);
                return;
            }
            
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), 
                    Uri.fromFile(new File(data.path)));
            showCharacterOnScreen(bitmap);
            Toast.makeText(this, "Персонаж на экране", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e(TAG, "loadCharacterToScreen error", e);
            Toast.makeText(this, "Ошибка загрузки", Toast.LENGTH_SHORT).show();
        }
    }

    private void showCharacterOnScreen(Bitmap bitmap) {
        try {
            if (windowManager == null) return;
            
            removeCharacter();
            
            currentCharacterBitmap = removeGreenScreen(bitmap, 40);
            isVideoMode = false;
            
            characterContainer = new FrameLayout(this);
            characterContainer.setBackgroundColor(Color.TRANSPARENT);
            
            characterView = new ImageView(this);
            characterView.setImageBitmap(currentCharacterBitmap);
            characterView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            characterView.setClickable(true);
            characterView.setFocusable(true);
            
            FrameLayout.LayoutParams charParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT);
            characterContainer.addView(characterView, charParams);
            
            addCharacterControls(characterContainer);
            
            characterParams = new WindowManager.LayoutParams(
                    400, 400,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT);
            characterParams.gravity = Gravity.CENTER;
            
            windowManager.addView(characterContainer, characterParams);
            isCharacterModeActive = true;
            isCharacterFixed = false;
        } catch (Exception e) {
            Log.e(TAG, "showCharacterOnScreen error", e);
        }
    }

    private void showVideoOnScreen(CharacterData data) {
        try {
            if (windowManager == null) return;
            
            removeCharacter();
            
            isVideoMode = true;
            currentVideoPath = data.path;
            
            characterContainer = new FrameLayout(this);
            characterContainer.setBackgroundColor(Color.TRANSPARENT);
            
            videoView = new VideoView(this);
            videoView.setVideoPath(data.path);
            videoView.setClickable(true);
            videoView.setFocusable(true);
            
            videoView.setOnPreparedListener(mp -> {
                currentMediaPlayer = mp;
                mp.setLooping(true);
                videoView.start();
                isVideoPlaying = true;
            });
            
            videoView.setOnErrorListener((mp, what, extra) -> {
                Toast.makeText(this, "Ошибка видео", Toast.LENGTH_SHORT).show();
                return true;
            });
            
            FrameLayout.LayoutParams vidParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT);
            characterContainer.addView(videoView, vidParams);
            
            addCharacterControls(characterContainer);
            
            characterParams = new WindowManager.LayoutParams(
                    400, 400,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT);
            characterParams.gravity = Gravity.CENTER;
            
            windowManager.addView(characterContainer, characterParams);
            isCharacterModeActive = true;
            isCharacterFixed = false;
            
            videoView.start();
            Toast.makeText(this, "Видео воспроизводится", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "showVideoOnScreen error", e);
        }
    }

    // ==================== УПРАВЛЕНИЕ ПЕРСОНАЖЕМ ====================

    private void addCharacterControls(FrameLayout container) {
        try {
            fixButton = new ImageButton(this);
            fixButton.setImageDrawable(createLockIcon(false));
            GradientDrawable fixBg = new GradientDrawable();
            fixBg.setShape(GradientDrawable.OVAL);
            fixBg.setColor(Color.parseColor("#FF6B00"));
            fixBg.setStroke(2, Color.WHITE);
            fixButton.setBackground(fixBg);
            fixButton.setPadding(16, 16, 16, 16);
            
            FrameLayout.LayoutParams fixParams = new FrameLayout.LayoutParams(60, 60, Gravity.TOP | Gravity.END);
            fixParams.setMargins(0, 24, 24, 0);
            fixButton.setLayoutParams(fixParams);
            fixButton.setOnClickListener(v -> toggleCharacterFix());
            
            deleteButton = new ImageButton(this);
            deleteButton.setImageDrawable(createDeleteIcon());
            GradientDrawable deleteBg = new GradientDrawable();
            deleteBg.setShape(GradientDrawable.OVAL);
            deleteBg.setColor(Color.RED);
            deleteBg.setStroke(2, Color.WHITE);
            deleteButton.setBackground(deleteBg);
            deleteButton.setPadding(16, 16, 16, 16);
            
            FrameLayout.LayoutParams deleteParams = new FrameLayout.LayoutParams(60, 60, Gravity.TOP | Gravity.START);
            deleteParams.setMargins(24, 24, 0, 0);
            deleteButton.setLayoutParams(deleteParams);
            deleteButton.setOnClickListener(v -> {
                removeCharacter();
                Toast.makeText(this, "Персонаж удалён", Toast.LENGTH_SHORT).show();
            });
            
            backButton = new ImageButton(this);
            backButton.setImageDrawable(createCloseIcon());
            GradientDrawable backBg = new GradientDrawable();
            backBg.setShape(GradientDrawable.OVAL);
            backBg.setColor(Color.parseColor("#9C27B0"));
            backBg.setStroke(2, Color.WHITE);
            backButton.setBackground(backBg);
            backButton.setPadding(16, 16, 16, 16);
            
            FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(60, 60, Gravity.BOTTOM | Gravity.CENTER);
            backParams.setMargins(0, 0, 0, 24);
            backButton.setLayoutParams(backParams);
            backButton.setOnClickListener(v -> {
                removeCharacter();
                Toast.makeText(this, "Персонаж закрыт", Toast.LENGTH_SHORT).show();
            });
            
            controlsLayout = new LinearLayout(this);
            controlsLayout.setOrientation(LinearLayout.VERTICAL);
            controlsLayout.setGravity(Gravity.CENTER);
            controlsLayout.setBackgroundColor(Color.parseColor("#AA000000"));
            controlsLayout.setPadding(16, 12, 16, 12);
            controlsLayout.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
            ));
            
            TextView titleText = new TextView(this);
            titleText.setText(isVideoMode ? "🎬 ВИДЕО" : "РАЗМЕР");
            titleText.setTextColor(Color.WHITE);
            titleText.setGravity(Gravity.CENTER);
            titleText.setTextSize(14);
            titleText.setTypeface(null, android.graphics.Typeface.BOLD);
            titleText.setPadding(0, 0, 0, 8);
            controlsLayout.addView(titleText);
            
            // X контрол
            LinearLayout xLayout = new LinearLayout(this);
            xLayout.setOrientation(LinearLayout.HORIZONTAL);
            xLayout.setGravity(Gravity.CENTER);
            
            sizeXLabel = new TextView(this);
            sizeXLabel.setText("Ш");
            sizeXLabel.setTextColor(Color.WHITE);
            sizeXLabel.setPadding(0, 0, 8, 0);
            
            sizeXInput = new EditText(this);
            sizeXInput.setText("400");
            sizeXInput.setInputType(InputType.TYPE_CLASS_NUMBER);
            sizeXInput.setTextColor(Color.WHITE);
            sizeXInput.setBackgroundColor(Color.parseColor("#333333"));
            sizeXInput.setPadding(8, 4, 8, 4);
            sizeXInput.setWidth(60);
            
            SeekBar xSeekBar = new SeekBar(this);
            xSeekBar.setMax(800);
            xSeekBar.setProgress(400);
            xSeekBar.setMinWidth(80);
            
            xLayout.addView(sizeXLabel);
            xLayout.addView(sizeXInput);
            xLayout.addView(xSeekBar);
            controlsLayout.addView(xLayout);
            
            // Y контрол
            LinearLayout yLayout = new LinearLayout(this);
            yLayout.setOrientation(LinearLayout.HORIZONTAL);
            yLayout.setGravity(Gravity.CENTER);
            
            sizeYLabel = new TextView(this);
            sizeYLabel.setText("В");
            sizeYLabel.setTextColor(Color.WHITE);
            sizeYLabel.setPadding(0, 0, 8, 0);
            
            sizeYInput = new EditText(this);
            sizeYInput.setText("400");
            sizeYInput.setInputType(InputType.TYPE_CLASS_NUMBER);
            sizeYInput.setTextColor(Color.WHITE);
            sizeYInput.setBackgroundColor(Color.parseColor("#333333"));
            sizeYInput.setPadding(8, 4, 8, 4);
            sizeYInput.setWidth(60);
            
            SeekBar ySeekBar = new SeekBar(this);
            ySeekBar.setMax(800);
            ySeekBar.setProgress(400);
            ySeekBar.setMinWidth(80);
            
            yLayout.addView(sizeYLabel);
            yLayout.addView(sizeYInput);
            yLayout.addView(ySeekBar);
            controlsLayout.addView(yLayout);
            
            // Z контрол
            LinearLayout zLayout = new LinearLayout(this);
            zLayout.setOrientation(LinearLayout.HORIZONTAL);
            zLayout.setGravity(Gravity.CENTER);
            
            sizeZLabel = new TextView(this);
            sizeZLabel.setText("α");
            sizeZLabel.setTextColor(Color.WHITE);
            sizeZLabel.setPadding(0, 0, 8, 0);
            
            sizeZInput = new EditText(this);
            sizeZInput.setText("100");
            sizeZInput.setInputType(InputType.TYPE_CLASS_NUMBER);
            sizeZInput.setTextColor(Color.WHITE);
            sizeZInput.setBackgroundColor(Color.parseColor("#333333"));
            sizeZInput.setPadding(8, 4, 8, 4);
            sizeZInput.setWidth(60);
            
            SeekBar zSeekBar = new SeekBar(this);
            zSeekBar.setMax(100);
            zSeekBar.setProgress(100);
            zSeekBar.setMinWidth(80);
            
            zLayout.addView(sizeZLabel);
            zLayout.addView(sizeZInput);
            zLayout.addView(zSeekBar);
            controlsLayout.addView(zLayout);
            
            // Слушатели
            xSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && characterParams != null) {
                        characterParams.width = progress + 50;
                        sizeXInput.setText(String.valueOf(progress + 50));
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
                        if (val > 50) {
                            characterParams.width = val;
                            xSeekBar.setProgress(Math.min(val - 50, 800));
                            updateCharacterSize();
                        }
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "sizeXInput error", e);
                    }
                }
            });
            
            ySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && characterParams != null) {
                        characterParams.height = progress + 50;
                        sizeYInput.setText(String.valueOf(progress + 50));
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
                        if (val > 50) {
                            characterParams.height = val;
                            ySeekBar.setProgress(Math.min(val - 50, 800));
                            updateCharacterSize();
                        }
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "sizeYInput error", e);
                    }
                }
            });
            
            zSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        sizeZInput.setText(String.valueOf(progress));
                        float alpha = progress / 100f;
                        if (characterContainer != null) characterContainer.setAlpha(alpha);
                        if (videoView != null) videoView.setAlpha(alpha);
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            
            sizeZInput.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    try {
                        int val = Integer.parseInt(sizeZInput.getText().toString());
                        if (val >= 0 && val <= 100) {
                            zSeekBar.setProgress(val);
                            float alpha = val / 100f;
                            if (characterContainer != null) characterContainer.setAlpha(alpha);
                            if (videoView != null) videoView.setAlpha(alpha);
                        }
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "sizeZInput error", e);
                    }
                }
            });
            
            FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
            );
            controlsParams.setMargins(0, 0, 0, 100);
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
            
            if (videoView != null) {
                videoView.setOnTouchListener((v, event) -> {
                    if (isCharacterFixed) return false;
                    return handleCharacterTouch(event);
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "addCharacterControls error", e);
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
            Log.e(TAG, "handleCharacterTouch error", e);
        }
        return false;
    }

    private void toggleCharacterFix() {
        try {
            isCharacterFixed = !isCharacterFixed;
            
            if (isCharacterFixed) {
                Toast.makeText(this, "🔒 Персонаж закреплён", Toast.LENGTH_SHORT).show();
                fixButton.setImageDrawable(createLockIcon(true));
                hideAllControls();
                
                if (characterParams != null && windowManager != null) {
                    characterParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    windowManager.updateViewLayout(characterContainer, characterParams);
                }
                
                if (characterContainer != null) {
                    characterContainer.setClickable(false);
                    characterContainer.setFocusable(false);
                }
                if (characterView != null) {
                    characterView.setClickable(false);
                    characterView.setFocusable(false);
                }
                if (videoView != null) {
                    videoView.setClickable(false);
                    videoView.setFocusable(false);
                }
            } else {
                Toast.makeText(this, "🔓 Персонаж разблокирован", Toast.LENGTH_SHORT).show();
                fixButton.setImageDrawable(createLockIcon(false));
                showAllControls();
                
                if (characterParams != null && windowManager != null) {
                    characterParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    windowManager.updateViewLayout(characterContainer, characterParams);
                }
                
                if (characterContainer != null) {
                    characterContainer.setClickable(true);
                    characterContainer.setFocusable(true);
                }
                if (characterView != null) {
                    characterView.setClickable(true);
                    characterView.setFocusable(true);
                }
                if (videoView != null) {
                    videoView.setClickable(true);
                    videoView.setFocusable(true);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "toggleCharacterFix error", e);
        }
    }

    private void hideAllControls() {
        try {
            if (controlsLayout != null) controlsLayout.setVisibility(View.GONE);
            if (deleteButton != null) deleteButton.setVisibility(View.GONE);
            if (backButton != null) backButton.setVisibility(View.GONE);
            if (fixButton != null) fixButton.setVisibility(View.GONE);
        } catch (Exception e) {
            Log.e(TAG, "hideAllControls error", e);
        }
    }

    private void showAllControls() {
        try {
            if (controlsLayout != null) controlsLayout.setVisibility(View.VISIBLE);
            if (deleteButton != null) deleteButton.setVisibility(View.VISIBLE);
            if (backButton != null) backButton.setVisibility(View.VISIBLE);
            if (fixButton != null) fixButton.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            Log.e(TAG, "showAllControls error", e);
        }
    }
    
    private void updateCharacterSize() {
        try {
            if (windowManager != null && characterContainer != null && characterParams != null) {
                windowManager.updateViewLayout(characterContainer, characterParams);
            }
        } catch (Exception e) {
            Log.e(TAG, "updateCharacterSize error", e);
        }
    }

    private void removeCharacter() {
        try {
            releaseMediaResources();
            
            if (characterContainer != null && windowManager != null) {
                windowManager.removeView(characterContainer);
                characterContainer = null;
            }
            
            characterView = null;
            videoView = null;
            currentCharacterBitmap = null;
            currentMediaPlayer = null;
            isCharacterModeActive = false;
            isCharacterFixed = false;
            isVideoMode = false;
            isVideoPlaying = false;
            
            Log.d(TAG, "Персонаж удален");
        } catch (Exception e) {
            Log.e(TAG, "removeCharacter error", e);
        }
    }

    private void releaseMediaResources() {
        try {
            if (currentMediaPlayer != null) {
                currentMediaPlayer.stop();
                currentMediaPlayer.release();
                currentMediaPlayer = null;
            }
            if (videoView != null) {
                videoView.stopPlayback();
            }
        } catch (Exception e) {
            Log.e(TAG, "releaseMediaResources error", e);
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
            Log.e(TAG, "removeGreenScreen error", e);
            return null;
        }
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
        if (videoView != null && isVideoPlaying) {
            videoView.start();
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
        if (videoView != null && isVideoPlaying) {
            videoView.pause();
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
            removeCharacter();
            removeWebOverlay();
            removeMainCircle();
            hideMainOverlay();
            releaseMediaResources();
            
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
                case REQUEST_MICROPHONE: Toast.makeText(this, "Микрофон разрешён", Toast.LENGTH_SHORT).show(); break;
                case REQUEST_CAMERA: Toast.makeText(this, "Камера разрешена", Toast.LENGTH_SHORT).show(); break;
                case REQUEST_STORAGE: Toast.makeText(this, "Хранилище разрешено", Toast.LENGTH_SHORT).show(); break;
                case REQUEST_VIDEO: Toast.makeText(this, "Доступ к видео разрешён", Toast.LENGTH_SHORT).show(); break;
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
                    Bitmap original = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                    String path = saveImageToStorage(original);
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
  }
