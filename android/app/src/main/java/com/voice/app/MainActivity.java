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
import android.os.Handler;
import android.os.Looper;
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

// Импорты ZeroTier
import com.zerotier.sdk.*;

import java.io.*;
import java.net.*;

public class MainActivity extends BridgeActivity {

    private static final int REQUEST_MICROPHONE = 100;
    private static final int REQUEST_CAMERA = 102;
    private static final int REQUEST_OVERLAY_PERMISSION = 101;
    private static final long ZEROTIER_NETWORK_ID = 0x633e31d8a2fcb63fL; // ТВОЙ NETWORK ID

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

    // === НОВЫЕ ПЕРЕМЕННЫЕ ДЛЯ ZEROTIER И LAN DISCOVERY ===
    private Node zerotierNode;
    private boolean zerotierRunning = false;
    private String virtualIP = null;
    private boolean discoveryRunning = false;
    private Thread discoveryThread;
    private DatagramSocket discoverySocket;
    private String serverName = "Мой мир";     // Название сервера
    private String levelName = "Выживание";    // Название мира
    private int gameType = 0;                  // 0=Выживание, 1=Творчество
    private int maxPlayers = 10;               // Максимум игроков
    // ==============================================

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Запрос микрофона
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MICROPHONE);
        }

        // Запрос камеры
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
        }

        // Запрос разрешения на плавающее окно
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

        // Запуск Foreground Service
        Intent serviceIntent = new Intent(this, VoiceForegroundService.class);
        ContextCompat.startForegroundService(this, serviceIntent);

        // НОВОЕ: Запускаем ZeroTier через 2 секунды
        new Handler(Looper.getMainLooper()).postDelayed(() -> startZeroTier(), 2000);

        // Настройка WebView в основном приложении
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

    // ==================== НОВЫЙ МЕТОД: ZEROTIER ====================
    private void startZeroTier() {
        if (zerotierRunning) return;
        showToast("ZeroTier: подключение...");

        try {
            Node.Configuration config = new Node.Configuration();
            zerotierNode = new Node(getApplicationContext(), config, null, new Node.Listener() {
                @Override
                public void onNetworkReady(long nwid) {
                    showToast("ZeroTier: подключено!");
                    VirtualNetworkInfo vnet = zerotierNode.getNetworkInfo(ZEROTIER_NETWORK_ID);
                    if (vnet != null && vnet.getAssignedAddresses().length > 0) {
                        for (InetAddress addr : vnet.getAssignedAddresses()) {
                            if (addr instanceof Inet4Address) {
                                virtualIP = addr.getHostAddress();
                                showToast("Виртуальный IP: " + virtualIP);
                                break;
                            }
                        }
                    }
                    startDiscoveryServer();
                }

                @Override
                public void onNetworkFailure(long nwid, int err) {
                    showToast("ZeroTier ошибка: " + err);
                    startDiscoveryServer();
                }
            });

            zerotierNode.start();
            zerotierRunning = true;
            zerotierNode.join(ZEROTIER_NETWORK_ID);
        } catch (Exception e) {
            showToast("ZeroTier ошибка: " + e.getMessage());
            startDiscoveryServer();
        }
    }

    // ==================== НОВЫЙ МЕТОД: LAN DISCOVERY ====================
    private void startDiscoveryServer() {
        if (discoveryRunning) return;
        discoveryRunning = true;

        discoveryThread = new Thread(() -> {
            try {
                discoverySocket = new DatagramSocket(7551);
                discoverySocket.setBroadcast(true);
                discoverySocket.setSoTimeout(1000);
                showToast("LAN Discovery запущен на порту 7551");

                byte[] buffer = new byte[2048];
                while (discoveryRunning) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        discoverySocket.receive(packet);
                        sendDiscoveryResponse(packet.getAddress());
                    } catch (SocketTimeoutException e) {
                        // продолжаем
                    }
                }
            } catch (Exception e) {
                showToast("Discovery ошибка: " + e.getMessage());
            }
        });
        discoveryThread.start();
    }

    private void sendDiscoveryResponse(InetAddress clientAddress) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeByte(1);
            dos.writeUTF(serverName);
            dos.writeUTF(levelName);
            dos.writeInt(gameType);
            dos.writeInt(1);
            dos.writeInt(maxPlayers);
            dos.writeBoolean(false);
            dos.writeBoolean(false);
            dos.writeInt(2);
            dos.writeInt(4);
            byte[] data = baos.toByteArray();
            DatagramPacket response = new DatagramPacket(data, data.length, clientAddress, 7551);
            discoverySocket.send(response);
        } catch (Exception e) {}
    }

    private void stopAll() {
        discoveryRunning = false;
        if (discoverySocket != null) discoverySocket.close();
        if (zerotierNode != null) {
            try { zerotierNode.leave(ZEROTIER_NETWORK_ID); } catch (Exception e) {}
            try { zerotierNode.close(); } catch (Exception e) {}
        }
    }

    private void showToast(String message) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        );
    }

    // ==================== СТАРЫЙ КОД (КРУЖОК, ОВЕРЛЕЙ, WEBVIEW) ====================
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
                            showOverlay();
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
            hideOverlay();
            if (floatingCircle != null && windowManager != null) {
                windowManager.removeView(floatingCircle);
                floatingCircle = null;
            }
            finishAffinity();
        });

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
            hideOverlay();
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

    private Drawable createCloseIcon() {
        Bitmap bitmap = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(8);
        paint.setStyle(Paint.Style.STROKE);

        float centerX = 30;
        float centerY = 30;
        float offset = 15;

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

        float centerX = 30;
        float centerY = 30;
        float width = 25;
        float height = 15;

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

    private void hideOverlay() {
        if (overlayLayout != null && windowManager != null && isOverlayVisible) {
            windowManager.removeView(overlayLayout);
            isOverlayVisible = false;
        }
    }

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
        stopAll(); // Останавливаем ZeroTier и Discovery
        super.onDestroy();
        if (floatingCircle != null && windowManager != null) {
            windowManager.removeView(floatingCircle);
        }
        if (overlayLayout != null && windowManager != null && isOverlayVisible) {
            windowManager.removeView(overlayLayout);
        }
    }
                              }
