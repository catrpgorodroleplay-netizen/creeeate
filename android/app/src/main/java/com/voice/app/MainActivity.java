package com.voice.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Запрос разрешения у пользователя
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, 100);
        }

        // Настройка WebView
        bridge.getWebView().getSettings().setJavaScriptEnabled(true);
        bridge.getWebView().getSettings().setMediaPlaybackRequiresUserGesture(false);
        
        // КЛЮЧЕВОЙ МОМЕНТ: правильный WebChromeClient
        bridge.getWebView().setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                // Проверяем, что запрос пришел с твоего сайта
                if (request.getOrigin().toString().equals("https://crconferensimessenger.vercel.app")) {
                    // Даем разрешение ТОЛЬКО на микрофон
                    request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                } else {
                    request.deny();
                }
            }
        });
    }
}
