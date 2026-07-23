package com.cr.arcade;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentValues;
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
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.InputType;
import android.view.Display;
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
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends BridgeActivity {

    private static final int REQUEST_MICROPHONE = 100;
    private static final int REQUEST_CAMERA = 102;
    private static final int REQUEST_OVERLAY_PERMISSION = 101;
    private static final int REQUEST_GALLERY = 103;
    private static final int REQUEST_STORAGE = 104;
    private static final int REQUEST_NOTIFICATION = 106;
    private static final int REQUEST_SCREEN_CAPTURE = 107;

    private static final String URL_HOME = "https://wyikhedfghhopyewfvjkurrhncswehipkhf.vercel.app/";
    private static final String URL_SETTINGS = "https://whuokhgrdcbnmkloplureecvjiqoendu.vercel.app/";

    private WindowManager windowManager;
    private FrameLayout mainCircleContainer;
    private WindowManager.LayoutParams mainCircleParams;
    
    private float startX, startY;
    private int initialX, initialY;
    private boolean isDragging = false;
    
    private FrameLayout mainOverlay;
    private WebView webView;
    private WindowManager.LayoutParams mainOverlayParams;
    private boolean isMainOverlayVisible = false;
    private boolean isAppInForeground = true;

    // Компоненты персонажей
    private FrameLayout characterContainer;
    private ImageView characterView;
    private WindowManager.LayoutParams characterParams;
    private Bitmap currentCharacterBitmap;
    private boolean isCharacterFixed = false;
    private boolean isCharacterModeActive = false;
    
    private float lastTouchX, lastTouchY;
    private float initialPinchDistance = 0;
    
    private LinearLayout controlsLayout;
    private ImageButton fixButton;
    private ImageButton deleteButton;
    private ImageButton backButton;
    private EditText sizeXInput, sizeYInput, sizeZInput;
    private TextView sizeXLabel, sizeYLabel, sizeZLabel;
    
    // Система персонажей
    private ArrayList<CharacterData> characters = new ArrayList<>();
    private SharedPreferences prefs;
    private String tempCharacterName = "";
    
    private EditText nameInput;
    
    // Переключение режимов
    private boolean isWebViewMode = true;
    private FrameLayout contentContainer;
    private LinearLayout charactersGridLayout;
    
    // Настройки оверлея
    private int overlayAlpha = 255;
    private int overlaySize = 136;

    // ==================== СИСТЕМА ТРЕКИНГА ДРУЗЕЙ/ВРАГОВ ====================
    
    private static class FriendData {
        String name;
        String tag;
        long timestamp;
        
        FriendData(String name, String tag) {
            this.name = name;
            this.tag = tag;
            this.timestamp = System.currentTimeMillis();
        }
        
        FriendData(JSONObject json) throws Exception {
            this.name = json.getString("name");
            this.tag = json.getString("tag");
            this.timestamp = json.optLong("timestamp", System.currentTimeMillis());
        }
        
        JSONObject toJSON() throws Exception {
            JSONObject json = new JSONObject();
            json.put("name", name);
            json.put("tag", tag);
            json.put("timestamp", timestamp);
            return json;
        }
    }
    
    private ArrayList<FriendData> friends = new ArrayList<>();
    private String selectedTag = "friend";
    
    // Компоненты для отображения тегов
    private Map<String, TagView> activeTags = new HashMap<>();
    
    // Система захвата экрана
    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private ExecutorService executorService;
    private Handler mainHandler;
    private boolean isScreenCaptureRunning = false;
    private Handler scanHandler = new Handler(Looper.getMainLooper());
    private Runnable scanRunnable;
    private int scanInterval = 2000;

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

    // ==================== КЛАСС ДЛЯ ТЕГОВ ====================
    
    private class TagView {
        FrameLayout container;
        TextView tagText;
        TextView nameText;
        String friendName;
        String tag;
        int x, y;
        
        TagView(Context context, String name, String tag) {
            this.friendName = name;
            this.tag = tag;
            
            container = new FrameLayout(context);
            container.setBackgroundColor(Color.TRANSPARENT);
            container.setPadding(8, 4, 8, 4);
            
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(12);
            bg.setColor(tag.equals("friend") ? Color.parseColor("#CC4CAF50") : Color.parseColor("#CCF44336"));
            bg.setStroke(2, Color.WHITE);
            container.setBackground(bg);
            
            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setGravity(Gravity.CENTER);
            
            nameText = new TextView(context);
            nameText.setText(name);
            nameText.setTextColor(Color.WHITE);
            nameText.setTextSize(14);
            nameText.setTypeface(null, android.graphics.Typeface.BOLD);
            nameText.setGravity(Gravity.CENTER);
            
            tagText = new TextView(context);
            tagText.setText(tag.equals("friend") ? "🤝 ДРУГ" : "👿 ВРАГ");
            tagText.setTextColor(Color.WHITE);
            tagText.setTextSize(11);
            tagText.setGravity(Gravity.CENTER);
            tagText.setPadding(0, 2, 0, 0);
            
            layout.addView(nameText);
            layout.addView(tagText);
            container.addView(layout);
            
            container.setOnTouchListener((v, event) -> false);
            container.setClickable(false);
            container.setFocusable(false);
        }
        
        void updatePosition(int x, int y) {
            this.x = x;
            this.y = y;
            if (container.getParent() != null) {
                WindowManager.LayoutParams params = (WindowManager.LayoutParams) container.getLayoutParams();
                params.x = x;
                params.y = y;
                if (windowManager != null) {
                    windowManager.updateViewLayout(container, params);
                }
            }
        }
        
        void show() {
            if (container.getParent() == null && windowManager != null) {
                int flag = getOverlayFlag();
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        flag,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        PixelFormat.TRANSLUCENT
                );
                params.gravity = Gravity.TOP | Gravity.START;
                params.x = x;
                params.y = y;
                windowManager.addView(container, params);
            }
        }
        
        void hide() {
            if (container.getParent() != null && windowManager != null) {
                windowManager.removeView(container);
            }
        }
    }

    // ==================== ONCREATE ====================
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            prefs = getSharedPreferences("arcade_data", MODE_PRIVATE);
            overlayAlpha = prefs.getInt("overlay_alpha", 255);
            overlaySize = prefs.getInt("overlay_size", 136);
            loadCharacters();
            loadFriends();
        } catch (Exception e) {
            e.printStackTrace();
        }

        requestPermissionsIfNeeded();
        createWebView();

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

        if (windowManager == null) {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        }
        
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        
        startOverlayService();
        createNotificationChannel();
    }

    // ==================== ЗАПУСК СЕРВИСА ====================
    
    private void startOverlayService() {
        try {
            Intent serviceIntent = new Intent(this, OverlayService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        "overlay_channel",
                        "CR Arcade Overlay",
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription("Управление оверлеем CR Arcade");
                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) {
                    manager.createNotificationChannel(channel);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== СОХРАНЕНИЕ ДАННЫХ ====================
    
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
                .putInt("overlay_alpha", overlayAlpha)
                .putInt("overlay_size", overlaySize)
                .apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void loadFriends() {
        try {
            friends.clear();
            String json = prefs.getString("friends_list", "");
            if (!json.isEmpty()) {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    friends.add(new FriendData(array.getJSONObject(i)));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void saveFriends() {
        try {
            JSONArray array = new JSONArray();
            for (FriendData data : friends) {
                array.put(data.toJSON());
            }
            prefs.edit().putString("friends_list", array.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private String saveImageToStorage(Bitmap bitmap) {
        try {
            File dir = new File(getExternalFilesDir(null), "characters");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
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

    // ==================== РАЗРЕШЕНИЯ ====================
    
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

    // ==================== СОЗДАНИЕ WEBVIEW ====================

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
            ws.setCacheMode(WebSettings.LOAD_DEFAULT);
            ws.setDatabaseEnabled(true);

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
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    view.loadUrl(url);
                    return true;
                }
            });

            webView.loadUrl(URL_HOME);
            webView.setBackgroundColor(Color.parseColor("#0A0A0A"));
            webView.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== ГЛАВНЫЙ КРУЖОК ====================

    private void createMainCircle() {
        try {
            if (isAppInForeground) {
                removeMainCircle();
                return;
            }
            
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

    private Drawable createFriendIcon() {
        try {
            Bitmap b = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(7);
            
            float cx = 60, cy = 60;
            c.drawCircle(cx, cy - 12, 18, p);
            c.drawLine(cx - 25, cy + 8, cx - 10, cy + 18, p);
            c.drawLine(cx + 25, cy + 8, cx + 10, cy + 18, p);
            c.drawLine(cx, cy + 18, cx, cy + 40, p);
            
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Exception e) {
            e.printStackTrace();
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

            // ВЕРХНЯЯ ПАНЕЛЬ
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
                if (webView != null) webView.loadUrl(URL_HOME);
                updateContent();
            });
            leftButtons.addView(homeBtn);
            
            ImageButton charsBtn = createLargeRoundButton(createCharacterIcon(), "#1A1A1A", 4, "#CC0000");
            charsBtn.setOnClickListener(v -> {
                isWebViewMode = false;
                updateContent();
            });
            leftButtons.addView(charsBtn);
            
            ImageButton friendsBtn = createLargeRoundButton(createFriendIcon(), "#1A1A1A", 4, "#CC0000");
            friendsBtn.setOnClickListener(v -> showFriendsMenu());
            leftButtons.addView(friendsBtn);
            
            ImageButton settingsBtn = createLargeRoundButton(createSettingsIcon(), "#1A1A1A", 4, "#CC0000");
            settingsBtn.setOnClickListener(v -> {
                isWebViewMode = true;
                if (webView != null) webView.loadUrl(URL_SETTINGS);
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

    // ==================== МЕНЮ ДРУЗЕЙ ====================
    
    private void showFriendsMenu() {
        try {
            FrameLayout friendsOverlay = new FrameLayout(this);
            friendsOverlay.setBackgroundColor(Color.parseColor("#CC000000"));
            
            LinearLayout friendsLayout = new LinearLayout(this);
            friendsLayout.setOrientation(LinearLayout.VERTICAL);
            friendsLayout.setGravity(Gravity.CENTER);
            friendsLayout.setPadding(30, 30, 30, 30);
            
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(30);
            bg.setColor(Color.parseColor("#0D0D0D"));
            bg.setStroke(3, Color.parseColor("#CC0000"));
            friendsLayout.setBackground(bg);
            
            TextView title = new TextView(this);
            title.setText("👥 ЗНАКОМЫЕ");
            title.setTextColor(Color.parseColor("#CC0000"));
            title.setTextSize(22);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, 0, 0, 20);
            friendsLayout.addView(title);
            
            ScrollView scrollView = new ScrollView(this);
            LinearLayout listLayout = new LinearLayout(this);
            listLayout.setOrientation(LinearLayout.VERTICAL);
            listLayout.setPadding(0, 0, 0, 16);
            
            if (friends.isEmpty()) {
                TextView emptyText = new TextView(this);
                emptyText.setText("📭 Нет добавленных знакомых");
                emptyText.setTextColor(Color.parseColor("#555555"));
                emptyText.setTextSize(16);
                emptyText.setGravity(Gravity.CENTER);
                emptyText.setPadding(0, 40, 0, 40);
                listLayout.addView(emptyText);
            } else {
                for (int i = 0; i < friends.size(); i++) {
                    final int position = i;
                    FriendData friend = friends.get(i);
                    
                    LinearLayout itemLayout = new LinearLayout(this);
                    itemLayout.setOrientation(LinearLayout.HORIZONTAL);
                    itemLayout.setGravity(Gravity.CENTER_VERTICAL);
                    itemLayout.setPadding(12, 8, 12, 8);
                    
                    GradientDrawable itemBg = new GradientDrawable();
                    itemBg.setCornerRadius(12);
                    itemBg.setColor(Color.parseColor("#1A0000"));
                    itemBg.setStroke(1, Color.parseColor("#8B0000"));
                    itemLayout.setBackground(itemBg);
                    
                    TextView nameText = new TextView(this);
                    nameText.setText(friend.name);
                    nameText.setTextColor(Color.WHITE);
                    nameText.setTextSize(16);
                    nameText.setTypeface(null, android.graphics.Typeface.BOLD);
                    LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                    nameText.setLayoutParams(nameParams);
                    itemLayout.addView(nameText);
                    
                    TextView tagText = new TextView(this);
                    tagText.setText(friend.tag.equals("friend") ? "🤝 ДРУГ" : "👿 ВРАГ");
                    tagText.setTextColor(friend.tag.equals("friend") ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));
                    tagText.setTextSize(12);
                    tagText.setTypeface(null, android.graphics.Typeface.BOLD);
                    tagText.setPadding(8, 0, 8, 0);
                    itemLayout.addView(tagText);
                    
                    Button deleteBtn = new Button(this);
                    deleteBtn.setText("✕");
                    deleteBtn.setTextColor(Color.RED);
                    deleteBtn.setTextSize(16);
                    deleteBtn.setBackgroundColor(Color.TRANSPARENT);
                    deleteBtn.setOnClickListener(v -> {
                        friends.remove(position);
                        saveFriends();
                        showFriendsMenu();
                    });
                    itemLayout.addView(deleteBtn);
                    
                    listLayout.addView(itemLayout);
                    listLayout.addView(new View(this) {{
                        setLayoutParams(new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, 4));
                    }});
                }
            }
            
            scrollView.addView(listLayout);
            LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
            scrollParams.setMargins(0, 0, 0, 16);
            scrollView.setLayoutParams(scrollParams);
            friendsLayout.addView(scrollView);
            
            LinearLayout btnLayout = new LinearLayout(this);
            btnLayout.setOrientation(LinearLayout.HORIZONTAL);
            btnLayout.setGravity(Gravity.CENTER);
            
            Button addBtn = new Button(this);
            addBtn.setText("➕ ДОБАВИТЬ");
            addBtn.setTextColor(Color.WHITE);
            addBtn.setTextSize(14);
            addBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable addBg = new GradientDrawable();
            addBg.setCornerRadius(20);
            addBg.setColor(Color.parseColor("#CC0000"));
            addBtn.setBackground(addBg);
            addBtn.setPadding(24, 14, 24, 14);
            addBtn.setOnClickListener(v -> {
                if (windowManager != null) windowManager.removeView(friendsOverlay);
                showAddFriendDialog();
            });
            
            Button closeBtn = new Button(this);
            closeBtn.setText("ЗАКРЫТЬ");
            closeBtn.setTextColor(Color.WHITE);
            closeBtn.setTextSize(14);
            closeBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable closeBg = new GradientDrawable();
            closeBg.setCornerRadius(20);
            closeBg.setColor(Color.parseColor("#2A0000"));
            closeBg.setStroke(2, Color.parseColor("#8B0000"));
            closeBtn.setBackground(closeBg);
            closeBtn.setPadding(24, 14, 24, 14);
            closeBtn.setOnClickListener(v -> {
                if (windowManager != null) windowManager.removeView(friendsOverlay);
            });
            
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            btnParams.setMargins(8, 0, 8, 0);
            btnLayout.addView(addBtn, btnParams);
            btnLayout.addView(closeBtn, btnParams);
            friendsLayout.addView(btnLayout);
            
            FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            containerParams.gravity = Gravity.CENTER;
            containerParams.setMargins(30, 0, 30, 0);
            friendsOverlay.addView(friendsLayout, containerParams);
            
            WindowManager.LayoutParams windowParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    getOverlayFlag(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT);
            
            if (windowManager != null) windowManager.addView(friendsOverlay, windowParams);
            
            if (!friends.isEmpty() && !isScreenCaptureRunning) {
                startScreenCapture();
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== ДОБАВЛЕНИЕ ЗНАКОМОГО ====================
    
    private void showAddFriendDialog() {
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
            title.setText("👤 ДОБАВИТЬ ЗНАКОМОГО");
            title.setTextColor(Color.parseColor("#CC0000"));
            title.setTextSize(22);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, 0, 0, 20);
            dialogLayout.addView(title);
            
            TextView nameLabel = new TextView(this);
            nameLabel.setText("НИК В ИГРЕ");
            nameLabel.setTextColor(Color.parseColor("#888888"));
            nameLabel.setTextSize(12);
            nameLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            nameLabel.setPadding(0, 0, 0, 8);
            dialogLayout.addView(nameLabel);
            
            EditText nameInput = new EditText(this);
            nameInput.setHint("Введите ник игрока");
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
            
            LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            inputParams.setMargins(0, 0, 0, 20);
            nameInput.setLayoutParams(inputParams);
            dialogLayout.addView(nameInput);
            
            TextView tagLabel = new TextView(this);
            tagLabel.setText("ВЫБЕРИТЕ ТЕГ");
            tagLabel.setTextColor(Color.parseColor("#888888"));
            tagLabel.setTextSize(12);
            tagLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            tagLabel.setPadding(0, 0, 0, 8);
            dialogLayout.addView(tagLabel);
            
            LinearLayout tagLayout = new LinearLayout(this);
            tagLayout.setOrientation(LinearLayout.HORIZONTAL);
            tagLayout.setGravity(Gravity.CENTER);
            tagLayout.setPadding(0, 0, 0, 20);
            
            String[] tags = {"🤝 ДРУГ", "👿 ВРАГ"};
            String[] tagValues = {"friend", "enemy"};
            int[] tagColors = {Color.parseColor("#4CAF50"), Color.parseColor("#F44336")};
            
            for (int i = 0; i < tags.length; i++) {
                final String tagValue = tagValues[i];
                Button tagBtn = new Button(this);
                tagBtn.setText(tags[i]);
                tagBtn.setTextColor(Color.WHITE);
                tagBtn.setTextSize(14);
                tagBtn.setTypeface(null, android.graphics.Typeface.BOLD);
                
                GradientDrawable tagBg = new GradientDrawable();
                tagBg.setCornerRadius(20);
                tagBg.setColor(tagColors[i]);
                tagBg.setStroke(2, tagColors[i]);
                tagBtn.setBackground(tagBg);
                tagBtn.setPadding(20, 12, 20, 12);
                
                LinearLayout.LayoutParams tagParams = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                tagParams.setMargins(8, 0, 8, 0);
                tagBtn.setLayoutParams(tagParams);
                
                final int index = i;
                tagBtn.setOnClickListener(v -> {
                    selectedTag = tagValue;
                    Toast.makeText(this, "Выбран тег: " + tags[index], Toast.LENGTH_SHORT).show();
                });
                
                tagLayout.addView(tagBtn);
            }
            dialogLayout.addView(tagLayout);
            
            Button addBtn = new Button(this);
            addBtn.setText("✅ ДОБАВИТЬ");
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
                if (name.isEmpty()) {
                    Toast.makeText(this, "Введите ник!", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                friends.add(new FriendData(name, selectedTag));
                saveFriends();
                if (windowManager != null) windowManager.removeView(dialogOverlay);
                Toast.makeText(this, "Знакомый добавлен!", Toast.LENGTH_SHORT).show();
                
                if (!isScreenCaptureRunning) {
                    startScreenCapture();
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
            e.printStackTrace();
        }
    }

    // ==================== ЗАХВАТ ЭКРАНА ====================
    
    private void startScreenCapture() {
        try {
            if (isScreenCaptureRunning) return;
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                Intent intent = projectionManager.createScreenCaptureIntent();
                startActivityForResult(intent, REQUEST_SCREEN_CAPTURE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void stopScreenCapture() {
        try {
            isScreenCaptureRunning = false;
            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
            }
            if (mediaProjection != null) {
                mediaProjection.stop();
                mediaProjection = null;
            }
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
            scanHandler.removeCallbacks(scanRunnable);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void setupScreenCapture(int resultCode, Intent data) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mediaProjection = projectionManager.getMediaProjection(resultCode, data);
                
                Display display = getWindowManager().getDefaultDisplay();
                android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
                display.getMetrics(metrics);
                
                int width = metrics.widthPixels;
                int height = metrics.heightPixels;
                int density = metrics.densityDpi;
                
                imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
                
                virtualDisplay = mediaProjection.createVirtualDisplay(
                        "ScreenCapture",
                        width, height, density,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        imageReader.getSurface(),
                        null, null
                );
                
                isScreenCaptureRunning = true;
                startScanning();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void startScanning() {
        scanRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isScreenCaptureRunning) return;
                captureAndAnalyze();
                scanHandler.postDelayed(this, scanInterval);
            }
        };
        scanHandler.post(scanRunnable);
    }
    
    private void captureAndAnalyze() {
        try {
            if (imageReader == null || friends.isEmpty()) return;
            
            Image image = imageReader.acquireLatestImage();
            if (image == null) return;
            
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            
            int width = image.getWidth();
            int height = image.getHeight();
            
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
            image.close();
            
            analyzeScreen(bitmap);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void analyzeScreen(Bitmap bitmap) {
        try {
            executorService.execute(() -> {
                try {
                    for (FriendData friend : friends) {
                        boolean found = Math.random() > 0.5;
                        
                        mainHandler.post(() -> {
                            if (found) {
                                showTagForFriend(friend);
                            } else {
                                hideTagForFriend(friend.name);
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void showTagForFriend(FriendData friend) {
        try {
            if (activeTags.containsKey(friend.name)) return;
            
            int x = 100 + (int)(Math.random() * 500);
            int y = 200 + (int)(Math.random() * 500);
            
            TagView tagView = new TagView(this, friend.name, friend.tag);
            tagView.x = x;
            tagView.y = y;
            tagView.show();
            activeTags.put(friend.name, tagView);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void hideTagForFriend(String name) {
        try {
            TagView tagView = activeTags.remove(name);
            if (tagView != null) {
                tagView.hide();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== УДАЛЕНИЕ ОВЕРЛЕЯ ====================
    
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
                    if (windowManager != null) windowManager.removeView(confirmOverlay);
                    hideMainOverlay();
                    removeCharacter();
                    removeMainCircle();
                    stopScreenCapture();
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

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================
    
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
            e.printStackTrace();
            return null;
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
            addBtn.setOnClickListener(v -> showAddCharacterDialog());
            headerRow.addView(addBtn);
            charactersGridLayout.addView(headerRow);
            
            if (characters.isEmpty()) {
                TextView emptyText = new TextView(this);
                emptyText.setText("📭 Нет сохранённых персонажей\n\nНажмите + чтобы добавить");
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
            e.printStackTrace();
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
                e.printStackTrace();
            }
            
            card.addView(previewContainer);
            
            String displayName = data.name.trim().isEmpty() ? "Без имени" : data.name;
            if (displayName.length() > 12) displayName = displayName.substring(0, 10) + "...";
            
            TextView nameText = new TextView(this);
            nameText.setText(displayName);
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
            final int position = index;
            deleteBtn.setOnClickListener(v -> {
                characters.remove(position);
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
            e.printStackTrace();
            return null;
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
            title.setText("🖼 НОВЫЙ ПЕРСОНАЖ");
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
            nameInput.setHint("Введите имя персонажа");
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
            addBtn.setText("📷 ВЫБРАТЬ ИЗОБРАЖЕНИЕ");
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
                openGalleryForCharacter();
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
            e.printStackTrace();
        }
    }

    private void openGalleryForCharacter() {
        try {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, REQUEST_GALLERY);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== ЗАГРУЗКА ПЕРСОНАЖА ====================

    private void loadCharacterToFloat(CharacterData data) {
        try {
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
            e.printStackTrace();
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
                e.printStackTrace();
            }
            return false;
        };
    }

    // ==================== ЗАГРУЗКА НА ЭКРАН ====================

    private void loadCharacterToScreen(CharacterData data) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), 
                    Uri.fromFile(new File(data.path)));
            showCharacterOnScreen(bitmap);
            Toast.makeText(this, "Персонаж на экране", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
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
            e.printStackTrace();
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
            titleText.setText("РАЗМЕР");
            titleText.setTextColor(Color.WHITE);
            titleText.setGravity(Gravity.CENTER);
            titleText.setTextSize(14);
            titleText.setTypeface(null, android.graphics.Typeface.BOLD);
            titleText.setPadding(0, 0, 0, 8);
            controlsLayout.addView(titleText);
            
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
                    } catch (NumberFormatException e) {}
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
                    } catch (NumberFormatException e) {}
                }
            });
            
            zSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        sizeZInput.setText(String.valueOf(progress));
                        float alpha = progress / 100f;
                        if (characterContainer != null) characterContainer.setAlpha(alpha);
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
                        }
                    } catch (NumberFormatException e) {}
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
            e.printStackTrace();
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
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void hideAllControls() {
        try {
            if (controlsLayout != null) controlsLayout.setVisibility(View.GONE);
            if (deleteButton != null) deleteButton.setVisibility(View.GONE);
            if (backButton != null) backButton.setVisibility(View.GONE);
            if (fixButton != null) fixButton.setVisibility(View.GONE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showAllControls() {
        try {
            if (controlsLayout != null) controlsLayout.setVisibility(View.VISIBLE);
            if (deleteButton != null) deleteButton.setVisibility(View.VISIBLE);
            if (backButton != null) backButton.setVisibility(View.VISIBLE);
            if (fixButton != null) fixButton.setVisibility(View.VISIBLE);
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
                windowManager.removeView(characterContainer);
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
        removeMainCircle();
        if (isMainOverlayVisible) {
            updateContent();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        isAppInForeground = false;
        hideMainOverlay();
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
            removeMainCircle();
            hideMainOverlay();
            stopScreenCapture();
            if (webView != null) {
                webView.destroy();
                webView = null;
            }
            if (executorService != null) {
                executorService.shutdown();
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
                case REQUEST_NOTIFICATION: Toast.makeText(this, "Уведомления разрешены", Toast.LENGTH_SHORT).show(); break;
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
                        characters.add(new CharacterData(tempCharacterName, path));
                        saveCharacters();
                        Toast.makeText(this, "Персонаж сохранён", Toast.LENGTH_SHORT).show();
                        if (isMainOverlayVisible) updateContent();
                    }
                }
            }
            
            if (requestCode == REQUEST_OVERLAY_PERMISSION) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (Settings.canDrawOverlays(this)) {
                        if (!isAppInForeground) {
                            createMainCircle();
                        }
                    } else {
                        Toast.makeText(this, "Разрешение на оверлей требуется!", Toast.LENGTH_LONG).show();
                    }
                }
            }
            
            if (requestCode == REQUEST_SCREEN_CAPTURE) {
                if (resultCode == RESULT_OK) {
                    setupScreenCapture(resultCode, data);
                    Toast.makeText(this, "Захват экрана запущен", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Необходимо разрешение на захват экрана", Toast.LENGTH_LONG).show();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка", Toast.LENGTH_SHORT).show();
        }
    }
      }
