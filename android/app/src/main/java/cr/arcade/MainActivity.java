package com.cr.arcade;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.BridgeActivity;

import java.util.ArrayList;

/**
 * Activity = только "окно" для общего WebView + запрос прав.
 * Вышли из приложения -> WebView уходит в оверлей (OverlayService) без перезагрузки.
 * Вернулись -> WebView возвращается сюда.
 *
 * ВАЖНО: файл должен лежать в
 *   android/app/src/main/java/com/cr/arcade/MainActivity.java
 * и пакет должен быть com.cr.arcade.
 */
public class MainActivity extends BridgeActivity {

    static MainActivity instance;
    private FrameLayout webContainer;
    private boolean webInActivity = false;
    private boolean overlayAsked = false;
    private boolean accessAsked = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;

        webContainer = new FrameLayout(this);
        webContainer.setBackgroundColor(0xFF0E0E0E);
        addContentView(webContainer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                WebView w = WebHolder.get(MainActivity.this);
                if (webInActivity && w.canGoBack()) w.goBack();
                else moveTaskToBack(true); // сворачиваем -> сайт уходит в оверлей
            }
        });

        requestRuntimePermissions();
    }

    // ---- ВАЖНО: public, а не protected (иначе ошибка override) ----

    @Override
    public void onStart() {
        super.onStart();
        OverlayService.appForeground = true;
    }

    @Override
    public void onResume() {
        super.onResume();
        attachWeb();
        ensureOverlayPermission();
        ensureAccessibility();
    }

    @Override
    public void onStop() {
        OverlayService.appForeground = false;
        detachWeb();
        super.onStop();
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    private void attachWeb() {
        WebView w = WebHolder.get(this);
        OverlayService.start(this, OverlayService.ACTION_HIDE);   // прячет оверлей, обновляет типы foreground
        WebHolder.detach();                                       // забираем из оверлейного окна
        WebHolder.setBase(this);
        if (w.getParent() == null) {
            webContainer.addView(w, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        webInActivity = true;
        w.requestFocus();
    }

    private void detachWeb() {
        if (!webInActivity) return;
        WebHolder.detach();
        WebHolder.setBase(getApplicationContext());
        webInActivity = false;
        OverlayService.start(this, OverlayService.ACTION_SHOW);   // оверлей подхватывает WebView
    }

    // ---------------- права ----------------

    private void requestRuntimePermissions() {
        ArrayList<String> need = new ArrayList<>();
        String[] all = Build.VERSION.SDK_INT >= 33
                ? new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA,
                Manifest.permission.POST_NOTIFICATIONS}
                : new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA};
        for (String p : all) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) need.add(p);
        }
        if (!need.isEmpty()) ActivityCompat.requestPermissions(this, need.toArray(new String[0]), 100);
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        OverlayService.start(this, OverlayService.ACTION_HIDE); // пересчитать тип foreground (микрофон)
    }

    private void ensureOverlayPermission() {
        if (overlayAsked || Build.VERSION.SDK_INT < 23) return;
        if (!Settings.canDrawOverlays(this)) {
            overlayAsked = true;
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {}
        }
    }

    private void ensureAccessibility() {
        if (accessAsked || MacroService.instance != null) return;
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) return; // сначала оверлей
        accessAsked = true;
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Разрешение для макросов")
                .setMessage("Чтобы макросы могли нажимать на экран, включите «Arcade» в Специальных возможностях.")
                .setPositiveButton("Включить", (d, w) ->
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .setNegativeButton("Позже", null)
                .show();
    }
}
